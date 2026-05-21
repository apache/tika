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

import org.apache.tika.ml.junkdetect.HtmlContentCleaner;
import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.ml.junkdetect.V7Tables;

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

    // -----------------------------------------------------------------------
    // v7 model constants (per-script open-addressing codepoint-bigram tables)
    // -----------------------------------------------------------------------

    /** Unigram backoff multiplier.  α=1.0 = plain independence; prototype validated. */
    static final float V7_BACKOFF_ALPHA = 1.0f;
    /** Additive smoothing constant for log-prob computation. */
    static final double V7_ADD_ALPHA = 0.01;

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
     * Per-script extra positive-sample sources.  For each entry the trainer
     * reads length-prefixed records from {@code file}, decodes under the
     * declared {@code charset}, and adds the resulting Unicode strings to
     * the per-script positive class at fraction {@code fraction} of the
     * primary corpus.  Phase C of the JunkDetector cleanup uses this to
     * augment the LATIN positive class with under-represented Central
     * European and South-East Asian languages (Vietnamese, Polish, Czech,
     * Baltic) sourced from the charset-detection training corpus.
     */
    static final Map<String, List<ExtraPositiveSource>> EXTRA_POSITIVE_SOURCES;

    static {
        Map<String, List<ExtraPositiveSource>> m = new LinkedHashMap<>();
        Path charsetTrain = Paths.get(System.getProperty("user.home"),
                "data", "charsets", "train");
        // Fractions chosen small (0.04 / 0.04 / 0.02) to nudge bigram
        // coverage without drowning out the primary LATIN corpus or
        // collapsing per-script bias/discrimination on Western-Latin
        // (English/Spanish/French) and Baltic test fixtures.  Initial
        // larger fractions (0.15/0.10/0.05) helped Vietnamese but
        // dropped LATIN bias from ~1.6 to ~0.4 and broke the cp1257
        // Baltic discrimination test.
        m.put("LATIN", List.of(
                // Vietnamese (the deferred Phase C target).  windows-1258
                // bytes decoded as windows-1258 give Unicode Vietnamese text
                // that lifts the LATIN bigram model's Vietnamese coverage.
                new ExtraPositiveSource(charsetTrain.resolve("windows-1258.bin.gz"),
                        "windows-1258", 0.04),
                // Central European (Polish, Czech, Slovak, Hungarian,
                // Croatian) — similarly under-represented.
                new ExtraPositiveSource(charsetTrain.resolve("windows-1250.bin.gz"),
                        "windows-1250", 0.04),
                // Baltic — modest boost for windows-1257 cohort coverage.
                new ExtraPositiveSource(charsetTrain.resolve("windows-1257.bin.gz"),
                        "windows-1257", 0.02)));
        EXTRA_POSITIVE_SOURCES = Collections.unmodifiableMap(m);
    }

    static final class ExtraPositiveSource {
        final Path file;
        final String charsetName;
        final double fraction;

        ExtraPositiveSource(Path file, String charsetName, double fraction) {
            this.file = file;
            this.charsetName = charsetName;
            this.fraction = fraction;
        }
    }

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
     * Same pairs as the LATIN→CJK block above, but isolated for the
     * sampling-boost in {@link #trainClassifierV7}.  When training the
     * LATIN classifier, half of the case-2 (byte-level-mojibake) picks
     * come from this subset rather than from the full pair list.
     * Without the boost, LATIN→CJK pairs are ~6/54 = 11% of case-2,
     * which translates to ~1.4% of all LATIN negatives — too rare to
     * lift w9 (script-alternation ratio) above the L2 floor.  Boosting
     * to 50% of case-2 = ~6% of all negatives gives the LR enough z9
     * signal to fit a meaningful weight.
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
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "data", "junk-augmented-symbolboost");
        // Output is written straight to the bundled resource (run from the repo
        // root).  Prior model versions live in git history; the model ships with
        // the code and is intentionally NOT backwards compatible.
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
        System.out.println("  --- v7 format constants (TrainJunkModel) ---");
        System.out.printf( "  backoff_alpha:      %.2f%n", V7_BACKOFF_ALPHA);
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
        // Phase 1 — per-script F1 tables (V7), F1 calibration, F2 block tables,
        //           F3 control-byte calibration
        // -----------------------------------------------------------------------
        TreeMap<String, V7Tables> f1TablesByScript = new TreeMap<>();
        // Shared COMMON pool: digits/punctuation/symbols/space runs from EVERY
        // corpus go into one COMMON table so charset-invariant content cancels
        // across candidate decodes instead of polluting a per-script table.
        HashMap<Long, long[]> commonPairs = new HashMap<>(1 << 16);
        HashMap<Integer, long[]> commonUnigrams = new HashMap<>(1 << 12);
        long[] commonTotals = new long[2];
        System.out.println("\n--- Phase 1: per-script F1 tables + calibrations ---");
        for (Path trainFile : trainFiles) {
            String filename = trainFile.getFileName().toString();
            String script = filename.substring(0, filename.length() - ".train.gz".length())
                    .toUpperCase();

            System.out.printf("%n  [%s]%n", script);
            allTrainFiles.add(trainFile);

            t0 = System.currentTimeMillis();
            System.out.print("    Training V7 F1 tables (cp index + OA)..");
            // Segment + route: this file's non-COMMON runs build its script
            // table; its COMMON runs accumulate into the shared COMMON pool.
            HashMap<Long, long[]> scriptPairs = new HashMap<>(1 << 14);
            HashMap<Integer, long[]> scriptUnigrams = new HashMap<>(1 << 12);
            long[] scriptTotals = new long[2];
            tallyFileRuns(trainFile, scriptPairs, scriptUnigrams, scriptTotals,
                    commonPairs, commonUnigrams, commonTotals);
            V7Tables v7 = buildV7TablesFromCounts(scriptPairs, scriptUnigrams,
                    scriptTotals[0], minBigramCount, loadFactor, keyIndexBits);
            System.out.printf(" done (%dms)%n", System.currentTimeMillis() - t0);
            System.out.println(v7.statsString());
            f1TablesByScript.put(script, v7);

            t0 = System.currentTimeMillis();
            System.out.print("    Training named-block table...       ");
            float[] blockTable = trainBlockTable(trainFile);
            // Round-trip through int8 quantization so the calibration sees
            // the same precision the inference path will see (Phase F:
            // eliminates train/infer drift on F2 dequantized lookups).
            blockTable = quantizeDequantizeRoundTrip(blockTable);
            System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

            t0 = System.currentTimeMillis();
            System.out.print("    Calibrating F1 (cp-hash) on train.. ");
            float[] f1Cal = calibrateF1PerScript(trainFile, v7);
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
        // Phase 1b — pooled COMMON table (digits/punctuation/symbols/space).
        // Built from COMMON runs accumulated across every corpus.  Registered
        // into f1Calibrations/classifierWeights only AFTER Phase 3 so no COMMON
        // classifier is trained — COMMON uses z1-passthrough weights (approach
        // (i)): its z1 cancels across candidate decodes and flags symbol salad.
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 1b: pooled COMMON table ---");
        t0 = System.currentTimeMillis();
        V7Tables commonV7 = buildV7TablesFromCounts(commonPairs, commonUnigrams,
                commonTotals[0], minBigramCount, loadFactor, keyIndexBits);
        f1TablesByScript.put(JunkDetector.COMMON_SCRIPT, commonV7);
        System.out.printf("    %s done (%dms)%n", commonV7.statsString(),
                System.currentTimeMillis() - t0);
        List<Double> commonScores = new ArrayList<>();
        for (Path f : trainFiles) {
            for (String window : sampleSubstrings(f, CALIB_SAMPLES, CALIB_LENGTHS, 77)) {
                double s = windowMeanRunF1(window, commonV7, true);
                if (!Double.isNaN(s)) {
                    commonScores.add(s);
                }
            }
        }
        float[] commonCal = muSigma(commonScores);
        System.out.printf("    COMMON F1 cal: mu=%.4f sigma=%.4f (%,d windows)%n",
                commonCal[0], commonCal[1], commonScores.size());

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
        // Round-trip through int8 quantization so calibration sees the
        // values inference will see (Phase F: F4 is also stored quantized).
        scriptTransTable = quantizeDequantizeRoundTrip(scriptTransTable);
        System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

        t0 = System.currentTimeMillis();
        System.out.print("  Calibrating script transitions...   ");
        float[] scriptTransCal = calibrateScriptTransitions(allTrainFiles, scriptTransTable,
                scriptBucketMap, numScriptBuckets);
        System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                scriptTransCal[0], scriptTransCal[1], System.currentTimeMillis() - t0);

        // -----------------------------------------------------------------------
        // Phase 3 — ONE global combiner, trained pointwise (clean>garbage, the
        // absolute junkness scale) + contrastive (correct-decode>wrong, incl. RTL
        // logical>reversed, the ranking task).  Replaces the per-script LRs and
        // their corruption recipes.  Features are extracted through a temp model
        // so they are exactly the inference features (no train/infer drift).
        // -----------------------------------------------------------------------
        System.out.println("\n--- Phase 3: global contrastive combiner ---");
        // COMMON (z1-passthrough) is registered before the temp save so feature
        // extraction routes COMMON runs to the COMMON table.
        f1Calibrations.put(JunkDetector.COMMON_SCRIPT, commonCal);
        classifierWeights.put(JunkDetector.COMMON_SCRIPT,
                new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f});

        Path tmpModel = Files.createTempFile("junkdetect-feat", ".bin");
        saveModel(f1TablesByScript, f1Calibrations, blockTables, blockCalibrations,
                controlCalibrations, classifierWeights, scriptBuckets,
                scriptTransTable, scriptTransCal, tmpModel);
        JunkDetector featExtractor = JunkDetector.loadFromPath(tmpModel);
        Files.deleteIfExists(tmpModel);

        t0 = System.currentTimeMillis();
        float[] global = trainGlobalCombiner(featExtractor, trainFilePaths);
        System.out.printf(
                "  global w=[%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f] bias=%.3f (%dms)%n",
                global[0], global[1], global[2], global[3], global[4], global[5],
                global[6], global[7], global[8], global[9],
                System.currentTimeMillis() - t0);
        // Apply the global combiner to every real script; COMMON keeps passthrough.
        for (String s : f1TablesByScript.keySet()) {
            if (!JunkDetector.COMMON_SCRIPT.equals(s)) {
                classifierWeights.put(s, global);
            }
        }

        System.out.printf("%nWriting model (%d scripts, blockN=%d, scriptBuckets=%d) → %s%n",
                f1Calibrations.size(), blockN, numScriptBuckets, output);
        saveModel(f1TablesByScript, f1Calibrations,
                  blockTables, blockCalibrations,
                  controlCalibrations, classifierWeights,
                  scriptBuckets, scriptTransTable, scriptTransCal,
                  output);
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

    /**
     * Read length-prefixed binary records ({@code [u16-BE length][bytes]})
     * from a gzipped file (the format used by {@code ~/data/charsets/train/})
     * and sample {@code nSamples} substrings of varying length, decoded
     * under {@code charset}.  Mirrors {@link #sampleSubstrings} but reads
     * a different file format so the trainer can pull Vietnamese / Polish /
     * Baltic positive samples from the charset-detection training corpus
     * (Phase C: {@link #EXTRA_POSITIVE_SOURCES}).
     */
    static List<String> sampleBinaryRecords(Path file, Charset charset,
                                            int nSamples, int[] lengths,
                                            long seed) throws IOException {
        List<byte[]> records = new ArrayList<>();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file.toFile());
             java.util.zip.GZIPInputStream gis = new java.util.zip.GZIPInputStream(fis);
             java.io.DataInputStream dis = new java.io.DataInputStream(gis)) {
            // Read up to 4000 records (plenty to sample from)
            int cap = Math.max(nSamples * 4, 4000);
            while (records.size() < cap) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (java.io.EOFException eof) {
                    break;
                }
                byte[] rec = new byte[len];
                dis.readFully(rec);
                if (rec.length >= 2) {
                    records.add(rec);
                }
            }
        }
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        Random rng = new Random(seed);
        List<String> result = new ArrayList<>(nSamples);
        for (int i = 0; i < nSamples; i++) {
            byte[] rec = records.get(rng.nextInt(records.size()));
            int targetLen = lengths[i % lengths.length];

            String text;
            if (rec.length <= targetLen) {
                text = new String(rec, charset);
            } else {
                int start = rng.nextInt(rec.length - targetLen);
                int end = Math.min(start + targetLen, rec.length);
                text = new String(rec, start, end - start, charset);
            }
            // NFC + skip if effectively empty after decoding
            text = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC);
            if (!text.isEmpty()) {
                result.add(text);
            }
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
     * Trains a per-script binary logistic regression classifier on
     * 8 features (z1-z8).
     *
     * <p>Clean examples: {@link #NUM_CLASSIFIER_SAMPLES} random dev windows
     * (seed 100).  Corrupted examples: same count, cycling through eight
     * realistic distortions (seed 102) covering charset mojibake (via full-
     * text byte-level codec confusion), PDF/OCR junk (PUA injection,
     * diacritic shedding, visual OCR substitutions, whitespace mangling,
     * repeat-byte storms), RTL direction flip, and general structural
     * corruption (control-byte injection, codepoint shuffle).
     *
     * @return float[9] = {w1, w2, w3, w4, w5, w6, w7, w8, bias}
     *         classifier weights; positive logit = clean.
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
     * <p>Trains v8's z5 (letter-adjacent-to-mark) feature: real
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
    // v7 Phase 1: per-script open-addressing F1 table training
    // -----------------------------------------------------------------------

    /**
     * Builds the {@link V7Tables} F1 carrier for one script's training data.
     *
     * <p>Two-pass:
     * <ol>
     *   <li><b>Pass 1.</b> Count every (cpA, cpB) pair occurrence and every
     *       cp unigram occurrence in the script's {@code *.train.gz} file.
     *       Pairs with count {@code < minBigramCount} are dropped at this
     *       step — they're typically OCR artifacts and proper-noun noise.</li>
     *   <li><b>Pass 2.</b> Collect every codepoint that appears in any
     *       kept pair (as either side), sort, assign each a dense small
     *       index.  Build a power-of-two open-addressing hash table sized
     *       for {@code keptPairs / loadFactor}; pack each retained
     *       {@code (idxA, idxB)} into a 32-bit key and insert via linear
     *       probing.  Quantize both bigram log-probs and unigram log-probs
     *       to 8-bit.</li>
     * </ol>
     *
     * <p>Returned {@link V7Tables} are ready to hand to
     * {@link #saveModel}.
     *
     * @param trainFile         the per-script {@code *.train.gz}
     * @param minBigramCount    drop pairs whose count is below this
     * @param loadFactor        target OA table load factor (e.g. 0.5)
     * @param keyIndexBits      bit-width per index in the packed key
     *                          (each side of the pair must fit)
     */
    public static V7Tables trainV7TablesForScript(Path trainFile,
                                                  int minBigramCount,
                                                  double loadFactor,
                                                  int keyIndexBits) throws IOException {
        // Single-file convenience: pools only this file's own (non-COMMON)
        // script runs.  The full pipeline routes COMMON across all corpora via
        // tallyFileRuns into a shared COMMON table (the throwaway maps here
        // discard this file's COMMON runs).
        HashMap<Long, long[]> pairCounts = new HashMap<>(1 << 14);
        HashMap<Integer, long[]> unigramCounts = new HashMap<>(1 << 12);
        long[] scriptTotals = new long[2];
        tallyFileRuns(trainFile, pairCounts, unigramCounts, scriptTotals,
                new HashMap<>(), new HashMap<>(), new long[2]);
        return buildV7TablesFromCounts(pairCounts, unigramCounts, scriptTotals[0],
                minBigramCount, loadFactor, keyIndexBits);
    }

    /**
     * Reads a {@code *.train.gz}, NFC-normalizes each line, segments it via
     * {@link JunkDetector#segmentRuns} and tallies sentinel-bounded bigrams:
     * non-COMMON runs into the per-script maps, COMMON runs into the shared
     * COMMON maps.  NFC + sentinels match inference exactly (no train/infer
     * drift).  {@code *Totals[0]}=unigram count, {@code *Totals[1]}=bigrams.
     */
    static void tallyFileRuns(Path trainFile,
                              HashMap<Long, long[]> scriptPairs,
                              HashMap<Integer, long[]> scriptUnigrams,
                              long[] scriptTotals,
                              HashMap<Long, long[]> commonPairs,
                              HashMap<Integer, long[]> commonUnigrams,
                              long[] commonTotals) throws IOException {
        try (BufferedReader r = openGzipped(trainFile)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                String norm = java.text.Normalizer.normalize(
                        line, java.text.Normalizer.Form.NFC);
                for (JunkDetector.Run run : JunkDetector.segmentRuns(norm)) {
                    int[] seq = run.withSentinels();
                    if (run.isCommon()) {
                        tallySeq(seq, commonPairs, commonUnigrams, commonTotals);
                    } else {
                        tallySeq(seq, scriptPairs, scriptUnigrams, scriptTotals);
                    }
                }
            }
        }
    }

    /**
     * Tallies unigram + adjacent-pair counts of a codepoint sequence into the
     * given maps ({@code totals[0]}+=unigrams, {@code totals[1]}+=bigrams).
     * Matches {@link JunkDetector#computeF1MeanLogP(int[], V7Tables)} exactly
     * (every adjacent pair, no skips) so trained tables fit scored sequences.
     */
    private static void tallySeq(int[] seq,
                                 HashMap<Long, long[]> pairs,
                                 HashMap<Integer, long[]> unigrams,
                                 long[] totals) {
        int prevCp = -1;
        for (int cp : seq) {
            long[] uc = unigrams.get(cp);
            if (uc == null) {
                unigrams.put(cp, new long[]{1L});
            } else {
                uc[0]++;
            }
            totals[0]++;
            if (prevCp >= 0) {
                long packed = ((long) prevCp << 32) | (cp & 0xFFFFFFFFL);
                long[] bc = pairs.get(packed);
                if (bc == null) {
                    pairs.put(packed, new long[]{1L});
                } else {
                    bc[0]++;
                }
                totals[1]++;
            }
            prevCp = cp;
        }
    }

    /**
     * Builds the {@link V7Tables} F1 carrier from pre-tallied pair/unigram
     * counts (see {@link #tallyFileRuns}).  Drops pairs below
     * {@code minBigramCount}, assigns dense codepoint indices, and packs an
     * open-addressing bigram table; unigram log-probs use {@code unigramTotal}
     * as the denominator.
     */
    public static V7Tables buildV7TablesFromCounts(
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
        double bigramDenom = keptBigramTotal + V7_ADD_ALPHA * keptPairs;
        // Unigram log-probs.  We keep one entry per indexed codepoint; the
        // denominator uses ALL unigram observations (kept pairs only would
        // bias the backoff toward common pairs).
        double unigramDenom = unigramTotal + V7_ADD_ALPHA * unigramCounts.size();
        float[] unigramLogP = new float[cpIndex.length];
        for (int i = 0; i < cpIndex.length; i++) {
            long[] uc = unigramCounts.get(cpIndex[i]);
            long count = uc != null ? uc[0] : 0L;
            double p = (count + V7_ADD_ALPHA) / unigramDenom;
            unigramLogP[i] = (float) Math.log(p);
        }
        // Per-script "absent codepoint" fallback: the lowest unigram log-prob
        // we'd assign to a codepoint observed exactly once.  A codepoint
        // *not* in our index has count 0, so:
        double fallbackP = V7_ADD_ALPHA / unigramDenom;
        float unigramFallbackLogP = (float) Math.log(fallbackP);

        // Quantize unigram log-probs.
        QuantizedFloats qUnigram = quantizeFloats(unigramLogP);

        // --- Build the open-addressing bigram table. ---
        int slots = nextPowerOfTwo((int) Math.max(2, Math.ceil(keptPairs / loadFactor)));
        int[] keys = new int[slots];
        java.util.Arrays.fill(keys, V7Tables.EMPTY_KEY);
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
            double p = (count + V7_ADD_ALPHA) / bigramDenom;
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

        return new V7Tables(cpIndex, keys, values, qUnigram.bytes,
                qBigram.min, qBigram.max,
                qUnigram.min, qUnigram.max,
                unigramFallbackLogP, V7_BACKOFF_ALPHA);
    }

    /**
     * Inserts a {@code (packedKey, value)} pair into the open-addressing
     * table.  The caller is responsible for sizing the table large enough
     * to avoid an infinite probe (any load &lt; 1.0 is safe).
     */
    private static void insertOA(int[] keys, byte[] values, int packedKey, byte value) {
        int mask = keys.length - 1;
        int h = JunkDetector.mixIndexKey(packedKey) & mask;
        while (keys[h] != V7Tables.EMPTY_KEY) {
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

    /**
     * Computes per-script F1 calibration ({mu, sigma}) by scoring each
     * window in the dev file against the trained per-script codepoint
     * tables.  Delegates to
     * {@link org.apache.tika.ml.junkdetect.JunkDetector#computeF1MeanLogP}
     * — the single authoritative F1 implementation shared between training
     * and inference.
     */
    public static float[] calibrateF1PerScript(Path devGz, V7Tables tables) throws IOException {
        List<String> windows = sampleSubstrings(devGz, CALIB_SAMPLES, CALIB_LENGTHS, 42);
        List<Double> scores = new ArrayList<>(windows.size());
        for (String window : windows) {
            double score = windowMeanRunF1(window, tables, false);
            if (!Double.isNaN(score)) {
                scores.add(score);
            }
        }
        System.out.printf("    %,d dev windows%n", scores.size());
        return muSigma(scores);
    }

    /**
     * Byte-weighted mean of sentinel-bounded per-run F1 over the runs of
     * {@code window} selected by {@code wantCommon} (COMMON runs if true, else
     * non-COMMON script runs), scored against {@code tables}.  This is the
     * windowed analog of inference's per-run z1 input (pre-calibration): it
     * segments and sentinel-bounds exactly as {@link JunkDetector#scoreText}
     * does, so calibration and classifier z1 cannot drift from inference.
     */
    static double windowMeanRunF1(String window, V7Tables tables, boolean wantCommon) {
        String norm = java.text.Normalizer.normalize(window, java.text.Normalizer.Form.NFC);
        double weighted = 0;
        long bytes = 0;
        for (JunkDetector.Run run : JunkDetector.segmentRuns(norm)) {
            if (run.isCommon() != wantCommon) {
                continue;
            }
            double f1 = JunkDetector.computeF1MeanLogP(run.withSentinels(), tables);
            if (Double.isNaN(f1)) {
                continue;
            }
            int n = run.text().getBytes(StandardCharsets.UTF_8).length;
            weighted += f1 * n;
            bytes += n;
        }
        return bytes == 0 ? Double.NaN : weighted / bytes;
    }

    // -----------------------------------------------------------------------
    // v7 Phase 3: classifier feature extractor + orchestrator
    // -----------------------------------------------------------------------

    /**
     * Extracts a 4-dim calibrated z-score vector for one training window
     * using the v7 per-script tables.  z2/z3/z4 delegate to the public
     * helpers on {@link JunkDetector} — same math used at inference, no
     * trainer/inference drift possible.
     *
     * @return float[4] = {z1_cpHash, z2_block, z3_control, z4_scriptTrans}
     */
    static float[] extractFeaturesV7(String window,
                                      V7Tables tables, float[] f1Cal,
                                      float[] blockTable, float[] blockCal,
                                      float[] controlCal,
                                      float[] scriptTransTable, float[] scriptTransCal,
                                      Map<String, Integer> scriptBucketMap,
                                      int numScriptBuckets) {
        // NFC-normalize defensively — corruption modes (utf8AsWindows1252-
        // Mojibake, etc.) produce text in whatever form the encoder yields.
        // Matches JunkDetector.scoreText / scoreWithFeatureComponents.
        window = java.text.Normalizer.normalize(window, java.text.Normalizer.Form.NFC);
        byte[] utf8 = window.getBytes(StandardCharsets.UTF_8);

        // z1: per-script codepoint-bigram mean log-prob over the window's
        // non-COMMON runs, sentinel-bounded — mirrors inference's per-run z1.
        float z1 = 0f;
        double rawF1 = windowMeanRunF1(window, tables, false);
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

        // z5: letter-adjacent-to-mark ratio.  Raw [0,1] — high for
        // mark-using scripts in correct decode, ~0 for mojibake.  LR
        // weight absorbs scale; non-negativity → positive contribution.
        double rawZ5 = org.apache.tika.ml.junkdetect.TextQualityFeatures
                .letterAdjacentToMarkRatio(window);
        float z5 = Double.isNaN(rawZ5) ? 0f : (float) rawZ5;

        // z6: 1 - replacement-character ratio (high for clean text, low
        // when the decode produced U+FFFD).  Flipped so the LR's
        // non-negativity puts positive weight on it.
        double rawZ6 = org.apache.tika.ml.junkdetect.TextQualityFeatures
                .replacementRatio(window);
        float z6 = Double.isNaN(rawZ6) ? 1f : 1f - (float) rawZ6;

        // z7: script density — fraction of codepoints in any script
        // (non-COMMON/INHERITED/UNKNOWN).  Pure-whitespace / pure-digit
        // text scores 0.  High = script-bearing content (positive signal).
        double rawZ7 = org.apache.tika.ml.junkdetect.TextQualityFeatures
                .scriptDensity(window);
        float z7 = Double.isNaN(rawZ7) ? 0f : (float) rawZ7;

        // z8: script coherence = 1 - fragmentation.  High = one coherent
        // script run; low = script-salad mojibake.  Flipped so positive
        // weight in LR means "more coherent → cleaner."
        double rawZ8 = org.apache.tika.ml.junkdetect.TextQualityFeatures
                .scriptFragmentation(window);
        float z8 = Double.isNaN(rawZ8) ? 1f : 1f - (float) rawZ8;

        // z9: scriptAlternationRatio = transitions / (2 * min(N_dom, N_foreign)).
        // Length- and proportion-invariant.  Catches LATIN→CJK mojibake
        // (every accent becomes a singleton Han → maximally alternating).
        // Clean text and clumped real mixed-script both score near 0.
        // Sign flipped so high alternation = junky = negative z9; LR fits
        // positive weight where "low alternation → cleaner."
        double rawZ9 = org.apache.tika.ml.junkdetect.TextQualityFeatures
                .scriptAlternationRatio(window);
        float z9 = Double.isNaN(rawZ9) ? 0f : -(float) rawZ9;

        return new float[]{z1, z2, z3, z4, z5, z6, z7, z8, z9};
    }

    /**
     * Trains a per-script binary logistic regression classifier on
     * (z1_cpHash, z2, z3, z4, z5, z6).  Same scaffolding as the v6/v7
     * trainer
     * (sample windows, corrupt half, fit LR, bias-calibrate on short
     * windows) but uses v7 per-script F1 tables.
     */
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
     * wrong) + L2.  Feature weights are projected non-negative (same orientation
     * convention as {@link #fitLogisticRegression}); the bias is unconstrained.
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

    static float[] trainClassifierV7(String script,
                                      Path devGz,
                                      V7Tables tables, float[] f1Cal,
                                      float[] blockTable, float[] blockCal,
                                      float[] controlCal,
                                      float[] scriptTransTable, float[] scriptTransCal,
                                      Map<String, Integer> scriptBucketMap, int numScriptBuckets)
            throws IOException {
        int nEach = NUM_CLASSIFIER_SAMPLES;

        List<String> cleanWindows = new ArrayList<>(
                sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 100));

        // Phase C: augment per-script positive samples from EXTRA_POSITIVE_SOURCES.
        // For LATIN this pulls in Vietnamese / Polish / Baltic content
        // sourced from the charset-detection training corpus, fixing the
        // under-representation that caused Vietnamese and Polish-¶
        // cohort regressions in the cc-html-29k eval.
        List<ExtraPositiveSource> extras = EXTRA_POSITIVE_SOURCES.get(script);
        if (extras != null) {
            int seed = 110;
            for (ExtraPositiveSource src : extras) {
                if (!Files.isReadable(src.file)) {
                    System.out.printf("%n    EXTRA %s: file unreadable, skipping%n", src.file);
                    continue;
                }
                int nExtra = (int) Math.round(nEach * src.fraction);
                List<String> extraWindows = sampleBinaryRecords(
                        src.file, Charset.forName(src.charsetName),
                        nExtra, CALIB_LENGTHS, seed++);
                cleanWindows.addAll(extraWindows);
                System.out.printf("%n    EXTRA %s (%s, fraction=%.2f): +%d positive samples",
                        src.file.getFileName(), src.charsetName, src.fraction,
                        extraWindows.size());
            }
        }

        List<String> baseWindows = sampleSubstrings(devGz, nEach, CALIB_LENGTHS, 101);
        Random rng = new Random(102);
        List<String> corruptedWindows = new ArrayList<>(nEach);
        // Corruption mix — 9-way rotation, all realistic real-world failures:
        //  0: random control bytes (universal binary-garbage signal)
        //  1: codepoint shuffle (general structural corruption)
        //  2: full-text byte-level mojibake (random pair — primary mode)
        //  3: reverse RTL text (PDF/OCR BiDi-flip on Arabic/Hebrew/etc.)
        //  4: PUA injection (PDF cmap garbage)
        //  5: diacritic shedding (OCR/PDF mark-loss)
        //  6: visual OCR substitutions (O↔0, l↔1, etc.)
        //  7: whitespace mangle (PDF columnar/kerning) + repeat-byte storm
        //     alternated per-window
        //  8: LATIN→CJK byte-level mojibake (for LATIN script only — drives
        //     z9 / z4 / z8 signal for the 66-file wrong-CJK over-adoption
        //     failure mode; for other scripts, falls back to random
        //     byte-level mojibake to avoid crowding any single mode).
        for (int i = 0; i < baseWindows.size(); i++) {
            String w = baseWindows.get(i);
            switch (i % 9) {
                case 0:
                    corruptedWindows.add(injectControlChars(w, CLASSIFIER_INJECT_RATE, rng));
                    break;
                case 1:
                    corruptedWindows.add(shuffleChars(w, rng));
                    break;
                case 2: {
                    String[] pair = BYTE_LEVEL_MOJIBAKE_PAIRS[
                            rng.nextInt(BYTE_LEVEL_MOJIBAKE_PAIRS.length)];
                    corruptedWindows.add(byteLevelMojibake(w, pair[0], pair[1]));
                    break;
                }
                case 3:
                    corruptedWindows.add(reverseRtlText(w));
                    break;
                case 4:
                    corruptedWindows.add(injectPrivateUseAreaChars(w, 0.10, rng));
                    break;
                case 5:
                    corruptedWindows.add(shedDiacritics(w));
                    break;
                case 6:
                    corruptedWindows.add(visualOcrSubstitutions(w, 0.05, rng));
                    break;
                case 7:
                    if (rng.nextBoolean()) {
                        if (rng.nextBoolean()) {
                            corruptedWindows.add(collapseWhitespace(w));
                        } else {
                            corruptedWindows.add(inflateWhitespace(w, 0.10, rng));
                        }
                    } else {
                        corruptedWindows.add(repeatByteStorm(w, rng));
                    }
                    break;
                default: {
                    // Case 8: dedicated LATIN→CJK slot.  For LATIN script,
                    // produces the long-Latin-with-singleton-HAN pattern
                    // that z9 measures.  For other scripts, fall back to
                    // a random byte-level mojibake pair so we don't waste
                    // the slot.
                    String[] pair = "LATIN".equals(script)
                            ? LATIN_TO_CJK_PAIRS[rng.nextInt(LATIN_TO_CJK_PAIRS.length)]
                            : BYTE_LEVEL_MOJIBAKE_PAIRS[
                                    rng.nextInt(BYTE_LEVEL_MOJIBAKE_PAIRS.length)];
                    corruptedWindows.add(byteLevelMojibake(w, pair[0], pair[1]));
                    break;
                }
            }
        }

        List<float[]> features = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());
        List<Integer> labels   = new ArrayList<>(cleanWindows.size() + corruptedWindows.size());

        for (String w : cleanWindows) {
            features.add(extractFeaturesV7(w, tables, f1Cal,
                    blockTable, blockCal, controlCal,
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets));
            labels.add(1);
        }
        for (String w : corruptedWindows) {
            features.add(extractFeaturesV7(w, tables, f1Cal,
                    blockTable, blockCal, controlCal,
                    scriptTransTable, scriptTransCal, scriptBucketMap, numScriptBuckets));
            labels.add(0);
        }

        float[] weights = fitLogisticRegression(features, labels, 9);

        // Bias calibration on short windows so FPR ≤ 2.5% at worst-case length.
        List<String> shortWindows = sampleSubstrings(devGz, nEach, new int[]{15}, 200);
        List<Float> shortLogits = new ArrayList<>(shortWindows.size());
        int nFeat = weights.length - 1;
        for (String w : shortWindows) {
            float[] x = extractFeaturesV7(w, tables, f1Cal,
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
     * Writes a model file in the current binary format.  Layout: gzip
     * envelope around {@code JUNKDET1} magic + {@link JunkDetector#VERSION} byte +
     * global script-transition section + z5/z6 calibrations + per-script
     * sections (F1 tables, F2 block transitions, F3 control calibration,
     * 7-element LR weight vector = 6 weights + bias).  See
     * {@link JunkDetector#load} for the load-side spec.
     */
    public static void saveModel(TreeMap<String, V7Tables> f1Tables,
                                 TreeMap<String, float[]> f1Calibrations,
                                 TreeMap<String, float[]> blockTables,
                                 TreeMap<String, float[]> blockCalibrations,
                                 TreeMap<String, float[]> controlCalibrations,
                                 TreeMap<String, float[]> classifierWeights,
                                 List<String> scriptBuckets,
                                 float[] scriptTransTable,
                                 float[] scriptTransCal,
                                 Path output) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output)))) {

            dos.write(MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(JunkDetector.VERSION); // single source of truth
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
            // F4 script-transition table — Phase F int16 quantized.
            // Layout: [float min][float max][numBuckets² × 2 bytes BE].
            QuantizedShorts qScriptTrans = quantizeToShorts(scriptTransTable);
            dos.writeFloat(qScriptTrans.min);
            dos.writeFloat(qScriptTrans.max);
            for (short s : qScriptTrans.shorts) {
                dos.writeShort(s);
            }
            dos.writeFloat(scriptTransCal[0]);
            dos.writeFloat(scriptTransCal[1]);

            // Three document-level calibrations:
            //   z5 (letter-adjacent-to-mark): pass-through (mu=0, sigma=1)
            //   z6 (replacement-ratio): mu=1, sigma=1 so inference returns 1-raw
            //   z9 (scriptRunDensity): pass-through with flip — mu=0, sigma=1
            //       so inference returns -raw (high density = junky = negative).
            //   Training extractor mirrors each flip.  LR weight absorbs scale.
            dos.writeFloat(0f); // z5 mu
            dos.writeFloat(1f); // z5 sigma
            dos.writeFloat(1f); // z6 mu  (so inference returns 1 - raw)
            dos.writeFloat(1f); // z6 sigma
            dos.writeFloat(0f); // z9 mu  (so inference returns -raw)
            dos.writeFloat(1f); // z9 sigma

            // Per-script sections.  V7 embeds the F1 tables inline.
            int blockN = org.apache.tika.ml.junkdetect.UnicodeBlockRanges.bucketCount();
            for (var entry : f1Calibrations.entrySet()) {
                String script = entry.getKey();
                float[] f1Cal      = entry.getValue();
                V7Tables tables    = f1Tables.get(script);
                if (tables == null) {
                    throw new IllegalStateException("No V7Tables for script " + script);
                }
                float[] blockTable = blockTables.getOrDefault(script, new float[blockN * blockN]);
                float[] blockCal   = blockCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] controlCal = controlCalibrations.getOrDefault(script, new float[]{0f, 1f});
                float[] weights    = classifierWeights.getOrDefault(script,
                        new float[]{1f / 9, 1f / 9, 1f / 9, 1f / 9, 1f / 9,
                                    1f / 9, 1f / 9, 1f / 9, 1f / 9, 0f});

                byte[] nameBytes = script.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);

                // F1 calibration
                dos.writeFloat(f1Cal[0]);
                dos.writeFloat(f1Cal[1]);

                // F1 per-script tables
                tables.writeTo(dos);

                // F2 — block transitions (Phase F int16 quantized).
                // Layout: [calMu][calSigma][float min][float max][blockN² × 2 bytes BE].
                dos.writeFloat(blockCal[0]);
                dos.writeFloat(blockCal[1]);
                QuantizedShorts qBlock = quantizeToShorts(blockTable);
                dos.writeFloat(qBlock.min);
                dos.writeFloat(qBlock.max);
                for (short s : qBlock.shorts) {
                    dos.writeShort(s);
                }

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
    /**
     * Quantize a float[] to int16 and dequantize back, returning a new
     * float[] with the int16-precision values.  Used at training time
     * so downstream calibration (mu, sigma) is computed on values the
     * inference path will actually see.  Eliminates the train/infer
     * drift that v13's first attempt at Phase F exhibited.  65536
     * levels keep ~0.0002 nats/level resolution, essentially lossless
     * for our [-15, -1] log-prob range.
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


    // -----------------------------------------------------------------------
    // Eval-tooling helpers — used by {@link EvalJunkDetector} for the
    // synthetic-corruption eval matrix.  No longer used by classifier
    // training (Phase E replaced wrongCodecRemap with full-text
    // byteLevelMojibake from BYTE_LEVEL_MOJIBAKE_PAIRS).
    // -----------------------------------------------------------------------

    /**
     * Legacy codec pairs for the synthetic char-level remap eval mode.
     * Production training uses {@link #BYTE_LEVEL_MOJIBAKE_PAIRS} instead.
     */
    static final String[][] WRONG_CODEC_PAIRS = {
        {"windows-1252", "windows-1250"},
        {"windows-1250", "windows-1252"},
        {"windows-1252", "windows-1257"},
        {"windows-1257", "windows-1252"},
        {"windows-1251", "windows-1252"},
        {"windows-1252", "windows-1251"},
        {"windows-1253", "windows-1252"},
        {"windows-1255", "windows-1252"},
        {"ISO-8859-2", "windows-1250"},
        {"ISO-8859-1", "windows-1252"},
        {"windows-1258", "windows-1252"},
    };

    /**
     * Build a char→char remap table for a single-byte (sourceCodec,
     * wrongCodec) pair.  Used by {@link EvalJunkDetector}'s synthetic
     * eval; not used by training (full-text {@link #byteLevelMojibake}
     * is more realistic).
     */
    static Map<Character, Character> buildRemapTable(String sourceCodec, String wrongCodec) {
        Charset src, wrong;
        try {
            src = Charset.forName(sourceCodec);
            wrong = Charset.forName(wrongCodec);
        } catch (IllegalArgumentException e) {
            return Collections.emptyMap();
        }
        Map<Character, Character> table = new HashMap<>();
        byte[] singleByte = new byte[1];
        for (int b = 0x80; b <= 0xFF; b++) {
            singleByte[0] = (byte) b;
            String fromSrc = new String(singleByte, src);
            String fromWrong = new String(singleByte, wrong);
            if (fromSrc.length() == 1 && fromWrong.length() == 1
                    && fromSrc.charAt(0) != '�' && fromWrong.charAt(0) != '�'
                    && fromSrc.charAt(0) != fromWrong.charAt(0)) {
                table.put(fromSrc.charAt(0), fromWrong.charAt(0));
            }
        }
        return table;
    }

    /**
     * Stochastic char-level codec remap.  See {@link #buildRemapTable}.
     * Eval-only.
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
