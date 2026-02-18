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
 *   Offset  Field
 *   0       4B magic: 0x4C444D31
 *   4       4B version: 1
 *   8       4B numBuckets (B)
 *   12      4B numClasses (C)
 *   16+     Labels: C entries of [2B length + UTF-8 bytes]
 *           Scales: C × 4B float (per-class dequantization)
 *           Biases: C × 4B float (per-class bias term)
 *           Weights: B × C bytes (bucket-major, INT8 signed)
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
    static final int VERSION = 1;

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
     * Construct from class-major {@code byte[][]} weights.
     * Transposes to bucket-major flat layout internally.
     */
    public CharSoupModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.flatWeights = transposeToBucketMajor(weights, numBuckets, numClasses);
    }

    private CharSoupModel(int numBuckets, int numClasses,
                        String[] labels, float[] scales,
                        float[] biases, byte[] flatWeights) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.flatWeights = flatWeights;
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
        if (version != VERSION) {
            throw new IOException(
                    "Unsupported version: " + version
                            + " (expected " + VERSION + ")");
        }

        int numBuckets = dis.readInt();
        int numClasses = dis.readInt();

        String[] labels = readLabels(dis, numClasses);
        float[] scales = readFloats(dis, numClasses);
        float[] biases = readFloats(dis, numClasses);

        byte[] flat = new byte[numBuckets * numClasses];
        dis.readFully(flat);

        return new CharSoupModel(numBuckets, numClasses,
                labels, scales, biases, flat);
    }

    // ================================================================
    //  Saving
    // ================================================================

    /**
     * Write the model in LDM1 binary format.
     */
    public void save(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeInt(numBuckets);
        dos.writeInt(numClasses);
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
            logits[c] = biases[c] + scales[c] * dots[c];
        }
        return softmax(logits);
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
     * Create the {@link ScriptAwareFeatureExtractor} for this
     * model. Ensures inference uses the same feature extraction
     * pipeline as training.
     */
    public FeatureExtractor createExtractor() {
        return new ScriptAwareFeatureExtractor(numBuckets);
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
