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
 * <h3>Feature set (fixed — UB-AS)</h3>
 * <p>This production extractor uses the feature set selected by grid search over
 * the MadLAD-derived {@code charset-detect3} corpus (34 charsets, 3 runs × 6
 * configs × 3 bucket sizes, devtest accuracy averaged to reduce SGD noise):
 * <strong>unigrams + bigrams + anchored bigrams + stride-2 bigrams</strong>
 * (UB-AS), 16384 buckets.</p>
 *
 * <p>Key findings from the ablation/grid search:</p>
 * <ul>
 *   <li>Trigrams (T) added no accuracy over UB-AS and were dropped.</li>
 *   <li>Stride-2 bigrams (S) are the single most important new feature —
 *       they lifted overall accuracy from ~73% (old UBT- model without UTF-16/32
 *       training) to ~95% by giving the model direct code-unit visibility into
 *       UTF-16/32 structure.</li>
 *   <li>Anchored bigrams (A) add ~0.04% at 16384 buckets — tiny but consistent.</li>
 *   <li>Accuracy plateau between 8192 and 32768 buckets is within SGD noise;
 *       16384 chosen as the best size/accuracy trade-off.</li>
 * </ul>
 *
 * <p>The feature flags are intentionally not configurable here — the shipped model
 * was trained with exactly this configuration, and using any other combination
 * at inference time would produce silently wrong predictions.
 * For training new models with different feature combinations, use
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
 *   <li><strong>Anchored bigrams</strong>: pairs {@code (b[i+1], b[i+2])} emitted
 *       when {@code (b[i] & 0xFF) >= 0x80} and {@code (b[i+1] & 0xFF) < 0x80}
 *       (i.e. a high lead byte is followed by a low trail byte). Captures
 *       cross-character boundary structure in Shift-JIS and Big5 where trail
 *       bytes fall below 0x80 (0x40–0x7E). A distinct salt ({@code FNV_ANCHOR_SALT})
 *       prevents hash collisions with stride-1 bigrams.</li>
 *   <li><strong>Stride-2 bigrams</strong>: pairs {@code (b[i], b[i+1])} sampled
 *       at even positions {@code i = 0, 2, 4, ...}, covering all bytes (not just
 *       high bytes). These pairs directly reflect code-unit structure: UTF-16LE
 *       BMP text produces many {@code (XX, 0x00)} pairs; UTF-16BE produces
 *       {@code (0x00, XX)}. A distinct FNV salt ({@code FNV_STRIDE2_SALT})
 *       prevents hash collisions with stride-1 features. The BOM must be
 *       stripped upstream before bytes reach this extractor so that offset 0
 *       always aligns with a real code unit, matching the BOM-free training
 *       data.</li>
 * </ul>
 *
 * <h3>Why the high-byte filter matters for stride-1 features</h3>
 * <p>Training data is clean text (no HTML tags). Inference data is often raw
 * HTML (many ASCII tag bytes). Without the filter, the model would see a
 * different byte distribution at inference time than at training time. By
 * ignoring bytes below 0x80 entirely for stride-1 features, HTML tags are
 * invisible to both the training and inference feature computation — no
 * stripping needed. Stride-2 features intentionally include all bytes because
 * the low bytes are the signal (e.g. the 0x00 high byte in UTF-16 BMP text).</p>
 */
public class ByteNgramFeatureExtractor implements FeatureExtractor<byte[]> {

    private static final int FNV_PRIME        = 0x01000193;
    private static final int FNV_OFFSET       = 0x811c9dc5;
    /** Distinct salt for anchored bigrams (high→low boundary) — prevents collision with stride-1. */
    private static final int FNV_ANCHOR_SALT  = 0x27d4eb2f;
    /** Distinct salt for stride-2 bigrams — prevents collision with stride-1 hashes. */
    private static final int FNV_STRIDE2_SALT = 0x9e3779b9;

    /**
     * Number of reserved slots at the high end of the feature vector used for
     * global (whole-probe) features when {@link #useGlobalFeatures} is enabled.
     * Currently 6 slots hold ASCII-text-density bins (see
     * {@link #asciiDensityBin(byte[])}).  Must match the training-side
     * {@code ConfigurableByteNgramFeatureExtractor.GLOBAL_FEATURE_COUNT}.
     */
    public static final int GLOBAL_FEATURE_COUNT = 6;

    private final int numBuckets;
    private final int stride1Buckets;
    private final int stride2Buckets;
    private final int stride2Base;
    private final int globalBase;
    private final boolean useGlobalFeatures;
    private final boolean useSplitSpaces;

    /**
     * Legacy constructor: no globals, shared stride-1/stride-2 hash space.
     * Matches the layout used by the shipped {@code chardetect-v6-no-utf32.bin}.
     *
     * @param numBuckets number of hash buckets (feature-vector dimension)
     */
    public ByteNgramFeatureExtractor(int numBuckets) {
        this(numBuckets, false, false);
    }

    /**
     * Create an extractor matching the layout of a trained model.
     *
     * @param numBuckets         total feature-vector dimension.
     * @param useGlobalFeatures  reserve the last {@link #GLOBAL_FEATURE_COUNT}
     *                           slots for ASCII-density bin features.
     * @param useSplitSpaces     split the hash space 50/50 between stride-1
     *                           features (low half) and stride-2 features
     *                           (high half) so cross-family hash collisions
     *                           cannot pollute single-byte-charset weights
     *                           with stride-2 signals.
     */
    public ByteNgramFeatureExtractor(int numBuckets,
                                     boolean useGlobalFeatures,
                                     boolean useSplitSpaces) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive: " + numBuckets);
        }
        int globalsReserved = useGlobalFeatures ? GLOBAL_FEATURE_COUNT : 0;
        int hashSpace = numBuckets - globalsReserved;
        if (hashSpace <= 0) {
            throw new IllegalArgumentException(
                    "numBuckets must exceed GLOBAL_FEATURE_COUNT when useGlobalFeatures=true: "
                            + numBuckets);
        }
        if (useSplitSpaces && hashSpace < 2) {
            throw new IllegalArgumentException(
                    "useSplitSpaces requires hashSpace >= 2: " + hashSpace);
        }
        this.numBuckets = numBuckets;
        this.useSplitSpaces = useSplitSpaces;
        this.useGlobalFeatures = useGlobalFeatures;
        if (useSplitSpaces) {
            this.stride1Buckets = hashSpace / 2;
            this.stride2Buckets = hashSpace - this.stride1Buckets;
            this.stride2Base = this.stride1Buckets;
        } else {
            this.stride1Buckets = hashSpace;
            this.stride2Buckets = hashSpace;
            this.stride2Base = 0;
        }
        this.globalBase = hashSpace;
    }

    /**
     * Returns which ASCII-text-density bin this probe falls into, in [0, 6).
     * Must match the training-side
     * {@code ConfigurableByteNgramFeatureExtractor.asciiDensityBin}.
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

        // Stride-2: code-unit pairs at positions 0, 2, 4, ...
        // Covers all bytes (not just high bytes) so UTF-16 null bytes are visible.
        for (int i = 0; i + 1 < input.length; i += 2) {
            int b0 = input[i] & 0xFF;
            int b1 = input[i + 1] & 0xFF;
            int h = (FNV_STRIDE2_SALT ^ b0) * FNV_PRIME;
            h = (h ^ b1) * FNV_PRIME;
            int bkt = stride2Bucket(h);
            if (dense[bkt] == 0) {
                touched[n++] = bkt;
            }
            dense[bkt]++;
        }

        // Global features: fire exactly one ASCII-density bin.
        if (useGlobalFeatures) {
            int bkt = globalBase + asciiDensityBin(input);
            if (dense[bkt] == 0) {
                touched[n++] = bkt;
            }
            dense[bkt]++;
        }

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

        // Stride-2 bigrams (same logic as extractSparseInto).
        for (int i = from; i + 1 < to; i += 2) {
            int b0 = b[i] & 0xFF;
            int b1 = b[i + 1] & 0xFF;
            int h = (FNV_STRIDE2_SALT ^ b0) * FNV_PRIME;
            h = (h ^ b1) * FNV_PRIME;
            counts[stride2Bucket(h)]++;
        }

        // Global features: fire exactly one ASCII-density bin.
        if (useGlobalFeatures) {
            byte[] slice = (from == 0 && to == b.length)
                    ? b : java.util.Arrays.copyOfRange(b, from, to);
            counts[globalBase + asciiDensityBin(slice)]++;
        }
    }

    private int stride1Bucket(int hash) {
        return (hash & 0x7fffffff) % stride1Buckets;
    }

    private int stride2Bucket(int hash) {
        return stride2Base + (hash & 0x7fffffff) % stride2Buckets;
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
                "ByteNgramFeatureExtractor{buckets=%d, UB-AS}", numBuckets);
    }
}
