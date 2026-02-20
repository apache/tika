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
package org.apache.tika.langdetect.charsoup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class ScriptAwareFeatureExtractorTest {

    private static final int NUM_BUCKETS = 8192;

    // ---- Basic sanity ----

    @Test
    public void testEmptyAndNull() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract(null);
        assertEquals(NUM_BUCKETS, counts.length);
        assertEquals(0, sum(counts));

        counts = ext.extract("");
        assertEquals(0, sum(counts));
    }

    @Test
    public void testSingleWord() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("hello");
        // "hello":
        // bigrams: (_,h) (h,e) (e,l) (l,l) (l,o) (o,_) = 6
        // word unigram: "hello" = 1
        // total = 7
        assertEquals(7, sum(counts));
    }

    @Test
    public void testCjkUnigrams() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "中文": no sentinels for CJK
        // bigrams: (中,文) = 1
        // unigrams: 中, 文 = 2
        // total = 3
        int[] counts = ext.extract("中文");
        assertEquals(3, sum(counts));
    }

    @Test
    public void testHiraganaUnigrams() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "あい": no sentinels for kana
        // bigrams: (あ,い) = 1
        // unigrams: あ, い = 2
        // total = 3
        int[] counts = ext.extract("あい");
        assertEquals(3, sum(counts));
    }

    @Test
    public void testKatakanaUnigrams() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "アイ": same as hiragana
        int[] counts = ext.extract("アイ");
        assertEquals(3, sum(counts));
    }

    // ---- CJK space bridging ----

    @Test
    public void testCjkSpaceBridging() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "中 文" with space should produce same features as "中文"
        // The space is bridged for CJK
        int[] withSpace = ext.extract("中 文");
        int[] noSpace = ext.extract("中文");
        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(noSpace[i], withSpace[i],
                    "CJK space bridging: bucket " + i);
        }
    }

    @Test
    public void testCjkPunctuationBreaks() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "中。文" — punctuation IS a real break
        int[] withPunct = ext.extract("中。文");
        int[] noSpace = ext.extract("中文");
        // Should differ: punctuation breaks the bigram chain
        boolean differ = false;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            if (withPunct[i] != noSpace[i]) {
                differ = true;
                break;
            }
        }
        assertTrue(differ,
                "Punctuation should break CJK bigram chain");
    }

    // ---- Script isolation ----

    @Test
    public void testLatinAndCyrillicDontCollide() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        int[] latin = ext.extract("ab");
        int[] cyrillic = ext.extract("аб");
        assertNotEquals(0, sum(latin));
        assertNotEquals(0, sum(cyrillic));
        boolean differ = false;
        for (int i = 0; i < NUM_BUCKETS; i++) {
            if (latin[i] != cyrillic[i]) {
                differ = true;
                break;
            }
        }
        assertTrue(differ,
                "Latin and Cyrillic features should hash differently");
    }

    // ---- Japanese script family (no boundary) ----

    @Test
    public void testJapaneseScriptFamilyNoBoundary() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "漢あア" — Han + Hiragana + Katakana
        // All are CJK family, so no boundary between them.
        // bigrams: (漢,あ) (あ,ア) = 2
        // unigrams: 漢, あ, ア = 3
        // total = 5
        int[] counts = ext.extract("漢あア");
        assertEquals(5, sum(counts));
    }

    @Test
    public void testJapaneseVsLatinCreatesBoundary() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "漢a" — Han then Latin: different family → boundary
        // Han part: (漢) = 1 unigram (no sentinels for CJK)
        // Latin part: (_,a) (a,_) = 2 bigrams (sentinels)
        // total = 3
        int[] counts = ext.extract("漢a");
        assertEquals(3, sum(counts));
    }

    @Test
    public void testHanHiraganaBigramChain() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "食べる" — Han(食) Hiragana(べ) Hiragana(る)
        // bigrams: (食,べ) (べ,る) = 2
        // unigrams: 食, べ, る = 3
        // total = 5
        int[] counts = ext.extract("食べる");
        assertEquals(5, sum(counts));
    }

    // ---- Script change boundaries ----

    @Test
    public void testScriptChangeCreatesBoundary() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "abаб" — Latin "ab" followed by Cyrillic "аб"
        int[] mixed = ext.extract("abаб");

        int[] separate = new int[NUM_BUCKETS];
        int[] latin = ext.extract("ab");
        int[] cyrillic = ext.extract("аб");
        for (int i = 0; i < NUM_BUCKETS; i++) {
            separate[i] = latin[i] + cyrillic[i];
        }

        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(separate[i], mixed[i],
                    "Script change = word boundary at bucket " + i);
        }
    }

    // ---- Word unigrams ----

    @Test
    public void testWordUnigrams() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "abc":
        // bigrams: (_,a) (a,b) (b,c) (c,_) = 4
        // word unigram: "abc" = 1
        // total = 5
        int[] counts = ext.extract("abc");
        assertEquals(5, sum(counts));
    }

    @Test
    public void testSingleCharWordNoWordUnigram() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        // "a" — single char word, below MIN_WORD_LENGTH
        // bigrams: (_,a) (a,_) = 2
        // word unigram: skipped (len < 2)
        // total = 2
        int[] counts = ext.extract("a");
        assertEquals(2, sum(counts));
    }

    // ---- Transparent characters ----

    @Test
    public void testArabicDiacriticsTransparent() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        int[] plain = ext.extract("كتب");
        int[] diacritics = ext.extract("كَتَبَ");
        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(plain[i], diacritics[i],
                    "Diacritics should be transparent");
        }
    }

    // ---- Preprocessing reuse ----

    @Test
    public void testExtractFromPreprocessed() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        String raw = "Hello https://example.com world";
        String preprocessed =
                CharSoupFeatureExtractor.preprocess(raw);
        int[] fromRaw = ext.extract(raw);
        int[] fromPreprocessed =
                ext.extractFromPreprocessed(preprocessed);
        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(fromRaw[i], fromPreprocessed[i]);
        }
    }

    @Test
    public void testExtractFromPreprocessedAccumulate() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("hello");
        int sum1 = sum(counts);
        String preprocessed =
                CharSoupFeatureExtractor.preprocess("world");
        ext.extractFromPreprocessed(preprocessed, counts, false);
        int sum2 = sum(counts);
        assertTrue(sum2 > sum1,
                "Accumulated counts should be larger");
    }

    // ---- ScriptCategory ----

    @Test
    public void testScriptCategoryAscii() {
        assertEquals(ScriptCategory.LATIN, ScriptCategory.of('a'));
        assertEquals(ScriptCategory.LATIN, ScriptCategory.of('z'));
        assertEquals(ScriptCategory.LATIN, ScriptCategory.of('A'));
    }

    @Test
    public void testScriptCategoryNonLatin() {
        assertEquals(ScriptCategory.CYRILLIC, ScriptCategory.of('а'));
        assertEquals(ScriptCategory.ARABIC, ScriptCategory.of('ع'));
        assertEquals(ScriptCategory.HAN, ScriptCategory.of('中'));
        assertEquals(ScriptCategory.HIRAGANA, ScriptCategory.of('あ'));
        assertEquals(ScriptCategory.KATAKANA, ScriptCategory.of('ア'));
        assertEquals(ScriptCategory.HANGUL, ScriptCategory.of('한'));
        assertEquals(ScriptCategory.DEVANAGARI, ScriptCategory.of('क'));
        assertEquals(ScriptCategory.THAI, ScriptCategory.of('ก'));
        assertEquals(ScriptCategory.GREEK, ScriptCategory.of('α'));
        assertEquals(ScriptCategory.HEBREW, ScriptCategory.of('א'));
        assertEquals(ScriptCategory.BENGALI, ScriptCategory.of('ক'));
        assertEquals(ScriptCategory.GEORGIAN, ScriptCategory.of('ა'));
        assertEquals(ScriptCategory.ARMENIAN, ScriptCategory.of('ա'));
        assertEquals(ScriptCategory.ETHIOPIC, ScriptCategory.of('ሀ'));
    }

    // ---- Randomized fuzz test ----

    @RepeatedTest(50)
    public void testRandomInputNoInfiniteLoopOrCrash() {
        Random rng = new Random();
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(
                        1024 + rng.nextInt(32768));

        int len = rng.nextInt(5000);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int kind = rng.nextInt(10);
            int cp;
            switch (kind) {
                case 0:
                    cp = rng.nextInt(128);
                    break;
                case 1:
                    cp = 0x00C0 + rng.nextInt(0x0180);
                    break;
                case 2:
                    cp = 0x0400 + rng.nextInt(0x0100);
                    break;
                case 3:
                    cp = 0x0600 + rng.nextInt(0x0100);
                    break;
                case 4:
                    cp = 0x4E00 + rng.nextInt(0x5000);
                    break;
                case 5:
                    cp = 0xAC00 + rng.nextInt(0x2BA4);
                    break;
                case 6:
                    cp = 0x10000 + rng.nextInt(0x10000);
                    break;
                case 7:
                    cp = rng.nextInt(32);
                    break;
                case 8:
                    cp = 0x0900 + rng.nextInt(0x0080);
                    break;
                default:
                    cp = rng.nextInt(0xD800);
                    break;
            }
            sb.appendCodePoint(cp);
        }

        int[] counts = ext.extract(sb.toString());
        assertEquals(ext.getNumBuckets(), counts.length);
        for (int c : counts) {
            assertTrue(c >= 0,
                    "Bucket count must be non-negative");
        }
        int total = sum(counts);
        assertTrue(total <= len * 4 + 10,
                "Total features (" + total
                        + ") too high for length " + len);
    }

    @RepeatedTest(10)
    public void testRandomSurrogatePairsAndEdgeCases() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);

        String[] pathological = {
                new String(new char[]{0xD800, 0xD801, 0xD802}),
                "\u064E\u064F\u0650\u0651\u0652",
                "   123   456   ",
                "aаbбcвdг",
                "x",
                "a".repeat(10000),
                "hello мир 世界 한국 あい アイ",
                "https://example.com https://test.org",
                "\0\0\0",
                "hello \uD83D\uDE00 world 你好",
        };

        for (String input : pathological) {
            int[] counts = ext.extract(input);
            assertEquals(NUM_BUCKETS, counts.length);
            for (int c : counts) {
                assertTrue(c >= 0,
                        "Negative count on: "
                                + input.substring(0,
                                Math.min(20, input.length())));
            }
        }
    }

    // ---- Determinism ----

    @Test
    public void testDeterministic() {
        ScriptAwareFeatureExtractor ext =
                new ScriptAwareFeatureExtractor(NUM_BUCKETS);
        String text =
                "The quick brown fox 快速的棕色狐狸 прыгнул через";
        int[] first = ext.extract(text);
        int[] second = ext.extract(text);
        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(first[i], second[i],
                    "Must be deterministic");
        }
    }

    // ---- Helpers ----

    private int sum(int[] arr) {
        int s = 0;
        for (int v : arr) {
            s += v;
        }
        return s;
    }
}
