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
 * Script-aware feature extractor for language detection.
 * <p>
 * Emits three feature types, each with a distinct FNV-1a salt:
 * <ol>
 *   <li><b>Character bigrams</b> — consecutive letter pairs with
 *       underscore sentinels at word boundaries (non-CJK only).</li>
 *   <li><b>Word unigrams</b> — whole-word hashes for space-delimited
 *       scripts (2–30 codepoints). Not emitted for CJK.</li>
 *   <li><b>CJK/kana unigrams</b> — individual ideographic or kana
 *       characters, where each character is a morpheme.</li>
 * </ol>
 *
 * <h3>Script handling</h3>
 * <ul>
 *   <li>Every hash includes the {@link ScriptCategory} ID as a leading
 *       byte, so Latin "ab" and Cyrillic "аб" never collide.</li>
 *   <li>A script change between consecutive letters is treated as a
 *       word boundary.</li>
 *   <li><b>CJK space bridging</b>: spaces within a CJK run are
 *       skipped — the bigram chain continues across them. This
 *       normalizes word-segmented training corpora (Leipzig) to
 *       match unsegmented real-world CJK text. Punctuation and
 *       numbers still act as real boundaries.</li>
 *   <li>CJK runs do not emit sentinel bigrams or word unigrams.</li>
 * </ul>
 */
public class ScriptAwareFeatureExtractor implements TextFeatureExtractor {

    /** FNV offset basis for character bigrams (standard FNV-1a). */
    static final int BIGRAM_BASIS = 0x811c9dc5;

    /** FNV offset basis for CJK/kana unigrams. */
    static final int UNIGRAM_BASIS = 0x2f4a3c17;

    /** FNV offset basis for whole-word unigrams. */
    static final int WORD_BASIS = 0x4a1c7b39;

    /**
     * FNV offset basis for skip-bigrams (pairs (c[i], c[i+2]), skipping c[i+1]).
     * Distinct from {@link #BIGRAM_BASIS} so skip-bigrams and regular bigrams
     * occupy different hash regions and do not systematically collide.
     */
    static final int SKIP_BIGRAM_BASIS = 0x9e3779b9;

    static final int MAX_WORD_LENGTH = 30;
    static final int MIN_WORD_LENGTH = 2;
    static final int SENTINEL = '_';

    private final int numBuckets;
    private final boolean includeTrigrams;
    private final boolean includeSkipBigrams;

    public ScriptAwareFeatureExtractor(int numBuckets) {
        this(numBuckets, false, false);
    }

    public ScriptAwareFeatureExtractor(int numBuckets, boolean includeTrigrams) {
        this(numBuckets, includeTrigrams, false);
    }

    public ScriptAwareFeatureExtractor(int numBuckets,
                                       boolean includeTrigrams,
                                       boolean includeSkipBigrams) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException(
                    "numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.includeTrigrams = includeTrigrams;
        this.includeSkipBigrams = includeSkipBigrams;
    }

    public boolean isIncludeTrigrams() {
        return includeTrigrams;
    }

    public boolean isIncludeSkipBigrams() {
        return includeSkipBigrams;
    }

    @Override
    public int[] extract(String rawText) {
        int[] counts = new int[numBuckets];
        if (rawText == null || rawText.isEmpty()) {
            return counts;
        }
        extractFeatures(
                CharSoupFeatureExtractor.preprocess(rawText), counts);
        return counts;
    }

    @Override
    public void extract(String rawText, int[] counts) {
        Arrays.fill(counts, 0);
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        extractFeatures(
                CharSoupFeatureExtractor.preprocess(rawText), counts);
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
    public void extractFromPreprocessed(String text,
                                        int[] counts, boolean clear) {
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
        int prevPrevCp = -1;   // two positions back (for trigrams, non-CJK only)
        int prevScript = -1;
        boolean prevWasLetter = false;
        boolean prevWasCjk = false;

        int wordHash = WORD_BASIS;
        int wordLen = 0;
        int wordScript = -1;

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp >= 0x0300
                    && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                int lower = Character.toLowerCase(cp);
                int script = ScriptCategory.of(lower);
                boolean cjk = isCjkScript(script);

                if (prevWasLetter) {
                    if (!sameFamily(script, prevScript)) {
                        // Script family change → boundary
                        emitBoundaryEnd(counts, prevScript,
                                prevCp, prevWasCjk,
                                wordHash, wordLen, wordScript);
                        emitBoundaryStart(counts, script,
                                lower, cjk);
                        wordHash = WORD_BASIS;
                        wordHash = fnvFeedByte(wordHash, script);
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen = 1;
                        wordScript = script;
                        prevPrevCp = -1; // reset: crossed a boundary
                    } else {
                        emitBigram(counts, script, prevCp, lower);
                        if (prevPrevCp >= 0 && !cjk) {
                            if (includeTrigrams) {
                                emitTrigram(counts, script, prevPrevCp, prevCp, lower);
                            }
                            if (includeSkipBigrams) {
                                emitSkipBigram(counts, script, prevPrevCp, lower);
                            }
                        }
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen++;
                        prevPrevCp = prevCp;
                    }
                } else {
                    // First letter after gap
                    if (prevWasCjk && cjk
                            && prevCp != SENTINEL) {
                        // CJK bridging across space — no trigrams across gaps
                        emitBigram(counts, script, prevCp, lower);
                    } else {
                        emitBoundaryStart(counts, script,
                                lower, cjk);
                        wordHash = WORD_BASIS;
                        wordHash = fnvFeedByte(wordHash, script);
                        wordHash = fnvFeedInt(wordHash, lower);
                        wordLen = 1;
                        wordScript = script;
                    }
                    prevPrevCp = -1; // no trigram at word start
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
                        // CJK space bridging — skip
                        prevWasLetter = false;
                        continue;
                    }
                    if (!prevWasCjk && prevPrevCp >= 0) {
                        if (includeTrigrams) {
                            emitTrigram(counts, prevScript, prevPrevCp, prevCp, SENTINEL);
                        }
                        if (includeSkipBigrams) {
                            emitSkipBigram(counts, prevScript, prevPrevCp, SENTINEL);
                        }
                    }
                    emitBoundaryEnd(counts, prevScript,
                            prevCp, prevWasCjk,
                            wordHash, wordLen, wordScript);
                }
                prevWasLetter = false;
                prevWasCjk = false;
                prevCp = SENTINEL;
                prevPrevCp = -1;
                wordLen = 0;
            }
        }

        if (prevWasLetter) {
            if (!prevWasCjk && prevPrevCp >= 0) {
                if (includeTrigrams) {
                    emitTrigram(counts, prevScript, prevPrevCp, prevCp, SENTINEL);
                }
                if (includeSkipBigrams) {
                    emitSkipBigram(counts, prevScript, prevPrevCp, SENTINEL);
                }
            }
            emitBoundaryEnd(counts, prevScript,
                    prevCp, prevWasCjk,
                    wordHash, wordLen, wordScript);
        }
    }

    private void emitBoundaryStart(int[] counts, int script,
                                   int lower, boolean cjk) {
        if (!cjk) {
            emitBigram(counts, script, SENTINEL, lower);
        }
    }

    private void emitBoundaryEnd(int[] counts, int script,
                                 int prevCp, boolean cjk,
                                 int wordHash, int wordLen,
                                 int wordScript) {
        if (!cjk) {
            emitBigram(counts, script, prevCp, SENTINEL);
            emitWordIfEligible(counts, wordHash, wordLen);
        }
    }

    // ---- Feature emission ----

    private void emitBigram(int[] counts,
                            int script, int cp1, int cp2) {
        int h = BIGRAM_BASIS;
        h = fnvFeedByte(h, script);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitTrigram(int[] counts,
                             int script, int cp1, int cp2, int cp3) {
        // Trigrams share the bigram basis — the extra cp3 naturally
        // extends the hash chain without systematic collision with bigrams.
        int h = BIGRAM_BASIS;
        h = fnvFeedByte(h, script);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        h = fnvFeedInt(h, cp3);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    /**
     * Emit a skip-bigram: the pair (cp1, cp2) where cp1 = c[i-2] and cp2 = c[i],
     * skipping the intervening character c[i-1]. Uses {@link #SKIP_BIGRAM_BASIS}
     * as the FNV salt so skip-bigrams and regular bigrams occupy distinct hash
     * regions. Not emitted for CJK (where character unigrams are already the
     * right granularity) or across word boundaries.
     */
    private void emitSkipBigram(int[] counts, int script, int cp1, int cp2) {
        int h = SKIP_BIGRAM_BASIS;
        h = fnvFeedByte(h, script);
        h = fnvFeedInt(h, cp1);
        h = fnvFeedInt(h, cp2);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitUnigram(int[] counts, int script, int cp) {
        int h = UNIGRAM_BASIS;
        h = fnvFeedByte(h, script);
        h = fnvFeedInt(h, cp);
        counts[(h & 0x7FFFFFFF) % numBuckets]++;
    }

    private void emitWordIfEligible(int[] counts,
                                    int wordHash, int wordLen) {
        if (wordLen >= MIN_WORD_LENGTH
                && wordLen <= MAX_WORD_LENGTH) {
            counts[(wordHash & 0x7FFFFFFF) % numBuckets]++;
        }
    }

    // ---- Script helpers ----

    private static boolean isCjkScript(int script) {
        return script == ScriptCategory.HAN
                || script == ScriptCategory.HIRAGANA
                || script == ScriptCategory.KATAKANA;
    }

    /**
     * Returns true if two scripts belong to the same "family"
     * for boundary detection. Han, Hiragana, and Katakana are
     * one family because Japanese text freely mixes all three
     * within words and phrases. All other scripts are their
     * own family (only match themselves).
     */
    private static boolean sameFamily(int a, int b) {
        if (a == b) {
            return true;
        }
        return isCjkScript(a) && isCjkScript(b);
    }

    private static boolean isSpace(int cp) {
        return cp == ' ' || cp == '\t'
                || Character.getType(cp)
                == Character.SPACE_SEPARATOR;
    }

    static boolean isCjkOrKana(int cp) {
        if (Character.isIdeographic(cp)) {
            return true;
        }
        Character.UnicodeScript us =
                Character.UnicodeScript.of(cp);
        return us == Character.UnicodeScript.HIRAGANA
                || us == Character.UnicodeScript.KATAKANA;
    }

    // ---- FNV-1a ----

    private static int fnvFeedByte(int hash, int b) {
        hash ^= (b & 0xFF);
        hash *= 0x01000193;
        return hash;
    }

    private static int fnvFeedInt(int hash, int value) {
        hash ^= (value & 0xFF);
        hash *= 0x01000193;
        hash ^= ((value >>> 8) & 0xFF);
        hash *= 0x01000193;
        hash ^= ((value >>> 16) & 0xFF);
        hash *= 0x01000193;
        hash ^= ((value >>> 24) & 0xFF);
        hash *= 0x01000193;
        return hash;
    }

    public int getNumBuckets() {
        return numBuckets;
    }
}
