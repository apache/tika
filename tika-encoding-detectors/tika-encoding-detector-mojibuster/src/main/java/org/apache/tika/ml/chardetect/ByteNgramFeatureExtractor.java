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
package org.apache.tika.ml.chardetect;

import org.apache.tika.ml.FeatureExtractor;

/**
 * Feature extractor for raw bytes for charset detection, using FNV-1a hashing
 * into a fixed-width bucket array.
 *
 * <h3>Feature set (fixed — UB-A)</h3>
 * <p>This production extractor emits <strong>high-byte-anchored unigrams,
 * bigrams, and anchored bigrams</strong> plus a single ASCII-density global
 * feature.  The total feature-vector dimension is {@link #NUM_BUCKETS}.</p>
 *
 * <p>The feature flags are intentionally not configurable — the shipped model
 * was trained with exactly this configuration, and using any other combination
 * at inference time would produce silently wrong predictions.  Design choices
 * are tracked in git rather than at the command line.</p>
 *
 * <h3>Features emitted</h3>
 * <ul>
 *   <li><strong>Unigrams</strong>: every byte {@code b} where
 *       {@code (b & 0xFF) >= 0x80}. These directly encode the high-byte
 *       frequency distribution that distinguishes single-byte encodings
 *       (KOI8-R vs Windows-1251 vs ISO-8859-2, etc.).</li>
 *   <li><strong>Bigrams</strong>: consecutive pairs {@code (b[i], b[i+1])}
 *       where {@code (b[i] & 0xFF) >= 0x80}. Anchoring on a high first byte
 *       captures multi-byte character structure (Big5, Shift-JIS, GBK,
 *       EUC-* lead/trail pairs) while automatically excluding ASCII-ASCII
 *       pairs produced by HTML tag markup.</li>
 *   <li><strong>Anchored bigrams</strong>: pairs {@code (b[i+1], b[i+2])} emitted
 *       when {@code (b[i] & 0xFF) >= 0x80} and {@code (b[i+1] & 0xFF) < 0x80}
 *       (i.e. a high lead byte is followed by a low trail byte). Captures
 *       cross-character boundary structure in Shift-JIS and Big5 where trail
 *       bytes fall below 0x80 (0x40–0x7E). A distinct salt ({@code FNV_ANCHOR_SALT})
 *       prevents hash collisions with stride-1 bigrams.</li>
 *   <li><strong>ASCII-density global</strong>: exactly one of
 *       {@link #GLOBAL_FEATURE_COUNT} bins fires per probe, based on the
 *       fraction of bytes that are printable ASCII (see
 *       {@link #asciiDensityBin(byte[])}).  Helps the model condition its
 *       Western-European vs CJK vs EBCDIC decision on overall probe shape.</li>
 * </ul>
 *
 * <h3>UTF-16 detection is owned by the UTF-16 specialist</h3>
 * <p>Stride-2 bigrams previously emitted here were the model's primary UTF-16
 * signal.  They are no longer emitted: UTF-16 detection is now handled by
 * {@code Utf16SpecialistEncodingDetector}, which uses column-aggregate byte-
 * range features.  That specialist correctly handles Latin, Cyrillic, Arabic,
 * Hebrew, Indic, Thai, CJK Unified, and Hangul UTF-16 alike — including the
 * CJK UTF-16 cases that a printable-ASCII-filtered stride-2 would have
 * missed (common Chinese U+4E00–U+7EFF and hiragana U+3040–U+309F are
 * frequently in the {@code [0x20, 0x7E]} range).  Native multi-byte CJK
 * (Shift_JIS / GB18030 / Big5 / EUC-*) is still discriminated here via
 * high-byte-anchored bigrams — all CJK lead bytes are {@code >= 0x81}.</p>
 *
 * <h3>Why the high-byte filter matters</h3>
 * <p>Training data is clean text (no HTML tags). Inference data is often raw
 * HTML (many ASCII tag bytes). Without the filter, the model would see a
 * different byte distribution at inference time than at training time. By
 * ignoring bytes below 0x80 entirely for stride-1 features, HTML tags are
 * invisible to both the training and inference feature computation — no
 * stripping needed.</p>
 */
public class ByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME        = 0x01000193;
    private static final int FNV_OFFSET       = 0x811c9dc5;
    /** Distinct salt for anchored bigrams (high→low boundary) — prevents collision with stride-1. */
    private static final int FNV_ANCHOR_SALT  = 0x27d4eb2f;

    /** Total feature-vector dimension used by the shipped model (including global slots). */
    public static final int NUM_BUCKETS = 16390;

    /**
     * Number of reserved slots at the high end of the feature vector for
     * global (whole-probe) features. The last 6 slots hold ASCII-text-density
     * bins (see {@link #asciiDensityBin(byte[])}). Always active.
     */
    public static final int GLOBAL_FEATURE_COUNT = 6;

    private final int numBuckets;
    private final int hashSpace;   // numBuckets - GLOBAL_FEATURE_COUNT
    private final int globalBase;  // = hashSpace (first of 6 global slots)

    /**
     * @param numBuckets total feature-vector dimension, including the
     *                   {@link #GLOBAL_FEATURE_COUNT} global slots at the end.
     */
    public ByteNgramFeatureExtractor(int numBuckets) {
        if (numBuckets <= GLOBAL_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    "numBuckets must exceed GLOBAL_FEATURE_COUNT: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.hashSpace  = numBuckets - GLOBAL_FEATURE_COUNT;
        this.globalBase = hashSpace;
    }

    /**
     * Returns which ASCII-text-density bin this probe falls into, in [0, 6).
     *
     * <p>Bin layout (fraction of bytes that are ASCII-text: printable
     * {@code 0x20..0x7E} plus {@code 0x09 0x0A 0x0D}):</p>
     * <ul>
     *   <li>0: [0.00, 0.10)</li>
     *   <li>1: [0.10, 0.50)</li>
     *   <li>2: [0.50, 0.80)</li>
     *   <li>3: [0.80, 0.95)</li>
     *   <li>4: [0.95, 0.99)</li>
     *   <li>5: [0.99, 1.00]</li>
     * </ul>
     */
    public static int asciiDensityBin(byte[] input) {
        if (input == null || input.length == 0) {
            return 5;
        }
        int asciiText = 0;
        for (byte b : input) {
            int v = b & 0xFF;
            if ((v >= 0x20 && v <= 0x7E) || v == 0x09 || v == 0x0A || v == 0x0D) {
                asciiText++;
            }
        }
        double p = (double) asciiText / input.length;
        if (p < 0.10) {
            return 0;
        }
        if (p < 0.50) {
            return 1;
        }
        if (p < 0.80) {
            return 2;
        }
        if (p < 0.95) {
            return 3;
        }
        if (p < 0.99) {
            return 4;
        }
        return 5;
    }

    @Override
    public int[] extract(byte[] input) {
        int[] counts = new int[numBuckets];
        if (input == null || input.length == 0) {
            return counts;
        }
        extractInto(input, 0, input.length, counts);
        return counts;
    }

    /**
     * Extract from a sub-range of a byte array.
     */
    public int[] extract(byte[] input, int offset, int length) {
        int[] counts = new int[numBuckets];
        if (input == null || length == 0) {
            return counts;
        }
        extractInto(input, offset, offset + length, counts);
        return counts;
    }

    /**
     * Sparse extraction into caller-owned, reusable buffers.
     *
     * <p>This is O(probe length), not O(numBuckets), making it safe for large
     * bucket counts in tight training loops.
     *
     * <p>After the call, {@code dense[touched[0..n-1]]} hold the non-zero
     * counts.  The caller <em>must</em> clear those entries after use:
     * <pre>{@code
     *   for (int i = 0; i < n; i++) dense[touched[i]] = 0;
     * }</pre>
     *
     * @param input   raw bytes to extract features from
     * @param dense   caller-allocated scratch buffer of length {@code numBuckets}
     *                (must be all-zeros on entry; caller clears it after use)
     * @param touched caller-allocated buffer receiving indices of non-zero buckets
     * @return number of active entries written into {@code touched}
     */
    public int extractSparseInto(byte[] input, int[] dense, int[] touched) {
        if (input == null || input.length == 0) {
            return 0;
        }
        int n = 0;

        // Stride-1: high-byte-anchored unigrams and bigrams.
        for (int i = 0; i < input.length; i++) {
            int bi = input[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }

            // Unigram
            int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
            int bkt = stride1Bucket(h);
            if (dense[bkt] == 0) {
                touched[n++] = bkt;
            }
            dense[bkt]++;

            if (i + 1 < input.length) {
                int bi1 = input[i + 1] & 0xFF;

                // Bigram
                h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
                bkt = stride1Bucket(h);
                if (dense[bkt] == 0) {
                    touched[n++] = bkt;
                }
                dense[bkt]++;
            }
        }

        // Global feature: fire exactly one ASCII-density bin.
        int bkt = globalBase + asciiDensityBin(input);
        if (dense[bkt] == 0) {
            touched[n++] = bkt;
        }
        dense[bkt]++;

        return n;
    }

    private void extractInto(byte[] b, int from, int to, int[] counts) {
        // Stride-1: high-byte-anchored unigrams and bigrams.
        for (int i = from; i < to; i++) {
            int bi = b[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }

            // Unigram
            counts[stride1Bucket((FNV_OFFSET ^ bi) * FNV_PRIME)]++;

            if (i + 1 < to) {
                int bi1 = b[i + 1] & 0xFF;

                // Bigram
                int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
                counts[stride1Bucket(h)]++;
            }
        }

        // Global feature: fire exactly one ASCII-density bin.
        byte[] slice = (from == 0 && to == b.length)
                ? b : java.util.Arrays.copyOfRange(b, from, to);
        counts[globalBase + asciiDensityBin(slice)]++;
    }

    private int stride1Bucket(int hash) {
        return (hash & 0x7fffffff) % hashSpace;
    }

    @Override
    public int getNumBuckets() {
        return numBuckets;
    }

    /**
     * Returns the fraction of bytes in {@code input} that are below 0x80 and
     * therefore contribute no features to this extractor.
     */
    public static double oovRate(byte[] input) {
        if (input == null || input.length == 0) {
            return 1.0;
        }
        int ascii = 0;
        for (byte b : input) {
            if ((b & 0xFF) < 0x80) {
                ascii++;
            }
        }
        return (double) ascii / input.length;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "ByteNgramFeatureExtractor{buckets=%d, UB-A}", numBuckets);
    }
}
