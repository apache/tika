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
package org.apache.tika.eval.textquality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.textquality.TextQualityResult;
import org.apache.tika.textquality.TextQualityScorer;

class BigramTextQualityScorerTest {

    // Arabic: "This is a test for the text quality assessment system
    //          which should work correctly in all cases"
    private static final String ARABIC_TEXT =
            "\u0647\u0630\u0627 \u0647\u0648 \u0627\u062e\u062a\u0628"
            + "\u0627\u0631 \u0644\u0646\u0638\u0627\u0645 \u062a\u0642"
            + "\u064a\u064a\u0645 \u062c\u0648\u062f\u0629 \u0627\u0644"
            + "\u0646\u0635 \u0627\u0644\u0639\u0631\u0628\u064a "
            + "\u0648\u0627\u0644\u0630\u064a \u064a\u062c\u0628 "
            + "\u0623\u0646 \u064a\u0639\u0645\u0644 \u0628\u0634"
            + "\u0643\u0644 \u0635\u062d\u064a\u062d \u0641\u064a "
            + "\u062c\u0645\u064a\u0639 \u0627\u0644\u0623\u062d"
            + "\u0648\u0627\u0644";

    // Arabic (shorter): "This is a test... which should work correctly"
    private static final String ARABIC_SHORT =
            "\u0647\u0630\u0627 \u0647\u0648 \u0627\u062e\u062a\u0628"
            + "\u0627\u0631 \u0644\u0646\u0638\u0627\u0645 \u062a\u0642"
            + "\u064a\u064a\u0645 \u062c\u0648\u062f\u0629 \u0627\u0644"
            + "\u0646\u0635 \u0627\u0644\u0639\u0631\u0628\u064a "
            + "\u0648\u0627\u0644\u0630\u064a \u064a\u062c\u0628 "
            + "\u0623\u0646 \u064a\u0639\u0645\u0644 \u0628\u0634"
            + "\u0643\u0644 \u0635\u062d\u064a\u062d";

    // Hebrew: "This is a test for the text quality assessment system
    //          that should work properly in all cases"
    private static final String HEBREW_TEXT =
            "\u05d6\u05d4\u05d5 \u05de\u05d1\u05d7\u05df "
            + "\u05dc\u05de\u05e2\u05e8\u05db\u05ea "
            + "\u05d4\u05e2\u05e8\u05db\u05ea "
            + "\u05d0\u05d9\u05db\u05d5\u05ea "
            + "\u05d4\u05d8\u05e7\u05e1\u05d8 "
            + "\u05d4\u05e2\u05d1\u05e8\u05d9 "
            + "\u05e9\u05d0\u05de\u05d5\u05e8 "
            + "\u05dc\u05e2\u05d1\u05d5\u05d3 "
            + "\u05db\u05e8\u05d0\u05d5\u05d9 \u05d1\u05db\u05dc "
            + "\u05d4\u05de\u05e7\u05e8\u05d9\u05dd";

    // Hebrew (shorter): "...that should work properly"
    private static final String HEBREW_SHORT =
            "\u05d6\u05d4\u05d5 \u05de\u05d1\u05d7\u05df "
            + "\u05dc\u05de\u05e2\u05e8\u05db\u05ea "
            + "\u05d4\u05e2\u05e8\u05db\u05ea "
            + "\u05d0\u05d9\u05db\u05d5\u05ea "
            + "\u05d4\u05d8\u05e7\u05e1\u05d8 "
            + "\u05d4\u05e2\u05d1\u05e8\u05d9 "
            + "\u05e9\u05d0\u05de\u05d5\u05e8 "
            + "\u05dc\u05e2\u05d1\u05d5\u05d3 "
            + "\u05db\u05e8\u05d0\u05d5\u05d9";

    private static BigramTextQualityScorer scorer;

    @BeforeAll
    static void setUp() {
        scorer = new BigramTextQualityScorer();
    }

    @Test
    void testEnglishDetection() {
        TextQualityResult result = scorer.score(
                "The quick brown fox jumps over the lazy dog. "
                + "This is a test of the text quality scoring system.");
        assertEquals("eng", result.getLanguage());
        assertTrue(result.getScore() > -15.0,
                "English score should be reasonable: " + result.getScore());
        assertTrue(result.getBigramCount() > 0);
    }

    @Test
    void testArabicDetection() {
        TextQualityResult result = scorer.score(ARABIC_TEXT);
        assertEquals("ara", result.getLanguage());
        assertTrue(result.getScore() > -18.0,
                "Arabic score should be reasonable: " + result.getScore());
    }

    @Test
    void testHebrewDetection() {
        TextQualityResult result = scorer.score(HEBREW_TEXT);
        assertEquals("heb", result.getLanguage());
        assertTrue(result.getScore() > -18.0,
                "Hebrew score should be reasonable: " + result.getScore());
    }

    @Test
    void testFrenchDetection() {
        TextQualityResult result = scorer.score(
                "Ceci est un test du syst\u00e8me d'\u00e9valuation"
                + " de la qualit\u00e9 du texte fran\u00e7ais. Cette"
                + " phrase devrait \u00eatre suffisamment longue pour"
                + " que le d\u00e9tecteur puisse identifier"
                + " correctement la langue.");
        assertEquals("fra", result.getLanguage());
        assertTrue(result.getScore() > -18.0,
                "French score should be reasonable: " + result.getScore());
    }

    @Test
    void testReversedArabicScoresLower() {
        String reversed = new StringBuilder(ARABIC_SHORT).reverse()
                .toString();
        TextQualityResult normalResult = scorer.score(ARABIC_SHORT);
        TextQualityResult reversedResult = scorer.score(reversed);

        double delta = normalResult.getScore() - reversedResult.getScore();
        assertTrue(delta > 0.5,
                String.format(Locale.ROOT,
                        "Normal (%.4f) vs reversed (%.4f): "
                        + "delta %.4f should be > 0.5 bits",
                        normalResult.getScore(),
                        reversedResult.getScore(), delta));
    }

    @Test
    void testReversedHebrewScoresLower() {
        String reversed = new StringBuilder(HEBREW_SHORT).reverse()
                .toString();
        TextQualityResult normalResult = scorer.score(HEBREW_SHORT);
        TextQualityResult reversedResult = scorer.score(reversed);

        double delta = normalResult.getScore() - reversedResult.getScore();
        assertTrue(delta > 0.5,
                String.format(Locale.ROOT,
                        "Normal (%.4f) vs reversed (%.4f): "
                        + "delta %.4f should be > 0.5 bits",
                        normalResult.getScore(),
                        reversedResult.getScore(), delta));
    }

    @Test
    void testGarbledTextScoresLow() {
        String garbage = "xqzjk vwpfl mnbvc zxqwk jhrty plmnk";
        TextQualityResult result = scorer.score(garbage);

        assertTrue(result.getScore() < -8.0,
                "Garbled text should score low: " + result.getScore());
    }

    @Test
    void testEmptyTextReturnsNeutral() {
        TextQualityResult result = scorer.score("");
        assertEquals(0.0, result.getScore(), 0.001);
        assertEquals("unk", result.getLanguage());
        assertEquals(0, result.getBigramCount());
    }

    @Test
    void testWhitespaceOnlyReturnsNeutral() {
        TextQualityResult result = scorer.score("   \t\n  ");
        assertEquals(0.0, result.getScore(), 0.001);
        assertEquals("unk", result.getLanguage());
        assertEquals(0, result.getBigramCount());
    }

    @Test
    void testNumericOnlyReturnsNeutral() {
        TextQualityResult result = scorer.score("12345 67890 2024");
        assertEquals(0, result.getBigramCount());
    }

    @Test
    void testServiceLoaderDiscovery() {
        TextQualityScorer discovered = TextQualityScorer.getDefault();
        assertTrue(discovered instanceof BigramTextQualityScorer,
                "SPI should discover BigramTextQualityScorer");
    }

    @Test
    void testRelativeComparisonForCharset() {
        String correct = "The quick brown fox jumps over the lazy dog";
        // Caesar shift +3: simulates wrong glyph mapping (common in
        // PDFs with bad ToUnicode). NFKD normalization can't fix this.
        String garbled = "Wkh frpplwwhh uhfrpphqghg wkdw wkh djhqfb"
                + " sxuvxh dowhuqdwlyh vwudwhjlhv";

        TextQualityResult correctResult = scorer.score(correct);
        TextQualityResult garbledResult = scorer.score(garbled);

        assertTrue(correctResult.getScore() > garbledResult.getScore(),
                String.format(Locale.ROOT,
                        "Correct charset (%.4f) should score higher "
                        + "than garbled (%.4f)",
                        correctResult.getScore(),
                        garbledResult.getScore()));
    }

    @Test
    void testScoreNormalizedByLength() {
        String shortText = "The quick brown fox";
        String longText = "The quick brown fox jumps over the lazy dog. "
                + "This sentence contains more text to make it longer. "
                + "The quality scorer should normalize by length.";

        TextQualityResult shortResult = scorer.score(shortText);
        TextQualityResult longResult = scorer.score(longText);

        double diff = Math.abs(shortResult.getScore()
                - longResult.getScore());
        assertTrue(diff < 2.0,
                String.format(Locale.ROOT,
                        "Short (%.4f) and long (%.4f) English text "
                        + "should have similar per-bigram scores",
                        shortResult.getScore(),
                        longResult.getScore()));
    }

    @Test
    void testCJKBigrams() {
        // CJK ideographs: 3 internal + 2 boundary bigrams
        Map<String, Integer> bigrams =
                BigramTextQualityScorer.extractBigrams(
                        "\u4e2d\u56fd\u4eba\u6c11");
        assertEquals(5, bigrams.size());
        assertEquals(1, bigrams.get("_\u4e2d"));
        assertEquals(1, bigrams.get("\u4e2d\u56fd"));
        assertEquals(1, bigrams.get("\u56fd\u4eba"));
        assertEquals(1, bigrams.get("\u4eba\u6c11"));
        assertEquals(1, bigrams.get("\u6c11_"));
    }

    @Test
    void testConfidencePositiveForClearLanguage() {
        TextQualityResult result = scorer.score(
                "The quick brown fox jumps over the lazy dog. "
                + "This is clearly English text with enough content "
                + "to be confident about the language detection.");
        assertTrue(result.getConfidence() > 0.0,
                "Should have positive confidence for clear English");
    }
}
