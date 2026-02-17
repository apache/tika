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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Compares the built-in TikaLanguageDetector (bigram model) against
 * the OpenNLP-based language detector on a test split.
 * <p>
 * Results are broken out by text length:
 * <ul>
 *   <li><b>short</b> — sentences with &le; {@value #SHORT_CHAR_LIMIT} characters</li>
 *   <li><b>full</b>  — sentences with &gt; {@value #SHORT_CHAR_LIMIT} characters</li>
 *   <li><b>all</b>   — combined</li>
 * </ul>
 * Each bucket reports accuracy, total time, and throughput (sentences/sec).
 * Approximate heap usage for each model is measured around model loading.
 * <p>
 * Usage: {@code CompareDetectors <testSplitFile> <bigramModelFile> [outputReport]}
 * <p>
 * The bigram model is loaded directly from a file so that any model
 * (including one trained on a subset of languages) can be evaluated.
 * OpenNLP is loaded via reflection so that the dependency is optional.
 */
public class CompareDetectors {

    /** Sentences with at most this many characters are considered "short". */
    static final int SHORT_CHAR_LIMIT = 50;

    /** Warm-up iterations before timing to stabilise JIT. */
    private static final int WARMUP_ITERS = 200;

    /**
     * Confusable language groups — languages within the same group are nearly
     * indistinguishable by character bigrams alone. "Group accuracy" counts a
     * prediction as correct if it falls within the same group as the truth.
     */
    static final String[][] CONFUSABLE_GROUPS = {
            {"nob", "nno", "nor", "dan"},       // Scandinavian + Norwegian variants
            {"hrv", "srp", "bos", "hbs"},       // South Slavic + Serbo-Croatian
            {"msa", "zlm", "zsm", "ind"},       // Malay / Indonesian
            {"pes", "prs", "fas"},               // Persian / Dari
            {"zho", "cmn", "wuu", "yue"},        // Chinese varieties
            {"aze", "azj"},                      // Azerbaijani
            {"ekk", "est"},                      // Estonian
            {"lvs", "lav"},                      // Latvian
            {"plt", "mlg"},                      // Malagasy
            {"khk", "mon"},                      // Mongolian
            {"ydd", "yid"},                      // Yiddish
            {"sme", "smi"},                      // Sami
            {"sqi", "als"},                      // Albanian / Tosk Albanian
            {"tat", "bak"},                      // Tatar / Bashkir
            {"ita", "vec"},                      // Italian / Venetian
            {"spa", "arg", "ast"},               // Spanish / Aragonese / Asturian
    };

    /**
     * Maps each language code to the set of codes in its confusable group
     * (including itself). Languages not in any group map to a singleton set.
     */
    private static final Map<String, Set<String>> CONFUSABLE_MAP = buildConfusableMap();

    private static Map<String, Set<String>> buildConfusableMap() {
        Map<String, Set<String>> map = new HashMap<>();
        for (String[] group : CONFUSABLE_GROUPS) {
            Set<String> groupSet = new HashSet<>(Arrays.asList(group));
            for (String lang : group) {
                map.put(lang, groupSet);
            }
        }
        return map;
    }

    /**
     * Returns true if the predicted language is in the same confusable group
     * as the true language, or if they are an exact match.
     */
    static boolean isGroupMatch(String truth, String predicted) {
        if (truth.equals(predicted)) {
            return true;
        }
        Set<String> group = CONFUSABLE_MAP.get(truth);
        return group != null && group.contains(predicted);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: CompareDetectors <testSplitFile> <bigramModelFile>"
                            + " [outputReport] [threads]");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);
        Path modelFile = Paths.get(args[1]);
        Path reportFile = args.length > 2 && !args[2].matches("\\d+")
                ? Paths.get(args[2]) : null;
        int numThreads = args.length > 3 ? Integer.parseInt(args[3])
                : (args.length > 2 && args[2].matches("\\d+") ? Integer.parseInt(args[2])
                : Runtime.getRuntime().availableProcessors());
        System.out.println("Evaluation threads: " + numThreads);

        // ---- Load test data (raw text, not preprocessed) and split by length ----
        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> allData = TrainLanguageModel.readPreprocessedFile(testFile);
        // File format is the same (lang\ttext) whether preprocessed or raw
        List<LabeledSentence> shortData = new ArrayList<>();
        List<LabeledSentence> fullData = new ArrayList<>();
        for (LabeledSentence s : allData) {
            if (s.getText().length() <= SHORT_CHAR_LIMIT) {
                shortData.add(s);
            } else {
                fullData.add(s);
            }
        }
        System.out.printf(Locale.US,
                "Test sentences: %,d total  (%,d short <= %d chars, %,d full)%n",
                allData.size(), shortData.size(), SHORT_CHAR_LIMIT, fullData.size());

        // ---- Load bigram model ----
        System.out.println("\nLoading bigram model: " + modelFile);
        long heapBefore = usedHeap();
        CharSoupModel bigramModel;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(modelFile))) {
            bigramModel = CharSoupModel.load(is);
        }
        FeatureExtractor extractor = bigramModel.createExtractor();
        long bigramHeapBytes = usedHeap() - heapBefore;
        System.out.printf(Locale.US, "  Bigram model: %d classes, %d buckets, ~%.1f MB heap%n",
                bigramModel.getNumClasses(), bigramModel.getNumBuckets(),
                bigramHeapBytes / (1024.0 * 1024.0));

        // ---- Load OpenNLP detectors (one per thread, since LanguageDetector is stateful) ----
        System.out.println("Loading OpenNLP detector(s)...");
        heapBefore = usedHeap();
        String opennlpClassName = "org.apache.tika.langdetect.opennlp.OpenNLPDetector";
        List<LanguageDetector> opennlpPool = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            LanguageDetector d = loadDetector(opennlpClassName);
            if (d == null) {
                break;
            }
            opennlpPool.add(d);
        }
        LanguageDetector opennlpDetector = opennlpPool.isEmpty() ? null : opennlpPool.get(0);
        long opennlpHeapBytes = opennlpDetector != null ? usedHeap() - heapBefore : 0;
        if (opennlpDetector != null) {
            System.out.printf(Locale.US, "  OpenNLP model: %d instance(s), ~%.1f MB heap%n",
                    opennlpPool.size(), opennlpHeapBytes / (1024.0 * 1024.0));
        }

        // ---- Warm up JIT ----
        System.out.println("\nWarming up (" + WARMUP_ITERS + " iterations)...");
        for (int i = 0; i < WARMUP_ITERS && i < allData.size(); i++) {
            String text = allData.get(i).getText();
            bigramModel.predict(extractor.extract(text));
            if (opennlpDetector != null) {
                opennlpDetector.reset();
                opennlpDetector.addText(text);
                opennlpDetector.detectAll();
            }
        }

        // ---- Build shared-language subset (languages both models support) ----
        Set<String> bigramLangs = new HashSet<>(Arrays.asList(bigramModel.getLabels()));
        Set<String> sharedLangs = new HashSet<>();
        if (opennlpDetector != null) {
            for (String lang : bigramLangs) {
                if (opennlpDetector.hasModel(lang)) {
                    sharedLangs.add(lang);
                }
            }
        }
        System.out.printf(Locale.US, "\nShared languages: %d (of %d bigram, OpenNLP hasModel)%n",
                sharedLangs.size(), bigramLangs.size());

        List<LabeledSentence> sharedAll = filterByLangs(allData, sharedLangs);
        List<LabeledSentence> sharedShort = filterByLangs(shortData, sharedLangs);
        List<LabeledSentence> sharedFull = filterByLangs(fullData, sharedLangs);
        System.out.printf(Locale.US,
                "Shared-language sentences: %,d total  (%,d short, %,d full)%n",
                sharedAll.size(), sharedShort.size(), sharedFull.size());

        // ---- Evaluate ----
        System.out.println("\nEvaluating (all languages)...");
        EvalResult bigramAll = evaluateBigramParallel(
                bigramModel, extractor, allData, "bigram-all", numThreads);
        EvalResult bigramShort = evaluateBigramParallel(
                bigramModel, extractor, shortData, "bigram-short", numThreads);
        EvalResult bigramFull = evaluateBigramParallel(
                bigramModel, extractor, fullData, "bigram-full", numThreads);

        EvalResult opennlpAll = evaluateOpenNLPParallel(
                opennlpPool, allData, "opennlp-all");
        EvalResult opennlpShort = evaluateOpenNLPParallel(
                opennlpPool, shortData, "opennlp-short");
        EvalResult opennlpFull = evaluateOpenNLPParallel(
                opennlpPool, fullData, "opennlp-full");

        System.out.println("Evaluating (shared languages only)...");
        EvalResult bigramSharedAll = evaluateBigramParallel(
                bigramModel, extractor, sharedAll, "bigram-shared-all", numThreads);
        EvalResult bigramSharedShort = evaluateBigramParallel(
                bigramModel, extractor, sharedShort, "bigram-shared-short", numThreads);
        EvalResult bigramSharedFull = evaluateBigramParallel(
                bigramModel, extractor, sharedFull, "bigram-shared-full", numThreads);

        EvalResult opennlpSharedAll = evaluateOpenNLPParallel(
                opennlpPool, sharedAll, "opennlp-shared-all");
        EvalResult opennlpSharedShort = evaluateOpenNLPParallel(
                opennlpPool, sharedShort, "opennlp-shared-short");
        EvalResult opennlpSharedFull = evaluateOpenNLPParallel(
                opennlpPool, sharedFull, "opennlp-shared-full");

        // ---- Build report ----
        String report = buildReport(
                bigramAll, bigramShort, bigramFull, bigramHeapBytes,
                opennlpAll, opennlpShort, opennlpFull, opennlpHeapBytes,
                bigramSharedAll, bigramSharedShort, bigramSharedFull,
                opennlpSharedAll, opennlpSharedShort, opennlpSharedFull,
                allData.size(), shortData.size(), fullData.size(),
                sharedAll.size(), sharedLangs.size());
        System.out.println(report);

        if (reportFile != null) {
            if (reportFile.getParent() != null) {
                Files.createDirectories(reportFile.getParent());
            }
            try (BufferedWriter w = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                w.write(report);
            }
            System.out.println("Report written to: " + reportFile);
        }
    }

    // ---- Parallel bigram evaluation ----

    /**
     * Evaluate bigram model in parallel by partitioning data across threads.
     * CharSoupModel is thread-safe (read-only weights); each thread uses its own
     * FeatureExtractor (they allocate thread-local int[] arrays).
     */
    static EvalResult evaluateBigramParallel(CharSoupModel model, FeatureExtractor extractor,
                                             List<LabeledSentence> data, String name,
                                             int numThreads) throws Exception {
        if (data.isEmpty()) {
            return new EvalResult(name);
        }
        if (numThreads <= 1) {
            return evaluateBigramChunk(model, extractor, data, name);
        }

        List<List<LabeledSentence>> chunks = partition(data, numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<EvalResult>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (int i = 0; i < chunks.size(); i++) {
                final List<LabeledSentence> chunk = chunks.get(i);
                final String chunkName = name + "-t" + i;
                // Each thread gets its own extractor instance
                final FeatureExtractor threadExtractor = model.createExtractor();
                futures.add(pool.submit(
                        () -> evaluateBigramChunk(model, threadExtractor, chunk, chunkName)));
            }

            // Collect and merge
            EvalResult merged = new EvalResult(name);
            merged.perLang = new TreeMap<>();
            for (Future<EvalResult> f : futures) {
                EvalResult partial = f.get();
                merged.correct += partial.correct;
                merged.correctGroup += partial.correctGroup;
                merged.total += partial.total;
                // Sum CPU-time for phase breakdown
                merged.preprocessMs += partial.preprocessMs;
                merged.extractMs += partial.extractMs;
                merged.predictMs += partial.predictMs;
                mergePerLang(merged.perLang, partial.perLang);
            }
            // Wall-clock time for throughput
            merged.elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
            return merged;
        } finally {
            pool.shutdown();
        }
    }

    /** Single-threaded bigram evaluation on a chunk of data. */
    static EvalResult evaluateBigramChunk(CharSoupModel model, FeatureExtractor extractor,
                                          List<LabeledSentence> data, String name) {
        EvalResult result = new EvalResult(name);
        if (data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new TreeMap<>();
        int correct = 0;
        int correctGroup = 0;

        long preprocessNs = 0;
        long extractNs = 0;
        long predictNs = 0;
        long t0;

        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            t0 = System.nanoTime();
            String cleaned = CharSoupFeatureExtractor.preprocess(s.getText());
            preprocessNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            int[] features = extractor.extractFromPreprocessed(cleaned);
            extractNs += System.nanoTime() - t0;

            t0 = System.nanoTime();
            float[] probs = model.predict(features);
            predictNs += System.nanoTime() - t0;

            int predicted = argmax(probs);
            String predLabel = model.getLabel(predicted);

            String truth = s.getLanguage();
            int[] counts = perLang.computeIfAbsent(truth, k -> new int[3]);
            counts[1]++;
            if (predLabel.equals(truth)) {
                counts[0]++;
                correct++;
                counts[2]++;
                correctGroup++;
            } else if (isGroupMatch(truth, predLabel)) {
                counts[2]++;
                correctGroup++;
            }
        }
        long elapsedNs = System.nanoTime() - startNs;

        result.correct = correct;
        result.correctGroup = correctGroup;
        result.total = data.size();
        result.elapsedMs = elapsedNs / 1_000_000;
        result.preprocessMs = preprocessNs / 1_000_000;
        result.extractMs = extractNs / 1_000_000;
        result.predictMs = predictNs / 1_000_000;
        result.perLang = perLang;
        return result;
    }

    // ---- Parallel OpenNLP evaluation ----

    /**
     * Evaluate OpenNLP detector in parallel using a pre-created pool of
     * detector instances (one per thread, since LanguageDetector is stateful).
     */
    static EvalResult evaluateOpenNLPParallel(List<LanguageDetector> detectors,
                                              List<LabeledSentence> data,
                                              String name) throws Exception {
        if (detectors == null || detectors.isEmpty() || data.isEmpty()) {
            return new EvalResult(name);
        }
        int numThreads = detectors.size();
        if (numThreads <= 1) {
            return evaluateOpenNLPChunk(detectors.get(0), data, name);
        }

        List<List<LabeledSentence>> chunks = partition(data, numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<EvalResult>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (int i = 0; i < chunks.size(); i++) {
                final List<LabeledSentence> chunk = chunks.get(i);
                final String chunkName = name + "-t" + i;
                final LanguageDetector threadDetector = detectors.get(
                        Math.min(i, detectors.size() - 1));
                futures.add(pool.submit(
                        () -> evaluateOpenNLPChunk(threadDetector, chunk, chunkName)));
            }

            EvalResult merged = new EvalResult(name);
            merged.perLang = new TreeMap<>();
            for (Future<EvalResult> f : futures) {
                EvalResult partial = f.get();
                merged.correct += partial.correct;
                merged.correctGroup += partial.correctGroup;
                merged.total += partial.total;
                mergePerLang(merged.perLang, partial.perLang);
            }
            merged.elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
            return merged;
        } finally {
            pool.shutdown();
        }
    }

    /** Single-threaded OpenNLP evaluation on a chunk of data. */
    static EvalResult evaluateOpenNLPChunk(LanguageDetector detector,
                                           List<LabeledSentence> data, String name) {
        EvalResult result = new EvalResult(name);
        if (detector == null || data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new TreeMap<>();
        int correct = 0;
        int correctGroup = 0;

        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            detector.reset();
            detector.addText(s.getText());
            List<LanguageResult> results = detector.detectAll();
            String predicted = results.isEmpty() ? "unk" : results.get(0).getLanguage();

            String truth = s.getLanguage();
            int[] counts = perLang.computeIfAbsent(truth, k -> new int[3]);
            counts[1]++;
            if (predicted.equals(truth)) {
                counts[0]++;
                correct++;
                counts[2]++;
                correctGroup++;
            } else if (isGroupMatch(truth, predicted)) {
                counts[2]++;
                correctGroup++;
            }
        }
        long elapsedNs = System.nanoTime() - startNs;

        result.correct = correct;
        result.correctGroup = correctGroup;
        result.total = data.size();
        result.elapsedMs = elapsedNs / 1_000_000;
        result.perLang = perLang;
        return result;
    }

    // ---- Partitioning and merging helpers ----

    /** Split a list into approximately equal-sized sublists. */
    static <T> List<List<T>> partition(List<T> list, int n) {
        List<List<T>> parts = new ArrayList<>();
        int size = list.size();
        int chunkSize = (size + n - 1) / n;
        for (int i = 0; i < size; i += chunkSize) {
            parts.add(list.subList(i, Math.min(i + chunkSize, size)));
        }
        return parts;
    }

    /** Merge per-language counts from a partial result into an accumulator. */
    private static void mergePerLang(Map<String, int[]> target,
                                     Map<String, int[]> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<String, int[]> e : source.entrySet()) {
            int[] src = e.getValue();
            int[] dst = target.computeIfAbsent(e.getKey(), k -> new int[3]);
            dst[0] += src[0];
            dst[1] += src[1];
            dst[2] += src[2];
        }
    }

    // ---- Report ----

    static String buildReport(EvalResult bigramAll, EvalResult bigramShort, EvalResult bigramFull,
                              long bigramHeap,
                              EvalResult opennlpAll, EvalResult opennlpShort,
                              EvalResult opennlpFull,
                              long opennlpHeap,
                              EvalResult bigramSharedAll, EvalResult bigramSharedShort,
                              EvalResult bigramSharedFull,
                              EvalResult opennlpSharedAll, EvalResult opennlpSharedShort,
                              EvalResult opennlpSharedFull,
                              int totalCount, int shortCount, int fullCount,
                              int sharedCount, int sharedLangCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Language Detection Comparison Report ===\n\n");
        sb.append(String.format(Locale.US,
                "Test sentences: %,d total  (%,d short <= %d chars, %,d full)%n",
                totalCount, shortCount, SHORT_CHAR_LIMIT, fullCount));
        sb.append(String.format(Locale.US,
                "Shared languages: %d  (%,d sentences)%n%n",
                sharedLangCount, sharedCount));

        // Model sizes
        sb.append("Model heap (approx):\n");
        sb.append(String.format(Locale.US, "  Bigram:  ~%.1f MB%n",
                bigramHeap / (1024.0 * 1024.0)));
        sb.append(String.format(Locale.US, "  OpenNLP: ~%.1f MB%n%n",
                opennlpHeap / (1024.0 * 1024.0)));

        // Overall summary table — strict accuracy
        sb.append("Strict accuracy (exact language match):\n");
        sb.append(String.format(Locale.US,
                "%-14s  %10s  %10s  %10s  %12s  %12s%n",
                "Detector", "All", "Short", "Full", "Time(ms)", "Sent/sec"));
        sb.append("-".repeat(82)).append("\n");
        sb.append(formatSummaryRow("Bigram", bigramAll, bigramShort, bigramFull, false));
        sb.append(formatSummaryRow("OpenNLP", opennlpAll, opennlpShort, opennlpFull, false));
        sb.append("\n");

        // Overall summary table — group accuracy
        sb.append("Group accuracy (confusable languages counted as correct):\n");
        sb.append(formatConfusableGroups());
        sb.append(String.format(Locale.US,
                "%-14s  %10s  %10s  %10s%n",
                "Detector", "All", "Short", "Full"));
        sb.append("-".repeat(50)).append("\n");
        sb.append(formatSummaryRow("Bigram", bigramAll, bigramShort, bigramFull, true));
        sb.append(formatSummaryRow("OpenNLP", opennlpAll, opennlpShort, opennlpFull, true));
        sb.append("\n");

        // Shared-languages-only accuracy (apples-to-apples)
        sb.append(String.format(Locale.US,
                "Shared-language accuracy (%d languages both models support):%n",
                sharedLangCount));
        sb.append(String.format(Locale.US,
                "%-14s  %10s  %10s  %10s  %12s  %12s%n",
                "Detector", "All", "Short", "Full", "Time(ms)", "Sent/sec"));
        sb.append("-".repeat(82)).append("\n");
        sb.append(formatSummaryRow("Bigram",
                bigramSharedAll, bigramSharedShort, bigramSharedFull, false));
        sb.append(formatSummaryRow("OpenNLP",
                opennlpSharedAll, opennlpSharedShort, opennlpSharedFull, false));
        sb.append("\n");

        // Bigram phase breakdown (CPU-time summed across threads)
        long cpuTotal = bigramAll.preprocessMs + bigramAll.extractMs + bigramAll.predictMs;
        sb.append("Bigram timing breakdown (all sentences, CPU-time summed across threads):\n");
        sb.append(String.format(Locale.US,
                "  Preprocess (NFC/URL/truncate): %,d ms (%.0f%%)%n",
                bigramAll.preprocessMs,
                100.0 * bigramAll.preprocessMs / Math.max(1, cpuTotal)));
        sb.append(String.format(Locale.US,
                "  Feature extraction (bigrams):  %,d ms (%.0f%%)%n",
                bigramAll.extractMs,
                100.0 * bigramAll.extractMs / Math.max(1, cpuTotal)));
        sb.append(String.format(Locale.US,
                "  Model prediction (softmax):    %,d ms (%.0f%%)%n",
                bigramAll.predictMs,
                100.0 * bigramAll.predictMs / Math.max(1, cpuTotal)));
        sb.append(String.format(Locale.US,
                "  CPU total:                     %,d ms%n",
                cpuTotal));
        sb.append(String.format(Locale.US,
                "  Wall-clock total:              %,d ms%n%n",
                bigramAll.elapsedMs));

        // Per-language detail (all sentences) — strict + group for confusable langs
        sb.append("Per-language accuracy (all sentences):\n");
        sb.append(String.format(Locale.US,
                "%-12s  %8s %8s %8s  %8s %8s %8s%n",
                "Language", "Bigram", "Bi-Grp%", "Bigram%",
                "OpenNLP", "ON-Grp%", "OpenNLP%"));
        sb.append("-".repeat(82)).append("\n");

        // Merge per-lang: [0]=bigram strict, [1]=bigram total, [2]=bigram group,
        //                  [3]=opennlp strict, [4]=opennlp total, [5]=opennlp group
        Map<String, int[]> merged = new TreeMap<>();
        if (bigramAll.perLang != null) {
            for (var e : bigramAll.perLang.entrySet()) {
                int[] row = merged.computeIfAbsent(e.getKey(), k -> new int[6]);
                row[0] = e.getValue()[0];
                row[1] = e.getValue()[1];
                row[2] = e.getValue()[2];
            }
        }
        if (opennlpAll.perLang != null) {
            for (var e : opennlpAll.perLang.entrySet()) {
                int[] row = merged.computeIfAbsent(e.getKey(), k -> new int[6]);
                row[3] = e.getValue()[0];
                row[4] = e.getValue()[1];
                row[5] = e.getValue()[2];
            }
        }
        for (var e : merged.entrySet()) {
            int[] c = e.getValue();
            String lang = e.getKey();
            boolean confusable = CONFUSABLE_MAP.containsKey(lang);
            String bStrict = c[1] > 0 ?
                    String.format(Locale.US, "%6.1f%%", 100.0 * c[0] / c[1]) : "   N/A";
            String bGroup = confusable && c[1] > 0 ?
                    String.format(Locale.US, "%6.1f%%", 100.0 * c[2] / c[1]) : "      ";
            String oStrict = c[4] > 0 ?
                    String.format(Locale.US, "%6.1f%%", 100.0 * c[3] / c[4]) : "   N/A";
            String oGroup = confusable && c[4] > 0 ?
                    String.format(Locale.US, "%6.1f%%", 100.0 * c[5] / c[4]) : "      ";
            String marker = confusable ? " *" : "";
            sb.append(String.format(Locale.US,
                    "%-12s  %4d/%-4d %s %s  %4d/%-4d %s %s%s%n",
                    lang, c[0], c[1], bGroup, bStrict,
                    c[3], c[4], oGroup, oStrict, marker));
        }
        sb.append("\n* = member of a confusable group; Grp% = group accuracy\n");

        return sb.toString();
    }

    private static String formatSummaryRow(String name,
                                           EvalResult all, EvalResult shortR,
                                           EvalResult fullR, boolean group) {
        if (group) {
            return String.format(Locale.US,
                    "%-14s  %9s  %9s  %9s%n",
                    name,
                    fmtAcc(all, true), fmtAcc(shortR, true), fmtAcc(fullR, true));
        }
        return String.format(Locale.US,
                "%-14s  %9s  %9s  %9s  %,10d ms  %,10.0f%n",
                name,
                fmtAcc(all, false), fmtAcc(shortR, false), fmtAcc(fullR, false),
                all.elapsedMs,
                all.total > 0 ? all.total / (all.elapsedMs / 1000.0) : 0.0);
    }

    private static String formatConfusableGroups() {
        StringBuilder sb = new StringBuilder();
        sb.append("  Groups: ");
        for (int i = 0; i < CONFUSABLE_GROUPS.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{").append(String.join("/", CONFUSABLE_GROUPS[i])).append("}");
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String fmtAcc(EvalResult r, boolean group) {
        if (r.total == 0) {
            return "N/A";
        }
        int num = group ? r.correctGroup : r.correct;
        return String.format(Locale.US, "%.2f%%", 100.0 * num / r.total);
    }

    // ---- Helpers ----

    static List<LabeledSentence> filterByLangs(List<LabeledSentence> data,
                                                Set<String> langs) {
        List<LabeledSentence> filtered = new ArrayList<>();
        for (LabeledSentence s : data) {
            if (langs.contains(s.getLanguage())) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    static LanguageDetector loadDetector(String className) {
        try {
            Class<?> clz = Class.forName(className);
            LanguageDetector detector = (LanguageDetector) clz
                    .getDeclaredConstructor().newInstance();
            detector.loadModels();
            System.out.println("  Loaded: " + className);
            return detector;
        } catch (Exception e) {
            System.err.println("  WARN: Could not load " + className + ": " + e.getMessage());
            return null;
        }
    }

    private static int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[best]) {
                best = i;
            }
        }
        return best;
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

    static class EvalResult {
        String name;
        int correct;
        int correctGroup; // correct when allowing confusable group match
        int total;
        long elapsedMs;
        /** Bigram-only: time spent in preprocess (NFC, URL strip, truncate). */
        long preprocessMs;
        /** Bigram-only: time spent in bigram extraction + hashing. */
        long extractMs;
        /** Bigram-only: time spent in model prediction (softmax). */
        long predictMs;
        Map<String, int[]> perLang; // [strict correct, total, group correct]

        EvalResult(String name) {
            this.name = name;
        }
    }
}
