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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Ablation evaluation for the junk detector.
 *
 * <p>For each script's dev set, scores clean sentences alongside three corruption
 * modes — random-byte injection, codepoint-reversal, and byte-shuffle — at several
 * injection rates and string lengths.  Computes per-cell Cohen's d (discrimination
 * power) and TPR/FPR at a fixed z-score threshold.
 *
 * <p>Output: two TSV files.
 * <ul>
 *   <li><b>detail.tsv</b> — one row per (script, distortion, rate, length):
 *       {@code script, distortion, param, length, n_clean, n_corrupt,
 *       mean_clean_z, mean_corrupt_z, cohens_d, fpr, tpr}
 *   <li><b>summary.tsv</b> — macro-averaged Cohen's d and FPR/TPR per
 *       (distortion, rate, length) across all scripts.
 * </ul>
 *
 * <p>Cohen's d = (mean_clean_z − mean_corrupt_z) / pooled_std.
 * Higher d = better discrimination. FPR = fraction of clean text falsely flagged;
 * TPR = fraction of corrupted text correctly flagged. Both use threshold = −2.0.
 *
 * <p>To compare two model versions: run eval before and after, then diff the
 * summary TSVs. The "macro_d" column in summary.tsv is the single headline metric.
 *
 * <p>Usage:
 * <pre>
 *   java EvalJunkDetector \
 *     --model      /path/to/junkdetect.bin   (default: classpath)
 *     --data-dir   ~/datasets/madlad/junkdetect
 *     --output-dir /path/to/results          (default: data-dir/eval)
 *     --split      dev|test                  (default: dev — use test only for final reporting)
 *     --samples    200
 *     --seed       42
 *     --lengths    15,30,50,100,200
 *     --rates      0.01,0.05,0.10,0.25,0.50,0.90
 *     --threshold  -2.0
 * </pre>
 *
 * <p><b>Which split to use:</b> Use {@code --split dev} during iterative development
 * (dev data is seen by the calibration step, so numbers are slightly optimistic for
 * calibration quality, but still valid for relative comparisons between model versions).
 * Use {@code --split test} only when reporting final numbers — the test split is
 * completely held out and was never used to make any model or threshold decision.
 */
public class EvalJunkDetector {

    public static void main(String[] args) throws Exception {
        // Defaults
        Path modelPath = null;
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "datasets", "madlad", "junkdetect");
        Path outputDir = null;
        String split = "dev"; // dev during development; test for final reporting
        int samplesPerCell = 200;
        long seed = 42L;
        int[] lengths = {15, 30, 50, 100, 200};
        double[] rates = {0.01, 0.05, 0.10, 0.25, 0.50, 0.90};
        float threshold = -2.0f;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--data-dir":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--split":
                    split = args[++i];
                    if (!split.equals("dev") && !split.equals("test")) {
                        System.err.println("--split must be 'dev' or 'test'");
                        System.exit(1);
                    }
                    break;
                case "--samples":
                    samplesPerCell = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--lengths":
                    lengths = Arrays.stream(args[++i].split(","))
                            .mapToInt(Integer::parseInt).toArray();
                    break;
                case "--rates":
                    rates = Arrays.stream(args[++i].split(","))
                            .mapToDouble(Double::parseDouble).toArray();
                    break;
                case "--threshold":
                    threshold = Float.parseFloat(args[++i]);
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (outputDir == null) {
            outputDir = dataDir.resolve("eval");
        }
        Files.createDirectories(outputDir);

        JunkDetector detector = modelPath != null
                ? JunkDetector.loadFromPath(modelPath)
                : JunkDetector.loadFromClasspath();

        System.err.println("=== EvalJunkDetector ===");
        System.err.println("  data-dir:   " + dataDir);
        System.err.println("  output-dir: " + outputDir);
        System.err.println("  split:      " + split
                + (split.equals("test") ? "  [FINAL REPORTING MODE]" : ""));
        System.err.println("  scripts in model: " + detector.knownScripts().size());
        System.err.println("  threshold: " + threshold);

        String suffix = "." + split + ".gz";
        List<Path> devFiles;
        try (var stream = Files.list(dataDir)) {
            devFiles = stream
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (devFiles.isEmpty()) {
            System.err.println("ERROR: no *" + suffix + " files found in " + dataDir);
            System.exit(1);
        }

        Path detailPath = outputDir.resolve("detail.tsv");
        Path summaryPath = outputDir.resolve("summary.tsv");

        // Accumulate all rows for summary aggregation
        List<Row> allRows = new ArrayList<>();

        try (PrintWriter detail = new PrintWriter(
                Files.newBufferedWriter(detailPath, StandardCharsets.UTF_8))) {

            detail.println("script\tdistortion\tparam\tlength"
                    + "\tn_clean\tn_corrupt"
                    + "\tmean_clean_z\tmean_corrupt_z"
                    + "\tcohens_d\tfpr\ttpr");

            for (Path devFile : devFiles) {
                String filename = devFile.getFileName().toString();
                String script = filename
                        .substring(0, filename.length() - suffix.length())
                        .toUpperCase();

                System.err.printf("%n--- %s ---%n", script);

                List<String> sentences = loadSentences(devFile, samplesPerCell * 20);
                if (sentences.size() < 10) {
                    System.err.printf("  Skipping — only %d sentences%n", sentences.size());
                    continue;
                }

                Random rng = new Random(seed);

                // Score clean baseline once per (script, length)
                // Reuse the same clean scores for all distortion comparisons at that length
                for (int len : lengths) {
                    List<Float> cleanZ = scoreClean(detector, sentences, len,
                            samplesPerCell, new Random(seed));

                    // --- injection ---
                    for (double rate : rates) {
                        List<Float> corruptZ = scoreWithInjection(detector, sentences, len,
                                rate, samplesPerCell, new Random(seed + 1));
                        Row row = new Row(script, "inject",
                                String.format("%.2f", rate), len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    // --- codepoint reversal ---
                    {
                        List<Float> corruptZ = scoreReversed(detector, sentences, len,
                                samplesPerCell, new Random(seed + 2));
                        Row row = new Row(script, "char-reverse", "-", len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    // --- byte shuffle ---
                    {
                        List<Float> corruptZ = scoreShuffled(detector, sentences, len,
                                samplesPerCell, new Random(seed + 3));
                        Row row = new Row(script, "byte-shuffle", "-", len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    // --- wrong-codec: re-read UTF-8 bytes as ISO-8859-1 then re-encode ---
                    {
                        List<Float> corruptZ = scoreWrongCodec(detector, sentences, len,
                                samplesPerCell, new Random(seed + 4));
                        Row row = new Row(script, "wrong-codec", "latin1-as-utf8", len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    // --- byte-swap: swap each adjacent pair of bytes (endianness flip) ---
                    {
                        List<Float> corruptZ = scoreByteSwapped(detector, sentences, len,
                                samplesPerCell, new Random(seed + 5));
                        Row row = new Row(script, "byte-swap", "-", len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    detail.flush();
                    rng = new Random(seed); // reset between lengths for reproducibility
                }
            }
        }

        writeSummary(summaryPath, allRows, lengths, rates, threshold);

        System.err.println("\nWrote " + detailPath);
        System.err.println("Wrote " + summaryPath);
        System.err.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Summary aggregation
    // -----------------------------------------------------------------------

    private static void writeSummary(Path summaryPath, List<Row> rows,
                                     int[] lengths, double[] rates, float threshold)
            throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8))) {

            out.println("distortion\tparam\tlength\tn_scripts"
                    + "\tmacro_cohens_d\tmacro_fpr\tmacro_tpr");

            // For each unique (distortion, param, length), average across scripts
            // Build groups: inject@rate, char-reverse, byte-shuffle
            List<String[]> conditions = new ArrayList<>();
            for (double rate : rates) {
                conditions.add(new String[]{"inject", String.format("%.2f", rate)});
            }
            conditions.add(new String[]{"char-reverse", "-"});
            conditions.add(new String[]{"byte-shuffle", "-"});
            conditions.add(new String[]{"wrong-codec", "latin1-as-utf8"});
            conditions.add(new String[]{"byte-swap", "-"});

            for (String[] cond : conditions) {
                String distortion = cond[0];
                String param = cond[1];
                for (int len : lengths) {
                    List<Row> matching = rows.stream()
                            .filter(r -> r.distortion.equals(distortion)
                                    && r.param.equals(param)
                                    && r.length == len)
                            .collect(Collectors.toList());
                    if (matching.isEmpty()) {
                        continue;
                    }
                    double macroCohensD = matching.stream()
                            .filter(r -> !Double.isNaN(r.cohensD))
                            .mapToDouble(r -> r.cohensD)
                            .average().orElse(Double.NaN);
                    double macroFpr = matching.stream()
                            .mapToDouble(r -> r.fpr)
                            .average().orElse(Double.NaN);
                    double macroTpr = matching.stream()
                            .mapToDouble(r -> r.tpr)
                            .average().orElse(Double.NaN);

                    out.printf("%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f%n",
                            distortion, param, len, matching.size(),
                            macroCohensD, macroFpr, macroTpr);
                }
            }

            // Overall headline: macro-average Cohen's d across everything
            double overallD = rows.stream()
                    .filter(r -> !Double.isNaN(r.cohensD))
                    .mapToDouble(r -> r.cohensD)
                    .average().orElse(Double.NaN);
            double overallFpr = rows.stream()
                    .mapToDouble(r -> r.fpr)
                    .average().orElse(Double.NaN);
            double overallTpr = rows.stream()
                    .mapToDouble(r -> r.tpr)
                    .average().orElse(Double.NaN);
            out.println();
            out.printf("# OVERALL macro_cohens_d=%.3f  macro_fpr=%.3f  macro_tpr=%.3f%n",
                    overallD, overallFpr, overallTpr);

            System.err.printf("%nOVERALL: macro_cohens_d=%.3f  macro_fpr=%.3f  macro_tpr=%.3f%n",
                    overallD, overallFpr, overallTpr);
        }
    }

    // -----------------------------------------------------------------------
    // Row (one evaluation cell)
    // -----------------------------------------------------------------------

    private static final class Row {
        final String script;
        final String distortion;
        final String param;
        final int length;
        final int nClean;
        final int nCorrupt;
        final double meanCleanZ;
        final double meanCorruptZ;
        final double cohensD;
        final double fpr;
        final double tpr;

        Row(String script, String distortion, String param, int length,
            List<Float> cleanZ, List<Float> corruptZ, float threshold) {
            this.script = script;
            this.distortion = distortion;
            this.param = param;
            this.length = length;
            this.nClean = cleanZ.size();
            this.nCorrupt = corruptZ.size();
            this.meanCleanZ = mean(cleanZ);
            this.meanCorruptZ = mean(corruptZ);
            this.cohensD = computeCohensD(cleanZ, corruptZ);
            this.fpr = fractionBelow(cleanZ, threshold);
            this.tpr = fractionBelow(corruptZ, threshold);
        }

        String toTsv() {
            return String.format("%s\t%s\t%s\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f",
                    script, distortion, param, length,
                    nClean, nCorrupt,
                    meanCleanZ, meanCorruptZ,
                    cohensD, fpr, tpr);
        }
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    /**
     * Cohen's d = (mean_clean − mean_corrupt) / pooled_std.
     * Positive = clean scores higher than corrupt (desirable).
     * Higher absolute value = better discrimination.
     */
    private static double computeCohensD(List<Float> clean, List<Float> corrupt) {
        if (clean.isEmpty() || corrupt.isEmpty()) {
            return Double.NaN;
        }
        double mc = mean(clean);
        double mj = mean(corrupt);
        double vc = variance(clean, mc);
        double vj = variance(corrupt, mj);
        double pooledStd = Math.sqrt((vc + vj) / 2.0);
        if (pooledStd < 1e-9) {
            return Double.NaN;
        }
        return (mc - mj) / pooledStd;
    }

    private static double mean(List<Float> xs) {
        return xs.stream().mapToDouble(Float::floatValue).average().orElse(0);
    }

    private static double variance(List<Float> xs, double mu) {
        return xs.stream().mapToDouble(x -> (x - mu) * (x - mu)).average().orElse(0);
    }

    private static double fractionBelow(List<Float> zs, float threshold) {
        if (zs.isEmpty()) {
            return Double.NaN;
        }
        long count = zs.stream().filter(z -> z < threshold).count();
        return (double) count / zs.size();
    }

    // -----------------------------------------------------------------------
    // Scoring helpers
    // -----------------------------------------------------------------------

    private static List<Float> scoreClean(JunkDetector detector, List<String> sentences,
                                          int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            TextQualityScore score = detector.score(s);
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    private static List<Float> scoreWithInjection(JunkDetector detector,
                                                   List<String> sentences, int targetLen,
                                                   double rate, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            injectRandomBytes(bytes, rate, rng);
            TextQualityScore score = detector.score(new String(bytes, StandardCharsets.ISO_8859_1));
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    private static List<Float> scoreReversed(JunkDetector detector, List<String> sentences,
                                              int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = reverseCodepoints(pickSubstring(sentences, targetLen, rng));
            TextQualityScore score = detector.score(s);
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    private static List<Float> scoreShuffled(JunkDetector detector, List<String> sentences,
                                              int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            shuffleBytes(bytes, rng);
            TextQualityScore score = detector.score(new String(bytes, StandardCharsets.ISO_8859_1));
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    private static List<Float> scoreWrongCodec(JunkDetector detector, List<String> sentences,
                                                int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            byte[] garbled = wrongCodecBytes(s.getBytes(StandardCharsets.UTF_8));
            TextQualityScore score = detector.score(new String(garbled, StandardCharsets.UTF_8));
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    private static List<Float> scoreByteSwapped(JunkDetector detector, List<String> sentences,
                                                 int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            byte[] swapped = swapByteOrder(s.getBytes(StandardCharsets.UTF_8));
            TextQualityScore score = detector.score(new String(swapped, StandardCharsets.ISO_8859_1));
            if (!score.isUnknown()) {
                results.add(score.getZScore());
            }
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Distortion primitives
    // -----------------------------------------------------------------------

    /**
     * Injects control characters (0x01–0x09, i.e. below newline/0x0A, excluding null)
     * at the given rate.  These bytes never appear in clean natural-language UTF-8 text
     * and simulate binary data leaking into a text stream.  0x00 is excluded because
     * null bytes cause problems in many text-processing pipelines.
     */
    static void injectRandomBytes(byte[] bytes, double rate, Random rng) {
        for (int i = 0; i < bytes.length; i++) {
            if (rng.nextDouble() < rate) {
                // 0x01..0x09 inclusive (9 values): SOH STX ETX EOT ENQ ACK BEL BS HT
                bytes[i] = (byte) (0x01 + rng.nextInt(9));
            }
        }
    }

    /**
     * Wrong-codec distortion: the UTF-8 bytes of the sentence are re-interpreted
     * as ISO-8859-1 (Latin-1) and then re-encoded as UTF-8.  This is the classic
     * "saved as UTF-8, displayed as Latin-1" mojibake: every byte in 0x80–0xFF
     * becomes a two-byte UTF-8 sequence, doubling the byte length of non-ASCII runs
     * and producing bogus accented-Latin bigrams.
     */
    static byte[] wrongCodecBytes(byte[] utf8) {
        String misread = new String(utf8, StandardCharsets.ISO_8859_1);
        return misread.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Byte-swap distortion: swaps each adjacent pair of bytes — (0,1), (2,3), etc.
     * If the array has an odd length the last byte is left unchanged.
     * Simulates reading a 2-byte encoding (UTF-16, UCS-2, CP932 two-byte sequences)
     * with the wrong byte order.
     */
    static byte[] swapByteOrder(byte[] bytes) {
        byte[] out = bytes.clone();
        for (int i = 0; i + 1 < out.length; i += 2) {
            byte tmp = out[i];
            out[i] = out[i + 1];
            out[i + 1] = tmp;
        }
        return out;
    }

    static void shuffleBytes(byte[] bytes, Random rng) {
        for (int i = bytes.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            byte tmp = bytes[i];
            bytes[i] = bytes[j];
            bytes[j] = tmp;
        }
    }

    static String reverseCodepoints(String s) {
        int[] codepoints = s.codePoints().toArray();
        for (int lo = 0, hi = codepoints.length - 1; lo < hi; lo++, hi--) {
            int tmp = codepoints[lo];
            codepoints[lo] = codepoints[hi];
            codepoints[hi] = tmp;
        }
        return new String(codepoints, 0, codepoints.length);
    }

    // -----------------------------------------------------------------------
    // Sentence sampling
    // -----------------------------------------------------------------------

    private static String pickSubstring(List<String> sentences, int targetLen, Random rng) {
        String s = sentences.get(rng.nextInt(sentences.size()));
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= targetLen) {
            return s;
        }
        // Pick a random window of targetLen bytes, aligned to a codepoint boundary
        int start = rng.nextInt(bytes.length - targetLen);
        while (start > 0 && (bytes[start] & 0xC0) == 0x80) {
            start--;
        }
        int end = Math.min(start + targetLen, bytes.length);
        while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
            end++;
        }
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    private static List<String> loadSentences(Path devGz, int maxSentences) throws IOException {
        List<String> result = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(devGz)),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null && result.size() < maxSentences) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()
                        && trimmed.getBytes(StandardCharsets.UTF_8).length >= 15) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
