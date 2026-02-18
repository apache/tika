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
import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

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
    private static final int MIN_SENTENCES_PER_LANG = 10_000;
    private static final int MAX_TEST_PER_LANG = 20_000;
    private static final int MAX_DEV_PER_LANG = 20_000;

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
        if (args.length < 2) {
            System.err.println(
                    "Usage: TrainLanguageModel <corpusDir> <outputFile>"
                            + " [numBuckets] [targetEpochTotal]");
            System.exit(1);
        }

        Path corpusDir = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);
        int numBuckets = args.length > 2
                ? Integer.parseInt(args[2]) : DEFAULT_NUM_BUCKETS;
        long targetEpochTotal = args.length > 3
                ? Long.parseLong(args[3])
                : DEFAULT_TARGET_EPOCH_TOTAL;

        Path prepDir = (outputFile.getParent() != null
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
            System.out.println("--- Step 1: Data preparation ---");
            Files.createDirectories(poolDir);
            int[] counts = prepareData(corpusDir, prepDir);
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
                .setPreprocessed(true);
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
                .setPreprocessed(true);
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
        CharSoupModel quantized = ModelQuantizer.quantize(pass2);
        System.out.printf(Locale.US, "  [%.1f s]%n",
                elapsed(stepStart));

        // ---- Evaluate on raw test set ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 6: Evaluating on raw test set ---");
        List<LabeledSentence> testData =
                readPreprocessedFile(testFile);
        double testAcc = evaluateQuantized(quantized, testData);
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

    static double evaluateQuantized(CharSoupModel model,
                                    List<LabeledSentence> data) {
        FeatureExtractor extractor = model.createExtractor();
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < model.getNumClasses(); i++) {
            labelIndex.put(model.getLabel(i), i);
        }

        int correct = 0;
        int total = 0;
        for (LabeledSentence s : data) {
            Integer trueIdx =
                    labelIndex.get(s.getLanguage());
            if (trueIdx == null) {
                continue;
            }
            // Test data is raw; extract runs full pipeline
            int[] features = extractor.extract(s.getText());
            float[] probs = model.predict(features);
            int predicted = argmax(probs);
            if (predicted == trueIdx) {
                correct++;
            }
            total++;
        }
        return total > 0 ? (double) correct / total : 0.0;
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
     *   <li>{@code pool/} — per-language preprocessed files
     *       for training</li>
     *   <li>{@code dev.txt} — fixed dev set, preprocessed</li>
     *   <li>{@code test_raw.txt} — fixed test set, raw</li>
     * </ul>
     *
     * @return int[3]: {poolCount, devCount, testCount}
     */
    static int[] prepareData(Path corpusDir, Path prepDir)
            throws IOException {
        Path poolDir = prepDir.resolve("pool");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test_raw.txt");

        Files.createDirectories(poolDir);

        int totalPool = 0, totalDev = 0, totalTest = 0;
        int langCount = 0;
        int droppedCount = 0;
        long totalDupes = 0;
        Map<String, Integer> langCounts = new TreeMap<>();
        List<String> dropped = new ArrayList<>();

        List<Path> langDirs = new ArrayList<>();
        try (DirectoryStream<Path> dirs =
                     Files.newDirectoryStream(corpusDir,
                             Files::isDirectory)) {
            for (Path d : dirs) {
                langDirs.add(d);
            }
        }
        langDirs.sort((a, b) -> a.getFileName().toString()
                .compareTo(b.getFileName().toString()));

        Map<String, List<LabeledSentence>> mergeAccum =
                new HashMap<>();

        try (BufferedWriter devWriter = Files.newBufferedWriter(
                     devFile, StandardCharsets.UTF_8);
             BufferedWriter testWriter = Files.newBufferedWriter(
                     testFile, StandardCharsets.UTF_8)) {

            for (Path langDir : langDirs) {
                String dirName =
                        langDir.getFileName().toString();
                if (dirName.startsWith("_")) {
                    continue;
                }

                List<LabeledSentence> sentences =
                        new ArrayList<>();
                CorpusReader.readLanguageDir(
                        langDir, dirName, sentences);

                int beforeDedup = sentences.size();
                sentences = dedup(sentences);
                int removed = beforeDedup - sentences.size();
                if (removed > 0) {
                    totalDupes += removed;
                    if (removed > beforeDedup / 5) {
                        System.out.printf(Locale.US,
                                "  %s: removed %,d/%,d dupes"
                                        + " (%.1f%%)%n",
                                dirName, removed, beforeDedup,
                                100.0 * removed / beforeDedup);
                    }
                }

                String canonLang = LANG_MERGE_MAP.getOrDefault(
                        dirName, dirName);

                if (!canonLang.equals(dirName)) {
                    List<LabeledSentence> relabeled =
                            new ArrayList<>(sentences.size());
                    for (LabeledSentence s : sentences) {
                        relabeled.add(new LabeledSentence(
                                canonLang, s.getText()));
                    }
                    sentences = relabeled;
                    System.out.printf(Locale.US,
                            "  %s → %s (%,d sentences)%n",
                            dirName, canonLang,
                            sentences.size());
                }

                if (!canonLang.equals(dirName)) {
                    mergeAccum.computeIfAbsent(canonLang,
                            k -> new ArrayList<>())
                            .addAll(sentences);
                    continue;
                }

                List<LabeledSentence> accumulated =
                        mergeAccum.remove(canonLang);
                if (accumulated != null) {
                    sentences.addAll(accumulated);
                    sentences = dedup(sentences);
                }

                if (EXCLUDED_LANGS.contains(canonLang)) {
                    dropped.add(canonLang + "(excluded)");
                    droppedCount++;
                    continue;
                }

                if (sentences.size() < MIN_SENTENCES_PER_LANG) {
                    dropped.add(canonLang + "("
                            + sentences.size() + ")");
                    droppedCount++;
                    continue;
                }

                int[] written = writeLanguageSplit(
                        sentences, canonLang, poolDir,
                        devWriter, testWriter);
                totalPool += written[0];
                totalDev += written[1];
                totalTest += written[2];
                langCounts.put(canonLang, sentences.size());

                langCount++;
                if (langCount % 50 == 0) {
                    System.out.printf(Locale.US,
                            "  Processed %d languages...%n",
                            langCount);
                }
            }

            // Flush remaining merged languages
            for (Map.Entry<String, List<LabeledSentence>> e
                    : mergeAccum.entrySet()) {
                String lang = e.getKey();
                List<LabeledSentence> sentences =
                        dedup(e.getValue());
                if (EXCLUDED_LANGS.contains(lang)) {
                    dropped.add(lang + "(excluded)");
                    droppedCount++;
                    continue;
                }
                if (sentences.size() < MIN_SENTENCES_PER_LANG) {
                    dropped.add(lang + "("
                            + sentences.size() + ")");
                    droppedCount++;
                    continue;
                }
                int[] written = writeLanguageSplit(
                        sentences, lang, poolDir,
                        devWriter, testWriter);
                totalPool += written[0];
                totalDev += written[1];
                totalTest += written[2];
                langCounts.put(lang, sentences.size());
                langCount++;
            }
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

    /**
     * Split one language's sentences into test, dev, and pool.
     * <ul>
     *   <li>Test: 10% (max 20K), written raw</li>
     *   <li>Dev: 10% (max 20K), written preprocessed</li>
     *   <li>Pool: rest, written preprocessed to per-language file</li>
     * </ul>
     *
     * @return int[3]: {pool, dev, test} counts
     */
    private static int[] writeLanguageSplit(
            List<LabeledSentence> sentences, String lang,
            Path poolDir,
            BufferedWriter devWriter,
            BufferedWriter testWriter) throws IOException {

        Random rng = new Random(lang.hashCode() + 42L);
        Collections.shuffle(sentences, rng);

        int n = sentences.size();
        int testCount = Math.min(
                (int) (n * 0.1f), MAX_TEST_PER_LANG);
        int devCount = Math.min(
                (int) ((n - testCount) * 0.1f / 0.9f),
                MAX_DEV_PER_LANG);
        int poolStart = testCount + devCount;

        // Test: raw text
        for (int i = 0; i < testCount; i++) {
            String raw = sentences.get(i).getText();
            testWriter.write(lang);
            testWriter.write('\t');
            testWriter.write(raw);
            testWriter.newLine();
        }

        // Dev: preprocessed
        for (int i = testCount; i < testCount + devCount; i++) {
            String cleaned =
                    CharSoupFeatureExtractor.preprocess(
                            sentences.get(i).getText());
            devWriter.write(lang);
            devWriter.write('\t');
            devWriter.write(cleaned);
            devWriter.newLine();
        }

        // Pool: preprocessed, one file per language
        Path poolFile = poolDir.resolve(lang);
        try (BufferedWriter pw = Files.newBufferedWriter(
                poolFile, StandardCharsets.UTF_8)) {
            for (int i = poolStart; i < n; i++) {
                String cleaned =
                        CharSoupFeatureExtractor.preprocess(
                                sentences.get(i).getText());
                pw.write(cleaned);
                pw.newLine();
            }
        }

        return new int[]{n - poolStart, devCount, testCount};
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
