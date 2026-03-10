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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Two-pass training pipeline for the CharSoup language detector.
 *
 * <p><strong>WARNING — feature extraction must stay in sync with
 * {@link org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor}.</strong>
 * If you change training hyper-parameters (bucket count, n-gram order, etc.)
 * you must also update the corresponding inference constants and retrain from
 * scratch.
 *
 * <p>Data preparation (corpus → pool/dev/test splits) is handled by
 * {@link PrepareCorpus}, which owns the language-inclusion policy. Run
 * {@code PrepareCorpus} first, then point this trainer at the output directory
 * via {@code --prep-dir}. If {@code --prep-dir} already contains the three
 * expected outputs the prep step is skipped automatically.
 *
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Data prep via {@link PrepareCorpus#prepareData} (skipped if
 *       {@code --prep-dir} already populated)</li>
 *   <li><b>Pass 1</b>: Train with epoch-level resampling</li>
 *   <li><b>Filter</b>: Remove mislabeled sentences (respecting confusable
 *       groups)</li>
 *   <li><b>Pass 2</b>: Retrain on filtered pool</li>
 *   <li>Quantize to INT8, evaluate on raw test set, export</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   TrainLanguageModel --corpus &lt;dir&gt; --output &lt;file&gt;
 *       [--prep-dir &lt;dir&gt;] [--buckets N] [--max-train N]
 *       [--skip-bigrams] [--trigrams] [--eval-only &lt;model&gt;]
 * </pre>
 */
public class TrainLanguageModel {

    private static final int DEFAULT_NUM_BUCKETS = 8_192;
    private static final long DEFAULT_TARGET_EPOCH_TOTAL = 5_000_000L;
    private static final int DEFAULT_MAX_TEST_PER_LANG  = 20_000;
    private static final int DEFAULT_MAX_DEV_PER_LANG   = 20_000;
    private static final int DEFAULT_MAX_TRAIN_PER_LANG = 0; // 0 = unlimited

    // Language-inclusion policy (exclusion list, merge aliases, thresholds)
    // lives in PrepareCorpus — see that class for documentation.

    public static void main(String[] args) throws IOException {
        Path corpusDir        = null;
        Path outputFile       = null;
        int  numBuckets       = DEFAULT_NUM_BUCKETS;
        long targetEpochTotal = DEFAULT_TARGET_EPOCH_TOTAL;
        int  maxTrainPerLang  = DEFAULT_MAX_TRAIN_PER_LANG;
        int  maxDevPerLang    = DEFAULT_MAX_DEV_PER_LANG;
        int  maxTestPerLang   = DEFAULT_MAX_TEST_PER_LANG;
        boolean useTrigrams    = false;
        boolean useSkipBigrams = false;
        boolean singlePass     = false;
        Path    evalOnlyModel  = null;
        Path    prepDirOverride = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--corpus":
                    corpusDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    outputFile = Paths.get(args[++i]);
                    break;
                case "--buckets":
                    numBuckets = Integer.parseInt(args[++i]);
                    break;
                case "--max-train":
                    maxTrainPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--max-dev":
                    maxDevPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--max-test":
                    maxTestPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--epoch-total":
                    targetEpochTotal = Long.parseLong(args[++i]);
                    break;
                case "--trigrams":
                    useTrigrams = true;
                    break;
                case "--skip-bigrams":
                    useSkipBigrams = true;
                    break;
                case "--single-pass":
                    singlePass = true;
                    break;
                case "--prep-dir":
                    prepDirOverride = Paths.get(args[++i]);
                    break;
                case "--eval-only":
                    evalOnlyModel = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (corpusDir == null || (outputFile == null && evalOnlyModel == null)) {
            printUsage();
            System.exit(1);
        }

        if (evalOnlyModel != null) {
            Path prepDir2 = prepDirOverride != null ? prepDirOverride
                    : (evalOnlyModel.getParent() != null
                        ? evalOnlyModel.getParent()
                        : Paths.get(".")).resolve("preprocessed");
            System.out.println("=== Eval-only mode ===");
            System.out.println("Model:    " + evalOnlyModel);
            System.out.println("Prep dir: " + prepDir2);
            CharSoupModel evalModel;
            try (java.io.InputStream is = new java.io.BufferedInputStream(
                    Files.newInputStream(evalOnlyModel))) {
                evalModel = CharSoupModel.load(is);
            }
            runEval(evalModel, prepDir2.resolve("test_raw.txt"), "test");
            return;
        }

        Path prepDir = prepDirOverride != null ? prepDirOverride
                : (outputFile.getParent() != null
                    ? outputFile.getParent()
                    : Paths.get(".")).resolve("preprocessed");

        System.out.println("=== Language Model Training Pipeline ===");
        System.out.println("Corpus:          " + corpusDir);
        System.out.println("Output:          " + outputFile);
        System.out.println("Prep dir:        " + prepDir);
        System.out.println("Buckets:         " + numBuckets);
        System.out.printf(Locale.US,
                "TargetEpochTotal: %,d%n", targetEpochTotal);
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
            System.out.println("--- Step 1: Data preparation (PrepareCorpus) ---");
            Files.createDirectories(poolDir);
            int[] counts = PrepareCorpus.prepareData(corpusDir, prepDir,
                    maxTrainPerLang, maxDevPerLang, maxTestPerLang);
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
                .setUseSkipBigrams(useSkipBigrams)
                .setUseTrigrams(useTrigrams);
        pass1.trainWithResampling(allLabels,
                epochNum -> createEpochFile(
                        poolDir, epochFile,
                        pass1Targets, epochNum),
                devData);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        Phase2Trainer finalTrainer;
        if (singlePass) {
            System.out.println("\n--- Single-pass mode: skipping filter and Pass 2 ---");
            finalTrainer = pass1;
        } else {
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
                    .setUseSkipBigrams(useSkipBigrams)
                    .setUseTrigrams(useTrigrams);
            pass2.trainWithResampling(filteredLabels,
                    epochNum -> createEpochFile(
                            filteredPoolDir, epochFile,
                            pass2Targets, epochNum),
                    devData);
            System.out.printf(Locale.US, "  [%.1f s]%n",
                    elapsed(stepStart));
            finalTrainer = pass2;
        }

        // ---- Quantize ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 5: Quantizing to INT8 ---");
        CharSoupModel quantized = ModelQuantizer.quantize(finalTrainer);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Evaluate on raw test set ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 6: Evaluating on raw test set ---");
        List<LabeledSentence> testData =
                readPreprocessedFile(testFile);

        // Evaluate at truncated lengths
        int[] truncLengths = {20, 50, 100, 200, 500};
        List<Integer> evalLengths = new ArrayList<>();
        for (int l : truncLengths) {
            evalLengths.add(l);
        }

        System.out.printf(Locale.US,
                "%-10s  %8s  %8s  %12s  %8s  %8s%n",
                "length", "macroF1", "median", ">=0.90/total",
                "accuracy", "n");
        System.out.println(
                "----------  --------  --------"
                + "  ------------  --------  --------");

        for (int maxChars : evalLengths) {
            List<LabeledSentence> subset =
                    truncateTestData(testData, maxChars);
            EvalResult r = evaluateQuantized(quantized, subset);
            String lenLabel = String.format(
                    Locale.US, "@%d", maxChars);
            System.out.printf(Locale.US,
                    "%-10s  %8.4f  %8.4f  %5d/%-6d  %8.4f  %,8d%n",
                    lenLabel, r.macroF1, r.medianF1,
                    r.numAbove90, r.numLangs,
                    r.accuracy, r.total);
        }

        // Worst-10 per length + full TSV dump
        int worstN = 10;
        System.out.println();

        // Collect all results first (avoid re-evaluating for TSV)
        List<EvalResult> allResults = new ArrayList<>();
        for (int maxChars : evalLengths) {
            allResults.add(evaluateQuantized(
                    quantized, truncateTestData(testData, maxChars)));
        }

        for (int ri = 0; ri < evalLengths.size(); ri++) {
            int maxChars = evalLengths.get(ri);
            EvalResult r = allResults.get(ri);
            System.out.printf(Locale.US,
                    "Worst %d languages (@%d chars):%n",
                    worstN, maxChars);
            int limit = Math.min(worstN, r.perLang.size());
            for (int i = 0; i < limit; i++) {
                LangF1 lf = r.perLang.get(i);
                System.out.printf(Locale.US,
                        "  %-8s  F1=%.4f%n", lf.lang, lf.f1);
            }
            System.out.println();
        }

        // Full per-language TSV: lang, f1@20, f1@50, f1@100, f1@200, f1@500
        Path tsvFile = outputFile.resolveSibling(
                outputFile.getFileName().toString()
                        .replaceFirst("\\.bin$", "")
                + "-per-lang.tsv");
        try (BufferedWriter tsv = Files.newBufferedWriter(
                tsvFile, StandardCharsets.UTF_8)) {
            tsv.write("lang");
            for (int maxChars : evalLengths) {
                tsv.write("\tf1@" + maxChars);
            }
            tsv.newLine();
            // Use the first result's lang list (all same langs)
            EvalResult first = allResults.get(0);
            // Build a map from lang → f1 for each length
            List<Map<String, Double>> f1Maps = new ArrayList<>();
            for (EvalResult r : allResults) {
                Map<String, Double> m = new HashMap<>();
                for (LangF1 lf : r.perLang) {
                    m.put(lf.lang, lf.f1);
                }
                f1Maps.add(m);
            }
            // Collect all langs (sorted)
            List<String> allLangs = new ArrayList<>();
            for (LangF1 lf : first.perLang) {
                allLangs.add(lf.lang);
            }
            allLangs.sort(String::compareTo);
            for (String lang : allLangs) {
                tsv.write(lang);
                for (Map<String, Double> m : f1Maps) {
                    tsv.write(String.format(Locale.US,
                            "\t%.4f", m.getOrDefault(lang, 0.0)));
                }
                tsv.newLine();
            }
        }
        System.out.println("Per-language TSV: " + tsvFile);

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

    private static void printUsage() {
        System.err.println("Usage: TrainLanguageModel"
                + " --corpus <dir> --output <file>"
                + " [--prep-dir <dir>] [--buckets N] [--max-train N]"
                + " [--skip-bigrams] [--trigrams] [--single-pass]"
                + " [--eval-only <model>]");
        System.err.println("  --single-pass  skip filterPool + Pass 2 (Pass 1 only)");
        System.err.println("  Data preparation is handled by PrepareCorpus."
                + " Run PrepareCorpus first, or provide --corpus so this"
                + " trainer can call PrepareCorpus.prepareData automatically.");
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
                FeatureExtractor ext = trainer.getExtractor();
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

    private static void runEval(CharSoupModel model,
                                Path dataFile,
                                String label) throws IOException {
        List<LabeledSentence> data = readPreprocessedFile(dataFile);
        System.out.printf(Locale.US,
                "%n--- Eval on %s (%,d sentences, %d langs) ---%n",
                label, data.size(),
                data.stream().map(LabeledSentence::getLanguage)
                        .distinct().count());
        int[] lengths = {20, 50, 100, 200, 500};
        List<Integer> evalLengths = new ArrayList<>();
        for (int l : lengths) {
            evalLengths.add(l);
        }
        System.out.printf(Locale.US,
                "%-10s  %8s  %8s  %12s  %8s%n",
                "length", "macroF1", "median",
                ">=0.90/total", "accuracy");
        System.out.println(
                "----------  --------  --------"
                        + "  ------------  --------");
        List<EvalResult> results = new ArrayList<>();
        for (int maxChars : lengths) {
            List<LabeledSentence> subset =
                    truncateTestData(data, maxChars);
            EvalResult r = evaluateQuantized(model, subset);
            results.add(r);
            System.out.printf(Locale.US,
                    "%-10s  %8.4f  %8.4f  %5d/%-6d  %8.4f%n",
                    "@" + maxChars, r.macroF1, r.medianF1,
                    r.numAbove90, r.numLangs, r.accuracy);
        }
        // Worst-10 at @500
        EvalResult last = results.get(results.size() - 1);
        System.out.printf(Locale.US,
                "%nWorst 10 languages (@500 chars, %s):%n", label);
        int limit = Math.min(10, last.perLang.size());
        for (int i = 0; i < limit; i++) {
            LangF1 lf = last.perLang.get(i);
            System.out.printf(Locale.US,
                    "  %-8s  F1=%.4f%n", lf.lang, lf.f1);
        }
        // TSV dump
        Path tsvFile = dataFile.resolveSibling(
                label.replace("-", "_") + "-per-lang.tsv");
        try (BufferedWriter tsv = Files.newBufferedWriter(
                tsvFile, StandardCharsets.UTF_8)) {
            tsv.write("lang");
            for (int l : lengths) {
                tsv.write("\tf1@" + l);
            }
            tsv.newLine();
            Map<String, Double>[] f1Maps =
                    new HashMap[lengths.length];
            for (int ri = 0; ri < results.size(); ri++) {
                f1Maps[ri] = new HashMap<>();
                for (LangF1 lf : results.get(ri).perLang) {
                    f1Maps[ri].put(lf.lang, lf.f1);
                }
            }
            List<String> allLangs = new ArrayList<>();
            for (LangF1 lf : last.perLang) {
                allLangs.add(lf.lang);
            }
            allLangs.sort(String::compareTo);
            for (String lang : allLangs) {
                tsv.write(lang);
                for (Map<String, Double> m : f1Maps) {
                    tsv.write(String.format(Locale.US,
                            "\t%.4f", m.getOrDefault(lang, 0.0)));
                }
                tsv.newLine();
            }
        }
        System.out.println("TSV written: " + tsvFile);

        // Confusion analysis at @500 — high-resource langs + worst 5
        List<LabeledSentence> at500 = truncateTestData(data, 500);
        Set<String> analysisTargets = new LinkedHashSet<>(
                Arrays.asList("eng", "deu", "fra", "spa", "rus",
                        "zho", "ara", "por"));
        for (int i = 0; i < Math.min(5, last.perLang.size()); i++) {
            analysisTargets.add(last.perLang.get(i).lang);
        }
        for (String lang : analysisTargets) {
            printConfusion(model, at500, lang, 15);
        }
    }

    private static void printConfusion(CharSoupModel model,
                                       List<LabeledSentence> data,
                                       String targetLang,
                                       int topN) {
        FeatureExtractor extractor = model.createExtractor();
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < model.getNumClasses(); i++) {
            labelIndex.put(model.getLabel(i), i);
        }
        Integer trueIdx = labelIndex.get(targetLang);
        if (trueIdx == null) {
            System.out.println("Language not in model: " + targetLang);
            return;
        }

        // predicted → count, for sentences whose true label = targetLang
        Map<String, Integer> predictedWhen = new HashMap<>();
        // true → count, for sentences whose predicted label = targetLang (false positives)
        Map<String, Integer> trueWhen = new HashMap<>();
        int total = 0;
        int correct = 0;
        for (LabeledSentence s : data) {
            if (targetLang.equals(s.getLanguage())) {
                total++;
                int[] features = extractor.extract(s.getText());
                float[] probs = model.predict(features);
                int predicted = argmax(probs);
                String predLabel = model.getLabel(predicted);
                if (predicted == trueIdx) {
                    correct++;
                }
                predictedWhen.merge(predLabel, 1, Integer::sum);
            } else {
                Integer ti = labelIndex.get(s.getLanguage());
                if (ti == null) {
                    continue;
                }
                int[] features = extractor.extract(s.getText());
                float[] probs = model.predict(features);
                int predicted = argmax(probs);
                if (predicted == trueIdx) {
                    trueWhen.merge(s.getLanguage(), 1, Integer::sum);
                }
            }
        }

        double prec = (correct + trueWhen.values().stream()
                .mapToInt(Integer::intValue).sum()) > 0
                ? (double) correct
                / (correct + trueWhen.values().stream()
                        .mapToInt(Integer::intValue).sum())
                : 0.0;
        double rec = total > 0 ? (double) correct / total : 0.0;
        System.out.printf(Locale.US,
                "%n--- Confusion for '%s' @500 chars"
                        + " (correct=%d/%d, prec=%.3f, rec=%.3f) ---%n",
                targetLang, correct, total, prec, rec);

        // Top misclassifications (predicted X when true=targetLang)
        final int finalTotal = total;
        System.out.printf(Locale.US,
                "  True=%s, predicted as (top %d):%n",
                targetLang, topN);
        predictedWhen.entrySet().stream()
                .filter(e -> !e.getKey().equals(targetLang))
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> System.out.printf(Locale.US,
                        "    %-8s  %5d (%.1f%%)%n",
                        e.getKey(), e.getValue(),
                        100.0 * e.getValue() / finalTotal));

        // Top false positives (predicted targetLang when true=X)
        int fpTotal = trueWhen.values().stream()
                .mapToInt(Integer::intValue).sum();
        System.out.printf(Locale.US,
                "  Predicted=%s when actually (top %d, %d total FP):%n",
                targetLang, topN, fpTotal);
        trueWhen.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> System.out.printf(Locale.US,
                        "    %-8s  %5d%n", e.getKey(), e.getValue()));
    }

    static class LangF1 {
        final String lang;
        final double f1;

        LangF1(String lang, double f1) {
            this.lang = lang;
            this.f1 = f1;
        }
    }

    static class EvalResult {
        final double macroF1;
        final double medianF1;
        final double accuracy;
        final int numLangs;
        final int numAbove90;
        final int total;
        final List<LangF1> perLang; // sorted worst-first

        EvalResult(double macroF1, double medianF1,
                   double accuracy, int numLangs,
                   int numAbove90, int total,
                   List<LangF1> perLang) {
            this.macroF1 = macroF1;
            this.medianF1 = medianF1;
            this.accuracy = accuracy;
            this.numLangs = numLangs;
            this.numAbove90 = numAbove90;
            this.total = total;
            this.perLang = perLang;
        }
    }

    /**
     * Evaluate quantized model with macro-F1, median per-language F1,
     * micro accuracy, and per-language breakdown.
     * Test data is raw text — the extractor runs the full pipeline.
     */
    static EvalResult evaluateQuantized(CharSoupModel model,
                                        List<LabeledSentence> data) {
        FeatureExtractor extractor = model.createExtractor();
        int n = model.getNumClasses();
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < n; i++) {
            labelIndex.put(model.getLabel(i), i);
        }

        int[] tp = new int[n];
        int[] fp = new int[n];
        int[] fn = new int[n];
        int correct = 0;
        int total = 0;

        for (LabeledSentence s : data) {
            Integer trueIdx = labelIndex.get(s.getLanguage());
            if (trueIdx == null) {
                continue;
            }
            int[] features = extractor.extract(s.getText());
            float[] probs = model.predict(features);
            int predicted = argmax(probs);
            if (predicted == trueIdx) {
                tp[trueIdx]++;
                correct++;
            } else {
                fn[trueIdx]++;
                fp[predicted]++;
            }
            total++;
        }

        List<LangF1> perLang = new ArrayList<>();
        for (int c = 0; c < n; c++) {
            if (tp[c] + fn[c] == 0) {
                continue;
            }
            double prec = tp[c] + fp[c] > 0
                    ? (double) tp[c] / (tp[c] + fp[c]) : 0.0;
            double rec = (double) tp[c] / (tp[c] + fn[c]);
            double f1 = prec + rec > 0
                    ? 2.0 * prec * rec / (prec + rec) : 0.0;
            perLang.add(new LangF1(model.getLabel(c), f1));
        }
        // sort worst-first for easy scanning
        perLang.sort(Comparator.comparingDouble(x -> x.f1));

        int activeLangs = perLang.size();
        double f1Sum = 0;
        int numAbove90 = 0;
        for (LangF1 lf : perLang) {
            f1Sum += lf.f1;
            if (lf.f1 >= 0.90) {
                numAbove90++;
            }
        }
        double macroF1 = activeLangs > 0 ? f1Sum / activeLangs : 0.0;

        double medianF1 = 0.0;
        if (activeLangs > 0) {
            int mid = activeLangs / 2;
            medianF1 = activeLangs % 2 == 1
                    ? perLang.get(mid).f1
                    : (perLang.get(mid - 1).f1
                            + perLang.get(mid).f1) / 2.0;
        }

        double accuracy = total > 0 ? (double) correct / total : 0.0;
        return new EvalResult(macroF1, medianF1, accuracy,
                activeLangs, numAbove90, total, perLang);
    }

    /**
     * Return a copy of {@code data} where each sentence's text is
     * truncated to {@code maxChars} Unicode code units. Only sentences
     * that have at least one character after truncation are included.
     * Sentences already shorter than {@code maxChars} are included as-is.
     */
    static List<LabeledSentence> truncateTestData(
            List<LabeledSentence> data, int maxChars) {
        List<LabeledSentence> result =
                new ArrayList<>(data.size());
        for (LabeledSentence s : data) {
            String text = s.getText();
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            if (!text.isEmpty()) {
                result.add(new LabeledSentence(
                        s.getLanguage(), text));
            }
        }
        return result;
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
