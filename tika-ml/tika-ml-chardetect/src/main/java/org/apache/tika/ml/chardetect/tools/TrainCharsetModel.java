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
import java.util.Set;
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

    private static final int DEFAULT_NUM_BUCKETS = ByteNgramFeatureExtractor.NUM_BUCKETS;
    private static final int DEFAULT_EPOCHS = 3;
    private static final float DEFAULT_LR = 0.05f;
    private static final int DEFAULT_MAX_SAMPLES = 500_000;

    /**
     * Labels the main SBCS "kitchen-sink" model is trained on today.
     *
     * <p>Include-list semantics (not exclude): {@link BuildCharsetTrainingData}
     * generates training corpora for many more labels than these (EBCDIC
     * nationals, DOS OEM, Mac charsets, extended ISO-8859 variants, etc.),
     * pre-positioned for future specialists; today's SBCS consumes only the
     * explicit set below.  Hardcoded here so the model's class set is
     * versioned in git alongside the code that uses it — past retraining
     * runs with inconsistent CLI flags were a recurring source of mismatched
     * inference/training feature sets.</p>
     *
     * <p>Baseline is the v6 label set ({@code chardetect-v6-no-utf32.bin},
     * 35 classes), with these changes:</p>
     * <ul>
     *   <li><b>Removed</b> {@code IBM424-ltr/rtl}, {@code IBM420-ltr/rtl}
     *       (Hebrew/Arabic EBCDIC) — content bytes occupy {@code 0x41–0x6A},
     *       entirely below the {@code 0x80} threshold the shipped
     *       {@link ByteNgramFeatureExtractor} considers.  Training on these
     *       labels teaches weights the inference path cannot match.</li>
     *   <li><b>Removed</b> {@code IBM1047} — byte-identical to {@code IBM500}
     *       on most prose; having both as classes splits the EBCDIC-Latin
     *       signal without adding discrimination.</li>
     *   <li><b>Removed</b> {@code UTF-16-LE} / {@code UTF-16-BE} — owned by
     *       {@code Utf16SpecialistEncodingDetector}; no longer emitted as
     *       main-model classes (same reasoning the v6 name
     *       "{@code -no-utf32}" captures for UTF-32).</li>
     *   <li><b>Added</b> {@code x-windows-949} — Korean MS949, strict
     *       superset of EUC-KR; trained as a separate class so the model
     *       can discriminate MS949-extension-byte content from pure
     *       EUC-KR.</li>
     * </ul>
     */
    static final Set<String> TODAY_SBCS_INCLUDE = Set.of(
            // CJK (multi-byte)
            "Big5-HKSCS", "EUC-JP", "EUC-KR", "x-windows-949",
            "GB18030", "Shift_JIS", "x-EUC-TW",
            // Unicode
            "UTF-8",
            // EBCDIC (international Latin only — other variants deferred to specialist)
            "IBM500",
            // DOS / OEM Latin (retained from v6)
            "IBM850", "IBM852",
            // Cyrillic
            "IBM855", "IBM866", "KOI8-R", "KOI8-U",
            "windows-1251", "x-mac-cyrillic",
            // Windows single-byte
            "windows-1250", "windows-1252", "windows-1253", "windows-1254",
            "windows-1255", "windows-1256", "windows-1257", "windows-1258",
            "windows-874",
            // ISO-8859 (only the ones v6 kept as distinct labels; 1/2/4/9 fold
            // into their windows-12XX supersets)
            "ISO-8859-3", "ISO-8859-16",
            // Mac
            "x-MacRoman");

    public static void main(String[] args) throws IOException {
        Path dataDir = null;
        Path outputPath = Paths.get("chardetect.bin");
        int numBuckets = DEFAULT_NUM_BUCKETS;
        int epochs = DEFAULT_EPOCHS;
        float lr = DEFAULT_LR;
        int maxSamplesPerClass = DEFAULT_MAX_SAMPLES;
        // --label-remap src1:dst1,src2:dst2 — merges multiple source labels into
        // one target label at training time (e.g. merge script variants into one class).
        Map<String, String> labelRemap = new HashMap<>();
        // CLI --exclude adds extra labels to drop *on top of* the include-list
        // policy (used for ablation experiments).  Cannot override the include
        // list — labels not in the policy are excluded regardless.
        Set<String> excludeLabels = new java.util.HashSet<>();

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
                case "--label-remap":
                    for (String pair : args[++i].split(",")) {
                        String[] kv = pair.split(":", 2);
                        if (kv.length != 2) {
                            System.err.println("Bad --label-remap entry (expected src:dst): " + pair);
                            System.exit(1);
                        }
                        labelRemap.put(kv[0].trim(), kv[1].trim());
                    }
                    break;
                case "--exclude":
                    for (String label : args[++i].split(",")) {
                        excludeLabels.add(label.trim());
                    }
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (dataDir == null) {
            System.err.println("Usage: TrainCharsetModel --data <dir> [options]");
            System.err.println("  --buckets N              feature hash buckets (default " + DEFAULT_NUM_BUCKETS + ")");
            System.err.println("  --epochs N               SGD epochs (default " + DEFAULT_EPOCHS + ")");
            System.err.println("  --lr F                   learning rate (default " + DEFAULT_LR + ")");
            System.err.println("  --max-samples-per-class N");
            System.err.println("  --label-remap src1:dst1,src2:dst2");
            System.err.println("                           merge source labels into a single target label");
            System.err.println("  --exclude cs1,cs2          drop these additionally on top of the hardcoded "
                    + "include list (" + TODAY_SBCS_INCLUDE.size() + " classes in TODAY_SBCS_INCLUDE)");
            System.exit(1);
        }

        // Discover charset files.  Include-list policy: only labels in
        // TODAY_SBCS_INCLUDE are admitted, regardless of what files exist in
        // dataDir (which may contain future-specialist corpora — Mac, DOS
        // OEM, EBCDIC nationals, etc.).  CLI --exclude can drop further
        // labels for ablation.
        List<Path> charsetFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .filter(p -> {
                    String cs = p.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
                    return TODAY_SBCS_INCLUDE.contains(cs) && !excludeLabels.contains(cs);
                })
                .sorted()
                .collect(Collectors.toList());

        System.out.println("TODAY_SBCS_INCLUDE (" + TODAY_SBCS_INCLUDE.size() + " classes): "
                + new java.util.TreeSet<>(TODAY_SBCS_INCLUDE));
        if (!excludeLabels.isEmpty()) {
            System.out.println("Additional CLI --exclude: " + excludeLabels);
        }
        // Report any include-list classes that had no matching file on disk.
        java.util.Set<String> foundLabels = charsetFiles.stream()
                .map(p -> p.getFileName().toString().replaceAll("\\.bin\\.gz$", ""))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        java.util.Set<String> missing = new java.util.TreeSet<>(TODAY_SBCS_INCLUDE);
        missing.removeAll(foundLabels);
        missing.removeAll(excludeLabels);
        if (!missing.isEmpty()) {
            System.err.println("WARNING: include-list classes with no data file in "
                    + dataDir + ": " + missing);
        }

        if (charsetFiles.isEmpty()) {
            System.err.println("No matching .bin.gz files found in: " + dataDir);
            System.exit(1);
        }

        // Build class label list (charset names stripped of .bin.gz, with remap applied).
        // Multiple source files that remap to the same target label are merged into one
        // class — their samples are pooled under the remapped label.
        List<String> labelList = new ArrayList<>();
        Map<String, List<Path>> labelToFiles = new java.util.LinkedHashMap<>();
        for (Path p : charsetFiles) {
            String raw = p.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            String label = labelRemap.getOrDefault(raw, raw);
            labelToFiles.computeIfAbsent(label, k -> new ArrayList<>()).add(p);
            if (!labelList.contains(label)) {
                labelList.add(label);
            }
        }
        String[] labels = labelList.toArray(new String[0]);
        int numClasses = labels.length;

        if (!labelRemap.isEmpty()) {
            System.out.println("Label remaps: " + labelRemap);
        }
        System.out.println("Classes (" + numClasses + "): " + Arrays.toString(labels));
        System.out.printf(java.util.Locale.ROOT,
                "Buckets: %d  epochs: %d  lr: %.4f  max-samples/class: %d%n",
                numBuckets, epochs, lr, maxSamplesPerClass);

        ByteNgramFeatureExtractor extractor = new ByteNgramFeatureExtractor(numBuckets);

        // Build class index map
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < numClasses; i++) {
            labelIndex.put(labels[i], i);
        }

        // Load samples (up to maxSamplesPerClass per class, pooling remapped sources)
        System.out.println("Loading training data ...");
        List<byte[]>[] samplesPerClass = new List[numClasses];
        long totalSamples = 0;
        for (int ci = 0; ci < numClasses; ci++) {
            List<Path> sources = labelToFiles.get(labels[ci]);
            List<byte[]> pooled = new ArrayList<>();
            int perFile = sources.size() > 1
                    ? maxSamplesPerClass / sources.size() : maxSamplesPerClass;
            for (Path src : sources) {
                pooled.addAll(loadSamples(src, perFile));
            }
            // Re-cap in case per-file rounding left excess
            if (pooled.size() > maxSamplesPerClass) {
                pooled = pooled.subList(0, maxSamplesPerClass);
            }
            samplesPerClass[ci] = pooled;
            totalSamples += pooled.size();
            System.out.printf(java.util.Locale.ROOT, "  %-30s  %,d samples  (from %d file(s))%n",
                    labels[ci], pooled.size(), sources.size());
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

        // Reusable sparse-extraction buffers (avoids per-sample allocation)
        int[] denseScratch = new int[numBuckets];
        int[] touched = new int[numBuckets]; // worst-case size

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(index);
            long strictCorrect = 0;
            float lossSum = 0f;
            int count = 0;

            for (int[] pair : index) {
                int trueClass = pair[0];
                byte[] sample = samplesPerClass[trueClass].get(pair[1]);

                // Sparse extraction: O(probeLength), not O(numBuckets)
                int nActive = extractor.extractSparseInto(sample, denseScratch, touched);

                // Per-bucket contribution clip matching LinearModel.predictLogits at inference.
                // Prevents any single colliding bucket from dominating the logit.
                float clip = 1.5f * (float) Math.sqrt(nActive);

                // Forward pass: clipped contributions, matching inference behaviour.
                float[] logits = new float[numClasses];
                for (int c = 0; c < numClasses; c++) {
                    float dot = biases[c];
                    for (int t = 0; t < nActive; t++) {
                        int b = touched[t];
                        float contrib = weights[c][b] * denseScratch[b];
                        dot += Math.max(-clip, Math.min(clip, contrib));
                    }
                    logits[c] = dot;
                }
                float[] probs = LinearModel.softmax(logits.clone());

                // Cross-entropy loss on the true class
                lossSum -= (float) Math.log(Math.max(probs[trueClass], 1e-12f));

                if (argmax(probs) == trueClass) {
                    strictCorrect++;
                }

                // Backward pass: gradient = probs - one_hot(trueClass)
                float[] grad = probs.clone();
                grad[trueClass] -= 1f;

                // Sparse SGD update with L2 regularization on both weights and biases.
                // Straight-through estimator for the clip: pass the full gradient when
                // the contribution was inside the clip window; only L2 decay when clipped.
                for (int c = 0; c < numClasses; c++) {
                    float g = grad[c];
                    biases[c] -= lr * (g + lambda * biases[c]);
                    for (int t = 0; t < nActive; t++) {
                        int b = touched[t];
                        float contrib = weights[c][b] * denseScratch[b];
                        if (contrib > -clip && contrib < clip) {
                            weights[c][b] -= lr * (g * denseScratch[b]
                                    + lambda * weights[c][b]);
                        } else {
                            weights[c][b] -= lr * lambda * weights[c][b];
                        }
                    }
                }
                count++;

                // Clear only the active entries (O(nActive), not O(numBuckets))
                for (int t = 0; t < nActive; t++) {
                    denseScratch[touched[t]] = 0;
                }
            }

            System.out.printf(java.util.Locale.ROOT,
                    "Epoch %d/%d  loss=%.4f  strict-acc=%.2f%%%n",
                    epoch + 1, epochs, lossSum / count,
                    100.0 * strictCorrect / count);
        }

        // Bias summary — helps catch runaway bias drift between training runs
        float biasMin = Float.MAX_VALUE, biasMax = -Float.MAX_VALUE, biasSum = 0f;
        for (float b : biases) {
            biasMin = Math.min(biasMin, b);
            biasMax = Math.max(biasMax, b);
            biasSum += b;
        }
        float biasMean = biasSum / numClasses;
        float biasVar = 0f;
        for (float b : biases) {
            biasVar += (b - biasMean) * (b - biasMean);
        }
        float biasStd = (float) Math.sqrt(biasVar / numClasses);
        System.out.printf(java.util.Locale.ROOT,
                "%nBias summary: min=%.3f  max=%.3f  mean=%.3f  std=%.3f  spread=%.3f%n",
                biasMin, biasMax, biasMean, biasStd, biasMax - biasMin);
        // Per-class bias listing (sorted by value)
        Integer[] biasOrder = new Integer[numClasses];
        for (int i = 0; i < numClasses; i++) biasOrder[i] = i;
        Arrays.sort(biasOrder, (a, b) -> Float.compare(biases[b], biases[a]));
        for (int idx : biasOrder) {
            System.out.printf(java.util.Locale.ROOT, "  %-20s  bias=%9.3f%n",
                    labels[idx], biases[idx]);
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
