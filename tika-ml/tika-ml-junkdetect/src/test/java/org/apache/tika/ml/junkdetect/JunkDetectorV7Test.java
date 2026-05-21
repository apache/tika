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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.ml.junkdetect.tools.JunkDetectorTrainingConfig;
import org.apache.tika.ml.junkdetect.tools.TrainJunkModel;
import org.apache.tika.quality.TextQualityScore;

/**
 * Validates the model file format end-to-end: a synthetic small model is
 * constructed in-memory with known table values, saved via
 * {@link TrainJunkModel#saveModel}, loaded via {@link JunkDetector#load},
 * scored against known input, and the output verified against hand-computed
 * expected values.
 *
 * <p>Confirms that the file format spec, the trainer's save path, the
 * loader, and the scoring path (per-script open-addressing codepoint-bigram
 * + unigram backoff) all agree on the semantics.  Does not require the
 * production training corpus.
 */
public class JunkDetectorV7Test {

    @Test
    void v7RoundTripSeenPairAndUnigramBackoff(@TempDir Path tmp) throws IOException {
        // -----------------------------------------------------------------
        // Build a tiny synthetic v7 model for LATIN.
        //
        // codepointIndex = ['A', 'B']  (indices 0, 1)
        // Pair (A, B) stored with log-prob -1.0
        // (B, A) is *not* in the bigram table — falls back to unigram.
        // Unigram log-prob = -2.0 for both 'A' and 'B'.
        // backoffAlpha = 1.0  →  backoff sum = -4.0
        //
        // Expected mean log-prob over "ABAB":
        //   (A,B) seen:    -1.0
        //   (B,A) backoff: 1.0 * (-2 + -2) = -4.0
        //   (A,B) seen:    -1.0
        //   mean = -2.0
        // f1Cal mu=-5, sigma=1  →  z1 = (-2 - -5) / 1 = +3.0
        // Classifier w1=1, rest 0, bias=0  →  logit = +3.0
        // -----------------------------------------------------------------
        V7Tables tables = buildLatinTablesAB();

        Path modelFile = tmp.resolve("v7-test.bin");
        saveMinimalV7Model(tables, modelFile);

        // Verify the file roundtrips through the loader.
        JunkDetector detector = JunkDetector.loadFromPath(modelFile);
        assertEquals(JunkDetector.VERSION, detector.getModelVersion(), "Loaded model should match current VERSION");

        TextQualityScore score = detector.score("ABAB");
        assertEquals("LATIN", score.getDominantScript(), "Dominant script should be LATIN");
        // Quantization of [-4, -1] to 8 bits introduces ~0.012 nat / level.
        // Net z-error over 3 pairs bounded ~0.05; allow 0.3 to be safe.
        // Sentinel-bounded scoring: expected = calibrated F1 over ^_doc A B A B $_doc.
        assertEquals(expectedRunZ(tables, "ABAB", -5.0f, 1.0f), score.getZScore(), 0.05f,
                "Inference z must match the authoritative F1 on the sentinel-bounded run");
    }

    @Test
    void v7RoundTripAllSeenPairsScoreHigher(@TempDir Path tmp) throws IOException {
        // Same shape as the first test but with BOTH (A,B) and (B,A) in the
        // bigram table.  mean log-prob = -1.0, z1 = +4.0, logit = +4.0.
        int[] cpIndex = new int[]{'A', 'B'};
        int[] keys = new int[4];
        Arrays.fill(keys, V7Tables.EMPTY_KEY);
        byte[] values = new byte[4];
        float bMin = -10.0f;
        float bMax = -1.0f;
        byte b = quantizeOne(-1.0f, bMin, bMax);
        insertOA(keys, values, JunkDetector.packBigramKey(0, 1), b);
        insertOA(keys, values, JunkDetector.packBigramKey(1, 0), b);

        float uMin = -5.0f;
        float uMax = -2.0f;
        byte[] unigramBytes = new byte[]{
                quantizeOne(-2.0f, uMin, uMax),
                quantizeOne(-2.0f, uMin, uMax),
        };

        V7Tables tables = new V7Tables(cpIndex, keys, values, unigramBytes,
                bMin, bMax, uMin, uMax,
                -10.0f, 1.0f);

        Path modelFile = tmp.resolve("v7-test-allseen.bin");
        saveMinimalV7Model(tables, modelFile);
        JunkDetector detector = JunkDetector.loadFromPath(modelFile);

        TextQualityScore score = detector.score("ABAB");
        assertEquals(expectedRunZ(tables, "ABAB", -5.0f, 1.0f), score.getZScore(), 0.05f,
                "All-seen 'ABAB': inference z must match authoritative sentinel-bounded F1");
    }

    /**
     * End-to-end trainer integration: drives {@link
     * TrainJunkModel#trainV7TablesForScript} on a tiny synthetic corpus,
     * calibrates F1, saves a model, loads it, and scores text.  Catches
     * drift between trainer F1 math and inference F1 math — the FNV
     * mix-hash, packed-key layout, and codepoint-pair iteration order all
     * have to agree exactly, or scoring produces nonsense.
     *
     * <p>F2/F3/F4 are zeroed out (placeholder data) — the test isolates
     * F1's trainer↔inference round-trip.
     */
    @Test
    void trainerRoundTripIntegration(@TempDir Path tmp) throws IOException {
        // --- 1. Build a tiny LATIN corpus on disk ---
        Path trainFile = tmp.resolve("LATIN.train.gz");
        writeGzippedLines(trainFile,
                "the quick brown fox jumps over the lazy dog",
                "pack my box with five dozen liquor jugs",
                "how vexingly quick daft zebras jump",
                "the five boxing wizards jump quickly",
                "sphinx of black quartz judge my vow");
        Path devFile = tmp.resolve("LATIN.dev.gz");
        writeGzippedLines(devFile,
                "the rain in spain falls mainly on the plain",
                "a stitch in time saves nine",
                "all that glitters is not gold");

        // --- 2. Phase 1: train V7 F1 tables for this script ---
        // Tiny corpus → min_count=1 so all pairs survive.
        V7Tables tables = TrainJunkModel.trainV7TablesForScript(trainFile,
                1, JunkDetectorTrainingConfig.OA_LOAD_FACTOR,
                JunkDetectorTrainingConfig.KEY_INDEX_BITS);

        // Sanity: 'h' should be in the codepoint index (appears in "the").
        assertTrue(Arrays.binarySearch(tables.codepointIndex, (int) 'h') >= 0,
                "'h' should be in codepoint index — it appears in training");
        assertTrue(Arrays.binarySearch(tables.codepointIndex, (int) 'x') >= 0,
                "'x' should be in codepoint index — appears in 'box', 'fox'");

        // The pair (t, h) is in training; the OA lookup should find it.
        int idxT = Arrays.binarySearch(tables.codepointIndex, (int) 't');
        int idxH = Arrays.binarySearch(tables.codepointIndex, (int) 'h');
        assertTrue(idxT >= 0 && idxH >= 0);
        int slot = JunkDetector.lookupBigramSlot(tables, idxT, idxH);
        assertTrue(slot >= 0, "OA lookup should find seen pair (t, h)");

        // --- 3. F1 raw scoring sanity ---
        double meanLogP = JunkDetector.computeF1MeanLogP("the quick brown fox", tables);
        assertTrue(Double.isFinite(meanLogP),
                "Mean log-prob on training text should be finite, got " + meanLogP);
        assertTrue(meanLogP > -15 && meanLogP < 0,
                "Score on training text should be sensible, got " + meanLogP);

        // --- 4. Phase 1.5: F1 calibration on dev ---
        float[] f1CalLatin = TrainJunkModel.calibrateF1PerScript(devFile, tables);
        assertTrue(Float.isFinite(f1CalLatin[0]), "mu1 should be finite");
        assertTrue(Float.isFinite(f1CalLatin[1]) && f1CalLatin[1] > 0,
                "sigma1 should be positive finite");

        // --- 5. Assemble + save a minimal v7 model ---
        int blockN = UnicodeBlockRanges.bucketCount();
        TreeMap<String, V7Tables> f1Tables = new TreeMap<>();
        f1Tables.put("LATIN", tables);
        TreeMap<String, float[]> blockTables = new TreeMap<>();
        blockTables.put("LATIN", new float[blockN * blockN]);
        TreeMap<String, float[]> blockCal = new TreeMap<>();
        blockCal.put("LATIN", new float[]{0f, 1f});
        TreeMap<String, float[]> controlCal = new TreeMap<>();
        controlCal.put("LATIN", new float[]{0f, 1f});
        TreeMap<String, float[]> f1CalMap = new TreeMap<>();
        f1CalMap.put("LATIN", f1CalLatin);
        TreeMap<String, float[]> classifierWeights = new TreeMap<>();
        // 6 feature weights + bias.  Only z1 is non-zero here.
        // 8 feature weights + bias.  Only z1 is non-zero here.
        classifierWeights.put("LATIN", new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f});

        List<String> scriptBuckets = List.of("LATIN", "OTHER");
        float[] scriptTransTable = new float[scriptBuckets.size() * scriptBuckets.size()];
        float[] scriptTransCal = new float[]{0f, 1f};

        Path modelPath = tmp.resolve("junkdetect.bin");
        TrainJunkModel.saveModel(
                f1Tables, f1CalMap, blockTables, blockCal, controlCal,
                classifierWeights, scriptBuckets, scriptTransTable,
                scriptTransCal, modelPath);

        // --- 6. Load via JunkDetector and score ---
        JunkDetector detector = JunkDetector.loadFromPath(modelPath);
        assertEquals(JunkDetector.VERSION, detector.getModelVersion(),
                "Loaded model should match current VERSION");
        assertTrue(detector.knownScripts().contains("LATIN"),
                "Loaded model should know LATIN");

        TextQualityScore score = detector.score("the quick brown fox jumps");
        assertEquals("LATIN", score.getDominantScript());
        assertTrue(Float.isFinite(score.getZScore()),
                "Score on in-distribution text should be finite, got " + score);

        // --- 7. Train/infer consistency check ---
        // The inference path should compute the same raw F1 score as
        // JunkDetector.computeF1MeanLogP on the same text — if these
        // two ever disagree, the model's calibration is silently wrong.
        String probe = "pack my box with five dozen liquor jugs";
        float expectedZ1 = expectedRunZ(tables, probe, f1CalLatin[0], f1CalLatin[1]);
        TextQualityScore probeScore = detector.score(probe);
        // logit = w1*z1 (rest 0); inference aggregates the same per-run
        // sentinel-bounded F1 the helper computes — must agree (no drift).
        assertEquals(expectedZ1, probeScore.getZScore(), 0.02f,
                "Inference z1 must match the per-run sentinel-bounded F1 "
                + "(train/infer F1 math drift)");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a V7Tables with codepoint index ['A', 'B'], where (A,B) has a
     * stored log-prob of -1.0 but (B,A) is absent (forces unigram backoff).
     * Unigram log-prob = -2.0 for both A and B.
     *
     * <p>Bigram quant range is set explicitly to {@code [-10, -1]} so that
     * the single stored value at -1.0 maps to byte 255 (avoids the
     * degenerate {@code min == max} branch in
     * {@link TrainJunkModel#quantizeFloats}).  Same idea for the unigram
     * range {@code [-5, -2]} so the (-2.0, -2.0) values map to byte 255.
     */
    private static V7Tables buildLatinTablesAB() {
        int[] cpIndex = new int[]{'A', 'B'};

        // 4 slots ≈ 25% load for 1 pair.  Open-addressing with linear probe.
        int[] keys = new int[4];
        Arrays.fill(keys, V7Tables.EMPTY_KEY);
        byte[] values = new byte[4];

        // Manual quantization with a chosen range so we don't hit the
        // degenerate single-element case.  range=[-10, -1] → -1.0 → byte 255.
        float bMin = -10.0f;
        float bMax = -1.0f;
        byte b = quantizeOne(-1.0f, bMin, bMax);
        insertOA(keys, values, JunkDetector.packBigramKey(0, 1), b);

        float uMin = -5.0f;
        float uMax = -2.0f;
        byte[] unigramBytes = new byte[]{
                quantizeOne(-2.0f, uMin, uMax),
                quantizeOne(-2.0f, uMin, uMax),
        };

        return new V7Tables(cpIndex, keys, values, unigramBytes,
                bMin, bMax,
                uMin, uMax,
                -10.0f, 1.0f);
    }

    /**
     * Mirrors inference's z1: byte-weighted mean over non-COMMON runs of the
     * sentinel-bounded F1, then calibrated.  COMMON runs are skipped (these
     * minimal models have no COMMON table, so inference skips them too).
     */
    private static float expectedRunZ(V7Tables tables, String text, float mu, float sigma) {
        double weighted = 0;
        long bytes = 0;
        for (JunkDetector.Run r : JunkDetector.segmentRuns(text)) {
            if (r.isCommon()) {
                continue;
            }
            double f1 = JunkDetector.computeF1MeanLogP(r.withSentinels(), tables);
            if (Double.isNaN(f1)) {
                continue;
            }
            int n = r.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            weighted += f1 * n;
            bytes += n;
        }
        return (float) ((weighted / bytes - mu) / sigma);
    }

    /** Quantize a single float to 8-bit unsigned using the explicit range. */
    private static byte quantizeOne(float v, float min, float max) {
        float range = max - min;
        int q = Math.round(((v - min) / range) * 255.0f);
        if (q < 0) q = 0;
        else if (q > 255) q = 255;
        return (byte) q;
    }

    /**
     * Replica of {@code TrainJunkModel.insertOA} (package-private) for the
     * test's hand-constructed tables.  Uses the same mix-hash as the
     * production code path.
     */
    private static void insertOA(int[] keys, byte[] values, int packedKey, byte value) {
        int mask = keys.length - 1;
        int h = JunkDetector.mixIndexKey(packedKey) & mask;
        while (keys[h] != V7Tables.EMPTY_KEY) {
            if (keys[h] == packedKey) {
                values[h] = value;
                return;
            }
            h = (h + 1) & mask;
        }
        keys[h] = packedKey;
        values[h] = value;
    }

    /**
     * Saves a minimal v7 model containing only LATIN, with F2/F3/F4 zeroed
     * out and pure-F1 classifier weights (w1=1, rest 0, bias 0).  Scoring
     * a window thus reduces to z1 directly.  F1 calibration: mu=-5, sigma=1.
     */
    private static void saveMinimalV7Model(V7Tables tables, Path modelFile) throws IOException {
        TreeMap<String, V7Tables> f1Tables = new TreeMap<>();
        f1Tables.put("LATIN", tables);

        TreeMap<String, float[]> f1Cal = new TreeMap<>();
        f1Cal.put("LATIN", new float[]{-5.0f, 1.0f});

        int blockN = UnicodeBlockRanges.bucketCount();

        TreeMap<String, float[]> blockTables = new TreeMap<>();
        blockTables.put("LATIN", new float[blockN * blockN]);
        TreeMap<String, float[]> blockCal = new TreeMap<>();
        blockCal.put("LATIN", new float[]{0f, 1f});

        TreeMap<String, float[]> controlCal = new TreeMap<>();
        controlCal.put("LATIN", new float[]{0f, 1f});

        List<String> scriptBuckets = List.of("LATIN", "OTHER");
        float[] scriptTransTable = new float[scriptBuckets.size() * scriptBuckets.size()];
        float[] scriptTransCal = new float[]{0f, 1f};

        TreeMap<String, float[]> classifierWeights = new TreeMap<>();
        // Current model format: 7 feature weights + bias.  Only z1 is
        // non-zero in this minimal fixture; z2-z7 contribute 0 to the logit.
        // 8 feature weights + bias (z1-z8 + bias).
        classifierWeights.put("LATIN", new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f});

        TrainJunkModel.saveModel(
                f1Tables, f1Cal, blockTables, blockCal, controlCal,
                classifierWeights, scriptBuckets, scriptTransTable,
                scriptTransCal, modelFile);
    }

    private static void writeGzippedLines(Path path, String... lines) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(path)),
                StandardCharsets.UTF_8))) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
    }
}
