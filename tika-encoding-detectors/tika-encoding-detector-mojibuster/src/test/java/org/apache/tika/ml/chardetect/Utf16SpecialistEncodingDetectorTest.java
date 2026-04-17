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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * Tests for {@link Utf16SpecialistEncodingDetector}.  Uses a synthetic
 * {@link LinearModel} with hand-picked weights to exercise the inference
 * pipeline without requiring a trained model.
 *
 * <p>Synthetic model design:</p>
 * <ul>
 *   <li>Class 0 = {@code UTF-16-LE}</li>
 *   <li>Class 1 = {@code UTF-16-BE}</li>
 *   <li>Weights encode asymmetry between paired features: a feature firing
 *       on the "LE-characteristic" column pulls class 0 up; the same feature
 *       firing on the "BE-characteristic" column pulls class 1 up.</li>
 *   <li>Specifically: for feature pairs like {@code count_even(0x00)} vs
 *       {@code count_odd(0x00)}, we give class 0 negative weight on even
 *       and positive weight on odd (so UTF-16LE Latin with nulls in odd
 *       column produces a positive class-0 logit), and class 1 gets the
 *       mirror.</li>
 * </ul>
 *
 * <p>The synthetic model doesn't need to be accurate — it just needs to be
 * well-defined so we can predict which side "should win" for each test
 * probe and verify the detector behaves correspondingly.</p>
 */
public class Utf16SpecialistEncodingDetectorTest {

    // Feature indices — must match Utf16ColumnFeatureExtractor
    private static final int NUL_EVEN = 0, NUL_ODD = 1;
    private static final int CTRL_EVEN = 2, CTRL_ODD = 3;
    private static final int ASCII_EVEN = 4, ASCII_ODD = 5;
    // 6, 7 = DEL
    private static final int C1_EVEN = 8, C1_ODD = 9;
    private static final int HI_EVEN = 10, HI_ODD = 11;

    /**
     * Build a synthetic UTF-16 specialist model with hand-picked weights.
     *
     * <p>Convention: class 0 = LE, class 1 = BE.  Weights are assigned so
     * that column asymmetry (high count in odd column for LE, high count
     * in even column for BE) produces strong logits.</p>
     */
    private static LinearModel syntheticModel() {
        int numBuckets = Utf16ColumnFeatureExtractor.NUM_FEATURES;
        int numClasses = 2;
        String[] labels = {"UTF-16-LE", "UTF-16-BE"};

        // INT8 weights: class 0 (LE) vs class 1 (BE).
        // For each range, the "odd column supports LE, even column supports BE" rule.
        byte[][] weights = new byte[numClasses][numBuckets];

        // For UTF-16LE, high byte lands in ODD column, low byte in EVEN.
        // Per-script "high byte" ranges: NUL (Latin), CTRL (Cyrillic/Greek),
        // ASCII (CJK U+4E00-7EFF), C1 (upper CJK), HI (extreme CJK).
        //
        // Weights and scale chosen so that: (a) long Latin probes don't
        // saturate the per-feature clip (1.5 * sqrt(nnz)) into a tie —
        // requires ASCII_weight * max_count * scale < clip; (b) short CJK
        // probes clear the MIN_LOGIT_MARGIN threshold — requires boosting
        // the CJK-discriminating C1 weights.
        weights[0][NUL_ODD]    = +10;
        weights[0][NUL_EVEN]   = -10;
        weights[0][CTRL_ODD]   = +10;
        weights[0][CTRL_EVEN]  = -10;
        weights[0][ASCII_ODD]  = +3;
        weights[0][ASCII_EVEN] = -3;
        weights[0][C1_ODD]     = +100;
        weights[0][C1_EVEN]    = -100;
        weights[0][HI_EVEN]    = +3;
        weights[0][HI_ODD]     = -3;

        // BE: exact mirror (high byte at EVEN)
        weights[1][NUL_EVEN]   = +10;
        weights[1][NUL_ODD]    = -10;
        weights[1][CTRL_EVEN]  = +10;
        weights[1][CTRL_ODD]   = -10;
        weights[1][ASCII_EVEN] = +3;
        weights[1][ASCII_ODD]  = -3;
        weights[1][C1_EVEN]    = +100;
        weights[1][C1_ODD]     = -100;
        weights[1][HI_ODD]     = +3;
        weights[1][HI_EVEN]    = -3;

        float[] scales = {0.002f, 0.002f};
        float[] biases = {0.0f, 0.0f};

        return new LinearModel(numBuckets, numClasses, labels, scales, biases, weights);
    }

    private Utf16SpecialistEncodingDetector detector() {
        return new Utf16SpecialistEncodingDetector(syntheticModel(), 512);
    }

    private static List<EncodingResult> detect(Utf16SpecialistEncodingDetector d,
                                                byte[] probe) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            return d.detect(tis, new Metadata(), new ParseContext());
        }
    }

    // --- model-loading semantics ---

    @Test
    public void nullModelRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Utf16SpecialistEncodingDetector(null, 512));
    }

    @Test
    public void wrongBucketCountRejected() {
        byte[][] weights = new byte[2][5];  // wrong bucket count
        float[] scales = {1.0f, 1.0f};
        float[] biases = {0.0f, 0.0f};
        LinearModel bad = new LinearModel(5, 2,
                new String[]{"UTF-16-LE", "UTF-16-BE"}, scales, biases, weights);
        assertThrows(IllegalArgumentException.class,
                () -> new Utf16SpecialistEncodingDetector(bad, 512));
    }

    @Test
    public void wrongClassCountRejected() {
        byte[][] weights = new byte[3][Utf16ColumnFeatureExtractor.NUM_FEATURES];
        float[] scales = {1.0f, 1.0f, 1.0f};
        float[] biases = {0.0f, 0.0f, 0.0f};
        LinearModel bad = new LinearModel(Utf16ColumnFeatureExtractor.NUM_FEATURES, 3,
                new String[]{"A", "B", "C"}, scales, biases, weights);
        assertThrows(IllegalArgumentException.class,
                () -> new Utf16SpecialistEncodingDetector(bad, 512));
    }

    @Test
    public void bundledClasspathResourceLoads() throws IOException {
        // The trained model ships as a classpath resource in the mojibuster
        // module.  No-arg constructor must load it successfully, and the
        // loaded model must have the expected shape for the UTF-16 extractor.
        Utf16SpecialistEncodingDetector d = new Utf16SpecialistEncodingDetector();
        // A clean UTF-16LE probe should produce a confident LE result.
        byte[] probe = "Hello World. This is a UTF-16LE sanity check."
                .getBytes(Charset.forName("UTF-16LE"));
        SpecialistOutput out = d.score(probe);
        assertEquals(2, out.getClassLogits().size());
        assertTrue(out.getClassLogits().containsKey("UTF-16-LE"));
        assertTrue(out.getClassLogits().containsKey("UTF-16-BE"));
        assertTrue(out.getLogit("UTF-16-LE") > out.getLogit("UTF-16-BE"),
                "bundled model should rank LE > BE on LE bytes; got "
                        + out.getClassLogits());
    }

    // --- detection outputs ---

    @Test
    public void emptyProbeReturnsEmpty() throws IOException {
        List<EncodingResult> results = detect(detector(), new byte[0]);
        assertEquals(0, results.size());
    }

    @Test
    public void singleByteProbeReturnsEmpty() throws IOException {
        // Can't tell alignment from fewer than 2 bytes.
        List<EncodingResult> results = detect(detector(), new byte[]{0x41});
        assertEquals(0, results.size());
    }

    @Test
    public void utf16LeLatinDetectedAsLE() throws IOException {
        byte[] probe = "Hello World. This is a UTF-16LE Latin probe."
                .getBytes(Charset.forName("UTF-16LE"));
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(1, results.size(), "should return exactly one candidate");
        EncodingResult r = results.get(0);
        assertEquals("UTF-16-LE", r.getLabel());
        assertEquals(Charset.forName("UTF-16LE"), r.getCharset());
        assertEquals(EncodingResult.ResultType.STATISTICAL, r.getResultType());
        assertTrue(r.getConfidence() > 0.5f,
                "confidence should be substantial, got " + r.getConfidence());
    }

    @Test
    public void utf16BeLatinDetectedAsBE() throws IOException {
        byte[] probe = "Hello World. This is a UTF-16BE Latin probe."
                .getBytes(Charset.forName("UTF-16BE"));
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(1, results.size());
        EncodingResult r = results.get(0);
        assertEquals("UTF-16-BE", r.getLabel());
        assertEquals(Charset.forName("UTF-16BE"), r.getCharset());
    }

    @Test
    public void utf16LeCjkDetectedAsLE() throws IOException {
        byte[] probe = "精密過濾旋流器は日本の製品です。東京で製造されています。"
                .getBytes(Charset.forName("UTF-16LE"));
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(1, results.size());
        assertEquals("UTF-16-LE", results.get(0).getLabel());
    }

    @Test
    public void utf16BeCjkDetectedAsBE() throws IOException {
        byte[] probe = "精密過濾旋流器は日本の製品です。東京で製造されています。"
                .getBytes(Charset.forName("UTF-16BE"));
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(1, results.size());
        assertEquals("UTF-16-BE", results.get(0).getLabel());
    }

    @Test
    public void htmlProducesNoResult() throws IOException {
        // HTML: near-symmetric columns → neither LE nor BE exceeds the
        // logit-margin threshold → detector returns empty.
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            html.append("<div class=\"item-").append(i).append("\">content ")
                .append(i).append("</div>\n");
        }
        byte[] probe = html.toString().getBytes(StandardCharsets.US_ASCII);
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(0, results.size(),
                "HTML should produce empty result (column-symmetric) — "
                        + "this is the HTML-immunity property");
    }

    @Test
    public void pureAsciiEnglishProducesNoResult() throws IOException {
        byte[] probe = ("The quick brown fox jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs.")
                .getBytes(StandardCharsets.US_ASCII);
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(0, results.size(),
                "pure ASCII should produce empty result");
    }

    @Test
    public void scatteredNullsProduceNoResult() throws IOException {
        // Regression case P1: random bytes with ~1% null density that
        // previously tricked the old structural UTF-16 detector.
        byte[] probe = new byte[1000];
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < probe.length; i++) {
            if (rng.nextDouble() < 0.01) {
                probe[i] = 0x00;
            } else {
                probe[i] = (byte) (0x20 + rng.nextInt(95));
            }
        }
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(0, results.size(),
                "scattered nulls with no 2-byte alignment should not trigger");
    }

    @Test
    public void probeLongerThanBudgetIsTrimmed() throws IOException {
        // Build a probe much longer than the default 512-byte budget but with
        // clear UTF-16LE structure.  Detector should still handle it correctly
        // (reading only the prefix) and produce a confident result.
        String text = "This is a sufficiently long UTF-16LE Latin test probe " +
                "with plenty of content to exercise the probe-size bound. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 2000) {
            sb.append(text);
        }
        byte[] probe = sb.toString().getBytes(Charset.forName("UTF-16LE"));
        List<EncodingResult> results = detect(detector(), probe);

        assertEquals(1, results.size());
        assertEquals("UTF-16-LE", results.get(0).getLabel());
    }

    // --- logit-level (combiner) entry points ---

    @Test
    public void scoreEmitsBothClassLogitsWithoutThreshold() throws IOException {
        // detect() returns [] for short probes where margin < threshold.
        // score() returns raw logits regardless — the combiner decides.
        byte[] probe = "Hi".getBytes(Charset.forName("UTF-16LE"));
        Utf16SpecialistEncodingDetector d = detector();
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            SpecialistOutput out = d.score(tis);
            assertEquals("utf16", out.getSpecialistName());
            assertEquals(2, out.getClassLogits().size());
            assertTrue(out.getClassLogits().containsKey("UTF-16-LE"));
            assertTrue(out.getClassLogits().containsKey("UTF-16-BE"));
        }
    }

    @Test
    public void scoreReturnsNullForTooShortProbe() throws IOException {
        Utf16SpecialistEncodingDetector d = detector();
        try (TikaInputStream tis = TikaInputStream.get(new byte[]{0x41})) {
            assertEquals(null, d.score(tis));
        }
    }

    @Test
    public void scoreBytesGivesLeHigherLogitForLePattern() {
        byte[] probe = "Hello World. This is UTF-16LE."
                .getBytes(Charset.forName("UTF-16LE"));
        SpecialistOutput out = detector().scoreBytes(probe);
        float le = out.getLogit("UTF-16-LE");
        float be = out.getLogit("UTF-16-BE");
        assertTrue(le > be, "LE should score higher than BE, got LE=" + le + " BE=" + be);
    }

    @Test
    public void streamPositionIsPreserved() throws IOException {
        // The detector marks/resets the stream — a subsequent read should see
        // the same bytes as if we hadn't called detect at all.
        byte[] probe = "Hello World.".getBytes(Charset.forName("UTF-16LE"));
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            byte firstByte = (byte) tis.read();
            // push back...
        }
        // Separate test: read 2 bytes, detect, read rest, verify all bytes match.
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            detector().detect(tis, new Metadata(), new ParseContext());
            byte[] reRead = new byte[probe.length];
            int n = 0;
            int b;
            while ((b = tis.read()) != -1 && n < reRead.length) {
                reRead[n++] = (byte) b;
            }
            assertEquals(probe.length, n);
            for (int i = 0; i < probe.length; i++) {
                assertEquals(probe[i], reRead[i],
                        "byte " + i + " should match after detect/reset cycle");
            }
        }
    }
}
