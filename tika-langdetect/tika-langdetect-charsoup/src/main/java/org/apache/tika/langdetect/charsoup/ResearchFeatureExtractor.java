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
 * Fully-parameterized feature extractor supporting character bigrams, trigrams,
 * 4-grams, 5-grams, word unigrams, and optional suffixes/prefixes.
 * <p>
 * Used for the short-text language model (trained with +tri+4g+5g configuration)
 * and for ablation studies. The general model uses the simpler
 * {@link ScriptAwareFeatureExtractor} instead.
 * <p>
 * All feature types share the same flat bucket space. Hash collisions are
 * tolerated by design — the model learns to route around them.
 */
public class ResearchFeatureExtractor implements FeatureExtractor {

    static final int BIGRAM_BASIS       = 0x811c9dc5;
    static final int TRIGRAM_BASIS      = 0x9f4e3c21;
    static final int SKIP_BASIS         = 0x6d4d3a2b;
    static final int UNIGRAM_BASIS      = 0x2f4a3c17;
    static final int WORD_BASIS         = 0x4a1c7b39;
    static final int SUFFIX_BASIS       = 0x7e2b1a8f;
    static final int SUFFIX4_BASIS      = 0x5c8a1e49;
    static final int PREFIX_BASIS       = 0x3b7e9f12;
    static final int CHAR_UNIGRAM_BASIS = 0x1d4f8c3a;
    static final int FOURGRAM_BASIS     = 0xa3d8f215;
    static final int FIVEGRAM_BASIS     = 0xc7b46e38;

    static final int MAX_WORD_LENGTH = 30;
    static final int MIN_WORD_LENGTH = 2;
    static final int SENTINEL = '_';

    private final int numBuckets;
    private final boolean useTrigrams;
    private final boolean useSkipBigrams;
    private final boolean useSuffixes;
    private final boolean useSuffix4;
    private final boolean usePrefix;
    private final boolean useWordUnigrams;
    private final boolean useCharUnigrams;
    private final boolean use4grams;
    private final boolean use5grams;

    /** Minimal constructor: bigrams + word unigrams + CJK unigrams. */
    public ResearchFeatureExtractor(int numBuckets) {
        this(numBuckets, false, false, false, false, false, true, false, false, false);
    }

    /** Full-config constructor. All features share the same flat bucket space. */
    public ResearchFeatureExtractor(int numBuckets,
                                    boolean useTrigrams,
                                    boolean useSkipBigrams,
                                    boolean useSuffixes,
                                    boolean useSuffix4,
                                    boolean usePrefix,
                                    boolean useWordUnigrams,
                                    boolean useCharUnigrams,
                                    boolean use4grams,
                                    boolean use5grams) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException(
                    "numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.useTrigrams = useTrigrams;
        this.useSkipBigrams = useSkipBigrams;
        this.useSuffixes = useSuffixes;
        this.useSuffix4 = useSuffix4;
        this.usePrefix = usePrefix;
        this.useWordUnigrams = useWordUnigrams;
        this.useCharUnigrams = useCharUnigrams;
        this.use4grams = use4grams;
        this.use5grams = use5grams;
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

    private void extractFeatures(String text, int[] counts) {
        int prevCp = SENTINEL;
        int prevScript = -1;
        boolean prevWasLetter = false;
        boolean prevWasCjk = false;
        int prevPrevCp = SENTINEL;
        int prevPrevPrevCp = SENTINEL;
        int prevPrevPrevPrevCp = SENTINEL;

        int wordHash = WORD_BASIS;
        int wordLen = 0;
        int wordScript = -1;
        int suf0 = SENTINEL;
        int suf1 = SENTINEL;
        int suf2 = SENTINEL;
        int suf3 = SENTINEL;
        int preA = SENTINEL;
        int preB = SENTINEL;
        int preC = SENTINEL;

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

                if (prevWasLetter) {
                    if (!sameFamily(script, prevScript)) {
                        emitBoundaryEnd(counts, prevScript, prevCp, prevWasCjk,
                                wordHash, wordLen, wordScript,
                                suf0, suf1, suf2, suf3, preA, preB, preC);
                        emitBoundaryStart(counts, script, lower, cjk);
                        wordHash = WORD_BASIS;
                        wordHash = fnvFeedByte(wordHash, script);
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen = 1;
                        wordScript = script;
                        prevPrevPrevPrevCp = SENTINEL;
                        prevPrevPrevCp = SENTINEL;
                        prevPrevCp = SENTINEL;
                        suf0 = SENTINEL;
                        suf1 = SENTINEL;
                        suf2 = SENTINEL;
                        suf3 = lower;
                        preA = lower;
                        preB = SENTINEL;
                        preC = SENTINEL;
                    } else {
                        emitBigram(counts, script, prevCp, lower);
                        if (!cjk && prevPrevCp != SENTINEL) {
                            if (useTrigrams) {
                                emitTrigram(counts, script, prevPrevCp, prevCp, lower);
                            }
                            if (useSkipBigrams) {
                                emitSkipBigram(counts, script, prevPrevCp, lower);
                            }
                            if (use4grams && prevPrevPrevCp != SENTINEL) {
                                emit4gram(counts, script, prevPrevPrevCp, prevPrevCp, prevCp, lower);
                            }
                            if (use5grams && prevPrevPrevPrevCp != SENTINEL) {
                                emit5gram(counts, script, prevPrevPrevPrevCp, prevPrevPrevCp, prevPrevCp, prevCp, lower);
                            }
                        }
                        prevPrevPrevPrevCp = prevPrevPrevCp;
                        prevPrevPrevCp = prevPrevCp;
                        prevPrevCp = prevCp;
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen++;
                        if (!cjk) {
                            suf0 = suf1;
                            suf1 = suf2;
                            suf2 = suf3;
                            suf3 = lower;
                            if (wordLen == 2) {
                                preB = lower;
                                if (useTrigrams) {
                                    emitTrigram(counts, script, SENTINEL, prevCp, lower);
                                }
                            } else if (wordLen == 3) {
                                preC = lower;
                                if (use4grams) {
                                    emit4gram(counts, script, SENTINEL, preA, preB, lower);
                                }
                            } else if (wordLen == 4) {
                                if (use5grams) {
                                    emit5gram(counts, script, SENTINEL, preA, preB, preC, lower);
                                }
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
                        prevPrevPrevPrevCp = SENTINEL;
                        prevPrevPrevCp = SENTINEL;
                        prevPrevCp = SENTINEL;
                        suf0 = SENTINEL;
                        suf1 = SENTINEL;
                        suf2 = SENTINEL;
                        suf3 = lower;
                        preA = lower;
                        preB = SENTINEL;
                        preC = SENTINEL;
                    }
                }

                if (isCjkOrKana(lower)) {
                    emitUnigram(counts, script, lower);
                } else if (useCharUnigrams) {
                    emitCharUnigram(counts, script, lower);
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
                            wordHash, wordLen, wordScript,
                            suf0, suf1, suf2, suf3, preA, preB, preC);
                }
                prevWasLetter = false;
                prevWasCjk = false;
                prevCp = SENTINEL;
                prevPrevCp = SENTINEL;
                prevPrevPrevCp = SENTINEL;
                prevPrevPrevPrevCp = SENTINEL;
                wordLen = 0;
                suf0 = SENTINEL;
                suf1 = SENTINEL;
                suf2 = SENTINEL;
                suf3 = SENTINEL;
                preA = SENTINEL;
                preB = SENTINEL;
                preC = SENTINEL;
            }
        }

        if (prevWasLetter) {
            emitBoundaryEnd(counts, prevScript, prevCp, prevWasCjk,
                    wordHash, wordLen, wordScript,
                    suf0, suf1, suf2, suf3, preA, preB, preC);
        }
    }

    private void emitBoundaryStart(int[] counts, int script, int lower, boolean cjk) {
        if (!cjk) {
            emitBigram(counts, script, SENTINEL, lower);
        }
    }

    private void emitBoundaryEnd(int[] counts, int script, int prevCp, boolean cjk,
                                  int wordHash, int wordLen, int wordScript,
                                  int suf0, int suf1, int suf2, int suf3,
                                  int preA, int preB, int preC) {
        if (!cjk) {
            emitBigram(counts, script, prevCp, SENTINEL);
            if (useTrigrams && wordLen >= 2) {
                emitTrigram(counts, script, suf2, suf3, SENTINEL);
            }
            if (use4grams && wordLen >= 3) {
                emit4gram(counts, wordScript, suf1, suf2, suf3, SENTINEL);
            }
            if (use4grams && wordLen == 2) {
                // complete 2-letter word: (_, a, b, _)
                emit4gram(counts, wordScript, SENTINEL, suf2, suf3, SENTINEL);
            }
            if (use5grams && wordLen >= 4) {
                emit5gram(counts, wordScript, suf0, suf1, suf2, suf3, SENTINEL);
            }
            if (use5grams && wordLen == 3) {
                // complete 3-letter word: (_, a, b, c, _)
                emit5gram(counts, wordScript, SENTINEL, suf1, suf2, suf3, SENTINEL);
            }
            if (useWordUnigrams) {
                emitWordIfEligible(counts, wordHash, wordLen);
            }
            if (useSuffixes && wordLen >= 3) {
                emitSuffix(counts, wordScript, suf1, suf2, suf3);
            }
            if (useSuffix4 && wordLen >= 4) {
                emitSuffix4(counts, wordScript, suf0, suf1, suf2, suf3);
            }
            if (usePrefix && wordLen >= 3) {
                emitPrefix(counts, wordScript, preA, preB, preC);
            }
        }
    }

    private void emitBigram(int[] counts, int script, int cp1, int cp2) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedByte(BIGRAM_BASIS, script), cp1), cp2);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitTrigram(int[] counts, int script, int cp1, int cp2, int cp3) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(TRIGRAM_BASIS, script), cp1), cp2), cp3);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitSkipBigram(int[] counts, int script, int cp1, int cp2) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedByte(SKIP_BASIS, script), cp1), cp2);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitUnigram(int[] counts, int script, int cp) {
        int h = fnvFeedInt(fnvFeedByte(UNIGRAM_BASIS, script), cp);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitCharUnigram(int[] counts, int script, int cp) {
        int h = fnvFeedInt(fnvFeedByte(CHAR_UNIGRAM_BASIS, script), cp);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitSuffix(int[] counts, int script, int cp1, int cp2, int cp3) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(SUFFIX_BASIS, script), cp1), cp2), cp3);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitSuffix4(int[] counts, int script, int cp1, int cp2, int cp3, int cp4) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(SUFFIX4_BASIS, script), cp1), cp2), cp3), cp4);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emit4gram(int[] counts, int script, int cp1, int cp2, int cp3, int cp4) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(FOURGRAM_BASIS, script), cp1), cp2), cp3), cp4);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emit5gram(int[] counts, int script, int cp1, int cp2, int cp3, int cp4, int cp5) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(FIVEGRAM_BASIS, script), cp1), cp2), cp3), cp4), cp5);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitPrefix(int[] counts, int script, int cp1, int cp2, int cp3) {
        int h = fnvFeedInt(fnvFeedInt(fnvFeedInt(fnvFeedByte(PREFIX_BASIS, script), cp1), cp2), cp3);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitWordIfEligible(int[] counts, int wordHash, int wordLen) {
        if (wordLen >= MIN_WORD_LENGTH && wordLen <= MAX_WORD_LENGTH) {
            counts[(wordHash & 0x7FFFFFFF) % numBuckets]++;
        }
    }

    private static boolean isCjkScript(int script) {
        return script == ScriptCategory.HAN
                || script == ScriptCategory.HIRAGANA
                || script == ScriptCategory.KATAKANA;
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
}
