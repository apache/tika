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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.ml.junkdetect.F1Tables;
import org.apache.tika.ml.junkdetect.JunkDetector;

/**
 * Trains the junk detector model from per-script corpus files produced by
 * {@link BuildJunkTrainingData}.
 *
 * <p>For each script group (identified by a {@code {script}.train.gz} file),
 * four features are trained and then combined by a per-script logistic
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
 *   <li><b>Script-transition log-probability</b>: global table of log P(script_b | script_a)
 *       over raw {@link Character.UnicodeScript} values (excluding COMMON, INHERITED, UNKNOWN),
 *       pooled across all training scripts (z4).</li>
 * </ol>
 *
 * <p>All four features are calibrated (mu/sigma) on the dev split so their
 * z-scores are on a common scale.  A per-script binary logistic regression
 * classifier is then fit on (z1, z2, z3, z4) using clean dev windows and corrupted
 * versions (inject@5%, char-shuffle) as training examples.  The learned weights
 * replace the fixed equal-weight average, allowing the model to automatically
 * downweight noisy features (e.g. high-variance block transitions for MYANMAR)
 * and upweight informative ones (e.g. control-byte fraction for inject@0.01).
 *
 * <p>At inference, the final score is the linear combination
 * {@code w1*z1 + w2*z2 + w3*z3 + w4*z4 + bias}; positive values indicate clean text.
 * The natural threshold is 0 (probability 0.5); use a negative threshold for
 * more conservative junk detection.
 *
 * <p>Output format: {@code JUNKDET1} gzipped binary, <b>version 5</b>.
 * Version 1–4 files can still be loaded by {@code JunkDetector} on the JVM they were trained on.
 *
 * <pre>
 *   [8 bytes]  magic "JUNKDET1" (ASCII)
 *   [1 byte]   version = 4
 *   [4 bytes]  num_scripts (big-endian int)
 *   [2 bytes]  block_N — number of distinct named Unicode blocks + 1 (unassigned)
 *   // Block names section (version 5+): block_N-1 entries for JVM-independence
 *   for i in [0, block_N-1):
 *     [2 bytes]     name length (big-endian ushort)
 *     [name bytes]  Unicode block name (Character.UnicodeBlock.toString())
 *   // Global script-transition section (version 4+)
 *   [1 byte]   num_script_buckets
 *   for each bucket:
 *     [2 bytes]     name length (big-endian ushort)
 *     [name bytes]  bucket name (UnicodeScript.name() or "OTHER")
 *   [num_script_buckets² × 4 bytes]  script-transition log-prob table
 *   [4 bytes]  mu4   (float32 big-endian)
 *   [4 bytes]  sigma4 (float32 big-endian)
 *   // Per-script data (same as v3 but num_features = 4)
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
 *     [1 byte]        num_features (= 4 for v4)
 *     [4 bytes]       w1   (float32 big-endian)
 *     [4 bytes]       w2   (float32 big-endian)
 *     [4 bytes]       w3   (float32 big-endian)
 *     [4 bytes]       w4   (float32 big-endian)
 *     [4 bytes]       bias (float32 big-endian)
 * </pre>
 */
public class TrainJunkModel {

    static final String MAGIC = "JUNKDET1";
    /** Sole supported file-format version.  Matches JunkDetector.VERSION. */
    static final byte VERSION = 6;

    // -----------------------------------------------------------------------
    // v6 model constants (codepoint-bigram-hash + Bloom + unigram-backoff)
    // -----------------------------------------------------------------------

    /** Bigram hash bucket count (power of 2). */
    static final int V6_BIGRAM_BUCKETS = 4096;
    /** Unigram hash bucket count (power of 2). */
    static final int V6_UNIGRAM_BUCKETS = 8192;
    /** Default Bloom filter capacity in bits (must be a multiple of 64). */
    static final int V6_BLOOM_BITS_DEFAULT = 16 * 1024 * 1024;
    /** Number of hash functions for the Bloom filter (double-hashing). */
    static final int V6_BLOOM_K = 7;
    /** FNV-1a seed; stored in the model so training and inference stay synced. */
    public static final int V6_FNV_SEED = 0xB8E7A1F3;
    /** Unigram backoff multiplier.  α=1.0 = plain independence; prototype validated. */
    static final float V6_BACKOFF_ALPHA = 1.0f;
    /** Additive smoothing constant for log-prob computation. */
    static final double V6_ADD_ALPHA = 0.01;

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
     * Codec pairs used to build wrong-codec remap tables for training.
     * Each entry is {sourceCodec, wrongCodec}: text encoded in sourceCodec but
     * decoded as wrongCodec.  Pairs within the same script family (e.g. CP1250↔CP1252)
     * produce wrong-accent distortions that shift characters between Unicode blocks
     * while staying in LATIN.  Cross-script pairs (CP1252↔CP1255) additionally change
     * the Unicode script, which z4 also detects.
     */
    static final String[][] WRONG_CODEC_PAIRS = {
        {"windows-1252", "windows-1250"}, // Western ↔ Central European (wrong accents)
        {"windows-1250", "windows-1252"}, // reverse
        {"windows-1252", "windows-1257"}, // Western ↔ Baltic (wrong accents)
        {"windows-1257", "windows-1252"}, // reverse
        {"windows-1252", "windows-1254"}, // Western ↔ Turkish (wrong accents)
        {"windows-1251", "windows-1252"}, // Cyrillic → Latin (cross-script)
        {"windows-1252", "windows-1251"}, // Latin → Cyrillic (cross-script)
        {"windows-1253", "windows-1252"}, // Greek → Latin (cross-script)
        {"windows-1252", "windows-1253"}, // Latin → Greek (cross-script)
        {"windows-1255", "windows-1252"}, // Hebrew → Latin (cross-script)
        {"windows-1252", "windows-1255"}, // Latin → Hebrew (the German vcard case)
    };

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

        // Durable training parameters live in JunkDetectorTrainingConfig; this
        // tool deliberately refuses CLI overrides so a built model file's
        // identity always matches a committed config.
        int bloomBits = JunkDetectorTrainingConfig.BLOOM_BITS;
        int minBigramCount = JunkDetectorTrainingConfig.MIN_BIGRAM_COUNT;
        if (bloomBits % 64 != 0) {
            System.err.println("ERROR: BLOOM_BITS must be a multiple of 64");
            System.exit(1);
        }
        if (minBigramCount < 1) {
            System.err.println("ERROR: MIN_BIGRAM_COUNT must be >= 1");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data-dir":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    output = Paths.get(args[++i]);
                    break;
                case "--bloom-bits":
                case "--min-bigram-count":
                    System.err.println("ERROR: " + args[i] + " is no longer a CLI option."
                            + "  Edit JunkDetectorTrainingConfig and commit the change instead.");
                    System.exit(1);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        System.out.println("=== TrainJunkModel ===");
        System.out.println("  data-dir:           " + dataDir);
        System.out.println("  output:             " + output);
        System.out.println("  --- v6 format constants (TrainJunkModel) ---");
        System.out.printf( "  bigram_buckets:     %d%n", V6_BIGRAM_BUCKETS);
        System.out.printf( "  unigram_buckets:    %d%n", V6_UNIGRAM_BUCKETS);
        System.out.printf( "  fnv_seed:           0x%08X%n", V6_FNV_SEED);
        System.out.printf( "  backoff_alpha:      %.2f%n", V6_BACKOFF_ALPHA);
        System.out.println("  --- config (JunkDetectorTrainingConfig) ---");
        System.out.printf( "  bloom_bits:         %d (%d KB), k=%d%n",
                bloomBits, bloomBits / 8 / 1024, V6_BLOOM_K);
        System.out.printf( "  min_bigram_count:   %d%n", minBigramCount);

        if (!Files.isDirectory(dataDir)) {
            System.err.println("ERROR: data-dir not found: " + dataDir);
            System.exit(1);
        }

        int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
        System.out.printf("Block bucketing: %d named blocks + 1 unassigned "
                + "(scheme version %d, JVM-independent)%n",
                blockN - 1, org.apache.tika.ml.junkdetect.UnicodeBlockRanges.SCHEME_VERSION);
        long t0 = System.currentTimeMillis();

        TreeMap<String, float[]> f1Calibrations    = new TreeMap<>();
        TreeMap<String, float[]> blockTables       = new TreeMap<>();
        TreeMap<String, float[]> blockCalibrations = new TreeMap<>();
        TreeMap<String, float[]> controlCalibrations = new TreeMap<>();
        TreeMap<String, float[]> classifierWeights = new TreeMap<>();
        TreeMap<String, Path>    trainFilePaths    = new TreeMap<>();
        List<Path>               allTrainFiles     = new ArrayList<>();

        List<Path> trainFiles;
        try (var stream = Files.list(dataDir)) {
            trainFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".train.gz"))
                    .sorted()
                    .toList();
        }

        if (trainFiles.isEmpty()) {
            System.err.println("ERROR: no *.train.gz files found in " + dataDir);
            System.exit(1);
        }

        // -----------------------------------------------------------------------
        // Phase 1 — global codepoint-bigram + unigram hash + Bloom filter
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 1: global codepoint-hash tables + Bloom ---");
        t0 = System.currentTimeMillis();
        System.out.print("  Training global codepoint-bigram + unigram + Bloom... ");
        F1Tables f1Tables = trainCodepointHashTables(trainFiles, bloomBits, minBigramCount);
        System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);
        System.out.print(f1Tables.statsString());

        // -----------------------------------------------------------------------
        // Phase 1.5 — per-script F1 calibration + F2 block tables + F3 control cal
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 1.5: per-script tables and calibrations ---");
        for (Path trainFile : trainFiles) {
            String filename = trainFile.getFileName().toString();
            String script = filename.substring(0, filename.length() - ".train.gz".length())
                    .toUpperCase();

            System.out.printf("%n  [%s]%n", script);
            allTrainFiles.add(trainFile);

            t0 = System.currentTimeMillis();
            System.out.print("    Training named-block table...       ");
            float[] blockTable = trainBlockTable(trainFile);
            System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            System.out.print("    Calibrating F1 (cp-hash) on train.. ");
            float[] f1Cal = calibrateF1PerScript(trainFile, f1Tables);
            System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                    f1Cal[0], f1Cal[1], System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            System.out.print("    Calibrating named blocks on train...");
            float[] blockCal = computeBlockCalibration(trainFile, blockTable);
            System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                    blockCal[0], blockCal[1], System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            System.out.print("    Calibrating control bytes on train..");
            float[] controlCal = computeControlByteCalibration(trainFile);
            System.out.printf("done — mu=%.6f sigma=%.6f (%dms)%n",
                    controlCal[0], controlCal[1], System.currentTimeMillis() - t0);

            trainFilePaths.put(script, trainFile);

            f1Calibrations.put(script, f1Cal);
            blockTables.put(script, blockTable);
            blockCalibrations.put(script, blockCal);
            controlCalibrations.put(script, controlCal);
            // Placeholder — set in Phase 3
            classifierWeights.put(script, new float[]{1f / 4, 1f / 4, 1f / 4, 1f / 4, 0f});
        }

        // -----------------------------------------------------------------------
        // Phase 2 — global script-transition table + supporting pools
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 2: global script-transition table ---");
        List<String> scriptBuckets = buildScriptBuckets();
        int numScriptBuckets = scriptBuckets.size();
        Map<String, Integer> scriptBucketMap = new LinkedHashMap<>();
        for (int i = 0; i < numScriptBuckets; i++) {
            scriptBucketMap.put(scriptBuckets.get(i), i);
        }
        System.out.printf("  %d script buckets (including OTHER)%n", numScriptBuckets);

        t0 = System.currentTimeMillis();
        System.out.print("  Training script-transition table... ");
        float[] scriptTransTable = trainScriptTransitionTable(allTrainFiles, scriptBucketMap, numScriptBuckets);
        System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

        t0 = System.currentTimeMillis();
        System.out.print("  Calibrating script transitions...   ");
        float[] scriptTransCal = calibrateScriptTransitions(allTrainFiles, scriptTransTable,
                scriptBucketMap, numScriptBuckets);
        System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                scriptTransCal[0], scriptTransCal[1], System.currentTimeMillis() - t0);

        t0 = System.currentTimeMillis();
        System.out.print("  Collecting per-script codepoint pools... ");
        Map<String, List<Integer>> scriptCodepoints = collectScriptCodepoints(allTrainFiles, 200);
        System.out.printf("done — %d scripts (%dms)%n",
                scriptCodepoints.size(), System.currentTimeMillis() - t0);

        System.out.print("  Building wrong-codec remap tables...    ");
        List<Map<Character, Character>> remapTables = new ArrayList<>();
        for (String[] pair : WRONG_CODEC_PAIRS) {
            Map<Character, Character> table = buildRemapTable(pair[0], pair[1]);
            if (!table.isEmpty()) remapTables.add(table);
        }
        System.out.printf("%d tables built%n", remapTables.size());

        // -----------------------------------------------------------------------
        // Phase 3 — per-script linear classifiers using v6 features
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 3: per-script linear classifiers (z1,z2,z3,z4) ---");
        for (String script : f1Calibrations.keySet()) {
            Path trainFile = trainFilePaths.get(script);
            if (trainFile == null) {
                System.out.printf("  [%s] WARNING: no train file, keeping equal-weight defaults%n", script);
                continue;
            }
            t0 = System.currentTimeMillis();
            System.out.printf("  [%s] training classifier... ", script);
            float[] weights = trainClassifierV6(trainFile,
                    f1Tables, f1Calibrations.get(script),
                    blockTables.get(script), blockCalibrations.get(script),
                    controlCalibrations.get(script),
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets,
                    scriptCodepoints, remapTables);
            classifierWeights.put(script, weights);
            System.out.printf("done — w=[%.3f,%.3f,%.3f,%.3f] bias=%.3f (%dms)%n",
                    weights[0], weights[1], weights[2], weights[3], weights[4],
                    System.currentTimeMillis() - t0);
        }

        System.out.printf("%nWriting model (%d scripts, blockN=%d, scriptBuckets=%d) → %s%n",
                f1Calibrations.size(), blockN, numScriptBuckets, output);
        saveModelV6(f1Calibrations,
                  blockTables, blockCalibrations,
                  controlCalibrations, classifierWeights,
                  scriptBuckets, scriptTransTable, scriptTransCal,
                  f1Tables, output);
        System.out.printf("Model size: %,d bytes (%.1f KB)%n",
                Files.size(output), Files.size(output) / 1024.0);
        System.out.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    /**
     * Trains a {@code N × N} block-transition log-probability table where
     * {@code N = UnicodeBlockRanges.bucketCount()}.  Block bucketing uses
     * the JVM-independent {@link UnicodeBlockRanges} table.
     *
     * @return float[N*N] where index {@code a*N+b} = log P(block_b | block_a)
     */
    static float[] trainBlockTable(Path trainGz) throws IOException {
        int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
        long[] counts = new long[blockN * blockN];
        long totalBigrams = 0;
        long sentences = 0;

        try (BufferedReader r = openGzipped(trainGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                int prev = -1;
                for (int i = 0; i < line.length(); ) {
                    int cp = line.codePointAt(i);
                    int blockId = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketOf(cp);
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

    /** @return float[2] = {mu, sigma} of block-transition mean log-prob on dev windows */
    static float[] computeBlockCalibration(Path devGz, float[] blockTable) throws IOException {
        int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 43);
        List<Double> scores = new ArrayList<>(windows.size());
        for (String window : windows) {
            int[] ids = new int[window.length()];
            int len = 0;
            for (int i = 0; i < window.length(); ) {
                int cp = window.codePointAt(i);
                ids[len++] = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketOf(cp);
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
     * Trains a per-script binary logistic regression classifier on (z1, z2, z3, z4).
     *
     * <p>Clean examples: {@link #NUM_CLASSIFIER_SAMPLES} random dev windows (seed 100).
     * Corrupted examples: same count, cycling through four distortions (seed 102):
     * <ol>
     *   <li>inject@5% control chars</li>
     *   <li>char-shuffle</li>
     *   <li>cross-script substitution — replaces ~5% of characters with codepoints from
     *       foreign scripts, simulating charset encoding errors such as German umlauts
     *       becoming Hebrew letters when CP1252 text is decoded as CP1255</li>
     *   <li>wrong-codec remap — replaces ~5% of characters using a random pre-computed
     *       charset remap table (e.g. CP1252→CP1250 for wrong accents, CP1252→CP1255
     *       for script crossings), simulating real-world charset misdetection</li>
     * </ol>
     *
     * @param remapTables list of pre-built wrong-codec remap tables from {@link #buildRemapTable}
     * @return float[5] = {w1, w2, w3, w4, bias} — classifier weights; positive logit = clean
     */

    // Per-feature z-score helpers (z2, z3, z4) for the classifier-training
    // path live on JunkDetector as public static methods so they are the
    // SOLE implementation — inference and training share the exact same
    // math by construction.  See {@link JunkDetector#computeZ2BlockTransition},
    // {@link JunkDetector#computeZ3ControlByte},
    // {@link JunkDetector#computeZ4ScriptTransition}.  z1 (codepoint-hash)
    // is computed against the in-progress hash tables during training and
    // against the loaded model at inference.

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
     * <p>Uses full-batch gradient descent with L2 regularization and a
     * non-negativity constraint on feature weights (projected gradient descent:
     * {@code w[j] = max(0, w[j])} after each step).  The constraint enforces the
     * semantic invariant that every feature is calibrated so higher = cleaner;
     * a negative weight would mean "more unusual transitions → cleaner text", which
     * is semantically wrong and causes pathological behaviour when collinear features
     * (e.g. z2 block-transitions and z4 script-transitions) are both present.
     * The bias term is unconstrained.  Converges reliably for {@code numFeatures} ≤ 10
     * with the default hyperparameters.
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
                w[j] = Math.max(0f, w[j]); // projected gradient: feature weights are non-negative by design
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

    private static byte[] toBytes(float[] table) {
        ByteBuffer buf = ByteBuffer.allocate(table.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float v : table) buf.putFloat(v);
        return buf.array();
    }

    // -----------------------------------------------------------------------
    // v6 model save + carrier
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // v6 Phase 1: global codepoint-hash training
    // -----------------------------------------------------------------------

    /**
     * Single global pass over all training .gz files: counts codepoint
     * pairs into a {@link #V6_BIGRAM_BUCKETS}-sized hash table, codepoint
     * unigrams into a {@link #V6_UNIGRAM_BUCKETS}-sized table, and inserts
     * every observed pair into a Bloom filter sized at {@code bloomBits}.
     * Quantizes both log-prob tables to 8-bit unsigned.  Returns a
     * {@link org.apache.tika.ml.junkdetect.F1Tables} ready to
     * hand to {@link #saveModelV6}.
     *
     * <p>Hash function and Bloom-filter scheme are identical to those used at
     * inference time in {@link org.apache.tika.ml.junkdetect.JunkDetector#computeF1MeanLogP}
     * — same seed, same FNV-1a polynomial, same double-hashing layout —
     * so the trained Bloom filter accurately reflects which pairs the
     * scorer will treat as "seen".
     */
    public static F1Tables trainCodepointHashTables(List<Path> trainFiles, int bloomBits)
            throws IOException {
        return trainCodepointHashTables(trainFiles, bloomBits, 1);
    }

    /**
     * Same as the 2-arg overload, but only bigrams with global per-pair count
     * &gt;= {@code minBigramCount} contribute to the bigram hash table and the
     * Bloom filter.  Unigrams are always counted (used for backoff).  When
     * {@code minBigramCount == 1} this is a no-op and the single-pass code
     * path runs.  Otherwise a first pass tallies per-pair counts into an
     * in-memory map and a second pass emits only frequent pairs.
     */
    public static F1Tables trainCodepointHashTables(List<Path> trainFiles, int bloomBits,
            int minBigramCount) throws IOException {
        long[] bigramCounts = new long[V6_BIGRAM_BUCKETS];
        long[] unigramCounts = new long[V6_UNIGRAM_BUCKETS];
        long bigramTotal = 0;
        long unigramTotal = 0;
        long[] bloomBitArr = new long[(bloomBits + 63) >> 6];

        HashMap<Long, long[]> pairTallies = null;
        if (minBigramCount > 1) {
            System.out.printf("  pre-pass: tallying per-pair counts "
                    + "(min_bigram_count=%d)%n", minBigramCount);
            pairTallies = new HashMap<>(1 << 18);
            for (Path trainFile : trainFiles) {
                try (BufferedReader r = openGzipped(trainFile)) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        int prevCp = -1;
                        for (int i = 0; i < line.length(); ) {
                            int cp = line.codePointAt(i);
                            i += Character.charCount(cp);
                            if (prevCp >= 0) {
                                long packed = ((long) prevCp << 24) | (cp & 0xFFFFFFL);
                                long[] c = pairTallies.get(packed);
                                if (c == null) {
                                    pairTallies.put(packed, new long[]{1L});
                                } else {
                                    c[0]++;
                                }
                            }
                            prevCp = cp;
                        }
                    }
                }
            }
            int kept = 0;
            int dropped = 0;
            for (long[] c : pairTallies.values()) {
                if (c[0] >= minBigramCount) kept++;
                else dropped++;
            }
            System.out.printf("  pre-pass: distinct pairs=%,d  kept=%,d  dropped=%,d%n",
                    pairTallies.size(), kept, dropped);
        }

        for (Path trainFile : trainFiles) {
            try (BufferedReader r = openGzipped(trainFile)) {
                String line;
                while ((line = r.readLine()) != null) {
                    int prevCp = -1;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        int uBucket = (int) (fnv1aUnigramV6(cp, V6_FNV_SEED)
                                & (V6_UNIGRAM_BUCKETS - 1));
                        unigramCounts[uBucket]++;
                        unigramTotal++;
                        if (prevCp >= 0) {
                            boolean accept = true;
                            if (pairTallies != null) {
                                long packed = ((long) prevCp << 24) | (cp & 0xFFFFFFL);
                                long[] c = pairTallies.get(packed);
                                accept = c != null && c[0] >= minBigramCount;
                            }
                            if (accept) {
                                int bBucket = (int) (fnv1aBigramV6(prevCp, cp, V6_FNV_SEED)
                                        & (V6_BIGRAM_BUCKETS - 1));
                                bigramCounts[bBucket]++;
                                bigramTotal++;
                                bloomAddV6(bloomBitArr, bloomBits, V6_BLOOM_K,
                                        prevCp, cp, V6_FNV_SEED);
                            }
                        }
                        prevCp = cp;
                    }
                }
            }
        }

        // Add-α smoothing → log-prob → 8-bit quantize.
        float[] bigramLogP = new float[V6_BIGRAM_BUCKETS];
        double bigramDenom = bigramTotal + V6_ADD_ALPHA * V6_BIGRAM_BUCKETS;
        for (int i = 0; i < V6_BIGRAM_BUCKETS; i++) {
            double p = (bigramCounts[i] + V6_ADD_ALPHA) / bigramDenom;
            bigramLogP[i] = (float) Math.log(p);
        }
        float[] unigramLogP = new float[V6_UNIGRAM_BUCKETS];
        double unigramDenom = unigramTotal + V6_ADD_ALPHA * V6_UNIGRAM_BUCKETS;
        for (int i = 0; i < V6_UNIGRAM_BUCKETS; i++) {
            double p = (unigramCounts[i] + V6_ADD_ALPHA) / unigramDenom;
            unigramLogP[i] = (float) Math.log(p);
        }

        QuantizedFloats qBigram = quantizeFloats(bigramLogP);
        QuantizedFloats qUnigram = quantizeFloats(unigramLogP);

        return new F1Tables(
                qBigram.bytes, V6_BIGRAM_BUCKETS,
                qBigram.min, qBigram.max,
                qUnigram.bytes, V6_UNIGRAM_BUCKETS,
                qUnigram.min, qUnigram.max,
                bloomBitArr, bloomBits, V6_BLOOM_K,
                V6_FNV_SEED, V6_BACKOFF_ALPHA);
    }

    /**
     * Computes per-script F1 calibration ({mu, sigma}) by scoring each
     * window in the dev file against the trained codepoint-hash tables
     * and collecting the mean log-prob distribution.  Delegates to
     * {@link org.apache.tika.ml.junkdetect.JunkDetector#computeF1MeanLogP}
     * — the single authoritative F1 implementation shared between training
     * and inference.
     */
    public static float[] calibrateF1PerScript(Path devGz, F1Tables f1) throws IOException {
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 42);
        List<Double> scores = new ArrayList<>(windows.size());
        for (String window : windows) {
            double score = JunkDetector.computeF1MeanLogP(window, f1);
            if (!Double.isNaN(score)) {
                scores.add(score);
            }
        }
        System.out.printf("    %,d dev windows%n", scores.size());
        return muSigma(scores);
    }

    /** Bloom membership check (matches JunkDetector.bloomContains semantics). */
    public static boolean bloomContainsV6(long[] bloomBits, int bitCount, int k,
                                          int cpA, int cpB, int seed) {
        long h1 = fnv1aBigramV6(cpA, cpB, seed);
        long h2 = secondaryHashV6(cpA, cpB);
        for (int i = 0; i < k; i++) {
            long pos = ((h1 + (long) i * h2) & 0x7FFFFFFFFFFFFFFFL) % bitCount;
            if ((bloomBits[(int) (pos >>> 6)] & (1L << (pos & 63))) == 0) {
                return false;
            }
        }
        return true;
    }

    private static float dequantize(byte b, float min, float max) {
        return min + ((b & 0xFF) / 255.0f) * (max - min);
    }

    // -----------------------------------------------------------------------
    // v6 Phase 3: classifier feature extractor + orchestrator
    // -----------------------------------------------------------------------

    /**
     * Extracts a 4-dim calibrated z-score vector for one training window
     * using the codepoint-hash architecture.  z2/z3/z4 delegate to the
     * public helpers on {@link JunkDetector} — same math used at inference,
     * no trainer/inference drift possible.
     *
     * @return float[4] = {z1_cpHash, z2_block, z3_control, z4_scriptTrans}
     */
    static float[] extractFeaturesV6(String window,
                                      F1Tables f1, float[] f1Cal,
                                      float[] blockTable, float[] blockCal,
                                      float[] controlCal,
                                      float[] scriptTransTable, float[] scriptTransCal,
                                      Map<String, Integer> scriptBucketMap,
                                      int numScriptBuckets) {
        byte[] utf8 = window.getBytes(StandardCharsets.UTF_8);

        // z1: codepoint-hash mean log-prob, per-script-calibrated
        float z1 = 0f;
        double rawF1 = JunkDetector.computeF1MeanLogP(window, f1);
        if (!Double.isNaN(rawF1) && f1Cal != null && f1Cal[1] > 0) {
            z1 = ((float) rawF1 - f1Cal[0]) / f1Cal[1];
        }

        float z2 = org.apache.tika.ml.junkdetect.JunkDetector
                .computeZ2BlockTransition(window, blockTable, blockCal);
        float z3 = org.apache.tika.ml.junkdetect.JunkDetector
                .computeZ3ControlByte(utf8, controlCal);
        float z4 = org.apache.tika.ml.junkdetect.JunkDetector
                .computeZ4ScriptTransition(window, scriptTransTable, scriptTransCal,
                        scriptBucketMap, numScriptBuckets);

        return new float[]{z1, z2, z3, z4};
    }

    /**
     * Trains a per-script binary logistic regression classifier on
     * (z1_cpHash, z2, z3, z4).  Mirrors the original {@code trainClassifier}
     * scaffolding (sample windows, corrupt half, fit LR, bias-calibrate
     * on short windows) but uses the codepoint-hash feature extractor.
     */
    static float[] trainClassifierV6(Path devGz,
                                      F1Tables f1, float[] f1Cal,
                                      float[] blockTable, float[] blockCal,
                                      float[] controlCal,
                                      float[] scriptTransTable, float[] scriptTransCal,
                                      Map<String, Integer> scriptBucketMap, int numScriptBuckets,
                                      Map<String, List<Integer>> scriptCodepoints,
                                      List<Map<Character, Character>> remapTables)
            throws IOException {
        int nEach = NUM_CLASSIFIER_SAMPLES;

        List<String> cleanWindows = sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 100);

        List<String> baseWindows = sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 101);
        Random rng = new Random(102);
        List<String> corruptedWindows = new ArrayList<>(nEach);
        for (int i = 0; i < baseWindows.size(); i++) {
            String w = baseWindows.get(i);
            switch (i % 4) {
                case 0:
                    corruptedWindows.add(injectControlChars(w, CLASSIFIER_INJECT_RATE, rng));
                    break;
                case 1:
                    corruptedWindows.add(shuffleChars(w, rng));
                    break;
                case 2:
                    corruptedWindows.add(injectCrossScriptChars(w, CLASSIFIER_INJECT_RATE, rng,
                            scriptCodepoints));
                    break;
                default:
                    if (!remapTables.isEmpty()) {
                        Map<Character, Character> table =
                                remapTables.get(rng.nextInt(remapTables.size()));
                        corruptedWindows.add(wrongCodecRemap(w, table, CLASSIFIER_INJECT_RATE, rng));
                    } else {
                        corruptedWindows.add(injectControlChars(w, CLASSIFIER_INJECT_RATE, rng));
                    }
                    break;
            }
        }

        List<float[]> features = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());
        List<Integer> labels   = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());

        for (String w : cleanWindows) {
            features.add(extractFeaturesV6(w, f1, f1Cal,
                    blockTable, blockCal, controlCal,
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets));
            labels.add(1);
        }
        for (String w : corruptedWindows) {
            features.add(extractFeaturesV6(w, f1, f1Cal,
                    blockTable, blockCal, controlCal,
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets));
            labels.add(0);
        }

        float[] weights = fitLogisticRegression(features, labels, 4);

        // Bias calibration on short windows so FPR ≤ 2.5% at worst-case length.
        List<String> shortWindows = sampleSubstrings(devGz, nEach, new int[]{15}, 200);
        List<Float> shortLogits = new ArrayList<>(shortWindows.size());
        int nFeat = weights.length - 1;
        for (String w : shortWindows) {
            float[] x = extractFeaturesV6(w, f1, f1Cal,
                    blockTable, blockCal, controlCal,
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets);
            float logit = weights[nFeat];
            for (int j = 0; j < nFeat; j++) logit += weights[j] * x[j];
            shortLogits.add(logit);
        }
        if (!shortLogits.isEmpty()) {
            Collections.sort(shortLogits);
            int pIdx = (int) (0.025 * shortLogits.size());
            float p025 = shortLogits.get(Math.max(0, pIdx));
            weights[nFeat] -= p025;
        }

        return weights;
    }

    /**
     * Writes a v6 model file (JUNKDET1 version=6 gzipped binary).
     *
     * <p>Layout differs from v5 in two ways:
     * <ol>
     *   <li>A new global F1 section after the script-transition section,
     *       holding the codepoint-bigram hash + Bloom + unigram-backoff
     *       tables.</li>
     *   <li>Per-script section drops the 65,536-float byte-bigram table.
     *       The {@code mu1}/{@code sigma1} calibration fields remain (now
     *       calibrated on the codepoint-hash mean log-prob, not byte-bigram).</li>
     * </ol>
     *
     * F2 (block transition), F3 (control byte), F4 (script transition)
     * sections are unchanged from v5 — pass v5-trained tables through.
     */
    public static void saveModelV6(TreeMap<String, float[]> f1Calibrations,
                                   TreeMap<String, float[]> blockTables,
                                   TreeMap<String, float[]> blockCalibrations,
                                   TreeMap<String, float[]> controlCalibrations,
                                   TreeMap<String, float[]> classifierWeights,
                                   List<String> scriptBuckets,
                                   float[] scriptTransTable,
                                   float[] scriptTransCal,
                                   F1Tables v6,
                                   Path output) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output)))) {

            dos.write(MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(VERSION);
            dos.writeInt(f1Calibrations.size());

            // Block-scheme version byte — bound to the JVM-independent
            // UnicodeBlockRanges static table.  Mismatch at load time is a
            // hard error (no silent re-mapping).
            dos.writeByte(org.apache.tika.ml.junkdetect.UnicodeBlockRanges.SCHEME_VERSION);

            // Global script-transition section
            int numBuckets = scriptBuckets.size();
            dos.writeByte(numBuckets);
            for (String bucketName : scriptBuckets) {
                byte[] nameBytes = bucketName.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);
            }
            dos.write(toBytes(scriptTransTable));
            dos.writeFloat(scriptTransCal[0]);
            dos.writeFloat(scriptTransCal[1]);

            // Global F1 section (v6+)
            v6.writeTo(dos);

            // Per-script section — v6 drops the per-script byte-bigram table.
            // mu1/sigma1 remain (calibrated on the codepoint-hash score).
            int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
            for (var entry : f1Calibrations.entrySet()) {
                String script = entry.getKey();
                float[] f1Cal      = entry.getValue();
                float[] blockTable = blockTables.getOrDefault(script, new float[blockN * blockN]);
                float[] blockCal   = blockCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] controlCal = controlCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] weights    = classifierWeights.getOrDefault(script,
                        new float[]{1f / 4, 1f / 4, 1f / 4, 1f / 4, 0f});

                byte[] nameBytes = script.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);

                // F1 calibration only (no byte-bigram table in v6)
                dos.writeFloat(f1Cal[0]);
                dos.writeFloat(f1Cal[1]);

                // F2 — block transitions
                dos.writeFloat(blockCal[0]);
                dos.writeFloat(blockCal[1]);
                dos.write(toBytes(blockTable));

                // F3 — control-byte calibration
                dos.writeFloat(controlCal[0]);
                dos.writeFloat(controlCal[1]);

                // Classifier weights
                int numFeatures = weights.length - 1;
                dos.writeByte(numFeatures);
                for (float v : weights) {
                    dos.writeFloat(v);
                }
            }
        }
    }

    /**
     * Quantizes a float array to 8-bit unsigned by linearly mapping
     * {@code [min, max] → [0, 255]}.  Returns the byte array; {@code min}
     * and {@code max} are computed from the input.
     *
     * <p>Stored in v6 model files as 8-bit log-prob tables; reader
     * dequantizes via {@code min + (b/255) * (max - min)}.
     *
     * @return three-element record: byte[] quantized, float min, float max
     */
    public static QuantizedFloats quantizeFloats(float[] in) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float v : in) {
            if (Float.isFinite(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!Float.isFinite(min) || !Float.isFinite(max) || max == min) {
            // Degenerate input — emit zeros, store dummy range.
            return new QuantizedFloats(new byte[in.length], 0f, 1f);
        }
        byte[] out = new byte[in.length];
        float range = max - min;
        for (int i = 0; i < in.length; i++) {
            float v = Float.isFinite(in[i]) ? in[i] : min;
            int q = Math.round(((v - min) / range) * 255.0f);
            if (q < 0) q = 0;
            else if (q > 255) q = 255;
            out[i] = (byte) q;
        }
        return new QuantizedFloats(out, min, max);
    }

    /** Return type of {@link #quantizeFloats(float[])}. */
    public static final class QuantizedFloats {
        public final byte[] bytes;
        public final float min;
        public final float max;
        public QuantizedFloats(byte[] bytes, float min, float max) {
            this.bytes = bytes;
            this.min = min;
            this.max = max;
        }
    }

    // -----------------------------------------------------------------------
    // FNV-1a hash + Bloom utilities (v6 — must match JunkDetector inference)
    // -----------------------------------------------------------------------

    private static final long V6_FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long V6_FNV_PRIME  = 0x100000001b3L;

    public static long fnv1aBigramV6(int cpA, int cpB, int seed) {
        long h = V6_FNV_OFFSET;
        h = (h ^ (seed & 0xFF))        * V6_FNV_PRIME;
        h = (h ^ ((cpA >>> 24) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cpA >>> 16) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cpA >>> 8)  & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ (cpA & 0xFF))         * V6_FNV_PRIME;
        h = (h ^ 0xFF)                 * V6_FNV_PRIME;
        h = (h ^ ((cpB >>> 24) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cpB >>> 16) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cpB >>> 8)  & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ (cpB & 0xFF))         * V6_FNV_PRIME;
        return h;
    }

    public static long fnv1aUnigramV6(int cp, int seed) {
        long h = V6_FNV_OFFSET;
        h = (h ^ (seed & 0xFF))        * V6_FNV_PRIME;
        h = (h ^ ((cp >>> 24) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cp >>> 16) & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ ((cp >>> 8)  & 0xFF)) * V6_FNV_PRIME;
        h = (h ^ (cp & 0xFF))          * V6_FNV_PRIME;
        return h;
    }

    public static long secondaryHashV6(int cpA, int cpB) {
        long h = 0xff51afd7ed558ccdL;
        h = (h ^ Integer.reverse(cpA)) * 0xc4ceb9fe1a85ec53L;
        h = (h ^ Integer.reverse(cpB)) * 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** Adds the codepoint pair to the Bloom filter using double-hashing. */
    public static void bloomAddV6(long[] bloomBits, int bitCount, int k,
                                  int cpA, int cpB, int seed) {
        long h1 = fnv1aBigramV6(cpA, cpB, seed);
        long h2 = secondaryHashV6(cpA, cpB);
        for (int i = 0; i < k; i++) {
            long pos = ((h1 + (long) i * h2) & 0x7FFFFFFFFFFFFFFFL) % bitCount;
            bloomBits[(int) (pos >>> 6)] |= 1L << (pos & 63);
        }
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

    /**
     * Returns an ordered list of all recognized {@link Character.UnicodeScript} names
     * (excluding COMMON, INHERITED, UNKNOWN pseudo-scripts), sorted alphabetically,
     * with "OTHER" appended as the final fallback bucket.
     *
     * <p>Using raw UnicodeScript names (not the SCRIPT_MODEL_FALLBACK-mapped names)
     * preserves discrimination power: clean Japanese text has characteristic
     * KANJI→HIRAGANA→KATAKANA transitions that char-shuffle disrupts, which would
     * be lost if all three were merged into "HAN".
     */
    static List<String> buildScriptBuckets() {
        List<String> buckets = new ArrayList<>();
        for (Character.UnicodeScript s : Character.UnicodeScript.values()) {
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                buckets.add(s.name());
            }
        }
        Collections.sort(buckets);
        buckets.add("OTHER");
        return buckets;
    }

    /**
     * Trains a global {@code numBuckets×numBuckets} script-transition log-probability
     * table by pooling all training files.  Uses raw {@link Character.UnicodeScript}
     * values (not the SCRIPT_MODEL_FALLBACK mapping) so that HIRAGANA, KATAKANA, and
     * HAN remain distinct buckets.
     *
     * @return float[numBuckets * numBuckets] where index {@code a*numBuckets+b} = log P(script_b | script_a)
     */
    static float[] trainScriptTransitionTable(List<Path> trainFiles,
                                               Map<String, Integer> scriptBucketMap,
                                               int numBuckets) throws IOException {
        long[] counts = new long[numBuckets * numBuckets];
        int otherBucket = numBuckets - 1;
        long totalTransitions = 0;

        for (Path trainFile : trainFiles) {
            try (BufferedReader r = openGzipped(trainFile)) {
                String line;
                while ((line = r.readLine()) != null) {
                    int prev = -1;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                        if (s == Character.UnicodeScript.COMMON
                                || s == Character.UnicodeScript.INHERITED
                                || s == Character.UnicodeScript.UNKNOWN) {
                            continue;
                        }
                        int bucket = scriptBucketMap.getOrDefault(s.name(), otherBucket);
                        if (prev >= 0) {
                            counts[prev * numBuckets + bucket]++;
                            totalTransitions++;
                        }
                        prev = bucket;
                    }
                }
            }
        }
        System.out.printf("%,d script transitions across %d files%n", totalTransitions, trainFiles.size());
        return laplaceSmoothLogProb(counts, numBuckets);
    }

    /**
     * Calibrates the script-transition feature by computing mu and sigma over pooled
     * dev windows from all scripts.
     *
     * @return float[2] = {mu, sigma}
     */
    static float[] calibrateScriptTransitions(List<Path> devFiles,
                                               float[] scriptTransTable,
                                               Map<String, Integer> scriptBucketMap,
                                               int numBuckets) throws IOException {
        List<Double> scores = new ArrayList<>();
        int otherBucket = numBuckets - 1;
        for (Path devFile : devFiles) {
            List<String> windows = sampleSubstrings(devFile, 200, CALIB_LENGTHS, 45);
            for (String window : windows) {
                double raw = rawScriptTransitionLogProb(window, scriptTransTable,
                        scriptBucketMap, numBuckets, otherBucket);
                if (!Double.isNaN(raw)) {
                    scores.add(raw);
                }
            }
        }
        System.out.printf("%,d dev windows pooled%n", scores.size());
        return muSigma(scores);
    }

    /**
     * Returns the mean script-transition log-probability for a string, or
     * {@link Double#NaN} if there are fewer than two non-neutral codepoints.
     * Uses raw {@link Character.UnicodeScript} values (no SCRIPT_MODEL_FALLBACK).
     */
    private static double rawScriptTransitionLogProb(String text, float[] table,
                                                      Map<String, Integer> bucketMap,
                                                      int numBuckets, int otherBucket) {
        int prev = -1;
        double sum = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s == Character.UnicodeScript.COMMON
                    || s == Character.UnicodeScript.INHERITED
                    || s == Character.UnicodeScript.UNKNOWN) {
                continue;
            }
            int bucket = bucketMap.getOrDefault(s.name(), otherBucket);
            if (prev >= 0) {
                sum += table[prev * numBuckets + bucket];
                count++;
            }
            prev = bucket;
        }
        return count > 0 ? sum / count : Double.NaN;
    }

    /**
     * Builds a character→character remap table for a (sourceCodec, wrongCodec) pair.
     * For every byte 0x80–0xFF, if the two codecs decode it to different characters
     * (and neither produces the replacement character U+FFFD), the source character
     * maps to the wrong-codec character.
     *
     * <p>Returns an empty map if either codec is unavailable on this JVM.
     */
    static Map<Character, Character> buildRemapTable(String sourceCodec, String wrongCodec) {
        Charset src, wrong;
        try {
            src  = Charset.forName(sourceCodec);
            wrong = Charset.forName(wrongCodec);
        } catch (UnsupportedCharsetException e) {
            return Collections.emptyMap();
        }
        Map<Character, Character> table = new HashMap<>();
        byte[] singleByte = new byte[1];
        for (int b = 0x80; b <= 0xFF; b++) {
            singleByte[0] = (byte) b;
            String fromSrc  = new String(singleByte, src);
            String fromWrong = new String(singleByte, wrong);
            if (fromSrc.length() == 1 && fromWrong.length() == 1
                    && fromSrc.charAt(0) != '\uFFFD' && fromWrong.charAt(0) != '\uFFFD'
                    && fromSrc.charAt(0) != fromWrong.charAt(0)) {
                table.put(fromSrc.charAt(0), fromWrong.charAt(0));
            }
        }
        return table;
    }

    /**
     * Replaces characters using a pre-computed wrong-codec remap table, simulating
     * the effect of encoding text in one charset and decoding it in another.
     * Only characters present in the remap table are candidates for replacement.
     *
     * <p>This produces realistic mojibake: German umlauts becoming Hebrew letters,
     * Polish characters becoming Western accents, Cyrillic becoming Latin symbols, etc.
     *
     * @param remapTable source-char → wrong-char substitution table (from {@link #buildRemapTable})
     * @param rate       fraction of remappable characters to replace [0, 1]
     */
    static String wrongCodecRemap(String text, Map<Character, Character> remapTable,
                                   double rate, Random rng) {
        if (text.isEmpty() || remapTable.isEmpty()) {
            return text;
        }
        int[] codepoints = text.codePoints().toArray();
        for (int i = 0; i < codepoints.length; i++) {
            if (codepoints[i] < 0x10000 && rng.nextDouble() < rate) {
                Character replacement = remapTable.get((char) codepoints[i]);
                if (replacement != null) {
                    codepoints[i] = replacement;
                }
            }
        }
        return new String(codepoints, 0, codepoints.length);
    }

    /**
     * Collects a sample of codepoints from each raw {@link Character.UnicodeScript}
     * found across all training files.  Used to build the foreign-script codepoint
     * pools for the cross-script substitution distortion.
     *
     * @param maxPerScript maximum distinct codepoints to collect per script
     * @return map from raw UnicodeScript name → list of sampled codepoints
     */
    static Map<String, List<Integer>> collectScriptCodepoints(List<Path> trainFiles,
                                                               int maxPerScript)
            throws IOException {
        Map<String, Set<Integer>> collected = new HashMap<>();
        for (Path trainFile : trainFiles) {
            try (BufferedReader r = openGzipped(trainFile)) {
                String line;
                while ((line = r.readLine()) != null) {
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                        if (s == Character.UnicodeScript.COMMON
                                || s == Character.UnicodeScript.INHERITED
                                || s == Character.UnicodeScript.UNKNOWN) {
                            continue;
                        }
                        Set<Integer> pool = collected.computeIfAbsent(
                                s.name(), k -> new HashSet<>());
                        if (pool.size() < maxPerScript) {
                            pool.add(cp);
                        }
                    }
                }
            }
        }
        Map<String, List<Integer>> result = new HashMap<>(collected.size() * 2);
        for (Map.Entry<String, Set<Integer>> e : collected.entrySet()) {
            result.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return result;
    }

    /**
     * Replaces a random fraction of characters with codepoints drawn from scripts
     * that do NOT appear in the source text.  Simulates real-world charset encoding
     * errors where accented characters in one script are misread as characters from
     * a completely different script — e.g., German umlauts (ä, ö, ü) becoming
     * Hebrew letters when CP1252-encoded text is decoded as CP1255.
     *
     * @param rate            fraction of characters to replace [0, 1]
     * @param scriptCodepoints map from raw UnicodeScript name → pool of codepoints
     */
    static String injectCrossScriptChars(String text, double rate, Random rng,
                                          Map<String, List<Integer>> scriptCodepoints) {
        if (text.isEmpty() || scriptCodepoints.isEmpty()) {
            return text;
        }

        // Identify which scripts appear in the source text
        Set<String> sourceScripts = new HashSet<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                sourceScripts.add(s.name());
            }
        }

        // Build pool of codepoints from all other scripts
        List<Integer> foreignPool = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : scriptCodepoints.entrySet()) {
            if (!sourceScripts.contains(e.getKey())) {
                foreignPool.addAll(e.getValue());
            }
        }
        if (foreignPool.isEmpty()) {
            return text;
        }

        int[] codepoints = text.codePoints().toArray();
        for (int i = 0; i < codepoints.length; i++) {
            if (rng.nextDouble() < rate) {
                codepoints[i] = foreignPool.get(rng.nextInt(foreignPool.size()));
            }
        }
        return new String(codepoints, 0, codepoints.length);
    }

    private static void printUsage() {
        System.err.println("Usage: TrainJunkModel [options]");
        System.err.println("  --data-dir <path>  Directory with {script}.train.gz / .dev.gz files");
        System.err.println("                     (default: ~/datasets/madlad/junkdetect)");
        System.err.println("  --output   <path>  Output model file");
        System.err.println("                     (default: {data-dir}/junkdetect.bin)");
        System.err.println();
        System.err.println("All other training parameters (Bloom filter size, min bigram count, etc.)");
        System.err.println("are fixed in JunkDetectorTrainingConfig and tracked in git.  Edit that");
        System.err.println("file and commit to change them.");
    }
}
