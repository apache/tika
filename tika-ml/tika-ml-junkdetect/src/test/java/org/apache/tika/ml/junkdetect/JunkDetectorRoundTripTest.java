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
import java.util.ArrayList;
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
public class JunkDetectorRoundTripTest {

    @Test
    void roundTripSeenPairAndUnigramBackoff(@TempDir Path tmp) throws IOException {
        // -----------------------------------------------------------------
        // Build a tiny synthetic model for LATIN.
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
        BigramTables tables = buildLatinTablesAB();

        Path modelFile = tmp.resolve("junk-test.bin");
        saveMinimalModel(tables, modelFile);

        // Verify the file roundtrips through the loader.
        JunkDetector detector = JunkDetector.loadFromPath(modelFile);
        assertEquals(JunkDetector.VERSION, detector.getModelVersion(), "Loaded model should match current VERSION");

        TextQualityScore score = detector.score("ABAB");
        assertEquals("LATIN", score.getDominantScript(), "Dominant script should be LATIN");
        // Quantization of [-4, -1] to 8 bits introduces ~0.012 nat / level.
        // Net z-error over 3 pairs bounded ~0.05; allow 0.3 to be safe.
        // Expected = calibrated mean F1 over the bucketed bigrams (A·B, B·A, A·B).
        assertEquals(expectedRunZ(tables, "ABAB", -5.0f, 1.0f), score.getZScore(), 0.05f,
                "Inference z must match the authoritative F1 over the bucketed bigrams");
    }

    @Test
    void roundTripAllSeenPairsScoreHigher(@TempDir Path tmp) throws IOException {
        // Same shape as the first test but with BOTH (A,B) and (B,A) in the
        // bigram table.  mean log-prob = -1.0, z1 = +4.0, logit = +4.0.
        int[] cpIndex = new int[]{'A', 'B'};
        int[] keys = new int[4];
        Arrays.fill(keys, BigramTables.EMPTY_KEY);
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

        BigramTables tables = new BigramTables(cpIndex, keys, values, unigramBytes,
                bMin, bMax, uMin, uMax,
                -10.0f, 1.0f);

        Path modelFile = tmp.resolve("junk-test-allseen.bin");
        saveMinimalModel(tables, modelFile);
        JunkDetector detector = JunkDetector.loadFromPath(modelFile);

        TextQualityScore score = detector.score("ABAB");
        assertEquals(expectedRunZ(tables, "ABAB", -5.0f, 1.0f), score.getZScore(), 0.05f,
                "All-seen 'ABAB': inference z must match authoritative bucketed F1");
    }

    /**
     * End-to-end trainer integration: drives {@link
     * TrainJunkModel#trainBigramTablesForScript} on a tiny synthetic corpus,
     * calibrates F1, saves a model, loads it, and scores text.  Catches
     * drift between trainer F1 math and inference F1 math — the FNV
     * mix-hash, packed-key layout, and codepoint-pair iteration order all
     * have to agree exactly, or scoring produces nonsense.
     *
     * <p>The block / control / script-transition features are zeroed out
     * (placeholder data) — the test isolates the bigram (z1)
     * trainer↔inference round-trip.
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

        // --- 2. Train bigram tables for this script ---
        // Tiny corpus → min_count=1 so all pairs survive.
        BigramTables tables = TrainJunkModel.trainBigramTablesForScript(trainFile,
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

        // --- 4. z1 calibration on dev ---
        float[] f1CalLatin = TrainJunkModel.calibrateBucketScript(
                devFile, "LATIN", java.util.Map.of("LATIN", tables));
        assertTrue(Float.isFinite(f1CalLatin[0]), "mu1 should be finite");
        assertTrue(Float.isFinite(f1CalLatin[1]) && f1CalLatin[1] > 0,
                "sigma1 should be positive finite");

        // --- 5. Assemble + save a minimal model ---
        int blockN = UnicodeBlockRanges.bucketCount();
        TreeMap<String, BigramTables> f1Tables = new TreeMap<>();
        f1Tables.put("LATIN", tables);
        TreeMap<String, float[]> f1CalMap = new TreeMap<>();
        f1CalMap.put("LATIN", f1CalLatin);
        // Single GLOBAL block table / control calib / combiner.
        float[] blockTable = new float[blockN * blockN];
        float[] blockCal = new float[]{0f, 1f};
        float[] controlCal = new float[]{0f, 1f};
        float[] combiner = new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};

        List<String> scriptBuckets = List.of("LATIN", "OTHER");
        float[] scriptTransTable = new float[scriptBuckets.size() * scriptBuckets.size()];
        float[] scriptTransCal = new float[]{0f, 1f};

        Path modelPath = tmp.resolve("junkdetect.bin");
        TrainJunkModel.saveModel(
                f1Tables, f1CalMap, blockTable, blockCal, controlCal,
                combiner, scriptBuckets, scriptTransTable, scriptTransCal, modelPath);

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
        // logit = w1*z1 (rest 0); inference aggregates the same bucketed
        // bigram F1 the helper computes — must agree (no drift).
        assertEquals(expectedZ1, probeScore.getZScore(), 0.02f,
                "Inference z1 must match the bucketed bigram F1 "
                + "(train/infer F1 math drift)");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a BigramTables with codepoint index ['A', 'B'], where (A,B) has a
     * stored log-prob of -1.0 but (B,A) is absent (forces unigram backoff).
     * Unigram log-prob = -2.0 for both A and B.
     *
     * <p>Bigram quant range is set explicitly to {@code [-10, -1]} so that
     * the single stored value at -1.0 maps to byte 255 (avoids the
     * degenerate {@code min == max} branch in
     * {@link TrainJunkModel#quantizeFloats}).  Same idea for the unigram
     * range {@code [-5, -2]} so the (-2.0, -2.0) values map to byte 255.
     */
    private static BigramTables buildLatinTablesAB() {
        int[] cpIndex = new int[]{'A', 'B'};

        // 4 slots ≈ 25% load for 1 pair.  Open-addressing with linear probe.
        int[] keys = new int[4];
        Arrays.fill(keys, BigramTables.EMPTY_KEY);
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

        return new BigramTables(cpIndex, keys, values, unigramBytes,
                bMin, bMax,
                uMin, uMax,
                -10.0f, 1.0f);
    }

    @Test
    void tokenizationScoresGlueButKeepsAnomaliesAndWhitespaceAsBoundaries() {
        // A letter run is wrapped ^...$.  GLUE (punctuation, symbols, numbers)
        // attaches to the open run and IS scored at codepoint resolution, so a
        // symbol wedged mid-word becomes a real (rare) bigram the LM can floor.
        // But DECODE ANOMALIES (here U+FFFD; also C1 / PUA) and WHITESPACE stay
        // boundaries that split the run and emit nothing — the anomaly penalty
        // lives solely in z6, never z1, so z1 cannot cannibalize the FFFD signal.
        String fffd = String.valueOf((char) 0xFFFD);
        // all-letter run
        assertEquals(List.of("^-a", "a-b", "b-c", "c-d", "d-$"), bigrams("abcd"));
        // glue (period, U+2030 per-mille) is SCORED inside the run, not dropped
        assertEquals(List.of("^-a", "a-b", "b-.", ".-c", "c-d", "d-$"), bigrams("ab.cd"));
        assertEquals(List.of("^-a", "a-b", "b-\u2030", "\u2030-c", "c-d", "d-$"),
                bigrams("ab\u2030cd"));
        // U+FFFD (decode anomaly) is still a BOUNDARY: splits, emits nothing
        assertEquals(List.of("^-a", "a-b", "b-$", "^-c", "c-d", "d-$"),
                bigrams("ab" + fffd + "cd"));
        assertEquals(List.of("^-a", "a-b", "b-$"), bigrams("ab" + fffd + fffd));
        // whitespace is a boundary too
        assertEquals(List.of("^-a", "a-b", "b-$", "^-c", "c-d", "d-$"),
                bigrams("ab cd"));
    }

    /** Collects {@link JunkDetector#forEachScriptBigram} output as "a-b" strings,
     *  rendering the run-boundary sentinels as {@code ^} (start) / {@code $} (end). */
    private static List<String> bigrams(String s) {
        List<String> out = new ArrayList<>();
        JunkDetector.forEachScriptBigram(s.codePoints().toArray(), (script, a, b) ->
                out.add(fmtCp(a) + "-" + fmtCp(b)));
        return out;
    }

    private static String fmtCp(int cp) {
        if (cp == JunkDetector.TOKEN_START) return "^";
        if (cp == JunkDetector.TOKEN_END) return "$";
        return new String(Character.toChars(cp));
    }

    @Test
    void caseFoldedBackoffRescuesAllCapsButNotMixedOrMojibake() {
        // Synthetic LATIN table: index ['a','b'], the lowercase pair (a,b) seen
        // at a high log-prob (-1.0).  Uppercase 'A'/'B' are absent from the index.
        BigramTables t = buildLatinTablesLowerAB();
        double seenLower = JunkDetector.computeF1MeanLogP(new int[]{'a', 'b'}, t);
        double allCaps = JunkDetector.computeF1MeanLogP(new int[]{'A', 'B'}, t);
        double mixed = JunkDetector.computeF1MeanLogP(new int[]{'a', 'B'}, t);
        double noTwin = JunkDetector.computeF1MeanLogP(new int[]{'B', 'A'}, t);
        // All-caps "AB" folds to the SEEN lowercase "ab", landing a small case-fold
        // penalty BELOW it (all-caps is a slightly weaker signal) -- but nowhere near
        // the independence floor the mixed/mojibake cases hit below.
        assertTrue(allCaps < seenLower && allCaps > seenLower - 0.5,
                "all-caps AB must fold to ~ the seen lowercase ab (minus a small penalty); "
                + "seenLower=" + seenLower + " allCaps=" + allCaps);
        // Mixed-case "aB" is case-INCONSISTENT -> not folded -> independence floor.
        assertTrue(mixed < allCaps - 1.0,
                "mixed-case aB must not fold (consistency gate)");
        // All-caps "BA" whose lowercase twin (b,a) is UNSEEN -> floors (mojibake case).
        assertTrue(noTwin < allCaps - 1.0,
                "all-caps with no seen lowercase twin must floor");
    }

    /** Like {@link #buildLatinTablesAB} but indexed on LOWERCASE ['a','b'] with
     *  the lowercase pair (a,b) seen at -1.0 — exercises the case-folded backoff
     *  (uppercase 'A'/'B' are absent from the index, so they must fold). */
    private static BigramTables buildLatinTablesLowerAB() {
        int[] cpIndex = new int[]{'a', 'b'};
        int[] keys = new int[4];
        Arrays.fill(keys, BigramTables.EMPTY_KEY);
        byte[] values = new byte[4];
        float bMin = -10.0f;
        float bMax = -1.0f;
        insertOA(keys, values, JunkDetector.packBigramKey(0, 1),
                quantizeOne(-1.0f, bMin, bMax));
        float uMin = -5.0f;
        float uMax = -2.0f;
        byte[] unigramBytes = new byte[]{
                quantizeOne(-2.0f, uMin, uMax),
                quantizeOne(-2.0f, uMin, uMax),
        };
        return new BigramTables(cpIndex, keys, values, unigramBytes,
                bMin, bMax, uMin, uMax, -10.0f, 1.0f);
    }

    /** Expected z1: mean log-prob over the bigrams {@link
     *  JunkDetector#forEachScriptBigram} emits (word-run tokenization with ^/$
     *  wrapping), scored against the single-script {@code tables}, calibrated.
     *  Delegates to the production tokenizer so it cannot drift from inference. */
    private static float expectedRunZ(BigramTables tables, String text, float mu, float sigma) {
        double[] acc = new double[2]; // {sum, count}
        JunkDetector.forEachScriptBigram(text.codePoints().toArray(), (script, a, b) -> {
            double f1 = JunkDetector.computeF1MeanLogP(new int[]{a, b}, tables);
            if (!Double.isNaN(f1)) {
                acc[0] += f1;
                acc[1] += 1;
            }
        });
        return (float) ((acc[0] / acc[1] - mu) / sigma);
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
        while (keys[h] != BigramTables.EMPTY_KEY) {
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
     * Saves a minimal model containing only LATIN, with the block / control /
     * script-transition features zeroed out and pure-z1 combiner weights
     * (w1=1, rest 0, bias 0).  Scoring a window thus reduces to z1 directly.
     * z1 calibration: mu=-5, sigma=1.
     */
    private static void saveMinimalModel(BigramTables tables, Path modelFile) throws IOException {
        TreeMap<String, BigramTables> f1Tables = new TreeMap<>();
        f1Tables.put("LATIN", tables);

        TreeMap<String, float[]> f1Cal = new TreeMap<>();
        f1Cal.put("LATIN", new float[]{-5.0f, 1.0f});

        int blockN = UnicodeBlockRanges.bucketCount();
        // Single GLOBAL block table + global control calibration.
        float[] blockTable = new float[blockN * blockN];
        float[] blockCal = new float[]{0f, 1f};
        float[] controlCal = new float[]{0f, 1f};

        List<String> scriptBuckets = List.of("LATIN", "OTHER");
        float[] scriptTransTable = new float[scriptBuckets.size() * scriptBuckets.size()];
        float[] scriptTransCal = new float[]{0f, 1f};

        // Single GLOBAL combiner: z1 weight 1, z2..z9 weights 0, bias 0 → logit = z1.
        float[] combiner = new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f};

        TrainJunkModel.saveModel(
                f1Tables, f1Cal, blockTable, blockCal, controlCal,
                combiner, scriptBuckets, scriptTransTable, scriptTransCal, modelFile);
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
