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

import org.junit.jupiter.api.Test;

import org.apache.tika.ml.junkdetect.TextQualityFeatures.StripMode;

class TextQualityFeaturesTest {

    @Test
    void alphabeticRatio_polishCorrectVsMojibake() {
        // Correct decode: every codepoint is a letter
        assertEquals(1.0, TextQualityFeatures.alphabeticRatio("ciśnienia"), 1e-9);
        // Wrong decode: pilcrow replaces ś, so 8/9 letters
        assertEquals(8.0 / 9.0, TextQualityFeatures.alphabeticRatio("ci¶nienia"), 1e-9);
    }

    @Test
    void letterPairDensity_polishCorrectVsMojibake() {
        // ciśnienia: 8 adjacent pairs, all (letter, letter, same-cluster)
        assertEquals(1.0, TextQualityFeatures.letterPairDensity("ciśnienia"), 1e-9);
        // ci¶nienia: pairs (i,¶) and (¶,n) fail → 6/8 = 0.75
        assertEquals(0.75, TextQualityFeatures.letterPairDensity("ci¶nienia"), 1e-9);
    }

    @Test
    void letterPairDensity_mixedScriptToken() {
        // Latin + Cyrillic + Greek in one "word" → none of the adjacent
        // letter pairs are same-cluster.
        // h(Latin) e(Latin) l(Latin) l(Latin) о(Cyr) α(Greek)
        // pairs: (h,e) Latin-Latin same; (e,l) same; (l,l) same;
        //        (l,о) Latin-Cyr different; (о,α) Cyr-Greek different.
        // 3/5 same cluster.
        assertEquals(3.0 / 5.0, TextQualityFeatures.letterPairDensity("hellоα"), 1e-9);
    }

    @Test
    void letterPairDensity_cjkClusterGroupsKana() {
        // 私は学生です — HAN, HIRAGANA, HAN, HAN, HIRAGANA, HIRAGANA.
        // All in the CJK cluster, all letters → 1.0
        assertEquals(1.0, TextQualityFeatures.letterPairDensity("私は学生です"), 1e-9);
    }

    @Test
    void replacementCount_countsUFFFD() {
        assertEquals(0, TextQualityFeatures.replacementCount("hello"));
        assertEquals(2, TextQualityFeatures.replacementCount("he�ll�o"));
    }

    @Test
    void highByteEntropy_zeroWhenAllAscii() {
        assertEquals(0.0, TextQualityFeatures.highByteEntropy("hello world"), 1e-9);
    }

    @Test
    void highByteEntropy_higherForFannedOutMojibake() {
        // Realistic CJK-as-Latin1 mojibake fans out across many high bytes
        String mojibake = "Ã¦ Ã« Ã¬ Ã­ Ã® Ã¯ Ã° Ã±";
        // Polish text uses a small set of high-byte letters repeatedly
        String polish = "ciśnienia ciśnienia ciśnienia ciśnienia";
        assertTrue(TextQualityFeatures.highByteEntropy(mojibake)
                > TextQualityFeatures.highByteEntropy(polish),
                "mojibake should have higher high-byte entropy than repeated Polish word");
    }

    @Test
    void perWordScriptPurity_mixedScriptTokenScoresLow() {
        // Two clean words + one mixed word → 2/3 pure
        assertEquals(2.0 / 3.0,
                TextQualityFeatures.perWordScriptPurity("hello world hellоα"),
                1e-9);
    }

    @Test
    void perWordScriptPurity_allCleanWords() {
        assertEquals(1.0,
                TextQualityFeatures.perWordScriptPurity("hello world foo bar"),
                1e-9);
    }

    @Test
    void strip_noneIsIdentity() {
        String s = "hello world ¶ ś";
        assertEquals(s, TextQualityFeatures.strip(s, StripMode.NONE));
    }

    @Test
    void strip_whitespaceKeepsPunctuation() {
        assertEquals("hello¶world",
                TextQualityFeatures.strip("hello ¶ world", StripMode.WHITESPACE));
        assertEquals("aś!b",
                TextQualityFeatures.strip("a\tś!\nb", StripMode.WHITESPACE));
    }

    @Test
    void strip_whitespaceControlAlsoRemovesControls() {
        //  is a CONTROL char; ¶ should survive
        assertEquals("hello¶world",
                TextQualityFeatures.strip("hello ¶world",
                        StripMode.WHITESPACE_CONTROL));
    }

    @Test
    void combiningMarkRatio_vietnameseVsMojibake() {
        // Vietnamese "Vẻ" written as V + e + combining-hook (U+0309) → 2/3 letters, 1/3 mark
        String vietnamese = "Vẻ";
        assertEquals(1.0 / 3.0,
                TextQualityFeatures.combiningMarkRatio(vietnamese), 1e-9);
        // Latin-1 mojibake form "VeÒ" has no combining marks
        assertEquals(0.0,
                TextQualityFeatures.combiningMarkRatio("VeÒ"), 1e-9);
    }

    @Test
    void letterAdjacentToMarkRatio_vietnameseDecoration() {
        // V + e + ̉  → pairs (V,e) no, (e,mark) yes → 1/2
        assertEquals(0.5,
                TextQualityFeatures.letterAdjacentToMarkRatio("Vẻ"),
                1e-9);
        // No marks → 0
        assertEquals(0.0,
                TextQualityFeatures.letterAdjacentToMarkRatio("hello world"),
                1e-9);
    }

    @Test
    void scriptDensity_allCommonScoresZero() {
        assertEquals(0.0, TextQualityFeatures.scriptDensity("   \t\n"), 1e-9);
        assertEquals(0.0, TextQualityFeatures.scriptDensity("12345 67890"), 1e-9);
        assertEquals(0.0, TextQualityFeatures.scriptDensity("!@#$%^&*()"), 1e-9);
    }

    @Test
    void scriptDensity_pureScriptScoresOne() {
        assertEquals(1.0, TextQualityFeatures.scriptDensity("hello"), 1e-9);
        assertEquals(1.0, TextQualityFeatures.scriptDensity("ciśnienia"), 1e-9);
        assertEquals(1.0, TextQualityFeatures.scriptDensity("私は学生です"), 1e-9);
    }

    @Test
    void scriptDensity_mixedTextScoresPartial() {
        // "hi 5" → h,i (LATIN), ' ' (COMMON), 5 (COMMON) → 2/4
        assertEquals(0.5, TextQualityFeatures.scriptDensity("hi 5"), 1e-9);
    }

    @Test
    void scriptFragmentation_singleScriptScoresZero() {
        assertEquals(0.0, TextQualityFeatures.scriptFragmentation("hello"), 1e-9);
        assertEquals(0.0, TextQualityFeatures.scriptFragmentation("ciśnienia"), 1e-9);
        // COMMON codepoints don't count
        assertEquals(0.0, TextQualityFeatures.scriptFragmentation("hello world"), 1e-9);
    }

    @Test
    void scriptFragmentation_noScriptedContentScoresZero() {
        assertEquals(0.0, TextQualityFeatures.scriptFragmentation("   12345"), 1e-9);
    }

    @Test
    void scriptFragmentation_scriptSaladHigh() {
        // Mixed LATIN + CYRILLIC + GREEK + HEBREW, each single codepoint
        // → 4 scripted codepoints, longest run = 1, fragmentation = 0.75
        assertEquals(0.75,
                TextQualityFeatures.scriptFragmentation("aбαא"), 1e-9);
    }

    @Test
    void scriptFragmentation_mostlyOneScriptLowFragmentation() {
        // "helloбя" → 5 LATIN + 2 CYR.  longest_run=5, total=7 → 1 - 5/7 ≈ 0.286
        assertEquals(1.0 - 5.0 / 7.0,
                TextQualityFeatures.scriptFragmentation("helloбя"), 1e-9);
    }

    @Test
    void strip_allCommonMatchesProductionBehaviour() {
        // ALL_COMMON should drop ¶ (which is COMMON-script punctuation), space, and !
        assertEquals("helloworld",
                TextQualityFeatures.strip("hello ¶ world!", StripMode.ALL_COMMON));
        // But should keep script-bearing letters like ś (Latin)
        assertEquals("helśloworld",
                TextQualityFeatures.strip("helś lo ¶ world", StripMode.ALL_COMMON));
    }
}
