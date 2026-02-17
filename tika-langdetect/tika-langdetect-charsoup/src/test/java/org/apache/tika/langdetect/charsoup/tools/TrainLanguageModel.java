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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Two-pass training pipeline for the bigram language detector.
 * <p>
 * Usage: {@code TrainLanguageModel <corpusDir> <outputFile> [numBuckets] [maxPerLang]}
 * </p>
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Read corpus from {@code corpusDir} (Leipzig format: {@code lang/sentences.txt})</li>
 *   <li>Merge duplicate ISO 639-3 codes (e.g., cmn → zho, azj → aze)</li>
 *   <li>Deduplicate sentences within each language</li>
 *   <li>Split into train/dev/test (80/10/10, stratified by language)</li>
 *   <li><b>Pass 1</b>: Train initial model (AdamW → SGD, streaming)</li>
 *   <li><b>Filter</b>: Remove mislabeled sentences from training data
 *       (respecting confusable groups)</li>
 *   <li><b>Pass 2</b>: Retrain on filtered data</li>
 *   <li>Quantize to INT8</li>
 *   <li>Evaluate quantized model on test set</li>
 *   <li>Export to binary LDM1 v5 format</li>
 * </ol>
 */
public class TrainLanguageModel {

    private static final int DEFAULT_NUM_BUCKETS = 8_192;

    /**
     * Minimum sentences required per language. Languages with fewer
     * sentences are dropped before training.
     */
    private static final int MIN_SENTENCES_PER_LANG = 10_000;

    /**
     * Merge map for duplicate ISO 639-3 codes that refer to the same
     * language. Maps non-canonical → canonical.
     */
    private static final Map<String, String> LANG_MERGE_MAP;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("azj", "aze");  // Azerbaijani
        m.put("ekk", "est");  // Estonian
        m.put("pes", "fas");  // Persian (Western Farsi)
        m.put("zsm", "msa");  // Malay (Standard)
        m.put("nor", "nob");  // Norwegian → Bokmål
        m.put("plt", "mlg");  // Malagasy (Plateau)
        m.put("cmn", "zho");  // Chinese (Mandarin)
        m.put("lvs", "lav");  // Latvian (Standard)
        LANG_MERGE_MAP = Collections.unmodifiableMap(m);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println(
                    "Usage: TrainLanguageModel <corpusDir> <outputFile>"
                            + " [numBuckets] [maxPerLang]");
            System.exit(1);
        }

        Path corpusDir = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);
        int numBuckets = args.length > 2
                ? Integer.parseInt(args[2]) : DEFAULT_NUM_BUCKETS;
        int maxPerLang = args.length > 3
                ? Integer.parseInt(args[3]) : 0;

        Path prepDir = (outputFile.getParent() != null
                ? outputFile.getParent()
                : Paths.get(".")).resolve("preprocessed");

        System.out.println("=== Language Model Training Pipeline ===");
        System.out.println("Corpus:     " + corpusDir);
        System.out.println("Output:     " + outputFile);
        System.out.println("Prep dir:   " + prepDir);
        System.out.println("Buckets:    " + numBuckets);
        System.out.println("MaxPerLang: "
                + (maxPerLang > 0 ? maxPerLang : "unlimited"));
        System.out.println();

        long pipelineStart = System.nanoTime();
        long stepStart;

        // ---- Data preparation ----
        Path trainFile = prepDir.resolve("train.txt");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test.txt");

        if (Files.exists(trainFile) && Files.exists(devFile)
                && Files.exists(testFile)) {
            stepStart = System.nanoTime();
            System.out.println(
                    "--- Preprocessed splits found — skipping data prep ---");
            System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));
        } else {
            stepStart = System.nanoTime();
            System.out.println("--- Step 1: Data preparation ---");
            Files.createDirectories(prepDir);
            int[] counts = prepareDataStreaming(
                    corpusDir, prepDir, maxPerLang,
                    MIN_SENTENCES_PER_LANG);
            System.out.printf(Locale.US,
                    "Prepared: train=%,d  dev=%,d  test=%,d%n",
                    counts[0], counts[1], counts[2]);
            System.out.printf(Locale.US,
                    "  [%.1f s]%n", elapsed(stepStart));
        }

        // Load dev data into memory for evaluation
        stepStart = System.nanoTime();
        System.out.println("\n--- Loading dev data ---");
        List<LabeledSentence> devData =
                readPreprocessedFile(devFile);
        System.out.printf(Locale.US,
                "Dev: %,d sentences  [%.1f s]%n",
                devData.size(), elapsed(stepStart));

        // ---- Pass 1: Train initial model ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 2: Pass 1 — Initial training ---");
        Phase2Trainer pass1 = new Phase2Trainer(numBuckets)
                .setPreprocessed(true);
        pass1.train(trainFile, devData);
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

        // ---- Filter mislabeled sentences ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 3: Filtering mislabeled sentences ---");
        Path filteredFile = prepDir.resolve("train_filtered.txt");
        int[] filterCounts = filterMislabeled(
                pass1, trainFile, filteredFile);
        System.out.printf(Locale.US,
                "Kept %,d / %,d sentences (removed %,d = %.1f%%)%n",
                filterCounts[0], filterCounts[1],
                filterCounts[1] - filterCounts[0],
                100.0 * (filterCounts[1] - filterCounts[0])
                        / filterCounts[1]);
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

        // ---- Pass 2: Retrain on filtered data ----
        stepStart = System.nanoTime();
        System.out.println(
                "\n--- Step 4: Pass 2 — Retraining on filtered data ---");
        Phase2Trainer pass2 = new Phase2Trainer(numBuckets)
                .setPreprocessed(true);
        pass2.train(filteredFile, devData);
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

        // ---- Quantize ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 5: Quantizing to INT8 ---");
        CharSoupModel quantized = ModelQuantizer.quantize(pass2);
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

        // ---- Evaluate on test set ----
        stepStart = System.nanoTime();
        System.out.println("\n--- Step 6: Evaluating on test set ---");
        List<LabeledSentence> testData =
                readPreprocessedFile(prepDir.resolve("test.txt"));
        double testAcc = evaluateQuantized(quantized, testData);
        System.out.printf(Locale.US,
                "Test accuracy (quantized): %.4f%n", testAcc);
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

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
        System.out.printf(Locale.US, "  [%.1f s]%n", elapsed(stepStart));

        double totalMin = (System.nanoTime() - pipelineStart)
                / 1_000_000_000.0 / 60.0;
        System.out.printf(Locale.US,
                "%nDone! Total time: %.1f min%n", totalMin);
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    // ================================================================
    //  Mislabeled sentence filtering
    // ================================================================

    /**
     * Stream through the training file and write only sentences that
     * the model classifies correctly (or within a confusable group).
     *
     * @return int[2]: {kept, total}
     */
    static int[] filterMislabeled(Phase2Trainer trainer,
                                   Path inputFile,
                                   Path outputFile) throws IOException {
        int kept = 0;
        int total = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                inputFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(
                     outputFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                total++;
                String lang = line.substring(0, tab);
                String text = line.substring(tab + 1);
                String predicted = trainer.predict(text);
                if (CompareDetectors.isGroupMatch(lang, predicted)) {
                    writer.write(line);
                    writer.newLine();
                    kept++;
                }
            }
        }
        return new int[]{kept, total};
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
            Integer trueIdx = labelIndex.get(s.getLanguage());
            if (trueIdx == null) {
                continue;
            }
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
    //  Data preparation (streaming, one language at a time)
    // ================================================================

    /**
     * Prepare train/dev/test splits by streaming through the corpus
     * one language directory at a time.
     *
     * @return int[3]: {trainCount, devCount, testCount}
     */
    static int[] prepareDataStreaming(Path corpusDir, Path prepDir,
                                      int maxPerLang,
                                      int minPerLang)
            throws IOException {
        Path trainFile = prepDir.resolve("train.txt");
        Path devFile = prepDir.resolve("dev.txt");
        Path testFile = prepDir.resolve("test.txt");
        Path rawDevFile = prepDir.resolve("dev_raw.txt");
        Path rawTestFile = prepDir.resolve("test_raw.txt");

        Files.deleteIfExists(trainFile);
        Files.deleteIfExists(devFile);
        Files.deleteIfExists(testFile);
        Files.deleteIfExists(rawDevFile);
        Files.deleteIfExists(rawTestFile);

        int totalTrain = 0, totalDev = 0, totalTest = 0;
        int langCount = 0;
        int droppedCount = 0;
        long totalDupes = 0;
        Map<String, Integer> langCounts = new TreeMap<>();
        List<String> dropped = new ArrayList<>();

        float trainRatio = 0.8f;
        float devRatio = 0.1f;

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

        // Accumulate merged language data
        Map<String, List<LabeledSentence>> mergeAccum =
                new HashMap<>();

        try (BufferedWriter trainWriter = Files.newBufferedWriter(
                     trainFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND);
             BufferedWriter devWriter = Files.newBufferedWriter(
                     devFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND);
             BufferedWriter testWriter = Files.newBufferedWriter(
                     testFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND);
             BufferedWriter rawDevWriter = Files.newBufferedWriter(
                     rawDevFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND);
             BufferedWriter rawTestWriter = Files.newBufferedWriter(
                     rawTestFile, StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.APPEND)) {

            for (Path langDir : langDirs) {
                String dirName = langDir.getFileName().toString();
                if (dirName.startsWith("_")) {
                    continue;
                }

                List<LabeledSentence> sentences;
                if (maxPerLang > 0) {
                    sentences = new ArrayList<>(maxPerLang);
                    CorpusReader.readLanguageDirSampled(
                            langDir, dirName, maxPerLang,
                            sentences);
                } else {
                    sentences = new ArrayList<>();
                    CorpusReader.readLanguageDir(
                            langDir, dirName, sentences);
                }

                // Deduplicate
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

                // Apply language code merge
                String canonLang = LANG_MERGE_MAP.getOrDefault(
                        dirName, dirName);

                // Relabel
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
                            dirName, canonLang, sentences.size());
                }

                // If this lang merges with another, accumulate
                if (!canonLang.equals(dirName)) {
                    mergeAccum.computeIfAbsent(canonLang,
                            k -> new ArrayList<>())
                            .addAll(sentences);
                    continue;
                }

                // Check if accumulated merge data exists
                List<LabeledSentence> accumulated =
                        mergeAccum.remove(canonLang);
                if (accumulated != null) {
                    sentences.addAll(accumulated);
                    sentences = dedup(sentences);
                }

                if (sentences.size() < minPerLang) {
                    dropped.add(canonLang + "("
                            + sentences.size() + ")");
                    droppedCount++;
                    continue;
                }

                int[] written = writeGroup(sentences,
                        canonLang,
                        trainRatio, devRatio,
                        trainWriter, devWriter, testWriter,
                        rawDevWriter, rawTestWriter,
                        langCounts);
                totalTrain += written[0];
                totalDev += written[1];
                totalTest += written[2];

                langCount++;
                if (langCount % 50 == 0) {
                    System.out.printf(Locale.US,
                            "  Processed %d languages...%n",
                            langCount);
                }
            }

            // Flush any remaining accumulated merge data
            for (Map.Entry<String, List<LabeledSentence>> e
                    : mergeAccum.entrySet()) {
                String lang = e.getKey();
                List<LabeledSentence> sentences =
                        dedup(e.getValue());
                if (sentences.size() < minPerLang) {
                    dropped.add(lang + "("
                            + sentences.size() + ")");
                    droppedCount++;
                    continue;
                }
                int[] written = writeGroup(sentences, lang,
                        trainRatio, devRatio,
                        trainWriter, devWriter, testWriter,
                        rawDevWriter, rawTestWriter,
                        langCounts);
                totalTrain += written[0];
                totalDev += written[1];
                totalTest += written[2];
                langCount++;
            }
        }

        // Shuffle training file so languages are interleaved.
        // Without this, mini-batches would see one language
        // at a time since data is written language-by-language.
        System.out.println("Shuffling training data...");
        shuffleFile(trainFile, 42L);

        // Report
        if (totalDupes > 0) {
            System.out.printf(Locale.US,
                    "Deduplicated: removed %,d duplicate"
                            + " sentences%n",
                    totalDupes);
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
                .sorted(Map.Entry.<String, Integer>comparingByValue()
                        .reversed())
                .limit(20)
                .forEach(e -> System.out.printf(Locale.US,
                        "  %-12s %,d%n",
                        e.getKey(), e.getValue()));
        if (langCounts.size() > 20) {
            System.out.println("  ... and "
                    + (langCounts.size() - 20) + " more");
        }

        return new int[]{totalTrain, totalDev, totalTest};
    }

    /**
     * Shuffle, split, preprocess, and write one language group.
     *
     * @return int[3]: {train, dev, test} counts
     */
    private static int[] writeGroup(
            List<LabeledSentence> sentences, String lang,
            float trainRatio, float devRatio,
            BufferedWriter trainWriter,
            BufferedWriter devWriter,
            BufferedWriter testWriter,
            BufferedWriter rawDevWriter,
            BufferedWriter rawTestWriter,
            Map<String, Integer> langCounts) throws IOException {

        Random rng = new Random(lang.hashCode() + 42L);
        Collections.shuffle(sentences, rng);

        int n = sentences.size();
        int trainEnd = (int) (n * trainRatio);
        int devEnd = trainEnd + (int) (n * devRatio);

        for (int i = 0; i < trainEnd; i++) {
            String cleaned = CharSoupFeatureExtractor.preprocess(
                    sentences.get(i).getText());
            writeLine(trainWriter, lang, cleaned);
        }
        for (int i = trainEnd; i < devEnd; i++) {
            String raw = sentences.get(i).getText();
            String cleaned =
                    CharSoupFeatureExtractor.preprocess(raw);
            writeLine(devWriter, lang, cleaned);
            writeLine(rawDevWriter, lang, raw);
        }
        for (int i = devEnd; i < n; i++) {
            String raw = sentences.get(i).getText();
            String cleaned =
                    CharSoupFeatureExtractor.preprocess(raw);
            writeLine(testWriter, lang, cleaned);
            writeLine(rawTestWriter, lang, raw);
        }
        langCounts.put(lang, n);
        return new int[]{trainEnd, devEnd - trainEnd, n - devEnd};
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

    /**
     * Shuffle all lines of a file in-place. Reads all lines
     * into memory, shuffles, and rewrites. Requires enough
     * heap for the full file contents.
     */
    static void shuffleFile(Path file, long seed)
            throws IOException {
        List<String> lines = new ArrayList<>(
                Files.readAllLines(file, StandardCharsets.UTF_8));
        System.out.printf(Locale.US,
                "  Read %,d lines, shuffling...%n",
                lines.size());
        Collections.shuffle(lines, new Random(seed));
        try (BufferedWriter w = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        }
        System.out.println("  Shuffle complete.");
    }

    private static void writeLine(
            BufferedWriter writer, String lang, String text)
            throws IOException {
        writer.write(lang);
        writer.write('\t');
        writer.write(text);
        writer.newLine();
    }
}
