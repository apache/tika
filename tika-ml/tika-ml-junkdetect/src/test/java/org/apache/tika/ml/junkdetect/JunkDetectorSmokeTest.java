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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityScore;

/**
 * Smoke tests verifying the bundled model meets minimum quality thresholds.
 * Failures indicate the model needs more data or feature extraction is wrong.
 *
 */
public class JunkDetectorSmokeTest {

    private static JunkDetector detector;

    @BeforeAll
    static void loadModel() throws Exception {
        detector = JunkDetector.loadFromClasspath();
    }

    /**
     * Clean English should score higher than random high-byte garbage interpreted
     * as ISO-8859-1.  Simulates binary data mixed into a text extraction.
     */
    @Test
    void cleanVsGarbage() {
        String clean = "The quick brown fox jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs.";

        byte[] garbageBytes = new byte[80];
        new Random(42).nextBytes(garbageBytes);
        for (int i = 0; i < garbageBytes.length; i++) {
            garbageBytes[i] = (byte) (0x80 | (garbageBytes[i] & 0x7F));
        }
        // Decode as ISO-8859-1 so the string contains high-codepoint characters
        String garbage = new String(garbageBytes, StandardCharsets.ISO_8859_1);

        TextQualityScore cleanScore = detector.score(clean);
        TextQualityScore garbageScore = detector.score(garbage);

        System.out.println("clean:   " + cleanScore);
        System.out.println("garbage: " + garbageScore);

        assertTrue(cleanScore.getZScore() > garbageScore.getZScore(),
                "Clean text should score higher than garbage");
    }

    /**
     * Forward Arabic should score higher than character-reversed Arabic.
     * Character (codepoint) reversal produces valid UTF-8 but wrong reading order —
     * analogous to bidirectional rendering failures or incorrectly stored RTL text.
     */
    @Test
    void forwardVsReversedArabic() {
        String arabic = "اللغة العربية جميلة وغنية بالمفردات والتعبيرات";
        String reversed = reverseString(arabic);

        TextQualityScore fwd = detector.score(arabic);
        TextQualityScore rev = detector.score(reversed);

        System.out.println("arabic forward:  " + fwd);
        System.out.println("arabic reversed: " + rev);

        assertTrue(fwd.getZScore() > rev.getZScore(),
                "Forward Arabic should score higher than character-reversed Arabic");
    }

    /**
     * cp1257 (Baltic) decoding of Lithuanian text should win over cp1252.
     *
     * <p>Tests the {@link JunkDetector#compare} API: given raw bytes that were
     * encoded as cp1257, comparing both decodings should prefer the correct one.
     * A low delta is expected because the LATIN model is trained across ~322 languages
     * and Baltic-specific bigrams are diluted.
     *
     * <p>TODO: improve separation with a Baltic sub-model or Baltic-weighted retraining.
     */
    @Test
    void cp1252VsCp1257OnBalticText() throws Exception {
        String lithuanian = "Lietuvių kalba yra labai graži ir turtinga";
        byte[] cp1257bytes = lithuanian.getBytes("cp1257");

        String ascp1252 = new String(cp1257bytes, "cp1252");
        String ascp1257 = new String(cp1257bytes, "cp1257");

        TextQualityComparison result = detector.compare("cp1252", ascp1252, "cp1257", ascp1257);

        System.out.println("Baltic comparison: " + result);

        assertEquals("cp1257", result.winner(),
                "cp1257 should be identified as the correct encoding for Lithuanian text");
        // Delta is weak (pooled LATIN model dilutes Baltic-specific bigrams).
        // Production threshold is delta > 1.0; PoC floor is 0.1.
        assertTrue(result.delta() > 0.1,
                "Should have some separation: delta=" + result.delta());
    }

    /**
     * cp1251 decoding of Russian text should win over cp1252.
     *
     * <p>This is the canonical Cyrillic mojibake scenario: Windows-1251-encoded Russian
     * text misinterpreted as Windows-1252 (Western European).  The cp1252 decoding
     * produces Latin symbols interspersed with control characters, while cp1251 produces
     * proper Cyrillic.  The model should strongly prefer cp1251.
     *
     * <p>Note: character-reversal of LTR Cyrillic is NOT a useful test — byte-bigram
     * statistics are nearly identical forward and backward for LTR scripts.  Codec
     * comparison is the correct test for LTR scripts.
     */
    @Test
    void cp1252VsCp1251OnRussianText() throws Exception {
        String russian = "Русский язык является одним из восточнославянских языков";
        byte[] cp1251bytes = russian.getBytes("cp1251");

        String ascp1252 = new String(cp1251bytes, "cp1252");
        String ascp1251 = new String(cp1251bytes, "cp1251");

        TextQualityComparison result = detector.compare("cp1252", ascp1252, "cp1251", ascp1251);

        System.out.println("Russian Cyrillic comparison: " + result);

        assertEquals("cp1251", result.winner(),
                "cp1251 should be identified as the correct encoding for Russian text");
        assertTrue(result.delta() > 1.0,
                "Cyrillic codec separation should be strong: delta=" + result.delta());
    }

    /**
     * Clean Japanese (CJK) should score higher than byte-shuffled Japanese.
     */
    @Test
    void cleanVsShuffledCjk() {
        String japanese = "日本語は美しい言語であり、世界中で約1億3千万人が話している。";
        byte[] cleanBytes = japanese.getBytes(StandardCharsets.UTF_8);
        byte[] shuffledBytes = shuffled(cleanBytes, 42);

        // Shuffled bytes are not valid UTF-8; decode as ISO-8859-1 to get a scoreable string
        String shuffledText = new String(shuffledBytes, StandardCharsets.ISO_8859_1);

        TextQualityScore cleanScore = detector.score(japanese);
        TextQualityScore shuffledScore = detector.score(shuffledText);

        System.out.println("Japanese clean:    " + cleanScore);
        System.out.println("Japanese shuffled: " + shuffledScore);

        assertTrue(cleanScore.getZScore() > shuffledScore.getZScore(),
                "Clean Japanese should score higher than shuffled bytes");
    }

    /**
     * Shift-JIS zip entry name (9 bytes) decoded as Shift-JIS should beat the same
     * bytes decoded as UTF-8 (which produces mojibake with FFFD replacement chars).
     *
     * <p>This is the canonical short-text use case: zip parsers encounter raw filename
     * bytes with no BOM or language tag.  At 9 bytes the z-score signal is weak, but
     * the corrupted UTF-8 decode contains FFFD sequences (0xEF 0xBF 0xBD) which are
     * very unlikely in LATIN text, yielding a clearly negative bigram z-score.
     *
     * <p>"テスト.tx" is pure katakana — KATAKANA script maps to the HAN model via
     * {@link JunkDetector#SCRIPT_MODEL_FALLBACK}.
     */
    @Test
    void shiftJisZipEntryNameVsUtf8() throws Exception {
        // 9 Shift-JIS bytes: テスト.tx
        byte[] sjisBytes = "テスト.tx".getBytes("Shift_JIS");
        assertEquals(9, sjisBytes.length, "fixture sanity: expect exactly 9 Shift-JIS bytes");

        String asShiftJis = new String(sjisBytes, "Shift_JIS"); // "テスト.tx"
        String asUtf8     = new String(sjisBytes, StandardCharsets.UTF_8); // "?e?X?g.tx" (mojibake)

        TextQualityComparison result = detector.compare("Shift-JIS", asShiftJis, "UTF-8", asUtf8);

        System.out.println("Shift-JIS zip entry: " + result);

        assertEquals("Shift-JIS", result.winner(),
                "Shift-JIS decode should beat garbled UTF-8 for short Japanese filename");
    }

    /**
     * Regression: a single CJK codepoint sandwiched between modeled-script
     * runs used to NaN-poison the entire score, because the byte-length
     * filter ({@code runUtf8.length >= 2}) and the UTF-16 char-length
     * filter inside {@code computeF1MeanLogP} ({@code text.length() >= 2})
     * disagreed.  A single CJK char is 3 UTF-8 bytes (1 UTF-16 unit), so
     * it passed the outer filter, computed NaN inside, and poisoned the
     * weighted aggregate — surfacing as UNKNOWN to callers.  This was the
     * root cause of the AIT5-class regressions (UTF-8 Malayalam decoded as
     * GB18030 returns lots of single-Han-char runs).
     */
    @Test
    void singleCjkCharDoesNotNaNPoisonScore() {
        // Latin sentence with a stray CJK char dropped in — exactly the
        // shape of a GB18030-mojibake-of-UTF-8 decode at the run-boundary
        // level.  The CJK char forms a single-codepoint HAN run.
        String text = "The quick brown 中 fox jumps over the lazy dog. "
                + "Pack 中 my box with five dozen liquor jugs.";
        TextQualityScore score = detector.score(text);
        assertFalse(score.isUnknown(),
                "score should not be UNKNOWN — single-CJK run should be skipped, "
                        + "not poison the aggregate.  Got: " + score);
    }

    /**
     * Sibling regression: the same NaN-poisoning case caused by a single
     * supplementary-plane (4-byte UTF-8, 2-UTF-16-unit) codepoint.  Less
     * load-bearing than the BMP-CJK case — supplementary chars decode to
     * {@code text.length() == 2} so they pass the inner filter — but
     * worth pinning the behaviour.
     */
    @Test
    void supplementaryPlaneCharSurvivesScoring() {
        // U+1F600 (😀) is a 2-UTF-16-unit supplementary char with script COMMON,
        // so it attaches to a preceding modeled run rather than forming its own.
        String text = "Hello world 😀 this is some plain English text.";
        TextQualityScore score = detector.score(text);
        assertFalse(score.isUnknown(), "supplementary char should not break scoring: " + score);
    }

    // -----------------------------------------------------------------------

    /**
     * Reverses the string at codepoint granularity (not char granularity), so
     * surrogate pairs are kept intact.  Produces valid Unicode in reverse reading
     * order — a realistic distortion for RTL-language tests.
     */
    static String reverseString(String s) {
        int[] codepoints = s.codePoints().toArray();
        for (int i = 0, j = codepoints.length - 1; i < j; i++, j--) {
            int tmp = codepoints[i];
            codepoints[i] = codepoints[j];
            codepoints[j] = tmp;
        }
        return new String(codepoints, 0, codepoints.length);
    }

    private static byte[] shuffled(byte[] bytes, long seed) {
        byte[] copy = bytes.clone();
        Random rng = new Random(seed);
        for (int i = copy.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            byte tmp = copy[i];
            copy[i] = copy[j];
            copy[j] = tmp;
        }
        return copy;
    }
}
