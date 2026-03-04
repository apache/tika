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
package org.apache.tika.ml.chardetect.tools;

import org.apache.tika.ml.FeatureExtractor;

/**
 * Configurable byte n-gram feature extractor for use during training and
 * ablation studies.
 *
 * <p>This class exposes all hyperparameters ({@code numBuckets}, feature flags)
 * as constructor arguments so that training tools and annealing scripts can
 * explore the full search space.  It is intentionally kept out of the
 * production {@code tika-encoding-detector-mojibuster} module — the shipped
 * model was trained with fixed parameters (UBT-: unigrams + bigrams + trigrams,
 * no anchored bigrams, 8192 buckets) which are hard-coded in the production
 * {@link org.apache.tika.ml.chardetect.ByteNgramFeatureExtractor}.</p>
 *
 * <p>Using this class at inference time against a model trained with different
 * flags would produce silently wrong predictions.</p>
 *
 * <h3>Feature flags</h3>
 * <ul>
 *   <li><b>useUnigrams</b>: emit one feature per high byte ({@code >= 0x80})</li>
 *   <li><b>useBigrams</b>: emit one feature per (high, next) byte pair</li>
 *   <li><b>useTrigrams</b>: emit one feature per (high, next, next+1) triple</li>
 *   <li><b>useAnchoredBigrams</b>: emit one feature per (low-trail, next) pair
 *       when the trail byte is {@code < 0x80} — captures cross-character
 *       boundaries in encodings like Shift-JIS and Big5 with low trail bytes</li>
 * </ul>
 */
public class ConfigurableByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME  = 0x01000193;
    private static final int FNV_OFFSET = 0x811c9dc5;
    private static final int FNV_ANCHOR_SALT = 0x27d4eb2f;

    private final int numBuckets;
    private final boolean useUnigrams;
    private final boolean useBigrams;
    private final boolean useTrigrams;
    private final boolean useAnchoredBigrams;

    /**
     * @param numBuckets         number of hash buckets (feature-vector dimension)
     * @param useUnigrams        emit unigram for each high byte
     * @param useBigrams         emit bigram anchored on each high byte
     * @param useTrigrams        emit trigram anchored on each high byte
     * @param useAnchoredBigrams emit bigram anchored on each low trail byte
     */
    public ConfigurableByteNgramFeatureExtractor(int numBuckets,
                                                 boolean useUnigrams,
                                                 boolean useBigrams,
                                                 boolean useTrigrams,
                                                 boolean useAnchoredBigrams) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.useUnigrams = useUnigrams;
        this.useBigrams = useBigrams;
        this.useTrigrams = useTrigrams;
        this.useAnchoredBigrams = useAnchoredBigrams;
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
     * Sparse extraction into caller-owned, reusable buffers.  O(probe length).
     *
     * @param input   raw bytes
     * @param dense   scratch buffer of length {@code numBuckets}, all-zeros on entry
     * @param touched receives indices of non-zero buckets
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

            if (useUnigrams) {
                int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                int bkt = (h & 0x7fffffff) % numBuckets;
                if (dense[bkt] == 0) {
                    touched[n++] = bkt;
                }
                dense[bkt]++;
            }

            if (i + 1 < input.length) {
                int bi1 = input[i + 1] & 0xFF;

                if (useBigrams) {
                    int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                    h = (h ^ bi1) * FNV_PRIME;
                    int bkt = (h & 0x7fffffff) % numBuckets;
                    if (dense[bkt] == 0) {
                        touched[n++] = bkt;
                    }
                    dense[bkt]++;
                }

                if (useAnchoredBigrams && bi1 < 0x80) {
                    int h = (FNV_ANCHOR_SALT ^ bi1) * FNV_PRIME;
                    if (i + 2 < input.length) {
                        h = (h ^ (input[i + 2] & 0xFF)) * FNV_PRIME;
                    }
                    int bkt = (h & 0x7fffffff) % numBuckets;
                    if (dense[bkt] == 0) {
                        touched[n++] = bkt;
                    }
                    dense[bkt]++;
                }

                if (useTrigrams && i + 2 < input.length) {
                    int bi2 = input[i + 2] & 0xFF;
                    int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                    h = (h ^ bi1) * FNV_PRIME;
                    h = (h ^ bi2) * FNV_PRIME;
                    int bkt = (h & 0x7fffffff) % numBuckets;
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

            if (useUnigrams) {
                counts[bucket((FNV_OFFSET ^ bi) * FNV_PRIME)]++;
            }

            if (i + 1 < to) {
                int bi1 = b[i + 1] & 0xFF;

                if (useBigrams) {
                    int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                    h = (h ^ bi1) * FNV_PRIME;
                    counts[bucket(h)]++;
                }

                if (useAnchoredBigrams && bi1 < 0x80) {
                    int h = (FNV_ANCHOR_SALT ^ bi1) * FNV_PRIME;
                    if (i + 2 < to) {
                        h = (h ^ (b[i + 2] & 0xFF)) * FNV_PRIME;
                    }
                    counts[bucket(h)]++;
                }

                if (useTrigrams && i + 2 < to) {
                    int bi2 = b[i + 2] & 0xFF;
                    int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
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

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "ConfigurableByteNgramFeatureExtractor{buckets=%d, uni=%b, bi=%b, tri=%b, anchored=%b}",
                numBuckets, useUnigrams, useBigrams, useTrigrams, useAnchoredBigrams);
    }
}
