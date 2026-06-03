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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Unit tests for {@link JunkFilterEncodingDetector}.
 *
 * <p>Uses a stub {@link TextQualityDetector} rather than the real
 * {@link JunkDetector} — we are testing arbitration control flow, not
 * the quality of the junk detector's decisions.
 */
public class JunkFilterEncodingDetectorTest {

    /** Stub quality detector: always picks the label matching {@link #preferred}. */
    private static final class PreferenceStub implements TextQualityDetector {
        private final String preferred;

        PreferenceStub(String preferred) {
            this.preferred = preferred;
        }

        @Override
        public TextQualityScore score(String text) {
            return new TextQualityScore(Float.NaN, Float.NaN, Float.NaN,
                    Float.NaN, "UNKNOWN");
        }

        @Override
        public TextQualityComparison compare(String labelA, String candidateA,
                                             String labelB, String candidateB) {
            String winner = preferred.equals(labelA) ? "A"
                    : preferred.equals(labelB) ? "B" : "A";
            return new TextQualityComparison(winner, 0.0f,
                    score(candidateA), score(candidateB), labelA, labelB);
        }
    }

    /**
     * Functional stub for the CJK-vs-non-CJK family gate.  Returns one of four
     * controlled z-scores per scored string, keyed on whether the string
     * contains Han ideographs (CJK family) and whether it is a "diff" string
     * (script-letters only, i.e. every codepoint &ge; 0x80) vs whole text.
     * Lets us drive {@code JunkFilterEncodingDetector}'s gate deterministically
     * without the real model: the detector scores both the whole decoded text
     * (champion metric) and its script-letter diff (family-gate metric) for
     * each candidate, so the four cells fully determine the gate's decision.
     */
    private static final class ZStub implements TextQualityDetector {
        private final double wholeCjk;
        private final double wholeNonCjk;
        private final double diffCjk;
        private final double diffNonCjk;

        ZStub(double wholeCjk, double wholeNonCjk, double diffCjk, double diffNonCjk) {
            this.wholeCjk = wholeCjk;
            this.wholeNonCjk = wholeNonCjk;
            this.diffCjk = diffCjk;
            this.diffNonCjk = diffNonCjk;
        }

        private static boolean isCjk(String s) {
            return s.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
        }

        /** Diff string = script-letters only: non-empty, every codepoint &ge; 0x80. */
        private static boolean isDiff(String s) {
            return !s.isEmpty() && s.codePoints().allMatch(c -> c >= 0x80);
        }

        @Override
        public TextQualityScore score(String text) {
            boolean cjk = isCjk(text);
            double z = isDiff(text)
                    ? (cjk ? diffCjk : diffNonCjk)
                    : (cjk ? wholeCjk : wholeNonCjk);
            return new TextQualityScore((float) z, Float.NaN, Float.NaN, Float.NaN,
                    cjk ? "HAN" : "LATIN");
        }

        @Override
        public TextQualityComparison compare(String labelA, String candidateA,
                                             String labelB, String candidateB) {
            // Not exercised by the gate path (which uses score()); provided only
            // to satisfy the interface.  Honor the contract: winner() must be
            // labelA or labelB, picked by the higher (cleaner) z-score.
            TextQualityScore a = score(candidateA);
            TextQualityScore b = score(candidateB);
            String winner = a.getZScore() >= b.getZScore() ? labelA : labelB;
            float delta = Math.abs(a.getZScore() - b.getZScore());
            return new TextQualityComparison(winner, delta, a, b, labelA, labelB);
        }
    }

    /**
     * ASCII filler + 20 copies of the byte pair {@code {0xC4, 0xE3}}: decodes to
     * Han ideographs (你…) under GB18030 but accented Latin (Ä ã…) under
     * windows-1252.  A clean false-CJK vs real-CJK probe — the ASCII keeps the
     * whole-text strings out of the "diff" bucket, while the high bytes are the
     * only place the two decodes disagree.
     */
    private static byte[] cjkAmbiguousBytes() {
        byte[] ascii = "the quick brown fox jumps over the lazy dog "
                .getBytes(StandardCharsets.US_ASCII);
        byte[] hi = new byte[40];
        for (int i = 0; i < 20; i++) {
            hi[2 * i] = (byte) 0xC4;
            hi[2 * i + 1] = (byte) 0xE3;
        }
        byte[] out = new byte[ascii.length + hi.length];
        System.arraycopy(ascii, 0, out, 0, ascii.length);
        System.arraycopy(hi, 0, out, ascii.length, hi.length);
        return out;
    }

    private static ParseContext contextWith(EncodingResult... results) {
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(results), "stub");
        ParseContext p = new ParseContext();
        p.set(EncodingDetectorContext.class, ctx);
        return p;
    }

    @Test
    public void picksPreferredCharsetFromTwoCandidates() throws Exception {
        Charset utf8 = StandardCharsets.UTF_8;
        Charset win1252 = Charset.forName("windows-1252");
        // Non-ASCII bytes so UTF-8 and windows-1252 decode to different strings
        // (otherwise arbiter sees identical decodings and abstains).
        byte[] bytes = "café résumé naïve".getBytes(StandardCharsets.UTF_8);

        ParseContext pc = contextWith(
                new EncodingResult(utf8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.5f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size(), "Expected exactly one result");
            assertEquals(utf8, out.get(0).getCharset());
        }
    }

    @Test
    public void noopWhenNoQualityDetector() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector((TextQualityDetector) null);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "No TextQualityDetector → detector must be a no-op");
        }
    }

    @Test
    public void noopWhenOnlyOneCandidate() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.9f, "UTF-8",
                        EncodingResult.ResultType.DECLARATIVE));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Single candidate → no arbitration needed, no-op");
        }
    }

    @Test
    public void noopWhenAllDecodingsIdentical() throws Exception {
        // Pure-ASCII bytes decode identically under UTF-8 and windows-1252.
        byte[] bytes = "plain ascii content".getBytes(StandardCharsets.US_ASCII);

        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new PreferenceStub("UTF-8"));
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Byte-identical decodings → arbiter abstains");
            assertEquals("junk-filter-identical-decodings",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    // NOTE: a full default-constructor integration test (which would load
    // the bundled JunkDetector via ServiceLoader) is not included here
    // because JunkDetector currently exposes only static factory methods
    // (loadFromClasspath / loadFromPath / load) and has no public no-arg
    // constructor — ServiceLoader cannot instantiate it. Wiring JunkDetector
    // up as a proper SPI provider is tracked as follow-up work for TIKA-4720;
    // at that point this test can be added to exercise the real SPI path.

    /**
     * Regression: Korean text was being mis-arbitrated to GB18030 (Chinese)
     * because JunkDetector's HAN classifier scores cross-script mojibake more
     * permissively than HANGUL scores its own correct text (per-script
     * calibration bias).  The fix is the calibrated rescaling in
     * {@link JunkFilterEncodingDetector} (per-script affine transform of
     * z-scores to a common scale).
     *
     * <p>This test uses a real {@link JunkDetector} model (default
     * constructor loads from classpath) on synthesized bytes — no corpus
     * dependency.
     */
    @Test
    public void koreanTextNotMisarbitragedToChinese() throws Exception {
        Charset xwin949 = Charset.forName("x-windows-949");
        Charset gb18030 = Charset.forName("GB18030");
        // Real Korean text — enough characters that the HANGUL classifier
        // has signal to work with after HTML strip would leave it alone
        // (the bytes are pure non-HTML).
        String korean = "초록샘 새벽교회 주일말씀 열린침례교회 한국교회";
        byte[] bytes = korean.getBytes(xwin949);

        // Note: GB18030 listed first so calibrated arbitration has to beat
        // the insertion-order tiebreak to pick x-windows-949 — this also
        // exercises the cross-script calibration directly.
        ParseContext pc = contextWith(
                new EncodingResult(gb18030, 1.0f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(xwin949, 1.0f, "x-windows-949",
                        EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector = new JunkFilterEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size(), "Expected exactly one result");
            assertEquals(xwin949, out.get(0).getCharset(),
                    "Korean text must arbitrate to x-windows-949, not GB18030. "
                            + "Without calibrated rescaling, the HAN classifier's "
                            + "permissive bias lets Chinese-gibberish decode "
                            + "out-score correct HANGUL.");
        }
    }

    @Test
    void expandHtmlEntities_numericDecimalResolvesToCodepoint() {
        // U+0D4D = Malayalam Sign Virama
        assertEquals("്",
                JunkFilterEncodingDetector.expandHtmlEntities("&#3405;"));
        // Surrounding ASCII preserved
        assertEquals("a്b",
                JunkFilterEncodingDetector.expandHtmlEntities("a&#3405;b"));
    }

    @Test
    void expandHtmlEntities_numericHexResolvesToCodepoint() {
        // U+4E2D = 中 (Han ideograph "middle")
        assertEquals("中",
                JunkFilterEncodingDetector.expandHtmlEntities("&#x4E2D;"));
        assertEquals("中",
                JunkFilterEncodingDetector.expandHtmlEntities("&#x4e2d;"));
    }

    @Test
    void expandHtmlEntities_namedReferences() {
        assertEquals("&", JunkFilterEncodingDetector.expandHtmlEntities("&amp;"));
        assertEquals("<", JunkFilterEncodingDetector.expandHtmlEntities("&lt;"));
        assertEquals(">", JunkFilterEncodingDetector.expandHtmlEntities("&gt;"));
        assertEquals("\"", JunkFilterEncodingDetector.expandHtmlEntities("&quot;"));
        assertEquals("a & b < c", JunkFilterEncodingDetector.expandHtmlEntities("a &amp; b &lt; c"));
    }

    @Test
    void expandHtmlEntities_malformedPassesThrough() {
        // No semicolon → not matched, left as literal
        assertEquals("&#3405", JunkFilterEncodingDetector.expandHtmlEntities("&#3405"));
        // Unknown named entity → left as literal
        assertEquals("&unknown;",
                JunkFilterEncodingDetector.expandHtmlEntities("&unknown;"));
        // Out-of-range numeric → left as literal (passes overflow guard)
        assertEquals("&#999999999;",
                JunkFilterEncodingDetector.expandHtmlEntities("&#999999999;"));
    }

    @Test
    void expandHtmlEntities_mixedEntityAndRawCodepoints() {
        // Simulates an AIT5-style document: mix of raw Malayalam codepoints
        // and numeric entity references encoding more Malayalam codepoints.
        // ത = ത  ് = ് (virama)
        String input = "ത&#3405;ര";
        String expected = "ത്ര";
        assertEquals(expected, JunkFilterEncodingDetector.expandHtmlEntities(input));
    }

    // ----- CJK-vs-non-CJK family gate (the demote-only false-CJK fix) -----
    //
    // The whole-text z coin-flips on the CJK/non-CJK boundary for
    // COMMON-dominated docs: markup/digits/punctuation decode identically under
    // every candidate and swamp the few discriminating high bytes, so the junk
    // model's whole-text argmax sometimes crowns a garbage CJK decode over the
    // correct single-byte one (false-CJK), and sometimes the reverse.  The
    // script-letter "diff" z reads that boundary cleanly (coherent CJK vs
    // ideograph mojibake), so the gate uses it to decide ONLY the family.
    // Measured at 29k, the diff z reliably DEMOTES (CJK champion -> non-CJK; OOV
    // improves on every flip) but UNreliably promotes, so the gate is
    // demote-only and fires only past FAMILY_DIFF_MARGIN.  These four tests lock
    // each arm of that decision against the {@link ZStub}.

    @Test
    public void familyGate_demotesFalseCjkToNonCjk() throws Exception {
        // Whole-text champion is the CJK pick (the coin-flip), but the diff z
        // clearly prefers the non-CJK decode (coherent Latin >> ideograph
        // mojibake, margin 7.0 > 2.0) -> gate must demote to windows-1252.
        Charset gb = Charset.forName("GB18030");
        Charset win1252 = Charset.forName("windows-1252");
        ParseContext pc = contextWith(
                new EncodingResult(gb, 0.8f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.7f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));
        // wholeCjk(-1.0) > wholeNonCjk(-1.5); diffNonCjk(-1.0) >> diffCjk(-8.0)
        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new ZStub(-1.0, -1.5, -8.0, -1.0));
        try (TikaInputStream tis = TikaInputStream.get(cjkAmbiguousBytes())) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(win1252, out.get(0).getCharset(),
                    "diff z prefers non-CJK by > FAMILY_DIFF_MARGIN -> CJK "
                            + "champion must be demoted to windows-1252");
        }
    }

    @Test
    public void familyGate_keepsRealCjkWhenDiffAgrees() throws Exception {
        // Whole-text champion is CJK and the diff z AGREES (ideographs coherent,
        // Latin garbage) -> gate must NOT fire; real CJK stays CJK.
        Charset gb = Charset.forName("GB18030");
        Charset win1252 = Charset.forName("windows-1252");
        ParseContext pc = contextWith(
                new EncodingResult(gb, 0.8f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.7f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));
        // diffCjk(-1.0) >> diffNonCjk(-8.0): non-CJK does not beat CJK -> no demote
        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new ZStub(-1.0, -1.5, -1.0, -8.0));
        try (TikaInputStream tis = TikaInputStream.get(cjkAmbiguousBytes())) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(gb, out.get(0).getCharset(),
                    "diff z agrees with the CJK champion -> must not demote");
        }
    }

    @Test
    public void familyGate_isDemoteOnly_neverPromotesNonCjkToCjk() throws Exception {
        // Whole-text champion is NON-CJK; even though the diff z would prefer
        // CJK, the gate is demote-only (the promote direction regressed at 29k),
        // so the non-CJK champion must stand.
        Charset gb = Charset.forName("GB18030");
        Charset win1252 = Charset.forName("windows-1252");
        ParseContext pc = contextWith(
                new EncodingResult(gb, 0.7f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.8f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));
        // wholeNonCjk(-1.0) > wholeCjk(-1.5) -> champion non-CJK; diffCjk strong but ignored
        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new ZStub(-1.5, -1.0, -1.0, -8.0));
        try (TikaInputStream tis = TikaInputStream.get(cjkAmbiguousBytes())) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(win1252, out.get(0).getCharset(),
                    "gate is demote-only: a non-CJK champion is never promoted to CJK");
        }
    }

    @Test
    public void familyGate_respectsDiffMargin() throws Exception {
        // Non-CJK diff z beats CJK diff z, but by LESS than FAMILY_DIFF_MARGIN
        // (2.0): a boundary-noise tie, not a clear signal -> no demote.
        Charset gb = Charset.forName("GB18030");
        Charset win1252 = Charset.forName("windows-1252");
        ParseContext pc = contextWith(
                new EncodingResult(gb, 0.8f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.7f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));
        // diffNonCjk(-1.0) - diffCjk(-2.0) = 1.0 < margin 2.0 -> no demote
        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(new ZStub(-1.0, -1.5, -2.0, -1.0));
        try (TikaInputStream tis = TikaInputStream.get(cjkAmbiguousBytes())) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(gb, out.get(0).getCharset(),
                    "diff margin below FAMILY_DIFF_MARGIN -> no demote "
                            + "(boundary-noise guard)");
        }
    }
}
