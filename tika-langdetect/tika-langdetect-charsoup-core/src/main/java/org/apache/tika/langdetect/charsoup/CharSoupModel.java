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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * INT8-quantized multinomial logistic regression model for
 * language detection.
 * <p>
 * Binary format (big-endian, magic "LDM1"):
 * <pre>
 *   v1 layout:
 *   Offset  Field
 *   0       4B magic: 0x4C444D31
 *   4       4B version: 1
 *   8       4B numBuckets (B)
 *   12      4B numClasses (C)
 *   16+     Labels: C entries of [2B length + UTF-8 bytes]
 *           Scales: C × 4B float (per-class dequantization)
 *           Biases: C × 4B float (per-class bias term)
 *           Weights: B × C bytes (bucket-major, INT8 signed)
 *
 *   v2 layout (adds feature flags after numClasses):
 *   Offset  Field
 *   0       4B magic: 0x4C444D31
 *   4       4B version: 2
 *   8       4B numBuckets (B)
 *   12      4B numClasses (C)
 *   16      4B featureFlags (bitmask of FLAG_* constants)
 *   20+     Labels, Scales, Biases, Weights (same as v1)
 * </pre>
 * <p>
 * Weights are stored in bucket-major order:
 * {@code weights[bucket * numClasses + class]}. This layout
 * is optimal for the sparse dot-product in {@link #predict}
 * — each non-zero bucket reads a contiguous run of
 * {@code numClasses} bytes, ideal for SIMD and cache
 * prefetching.
 * <p>
 * Feature extraction always uses
 * {@link ScriptAwareFeatureExtractor}, which produces
 * character bigrams (with sentinels for non-CJK), whole-word
 * unigrams, CJK character unigrams, and CJK space bridging.
 */
public class CharSoupModel {

    static final int MAGIC = 0x4C444D31; // "LDM1"
    static final int VERSION_V1 = 1;
    static final int VERSION_V2 = 2;

    /** Feature flag: enable character trigrams. */
    public static final int FLAG_TRIGRAMS      = 1 << 0;
    /** Feature flag: enable skip bigrams. */
    public static final int FLAG_SKIP_BIGRAMS  = 1 << 1;
    /** Feature flag: enable 3-char word suffixes. */
    public static final int FLAG_SUFFIXES      = 1 << 2;
    /** Feature flag: enable 4-char word suffixes. */
    public static final int FLAG_SUFFIX4       = 1 << 3;
    /** Feature flag: enable 3-char word prefixes. */
    public static final int FLAG_PREFIX        = 1 << 4;
    /** Feature flag: enable whole-word unigrams. */
    public static final int FLAG_WORD_UNIGRAMS = 1 << 5;
    /** Feature flag: enable non-CJK character unigrams. */
    public static final int FLAG_CHAR_UNIGRAMS = 1 << 6;
    /** Feature flag: enable character 4-grams. */
    public static final int FLAG_4GRAMS        = 1 << 7;
    /** Feature flag: enable character 5-grams. */
    public static final int FLAG_5GRAMS        = 1 << 8;
    /** Feature flag: enable script-block presence + transition features. */
    public static final int FLAG_SCRIPT_BLOCKS = 1 << 9;
    /** Feature flag: L2-normalize the feature vector before prediction. */
    public static final int FLAG_L2_NORM       = 1 << 10;
    /** Feature flag: short-word-anchored word bigrams (hash pairs where anchor is 1–3 chars). */
    public static final int FLAG_WORD_BIGRAMS  = 1 << 11;
    /** Feature flag: non-CJK word length features (exact length, capped). */
    public static final int FLAG_WORD_LENGTH   = 1 << 12;

    /** Default flags for v1 models (word unigrams only). */
    public static final int V1_DEFAULT_FLAGS = FLAG_WORD_UNIGRAMS;

    private final int numBuckets;
    private final int numClasses;
    private final String[] labels;
    private final float[] scales;
    private final float[] biases;

    /**
     * Flat INT8 weight array in bucket-major order:
     * {@code [bucket * numClasses + class]}.
     */
    private final byte[] flatWeights;

    /**
     * Bitmask of feature flags that were active during training.
     * See {@code FLAG_*} constants. Used by {@link #createExtractor()} to
     * reconstruct the exact same feature extractor at inference time.
     */
    private final int featureFlags;

    /**
     * Construct from class-major {@code byte[][]} weights with default feature
     * configuration (word unigrams only — backward compatible with v1).
     */
    public CharSoupModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights) {
        this(numBuckets, numClasses, labels, scales, biases, weights, V1_DEFAULT_FLAGS);
    }

    /**
     * Construct from class-major {@code byte[][]} weights with explicit feature flags.
     *
     * @param featureFlags bitmask of {@code FLAG_*} constants
     */
    public CharSoupModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights,
                       int featureFlags) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.flatWeights = transposeToBucketMajor(weights, numBuckets, numClasses);
        this.featureFlags = featureFlags;
    }

    private CharSoupModel(int numBuckets, int numClasses,
                        String[] labels, float[] scales,
                        float[] biases, byte[] flatWeights,
                        int featureFlags) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.flatWeights = flatWeights;
        this.featureFlags = featureFlags;
    }

    private static byte[] transposeToBucketMajor(
            byte[][] classMajor, int numBuckets,
            int numClasses) {
        byte[] flat = new byte[numBuckets * numClasses];
        for (int c = 0; c < numClasses; c++) {
            byte[] row = classMajor[c];
            for (int b = 0; b < numBuckets; b++) {
                flat[b * numClasses + c] = row[b];
            }
        }
        return flat;
    }

    // ================================================================
    //  Loading
    // ================================================================

    /**
     * Load a model from the classpath.
     */
    public static CharSoupModel loadFromClasspath(
            String resourcePath) throws IOException {
        try (InputStream is =
                     CharSoupModel.class.getResourceAsStream(
                             resourcePath)) {
            if (is == null) {
                throw new IOException(
                        "Model resource not found: "
                                + resourcePath);
            }
            return load(is);
        }
    }

    /**
     * Load a model from an input stream.
     * Supports both v1 (LDM1) and v2 (LDM2) formats.
     */
    public static CharSoupModel load(InputStream is)
            throws IOException {
        DataInputStream dis = new DataInputStream(is);
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IOException(String.format(Locale.US,
                    "Invalid magic: expected 0x%08X, got 0x%08X",
                    MAGIC, magic));
        }
        int version = dis.readInt();
        if (version != VERSION_V1 && version != VERSION_V2) {
            throw new IOException(
                    "Unsupported version: " + version
                            + " (expected " + VERSION_V1
                            + " or " + VERSION_V2 + ")");
        }

        int numBuckets = dis.readInt();
        int numClasses = dis.readInt();

        int featureFlags = V1_DEFAULT_FLAGS;
        if (version == VERSION_V2) {
            featureFlags = dis.readInt();
        }

        String[] labels = readLabels(dis, numClasses);
        float[] scales = readFloats(dis, numClasses);
        float[] biases = readFloats(dis, numClasses);

        byte[] flat = new byte[numBuckets * numClasses];
        dis.readFully(flat);

        return new CharSoupModel(numBuckets, numClasses,
                labels, scales, biases, flat, featureFlags);
    }

    // ================================================================
    //  Saving
    // ================================================================

    /**
     * Write the model in LDM2 binary format (includes feature flags).
     */
    public void save(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION_V2);
        dos.writeInt(numBuckets);
        dos.writeInt(numClasses);
        dos.writeInt(featureFlags);
        writeLabels(dos);
        writeFloats(dos, scales);
        writeFloats(dos, biases);
        dos.write(flatWeights);
        dos.flush();
    }

    // ================================================================
    //  Inference
    // ================================================================

    /**
     * Compute softmax probabilities for the given feature
     * vector. Uses a sparse inner loop — only non-zero
     * buckets are visited.
     *
     * @param features int array of size {@code numBuckets}
     * @return float array of size {@code numClasses}
     *         (softmax probabilities, sum ≈ 1.0)
     */
    public float[] predict(int[] features) {
        float[] logits = predictLogits(features);
        return softmax(logits);
    }

    /**
     * Compute raw logits (pre-softmax scores) for the given
     * feature vector. Higher logits indicate stronger match.
     * Unlike {@link #predict}, this preserves the full dynamic
     * range of the model's output, which is useful when
     * comparing confidence across different input texts.
     *
     * @param features int array of size {@code numBuckets}
     * @return float array of size {@code numClasses}
     *         (raw logits, not normalized)
     */
    public float[] predictLogits(int[] features) {
        int nnz = 0;
        for (int b = 0; b < numBuckets; b++) {
            if (features[b] != 0) {
                nnz++;
            }
        }
        int[] nzIdx = new int[nnz];
        int pos = 0;
        for (int b = 0; b < numBuckets; b++) {
            if (features[b] != 0) {
                nzIdx[pos++] = b;
            }
        }

        float invNorm = 1.0f;
        if ((featureFlags & FLAG_L2_NORM) != 0) {
            double normSq = 0;
            for (int i = 0; i < nnz; i++) {
                long fv = features[nzIdx[i]];
                normSq += fv * fv;
            }
            invNorm = normSq > 0 ? (float) (1.0 / Math.sqrt(normSq)) : 1.0f;
        }

        long[] dots = new long[numClasses];
        for (int i = 0; i < nnz; i++) {
            int b = nzIdx[i];
            int fv = features[b];
            int off = b * numClasses;
            for (int c = 0; c < numClasses; c++) {
                dots[c] += (long) flatWeights[off + c] * fv;
            }
        }

        float[] logits = new float[numClasses];
        for (int c = 0; c < numClasses; c++) {
            logits[c] = biases[c] + scales[c] * dots[c] * invNorm;
        }
        return logits;
    }

    /**
     * In-place softmax with numerical stability.
     */
    public static float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) {
            if (v > max) {
                max = v;
            }
        }
        float sum = 0f;
        for (int i = 0; i < logits.length; i++) {
            logits[i] = (float) Math.exp(logits[i] - max);
            sum += logits[i];
        }
        if (sum > 0f) {
            for (int i = 0; i < logits.length; i++) {
                logits[i] /= sum;
            }
        }
        return logits;
    }

    /**
     * Shannon entropy (in bits) of a probability distribution.
     */
    public static float entropy(float[] probs) {
        double h = 0.0;
        for (float p : probs) {
            if (p > 0f) {
                h -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return (float) h;
    }

    // ================================================================
    //  Accessors
    // ================================================================

    public int getNumBuckets() {
        return numBuckets;
    }

    public int getNumClasses() {
        return numClasses;
    }

    public String[] getLabels() {
        return labels;
    }

    public String getLabel(int classIndex) {
        return labels[classIndex];
    }

    public float[] getScales() {
        return scales;
    }

    public float[] getBiases() {
        return biases;
    }

    /**
     * Return weights in class-major {@code [class][bucket]}
     * layout. Creates a new array each call.
     */
    public byte[][] getWeights() {
        byte[][] cm = new byte[numClasses][numBuckets];
        for (int b = 0; b < numBuckets; b++) {
            int off = b * numClasses;
            for (int c = 0; c < numClasses; c++) {
                cm[c][b] = flatWeights[off + c];
            }
        }
        return cm;
    }

    /**
     * Create the production {@link FeatureExtractor} for this model by dispatching
     * on the {@link #featureFlags} embedded in the binary.
     * <p>
     * Supported flag sets:
     * <ul>
     *   <li>{@link ScriptAwareFeatureExtractor#FEATURE_FLAGS} — general model</li>
     *   <li>{@link ShortTextFeatureExtractor#FEATURE_FLAGS} — short-text model</li>
     * </ul>
     *
     * @throws IllegalStateException if the flags do not match any known production extractor.
     *         Experimental configs should use {@code ResearchFeatureExtractor} in the test module.
     */
    public FeatureExtractor createExtractor() {
        // L2 norm is handled at prediction time, not in the feature extractor
        int extractorFlags = featureFlags & ~FLAG_L2_NORM;
        if (extractorFlags == ScriptAwareFeatureExtractor.FEATURE_FLAGS) {
            return new ScriptAwareFeatureExtractor(numBuckets, true);
        }
        if (extractorFlags == ScriptAwareFeatureExtractor.FEATURE_FLAGS_LEGACY) {
            return new ScriptAwareFeatureExtractor(numBuckets, false);
        }
        if (extractorFlags == ShortTextFeatureExtractor.FEATURE_FLAGS) {
            return new ShortTextFeatureExtractor(numBuckets, true);
        }
        if (extractorFlags == ShortTextFeatureExtractor.FEATURE_FLAGS_LEGACY) {
            return new ShortTextFeatureExtractor(numBuckets, false);
        }
        if (extractorFlags == SaltedNgramFeatureExtractor.FEATURE_FLAGS) {
            return new SaltedNgramFeatureExtractor(numBuckets);
        }
        if (extractorFlags == SaltedNgramFeatureExtractor.FEATURE_FLAGS_WITH_WORD_BIGRAMS) {
            return new SaltedNgramFeatureExtractor(numBuckets, true);
        }
        if (extractorFlags == SaltedNgramFeatureExtractor.FEATURE_FLAGS_V11) {
            return new SaltedNgramFeatureExtractor(numBuckets, true, true);
        }
        throw new IllegalStateException(String.format(
                Locale.ROOT,
                "No production FeatureExtractor for featureFlags=0x%03x. "
                + "Known: ScriptAware=0x%03x, ScriptAwareLegacy=0x%03x, "
                + "ShortText=0x%03x, ShortTextLegacy=0x%03x, "
                + "SaltedNgram=0x%03x, SaltedNgramWordBigrams=0x%03x, "
                + "SaltedNgramV11=0x%03x. "
                + "Use ResearchFeatureExtractor (test scope) for experimental configs.",
                extractorFlags,
                ScriptAwareFeatureExtractor.FEATURE_FLAGS,
                ScriptAwareFeatureExtractor.FEATURE_FLAGS_LEGACY,
                ShortTextFeatureExtractor.FEATURE_FLAGS,
                ShortTextFeatureExtractor.FEATURE_FLAGS_LEGACY,
                SaltedNgramFeatureExtractor.FEATURE_FLAGS,
                SaltedNgramFeatureExtractor.FEATURE_FLAGS_WITH_WORD_BIGRAMS,
                SaltedNgramFeatureExtractor.FEATURE_FLAGS_V11));
    }

    public int getFeatureFlags() {
        return featureFlags;
    }

    /**
     * Returns a new model with the same weights but a different feature-flags bitmask.
     * Useful for correcting flags on models saved before this field was properly set.
     *
     * @param newFlags bitmask of {@code FLAG_*} constants
     * @return copy of this model with updated feature flags
     */
    public CharSoupModel withFeatureFlags(int newFlags) {
        return new CharSoupModel(numBuckets, numClasses, labels.clone(),
                scales.clone(), biases.clone(), flatWeights.clone(), newFlags);
    }

    // ================================================================
    //  Internal I/O helpers
    // ================================================================

    private static String[] readLabels(DataInputStream dis,
                                        int numClasses)
            throws IOException {
        String[] labels = new String[numClasses];
        for (int c = 0; c < numClasses; c++) {
            int len = dis.readUnsignedShort();
            byte[] utf8 = new byte[len];
            dis.readFully(utf8);
            labels[c] = new String(utf8, StandardCharsets.UTF_8);
        }
        return labels;
    }

    private static float[] readFloats(DataInputStream dis,
                                       int count)
            throws IOException {
        float[] arr = new float[count];
        for (int i = 0; i < count; i++) {
            arr[i] = dis.readFloat();
        }
        return arr;
    }

    private void writeLabels(DataOutputStream dos)
            throws IOException {
        for (int c = 0; c < numClasses; c++) {
            byte[] utf8 =
                    labels[c].getBytes(StandardCharsets.UTF_8);
            dos.writeShort(utf8.length);
            dos.write(utf8);
        }
    }

    private static void writeFloats(DataOutputStream dos,
                                     float[] arr)
            throws IOException {
        for (float v : arr) {
            dos.writeFloat(v);
        }
    }
}
