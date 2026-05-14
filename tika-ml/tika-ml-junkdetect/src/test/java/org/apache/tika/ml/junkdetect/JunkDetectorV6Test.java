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

import org.apache.tika.ml.junkdetect.tools.TrainJunkModel;
import org.apache.tika.quality.TextQualityScore;

/**
 * Validates the v6 model file format end-to-end: a synthetic small model is
 * constructed in-memory with known hash-table values, saved via
 * {@link TrainJunkModel#saveModelV6}, loaded via {@link JunkDetector#load},
 * scored against known input, and the output verified against hand-computed
 * expected values.
 *
 * <p>This is the architectural-decision validation: it confirms that the v6
 * file format spec, the trainer's save path, the loader, and the scoring
 * path (hashed codepoint-bigram + Bloom + unigram backoff) all agree on the
 * semantics.  Does not require the production training corpus.
 */
public class JunkDetectorV6Test {

    @Test
    void v6RoundTripSeenPairAndUnigramBackoff(@TempDir Path tmp) throws IOException {
        final int seed = TrainJunkModel.V6_FNV_SEED;

        // -----------------------------------------------------------------
        // Build a tiny synthetic v6 model.
        //
        // Bigram table: floor at -10.0 nat, bucket for (A,B) at -1.0 nat.
        // Unigram table: floor at -5.0 nat, buckets for A and B at -2.0 nat.
        // Bloom: contains only (A,B).  (B,A) takes the unigram-backoff path.
        // -----------------------------------------------------------------

        int bigramBuckets = 4096;
        float[] bigramLog = new float[bigramBuckets];
        Arrays.fill(bigramLog, -10.0f);
        int bucketAB = (int) (TrainJunkModel.fnv1aBigramV6('A', 'B', seed)
                & (bigramBuckets - 1));
        bigramLog[bucketAB] = -1.0f;
        TrainJunkModel.QuantizedFloats qBigram = TrainJunkModel.quantizeFloats(bigramLog);

        int unigramBuckets = 8192;
        float[] unigramLog = new float[unigramBuckets];
        Arrays.fill(unigramLog, -5.0f);
        int bucketA = (int) (TrainJunkModel.fnv1aUnigramV6('A', seed)
                & (unigramBuckets - 1));
        int bucketB = (int) (TrainJunkModel.fnv1aUnigramV6('B', seed)
                & (unigramBuckets - 1));
        unigramLog[bucketA] = -2.0f;
        unigramLog[bucketB] = -2.0f;
        TrainJunkModel.QuantizedFloats qUnigram = TrainJunkModel.quantizeFloats(unigramLog);

        int bloomBits = 1024;
        int bloomK = 3;
        long[] bloom = new long[(bloomBits + 63) >> 6];
        TrainJunkModel.bloomAddV6(bloom, bloomBits, bloomK, 'A', 'B', seed);

        F1Tables v6Tables = new F1Tables(
                qBigram.bytes, bigramBuckets, qBigram.min, qBigram.max,
                qUnigram.bytes, unigramBuckets, qUnigram.min, qUnigram.max,
                bloom, bloomBits, bloomK, seed, 1.0f);

        // -----------------------------------------------------------------
        // Per-script F2/F3/F4 placeholders — all zeros, but with valid
        // calibrations (mu=0, sigma=1).  Classifier weights for LATIN
        // make ONLY z1 contribute (w1=1, w2=w3=w4=0, bias=0), so the
        // expected z-score isolates the v6 F1 codepoint-hash path.
        // -----------------------------------------------------------------

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
        classifierWeights.put("LATIN", new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f});

        Path modelFile = tmp.resolve("v6-test.bin");
        TrainJunkModel.saveModelV6(
                f1Cal, blockTables, blockCal, controlCal, classifierWeights,
                scriptBuckets, scriptTransTable, scriptTransCal,
                v6Tables, modelFile);

        assertTrue(Files.size(modelFile) > 0, "Saved model should be non-empty");

        // -----------------------------------------------------------------
        // Load and verify version.
        // -----------------------------------------------------------------

        JunkDetector detector = JunkDetector.loadFromPath(modelFile);
        assertEquals(6, detector.getModelVersion(), "Loaded model should be v6");

        // -----------------------------------------------------------------
        // Score "ABAB".  Expected:
        //   Pair (A, B):  in Bloom → bigram table → -1.0
        //   Pair (B, A):  not in Bloom → unigram backoff = 1.0 * (-2 + -2) = -4.0
        //   Pair (A, B):  in Bloom → -1.0
        //   mean log-prob = (-1 + -4 + -1) / 3 = -2.0
        //   z1 = (-2 - (-5)) / 1 = +3.0
        //   logit = 1.0 * 3.0 + 0 + 0 + 0 + 0 = +3.0
        //
        // Tolerance: 8-bit quantization of bigram table [-10, -1] gives
        // ~0.035 nat per level; of unigram table [-5, -2] gives ~0.012 nat
        // per level.  Net z-score error is bounded by ~0.1 over 3 pairs.
        // Allow 0.3 tolerance to be safe.
        // -----------------------------------------------------------------

        TextQualityScore score = detector.score("ABAB");
        assertEquals("LATIN", score.getDominantScript(), "Dominant script should be LATIN");
        assertEquals(3.0f, score.getZScore(), 0.3f,
                "Expected z ≈ +3.0 for 'ABAB' (seen-pair + backoff mix)");
    }

    @Test
    void v6RoundTripAllSeenPairsScoreHigher(@TempDir Path tmp) throws IOException {
        // Same shape as the first test but with ALL pairs in the Bloom.
        // mean log-prob = -1.0, z1 = +4.0.  Verifies seen-only path.
        final int seed = TrainJunkModel.V6_FNV_SEED;

        int bigramBuckets = 4096;
        float[] bigramLog = new float[bigramBuckets];
        Arrays.fill(bigramLog, -10.0f);
        // Put both (A,B) and (B,A) at -1.0
        int bucketAB = (int) (TrainJunkModel.fnv1aBigramV6('A', 'B', seed)
                & (bigramBuckets - 1));
        int bucketBA = (int) (TrainJunkModel.fnv1aBigramV6('B', 'A', seed)
                & (bigramBuckets - 1));
        bigramLog[bucketAB] = -1.0f;
        bigramLog[bucketBA] = -1.0f;
        TrainJunkModel.QuantizedFloats qBigram = TrainJunkModel.quantizeFloats(bigramLog);

        int unigramBuckets = 8192;
        float[] unigramLog = new float[unigramBuckets];
        Arrays.fill(unigramLog, -5.0f);
        TrainJunkModel.QuantizedFloats qUnigram = TrainJunkModel.quantizeFloats(unigramLog);

        int bloomBits = 1024;
        int bloomK = 3;
        long[] bloom = new long[(bloomBits + 63) >> 6];
        TrainJunkModel.bloomAddV6(bloom, bloomBits, bloomK, 'A', 'B', seed);
        TrainJunkModel.bloomAddV6(bloom, bloomBits, bloomK, 'B', 'A', seed);

        F1Tables v6Tables = new F1Tables(
                qBigram.bytes, bigramBuckets, qBigram.min, qBigram.max,
                qUnigram.bytes, unigramBuckets, qUnigram.min, qUnigram.max,
                bloom, bloomBits, bloomK, seed, 1.0f);

        Path modelFile = tmp.resolve("v6-test-allseen.bin");
        saveMinimalV6Model(v6Tables, modelFile);
        JunkDetector detector = JunkDetector.loadFromPath(modelFile);

        TextQualityScore score = detector.score("ABAB");
        // mean = -1.0, z1 = +4.0, logit = +4.0
        assertEquals(4.0f, score.getZScore(), 0.3f,
                "All-seen 'ABAB' should score z ≈ +4");
    }

    // -----------------------------------------------------------------------
    // Helper — minimal LATIN-only v6 model for tests that only need to
    // exercise scoring of LATIN text.
    // -----------------------------------------------------------------------

    private static void saveMinimalV6Model(F1Tables v6,
                                            Path modelFile) throws IOException {
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
        classifierWeights.put("LATIN", new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f});

        TrainJunkModel.saveModelV6(
                f1Cal, blockTables, blockCal, controlCal, classifierWeights,
                scriptBuckets, scriptTransTable, scriptTransCal,
                v6, modelFile);
    }

    /**
     * End-to-end trainer integration: drives
     * {@link TrainJunkModel#trainCodepointHashTables} on a tiny synthetic
     * corpus, calibrates F1, saves a model, loads it via
     * {@link JunkDetector#load}, and scores text.  Catches drift between
     * trainer F1 math and inference F1 math — the Bloom-filter hash
     * scheme, FNV seed, quantization range, and codepoint-pair iteration
     * order all have to agree exactly, or scoring produces nonsense.
     *
     * <p>F2/F3/F4 are zeroed out (placeholder data) — the test isolates
     * F1's trainer↔inference round-trip.  The actual retrain (with real
     * F2/F3/F4 training data) is the training-phase work.
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

        // --- 2. Phase 1: train codepoint-hash tables ---
        // Use a small Bloom (64 KB) — the synthetic corpus has only a
        // few hundred unique pairs.
        F1Tables f1 = TrainJunkModel.trainCodepointHashTables(
                List.of(trainFile), 524288);

        // Sanity: Bloom should contain pairs we observed in training.
        // "the" → pairs (t,h) and (h,e); "fox" → (f,o), (o,x).
        assertTrue(TrainJunkModel.bloomContainsV6(
                        f1.bloomBits, f1.bloomBitCount, f1.bloomK,
                        't', 'h', f1.fnvSeed),
                "Bloom should contain (t, h) — appears in training");
        assertTrue(TrainJunkModel.bloomContainsV6(
                        f1.bloomBits, f1.bloomBitCount, f1.bloomK,
                        'o', 'x', f1.fnvSeed),
                "Bloom should contain (o, x) — appears in training");

        // --- 3. F1 raw scoring sanity ---
        double meanLogP = JunkDetector.computeF1MeanLogP(
                "the quick brown fox", f1);
        assertTrue(Double.isFinite(meanLogP),
                "Mean log-prob on training text should be finite, got " + meanLogP);
        // A score in [-10, 0] is the expected range for in-distribution text.
        assertTrue(meanLogP > -10 && meanLogP < 0,
                "Score on training text should be sensible, got " + meanLogP);

        // --- 4. Phase 1.5: F1 calibration on dev ---
        float[] f1CalLatin = TrainJunkModel.calibrateF1PerScript(devFile, f1);
        assertTrue(Float.isFinite(f1CalLatin[0]), "mu1 should be finite");
        assertTrue(Float.isFinite(f1CalLatin[1]) && f1CalLatin[1] > 0,
                "sigma1 should be positive finite");

        // --- 5. Assemble + save a minimal v6 model ---
        // F2/F3/F4 tables zeroed, classifier weights pure-F1 (w1=1, rest 0).
        int blockN = UnicodeBlockRanges.bucketCount();
        TreeMap<String, float[]> blockTables = new TreeMap<>();
        blockTables.put("LATIN", new float[blockN * blockN]);
        TreeMap<String, float[]> blockCal = new TreeMap<>();
        blockCal.put("LATIN", new float[]{0f, 1f});
        TreeMap<String, float[]> controlCal = new TreeMap<>();
        controlCal.put("LATIN", new float[]{0f, 1f});
        TreeMap<String, float[]> f1CalMap = new TreeMap<>();
        f1CalMap.put("LATIN", f1CalLatin);
        TreeMap<String, float[]> classifierWeights = new TreeMap<>();
        classifierWeights.put("LATIN", new float[]{1f, 0f, 0f, 0f, 0f});

        List<String> scriptBuckets = List.of("LATIN", "OTHER");
        float[] scriptTransTable = new float[scriptBuckets.size() * scriptBuckets.size()];
        float[] scriptTransCal = new float[]{0f, 1f};

        Path modelPath = tmp.resolve("junkdetect.bin");
        TrainJunkModel.saveModelV6(
                f1CalMap, blockTables, blockCal, controlCal, classifierWeights,
                scriptBuckets, scriptTransTable, scriptTransCal, f1, modelPath);

        // --- 6. Load via JunkDetector and score ---
        JunkDetector detector = JunkDetector.loadFromPath(modelPath);
        assertEquals(6, detector.getModelVersion(),
                "Loaded model should be v6");
        assertTrue(detector.knownScripts().contains("LATIN"),
                "Loaded model should know LATIN");

        // Score in-distribution text.  With w1=1 and z2/z3/z4 forced to 0,
        // the logit is purely z1 = (raw - mu)/sigma.  A short window of
        // in-distribution text should produce z1 in roughly [-2, +2] —
        // not at the calibration extremes.
        TextQualityScore score = detector.score("the quick brown fox jumps");
        assertEquals("LATIN", score.getDominantScript());
        assertTrue(Float.isFinite(score.getZScore()),
                "Score on in-distribution text should be finite, got " + score);

        // --- 7. Train/infer consistency check ---
        // The inference path should compute the same raw F1 score as
        // JunkDetector.computeF1MeanLogP on the same text — if these
        // two ever disagree, the model's calibration is silently wrong.
        // We can verify indirectly: score same text using
        // computeF1MeanLogP and re-derive z1 manually.
        String probe = "pack my box with five dozen liquor jugs";
        double trainerRawMean = JunkDetector.computeF1MeanLogP(probe, f1);
        float expectedZ1 = (float) (trainerRawMean - f1CalLatin[0]) / f1CalLatin[1];
        TextQualityScore probeScore = detector.score(probe);
        // logit = w1 * z1 + 0 + 0 + 0 + 0 = z1 in this test configuration.
        assertEquals(expectedZ1, probeScore.getZScore(), 0.001f,
                "Inference z1 must match trainer-computed z1 "
                + "(train/infer F1 math drift)");
    }

    // Writes one sentence per line, UTF-8, gzipped.
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
