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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;
import org.apache.tika.langdetect.charsoup.TextFeatureExtractor;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.ml.LinearModel;

/**
 * Cross-domain evaluation: test bigram model and OpenNLP on external
 * datasets (Tatoeba, WiLI-2018) that were NOT used during training.
 * <p>
 * Reports accuracy at multiple text-length thresholds to assess
 * short-text performance — the key use case for PDF directionality
 * detection.
 * <p>
 * Also measures heap usage and throughput, and writes out a normalized
 * {@code lang\ttext} file for the shared-language subset so that
 * external tools (e.g., {@code eval_fasttext.py}) can be run on
 * identical data.
 * <p>
 * Usage:
 * <pre>
 *   CrossDomainEval &lt;bigramModelFile&gt; &lt;dataset&gt; &lt;dataPath&gt;
 *                   [reportFile] [threads]
 *
 *   dataset: "tatoeba" or "wili"
 *   dataPath: for tatoeba: path to sentences.csv
 *             for wili: directory containing x_test.txt, y_test.txt,
 *                       labels.csv
 * </pre>
 */
public class CrossDomainEval {

    /** Length thresholds for bucketed accuracy reporting. */
    private static final int[] THRESHOLDS = {10, 20, 30, 50, 100};

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: CrossDomainEval <bigramModel>"
                    + " <tatoeba|wili> <dataPath>"
                    + " [reportFile] [threads]");
            System.exit(1);
        }

        Path modelFile = Paths.get(args[0]);
        String dataset = args[1].toLowerCase(Locale.ROOT);
        Path dataPath = Paths.get(args[2]);
        Path reportFile = args.length > 3 ? Paths.get(args[3]) : null;
        int threads = args.length > 4
                ? Integer.parseInt(args[4])
                : Runtime.getRuntime().availableProcessors();

        // ---- Load bigram model (with heap measurement) ----
        System.out.println("Loading bigram model: " + modelFile);
        long heapBefore = usedHeap();
        LinearModel model;
        try (InputStream is = new BufferedInputStream(
                Files.newInputStream(modelFile))) {
            model = LinearModel.load(is);
        }
        TextFeatureExtractor extractor = new ScriptAwareFeatureExtractor(model.getNumBuckets());
        long bigramHeapBytes = usedHeap() - heapBefore;
        Set<String> modelLangs = new HashSet<>(
                Arrays.asList(model.getLabels()));
        System.out.printf(Locale.US,
                "  %d classes, %d buckets, ~%.1f MB heap%n",
                model.getNumClasses(), model.getNumBuckets(),
                bigramHeapBytes / (1024.0 * 1024.0));

        // ---- Load OpenNLP detectors (one per thread) ----
        System.out.println("Loading OpenNLP detector(s)...");
        heapBefore = usedHeap();
        String opennlpClassName =
                "org.apache.tika.langdetect.opennlp.OpenNLPDetector";
        List<LanguageDetector> opennlpPool = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            LanguageDetector d =
                    CompareDetectors.loadDetector(opennlpClassName);
            if (d == null) {
                break;
            }
            opennlpPool.add(d);
        }
        LanguageDetector opennlpDetector =
                opennlpPool.isEmpty() ? null : opennlpPool.get(0);
        long opennlpHeapBytes = opennlpDetector != null
                ? usedHeap() - heapBefore : 0;
        if (opennlpDetector != null) {
            System.out.printf(Locale.US,
                    "  OpenNLP: %d instance(s), ~%.1f MB heap%n",
                    opennlpPool.size(),
                    opennlpHeapBytes / (1024.0 * 1024.0));
        }

        // ---- Load dataset ----
        System.out.println("\nLoading dataset: " + dataset
                + " from " + dataPath);
        List<LabeledSentence> data;
        if ("tatoeba".equals(dataset)) {
            data = loadTatoeba(dataPath, modelLangs);
        } else if ("wili".equals(dataset)) {
            data = loadWiLI(dataPath, modelLangs);
        } else {
            System.err.println("Unknown dataset: " + dataset);
            System.exit(1);
            return;
        }

        // Count shared languages
        Set<String> sharedLangs = new HashSet<>();
        for (LabeledSentence s : data) {
            sharedLangs.add(s.getLanguage());
        }
        System.out.printf(Locale.US,
                "Loaded %,d sentences across %d shared languages%n",
                data.size(), sharedLangs.size());

        // ---- Write normalized shared-language file for fastText ----
        if (reportFile != null) {
            Path sharedFile = reportFile.getParent() != null
                    ? reportFile.getParent().resolve(
                    dataset + "-shared.txt")
                    : Paths.get(dataset + "-shared.txt");
            System.out.println("Writing shared-language data: "
                    + sharedFile);
            try (BufferedWriter w = Files.newBufferedWriter(
                    sharedFile, StandardCharsets.UTF_8)) {
                for (LabeledSentence s : data) {
                    w.write(s.getLanguage());
                    w.write('\t');
                    w.write(s.getText());
                    w.newLine();
                }
            }
            System.out.printf(Locale.US,
                    "  Wrote %,d sentences for external eval%n",
                    data.size());
        }

        // ---- Bucket by length ----
        Map<String, List<LabeledSentence>> buckets =
                bucketByLength(data);
        for (Map.Entry<String, List<LabeledSentence>> e
                : buckets.entrySet()) {
            System.out.printf(Locale.US, "  %-14s %,d%n",
                    e.getKey(), e.getValue().size());
        }

        // ---- Warmup ----
        System.out.println("\nWarming up...");
        int warmup = Math.min(500, data.size());
        for (int i = 0; i < warmup; i++) {
            model.predict(extractor.extract(data.get(i).getText()));
            if (opennlpDetector != null) {
                opennlpDetector.reset();
                opennlpDetector.addText(data.get(i).getText());
                opennlpDetector.detectAll();
            }
        }

        // ---- Evaluate all length buckets ----
        System.out.println("Evaluating (" + threads
                + " threads)...\n");

        Map<String, CompareDetectors.EvalResult> bigramResults =
                new LinkedHashMap<>();
        Map<String, CompareDetectors.EvalResult> opennlpResults =
                new LinkedHashMap<>();

        for (Map.Entry<String, List<LabeledSentence>> e
                : buckets.entrySet()) {
            String bucket = e.getKey();
            List<LabeledSentence> subset = e.getValue();

            bigramResults.put(bucket,
                    CompareDetectors.evaluateBigramParallel(
                            model, extractor, subset,
                            "bigram-" + bucket, threads));
            opennlpResults.put(bucket,
                    CompareDetectors.evaluateOpenNLPParallel(
                            opennlpPool, subset,
                            "opennlp-" + bucket));
        }

        // ---- Build report ----
        StringBuilder report = new StringBuilder();
        report.append(String.format(Locale.US,
                "=== Cross-Domain Evaluation: %s ===%n%n",
                dataset.toUpperCase(Locale.ROOT)));
        report.append(String.format(Locale.US,
                "Model:    %s (%d classes, %d buckets)%n",
                modelFile.getFileName(),
                model.getNumClasses(), model.getNumBuckets()));
        report.append(String.format(Locale.US,
                "Dataset:  %s (%,d sentences, %d shared langs)%n",
                dataset, data.size(), sharedLangs.size()));
        report.append(String.format(Locale.US,
                "Threads:  %d%n%n", threads));

        // Model sizes
        report.append("Model heap (approx):\n");
        report.append(String.format(Locale.US,
                "  Bigram:  ~%.1f MB%n",
                bigramHeapBytes / (1024.0 * 1024.0)));
        report.append(String.format(Locale.US,
                "  OpenNLP: ~%.1f MB%n%n",
                opennlpHeapBytes / (1024.0 * 1024.0)));

        // Strict accuracy summary table
        report.append(
                "Strict accuracy (exact language match):\n");
        report.append(String.format(Locale.US,
                "%-14s  %10s  %10s  %12s  %12s%n",
                "Length bucket", "Bigram", "OpenNLP",
                "Time(ms)", "Sent/sec"));
        report.append("-".repeat(66)).append("\n");

        for (String bucket : buckets.keySet()) {
            CompareDetectors.EvalResult br =
                    bigramResults.get(bucket);
            CompareDetectors.EvalResult or =
                    opennlpResults.get(bucket);
            report.append(String.format(Locale.US,
                    "%-14s  %9s  %9s  %,10d  %,10.0f%n",
                    bucket,
                    fmtAcc(br, false),
                    fmtAcc(or, false),
                    br.elapsedMs,
                    throughput(br)));
        }
        report.append("\n");

        // Group accuracy summary table
        report.append(
                "Group accuracy (confusable languages "
                        + "counted as correct):\n");
        report.append(formatConfusableGroups());
        report.append(String.format(Locale.US,
                "%-14s  %10s  %10s%n",
                "Length bucket", "Bigram", "OpenNLP"));
        report.append("-".repeat(40)).append("\n");

        for (String bucket : buckets.keySet()) {
            CompareDetectors.EvalResult br =
                    bigramResults.get(bucket);
            CompareDetectors.EvalResult or =
                    opennlpResults.get(bucket);
            report.append(String.format(Locale.US,
                    "%-14s  %9s  %9s%n",
                    bucket,
                    fmtAcc(br, true),
                    fmtAcc(or, true)));
        }
        report.append("\n");

        // Bigram timing breakdown (all sentences)
        CompareDetectors.EvalResult bigramAll =
                bigramResults.get("all");
        if (bigramAll != null) {
            long cpuTotal = bigramAll.preprocessMs
                    + bigramAll.extractMs + bigramAll.predictMs;
            report.append("Bigram timing breakdown "
                    + "(all sentences, CPU-time across threads):\n");
            report.append(String.format(Locale.US,
                    "  Preprocess (NFC/URL/truncate):"
                            + " %,d ms (%.0f%%)%n",
                    bigramAll.preprocessMs,
                    pct(bigramAll.preprocessMs, cpuTotal)));
            report.append(String.format(Locale.US,
                    "  Feature extraction:           "
                            + " %,d ms (%.0f%%)%n",
                    bigramAll.extractMs,
                    pct(bigramAll.extractMs, cpuTotal)));
            report.append(String.format(Locale.US,
                    "  Model prediction (softmax):   "
                            + " %,d ms (%.0f%%)%n",
                    bigramAll.predictMs,
                    pct(bigramAll.predictMs, cpuTotal)));
            report.append(String.format(Locale.US,
                    "  CPU total:                    "
                            + " %,d ms%n", cpuTotal));
            report.append(String.format(Locale.US,
                    "  Wall-clock total:             "
                            + " %,d ms%n%n", bigramAll.elapsedMs));
        }

        // Per-language detail
        report.append(perLanguageReport(bigramResults, opennlpResults));

        // Detailed bigram analysis: macro F1, confusion pairs,
        // confidence calibration, entropy-threshold accuracy
        System.out.println("Running detailed bigram analysis...");
        report.append(detailedBigramAnalysis(
                model, extractor, data, threads));

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

    // ---- Dataset loaders ----

    /**
     * Load Tatoeba sentences.csv (id\tlang\ttext), filtering to
     * languages our model supports. Tatoeba uses ISO 639-3 codes
     * which match ours directly.
     */
    static List<LabeledSentence> loadTatoeba(
            Path sentencesFile, Set<String> modelLangs)
            throws Exception {
        List<LabeledSentence> sentences = new ArrayList<>();
        Map<String, Integer> skippedLangs = new HashMap<>();
        int total = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                sentencesFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                String lang = parts[1].trim();
                String text = parts[2].trim();
                if (text.isEmpty()) {
                    continue;
                }

                // Map Tatoeba codes to our ISO 639-3 codes
                String mapped = mapTatoebaLang(lang);
                if (mapped != null && modelLangs.contains(mapped)) {
                    sentences.add(
                            new LabeledSentence(mapped, text));
                } else {
                    skippedLangs.merge(lang, 1, Integer::sum);
                }
            }
        }

        Set<String> foundLangs = new HashSet<>();
        for (LabeledSentence s : sentences) {
            foundLangs.add(s.getLanguage());
        }
        System.out.printf(Locale.US,
                "Tatoeba: %,d/%,d sentences in %d shared languages"
                        + " (skipped %d language codes)%n",
                sentences.size(), total, foundLangs.size(),
                skippedLangs.size());
        return sentences;
    }

    /**
     * Load WiLI-2018 test set (x_test.txt + y_test.txt +
     * labels.csv), filtering to shared languages.
     */
    static List<LabeledSentence> loadWiLI(
            Path wiliDir, Set<String> modelLangs)
            throws Exception {
        // Build label → ISO 639-3 mapping from labels.csv
        Map<String, String> labelToIso = new HashMap<>();
        Path labelsFile = wiliDir.resolve("labels.csv");
        try (BufferedReader r = Files.newBufferedReader(
                labelsFile, StandardCharsets.UTF_8)) {
            String line = r.readLine(); // skip header
            while ((line = r.readLine()) != null) {
                String[] parts = line.split(";", -1);
                if (parts.length >= 4) {
                    String label = parts[0].trim();
                    String iso = parts[3].trim();
                    if (!iso.isEmpty()) {
                        labelToIso.put(label, iso);
                    } else {
                        labelToIso.put(label, label);
                    }
                }
            }
        }

        // Read x_test.txt and y_test.txt
        List<String> texts = Files.readAllLines(
                wiliDir.resolve("x_test.txt"),
                StandardCharsets.UTF_8);
        List<String> labels = Files.readAllLines(
                wiliDir.resolve("y_test.txt"),
                StandardCharsets.UTF_8);

        if (texts.size() != labels.size()) {
            throw new IllegalStateException(
                    "WiLI x/y size mismatch: "
                            + texts.size() + " vs "
                            + labels.size());
        }

        List<LabeledSentence> sentences = new ArrayList<>();
        Map<String, Integer> skippedLangs = new HashMap<>();

        for (int i = 0; i < texts.size(); i++) {
            String wiliLabel = labels.get(i).trim();
            String text = texts.get(i).trim();
            if (text.isEmpty()) {
                continue;
            }

            String iso = labelToIso.getOrDefault(
                    wiliLabel, wiliLabel);
            String mapped = mapWiLILang(iso);
            if (mapped != null && modelLangs.contains(mapped)) {
                sentences.add(new LabeledSentence(mapped, text));
            } else {
                skippedLangs.merge(wiliLabel, 1, Integer::sum);
            }
        }

        Set<String> foundLangs = new HashSet<>();
        for (LabeledSentence s : sentences) {
            foundLangs.add(s.getLanguage());
        }
        System.out.printf(Locale.US,
                "WiLI: %,d/%,d paragraphs in %d shared languages"
                        + " (skipped %d label codes)%n",
                sentences.size(), texts.size(), foundLangs.size(),
                skippedLangs.size());
        return sentences;
    }

    // ---- Language code mapping ----

    /**
     * Map Tatoeba language codes to our model's ISO 639-3 codes.
     * Tatoeba mostly uses ISO 639-3 but has some exceptions.
     */
    static String mapTatoebaLang(String code) {
        switch (code) {
            case "ber": return "kab";
            case "cmn": return "cmn";
            case "zsm": return "zsm";
            case "lvs": return "lvs";
            case "ekk": return "ekk";
            case "nob": return "nob";
            case "nno": return "nno";
            case "yue": return "yue";
            case "wuu": return "wuu";
            case "por": return "por";
            default: return code;
        }
    }

    /**
     * Map WiLI ISO 639-3 / label codes to our model's codes.
     */
    static String mapWiLILang(String iso) {
        switch (iso) {
            case "gsw": return "als";
            default: return iso;
        }
    }

    // ---- Length bucketing ----

    static Map<String, List<LabeledSentence>> bucketByLength(
            List<LabeledSentence> data) {
        Map<String, List<LabeledSentence>> buckets =
                new LinkedHashMap<>();
        for (int t : THRESHOLDS) {
            buckets.put("<=" + t + " chars", new ArrayList<>());
        }
        buckets.put(">" + THRESHOLDS[THRESHOLDS.length - 1]
                + " chars", new ArrayList<>());
        buckets.put("all", new ArrayList<>());

        for (LabeledSentence s : data) {
            int len = s.getText().length();
            buckets.get("all").add(s);
            boolean placed = false;
            for (int t : THRESHOLDS) {
                if (len <= t) {
                    buckets.get("<=" + t + " chars").add(s);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                buckets.get(">" + THRESHOLDS[THRESHOLDS.length - 1]
                        + " chars").add(s);
            }
        }
        return buckets;
    }

    // ---- Per-language report ----

    /**
     * Build per-language accuracy table from the "all" bucket results.
     * Includes strict and group accuracy for both detectors, plus
     * confusable-group markers.
     */
    static String perLanguageReport(
            Map<String, CompareDetectors.EvalResult> bigramResults,
            Map<String, CompareDetectors.EvalResult> opennlpResults) {

        CompareDetectors.EvalResult bigramAll =
                bigramResults.get("all");
        CompareDetectors.EvalResult opennlpAll =
                opennlpResults.get("all");

        StringBuilder sb = new StringBuilder();
        sb.append("Per-language accuracy (all sentences):\n");
        sb.append(String.format(Locale.US,
                "%-12s  %8s %8s %8s  %8s %8s %8s%n",
                "Language", "Bigram", "Bi-Grp%", "Bigram%",
                "OpenNLP", "ON-Grp%", "ONLP%"));
        sb.append("-".repeat(82)).append("\n");

        // Merge per-lang:
        // [0]=bigram strict, [1]=bigram total, [2]=bigram group,
        // [3]=opennlp strict, [4]=opennlp total, [5]=opennlp group
        Map<String, int[]> merged = new TreeMap<>();
        if (bigramAll != null && bigramAll.perLang != null) {
            for (var e : bigramAll.perLang.entrySet()) {
                int[] row = merged.computeIfAbsent(
                        e.getKey(), k -> new int[6]);
                row[0] = e.getValue()[0];
                row[1] = e.getValue()[1];
                row[2] = e.getValue()[2];
            }
        }
        if (opennlpAll != null && opennlpAll.perLang != null) {
            for (var e : opennlpAll.perLang.entrySet()) {
                int[] row = merged.computeIfAbsent(
                        e.getKey(), k -> new int[6]);
                row[3] = e.getValue()[0];
                row[4] = e.getValue()[1];
                row[5] = e.getValue()[2];
            }
        }

        int bigramWins = 0;
        int opennlpWins = 0;
        int ties = 0;
        for (var e : merged.entrySet()) {
            int[] c = e.getValue();
            String lang = e.getKey();
            boolean confusable =
                    CompareDetectors.isGroupMatch(lang, "")
                            ? false
                            : isInConfusableGroup(lang);
            String bStrict = c[1] > 0
                    ? String.format(Locale.US, "%6.1f%%",
                    100.0 * c[0] / c[1]) : "   N/A";
            String bGroup = confusable && c[1] > 0
                    ? String.format(Locale.US, "%6.1f%%",
                    100.0 * c[2] / c[1]) : "      ";
            String oStrict = c[4] > 0
                    ? String.format(Locale.US, "%6.1f%%",
                    100.0 * c[3] / c[4]) : "   N/A";
            String oGroup = confusable && c[4] > 0
                    ? String.format(Locale.US, "%6.1f%%",
                    100.0 * c[5] / c[4]) : "      ";
            String marker = confusable ? " *" : "";
            sb.append(String.format(Locale.US,
                    "%-12s  %4d/%-4d %s %s"
                            + "  %4d/%-4d %s %s%s%n",
                    lang, c[0], c[1], bGroup, bStrict,
                    c[3], c[4], oGroup, oStrict, marker));

            if (c[1] > 0 && c[4] > 0) {
                double bAcc = (double) c[0] / c[1];
                double oAcc = (double) c[3] / c[4];
                if (bAcc > oAcc + 0.005) {
                    bigramWins++;
                } else if (oAcc > bAcc + 0.005) {
                    opennlpWins++;
                } else {
                    ties++;
                }
            }
        }

        sb.append("\n* = member of a confusable group; "
                + "Grp% = group accuracy\n");
        sb.append(String.format(Locale.US,
                "%nBigram wins: %d  OpenNLP wins: %d  Ties: %d "
                        + "(>0.5%% margin)%n",
                bigramWins, opennlpWins, ties));
        return sb.toString();
    }

    // ---- Detailed bigram analysis ----

    /** Number of entropy histogram bins (0.1 resolution, 0.0 to 12.0). */
    private static final int ENTROPY_BINS = 120;
    private static final float ENTROPY_BIN_WIDTH = 0.1f;

    /** Entropy thresholds for "reject low-confidence" analysis. */
    private static final float[] ENTROPY_THRESHOLDS =
            {0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f};

    /** Top-N confusion pairs to report. */
    private static final int TOP_CONFUSIONS = 30;

    /**
     * Thread-local accumulator for detailed per-prediction stats.
     * Designed to be merged across threads after parallel evaluation.
     */
    static class DetailedStats {
        // Per-language TP/FP/FN for macro F1
        // key=lang, value=[TP, FP, FN]
        Map<String, int[]> langCounts = new HashMap<>();

        // Confusion pairs: "truth\tpred" -> count (errors only)
        Map<String, Integer> confusions = new HashMap<>();

        // Entropy histogram: [bin][0]=total, [bin][1]=correct
        int[][] entropyHist = new int[ENTROPY_BINS + 1][2];

        // Running sums for mean entropy
        double entropyCorrectSum = 0;
        int entropyCorrectCount = 0;
        double entropyIncorrectSum = 0;
        int entropyIncorrectCount = 0;

        void record(String truth, String predicted, float entropy) {
            boolean correct = truth.equals(predicted);

            // TP/FP/FN
            int[] tc = langCounts.computeIfAbsent(
                    truth, k -> new int[3]);
            int[] pc = langCounts.computeIfAbsent(
                    predicted, k -> new int[3]);
            if (correct) {
                tc[0]++; // TP for truth
            } else {
                tc[2]++; // FN for truth
                pc[1]++; // FP for predicted
                String key = truth + "\t" + predicted;
                confusions.merge(key, 1, Integer::sum);
            }

            // Entropy histogram
            int bin = Math.min(
                    (int) (entropy / ENTROPY_BIN_WIDTH), ENTROPY_BINS);
            entropyHist[bin][0]++;
            if (correct) {
                entropyHist[bin][1]++;
            }

            // Running entropy sums
            if (correct) {
                entropyCorrectSum += entropy;
                entropyCorrectCount++;
            } else {
                entropyIncorrectSum += entropy;
                entropyIncorrectCount++;
            }
        }

        void merge(DetailedStats other) {
            for (var e : other.langCounts.entrySet()) {
                int[] mine = langCounts.computeIfAbsent(
                        e.getKey(), k -> new int[3]);
                int[] theirs = e.getValue();
                mine[0] += theirs[0];
                mine[1] += theirs[1];
                mine[2] += theirs[2];
            }
            for (var e : other.confusions.entrySet()) {
                confusions.merge(
                        e.getKey(), e.getValue(), Integer::sum);
            }
            for (int i = 0; i <= ENTROPY_BINS; i++) {
                entropyHist[i][0] += other.entropyHist[i][0];
                entropyHist[i][1] += other.entropyHist[i][1];
            }
            entropyCorrectSum += other.entropyCorrectSum;
            entropyCorrectCount += other.entropyCorrectCount;
            entropyIncorrectSum += other.entropyIncorrectSum;
            entropyIncorrectCount += other.entropyIncorrectCount;
        }
    }

    /**
     * Run a detailed single-pass evaluation collecting macro F1,
     * confusion pairs, confidence calibration, and entropy-threshold
     * accuracy.
     */
    static String detailedBigramAnalysis(
            LinearModel model, TextFeatureExtractor extractor,
            List<LabeledSentence> data, int threads)
            throws Exception {

        // Build label index for argmax -> label
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < model.getNumClasses(); i++) {
            labelIndex.put(model.getLabel(i), i);
        }

        // Parallel evaluation
        DetailedStats merged;
        if (threads <= 1) {
            merged = detailedChunk(model, extractor, data);
        } else {
            List<List<LabeledSentence>> chunks =
                    CompareDetectors.partition(data, threads);
            ExecutorService pool =
                    Executors.newFixedThreadPool(chunks.size());
            try {
                List<Future<DetailedStats>> futures =
                        new ArrayList<>();
                for (List<LabeledSentence> chunk : chunks) {
                    TextFeatureExtractor te =
                            new ScriptAwareFeatureExtractor(model.getNumBuckets());
                    futures.add(pool.submit(
                            () -> detailedChunk(
                                    model, te, chunk)));
                }
                merged = new DetailedStats();
                for (Future<DetailedStats> f : futures) {
                    merged.merge(f.get());
                }
            } finally {
                pool.shutdown();
            }
        }

        // Format report
        StringBuilder sb = new StringBuilder();

        // 1. Macro F1
        sb.append("Macro F1 (bigram model):\n");
        double macroF1Sum = 0;
        int macroF1Count = 0;
        for (var e : merged.langCounts.entrySet()) {
            int[] c = e.getValue();
            int tp = c[0];
            int fp = c[1];
            int fn = c[2];
            double precision = tp + fp > 0
                    ? (double) tp / (tp + fp) : 0;
            double recall = tp + fn > 0
                    ? (double) tp / (tp + fn) : 0;
            double f1 = precision + recall > 0
                    ? 2 * precision * recall / (precision + recall)
                    : 0;
            if (tp + fn > 0) {
                macroF1Sum += f1;
                macroF1Count++;
            }
        }
        double macroF1 = macroF1Count > 0
                ? macroF1Sum / macroF1Count : 0;
        sb.append(String.format(Locale.US,
                "  Macro F1:     %.4f (%d languages)%n",
                macroF1, macroF1Count));

        // Also compute micro F1 (= accuracy for multi-class)
        int totalTP = 0;
        int totalSamples = 0;
        for (int[] c : merged.langCounts.values()) {
            totalTP += c[0];
            totalSamples += c[0] + c[2]; // TP + FN = total per lang
        }
        sb.append(String.format(Locale.US,
                "  Micro F1:     %.4f (= accuracy)%n%n",
                totalSamples > 0
                        ? (double) totalTP / totalSamples : 0));

        // Bottom-10 languages by F1
        List<Map.Entry<String, double[]>> langF1 = new ArrayList<>();
        for (var e : merged.langCounts.entrySet()) {
            int[] c = e.getValue();
            int tp = c[0], fp = c[1], fn = c[2];
            double p = tp + fp > 0 ? (double) tp / (tp + fp) : 0;
            double r = tp + fn > 0 ? (double) tp / (tp + fn) : 0;
            double f1 = p + r > 0 ? 2 * p * r / (p + r) : 0;
            langF1.add(Map.entry(e.getKey(),
                    new double[]{f1, p, r, tp + fn}));
        }
        langF1.sort((a, b) -> Double.compare(
                a.getValue()[0], b.getValue()[0]));

        sb.append("Bottom 15 languages by F1:\n");
        sb.append(String.format(Locale.US,
                "  %-12s  %8s  %8s  %8s  %6s%n",
                "Language", "F1", "Prec", "Recall", "Count"));
        sb.append("  ").append("-".repeat(50)).append("\n");
        int show = Math.min(15, langF1.size());
        for (int i = 0; i < show; i++) {
            var e = langF1.get(i);
            double[] v = e.getValue();
            sb.append(String.format(Locale.US,
                    "  %-12s  %7.4f  %7.4f  %7.4f  %5.0f%n",
                    e.getKey(), v[0], v[1], v[2], v[3]));
        }
        sb.append("\n");

        // 2. Top confusion pairs
        sb.append(String.format(Locale.US,
                "Top %d confusion pairs (truth -> predicted):%n",
                TOP_CONFUSIONS));
        sb.append(String.format(Locale.US,
                "  %-12s  %-12s  %8s%n",
                "Truth", "Predicted", "Count"));
        sb.append("  ").append("-".repeat(36)).append("\n");

        merged.confusions.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        b.getValue(), a.getValue()))
                .limit(TOP_CONFUSIONS)
                .forEach(e -> {
                    String[] parts = e.getKey().split("\t", 2);
                    sb.append(String.format(Locale.US,
                            "  %-12s  %-12s  %,8d%n",
                            parts[0], parts[1], e.getValue()));
                });
        sb.append("\n");

        // 3. Confidence calibration (mean entropy)
        sb.append("Confidence calibration (entropy, bits):\n");
        double meanCorrect = merged.entropyCorrectCount > 0
                ? merged.entropyCorrectSum
                / merged.entropyCorrectCount : 0;
        double meanIncorrect = merged.entropyIncorrectCount > 0
                ? merged.entropyIncorrectSum
                / merged.entropyIncorrectCount : 0;
        sb.append(String.format(Locale.US,
                "  Correct predictions:   mean entropy = "
                        + "%.3f bits (%,d samples)%n",
                meanCorrect, merged.entropyCorrectCount));
        sb.append(String.format(Locale.US,
                "  Incorrect predictions: mean entropy = "
                        + "%.3f bits (%,d samples)%n",
                meanIncorrect, merged.entropyIncorrectCount));
        sb.append(String.format(Locale.US,
                "  Separation ratio:      %.1fx%n%n",
                meanCorrect > 0
                        ? meanIncorrect / meanCorrect : 0));

        // 4. Entropy-threshold accuracy ("reject uncertain")
        sb.append("Entropy-threshold accuracy "
                + "(reject predictions above threshold):\n");
        sb.append(String.format(Locale.US,
                "  %-14s  %10s  %10s  %10s%n",
                "Max entropy", "Accuracy", "Accepted",
                "Rejected%"));
        sb.append("  ").append("-".repeat(50)).append("\n");

        // Cumulate histogram from low to high
        int cumTotal = 0;
        int cumCorrect = 0;
        int grandTotal = merged.entropyCorrectCount
                + merged.entropyIncorrectCount;
        int threshIdx = 0;
        for (int bin = 0; bin <= ENTROPY_BINS
                && threshIdx < ENTROPY_THRESHOLDS.length; bin++) {
            cumTotal += merged.entropyHist[bin][0];
            cumCorrect += merged.entropyHist[bin][1];
            float binEnd = (bin + 1) * ENTROPY_BIN_WIDTH;
            while (threshIdx < ENTROPY_THRESHOLDS.length
                    && ENTROPY_THRESHOLDS[threshIdx] <= binEnd) {
                double acc = cumTotal > 0
                        ? 100.0 * cumCorrect / cumTotal : 0;
                double rejPct = grandTotal > 0
                        ? 100.0 * (grandTotal - cumTotal)
                        / grandTotal : 0;
                sb.append(String.format(Locale.US,
                        "  <= %-9.1f  %9.2f%%  %,10d  %9.1f%%%n",
                        ENTROPY_THRESHOLDS[threshIdx],
                        acc, cumTotal, rejPct));
                threshIdx++;
            }
        }
        // Fill remaining thresholds with cumulated totals
        // (accumulate remaining bins)
        for (int bin = (int) (ENTROPY_THRESHOLDS[
                Math.min(threshIdx, ENTROPY_THRESHOLDS.length) - 1]
                / ENTROPY_BIN_WIDTH) + 1;
             bin <= ENTROPY_BINS; bin++) {
            cumTotal += merged.entropyHist[bin][0];
            cumCorrect += merged.entropyHist[bin][1];
        }
        while (threshIdx < ENTROPY_THRESHOLDS.length) {
            double acc = cumTotal > 0
                    ? 100.0 * cumCorrect / cumTotal : 0;
            double rejPct = grandTotal > 0
                    ? 100.0 * (grandTotal - cumTotal)
                    / grandTotal : 0;
            sb.append(String.format(Locale.US,
                    "  <= %-9.1f  %9.2f%%  %,10d  %9.1f%%%n",
                    ENTROPY_THRESHOLDS[threshIdx],
                    acc, cumTotal, rejPct));
            threshIdx++;
        }
        // "no threshold" = accept all
        sb.append(String.format(Locale.US,
                "  %-14s  %9.2f%%  %,10d  %9.1f%%%n",
                "(no threshold)",
                grandTotal > 0
                        ? 100.0 * (merged.entropyCorrectCount)
                        / grandTotal : 0,
                grandTotal, 0.0));
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Evaluate a chunk of data collecting detailed per-prediction stats.
     */
    private static DetailedStats detailedChunk(
            LinearModel model, TextFeatureExtractor extractor,
            List<LabeledSentence> data) {
        DetailedStats stats = new DetailedStats();
        for (LabeledSentence s : data) {
            String cleaned = CharSoupFeatureExtractor.preprocess(
                    s.getText());
            int[] features =
                    extractor.extractFromPreprocessed(cleaned);
            float[] probs = model.predict(features);
            float entropy = LinearModel.entropy(probs);

            int predIdx = 0;
            for (int c = 1; c < probs.length; c++) {
                if (probs[c] > probs[predIdx]) {
                    predIdx = c;
                }
            }
            String predicted = model.getLabel(predIdx);
            stats.record(s.getLanguage(), predicted, entropy);
        }
        return stats;
    }

    // ---- Helpers ----

    /**
     * Check if a language is a member of any confusable group.
     */
    private static boolean isInConfusableGroup(String lang) {
        for (String[] group : CompareDetectors.CONFUSABLE_GROUPS) {
            for (String member : group) {
                if (member.equals(lang)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String fmtAcc(
            CompareDetectors.EvalResult r, boolean group) {
        if (r == null || r.total == 0) {
            return "N/A";
        }
        int num = group ? r.correctGroup : r.correct;
        return String.format(Locale.US,
                "%.2f%%", 100.0 * num / r.total);
    }

    private static double throughput(
            CompareDetectors.EvalResult r) {
        if (r == null || r.elapsedMs <= 0) {
            return 0;
        }
        return r.total / (r.elapsedMs / 1000.0);
    }

    private static double pct(long num, long denom) {
        return denom > 0 ? 100.0 * num / denom : 0.0;
    }

    private static String formatConfusableGroups() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Groups: ");
        for (int i = 0;
             i < CompareDetectors.CONFUSABLE_GROUPS.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{").append(String.join("/",
                    CompareDetectors.CONFUSABLE_GROUPS[i]))
                    .append("}");
        }
        sb.append("\n");
        return sb.toString();
    }

    /** Force GC and return approximate used heap in bytes. */
    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            rt.gc();
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return rt.totalMemory() - rt.freeMemory();
    }
}
