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
package org.apache.tika.langdetect.charsoup.core;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;


public class CharSoupFeatureExtractorTest {

    private static final int NUM_BUCKETS = 1024;

    @Test
    public void testNullAndEmpty() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract(null);
        assertEquals(NUM_BUCKETS, counts.length);
        assertEquals(0, sum(counts));

        counts = ext.extract("");
        assertEquals(0, sum(counts));
    }

    @Test
    public void testHelloWorld() {
        // "Hello world" → lowercase → "hello world"
        // bigrams: _h, he, el, ll, lo, o_, _w, wo, or, rl, ld, d_
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("Hello world");
        assertEquals(12, sum(counts));
    }

    @Test
    public void testCJK() {
        // "你好世界" → bigrams: _你, 你好, 好世, 世界, 界_
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("你好世界");
        assertEquals(5, sum(counts));
    }

    @Test
    public void testSentinels() {
        // Single letter "a" → _a, a_ = 2 bigrams
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("a");
        assertEquals(2, sum(counts));
    }

    @Test
    public void testDigitsAndPunctuation() {
        // "ab 12 cd" → letters: a,b then c,d (separated by non-letters)
        // bigrams: _a, ab, b_, _c, cd, d_ = 6
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("ab 12 cd");
        assertEquals(6, sum(counts));
    }

    @Test
    public void testCaseFolding() {
        // "ABC" and "abc" should produce identical features
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] upper = ext.extract("ABC");
        int[] lower = ext.extract("abc");
        assertArrayEquals(upper, lower);
    }

    @Test
    public void testNFCNormalization() {
        // é as composed (U+00E9) vs decomposed (e + U+0301) should produce same features
        String composed = "\u00e9";        // é (NFC)
        String decomposed = "e\u0301";     // e + combining acute (NFD)
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] countsC = ext.extract(composed);
        int[] countsD = ext.extract(decomposed);
        assertArrayEquals(countsC, countsD);
    }

    @Test
    public void testUrlMailStrippingMatchesGreedyRegexReference() {
        // TIKA-4875: the scanners must stay byte-identical to the regexes they replaced --
        // the langdetect and junkdetect models were trained on this exact preprocessing.
        // Inputs are NFC-stable, so preprocessNoTruncate's NFC step is an identity here.
        Pattern greedyUrl = Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]{10,10000}");
        Pattern greedyMail = Pattern.compile("[-_.0-9A-Za-z]{1,100}@[-_0-9A-Za-z]{1,100}[-_.0-9A-Za-z]{1,100}");

        List<String> cases = new ArrayList<>();
        // middle mail repeat must backtrack to feed the dot-class tail
        cases.add("a@bb");
        cases.add("a@b");
        cases.add("a@.b");
        cases.add("a@b..c");
        cases.add("a@" + "b".repeat(250));
        cases.add("a".repeat(150) + "@x.y");
        cases.add("a".repeat(200) + "@");
        cases.add("local@local@local");
        cases.add("x@y.z a@bb user@example.com");
        cases.add("@@@@@@");
        cases.add("a@a@a@a@a@a@");
        // mail caps: head {1,100} then tail {1,100} over one long run
        cases.add("a@" + "b".repeat(100) + "." + "c".repeat(150));
        cases.add("a@" + "b".repeat(99) + "." + "c".repeat(99));
        cases.add("a@" + "b".repeat(300));
        // find() resumes after a match: leftover run chars are not a fresh local part
        cases.add("aa@bb cc@dd ee@ff");
        cases.add("a@bb@cc@dd");
        // URL length boundaries: min 10 after scheme, cap 10000
        cases.add("http://" + "a".repeat(9));
        cases.add("http://" + "a".repeat(10));
        cases.add("https://" + "a".repeat(10000));
        cases.add("https://" + "a".repeat(10001));
        cases.add("https://" + "a".repeat(10005) + "@bb");
        cases.add("http://http://aaaaaaaaaa");
        cases.add("http://aaaahttp://bbbbbbbbbb");
        cases.add("hhttp://aaaaaaaaaaa");
        cases.add("http:/notaurl http//nope https:/x");
        // the URL pass runs first; its replacement is a barrier for the mail pass
        cases.add("http://aaaaaaaaaa@bb");
        cases.add("a@http://aaaaaaaaaa");
        cases.add("a@httpx.y");
        cases.add("user@example.com/http://foobarbazqux");
        // non-ASCII neighbors exercise the < 128 guards
        cases.add("é@bb");
        cases.add("aé@bb");
        cases.add("a@büc.d");
        cases.add("http://aéaaaaaaaaaa");
        cases.add("see http://example.com/a/b?q=1#f and mail bob.smith@sub-domain_x.example.org.");

        long seed = new Random().nextLong();
        Random random = new Random(seed);
        String[] atoms = {"a", "B", "9", ".", "-", "_", "@", ":", "/", "#", "?", "=", " ",
                "http://", "https://", "http", "://", "@a.", "é", "aaaaaaaaaa"};
        for (int i = 0; i < 5000; i++) {
            int len = 1 + random.nextInt(60);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) {
                sb.append(atoms[random.nextInt(atoms.length)]);
            }
            cases.add(sb.toString());
        }
        // long-run shapes that hit the 100/10000 caps
        for (int i = 0; i < 50; i++) {
            StringBuilder sb = new StringBuilder();
            while (sb.length() < 3000) {
                sb.append("a".repeat(1 + random.nextInt(400)));
                sb.append(atoms[random.nextInt(atoms.length)]);
            }
            cases.add(sb.toString());
        }

        for (String text : cases) {
            String expected = greedyMail
                    .matcher(greedyUrl.matcher(text).replaceAll(" "))
                    .replaceAll(" ");
            String actual = CharSoupFeatureExtractor.preprocessNoTruncate(text);
            assertEquals(expected, actual, "seed=" + seed + " input=" + text);
        }
    }

    @Test
    public void testURLStripping() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] withUrl = ext.extract("hello https://example.com/path world");
        int[] withoutUrl = ext.extract("hello  world");
        assertArrayEquals(withUrl, withoutUrl);
    }

    @Test
    public void testEmailStripping() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] withEmail = ext.extract("hello user@example.com world");
        int[] withoutEmail = ext.extract("hello  world");
        assertArrayEquals(withEmail, withoutEmail);
    }

    @Test
    public void testSurrogatePairs() {
        // 𝐀 (U+1D400, Mathematical Bold Capital A) — supplementary char
        String text = "\uD835\uDC00"; // U+1D400
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract(text);
        // Single letter → _X, X_ = 2 bigrams
        assertEquals(2, sum(counts));
    }

    @Test
    public void testFNVDeterminism() {
        // Same input → same hash, always
        int h1 = CharSoupFeatureExtractor.hashBigram('a', 'b');
        int h2 = CharSoupFeatureExtractor.hashBigram('a', 'b');
        assertEquals(h1, h2);

        // Different input → different hash (with overwhelming probability)
        int h3 = CharSoupFeatureExtractor.hashBigram('b', 'a');
        assertNotEquals(h1, h3);
    }

    @Test
    public void testFNVDistribution() {
        // Check that FNV-1a distributes across buckets reasonably
        int numBuckets = 256;
        int[] bucketHits = new int[numBuckets];
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(numBuckets);
        for (int cp1 = 'a'; cp1 <= 'z'; cp1++) {
            for (int cp2 = 'a'; cp2 <= 'z'; cp2++) {
                int bucket = ext.bucketIndex(cp1, cp2);
                assertTrue(bucket >= 0 && bucket < numBuckets);
                bucketHits[bucket]++;
            }
        }
        // 676 bigrams across 256 buckets → average ~2.6 per bucket
        // At least 200 buckets should be hit (no extreme clustering)
        int nonEmpty = 0;
        for (int h : bucketHits) {
            if (h > 0) nonEmpty++;
        }
        assertTrue(nonEmpty > 200, "FNV should distribute well: " + nonEmpty + " buckets hit");
    }

    @Test
    public void testTruncation() {
        // Text longer than MAX_TEXT_LENGTH should be truncated
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CharSoupFeatureExtractor.MAX_TEXT_LENGTH + 1000; i++) {
            sb.append('a');
        }
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // Should not throw
        int[] counts = ext.extract(sb.toString());
        assertTrue(sum(counts) > 0);
    }

    @Test
    public void testOnlyNonLetters() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        int[] counts = ext.extract("12345 !@#$%");
        assertEquals(0, sum(counts));
    }

    // --- Transparent character tests ---

    @Test
    public void testArabicDiacriticsTransparent() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // كَتَبَ (kaf + fatha + ta + fatha + ba + fatha)
        String withDiacritics = "\u0643\u064E\u062A\u064E\u0628\u064E";
        // كتب (kaf + ta + ba) — same word, no diacritics
        String withoutDiacritics = "\u0643\u062A\u0628";
        int[] countsWith = ext.extract(withDiacritics);
        int[] countsWithout = ext.extract(withoutDiacritics);
        assertArrayEquals(countsWithout, countsWith,
                "Arabic diacritics should be transparent — bigrams must be identical");
    }

    @Test
    public void testArabicShadda() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // علَّم (ain + lam + shadda + fatha + mim)
        String withShadda = "\u0639\u0644\u0651\u064E\u0645";
        // علم (ain + lam + mim)
        String withoutShadda = "\u0639\u0644\u0645";
        int[] countsWith = ext.extract(withShadda);
        int[] countsWithout = ext.extract(withoutShadda);
        assertArrayEquals(countsWithout, countsWith,
                "Shadda (gemination mark) should be transparent");
    }

    @Test
    public void testArabicTatweel() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // كـتـب (kaf + tatweel + ta + tatweel + ba)
        String withTatweel = "\u0643\u0640\u062A\u0640\u0628";
        // كتب (kaf + ta + ba)
        String withoutTatweel = "\u0643\u062A\u0628";
        int[] countsWith = ext.extract(withTatweel);
        int[] countsWithout = ext.extract(withoutTatweel);
        assertArrayEquals(countsWithout, countsWith,
                "Tatweel (kashida) should be transparent — bigrams must be identical");
    }

    @Test
    public void testHebrewNiqqud() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // שָׁלוֹם (shin + qamats + lamed + vav + holam + mem)
        String withNiqqud = "\u05E9\u05B8\u05C1\u05DC\u05D5\u05B9\u05DD";
        // שלום (shin + lamed + vav + mem) — no niqqud
        String withoutNiqqud = "\u05E9\u05DC\u05D5\u05DD";
        int[] countsWith = ext.extract(withNiqqud);
        int[] countsWithout = ext.extract(withoutNiqqud);
        assertArrayEquals(countsWithout, countsWith,
                "Hebrew niqqud should be transparent — bigrams must be identical");
    }

    @Test
    public void testZWNJTransparent() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // Persian: می‌خواهم (mi + ZWNJ + khaaham) — ZWNJ is not a word boundary
        String withZWNJ = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645";
        // Same without ZWNJ
        String withoutZWNJ = "\u0645\u06CC\u062E\u0648\u0627\u0647\u0645";
        int[] countsWith = ext.extract(withZWNJ);
        int[] countsWithout = ext.extract(withoutZWNJ);
        assertArrayEquals(countsWithout, countsWith,
                "ZWNJ should be transparent — bigrams must span across it");
    }

    @Test
    public void testArabicVsArabicReversed() {
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        // كتب → bigrams: _ك, كت, تب, ب_
        String normal = "\u0643\u062A\u0628";
        // بتك (reversed) → bigrams: _ب, بت, تك, ك_
        String reversed = "\u0628\u062A\u0643";
        int[] countsNormal = ext.extract(normal);
        int[] countsReversed = ext.extract(reversed);
        // These must differ — reversed text produces different bigram distributions
        assertNotEquals(sum(countsNormal), 0);
        assertNotEquals(sum(countsReversed), 0);
        // Bigram counts should be equal (same number of bigrams) but in different buckets
        assertEquals(sum(countsNormal), sum(countsReversed));
        boolean differ = false;
        for (int j = 0; j < NUM_BUCKETS; j++) {
            if (countsNormal[j] != countsReversed[j]) {
                differ = true;
                break;
            }
        }
        assertTrue(differ, "Normal and reversed Arabic must produce different bigram distributions");
    }

    @Test
    public void testIsTransparent() {
        // Arabic harakat
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x064E)); // fatha
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x064F)); // damma
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x0650)); // kasra
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x0651)); // shadda
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x0652)); // sukun
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x0670)); // superscript alef
        // Hebrew niqqud
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x05B0)); // sheva
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x05B4)); // hiriq
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x05C1)); // shin dot
        // Special characters
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x0640)); // tatweel
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x200C)); // ZWNJ
        assertTrue(CharSoupFeatureExtractor.isTransparent(0x200D)); // ZWJ
        // Not transparent
        assertFalse(CharSoupFeatureExtractor.isTransparent('a'));
        assertFalse(CharSoupFeatureExtractor.isTransparent('5'));
        assertFalse(CharSoupFeatureExtractor.isTransparent(' '));
        assertFalse(CharSoupFeatureExtractor.isTransparent(0x0643)); // Arabic kaf — a letter
    }

    // ---- Trigram tests ----

    @Test
    public void testTrigramsProduceMoreFeatures() {
        // "hello" → bigrams: _h, he, el, ll, lo, o_ = 6
        //         + trigrams: _he, hel, ell, llo, lo_, = 5
        //         = 11 total
        CharSoupFeatureExtractor biOnly = new CharSoupFeatureExtractor(NUM_BUCKETS, false);
        CharSoupFeatureExtractor biTri = new CharSoupFeatureExtractor(NUM_BUCKETS, true);

        int[] countsBi = biOnly.extract("hello");
        int[] countsBiTri = biTri.extract("hello");

        assertEquals(6, sum(countsBi));
        assertEquals(11, sum(countsBiTri));
    }

    @Test
    public void testTrigramsSingleWord() {
        // "ab" → bigrams: _a, ab, b_ = 3
        //       + trigrams: _ab, ab_ = 2
        //       = 5 total
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS, true);
        int[] counts = ext.extract("ab");
        assertEquals(5, sum(counts));
    }

    @Test
    public void testTrigramsSingleChar() {
        // "a" → bigrams: _a, a_ = 2
        //       trigrams: _a_ would need prevPrevCp set... let's check
        //       After _a: prevPrevCp=SENTINEL, prevCp=a. End-of-text trigram: (SENTINEL,a,SENTINEL)
        //       = 1 trigram
        //       = 3 total
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS, true);
        int[] counts = ext.extract("a");
        assertEquals(3, sum(counts));
    }

    @Test
    public void testTrigramsTwoWords() {
        // "ab cd" → bigrams: _a, ab, b_, _c, cd, d_ = 6
        //         + trigrams: _ab, ab_, _cd, cd_ = 4
        //         = 10 total
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS, true);
        int[] counts = ext.extract("ab cd");
        assertEquals(10, sum(counts));
    }

    @Test
    public void testTrigramsNoLetters() {
        // No letters → 0 features regardless of trigram mode
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS, true);
        int[] counts = ext.extract("12345");
        assertEquals(0, sum(counts));
    }

    @Test
    public void testTrigramsDisabledByDefault() {
        // Verify default constructor doesn't include trigrams
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        assertFalse(ext.isIncludeTrigrams());

        CharSoupFeatureExtractor extTri = new CharSoupFeatureExtractor(NUM_BUCKETS, true);
        assertTrue(extTri.isIncludeTrigrams());
    }

    @Test
    public void testTrigramHashesDifferFromBigrams() {
        // Verify that hashTrigram produces different values than hashBigram
        // for overlapping inputs (avoids systematic collisions)
        int biHash = CharSoupFeatureExtractor.hashBigram('a', 'b');
        int triHash = CharSoupFeatureExtractor.hashTrigram('a', 'b', 'c');
        assertNotEquals(biHash, triHash);
    }

    // ---- Accumulation tests (clear=false mode for training loops) ----

    @Test
    public void testAccumulateNoClear() {
        // When clear=false, extractFromPreprocessed should add to existing counts
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        String preprocessed = CharSoupFeatureExtractor.preprocess("hello world");

        int[] counts = new int[NUM_BUCKETS];
        ext.extractFromPreprocessed(preprocessed, counts, false);
        int firstSum = sum(counts);
        assertEquals(12, firstSum);

        // Extract again without clearing — counts should double
        ext.extractFromPreprocessed(preprocessed, counts, false);
        assertEquals(24, sum(counts));
    }

    @Test
    public void testAccumulateWithClear() {
        // When clear=true, extractFromPreprocessed should zero before extracting
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        String preprocessed = CharSoupFeatureExtractor.preprocess("hello world");

        int[] counts = new int[NUM_BUCKETS];
        ext.extractFromPreprocessed(preprocessed, counts, false);
        assertEquals(12, sum(counts));

        // Extract with clear — should be back to 12, not 24
        ext.extractFromPreprocessed(preprocessed, counts, true);
        assertEquals(12, sum(counts));
    }

    // ---- Growing-prefix invariant test ----

    @Test
    public void testGrowingPrefixProducesMoreFeatures() {
        // A larger prefix of text should produce >= bigram features than a
        // smaller prefix. This monotonicity property ensures that longer
        // text chunks always give the model at least as much signal.
        CharSoupFeatureExtractor ext = new CharSoupFeatureExtractor(NUM_BUCKETS);
        String text = "The quick brown fox jumps over the lazy dog near the river";
        String preprocessed = CharSoupFeatureExtractor.preprocess(text);

        int prevSum = 0;
        for (int prefixLen = 10; prefixLen <= preprocessed.length();
                prefixLen = Math.min(prefixLen * 2, preprocessed.length())) {
            String prefix = preprocessed.substring(0, prefixLen);
            int[] counts = ext.extractFromPreprocessed(prefix);
            int currentSum = sum(counts);
            assertTrue(currentSum >= prevSum,
                    "Prefix of " + prefixLen + " chars should produce >= features "
                    + "than shorter prefix: " + currentSum + " vs " + prevSum);
            prevSum = currentSum;
            if (prefixLen == preprocessed.length()) {
                break;
            }
        }
    }

    @Test
    public void testPreprocessIdempotent() {
        // Preprocessing the same text twice must produce identical output.
        // Idempotency is critical for deterministic detection results.
        String raw = "Hello world! Visit https://example.com for more.";
        String pp1 = CharSoupFeatureExtractor.preprocess(raw.substring(0, 20));
        String pp2 = CharSoupFeatureExtractor.preprocess(raw.substring(0, 20));
        assertEquals(pp1, pp2, "Preprocessing must be idempotent");
    }

    private int sum(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
}
