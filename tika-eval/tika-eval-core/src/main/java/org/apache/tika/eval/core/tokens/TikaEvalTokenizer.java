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
package org.apache.tika.eval.core.tokens;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;

/**
 * Tokenizer for tika-eval text analysis. Provides two modes:
 * <ul>
 *   <li>{@link Mode#STANDARD} — for general token counting. Emits all
 *       alphabetic, ideographic, and numeric tokens with no minimum length
 *       and no skip list. Used by {@link AnalyzerManager} for
 *       {@code NUM_TOKENS} / {@code NUM_UNIQUE_TOKENS}.</li>
 *   <li>{@link Mode#COMMON_TOKENS} — for building and querying common-token
 *       frequency lists. Alphabetic only (no numbers), minimum 3 characters,
 *       common HTML markup terms excluded. Used by
 *       {@link org.apache.tika.eval.core.tokens.CommonTokenCountManager}
 *       and the common token generator.</li>
 * </ul>
 * <p>
 * Both modes share the same preprocessing pipeline:
 * <ol>
 *   <li>URL/email stripping and truncation via
 *       {@link CharSoupFeatureExtractor#preprocess(String)}</li>
 *   <li>NFKD normalization for accent-insensitive matching (combining
 *       marks are dropped by
 *       {@link CharSoupFeatureExtractor#isTransparent(int)})</li>
 *   <li>Case folding via {@link Character#toLowerCase(int)}</li>
 *   <li>CJK character bigrams (no unigrams)</li>
 * </ol>
 * <p>
 * This class is intentionally separate from
 * {@link org.apache.tika.langdetect.charsoup.WordTokenizer} to avoid
 * parameterization in the language-detection hot path.
 */
public class TikaEvalTokenizer {

    /**
     * Tokenization mode.
     */
    public enum Mode {
        /**
         * General token counting — letters, ideographs, and numbers.
         * No minimum length, no skip list.
         */
        STANDARD,
        /**
         * Common-token analysis — letters and ideographs only.
         * Minimum 3 characters for alphabetic tokens, HTML terms excluded.
         */
        COMMON_TOKENS
    }

    /**
     * Minimum token length for alphabetic (non-CJK) tokens in
     * {@link Mode#COMMON_TOKENS}. CJK bigrams (2 chars) are exempt
     * since they are the natural unit for ideographic scripts.
     */
    static final int MIN_ALPHA_TOKEN_LENGTH = 3;

    /**
     * Maximum length of any single emitted token. Tokens longer than this are
     * discarded. This guards against pathological inputs with no whitespace
     * (base64 blobs, binary garbage, very long numeric strings) that would
     * otherwise cause the word buffer to grow without bound.
     * <p>
     * 128 chars comfortably covers the longest real words in any language
     * (the longest dictionary word in English is 45 chars; German compound
     * words rarely exceed 80 chars). Numbers longer than this are not
     * meaningful for document-quality comparison.
     */
    static final int MAX_TOKEN_LENGTH = 128;

    /** Common HTML markup terms to exclude in {@link Mode#COMMON_TOKENS}. */
    private static final Set<String> SKIP_SET = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                    "span", "table", "href", "head", "title", "body", "html",
                    "tagname", "lang", "style", "script", "strong", "blockquote",
                    "form", "iframe", "section", "colspan", "rowspan")));

    private TikaEvalTokenizer() {
    }

    /**
     * Tokenize in {@link Mode#COMMON_TOKENS} mode and return tokens as a list.
     *
     * @param rawText raw input text
     * @return filtered token list
     */
    public static List<String> tokenize(String rawText) {
        return tokenize(rawText, Mode.COMMON_TOKENS);
    }

    /**
     * Tokenize in the specified mode and return tokens as a list.
     *
     * @param rawText raw input text
     * @param mode    tokenization mode
     * @return token list
     */
    public static List<String> tokenize(String rawText, Mode mode) {
        List<String> result = new ArrayList<>();
        tokenize(rawText, mode, result::add);
        return result;
    }

    /**
     * Tokenize in {@link Mode#COMMON_TOKENS} mode, streaming tokens to a consumer.
     *
     * @param rawText  raw input text
     * @param consumer receives each token
     */
    public static void tokenize(String rawText, Consumer<String> consumer) {
        tokenize(rawText, Mode.COMMON_TOKENS, consumer);
    }

    /**
     * Tokenize in the specified mode, streaming tokens to a consumer.
     *
     * @param rawText  raw input text
     * @param mode     tokenization mode
     * @param consumer receives each token
     */
    public static void tokenize(String rawText, Mode mode, Consumer<String> consumer) {
        tokenize(rawText, mode, Integer.MAX_VALUE, consumer);
    }

    /**
     * Tokenize in the specified mode, streaming at most {@code maxTokens} tokens to a
     * consumer. Iteration stops as soon as the limit is reached — no wasted work on the
     * remainder of the string.
     *
     * @param rawText   raw input text
     * @param mode      tokenization mode
     * @param maxTokens maximum number of tokens to emit; use {@link Integer#MAX_VALUE} for no limit
     * @param consumer  receives each token
     */
    public static void tokenize(String rawText, Mode mode, int maxTokens,
                                Consumer<String> consumer) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        String text = CharSoupFeatureExtractor.preprocessNoTruncate(rawText);
        text = toNFKD(text);
        tokenizePreprocessed(text, mode, maxTokens, consumer);
    }

    /**
     * NFKD normalize for accent-insensitive matching.
     * Compatibility decomposition breaks precomposed characters like
     * {@code é} into {@code e} + combining acute accent (U+0301).
     * The combining mark is then skipped during tokenization by
     * {@link CharSoupFeatureExtractor#isTransparent(int)}.
     * Also handles compatibility characters: {@code ﬁ} → {@code fi},
     * full-width forms → ASCII, etc.
     */
    static String toNFKD(String text) {
        if (Normalizer.isNormalized(text, Normalizer.Form.NFKD)) {
            return text;
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKD);
    }

    /**
     * Tokenize already-preprocessed (NFKD, URL-stripped) text.
     *
     * @param text     preprocessed text
     * @param mode     tokenization mode
     * @param consumer receives each accepted token
     */
    static void tokenizePreprocessed(String text, Mode mode, Consumer<String> consumer) {
        tokenizePreprocessed(text, mode, Integer.MAX_VALUE, consumer);
    }

    /**
     * Tokenize already-preprocessed (NFKD, URL-stripped) text, emitting at most
     * {@code maxTokens} tokens. Stops iterating as soon as the limit is reached.
     *
     * @param text      preprocessed text
     * @param mode      tokenization mode
     * @param maxTokens hard cap on emitted tokens; use {@link Integer#MAX_VALUE} for no limit
     * @param consumer  receives each accepted token
     */
    static void tokenizePreprocessed(String text, Mode mode, int maxTokens,
                                     Consumer<String> consumer) {
        boolean includeNumbers = (mode == Mode.STANDARD);
        StringBuilder wordBuffer = new StringBuilder();
        boolean wordIsNumeric = false;
        int prevIdeograph = -1;
        int[] emitted = {0};
        Consumer<String> limited = token -> {
            consumer.accept(token);
            emitted[0]++;
        };

        int i = 0;
        int len = text.length();
        while (i < len && emitted[0] < maxTokens) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                if (wordIsNumeric) {
                    flushWord(wordBuffer, true, mode, limited);
                    wordIsNumeric = false;
                }
                int lower = Character.toLowerCase(cp);
                if (Character.isIdeographic(cp)) {
                    flushWord(wordBuffer, false, mode, limited);
                    if (prevIdeograph >= 0) {
                        limited.accept(
                                new String(new int[]{prevIdeograph, lower}, 0, 2));
                    }
                    prevIdeograph = lower;
                } else {
                    prevIdeograph = -1;
                    if (wordBuffer.length() < MAX_TOKEN_LENGTH) {
                        wordBuffer.appendCodePoint(lower);
                    }
                }
            } else if (includeNumbers && Character.isDigit(cp)) {
                if (!wordIsNumeric && wordBuffer.length() > 0) {
                    flushWord(wordBuffer, false, mode, limited);
                }
                prevIdeograph = -1;
                if (wordBuffer.length() < MAX_TOKEN_LENGTH) {
                    wordBuffer.appendCodePoint(cp);
                }
                wordIsNumeric = true;
            } else if (includeNumbers && wordIsNumeric
                    && (cp == ',' || cp == '.')
                    && i < len && Character.isDigit(text.codePointAt(i))) {
                // Thousands separator or decimal point between digits: skip the
                // punctuation and keep accumulating the numeric token so that
                // "1,200" and "1200" both produce the same token "1200".
            } else {
                flushWord(wordBuffer, wordIsNumeric, mode, limited);
                wordIsNumeric = false;
                prevIdeograph = -1;
            }
        }
        if (emitted[0] < maxTokens) {
            flushWord(wordBuffer, wordIsNumeric, mode, limited);
        }
    }

    private static void flushWord(StringBuilder buf, boolean isNumeric,
                                  Mode mode, Consumer<String> consumer) {
        if (buf.length() == 0) {
            return;
        }
        if (buf.length() > MAX_TOKEN_LENGTH) {
            buf.setLength(0);
            return;
        }
        if (isNumeric) {
            // Numeric tokens: emit as-is, no min length or skip set
            consumer.accept(buf.toString());
            buf.setLength(0);
            return;
        }
        if (mode == Mode.COMMON_TOKENS && buf.length() < MIN_ALPHA_TOKEN_LENGTH) {
            buf.setLength(0);
            return;
        }
        String word = buf.toString();
        buf.setLength(0);
        if (mode == Mode.COMMON_TOKENS && SKIP_SET.contains(word)) {
            return;
        }
        consumer.accept(word);
    }
}
