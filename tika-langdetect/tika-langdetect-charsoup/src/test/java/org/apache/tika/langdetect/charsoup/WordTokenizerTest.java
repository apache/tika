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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class WordTokenizerTest {

    @Test
    public void testSimpleEnglish() {
        List<String> tokens = WordTokenizer.tokenize("Hello World");
        assertEquals(List.of("hello", "world"), tokens);
    }

    @Test
    public void testPunctuationSplitting() {
        List<String> tokens = WordTokenizer.tokenize("hello, world! foo-bar");
        assertEquals(List.of("hello", "world", "foo", "bar"), tokens);
    }

    @Test
    public void testCaseFolding() {
        List<String> tokens = WordTokenizer.tokenize("ABC DEF");
        assertEquals(List.of("abc", "def"), tokens);
    }

    @Test
    public void testCJKBigrams() {
        // "你好世界" → bigrams: 你好, 好世, 世界
        List<String> tokens = WordTokenizer.tokenize("你好世界");
        assertEquals(3, tokens.size());
        assertEquals("你好", tokens.get(0));
        assertEquals("好世", tokens.get(1));
        assertEquals("世界", tokens.get(2));
    }

    @Test
    public void testMixedAlphaAndCJK() {
        // "hello你好world" → "hello", 你好, "world"
        List<String> tokens = WordTokenizer.tokenize("hello你好world");
        assertEquals(3, tokens.size());
        assertEquals("hello", tokens.get(0));
        assertEquals("你好", tokens.get(1));
        assertEquals("world", tokens.get(2));
    }

    @Test
    public void testNullAndEmpty() {
        assertTrue(WordTokenizer.tokenize(null).isEmpty());
        assertTrue(WordTokenizer.tokenize("").isEmpty());
    }

    @Test
    public void testURLStripping() {
        List<String> tokens = WordTokenizer.tokenize("visit https://example.com/page today");
        assertEquals(List.of("visit", "today"), tokens);
    }

    @Test
    public void testNFCNormalization() {
        // é composed vs decomposed should produce same tokens
        String composed = "caf\u00e9";
        String decomposed = "cafe\u0301";
        assertEquals(WordTokenizer.tokenize(composed), WordTokenizer.tokenize(decomposed));
    }

    @Test
    public void testSingleCJKChar() {
        // A single ideographic character has no pair → no bigram emitted
        List<String> tokens = WordTokenizer.tokenize("中");
        assertTrue(tokens.isEmpty());
    }

    @Test
    public void testDigitsIgnored() {
        List<String> tokens = WordTokenizer.tokenize("abc 123 def");
        assertEquals(List.of("abc", "def"), tokens);
    }

    // --- tokenizeAlphanumeric tests ---

    @Test
    public void testAlphanumericIncludesNumbers() {
        List<String> tokens = new ArrayList<>();
        WordTokenizer.tokenizeAlphanumeric("abc 123 def", tokens::add);
        assertEquals(List.of("abc", "123", "def"), tokens);
    }

    @Test
    public void testAlphanumericMixed() {
        List<String> tokens = new ArrayList<>();
        WordTokenizer.tokenizeAlphanumeric("hello42world", tokens::add);
        // "hello", "42", "world" — digit run breaks the word
        assertEquals(List.of("hello", "42", "world"), tokens);
    }

    @Test
    public void testAlphanumericPunctuation() {
        List<String> tokens = new ArrayList<>();
        WordTokenizer.tokenizeAlphanumeric("price: $99, qty 5!", tokens::add);
        assertEquals(List.of("price", "99", "qty", "5"), tokens);
    }

    @Test
    public void testAlphanumericCJK() {
        // Ideographic bigrams still work, numbers still emitted
        // "你好123世界" → 你好 (bigram), 123 (digits), 世界 (bigram)
        // Note: no bigram across the digit boundary (好-世 lost)
        List<String> tokens = new ArrayList<>();
        WordTokenizer.tokenizeAlphanumeric("你好123世界", tokens::add);
        assertEquals(3, tokens.size());
        assertEquals("你好", tokens.get(0));
        assertEquals("123", tokens.get(1));
        assertEquals("世界", tokens.get(2));
    }

    @Test
    public void testAlphanumericNullEmpty() {
        List<String> tokens = new ArrayList<>();
        WordTokenizer.tokenizeAlphanumeric(null, tokens::add);
        assertTrue(tokens.isEmpty());
        WordTokenizer.tokenizeAlphanumeric("", tokens::add);
        assertTrue(tokens.isEmpty());
    }

    // --- Transparent character tests ---

    @Test
    public void testArabicDiacriticsStripped() {
        // كَتَبَ with diacritics should tokenize to one word: كتب
        String withDiacritics = "\u0643\u064E\u062A\u064E\u0628\u064E";
        String withoutDiacritics = "\u0643\u062A\u0628";
        assertEquals(WordTokenizer.tokenize(withoutDiacritics),
                WordTokenizer.tokenize(withDiacritics));
    }

    @Test
    public void testArabicTatweelStripped() {
        // كـتـب (with tatweel) should tokenize same as كتب
        String withTatweel = "\u0643\u0640\u062A\u0640\u0628";
        String withoutTatweel = "\u0643\u062A\u0628";
        assertEquals(WordTokenizer.tokenize(withoutTatweel),
                WordTokenizer.tokenize(withTatweel));
    }

    @Test
    public void testZWNJKeepsWordTogether() {
        // Persian می‌خواهم — ZWNJ should not split the word
        String withZWNJ = "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645";
        List<String> tokens = WordTokenizer.tokenize(withZWNJ);
        assertEquals(1, tokens.size(), "ZWNJ should not be a word boundary");
    }

    @Test
    public void testHebrewNiqqudStripped() {
        // שָׁלוֹם with niqqud should tokenize same as שלום
        String withNiqqud = "\u05E9\u05B8\u05C1\u05DC\u05D5\u05B9\u05DD";
        String withoutNiqqud = "\u05E9\u05DC\u05D5\u05DD";
        assertEquals(WordTokenizer.tokenize(withoutNiqqud),
                WordTokenizer.tokenize(withNiqqud));
    }

    @Test
    public void testAlphanumericArabicDiacritics() {
        // Same transparency applies in alphanumeric mode
        List<String> tokens = new ArrayList<>();
        // كتبَ 123 (Arabic word with fatha, then number)
        WordTokenizer.tokenizeAlphanumeric("\u0643\u062A\u0628\u064E 123", tokens::add);
        assertEquals(2, tokens.size());
        assertEquals("\u0643\u062A\u0628", tokens.get(0)); // base letters only
        assertEquals("123", tokens.get(1));
    }
}
