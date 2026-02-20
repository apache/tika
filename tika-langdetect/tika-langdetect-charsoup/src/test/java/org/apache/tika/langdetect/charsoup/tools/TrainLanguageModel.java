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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.LanguageConfusables;
import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;
import org.apache.tika.langdetect.charsoup.TextFeatureExtractor;
import org.apache.tika.ml.LinearModel;

/**
 * Two-pass training pipeline for the bigram language detector.
 * <p>
 * <strong>WARNING — feature extraction must stay in sync with
 * {@link org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor}.</strong>
 * Training uses {@code CharSoupFeatureExtractor} directly, so any change to
 * that class's preprocessing or hashing automatically applies here. However,
 * if you change training hyper-parameters (bucket count, n-gram order, etc.)
 * you must also update the corresponding inference constants and retrain from
 * scratch; there is no automatic check that the saved model matches the
 * current code.
 * </p>
 * <p>
 * Usage: {@code TrainLanguageModel <corpusDir> <outputFile> [numBuckets] [epochMaxPerLang]}
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Read corpus, merge ISO 639-3 codes, dedup, split into
 *       test (10%, max 20K, raw), dev (10%, max 20K, preprocessed),
 *       and training pool (rest, preprocessed, per-language files)</li>
 *   <li><b>Pass 1</b>: Train with epoch-level resampling
 *       (cap per language per epoch)</li>
 *   <li><b>Filter</b>: Remove mislabeled sentences from entire
 *       training pool (respecting confusable groups)</li>
 *   <li><b>Pass 2</b>: Retrain on filtered pool with resampling</li>
 *   <li>Quantize to INT8, evaluate on raw test, export</li>
 * </ol>
 */
public class TrainLanguageModel {

    private static final int DEFAULT_NUM_BUCKETS = 8_192;
    private static final long DEFAULT_TARGET_EPOCH_TOTAL = 5_000_000L;
    private static final int DEFAULT_MAX_TRAIN_PER_LANG = 0;   // 0 = unlimited
    private static final int DEFAULT_MAX_DEV_PER_LANG   = 20_000;
    private static final int DEFAULT_MAX_TEST_PER_LANG  = 20_000;

    /**
     * Languages explicitly excluded from the model despite having enough
     * corpus data to meet the {@link #MIN_SENTENCES_PER_LANG} threshold.
     * Each exclusion is justified by one of two criteria:
     *
     * <ul>
     *   <li><b>Collateral damage</b> — adding the language causes a closely
     *       related majority language to drop significantly in accuracy,
     *       because the model cannot reliably distinguish them at the
     *       character n-gram level.</li>
     *   <li><b>Unacceptable own accuracy</b> — the language's own detection
     *       accuracy is too low to be useful, typically because its written
     *       form is nearly identical to one or more larger languages.</li>
     * </ul>
     *
     * Decisions were made by evaluating per-language accuracy on a held-out
     * test set and examining the drop in accuracy for related languages.
     * See the build documentation for per-language justifications.
     */
    static final Set<String> EXCLUDED_LANGS;
    static {
        Set<String> ex = new HashSet<>();
        // Venetian (vec): 72.0% own accuracy; Italian (ita) dropped to
        // 83.6% (a 14.5pp gap vs group accuracy). Collateral damage
        // to a major language is unacceptable.
        ex.add("vec");
        // Tosk Albanian (als): 69.7% own accuracy; Standard Albanian (sqi)
        // collapsed to 51.6% strict accuracy. Albanian is broken by its
        // presence.
        ex.add("als");
        // Madurese (mad): 9.1% own accuracy on 1,003 test sentences —
        // essentially random. Written in Latin script, indistinguishable
        // from Javanese/Indonesian at the character level.
        ex.add("mad");
        // Anaang (anw): 32.5% own accuracy on only 3,036 test sentences.
        // Below any useful quality bar; no confusable partner to explain
        // the failure.
        ex.add("anw");
        // Konkani (knn): 46.2% own accuracy. Uses Devanagari script
        // (same as Marathi), and its character n-gram profile is too
        // similar to Marathi to distinguish reliably.
        ex.add("knn");
        // Gilaki (glk): 88.6% own accuracy. Northwestern Iranian language
        // whose script profile overlaps with Persian and Mazanderani;
        // below the quality bar we want for included languages.
        ex.add("glk");
        // Kituba / Monokutuba (mkw): 80.1% own accuracy. Bantu contact
        // language whose character profile overlaps with Kongo and Lingala;
        // accuracy is not high enough to justify inclusion.
        ex.add("mkw");
        EXCLUDED_LANGS = Collections.unmodifiableSet(ex);
    }

    private static final Map<String, String> LANG_MERGE_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("azj", "aze");
        m.put("ekk", "est");
        m.put("pes", "fas");
        m.put("zsm", "msa");
        m.put("nor", "nob");
        m.put("plt", "mlg");
        m.put("cmn", "zho");
        m.put("lvs", "lav");
        m.put("gug", "grn");
        m.put("quz", "que");
        m.put("swa", "swh");
        m.put("yid", "ydd");
        LANG_MERGE_MAP = Collections.unmodifiableMap(m);
    }

    public static void main(String[] args) throws IOException {
        Path corpusDir  = null;
        Path outputFile = null;
        int  numBuckets       = DEFAULT_NUM_BUCKETS;
        long targetEpochTotal = DEFAULT_TARGET_EPOCH_TOTAL;
        int  maxTrainPerLang  = DEFAULT_MAX_TRAIN_PER_LANG;
        int  maxDevPerLang    = DEFAULT_MAX_DEV_PER_LANG;
        int  maxTestPerLang   = DEFAULT_MAX_TEST_PER_LANG;
        boolean useTrigrams      = false;
        boolean useSkipBigrams   = false;
        Path    evalOnlyModel    = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--corpus":       corpusDir      = Paths.get(args[++i]); break;
                case "--output":       outputFile     = Paths.get(args[++i]); break;
                case "--buckets":      numBuckets     = Integer.parseInt(args[++i]); break;
                case "--epoch-total":  targetEpochTotal = Long.parseLong(args[++i]); break;
                case "--max-train":    maxTrainPerLang = Integer.parseInt(args[++i]); break;
                case "--max-dev":      maxDevPerLang   = Integer.parseInt(args[++i]); break;
                case "--max-test":     maxTestPerLang  = Integer.parseInt(args[++i]); break;
                case "--trigrams":     useTrigrams     = true; break;
                case "--skip-bigrams": useSkipBigrams  = true; break;
                case "--eval-only":    evalOnlyModel   = Paths.get(args[++i]); break;
                default:
                    // legacy positional args
                    if (corpusDir == null)       { corpusDir  = Paths.get(args[i]); }
                    else if (outputFile == null) { outputFile = Paths.get(args[i]); }
                    else {
                        System.err.println("Unknown argument: " + args[i]);
                        System.exit(1);
                    }
            }
        }

        // --eval-only requires --output (to locate preprocessed/) but not --corpus
        if (evalOnlyModel != null) {
            if (outputFile == null) {
                System.err.println(
                        "Usage: TrainLanguageModel --eval-only <model> --output <file>"
                        + " [--trigrams] [--skip-bigrams]");
                System.exit(1);
            }
            Path prepDir  = (outputFile.getParent() != null
                    ? outputFile.getParent() : Paths.get(".")).resolve("preprocessed");
            Path testFile = prepDir.resolve("test_raw.txt");
            System.out.println("=== Eval-only mode ===");
            System.out.println("Model:    " + evalOnlyModel);
            System.out.println("Test set: " + testFile);
            LinearModel model = LinearModel.loadFromPath(evalOnlyModel);
            List<LabeledSentence> testData = readPreprocessedFile(testFile);
            evaluateQuantized(model, testData, useTrigrams, useSkipBigrams);
            return;
        }

        if (corpusDir == null || outputFile == null) {
            System.err.println(
                    "Usage: TrainLanguageModel --corpus <dir> --output <file>"
                    + " [--buckets N] [--epoch-total N]"
                    + " [--max-train N] [--max-dev N] [--max-test N]"
                    + " [--trigrams] [--skip-bigrams]"
                    + " [--eval-only <model-path>]");
            System.exit(1);
        }

        // Languages with fewer than (train+dev+test) sentences are skipped.
        // A value of 0 for maxTrainPerLang means "no explicit cap on train pool".
        int minSentencesPerLang = (maxTrainPerLang > 0 ? maxTrainPerLang : 0)
                + maxDevPerLang + maxTestPerLang;
        if (minSentencesPerLang == 0) {
            // all defaults: fall back to a sensible floor
            minSentencesPerLang = 10_000;
        }

        Path prepDir = (outputFile.getParent() != null
                ? outputFile.getParent()
                : Paths.get(".")).resolve("preprocessed");

        System.out.println("=== Language Model Training Pipeline ===");
        System.out.println("Corpus:           " + corpusDir);
        System.out.println("Output:           " + outputFile);
        System.out.println("Prep dir:         " + prepDir);
        System.out.println("Buckets:          " + numBuckets);
        System.out.printf(Locale.US, "Epoch total:      %,d%n", targetEpochTotal);
        System.out.printf(Locale.US, "Max train/lang:   %s%n",
                maxTrainPerLang > 0 ? String.format(Locale.US, "%,d", maxTrainPerLang) : "unlimited");
        System.out.printf(Locale.US, "Max dev/lang:     %,d%n", maxDevPerLang);
        System.out.printf(Locale.US, "Max test/lang:    %,d%n", maxTestPerLang);
        System.out.printf(Locale.US, "Min sentences:    %,d (skip threshold)%n", minSentencesPerLang);
        System.out.println();

        long pipelineStart = System.nanoTime();
        long stepStart;

        // ---- Data preparation ----
        Path poolDir = prepDir.resolve("pool");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test_raw.txt");

        if (Files.isDirectory(poolDir)
                && Files.exists(devFile)
                && Files.exists(testFile)) {
            System.out.println(
                    "--- Preprocessed data found — skipping data prep ---");
        } else {
            stepStart = System.nanoTime();
            System.out.println("--- Step 1: Data preparation ---");
            Files.createDirectories(poolDir);
            int[] counts = prepareData(corpusDir, prepDir,
                    maxTrainPerLang, maxDevPerLang, maxTestPerLang, minSentencesPerLang);
            System.out.printf(Locale.US,
                    "Prepared: pool=%,d  dev=%,d  test=%,d%n",
                    counts[0], counts[1], counts[2]);
            System.out.printf(Locale.US,
                    "  [%.1f s]%n", elapsed(stepStart));
        }

        // Collect all labels from pool directory
        String[] allLabels = collectLabels(poolDir);
        System.out.printf(Locale.US,
                "Languages in pool: %d%n%n", allLabels.length);

        // Load fixed dev data
        stepStart = System.nanoTime();
        System.out.println("--- Loading dev data ---");
        List<LabeledSentence> devData =
                readPreprocessedFile(devFile);
        System.out.printf(Locale.US,
                "Dev: %,d sentences  [%.1f s]%n",
                devData.size(), elapsed(stepStart));

        Path epochFile = prepDir.resolve("epoch_train.txt");

        // ---- Pass 1 ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 2: Pass 1 — Initial training ---");
        Map<String, Integer> pass1Targets =
                computePerLangTargets(
                        scanPoolSizes(poolDir),
                        targetEpochTotal);
        Phase2Trainer pass1 = new Phase2Trainer(numBuckets)
                .setPreprocessed(true)
                .setUseTrigrams(useTrigrams)
                .setUseSkipBigrams(useSkipBigrams);
        pass1.trainWithResampling(allLabels,
                epochNum -> createEpochFile(
                        poolDir, epochFile,
                        pass1Targets, epochNum),
                devData);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Filter training pool ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 3: Filtering mislabeled sentences ---");
        Path filteredPoolDir = prepDir.resolve("pool_filtered");
        long[] filterCounts = filterPool(
                pass1, poolDir, filteredPoolDir);
        System.out.printf(Locale.US,
                "Kept %,d / %,d sentences "
                        + "(removed %,d = %.1f%%)%n",
                filterCounts[0], filterCounts[1],
                filterCounts[1] - filterCounts[0],
                100.0 * (filterCounts[1] - filterCounts[0])
                        / filterCounts[1]);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Pass 2 ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 4: Pass 2 — Retraining on "
                        + "filtered data ---");
        String[] filteredLabels =
                collectLabels(filteredPoolDir);
        Map<String, Integer> pass2Targets =
                computePerLangTargets(
                        scanPoolSizes(filteredPoolDir),
                        targetEpochTotal);
        Phase2Trainer pass2 = new Phase2Trainer(numBuckets)
                .setPreprocessed(true)
                .setUseTrigrams(useTrigrams)
                .setUseSkipBigrams(useSkipBigrams);
        pass2.trainWithResampling(filteredLabels,
                epochNum -> createEpochFile(
                        filteredPoolDir, epochFile,
                        pass2Targets, epochNum),
                devData);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Quantize ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 5: Quantizing to INT8 ---");
        LinearModel quantized = ModelQuantizer.quantize(pass2);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Evaluate on raw test set ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 6: Evaluating on raw test set ---");
        List<LabeledSentence> testData =
                readPreprocessedFile(testFile);
        double testAcc = evaluateQuantized(quantized, testData, useTrigrams, useSkipBigrams);
        System.out.printf(Locale.US,
                "Test accuracy (quantized): %.4f%n", testAcc);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Export ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 7: Exporting model ---");
        if (outputFile.getParent() != null) {
            Files.createDirectories(outputFile.getParent());
        }
        try (OutputStream os = new BufferedOutputStream(
                Files.newOutputStream(outputFile))) {
            quantized.save(os);
        }
        long fileSize = Files.size(outputFile);
        System.out.printf(Locale.US,
                "Model saved: %s (%.1f MB)%n",
                outputFile, fileSize / (1024.0 * 1024.0));
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        double totalMin = (System.nanoTime() - pipelineStart)
                / 1_000_000_000.0 / 60.0;
        System.out.printf(Locale.US,
                "%nDone! Total time: %.1f min%n", totalMin);
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    // ================================================================
    //  Epoch file creation (resampling from pool)
    // ================================================================

    /**
     * Sample up to {@code maxPerLang} sentences from each
     * per-language pool file and write to a single epoch
     * training file with languages interleaved.
     * <p>
     * Two-phase approach to stay memory-efficient:
     * <ol>
     *   <li><b>Sample</b>: reservoir-sample each language
     *       one at a time into a per-language temp file.
     *       Peak memory = one language's reservoir.</li>
     *   <li><b>Interleave</b>: open all temp files, randomly
     *       pick a language for each line, write to epoch
     *       file. Memory = reader buffers only (~200 × 8 KB).
     *       </li>
     * </ol>
     * Interleaving is critical: without it, the epoch file
     * would contain single-language blocks that cause
     * catastrophic forgetting during SGD.
     *
     * @return the epoch file path
     */
    static Path createEpochFile(Path poolDir, Path epochFile,
                                Map<String, Integer> perLangTargets,
                                int epochNum)
            throws IOException {
        Random rng = new Random(42L + epochNum * 31L);

        List<String> langs = new ArrayList<>();
        List<Path> poolFiles = new ArrayList<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(poolDir)) {
            for (Path langFile : ds) {
                if (!Files.isRegularFile(langFile)) {
                    continue;
                }
                langs.add(
                        langFile.getFileName().toString());
                poolFiles.add(langFile);
            }
        }

        // Phase 1: reservoir sample each language into a
        // temp file (one language in memory at a time)
        Path sampledDir =
                Files.createTempDirectory("epoch_sampled_");
        List<Path> sampledFiles =
                new ArrayList<>(langs.size());

        for (int i = 0; i < poolFiles.size(); i++) {
            int target = perLangTargets.getOrDefault(
                    langs.get(i), 0);
            List<String> reservoir = new ArrayList<>();
            try (BufferedReader reader =
                         Files.newBufferedReader(
                                 poolFiles.get(i),
                                 StandardCharsets.UTF_8)) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineNum < target) {
                        reservoir.add(line);
                    } else {
                        int j = rng.nextInt(lineNum + 1);
                        if (j < target) {
                            reservoir.set(j, line);
                        }
                    }
                    lineNum++;
                }
            }

            Collections.shuffle(reservoir, rng);
            Path sampledFile =
                    sampledDir.resolve(langs.get(i));
            try (BufferedWriter sw =
                         Files.newBufferedWriter(
                                 sampledFile,
                                 StandardCharsets.UTF_8)) {
                for (String text : reservoir) {
                    sw.write(text);
                    sw.newLine();
                }
            }
            sampledFiles.add(sampledFile);
        }

        // Phase 2: interleave into epoch file by randomly
        // picking among active languages for each line
        int numLangs = langs.size();
        BufferedReader[] readers =
                new BufferedReader[numLangs];
        String[] pending = new String[numLangs];
        List<Integer> active = new ArrayList<>(numLangs);
        int totalWritten = 0;

        try (BufferedWriter w = Files.newBufferedWriter(
                epochFile, StandardCharsets.UTF_8)) {
            for (int i = 0; i < numLangs; i++) {
                readers[i] = Files.newBufferedReader(
                        sampledFiles.get(i),
                        StandardCharsets.UTF_8);
                pending[i] = readers[i].readLine();
                if (pending[i] != null) {
                    active.add(i);
                }
            }

            while (!active.isEmpty()) {
                int pick = rng.nextInt(active.size());
                int idx = active.get(pick);

                w.write(langs.get(idx));
                w.write('\t');
                w.write(pending[idx]);
                w.newLine();
                totalWritten++;

                pending[idx] = readers[idx].readLine();
                if (pending[idx] == null) {
                    readers[idx].close();
                    readers[idx] = null;
                    int last = active.size() - 1;
                    active.set(pick, active.get(last));
                    active.remove(last);
                }
            }
        } finally {
            for (BufferedReader r : readers) {
                if (r != null) {
                    r.close();
                }
            }
            for (Path f : sampledFiles) {
                Files.deleteIfExists(f);
            }
            Files.deleteIfExists(sampledDir);
        }

        System.out.printf(Locale.US,
                "  Epoch %d: sampled %,d sentences%n",
                epochNum + 1, totalWritten);
        return epochFile;
    }

    // ================================================================
    //  Pool size scanning and per-language target computation
    // ================================================================

    /**
     * Count lines in each per-language pool file.
     */
    static Map<String, Long> scanPoolSizes(Path poolDir)
            throws IOException {
        Map<String, Long> sizes = new HashMap<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(poolDir)) {
            for (Path p : ds) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                long count = 0;
                try (BufferedReader br =
                             Files.newBufferedReader(
                                     p,
                                     StandardCharsets.UTF_8)) {
                    while (br.readLine() != null) {
                        count++;
                    }
                }
                sizes.put(p.getFileName().toString(), count);
            }
        }
        return sizes;
    }

    /**
     * Compute per-language epoch targets by binary-searching
     * for a flat cap C such that {@code Σ min(n_i, C) ≈ targetTotal}.
     * Languages with fewer sentences than C contribute all their
     * data; larger languages are uniformly capped.
     */
    static Map<String, Integer> computePerLangTargets(
            Map<String, Long> poolSizes,
            long targetTotal) {
        long totalAvailable = poolSizes.values().stream()
                .mapToLong(Long::longValue).sum();

        if (totalAvailable <= targetTotal) {
            System.out.printf(Locale.US,
                    "  Pool total=%,d <= target=%,d;"
                            + " using all data%n",
                    totalAvailable, targetTotal);
            Map<String, Integer> targets = new HashMap<>();
            poolSizes.forEach((lang, size) ->
                    targets.put(lang, (int) Math.min(
                            size, Integer.MAX_VALUE)));
            return targets;
        }

        long lo = 0;
        long hi = poolSizes.values().stream()
                .mapToLong(Long::longValue).max().orElse(0);
        while (lo < hi - 1) {
            long mid = (lo + hi) / 2;
            long total = poolSizes.values().stream()
                    .mapToLong(n -> Math.min(n, mid)).sum();
            if (total < targetTotal) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        long cap = hi;
        long actualTotal = poolSizes.values().stream()
                .mapToLong(n -> Math.min(n, cap)).sum();
        long capped = poolSizes.values().stream()
                .filter(n -> n > cap).count();
        System.out.printf(Locale.US,
                "  Epoch cap=%,d  actual=%,d  target=%,d"
                        + "  (%d/%d langs capped)%n",
                cap, actualTotal, targetTotal,
                capped, poolSizes.size());

        Map<String, Integer> targets = new HashMap<>();
        poolSizes.forEach((lang, size) ->
                targets.put(lang,
                        (int) Math.min(size, cap)));
        return targets;
    }

    // ================================================================
    //  Mislabeled sentence filtering (pool-based)
    // ================================================================

    /**
     * Filter all per-language pool files in parallel. For each
     * sentence, if the model's prediction doesn't match the label
     * (respecting confusable groups), it is removed.
     * <p>
     * Each worker thread reuses its own extractor and feature/logit
     * buffers to avoid per-sentence allocation overhead.
     *
     * @return long[2]: {kept, total}
     */
    static long[] filterPool(Phase2Trainer trainer,
                             Path poolDir, Path filteredDir)
            throws IOException {
        Files.createDirectories(filteredDir);

        List<Path> langFiles = new ArrayList<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(poolDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    langFiles.add(p);
                }
            }
        }
        langFiles.sort(Comparator.comparing(
                p -> p.getFileName().toString()));

        int numLangs = langFiles.size();
        AtomicLong keptTotal = new AtomicLong();
        AtomicLong grandTotal = new AtomicLong();
        AtomicLong langsProcessed = new AtomicLong();

        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService exec =
                Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(numLangs);

        for (Path langFile : langFiles) {
            futures.add(exec.submit(() -> {
                String lang =
                        langFile.getFileName().toString();
                Path outFile = filteredDir.resolve(lang);
                TextFeatureExtractor ext = trainer.getExtractor();
                int[] featureBuf =
                        new int[trainer.getNumBuckets()];
                float[] logitBuf =
                        new float[trainer.getNumClasses()];
                long langKept = 0;
                long langTotal = 0;
                try (BufferedReader reader =
                             Files.newBufferedReader(
                                     langFile,
                                     StandardCharsets.UTF_8);
                     BufferedWriter writer =
                             Files.newBufferedWriter(
                                     outFile,
                                     StandardCharsets.UTF_8)) {
                    String text;
                    while ((text = reader.readLine())
                            != null) {
                        langTotal++;
                        String predicted =
                                trainer.predictBuffered(
                                        text, ext,
                                        featureBuf, logitBuf);
                        if (CompareDetectors.isGroupMatch(
                                lang, predicted)) {
                            writer.write(text);
                            writer.newLine();
                            langKept++;
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(
                            "Filter failed: " + lang, e);
                }
                keptTotal.addAndGet(langKept);
                grandTotal.addAndGet(langTotal);
                long done = langsProcessed.incrementAndGet();
                System.out.printf(Locale.US,
                        "  %s: kept %,d/%,d"
                                + "  [%d/%d langs done]%n",
                        lang, langKept, langTotal,
                        done, numLangs);
            }));
        }

        exec.shutdown();
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Filter thread failed", e);
            }
        }

        return new long[]{keptTotal.get(), grandTotal.get()};
    }

    // ================================================================
    //  Evaluation
    // ================================================================

    private static final int[] EVAL_LENGTHS = {20, 50, 100, 200, 0}; // 0 = full text

    static double evaluateQuantized(LinearModel model,
                                    List<LabeledSentence> data,
                                    boolean useTrigrams,
                                    boolean useSkipBigrams) {
        TextFeatureExtractor extractor =
                new ScriptAwareFeatureExtractor(
                        model.getNumBuckets(), useTrigrams, useSkipBigrams);
        int numClasses = model.getNumClasses();
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < numClasses; i++) {
            labelIndex.put(model.getLabel(i), i);
        }

        // Per-length: strict correct, lenient correct, total, per-class TP/FP/FN for macro F1
        int[] correct        = new int[EVAL_LENGTHS.length];
        int[] lenientCorrect = new int[EVAL_LENGTHS.length];
        int[] total          = new int[EVAL_LENGTHS.length];
        // [lengthIdx][classIdx] → {tp, fp, fn}
        int[][][] perClass = new int[EVAL_LENGTHS.length][numClasses][3];
        // [classIdx][lengthIdx] → strict correct count (for per-lang table)
        int[][] langCorrect = new int[numClasses][EVAL_LENGTHS.length];
        int[]   langTotal   = new int[numClasses];
        // confusion tracking at 20-char length: trueIdx → (predLabel → count)
        @SuppressWarnings("unchecked")
        Map<String, Integer>[] confusions20 = new HashMap[numClasses];

        for (LabeledSentence s : data) {
            Integer trueIdx = labelIndex.get(s.getLanguage());
            if (trueIdx == null) {
                continue;
            }
            String raw = s.getText();
            String trueLabel = s.getLanguage();
            langTotal[trueIdx]++;
            for (int li = 0; li < EVAL_LENGTHS.length; li++) {
                int maxLen = EVAL_LENGTHS[li];
                String text = (maxLen > 0 && raw.length() > maxLen)
                        ? raw.substring(0, maxLen) : raw;
                int[] features = extractor.extract(text);
                float[] probs = model.predict(features);
                int predIdx = argmax(probs);
                String predLabel = model.getLabel(predIdx);
                boolean strict  = predIdx == trueIdx;
                boolean lenient = strict
                        || LanguageConfusables.isLenientMatch(trueLabel, predLabel);
                if (strict) {
                    correct[li]++;
                    perClass[li][trueIdx][0]++;  // TP
                    langCorrect[trueIdx][li]++;
                } else {
                    perClass[li][trueIdx][2]++;  // FN
                    perClass[li][predIdx][1]++;  // FP
                    if (li == 0) {               // track confusion at 20 chars
                        if (confusions20[trueIdx] == null) {
                            confusions20[trueIdx] = new HashMap<>();
                        }
                        confusions20[trueIdx].merge(predLabel, 1, Integer::sum);
                    }
                }
                if (lenient) {
                    lenientCorrect[li]++;
                }
                total[li]++;
            }
        }

        System.out.printf(Locale.US, "  %-8s  %-10s  %-10s  %s%n",
                "chars", "strict", "lenient", "macro-F1");
        System.out.printf(Locale.US, "  %-8s  %-10s  %-10s  %s%n",
                "------", "--------", "--------", "--------");
        double fullAccuracy = 0.0;
        for (int li = 0; li < EVAL_LENGTHS.length; li++) {
            int maxLen = EVAL_LENGTHS[li];
            double acc        = total[li] > 0 ? (double) correct[li]        / total[li] : 0.0;
            double lenientAcc = total[li] > 0 ? (double) lenientCorrect[li] / total[li] : 0.0;
            double f1Sum = 0.0;
            int f1Count = 0;
            for (int c = 0; c < numClasses; c++) {
                int tp = perClass[li][c][0];
                int fp = perClass[li][c][1];
                int fn = perClass[li][c][2];
                if (tp + fn == 0) {
                    continue;
                }
                double p  = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
                double r  = (double) tp / (tp + fn);
                double f1 = (p + r) > 0 ? 2.0 * p * r / (p + r) : 0.0;
                f1Sum += f1;
                f1Count++;
            }
            double macroF1 = f1Count > 0 ? f1Sum / f1Count : 0.0;
            String label = maxLen > 0 ? String.valueOf(maxLen) : "full";
            System.out.printf(Locale.US, "  %-8s  %-10s  %-10s  %.4f%n",
                    label,
                    String.format(Locale.US, "%.2f%%", acc * 100.0),
                    String.format(Locale.US, "%.2f%%", lenientAcc * 100.0),
                    macroF1);
            if (maxLen == 0) {
                fullAccuracy = acc;
            }
        }

        // Per-language breakdown: sort by 20-char accuracy ascending (worst first)
        // Find the length indices for 20-char and full-text
        int li20   = 0; // EVAL_LENGTHS[0] = 20
        int liFull = EVAL_LENGTHS.length - 1; // EVAL_LENGTHS[last] = 0 (full)
        Integer[] classOrder = new Integer[numClasses];
        for (int c = 0; c < numClasses; c++) {
            classOrder[c] = c;
        }
        Arrays.sort(classOrder, (a, b) -> {
            double accA = langTotal[a] > 0 ? (double) langCorrect[a][li20] / langTotal[a] : 1.0;
            double accB = langTotal[b] > 0 ? (double) langCorrect[b][li20] / langTotal[b] : 1.0;
            return Double.compare(accA, accB); // ascending: worst first
        });
        System.out.println();
        System.out.printf(Locale.US, "  %-6s  %-8s  %-8s  %s%n",
                "lang", "@20", "@full", "n");
        System.out.printf(Locale.US, "  %-6s  %-8s  %-8s  %s%n",
                "------", "--------", "--------", "----");
        for (int c : classOrder) {
            if (langTotal[c] == 0) {
                continue;
            }
            double acc20   = (double) langCorrect[c][li20]   / langTotal[c];
            double accFull = (double) langCorrect[c][liFull] / langTotal[c];
            System.out.printf(Locale.US, "  %-6s  %-8s  %-8s  %d%n",
                    model.getLabel(c),
                    String.format(Locale.US, "%.1f%%", acc20   * 100.0),
                    String.format(Locale.US, "%.1f%%", accFull * 100.0),
                    langTotal[c]);
        }

        // Confusion detail: top-3 wrong predictions at 20 chars, worst 10 languages
        System.out.println();
        System.out.println("  Top confusions at 20 chars (worst languages):");
        System.out.printf(Locale.US, "  %-6s  %s%n", "lang", "confused-as (count)");
        System.out.printf(Locale.US, "  %-6s  %s%n", "------", "-------------------");
        int printed = 0;
        for (int c : classOrder) {
            if (printed >= 10) {
                break;
            }
            if (langTotal[c] == 0 || confusions20[c] == null) {
                continue;
            }
            List<Map.Entry<String, Integer>> entries =
                    new ArrayList<>(confusions20[c].entrySet());
            entries.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < Math.min(3, entries.size()); k++) {
                if (k > 0) {
                    sb.append(", ");
                }
                sb.append(entries.get(k).getKey())
                  .append('(').append(entries.get(k).getValue()).append(')');
            }
            System.out.printf(Locale.US, "  %-6s  %s%n",
                    model.getLabel(c), sb);
            printed++;
        }
        return fullAccuracy;
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

    // ================================================================
    //  Data preparation
    // ================================================================

    /**
     * Prepare data splits from corpus:
     * <ul>
     *   <li>{@code pool/} — per-language preprocessed files for training</li>
     *   <li>{@code dev.txt} — fixed dev set, preprocessed</li>
     *   <li>{@code test_raw.txt} — fixed test set, raw</li>
     * </ul>
     * <p>
     * Languages with fewer than {@code minSentencesPerLang} sentences after
     * deduplication are skipped and reported. When {@code maxTrainPerLang > 0},
     * each language directory is reservoir-sampled to at most
     * {@code maxTrainPerLang + maxDevPerLang + maxTestPerLang} sentences so
     * that large corpora (e.g. 2M English sentences) are not fully loaded
     * into memory.
     *
     * @return int[3]: {poolCount, devCount, testCount}
     */
    static int[] prepareData(Path corpusDir, Path prepDir,
                             int maxTrainPerLang, int maxDevPerLang,
                             int maxTestPerLang, int minSentencesPerLang)
            throws IOException {
        Path poolDir = prepDir.resolve("pool");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test_raw.txt");

        Files.createDirectories(poolDir);

        // Read cap: if the user has set explicit per-split limits, only load
        // as many sentences as we could possibly use. 0 = unlimited.
        int readCap = (maxTrainPerLang > 0)
                ? maxTrainPerLang + maxDevPerLang + maxTestPerLang
                : 0;

        int totalPool = 0, totalDev = 0, totalTest = 0;
        int langCount = 0;
        int droppedCount = 0;
        long totalDupes = 0;
        Map<String, Integer> langCounts = new TreeMap<>();
        List<String> dropped = new ArrayList<>();

        List<Path> langDirs = new ArrayList<>();
        try (DirectoryStream<Path> dirs =
                     Files.newDirectoryStream(corpusDir, Files::isDirectory)) {
            for (Path d : dirs) {
                langDirs.add(d);
            }
        }
        langDirs.sort((a, b) -> a.getFileName().toString()
                .compareTo(b.getFileName().toString()));

        // Per-language result collected by parallel workers before writing shared files.
        // lang → {devSentences, testSentences} (pool file is written directly to disk)
        // Using a concurrent map so workers can insert without locking.
        java.util.concurrent.ConcurrentHashMap<String, LangPrepResult> prepResults =
                new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.ConcurrentHashMap<String, String> droppedMap =
                new java.util.concurrent.ConcurrentHashMap<>();

        // Merge-accumulator must be populated before parallel work starts
        // because some dirs are aliases that need combining (e.g. azj → aze).
        Map<String, List<LabeledSentence>> mergeAccum = new HashMap<>();
        for (Path langDir : langDirs) {
            String dirName = langDir.getFileName().toString();
            if (dirName.startsWith("_")) {
                continue;
            }
            String canonLang = LANG_MERGE_MAP.getOrDefault(dirName, dirName);
            if (!canonLang.equals(dirName)) {
                // Pre-read alias dirs so they can be combined with the canonical dir
                List<LabeledSentence> sentences = new ArrayList<>();
                if (readCap > 0) {
                    CorpusReader.readLanguageDirHead(langDir, canonLang, readCap, sentences);
                } else {
                    CorpusReader.readLanguageDir(langDir, canonLang, sentences);
                }
                mergeAccum.computeIfAbsent(canonLang, k -> new ArrayList<>())
                        .addAll(sentences);
                System.out.printf(Locale.US, "  %s → %s (%,d sentences pre-loaded)%n",
                        dirName, canonLang, sentences.size());
            }
        }

        // Collect canonical language dirs (non-alias) for parallel processing
        List<Path> canonDirs = new ArrayList<>();
        for (Path langDir : langDirs) {
            String dirName = langDir.getFileName().toString();
            if (dirName.startsWith("_")) {
                continue;
            }
            String canonLang = LANG_MERGE_MAP.getOrDefault(dirName, dirName);
            if (canonLang.equals(dirName)) {
                canonDirs.add(langDir);
            }
        }
        // Also add any merged languages whose canonical dir wasn't in corpus
        for (String lang : mergeAccum.keySet()) {
            boolean found = canonDirs.stream()
                    .anyMatch(p -> p.getFileName().toString().equals(lang));
            if (!found) {
                canonDirs.add(null); // sentinel — handled below
            }
        }

        int threads = Math.min(Runtime.getRuntime().availableProcessors(),
                canonDirs.size());
        java.util.concurrent.ExecutorService exec =
                java.util.concurrent.Executors.newFixedThreadPool(threads);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

        for (Path langDir : canonDirs) {
            if (langDir == null) {
                continue;
            }
            futures.add(exec.submit(() -> {
                String lang = langDir.getFileName().toString();
                try {
                    List<LabeledSentence> sentences = new ArrayList<>();
                    if (readCap > 0) {
                        CorpusReader.readLanguageDirHead(langDir, lang, readCap, sentences);
                    } else {
                        CorpusReader.readLanguageDir(langDir, lang, sentences);
                    }

                    // Merge any alias data accumulated for this canonical lang
                    List<LabeledSentence> extra = mergeAccum.get(lang);
                    if (extra != null) {
                        sentences.addAll(extra);
                    }

                    sentences = dedup(sentences);

                    if (EXCLUDED_LANGS.contains(lang)) {
                        droppedMap.put(lang, lang + "(excluded)");
                        return;
                    }
                    if (sentences.size() < minSentencesPerLang) {
                        droppedMap.put(lang, String.format(Locale.US,
                                "%s(%,d)", lang, sentences.size()));
                        return;
                    }

                    LangPrepResult result = buildLangPrepResult(
                            sentences, lang, poolDir,
                            maxTrainPerLang, maxDevPerLang, maxTestPerLang);
                    prepResults.put(lang, result);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to prepare lang: " + lang, e);
                }
            }));
        }

        // Also handle merged-only langs (alias target not present as a dir)
        for (Map.Entry<String, List<LabeledSentence>> e : mergeAccum.entrySet()) {
            String lang = e.getKey();
            boolean hasCanonDir = canonDirs.stream()
                    .filter(p -> p != null)
                    .anyMatch(p -> p.getFileName().toString().equals(lang));
            if (!hasCanonDir) {
                futures.add(exec.submit(() -> {
                    try {
                        List<LabeledSentence> sentences = dedup(e.getValue());
                        if (EXCLUDED_LANGS.contains(lang)) {
                            droppedMap.put(lang, lang + "(excluded)");
                            return;
                        }
                        if (sentences.size() < minSentencesPerLang) {
                            droppedMap.put(lang, String.format(Locale.US,
                                    "%s(%,d)", lang, sentences.size()));
                            return;
                        }
                        LangPrepResult result = buildLangPrepResult(
                                sentences, lang, poolDir,
                                maxTrainPerLang, maxDevPerLang, maxTestPerLang);
                        prepResults.put(lang, result);
                    } catch (IOException ex) {
                        throw new RuntimeException("Failed to prepare lang: " + lang, ex);
                    }
                }));
            }
        }

        exec.shutdown();
        for (java.util.concurrent.Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException("Data prep worker failed", e);
            }
        }

        // Write shared dev.txt and test.txt sequentially (sorted for reproducibility)
        try (BufferedWriter devWriter = Files.newBufferedWriter(devFile, StandardCharsets.UTF_8);
             BufferedWriter testWriter = Files.newBufferedWriter(testFile, StandardCharsets.UTF_8)) {

            List<String> sortedLangs = new ArrayList<>(prepResults.keySet());
            Collections.sort(sortedLangs);

            for (String lang : sortedLangs) {
                LangPrepResult r = prepResults.get(lang);
                for (String line : r.devLines) {
                    devWriter.write(line);
                    devWriter.newLine();
                }
                for (String line : r.testLines) {
                    testWriter.write(line);
                    testWriter.newLine();
                }
                totalPool += r.poolCount;
                totalDev  += r.devLines.size();
                totalTest += r.testLines.size();
                langCounts.put(lang, r.totalSentences);
                langCount++;
            }
        }

        for (String msg : droppedMap.values()) {
            dropped.add(msg);
            droppedCount++;
        }

        // Report
        if (totalDupes > 0) {
            System.out.printf(Locale.US,
                    "Deduplicated: removed %,d duplicate"
                            + " sentences%n", totalDupes);
        }
        if (!dropped.isEmpty()) {
            dropped.sort(String::compareTo);
            System.out.println("Dropped " + droppedCount
                    + " low-resource languages: "
                    + String.join(", ", dropped));
        }
        System.out.println("Languages included: "
                + langCounts.size());
        langCounts.entrySet().stream()
                .sorted(Map.Entry
                        .<String, Integer>comparingByValue()
                        .reversed())
                .limit(20)
                .forEach(e -> System.out.printf(Locale.US,
                        "  %-12s %,d%n",
                        e.getKey(), e.getValue()));
        if (langCounts.size() > 20) {
            System.out.println("  ... and "
                    + (langCounts.size() - 20) + " more");
        }

        return new int[]{totalPool, totalDev, totalTest};
    }


    // ================================================================
    //  Helpers
    // ================================================================

    /**
     * Collect all language labels from the pool directory
     * (file names = language codes).
     */
    static String[] collectLabels(Path poolDir)
            throws IOException {
        List<String> labels = new ArrayList<>();
        try (DirectoryStream<Path> ds =
                     Files.newDirectoryStream(poolDir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) {
                    labels.add(
                            p.getFileName().toString());
                }
            }
        }
        Collections.sort(labels);
        return labels.toArray(new String[0]);
    }

    static List<LabeledSentence> dedup(
            List<LabeledSentence> sentences) {
        Set<Long> seen = new HashSet<>();
        List<LabeledSentence> unique = new ArrayList<>();
        for (LabeledSentence s : sentences) {
            long hash = DuplicateChecker.fnv1a64(s.getText());
            if (seen.add(hash)) {
                unique.add(s);
            }
        }
        return unique;
    }

    /** Holds per-language prep output before writing to shared dev/test files. */
    private static final class LangPrepResult {
        final List<String> devLines;   // "lang\tpreprocessed"
        final List<String> testLines;  // "lang\traw"
        final int poolCount;
        final int totalSentences;

        LangPrepResult(List<String> devLines, List<String> testLines,
                       int poolCount, int totalSentences) {
            this.devLines  = devLines;
            this.testLines = testLines;
            this.poolCount = poolCount;
            this.totalSentences = totalSentences;
        }
    }

    /**
     * Read, dedup, split, preprocess and write the pool file for one language.
     * Dev and test lines are returned in-memory for the caller to write to
     * shared files. This method is safe to call from multiple threads as long
     * as each language uses a distinct {@code poolDir} file path.
     */
    private static LangPrepResult buildLangPrepResult(
            List<LabeledSentence> sentences, String lang, Path poolDir,
            int maxTrainPerLang, int maxDevPerLang, int maxTestPerLang)
            throws IOException {

        Random rng = new Random(lang.hashCode() + 42L);
        Collections.shuffle(sentences, rng);

        int n = sentences.size();
        int testCount = (maxTestPerLang > 0)
                ? Math.min(maxTestPerLang, n)
                : Math.min((int) (n * 0.1f), DEFAULT_MAX_TEST_PER_LANG);
        int devCount = (maxDevPerLang > 0)
                ? Math.min(maxDevPerLang, n - testCount)
                : Math.min((int) ((n - testCount) * 0.1f / 0.9f), DEFAULT_MAX_DEV_PER_LANG);
        int poolStart = testCount + devCount;
        int poolEnd = (maxTrainPerLang > 0)
                ? Math.min(poolStart + maxTrainPerLang, n)
                : n;

        // test lines — raw
        List<String> testLines = new ArrayList<>(testCount);
        for (int i = 0; i < testCount; i++) {
            testLines.add(lang + "\t" + sentences.get(i).getText());
        }

        // dev lines — preprocessed
        List<String> devLines = new ArrayList<>(devCount);
        for (int i = testCount; i < testCount + devCount; i++) {
            devLines.add(lang + "\t" +
                    CharSoupFeatureExtractor.preprocess(sentences.get(i).getText()));
        }

        // pool file — preprocessed, written directly to disk
        Path poolFile = poolDir.resolve(lang);
        try (BufferedWriter pw = Files.newBufferedWriter(poolFile, StandardCharsets.UTF_8)) {
            for (int i = poolStart; i < poolEnd; i++) {
                pw.write(CharSoupFeatureExtractor.preprocess(sentences.get(i).getText()));
                pw.newLine();
            }
        }

        return new LangPrepResult(devLines, testLines, poolEnd - poolStart, n);
    }

    static List<LabeledSentence> readPreprocessedFile(Path file)
            throws IOException {
        List<LabeledSentence> sentences = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                sentences.add(new LabeledSentence(
                        line.substring(0, tab),
                        line.substring(tab + 1)));
            }
        }
        return sentences;
    }
}
