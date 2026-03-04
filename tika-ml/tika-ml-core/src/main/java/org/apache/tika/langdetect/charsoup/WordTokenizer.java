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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * General-purpose word tokenizer that shares the same preprocessing pipeline
 * as {@link CharSoupFeatureExtractor}: NFC normalization, URL/email stripping,
 * case folding via {@link Character#toLowerCase(int)}.
 * <p>
 * This tokenizer is designed to replace Lucene's analyzer pipeline in tika-eval.
 * It handles both alphabetic and ideographic scripts:
 * <ul>
 *   <li><b>Alphabetic scripts</b>: accumulates letters into words, emits
 *       on word boundary (non-letter codepoint)</li>
 *   <li><b>Ideographic characters</b>: emits character bigrams (pairs of adjacent
 *       ideographic characters), equivalent to Lucene's CJKBigramFilter</li>
 * </ul>
 * <p>
 * Mixed runs (e.g., alphabetic followed by ideographic) are handled correctly:
 * the alphabetic word is emitted at the boundary, then ideographic bigrams begin.
 */
public class WordTokenizer {

    private WordTokenizer() {
    }

    /**
     * Tokenize the given raw text with full preprocessing (truncate, strip URLs/emails,
     * NFC normalize, case fold) and return tokens as a list.
     * Only alphabetic and ideographic tokens are emitted (no numbers).
     *
     * @param rawText raw input text
     * @return list of token strings (words for alphabetic, bigrams for ideographic)
     */
    public static List<String> tokenize(String rawText) {
        List<String> result = new ArrayList<>();
        tokenize(rawText, result::add);
        return result;
    }

    /**
     * Tokenize with full preprocessing, streaming tokens to a consumer.
     * Only alphabetic and ideographic tokens are emitted (no numbers).
     *
     * @param rawText  raw input text
     * @param consumer receives each token
     */
    public static void tokenize(String rawText, Consumer<String> consumer) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        String text = CharSoupFeatureExtractor.preprocess(rawText);
        tokenizePreprocessed(text, consumer);
    }

    /**
     * Tokenize the given raw text with full preprocessing, including numeric tokens.
     * Alphabetic words and digit-only runs are emitted as separate tokens.
     * Ideographic text produces character bigrams as usual.
     *
     * @param rawText  raw input text
     * @param consumer receives each token
     */
    public static void tokenizeAlphanumeric(String rawText, Consumer<String> consumer) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        String text = CharSoupFeatureExtractor.preprocess(rawText);
        tokenizePreprocessedAlphanumeric(text, consumer);
    }

    /**
     * Tokenize already-preprocessed (NFC, stripped) text.
     * Only alphabetic and ideographic tokens; no numbers.
     * <p>
     * Transparent characters (Arabic harakat, Hebrew niqqud, tatweel, ZWNJ, ZWJ)
     * are skipped so that base letters remain contiguous within a word.
     * See {@link CharSoupFeatureExtractor#isTransparent(int)}.
     *
     * @param text     preprocessed text
     * @param consumer receives each token
     */
    static void tokenizePreprocessed(String text, Consumer<String> consumer) {
        StringBuilder wordBuffer = new StringBuilder();
        int prevIdeograph = -1; // previous ideographic codepoint for bigram emission

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            // Skip diacritics, tatweel, ZWNJ, ZWJ
            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                int lower = Character.toLowerCase(cp);
                if (Character.isIdeographic(cp)) {
                    // Flush any pending alphabetic word
                    if (wordBuffer.length() > 0) {
                        consumer.accept(wordBuffer.toString());
                        wordBuffer.setLength(0);
                    }
                    // Emit ideographic bigram if we have a previous ideograph
                    if (prevIdeograph >= 0) {
                        consumer.accept(new String(new int[]{prevIdeograph, lower}, 0, 2));
                    }
                    prevIdeograph = lower;
                } else {
                    // Alphabetic: flush ideographic state
                    prevIdeograph = -1;
                    wordBuffer.appendCodePoint(lower);
                }
            } else {
                // Non-letter: word boundary
                if (wordBuffer.length() > 0) {
                    consumer.accept(wordBuffer.toString());
                    wordBuffer.setLength(0);
                }
                prevIdeograph = -1;
            }
        }
        // Flush trailing alphabetic word
        if (wordBuffer.length() > 0) {
            consumer.accept(wordBuffer.toString());
        }
        // Note: a single trailing ideograph does NOT emit a bigram (needs a pair)
    }

    /**
     * Tokenize already-preprocessed text, emitting both alphabetic words and
     * digit-only runs as tokens.  Ideographic bigrams are emitted as usual.
     * <p>
     * This is a separate code path from {@link #tokenizePreprocessed} so that
     * the alpha-only hot path used by language detection has zero overhead
     * from a numeric check.
     * <p>
     * Transparent characters (Arabic harakat, Hebrew niqqud, tatweel, ZWNJ, ZWJ)
     * are skipped so that base letters remain contiguous within a word.
     * See {@link CharSoupFeatureExtractor#isTransparent(int)}.
     *
     * @param text     preprocessed text
     * @param consumer receives each token
     */
    static void tokenizePreprocessedAlphanumeric(String text, Consumer<String> consumer) {
        StringBuilder wordBuffer = new StringBuilder();
        int prevIdeograph = -1;
        boolean inDigits = false;

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            // Skip diacritics, tatweel, ZWNJ, ZWJ
            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isDigit(cp)) {
                // Flush alphabetic word or ideographic state
                if (wordBuffer.length() > 0 && !inDigits) {
                    consumer.accept(wordBuffer.toString());
                    wordBuffer.setLength(0);
                }
                prevIdeograph = -1;
                inDigits = true;
                wordBuffer.appendCodePoint(cp);
            } else if (Character.isLetter(cp)) {
                // Flush digit run
                if (inDigits && wordBuffer.length() > 0) {
                    consumer.accept(wordBuffer.toString());
                    wordBuffer.setLength(0);
                    inDigits = false;
                }
                int lower = Character.toLowerCase(cp);
                if (Character.isIdeographic(cp)) {
                    if (wordBuffer.length() > 0) {
                        consumer.accept(wordBuffer.toString());
                        wordBuffer.setLength(0);
                    }
                    if (prevIdeograph >= 0) {
                        consumer.accept(new String(new int[]{prevIdeograph, lower}, 0, 2));
                    }
                    prevIdeograph = lower;
                } else {
                    prevIdeograph = -1;
                    wordBuffer.appendCodePoint(lower);
                }
            } else {
                // Non-letter, non-digit: word boundary
                if (wordBuffer.length() > 0) {
                    consumer.accept(wordBuffer.toString());
                    wordBuffer.setLength(0);
                }
                prevIdeograph = -1;
                inDigits = false;
            }
        }
        if (wordBuffer.length() > 0) {
            consumer.accept(wordBuffer.toString());
        }
    }
}
