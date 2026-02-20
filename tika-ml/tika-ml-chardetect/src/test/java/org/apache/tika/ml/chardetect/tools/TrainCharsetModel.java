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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.LinearModel;
import org.apache.tika.ml.chardetect.ByteNgramFeatureExtractor;
import org.apache.tika.ml.chardetect.CharsetConfusables;

/**
 * Trains a {@link LinearModel} for charset detection from the binary training
 * data produced by {@code build_charset_training.py}.
 * <p>
 * <strong>Usage:</strong>
 * <pre>
 *   java TrainCharsetModel \
 *     --data /path/to/chardet-training \
 *     --output chardetect.bin \
 *     [--buckets 65536] \
 *     [--epochs 3] \
 *     [--lr 0.05] \
 *     [--max-samples-per-class 500000]
 * </pre>
 * <p>
 * Training file format (per charset, one {@code <charset>.bin.gz} file):
 * <pre>
 *   [uint16 length][bytes of that length]
 *   ... repeated ...
 * </pre>
 * Each record is a raw byte chunk that should be classified as the charset
 * named by the filename.
 */
public class TrainCharsetModel {

    private static final int DEFAULT_NUM_BUCKETS = 65536;
    private static final int DEFAULT_EPOCHS = 3;
    private static final float DEFAULT_LR = 0.05f;
    private static final int DEFAULT_MAX_SAMPLES = 500_000;

    public static void main(String[] args) throws IOException {
        Path dataDir = null;
        Path outputPath = Paths.get("chardetect.bin");
        int numBuckets = DEFAULT_NUM_BUCKETS;
        int epochs = DEFAULT_EPOCHS;
        float lr = DEFAULT_LR;
        int maxSamplesPerClass = DEFAULT_MAX_SAMPLES;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    outputPath = Paths.get(args[++i]);
                    break;
                case "--buckets":
                    numBuckets = Integer.parseInt(args[++i]);
                    break;
                case "--epochs":
                    epochs = Integer.parseInt(args[++i]);
                    break;
                case "--lr":
                    lr = Float.parseFloat(args[++i]);
                    break;
                case "--max-samples-per-class":
                    maxSamplesPerClass = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (dataDir == null) {
            System.err.println("Usage: TrainCharsetModel --data <dir> [options]");
            System.exit(1);
        }

        // Discover charset files
        List<Path> charsetFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .sorted()
                .collect(Collectors.toList());

        if (charsetFiles.isEmpty()) {
            System.err.println("No .bin.gz files found in: " + dataDir);
            System.exit(1);
        }

        // Build class label list (charset names stripped of .bin.gz)
        String[] labels = charsetFiles.stream()
                .map(p -> p.getFileName().toString()
                        .replaceAll("\\.bin\\.gz$", ""))
                .toArray(String[]::new);
        int numClasses = labels.length;

        System.out.println("Classes (" + numClasses + "): " + Arrays.toString(labels));
        System.out.println("Buckets: " + numBuckets + ", epochs: " + epochs +
                ", lr: " + lr + ", max-samples/class: " + maxSamplesPerClass);

        ByteNgramFeatureExtractor extractor =
                new ByteNgramFeatureExtractor(numBuckets, true);

        // Build class index map
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < numClasses; i++) {
            labelIndex.put(labels[i], i);
        }

        // Load samples (up to maxSamplesPerClass per class)
        System.out.println("Loading training data ...");
        List<byte[]>[] samplesPerClass = new List[numClasses];
        long totalSamples = 0;
        for (int ci = 0; ci < numClasses; ci++) {
            samplesPerClass[ci] = loadSamples(charsetFiles.get(ci), maxSamplesPerClass);
            totalSamples += samplesPerClass[ci].size();
            System.out.printf(java.util.Locale.ROOT, "  %-30s  %,d samples%n", labels[ci], samplesPerClass[ci].size());
        }
        System.out.printf(java.util.Locale.ROOT, "Total training samples: %,d%n", totalSamples);

        // SGD training: multinomial logistic regression with L2 regularisation
        // Weight matrix: [numClasses][numBuckets]
        float[][] weights = new float[numClasses][numBuckets];
        float[] biases = new float[numClasses];
        float lambda = 1e-5f; // L2 regularisation coefficient

        // Build a shuffled training index: list of (classIndex, sampleIndex) pairs
        List<int[]> index = new ArrayList<>((int) Math.min(totalSamples, Integer.MAX_VALUE));
        for (int ci = 0; ci < numClasses; ci++) {
            for (int si = 0; si < samplesPerClass[ci].size(); si++) {
                index.add(new int[]{ci, si});
            }
        }

        // Build int[][] group indices for probability collapsing and per-charset eval
        int[][] groupIndices = CharsetConfusables.buildGroupIndices(labels);

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(index);
            long strictCorrect = 0;
            float lossSum = 0f;
            int count = 0;

            for (int[] pair : index) {
                int trueClass = pair[0];
                byte[] sample = samplesPerClass[trueClass].get(pair[1]);

                int[] features = extractor.extract(sample);

                // Forward pass: compute logits
                float[] logits = new float[numClasses];
                for (int c = 0; c < numClasses; c++) {
                    float dot = biases[c];
                    for (int b = 0; b < numBuckets; b++) {
                        if (features[b] != 0) {
                            dot += weights[c][b] * features[b];
                        }
                    }
                    logits[c] = dot;
                }
                float[] probs = LinearModel.softmax(logits.clone());

                // Cross-entropy loss on the true class — confusable groups play no
                // part in the gradient; lenient scoring belongs only in the evaluator.
                float loss = -(float) Math.log(Math.max(probs[trueClass], 1e-12f));
                lossSum += loss;

                if (argmax(probs) == trueClass) {
                    strictCorrect++;
                }

                // Backward pass: gradient = probs - one_hot(trueClass)
                float[] grad = probs.clone();
                grad[trueClass] -= 1f;

                // SGD update with L2 regularisation
                for (int c = 0; c < numClasses; c++) {
                    float g = grad[c];
                    biases[c] -= lr * g;
                    for (int b = 0; b < numBuckets; b++) {
                        if (features[b] != 0) {
                            weights[c][b] -= lr * (g * features[b] + lambda * weights[c][b]);
                        } else {
                            weights[c][b] *= (1f - lr * lambda);
                        }
                    }
                }
                count++;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "Epoch %d/%d  loss=%.4f  strict-acc=%.2f%%%n",
                    epoch + 1, epochs, lossSum / count,
                    100.0 * strictCorrect / count);
        }

        // Quantize and save
        System.out.println("Quantizing ...");
        String[] qLabels = labels;
        float[] qScales = new float[numClasses];
        float[] qBiases = biases;
        byte[][] qWeights = new byte[numClasses][numBuckets];

        for (int c = 0; c < numClasses; c++) {
            float maxAbs = 1e-6f;
            for (float w : weights[c]) {
                float abs = Math.abs(w);
                if (abs > maxAbs) {
                    maxAbs = abs;
                }
            }
            qScales[c] = maxAbs / 127f;
            for (int b = 0; b < numBuckets; b++) {
                int q = Math.round(weights[c][b] / qScales[c]);
                qWeights[c][b] = (byte) Math.max(-127, Math.min(127, q));
            }
        }

        LinearModel model = new LinearModel(numBuckets, numClasses,
                qLabels, qScales, qBiases, qWeights);

        try (OutputStream os = new FileOutputStream(outputPath.toFile())) {
            model.save(os);
        }
        System.out.println("Model saved to: " + outputPath);

        // Per-charset evaluation on the training data (in-sample, sanity check)
        System.out.println("\nPer-charset evaluation (quantized model, training data):");
        evaluatePerCharset(model, extractor, samplesPerClass, labels, groupIndices);

        System.out.println("Done.");
    }

    private static List<byte[]> loadSamples(Path file, int maxSamples) throws IOException {
        List<byte[]> samples = new ArrayList<>();
        try (InputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (samples.size() < maxSamples) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (java.io.EOFException e) {
                    break;
                }
                byte[] chunk = new byte[len];
                dis.readFully(chunk);
                samples.add(chunk);
            }
        }
        return samples;
    }

    /**
     * Evaluate the quantized model on the training samples and print a table
     * showing per-charset strict accuracy and lenient accuracy (confusable group
     * match counts as correct).
     */
    private static void evaluatePerCharset(
            LinearModel model,
            ByteNgramFeatureExtractor extractor,
            List<byte[]>[] samplesPerClass,
            String[] labels,
            int[][] groupIndices) {

        int numClasses = labels.length;
        int[] strictCorrect = new int[numClasses];
        int[] lenientCorrect = new int[numClasses];
        int[] totals = new int[numClasses];

        for (int trueClass = 0; trueClass < numClasses; trueClass++) {
            for (byte[] sample : samplesPerClass[trueClass]) {
                int[] features = extractor.extract(sample);
                float[] probs = CharsetConfusables.collapseGroups(
                        model.predict(features), groupIndices);
                int predicted = argmax(probs);
                totals[trueClass]++;
                if (predicted == trueClass) {
                    strictCorrect[trueClass]++;
                    lenientCorrect[trueClass]++;
                } else if (CharsetConfusables.isLenientMatch(
                        labels[trueClass], labels[predicted])) {
                    lenientCorrect[trueClass]++;
                }
            }
        }

        // Print table
        int maxLabelLen = 0;
        for (String l : labels) {
            maxLabelLen = Math.max(maxLabelLen, l.length());
        }
        String fmt = "  %-" + maxLabelLen + "s  %7d  %7.2f%%  %7.2f%%  %s%n";
        System.out.printf(java.util.Locale.ROOT,
                "  %-" + maxLabelLen + "s  %7s  %8s  %8s%n",
                "Charset", "N", "Strict", "Soft");
        System.out.println("  " + "-".repeat(maxLabelLen + 32));

        long totalStrict = 0;
        long totalLenient = 0;
        long totalN = 0;
        for (int c = 0; c < numClasses; c++) {
            int n = totals[c];
            if (n == 0) {
                continue;
            }
            double strict = 100.0 * strictCorrect[c] / n;
            double lenient = 100.0 * lenientCorrect[c] / n;
            // Flag rows where lenient > strict (confusable errors are happening)
            String flag = (lenientCorrect[c] > strictCorrect[c]) ? "*" : "";
            System.out.printf(java.util.Locale.ROOT, fmt,
                    labels[c], n, strict, lenient, flag);
            totalStrict += strictCorrect[c];
            totalLenient += lenientCorrect[c];
            totalN += n;
        }
        System.out.println("  " + "-".repeat(maxLabelLen + 32));
        System.out.printf(java.util.Locale.ROOT, fmt,
                "OVERALL", totalN,
                100.0 * totalStrict / totalN,
                100.0 * totalLenient / totalN, "");
        System.out.println("  (* = confusable-group errors present; lenient > strict)");
    }

    private static int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[best]) {
                best = i;
            }
        }
        return best;
    }
}
