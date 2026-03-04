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
 * <h3>Feature set (fixed)</h3>
 * <p>This production extractor uses the feature set selected by ablation study:
 * <strong>unigrams + bigrams + trigrams, no anchored bigrams</strong> (UBT-).
 * The feature flags are intentionally not configurable here — the shipped model
 * was trained with exactly this configuration, and using any other combination
 * at inference time would produce silently wrong predictions.</p>
 * <p>For training new models with different feature combinations, use
 * {@code ConfigurableByteNgramFeatureExtractor} in the training-tools module.</p>
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
 *   <li><strong>Trigrams</strong>: {@code (b[i], b[i+1], b[i+2])} anchored on
 *       the same high byte. A 3-byte window captures cross-character transitions
 *       in a single feature: {@code (0x83, 0x41, 0x83)} (ア→next lead in SJIS)
 *       is impossible in EUC-JP, GBK-2byte, or UTF-8 since their trail bytes are
 *       always {@code >= 0x80}. Encodings that benefit: Shift-JIS (0x40–0x7E),
 *       Big5 (0x40–0x7E), GBK (0x40–0x7E), and GB18030 4-byte sequences.
 *       For EBCDIC, {@code (LETTER, 0x40_space, LETTER)} trigrams are equally
 *       distinctive.</li>
 * </ul>
 *
 * <h3>Why the high-byte filter matters</h3>
 * <p>Training data is clean text (no HTML tags). Inference data is often raw
 * HTML (many ASCII tag bytes). Without the filter, the model would see a
 * different byte distribution at inference time than at training time. By
 * ignoring bytes below 0x80 entirely, HTML tags are invisible to both the
 * training and inference feature computation — no stripping needed.</p>
 */
public class ByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME  = 0x01000193;
    private static final int FNV_OFFSET = 0x811c9dc5;

    private final int numBuckets;

    /**
     * Create an extractor with the production feature set (UBT-: unigrams +
     * bigrams + trigrams, no anchored bigrams) and the given bucket count.
     * The bucket count must match the model the extractor will be paired with —
     * in practice this is read from the model binary via
     * {@link org.apache.tika.ml.LinearModel#getNumBuckets()}.
     *
     * @param numBuckets number of hash buckets (feature-vector dimension)
     */
    public ByteNgramFeatureExtractor(int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
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
        for (int i = 0; i < input.length; i++) {
            int bi = input[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }

            // Unigram
            int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
            int bkt = (h & 0x7fffffff) % numBuckets;
            if (dense[bkt] == 0) {
                touched[n++] = bkt;
            }
            dense[bkt]++;

            if (i + 1 < input.length) {
                int bi1 = input[i + 1] & 0xFF;

                // Bigram
                h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
                bkt = (h & 0x7fffffff) % numBuckets;
                if (dense[bkt] == 0) {
                    touched[n++] = bkt;
                }
                dense[bkt]++;

                // Trigram
                if (i + 2 < input.length) {
                    int bi2 = input[i + 2] & 0xFF;
                    h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                    h = (h ^ bi1) * FNV_PRIME;
                    h = (h ^ bi2) * FNV_PRIME;
                    bkt = (h & 0x7fffffff) % numBuckets;
                    if (dense[bkt] == 0) {
                        touched[n++] = bkt;
                    }
                    dense[bkt]++;
                }
            }
        }
        return n;
    }

    private void extractInto(byte[] b, int from, int to, int[] counts) {
        for (int i = from; i < to; i++) {
            int bi = b[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }

            // Unigram
            counts[bucket((FNV_OFFSET ^ bi) * FNV_PRIME)]++;

            if (i + 1 < to) {
                int bi1 = b[i + 1] & 0xFF;

                // Bigram
                int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                h = (h ^ bi1) * FNV_PRIME;
                counts[bucket(h)]++;

                // Trigram
                if (i + 2 < to) {
                    int bi2 = b[i + 2] & 0xFF;
                    h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                    h = (h ^ bi1) * FNV_PRIME;
                    h = (h ^ bi2) * FNV_PRIME;
                    counts[bucket(h)]++;
                }
            }
        }
    }

    private int bucket(int hash) {
        return (hash & 0x7fffffff) % numBuckets;
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
                "ByteNgramFeatureExtractor{buckets=%d, UBT-}", numBuckets);
    }
}
