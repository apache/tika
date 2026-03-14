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
 * language.  The score is used to arbitrate between candidate charsets when
 * statistical decoders disagree on script or language.
 *
 * <h3>Feature types</h3>
 * <ul>
 *   <li><b>CJK languages</b> (Han, Hiragana, Katakana): character unigrams
 *       and bigrams extracted from CJK/kana codepoints.</li>
 *   <li><b>Non-CJK languages</b>: character unigrams, bigrams (with
 *       word-boundary sentinels), and trigrams (with sentinels).</li>
 * </ul>
 *
 * <p>Log-probabilities are quantized to unsigned INT8 over the range
 * [{@link #LOGP_MIN}, 0] and stored in dense byte arrays.
 *
 * <h3>Binary format ({@code GLM1} v2)</h3>
 * <pre>
 *   INT  magic    = 0x474C4D31
 *   INT  version  = 2
 *   INT  numLangs
 *   INT  cjkUnigramBuckets
 *   INT  cjkBigramBuckets
 *   INT  noncjkUnigramBuckets
 *   INT  noncjkBigramBuckets
 *   INT  noncjkTrigramBuckets
 *   For each language:
 *     SHORT  codeLen
 *     BYTES  langCode (UTF-8)
 *     BYTE   isCjk (0|1)
 *     FLOAT  scoreMean   (μ of score distribution on training data)
 *     FLOAT  scoreStdDev (σ of score distribution on training data)
 *     BYTES  unigramTable  [cjkUnigramBuckets | noncjkUnigramBuckets]
 *     BYTES  bigramTable   [cjkBigramBuckets  | noncjkBigramBuckets]
 *     BYTES  trigramTable  [noncjkTrigramBuckets] (absent for CJK)
 * </pre>
 */
public class GenerativeLanguageModel {

    // ---- Bucket counts ----

    public static final int CJK_UNIGRAM_BUCKETS    =  8_192;
    public static final int CJK_BIGRAM_BUCKETS     = 32_768;
    public static final int NONCJK_UNIGRAM_BUCKETS =  8_192;
    public static final int NONCJK_BIGRAM_BUCKETS  =  8_192;
    public static final int NONCJK_TRIGRAM_BUCKETS = 16_384;

    /** Default classpath resource path for the bundled generative model. */
    public static final String DEFAULT_MODEL_RESOURCE =
            "/org/apache/tika/langdetect/charsoup/langdetect-generative-v1-20260310.bin";

    /**
     * Quantization floor.  Log-probabilities below this value are clamped
     * before quantizing; values stored in the table never go lower.
     */
    public static final float LOGP_MIN = -18.0f;

    private static final int MAGIC   = 0x474C4D31; // "GLM1"
    private static final int VERSION = 2;

    // ---- FNV-1a basis constants ----

    /**
     * Bigram basis shared with {@link ScriptAwareFeatureExtractor} so that
     * identical text produces the same bucket indices for both models.
     */
    static final int BIGRAM_BASIS         = ScriptAwareFeatureExtractor.BIGRAM_BASIS;

    /**
     * CJK unigram basis shared with {@link ScriptAwareFeatureExtractor}.
     */
    static final int CJK_UNIGRAM_BASIS    = ScriptAwareFeatureExtractor.UNIGRAM_BASIS;

    /** Distinct salt for non-CJK character unigrams (not in discriminative model). */
    static final int NONCJK_UNIGRAM_BASIS = 0x1a3f7c4e;

    /** Distinct salt for character trigrams (not in discriminative model). */
    static final int TRIGRAM_BASIS        = 0x7e3d9b21;

    /** Word-boundary sentinel codepoint, matching the discriminative model. */
    static final int SENTINEL = '_';

    // ---- Model state ----

    private final List<String>         langIds;
    private final Map<String, Integer> langIndex;
    private final boolean[]  isCjk;
    private final byte[][]   unigramTables;   // [langIdx][bucket]
    private final byte[][]   bigramTables;    // [langIdx][bucket]
    private final byte[][]   trigramTables;   // [langIdx][bucket]; null entry for CJK langs
    private final float[]    scoreMeans;      // μ per language (from training data)
    private final float[]    scoreStdDevs;    // σ per language (from training data)

    private GenerativeLanguageModel(
            List<String> langIds,
            boolean[]    isCjk,
            byte[][]     unigramTables,
            byte[][]     bigramTables,
            byte[][]     trigramTables,
            float[]      scoreMeans,
            float[]      scoreStdDevs) {
        this.langIds       = Collections.unmodifiableList(new ArrayList<>(langIds));
        this.isCjk         = isCjk;
        this.unigramTables = unigramTables;
        this.bigramTables  = bigramTables;
        this.trigramTables = trigramTables;
        this.scoreMeans    = scoreMeans;
        this.scoreStdDevs  = scoreStdDevs;
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
        if (text == null || text.isEmpty()) {
            return Float.NaN;
        }
        Integer li = langIndex.get(language);
        if (li == null) {
            return Float.NaN;
        }
        String preprocessed = CharSoupFeatureExtractor.preprocess(text);
        if (preprocessed.isEmpty()) {
            return Float.NaN;
        }

        double[] sum = {0.0};
        int[]    cnt = {0};

        if (isCjk[li]) {
            byte[] uniT = unigramTables[li];
            byte[] biT  = bigramTables[li];
            extractCjkNgrams(preprocessed,
                h -> {
                    sum[0] += dequantize(uniT[h % CJK_UNIGRAM_BUCKETS]);
                    cnt[0]++;
                },
                h -> {
                    sum[0] += dequantize(biT[h % CJK_BIGRAM_BUCKETS]);
                    cnt[0]++;
                });
        } else {
            byte[] uniT = unigramTables[li];
            byte[] biT  = bigramTables[li];
            byte[] triT = trigramTables[li];
            extractNonCjkNgrams(preprocessed,
                h -> {
                    sum[0] += dequantize(uniT[h % NONCJK_UNIGRAM_BUCKETS]);
                    cnt[0]++;
                },
                h -> {
                    sum[0] += dequantize(biT[h % NONCJK_BIGRAM_BUCKETS]);
                    cnt[0]++;
                },
                h -> {
                    sum[0] += dequantize(triT[h % NONCJK_TRIGRAM_BUCKETS]);
                    cnt[0]++;
                });
        }

        return cnt[0] == 0 ? Float.NaN : (float) (sum[0] / cnt[0]);
    }

    /**
     * Score {@code text} against all languages and return the best match.
     *
     * @return an entry {@code (languageCode, score)}, or {@code null} if no
     *         language yields a finite score.
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
     *
     * <p>Unlike {@link #bestMatch}, which picks the single best language
     * globally (often a non-CJK language when ASCII dominates short CJK
     * filenames), this method evaluates the text only through CJK-trained
     * n-gram tables.  Comparing average CJK scores across different charset
     * decodings of the same raw bytes reveals which decoding produces more
     * natural CJK characters — a real word like 文章 consistently scores
     * higher than hash-ghost gibberish like 訜覧 across all CJK models.</p>
     *
     * @return the average score across CJK languages, or {@link Float#NaN}
     *         if no CJK language yields a finite score.
     */
    public float avgCjkScore(String text) {
        double sum = 0;
        int count = 0;
        for (int i = 0; i < langIds.size(); i++) {
            if (!isCjk[i]) {
                continue;
            }
            float s = score(text, langIds.get(i));
            if (!Float.isNaN(s)) {
                sum += s;
                count++;
            }
        }
        return count == 0 ? Float.NaN : (float) (sum / count);
    }

    /**
     * Z-score of {@code text} under {@code language}:
     * {@code (score(text, language) - μ) / σ}, where μ and σ were computed
     * from the language's training corpus.
     *
     * <p>Appropriate when the input text is roughly the same length as
     * training sentences.  For short or variable-length text, prefer
     * {@link #zScoreLengthAdjusted}.
     *
     * @return the z-score, or {@link Float#NaN} if the language is unknown,
     *         the text yields no scorable n-grams, or σ is zero/uncalibrated.
     */
    public float zScore(String text, String language) {
        Integer li = langIndex.get(language);
        if (li == null || scoreStdDevs[li] <= 0.0f) {
            return Float.NaN;
        }
        float s = score(text, language);
        if (Float.isNaN(s)) {
            return Float.NaN;
        }
        return (s - scoreMeans[li]) / scoreStdDevs[li];
    }

    /**
     * Approximate character length of a typical training sentence.
     * Used by {@link #zScoreLengthAdjusted} to inflate σ for short text.
     * Empirically derived from the calibration data: score σ scales as
     * roughly 1/√(charLen) and stabilises around this length.
     */
    static final int CALIBRATION_CHAR_LENGTH = 120;

    /** Floor on text length to avoid extreme σ inflation. */
    static final int MIN_ADJUSTED_CHAR_LENGTH = 10;

    /**
     * Length-adjusted z-score of {@code text} under {@code language}.
     *
     * <p>Score variance scales as approximately 1/√(textLength).  The
     * stored σ was calibrated on full training sentences (typically
     * ~{@value #CALIBRATION_CHAR_LENGTH} characters).  For shorter text
     * this method inflates σ proportionally, preventing spurious low
     * z-scores on short snippets.  For text at or above the calibration
     * length, the result equals {@link #zScore}.
     *
     * @return the adjusted z-score, or {@link Float#NaN} if the language
     *         is unknown, the text yields no scorable n-grams, or σ is
     *         zero/uncalibrated.
     */
    public float zScoreLengthAdjusted(String text, String language) {
        Integer li = langIndex.get(language);
        if (li == null || scoreStdDevs[li] <= 0.0f) {
            return Float.NaN;
        }
        float s = score(text, language);
        if (Float.isNaN(s)) {
            return Float.NaN;
        }
        int textLen = text.length();
        float adjustment = (float) Math.sqrt(
                (double) CALIBRATION_CHAR_LENGTH
                / Math.max(textLen, MIN_ADJUSTED_CHAR_LENGTH));
        float adjustedSigma = scoreStdDevs[li] * Math.max(1.0f, adjustment);
        return (s - scoreMeans[li]) / adjustedSigma;
    }

    /**
     * Set the calibration statistics for a language. Typically called by
     * the training tool after a second pass over the training corpus.
     */
    public void setStats(String language, float mean, float stdDev) {
        Integer li = langIndex.get(language);
        if (li == null) {
            throw new IllegalArgumentException("Unknown language: " + language);
        }
        scoreMeans[li]   = mean;
        scoreStdDevs[li] = stdDev;
    }

    // ---- N-gram extraction (shared by scoring and training) ----

    /**
     * Callback receiving a non-negative raw FNV hash for a single n-gram.
     * The caller is responsible for reducing it modulo a table size.
     */
    @FunctionalInterface
    public interface HashConsumer {
        void consume(int hash);
    }

    /**
     * Extract CJK character unigrams and bigrams from preprocessed text,
     * delivering raw (positive) hashes to the supplied sinks.
     */
    public static void extractCjkNgrams(
            String text,
            HashConsumer unigramSink,
            HashConsumer bigramSink) {
        int prevCp = -1;
        int i = 0;
        int len = text.length();
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
            unigramSink.consume(cjkUnigramHash(script, lower));
            if (prevCp >= 0) {
                bigramSink.consume(bigramHash(script, prevCp, lower));
            }
            prevCp = lower;
        }
    }

    /**
     * Extract non-CJK character unigrams, sentinel-padded bigrams, and
     * sentinel-padded trigrams from preprocessed text.
     *
     * <p>A "word" is a maximal run of non-CJK letter codepoints within the
     * same script family. Sentinels ({@link #SENTINEL}) pad each word on
     * both sides, so a word of length L yields L+1 bigrams and L+2 trigrams.
     */
    public static void extractNonCjkNgrams(
            String text,
            HashConsumer unigramSink,
            HashConsumer bigramSink,
            HashConsumer trigramSink) {
        int  prevPrev  = SENTINEL;
        int  prev      = SENTINEL;
        int  prevScript = -1;
        boolean inWord = false;

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (cp >= 0x0300 && CharSoupFeatureExtractor.isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                int lower  = Character.toLowerCase(cp);
                if (ScriptAwareFeatureExtractor.isCjkOrKana(lower)) {
                    if (inWord) {
                        emitWordEnd(prevScript, prevPrev, prev, bigramSink, trigramSink);
                        inWord = false;
                        prevPrev = SENTINEL;
                        prev = SENTINEL;
                        prevScript = -1;
                    }
                    continue;
                }
                int script = ScriptCategory.of(lower);

                if (inWord && script != prevScript) {
                    // Script change is a word boundary
                    emitWordEnd(prevScript, prevPrev, prev, bigramSink, trigramSink);
                    inWord = false;
                    prevPrev = SENTINEL;
                    prev = SENTINEL;
                }

                unigramSink.consume(noncjkUnigramHash(script, lower));

                if (!inWord) {
                    // Leading sentinels
                    bigramSink.consume(bigramHash(script, SENTINEL, lower));
                    trigramSink.consume(trigramHash(script, SENTINEL, SENTINEL, lower));
                    prevPrev = SENTINEL;
                } else {
                    bigramSink.consume(bigramHash(script, prev, lower));
                    trigramSink.consume(trigramHash(script, prevPrev, prev, lower));
                    prevPrev = prev;
                }
                prev = lower;
                prevScript = script;
                inWord = true;
            } else {
                if (inWord) {
                    emitWordEnd(prevScript, prevPrev, prev, bigramSink, trigramSink);
                    inWord = false;
                    prevPrev = SENTINEL;
                    prev = SENTINEL;
                    prevScript = -1;
                }
            }
        }

        if (inWord) {
            emitWordEnd(prevScript, prevPrev, prev, bigramSink, trigramSink);
        }
    }

    private static void emitWordEnd(
            int script, int pp, int p,
            HashConsumer bigramSink, HashConsumer trigramSink) {
        bigramSink.consume(bigramHash(script, p, SENTINEL));
        trigramSink.consume(trigramHash(script, pp, p, SENTINEL));
        trigramSink.consume(trigramHash(script, p, SENTINEL, SENTINEL));
    }

    // ---- Hash functions (FNV-1a) ----

    static int cjkUnigramHash(int script, int cp) {
        int h = CJK_UNIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp);
        return h & 0x7FFFFFFF;
    }

    static int noncjkUnigramHash(int script, int cp) {
        int h = NONCJK_UNIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp);
        return h & 0x7FFFFFFF;
    }

    static int bigramHash(int script, int cp1, int cp2) {
        int h = BIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        return h & 0x7FFFFFFF;
    }

    static int trigramHash(int script, int cp1, int cp2, int cp3) {
        int h = TRIGRAM_BASIS;
        h = fnvByte(h, script);
        h = fnvInt(h, cp1);
        h = fnvInt(h, cp2);
        h = fnvInt(h, cp3);
        return h & 0x7FFFFFFF;
    }

    private static int fnvByte(int h, int b) {
        return (h ^ (b & 0xFF)) * 0x01000193;
    }

    private static int fnvInt(int h, int v) {
        h = (h ^ (v         & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>>  8) & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>> 16) & 0xFF)) * 0x01000193;
        h = (h ^ ((v >>> 24) & 0xFF)) * 0x01000193;
        return h;
    }

    // ---- Quantization ----

    /**
     * Quantize a log-probability in [{@link #LOGP_MIN}, 0] to an unsigned byte
     * value: 0 maps to {@code LOGP_MIN}, 255 maps to 0.
     */
    static byte quantize(float logP) {
        float clamped = Math.max(LOGP_MIN, Math.min(0.0f, logP));
        return (byte) Math.round((clamped - LOGP_MIN) / (-LOGP_MIN) * 255.0f);
    }

    /** Inverse of {@link #quantize}. */
    static float dequantize(byte b) {
        return (b & 0xFF) / 255.0f * (-LOGP_MIN) + LOGP_MIN;
    }

    // ---- Serialization ----

    /**
     * Load a model from a classpath resource.
     *
     * @param resourcePath absolute classpath path, e.g.
     *        {@code "/org/apache/tika/langdetect/charsoup/langdetect-generative-v1-20260310.bin"}
     * @return the loaded model
     * @throws IOException if the resource is missing or malformed
     */
    public static GenerativeLanguageModel loadFromClasspath(String resourcePath)
            throws IOException {
        try (InputStream is = GenerativeLanguageModel.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return load(is);
        }
    }

    /**
     * Deserialize a model from the GLM1 binary format.
     */
    public static GenerativeLanguageModel load(InputStream is) throws IOException {
        DataInputStream din = new DataInputStream(new BufferedInputStream(is));

        int magic = din.readInt();
        if (magic != MAGIC) {
            throw new IOException("Not a GLM1 file (bad magic)");
        }
        int version = din.readInt();
        if (version != 1 && version != VERSION) {
            throw new IOException("Unsupported GLM version: " + version);
        }
        boolean hasStats = version >= 2;

        int numLangs        = din.readInt();
        int cjkUni          = din.readInt();
        int cjkBi           = din.readInt();
        int noncjkUni       = din.readInt();
        int noncjkBi        = din.readInt();
        int noncjkTri       = din.readInt();

        List<String> langIds      = new ArrayList<>(numLangs);
        boolean[]    isCjk        = new boolean[numLangs];
        byte[][]     unigramTables = new byte[numLangs][];
        byte[][]     bigramTables  = new byte[numLangs][];
        byte[][]     trigramTables = new byte[numLangs][];
        float[]      means        = new float[numLangs];
        float[]      stdDevs      = new float[numLangs];

        for (int i = 0; i < numLangs; i++) {
            int    codeLen   = din.readUnsignedShort();
            byte[] codeBytes = new byte[codeLen];
            din.readFully(codeBytes);
            langIds.add(new String(codeBytes, StandardCharsets.UTF_8));

            isCjk[i] = din.readByte() != 0;

            if (hasStats) {
                means[i]   = din.readFloat();
                stdDevs[i] = din.readFloat();
            }

            int uniSize = isCjk[i] ? cjkUni    : noncjkUni;
            int biSize  = isCjk[i] ? cjkBi     : noncjkBi;

            unigramTables[i] = new byte[uniSize];
            din.readFully(unigramTables[i]);

            bigramTables[i] = new byte[biSize];
            din.readFully(bigramTables[i]);

            if (!isCjk[i]) {
                trigramTables[i] = new byte[noncjkTri];
                din.readFully(trigramTables[i]);
            }
        }

        return new GenerativeLanguageModel(langIds, isCjk,
                unigramTables, bigramTables, trigramTables,
                means, stdDevs);
    }

    /**
     * Serialize this model to the GLM1 binary format.
     */
    public void save(OutputStream os) throws IOException {
        DataOutputStream dout = new DataOutputStream(new BufferedOutputStream(os));

        dout.writeInt(MAGIC);
        dout.writeInt(VERSION);
        dout.writeInt(langIds.size());
        dout.writeInt(CJK_UNIGRAM_BUCKETS);
        dout.writeInt(CJK_BIGRAM_BUCKETS);
        dout.writeInt(NONCJK_UNIGRAM_BUCKETS);
        dout.writeInt(NONCJK_BIGRAM_BUCKETS);
        dout.writeInt(NONCJK_TRIGRAM_BUCKETS);

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

        private final Map<String, Boolean> cjkFlags      = new LinkedHashMap<>();
        private final Map<String, long[]>  unigramCounts = new HashMap<>();
        private final Map<String, long[]>  bigramCounts  = new HashMap<>();
        private final Map<String, long[]>  trigramCounts = new HashMap<>();

        /**
         * Register a language before feeding it samples.  Must be called
         * before {@link #addSample(String, String)}.
         */
        public Builder registerLanguage(String langCode, boolean isCjk) {
            cjkFlags.put(langCode, isCjk);
            unigramCounts.put(langCode,
                    new long[isCjk ? CJK_UNIGRAM_BUCKETS : NONCJK_UNIGRAM_BUCKETS]);
            bigramCounts.put(langCode,
                    new long[isCjk ? CJK_BIGRAM_BUCKETS  : NONCJK_BIGRAM_BUCKETS]);
            if (!isCjk) {
                trigramCounts.put(langCode, new long[NONCJK_TRIGRAM_BUCKETS]);
            }
            return this;
        }

        /**
         * Add a text sample for the named language.  The language must have
         * been registered via {@link #registerLanguage} first.
         */
        public Builder addSample(String langCode, String text) {
            Boolean cjk = cjkFlags.get(langCode);
            if (cjk == null) {
                throw new IllegalArgumentException("Unknown language: " + langCode);
            }
            String pp = CharSoupFeatureExtractor.preprocess(text);
            if (pp.isEmpty()) {
                return this;
            }

            long[] ug = unigramCounts.get(langCode);
            long[] bg = bigramCounts.get(langCode);

            if (cjk) {
                extractCjkNgrams(pp,
                        h -> ug[h % CJK_UNIGRAM_BUCKETS]++,
                        h -> bg[h % CJK_BIGRAM_BUCKETS]++);
            } else {
                long[] tg = trigramCounts.get(langCode);
                extractNonCjkNgrams(pp,
                        h -> ug[h % NONCJK_UNIGRAM_BUCKETS]++,
                        h -> bg[h % NONCJK_BIGRAM_BUCKETS]++,
                        h -> tg[h % NONCJK_TRIGRAM_BUCKETS]++);
            }
            return this;
        }

        /**
         * Finalize training with add-{@code k} smoothing and return the model.
         *
         * @param addK smoothing constant; 0.01 is a reasonable default
         */
        public GenerativeLanguageModel build(float addK) {
            List<String> ids  = new ArrayList<>(cjkFlags.keySet());
            int n = ids.size();

            boolean[] cjkArr    = new boolean[n];
            byte[][]  uniTables = new byte[n][];
            byte[][]  biTables  = new byte[n][];
            byte[][]  triTables = new byte[n][];

            for (int i = 0; i < n; i++) {
                String lang = ids.get(i);
                cjkArr[i]  = cjkFlags.get(lang);
                uniTables[i] = toLogProbTable(unigramCounts.get(lang), addK);
                biTables[i]  = toLogProbTable(bigramCounts.get(lang),  addK);
                if (!cjkArr[i]) {
                    triTables[i] = toLogProbTable(trigramCounts.get(lang), addK);
                }
            }
            return new GenerativeLanguageModel(ids, cjkArr, uniTables, biTables, triTables,
                    new float[n], new float[n]);
        }

        private static byte[] toLogProbTable(long[] counts, float addK) {
            long total = 0;
            for (long c : counts) {
                total += c;
            }
            double denom = total + (double) addK * counts.length;
            byte[] table = new byte[counts.length];
            for (int i = 0; i < counts.length; i++) {
                double p = (counts[i] + addK) / denom;
                table[i] = quantize((float) Math.log(p));
            }
            return table;
        }
    }
}
