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
package org.apache.tika.ml;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * INT8-quantized multinomial logistic regression model for classification.
 * <p>
 * Binary format (big-endian, magic "LDM1"):
 * <pre>
 *   Offset  Field
 *   0       4B magic: 0x4C444D31
 *   4       4B version: 1 or 2
 *   8       4B numBuckets (B)
 *   12      4B numClasses (C)
 *   16+     Labels: C entries of [2B length + UTF-8 bytes]
 *           Scales: C × 4B float (per-class dequantization)
 *           Biases: C × 4B float (per-class bias term)
 *           (V2 only)
 *           1B hasCalibration flag
 *           If hasCalibration: ClassMean: C × 4B float, ClassStd: C × 4B float
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
 * Calibration (V2): optional per-class mean/std of training-set logits.
 * When present, {@link #predictCalibratedLogits} standardizes raw logits
 * so cross-specialist pooling can compare "unusually confident" signals on
 * equal footing.  V1 files are still readable; calibration is absent and
 * {@link #predictCalibratedLogits} falls back to raw logits.
 */
public class LinearModel {

    public static final int MAGIC = 0x4C444D31; // "LDM1"
    public static final int VERSION_V1 = 1;
    public static final int VERSION_V2 = 2;
    /**
     * Latest version we emit.
     */
    public static final int VERSION = VERSION_V2;

    private final int numBuckets;
    private final int numClasses;
    private final String[] labels;
    private final float[] scales;
    private final float[] biases;
    /**
     * Optional per-class logit mean for calibration; {@code null} if absent.
     */
    private final float[] classMean;
    /**
     * Optional per-class logit std (never zero when present).
     */
    private final float[] classStd;

    /**
     * Flat INT8 weight array in bucket-major order:
     * {@code [bucket * numClasses + class]}.
     */
    private final byte[] flatWeights;

    /**
     * Construct without calibration (V1-compatible).
     * Transposes class-major weights to bucket-major flat layout internally.
     */
    public LinearModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights) {
        this(numBuckets, numClasses, labels, scales, biases, weights, null, null);
    }

    /**
     * Construct with optional calibration.  Pass {@code classMean} and
     * {@code classStd} (each of length {@code numClasses}) to enable
     * z-score calibration in {@link #predictCalibratedLogits}; pass
     * {@code null} for both to skip.  Any {@code classStd[c] == 0} is
     * rewritten to {@code 1.0f} to avoid divide-by-zero.
     */
    public LinearModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights,
                       float[] classMean, float[] classStd) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.classMean = classMean;
        this.classStd = sanitizeStd(classStd);
        this.flatWeights = transposeToBucketMajor(weights, numBuckets, numClasses);
        validateCalibration();
    }

    private LinearModel(int numBuckets, int numClasses,
                        String[] labels, float[] scales,
                        float[] biases, byte[] flatWeights,
                        float[] classMean, float[] classStd) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.classMean = classMean;
        this.classStd = sanitizeStd(classStd);
        this.flatWeights = flatWeights;
        validateCalibration();
    }

    private static float[] sanitizeStd(float[] std) {
        if (std == null) {
            return null;
        }
        float[] out = new float[std.length];
        for (int i = 0; i < std.length; i++) {
            out[i] = std[i] > 0f ? std[i] : 1.0f;
        }
        return out;
    }

    private void validateCalibration() {
        if ((classMean == null) != (classStd == null)) {
            throw new IllegalArgumentException(
                    "classMean and classStd must both be provided or both null");
        }
        if (classMean != null && classMean.length != numClasses) {
            throw new IllegalArgumentException(
                    "classMean length " + classMean.length + " != numClasses " + numClasses);
        }
        if (classStd != null && classStd.length != numClasses) {
            throw new IllegalArgumentException(
                    "classStd length " + classStd.length + " != numClasses " + numClasses);
        }
    }

    private static byte[] transposeToBucketMajor(
            byte[][] classMajor, int numBuckets, int numClasses) {
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
     * Load a model from the classpath.  Transparently handles both plain
     * LDM1 binaries and gzip-compressed LDM1 binaries (detected by magic bytes).
     */
    public static LinearModel loadFromClasspath(String resourcePath) throws IOException {
        try (InputStream is = LinearModel.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Model resource not found: " + resourcePath);
            }
            return load(is);
        }
    }

    /**
     * Load a model from a file on disk.  Transparently handles both plain
     * and gzip-compressed LDM1 files.
     */
    public static LinearModel loadFromPath(java.nio.file.Path path) throws IOException {
        try (InputStream is = new BufferedInputStream(
                java.nio.file.Files.newInputStream(path))) {
            return load(is);
        }
    }

    /**
     * Load a model from an input stream.  Transparently handles both plain
     * LDM1 binaries and gzip-compressed ones: if the first two bytes are the
     * gzip magic {@code 0x1F 0x8B} the stream is wrapped in a
     * {@link GZIPInputStream} before reading.
     */
    public static LinearModel load(InputStream is) throws IOException {
        // Buffer so we can peek at the magic without consuming it
        BufferedInputStream buf = is instanceof BufferedInputStream
                ? (BufferedInputStream) is : new BufferedInputStream(is);
        buf.mark(2);
        int b0 = buf.read();
        int b1 = buf.read();
        buf.reset();
        if (b0 == 0x1F && b1 == 0x8B) {
            is = new GZIPInputStream(buf);
        } else {
            is = buf;
        }
        return loadRaw(is);
    }

    /**
     * Read LDM from an already-unwrapped (non-gzip) stream.
     */
    private static LinearModel loadRaw(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(is);
        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new IOException(String.format(Locale.US,
                    "Invalid magic: expected 0x%08X, got 0x%08X", MAGIC, magic));
        }
        int version = dis.readInt();
        if (version != VERSION_V1 && version != VERSION_V2) {
            throw new IOException(
                    "Unsupported version: " + version
                            + " (expected " + VERSION_V1 + " or " + VERSION_V2 + ")");
        }

        int numBuckets = dis.readInt();
        int numClasses = dis.readInt();

        String[] labels = readLabels(dis, numClasses);
        float[] scales = readFloats(dis, numClasses);
        float[] biases = readFloats(dis, numClasses);

        float[] classMean = null;
        float[] classStd = null;
        if (version >= VERSION_V2) {
            boolean hasCalibration = dis.readBoolean();
            if (hasCalibration) {
                classMean = readFloats(dis, numClasses);
                classStd = readFloats(dis, numClasses);
            }
        }

        byte[] flat = new byte[numBuckets * numClasses];
        dis.readFully(flat);

        return new LinearModel(numBuckets, numClasses, labels, scales, biases,
                flat, classMean, classStd);
    }

    // ================================================================
    //  Saving
    // ================================================================

    /**
     * Write the model in LDM binary format.  Emits V2 (with or without
     * calibration block depending on whether this model has calibration).
     */
    public void save(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION_V2);
        dos.writeInt(numBuckets);
        dos.writeInt(numClasses);
        writeLabels(dos);
        writeFloats(dos, scales);
        writeFloats(dos, biases);
        boolean hasCal = hasCalibration();
        dos.writeBoolean(hasCal);
        if (hasCal) {
            writeFloats(dos, classMean);
            writeFloats(dos, classStd);
        }
        dos.write(flatWeights);
        dos.flush();
    }

    // ================================================================
    //  Inference
    // ================================================================

    /**
     * Compute raw logits for the given feature vector (before softmax).
     * Uses a sparse inner loop — only non-zero buckets are visited.
     *
     * @param features int array of size {@code numBuckets}
     * @return float array of size {@code numClasses} (raw, unnormalized logits)
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

        float clip = 1.5f * (float) Math.sqrt(nnz);

        float[] logits = new float[numClasses];
        for (int c = 0; c < numClasses; c++) {
            float dot = 0f;
            float sc = scales[c];
            for (int i = 0; i < nnz; i++) {
                int b = nzIdx[i];
                float w = sc * flatWeights[b * numClasses + c] * features[b];
                dot += Math.max(-clip, Math.min(clip, w));
            }
            logits[c] = biases[c] + dot;
        }
        return logits;
    }

    /**
     * Compute logits for a <em>dense</em> float feature vector.  Unlike
     * {@link #predictLogits(int[])}, which assumes sparse integer counts
     * and applies per-bucket clipping to suppress single-feature dominance
     * in hashed representations, this method just performs a plain
     * dot product — appropriate for adjudicator / meta-model feature
     * vectors where each slot is already a calibrated quantity
     * (specialist logit, z-score, one-hot flag, etc.).
     *
     * @param features float array of length {@code numBuckets}
     * @return float array of length {@code numClasses} (raw logits)
     */
    public float[] predictLogitsDense(float[] features) {
        if (features.length != numBuckets) {
            throw new IllegalArgumentException(
                    "features.length " + features.length + " != numBuckets " + numBuckets);
        }
        float[] logits = new float[numClasses];
        for (int c = 0; c < numClasses; c++) {
            float dot = 0f;
            float sc = scales[c];
            for (int i = 0; i < numBuckets; i++) {
                dot += sc * flatWeights[i * numClasses + c] * features[i];
            }
            logits[c] = biases[c] + dot;
        }
        return logits;
    }

    /**
     * Compute softmax probabilities for the given feature vector.
     *
     * @param features int array of size {@code numBuckets}
     * @return float array of size {@code numClasses} (softmax probabilities, sum ≈ 1.0)
     */
    public float[] predict(int[] features) {
        return softmax(predictLogits(features));
    }

    /**
     * Compute calibrated logits: {@code (raw - classMean[c]) / classStd[c]}
     * for each class, if the model carries calibration statistics, else raw
     * logits (no-op).  Calibrated logits are comparable across specialists
     * with different natural logit scales — they express "how many standard
     * deviations above this class's training-set mean" rather than raw weight
     * arithmetic.
     */
    public float[] predictCalibratedLogits(int[] features) {
        float[] raw = predictLogits(features);
        if (classMean == null || classStd == null) {
            return raw;
        }
        for (int c = 0; c < numClasses; c++) {
            raw[c] = (raw[c] - classMean[c]) / classStd[c];
        }
        return raw;
    }

    /**
     * {@code true} if this model carries per-class calibration statistics.
     */
    public boolean hasCalibration() {
        return classMean != null && classStd != null;
    }

    public float[] getClassMean() {
        return classMean;
    }

    public float[] getClassStd() {
        return classStd;
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
     * Return weights in class-major {@code [class][bucket]} layout.
     * Creates a new array each call.
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

    // ================================================================
    //  Internal I/O helpers
    // ================================================================

    private static String[] readLabels(DataInputStream dis, int numClasses) throws IOException {
        String[] labels = new String[numClasses];
        for (int c = 0; c < numClasses; c++) {
            int len = dis.readUnsignedShort();
            byte[] utf8 = new byte[len];
            dis.readFully(utf8);
            labels[c] = new String(utf8, StandardCharsets.UTF_8);
        }
        return labels;
    }

    private static float[] readFloats(DataInputStream dis, int count) throws IOException {
        float[] arr = new float[count];
        for (int i = 0; i < count; i++) {
            arr[i] = dis.readFloat();
        }
        return arr;
    }

    private void writeLabels(DataOutputStream dos) throws IOException {
        for (int c = 0; c < numClasses; c++) {
            byte[] utf8 = labels[c].getBytes(StandardCharsets.UTF_8);
            dos.writeShort(utf8.length);
            dos.write(utf8);
        }
    }

    private static void writeFloats(DataOutputStream dos, float[] arr) throws IOException {
        for (float v : arr) {
            dos.writeFloat(v);
        }
    }
}
