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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.tika.ml.junkdetect.BigramTables;
import org.apache.tika.ml.junkdetect.HtmlContentCleaner;
import org.apache.tika.ml.junkdetect.JunkDetector;

/**
 * Trains the junk detector model from per-script corpus files produced by
 * {@link BuildJunkTrainingData}.
 *
 * <p>z1 (codepoint-bigram log-probability) is trained per script by bucketing
 * every bigram to its script ({@link JunkDetector#forEachScriptBigram}) and
 * building a per-script open-addressing bigram table with unigram backoff.
 * z2 (Unicode block-transition), z3 (control-byte fraction), and z4
 * (script-transition) are single global document-level features.  All features
 * are calibrated (mu/sigma) and combined by a single global contrastive
 * combiner producing {@code logit = w1*z1 + ... + w9*z9 + bias}; positive
 * values indicate clean text.
 *
 * <p>The output model is written in the {@code JUNKDET1} gzipped binary format
 * read by {@link JunkDetector#load} — see that method's javadoc for the byte
 * layout.  The file carries {@link JunkDetector#VERSION}; the loader rejects
 * any other version.
 */
public class TrainJunkModel {

    static final String MAGIC = "JUNKDET1";

    /** Unigram backoff multiplier.  α=1.0 = plain independence. */
    static final float BACKOFF_ALPHA = 1.0f;
    /** Additive smoothing constant for log-prob computation. */
    static final double ADD_ALPHA = 0.01;

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
     * Full-text byte-level mojibake pairs used by {@link #byteLevelMojibake}.
     * Each entry is {sourceCodec, wrongCodec}: training text gets encoded in
     * sourceCodec, then the resulting bytes are re-decoded as wrongCodec to
     * produce realistic mojibake.  Covers SBCS sibling confusion (1252↔1250,
     * etc.), UTF-8 ↔ Latin (TIKA-4683), and CJK siblings (the GB18030↔EUC-JP
     * cohort that was -14817 in the 29K eval).  For codec pairs that share
     * an ASCII subset, ASCII-only training samples pass through unchanged
     * (no-op corruption), so the list is safe to apply across all scripts.
     */
    static final String[][] BYTE_LEVEL_MOJIBAKE_PAIRS = {
        // SBCS Western family
        {"windows-1252", "windows-1250"},
        {"windows-1250", "windows-1252"},
        {"windows-1252", "windows-1257"},
        {"windows-1257", "windows-1252"},
        {"windows-1252", "windows-1254"},
        {"ISO-8859-1", "windows-1252"},
        {"windows-1252", "ISO-8859-1"},
        {"x-MacRoman", "windows-1252"},
        // The exact win-1252/ISO-8859-2 sibling pathology: a win-1252 page with
        // ©/®/£ symbols read as ISO-8859-2 yields isolated Latin-Extended-A
        // letters (Š/Ž/Ł). Included as classifier negatives so the LR trains
        // against this pattern directly.
        {"windows-1252", "ISO-8859-2"},
        {"ISO-8859-2", "windows-1252"},
        // SBCS Cyrillic / Greek / RTL
        {"windows-1251", "windows-1252"},
        {"windows-1252", "windows-1251"},
        {"windows-1253", "windows-1252"},
        {"windows-1252", "windows-1253"},
        {"windows-1255", "windows-1252"},
        {"windows-1252", "windows-1255"},
        {"windows-1256", "windows-1252"},
        // Polish ¶ emblem and Central European
        {"ISO-8859-2", "windows-1250"},
        {"windows-1250", "ISO-8859-2"},
        {"ISO-8859-3", "windows-1250"},
        // Vietnamese
        {"windows-1258", "windows-1252"},
        {"windows-1252", "windows-1258"},
        // UTF-8 → Latin (TIKA-4683 / AIT5)
        {"UTF-8",      "windows-1252"},
        {"UTF-8",      "ISO-8859-1"},
        // UTF-16 → various — bytes-as-UTF-16 produces dense CJK ideographs
        // (the AIT5 / TIKA-4683 shape); included for HAN-classifier training
        // against this cohort.
        {"UTF-8",      "UTF-16LE"},
        {"UTF-8",      "UTF-16BE"},
        // CJK siblings
        {"GB18030",    "EUC-JP"},
        {"EUC-JP",     "GB18030"},        // reverse
        {"GB18030",    "Shift_JIS"},      // CJK siblings
        {"Shift_JIS",  "GB18030"},        // reverse
        {"Big5-HKSCS", "GB18030"},        // CJK siblings
        {"GB18030",    "Big5-HKSCS"},     // reverse
        // Latin → CJK: the SPECIFIC pattern that produces our 66 wrong-CJK
        // over-adoption cases.  Western European accents (0xC0-0xFE in
        // windows-1252) are valid 2-byte CJK lead bytes; GB18030/Shift_JIS/etc
        // decoders consume them as the lead of a multi-byte sequence, which
        // (a) inserts singleton Han characters scattered through Latin text
        // and (b) eats the byte after each accent.  Produces the
        // long-Latin-with-singleton-HAN fragmentation that z9 measures.
        // Without these pairs the LATIN classifier never sees this pattern
        // in its negatives and the LR fits w9 = 0.
        {"windows-1252", "GB18030"},
        {"windows-1252", "Shift_JIS"},
        {"windows-1252", "EUC-JP"},
        {"windows-1252", "Big5-HKSCS"},
        {"ISO-8859-1",   "GB18030"},
        {"ISO-8859-1",   "Shift_JIS"},
    };

    /**
     * The LATIN→CJK subset of {@link #BYTE_LEVEL_MOJIBAKE_PAIRS}, used by
     * {@link #trainGlobalCombiner} to add extra LATIN→CJK contrastive
     * negatives.  Western-European accents are valid CJK lead bytes, so
     * decoding Latin text as GB18030/Shift_JIS/etc. scatters singleton Han
     * characters through it — the high-alternation pattern z9 measures.
     * Without these the combiner never sees the pattern and fits w9 ≈ 0.
     */
    static final String[][] LATIN_TO_CJK_PAIRS = {
        {"windows-1252", "GB18030"},
        {"windows-1252", "Shift_JIS"},
        {"windows-1252", "EUC-JP"},
        {"windows-1252", "Big5-HKSCS"},
        {"ISO-8859-1",   "GB18030"},
        {"ISO-8859-1",   "Shift_JIS"},
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
        // Training data and output paths are intentionally NOT CLI-configurable.
        // Single bundled model in git; never train to /tmp/; never maintain
        // parallel A/B variants.  Edit and commit if these need to change.
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "data", "junk-augmented-symbolboost");
        Path output = Paths.get(
                "tika-ml/tika-ml-junkdetect/src/main/resources",
                "org/apache/tika/ml/junkdetect/junkdetect.bin");

        // Durable training parameters live in JunkDetectorTrainingConfig; this
        // tool deliberately refuses CLI overrides so a built model file's
        // identity always matches a committed config.
        int minBigramCount = JunkDetectorTrainingConfig.MIN_BIGRAM_COUNT;
        double loadFactor = JunkDetectorTrainingConfig.OA_LOAD_FACTOR;
        int keyIndexBits = JunkDetectorTrainingConfig.KEY_INDEX_BITS;
        if (minBigramCount < 1) {
            System.err.println("ERROR: MIN_BIGRAM_COUNT must be >= 1");
            System.exit(1);
        }
        if (loadFactor <= 0 || loadFactor >= 1) {
            System.err.println("ERROR: OA_LOAD_FACTOR must be in (0, 1), got " + loadFactor);
            System.exit(1);
        }
        if (keyIndexBits < 1 || keyIndexBits > 16) {
            System.err.println("ERROR: KEY_INDEX_BITS must be in [1, 16], got " + keyIndexBits);
            System.exit(1);
        }

        if (args.length > 0) {
            System.err.println("TrainJunkModel takes no arguments.  Data-dir and"
                    + " output path are baked in; edit the source if they need"
                    + " to change.  Other training parameters live in"
                    + " JunkDetectorTrainingConfig.");
            System.exit(1);
        }

        System.out.println("=== TrainJunkModel ===");
        System.out.println("  data-dir:           " + dataDir);
        System.out.println("  output:             " + output);
        System.out.println("  --- format constants (TrainJunkModel) ---");
        System.out.printf( "  backoff_alpha:      %.2f%n", BACKOFF_ALPHA);
        System.out.println("  --- config (JunkDetectorTrainingConfig) ---");
        System.out.printf( "  min_bigram_count:   %d%n", minBigramCount);
        System.out.printf( "  oa_load_factor:     %.2f%n", loadFactor);
        System.out.printf( "  key_index_bits:     %d%n", keyIndexBits);

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
        // Phase 1 — bucket-by-script bigram tables (per real script + COMMON).
        // ONE tally pass over every train file via forEachScriptBigram: digits
        // skipped, COMMON glue folded into the adjacent script, both-COMMON
        // bigrams charged to the COMMON bucket.  No runs, no sentinels.  COMMON
        // is a normal bucket (its own table + calibration + combiner weight).
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 1: bucket-by-script F1 tables ---");
        Map<String, HashMap<Long, long[]>> pairsByScript = new HashMap<>();
        Map<String, HashMap<Integer, long[]>> unigramsByScript = new HashMap<>();
        Map<String, long[]> totalsByScript = new HashMap<>();
        for (Path trainFile : trainFiles) {
            allTrainFiles.add(trainFile);
            String filename = trainFile.getFileName().toString();
            String script = filename.substring(0, filename.length() - ".train.gz".length())
                    .toUpperCase();
            trainFilePaths.put(script, trainFile);
            tallyFileBuckets(trainFile, pairsByScript, unigramsByScript, totalsByScript);
        }

        TreeMap<String, BigramTables> f1TablesByScript = new TreeMap<>();
        for (String script : new java.util.TreeSet<>(pairsByScript.keySet())) {
            t0 = System.currentTimeMillis();
            long[] totals = totalsByScript.get(script);
            BigramTables tables = buildBigramTablesFromCounts(pairsByScript.get(script),
                    unigramsByScript.get(script), totals[0],
                    minBigramCount, loadFactor, keyIndexBits);
            f1TablesByScript.put(script, tables);
            System.out.printf("  [%s] %s (%dms)%n", script, tables.statsString(),
                    System.currentTimeMillis() - t0);
        }

        // Phase 1b — per-script (incl COMMON) z1 calibration on the bucket mean.
        // Sample windows from all files ONCE; for each window distribute every
        // present script bucket's mean to that script's score list, then muSigma.
        // This is exactly the inference z1 input, so calibration cannot drift.
        System.out.println("\n--- Phase 1b: per-script z1 calibration ---");
        Map<String, List<Double>> z1ScoresByScript = new HashMap<>();
        int calibSeed = 42;
        for (Path f : trainFiles) {
            for (String window : sampleSubstrings(f, CALIB_SAMPLES, CALIB_LENGTHS, calibSeed++)) {
                int[] cps = window.codePoints().toArray();
                Map<String, double[]> b = JunkDetector.bucketSumsAndCounts(cps, f1TablesByScript);
                for (Map.Entry<String, double[]> e : b.entrySet()) {
                    if (e.getValue()[1] > 0) {
                        z1ScoresByScript.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                                .add(e.getValue()[0] / e.getValue()[1]);
                    }
                }
            }
        }
        for (String script : f1TablesByScript.keySet()) {
            List<Double> scores = z1ScoresByScript.getOrDefault(script, new ArrayList<>());
            float[] cal = scores.isEmpty() ? new float[]{0f, 1f} : muSigma(scores);
            f1Calibrations.put(script, cal);
            System.out.printf("  [%s] mu=%.4f sigma=%.4f (%,d windows)%n",
                    script, cal[0], cal[1], scores.size());
        }

        // -----------------------------------------------------------------------
        // Phase 2 — global script-transition table (z4) + single GLOBAL block
        // table (z2) + single GLOBAL control-byte calibration (z3).
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 2: global script-transition + block + control ---");
        List<String> scriptBuckets = buildScriptBuckets();
        int numScriptBuckets = scriptBuckets.size();
        Map<String, Integer> scriptBucketMap = new LinkedHashMap<>();
        for (int i = 0; i < numScriptBuckets; i++) {
            scriptBucketMap.put(scriptBuckets.get(i), i);
        }
        float[] scriptTransTable = trainScriptTransitionTable(allTrainFiles, scriptBucketMap, numScriptBuckets);
        scriptTransTable = quantizeDequantizeRoundTrip(scriptTransTable);
        float[] scriptTransCal = calibrateScriptTransitions(allTrainFiles, scriptTransTable,
                scriptBucketMap, numScriptBuckets);
        System.out.printf("  scriptTrans: mu=%.4f sigma=%.4f%n",
                scriptTransCal[0], scriptTransCal[1]);

        float[] blockTable = quantizeDequantizeRoundTrip(trainGlobalBlockTable(allTrainFiles));
        float[] blockCal = computeGlobalBlockCalibration(allTrainFiles, blockTable);
        System.out.printf("  block:       mu=%.4f sigma=%.4f%n", blockCal[0], blockCal[1]);

        float[] controlCal = computeGlobalControlCalibration(allTrainFiles);
        System.out.printf("  control:     mu=%.6f sigma=%.6f%n", controlCal[0], controlCal[1]);

        // -----------------------------------------------------------------------
        // Phase 3 — ONE global combiner over z1..z9, trained pointwise
        // (clean>garbage) + contrastive (correct-decode>wrong, incl. RTL
        // logical>reversed).  Features extracted through a temp model so they are
        // exactly the inference features.  Applies to every bucket incl COMMON.
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 3: global contrastive combiner ---");
        float[] placeholder = new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};
        Path tmpModel = Files.createTempFile("junkdetect-feat", ".bin");
        saveModel(f1TablesByScript, f1Calibrations, blockTable, blockCal, controlCal,
                placeholder, scriptBuckets, scriptTransTable, scriptTransCal, tmpModel);
        JunkDetector featExtractor = JunkDetector.loadFromPath(tmpModel);
        Files.deleteIfExists(tmpModel);

        t0 = System.currentTimeMillis();
        float[] combiner = trainGlobalCombiner(featExtractor, trainFilePaths);
        System.out.printf(
                "  global w=[%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f] bias=%.3f (%dms)%n",
                combiner[0], combiner[1], combiner[2], combiner[3], combiner[4], combiner[5],
                combiner[6], combiner[7], combiner[8], combiner[9],
                System.currentTimeMillis() - t0);

        System.out.printf("%nWriting model (%d scripts, blockN=%d, scriptBuckets=%d) → %s%n",
                f1TablesByScript.size(), blockN, numScriptBuckets, output);
        saveModel(f1TablesByScript, f1Calibrations, blockTable, blockCal, controlCal,
                combiner, scriptBuckets, scriptTransTable, scriptTransCal, output);
        System.out.printf("Model size: %,d bytes (%.1f KB)%n",
                Files.size(output), Files.size(output) / 1024.0);
        System.out.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

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
            String s = new String(bytes, start, end - start, StandardCharsets.UTF_8);
            // NFD-normalize on read so calibration/training feature math
            // matches JunkDetector.scoreText's NFD path.  On-disk corpus
            // may be NFC (older builds of BuildJunkTrainingData); NFD is
            // idempotent on already-NFD text.
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
            result.add(s);
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Bucket-by-script tally + global (z2/z3) calibration
    // -----------------------------------------------------------------------

    /**
     * Tallies bucket-by-script bigram + unigram counts from a train file using
     * {@link JunkDetector#forEachScriptBigram} — the SAME enumeration inference
     * scores, so trained tables fit scored bigrams.  Each emitted bigram is
     * charged to its script's pair map; both endpoints are counted as unigrams
     * (no single contiguous sequence exists in the bucket model — the unigram
     * normalization makes the 2x factor wash out).
     */
    static void tallyFileBuckets(Path trainFile,
            Map<String, HashMap<Long, long[]>> pairsByScript,
            Map<String, HashMap<Integer, long[]>> unigramsByScript,
            Map<String, long[]> totalsByScript) throws IOException {
        try (BufferedReader r = openGzipped(trainFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String norm = java.text.Normalizer.normalize(
                        line, java.text.Normalizer.Form.NFC);
                int[] cps = norm.codePoints().toArray();
                JunkDetector.forEachScriptBigram(cps, (script, a, b) -> {
                    HashMap<Long, long[]> pairs = pairsByScript.computeIfAbsent(
                            script, k -> new HashMap<>(1 << 14));
                    HashMap<Integer, long[]> uni = unigramsByScript.computeIfAbsent(
                            script, k -> new HashMap<>(1 << 12));
                    long[] totals = totalsByScript.computeIfAbsent(script, k -> new long[2]);
                    long packed = ((long) a << 32) | (b & 0xFFFFFFFFL);
                    long[] bc = pairs.get(packed);
                    if (bc == null) {
                        pairs.put(packed, new long[]{1L});
                    } else {
                        bc[0]++;
                    }
                    totals[1]++;
                    incUnigram(uni, a);
                    incUnigram(uni, b);
                    totals[0] += 2;
                });
            }
        }
    }

    private static void incUnigram(HashMap<Integer, long[]> uni, int cp) {
        long[] uc = uni.get(cp);
        if (uc == null) {
            uni.put(cp, new long[]{1L});
        } else {
            uc[0]++;
        }
    }

    /** Single GLOBAL block-transition log-prob table, pooled over all files. */
    static float[] trainGlobalBlockTable(List<Path> files) throws IOException {
        int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
        long[] counts = new long[blockN * blockN];
        for (Path f : files) {
            try (BufferedReader r = openGzipped(f)) {
                String line;
                while ((line = r.readLine()) != null) {
                    int prev = -1;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        int blockId = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketOf(cp);
                        if (prev >= 0) {
                            counts[prev * blockN + blockId]++;
                        }
                        prev = blockId;
                        i += Character.charCount(cp);
                    }
                }
            }
        }
        return laplaceSmoothLogProb(counts, blockN);
    }

    /** Single GLOBAL z2 (block-transition) calibration, pooled over all files. */
    static float[] computeGlobalBlockCalibration(List<Path> files, float[] blockTable)
            throws IOException {
        int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
        List<Double> scores = new ArrayList<>();
        int seed = 43;
        for (Path f : files) {
            for (String window : sampleSubstrings(f, CALIB_SAMPLES, CALIB_LENGTHS, seed++)) {
                int[] ids = new int[window.length()];
                int len = 0;
                for (int i = 0; i < window.length(); ) {
                    int cp = window.codePointAt(i);
                    ids[len++] = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketOf(cp);
                    i += Character.charCount(cp);
                }
                if (len < 2) {
                    continue;
                }
                double sum = 0;
                for (int i = 0; i + 1 < len; i++) {
                    sum += blockTable[ids[i] * blockN + ids[i + 1]];
                }
                scores.add(sum / (len - 1));
            }
        }
        return muSigma(scores);
    }

    /**
     * Bucket-model z1 calibration {mu, sigma} for one script, sampling windows
     * from {@code file} and taking that script's bucket mean log-prob per window
     * (the inference z1 input).  Public for round-trip tests.
     */
    public static float[] calibrateBucketScript(Path file, String script,
            Map<String, BigramTables> tables) throws IOException {
        List<Double> scores = new ArrayList<>();
        for (String window : sampleSubstrings(file, CALIB_SAMPLES, CALIB_LENGTHS, 42)) {
            double[] bk = JunkDetector.bucketSumsAndCounts(
                    window.codePoints().toArray(), tables).get(script);
            if (bk != null && bk[1] > 0) {
                scores.add(bk[0] / bk[1]);
            }
        }
        return scores.isEmpty() ? new float[]{0f, 1f} : muSigma(scores);
    }

    /** Single GLOBAL z3 (control-byte) calibration, pooled over all files. */
    static float[] computeGlobalControlCalibration(List<Path> files) throws IOException {
        List<Double> scores = new ArrayList<>();
        int seed = 44;
        for (Path f : files) {
            for (String window : sampleSubstrings(f, CALIB_SAMPLES, CALIB_LENGTHS, seed++)) {
                byte[] bytes = window.getBytes(StandardCharsets.UTF_8);
                if (bytes.length == 0) {
                    continue;
                }
                long controlCount = 0;
                for (byte b : bytes) {
                    if (isControlByte(b & 0xFF)) {
                        controlCount++;
                    }
                }
                scores.add(-(double) controlCount / bytes.length);
            }
        }
        if (scores.isEmpty()) {
            return new float[]{0f, CONTROL_BYTE_MIN_SIGMA};
        }
        double mu = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = scores.stream()
                .mapToDouble(s -> (s - mu) * (s - mu))
                .average().orElse(0);
        double sigma = Math.max(Math.sqrt(variance), CONTROL_BYTE_MIN_SIGMA);
        return new float[]{(float) mu, (float) sigma};
    }

    // -----------------------------------------------------------------------
    // Corruption helpers (used to synthesize combiner training negatives)
    // -----------------------------------------------------------------------

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
     * Reverses the codepoint order of the text.  Models the PDF/OCR
     * BiDi-direction-flip failure on RTL scripts (Arabic, Hebrew,
     * Syriac, N'Ko, Thaana) where extraction tools sometimes emit
     * runs in visual order rather than logical order — producing
     * readable-looking-but-meaningless text.  Applied only when the
     * dominant script is RTL; passthrough for LTR scripts.
     */
    static String reverseRtlText(String text) {
        if (text.isEmpty()) return text;
        Character.UnicodeScript dom = dominantScriptOf(text);
        if (dom != Character.UnicodeScript.ARABIC
                && dom != Character.UnicodeScript.HEBREW
                && dom != Character.UnicodeScript.SYRIAC
                && dom != Character.UnicodeScript.NKO
                && dom != Character.UnicodeScript.THAANA) {
            return text;
        }
        int[] cps = text.codePoints().toArray();
        for (int i = 0, j = cps.length - 1; i < j; i++, j--) {
            int tmp = cps[i];
            cps[i] = cps[j];
            cps[j] = tmp;
        }
        return new String(cps, 0, cps.length);
    }

    /**
     * Injects codepoints from the Unicode Private Use Area
     * (U+E000–U+F8FF) at the given rate.  Models PDF text extraction
     * where a broken / missing cmap table emits blocks of PUA chars
     * instead of real text — a common PDF-junk failure that
     * JunkDetector should catch outside its charset-arbitration role.
     */
    static String injectPrivateUseAreaChars(String text, double rate, Random rng) {
        if (text.isEmpty()) return text;
        int[] codepoints = text.codePoints().toArray();
        int puaSpan = 0xF8FF - 0xE000 + 1;
        for (int i = 0; i < codepoints.length; i++) {
            if (rng.nextDouble() < rate) {
                codepoints[i] = 0xE000 + rng.nextInt(puaSpan);
            }
        }
        return new String(codepoints, 0, codepoints.length);
    }

    /**
     * Strips combining marks (Mn / Mc / Me categories) after NFD
     * normalization.  Models the PDF/OCR pipeline that drops marks
     * during extraction — Vietnamese / Arabic / Indic content gets
     * stripped of its tone / vowel marks, which destroys meaning
     * while leaving the base letters intact.  Also useful training
     * signal for z5 (letter-adjacent-to-mark) since stripped text
     * has z5 = 0.
     */
    static String shedDiacritics(String text) {
        if (text.isEmpty()) return text;
        String nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(nfd.length());
        for (int i = 0; i < nfd.length(); ) {
            int cp = nfd.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /** Visual OCR substitution pairs — pairs that confuse OCR
     *  recognition: O↔0, 1↔l↔I, rn↔m, cl↔d, etc. */
    private static final char[][] OCR_SUBS = {
            {'O', '0'}, {'0', 'O'},
            {'1', 'l'}, {'l', '1'},
            {'I', 'l'}, {'l', 'I'},
            {'S', '5'}, {'5', 'S'},
            {'B', '8'}, {'8', 'B'},
            {'Z', '2'}, {'2', 'Z'},
            {'G', '6'}, {'6', 'G'},
    };

    /**
     * Applies single-char OCR-confusion substitutions at the given
     * rate.  Models OCR errors where visually-similar chars are
     * misrecognised; doesn't change byte structure but corrupts the
     * bigram distribution.
     */
    static String visualOcrSubstitutions(String text, double rate, Random rng) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp < 0x80 && rng.nextDouble() < rate) {
                for (char[] pair : OCR_SUBS) {
                    if (pair[0] == cp) {
                        cp = pair[1];
                        break;
                    }
                }
            }
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    /**
     * Removes all whitespace from the text.  Models PDF columnar
     * extraction that stitches words together without spaces.
     */
    static String collapseWhitespace(String text) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isWhitespace(cp)) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /**
     * Injects random ASCII spaces inside the text at the given rate.
     * Models PDF kerning bugs that fragment words with stray spaces.
     */
    static String inflateWhitespace(String text, double rate, Random rng) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder((int) (text.length() * (1 + rate)));
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            sb.appendCodePoint(cp);
            if (rng.nextDouble() < rate) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Picks a random codepoint and duplicates it 5–10 times in place.
     * Models OCR sticky-character artifacts and scanner repeat-row
     * bugs where a single glyph gets emitted as a run.
     */
    static String repeatByteStorm(String text, Random rng) {
        if (text.isEmpty()) return text;
        int[] cps = text.codePoints().toArray();
        if (cps.length == 0) return text;
        int target = rng.nextInt(cps.length);
        int repeats = 5 + rng.nextInt(6);
        StringBuilder sb = new StringBuilder(cps.length + repeats);
        for (int i = 0; i < cps.length; i++) {
            sb.appendCodePoint(cps[i]);
            if (i == target) {
                for (int r = 0; r < repeats; r++) {
                    sb.appendCodePoint(cps[i]);
                }
            }
        }
        return sb.toString();
    }

    /** Dominant Unicode script of {@code text}, COMMON/INHERITED/UNKNOWN
     *  excluded.  Used by {@link #reverseRtlText} to gate the corruption
     *  on RTL scripts only. */
    private static Character.UnicodeScript dominantScriptOf(String text) {
        if (text == null || text.isEmpty()) {
            return Character.UnicodeScript.COMMON;
        }
        Map<Character.UnicodeScript, Integer> counts = new HashMap<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                counts.merge(s, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Character.UnicodeScript.COMMON);
    }

    /**
     * Full-text byte-level mojibake: encodes the text in {@code sourceCs}
     * and decodes the resulting bytes as {@code wrongCs}.  ASCII-only text
     * is unchanged for ASCII-superset codec pairs (UTF-8↔Latin-1,
     * GB18030↔EUC-JP, etc.).  For non-ASCII content the result is the
     * realistic mojibake pattern that production charset mis-detection
     * produces.
     *
     * <p>Trains the z5 (letter-adjacent-to-mark) feature: real
     * mark-using text has letters followed by combining marks; the
     * mojibake decode loses all marks (they become precomposed Latin-1
     * letters or replacement chars).  Also exercises z6 because the
     * reinterpret typically introduces some U+FFFD on partial/invalid
     * sequences.
     *
     * <p>Returns the input unchanged on encoder/decoder error (preserves
     * positive training signal in those edge cases).
     */
    static String byteLevelMojibake(String text, String sourceCs, String wrongCs) {
        if (text.isEmpty()) return text;
        try {
            byte[] bytes = text.getBytes(Charset.forName(sourceCs));
            return new String(bytes, Charset.forName(wrongCs));
        } catch (IllegalArgumentException e) {
            // Covers UnsupportedCharsetException + IllegalCharsetNameException
            return text;
        }
    }

    // -----------------------------------------------------------------------
    // Model serialisation
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // Per-script bigram table training
    // -----------------------------------------------------------------------

    /**
     * Builds the {@link BigramTables} carrier for the dominant script bucket of
     * one {@code *.train.gz} file.  Tallies the file via the shared
     * {@link JunkDetector#forEachScriptBigram} enumeration (so trained tables
     * fit scored bigrams), then builds tables from the bucket whose name matches
     * the file's script (derived from the filename), falling back to the bucket
     * with the most bigrams.  Convenience for tests; the full pipeline tallies
     * all files together in {@link #main}.
     *
     * @param trainFile         the per-script {@code *.train.gz}
     * @param minBigramCount    drop pairs whose count is below this
     * @param loadFactor        target OA table load factor (e.g. 0.5)
     * @param keyIndexBits      bit-width per index in the packed key
     *                          (each side of the pair must fit)
     */
    public static BigramTables trainBigramTablesForScript(Path trainFile,
                                                  int minBigramCount,
                                                  double loadFactor,
                                                  int keyIndexBits) throws IOException {
        Map<String, HashMap<Long, long[]>> pairsByScript = new HashMap<>();
        Map<String, HashMap<Integer, long[]>> unigramsByScript = new HashMap<>();
        Map<String, long[]> totalsByScript = new HashMap<>();
        tallyFileBuckets(trainFile, pairsByScript, unigramsByScript, totalsByScript);

        String filename = trainFile.getFileName().toString();
        String script = filename.endsWith(".train.gz")
                ? filename.substring(0, filename.length() - ".train.gz".length()).toUpperCase()
                : null;
        if (script == null || !pairsByScript.containsKey(script)) {
            // Fall back to the bucket with the most bigrams.
            script = null;
            long best = -1;
            for (Map.Entry<String, long[]> e : totalsByScript.entrySet()) {
                if (e.getValue()[1] > best) {
                    best = e.getValue()[1];
                    script = e.getKey();
                }
            }
        }
        long[] totals = totalsByScript.getOrDefault(script, new long[2]);
        return buildBigramTablesFromCounts(
                pairsByScript.getOrDefault(script, new HashMap<>()),
                unigramsByScript.getOrDefault(script, new HashMap<>()),
                totals[0], minBigramCount, loadFactor, keyIndexBits);
    }

    /**
     * Builds the {@link BigramTables} carrier from pre-tallied pair/unigram
     * counts.  Drops pairs below
     * {@code minBigramCount}, assigns dense codepoint indices, and packs an
     * open-addressing bigram table; unigram log-probs use {@code unigramTotal}
     * as the denominator.
     */
    public static BigramTables buildBigramTablesFromCounts(
            HashMap<Long, long[]> pairCounts,
            HashMap<Integer, long[]> unigramCounts,
            long unigramTotal,
            int minBigramCount,
            double loadFactor,
            int keyIndexBits) {

        // --- Filter pairs by count, collect kept-codepoint set. ---
        int totalDistinct = pairCounts.size();
        int keptPairs = 0;
        long keptBigramTotal = 0;
        java.util.TreeSet<Integer> keptCodepoints = new java.util.TreeSet<>();
        for (Map.Entry<Long, long[]> e : pairCounts.entrySet()) {
            if (e.getValue()[0] < minBigramCount) continue;
            keptPairs++;
            keptBigramTotal += e.getValue()[0];
            long packed = e.getKey();
            int cpA = (int) (packed >>> 32);
            int cpB = (int) (packed & 0xFFFFFFFFL);
            keptCodepoints.add(cpA);
            keptCodepoints.add(cpB);
        }
        int dropped = totalDistinct - keptPairs;

        // --- Build sorted codepoint index. ---
        int[] cpIndex = new int[keptCodepoints.size()];
        int idx = 0;
        for (int cp : keptCodepoints) {
            cpIndex[idx++] = cp;
        }
        // Enforce the indexable-bits contract.
        int maxIndex = (1 << keyIndexBits) - 1;
        if (cpIndex.length > maxIndex + 1) {
            throw new IllegalStateException("Per-script codepoint count "
                    + cpIndex.length + " exceeds 2^KEY_INDEX_BITS (= "
                    + (maxIndex + 1) + ").  Increase KEY_INDEX_BITS or apply"
                    + " a tighter pair-count filter.");
        }

        // --- Compute per-pair log-prob (add-α smoothed over kept pairs). ---
        // Denominator: kept-bigram total + α × keptPairs (only pairs we store).
        double bigramDenom = keptBigramTotal + ADD_ALPHA * keptPairs;
        // Unigram log-probs.  We keep one entry per indexed codepoint; the
        // denominator uses ALL unigram observations (kept pairs only would
        // bias the backoff toward common pairs).
        double unigramDenom = unigramTotal + ADD_ALPHA * unigramCounts.size();
        float[] unigramLogP = new float[cpIndex.length];
        for (int i = 0; i < cpIndex.length; i++) {
            long[] uc = unigramCounts.get(cpIndex[i]);
            long count = uc != null ? uc[0] : 0L;
            double p = (count + ADD_ALPHA) / unigramDenom;
            unigramLogP[i] = (float) Math.log(p);
        }
        // Per-script "absent codepoint" fallback: the lowest unigram log-prob
        // we'd assign to a codepoint observed exactly once.  A codepoint
        // *not* in our index has count 0, so:
        double fallbackP = ADD_ALPHA / unigramDenom;
        float unigramFallbackLogP = (float) Math.log(fallbackP);

        // Quantize unigram log-probs.
        QuantizedFloats qUnigram = quantizeFloats(unigramLogP);

        // --- Build the open-addressing bigram table. ---
        int slots = nextPowerOfTwo((int) Math.max(2, Math.ceil(keptPairs / loadFactor)));
        int[] keys = new int[slots];
        java.util.Arrays.fill(keys, BigramTables.EMPTY_KEY);
        // Compute log-probs first, quantize once, then write into the table
        // alongside its key.
        float[] keptLogP = new float[keptPairs];
        int[] keptKeys = new int[keptPairs];
        int writeIdx = 0;
        // codepoint -> index lookup helper (small map keyed by Integer)
        HashMap<Integer, Integer> cpToIdx = new HashMap<>(cpIndex.length * 2);
        for (int i = 0; i < cpIndex.length; i++) {
            cpToIdx.put(cpIndex[i], i);
        }
        for (Map.Entry<Long, long[]> e : pairCounts.entrySet()) {
            long count = e.getValue()[0];
            if (count < minBigramCount) continue;
            long packed = e.getKey();
            int cpA = (int) (packed >>> 32);
            int cpB = (int) (packed & 0xFFFFFFFFL);
            int idxA = cpToIdx.get(cpA);
            int idxB = cpToIdx.get(cpB);
            int packedKey = JunkDetector.packBigramKey(idxA, idxB);
            double p = (count + ADD_ALPHA) / bigramDenom;
            keptKeys[writeIdx] = packedKey;
            keptLogP[writeIdx] = (float) Math.log(p);
            writeIdx++;
        }
        // Quantize all kept log-probs together so they share min/max.
        QuantizedFloats qBigram = quantizeFloats(keptLogP);
        byte[] values = new byte[slots];
        for (int i = 0; i < keptPairs; i++) {
            insertOA(keys, values, keptKeys[i], qBigram.bytes[i]);
        }

        System.out.printf(
                "    pair_counts: distinct=%,d, kept=%,d (>=%d), dropped=%,d  "
                + "cp_index=%,d  slots=%,d (load=%.2f)%n",
                totalDistinct, keptPairs, minBigramCount, dropped,
                cpIndex.length, slots, keptPairs / (double) slots);

        return new BigramTables(cpIndex, keys, values, qUnigram.bytes,
                qBigram.min, qBigram.max,
                qUnigram.min, qUnigram.max,
                unigramFallbackLogP, BACKOFF_ALPHA);
    }

    /**
     * Inserts a {@code (packedKey, value)} pair into the open-addressing
     * table.  The caller is responsible for sizing the table large enough
     * to avoid an infinite probe (any load &lt; 1.0 is safe).
     */
    private static void insertOA(int[] keys, byte[] values, int packedKey, byte value) {
        int mask = keys.length - 1;
        int h = JunkDetector.mixIndexKey(packedKey) & mask;
        while (keys[h] != BigramTables.EMPTY_KEY) {
            if (keys[h] == packedKey) {
                // Same key twice — shouldn't happen with our dedup, but be
                // defensive and overwrite rather than corrupt.
                values[h] = value;
                return;
            }
            h = (h + 1) & mask;
        }
        keys[h] = packedKey;
        values[h] = value;
    }

    private static int nextPowerOfTwo(int n) {
        if (n < 1) return 1;
        int p = Integer.highestOneBit(n - 1) << 1;
        return Math.max(1, p);
    }

    // -----------------------------------------------------------------------
    // Global contrastive combiner training
    // -----------------------------------------------------------------------

    private static final java.util.Set<String> RTL_SCRIPTS = java.util.Set.of(
            "ARABIC", "HEBREW", "SYRIAC", "NKO", "THAANA");

    private static boolean isRtl(String script) {
        return RTL_SCRIPTS.contains(script);
    }

    /**
     * Trains ONE global combiner over z1..z9 + bias.  The pointwise term anchors
     * the absolute junkness scale (clean windows positive, generic garbage
     * negative); the contrastive term ranks decodes (a clean window must outscore
     * every wrong decode of its own bytes, including RTL logical vs reversed).
     * Features come from {@code fx} (a temp model) so they match inference
     * exactly.  Reads only {@code *.train.gz}.
     */
    static float[] trainGlobalCombiner(JunkDetector fx,
                                       TreeMap<String, Path> trainFiles)
            throws IOException {
        List<float[]> good = new ArrayList<>();
        List<float[]> bad = new ArrayList<>();
        List<float[]> pairCorrect = new ArrayList<>();
        List<float[]> pairWrong = new ArrayList<>();
        Random rng = new Random(303);

        for (Map.Entry<String, Path> e : trainFiles.entrySet()) {
            String script = e.getKey();
            boolean rtl = isRtl(script);
            List<String> windows = sampleSubstrings(e.getValue(),
                    NUM_CLASSIFIER_SAMPLES, CALIB_LENGTHS, 300);
            for (String w : windows) {
                float[] fc = featureVector(fx, w);
                if (fc == null) {
                    continue;
                }
                good.add(fc);

                // Contrastive: the clean decode must beat wrong decodes of its bytes.
                for (int k = 0; k < 2; k++) {
                    String[] pr = BYTE_LEVEL_MOJIBAKE_PAIRS[
                            rng.nextInt(BYTE_LEVEL_MOJIBAKE_PAIRS.length)];
                    addContrastivePair(fx, w, byteLevelMojibake(w, pr[0], pr[1]),
                            fc, pairCorrect, pairWrong);
                }
                if ("LATIN".equals(script)) {
                    String[] pr = LATIN_TO_CJK_PAIRS[
                            rng.nextInt(LATIN_TO_CJK_PAIRS.length)];
                    addContrastivePair(fx, w, byteLevelMojibake(w, pr[0], pr[1]),
                            fc, pairCorrect, pairWrong);
                }
                if (rtl) {
                    addContrastivePair(fx, w, reverseRtlText(w), fc,
                            pairCorrect, pairWrong);
                }

                // Pointwise garbage anchor (generic junk, no correct counterpart).
                String junk;
                int mode = rng.nextInt(3);
                if (mode == 0) {
                    junk = injectControlChars(w, 0.15, rng);
                } else if (mode == 1) {
                    junk = shuffleChars(w, rng);
                } else {
                    junk = injectPrivateUseAreaChars(w, 0.12, rng);
                }
                float[] fb = featureVector(fx, junk);
                if (fb != null) {
                    bad.add(fb);
                }
            }
        }
        System.out.printf("  examples: good=%,d bad=%,d pairs=%,d%n",
                good.size(), bad.size(), pairCorrect.size());
        return fitContrastiveCombiner(good, bad, pairCorrect, pairWrong);
    }

    private static void addContrastivePair(JunkDetector fx, String correct,
                                           String wrong, float[] correctFeat,
                                           List<float[]> pc, List<float[]> pw) {
        if (wrong.equals(correct)) {
            return;
        }
        float[] fw = featureVector(fx, wrong);
        if (fw == null) {
            return;
        }
        pc.add(correctFeat);
        pw.add(fw);
    }

    /** z1..z9 for {@code text} via the inference feature path; null if any NaN. */
    private static float[] featureVector(JunkDetector fx, String text) {
        JunkDetector.FeatureComponents f = fx.scoreWithFeatureComponents(text);
        float[] z = {f.z1, f.z2, f.z3, f.z4, f.z5, f.z6, f.z7, f.z8, f.z9};
        for (float v : z) {
            if (Float.isNaN(v)) {
                return null;
            }
        }
        return z;
    }

    /**
     * Fits {@code w[9]+bias} by full-batch gradient descent: pointwise
     * cross-entropy (good=1, bad=0) + pairwise logistic (correct must outscore
     * wrong) + L2.  Feature weights are projected non-negative (every feature is
     * oriented so higher = cleaner); the bias is unconstrained.
     */
    static float[] fitContrastiveCombiner(List<float[]> good, List<float[]> bad,
                                          List<float[]> pairCorrect,
                                          List<float[]> pairWrong) {
        int f = 9;
        double[] w = new double[f];
        double bias = 0;
        double lr = 0.05;
        double lambda = 0.01;
        int epochs = 3000;
        int ng = Math.max(1, good.size());
        int nb = Math.max(1, bad.size());
        int np = Math.max(1, pairCorrect.size());

        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] grad = new double[f];
            double gradB = 0;
            for (float[] x : good) {
                double p = sigmoid(dotF(w, x) + bias);
                for (int j = 0; j < f; j++) {
                    grad[j] += (p - 1) * x[j] / ng;
                }
                gradB += (p - 1) / ng;
            }
            for (float[] x : bad) {
                double p = sigmoid(dotF(w, x) + bias);
                for (int j = 0; j < f; j++) {
                    grad[j] += p * x[j] / nb;
                }
                gradB += p / nb;
            }
            for (int i = 0; i < pairCorrect.size(); i++) {
                float[] c = pairCorrect.get(i);
                float[] wr = pairWrong.get(i);
                double s = sigmoid(-(dotF(w, c) - dotF(w, wr)));
                for (int j = 0; j < f; j++) {
                    grad[j] += -(c[j] - wr[j]) * s / np;
                }
            }
            for (int j = 0; j < f; j++) {
                w[j] -= lr * (grad[j] + lambda * w[j]);
                if (w[j] < 0) {
                    w[j] = 0;
                }
            }
            bias -= lr * gradB;
        }
        float[] out = new float[f + 1];
        for (int j = 0; j < f; j++) {
            out[j] = (float) w[j];
        }
        out[f] = (float) bias;
        return out;
    }

    private static double dotF(double[] w, float[] x) {
        double s = 0;
        for (int j = 0; j < w.length; j++) {
            s += w[j] * x[j];
        }
        return s;
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /**
     * Writes a model file in the current binary format.  Layout: gzip envelope
     * around {@code JUNKDET1} magic + {@link JunkDetector#VERSION} byte + global
     * script-transition / block / control / z5 / z6 / z9 sections + the global
     * combiner weight vector + per-script sections (z1 calibration + bigram
     * tables).  See {@link JunkDetector#load} for the load-side spec.
     */
    public static void saveModel(TreeMap<String, BigramTables> f1Tables,
                                 TreeMap<String, float[]> f1Calibrations,
                                 float[] blockTable,
                                 float[] blockCal,
                                 float[] controlCal,
                                 float[] combinerWeights,
                                 List<String> scriptBuckets,
                                 float[] scriptTransTable,
                                 float[] scriptTransCal,
                                 Path output) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output)))) {

            dos.write(MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(JunkDetector.VERSION); // single source of truth
            dos.writeInt(f1Tables.size());
            dos.writeByte(org.apache.tika.ml.junkdetect.UnicodeBlockRanges.SCHEME_VERSION);

            // z4 — global script-transition section (int16 quantized).
            int numBuckets = scriptBuckets.size();
            dos.writeByte(numBuckets);
            for (String bucketName : scriptBuckets) {
                byte[] nameBytes = bucketName.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);
            }
            QuantizedShorts qScriptTrans = quantizeToShorts(scriptTransTable);
            dos.writeFloat(qScriptTrans.min);
            dos.writeFloat(qScriptTrans.max);
            for (short s : qScriptTrans.shorts) {
                dos.writeShort(s);
            }
            dos.writeFloat(scriptTransCal[0]);
            dos.writeFloat(scriptTransCal[1]);

            // z2 — single GLOBAL block-transition table (int16 quantized).
            QuantizedShorts qBlock = quantizeToShorts(blockTable);
            dos.writeFloat(qBlock.min);
            dos.writeFloat(qBlock.max);
            for (short s : qBlock.shorts) {
                dos.writeShort(s);
            }
            dos.writeFloat(blockCal[0]);
            dos.writeFloat(blockCal[1]);

            // z3 — single GLOBAL control-byte calibration.
            dos.writeFloat(controlCal[0]);
            dos.writeFloat(controlCal[1]);

            // Document-level z5/z6/z9 calibrations (pass-through; LR absorbs scale):
            //   z5 (letter-adjacent-to-mark): mu=0, sigma=1
            //   z6 (replacement-ratio): mu=1, sigma=1 → inference returns 1-raw
            //   z9 (script-alternation): mu=0, sigma=1 → inference returns -raw
            dos.writeFloat(0f); // z5 mu
            dos.writeFloat(1f); // z5 sigma
            dos.writeFloat(1f); // z6 mu (→ 1 - raw)
            dos.writeFloat(1f); // z6 sigma
            dos.writeFloat(0f); // z9 mu (→ -raw)
            dos.writeFloat(1f); // z9 sigma

            // Single GLOBAL combiner weights {w1..wN, bias}.
            int numFeatures = combinerWeights.length - 1;
            dos.writeByte(numFeatures);
            for (float v : combinerWeights) {
                dos.writeFloat(v);
            }

            // Per-script section: z1 calibration + bigram tables (incl COMMON).
            for (var entry : f1Tables.entrySet()) {
                String script = entry.getKey();
                float[] f1Cal = f1Calibrations.getOrDefault(script, new float[]{0f, 1f});
                byte[] nameBytes = script.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);
                dos.writeFloat(f1Cal[0]);
                dos.writeFloat(f1Cal[1]);
                entry.getValue().writeTo(dos);
            }
        }
    }

    /**
     * Quantize a float[] to int16 and dequantize back, returning a new
     * float[] with the int16-precision values.  Used at training time
     * so downstream calibration (mu, sigma) is computed on values the
     * inference path will actually see, eliminating train/infer drift.
     * 65536 levels keep ~0.0002 nats/level resolution, essentially
     * lossless for our [-15, -1] log-prob range.
     */
    public static float[] quantizeDequantizeRoundTrip(float[] in) {
        QuantizedShorts q = quantizeToShorts(in);
        float scale = (q.max - q.min) / 65535f;
        float[] out = new float[in.length];
        for (int i = 0; i < in.length; i++) {
            int s = q.shorts[i] & 0xFFFF;
            out[i] = q.min + s * scale;
        }
        return out;
    }

    /** int16 (unsigned 0-65535) quantization of a float[].  Linear
     *  mapping {@code [min, max] → [0, 65535]}. */
    public static QuantizedShorts quantizeToShorts(float[] in) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (float v : in) {
            if (Float.isFinite(v)) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
        }
        if (!Float.isFinite(min) || !Float.isFinite(max) || max == min) {
            return new QuantizedShorts(new short[in.length], 0f, 1f);
        }
        float scale = 65535f / (max - min);
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            float v = in[i];
            int q = Math.round((v - min) * scale);
            if (q < 0) q = 0;
            if (q > 65535) q = 65535;
            out[i] = (short) q;
        }
        return new QuantizedShorts(out, min, max);
    }

    public static final class QuantizedShorts {
        public final short[] shorts;
        public final float min;
        public final float max;
        public QuantizedShorts(short[] shorts, float min, float max) {
            this.shorts = shorts;
            this.min = min;
            this.max = max;
        }
    }

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

    /**
     * Opens a gzipped train/dev file, applying {@link HtmlContentCleaner#clean}
     * to every line — the same cleaning {@code JunkFilterEncodingDetector} does
     * at inference, so train and inference match.  No-op on clean corpus lines.
     */
    static BufferedReader openGzipped(Path path) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(path)),
                        StandardCharsets.UTF_8)) {
            @Override
            public String readLine() throws IOException {
                String l = super.readLine();
                return l == null ? null : HtmlContentCleaner.clean(l);
            }
        };
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


}
