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
import java.util.zip.GZIPInputStream;

/**
 * For each {@code *.train.gz} file in a directory, compute per-line statistics
 * of "target-script fraction" — i.e. the fraction of codepoints in each line
 * that belong to the script the file is supposed to represent.
 *
 * <p>Reports a histogram across the buckets
 * [0, 5, 10, 20, 30, 50, 70, 90, 100]% so we can pick a per-script keep
 * threshold (e.g. "drop lines with &lt;20% HAN codepoints").  Also reports
 * what fraction of total bytes / lines would be dropped at each threshold.
 *
 * <p>Each {@code {script}.train.gz} maps to a {@link Character.UnicodeScript};
 * the file basename is uppercased.  Special-case handling routes a few
 * project-internal script names (e.g. HAN includes HALF_FULL ideographic
 * forms) when desired.
 *
 * <p>Usage:
 * <pre>
 *   java LineScriptFractions &lt;dataDir&gt; [thresholds]
 * </pre>
 */
public final class LineScriptFractions {

    private static final int[] BUCKETS = {0, 5, 10, 20, 30, 50, 70, 90, 100};

    private LineScriptFractions() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: LineScriptFractions <dataDir>");
            System.exit(1);
        }
        Path dataDir = Paths.get(args[0]);
        Path[] files;
        try (var s = Files.list(dataDir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".train.gz"))
                    .sorted().toArray(Path[]::new);
        }
        if (files.length == 0) {
            System.err.println("No *.train.gz files in " + dataDir);
            System.exit(1);
        }

        System.out.printf("%-20s %10s %10s | %s%n",
                "script", "lines", "<5%",
                "lines at target-frac threshold (cumulative dropped %)");
        System.out.println("                                            "
                + " <10%   <20%   <30%   <50%   <70%   <90%  <100%");
        System.out.println(repeat('-', 110));

        for (Path file : files) {
            String fname = file.getFileName().toString();
            String name = fname.substring(0, fname.length() - ".train.gz".length())
                    .toUpperCase();
            Character.UnicodeScript target = mapScript(name);
            if (target == null) {
                System.out.printf("%-20s  (no UnicodeScript mapping for '%s')%n", name, name);
                continue;
            }

            long lines = 0;
            long[] bucketCounts = new long[BUCKETS.length];
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                            new GZIPInputStream(Files.newInputStream(file)),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    lines++;
                    int total = 0;
                    int matching = 0;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                        if (s == Character.UnicodeScript.COMMON
                                || s == Character.UnicodeScript.INHERITED
                                || s == Character.UnicodeScript.UNKNOWN) {
                            // Don't count toward denominator: punctuation,
                            // spaces, diacritics are script-neutral.
                            continue;
                        }
                        total++;
                        if (s == target) matching++;
                    }
                    double pct = total == 0 ? 0.0 : 100.0 * matching / total;
                    int b = 0;
                    while (b < BUCKETS.length - 1 && pct >= BUCKETS[b + 1]) b++;
                    bucketCounts[b]++;
                }
            }

            // Convert bucket counts to "cumulative fraction dropped at threshold = BUCKETS[i]".
            StringBuilder sb = new StringBuilder();
            long cum = 0;
            // bucketCounts[i] holds lines with pct in [BUCKETS[i], BUCKETS[i+1]).
            // Drop-if-pct<T means drop all bucketCounts[j] with BUCKETS[j+1] <= T.
            // We report drop-fraction for thresholds 10, 20, 30, 50, 70, 90, 100.
            int[] thresholds = {10, 20, 30, 50, 70, 90, 100};
            for (int t : thresholds) {
                long dropped = 0;
                for (int j = 0; j < BUCKETS.length; j++) {
                    int hi = (j == BUCKETS.length - 1) ? 101 : BUCKETS[j + 1];
                    if (hi <= t) dropped += bucketCounts[j];
                }
                double pct = 100.0 * dropped / Math.max(1, lines);
                sb.append(String.format(" %6.1f", pct));
            }

            long below5 = bucketCounts[0];
            System.out.printf("%-20s %,10d %,10d |%s%n",
                    name.toLowerCase(), lines, below5, sb.toString());
        }
    }

    private static Character.UnicodeScript mapScript(String name) {
        try {
            return Character.UnicodeScript.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String repeat(char c, int n) {
        char[] b = new char[n];
        java.util.Arrays.fill(b, c);
        return new String(b);
    }
}
