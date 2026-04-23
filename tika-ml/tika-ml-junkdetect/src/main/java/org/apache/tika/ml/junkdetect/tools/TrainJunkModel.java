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
package org.apache.tika.ml.junkdetect.tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Trains the junk detector model from per-script corpus files produced by
 * {@link BuildJunkTrainingData}.
 *
 * <p>For each script group (identified by a {@code {script}.train.gz} file),
 * three features are trained and then combined by a per-script logistic
 * regression classifier:
 * <ol>
 *   <li><b>Byte-bigram log-probability</b>: 256×256 table of log P(b|a) over
 *       consecutive byte pairs in the UTF-8 encoding.</li>
 *   <li><b>Unicode named-block transition log-probability</b>: N×N table of
 *       log P(block_b | block_a), where block ID is determined by
 *       {@link Character.UnicodeBlock#of(int)} — one of the ~327 named Unicode
 *       blocks plus one extra bucket for unassigned codepoints.</li>
 *   <li><b>Control-byte fraction</b>: fraction of bytes in control-character
 *       ranges ([0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F]).  Stored as
 *       {@code −fraction} so the z-score convention matches the other features
 *       (higher = cleaner).</li>
 * </ol>
 *
 * <p>All three features are calibrated (mu/sigma) on the dev split so their
 * z-scores are on a common scale.  A per-script binary logistic regression
 * classifier is then fit on (z1, z2, z3) using clean dev windows and corrupted
 * versions (inject@5%, char-shuffle) as training examples.  The learned weights
 * replace the fixed equal-weight average, allowing the model to automatically
 * downweight noisy features (e.g. high-variance block transitions for MYANMAR)
 * and upweight informative ones (e.g. control-byte fraction for inject@0.01).
 *
 * <p>At inference, the final score is the linear combination
 * {@code w1*z1 + w2*z2 + w3*z3 + bias}; positive values indicate clean text.
 * The natural threshold is 0 (probability 0.5); use a negative threshold for
 * more conservative junk detection.
 *
 * <p>Output format: {@code JUNKDET1} gzipped binary, <b>version 3</b>.
 * Version 1 (bigrams only) and version 2 (equal-weight average) files can
 * still be loaded by {@code JunkDetector}.
 *
 * <pre>
 *   [8 bytes]  magic "JUNKDET1" (ASCII)
 *   [1 byte]   version = 3
 *   [4 bytes]  num_scripts (big-endian int)
 *   [2 bytes]  block_N — number of distinct named Unicode blocks + 1 (unassigned)
 *   for each script (sorted by name):
 *     [2 bytes]       name length (big-endian ushort)
 *     [name bytes]    script name (UTF-8)
 *     // Feature 1 — byte bigrams
 *     [4 bytes]       mu1   (float32 big-endian)
 *     [4 bytes]       sigma1 (float32 big-endian)
 *     [65536×4 bytes] byte-bigram log-prob table (256×256)
 *     // Feature 2 — block transitions
 *     [4 bytes]       mu2   (float32 big-endian)
 *     [4 bytes]       sigma2 (float32 big-endian)
 *     [block_N²×4 bytes] block-transition log-prob table
 *     // Feature 3 — control-byte fraction
 *     [4 bytes]       mu3   (float32 big-endian)
 *     [4 bytes]       sigma3 (float32 big-endian)
 *     // Linear classifier weights
 *     [1 byte]        num_features (= 3)
 *     [4 bytes]       w1   (float32 big-endian)
 *     [4 bytes]       w2   (float32 big-endian)
 *     [4 bytes]       w3   (float32 big-endian)
 *     [4 bytes]       bias (float32 big-endian)
 * </pre>
 */
public class TrainJunkModel {

    static final String MAGIC = "JUNKDET1";
    static final byte VERSION = 3;

    /** Number of clean (and corrupted) windows used to train the per-script classifier. */
    static final int NUM_CLASSIFIER_SAMPLES = 500;

    /** Fraction of characters replaced with control characters for inject distortion. */
    static final double CLASSIFIER_INJECT_RATE = 0.05;

    /**
     * Minimum sigma for the control-byte feature.  Because clean dev text
     * typically has zero control bytes in every sentence, the sample standard
     * deviation collapses to 0 and would be clamped to 1.0 by the generic
     * {@link #muSigma} helper — making the feature useless.  This floor
     * ensures a 1% control-byte injection ({@code inject@0.01}) produces
     * approximately z = −2, providing meaningful signal.
     */
    static final float CONTROL_BYTE_MIN_SIGMA = 0.005f;

    /**
     * Target byte-lengths used for calibration sampling, matching the evaluator defaults.
     */
    static final int[] CALIB_LENGTHS = {15, 30, 50, 100, 200};

    /**
     * Number of random byte-window samples drawn from the dev set for calibration.
     */
    static final int CALIB_SAMPLES = 5000;

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "datasets", "madlad", "junkdetect");
        Path output = dataDir.resolve("junkdetect.bin");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data-dir":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    output = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        System.out.println("=== TrainJunkModel ===");
        System.out.println("  data-dir: " + dataDir);
        System.out.println("  output:   " + output);

        if (!Files.isDirectory(dataDir)) {
            System.err.println("ERROR: data-dir not found: " + dataDir);
            System.exit(1);
        }

        System.out.print("Building Unicode named-block index... ");
        long t0 = System.currentTimeMillis();
        Map<Character.UnicodeBlock, Integer> blockIndex = buildBlockIndex();
        int blockN = blockIndex.size() + 1; // +1 for unassigned bucket
        System.out.printf("%d named blocks → table size %d×%d (%dms)%n",
                blockIndex.size(), blockN, blockN, System.currentTimeMillis() - t0);

        TreeMap<String, float[]> bigramTables        = new TreeMap<>();
        TreeMap<String, float[]> bigramCalibrations  = new TreeMap<>();
        TreeMap<String, float[]> blockTables         = new TreeMap<>();
        TreeMap<String, float[]> blockCalibrations   = new TreeMap<>();
        TreeMap<String, float[]> controlCalibrations = new TreeMap<>();
        TreeMap<String, float[]> classifierWeights   = new TreeMap<>();

        try (var stream = Files.list(dataDir)) {
            List<Path> trainFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".train.gz"))
                    .sorted()
                    .toList();

            if (trainFiles.isEmpty()) {
                System.err.println("ERROR: no *.train.gz files found in " + dataDir);
                System.exit(1);
            }

            for (Path trainFile : trainFiles) {
                String filename = trainFile.getFileName().toString();
                String script = filename.substring(0, filename.length() - ".train.gz".length())
                        .toUpperCase();
                Path devFile = trainFile.getParent().resolve(
                        filename.replace(".train.gz", ".dev.gz"));

                System.out.printf("%n--- %s ---%n", script);

                t0 = System.currentTimeMillis();
                System.out.print("  Training byte-bigram table...      ");
                float[] bigramTable = trainBigramTable(trainFile);
                System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

                t0 = System.currentTimeMillis();
                System.out.print("  Training named-block table...      ");
                float[] blockTable = trainBlockTable(trainFile, blockIndex, blockN);
                System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

                float[] bigramCal  = new float[]{0f, 1f};
                float[] blockCal   = new float[]{0f, 1f};
                float[] controlCal = new float[]{0f, 1f};
                // Default: equal-weight average (w=[1/3,1/3,1/3], bias=0)
                float[] weights    = new float[]{1f / 3, 1f / 3, 1f / 3, 0f};

                if (Files.exists(devFile)) {
                    t0 = System.currentTimeMillis();
                    System.out.print("  Calibrating byte bigrams on dev... ");
                    bigramCal = computeBigramCalibration(devFile, bigramTable);
                    System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                            bigramCal[0], bigramCal[1], System.currentTimeMillis() - t0);

                    t0 = System.currentTimeMillis();
                    System.out.print("  Calibrating named blocks on dev... ");
                    blockCal = computeBlockCalibration(devFile, blockTable, blockIndex, blockN);
                    System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                            blockCal[0], blockCal[1], System.currentTimeMillis() - t0);

                    t0 = System.currentTimeMillis();
                    System.out.print("  Calibrating control bytes on dev... ");
                    controlCal = computeControlByteCalibration(devFile);
                    System.out.printf("done — mu=%.6f sigma=%.6f (%dms)%n",
                            controlCal[0], controlCal[1], System.currentTimeMillis() - t0);

                    t0 = System.currentTimeMillis();
                    System.out.print("  Training linear classifier...      ");
                    weights = trainClassifier(devFile, bigramTable, bigramCal,
                            blockTable, blockCal, controlCal, blockIndex, blockN);
                    System.out.printf("done — w=[%.3f,%.3f,%.3f] bias=%.3f (%dms)%n",
                            weights[0], weights[1], weights[2], weights[3],
                            System.currentTimeMillis() - t0);
                } else {
                    System.out.println("  WARNING: no dev file found, using uncalibrated defaults");
                }

                bigramTables.put(script, bigramTable);
                bigramCalibrations.put(script, bigramCal);
                blockTables.put(script, blockTable);
                blockCalibrations.put(script, blockCal);
                controlCalibrations.put(script, controlCal);
                classifierWeights.put(script, weights);
            }
        }

        System.out.printf("%nWriting model (%d scripts, blockN=%d) → %s%n",
                bigramTables.size(), blockN, output);
        saveModel(bigramTables, bigramCalibrations,
                  blockTables, blockCalibrations,
                  controlCalibrations, classifierWeights, blockN, output);
        System.out.printf("Model size: %,d bytes (%.1f MB)%n",
                Files.size(output), Files.size(output) / 1_000_000.0);
        System.out.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Block index
    // -----------------------------------------------------------------------

    /**
     * Builds a stable ordered mapping from {@link Character.UnicodeBlock} to integer index
     * by scanning all valid Unicode codepoints in order (U+0000 to U+10FFFF) and
     * recording each block's first occurrence.
     *
     * <p>The resulting map has {@code size()} entries (one per named block).
     * Callers should reserve index {@code size()} as the "unassigned" bucket
     * (for codepoints where {@code UnicodeBlock.of(cp)} returns null).
     *
     * @return immutable ordered map: UnicodeBlock → integer index [0, size)
     */
    static Map<Character.UnicodeBlock, Integer> buildBlockIndex() {
        LinkedHashMap<Character.UnicodeBlock, Integer> index = new LinkedHashMap<>();
        for (int cp = 0; cp <= 0x10FFFF; cp++) {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
            if (b != null) index.putIfAbsent(b, index.size());
        }
        return Collections.unmodifiableMap(index);
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    /**
     * Trains a 256×256 byte-bigram log-probability table from a gzipped sentence file.
     *
     * @return float[65536] where index {@code a*256+b} = log P(b|a)
     */
    static float[] trainBigramTable(Path trainGz) throws IOException {
        long[] counts = new long[65536];
        long totalBigrams = 0;
        long sentences = 0;

        try (BufferedReader r = openGzipped(trainGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i + 1 < bytes.length; i++) {
                    counts[((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)]++;
                    totalBigrams++;
                }
                sentences++;
            }
        }

        System.out.printf("    %,d sentences, %,d byte bigrams%n", sentences, totalBigrams);
        return laplaceSmoothLogProb(counts, 256);
    }

    /**
     * Trains a {@code blockN×blockN} named-Unicode-block transition log-probability table.
     *
     * @param blockIndex ordered mapping from UnicodeBlock to index [0, blockIndex.size())
     * @param blockN     blockIndex.size() + 1 (includes the null bucket)
     * @return float[blockN*blockN] where index {@code a*blockN+b} = log P(block_b | block_a)
     */
    static float[] trainBlockTable(Path trainGz,
                                   Map<Character.UnicodeBlock, Integer> blockIndex,
                                   int blockN) throws IOException {
        long[] counts = new long[blockN * blockN];
        int nullId = blockN - 1;
        long totalBigrams = 0;
        long sentences = 0;

        try (BufferedReader r = openGzipped(trainGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                int prev = -1;
                for (int i = 0; i < line.length(); ) {
                    int cp = line.codePointAt(i);
                    Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
                    int blockId = b != null ? blockIndex.getOrDefault(b, nullId) : nullId;
                    if (prev >= 0) {
                        counts[prev * blockN + blockId]++;
                        totalBigrams++;
                    }
                    prev = blockId;
                    i += Character.charCount(cp);
                }
                sentences++;
            }
        }

        System.out.printf("    %,d sentences, %,d block bigrams%n", sentences, totalBigrams);
        return laplaceSmoothLogProb(counts, blockN);
    }

    /**
     * Applies Laplace (add-1) smoothing per row and converts to log-probabilities.
     *
     * @param counts raw bigram counts, length = size*size
     * @param size   number of distinct symbols (256 for byte table, blockN for block table)
     * @return float[size*size] log-prob table
     */
    private static float[] laplaceSmoothLogProb(long[] counts, int size) {
        float[] table = new float[size * size];
        for (int a = 0; a < size; a++) {
            long rowTotal = size; // add-1 pseudocount for each possible next symbol
            for (int b = 0; b < size; b++) {
                rowTotal += counts[a * size + b];
            }
            for (int b = 0; b < size; b++) {
                table[a * size + b] =
                        (float) Math.log((counts[a * size + b] + 1.0) / rowTotal);
            }
        }
        return table;
    }

    // -----------------------------------------------------------------------
    // Calibration
    // -----------------------------------------------------------------------

    /**
     * Loads all sentences from a gzipped file and draws {@code nSamples} random
     * byte-window substrings of target lengths cycling through {@code lengths}.
     *
     * <p>This mirrors the evaluator's {@code pickSubstring}: takes a random
     * UTF-8-aligned window of {@code targetLen} bytes from a randomly chosen
     * sentence, or the whole sentence if it is shorter.
     *
     * @param nSamples number of windows to sample
     * @param lengths  target byte-lengths to cycle through (round-robin)
     * @param seed     RNG seed for reproducibility
     */
    static List<String> sampleSubstrings(Path devGz, int nSamples,
                                         int[] lengths, long seed) throws IOException {
        List<byte[]> sentenceBytes = new ArrayList<>();
        try (BufferedReader r = openGzipped(devGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                byte[] b = line.getBytes(StandardCharsets.UTF_8);
                if (b.length >= 2) sentenceBytes.add(b);
            }
        }
        if (sentenceBytes.isEmpty()) return Collections.emptyList();

        Random rng = new Random(seed);
        List<String> result = new ArrayList<>(nSamples);
        for (int i = 0; i < nSamples; i++) {
            byte[] bytes = sentenceBytes.get(rng.nextInt(sentenceBytes.size()));
            int targetLen = lengths[i % lengths.length];

            if (bytes.length <= targetLen) {
                result.add(new String(bytes, StandardCharsets.UTF_8));
                continue;
            }
            int start = rng.nextInt(bytes.length - targetLen);
            while (start > 0 && (bytes[start] & 0xC0) == 0x80) {
                start--;
            }
            int end = Math.min(start + targetLen, bytes.length);
            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end++;
            }
            result.add(new String(bytes, start, end - start, StandardCharsets.UTF_8));
        }
        return result;
    }

    /** @return float[2] = {mu, sigma} of byte-bigram mean log-prob on dev windows */
    static float[] computeBigramCalibration(Path devGz, float[] bigramTable) throws IOException {
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 42);
        List<Double> scores = new ArrayList<>(windows.size());
        for (String window : windows) {
            byte[] bytes = window.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 2) continue;
            double sum = 0;
            for (int i = 0; i + 1 < bytes.length; i++) {
                sum += bigramTable[((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)];
            }
            scores.add(sum / (bytes.length - 1));
        }
        System.out.printf("    %,d dev windows%n", scores.size());
        return muSigma(scores);
    }

    /** @return float[2] = {mu, sigma} of block-transition mean log-prob on dev windows */
    static float[] computeBlockCalibration(Path devGz, float[] blockTable,
                                           Map<Character.UnicodeBlock, Integer> blockIndex,
                                           int blockN) throws IOException {
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 43);
        List<Double> scores = new ArrayList<>(windows.size());
        int nullId = blockN - 1;
        for (String window : windows) {
            int[] ids = new int[window.length()];
            int len = 0;
            for (int i = 0; i < window.length(); ) {
                int cp = window.codePointAt(i);
                Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
                ids[len++] = b != null ? blockIndex.getOrDefault(b, nullId) : nullId;
                i += Character.charCount(cp);
            }
            if (len < 2) continue;
            double sum = 0;
            for (int i = 0; i + 1 < len; i++) {
                sum += blockTable[ids[i] * blockN + ids[i + 1]];
            }
            scores.add(sum / (len - 1));
        }
        System.out.printf("    %,d dev windows%n", scores.size());
        return muSigma(scores);
    }

    /** @return float[2] = {mu, sigma} of control-byte fraction on dev windows */
    static float[] computeControlByteCalibration(Path devGz) throws IOException {
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 44);
        List<Double> scores = new ArrayList<>(windows.size());
        for (String window : windows) {
            byte[] bytes = window.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0) continue;
            long controlCount = 0;
            for (byte b : bytes) {
                if (isControlByte(b & 0xFF)) controlCount++;
            }
            scores.add(-(double) controlCount / bytes.length);
        }
        System.out.printf("    %,d dev windows%n", scores.size());
        if (scores.isEmpty()) return new float[]{0f, CONTROL_BYTE_MIN_SIGMA};
        double mu = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = scores.stream()
                .mapToDouble(s -> (s - mu) * (s - mu))
                .average().orElse(0);
        double sigma = Math.max(Math.sqrt(variance), CONTROL_BYTE_MIN_SIGMA);
        return new float[]{(float) mu, (float) sigma};
    }

    // -----------------------------------------------------------------------
    // Linear classifier training
    // -----------------------------------------------------------------------

    /**
     * Trains a per-script binary logistic regression classifier on (z1, z2, z3).
     *
     * <p>Clean examples: {@link #NUM_CLASSIFIER_SAMPLES} random dev windows (seed 100).
     * Corrupted examples: same count, alternating inject@5% (seed 102, even indices)
     * and char-shuffle (odd indices) applied to windows sampled with seed 101.
     *
     * @return float[4] = {w1, w2, w3, bias} — classifier weights; positive logit = clean
     */
    static float[] trainClassifier(Path devGz,
                                    float[] bigramTable, float[] bigramCal,
                                    float[] blockTable, float[] blockCal,
                                    float[] controlCal,
                                    Map<Character.UnicodeBlock, Integer> blockIndex,
                                    int blockN) throws IOException {
        int nEach = NUM_CLASSIFIER_SAMPLES;

        // Clean windows
        List<String> cleanWindows = sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 100);

        // Corrupted windows: sample base windows (seed 101), then distort
        List<String> baseWindows = sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 101);
        Random rng = new Random(102);
        List<String> corruptedWindows = new ArrayList<>(nEach);
        for (int i = 0; i < baseWindows.size(); i++) {
            String w = baseWindows.get(i);
            if (i % 2 == 0) {
                corruptedWindows.add(injectControlChars(w, CLASSIFIER_INJECT_RATE, rng));
            } else {
                corruptedWindows.add(shuffleChars(w, rng));
            }
        }

        // Build (z1, z2, z3) feature matrix
        List<float[]> features = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());
        List<Integer> labels   = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());

        for (String w : cleanWindows) {
            features.add(extractFeatures(w, bigramTable, bigramCal,
                    blockTable, blockCal, blockN, controlCal, blockIndex));
            labels.add(1); // clean
        }
        for (String w : corruptedWindows) {
            features.add(extractFeatures(w, bigramTable, bigramCal,
                    blockTable, blockCal, blockN, controlCal, blockIndex));
            labels.add(0); // corrupted
        }

        float[] weights = fitLogisticRegression(features, labels, 3);

        // Calibrate bias using only short (len=15) windows so that FPR ≤ 2.5%
        // even at the worst-case (shortest) window length.  Longer windows have
        // lower logit variance and will score well above this threshold naturally.
        List<String> shortWindows = sampleSubstrings(devGz, nEach, new int[]{15}, 200);
        List<Float> shortLogits = new ArrayList<>(shortWindows.size());
        int nFeat = weights.length - 1;
        for (String w : shortWindows) {
            float[] x = extractFeatures(w, bigramTable, bigramCal,
                    blockTable, blockCal, blockN, controlCal, blockIndex);
            float logit = weights[nFeat];
            for (int j = 0; j < nFeat; j++) logit += weights[j] * x[j];
            shortLogits.add(logit);
        }
        if (!shortLogits.isEmpty()) {
            Collections.sort(shortLogits);
            int pIdx = (int) (0.025 * shortLogits.size());
            float p025 = shortLogits.get(Math.max(0, pIdx));
            weights[nFeat] -= p025; // shift bias so p2.5 of len=15 logits = 0
        }

        return weights;
    }

    /**
     * Extracts calibrated z-scores (z1, z2, z3) for a single text window.
     *
     * @return float[3] = {z1_bigram, z2_block, z3_control}
     */
    static float[] extractFeatures(String window,
                                    float[] bigramTable, float[] bigramCal,
                                    float[] blockTable, float[] blockCal,
                                    int blockN, float[] controlCal,
                                    Map<Character.UnicodeBlock, Integer> blockIndex) {
        byte[] utf8 = window.getBytes(StandardCharsets.UTF_8);

        // z1: byte-bigram mean log-prob
        float z1 = 0f;
        if (utf8.length >= 2) {
            double sum = 0;
            int count = 0;
            for (int i = 0; i + 1 < utf8.length; i++) {
                sum += bigramTable[((utf8[i] & 0xFF) << 8) | (utf8[i + 1] & 0xFF)];
                count++;
            }
            z1 = ((float) (sum / count) - bigramCal[0]) / bigramCal[1];
        }

        // z2: block-transition mean log-prob
        float z2 = 0f;
        if (blockTable != null && window.length() >= 2) {
            int nullId = blockN - 1;
            int prev = -1;
            double sum = 0;
            int count = 0;
            for (int i = 0; i < window.length(); ) {
                int cp = window.codePointAt(i);
                Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
                int blockId = b != null ? blockIndex.getOrDefault(b, nullId) : nullId;
                if (prev >= 0) {
                    sum += blockTable[prev * blockN + blockId];
                    count++;
                }
                prev = blockId;
                i += Character.charCount(cp);
            }
            if (count > 0) {
                z2 = ((float) (sum / count) - blockCal[0]) / blockCal[1];
            }
        }

        // z3: control-byte fraction (stored as −fraction, so higher = cleaner)
        float z3 = 0f;
        if (utf8.length > 0 && controlCal != null) {
            long controlCount = 0;
            for (byte b : utf8) {
                if (isControlByte(b & 0xFF)) controlCount++;
            }
            float score = -(float) controlCount / utf8.length;
            z3 = (score - controlCal[0]) / controlCal[1];
        }

        return new float[]{z1, z2, z3};
    }

    /**
     * Replaces a random fraction of characters with Unicode control characters.
     * Operates at the codepoint level to produce well-formed strings with actual
     * control bytes in the UTF-8 encoding.
     *
     * @param rate fraction of characters to replace [0, 1]
     */
    static String injectControlChars(String text, double rate, Random rng) {
        if (text.isEmpty()) return text;
        int[] codepoints = text.codePoints().toArray();
        int[] controlChars = {0x01, 0x02, 0x03, 0x04, 0x07, 0x0B, 0x0C, 0x0E, 0x0F, 0x1A, 0x1B, 0x7F};
        for (int i = 0; i < codepoints.length; i++) {
            if (rng.nextDouble() < rate) {
                codepoints[i] = controlChars[rng.nextInt(controlChars.length)];
            }
        }
        return new String(codepoints, 0, codepoints.length);
    }

    /**
     * Randomly permutes all characters in the text (Fisher-Yates shuffle).
     * Destroys both bigram and block-transition structure while preserving script
     * distribution, making it a good test of transition-based features.
     */
    static String shuffleChars(String text, Random rng) {
        if (text.isEmpty()) return text;
        int[] codepoints = text.codePoints().toArray();
        for (int i = codepoints.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = codepoints[i];
            codepoints[i] = codepoints[j];
            codepoints[j] = tmp;
        }
        return new String(codepoints, 0, codepoints.length);
    }

    /**
     * Fits a binary logistic regression classifier on the given feature matrix.
     *
     * <p>Label convention: 1 = clean, 0 = corrupted.  At inference, positive
     * logit → clean text; negative logit → corrupted text.
     *
     * <p>Uses full-batch gradient descent with L2 regularization.  Converges
     * reliably for {@code numFeatures} ≤ 10 with the default hyperparameters.
     *
     * @param features list of feature vectors, each of length {@code numFeatures}
     * @param labels   parallel list of labels (0 or 1)
     * @param numFeatures number of features
     * @return float[numFeatures + 1] = {w[0], ..., w[numFeatures-1], bias}
     */
    static float[] fitLogisticRegression(List<float[]> features, List<Integer> labels,
                                          int numFeatures) {
        int n = features.size();
        float[] w = new float[numFeatures]; // zero-initialized
        float bias = 0f;

        if (n == 0) {
            float[] result = new float[numFeatures + 1];
            for (int i = 0; i < numFeatures; i++) result[i] = 1f / numFeatures;
            return result;
        }

        float lr = 0.05f;
        float lambda = 0.01f; // L2 regularization
        int epochs = 500;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradW = new double[numFeatures];
            double gradB = 0;

            for (int i = 0; i < n; i++) {
                float[] x = features.get(i);
                int y = labels.get(i);

                double logit = bias;
                for (int j = 0; j < numFeatures; j++) logit += w[j] * x[j];

                // Numerically stable sigmoid
                double p;
                if (logit >= 0) {
                    double e = Math.exp(-logit);
                    p = 1.0 / (1.0 + e);
                } else {
                    double e = Math.exp(logit);
                    p = e / (1.0 + e);
                }

                double err = p - y;
                for (int j = 0; j < numFeatures; j++) gradW[j] += err * x[j];
                gradB += err;
            }

            for (int j = 0; j < numFeatures; j++) {
                w[j] -= lr * (float) (gradW[j] / n + lambda * w[j]);
            }
            bias -= lr * (float) (gradB / n);
        }

        float[] result = new float[numFeatures + 1];
        for (int j = 0; j < numFeatures; j++) result[j] = w[j];
        result[numFeatures] = bias;
        return result;
    }

    // -----------------------------------------------------------------------
    // Model serialisation
    // -----------------------------------------------------------------------

    /**
     * Writes the trained model (version 3) to a gzipped binary file.
     *
     * <p>Format documented in the class Javadoc.  All multi-byte integers are
     * big-endian; floats are IEEE 754 big-endian.
     *
     * @param classifierWeights per-script float[4] = {w1, w2, w3, bias}
     * @param blockN the block table dimension (blockIndex.size() + 1)
     */
    static void saveModel(TreeMap<String, float[]> bigramTables,
                          TreeMap<String, float[]> bigramCalibrations,
                          TreeMap<String, float[]> blockTables,
                          TreeMap<String, float[]> blockCalibrations,
                          TreeMap<String, float[]> controlCalibrations,
                          TreeMap<String, float[]> classifierWeights,
                          int blockN,
                          Path output) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output)))) {

            dos.write(MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(VERSION);
            dos.writeInt(bigramTables.size());
            dos.writeShort(blockN); // global: block table dimension

            for (var entry : bigramTables.entrySet()) {
                String script = entry.getKey();
                float[] bigramTable  = entry.getValue();
                float[] bigramCal    = bigramCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] blockTable   = blockTables.getOrDefault(script, new float[blockN * blockN]);
                float[] blockCal     = blockCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] controlCal   = controlCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] weights      = classifierWeights.getOrDefault(script,
                        new float[]{1f / 3, 1f / 3, 1f / 3, 0f});

                byte[] nameBytes = script.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);

                // Feature 1: byte bigrams
                dos.writeFloat(bigramCal[0]);
                dos.writeFloat(bigramCal[1]);
                dos.write(toBytes(bigramTable));

                // Feature 2: named-block transitions
                dos.writeFloat(blockCal[0]);
                dos.writeFloat(blockCal[1]);
                dos.write(toBytes(blockTable));

                // Feature 3: control-byte fraction
                dos.writeFloat(controlCal[0]);
                dos.writeFloat(controlCal[1]);

                // Classifier weights: num_features (1 byte) + weights + bias
                int numFeatures = weights.length - 1; // last element is bias
                dos.writeByte(numFeatures);
                for (float v : weights) dos.writeFloat(v);
            }
        }
    }

    private static byte[] toBytes(float[] table) {
        ByteBuffer buf = ByteBuffer.allocate(table.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float v : table) buf.putFloat(v);
        return buf.array();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Returns true if the byte value is a control character that should not appear
     * in natural-language UTF-8 text: {@code [0x01–0x08, 0x0B, 0x0C, 0x0E–0x1F, 0x7F]}.
     *
     * <p>Excluded: 0x00 (null), 0x09 (tab), 0x0A (newline), 0x0D (carriage return)
     * — all appear legitimately in text.
     */
    static boolean isControlByte(int b) {
        return (b >= 0x01 && b <= 0x08)
                || b == 0x0B || b == 0x0C
                || (b >= 0x0E && b <= 0x1F)
                || b == 0x7F;
    }

    private static float[] muSigma(List<Double> scores) {
        if (scores.isEmpty()) return new float[]{0f, 1f};
        double mu = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = scores.stream()
                .mapToDouble(s -> (s - mu) * (s - mu))
                .average().orElse(1.0);
        double sigma = Math.sqrt(variance);
        if (sigma < 1e-9) sigma = 1.0;
        return new float[]{(float) mu, (float) sigma};
    }

    static BufferedReader openGzipped(Path path) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(path)),
                        StandardCharsets.UTF_8));
    }

    private static void printUsage() {
        System.err.println("Usage: TrainJunkModel [options]");
        System.err.println("  --data-dir <path>  Directory with {script}.train.gz / .dev.gz files");
        System.err.println("                     (default: ~/datasets/madlad/junkdetect)");
        System.err.println("  --output   <path>  Output model file");
        System.err.println("                     (default: {data-dir}/junkdetect.bin)");
    }
}
