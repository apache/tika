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

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Extracts character n-gram features from text using the hashing trick (FNV-1a).
 * <p>
 * This is the most critical class in the language detector — it defines
 * the exact preprocessing and tokenization pipeline shared between training
 * and inference, guaranteeing identical feature extraction.
 * </p>
 * <h3>Pipeline</h3>
 * <ol>
 *   <li>Truncate input at {@link #MAX_TEXT_LENGTH} chars</li>
 *   <li>Strip URLs and emails (TIKA-2777 bounded patterns)</li>
 *   <li>NFC normalize</li>
 *   <li>Iterate codepoints (surrogate-safe)</li>
 *   <li>Skip transparent characters (see {@link #isTransparent(int)})</li>
 *   <li>Filter: {@link Character#isLetter(int)}</li>
 *   <li>Case fold: {@link Character#toLowerCase(int)}</li>
 *   <li>Emit bigrams (and optionally trigrams) with underscore {@code _} sentinels
 *       at word boundaries</li>
 *   <li>Hash each n-gram via FNV-1a → bucket index</li>
 * </ol>
 *
 * <h3>Trigram mode</h3>
 * <p>
 * When {@code includeTrigrams} is enabled, both bigrams and trigrams are hashed
 * into the same bucket vector. Trigrams are more discriminative than bigrams
 * (e.g., "the" vs "th"+"he"), which improves accuracy on very short texts.
 * The tradeoff is more hash collisions in smaller bucket vectors.
 * </p>
 *
 * <h3>Transparent character handling</h3>
 * <p>
 * Certain codepoints are treated as <em>transparent</em> — they are skipped entirely
 * during n-gram extraction so that base letters on either side form a contiguous pair.
 * This is critical for Arabic and Hebrew where diacritical marks (harakat, niqqud) are
 * Unicode nonspacing marks ({@code Mn}) that would otherwise break words into isolated
 * single-letter fragments, destroying the bigram signal needed to distinguish
 * {@code ara} from {@code ara-x-ltr} (Arabic misrepresented as LTR).
 * </p>
 * <p>See {@link #isTransparent(int)} for the full list of skipped codepoints.</p>
 */
public class CharSoupFeatureExtractor implements FeatureExtractor {

    /** Maximum characters to process — prevents DoS, more than enough for detection. */
    static final int MAX_TEXT_LENGTH = 100_000;

    /** Underscore sentinel codepoint used for word boundary bigrams. */
    static final int SENTINEL = '_';

    // TIKA-2777: bounded regexes to avoid catastrophic backtracking
    private static final Pattern URL_REGEX =
            Pattern.compile("https?://[-_.?&~;+=/#0-9A-Za-z]{10,10000}");
    private static final Pattern MAIL_REGEX =
            Pattern.compile("[-_.0-9A-Za-z]{1,100}@[-_0-9A-Za-z]{1,100}[-_.0-9A-Za-z]{1,100}");

    /** Arabic Tatweel (kashida) — a typographic stretching character (U+0640). */
    private static final int TATWEEL = 0x0640;

    /** Zero Width Non-Joiner (U+200C) — used in Persian/Arabic/Urdu. */
    private static final int ZWNJ = 0x200C;

    /** Zero Width Joiner (U+200D). */
    private static final int ZWJ = 0x200D;

    private final int numBuckets;
    private final boolean includeTrigrams;

    /**
     * Create an extractor with bigrams only.
     *
     * @param numBuckets number of hash buckets (feature vector size)
     */
    public CharSoupFeatureExtractor(int numBuckets) {
        this(numBuckets, false);
    }

    /**
     * Create an extractor with configurable n-gram mode.
     *
     * @param numBuckets      number of hash buckets (feature vector size)
     * @param includeTrigrams if {@code true}, both bigrams and trigrams are
     *                        hashed into the bucket vector; if {@code false},
     *                        only bigrams are used (the default)
     */
    public CharSoupFeatureExtractor(int numBuckets, boolean includeTrigrams) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.includeTrigrams = includeTrigrams;
    }

    /**
     * Full preprocessing + feature extraction pipeline.
     *
     * @param rawText raw input text (may be {@code null})
     * @return int array of size {@code numBuckets} with bigram counts
     */
    @Override
    public int[] extract(String rawText) {
        int[] counts = new int[numBuckets];
        if (rawText == null || rawText.isEmpty()) {
            return counts;
        }
        String text = preprocess(rawText);
        extractBigrams(text, counts);
        return counts;
    }

    /**
     * Extract features into a caller-supplied buffer, avoiding allocation.
     * The buffer is zeroed and then filled with bigram counts.
     * <p>
     * Use this in tight training loops to eliminate per-sample GC pressure
     * from allocating 128KB int arrays millions of times.
     *
     * @param rawText raw input text (may be {@code null})
     * @param counts  pre-allocated int array of size {@code numBuckets} (will be zeroed)
     */
    @Override
    public void extract(String rawText, int[] counts) {
        java.util.Arrays.fill(counts, 0);
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        String text = preprocess(rawText);
        extractBigrams(text, counts);
    }

    /**
     * Extract features from <em>already-preprocessed</em> text (no NFC, no URL stripping,
     * no truncation). Use this when the text has already been passed through
     * {@link #preprocess(String)} — for example, when loading preprocessed data from disk.
     *
     * @param preprocessedText text that has already been through {@link #preprocess(String)}
     * @return int array of size {@code numBuckets} with bigram counts
     */
    @Override
    public int[] extractFromPreprocessed(String preprocessedText) {
        int[] counts = new int[numBuckets];
        if (preprocessedText == null || preprocessedText.isEmpty()) {
            return counts;
        }
        extractBigrams(preprocessedText, counts);
        return counts;
    }

    /**
     * Extract features from already-preprocessed text into a caller-supplied buffer.
     * Combines the benefits of {@link #extractFromPreprocessed(String)} (skip preprocessing)
     * and {@link #extract(String, int[])} (no allocation).
     * <p>
     * This is the fastest extraction path — use it in training loops where text has
     * been preprocessed and written to disk ahead of time.
     *
     * @param preprocessedText text that has already been through {@link #preprocess(String)}
     * @param counts           pre-allocated int array of size {@code numBuckets} (will be zeroed)
     */
    public void extractFromPreprocessed(String preprocessedText, int[] counts) {
        extractFromPreprocessed(preprocessedText, counts, true);
    }

    /**
     * Extract features from already-preprocessed text into a caller-supplied buffer,
     * optionally clearing it first.
     * <p>
     * When {@code clear} is {@code false}, bigram counts are <em>accumulated</em> on
     * top of whatever is already in the buffer. This is useful in training loops
     * where features from multiple sources need to be combined into a single vector.
     *
     * @param preprocessedText text that has already been through {@link #preprocess(String)}
     * @param counts           pre-allocated int array of size {@code numBuckets}
     * @param clear            if {@code true}, zero the array before extracting;
     *                         if {@code false}, accumulate on top of existing counts
     */
    @Override
    public void extractFromPreprocessed(String preprocessedText, int[] counts, boolean clear) {
        if (clear) {
            java.util.Arrays.fill(counts, 0);
        }
        if (preprocessedText == null || preprocessedText.isEmpty()) {
            return;
        }
        extractBigrams(preprocessedText, counts);
    }

    /**
     * Preprocessing: truncate, strip URLs/emails, NFC normalize.
     * <p>
     * This method is also used by the general word tokenizer so that
     * tika-eval shares the same normalization pipeline.
     * </p>
     *
     * @param rawText raw input
     * @return cleaned, NFC-normalized text
     */
    public static String preprocess(String rawText) {
        String text = rawText;

        // 1. Truncate
        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        // 2. Strip URLs and emails
        text = URL_REGEX.matcher(text).replaceAll(" ");
        text = MAIL_REGEX.matcher(text).replaceAll(" ");

        // 3. NFC normalize
        if (!Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
            text = Normalizer.normalize(text, Normalizer.Form.NFC);
        }

        return text;
    }

    /**
     * Determine whether a codepoint should be treated as transparent (skipped)
     * during bigram extraction and word tokenization.
     * <p>
     * Transparent codepoints are invisible to the bigram/tokenization logic:
     * base letters on either side of a transparent run form a contiguous bigram
     * or remain part of the same word token.
     * </p>
     * <p>The following categories are transparent:</p>
     * <ul>
     *   <li><b>Unicode nonspacing marks (Mn)</b> — includes Arabic harakat
     *       (fatha U+064E, damma U+064F, kasra U+0650, shadda U+0651,
     *       sukun U+0652, tanwin U+064B–U+064D, superscript alef U+0670)
     *       and Hebrew niqqud (U+05B0–U+05BD, U+05BF, U+05C1–U+05C2,
     *       U+05C4–U+05C5, U+05C7). Without this, diacritics break Arabic/Hebrew
     *       words into isolated single-letter fragments because
     *       {@link Character#isLetter(int)} returns {@code false} for Mn
     *       codepoints. Stripping them yields clean base-letter bigrams, which
     *       is essential for distinguishing {@code ara} from {@code ara-x-ltr}
     *       (reversed Arabic) where character <em>order</em> is the signal.</li>
     *   <li><b>Arabic Tatweel / Kashida (U+0640)</b> — a typographic stretching
     *       character that is classified as a letter but carries no linguistic
     *       information. "كتب" and "كـتـب" should produce identical bigrams.</li>
     *   <li><b>ZWNJ (U+200C)</b> — Zero Width Non-Joiner, used heavily in
     *       Persian/Farsi (e.g., "می‌خواهم") and in Arabic, Urdu, and Kurdish
     *       to control cursive joining. It is <em>not</em> a word boundary;
     *       bigrams should span across it.</li>
     *   <li><b>ZWJ (U+200D)</b> — Zero Width Joiner, forces cursive joining.
     *       Also not a word boundary.</li>
     * </ul>
     * <p>
     * A fast guard ({@code cp < 0x0300}) short-circuits the check for ASCII
     * and basic Latin/Greek text, adding zero overhead to the common case.
     * </p>
     *
     * @param cp a Unicode codepoint
     * @return {@code true} if the codepoint should be skipped
     */
    static boolean isTransparent(int cp) {
        // Fast path: ASCII and Latin-1 Supplement + Latin Extended blocks
        // have no transparent characters. Combining Diacritical Marks start
        // at U+0300; ZWNJ/ZWJ/Tatweel are all above that.
        if (cp < 0x0300) {
            return false;
        }
        return Character.getType(cp) == Character.NON_SPACING_MARK
                || cp == TATWEEL
                || cp == ZWNJ
                || cp == ZWJ;
    }

    /**
     * Iterate codepoints, skip {@linkplain #isTransparent(int) transparent}
     * characters, emit sentinel-bounded bigrams (and optionally trigrams),
     * hash into buckets.
     */
    private void extractBigrams(String text, int[] counts) {
        int prevCp = SENTINEL;  // previous codepoint (for bigrams)
        int prevPrevCp = -1;    // two codepoints back (for trigrams)
        boolean prevWasLetter = false;

        int i = 0;
        int len = text.length();
        while (i < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            // Skip diacritics, tatweel, ZWNJ, ZWJ — see isTransparent javadoc
            if (cp >= 0x0300 && isTransparent(cp)) {
                continue;
            }

            if (Character.isLetter(cp)) {
                int lower = Character.toLowerCase(cp);
                if (prevWasLetter) {
                    // mid-word bigram
                    incrementBucket(counts, prevCp, lower);
                    // mid-word trigram
                    if (includeTrigrams && prevPrevCp >= 0) {
                        incrementTrigramBucket(counts, prevPrevCp, prevCp, lower);
                    }
                } else {
                    // word-initial bigram: (sentinel, letter)
                    incrementBucket(counts, SENTINEL, lower);
                    // no trigram yet — need at least 2 previous codepoints
                }
                prevPrevCp = prevCp;
                prevCp = lower;
                prevWasLetter = true;
            } else {
                if (prevWasLetter) {
                    // word-final bigram: (letter, sentinel)
                    incrementBucket(counts, prevCp, SENTINEL);
                    // word-final trigram: (prev, letter, sentinel)
                    if (includeTrigrams && prevPrevCp >= 0) {
                        incrementTrigramBucket(counts, prevPrevCp, prevCp, SENTINEL);
                    }
                }
                prevWasLetter = false;
                prevPrevCp = -1;
                // prevCp doesn't matter when prevWasLetter is false
            }
        }

        // End of text: emit final sentinel bigram/trigram if last char was a letter
        if (prevWasLetter) {
            incrementBucket(counts, prevCp, SENTINEL);
            if (includeTrigrams && prevPrevCp >= 0) {
                incrementTrigramBucket(counts, prevPrevCp, prevCp, SENTINEL);
            }
        }
    }

    private void incrementBucket(int[] counts, int cp1, int cp2) {
        int bucket = bucketIndex(cp1, cp2);
        counts[bucket]++;
    }

    private void incrementTrigramBucket(int[] counts, int cp1, int cp2, int cp3) {
        int hash = hashTrigram(cp1, cp2, cp3);
        int bucket = (hash & 0x7FFFFFFF) % numBuckets;
        counts[bucket]++;
    }

    /**
     * Compute the bucket index for a bigram of two codepoints.
     *
     * @param cp1 first codepoint
     * @param cp2 second codepoint
     * @return bucket index in [0, numBuckets)
     */
    int bucketIndex(int cp1, int cp2) {
        int hash = hashBigram(cp1, cp2);
        return (hash & 0x7FFFFFFF) % numBuckets;
    }

    /**
     * FNV-1a 32-bit hash of two codepoints, each fed as 4 little-endian bytes.
     *
     * @param cp1 first codepoint
     * @param cp2 second codepoint
     * @return 32-bit FNV-1a hash
     */
    static int hashBigram(int cp1, int cp2) {
        int hash = 0x811c9dc5; // FNV offset basis
        hash = fnvFeedInt(hash, cp1);
        hash = fnvFeedInt(hash, cp2);
        return hash;
    }

    /**
     * FNV-1a 32-bit hash of three codepoints, each fed as 4 little-endian bytes.
     * <p>
     * Trigrams occupy a different hash space than bigrams naturally because
     * the third codepoint extends the hash chain, so bigram and trigram features
     * won't systematically collide.
     *
     * @param cp1 first codepoint
     * @param cp2 second codepoint
     * @param cp3 third codepoint
     * @return 32-bit FNV-1a hash
     */
    static int hashTrigram(int cp1, int cp2, int cp3) {
        int hash = 0x811c9dc5; // FNV offset basis
        hash = fnvFeedInt(hash, cp1);
        hash = fnvFeedInt(hash, cp2);
        hash = fnvFeedInt(hash, cp3);
        return hash;
    }

    /**
     * Feed a 32-bit int into an FNV-1a hash as 4 little-endian bytes.
     */
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

    public boolean isIncludeTrigrams() {
        return includeTrigrams;
    }
}
