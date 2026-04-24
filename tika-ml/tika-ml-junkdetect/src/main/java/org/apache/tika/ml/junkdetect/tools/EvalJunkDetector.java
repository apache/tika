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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.quality.TextQualityComparison;
import org.apache.tika.quality.TextQualityScore;

/**
 * Ablation evaluation for the junk detector.
 *
 * <p>For each script's dev set, scores clean sentences alongside several corruption
 * modes at various injection rates and string lengths.  Computes per-cell Cohen's d
 * (discrimination power) and TPR/FPR at a fixed z-score threshold.
 *
 * <p>Output files in {@code --output-dir}:
 * <ul>
 *   <li><b>detail.tsv</b> — one row per (script, distortion, rate, length):
 *       {@code script, distortion, param, length, n_clean, n_corrupt,
 *       mean_clean_z, mean_corrupt_z, cohens_d, fpr, tpr}
 *   <li><b>summary.tsv</b> — macro-averaged Cohen's d and FPR/TPR per
 *       (distortion, rate, length) across all scripts.
 *   <li><b>compare.tsv</b> — pairwise codec-comparison accuracy using the
 *       {@link JunkDetector#compare} API, stratified by string length.
 *       This is the primary metric for the charset-arbitration use case;
 *       larger mean delta = better discrimination at that length.
 * </ul>
 *
 * <p><b>Why char-remap is not in summary.tsv:</b> The character-level wrong-codec
 * substitution (e.g. CP1252→CP1255, replacing umlauts with Hebrew letters) is added
 * to training at a 5% rate.  At that rate it is too subtle to detect via the absolute
 * {@link JunkDetector#score} API — z-score distributions barely separate (Cohen's d ≈ 0).
 * The distortion trains the LR to distinguish subtly-wrong from correct decodings, which
 * only manifests as larger pairwise deltas in {@link JunkDetector#compare}.  Measuring it
 * via summary.tsv would produce misleading d≈0 "failure" rows; see compare.tsv instead.
 *
 * <p>Cohen's d = (mean_clean_z − mean_corrupt_z) / pooled_std.
 * Higher d = better discrimination. FPR = fraction of clean text falsely flagged;
 * TPR = fraction of corrupted text correctly flagged. Both use threshold = −2.0.
 *
 * <p>To compare two model versions: run eval before and after, then diff the
 * summary and compare TSVs. The "macro_d" column in summary.tsv and the
 * "mean_delta" columns in compare.tsv are the headline metrics.
 *
 * <p>Usage:
 * <pre>
 *   java EvalJunkDetector \
 *     --model          /path/to/junkdetect.bin   (default: classpath)
 *     --data-dir       ~/datasets/madlad/junkdetect
 *     --output-dir     /path/to/results          (default: data-dir/eval)
 *     --split          dev|test                  (default: dev)
 *     --samples        200
 *     --compare-n      200                       (qualifying pairs per codec pair per length)
 *     --seed           42
 *     --lengths        5,9,15,30,50,100,200
 *     --compare-lengths 5,9,15,30,50
 *     --rates          0.01,0.05,0.10,0.25,0.50,0.90
 *     --threshold      -2.0
 * </pre>
 */
public class EvalJunkDetector {

    public static void main(String[] args) throws Exception {
        // Defaults
        Path modelPath = null;
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "datasets", "madlad", "junkdetect");
        Path outputDir = null;
        String split = "dev";
        int samplesPerCell = 200;
        int compareN = 200;
        long seed = 42L;
        int[] lengths = {5, 9, 15, 30, 50, 100, 200};
        int[] compareLengths = {5, 9, 15, 30, 50};
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
                case "--compare-n":
                    compareN = Integer.parseInt(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--lengths":
                    lengths = Arrays.stream(args[++i].split(","))
                            .mapToInt(Integer::parseInt).toArray();
                    break;
                case "--compare-lengths":
                    compareLengths = Arrays.stream(args[++i].split(","))
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
        System.err.println("  data-dir:       " + dataDir);
        System.err.println("  output-dir:     " + outputDir);
        System.err.println("  split:          " + split
                + (split.equals("test") ? "  [FINAL REPORTING MODE]" : ""));
        System.err.println("  scripts in model: " + detector.knownScripts().size());
        System.err.println("  threshold:      " + threshold);

        // Build wrong-codec remap tables for char-remap distortion
        List<Map<Character, Character>> remapTables = new ArrayList<>();
        for (String[] pair : TrainJunkModel.WRONG_CODEC_PAIRS) {
            Map<Character, Character> table = TrainJunkModel.buildRemapTable(pair[0], pair[1]);
            if (!table.isEmpty()) remapTables.add(table);
        }
        System.err.println("  remap tables:   " + remapTables.size());

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
        Path comparePath = outputDir.resolve("compare.tsv");

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

                    // --- byte-swap ---
                    {
                        List<Float> corruptZ = scoreByteSwapped(detector, sentences, len,
                                samplesPerCell, new Random(seed + 5));
                        Row row = new Row(script, "byte-swap", "-", len,
                                cleanZ, corruptZ, threshold);
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }

                    // char-remap distortion is evaluated only via compare.tsv (pairwise delta),
                    // not via absolute score() — see class Javadoc for rationale.

                    detail.flush();
                }
            }
        }

        writeSummary(summaryPath, allRows, lengths, rates, threshold);
        writeCompareEval(detector, dataDir, suffix, comparePath, compareN, compareLengths, seed);

        System.err.println("\nWrote " + detailPath);
        System.err.println("Wrote " + summaryPath);
        System.err.println("Wrote " + comparePath);
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

            List<String[]> conditions = new ArrayList<>();
            for (double rate : rates) {
                conditions.add(new String[]{"inject", String.format("%.2f", rate)});
            }
            conditions.add(new String[]{"char-reverse", "-"});
            conditions.add(new String[]{"byte-shuffle", "-"});
            conditions.add(new String[]{"wrong-codec", "latin1-as-utf8"});
            conditions.add(new String[]{"byte-swap", "-"});
            // char-remap is intentionally excluded from summary: at 5% rate the character-level
            // wrong-codec substitution is too subtle to detect via absolute score() — both the
            // clean and corrupted strings score similarly.  The right metric for char-remap is
            // compare.tsv (pairwise delta), where it shows up strongly.  Including it here would
            // make d≈0 rows that look like failures but are actually expected.

            for (String[] cond : conditions) {
                String distortion = cond[0];
                String param = cond[1];
                for (int len : lengths) {
                    List<Row> matching = rows.stream()
                            .filter(r -> r.distortion.equals(distortion)
                                    && r.param.equals(param)
                                    && r.length == len)
                            .collect(Collectors.toList());
                    if (matching.isEmpty()) continue;

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
    // Compare eval — pairwise codec arbitration, stratified by string length
    // -----------------------------------------------------------------------

    /**
     * For each entry in {@link TrainJunkModel#WRONG_CODEC_PAIRS}, encodes sentences
     * from the appropriate script's dev file as the source charset, then calls
     * {@link JunkDetector#compare} with the correct decoding (A) vs the wrong
     * decoding (B).  Reports accuracy (how often A wins) and mean/median delta
     * at each requested string length.
     *
     * <p>Mean delta is the headline metric: larger delta means the model more
     * confidently picks the correct decoding.  At short lengths (5–9 bytes)
     * delta is expected to be small; at 50 bytes it should be decisive.
     */
    private static void writeCompareEval(JunkDetector detector,
                                          Path dataDir, String suffix,
                                          Path comparePath,
                                          int nPerCell, int[] lengths,
                                          long seed) throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(comparePath, StandardCharsets.UTF_8))) {

            out.println("source_codec\twrong_codec\tlength"
                    + "\tn_tested\taccuracy\tmean_delta\tmedian_delta\tn_no_diff");

            System.err.printf("%n--- compare() eval ---%n");

            for (String[] pair : TrainJunkModel.WRONG_CODEC_PAIRS) {
                String sourceCodec = pair[0];
                String wrongCodec  = pair[1];

                Charset srcCharset, wrongCharset;
                try {
                    srcCharset  = Charset.forName(sourceCodec);
                    wrongCharset = Charset.forName(wrongCodec);
                } catch (UnsupportedCharsetException e) {
                    System.err.printf("  [%s→%s] charset unavailable, skipping%n",
                            sourceCodec, wrongCodec);
                    continue;
                }

                String script = codecToScript(sourceCodec);
                Path devFile = dataDir.resolve(script.toLowerCase() + suffix);
                if (!Files.exists(devFile)) {
                    System.err.printf("  [%s→%s] no dev file for %s, skipping%n",
                            sourceCodec, wrongCodec, script);
                    continue;
                }

                // Load a large pool; we'll filter down per-length
                List<String> allSentences = loadSentences(devFile, nPerCell * 50);

                // Pre-filter: keep only sentences that roundtrip through sourceCodec
                // and produce at least one differing character vs wrongCodec.
                List<String[]> candidates = new ArrayList<>(); // {asSource, asWrong}
                for (String sentence : allSentences) {
                    byte[] bytes = sentence.getBytes(srcCharset);
                    String asSource = new String(bytes, srcCharset);
                    if (!asSource.equals(sentence)) continue; // encoding lost data
                    String asWrong = new String(bytes, wrongCharset);
                    if (asSource.equals(asWrong)) continue; // no differentiating bytes
                    candidates.add(new String[]{asSource, asWrong});
                }

                if (candidates.isEmpty()) {
                    System.err.printf("  [%s→%s] no qualifying sentences%n",
                            sourceCodec, wrongCodec);
                    continue;
                }

                System.err.printf("  [%s→%s] %d candidates from %s%n",
                        sourceCodec, wrongCodec, candidates.size(), script);

                for (int targetLen : lengths) {
                    Random rng = new Random(seed);
                    // Shuffle candidates for this length independently
                    List<String[]> shuffled = new ArrayList<>(candidates);
                    Collections.shuffle(shuffled, rng);

                    List<Float> deltas = new ArrayList<>();
                    int nCorrect = 0;
                    int nNoDiff = 0;

                    for (String[] cand : shuffled) {
                        if (deltas.size() + nNoDiff >= nPerCell * 3 && deltas.size() >= nPerCell) {
                            break;
                        }
                        String asSource = trimToLength(cand[0], targetLen);
                        String asWrong  = trimToLength(cand[1], targetLen);

                        if (asSource.equals(asWrong)) {
                            nNoDiff++;
                            continue;
                        }
                        if (asSource.isEmpty() || asWrong.isEmpty()) continue;

                        TextQualityComparison result = detector.compare(
                                sourceCodec, asSource, wrongCodec, asWrong);

                        deltas.add(result.delta());
                        if ("A".equals(result.winner())) nCorrect++;
                    }

                    if (deltas.isEmpty()) continue;

                    double accuracy    = (double) nCorrect / deltas.size();
                    double meanDelta   = deltas.stream().mapToDouble(Float::floatValue).average().orElse(0);
                    List<Float> sorted = new ArrayList<>(deltas);
                    Collections.sort(sorted);
                    float medianDelta  = sorted.get(sorted.size() / 2);

                    System.err.printf("    len=%3d  n=%3d  acc=%.3f  mean_delta=%.3f  median_delta=%.3f%n",
                            targetLen, deltas.size(), accuracy, meanDelta, medianDelta);

                    out.printf("%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%d%n",
                            sourceCodec, wrongCodec, targetLen,
                            deltas.size(), accuracy, meanDelta, medianDelta, nNoDiff);
                }
                out.flush();
            }
        }
    }

    /**
     * Returns which dev-file script to use for a given source codec.
     * CP1251 → CYRILLIC, CP1253 → GREEK, CP1255 → HEBREW, everything else → LATIN.
     */
    private static String codecToScript(String codec) {
        switch (codec.toLowerCase()) {
            case "windows-1251": return "CYRILLIC";
            case "windows-1253": return "GREEK";
            case "windows-1255": return "HEBREW";
            default:             return "LATIN";
        }
    }

    /**
     * Trims a string to approximately {@code targetLen} UTF-8 bytes, aligned to
     * a codepoint boundary.  Used to produce short-string variants for compare() testing.
     */
    private static String trimToLength(String s, int targetLen) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= targetLen) return s;
        int end = targetLen;
        while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) end++;
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
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

    private static double computeCohensD(List<Float> clean, List<Float> corrupt) {
        if (clean.isEmpty() || corrupt.isEmpty()) return Double.NaN;
        double mc = mean(clean);
        double mj = mean(corrupt);
        double vc = variance(clean, mc);
        double vj = variance(corrupt, mj);
        double pooledStd = Math.sqrt((vc + vj) / 2.0);
        if (pooledStd < 1e-9) return Double.NaN;
        return (mc - mj) / pooledStd;
    }

    private static double mean(List<Float> xs) {
        return xs.stream().mapToDouble(Float::floatValue).average().orElse(0);
    }

    private static double variance(List<Float> xs, double mu) {
        return xs.stream().mapToDouble(x -> (x - mu) * (x - mu)).average().orElse(0);
    }

    private static double fractionBelow(List<Float> zs, float threshold) {
        if (zs.isEmpty()) return Double.NaN;
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
            if (!score.isUnknown()) results.add(score.getZScore());
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
            if (!score.isUnknown()) results.add(score.getZScore());
        }
        return results;
    }

    private static List<Float> scoreReversed(JunkDetector detector, List<String> sentences,
                                              int targetLen, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = reverseCodepoints(pickSubstring(sentences, targetLen, rng));
            TextQualityScore score = detector.score(s);
            if (!score.isUnknown()) results.add(score.getZScore());
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
            if (!score.isUnknown()) results.add(score.getZScore());
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
            if (!score.isUnknown()) results.add(score.getZScore());
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
            if (!score.isUnknown()) results.add(score.getZScore());
        }
        return results;
    }

    /**
     * Applies a randomly chosen wrong-codec character remap at {@code rate} to each
     * sample.  Simulates real-world charset misdetection at the character level
     * (e.g. CP1252-encoded text decoded as CP1255, replacing umlauts with Hebrew letters).
     */
    private static List<Float> scoreWithRemap(JunkDetector detector, List<String> sentences,
                                               int targetLen,
                                               List<Map<Character, Character>> remapTables,
                                               double rate, int n, Random rng) {
        List<Float> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String s = pickSubstring(sentences, targetLen, rng);
            Map<Character, Character> table = remapTables.get(rng.nextInt(remapTables.size()));
            String corrupted = TrainJunkModel.wrongCodecRemap(s, table, rate, rng);
            TextQualityScore score = detector.score(corrupted);
            if (!score.isUnknown()) results.add(score.getZScore());
        }
        return results;
    }

    // -----------------------------------------------------------------------
    // Distortion primitives
    // -----------------------------------------------------------------------

    /**
     * Injects control characters (0x01–0x09) at the given rate.
     */
    static void injectRandomBytes(byte[] bytes, double rate, Random rng) {
        for (int i = 0; i < bytes.length; i++) {
            if (rng.nextDouble() < rate) {
                bytes[i] = (byte) (0x01 + rng.nextInt(9));
            }
        }
    }

    /**
     * Wrong-codec distortion: UTF-8 bytes re-interpreted as ISO-8859-1, then
     * re-encoded as UTF-8.  Produces bogus two-byte sequences for any non-ASCII byte.
     */
    static byte[] wrongCodecBytes(byte[] utf8) {
        String misread = new String(utf8, StandardCharsets.ISO_8859_1);
        return misread.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Swaps each adjacent pair of bytes — simulates reading a 2-byte encoding
     * (UTF-16, CP932 two-byte sequences) with wrong byte order.
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
        if (bytes.length <= targetLen) return s;
        int start = rng.nextInt(bytes.length - targetLen);
        while (start > 0 && (bytes[start] & 0xC0) == 0x80) start--;
        int end = Math.min(start + targetLen, bytes.length);
        while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) end++;
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
                        && trimmed.getBytes(StandardCharsets.UTF_8).length >= 5) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
