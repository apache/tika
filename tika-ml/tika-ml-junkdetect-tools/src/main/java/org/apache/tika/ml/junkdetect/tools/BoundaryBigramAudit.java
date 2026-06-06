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
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * For each {@code *.train.gz} file, classify every adjacent codepoint pair
 * by its relation to the target script S (= file's script).  Categories:
 *
 * <ul>
 *   <li>IN_S_INTERIOR — both codepoints are in S or in COMMON/INHERITED
 *   <li>S_BOUNDARY    — exactly one codepoint is in S-or-COMMON, the other
 *       is a non-S script
 *   <li>FOREIGN_INTERIOR — both codepoints are in some non-S script
 *       (possibly different scripts).  Under the proposed generalized
 *       boundary rule, these are the bigrams to drop from S's training.
 *   <li>ASCII_LETTER_RUN — special subcategory of foreign interior where
 *       both cps are ASCII A–Z/a–z; this is the English-run case.
 * </ul>
 *
 * <p>Reports occurrence counts, distinct-pair counts, and singleton counts
 * for each category, plus the implied model-size impact of dropping
 * FOREIGN_INTERIOR (or just ASCII_LETTER_RUN) under {@code min_count>=1}
 * and {@code min_count>=3}.
 */
public final class BoundaryBigramAudit {

    private BoundaryBigramAudit() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: BoundaryBigramAudit <dataDir>");
            System.exit(1);
        }
        Path dataDir = Paths.get(args[0]);
        Path[] files;
        try (Stream<Path> s = Files.list(dataDir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".train.gz"))
                    .sorted().toArray(Path[]::new);
        }

        System.out.printf("%-22s %14s %14s %14s %14s %12s | %14s %14s%n",
                "script", "in_S_occ", "boundary_occ", "foreign_occ",
                "ascii_run_occ", "total_occ",
                "drop_foreign_dist", "drop_asciirun_dist");
        System.out.println(repeat('-', 165));

        for (Path file : files) {
            String fname = file.getFileName().toString();
            String name = fname.substring(0, fname.length() - ".train.gz".length())
                    .toUpperCase();
            Character.UnicodeScript target;
            try {
                target = Character.UnicodeScript.valueOf(name);
            } catch (IllegalArgumentException e) {
                continue;
            }

            long inS = 0, boundary = 0, foreign = 0, asciiRun = 0;
            HashMap<Long, long[]> distinctAll = new HashMap<>(1 << 16);
            HashMap<Long, long[]> distinctKeptUnderForeignDrop = new HashMap<>(1 << 16);
            HashMap<Long, long[]> distinctKeptUnderAsciiDrop = new HashMap<>(1 << 16);

            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(
                            new GZIPInputStream(Files.newInputStream(file)),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int prevCp = -1;
                    for (int i = 0; i < line.length(); ) {
                        int cp = line.codePointAt(i);
                        i += Character.charCount(cp);
                        if (prevCp >= 0) {
                            boolean aInS = inScriptOrCommon(prevCp, target);
                            boolean bInS = inScriptOrCommon(cp, target);
                            boolean aLetter = isLatinLetter(prevCp);
                            boolean bLetter = isLatinLetter(cp);

                            long packed = ((long) prevCp << 24) | (cp & 0xFFFFFFL);
                            increment(distinctAll, packed);

                            if (aInS && bInS) {
                                inS++;
                                increment(distinctKeptUnderForeignDrop, packed);
                                increment(distinctKeptUnderAsciiDrop, packed);
                            } else if (aInS != bInS) {
                                boundary++;
                                increment(distinctKeptUnderForeignDrop, packed);
                                increment(distinctKeptUnderAsciiDrop, packed);
                            } else {
                                // both foreign (neither in S nor COMMON)
                                foreign++;
                                if (aLetter && bLetter) {
                                    asciiRun++;
                                } else {
                                    // foreign interior but not pure ASCII letters:
                                    // we'd keep this under the "ASCII-letter only" rule.
                                    increment(distinctKeptUnderAsciiDrop, packed);
                                }
                            }
                        }
                        prevCp = cp;
                    }
                }
            }

            long total = inS + boundary + foreign;
            int distAll = distinctAll.size();
            int distForeignDrop = distinctKeptUnderForeignDrop.size();
            int distAsciiDrop = distinctKeptUnderAsciiDrop.size();

            System.out.printf("%-22s %,14d %,14d %,14d %,14d %,12d | %,14d %,14d%n",
                    name.toLowerCase(), inS, boundary, foreign, asciiRun, total,
                    distAll - distForeignDrop, distAll - distAsciiDrop);
        }
    }

    private static boolean inScriptOrCommon(int cp, Character.UnicodeScript target) {
        Character.UnicodeScript s = Character.UnicodeScript.of(cp);
        return s == target
                || s == Character.UnicodeScript.COMMON
                || s == Character.UnicodeScript.INHERITED;
    }

    private static boolean isLatinLetter(int cp) {
        return (cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')
                || (cp >= 0xFF21 && cp <= 0xFF3A) // fullwidth A-Z
                || (cp >= 0xFF41 && cp <= 0xFF5A); // fullwidth a-z
    }

    private static void increment(HashMap<Long, long[]> map, long key) {
        long[] c = map.get(key);
        if (c == null) {
            map.put(key, new long[]{1L});
        } else {
            c[0]++;
        }
    }

    private static String repeat(char c, int n) {
        char[] b = new char[n];
        java.util.Arrays.fill(b, c);
        return new String(b);
    }
}
