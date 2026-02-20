/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.langdetect.charsoup.tools;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Quick evaluation tool: compute macro F1, accuracy, and throughput
 * for multiple bigram models and OpenNLP on the same raw test set.
 * <p>
 * Usage:
 * <pre>
 *   QuickF1Eval &lt;testFile&gt; &lt;model1.ldm&gt; [model2.ldm ...] [reportFile]
 * </pre>
 * The test file is raw (unpreprocessed) lang\ttext format.
 * The last argument is treated as the report file if it doesn't
 * end in .ldm.
 */
public class QuickF1Eval {

    /**
     * Maps every confusable language to a canonical representative
     * (the first member of its group). Non-confusable languages
     * map to themselves.
     */
    private static final Map<String, String> CANONICAL = buildCanonical();

    private static Map<String, String> buildCanonical() {
        Map<String, String> map = new HashMap<>();
        for (String[] group : CompareDetectors.CONFUSABLE_GROUPS) {
            String canon = group[0];
            for (String lang : group) {
                map.put(lang, canon);
            }
        }
        return map;
    }

    static String canonicalize(String lang) {
        return CANONICAL.getOrDefault(lang, lang);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: QuickF1Eval <testFile> <model1.ldm>"
                            + " [model2.ldm ...] [langFile]"
                            + " [reportFile]");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);
        int threads = Runtime.getRuntime().availableProcessors();

        List<Path> modelFiles = new ArrayList<>();
        Path reportFile = null;
        Path langFile = null;
        boolean includeOpenNLP = true;
        boolean doCollapse = true;
        boolean perLang = false;
        boolean binaryScript = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].endsWith(".ldm")) {
                modelFiles.add(Paths.get(args[i]));
            } else if (args[i].endsWith("_langs.txt")) {
                langFile = Paths.get(args[i]);
                includeOpenNLP = false;
            } else if ("--no-collapse".equals(args[i])) {
                doCollapse = false;
            } else if ("--per-lang".equals(args[i])) {
                perLang = true;
            } else if ("--binary-script".equals(args[i])) {
                binaryScript = true;
                doCollapse = false;
            } else {
                reportFile = Paths.get(args[i]);
            }
        }

        // Load test data (raw text)
        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> allData =
                TrainLanguageModel.readPreprocessedFile(testFile);
        System.out.printf(Locale.US,
                "Test sentences: %,d%n", allData.size());

        // Determine allowed canonical languages
        Set<String> allowedLangs = new HashSet<>();
        String langSource;
        if (langFile != null) {
            // Use explicit language whitelist
            for (String line : Files.readAllLines(
                    langFile, StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (!l.isEmpty()) {
                    allowedLangs.add(l);
                }
            }
            langSource = langFile.getFileName().toString();
        } else {
            // Use OpenNLP's language set
            System.out.println("Loading OpenNLP...");
            LanguageDetector opennlp =
                    CompareDetectors.loadDetector(
                            "org.apache.tika.langdetect.opennlp"
                                    + ".OpenNLPDetector");
            if (opennlp != null) {
                CharSoupModel firstModel;
                try (InputStream is = new BufferedInputStream(
                        Files.newInputStream(
                                modelFiles.get(0)))) {
                    firstModel = CharSoupModel.load(is);
                }
                for (String lang : firstModel.getLabels()) {
                    if (opennlp.hasModel(lang)
                            || opennlp.hasModel(
                            canonicalize(lang))) {
                        allowedLangs.add(canonicalize(lang));
                    }
                }
            }
            langSource = "OpenNLP shared";
        }

        // Filter to allowed languages
        List<LabeledSentence> sharedData = new ArrayList<>();
        Set<String> sharedCanonical = new HashSet<>();
        for (LabeledSentence s : allData) {
            String lang = s.getLanguage();
            String mapped = doCollapse
                    ? canonicalize(lang) : lang;
            if (allowedLangs.contains(mapped)) {
                sharedData.add(s);
                sharedCanonical.add(mapped);
            }
        }
        System.out.printf(Locale.US,
                "Shared languages (%s%s): %d "
                        + "(%,d sentences)%n%n",
                langSource,
                doCollapse ? ", collapsed" : ", no-collapse",
                sharedCanonical.size(), sharedData.size());

        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.US,
                "=== Macro F1 Evaluation "
                        + "(shared langs%s) ===%n",
                doCollapse ? ", confusables collapsed"
                        : ", no-collapse"));
        report.append(String.format(Locale.US,
                "Test set:  %s%n",
                testFile.getFileName()));
        report.append(String.format(Locale.US,
                "Sentences: %,d (from %,d total, "
                        + "filtered to %d shared canonical langs)%n",
                sharedData.size(), allData.size(),
                sharedCanonical.size()));
        report.append(String.format(Locale.US,
                "Threads:   %d%n", threads));
        if (doCollapse) {
            report.append(String.format(Locale.US,
                    "Confusable groups collapsed:%n"));
            for (String[] g :
                    CompareDetectors.CONFUSABLE_GROUPS) {
                boolean relevant = false;
                for (String m : g) {
                    if (allowedLangs.contains(
                            canonicalize(m))) {
                        relevant = true;
                        break;
                    }
                }
                if (relevant) {
                    report.append(String.format(Locale.US,
                            "  {%s} -> %s%n",
                            String.join("/", g), g[0]));
                }
            }
        }
        report.append("\n");

        report.append(String.format(Locale.US,
                "%-20s  %8s  %8s  %10s  %12s%n",
                "Model", "MacroF1", "Accuracy", "Time(ms)",
                "Sent/sec"));
        report.append("-".repeat(66)).append("\n");

        boolean warmedUp = false;

        // Evaluate each bigram model
        for (Path mf : modelFiles) {
            System.out.println("Loading: " + mf);
            CharSoupModel model;
            try (InputStream is = new BufferedInputStream(
                    Files.newInputStream(mf))) {
                model = CharSoupModel.load(is);
            }
            FeatureExtractor extractor = model.createExtractor();
            String label = mf.getParent() != null
                    ? mf.getParent().getFileName().toString()
                    : mf.getFileName().toString();
            System.out.printf(Locale.US,
                    "  %s: %d classes, %d buckets%n",
                    label, model.getNumClasses(),
                    model.getNumBuckets());

            if (!warmedUp) {
                System.out.println("Warming up...");
                int w = Math.min(500, sharedData.size());
                for (int i = 0; i < w; i++) {
                    model.predict(extractor.extract(
                            sharedData.get(i).getText()));
                }
                warmedUp = true;
            }

            System.out.println("Evaluating " + label + "...");
            F1Result r = evalBigramParallel(
                    model, extractor, sharedData,
                    threads, doCollapse, binaryScript);
            appendResult(report, "bigram-" + label, r);
            printResult(label, r);
            if (perLang) {
                appendPerLang(report, r);
            }
        }

        // Evaluate OpenNLP (only when not using explicit lang file)
        if (includeOpenNLP) {
            System.out.println("Loading OpenNLP for eval...");
            LanguageDetector opennlp =
                    CompareDetectors.loadDetector(
                            "org.apache.tika.langdetect.opennlp"
                                    + ".OpenNLPDetector");
            if (opennlp != null) {
                int w = Math.min(500, sharedData.size());
                for (int i = 0; i < w; i++) {
                    opennlp.reset();
                    opennlp.addText(
                            sharedData.get(i).getText());
                    opennlp.detectAll();
                }
                System.out.println("Evaluating OpenNLP...");
                F1Result r = evalOpenNLPParallel(
                        opennlp, sharedData, threads,
                        true, binaryScript);
                appendResult(report, "opennlp", r);
                printResult("opennlp", r);
            }
        }

        report.append("\n");
        String reportStr = report.toString();
        System.out.println(reportStr);

        if (reportFile != null) {
            if (reportFile.getParent() != null) {
                Files.createDirectories(reportFile.getParent());
            }
            try (BufferedWriter w = Files.newBufferedWriter(
                    reportFile, StandardCharsets.UTF_8)) {
                w.write(reportStr);
            }
            System.out.println("Report written to: " + reportFile);
        }
    }

    private static void appendResult(StringBuilder report,
                                     String name, F1Result r) {
        report.append(String.format(Locale.US,
                "%-20s  %8.4f  %7.2f%%  %,10d  %,12.0f%n",
                name, r.macroF1,
                100.0 * r.correct / r.total,
                r.elapsedMs,
                r.total / (r.elapsedMs / 1000.0)));
        report.append(formatBottom10(r, "  "));
    }

    private static void printResult(String label, F1Result r) {
        System.out.printf(Locale.US,
                "  -> MacroF1=%.4f  Acc=%.2f%%  %,dms%n%n",
                r.macroF1,
                100.0 * r.correct / r.total,
                r.elapsedMs);
    }

    // ---- F1 accumulator ----

    static class F1Stats {
        // lang -> [TP, FP, FN]
        Map<String, int[]> counts = new HashMap<>();
        int correct = 0;
        int total = 0;
        boolean collapse;
        boolean binaryScript;

        F1Stats(boolean collapse, boolean binaryScript) {
            this.collapse = collapse;
            this.binaryScript = binaryScript;
        }

        static String scriptLabel(String lang) {
            return lang.endsWith("-x-ltr") ? "ltr" : "native";
        }

        void record(String truth, String predicted) {
            if (binaryScript) {
                truth = scriptLabel(truth);
                predicted = scriptLabel(predicted);
            } else if (collapse) {
                truth = canonicalize(truth);
                predicted = canonicalize(predicted);
            }
            total++;
            boolean hit = truth.equals(predicted);
            if (hit) {
                correct++;
            }
            int[] tc = counts.computeIfAbsent(
                    truth, k -> new int[3]);
            int[] pc = counts.computeIfAbsent(
                    predicted, k -> new int[3]);
            if (hit) {
                tc[0]++;
            } else {
                tc[2]++;
                pc[1]++;
            }
        }

        void merge(F1Stats other) {
            correct += other.correct;
            total += other.total;
            for (var e : other.counts.entrySet()) {
                int[] mine = counts.computeIfAbsent(
                        e.getKey(), k -> new int[3]);
                int[] theirs = e.getValue();
                mine[0] += theirs[0];
                mine[1] += theirs[1];
                mine[2] += theirs[2];
            }
        }

        double macroF1() {
            double sum = 0;
            int n = 0;
            for (int[] c : counts.values()) {
                int tp = c[0], fp = c[1], fn = c[2];
                if (tp + fn == 0) {
                    continue;
                }
                double p = tp + fp > 0
                        ? (double) tp / (tp + fp) : 0;
                double r = (double) tp / (tp + fn);
                double f1 = p + r > 0
                        ? 2 * p * r / (p + r) : 0;
                sum += f1;
                n++;
            }
            return n > 0 ? sum / n : 0;
        }
    }

    static class F1Result {
        double macroF1;
        int correct;
        int total;
        long elapsedMs;
        Map<String, int[]> counts;
    }

    // ---- Bigram evaluation ----

    static F1Result evalBigramParallel(
            CharSoupModel model, FeatureExtractor extractor,
            List<LabeledSentence> data, int threads,
            boolean collapse,
            boolean binaryScript) throws Exception {
        List<List<LabeledSentence>> chunks =
                CompareDetectors.partition(data, threads);
        ExecutorService pool =
                Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<F1Stats>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (List<LabeledSentence> chunk : chunks) {
                FeatureExtractor te = model.createExtractor();
                futures.add(pool.submit(
                        () -> evalBigramChunk(
                                model, te, chunk, collapse,
                                binaryScript)));
            }
            F1Stats merged = new F1Stats(
                    collapse, binaryScript);
            for (Future<F1Stats> f : futures) {
                merged.merge(f.get());
            }
            long wallEnd = System.nanoTime();

            F1Result r = new F1Result();
            r.macroF1 = merged.macroF1();
            r.correct = merged.correct;
            r.total = merged.total;
            r.elapsedMs = (wallEnd - wallStart) / 1_000_000;
            r.counts = merged.counts;
            return r;
        } finally {
            pool.shutdown();
        }
    }

    static F1Stats evalBigramChunk(
            CharSoupModel model, FeatureExtractor extractor,
            List<LabeledSentence> data, boolean collapse,
            boolean binaryScript) {
        F1Stats stats = new F1Stats(collapse, binaryScript);
        for (LabeledSentence s : data) {
            int[] features = extractor.extract(s.getText());
            float[] probs = model.predict(features);
            int predIdx = 0;
            for (int c = 1; c < probs.length; c++) {
                if (probs[c] > probs[predIdx]) {
                    predIdx = c;
                }
            }
            stats.record(s.getLanguage(),
                    model.getLabel(predIdx));
        }
        return stats;
    }

    // ---- OpenNLP evaluation ----

    static F1Result evalOpenNLPParallel(
            LanguageDetector detector,
            List<LabeledSentence> data, int threads,
            boolean collapse,
            boolean binaryScript) throws Exception {
        List<List<LabeledSentence>> chunks =
                CompareDetectors.partition(data, threads);
        ExecutorService pool =
                Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<F1Stats>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (List<LabeledSentence> chunk : chunks) {
                futures.add(pool.submit(
                        () -> evalOpenNLPChunk(
                                detector, chunk, collapse,
                                binaryScript)));
            }
            F1Stats merged = new F1Stats(
                    collapse, binaryScript);
            for (Future<F1Stats> f : futures) {
                merged.merge(f.get());
            }
            long wallEnd = System.nanoTime();

            F1Result r = new F1Result();
            r.macroF1 = merged.macroF1();
            r.correct = merged.correct;
            r.total = merged.total;
            r.elapsedMs = (wallEnd - wallStart) / 1_000_000;
            r.counts = merged.counts;
            return r;
        } finally {
            pool.shutdown();
        }
    }

    static F1Stats evalOpenNLPChunk(
            LanguageDetector detector,
            List<LabeledSentence> data, boolean collapse,
            boolean binaryScript) {
        F1Stats stats = new F1Stats(collapse, binaryScript);
        LanguageDetector local;
        try {
            local = CompareDetectors.loadDetector(
                    "org.apache.tika.langdetect.opennlp"
                            + ".OpenNLPDetector");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (local == null) {
            return stats;
        }
        for (LabeledSentence s : data) {
            local.reset();
            local.addText(s.getText());
            List<LanguageResult> results = local.detectAll();
            String predicted = results.isEmpty()
                    ? "unk" : results.get(0).getLanguage();
            stats.record(s.getLanguage(), predicted);
        }
        return stats;
    }

    // ---- Formatting ----

    static String formatBottom10(F1Result r, String indent) {
        List<Map.Entry<String, double[]>> langF1 =
                new ArrayList<>();
        for (var e : r.counts.entrySet()) {
            int[] c = e.getValue();
            int tp = c[0], fp = c[1], fn = c[2];
            if (tp + fn == 0) {
                continue;
            }
            double p = tp + fp > 0
                    ? (double) tp / (tp + fp) : 0;
            double rec = (double) tp / (tp + fn);
            double f1 = p + rec > 0
                    ? 2 * p * rec / (p + rec) : 0;
            langF1.add(Map.entry(e.getKey(),
                    new double[]{f1, p, rec, tp + fn}));
        }
        langF1.sort((a, b) -> Double.compare(
                a.getValue()[0], b.getValue()[0]));

        StringBuilder sb = new StringBuilder();
        sb.append(indent).append(String.format(Locale.US,
                "Bottom 10: "));
        int show = Math.min(10, langF1.size());
        for (int i = 0; i < show; i++) {
            var e = langF1.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.US,
                    "%s=%.3f", e.getKey(), e.getValue()[0]));
        }
        sb.append("\n");
        return sb.toString();
    }

    static void appendPerLang(StringBuilder report,
                              F1Result r) {
        List<Map.Entry<String, double[]>> langF1 =
                new ArrayList<>();
        for (var e : r.counts.entrySet()) {
            int[] c = e.getValue();
            int tp = c[0], fp = c[1], fn = c[2];
            if (tp + fn == 0) {
                continue;
            }
            double p = tp + fp > 0
                    ? (double) tp / (tp + fp) : 0;
            double rec = (double) tp / (tp + fn);
            double f1 = p + rec > 0
                    ? 2 * p * rec / (p + rec) : 0;
            langF1.add(Map.entry(e.getKey(),
                    new double[]{f1, p, rec, tp + fn}));
        }
        langF1.sort((a, b) -> Double.compare(
                a.getValue()[0], b.getValue()[0]));
        report.append(String.format(Locale.US,
                "  %-16s  %8s  %8s  %8s  %8s%n",
                "Language", "F1", "Prec", "Recall",
                "Count"));
        report.append("  ").append("-".repeat(56))
                .append("\n");
        for (var e : langF1) {
            double[] v = e.getValue();
            report.append(String.format(Locale.US,
                    "  %-16s  %8.4f  %8.4f  %8.4f  %8.0f%n",
                    e.getKey(), v[0], v[1], v[2], v[3]));
        }
        report.append("\n");
    }
}
