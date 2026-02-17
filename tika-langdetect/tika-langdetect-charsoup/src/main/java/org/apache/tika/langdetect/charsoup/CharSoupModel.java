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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
     * Null when weights are memory-mapped.
     */
    private final byte[] flatWeights;

    /**
     * Memory-mapped weight buffer for zero-copy inference.
     * Null for heap-loaded models.
     */
    private final MappedByteBuffer mappedWeights;

    /**
     * Construct from class-major {@code byte[][]} weights.
     * Transposes to bucket-major flat layout internally.
     */
    public CharSoupModel(int numBuckets, int numClasses,
                       String[] labels, float[] scales,
                       float[] biases, byte[][] weights) {
        this(numBuckets, numClasses, labels, scales, biases,
                transposeToBucketMajor(weights, numBuckets,
                        numClasses),
                null);
    }

    /**
     * Private constructor for pre-flattened bucket-major
     * weights (heap or mapped). Exactly one of
     * {@code flatWeights} / {@code mappedWeights} must
     * be non-null.
     */
    private CharSoupModel(int numBuckets, int numClasses,
                        String[] labels, float[] scales,
                        float[] biases, byte[] flatWeights,
                        MappedByteBuffer mappedWeights) {
        this.numBuckets = numBuckets;
        this.numClasses = numClasses;
        this.labels = labels;
        this.scales = scales;
        this.biases = biases;
        this.flatWeights = flatWeights;
        this.mappedWeights = mappedWeights;
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
        readAndVerifyHeader(dis);

        int numBuckets = dis.readInt();
        int numClasses = dis.readInt();

        String[] labels = readLabels(dis, numClasses);
        float[] scales = readFloats(dis, numClasses);
        float[] biases = readFloats(dis, numClasses);

        byte[] flat = new byte[numBuckets * numClasses];
        dis.readFully(flat);

        return new CharSoupModel(numBuckets, numClasses,
                labels, scales, biases, flat, null);
    }

    /**
     * Load a model by memory-mapping a file. The weight blob
     * is read via a single bulk copy from the mapped region.
     */
    public static CharSoupModel loadMapped(Path path)
            throws IOException {
        try (FileChannel ch = FileChannel.open(
                path, StandardOpenOption.READ)) {
            MappedByteBuffer buf = ch.map(
                    FileChannel.MapMode.READ_ONLY,
                    0, ch.size());
            buf.order(ByteOrder.BIG_ENDIAN);

            verifyMagicAndVersion(buf);
            int numBuckets = buf.getInt();
            int numClasses = buf.getInt();

            String[] labels = readLabels(buf, numClasses);
            float[] scales = readFloats(buf, numClasses);
            float[] biases = readFloats(buf, numClasses);

            byte[] flat = new byte[numBuckets * numClasses];
            buf.get(flat);

            return new CharSoupModel(numBuckets, numClasses,
                    labels, scales, biases, flat, null);
        }
    }

    /**
     * Load with zero-copy weights from a split model (raw
     * weight blob + metadata sidecar).
     */
    public static CharSoupModel loadZeroCopy(
            Path weightsFile, Path metaFile)
            throws IOException {
        int numBuckets;
        int numClasses;
        String[] labels;
        float[] scales;
        float[] biases;

        try (InputStream is =
                     java.nio.file.Files.newInputStream(metaFile);
             DataInputStream dis = new DataInputStream(is)) {
            readAndVerifyHeader(dis);
            numBuckets = dis.readInt();
            numClasses = dis.readInt();
            labels = readLabels(dis, numClasses);
            scales = readFloats(dis, numClasses);
            biases = readFloats(dis, numClasses);
        }

        long expectedSize = (long) numBuckets * numClasses;
        try (FileChannel ch = FileChannel.open(
                weightsFile, StandardOpenOption.READ)) {
            if (ch.size() != expectedSize) {
                throw new IOException(String.format(Locale.US,
                        "Weights file size mismatch:"
                                + " expected %d, got %d",
                        expectedSize, ch.size()));
            }
            MappedByteBuffer mapped = ch.map(
                    FileChannel.MapMode.READ_ONLY,
                    0, expectedSize);

            return new CharSoupModel(numBuckets, numClasses,
                    labels, scales, biases, null, mapped);
        }
    }

    // ================================================================
    //  Saving
    // ================================================================

    /**
     * Write the model in LDM1 binary format.
     */
    public void save(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(os);
        writeHeader(dos);
        dos.writeInt(numBuckets);
        dos.writeInt(numClasses);
        writeLabels(dos);
        writeFloats(dos, scales);
        writeFloats(dos, biases);

        if (flatWeights != null) {
            dos.write(flatWeights);
        } else {
            byte[] buf = new byte[numBuckets * numClasses];
            mappedWeights.position(0);
            mappedWeights.get(buf);
            dos.write(buf);
        }
        dos.flush();
    }

    /**
     * Save as two files: raw weight blob + metadata sidecar.
     * The weights file is directly mmappable with zero parsing.
     */
    public void saveSplit(Path weightsFile, Path metaFile)
            throws IOException {
        byte[] weights = flatWeights;
        if (weights == null) {
            weights = new byte[numBuckets * numClasses];
            mappedWeights.position(0);
            mappedWeights.get(weights);
        }
        java.nio.file.Files.write(weightsFile, weights);

        try (OutputStream os =
                     java.nio.file.Files.newOutputStream(metaFile);
             DataOutputStream dos = new DataOutputStream(os)) {
            writeHeader(dos);
            dos.writeInt(numBuckets);
            dos.writeInt(numClasses);
            writeLabels(dos);
            writeFloats(dos, scales);
            writeFloats(dos, biases);
            dos.flush();
        }
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
        if (mappedWeights != null) {
            for (int i = 0; i < nnz; i++) {
                int b = nzIdx[i];
                int fv = features[b];
                int off = b * numClasses;
                for (int c = 0; c < numClasses; c++) {
                    dots[c] += (long) mappedWeights.get(
                            off + c) * fv;
                }
            }
        } else {
            for (int i = 0; i < nnz; i++) {
                int b = nzIdx[i];
                int fv = features[b];
                int off = b * numClasses;
                for (int c = 0; c < numClasses; c++) {
                    dots[c] += (long) flatWeights[
                            off + c] * fv;
                }
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
        if (flatWeights != null) {
            for (int b = 0; b < numBuckets; b++) {
                int off = b * numClasses;
                for (int c = 0; c < numClasses; c++) {
                    cm[c][b] = flatWeights[off + c];
                }
            }
        } else {
            for (int b = 0; b < numBuckets; b++) {
                int off = b * numClasses;
                for (int c = 0; c < numClasses; c++) {
                    cm[c][b] = mappedWeights.get(off + c);
                }
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

    private static void readAndVerifyHeader(DataInputStream dis)
            throws IOException {
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
    }

    private static void verifyMagicAndVersion(ByteBuffer buf)
            throws IOException {
        int magic = buf.getInt();
        if (magic != MAGIC) {
            throw new IOException(String.format(Locale.US,
                    "Invalid magic: expected 0x%08X, got 0x%08X",
                    MAGIC, magic));
        }
        int version = buf.getInt();
        if (version != VERSION) {
            throw new IOException(
                    "Unsupported version: " + version
                            + " (expected " + VERSION + ")");
        }
    }

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

    private static String[] readLabels(ByteBuffer buf,
                                        int numClasses) {
        String[] labels = new String[numClasses];
        for (int c = 0; c < numClasses; c++) {
            int len = buf.getShort() & 0xFFFF;
            byte[] utf8 = new byte[len];
            buf.get(utf8);
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

    private static float[] readFloats(ByteBuffer buf,
                                       int count) {
        float[] arr = new float[count];
        for (int i = 0; i < count; i++) {
            arr[i] = buf.getFloat();
        }
        return arr;
    }

    private static void writeHeader(DataOutputStream dos)
            throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
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
