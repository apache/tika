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

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests corresponding to Phase 5.1 target cases in the design doc.
 * These should pass once the model is trained; failures indicate the model
 * needs more data or the feature extraction is wrong.
 */
public class JunkDetectorSmokeTest {

    private static JunkDetector detector;

    @BeforeAll
    static void loadModel() throws Exception {
        detector = JunkDetector.loadFromClasspath();
    }

    /**
     * Clean English should score higher than random high-byte garbage.
     * Also serves as the byte-reversal baseline: garbage bytes ~ byte-reversed text.
     */
    @Test
    void cleanVsGarbage() {
        JunkScore clean = detector.score("The quick brown fox jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs.");
        byte[] garbageBytes = new byte[80];
        new Random(42).nextBytes(garbageBytes);
        // Force all bytes >= 0x80 so it's clearly invalid UTF-8-looking garbage
        for (int i = 0; i < garbageBytes.length; i++) {
            garbageBytes[i] = (byte) (0x80 | (garbageBytes[i] & 0x7F));
        }
        JunkScore garbage = detector.score(new String(garbageBytes, StandardCharsets.ISO_8859_1)
                .getBytes(StandardCharsets.UTF_8));

        System.out.println("clean:   " + clean);
        System.out.println("garbage: " + garbage);

        assertTrue(clean.getZScore() > garbage.getZScore(),
                "Clean text should score higher than garbage");
    }

    /**
     * Forward Arabic should score higher than character-reversed Arabic.
     * Character (codepoint) reversal is a realistic distortion: it produces
     * valid UTF-8 but wrong reading order — analogous to bidirectional rendering
     * failures or incorrectly stored RTL text.
     */
    @Test
    void forwardVsReversedArabic() {
        String arabic = "اللغة العربية جميلة وغنية بالمفردات والتعبيرات";
        byte[] forward = arabic.getBytes(StandardCharsets.UTF_8);
        byte[] reversed = reverseString(arabic).getBytes(StandardCharsets.UTF_8);

        JunkScore fwd = detector.score(forward);
        JunkScore rev = detector.score(reversed);

        System.out.println("arabic forward:  " + fwd);
        System.out.println("arabic reversed: " + rev);

        assertTrue(fwd.getZScore() > rev.getZScore(),
                "Forward Arabic should score higher than character-reversed Arabic");
    }

    /**
     * cp1257 (Baltic) decoding of Lithuanian text should win over cp1252.
     *
     * <p>This tests the {@link JunkDetector#compare} API: given raw bytes that were
     * encoded as cp1257, scoring both decodings should prefer the correct one.
     * A low delta is expected because the LATIN model is trained across ~322 languages
     * and Baltic-specific bigrams are diluted.
     *
     * <p>TODO: improve separation by adding a Baltic sub-model or Baltic-weighted retraining.
     */
    @Test
    void cp1252VsCp1257OnBalticText() throws Exception {
        String lithuanian = "Lietuvių kalba yra labai graži ir turtinga";
        byte[] cp1257bytes = lithuanian.getBytes("cp1257");

        JunkDetector.CompareResult result = detector.compare(cp1257bytes, "cp1252", "cp1257");

        System.out.println("Baltic comparison: " + result);

        assertEquals("B", result.winner(),
                "cp1257 should be identified as the correct encoding for Lithuanian text");
        // Delta is weak (pooled LATIN model dilutes Baltic-specific bigrams).
        // Production threshold is delta > 1.0; PoC floor is 0.1.
        assertTrue(result.delta() > 0.1,
                "Should have some separation: delta=" + result.delta());
    }

    /**
     * cp1251 decoding of Russian text should win over cp1252.
     *
     * <p>This is the canonical Cyrillic mojibake scenario: Windows-1251-encoded
     * Russian text misinterpreted as Windows-1252 (Western European).  The cp1252
     * decoding produces Latin symbols interspersed with control characters, while
     * cp1251 produces proper Cyrillic.  The model should strongly prefer cp1251.
     *
     * <p>Note: character-reversal of LTR Cyrillic is NOT a useful test here —
     * byte-bigram statistics are nearly identical forward and backward for LTR scripts,
     * so the model cannot distinguish them.  Use codec-confusion tests for LTR scripts.
     */
    @Test
    void cp1252VsCp1251OnRussianText() throws Exception {
        String russian = "Русский язык является одним из восточнославянских языков";
        byte[] cp1251bytes = russian.getBytes("cp1251");

        JunkDetector.CompareResult result = detector.compare(cp1251bytes, "cp1252", "cp1251");

        System.out.println("Russian Cyrillic comparison: " + result);

        assertEquals("B", result.winner(),
                "cp1251 should be identified as the correct encoding for Russian text");
        assertTrue(result.delta() > 1.0,
                "Cyrillic codec separation should be strong: delta=" + result.delta());
    }

    /**
     * Clean Japanese (CJK) should score higher than shuffled bytes.
     */
    @Test
    void cleanVsShuffledCjk() {
        String japanese = "日本語は美しい言語であり、世界中で約1億3千万人が話している。";
        byte[] clean = japanese.getBytes(StandardCharsets.UTF_8);
        byte[] shuffled = shuffled(clean, 42);

        JunkScore cleanScore = detector.score(clean);
        JunkScore shuffledScore = detector.score(shuffled);

        System.out.println("Japanese clean:    " + cleanScore);
        System.out.println("Japanese shuffled: " + shuffledScore);

        assertTrue(cleanScore.getZScore() > shuffledScore.getZScore(),
                "Clean Japanese should score higher than shuffled bytes");
    }

    // -----------------------------------------------------------------------

    /**
     * Reverses the string at codepoint granularity (not char granularity), so
     * surrogate pairs are kept intact.  This produces valid Unicode text in
     * reverse reading order — a realistic distortion for RTL-language tests.
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
