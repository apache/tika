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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.tika.langdetect.charsoup.CharSoupDetectorConfig;
import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;
import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.ConfusableGroups;
import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Compares CharSoup against OpenNLP, Lingua, and Optimaize on a test split.
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
 * Usage: {@code CompareDetectors <testSplitFile> <charSoupModelFile> [outputReport]}
 * <p>
 * The CharSoup model is loaded directly from a file so that any model
 * (including one trained on a subset of languages) can be evaluated.
 * All comparison detectors are loaded via reflection so their dependencies
 * are optional — any that are absent from the classpath are silently skipped.
 */
public class CompareDetectors {

    /** Text is truncated to each of these lengths before evaluation. */
    static final int[] EVAL_LENGTHS = {20, 50, 100, 150, 200, 500, Integer.MAX_VALUE};

    /**
     * Flores-200 language+script codes that represent a secondary or alternative script
     * for a language that also appears under a different script in Flores.
     * <p>
     * These codes are <em>not</em> normalized to bare ISO 639-3 — they are kept as
     * {@code xxx_Yyyy} so they appear as distinct evaluation classes in the report.
     * A model trained only on the primary script will score 0 on these, which is the
     * honest result (the model doesn't cover that script variant).
     * <p>
     * Rationale per entry:
     * <ul>
     *   <li>{@code ace_Arab} — Acehnese in Jawi; MADLAD {@code ace} is Latin-script</li>
     *   <li>{@code arb_Latn} — Romanized Arabic; distinct from Arabic-script {@code arb}</li>
     *   <li>{@code bjn_Arab} — Banjar in Jawi; digital Banjar is primarily Latin-script</li>
     *   <li>{@code kas_Deva} — Kashmiri in Devanagari; primary written form is Nastaliq</li>
     *   <li>{@code knc_Latn} — Central Kanuri in Latin; traditional script is Arabic</li>
     *   <li>{@code min_Arab} — Minangkabau in Jawi; MADLAD {@code min} is Latin-script</li>
     *   <li>{@code taq_Tfng} — Tamasheq in Tifinagh; digital text predominantly Latin</li>
     * </ul>
     * {@code zho_Hans} and {@code zho_Hant} are both native Chinese character sets and
     * both normalize to {@code zho}.
     */
    static final Set<String> FLORES_KEEP_SCRIPT_SUFFIX = Set.of(
            "ace_Arab",
            "arb_Latn",
            "bjn_Arab",
            "kas_Deva",
            "knc_Latn",
            "min_Arab",
            "taq_Tfng"
    );

    /** Warm-up iterations before timing to stabilise JIT. */
    private static final int WARMUP_ITERS = 200;

    /**
     * Confusable language groups — languages within the same group are nearly
     * indistinguishable by character bigrams alone. "Group accuracy" counts a
     * prediction as correct if it falls within the same group as the truth.
     */
    static final String[][] CONFUSABLE_GROUPS = ConfusableGroups.load();

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
        if (args.length < 1) {
            System.err.println(
                    "Usage: CompareDetectors <testSplitFile>"
                            + " [--strategy STANDARD|SHORT_TEXT|AUTOMATIC]"
                            + " [outputReport] [threads]");
            System.err.println("  Legacy: CompareDetectors <testSplitFile> <charSoupModelFile>"
                            + " [outputReport] [threads]");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);

        // Parse --strategy flag (new mode) vs positional model file (legacy mode)
        CharSoupLanguageDetector.Strategy strategy = CharSoupLanguageDetector.Strategy.STANDARD;
        Path modelFile = null;
        Path reportFile = null;
        int numThreads = Runtime.getRuntime().availableProcessors();

        List<String> remaining = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        for (int i = 0; i < remaining.size(); i++) {
            if ("--strategy".equals(remaining.get(i)) && i + 1 < remaining.size()) {
                strategy = CharSoupLanguageDetector.Strategy.valueOf(
                        remaining.get(i + 1).toUpperCase(Locale.ROOT));
                remaining.remove(i + 1);
                remaining.remove(i);
                i--;
            }
        }
        // Remaining positional args: [modelFile] [reportFile] [threads]
        if (!remaining.isEmpty() && !remaining.get(0).matches("\\d+")
                && !remaining.get(0).endsWith(".log") && !remaining.get(0).endsWith(".txt")
                && Paths.get(remaining.get(0)).toFile().exists()) {
            modelFile = Paths.get(remaining.remove(0));
        }
        if (!remaining.isEmpty() && !remaining.get(0).matches("\\d+")) {
            reportFile = Paths.get(remaining.remove(0));
        }
        if (!remaining.isEmpty()) {
            numThreads = Integer.parseInt(remaining.get(0));
        }

        System.out.println("CharSoup strategy: " + strategy);
        System.out.println("Evaluation threads: " + numThreads);

        // ---- Load test data ----
        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> allData = TrainLanguageModel.readPreprocessedFile(testFile);
        // Normalize Flores-200 xxx_Yyyy codes (e.g. zho_Hans → zho, ace_Latn → ace).
        // Secondary-script variants (romanized or minority-script forms) are dropped so
        // they don't pollute evaluation of models trained on the primary script.
        boolean floresMode = allData.stream()
                .anyMatch(s -> s.getLanguage().contains("_"));
        if (floresMode) {
            System.out.println("  Flores-200 mode: normalizing xxx_Yyyy → xxx codes");
            System.out.println("  (multi-script variants kept as xxx_Yyyy separate classes)");
            List<LabeledSentence> normalized = new ArrayList<>(allData.size());
            for (LabeledSentence s : allData) {
                String raw = s.getLanguage();
                // Keep secondary-script codes as-is (e.g. arb_Latn stays arb_Latn)
                // so they appear as distinct classes in the per-language report.
                String lang = FLORES_KEEP_SCRIPT_SUFFIX.contains(raw)
                        ? raw : normalizeLang(raw);
                normalized.add(new LabeledSentence(lang, s.getText()));
            }
            allData = normalized;
        }
        System.out.printf(Locale.US, "Test sentences: %,d%n", allData.size());

        // ---- Resolve CharSoup supported-language set ----
        // Evaluation always routes through CharSoupLanguageDetector (production pipeline).
        // The model file, if supplied, is used only to override the supported-language set
        // (legacy behaviour). When --strategy is used, we query the detector directly.
        final Set<String> bigramLangs;
        long bigramHeapBytes;
        if (modelFile != null) {
            System.out.println("\nLoading CharSoup model (for language set): " + modelFile);
            long heapBefore = usedHeap();
            CharSoupModel bigramModel;
            try (InputStream is = new BufferedInputStream(Files.newInputStream(modelFile))) {
                bigramModel = CharSoupModel.load(is);
            }
            bigramHeapBytes = usedHeap() - heapBefore;
            System.out.printf(Locale.US,
                    "  CharSoup model: %d classes, %d buckets, ~%.1f MB heap%n",
                    bigramModel.getNumClasses(), bigramModel.getNumBuckets(),
                    bigramHeapBytes / (1024.0 * 1024.0));
            bigramLangs = new HashSet<>(Arrays.asList(bigramModel.getLabels()));
        } else {
            bigramLangs = CharSoupLanguageDetector.getSupportedLanguages(strategy);
            System.out.printf(Locale.US,
                    "%nCharSoup supported languages (%s strategy): %d%n",
                    strategy, bigramLangs.size());
            bigramHeapBytes = 0L; // model already loaded in static initializer
        }
        System.out.println("  Evaluation routes through CharSoupLanguageDetector"
                + " (script gate + confusable group collapse).");

        // ---- Load OpenNLP detectors (one per thread; stateful) ----
        System.out.println("Loading OpenNLP detector(s)...");
        long heapBefore = usedHeap();
        List<LanguageDetector> opennlpPool = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            LanguageDetector d = loadDetector(
                    "org.apache.tika.langdetect.opennlp.OpenNLPDetector");
            if (d == null) {
                break;
            }
            opennlpPool.add(d);
        }
        LanguageDetector opennlpDetector = opennlpPool.isEmpty() ? null : opennlpPool.get(0);
        long opennlpHeapBytes = opennlpDetector != null ? usedHeap() - heapBefore : 0;
        if (opennlpDetector != null) {
            System.out.printf(Locale.US, "  OpenNLP: %d instance(s), ~%.1f MB heap%n",
                    opennlpPool.size(), opennlpHeapBytes / (1024.0 * 1024.0));
        }

        // ---- Load Lingua detector (thread-safe; single instance) ----
        System.out.println("Loading Lingua detector (low accuracy mode)...");
        heapBefore = usedHeap();
        LinguaWrapper lingua = LinguaWrapper.load();
        long linguaHeapBytes = lingua != null ? usedHeap() - heapBefore : 0;
        Set<String> linguaLangs = lingua != null
                ? lingua.supportedLangs : Collections.emptySet();

        // ---- Load Optimaize detectors (one per thread; stateful) ----
        System.out.println("Loading Optimaize detector(s)...");
        heapBefore = usedHeap();
        List<LanguageDetector> optimaizePool = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            LanguageDetector d = loadDetector(
                    "org.apache.tika.langdetect.optimaize.OptimaizeLangDetector");
            if (d == null) {
                break;
            }
            optimaizePool.add(d);
        }
        LanguageDetector optimaizeDetector = optimaizePool.isEmpty() ? null : optimaizePool.get(0);
        long optimaizeHeapBytes = optimaizeDetector != null ? usedHeap() - heapBefore : 0;
        if (optimaizeDetector != null) {
            System.out.printf(Locale.US, "  Optimaize: %d instance(s), ~%.1f MB heap%n",
                    optimaizePool.size(), optimaizeHeapBytes / (1024.0 * 1024.0));
        }

        // ---- Warm up JIT ----
        System.out.println("\nWarming up (" + WARMUP_ITERS + " iterations)...");
        CharSoupDetectorConfig warmupCfg = CharSoupDetectorConfig.fromMap(
                java.util.Map.of("strategy", strategy.name()));
        CharSoupLanguageDetector warmupDetector = new CharSoupLanguageDetector(warmupCfg);
        for (int i = 0; i < WARMUP_ITERS && i < allData.size(); i++) {
            String text = allData.get(i).getText();
            warmupDetector.reset();
            warmupDetector.addText(text.toCharArray(), 0, text.length());
            warmupDetector.detectAll();
            if (opennlpDetector != null) {
                opennlpDetector.reset();
                opennlpDetector.addText(text);
                opennlpDetector.detectAll();
            }
            if (lingua != null) {
                lingua.detect(text);
            }
            if (optimaizeDetector != null) {
                optimaizeDetector.reset();
                optimaizeDetector.addText(text);
                optimaizeDetector.detectAll();
            }
        }

        // ---- Build per-detector supported-language sets ----
        // Used to gate evaluation: sentences from unsupported languages are skipped entirely
        // so detectors are not penalised for languages they do not claim to cover.
        Set<String> allTestLangs = new HashSet<>();
        for (LabeledSentence s : allData) {
            allTestLangs.add(s.getLanguage());
        }

        Set<String> opennlpAllLangs = new HashSet<>();
        if (opennlpDetector != null) {
            for (String lang : allTestLangs) {
                if (opennlpDetector.hasModel(lang)) {
                    opennlpAllLangs.add(lang);
                }
            }
        }

        // Optimaize uses BCP-47 ISO 639-1 codes internally; translate to ISO 639-3
        // so we can intersect with our ISO 639-3 test set.
        Set<String> optimaizeAllLangs = optimaizeSupportedIso3(optimaizeDetector);

        Set<String> opennlpSharedLangs = new HashSet<>(bigramLangs);
        opennlpSharedLangs.retainAll(opennlpAllLangs);

        Set<String> linguaSharedLangs = new HashSet<>(bigramLangs);
        linguaSharedLangs.retainAll(linguaLangs);

        Set<String> optimaizeSharedLangs = new HashSet<>(bigramLangs);
        optimaizeSharedLangs.retainAll(optimaizeAllLangs);

        List<LabeledSentence> opennlpSub   = filterByLangs(allData, opennlpSharedLangs);
        List<LabeledSentence> linguaSub    = filterByLangs(allData, linguaSharedLangs);
        List<LabeledSentence> optimaizeSub = filterByLangs(allData, optimaizeSharedLangs);

        System.out.printf(Locale.US,
                "\nCharSoup \u2229 OpenNLP:   %d languages, %,d sentences%n",
                opennlpSharedLangs.size(), opennlpSub.size());
        System.out.printf(Locale.US,
                "CharSoup \u2229 Lingua:    %d languages, %,d sentences (Lingua covers %d)%n",
                linguaSharedLangs.size(), linguaSub.size(), linguaLangs.size());
        System.out.printf(Locale.US,
                "CharSoup \u2229 Optimaize: %d languages, %,d sentences%n",
                optimaizeSharedLangs.size(), optimaizeSub.size());

        // ---- Evaluate at each length ----
        List<LengthEval> allEvals        = new ArrayList<>();
        List<LengthEval> opennlpEvals    = new ArrayList<>();
        List<LengthEval> linguaEvals     = new ArrayList<>();
        List<LengthEval> optimaizeEvals  = new ArrayList<>();

        System.out.println();
        for (int maxLen : EVAL_LENGTHS) {
            String tag = maxLen == Integer.MAX_VALUE ? "full" : "@" + maxLen;
            System.out.printf(Locale.US, "Evaluating %-6s ...", tag);
            System.out.flush();

            List<LabeledSentence> tAll = truncate(allData, maxLen);
            List<LabeledSentence> tOnn = truncate(opennlpSub, maxLen);
            List<LabeledSentence> tLin = truncate(linguaSub, maxLen);
            List<LabeledSentence> tOpt = truncate(optimaizeSub, maxLen);

            LengthEval ae = new LengthEval(maxLen);
            ae.bigram    = evaluateBigramParallel(
                    tAll, "bigram-" + tag, numThreads, bigramLangs, strategy);
            ae.opennlp   = evaluateOpenNLPParallel(opennlpPool, tAll, "opennlp-" + tag,
                    opennlpAllLangs);
            ae.lingua    = evaluateLingua(lingua, tAll, "lingua-" + tag, linguaLangs);
            ae.optimaize = evaluateOptimaizeParallel(optimaizePool, tAll, "optimaize-" + tag,
                    optimaizeAllLangs);
            allEvals.add(ae);

            LengthEval oe = new LengthEval(maxLen);
            oe.bigram  = evaluateBigramParallel(tOnn, "bigram-onn-" + tag, numThreads, null, strategy);
            oe.opennlp = evaluateOpenNLPParallel(opennlpPool, tOnn, "opennlp-onn-" + tag);
            opennlpEvals.add(oe);

            LengthEval le = new LengthEval(maxLen);
            le.bigram  = evaluateBigramParallel(tLin, "bigram-lin-" + tag, numThreads, null, strategy);
            le.lingua  = evaluateLingua(lingua, tLin, "lingua-lin-" + tag);
            linguaEvals.add(le);

            LengthEval pe = new LengthEval(maxLen);
            pe.bigram    = evaluateBigramParallel(tOpt, "bigram-opt-" + tag, numThreads, null, strategy);
            pe.optimaize = evaluateOptimaizeParallel(optimaizePool, tOpt, "optimaize-opt-" + tag);
            optimaizeEvals.add(pe);

            System.out.printf(Locale.US,
                    "  charsoup=%s  opennlp=%s  lingua=%s  optimaize=%s%n",
                    fmtPct(ae.bigram), fmtPct(ae.opennlp), fmtPct(ae.lingua),
                    fmtPct(ae.optimaize));
        }

        // ---- Build report ----
        String report = buildReport(
                allEvals, opennlpEvals, linguaEvals, optimaizeEvals,
                bigramHeapBytes, opennlpHeapBytes, linguaHeapBytes, optimaizeHeapBytes,
                allData.size(),
                opennlpSharedLangs.size(), opennlpSub.size(),
                linguaSharedLangs.size(), linguaSub.size(),
                optimaizeSharedLangs.size(), optimaizeSub.size());
        System.out.println("\n" + report);

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

    /**
     * Macro-averaged F1: for each language compute F1 from its precision and recall,
     * then average equally across all languages that appear in the true set.
     * Languages with zero true samples are excluded from the average.
     * Languages with zero predictions get precision=0, recall=0, F1=0.
     * Languages predicted but absent from the truth set are not included —
     * their penalty is already captured as a recall loss on the true language.
     */
    static double computeMacroF1(EvalResult r) {
        if (r == null || r.perLang == null || r.perLang.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        int count = 0;
        for (var e : r.perLang.entrySet()) {
            int tp    = e.getValue()[0];
            int total = e.getValue()[1];
            if (total == 0) {
                continue;
            }
            int predicted = r.predictedCounts != null
                    ? r.predictedCounts.getOrDefault(e.getKey(), 0) : 0;
            double recall    = (double) tp / total;
            double precision = predicted > 0 ? (double) tp / predicted : 0.0;
            double f1 = (precision + recall) > 0
                    ? 2.0 * precision * recall / (precision + recall) : 0.0;
            sum += f1;
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Strict macro-F1 over all FLORES languages: covered languages use their real F1,
     * unsupported languages contribute 0 to the sum and increment the denominator.
     */
    static double computeStrictMacroF1(EvalResult r) {
        if (r == null || r.perLang == null) {
            return 0.0;
        }
        double sum = 0.0;
        int count = r.unsupportedLangCount; // unsupported langs all get F1=0
        for (var e : r.perLang.entrySet()) {
            int tp    = e.getValue()[0];
            int total = e.getValue()[1];
            if (total == 0) {
                continue;
            }
            int predicted = r.predictedCounts != null
                    ? r.predictedCounts.getOrDefault(e.getKey(), 0) : 0;
            double recall    = (double) tp / total;
            double precision = predicted > 0 ? (double) tp / predicted : 0.0;
            double f1 = (precision + recall) > 0
                    ? 2.0 * precision * recall / (precision + recall) : 0.0;
            sum += f1;
            count++;
        }
        return count > 0 ? sum / count : 0.0;
    }

    /** Strict accuracy over all FLORES languages: unsupported sentences count as misses. */
    static double computeStrictAccuracy(EvalResult r) {
        if (r == null) {
            return 0.0;
        }
        int totalAll = r.total + r.unsupportedTotal;
        return totalAll > 0 ? (double) r.correct / totalAll : 0.0;
    }

    private static String fmtF1(EvalResult r) {
        if (r == null || r.total == 0) {
            return "  N/A  ";
        }
        return String.format(Locale.US, "%6.2f%%", 100.0 * computeMacroF1(r));
    }

    private static String fmtPct(EvalResult r) {
        if (r == null || r.total == 0) {
            return "  N/A  ";
        }
        return String.format(Locale.US, "%6.2f%%", 100.0 * r.correct / r.total);
    }

    // ---- Parallel bigram evaluation ----
    // Evaluation routes through CharSoupLanguageDetector (the production pipeline),
    // which applies script gating and confusable group collapse. Each thread gets
    // its own detector instance since CharSoupLanguageDetector is stateful.

    /**
     * Evaluate CharSoup via the full production pipeline in parallel.
     * Each thread gets its own {@link CharSoupLanguageDetector} instance configured
     * with the given strategy.
     *
     * @param supportedLangs if non-null, sentences whose true label is NOT in this set are
     *                       skipped entirely — not counted in accuracy, F1, or confusion.
     */
    static EvalResult evaluateBigramParallel(List<LabeledSentence> data, String name,
                                             int numThreads, Set<String> supportedLangs,
                                             CharSoupLanguageDetector.Strategy strategy)
            throws Exception {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                java.util.Map.of("strategy", strategy.name()));
        if (data.isEmpty()) {
            return new EvalResult(name);
        }
        if (numThreads <= 1) {
            return evaluateBigramChunk(new CharSoupLanguageDetector(cfg), data, name, supportedLangs);
        }

        List<List<LabeledSentence>> chunks = partition(data, numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<EvalResult>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (int i = 0; i < chunks.size(); i++) {
                final List<LabeledSentence> chunk = chunks.get(i);
                final String chunkName = name + "-t" + i;
                final CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);
                futures.add(pool.submit(
                        () -> evaluateBigramChunk(detector, chunk, chunkName, supportedLangs)));
            }

            EvalResult merged = new EvalResult(name);
            merged.perLang = new TreeMap<>();
            merged.predictedCounts = new TreeMap<>();
            merged.confusions = new TreeMap<>();
            for (Future<EvalResult> f : futures) {
                EvalResult partial = f.get();
                merged.correct += partial.correct;
                merged.correctGroup += partial.correctGroup;
                merged.total += partial.total;
                merged.unsupportedTotal += partial.unsupportedTotal;
                mergePerLang(merged.perLang, partial.perLang);
                mergePredicted(merged.predictedCounts, partial.predictedCounts);
                mergeConfusions(merged.confusions, partial.confusions);
            }
            merged.elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
            return merged;
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Single-threaded evaluation via the full CharSoupLanguageDetector production pipeline.
     * The detector applies script gating and confusable group collapse — identical to
     * what end users receive.
     *
     * @param supportedLangs if non-null, sentences whose true label is NOT in this set are
     *                       skipped entirely — they do not contribute to accuracy, macro-F1,
     *                       per-language counts, or confusion tracking.
     */
    static EvalResult evaluateBigramChunk(CharSoupLanguageDetector detector,
                                          List<LabeledSentence> data, String name,
                                          Set<String> supportedLangs) {
        EvalResult result = new EvalResult(name);
        if (data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new TreeMap<>();
        Map<String, Integer> predictedCounts = new TreeMap<>();
        Map<String, Map<String, Integer>> confusions = new TreeMap<>();
        int correct = 0;
        int correctGroup = 0;
        int coveredTotal = 0;
        Set<String> unsupportedLangsSeen = supportedLangs != null ? new HashSet<>() : null;

        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            detector.reset();
            String text = s.getText();
            detector.addText(text.toCharArray(), 0, text.length());
            List<LanguageResult> results = detector.detectAll();
            String predLabel = (results.isEmpty()
                    || results.get(0).getConfidence() == LanguageConfidence.NONE)
                    ? "unk" : results.get(0).getLanguage();
            String truth = s.getLanguage();

            if (supportedLangs != null && !supportedLangs.contains(truth)) {
                result.unsupportedTotal++;
                if (unsupportedLangsSeen != null) unsupportedLangsSeen.add(truth);
                continue;
            }

            coveredTotal++;
            predictedCounts.merge(predLabel, 1, Integer::sum);

            int[] counts = perLang.computeIfAbsent(truth, k -> new int[3]);
            counts[1]++;

            if (predLabel.equals(truth)) {
                counts[0]++;
                correct++;
                counts[2]++;
                correctGroup++;
            } else {
                confusions.computeIfAbsent(truth, k -> new HashMap<>())
                        .merge(predLabel, 1, Integer::sum);
                if (isGroupMatch(truth, predLabel)) {
                    counts[2]++;
                    correctGroup++;
                }
            }
        }

        result.correct = correct;
        result.correctGroup = correctGroup;
        result.total = supportedLangs != null ? coveredTotal : data.size();
        if (unsupportedLangsSeen != null) {
            result.unsupportedLangCount = unsupportedLangsSeen.size();
        }
        result.elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        result.perLang = perLang;
        result.predictedCounts = predictedCounts;
        result.confusions = confusions;
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
        return evaluateOpenNLPParallel(detectors, data, name, null);
    }

    static EvalResult evaluateOpenNLPParallel(List<LanguageDetector> detectors,
                                              List<LabeledSentence> data,
                                              String name,
                                              Set<String> supportedLangs) throws Exception {
        if (detectors == null || detectors.isEmpty() || data.isEmpty()) {
            return new EvalResult(name);
        }
        int numThreads = detectors.size();
        if (numThreads <= 1) {
            return evaluateOpenNLPChunk(detectors.get(0), data, name, supportedLangs);
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
                        () -> evaluateOpenNLPChunk(threadDetector, chunk, chunkName,
                                supportedLangs)));
            }

            EvalResult merged = new EvalResult(name);
            merged.perLang = new TreeMap<>();
            merged.predictedCounts = new TreeMap<>();
            for (Future<EvalResult> f : futures) {
                EvalResult partial = f.get();
                merged.correct += partial.correct;
                merged.correctGroup += partial.correctGroup;
                merged.total += partial.total;
                mergePerLang(merged.perLang, partial.perLang);
                mergePredicted(merged.predictedCounts, partial.predictedCounts);
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
        return evaluateOpenNLPChunk(detector, data, name, null);
    }

    static EvalResult evaluateOpenNLPChunk(LanguageDetector detector,
                                           List<LabeledSentence> data, String name,
                                           Set<String> supportedLangs) {
        EvalResult result = new EvalResult(name);
        if (detector == null || data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new TreeMap<>();
        Map<String, Integer> predictedCounts = new TreeMap<>();
        int correct = 0;
        int correctGroup = 0;
        int coveredTotal = 0;
        Set<String> unsupportedLangsSeen = supportedLangs != null ? new HashSet<>() : null;

        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            detector.reset();
            detector.addText(s.getText());
            List<LanguageResult> results = detector.detectAll();
            String predicted = results.isEmpty() ? "unk" : results.get(0).getLanguage();
            String truth = s.getLanguage();
            if (supportedLangs != null && !supportedLangs.contains(truth)) {
                result.unsupportedTotal++;
                unsupportedLangsSeen.add(truth);
                continue;
            }
            coveredTotal++;
            predictedCounts.merge(predicted, 1, Integer::sum);
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
        result.total = supportedLangs != null ? coveredTotal : data.size();
        if (unsupportedLangsSeen != null) {
            result.unsupportedLangCount = unsupportedLangsSeen.size();
        }
        result.elapsedMs = elapsedNs / 1_000_000;
        result.perLang = perLang;
        result.predictedCounts = predictedCounts;
        return result;
    }

    // ---- Optimaize evaluation ----
    // Optimaize returns BCP-47 ISO 639-1 codes ("en", "fr", "zh-Hans").
    // We translate to ISO 639-3 ("eng", "fra", "zho") via Locale before comparing
    // against our ISO 639-3 test data.

    /**
     * Translate a BCP-47 language tag returned by Optimaize to an ISO 639-3 code.
     * Uses Java's Locale to do the mapping; falls back to the input if the
     * ISO 639-3 code is unavailable or empty.
     */
    static String optimaizePredToIso3(String bcp47) {
        if (bcp47 == null || bcp47.isEmpty() || "unk".equals(bcp47)) {
            return "unk";
        }
        try {
            String iso3 = java.util.Locale.forLanguageTag(bcp47).getISO3Language();
            return (iso3 != null && !iso3.isEmpty()) ? iso3 : bcp47;
        } catch (Exception e) {
            return bcp47;
        }
    }

    /**
     * Build the set of ISO 639-3 codes that Optimaize supports,
     * by translating each BCP-47 label returned by getSupportedLanguages().
     */
    @SuppressWarnings("unchecked")
    static Set<String> optimaizeSupportedIso3(LanguageDetector optimaize) {
        Set<String> iso3 = new HashSet<>();
        if (optimaize == null) {
            return iso3;
        }
        try {
            // Optimaize stores its supported BCP-47 tags in the static DEFAULT_LANGUAGES field.
            java.lang.reflect.Field f =
                    optimaize.getClass().getDeclaredField("DEFAULT_LANGUAGES");
            f.setAccessible(true);
            Set<String> bcp47Tags = (Set<String>) f.get(null);
            for (String tag : bcp47Tags) {
                iso3.add(optimaizePredToIso3(tag));
            }
        } catch (Exception e) {
            System.err.println("WARN: could not get Optimaize supported languages: " + e);
        }
        return iso3;
    }

    static EvalResult evaluateOptimaizeParallel(List<LanguageDetector> detectors,
                                                List<LabeledSentence> data,
                                                String name) throws Exception {
        return evaluateOptimaizeParallel(detectors, data, name, null);
    }

    static EvalResult evaluateOptimaizeParallel(List<LanguageDetector> detectors,
                                                List<LabeledSentence> data,
                                                String name,
                                                Set<String> supportedLangs) throws Exception {
        if (detectors == null || detectors.isEmpty() || data.isEmpty()) {
            return new EvalResult(name);
        }
        int numThreads = detectors.size();
        if (numThreads <= 1) {
            return evaluateOptimaizeChunk(detectors.get(0), data, name, supportedLangs);
        }
        List<List<LabeledSentence>> chunks = partition(data, numThreads);
        ExecutorService pool = Executors.newFixedThreadPool(chunks.size());
        try {
            List<Future<EvalResult>> futures = new ArrayList<>();
            long wallStart = System.nanoTime();
            for (int i = 0; i < chunks.size(); i++) {
                final List<LabeledSentence> chunk = chunks.get(i);
                final LanguageDetector d = detectors.get(i % detectors.size());
                futures.add(pool.submit(() ->
                        evaluateOptimaizeChunk(d, chunk, name, supportedLangs)));
            }
            EvalResult merged = new EvalResult(name);
            merged.perLang = new java.util.TreeMap<>();
            merged.predictedCounts = new java.util.TreeMap<>();
            for (Future<EvalResult> f : futures) {
                EvalResult part = f.get();
                merged.correct += part.correct;
                merged.correctGroup += part.correctGroup;
                merged.total += part.total;
                merged.elapsedMs += part.elapsedMs;
                mergePerLang(merged.perLang, part.perLang);
                mergePredicted(merged.predictedCounts, part.predictedCounts);
            }
            merged.elapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
            return merged;
        } finally {
            pool.shutdown();
        }
    }

    static EvalResult evaluateOptimaizeChunk(LanguageDetector detector,
                                             List<LabeledSentence> data,
                                             String name,
                                             Set<String> supportedLangs) {
        EvalResult result = new EvalResult(name);
        if (detector == null || data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new java.util.TreeMap<>();
        Map<String, Integer> predictedCounts = new java.util.TreeMap<>();
        int correct = 0;
        int correctGroup = 0;
        int coveredTotal = 0;
        Set<String> unsupportedLangsSeen = supportedLangs != null ? new HashSet<>() : null;

        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            detector.reset();
            detector.addText(s.getText());
            List<LanguageResult> results = detector.detectAll();
            String rawPred = results.isEmpty() ? "unk" : results.get(0).getLanguage();
            // Translate BCP-47 → ISO 639-3 so we can compare against our test labels
            String predicted = optimaizePredToIso3(rawPred);
            String truth = s.getLanguage();
            if (supportedLangs != null && !supportedLangs.contains(truth)) {
                result.unsupportedTotal++;
                unsupportedLangsSeen.add(truth);
                continue;
            }
            coveredTotal++;
            predictedCounts.merge(predicted, 1, Integer::sum);
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
        result.total = supportedLangs != null ? coveredTotal : data.size();
        if (unsupportedLangsSeen != null) {
            result.unsupportedLangCount = unsupportedLangsSeen.size();
        }
        result.elapsedMs = elapsedNs / 1_000_000;
        result.perLang = perLang;
        result.predictedCounts = predictedCounts;
        return result;
    }

    // ---- Lingua evaluation ----

    /**
     * Evaluate Lingua detector (single-threaded; Lingua's model is read-only and thread-safe,
     * but reflection call overhead means parallelism gains are modest for shorter runs).
     */
    static EvalResult evaluateLingua(LinguaWrapper lingua,
                                     List<LabeledSentence> data, String name) {
        return evaluateLingua(lingua, data, name, null);
    }

    static EvalResult evaluateLingua(LinguaWrapper lingua,
                                     List<LabeledSentence> data, String name,
                                     Set<String> supportedLangs) {
        EvalResult result = new EvalResult(name);
        if (lingua == null || data.isEmpty()) {
            return result;
        }
        Map<String, int[]> perLang = new TreeMap<>();
        int correct = 0;
        int correctGroup = 0;
        int coveredTotal = 0;
        Set<String> unsupportedLangsSeen = supportedLangs != null ? new HashSet<>() : null;

        Map<String, Integer> predictedCounts = new TreeMap<>();
        long startNs = System.nanoTime();
        for (LabeledSentence s : data) {
            String predicted = lingua.detect(s.getText());
            String truth = s.getLanguage();
            if (supportedLangs != null && !supportedLangs.contains(truth)) {
                result.unsupportedTotal++;
                unsupportedLangsSeen.add(truth);
                continue;
            }
            coveredTotal++;
            predictedCounts.merge(predicted, 1, Integer::sum);
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

        result.correct = correct;
        result.correctGroup = correctGroup;
        result.total = supportedLangs != null ? coveredTotal : data.size();
        if (unsupportedLangsSeen != null) {
            result.unsupportedLangCount = unsupportedLangsSeen.size();
        }
        result.elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
        result.perLang = perLang;
        result.predictedCounts = predictedCounts;
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

    private static void mergePredicted(Map<String, Integer> target,
                                       Map<String, Integer> source) {
        if (source == null) {
            return;
        }
        source.forEach((k, v) -> target.merge(k, v, Integer::sum));
    }

    private static void mergeConfusions(Map<String, Map<String, Integer>> target,
                                        Map<String, Map<String, Integer>> source) {
        if (source == null) {
            return;
        }
        for (var e : source.entrySet()) {
            Map<String, Integer> targetInner =
                    target.computeIfAbsent(e.getKey(), k -> new HashMap<>());
            e.getValue().forEach((k, v) -> targetInner.merge(k, v, Integer::sum));
        }
    }

    // ---- Report ----

    static String buildReport(
            List<LengthEval> allEvals,
            List<LengthEval> opennlpEvals,
            List<LengthEval> linguaEvals,
            List<LengthEval> optimaizeEvals,
            long bigramHeap, long opennlpHeap, long linguaHeap, long optimaizeHeap,
            int totalSentences,
            int opennlpSharedLangCount, int opennlpSharedCount,
            int linguaSharedLangCount, int linguaSharedCount,
            int optimaizeSharedLangCount, int optimaizeSharedCount) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== Language Detection Comparison Report ===\n\n");
        sb.append(String.format(Locale.US, "Test sentences:   %,d%n", totalSentences));
        sb.append(String.format(Locale.US,
                "CharSoup \u2229 OpenNLP:   %d languages, %,d sentences%n",
                opennlpSharedLangCount, opennlpSharedCount));
        sb.append(String.format(Locale.US,
                "CharSoup \u2229 Lingua:    %d languages, %,d sentences%n",
                linguaSharedLangCount, linguaSharedCount));
        sb.append(String.format(Locale.US,
                "CharSoup \u2229 Optimaize: %d languages, %,d sentences%n%n",
                optimaizeSharedLangCount, optimaizeSharedCount));

        // Model sizes
        sb.append("Model heap (approx):\n");
        sb.append(String.format(Locale.US, "  CharSoup:  ~%.1f MB%n",
                bigramHeap / (1024.0 * 1024.0)));
        sb.append(String.format(Locale.US, "  OpenNLP:   ~%.1f MB%n",
                opennlpHeap / (1024.0 * 1024.0)));
        sb.append(String.format(Locale.US, "  Lingua:    ~%.1f MB  (low accuracy mode)%n",
                linguaHeap / (1024.0 * 1024.0)));
        sb.append(String.format(Locale.US, "  Optimaize: ~%.1f MB%n%n",
                optimaizeHeap / (1024.0 * 1024.0)));

        // ---- Coverage-adjusted table ----
        sb.append("Coverage-adjusted accuracy \u2014 each detector scored on its own supported languages only\n");
        sb.append("  (test sentences whose true language is not in a detector's covered set are skipped)\n");
        sb.append(lengthTable(allEvals, true));
        sb.append("\n");

        // ---- Breadth-weighted (strict) table ----
        sb.append("Breadth-weighted accuracy \u2014 all 203 FLORES languages, unsupported languages score 0\n");
        sb.append("  (penalises limited coverage; use this to compare total useful output across all inputs)\n");
        sb.append(strictLengthTable(allEvals));
        sb.append("\n");

        // ---- CharSoup ∩ OpenNLP ----
        sb.append(String.format(Locale.US,
                "Strict accuracy \u2014 CharSoup \u2229 OpenNLP (%d languages, %,d sentences)%n",
                opennlpSharedLangCount, opennlpSharedCount));
        sb.append(lengthTableTwoDetectors(opennlpEvals, "CharSoup", "OpenNLP", "opennlp"));
        sb.append("\n");

        // ---- CharSoup ∩ Lingua ----
        sb.append(String.format(Locale.US,
                "Strict accuracy \u2014 CharSoup \u2229 Lingua (%d languages, %,d sentences)%n",
                linguaSharedLangCount, linguaSharedCount));
        sb.append(lengthTableTwoDetectors(linguaEvals, "CharSoup", "Lingua", "lingua"));
        sb.append("\n");

        // ---- CharSoup ∩ Optimaize ----
        sb.append(String.format(Locale.US,
                "Strict accuracy \u2014 CharSoup \u2229 Optimaize (%d languages, %,d sentences)%n",
                optimaizeSharedLangCount, optimaizeSharedCount));
        sb.append(lengthTableTwoDetectors(optimaizeEvals, "CharSoup", "Optimaize", "optimaize"));
        sb.append("\n");

        // ---- CharSoup timing at each length ----
        sb.append("CharSoup timing (wall-clock, full pipeline including script gate + group collapse):\n");
        sb.append(String.format(Locale.US, "%-6s  %10s  %10s%n",
                "Length", "Wall(ms)", "Sent/sec"));
        sb.append("-".repeat(32)).append("\n");
        for (LengthEval le : allEvals) {
            EvalResult b = le.bigram;
            double sps = b.total > 0 && b.elapsedMs > 0
                    ? b.total / (b.elapsedMs / 1000.0) : 0;
            sb.append(String.format(Locale.US, "%-6s  %,10d  %,10.0f%n",
                    le.tag(), b.elapsedMs, sps));
        }
        sb.append("\n");

        // ---- Per-language CharSoup F1 across all lengths ----
        sb.append("Per-language CharSoup F1 by length:\n");
        // Build header from eval tags
        sb.append(String.format(Locale.US, "%-14s", "Language"));
        for (LengthEval le : allEvals) {
            sb.append(String.format(Locale.US, "  %6s", le.tag()));
        }
        sb.append("\n");
        sb.append("-".repeat(14 + allEvals.size() * 8)).append("\n");

        // Collect all language codes across all eval snapshots
        Set<String> allLangs = new TreeSet<>();
        for (LengthEval le : allEvals) {
            if (le.bigram != null && le.bigram.perLang != null) {
                allLangs.addAll(le.bigram.perLang.keySet());
            }
        }
        for (String lang : allLangs) {
            // Only include languages that appear in the full-length test set
            LengthEval fullSnap = allEvals.get(allEvals.size() - 1);
            int[] fullCounts = fullSnap.bigram != null && fullSnap.bigram.perLang != null
                    ? fullSnap.bigram.perLang.get(lang) : null;
            if (fullCounts == null || fullCounts[1] == 0) continue;
            sb.append(String.format(Locale.US, "%-14s", lang));
            for (LengthEval le : allEvals) {
                int[] c = le.bigram != null && le.bigram.perLang != null
                        ? le.bigram.perLang.get(lang) : null;
                if (c == null || c[1] == 0) {
                    sb.append(String.format(Locale.US, "  %6s", "N/A"));
                } else {
                    int predCount = le.bigram.predictedCounts != null
                            ? le.bigram.predictedCounts.getOrDefault(lang, 0) : 0;
                    sb.append(String.format(Locale.US, "  %s",
                            fmtPerLangF1(c[0], c[1], predCount)));
                }
            }
            sb.append("\n");
        }
        sb.append("\n");

        // ---- Per-language table at @500 / full ----
        LengthEval snap = allEvals.get(allEvals.size() - 1); // "full"
        sb.append(String.format(Locale.US,
                "Per-language macro F1 (%s):%n", snap.tag()));
        sb.append(String.format(Locale.US,
                "%-12s  %7s  %7s  %7s  %7s%n",
                "Language", "CharSoup", "OpenNLP", "Lingua", "Optimaize"));
        sb.append("-".repeat(58)).append("\n");

        // Collect per-language TP, true-total, predicted-total for each detector
        // [0]=bi-tp,  [1]=bi-true,  [2]=bi-pred,
        // [3]=on-tp,  [4]=on-true,  [5]=on-pred,
        // [6]=li-tp,  [7]=li-true,  [8]=li-pred,
        // [9]=opt-tp, [10]=opt-true, [11]=opt-pred
        Map<String, int[]> merged = new TreeMap<>();
        mergeIntoFull(merged, snap.bigram,    0);
        mergeIntoFull(merged, snap.opennlp,   3);
        mergeIntoFull(merged, snap.lingua,    6);
        mergeIntoFull(merged, snap.optimaize, 9);

        for (var e : merged.entrySet()) {
            int[] c = e.getValue();
            // c[1] = bigram true-total; skip languages absent from the test set
            if (c[1] == 0) {
                continue;
            }
            sb.append(String.format(Locale.US,
                    "%-12s  %s  %s  %s  %s%n",
                    e.getKey(),
                    fmtPerLangF1(c[0],  c[1],  c[2]),
                    fmtPerLangF1(c[3],  c[4],  c[5]),
                    fmtPerLangF1(c[6],  c[7],  c[8]),
                    fmtPerLangF1(c[9],  c[10], c[11])));
        }

        // ---- Top confusions for CharSoup: @20 and full text ----
        LengthEval short20 = allEvals.get(0);
        appendConfusionTable(sb, short20.bigram, short20.tag());
        appendConfusionTable(sb, snap.bigram, snap.tag());

        return sb.toString();
    }

    private static void appendConfusionTable(StringBuilder sb, EvalResult result, String tag) {
        if (result == null || result.confusions == null || result.perLang == null) {
            return;
        }
        sb.append(String.format(Locale.US,
                "%nCharSoup top confusions (languages with F1 < 95%%, %s):%n", tag));
        sb.append(String.format(Locale.US, "%-12s  %6s  %s%n",
                "TrueLabel", "F1", "Top misclassifications (predicted \u2192 count)"));
        sb.append("-".repeat(72)).append("\n");

        List<Map.Entry<String, int[]>> perLangEntries =
                new ArrayList<>(result.perLang.entrySet());
        perLangEntries.sort(Comparator.comparingDouble(e -> {
            int[] c = e.getValue();
            int tp = c[0], total = c[1];
            if (total == 0) {
                return 1.0;
            }
            int pred = result.predictedCounts != null
                    ? result.predictedCounts.getOrDefault(e.getKey(), 0) : 0;
            double rec = (double) tp / total;
            double prec = pred > 0 ? (double) tp / pred : 0.0;
            return (prec + rec) > 0 ? 2.0 * prec * rec / (prec + rec) : 0.0;
        }));

        for (var e : perLangEntries) {
            int[] c = e.getValue();
            int tp = c[0], total = c[1];
            if (total == 0) {
                continue;
            }
            int pred = result.predictedCounts != null
                    ? result.predictedCounts.getOrDefault(e.getKey(), 0) : 0;
            double rec = (double) tp / total;
            double prec = pred > 0 ? (double) tp / pred : 0.0;
            double f1 = (prec + rec) > 0 ? 2.0 * prec * rec / (prec + rec) : 0.0;
            if (f1 >= 0.95) {
                continue;
            }
            Map<String, Integer> cm = result.confusions.get(e.getKey());
            String confStr = "  (no misses recorded)";
            if (cm != null && !cm.isEmpty()) {
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(cm.entrySet());
                sorted.sort((a, b) -> b.getValue() - a.getValue());
                StringBuilder confSb = new StringBuilder();
                int shown = 0;
                for (var ce : sorted) {
                    if (shown++ > 0) {
                        confSb.append(", ");
                    }
                    confSb.append(ce.getKey()).append("\u2192").append(ce.getValue());
                    if (shown >= 7) {
                        break;
                    }
                }
                confStr = confSb.toString();
            }
            sb.append(String.format(Locale.US, "%-12s  %5.1f%%  %s%n",
                    e.getKey(), 100.0 * f1, confStr));
        }
    }

    /** Merge perLang counts from an EvalResult into a 6-element merged map, at offset. */
    private static void mergeInto(Map<String, int[]> merged, EvalResult r, int offset) {
        if (r == null || r.perLang == null) {
            return;
        }
        for (var e : r.perLang.entrySet()) {
            int[] row = merged.computeIfAbsent(e.getKey(), k -> new int[6]);
            row[offset]     = e.getValue()[0]; // correct
            row[offset + 1] = e.getValue()[1]; // total
        }
    }

    /**
     * Merge perLang + predictedCounts from an EvalResult into a 12-element map.
     * Layout at offset: [tp, true-total, predicted-total].
     */
    private static void mergeIntoFull(Map<String, int[]> merged, EvalResult r, int offset) {
        if (r == null || r.perLang == null) {
            return;
        }
        for (var e : r.perLang.entrySet()) {
            int[] row = merged.computeIfAbsent(e.getKey(), k -> new int[12]);
            row[offset]     = e.getValue()[0]; // tp
            row[offset + 1] = e.getValue()[1]; // true total
        }
        if (r.predictedCounts != null) {
            for (var e : r.predictedCounts.entrySet()) {
                int[] row = merged.computeIfAbsent(e.getKey(), k -> new int[12]);
                row[offset + 2] += e.getValue(); // predicted total
            }
        }
    }

    private static String fmtPerLangF1(int tp, int trueTotal, int predTotal) {
        if (trueTotal == 0) {
            return "    N/A";
        }
        double recall    = (double) tp / trueTotal;
        double precision = predTotal > 0 ? (double) tp / predTotal : 0.0;
        double f1 = (precision + recall) > 0
                ? 2.0 * precision * recall / (precision + recall) : 0.0;
        return String.format(Locale.US, "%6.2f%%", 100.0 * f1);
    }

    /** Four-detector table: macro F1 + accuracy + wall-clock times. */
    /** Breadth-weighted table: uses strict macro-F1 and strict accuracy (all FLORES languages). */
    private static String strictLengthTable(List<LengthEval> evals) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "%-6s  %-14s  %-14s  %-14s  %-14s%n",
                "", "─ CharSoup ─", "─ OpenNLP ─", "── Lingua ──", "─ Optimaize ─"));
        sb.append(String.format(Locale.US,
                "%-6s  %6s %6s  %6s %6s  %6s %6s  %6s %6s%n",
                "Length", "mF1", "acc", "mF1", "acc", "mF1", "acc", "mF1", "acc"));
        sb.append("-".repeat(70)).append("\n");
        for (LengthEval le : evals) {
            sb.append(String.format(Locale.US,
                    "%-6s  %s %s  %s %s  %s %s  %s %s%n",
                    le.tag(),
                    fmtStrict(le.bigram),    fmtStrictAcc(le.bigram),
                    fmtStrict(le.opennlp),   fmtStrictAcc(le.opennlp),
                    fmtStrict(le.lingua),    fmtStrictAcc(le.lingua),
                    fmtStrict(le.optimaize), fmtStrictAcc(le.optimaize)));
        }
        return sb.toString();
    }

    private static String fmtStrict(EvalResult r) {
        if (r == null || (r.total + r.unsupportedTotal) == 0) {
            return "  N/A  ";
        }
        return String.format(Locale.US, "%6.2f%%", 100.0 * computeStrictMacroF1(r));
    }

    private static String fmtStrictAcc(EvalResult r) {
        if (r == null || (r.total + r.unsupportedTotal) == 0) {
            return "  N/A  ";
        }
        return String.format(Locale.US, "%6.2f%%", 100.0 * computeStrictAccuracy(r));
    }

    private static String lengthTable(List<LengthEval> evals, boolean showLingua) {
        StringBuilder sb = new StringBuilder();
        // Two header rows: detector names span F1+acc columns
        sb.append(String.format(Locale.US,
                "%-6s  %-14s  %-14s  %-14s  %-14s  %8s  %8s  %8s  %8s  %10s%n",
                "", "─ CharSoup ─", "─ OpenNLP ─", "── Lingua ──", "─ Optimaize ─",
                "CS(ms)", "ON(ms)", "Li(ms)", "Opt(ms)", "CS sent/s"));
        sb.append(String.format(Locale.US,
                "%-6s  %6s %6s  %6s %6s  %6s %6s  %6s %6s  %8s  %8s  %8s  %8s  %10s%n",
                "Length", "mF1", "acc", "mF1", "acc", "mF1", "acc", "mF1", "acc",
                "", "", "", "", ""));
        sb.append("-".repeat(118)).append("\n");
        for (LengthEval le : evals) {
            double sps = le.bigram.total > 0 && le.bigram.elapsedMs > 0
                    ? le.bigram.total / (le.bigram.elapsedMs / 1000.0) : 0;
            sb.append(String.format(Locale.US,
                    "%-6s  %s %s  %s %s  %s %s  %s %s  %,8d  %,8d  %,8d  %,8d  %,10.0f%n",
                    le.tag(),
                    fmtF1(le.bigram),    fmtPct(le.bigram),
                    fmtF1(le.opennlp),   fmtPct(le.opennlp),
                    fmtF1(le.lingua),    fmtPct(le.lingua),
                    fmtF1(le.optimaize), fmtPct(le.optimaize),
                    le.bigram.elapsedMs,
                    le.opennlp   != null ? le.opennlp.elapsedMs   : 0L,
                    le.lingua    != null ? le.lingua.elapsedMs    : 0L,
                    le.optimaize != null ? le.optimaize.elapsedMs : 0L,
                    sps));
        }
        return sb.toString();
    }

    /** Two-detector shared table: macro F1 + accuracy + wall-clock times. */
    private static String lengthTableTwoDetectors(List<LengthEval> evals,
                                                  String nameB, String nameC,
                                                  String cField) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "%-6s  %-14s  %-14s  %8s  %8s  %10s%n",
                "", "── " + nameB + " ──", "── " + nameC + " ──",
                "CS(ms)", nameC + "(ms)", "CS sent/s"));
        sb.append(String.format(Locale.US,
                "%-6s  %6s %6s  %6s %6s  %8s  %8s  %10s%n",
                "Length", "mF1", "acc", "mF1", "acc", "", "", ""));
        sb.append("-".repeat(72)).append("\n");
        for (LengthEval le : evals) {
            EvalResult c;
            switch (cField) {
                case "lingua":
                    c = le.lingua;
                    break;
                case "optimaize":
                    c = le.optimaize;
                    break;
                default:
                    c = le.opennlp;
                    break;
            }
            double sps = le.bigram.total > 0 && le.bigram.elapsedMs > 0
                    ? le.bigram.total / (le.bigram.elapsedMs / 1000.0) : 0;
            sb.append(String.format(Locale.US,
                    "%-6s  %s %s  %s %s  %,8d  %,8d  %,10.0f%n",
                    le.tag(),
                    fmtF1(le.bigram), fmtPct(le.bigram),
                    fmtF1(c),         fmtPct(c),
                    le.bigram.elapsedMs, c != null ? c.elapsedMs : 0L, sps));
        }
        return sb.toString();
    }

    // ---- Helpers ----

    /** Return a copy of {@code data} with each text truncated to {@code maxLen} characters. */
    static List<LabeledSentence> truncate(List<LabeledSentence> data, int maxLen) {
        if (maxLen == Integer.MAX_VALUE) {
            return data;
        }
        List<LabeledSentence> result = new ArrayList<>(data.size());
        for (LabeledSentence s : data) {
            String text = s.getText();
            if (text.length() > maxLen) {
                text = text.substring(0, maxLen);
            }
            result.add(new LabeledSentence(s.getLanguage(), text));
        }
        return result;
    }

    /**
     * FLORES-200 uses different ISO 639-3 codes for some languages than our training
     * pipeline does (which follows Wikipedia dump naming). After stripping the script
     * suffix we remap to the canonical code used in our model.
     * <p>
     * Must stay in sync with {@code PrepareCorpus.LANG_MERGE_MAP} and
     * {@code CommonTokenGenerator.LANG_MERGE_MAP}.
     */
    private static final Map<String, String> FLORES_CODE_REMAP = Map.ofEntries(
            Map.entry("arb", "ara"),   // Modern Standard Arabic → Arabic
            Map.entry("pes", "fas"),   // Western Persian → Farsi
            Map.entry("zsm", "msa"),   // Standard Malay → Malay
            Map.entry("lvs", "lav"),   // Standard Latvian → Latvian
            Map.entry("azj", "aze"),   // North Azerbaijani → Azerbaijani
            Map.entry("ekk", "est"),   // Standard Estonian → Estonian
            Map.entry("npi", "nep"),   // Nepali (individual) → Nepali
            Map.entry("als", "sqi"),   // Tosk Albanian → Albanian
            Map.entry("ory", "ori"),   // Odia (macrolanguage) → Oriya
            Map.entry("nor", "nob"),   // Norwegian → Bokmål
            Map.entry("cmn", "zho"),   // Mandarin → Chinese
            Map.entry("swa", "swh"),   // Swahili (macrolanguage) → Swahili
            Map.entry("yid", "ydd"),   // Yiddish → Eastern Yiddish
            Map.entry("gug", "grn"),   // Paraguayan Guaraní → Guaraní
            Map.entry("quz", "que"),   // Cusco Quechua → Quechua
            Map.entry("plt", "mlg"),   // Plateau Malagasy → Malagasy (dropped in v5; kept for safety)
            Map.entry("pbt", "pus"),   // Southern Pashto → Pashto
            Map.entry("uzn", "uzb"),   // Northern Uzbek → Uzbek
            Map.entry("kmr", "kur"),   // Kurmanji Kurdish → Kurdish
            Map.entry("khk", "mon")    // Khalkha Mongolian → Mongolian
    );

    /**
     * Strip Flores-200 script suffix: {@code zho_Hans} → {@code zho},
     * {@code ace_Arab} → {@code ace}. Then remap FLORES-specific codes to
     * the canonical codes used in our model. Plain codes are returned unchanged.
     */
    static String normalizeLang(String lang) {
        int underscore = lang.indexOf('_');
        String base = underscore >= 0 ? lang.substring(0, underscore) : lang;
        return FLORES_CODE_REMAP.getOrDefault(base, base);
    }

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

    /** Results for one evaluation length threshold across all detectors. */
    static class LengthEval {
        final int maxLen;
        EvalResult bigram;
        EvalResult opennlp;
        EvalResult lingua;
        EvalResult optimaize;

        LengthEval(int maxLen) {
            this.maxLen = maxLen;
        }

        String tag() {
            return maxLen == Integer.MAX_VALUE ? "full" : "@" + maxLen;
        }
    }

    /**
     * Reflective wrapper around {@code com.github.pemistahl.lingua.api.LanguageDetectorBuilder}.
     * Loaded optionally so that CompareDetectors runs even when Lingua is not on the classpath.
     * Uses low-accuracy mode (~300 MB) instead of high-accuracy (~3.5 GB).
     */
    static class LinguaWrapper {
        final Set<String> supportedLangs;
        private final Object detector;
        private final Method detectMethod;
        private final Method getIso3Method;

        private LinguaWrapper(Object detector, Method detectMethod,
                              Method getIso3Method, Set<String> supportedLangs) {
            this.detector = detector;
            this.detectMethod = detectMethod;
            this.getIso3Method = getIso3Method;
            this.supportedLangs = Collections.unmodifiableSet(supportedLangs);
        }

        static LinguaWrapper load() {
            try {
                Class<?> builderClass = Class.forName(
                        "com.github.pemistahl.lingua.api.LanguageDetectorBuilder");
                Object builder = builderClass.getMethod("fromAllLanguages").invoke(null);
                builder = builder.getClass().getMethod("withLowAccuracyMode").invoke(builder);
                Object det = builder.getClass().getMethod("build").invoke(builder);

                // Locate detectLanguageOf(CharSequence) — Kotlin compiles it as CharSequence
                Method detectM = null;
                for (Method m : det.getClass().getMethods()) {
                    if ("detectLanguageOf".equals(m.getName())
                            && m.getParameterCount() == 1) {
                        detectM = m;
                        break;
                    }
                }
                if (detectM == null) {
                    throw new NoSuchMethodException("detectLanguageOf not found on "
                            + det.getClass());
                }

                // Enumerate supported ISO 639-3 codes from the Language enum
                Class<?> langEnumClass = Class.forName(
                        "com.github.pemistahl.lingua.api.Language");
                Method getIso3M = langEnumClass.getMethod("getIsoCode639_3");
                Set<String> langs = new HashSet<>();
                for (Object lang : langEnumClass.getEnumConstants()) {
                    if ("UNKNOWN".equals(lang.toString())) {
                        continue;
                    }
                    String code = getIso3M.invoke(lang).toString().toLowerCase(Locale.ROOT);
                    if (!"none".equals(code)) {
                        langs.add(code);
                    }
                }

                System.out.printf(Locale.US,
                        "  Loaded Lingua (low accuracy mode, %d languages), ~%.1f MB heap%n",
                        langs.size(), 0.0); // heap measured externally
                return new LinguaWrapper(det, detectM, getIso3M, langs);
            } catch (Exception e) {
                System.err.println("  WARN: Could not load Lingua: " + e.getMessage());
                return null;
            }
        }

        String detect(String text) {
            try {
                Object lang = detectMethod.invoke(detector, text);
                if ("UNKNOWN".equals(lang.toString())) {
                    return "unk";
                }
                return getIso3Method.invoke(lang).toString().toLowerCase(Locale.ROOT);
            } catch (Exception e) {
                return "unk";
            }
        }
    }

    static class EvalResult {
        String name;
        int correct;
        int correctGroup; // correct when allowing confusable group match
        int total;        // sentences in covered languages only
        /** Sentences whose true language is not in the detector's supported set. */
        int unsupportedTotal;
        /** Distinct language codes from unsupported-language sentences. */
        int unsupportedLangCount;
        long elapsedMs;
        /** Per-language counts: [strict correct, total true, group correct]. */
        Map<String, int[]> perLang;
        /** How many times each label was predicted (for precision / macro F1). */
        Map<String, Integer> predictedCounts;
        /** For each true label: how many times each predicted label appeared on misses. */
        Map<String, Map<String, Integer>> confusions;

        EvalResult(String name) {
            this.name = name;
        }
    }
}
