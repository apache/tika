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
 * Production feature extractor for the CharSoup short-text language detection model.
 * <p>
 * Hardcoded to the configuration used to train the short-text model
 * (bigrams + trigrams + word unigrams + 4-grams, no suffixes or prefixes):
 * <ul>
 *   <li><b>Character bigrams</b> with word-boundary sentinels (non-CJK)</li>
 *   <li><b>Character trigrams</b> including boundary trigrams</li>
 *   <li><b>Character 4-grams</b> including boundary 4-grams</li>
 *   <li><b>Whole-word unigrams</b> (2–30 codepoints, non-CJK)</li>
 *   <li><b>CJK/kana character unigrams</b></li>
 * </ul>
 * All features share a single flat hash space.
 * <p>
 * Both training ({@code Phase2Trainer}) and inference ({@link CharSoupLanguageDetector})
 * must use this class for the short-text model to ensure feature consistency.
 */
public class ShortTextFeatureExtractor implements FeatureExtractor {

    /**
     * Bitmask of {@link CharSoupModel}{@code .FLAG_*} constants that exactly
     * describes the features this extractor emits.
     */
    public static final int FEATURE_FLAGS =
            CharSoupModel.FLAG_TRIGRAMS
            | CharSoupModel.FLAG_WORD_UNIGRAMS
            | CharSoupModel.FLAG_4GRAMS
            | CharSoupModel.FLAG_SCRIPT_BLOCKS;

    public static final int FEATURE_FLAGS_LEGACY =
            CharSoupModel.FLAG_TRIGRAMS
            | CharSoupModel.FLAG_WORD_UNIGRAMS
            | CharSoupModel.FLAG_4GRAMS;

    static final int BIGRAM_BASIS       = 0x811c9dc5;
    static final int TRIGRAM_BASIS      = 0x9f4e3c21;
    static final int FOURGRAM_BASIS     = 0xa3d8f215;
    static final int UNIGRAM_BASIS      = 0x2f4a3c17;
    static final int WORD_BASIS         = 0x4a1c7b39;
    static final int SCRIPT_BASIS       = ScriptAwareFeatureExtractor.SCRIPT_BASIS;
    static final int SCRIPT_TRANS_BASIS = ScriptAwareFeatureExtractor.SCRIPT_TRANS_BASIS;

    static final int MAX_WORD_LENGTH = 30;
    static final int MIN_WORD_LENGTH = 2;
    static final int SENTINEL = '_';

    private final int numBuckets;
    private final boolean useScriptBlocks;

    public ShortTextFeatureExtractor(int numBuckets) {
        this(numBuckets, true);
    }

    public ShortTextFeatureExtractor(int numBuckets, boolean useScriptBlocks) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException(
                    "numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.useScriptBlocks = useScriptBlocks;
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

    private void extractFeatures(String text, int[] counts) {
        int prevCp = SENTINEL;
        int prevScript = -1;
        boolean prevWasLetter = false;
        boolean prevWasCjk = false;
        int prevPrevCp = SENTINEL;
        int prevPrevPrevCp = SENTINEL;

        int wordHash = WORD_BASIS;
        int wordLen = 0;
        int wordScript = -1;
        int suf1 = SENTINEL;
        int suf2 = SENTINEL;
        int suf3 = SENTINEL;
        int preA = SENTINEL;
        int preB = SENTINEL;

        int[] scriptCounts = useScriptBlocks ? new int[ScriptCategory.COUNT] : null;
        int[] transitionCounts = useScriptBlocks
                ? new int[ScriptCategory.COUNT * ScriptCategory.COUNT] : null;
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

                if (useScriptBlocks) {
                    scriptCounts[script]++;
                    if (lastLetterScript >= 0 && lastLetterScript != script) {
                        transitionCounts[lastLetterScript * ScriptCategory.COUNT + script]++;
                    }
                    lastLetterScript = script;
                }

                if (prevWasLetter) {
                    if (!sameFamily(script, prevScript)) {
                        emitBoundaryEnd(counts, prevScript, prevCp, prevWasCjk,
                                wordHash, wordLen, wordScript, suf1, suf2, suf3);
                        emitBoundaryStart(counts, script, lower, cjk);
                        wordHash = WORD_BASIS;
                        wordHash = fnvFeedByte(wordHash, script);
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen = 1;
                        wordScript = script;
                        prevPrevPrevCp = SENTINEL;
                        prevPrevCp = SENTINEL;
                        suf1 = SENTINEL;
                        suf2 = SENTINEL;
                        suf3 = lower;
                        preA = lower;
                        preB = SENTINEL;
                    } else {
                        emitBigram(counts, script, prevCp, lower);
                        if (!cjk && prevPrevCp != SENTINEL) {
                            emitTrigram(counts, script, prevPrevCp, prevCp, lower);
                            if (prevPrevPrevCp != SENTINEL) {
                                emit4gram(counts, script, prevPrevPrevCp, prevPrevCp, prevCp, lower);
                            }
                        }
                        prevPrevPrevCp = prevPrevCp;
                        prevPrevCp = prevCp;
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen++;
                        if (!cjk) {
                            suf1 = suf2;
                            suf2 = suf3;
                            suf3 = lower;
                            if (wordLen == 2) {
                                preB = lower;
                                emitTrigram(counts, script, SENTINEL, prevCp, lower);
                            } else if (wordLen == 3) {
                                emit4gram(counts, script, SENTINEL, preA, preB, lower);
                            }
                        }
                    }
                } else {
                    if (prevWasCjk && cjk && prevCp != SENTINEL) {
                        emitBigram(counts, script, prevCp, lower);
                    } else {
                        emitBoundaryStart(counts, script, lower, cjk);
                        wordHash = WORD_BASIS;
                        wordHash = fnvFeedByte(wordHash, script);
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen = 1;
                        wordScript = script;
                        prevPrevPrevCp = SENTINEL;
                        prevPrevCp = SENTINEL;
                        suf1 = SENTINEL;
                        suf2 = SENTINEL;
                        suf3 = lower;
                        preA = lower;
                        preB = SENTINEL;
                    }
                }

                if (isCjkOrKana(lower)) {
                    emitUnigram(counts, script, lower);
                }

                prevCp = lower;
                prevScript = script;
                prevWasLetter = true;
                prevWasCjk = cjk;
            } else {
                if (prevWasLetter) {
                    if (prevWasCjk && isSpace(cp)) {
                        prevWasLetter = false;
                        continue;
                    }
                    emitBoundaryEnd(counts, prevScript, prevCp, prevWasCjk,
                            wordHash, wordLen, wordScript, suf1, suf2, suf3);
                }
                prevWasLetter = false;
                prevWasCjk = false;
                prevCp = SENTINEL;
                prevPrevCp = SENTINEL;
                prevPrevPrevCp = SENTINEL;
                wordHash = WORD_BASIS;
                wordLen = 0;
                suf1 = SENTINEL;
                suf2 = SENTINEL;
                suf3 = SENTINEL;
                preA = SENTINEL;
                preB = SENTINEL;
            }
        }

        if (prevWasLetter) {
            emitBoundaryEnd(counts, prevScript, prevCp, prevWasCjk,
                    wordHash, wordLen, wordScript, suf1, suf2, suf3);
        }

        if (useScriptBlocks) {
            emitScriptFeatures(counts, scriptCounts, transitionCounts);
        }
    }

    private void emitBoundaryStart(int[] counts, int script, int lower, boolean cjk) {
        if (!cjk) {
            emitBigram(counts, script, SENTINEL, lower);
        }
    }

    private void emitBoundaryEnd(int[] counts, int script, int prevCp, boolean cjk,
                                  int wordHash, int wordLen, int wordScript,
                                  int suf1, int suf2, int suf3) {
        if (!cjk) {
            emitBigram(counts, script, prevCp, SENTINEL);
            if (wordLen >= 2) {
                emitTrigram(counts, script, suf2, suf3, SENTINEL);
            }
            if (wordLen >= 3) {
                emit4gram(counts, wordScript, suf1, suf2, suf3, SENTINEL);
            }
            if (wordLen == 2) {
                // complete 2-letter word: (_, a, b, _)
                emit4gram(counts, wordScript, SENTINEL, suf2, suf3, SENTINEL);
            }
            emitWordIfEligible(counts, wordHash, wordLen);
        }
    }

    private void emitWordIfEligible(int[] counts, int wordHash, int wordLen) {
        if (wordLen >= MIN_WORD_LENGTH && wordLen <= MAX_WORD_LENGTH) {
            counts[(wordHash & 0x7FFFFFFF) % numBuckets]++;
        }
    }

    private void emitBigram(int[] counts, int script, int cp1, int cp2) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedByte(BIGRAM_BASIS, script), cp1), cp2);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitTrigram(int[] counts, int script, int cp1, int cp2, int cp3) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(
                fnvFeedByte(TRIGRAM_BASIS, script), cp1), cp2), cp3);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emit4gram(int[] counts, int script, int cp1, int cp2, int cp3, int cp4) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedInt(
                fnvFeedByte(FOURGRAM_BASIS, script), cp1), cp2), cp3), cp4);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitUnigram(int[] counts, int script, int cp) {
        int h = fnvFeedInt(fnvFeedByte(UNIGRAM_BASIS, script), cp);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitScriptFeatures(int[] counts,
                                     int[] scriptCounts,
                                     int[] transitionCounts) {
        for (int s = 0; s < ScriptCategory.COUNT; s++) {
            if (scriptCounts[s] > 0) {
                int h = fnvFeedByte(SCRIPT_BASIS, s);
                counts[(h & 0x7FFFFFFF) % numBuckets] += scriptCounts[s];
            }
        }

        for (int s = 0; s < ScriptCategory.COUNT; s++) {
            for (int t = 0; t < ScriptCategory.COUNT; t++) {
                int c = transitionCounts[s * ScriptCategory.COUNT + t];
                if (c > 0) {
                    int h = fnvFeedByte(fnvFeedByte(SCRIPT_TRANS_BASIS, s), t);
                    counts[(h & 0x7FFFFFFF) % numBuckets] += c;
                }
            }
        }
    }

    private static boolean isCjkScript(int script) {
        return ScriptAwareFeatureExtractor.isCjkScript(script);
    }

    private static boolean sameFamily(int a, int b) {
        if (a == b) return true;
        return isCjkScript(a) && isCjkScript(b);
    }

    private static boolean isSpace(int cp) {
        return cp == ' ' || cp == '\t'
                || Character.getType(cp) == Character.SPACE_SEPARATOR;
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

    public int getNumBuckets() {
        return numBuckets;
    }

    @Override
    public int getFeatureFlags() {
        return useScriptBlocks ? FEATURE_FLAGS : FEATURE_FLAGS_LEGACY;
    }
}
