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
package org.apache.tika.ml.junkdetect.tools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Trains the junk detector model from per-script corpus files produced by
 * {@link BuildJunkTrainingData}.
 *
 * <p>For each script group (identified by a {@code {script}.train.gz} file):
 * <ol>
 *   <li>Accumulates byte-bigram counts from the training sentences.</li>
 *   <li>Applies add-1 (Laplace) smoothing per row, converts to natural
 *       log-probabilities.</li>
 *   <li>Computes calibration statistics (mean and stddev of per-sentence mean
 *       bigram log-prob) from the dev split ({@code {script}.dev.gz}).</li>
 * </ol>
 *
 * <p>Output: a single gzipped binary model file ({@code junkdetect.bin}) in the
 * following format:
 * <pre>
 *   [8 bytes]  magic "JUNKDET1"
 *   [1 byte]   version = 1
 *   [4 bytes]  num_scripts (big-endian int)
 *   for each script (sorted by name):
 *     [2 bytes]  name length (big-endian ushort)
 *     [N bytes]  script name (UTF-8)
 *     [4 bytes]  mu   — mean of mean_bigram_logprob over dev sentences (float)
 *     [4 bytes]  sigma — stddev (float)
 *     [65536 * 4 bytes]  float32 log-prob table, row a*256+b = log P(b|a)
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   java TrainJunkModel \
 *     --data-dir   ~/datasets/madlad/junkdetect \
 *     --output     ~/datasets/madlad/junkdetect/junkdetect.bin
 * </pre>
 */
public class TrainJunkModel {

    static final String MAGIC = "JUNKDET1";
    static final byte VERSION = 1;

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "datasets", "madlad", "junkdetect");
        Path output = dataDir.resolve("junkdetect.bin");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data-dir":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    output = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        System.out.println("=== TrainJunkModel ===");
        System.out.println("  data-dir: " + dataDir);
        System.out.println("  output:   " + output);

        if (!Files.isDirectory(dataDir)) {
            System.err.println("ERROR: data-dir not found: " + dataDir);
            System.exit(1);
        }

        // Collect all script names by finding *.train.gz files
        TreeMap<String, float[]> tables = new TreeMap<>();
        TreeMap<String, float[]> calibrations = new TreeMap<>();

        try (var stream = Files.list(dataDir)) {
            List<Path> trainFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(".train.gz"))
                    .sorted()
                    .toList();

            if (trainFiles.isEmpty()) {
                System.err.println("ERROR: no *.train.gz files found in " + dataDir);
                System.exit(1);
            }

            for (Path trainFile : trainFiles) {
                String filename = trainFile.getFileName().toString();
                String script = filename.substring(0, filename.length() - ".train.gz".length())
                        .toUpperCase();

                Path devFile = trainFile.getParent().resolve(
                        filename.replace(".train.gz", ".dev.gz"));

                System.out.printf("%n--- %s ---%n", script);

                System.out.print("  Training bigram table... ");
                long t0 = System.currentTimeMillis();
                float[] table = trainBigramTable(trainFile);
                System.out.printf("done (%dms)%n", System.currentTimeMillis() - t0);

                float[] cal = new float[]{0f, 1f};
                if (Files.exists(devFile)) {
                    System.out.print("  Calibrating on dev set... ");
                    t0 = System.currentTimeMillis();
                    cal = computeCalibration(devFile, table);
                    System.out.printf("done — mu=%.4f sigma=%.4f (%dms)%n",
                            cal[0], cal[1], System.currentTimeMillis() - t0);
                } else {
                    System.out.println("  WARNING: no dev file found, using uncalibrated defaults");
                }

                tables.put(script, table);
                calibrations.put(script, cal);
            }
        }

        System.out.printf("%nWriting model (%d scripts) → %s%n", tables.size(), output);
        saveModel(tables, calibrations, output);
        System.out.printf("Model size: %,d bytes (%.1f MB)%n",
                Files.size(output), Files.size(output) / 1_000_000.0);
        System.out.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    /**
     * Trains a 256×256 byte-bigram log-probability table from a gzipped
     * sentence file (one UTF-8 sentence per line).
     *
     * <p>All 256×256 consecutive byte-pair counts are accumulated, then
     * add-1 (Laplace) smoothing is applied per row before converting to
     * natural log-probabilities: {@code log P(b|a) = log((C[a][b]+1) / sum_b(C[a][b]+1))}.
     *
     * @return float[65536] table where index {@code a*256+b} = log P(b|a)
     */
    static float[] trainBigramTable(Path trainGz) throws IOException {
        long[] counts = new long[65536];
        long totalBigrams = 0;
        long sentences = 0;

        try (BufferedReader r = openGzipped(trainGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                for (int i = 0; i + 1 < bytes.length; i++) {
                    counts[((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)]++;
                    totalBigrams++;
                }
                sentences++;
            }
        }

        System.out.printf("    %,d sentences, %,d bigrams%n", sentences, totalBigrams);

        // Add-1 smoothing per row, then log-prob
        float[] table = new float[65536];
        for (int a = 0; a < 256; a++) {
            long rowTotal = 256; // add 1 for each of the 256 possible next bytes
            for (int b = 0; b < 256; b++) {
                rowTotal += counts[a * 256 + b];
            }
            for (int b = 0; b < 256; b++) {
                table[a * 256 + b] = (float) Math.log((counts[a * 256 + b] + 1.0) / rowTotal);
            }
        }
        return table;
    }

    /**
     * Computes calibration statistics for a script by scoring each sentence
     * in the dev set with the given bigram table.
     *
     * <p>For each sentence, the per-sentence score is the mean log-probability
     * of its byte bigrams. The mean (mu) and stddev (sigma) of those scores
     * across all dev sentences are returned. At inference, z-score =
     * (score - mu) / sigma.
     *
     * @return float[2] = {mu, sigma}
     */
    static float[] computeCalibration(Path devGz, float[] table) throws IOException {
        List<Double> scores = new ArrayList<>();

        try (BufferedReader r = openGzipped(devGz)) {
            String line;
            while ((line = r.readLine()) != null) {
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                if (bytes.length < 2) {
                    continue;
                }
                double sum = 0;
                for (int i = 0; i + 1 < bytes.length; i++) {
                    sum += table[((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF)];
                }
                scores.add(sum / (bytes.length - 1));
            }
        }

        System.out.printf("    %,d dev sentences%n", scores.size());

        if (scores.isEmpty()) {
            return new float[]{0f, 1f};
        }

        double mu = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = scores.stream()
                .mapToDouble(s -> (s - mu) * (s - mu))
                .average().orElse(1.0);
        double sigma = Math.sqrt(variance);
        if (sigma < 1e-9) {
            sigma = 1.0;
        }
        return new float[]{(float) mu, (float) sigma};
    }

    // -----------------------------------------------------------------------
    // Model serialisation
    // -----------------------------------------------------------------------

    /**
     * Writes the trained model to a gzipped binary file.
     *
     * <p>Format: {@code [magic:8][version:1][num_scripts:4]
     * ([name_len:2][name:N][mu:4][sigma:4][table:65536*4])*}
     * All multi-byte integers are big-endian.  Floats are IEEE 754 big-endian.
     */
    static void saveModel(TreeMap<String, float[]> tables,
                          TreeMap<String, float[]> calibrations,
                          Path output) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(
                new GZIPOutputStream(Files.newOutputStream(output)))) {

            // Magic + version + count
            dos.write(MAGIC.getBytes(StandardCharsets.UTF_8));
            dos.writeByte(VERSION);
            dos.writeInt(tables.size());

            for (var entry : tables.entrySet()) {
                String script = entry.getKey();
                float[] table = entry.getValue();
                float[] cal = calibrations.getOrDefault(script, new float[]{0f, 1f});

                byte[] nameBytes = script.getBytes(StandardCharsets.UTF_8);
                dos.writeShort(nameBytes.length);
                dos.write(nameBytes);

                dos.writeFloat(cal[0]); // mu
                dos.writeFloat(cal[1]); // sigma

                // Write 65536 float32 values in big-endian
                ByteBuffer buf = ByteBuffer.allocate(65536 * 4).order(ByteOrder.BIG_ENDIAN);
                for (float v : table) {
                    buf.putFloat(v);
                }
                dos.write(buf.array());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    static BufferedReader openGzipped(Path path) throws IOException {
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(path)),
                        StandardCharsets.UTF_8));
    }

    private static void printUsage() {
        System.err.println("Usage: TrainJunkModel [options]");
        System.err.println("  --data-dir <path>  Directory with {script}.train.gz / .dev.gz files");
        System.err.println("                     (default: ~/datasets/madlad/junkdetect)");
        System.err.println("  --output   <path>  Output model file");
        System.err.println("                     (default: {data-dir}/junkdetect.bin)");
    }

}
