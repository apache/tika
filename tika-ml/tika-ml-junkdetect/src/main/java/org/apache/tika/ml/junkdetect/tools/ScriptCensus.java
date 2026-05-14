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
import java.util.zip.GZIPInputStream;

/**
 * Codepoint-level script census of one or more text files.  For each input
 * file, reports the percentage of codepoints in each {@link
 * Character.UnicodeScript}, optionally per-line script-mix histograms.
 *
 * <p>Useful to verify whether {@code BuildJunkTrainingData} is bucketing
 * languages correctly: e.g. Japanese is usually a mix of HIRAGANA, KATAKANA
 * and HAN; if {@code jpn} ends up in {@code han.train.gz} we want to know
 * what fraction of its codepoints are actually Han ideographs vs. kana.
 *
 * <p>Usage:
 * <pre>
 *   java ScriptCensus &lt;file&gt; [file ...]   # supports .gz and plain text
 * </pre>
 */
public final class ScriptCensus {

    /** Max lines to sample per file (set high for full pass). */
    private static final int MAX_LINES = 200_000;

    private ScriptCensus() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: ScriptCensus <file> [file ...]");
            System.exit(1);
        }
        for (String arg : args) {
            Path f = Paths.get(arg);
            if (!Files.isRegularFile(f)) {
                System.err.println("Skipping non-file: " + f);
                continue;
            }
            reportOne(f);
            System.out.println();
        }
    }

    private static void reportOne(Path file) throws IOException {
        Map<String, long[]> scriptCounts = new HashMap<>();
        // Per-line dominant-script histogram.
        Map<String, long[]> dominantHistogram = new HashMap<>();
        long total = 0;
        long lines = 0;
        long sampledBytes = 0;

        try (BufferedReader r = open(file)) {
            String line;
            while ((line = r.readLine()) != null && lines < MAX_LINES) {
                lines++;
                sampledBytes += line.length();
                // For MADLAD/Wikipedia files the format is "lineNum TAB text";
                // strip the prefix if present.
                int tab = line.indexOf('\t');
                String text = tab >= 0 ? line.substring(tab + 1) : line;

                Map<String, Long> perLine = new HashMap<>();
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    i += Character.charCount(cp);
                    Character.UnicodeScript s = Character.UnicodeScript.of(cp);
                    if (s == Character.UnicodeScript.COMMON
                            || s == Character.UnicodeScript.INHERITED
                            || s == Character.UnicodeScript.UNKNOWN) {
                        continue;
                    }
                    String name = s.name();
                    scriptCounts.computeIfAbsent(name, k -> new long[1])[0]++;
                    perLine.merge(name, 1L, Long::sum);
                    total++;
                }
                // Identify the dominant script for this line.
                String dom = null;
                long best = -1;
                for (Map.Entry<String, Long> e : perLine.entrySet()) {
                    if (e.getValue() > best) {
                        best = e.getValue();
                        dom = e.getKey();
                    }
                }
                if (dom != null) {
                    dominantHistogram.computeIfAbsent(dom, k -> new long[1])[0]++;
                }
            }
        }

        System.out.printf("File: %s%n", file);
        System.out.printf("  lines sampled: %,d   total codepoints (excl. COMMON/INHERITED): %,d%n%n",
                lines, total);

        if (total == 0) {
            System.out.println("  (empty / no scripted codepoints)");
            return;
        }

        System.out.println("  Codepoint distribution by script:");
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(scriptCounts.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> -e.getValue()[0]));
        long cumulative = 0;
        for (Map.Entry<String, long[]> e : sorted) {
            long c = e.getValue()[0];
            cumulative += c;
            double pct = 100.0 * c / total;
            double cumPct = 100.0 * cumulative / total;
            if (pct < 0.01 && c < 100) continue;
            System.out.printf("    %-22s %,14d  %6.2f%%  (cum %6.2f%%)%n",
                    e.getKey(), c, pct, cumPct);
        }

        System.out.println();
        System.out.println("  Per-line dominant-script histogram:");
        List<Map.Entry<String, long[]>> dom = new ArrayList<>(dominantHistogram.entrySet());
        dom.sort(Comparator.comparingLong((Map.Entry<String, long[]> e) -> -e.getValue()[0]));
        long domTotal = 0;
        for (long[] v : dominantHistogram.values()) domTotal += v[0];
        for (Map.Entry<String, long[]> e : dom) {
            long c = e.getValue()[0];
            double pct = 100.0 * c / domTotal;
            if (pct < 0.05) continue;
            System.out.printf("    %-22s %,12d  %6.2f%% of lines%n",
                    e.getKey(), c, pct);
        }
    }

    private static BufferedReader open(Path path) throws IOException {
        if (path.getFileName().toString().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(path)),
                    StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8);
    }
}
