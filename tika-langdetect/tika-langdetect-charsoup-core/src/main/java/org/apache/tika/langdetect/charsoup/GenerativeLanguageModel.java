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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dense INT8 generative character n-gram model for languageness scoring.
 *
 * <p>Computes an approximate per-n-gram average log P(text | language).
 * Higher scores indicate the decoded text is more consistent with the named
 * language.  The score is used to detect charset errors (mojibake), garbled
 * text, and other corpus-quality issues.
 *
 * <h3>Feature types (v4 model)</h3>
 * <ul>
 *   <li><b>CJK languages</b>: character unigrams and bigrams from CJK/kana
 *       codepoints.</li>
 *   <li><b>Non-CJK languages</b>: character unigrams (per-letter streaming);
 *       character bigrams and trigrams with positional salts (BOW/MID/EOW/
 *       FULL_WORD) applied at the word level; bidirectional word bigrams
 *       (short-anchor forward and backward).</li>
 *   <li><b>Script distribution</b>: normalized per-script letter proportions
 *       using {@link GlmScriptCategory} (34 fine-grained categories, no
 *       OTHER catch-all).</li>
 * </ul>
 *
 * <h3>Positional salting</h3>
 * N-grams use a single salt byte (BOW/MID/EOW/FULL_WORD) as the first byte
 * of the FNV hash rather than sentinel characters.  This means:
 * <ul>
 *   <li>N-grams always contain N real characters — no {@code _} padding.</li>
 *   <li>The same bigram at the start vs. middle of a word maps to a different
 *       bucket, encoding positional information without polluting codepoint
 *       space.</li>
 * </ul>
 *
 * <h3>Bidirectional word bigrams</h3>
 * <ul>
 *   <li><b>Forward</b>: fired when the previous word is short (≤ {@value
 *       #MAX_SHORT_WORD} chars) — captures function-word-in-context like
 *       "the X", "de X", "в X".</li>
 *   <li><b>Backward</b>: fired when the current word is short — captures
 *       "X the", "X de", "X в" (what precedes a function word).</li>
 * </ul>
 *
 * <h3>Binary format ({@code GLM1})</h3>
 * <pre>
 *   INT  magic    = 0x474C4D31  ("GLM1")
 *   INT  version                (3 = legacy, 4 = current)
 *   INT  numLangs
 *   INT  cjkUnigramBuckets
 *   INT  cjkBigramBuckets
 *   INT  noncjkUnigramBuckets
 *   INT  noncjkBigramBuckets
 *   INT  noncjkTrigramBuckets
 *   INT  scriptCategories
 *   INT  wordBigramBuckets      (v4+ only; 0 absent in v3)
 *   For each language:
 *     SHORT  codeLen
 *     BYTES  langCode (UTF-8)
 *     BYTE   isCjk (0|1)
 *     FLOAT  scoreMean
 *     FLOAT  scoreStdDev
 *     BYTES  unigramTable  [cjkUnigramBuckets | noncjkUnigramBuckets]
 *     BYTES  bigramTable   [cjkBigramBuckets  | noncjkBigramBuckets]
 *     BYTES  trigramTable  [noncjkTrigramBuckets] (absent for CJK)
 *     BYTES  wordBigramTable [wordBigramBuckets] (v4+, absent for CJK)
 *     BYTES  scriptTable   [scriptCategories]
 * </pre>
 */
public class GenerativeLanguageModel {

    // ---- Bucket counts (v4/v5) ----

    public static final int CJK_UNIGRAM_BUCKETS     =  8_192;
    public static final int CJK_BIGRAM_BUCKETS      = 16_384;
    public static final int NONCJK_UNIGRAM_BUCKETS  =  4_096;
    public static final int NONCJK_BIGRAM_BUCKETS   =  8_192;
    public static final int NONCJK_TRIGRAM_BUCKETS  = 16_384;
    public static final int WORD_BIGRAM_BUCKETS      =  8_192;  // v4: new

    /**
     * Script categories used for the script distribution feature.
     * Matches {@link GlmScriptCategory#COUNT} at model-build time; the actual
     * count is stored in the binary so older v3 readers still work.
     */
    public static final int SCRIPT_CATEGORIES = GlmScriptCategory.COUNT;

    /** Default classpath resource for the bundled generative model. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect-generative-v4-20260320.bin";

    /**
     * Quantization floor.  Log-probabilities below this are clamped before
     * quantizing; stored values never go lower.
     */
    public static final float LOGP_MIN = -18.0f;

    private static final int MAGIC   = 0x474C4D31;  // "GLM1"
    private static final int VERSION = 4;

    // ---- FNV constants ----

    static final int FNV_BASIS = 0x811c9dc5;

    /** Positional salt bytes — same scheme as {@link SaltedNgramFeatureExtractor}. */
    static final int SALT_MID            = 0x00;
    static final int SALT_BOW            = 0x01;
    static final int SALT_EOW            = 0x02;
    static final int SALT_FULL_WORD      = 0x03;
    static final int SALT_CJK_UNIGRAM    = 0x04;
    static final int SALT_NONCJK_UNIGRAM = 0x05;
    static final int SALT_WORD_FWD       = 0x06;  // short-prev → any-next
    static final int SALT_WORD_BWD       = 0x07;  // any-prev → short-next

    /**
     * Maximum anchor-word length for word bigrams.
     * Words of 1–{@value} characters are treated as anchors.
     */
    static final int MAX_SHORT_WORD = 3;

    // ---- Legacy v3 constants (kept for backward-compatible loading only) ----

    /** @deprecated Used only when loading v3 models. */
    @Deprecated
    static final int BIGRAM_BASIS          = ScriptAwareFeatureExtractor.BIGRAM_BASIS;
    /** @deprecated Used only when loading v3 models. */
    @Deprecated
    static final int CJK_UNIGRAM_BASIS     = ScriptAwareFeatureExtractor.UNIGRAM_BASIS;
    /** @deprecated Used only when loading v3 models. */
    @Deprecated
    static final int NONCJK_UNIGRAM_BASIS  = 0x1a3f7c4e;
    /** @deprecated Used only when loading v3 models. */
    @Deprecated
    static final int TRIGRAM_BASIS         = 0x7e3d9b21;
    /** @deprecated Sentinel used in v3 n-gram extraction. */
    @Deprecated
    static final int SENTINEL              = '_';

    // ---- Model state ----

    private final int             modelVersion;
    private final List<String>    langIds;
    private final Map<String, Integer> langIndex;
    private final boolean[]       isCjk;
    private final byte[][]        unigramTables;
    private final byte[][]        bigramTables;
    private final byte[][]        trigramTables;
    private final byte[][]        wordBigramTables;   // null for v3 models
    private final byte[][]        scriptTables;
    private final int             loadedScriptCats;   // actual count from binary
    private final float[]         scoreMeans;
    private final float[]         scoreStdDevs;

    private GenerativeLanguageModel(
            int          modelVersion,
            List<String> langIds,
            boolean[]    isCjk,
            byte[][]     unigramTables,
            byte[][]     bigramTables,
            byte[][]     trigramTables,
            byte[][]     wordBigramTables,
            byte[][]     scriptTables,
            int          loadedScriptCats,
            float[]      scoreMeans,
            float[]      scoreStdDevs) {
        this.modelVersion      = modelVersion;
        this.langIds           = Collections.unmodifiableList(new ArrayList<>(langIds));
        this.isCjk             = isCjk;
        this.unigramTables     = unigramTables;
        this.bigramTables      = bigramTables;
        this.trigramTables     = trigramTables;
        this.wordBigramTables  = wordBigramTables;
        this.scriptTables      = scriptTables;
        this.loadedScriptCats  = loadedScriptCats;
        this.scoreMeans        = scoreMeans;
        this.scoreStdDevs      = scoreStdDevs;
        Map<String, Integer> idx = new HashMap<>(langIds.size() * 2);
        for (int i = 0; i < langIds.size(); i++) {
            idx.put(langIds.get(i), i);
        }
        this.langIndex = Collections.unmodifiableMap(idx);
    }

    // ---- Public API ----

    public List<String> getLanguages() {
        return langIds;
    }

    public boolean isCjk(String language) {
        Integer i = langIndex.get(language);
        return i != null && isCjk[i];
    }

    /**
     * Per-n-gram average log-probability of {@code text} under {@code language}.
     *
     * @return a value in [{@link #LOGP_MIN}, 0], or {@link Float#NaN} if the
     *         language is unknown or the text yields no scorable n-grams.
     */
    public float score(String text, String language) {
        if (text == null || text.isEmpty()) return Float.NaN;
        Integer li = langIndex.get(language);
        if (li == null) return Float.NaN;
        String pp = CharSoupFeatureExtractor.preprocess(text);
        if (pp.isEmpty()) return Float.NaN;

        double[] sum = {0.0};
        int[]    cnt = {0};

        if (modelVersion >= 4) {
            scoreV4(pp, li, sum, cnt);
        } else {
            scoreV3(pp, li, sum, cnt);
        }

        return cnt[0] == 0 ? Float.NaN : (float) (sum[0] / cnt[0]);
    }

    // ---- Scoring — v4 (salted n-grams + word bigrams + fine-grained script) ----

    private void scoreV4(String pp, int li, double[] sum, int[] cnt) {
        if (isCjk[li]) {
            byte[] uniT = unigramTables[li];
            byte[] biT  = bigramTables[li];
            extractCjkFeaturesV4(pp,
                    h -> { sum[0] += dequantize(uniT[h % uniT.length]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(biT[h % biT.length]);
                           cnt[0]++; });
        } else {
            byte[] uniT  = unigramTables[li];
            byte[] biT   = bigramTables[li];
            byte[] triT  = trigramTables[li];
            byte[] wbiT  = wordBigramTables != null ? wordBigramTables[li] : null;
            HashConsumer wbiSink = wbiT != null
                    ? h -> { sum[0] += dequantize(wbiT[h % wbiT.length]);
                             cnt[0]++; }
                    : null;
            extractNonCjkFeaturesV4(pp,
                    h -> { sum[0] += dequantize(uniT[h % uniT.length]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(biT[h % biT.length]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(triT[h % triT.length]);
                           cnt[0]++; },
                    wbiSink);
        }

        if (scriptTables != null && scriptTables[li] != null) {
            addScriptContributionsV4(pp, scriptTables[li], sum, cnt);
        }
    }

    // ---- Scoring — v3 (legacy sentinel n-grams) ----

    private void scoreV3(String pp, int li, double[] sum, int[] cnt) {
        if (isCjk[li]) {
            byte[] uniT = unigramTables[li];
            byte[] biT  = bigramTables[li];
            extractCjkNgrams(pp,
                    h -> { sum[0] += dequantize(uniT[h % CJK_UNIGRAM_BUCKETS]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(biT[h % 32_768]);
                           cnt[0]++; });
        } else {
            byte[] uniT = unigramTables[li];
            byte[] biT  = bigramTables[li];
            byte[] triT = trigramTables[li];
            extractNonCjkNgrams(pp,
                    h -> { sum[0] += dequantize(uniT[h % uniT.length]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(biT[h % biT.length]);
                           cnt[0]++; },
                    h -> { sum[0] += dequantize(triT[h % triT.length]);
                           cnt[0]++; });
        }

        if (scriptTables != null && scriptTables[li] != null) {
            addScriptContributionsV3(pp, scriptTables[li], sum, cnt);
        }
    }

    /**
     * Score {@code text} against all languages and return the best match.
     */
    public Map.Entry<String, Float> bestMatch(String text) {
        String best = null;
        float  bestScore = Float.NEGATIVE_INFINITY;
        for (String lang : langIds) {
            float s = score(text, lang);
            if (!Float.isNaN(s) && s > bestScore) {
                bestScore = s;
                best = lang;
            }
        }
        return best == null ? null : Map.entry(best, bestScore);
    }

    /**
     * Average raw score of {@code text} across all CJK languages in the model.
     */
    public float avgCjkScore(String text) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < langIds.size(); i++) {
            if (!isCjk[i]) continue;
            float s = score(text, langIds.get(i));
            if (!Float.isNaN(s)) {
                sum += s;
                count++;
            }
        }
        return count == 0 ? Float.NaN : (float) (sum / count);
    }

    // ---- Z-score API ----

    static final int CALIBRATION_CHAR_LENGTH  = 120;
    static final int MIN_ADJUSTED_CHAR_LENGTH = 10;

    public float zScore(String text, String language) {
        Integer li = langIndex.get(language);
        if (li == null || scoreStdDevs[li] <= 0.0f) return Float.NaN;
        float s = score(text, language);
        if (Float.isNaN(s)) return Float.NaN;
        return (s - scoreMeans[li]) / scoreStdDevs[li];
    }

    public float zScoreLengthAdjusted(String text, String language) {
        Integer li = langIndex.get(language);
        if (li == null || scoreStdDevs[li] <= 0.0f) return Float.NaN;
        float s = score(text, language);
        if (Float.isNaN(s)) return Float.NaN;
        int textLen = text.length();
        float adjustment = (float) Math.sqrt(
                (double) CALIBRATION_CHAR_LENGTH
                / Math.max(textLen, MIN_ADJUSTED_CHAR_LENGTH));
        float adjustedSigma = scoreStdDevs[li] * Math.max(1.0f, adjustment);
        return (s - scoreMeans[li]) / adjustedSigma;
    }

    public void setStats(String language, float mean, float stdDev) {
        Integer li = langIndex.get(language);
        if (li == null) throw new IllegalArgumentException("Unknown language: " + language);
        scoreMeans[li]   = mean;
        scoreStdDevs[li] = stdDev;
    }

    // ---- N-gram extraction: v4 (salted, word-buffer) ----

    /**
     * Callback receiving a non-negative FNV hash for a single feature.
     */
    @FunctionalInterface
    public interface HashConsumer {
        void consume(int hash);
    }

    /**
     * Extract CJK character unigrams and bigrams (v4: no script salt).
     */
    public static void extractCjkFeaturesV4(
            String text,
            HashConsumer unigramSink,
            HashConsumer bigramSink) {
        int prevCp = -1;
        int i = 0, len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                prevCp = -1;
                continue;
            }
            int lower = Character.toLowerCase(cp);
            if (!ScriptAwareFeatureExtractor.isCjkOrKana(lower)) {
                prevCp = -1;
                continue;
            }
            unigramSink.consume(hashV4(SALT_CJK_UNIGRAM, lower));
            if (prevCp >= 0) {
                bigramSink.consume(hashV4(SALT_MID, prevCp, lower));
            }
            prevCp = lower;
        }
    }

    /**
     * Extract non-CJK features (v4): per-letter unigrams (streaming) plus
     * word-buffer bigrams/trigrams with positional salt, and bidirectional
     * word bigrams.
     *
     * <p>A word is a maximal run of same-script non-CJK letter codepoints.
     * Script is determined by {@link GlmScriptCategory#of(int)}; codepoints
     * returning {@code -1} (unrecognized script) are treated as their own
     * single-character word so they don't pollute adjacent-word bigrams.
     *
     * @param wordBigramSink may be {@code null} to skip word-bigram features
     */
    public static void extractNonCjkFeaturesV4(
            String       text,
            HashConsumer unigramSink,
            HashConsumer bigramSink,
            HashConsumer trigramSink,
            HashConsumer wordBigramSink) {

        int[] word     = new int[256];
        int   wordLen  = 0;
        int   wordScript = -2;  // -2 = no word in progress; -1 = unrecognized script

        int[] prevWord    = new int[256];
        int   prevWordLen = 0;

        int i = 0, len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) continue;

            if (Character.isLetter(cp)) {
                int lower  = Character.toLowerCase(cp);

                // CJK breaks non-CJK word stream
                if (ScriptAwareFeatureExtractor.isCjkOrKana(lower)) {
                    if (wordLen > 0) {
                        prevWordLen = flushWordV4(word, wordLen, bigramSink, trigramSink,
                                wordBigramSink, prevWord, prevWordLen);
                        wordLen = 0;
                        wordScript = -2;
                    }
                    prevWordLen = 0;  // CJK breaks word-bigram chain
                    continue;
                }

                // Unigram: every non-CJK letter
                unigramSink.consume(hashV4(SALT_NONCJK_UNIGRAM, lower));

                int script = GlmScriptCategory.of(lower);

                // Script change (or unrecognized) = word boundary
                boolean sameScript = (wordScript != -2)
                        && (script == wordScript)
                        && (script != -1);  // unrecognized is always its own word
                if (wordLen > 0 && !sameScript) {
                    prevWordLen = flushWordV4(word, wordLen, bigramSink, trigramSink,
                            wordBigramSink, prevWord, prevWordLen);
                    wordLen = 0;
                }

                if (wordLen < word.length) {
                    word[wordLen++] = lower;
                    wordScript = script;
                }
            } else {
                // Non-letter: flush word
                if (wordLen > 0) {
                    prevWordLen = flushWordV4(word, wordLen, bigramSink, trigramSink,
                            wordBigramSink, prevWord, prevWordLen);
                    wordLen = 0;
                    wordScript = -2;
                }
            }
        }

        if (wordLen > 0) {
            flushWordV4(word, wordLen, bigramSink, trigramSink,
                    wordBigramSink, prevWord, prevWordLen);
        }
    }

    /**
     * Emit bigrams/trigrams for a completed word and handle word bigrams.
     *
     * @return the new prevWordLen (= wordLen, since this word becomes the new prev)
     */
    private static int flushWordV4(
            int[] word, int wordLen,
            HashConsumer bigramSink,
            HashConsumer trigramSink,
            HashConsumer wordBigramSink,
            int[] prevWord, int prevWordLen) {

        emitWordNgramsV4(word, wordLen, bigramSink, trigramSink);

        if (wordBigramSink != null) {
            // Forward: short prev → any current
            if (prevWordLen >= 1 && prevWordLen <= MAX_SHORT_WORD) {
                emitWordBigram(wordBigramSink, SALT_WORD_FWD,
                        prevWord, prevWordLen, word, wordLen);
            }
            // Backward: any prev → short current
            if (wordLen >= 1 && wordLen <= MAX_SHORT_WORD && prevWordLen > 0) {
                emitWordBigram(wordBigramSink, SALT_WORD_BWD,
                        prevWord, prevWordLen, word, wordLen);
            }
        }

        System.arraycopy(word, 0, prevWord, 0, wordLen);
        return wordLen;
    }

    /**
     * Emit positionally-salted bigrams and trigrams for a completed word.
     *
     * <p>For each n-gram order k ∈ {2, 3}:
     * <ul>
     *   <li>If wordLen == k: emit once with FULL_WORD salt.</li>
     *   <li>If wordLen > k: first n-gram gets BOW, last gets EOW, rest get MID.</li>
     * </ul>
     */
    static void emitWordNgramsV4(int[] word, int wordLen,
                                  HashConsumer bigramSink,
                                  HashConsumer trigramSink) {
        // Bigrams
        if (wordLen == 2) {
            bigramSink.consume(hashV4(SALT_FULL_WORD, word[0], word[1]));
        } else if (wordLen > 2) {
            bigramSink.consume(hashV4(SALT_BOW, word[0], word[1]));
            for (int j = 1; j < wordLen - 2; j++) {
                bigramSink.consume(hashV4(SALT_MID, word[j], word[j + 1]));
            }
            bigramSink.consume(hashV4(SALT_EOW, word[wordLen - 2], word[wordLen - 1]));
        }

        // Trigrams
        if (wordLen == 3) {
            trigramSink.consume(hashV4(SALT_FULL_WORD, word[0], word[1], word[2]));
        } else if (wordLen > 3) {
            trigramSink.consume(hashV4(SALT_BOW, word[0], word[1], word[2]));
            for (int j = 1; j < wordLen - 3; j++) {
                trigramSink.consume(hashV4(SALT_MID, word[j], word[j + 1], word[j + 2]));
            }
            trigramSink.consume(
                    hashV4(SALT_EOW, word[wordLen - 3], word[wordLen - 2], word[wordLen - 1]));
        }
    }

    /**
     * Emit a word bigram feature for (w1, w2) with the given directional salt.
     * A separator byte (0xFF) prevents collisions between, e.g., "ab"+"cd"
     * and "abc"+"d".
     */
    static void emitWordBigram(HashConsumer sink, int salt,
                                int[] w1, int w1Len,
                                int[] w2, int w2Len) {
        int h = fnvByte(FNV_BASIS, salt);
        for (int j = 0; j < w1Len; j++) h = fnvInt(h, w1[j]);
        h = fnvByte(h, 0xFF);
        for (int j = 0; j < w2Len; j++) h = fnvInt(h, w2[j]);
        sink.consume(h & 0x7FFFFFFF);
    }

    /**
     * Add per-letter script log-probability contributions (v4).
     * Uses {@link GlmScriptCategory}: unrecognized scripts (return value -1)
     * are silently skipped rather than falling into an OTHER bucket.
     */
    static void addScriptContributionsV4(String pp, byte[] scriptTable,
                                          double[] sum, int[] cnt) {
        // Count letters per script category
        int[] scriptCounts = new int[GlmScriptCategory.COUNT];
        int totalLetters = 0;
        int i = 0, len = pp.length();
        while (i < len) {
            int cp = pp.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            int script = GlmScriptCategory.of(Character.toLowerCase(cp));
            if (script >= 0 && script < scriptTable.length) {
                scriptCounts[script]++;
                totalLetters++;
            }
        }
        if (totalLetters == 0) return;
        // L1-normalize: one weighted contribution regardless of text length,
        // so script signal doesn't swamp n-gram signal on long text.
        double scriptScore = 0.0;
        for (int s = 0; s < scriptTable.length; s++) {
            if (scriptCounts[s] > 0) {
                scriptScore += (double) scriptCounts[s] / totalLetters
                        * dequantize(scriptTable[s]);
            }
        }
        sum[0] += scriptScore;
        cnt[0]++;
    }

    // ---- N-gram extraction: v3 (legacy sentinel-based, for old model loading) ----

    /** @deprecated Use v4 extraction for new models. */
    @Deprecated
    public static void extractCjkNgrams(
            String text, HashConsumer unigramSink, HashConsumer bigramSink) {
        int prevCp = -1;
        int i = 0, len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                prevCp = -1;
                continue;
            }
            int lower = Character.toLowerCase(cp);
            if (!ScriptAwareFeatureExtractor.isCjkOrKana(lower)) {
                prevCp = -1;
                continue;
            }
            int script = ScriptCategory.of(lower);
            unigramSink.consume(cjkUnigramHashV3(script, lower));
            if (prevCp >= 0) bigramSink.consume(bigramHashV3(script, prevCp, lower));
            prevCp = lower;
        }
    }

    /** @deprecated Use v4 extraction for new models. */
    @Deprecated
    public static void extractNonCjkNgrams(
            String text,
            HashConsumer unigramSink,
            HashConsumer bigramSink,
            HashConsumer trigramSink) {
        int prevPrev = SENTINEL, prev = SENTINEL, prevScript = -1;
        boolean inWord = false;

        int i = 0, len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) continue;

            if (Character.isLetter(cp)) {
                int lower = Character.toLowerCase(cp);
                if (ScriptAwareFeatureExtractor.isCjkOrKana(lower)) {
                    if (inWord) {
                        emitWordEndV3(prevScript, prevPrev, prev, bigramSink, trigramSink);
                        inWord = false;
                        prevPrev = SENTINEL;
                        prev = SENTINEL;
                        prevScript = -1;
                    }
                    continue;
                }
                int script = ScriptCategory.of(lower);
                if (inWord && script != prevScript) {
                    emitWordEndV3(prevScript, prevPrev, prev, bigramSink, trigramSink);
                    inWord = false;
                    prevPrev = SENTINEL;
                    prev = SENTINEL;
                }
                unigramSink.consume(noncjkUnigramHashV3(script, lower));
                if (!inWord) {
                    bigramSink.consume(bigramHashV3(script, SENTINEL, lower));
                    trigramSink.consume(trigramHashV3(script, SENTINEL, SENTINEL, lower));
                    prevPrev = SENTINEL;
                } else {
                    bigramSink.consume(bigramHashV3(script, prev, lower));
                    trigramSink.consume(trigramHashV3(script, prevPrev, prev, lower));
                    prevPrev = prev;
                }
                prev = lower;
                prevScript = script;
                inWord = true;
            } else {
                if (inWord) {
                    emitWordEndV3(prevScript, prevPrev, prev, bigramSink, trigramSink);
                    inWord = false;
                    prevPrev = SENTINEL;
                    prev = SENTINEL;
                    prevScript = -1;
                }
            }
        }
        if (inWord) emitWordEndV3(prevScript, prevPrev, prev, bigramSink, trigramSink);
    }

    private static void emitWordEndV3(int script, int pp, int p,
                                       HashConsumer biSink, HashConsumer triSink) {
        biSink.consume(bigramHashV3(script, p, SENTINEL));
        triSink.consume(trigramHashV3(script, pp, p, SENTINEL));
        triSink.consume(trigramHashV3(script, p, SENTINEL, SENTINEL));
    }

    /** v3 script contributions using {@link ScriptCategory} (includes OTHER). */
    static void addScriptContributionsV3(String pp, byte[] scriptTable,
                                          double[] sum, int[] cnt) {
        int i = 0, len = pp.length();
        while (i < len) {
            int cp = pp.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) continue;
            int script = ScriptCategory.of(Character.toLowerCase(cp));
            if (script < scriptTable.length) {
                sum[0] += dequantize(scriptTable[script]);
                cnt[0]++;
            }
        }
    }

    // ---- Hash functions ----

    /** FNV-1a hash: salt byte then one codepoint. */
    static int hashV4(int salt, int cp1) {
        int h = fnvByte(FNV_BASIS, salt);
        h = fnvInt(h, cp1);
        return h & 0x7FFFFFFF;
    }

    /** FNV-1a hash: salt byte then two codepoints. */
    static int hashV4(int salt, int cp1, int cp2) {
        int h = fnvByte(FNV_BASIS, salt);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        return h & 0x7FFFFFFF;
    }

    /** FNV-1a hash: salt byte then three codepoints. */
    static int hashV4(int salt, int cp1, int cp2, int cp3) {
        int h = fnvByte(FNV_BASIS, salt);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        h = fnvInt(h, cp3);
        return h & 0x7FFFFFFF;
    }

    // ---- v3 hash functions (kept for backward-compatible scoring) ----

    @Deprecated static int cjkUnigramHashV3(int script, int cp) {
        int h = CJK_UNIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp);
        return h & 0x7FFFFFFF;
    }
    @Deprecated static int noncjkUnigramHashV3(int script, int cp) {
        int h = NONCJK_UNIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp);
        return h & 0x7FFFFFFF;
    }
    @Deprecated static int bigramHashV3(int script, int cp1, int cp2) {
        int h = BIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        return h & 0x7FFFFFFF;
    }
    @Deprecated static int trigramHashV3(int script, int cp1, int cp2, int cp3) {
        int h = TRIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        h = fnvInt(h, cp3);
        return h & 0x7FFFFFFF;
    }

    // ---- FNV-1a primitives ----

    static int fnvByte(int h, int b) {
        return (h ^ (b & 0xFF)) * 0x01000193;
    }

    static int fnvInt(int h, int v) {
        h = (h ^ (v         & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>>  8) & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>> 16) & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>> 24) & 0xFF)) * 0x01000193;
        return h;
    }

    // ---- Quantization ----

    static byte quantize(float logP) {
        float clamped = Math.max(LOGP_MIN, Math.min(0.0f, logP));
        return (byte) Math.round((clamped - LOGP_MIN) / (-LOGP_MIN) * 255.0f);
    }

    static float dequantize(byte b) {
        return (b & 0xFF) / 255.0f * (-LOGP_MIN) + LOGP_MIN;
    }

    // ---- Serialization ----

    public static GenerativeLanguageModel loadFromClasspath(String resourcePath)
            throws IOException {
        try (InputStream is = GenerativeLanguageModel.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Classpath resource not found: " + resourcePath);
            return load(is);
        }
    }

    public static GenerativeLanguageModel load(InputStream is) throws IOException {
        DataInputStream din = new DataInputStream(new BufferedInputStream(is));

        int magic = din.readInt();
        if (magic != MAGIC) throw new IOException("Not a GLM1 file (bad magic)");
        int version = din.readInt();
        if (version < 1 || version > VERSION) {
            throw new IOException("Unsupported GLM version: " + version);
        }

        boolean hasStats      = version >= 2;
        boolean hasScript     = version >= 3;
        boolean hasWordBigram = version >= 4;

        int numLangs    = din.readInt();
        int cjkUni      = din.readInt();
        int cjkBi       = din.readInt();
        int noncjkUni   = din.readInt();
        int noncjkBi    = din.readInt();
        int noncjkTri   = din.readInt();
        int scriptCats  = hasScript     ? din.readInt() : 0;
        int wordBiBkts  = hasWordBigram ? din.readInt() : 0;

        List<String> langIds      = new ArrayList<>(numLangs);
        boolean[]    isCjkArr     = new boolean[numLangs];
        byte[][]     unigramTbls  = new byte[numLangs][];
        byte[][]     bigramTbls   = new byte[numLangs][];
        byte[][]     trigramTbls  = new byte[numLangs][];
        byte[][]     wordBiTbls   = hasWordBigram ? new byte[numLangs][] : null;
        byte[][]     scriptTbls   = hasScript     ? new byte[numLangs][] : null;
        float[]      means        = new float[numLangs];
        float[]      stdDevs      = new float[numLangs];

        for (int i = 0; i < numLangs; i++) {
            int    codeLen   = din.readUnsignedShort();
            byte[] codeBytes = new byte[codeLen];
            din.readFully(codeBytes);
            langIds.add(new String(codeBytes, StandardCharsets.UTF_8));

            isCjkArr[i] = din.readByte() != 0;

            if (hasStats) {
                means[i]   = din.readFloat();
                stdDevs[i] = din.readFloat();
            }

            int uniSize = isCjkArr[i] ? cjkUni : noncjkUni;
            int biSize  = isCjkArr[i] ? cjkBi  : noncjkBi;

            unigramTbls[i] = new byte[uniSize];
            din.readFully(unigramTbls[i]);
            bigramTbls[i] = new byte[biSize];
            din.readFully(bigramTbls[i]);

            if (!isCjkArr[i]) {
                trigramTbls[i] = new byte[noncjkTri];
                din.readFully(trigramTbls[i]);
            }

            if (hasWordBigram && !isCjkArr[i] && wordBiBkts > 0) {
                wordBiTbls[i] = new byte[wordBiBkts];
                din.readFully(wordBiTbls[i]);
            }

            if (hasScript) {
                scriptTbls[i] = new byte[scriptCats];
                din.readFully(scriptTbls[i]);
            }
        }

        return new GenerativeLanguageModel(version, langIds, isCjkArr,
                unigramTbls, bigramTbls, trigramTbls, wordBiTbls,
                scriptTbls, scriptCats, means, stdDevs);
    }

    public void save(OutputStream os) throws IOException {
        DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(os));

        // Compute actual bucket sizes from the stored tables so that round-tripping
        // a model loaded from an older binary preserves the original table dimensions.
        int saveCjkUni = CJK_UNIGRAM_BUCKETS, saveCjkBi = CJK_BIGRAM_BUCKETS;
        int saveNoncjkUni = NONCJK_UNIGRAM_BUCKETS, saveNoncjkBi = NONCJK_BIGRAM_BUCKETS;
        int saveNoncjkTri = NONCJK_TRIGRAM_BUCKETS;
        int saveWordBi  = WORD_BIGRAM_BUCKETS;
        int saveScript  = SCRIPT_CATEGORIES;
        boolean foundCjk = false, foundNoncjk = false;
        for (int i = 0; i < langIds.size(); i++) {
            if (!foundCjk && isCjk[i] && unigramTables[i] != null) {
                saveCjkUni = unigramTables[i].length;
                saveCjkBi  = bigramTables[i].length;
                foundCjk = true;
            }
            if (!foundNoncjk && !isCjk[i] && unigramTables[i] != null) {
                saveNoncjkUni = unigramTables[i].length;
                saveNoncjkBi  = bigramTables[i].length;
                if (trigramTables[i] != null) saveNoncjkTri = trigramTables[i].length;
                if (wordBigramTables != null && wordBigramTables[i] != null)
                    saveWordBi = wordBigramTables[i].length;
                foundNoncjk = true;
            }
            if (scriptTables != null && scriptTables[i] != null && (i == 0 || saveScript == SCRIPT_CATEGORIES))
                saveScript = scriptTables[i].length;
            if (foundCjk && foundNoncjk) break;
        }

        dout.writeInt(MAGIC);
        dout.writeInt(VERSION);
        dout.writeInt(langIds.size());
        dout.writeInt(saveCjkUni);
        dout.writeInt(saveCjkBi);
        dout.writeInt(saveNoncjkUni);
        dout.writeInt(saveNoncjkBi);
        dout.writeInt(saveNoncjkTri);
        dout.writeInt(saveScript);
        dout.writeInt(saveWordBi);

        for (int i = 0; i < langIds.size(); i++) {
            byte[] codeBytes = langIds.get(i).getBytes(StandardCharsets.UTF_8);
            dout.writeShort(codeBytes.length);
            dout.write(codeBytes);
            dout.writeByte(isCjk[i] ? 1 : 0);
            dout.writeFloat(scoreMeans[i]);
            dout.writeFloat(scoreStdDevs[i]);
            dout.write(unigramTables[i]);
            dout.write(bigramTables[i]);
            if (!isCjk[i]) {
                dout.write(trigramTables[i]);
                // Word bigrams: write table or zeros if absent
                if (wordBigramTables != null && wordBigramTables[i] != null) {
                    dout.write(wordBigramTables[i]);
                } else {
                    dout.write(new byte[saveWordBi]);
                }
            }
            if (scriptTables != null && scriptTables[i] != null) {
                dout.write(scriptTables[i]);
            } else {
                dout.write(new byte[saveScript]);
            }
        }
        dout.flush();
    }

    // ---- Builder ----

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Accumulates training samples per language and produces a
     * {@link GenerativeLanguageModel} via add-k smoothing.
     */
    public static class Builder {

        private final Map<String, Boolean> cjkFlags         = new LinkedHashMap<>();
        private final Map<String, long[]>  unigramCounts    = new HashMap<>();
        private final Map<String, long[]>  bigramCounts     = new HashMap<>();
        private final Map<String, long[]>  trigramCounts    = new HashMap<>();
        private final Map<String, long[]>  wordBigramCounts = new HashMap<>();
        private final Map<String, long[]>  scriptCounts     = new HashMap<>();

        public Builder registerLanguage(String langCode, boolean isCjk) {
            cjkFlags.put(langCode, isCjk);
            unigramCounts.put(langCode,
                    new long[isCjk ? CJK_UNIGRAM_BUCKETS : NONCJK_UNIGRAM_BUCKETS]);
            bigramCounts.put(langCode,
                    new long[isCjk ? CJK_BIGRAM_BUCKETS  : NONCJK_BIGRAM_BUCKETS]);
            if (!isCjk) {
                trigramCounts.put(langCode, new long[NONCJK_TRIGRAM_BUCKETS]);
                wordBigramCounts.put(langCode, new long[WORD_BIGRAM_BUCKETS]);
            }
            scriptCounts.put(langCode, new long[SCRIPT_CATEGORIES]);
            return this;
        }

        public Builder addSample(String langCode, String text) {
            Boolean cjk = cjkFlags.get(langCode);
            if (cjk == null) throw new IllegalArgumentException("Unknown language: " + langCode);
            String pp = CharSoupFeatureExtractor.preprocess(text);
            if (pp.isEmpty()) return this;

            long[] ug = unigramCounts.get(langCode);
            long[] bg = bigramCounts.get(langCode);

            if (cjk) {
                extractCjkFeaturesV4(pp,
                        h -> ug[h % CJK_UNIGRAM_BUCKETS]++,
                        h -> bg[h % CJK_BIGRAM_BUCKETS]++);
            } else {
                long[] tg  = trigramCounts.get(langCode);
                long[] wbg = wordBigramCounts.get(langCode);
                extractNonCjkFeaturesV4(pp,
                        h -> ug[h  % NONCJK_UNIGRAM_BUCKETS]++,
                        h -> bg[h  % NONCJK_BIGRAM_BUCKETS]++,
                        h -> tg[h  % NONCJK_TRIGRAM_BUCKETS]++,
                        h -> wbg[h % WORD_BIGRAM_BUCKETS]++);
            }

            accumulateScriptCounts(pp, scriptCounts.get(langCode));
            return this;
        }

        public GenerativeLanguageModel build(float addK) {
            List<String> ids = new ArrayList<>(cjkFlags.keySet());
            int n = ids.size();

            boolean[] cjkArr     = new boolean[n];
            byte[][]  uniTables  = new byte[n][];
            byte[][]  biTables   = new byte[n][];
            byte[][]  triTables  = new byte[n][];
            byte[][]  wbiTables  = new byte[n][];
            byte[][]  scriptTbls = new byte[n][];

            for (int i = 0; i < n; i++) {
                String lang = ids.get(i);
                cjkArr[i]     = cjkFlags.get(lang);
                uniTables[i]  = toLogProbTable(unigramCounts.get(lang),   addK);
                biTables[i]   = toLogProbTable(bigramCounts.get(lang),    addK);
                if (!cjkArr[i]) {
                    triTables[i] = toLogProbTable(trigramCounts.get(lang),    addK);
                    wbiTables[i] = toLogProbTable(wordBigramCounts.get(lang), addK);
                }
                scriptTbls[i] = toLogProbTable(scriptCounts.get(lang), addK);
            }
            return new GenerativeLanguageModel(VERSION, ids, cjkArr,
                    uniTables, biTables, triTables, wbiTables,
                    scriptTbls, SCRIPT_CATEGORIES,
                    new float[n], new float[n]);
        }

        private static byte[] toLogProbTable(long[] counts, float addK) {
            long total = 0;
            for (long c : counts) total += c;
            double denom = total + (double) addK * counts.length;
            byte[] table = new byte[counts.length];
            for (int i = 0; i < counts.length; i++) {
                double p = (counts[i] + addK) / denom;
                table[i] = quantize((float) Math.log(p));
            }
            return table;
        }

        private static void accumulateScriptCounts(String pp, long[] dest) {
            int i = 0, len = pp.length();
            while (i < len) {
                int cp = pp.codePointAt(i);
                i += Character.charCount(cp);
                if (!Character.isLetter(cp)) continue;
                int script = GlmScriptCategory.of(Character.toLowerCase(cp));
                if (script >= 0 && script < dest.length) {
                    dest[script]++;
                }
                // script == -1 (unrecognized): silently skipped — no OTHER bucket
            }
        }

    }
}
