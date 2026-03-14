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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.chardetect.CharsetConfusables;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector;
import org.apache.tika.ml.chardetect.MojibusterEncodingDetector.Rule;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.txt.Icu4jEncodingDetector;
import org.apache.tika.parser.txt.UniversalEncodingDetector;

/**
 * Compares {@link MojibusterEncodingDetector} against ICU4J and juniversalchardet.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code --lengths 20,50,100,200,full} — per-probe-length accuracy sweep</li>
 *   <li>{@code --confusion} — top-confusion report for the ML-All detector</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   java EvalCharsetDetectors \
 *     [--model /path/to/chardetect.bin] \
 *     --data  /path/to/test-dir \
 *     [--lengths 20,50,100,200,full] \
 *     [--confusion]
 * </pre>
 */
public class EvalCharsetDetectors {

    private static final String NULL_LABEL = "(null)";
    private static final int FULL_LENGTH = Integer.MAX_VALUE;

    private static final double OOV_THRESHOLD_CJK  = 0.80;
    private static final double OOV_THRESHOLD_SBCS = 0.98;
    private static final Set<String> CJK_CHARSETS = Set.of(
            "Big5", "Big5-HKSCS", "EUC-JP", "EUC-KR", "EUC-TW",
            "GB18030", "GB2312", "GBK", "Shift_JIS"
    );
    private static final Set<String> OOV_EXEMPT = Set.of(
            "US-ASCII", "UTF-16-LE", "UTF-16-BE", "UTF-32-LE", "UTF-32-BE",
            "ISO-2022-JP", "ISO-2022-KR", "ISO-2022-CN"
    );

    private static final String[] COL_NAMES = {"Stat", "+ISO", "+CJK", "All", "ICU4J", "juniv"};
    private static final int NUM_DETECTORS = COL_NAMES.length;
    // Index of the "All" detector — used for confusion matrix and score-only output
    private static final int IDX_ALL = 3;

    private static final int TOP_K = 3;

    /**
     * Charsets present in the test directory but not as direct model labels —
     * either confusable aliases for a trained label or structural-only charsets
     * whose test data was generated before the structural-only distinction was
     * introduced.  Skipped in per-row reporting to avoid misleading 0% numbers.
     */
    private static final Set<String> DEFAULT_EXCLUDE = Set.of(
            "IBM437",          // superset IBM850 is the trained label; IBM437 is a confusable alias
            "x-ISO-2022-CN-CNS" // structural-only (ISO-2022 escape gates); no ML model label
    );

    public static void main(String[] args) throws Exception {
        Path modelPath = null;
        Path dataDir   = null;
        int[] probeLengths = {8, 16, 32, 64, 128, 256, FULL_LENGTH};
        boolean showConfusion = false;
        boolean scoreOnly = false;
        Set<String> exclude = new HashSet<>(DEFAULT_EXCLUDE);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--data":
                    dataDir = Paths.get(args[++i]);
                    break;
                case "--lengths":
                    probeLengths = parseLengths(args[++i]);
                    break;
                case "--confusion":
                    showConfusion = true;
                    break;
                case "--score-only":
                    scoreOnly = true;
                    break;
                case "--exclude":
                    exclude.addAll(Arrays.asList(args[++i].split(",")));
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }
        if (dataDir == null) {
            System.err.println(
                    "Usage: EvalCharsetDetectors [--model <path>] --data <dir>"
                    + " [--lengths 20,50,100,full] [--confusion] [--exclude cs1,cs2]");
            System.exit(1);
        }

        MojibusterEncodingDetector base = modelPath != null
                ? new MojibusterEncodingDetector(modelPath)
                : new MojibusterEncodingDetector();

        EncodingDetector[] detectors = {
            base.withRules(EnumSet.noneOf(Rule.class)),
            base.withRules(EnumSet.of(Rule.STRUCTURAL_GATES, Rule.ISO_TO_WINDOWS)),
            base.withRules(EnumSet.of(Rule.STRUCTURAL_GATES, Rule.CJK_GRAMMAR)),
            base,
            new Icu4jEncodingDetector(),
            new UniversalEncodingDetector()
        };

        List<Path> testFiles = Files.list(dataDir)
                .filter(p -> p.getFileName().toString().endsWith(".bin.gz"))
                .sorted()
                .collect(Collectors.toList());

        if (testFiles.isEmpty()) {
            System.err.println("No .bin.gz files found in: " + dataDir);
            System.exit(1);
        }

        // Load all samples once; truncation happens per probe-length sweep
        List<String> charsets = new ArrayList<>();
        List<List<byte[]>> allSamplesPerCharset = new ArrayList<>();
        for (Path f : testFiles) {
            String cs = f.getFileName().toString().replaceAll("\\.bin\\.gz$", "");
            if (exclude.contains(cs)) {
                continue;
            }
            List<byte[]> samples = loadSamples(f);
            if (!samples.isEmpty()) {
                charsets.add(cs);
                allSamplesPerCharset.add(samples);
            }
        }

        // Length-sweep summary: one line per length showing All-detector accuracy
        List<String> sweepLabels = new ArrayList<>();
        List<double[]> sweepResults = new ArrayList<>();

        // One pass per probe length
        for (int probeLen : probeLengths) {
            String lenLabel = probeLen == FULL_LENGTH ? "full" : probeLen + "B";
            if (!scoreOnly) {
                System.out.println("\n=== Probe length: " + lenLabel + " ===");
                printHeader();
            }

            long totalN = 0;
            // [detector][0=strict, 1=soft, 2=topK, 3=decodeMatch, 4=alphaMatch]
            long[][] totals = new long[NUM_DETECTORS][5];
            long[] totalNanos = new long[NUM_DETECTORS];
            // confusion[trueIdx][predLabel] = count  (only for IDX_ALL)
            List<Map<String, Integer>> confusion = new ArrayList<>();
            for (int ci = 0; ci < charsets.size(); ci++) {
                confusion.add(new HashMap<>());
            }

            for (int ci = 0; ci < charsets.size(); ci++) {
                String charset = charsets.get(ci);
                List<byte[]> samples = truncate(allSamplesPerCharset.get(ci), probeLen);
                int n = samples.size();
                if (n == 0) {
                    continue;
                }

                int[][] counts = new int[NUM_DETECTORS][5];
                for (byte[] sample : samples) {
                    for (int d = 0; d < NUM_DETECTORS; d++) {
                        long t0 = System.nanoTime();
                        List<String> preds = predictAll(detectors[d], sample);
                        totalNanos[d] += System.nanoTime() - t0;
                        String top1 = preds.isEmpty() ? NULL_LABEL : preds.get(0);
                        boolean strict = isStrict(charset, top1);
                        if (strict) {
                            counts[d][0]++;
                        }
                        if (isSoft(charset, top1)) {
                            counts[d][1]++;
                        }
                        if (isInTopK(charset, preds, TOP_K)) {
                            counts[d][2]++;
                        }
                        if (strict) {
                            counts[d][3]++;
                            counts[d][4]++;
                        } else if (!NULL_LABEL.equals(top1)) {
                            if (isDecodeMatch(charset, top1, sample)) {
                                counts[d][3]++;
                                counts[d][4]++;
                            } else if (isAlphaMatch(charset, top1, sample)) {
                                counts[d][4]++;
                            }
                        }
                        if (d == IDX_ALL && !strict) {
                            confusion.get(ci).merge(top1, 1, Integer::sum);
                        }
                    }
                }

                if (!scoreOnly) {
                    printRow(charset, n, counts);
                }
                totalN += n;
                for (int d = 0; d < NUM_DETECTORS; d++) {
                    for (int m = 0; m < 5; m++) {
                        totals[d][m] += counts[d][m];
                    }
                }
            }

            double strictPct  = totalN == 0 ? 0.0 : 100.0 * totals[IDX_ALL][0] / totalN;
            double softPct    = totalN == 0 ? 0.0 : 100.0 * totals[IDX_ALL][1] / totalN;
            double topKPct    = totalN == 0 ? 0.0 : 100.0 * totals[IDX_ALL][2] / totalN;
            double decodePct  = totalN == 0 ? 0.0 : 100.0 * totals[IDX_ALL][3] / totalN;
            double alphaPct   = totalN == 0 ? 0.0 : 100.0 * totals[IDX_ALL][4] / totalN;
            sweepLabels.add(lenLabel);
            sweepResults.add(new double[]{strictPct, softPct, topKPct, decodePct, alphaPct});

            if (scoreOnly) {
                System.out.printf(Locale.ROOT, "SCORE %.4f%n", strictPct);
            } else {
                printFooter(totalN, totals, totalNanos);
                if (showConfusion) {
                    printConfusion(charsets, allSamplesPerCharset, confusion, probeLen, lenLabel);
                }
            }
        }

        // Print length-sweep summary when multiple lengths were tested
        if (sweepLabels.size() > 1 && !scoreOnly) {
            System.out.println("\n=== Accuracy by probe length (All detector) ===");
            System.out.printf(Locale.ROOT, "  %-8s  %8s  %8s  %8s  %8s  %8s%n",
                    "Length", "Strict%", "Soft%", "Top" + TOP_K + "%", "Decode%", "Alpha%");
            System.out.println("  " + "-".repeat(58));
            for (int i = 0; i < sweepLabels.size(); i++) {
                System.out.printf(Locale.ROOT, "  %-8s  %8.1f  %8.1f  %8.1f  %8.1f  %8.1f%n",
                        sweepLabels.get(i),
                        sweepResults.get(i)[0], sweepResults.get(i)[1],
                        sweepResults.get(i)[2], sweepResults.get(i)[3],
                        sweepResults.get(i)[4]);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Confusion matrix
    // -----------------------------------------------------------------------

    private static void printConfusion(List<String> charsets,
                                       List<List<byte[]>> allSamplesPerCharset,
                                       List<Map<String, Integer>> confusion,
                                       int probeLen, String lenLabel) {
        System.out.println("\n--- Confusion (All/ML+rules, " + lenLabel
                + ", top errors per charset) ---");

        boolean anyError = false;
        for (int ci = 0; ci < charsets.size(); ci++) {
            Map<String, Integer> errors = confusion.get(ci);
            if (errors.isEmpty()) {
                continue;
            }
            anyError = true;
            int total = truncate(allSamplesPerCharset.get(ci), probeLen).size();
            int totalErrors = errors.values().stream().mapToInt(Integer::intValue).sum();
            double errPct = 100.0 * totalErrors / total;

            // Sort errors by count descending
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(errors.entrySet());
            sorted.sort((a, b) -> b.getValue() - a.getValue());

            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "  %-22s %5.1f%% wrong → ", charsets.get(ci), errPct));
            int shown = 0;
            for (Map.Entry<String, Integer> e : sorted) {
                if (shown > 0) {
                    sb.append(", ");
                }
                sb.append(String.format(Locale.ROOT, "%s:%.1f%%",
                        e.getKey(), 100.0 * e.getValue() / total));
                if (++shown >= 5) {
                    break;
                }
            }
            System.out.println(sb);
        }
        if (!anyError) {
            System.out.println("  (no errors)");
        }
    }

    // -----------------------------------------------------------------------
    //  Table formatting
    // -----------------------------------------------------------------------

    private static void printHeader() {
        StringBuilder sb1 = new StringBuilder();
        sb1.append(String.format(Locale.ROOT, "%-22s  %5s  ", "", "N"));
        sb1.append("| --- ML ablation --------------------------------------------------- ");
        sb1.append("| --- Baselines --------------------------------- |");
        System.out.println(sb1);

        StringBuilder sb2 = new StringBuilder();
        sb2.append(String.format(Locale.ROOT, "%-22s  %5s  ", "Charset", ""));
        for (String name : COL_NAMES) {
            sb2.append(String.format(Locale.ROOT, "| %-4s R%%   S%%  T%d%%  D%%   A%%  ", name, TOP_K));
        }
        sb2.append("|");
        System.out.println(sb2);
        System.out.println("-".repeat(sb2.length()));
    }

    private static void printRow(String charset, int n, int[][] counts) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%-22s  %5d  ", charset, n));
        for (int d = 0; d < NUM_DETECTORS; d++) {
            sb.append(String.format(Locale.ROOT, "| %5.1f %5.1f %5.1f %5.1f %5.1f  ",
                    pct(counts[d][0], n), pct(counts[d][1], n), pct(counts[d][2], n),
                    pct(counts[d][3], n), pct(counts[d][4], n)));
        }
        sb.append("|");
        System.out.println(sb);
    }

    private static void printFooter(long totalN, long[][] totals, long[] totalNanos) {
        System.out.println("-".repeat(210));
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%-22s  %5d  ", "OVERALL", totalN));
        for (int d = 0; d < NUM_DETECTORS; d++) {
            sb.append(String.format(Locale.ROOT, "| %5.1f %5.1f %5.1f %5.1f %5.1f  ",
                    pct(totals[d][0], totalN), pct(totals[d][1], totalN),
                    pct(totals[d][2], totalN), pct(totals[d][3], totalN),
                    pct(totals[d][4], totalN)));
        }
        sb.append("|");
        System.out.println(sb);
        System.out.println("  Stat=model only | +ISO=+C1-correction | +CJK=+grammar | "
                + "All=ML+rules | R%=strict | S%=soft | T" + TOP_K + "%=top-" + TOP_K
                + " hit | D%=decode-match | A%=alpha-match");

        // Timing row
        StringBuilder timing = new StringBuilder();
        timing.append(String.format(Locale.ROOT, "%-22s  %5s  ", "  µs/sample", ""));
        for (int d = 0; d < NUM_DETECTORS; d++) {
            double usPerSample = totalN == 0 ? 0.0 : (totalNanos[d] / 1000.0) / totalN;
            timing.append(String.format(Locale.ROOT, "| %26.1f  ", usPerSample));
        }
        timing.append("|");
        System.out.println(timing);
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private static int[] parseLengths(String spec) {
        String[] parts = spec.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = "full".equalsIgnoreCase(parts[i].trim())
                    ? FULL_LENGTH : Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    /** Returns samples truncated to at most {@code maxLen} bytes. */
    private static List<byte[]> truncate(List<byte[]> samples, int maxLen) {
        if (maxLen == FULL_LENGTH) {
            return samples;
        }
        List<byte[]> out = new ArrayList<>(samples.size());
        for (byte[] s : samples) {
            out.add(s.length <= maxLen ? s : Arrays.copyOf(s, maxLen));
        }
        return out;
    }

    private static List<String> predictAll(EncodingDetector detector, byte[] sample) {
        try (TikaInputStream tis = TikaInputStream.get(sample)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            List<String> labels = new ArrayList<>(results.size());
            for (EncodingResult er : results) {
                labels.add(er.getLabel());
            }
            return labels;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean isInTopK(String actual, List<String> predicted, int k) {
        int limit = Math.min(k, predicted.size());
        for (int i = 0; i < limit; i++) {
            if (isStrict(actual, predicted.get(i))
                    || isSoft(actual, predicted.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStrict(String actual, String predicted) {
        return !NULL_LABEL.equals(predicted)
                && normalize(actual).equals(normalize(predicted));
    }

    private static boolean isSoft(String actual, String predicted) {
        if (NULL_LABEL.equals(predicted)) {
            return false;
        }
        if (normalize(actual).equals(normalize(predicted))) {
            return true;
        }
        return CharsetConfusables.isLenientMatch(actual, predicted)
                || CharsetConfusables.isLenientMatch(predicted, actual);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private static boolean isDecodeMatch(String actualName, String predictedName,
                                        byte[] sample) {
        try {
            Charset actual = Charset.forName(actualName);
            Charset predicted = Charset.forName(predictedName);
            return new String(sample, actual).equals(new String(sample, predicted));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isAlphaMatch(String actualName, String predictedName,
                                        byte[] sample) {
        try {
            Charset actual = Charset.forName(actualName);
            Charset predicted = Charset.forName(predictedName);
            return stripNonAlphaNum(new String(sample, actual))
                    .equals(stripNonAlphaNum(new String(sample, predicted)));
        } catch (Exception e) {
            return false;
        }
    }

    private static String stripNonAlphaNum(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static double pct(long correct, long total) {
        return total == 0 ? 0.0 : 100.0 * correct / total;
    }

    private static List<byte[]> loadSamples(Path file) throws IOException {
        List<byte[]> out = new ArrayList<>();
        try (InputStream fis = new FileInputStream(file.toFile());
             GZIPInputStream gis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gis)) {
            while (true) {
                int len;
                try {
                    len = dis.readUnsignedShort();
                } catch (java.io.EOFException e) {
                    break;
                }
                byte[] chunk = new byte[len];
                dis.readFully(chunk);
                out.add(chunk);
            }
        }
        return out;
    }
}
