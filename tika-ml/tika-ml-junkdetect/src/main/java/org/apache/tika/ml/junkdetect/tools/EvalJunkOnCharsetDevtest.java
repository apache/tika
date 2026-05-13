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

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.junkdetect.JunkDetector;
import org.apache.tika.quality.TextQualityScore;

/**
 * Eval harness: for each labeled charset in {@code ~/data/charsets/devtest/},
 * decode under its true charset (clean) and under a curated set of wrong
 * charsets (mojibake), score with {@link JunkDetector}, report margin
 * statistics per (labeled_charset × wrong_charset × source-byte-length).
 *
 * <p>Devtest file format: gzip → repeated {@code [u16 big-endian length,
 * length bytes]} records, where the bytes are real text encoded in the
 * labeled charset.  Same format the charset trainer consumes.
 *
 * <p>Output (TSVs):
 * <ul>
 *   <li><b>detail.tsv</b>: one row per (labeled_cs, script, wrong_cs, length).
 *       Columns: n, mean_clean_z, mean_mojibake_z, cohens_d, mean_margin,
 *       p5_margin, p50_margin, fpr, tpr.</li>
 *   <li><b>summary.tsv</b>: macro-averaged across wrong charsets, per
 *       (script, length).  The headline "is this script in trouble?" view.</li>
 *   <li><b>script_pivot.tsv</b>: per-script rollup across all lengths +
 *       wrong charsets.  Single-number-per-script view for spot inversion.</li>
 * </ul>
 *
 * <p>"Margin" is the per-record paired difference {@code clean_z -
 * mojibake_z}.  Mean margin and 5th-percentile margin are the
 * margin-maximization metrics the v6 retrain is optimizing for.  Cohen's d
 * is the independent-distribution analog (kept for compatibility with the
 * existing {@link EvalJunkDetector} schema).
 *
 * <p>Usage:
 * <pre>
 *   ./mvnw -pl tika-ml/tika-ml-junkdetect exec:java \
 *     -Dexec.mainClass=org.apache.tika.ml.junkdetect.tools.EvalJunkOnCharsetDevtest \
 *     -Dexec.args="--devtest-dir ~/data/charsets/devtest --output-dir /tmp/v5-baseline"
 * </pre>
 */
public class EvalJunkOnCharsetDevtest {

    /**
     * Curated set of wrong charsets to cross-decode every labeled charset
     * against.  Chosen to span the common real-world mojibake families:
     * Western Latin (cp1252, ISO-8859-1, MacRoman), CJK over-claim (GB18030,
     * Big5-HKSCS, Shift_JIS), Cyrillic (KOI8-R, cp1251), Arabic (cp1256),
     * EBCDIC over-claim (IBM424), DOS Latin (IBM850), and UTF-8 (catches
     * non-UTF8 bytes as replacement-character garbage).
     */
    private static final List<String> DEFAULT_WRONG_CHARSETS = List.of(
            "windows-1252", "ISO-8859-1", "x-MacRoman",
            "GB18030", "Big5-HKSCS", "Shift_JIS",
            "KOI8-R", "windows-1251",
            "windows-1256", "IBM424",
            "IBM850", "UTF-8"
    );

    /** Source-byte length buckets to slice records into. */
    private static final int[] DEFAULT_LENGTHS = {20, 50, 100, 200, 500, 1000};

    /** Cap on records loaded per labeled-charset file. */
    private static final int DEFAULT_MAX_RECORDS = 2000;

    /** Threshold for FPR/TPR reporting; matches EvalJunkDetector default. */
    private static final float DEFAULT_THRESHOLD = -2.0f;

    /** Minimum number of paired (clean, mojibake) samples per cell to emit a row. */
    private static final int MIN_SAMPLES_PER_CELL = 30;

    public static void main(String[] args) throws IOException {
        Path devtestDir = Paths.get(System.getProperty("user.home"),
                "data", "charsets", "devtest");
        Path outputDir = Paths.get("/tmp/junkdetect-eval");
        Path modelPath = null;
        int maxRecords = DEFAULT_MAX_RECORDS;
        int[] lengths = DEFAULT_LENGTHS;
        float threshold = DEFAULT_THRESHOLD;
        List<String> wrongCharsets = DEFAULT_WRONG_CHARSETS;
        List<String> labeledFilter = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--devtest-dir":
                    devtestDir = Paths.get(args[++i]);
                    break;
                case "--output-dir":
                    outputDir = Paths.get(args[++i]);
                    break;
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--max-records":
                    maxRecords = Integer.parseInt(args[++i]);
                    break;
                case "--threshold":
                    threshold = Float.parseFloat(args[++i]);
                    break;
                case "--lengths":
                    lengths = Arrays.stream(args[++i].split(","))
                            .mapToInt(Integer::parseInt).toArray();
                    break;
                case "--wrong-charsets":
                    wrongCharsets = Arrays.asList(args[++i].split(","));
                    break;
                case "--only":
                    labeledFilter = Arrays.asList(args[++i].split(","));
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (!Files.isDirectory(devtestDir)) {
            System.err.println("ERROR: devtest-dir not found: " + devtestDir);
            System.exit(1);
        }
        Files.createDirectories(outputDir);

        JunkDetector detector = modelPath != null
                ? JunkDetector.loadFromPath(modelPath)
                : JunkDetector.loadFromClasspath();

        System.err.println("=== EvalJunkOnCharsetDevtest ===");
        System.err.println("  devtest-dir:  " + devtestDir);
        System.err.println("  output-dir:   " + outputDir);
        System.err.println("  model:        " + (modelPath != null ? modelPath : "classpath default"));
        System.err.println("  model version: " + detector.getModelVersion());
        System.err.println("  max-records:  " + maxRecords);
        System.err.println("  lengths:      " + Arrays.toString(lengths));
        System.err.println("  threshold:    " + threshold);
        System.err.println("  wrong-cs:     " + wrongCharsets);

        // Resolve wrong charsets (skip any the JVM doesn't have)
        Map<String, Charset> resolvedWrong = new LinkedHashMap<>();
        for (String name : wrongCharsets) {
            Charset cs = tryGetCharset(name);
            if (cs == null) {
                System.err.println("  WARN: wrong-charset unavailable: " + name);
                continue;
            }
            resolvedWrong.put(name, cs);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(devtestDir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            System.err.println("ERROR: no *.bin.gz files in " + devtestDir);
            System.exit(1);
        }

        Path detailPath = outputDir.resolve("detail.tsv");
        Path summaryPath = outputDir.resolve("summary.tsv");
        Path pivotPath = outputDir.resolve("script_pivot.tsv");

        List<Row> allRows = new ArrayList<>();

        try (PrintWriter detail = new PrintWriter(
                Files.newBufferedWriter(detailPath, StandardCharsets.UTF_8))) {

            detail.println("labeled_cs\tscript\twrong_cs\tlength\tn"
                    + "\tmean_clean_z\tmean_mojibake_z\tcohens_d"
                    + "\tmean_margin\tp5_margin\tp50_margin"
                    + "\tfpr\ttpr");

            for (Path file : files) {
                String labeledName = filenameToCharsetName(file);
                if (labeledFilter != null && !labeledFilter.contains(labeledName)) {
                    continue;
                }
                Charset labeled = tryGetCharset(labeledName);
                if (labeled == null) {
                    System.err.println("  SKIP: labeled charset unavailable: " + labeledName);
                    continue;
                }

                List<byte[]> records = readRecords(file, maxRecords);
                if (records.size() < MIN_SAMPLES_PER_CELL) {
                    System.err.printf("  SKIP %s: only %d records%n",
                            labeledName, records.size());
                    continue;
                }

                System.err.printf("%n--- %s (%d records) ---%n",
                        labeledName, records.size());

                for (int len : lengths) {
                    List<byte[]> slices = sliceToLength(records, len);
                    if (slices.size() < MIN_SAMPLES_PER_CELL) {
                        continue;
                    }

                    // Decode all slices under labeled (clean) once
                    List<String> cleanTexts = decodeAll(slices, labeled);
                    List<Float> cleanZs = scoreAll(detector, cleanTexts);
                    if (cleanZs.size() < MIN_SAMPLES_PER_CELL) {
                        continue;
                    }

                    // Detect script from a sample of the clean decoded text
                    String script = detectDominantScript(
                            cleanTexts.get(cleanTexts.size() / 2));

                    for (Map.Entry<String, Charset> entry : resolvedWrong.entrySet()) {
                        String wrongName = entry.getKey();
                        Charset wrongCs = entry.getValue();
                        if (equalCharset(labeled, wrongCs)) {
                            continue; // can't be its own mojibake
                        }

                        List<String> mojiTexts = decodeAll(slices, wrongCs);
                        // Pair cleanTexts[i] with mojiTexts[i] by source record
                        Row row = scorePairs(detector, script, labeledName,
                                wrongName, len, cleanTexts, mojiTexts,
                                cleanZs, threshold);
                        if (row == null) {
                            continue;
                        }
                        allRows.add(row);
                        detail.println(row.toTsv());
                    }
                    detail.flush();
                    System.err.printf("    len=%4d  n_clean=%d  cells=%d%n",
                            len, cleanZs.size(),
                            allRows.stream()
                                    .filter(r -> r.labeledCs.equals(labeledName)
                                            && r.length == len)
                                    .count());
                }
            }
        }

        writeSummary(summaryPath, allRows, lengths);
        writeScriptPivot(pivotPath, allRows);

        System.err.println("\nWrote " + detailPath);
        System.err.println("Wrote " + summaryPath);
        System.err.println("Wrote " + pivotPath);
        System.err.println("Done.");
    }

    // -----------------------------------------------------------------------
    // Per-cell scoring (one labeled × wrong × length cell)
    // -----------------------------------------------------------------------

    private static Row scorePairs(JunkDetector detector,
                                  String script,
                                  String labeledName, String wrongName,
                                  int length,
                                  List<String> cleanTexts,
                                  List<String> mojiTexts,
                                  List<Float> cleanZsPre,
                                  float threshold) {
        // cleanZsPre is the already-scored clean text (avoid re-scoring per wrong cs).
        // We re-score only the mojibake side here.
        int n = Math.min(cleanTexts.size(), mojiTexts.size());
        List<Float> cleanZs = new ArrayList<>(n);
        List<Float> mojiZs = new ArrayList<>(n);
        List<Float> margins = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            float cz = cleanZsPre.get(i);
            TextQualityScore ms = detector.score(mojiTexts.get(i));
            if (ms.isUnknown()) {
                continue;
            }
            float mz = ms.getZScore();
            cleanZs.add(cz);
            mojiZs.add(mz);
            margins.add(cz - mz);
        }
        if (margins.size() < MIN_SAMPLES_PER_CELL) {
            return null;
        }
        return new Row(labeledName, script, wrongName, length,
                cleanZs, mojiZs, margins, threshold);
    }

    // -----------------------------------------------------------------------
    // I/O: read the gzipped length-prefixed record format
    // -----------------------------------------------------------------------

    private static List<byte[]> readRecords(Path file, int maxRecords) throws IOException {
        List<byte[]> records = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (records.size() < maxRecords) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (EOFException eof) {
                    break;
                }
                byte[] rec = new byte[len];
                dis.readFully(rec);
                records.add(rec);
            }
        }
        return records;
    }

    private static List<byte[]> sliceToLength(List<byte[]> records, int len) {
        List<byte[]> slices = new ArrayList<>();
        for (byte[] r : records) {
            if (r.length >= len) {
                slices.add(Arrays.copyOf(r, len));
            }
        }
        return slices;
    }

    private static List<String> decodeAll(List<byte[]> slices, Charset cs) {
        List<String> texts = new ArrayList<>(slices.size());
        for (byte[] s : slices) {
            texts.add(decode(s, cs));
        }
        return texts;
    }

    private static String decode(byte[] bytes, Charset cs) {
        CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, cs); // fallback; shouldn't happen with REPLACE
        }
    }

    private static List<Float> scoreAll(JunkDetector detector, List<String> texts) {
        List<Float> zs = new ArrayList<>(texts.size());
        for (String t : texts) {
            TextQualityScore s = detector.score(t);
            if (!s.isUnknown()) {
                zs.add(s.getZScore());
            } else {
                zs.add(Float.NaN);
            }
        }
        return zs;
    }

    // -----------------------------------------------------------------------
    // Aggregation: summary.tsv (macro across wrong charsets, per script×length)
    // -----------------------------------------------------------------------

    private static void writeSummary(Path summaryPath, List<Row> rows,
                                     int[] lengths) throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8))) {
            out.println("script\tlength\tn_cells"
                    + "\tmacro_cohens_d\tmacro_mean_margin\tmacro_p5_margin"
                    + "\tmacro_fpr\tmacro_tpr");

            // Group by (script, length)
            Map<String, Map<Integer, List<Row>>> bucketed = new HashMap<>();
            for (Row r : rows) {
                bucketed
                        .computeIfAbsent(r.script, k -> new HashMap<>())
                        .computeIfAbsent(r.length, k -> new ArrayList<>())
                        .add(r);
            }

            List<String> scripts = new ArrayList<>(bucketed.keySet());
            Collections.sort(scripts);
            for (String script : scripts) {
                for (int len : lengths) {
                    List<Row> cell = bucketed.get(script).get(len);
                    if (cell == null || cell.isEmpty()) {
                        continue;
                    }
                    double macroD = cell.stream()
                            .filter(r -> !Double.isNaN(r.cohensD))
                            .mapToDouble(r -> r.cohensD)
                            .average().orElse(Double.NaN);
                    double macroMargin = cell.stream()
                            .mapToDouble(r -> r.meanMargin)
                            .average().orElse(Double.NaN);
                    double macroP5 = cell.stream()
                            .mapToDouble(r -> r.p5Margin)
                            .average().orElse(Double.NaN);
                    double macroFpr = cell.stream()
                            .mapToDouble(r -> r.fpr)
                            .average().orElse(Double.NaN);
                    double macroTpr = cell.stream()
                            .mapToDouble(r -> r.tpr)
                            .average().orElse(Double.NaN);
                    out.printf("%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f%n",
                            script, len, cell.size(),
                            macroD, macroMargin, macroP5, macroFpr, macroTpr);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Aggregation: script_pivot.tsv (single line per script — quick triage)
    // -----------------------------------------------------------------------

    private static void writeScriptPivot(Path path, List<Row> rows) throws IOException {
        try (PrintWriter out = new PrintWriter(
                Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            out.println("script\tn_cells"
                    + "\tmean_d\tmean_margin\tmean_p5_margin"
                    + "\tmin_d_cell\tmin_margin_cell");

            Map<String, List<Row>> byScript = new HashMap<>();
            for (Row r : rows) {
                byScript.computeIfAbsent(r.script, k -> new ArrayList<>()).add(r);
            }
            List<String> scripts = new ArrayList<>(byScript.keySet());
            Collections.sort(scripts);
            for (String script : scripts) {
                List<Row> cells = byScript.get(script);
                double meanD = cells.stream()
                        .filter(r -> !Double.isNaN(r.cohensD))
                        .mapToDouble(r -> r.cohensD)
                        .average().orElse(Double.NaN);
                double meanMargin = cells.stream()
                        .mapToDouble(r -> r.meanMargin)
                        .average().orElse(Double.NaN);
                double meanP5 = cells.stream()
                        .mapToDouble(r -> r.p5Margin)
                        .average().orElse(Double.NaN);
                Row minDCell = cells.stream()
                        .filter(r -> !Double.isNaN(r.cohensD))
                        .min((a, b) -> Double.compare(a.cohensD, b.cohensD))
                        .orElse(null);
                Row minMarginCell = cells.stream()
                        .min((a, b) -> Double.compare(a.meanMargin, b.meanMargin))
                        .orElse(null);
                out.printf("%s\t%d\t%.3f\t%.3f\t%.3f\t%s\t%s%n",
                        script, cells.size(),
                        meanD, meanMargin, meanP5,
                        minDCell != null ? cellLabel(minDCell) : "-",
                        minMarginCell != null ? cellLabel(minMarginCell) : "-");
            }
        }
    }

    private static String cellLabel(Row r) {
        return String.format("[%s→%s@%d]", r.labeledCs, r.wrongCs, r.length);
    }

    // -----------------------------------------------------------------------
    // Charset utilities
    // -----------------------------------------------------------------------

    private static String filenameToCharsetName(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".bin.gz")) {
            name = name.substring(0, name.length() - ".bin.gz".length());
        }
        return name;
    }

    private static Charset tryGetCharset(String name) {
        try {
            return Charset.forName(name);
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            return null;
        }
    }

    private static boolean equalCharset(Charset a, Charset b) {
        return a.name().equalsIgnoreCase(b.name())
                || a.aliases().contains(b.name())
                || b.aliases().contains(a.name());
    }

    // -----------------------------------------------------------------------
    // Script detection (parallels JunkDetector.detectDominantScript, which is
    // package-private; small enough to inline)
    // -----------------------------------------------------------------------

    private static final Map<String, String> SCRIPT_FALLBACK = Map.of(
            "HIRAGANA", "HAN",
            "KATAKANA", "HAN"
    );

    private static String detectDominantScript(String text) {
        if (text == null || text.isEmpty()) {
            return "LATIN";
        }
        Map<Character.UnicodeScript, Integer> counts = new HashMap<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Character.UnicodeScript s = Character.UnicodeScript.of(cp);
            if (s != Character.UnicodeScript.COMMON
                    && s != Character.UnicodeScript.INHERITED
                    && s != Character.UnicodeScript.UNKNOWN) {
                counts.merge(s, 1, Integer::sum);
            }
            i += Character.charCount(cp);
        }
        if (counts.isEmpty()) {
            return "LATIN";
        }
        String name = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse("LATIN");
        return SCRIPT_FALLBACK.getOrDefault(name, name);
    }

    // -----------------------------------------------------------------------
    // Row
    // -----------------------------------------------------------------------

    private static final class Row {
        final String labeledCs;
        final String script;
        final String wrongCs;
        final int length;
        final int n;
        final double meanCleanZ;
        final double meanMojiZ;
        final double cohensD;
        final double meanMargin;
        final double p5Margin;
        final double p50Margin;
        final double fpr;
        final double tpr;

        Row(String labeledCs, String script, String wrongCs, int length,
            List<Float> cleanZs, List<Float> mojiZs, List<Float> margins,
            float threshold) {
            this.labeledCs = labeledCs;
            this.script = script;
            this.wrongCs = wrongCs;
            this.length = length;
            this.n = margins.size();
            this.meanCleanZ = mean(cleanZs);
            this.meanMojiZ = mean(mojiZs);
            this.cohensD = computeCohensD(cleanZs, mojiZs);
            this.meanMargin = mean(margins);
            this.p5Margin = percentile(margins, 0.05);
            this.p50Margin = percentile(margins, 0.50);
            this.fpr = fractionBelow(cleanZs, threshold);
            this.tpr = fractionBelow(mojiZs, threshold);
        }

        String toTsv() {
            return String.format(
                    "%s\t%s\t%s\t%d\t%d\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f\t%.3f",
                    labeledCs, script, wrongCs, length, n,
                    meanCleanZ, meanMojiZ, cohensD,
                    meanMargin, p5Margin, p50Margin,
                    fpr, tpr);
        }
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private static double computeCohensD(List<Float> a, List<Float> b) {
        if (a.size() < 2 || b.size() < 2) {
            return Double.NaN;
        }
        double ma = mean(a);
        double mb = mean(b);
        double va = variance(a, ma);
        double vb = variance(b, mb);
        double pooled = Math.sqrt((va + vb) / 2.0);
        if (pooled < 1e-9) {
            return Double.NaN;
        }
        return (ma - mb) / pooled;
    }

    private static double mean(List<Float> xs) {
        double s = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                s += f;
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double variance(List<Float> xs, double m) {
        if (xs.size() < 2) {
            return 0;
        }
        double s = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                double d = f - m;
                s += d * d;
                n++;
            }
        }
        return n < 2 ? 0 : s / (n - 1);
    }

    private static double percentile(List<Float> xs, double p) {
        List<Float> sorted = new ArrayList<>(xs);
        sorted.removeIf(f -> Float.isNaN(f));
        if (sorted.isEmpty()) {
            return Double.NaN;
        }
        Collections.sort(sorted);
        int idx = (int) Math.floor(p * (sorted.size() - 1));
        return sorted.get(idx);
    }

    private static double fractionBelow(List<Float> xs, float threshold) {
        int below = 0;
        int n = 0;
        for (float f : xs) {
            if (!Float.isNaN(f)) {
                if (f < threshold) {
                    below++;
                }
                n++;
            }
        }
        return n == 0 ? Double.NaN : (double) below / n;
    }

    // -----------------------------------------------------------------------

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  EvalJunkOnCharsetDevtest");
        System.err.println("    [--devtest-dir <path>]   (default ~/data/charsets/devtest)");
        System.err.println("    [--output-dir <path>]    (default /tmp/junkdetect-eval)");
        System.err.println("    [--model <path>]         (default classpath junkdetect.bin)");
        System.err.println("    [--max-records N]        (default 2000)");
        System.err.println("    [--threshold F]          (default -2.0)");
        System.err.println("    [--lengths 20,50,...]");
        System.err.println("    [--wrong-charsets a,b,...]");
        System.err.println("    [--only labeledCs,...]   (filter for spot runs)");
    }
}
