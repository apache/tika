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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Diagnostic tool for sizing a per-script F1 bigram store (v7 design).
 *
 * <p>Walks every {@code *.train.gz} in {@code dataDir}, treating each file as
 * one script's corpus.  Counts (cpA, cpB) codepoint-pair frequencies and
 * reports, per script:
 *
 * <ul>
 *   <li>total bigram occurrences (N)
 *   <li>distinct pair count (U)
 *   <li>singletons — pairs seen exactly once (these are usually the
 *       worst candidates to keep; they often reflect OCR noise / rare
 *       proper nouns and inflate U without helping discrimination)
 *   <li>"effective" pair count = pairs seen at least {@code MIN_COUNT} times
 *   <li>coverage curve: how many of the top-N most-frequent pairs are needed
 *       to cover {x = 50, 75, 90, 95, 99, 99.9}% of all bigram occurrences
 *   <li>estimated v7 model size for several candidate cutoffs, assuming
 *       2.25 bytes/pair (MPHF + 8-bit fingerprint + 8-bit value)
 *       and 1.3 bytes/pair (MPHF + 8-bit value, no fingerprint)
 * </ul>
 *
 * <p>Usage:
 * <pre>
 *   mvn -pl tika-ml/tika-ml-junkdetect exec:java \
 *       -Dexec.mainClass=org.apache.tika.ml.junkdetect.tools.CountPerScriptBigrams \
 *       -Dexec.args="/path/to/junkdetect"
 * </pre>
 *
 * <p>No model output; this is read-only telemetry to inform the v7 sizing
 * decision (see {@code 20260514-junk-retrain-v6.md}).
 */
public final class CountPerScriptBigrams {

    private static final int[] COVERAGE_PCT = {50, 75, 90, 95, 99};
    private static final double[] COVERAGE_FRAC_HI = {0.999};

    /** Cutoffs reported in the size-estimate table. */
    private static final int[] MIN_COUNT_CUTOFFS = {1, 2, 3, 5, 10};

    /** Bytes per retained pair for each candidate storage scheme. */
    private static final double[] BYTES_PER_PAIR_SCHEMES = {1.3, 2.25, 6.25};
    private static final String[] SCHEME_NAMES = {
            "MPHF+val(1.3B)", "MPHF+fp+val(2.25B)", "open-addr+key(6.25B)"};

    private CountPerScriptBigrams() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "Usage: CountPerScriptBigrams <dataDir> [topK-per-script]");
            System.exit(1);
        }
        Path dataDir = Paths.get(args[0]);
        int topK = args.length >= 2 ? Integer.parseInt(args[1]) : 0;

        List<Path> trainFiles = new ArrayList<>();
        try (Stream<Path> s = Files.list(dataDir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".train.gz"))
             .sorted()
             .forEach(trainFiles::add);
        }
        if (trainFiles.isEmpty()) {
            System.err.println("ERROR: no *.train.gz files in " + dataDir);
            System.exit(1);
        }

        System.out.printf("Found %d *.train.gz files in %s%n%n",
                trainFiles.size(), dataDir);
        System.out.printf(
                "%-22s %12s %12s %12s %12s | %s%n",
                "script", "total_N", "distinct_U", "singletons",
                "U(>=10)", "coverage: pairs needed for [50,75,90,95,99,99.9]%");
        System.out.println(repeat('-', 140));

        long grandTotalN = 0;
        long grandTotalU = 0;
        long grandTotalUge2 = 0;
        long grandTotalUge10 = 0;

        // Per-script size accumulators for the global-size summary at the end.
        Map<String, long[]> perScriptStats = new HashMap<>();

        for (Path trainFile : trainFiles) {
            String fname = trainFile.getFileName().toString();
            String script = fname.substring(0, fname.length() - ".train.gz".length())
                    .toUpperCase();

            HashMap<Long, long[]> pairCounts = new HashMap<>(1 << 16);
            long totalN = 0;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                            new GZIPInputStream(Files.newInputStream(trainFile)),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int prevCp = -1;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        if (prevCp >= 0) {
                            long key = packPair(prevCp, cp);
                            long[] c = pairCounts.get(key);
                            if (c == null) {
                                pairCounts.put(key, new long[]{1L});
                            } else {
                                c[0]++;
                            }
                            totalN++;
                        }
                        prevCp = cp;
                    }
                }
            }

            int distinctU = pairCounts.size();

            long[] counts = new long[distinctU];
            int idx = 0;
            for (long[] c : pairCounts.values()) {
                counts[idx++] = c[0];
            }
            // Sort descending for coverage curve.
            java.util.Arrays.sort(counts);
            // Reverse in place.
            for (int i = 0, j = counts.length - 1; i < j; i++, j--) {
                long t = counts[i];
                counts[i] = counts[j];
                counts[j] = t;
            }

            int singletons = 0;
            int uGe2 = 0;
            int uGe10 = 0;
            for (long c : counts) {
                if (c == 1) singletons++;
                if (c >= 2) uGe2++;
                if (c >= 10) uGe10++;
            }

            // Coverage thresholds: minimum k such that sum(counts[0..k-1]) / N >= t.
            int[] coveragePairs = new int[COVERAGE_PCT.length + COVERAGE_FRAC_HI.length];
            double[] thresholds = new double[coveragePairs.length];
            for (int i = 0; i < COVERAGE_PCT.length; i++) {
                thresholds[i] = COVERAGE_PCT[i] / 100.0;
            }
            for (int i = 0; i < COVERAGE_FRAC_HI.length; i++) {
                thresholds[COVERAGE_PCT.length + i] = COVERAGE_FRAC_HI[i];
            }
            long running = 0;
            int tIdx = 0;
            for (int k = 0; k < counts.length && tIdx < thresholds.length; k++) {
                running += counts[k];
                while (tIdx < thresholds.length
                        && (double) running / totalN >= thresholds[tIdx]) {
                    coveragePairs[tIdx++] = k + 1;
                }
            }
            // Fill any unreached thresholds with U (means: never reached, took all).
            for (; tIdx < thresholds.length; tIdx++) {
                coveragePairs[tIdx] = distinctU;
            }

            StringBuilder cov = new StringBuilder();
            for (int i = 0; i < coveragePairs.length; i++) {
                if (i > 0) cov.append(", ");
                cov.append(String.format("%,d", coveragePairs[i]));
            }

            System.out.printf("%-22s %,12d %,12d %,12d %,12d | %s%n",
                    script.toLowerCase(),
                    totalN, distinctU, singletons, uGe10,
                    cov.toString());

            // Per-script size table.
            if (topK > 0 || true) {
                long[] sizeStats = new long[
                        2 + MIN_COUNT_CUTOFFS.length + BYTES_PER_PAIR_SCHEMES.length];
                sizeStats[0] = totalN;
                sizeStats[1] = distinctU;
                for (int i = 0; i < MIN_COUNT_CUTOFFS.length; i++) {
                    int minC = MIN_COUNT_CUTOFFS[i];
                    int kept = 0;
                    for (long c : counts) {
                        if (c >= minC) kept++;
                        else break;
                    }
                    sizeStats[2 + i] = kept;
                }
                perScriptStats.put(script.toLowerCase(), sizeStats);
            }

            // Per-script top-K dump if requested.
            if (topK > 0) {
                System.out.printf("    top %d pairs in %s:%n", topK, script.toLowerCase());
                List<Map.Entry<Long, long[]>> sorted = new ArrayList<>(pairCounts.entrySet());
                sorted.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
                for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
                    Map.Entry<Long, long[]> e = sorted.get(i);
                    long k = e.getKey();
                    int cpA = (int) (k >>> 24);
                    int cpB = (int) (k & 0xFFFFFFL);
                    System.out.printf("      U+%04X U+%04X  (%c %c)  %,d%n",
                            cpA, cpB,
                            safePrint(cpA), safePrint(cpB),
                            e.getValue()[0]);
                }
            }

            grandTotalN += totalN;
            grandTotalU += distinctU;
            grandTotalUge2 += uGe2;
            grandTotalUge10 += uGe10;
        }

        System.out.println(repeat('-', 140));
        System.out.printf("%-22s %,12d %,12d %12s %,12d%n%n",
                "TOTAL", grandTotalN, grandTotalU,
                "-", grandTotalUge10);

        // ------------------------------------------------------------------
        // Cutoff vs. model-size summary
        // ------------------------------------------------------------------
        System.out.println("=== Model-size estimates by min-count cutoff and storage scheme ===");
        System.out.println("(sum of retained pairs across all scripts × bytes-per-pair)");
        System.out.println();
        System.out.printf("%-12s", "cutoff");
        for (String name : SCHEME_NAMES) {
            System.out.printf(" %20s", name);
        }
        System.out.printf(" %20s%n", "retained_pairs");
        System.out.println(repeat('-', 12 + (SCHEME_NAMES.length + 1) * 21));

        for (int i = 0; i < MIN_COUNT_CUTOFFS.length; i++) {
            long retained = 0;
            for (long[] stats : perScriptStats.values()) {
                retained += stats[2 + i];
            }
            System.out.printf("min_count>=%-2d", MIN_COUNT_CUTOFFS[i]);
            for (double bpp : BYTES_PER_PAIR_SCHEMES) {
                double bytes = retained * bpp;
                System.out.printf(" %18s   ", humanBytes(bytes));
            }
            System.out.printf(" %,20d%n", retained);
        }

        System.out.println();
        System.out.println("Per-script pair counts retained at each cutoff:");
        System.out.printf("%-22s", "script");
        for (int c : MIN_COUNT_CUTOFFS) {
            System.out.printf(" %12s", ">=" + c);
        }
        System.out.println();
        List<Map.Entry<String, long[]>> sortedScripts =
                new ArrayList<>(perScriptStats.entrySet());
        sortedScripts.sort(Comparator.comparingLong(
                (Map.Entry<String, long[]> e) -> -e.getValue()[1]));
        for (Map.Entry<String, long[]> e : sortedScripts) {
            System.out.printf("%-22s", e.getKey());
            for (int i = 0; i < MIN_COUNT_CUTOFFS.length; i++) {
                System.out.printf(" %,12d", e.getValue()[2 + i]);
            }
            System.out.println();
        }
    }

    /** Pack two codepoints (each up to 21 bits) into a single long. */
    private static long packPair(int cpA, int cpB) {
        return ((long) cpA << 24) | (cpB & 0xFFFFFFL);
    }

    private static char safePrint(int cp) {
        if (cp < 0x20 || cp == 0x7F || !Character.isDefined(cp)) {
            return '.';
        }
        if (Character.charCount(cp) != 1) {
            return '?';
        }
        return (char) cp;
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }

    private static String humanBytes(double bytes) {
        if (bytes < 1024) return String.format("%.0f B", bytes);
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
