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

import java.util.Arrays;

/**
 * Feature extractor using positional salt (BOW/EOW/FULL_WORD) instead of
 * sentinel characters in n-grams.
 *
 * <h3>Design principles</h3>
 * <ul>
 *   <li>Single FNV basis constant for all features.  A one-byte <em>salt</em>
 *       prefix distinguishes feature types; n-gram order is differentiated by
 *       the number of codepoints fed into the hash chain.</li>
 *   <li>N-grams always contain N real characters — no sentinel padding.</li>
 *   <li>Word position is encoded via salt bytes (BOW, EOW, FULL_WORD, MID).</li>
 *   <li>No script salting on n-grams — different scripts use different
 *       codepoint ranges, so hashes naturally separate.</li>
 *   <li>Short complete words (1–4 chars) get a FULL_WORD salt on their
 *       matching n-gram order, replacing the separate word-unigram feature.</li>
 *   <li>Script block features (presence counts + transition counts) provide
 *       explicit script signal for the linear classifier.</li>
 *   <li>CJK/kana character unigrams use a dedicated salt (no word boundaries
 *       in CJK).</li>
 * </ul>
 *
 * <h3>Feature types</h3>
 * <ul>
 *   <li><b>Character bigrams</b> — all contiguous pairs within a word,
 *       plus BOW/EOW/FULL_WORD variants.</li>
 *   <li><b>Character trigrams</b> — all contiguous triples, with position salt.</li>
 *   <li><b>Character 4-grams</b> — all contiguous quads, with position salt.</li>
 *   <li><b>CJK/kana unigrams</b> — individual ideographic/kana codepoints.</li>
 *   <li><b>Script blocks</b> — per-script letter counts and transition counts.</li>
 * </ul>
 */
public class SaltedNgramFeatureExtractor implements FeatureExtractor {

    public static final int FEATURE_FLAGS =
            CharSoupModel.FLAG_TRIGRAMS
            | CharSoupModel.FLAG_4GRAMS
            | CharSoupModel.FLAG_SCRIPT_BLOCKS;

    public static final int FEATURE_FLAGS_WITH_WORD_BIGRAMS =
            FEATURE_FLAGS | CharSoupModel.FLAG_WORD_BIGRAMS;

    public static final int FEATURE_FLAGS_V11 =
            FEATURE_FLAGS | CharSoupModel.FLAG_WORD_BIGRAMS
            | CharSoupModel.FLAG_WORD_LENGTH;

    static final int FNV_BASIS = 0x811c9dc5;

    // Salt bytes — one per feature type.  N-gram order is already
    // differentiated by the number of codepoints fed into the hash chain.
    static final int SALT_MID          = 0x00;
    static final int SALT_BOW          = 0x01;
    static final int SALT_EOW          = 0x02;
    static final int SALT_FULL_WORD    = 0x03;
    static final int SALT_CJK_UNIGRAM  = 0x04;
    static final int SALT_SCRIPT       = 0x05;
    static final int SALT_SCRIPT_TRANS = 0x06;
    static final int SALT_WORD_BIGRAM  = 0x07;
    static final int SALT_WORD_LEN     = 0x08;

    /**
     * Maximum length (in codepoints) of the "anchor" word in a word bigram.
     * Words of 1–3 characters are typically function words (articles,
     * prepositions, conjunctions) whose identity combined with the following
     * word is a strong language discriminator.
     */
    static final int MAX_SHORT_WORD = 3;

    static final int MAX_WORD_LEN_FEATURE = 20;

    private final int numBuckets;
    private final boolean useWordBigrams;
    private final boolean useWordLength;

    public SaltedNgramFeatureExtractor(int numBuckets) {
        this(numBuckets, false, false);
    }

    public SaltedNgramFeatureExtractor(int numBuckets, boolean useWordBigrams) {
        this(numBuckets, useWordBigrams, false);
    }

    public SaltedNgramFeatureExtractor(int numBuckets,
                                       boolean useWordBigrams,
                                       boolean useWordLength) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.useWordBigrams = useWordBigrams;
        this.useWordLength = useWordLength;
    }

    @Override
    public int[] extract(String rawText) {
        int[] counts = new int[numBuckets];
        if (rawText == null || rawText.isEmpty()) {
            return counts;
        }
        extractFeatures(CharSoupFeatureExtractor.preprocess(rawText), counts);
        return counts;
    }

    @Override
    public void extract(String rawText, int[] counts) {
        Arrays.fill(counts, 0);
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        extractFeatures(CharSoupFeatureExtractor.preprocess(rawText), counts);
    }

    @Override
    public int[] extractFromPreprocessed(String text) {
        int[] counts = new int[numBuckets];
        if (text == null || text.isEmpty()) {
            return counts;
        }
        extractFeatures(text, counts);
        return counts;
    }

    @Override
    public void extractFromPreprocessed(String text, int[] counts, boolean clear) {
        if (clear) {
            Arrays.fill(counts, 0);
        }
        if (text == null || text.isEmpty()) {
            return;
        }
        extractFeatures(text, counts);
    }

    @Override
    public int extractAndCount(String rawText, int[] counts) {
        extract(rawText, counts);
        int n = 0;
        for (int c : counts) {
            n += c;
        }
        return n;
    }

    /**
     * Core extraction loop.  Collects codepoints into words (maximal runs
     * of same-family letters), then emits n-grams for each completed word
     * via {@link #emitWordNgrams}.  CJK characters get individual unigram
     * features and cross-space bigrams.
     *
     * <p>When {@link #useWordBigrams} is enabled, each completed word is
     * also checked against the previously completed word: if the previous
     * word was 1–{@value #MAX_SHORT_WORD} characters, a word bigram feature
     * {@code hash(SALT_WORD_BIGRAM, prevWord..., currentWord...)} is emitted.
     * This captures function-word-in-context patterns like "the X", "de X",
     * "в X" that are highly discriminative at short text lengths.
     */
    private void extractFeatures(String text, int[] counts) {
        // Word buffer — reused across words.  Stores lowercased codepoints.
        int[] word = new int[256];
        int wordLen = 0;
        int wordScript = -1;

        // Previous completed word — for word bigram features.
        int[] prevWordBuf = useWordBigrams ? new int[256] : null;
        int prevWordLen = 0;

        int prevCp = -1;
        int prevScript = -1;
        boolean prevWasLetter = false;
        boolean prevWasCjk = false;

        int[] scriptCounts = new int[ScriptCategory.COUNT];
        int[] transitionCounts = new int[ScriptCategory.COUNT * ScriptCategory.COUNT];
        int lastLetterScript = -1;

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                int lower = Character.toLowerCase(cp);
                int script = ScriptCategory.of(lower);
                boolean cjk = isCjkScript(script);

                scriptCounts[script]++;
                if (lastLetterScript >= 0 && lastLetterScript != script) {
                    transitionCounts[lastLetterScript * ScriptCategory.COUNT + script]++;
                }
                lastLetterScript = script;

                if (prevWasLetter && !sameFamily(script, prevScript)) {
                    // Script family change — flush current word
                    if (!prevWasCjk && wordLen > 0) {
                        emitWordNgrams(counts, word, wordLen);
                        emitWordLength(counts, wordLen);
                        prevWordLen = saveAndEmitWordBigram(
                                counts, prevWordBuf, prevWordLen,
                                word, wordLen);
                    }
                    wordLen = 0;
                }

                if (cjk) {
                    // CJK: emit unigram; bigram with previous CJK char
                    emitCjkUnigram(counts, lower);
                    if (prevWasCjk && prevCp >= 0) {
                        emit(counts, SALT_MID, prevCp, lower);
                    }
                    // Flush any non-CJK word in progress
                    if (wordLen > 0 && !prevWasCjk) {
                        emitWordNgrams(counts, word, wordLen);
                        emitWordLength(counts, wordLen);
                        prevWordLen = saveAndEmitWordBigram(
                                counts, prevWordBuf, prevWordLen,
                                word, wordLen);
                        wordLen = 0;
                    }
                    // CJK breaks the word-bigram chain
                    prevWordLen = 0;
                } else {
                    // Non-CJK: accumulate into word buffer
                    if (!prevWasLetter || prevWasCjk
                            || !sameFamily(script, prevScript)) {
                        wordLen = 0;
                        wordScript = script;
                    }
                    if (wordLen < word.length) {
                        word[wordLen++] = lower;
                    }
                }

                prevCp = lower;
                prevScript = script;
                prevWasLetter = true;
                prevWasCjk = cjk;
            } else {
                // Non-letter: flush word
                if (prevWasLetter && !prevWasCjk && wordLen > 0) {
                    emitWordNgrams(counts, word, wordLen);
                    emitWordLength(counts, wordLen);
                    prevWordLen = saveAndEmitWordBigram(
                            counts, prevWordBuf, prevWordLen,
                            word, wordLen);
                    wordLen = 0;
                }
                prevWasLetter = false;
                prevWasCjk = false;
                prevCp = -1;
            }
        }

        // Flush final word
        if (prevWasLetter && !prevWasCjk && wordLen > 0) {
            emitWordNgrams(counts, word, wordLen);
            emitWordLength(counts, wordLen);
            saveAndEmitWordBigram(counts, prevWordBuf, prevWordLen,
                    word, wordLen);
        }

        emitScriptFeatures(counts, scriptCounts, transitionCounts);
    }

    /**
     * If word bigrams are enabled and the previous word was 1–{@value #MAX_SHORT_WORD}
     * characters, emit a word bigram feature and save the current word as the
     * new previous word.
     *
     * @return the new previous-word length (i.e. {@code wordLen})
     */
    private int saveAndEmitWordBigram(int[] counts,
                                      int[] prevWordBuf, int prevWordLen,
                                      int[] word, int wordLen) {
        if (!useWordBigrams) {
            return 0;
        }
        if (prevWordLen >= 1 && prevWordLen <= MAX_SHORT_WORD && wordLen > 0) {
            emitWordBigram(counts, prevWordBuf, prevWordLen, word, wordLen);
        }
        System.arraycopy(word, 0, prevWordBuf, 0, wordLen);
        return wordLen;
    }

    /**
     * Hash a word bigram: the short anchor word followed by the next word.
     * Both words' full codepoint sequences are fed into FNV with a separator
     * byte (0xFF) between them to prevent collisions like
     * "ab" + "cd" vs "abc" + "d".
     */
    private void emitWordBigram(int[] counts,
                                int[] w1, int w1Len,
                                int[] w2, int w2Len) {
        int h = fnvFeedByte(FNV_BASIS, SALT_WORD_BIGRAM);
        for (int j = 0; j < w1Len; j++) {
            h = fnvFeedInt(h, w1[j]);
        }
        h = fnvFeedByte(h, 0xFF);
        for (int j = 0; j < w2Len; j++) {
            h = fnvFeedInt(h, w2[j]);
        }
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    /**
     * Emit all bigrams, trigrams, and 4-grams for a completed non-CJK word,
     * with positional salt.
     *
     * <p>For each n-gram of order k (2, 3, 4):
     * <ul>
     *   <li>If the word has exactly k characters, emit once with FULL_WORD salt.</li>
     *   <li>Otherwise, the first n-gram gets BOW salt, the last gets EOW salt,
     *       and all others get MID salt.</li>
     * </ul>
     */
    static void emitWordNgrams(int[] counts, int[] word, int wordLen) {
        // Bigrams
        if (wordLen == 2) {
            emit(counts, SALT_FULL_WORD, word[0], word[1]);
        } else if (wordLen > 2) {
            emit(counts, SALT_BOW, word[0], word[1]);
            for (int j = 1; j < wordLen - 2; j++) {
                emit(counts, SALT_MID, word[j], word[j + 1]);
            }
            emit(counts, SALT_EOW, word[wordLen - 2], word[wordLen - 1]);
        }

        // Trigrams
        if (wordLen == 3) {
            emit(counts, SALT_FULL_WORD, word[0], word[1], word[2]);
        } else if (wordLen > 3) {
            emit(counts, SALT_BOW, word[0], word[1], word[2]);
            for (int j = 1; j < wordLen - 3; j++) {
                emit(counts, SALT_MID, word[j], word[j + 1], word[j + 2]);
            }
            emit(counts, SALT_EOW,
                    word[wordLen - 3], word[wordLen - 2], word[wordLen - 1]);
        }

        // 4-grams
        if (wordLen == 4) {
            emit(counts, SALT_FULL_WORD,
                    word[0], word[1], word[2], word[3]);
        } else if (wordLen > 4) {
            emit(counts, SALT_BOW, word[0], word[1], word[2], word[3]);
            for (int j = 1; j < wordLen - 4; j++) {
                emit(counts, SALT_MID,
                        word[j], word[j + 1], word[j + 2], word[j + 3]);
            }
            emit(counts, SALT_EOW,
                    word[wordLen - 4], word[wordLen - 3], word[wordLen - 2], word[wordLen - 1]);
        }

        // 1-letter words (non-CJK single-char words like "I", "a", "y", "à")
        if (wordLen == 1) {
            emit(counts, SALT_FULL_WORD, word[0]);
        }
    }

    // ----- emit helpers -----

    private static void emit(int[] counts, int salt, int cp1) {
        int h = fnvFeedByte(FNV_BASIS, salt);
        h = fnvFeedInt(h, cp1);
        counts[(h & 0x7FFFFFFF) % counts.length]++;
    }

    private static void emit(int[] counts, int salt, int cp1, int cp2) {
        int h = fnvFeedByte(FNV_BASIS, salt);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        counts[(h & 0x7FFFFFFF) % counts.length]++;
    }

    private static void emit(int[] counts, int salt, int cp1, int cp2, int cp3) {
        int h = fnvFeedByte(FNV_BASIS, salt);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        h = fnvFeedInt(h, cp3);
        counts[(h & 0x7FFFFFFF) % counts.length]++;
    }

    private static void emit(int[] counts, int salt,
                              int cp1, int cp2, int cp3, int cp4) {
        int h = fnvFeedByte(FNV_BASIS, salt);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        h = fnvFeedInt(h, cp3);
        h = fnvFeedInt(h, cp4);
        counts[(h & 0x7FFFFFFF) % counts.length]++;
    }

    private void emitWordLength(int[] counts, int wordLen) {
        if (!useWordLength) {
            return;
        }
        int len = Math.min(wordLen, MAX_WORD_LEN_FEATURE);
        int h = fnvFeedByte(FNV_BASIS, SALT_WORD_LEN);
        h = fnvFeedByte(h, len);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitCjkUnigram(int[] counts, int cp) {
        int h = fnvFeedByte(FNV_BASIS, SALT_CJK_UNIGRAM);
        h = fnvFeedInt(h, cp);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitScriptFeatures(int[] counts,
                                     int[] scriptCounts,
                                     int[] transitionCounts) {
        for (int s = 0; s < ScriptCategory.COUNT; s++) {
            if (scriptCounts[s] > 0) {
                int h = fnvFeedByte(FNV_BASIS, SALT_SCRIPT);
                h = fnvFeedByte(h, s);
                counts[(h & 0x7FFFFFFF) % numBuckets] += scriptCounts[s];
            }
        }

        for (int s = 0; s < ScriptCategory.COUNT; s++) {
            for (int t = 0; t < ScriptCategory.COUNT; t++) {
                int c = transitionCounts[s * ScriptCategory.COUNT + t];
                if (c > 0) {
                    int h = fnvFeedByte(FNV_BASIS, SALT_SCRIPT_TRANS);
                    h = fnvFeedByte(h, s);
                    h = fnvFeedByte(h, t);
                    counts[(h & 0x7FFFFFFF) % numBuckets] += c;
                }
            }
        }
    }

    // ----- utilities -----

    private static boolean isCjkScript(int script) {
        return ScriptAwareFeatureExtractor.isCjkScript(script);
    }

    private static boolean sameFamily(int a, int b) {
        if (a == b) return true;
        return isCjkScript(a) && isCjkScript(b);
    }

    static boolean isCjkOrKana(int cp) {
        if (Character.isIdeographic(cp)) return true;
        Character.UnicodeScript us = Character.UnicodeScript.of(cp);
        return us == Character.UnicodeScript.HIRAGANA
                || us == Character.UnicodeScript.KATAKANA;
    }

    private static int fnvFeedByte(int hash, int b) {
        return (hash ^ (b & 0xFF)) * 0x01000193;
    }

    private static int fnvFeedInt(int hash, int value) {
        hash = (hash ^ (value & 0xFF)) * 0x01000193;
        hash = (hash ^ ((value >>> 8) & 0xFF)) * 0x01000193;
        hash = (hash ^ ((value >>> 16) & 0xFF)) * 0x01000193;
        hash = (hash ^ ((value >>> 24) & 0xFF)) * 0x01000193;
        return hash;
    }

    @Override
    public int getNumBuckets() {
        return numBuckets;
    }

    @Override
    public int getFeatureFlags() {
        int flags = FEATURE_FLAGS;
        if (useWordBigrams) {
            flags |= CharSoupModel.FLAG_WORD_BIGRAMS;
        }
        if (useWordLength) {
            flags |= CharSoupModel.FLAG_WORD_LENGTH;
        }
        return flags;
    }
}
