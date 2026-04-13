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
 *   <li><b>useStride2Bigrams</b>: emit one feature per (b[i], b[i+1]) pair at
 *       even positions i = 0, 2, 4, ... covering all bytes (not just high bytes).
 *       A distinct FNV salt prevents hash collision with stride-1 bigrams.
 *       Helps the model distinguish UTF-16BE/LE via their characteristic
 *       code-unit patterns.</li>
 * </ul>
 */
public class ConfigurableByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME        = 0x01000193;
    private static final int FNV_OFFSET       = 0x811c9dc5;
    private static final int FNV_ANCHOR_SALT  = 0x27d4eb2f;
    /** Distinct salt for stride-2 bigrams — prevents collision with stride-1 hashes. */
    private static final int FNV_STRIDE2_SALT = 0x9e3779b9;

    /**
     * Number of reserved slots at the high end of the feature vector used for
     * global (whole-probe) features when {@link #useGlobalFeatures} is enabled.
     * Currently 6 slots hold ASCII-low-byte density bins (see
     * {@link #asciiDensityBin(byte[])}).
     */
    public static final int GLOBAL_FEATURE_COUNT = 6;

    private final int numBuckets;
    private final int hashBuckets;
    private final boolean useUnigrams;
    private final boolean useBigrams;
    private final boolean useTrigrams;
    private final boolean useAnchoredBigrams;
    private final boolean useStride2Bigrams;
    private final boolean useGlobalFeatures;

    /**
     * Backwards-compatible constructor (no global features).
     */
    public ConfigurableByteNgramFeatureExtractor(int numBuckets,
                                                 boolean useUnigrams,
                                                 boolean useBigrams,
                                                 boolean useTrigrams,
                                                 boolean useAnchoredBigrams,
                                                 boolean useStride2Bigrams) {
        this(numBuckets, useUnigrams, useBigrams, useTrigrams,
                useAnchoredBigrams, useStride2Bigrams, false);
    }

    /**
     * @param numBuckets         total feature-vector dimension.  When
     *                           {@code useGlobalFeatures} is {@code true}, the
     *                           last {@link #GLOBAL_FEATURE_COUNT} slots are
     *                           reserved for global features and hashed n-gram
     *                           features mod into the first
     *                           {@code numBuckets - GLOBAL_FEATURE_COUNT} slots.
     * @param useUnigrams        emit unigram for each high byte
     * @param useBigrams         emit bigram anchored on each high byte
     * @param useTrigrams        emit trigram anchored on each high byte
     * @param useAnchoredBigrams emit bigram anchored on each low trail byte
     * @param useStride2Bigrams  emit stride-2 bigrams at even positions (all bytes)
     * @param useGlobalFeatures  emit whole-probe global features into the
     *                           reserved tail slots (ASCII-density bins)
     */
    public ConfigurableByteNgramFeatureExtractor(int numBuckets,
                                                 boolean useUnigrams,
                                                 boolean useBigrams,
                                                 boolean useTrigrams,
                                                 boolean useAnchoredBigrams,
                                                 boolean useStride2Bigrams,
                                                 boolean useGlobalFeatures) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        if (useGlobalFeatures && numBuckets <= GLOBAL_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                    "numBuckets must exceed GLOBAL_FEATURE_COUNT (" + GLOBAL_FEATURE_COUNT
                            + ") when useGlobalFeatures=true: " + numBuckets);
        }
        this.numBuckets = numBuckets;
        this.hashBuckets = useGlobalFeatures ? numBuckets - GLOBAL_FEATURE_COUNT : numBuckets;
        this.useUnigrams = useUnigrams;
        this.useBigrams = useBigrams;
        this.useTrigrams = useTrigrams;
        this.useAnchoredBigrams = useAnchoredBigrams;
        this.useStride2Bigrams = useStride2Bigrams;
        this.useGlobalFeatures = useGlobalFeatures;
    }

    /**
     * Returns which ASCII-text-density bin this probe falls into, in [0, 6).
     *
     * <p>Counts only <em>ASCII text bytes</em> — printable (0x20..0x7E) plus
     * common whitespace (0x09 tab, 0x0A LF, 0x0D CR).  NUL and other control
     * bytes do <em>not</em> count.  This matters because UTF-16LE/BE probes
     * contain ~50% 0x00 bytes; if we counted those as "low", UTF-16 English
     * would look like sparse Latin to the model, defeating the point of the
     * feature.  With the current definition, real UTF-16 English lands around
     * bin 2-3 (half ASCII-letter bytes, half nulls), distinguishable from
     * plain-ASCII probes (bin 5) and from real EBCDIC (bin 0-1).</p>
     *
     * <p>Bin layout (fraction of bytes that are ASCII-text):</p>
     * <ul>
     *   <li>0: [0.00, 0.10) — effectively no ASCII text (real EBCDIC letters)</li>
     *   <li>1: [0.10, 0.50) — heavy non-ASCII content (CJK text, UTF-16 mixed)</li>
     *   <li>2: [0.50, 0.80) — text with dense foreign script, UTF-16 Latin</li>
     *   <li>3: [0.80, 0.95) — normal foreign-script text with ASCII markup</li>
     *   <li>4: [0.95, 0.99) — sparse-diacritic Western text</li>
     *   <li>5: [0.99, 1.00] — near-pure ASCII (vCards, config, scripts)</li>
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

        // Stride-1: high-byte-anchored features.
        for (int i = 0; i < input.length; i++) {
            int bi = input[i] & 0xFF;
            if (bi < 0x80) {
                continue;
            }

            if (useUnigrams) {
                int h = (FNV_OFFSET ^ bi) * FNV_PRIME;
                int bkt = (h & 0x7fffffff) % hashBuckets;
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
                    int bkt = (h & 0x7fffffff) % hashBuckets;
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
                    int bkt = (h & 0x7fffffff) % hashBuckets;
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
                    int bkt = (h & 0x7fffffff) % hashBuckets;
                    if (dense[bkt] == 0) {
                        touched[n++] = bkt;
                    }
                    dense[bkt]++;
                }
            }
        }

        // Stride-2: code-unit pairs at positions 0, 2, 4, ...
        if (useStride2Bigrams) {
            for (int i = 0; i + 1 < input.length; i += 2) {
                int b0 = input[i] & 0xFF;
                int b1 = input[i + 1] & 0xFF;
                int h = (FNV_STRIDE2_SALT ^ b0) * FNV_PRIME;
                h = (h ^ b1) * FNV_PRIME;
                int bkt = (h & 0x7fffffff) % hashBuckets;
                if (dense[bkt] == 0) {
                    touched[n++] = bkt;
                }
                dense[bkt]++;
            }
        }

        // Global features at reserved tail slots: fire exactly one ASCII-density bin.
        if (useGlobalFeatures) {
            int bkt = hashBuckets + asciiDensityBin(input);
            if (dense[bkt] == 0) {
                touched[n++] = bkt;
            }
            dense[bkt]++;
        }

        return n;
    }

    private void extractInto(byte[] b, int from, int to, int[] counts) {
        // Stride-1: high-byte-anchored features.
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

        // Stride-2 bigrams (same logic as extractSparseInto).
        if (useStride2Bigrams) {
            for (int i = from; i + 1 < to; i += 2) {
                int b0 = b[i] & 0xFF;
                int b1 = b[i + 1] & 0xFF;
                int h = (FNV_STRIDE2_SALT ^ b0) * FNV_PRIME;
                h = (h ^ b1) * FNV_PRIME;
                counts[bucket(h)]++;
            }
        }

        // Global features at reserved tail slots: fire exactly one ASCII-density bin.
        if (useGlobalFeatures) {
            byte[] slice = (from == 0 && to == b.length)
                    ? b : java.util.Arrays.copyOfRange(b, from, to);
            counts[hashBuckets + asciiDensityBin(slice)]++;
        }
    }

    private int bucket(int hash) {
        return (hash & 0x7fffffff) % hashBuckets;
    }

    @Override
    public int getNumBuckets() {
        return numBuckets;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "ConfigurableByteNgramFeatureExtractor{buckets=%d, hash=%d, uni=%b, bi=%b, tri=%b, anchored=%b, stride2=%b, globals=%b}",
                numBuckets, hashBuckets, useUnigrams, useBigrams, useTrigrams,
                useAnchoredBigrams, useStride2Bigrams, useGlobalFeatures);
    }
}
