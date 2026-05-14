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
 * Diagnostic tool: bucket every bigram in {@code han.train.gz} (or any
 * specified file) by the {@link Character.UnicodeBlock} of each codepoint,
 * and report the distribution.
 *
 * <p>Goal: determine whether HAN's 224K distinct pairs split cleanly along
 * block boundaries — e.g. CJK Unified Ideographs vs. Hiragana vs. Katakana —
 * which would justify routing HAN windows to language-specific sub-models in
 * the v7 design.
 *
 * <p>Usage:
 * <pre>
 *   java ... AnalyzeHanByBlock /path/to/junkdetect/han.train.gz
 * </pre>
 */
public final class AnalyzeHanByBlock {

    private AnalyzeHanByBlock() {}

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: AnalyzeHanByBlock <train.gz>");
            System.exit(1);
        }
        Path file = Paths.get(args[0]);

        // (blockA, blockB) -> [totalBigrams, distinctSet via HashMap<Long, [count]>]
        // We use Maps of Maps to keep code simple; HAN is the only file
        // big enough to matter and fits in heap.
        Map<String, Map<Long, long[]>> byBlockPair = new HashMap<>();
        Map<String, long[]> blockPairTotals = new HashMap<>();
        long totalN = 0;

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file)),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int prevCp = -1;
                String prevBlock = null;
                for (int i = 0; i < line.length(); ) {
                    int cp = line.codePointAt(i);
                    i += Character.charCount(cp);
                    String block = blockShortName(cp);
                    if (prevCp >= 0) {
                        String key = prevBlock + "|" + block;
                        Map<Long, long[]> set = byBlockPair.computeIfAbsent(
                                key, k -> new HashMap<>(256));
                        long packed = ((long) prevCp << 24) | (cp & 0xFFFFFFL);
                        long[] c = set.get(packed);
                        if (c == null) {
                            set.put(packed, new long[]{1L});
                        } else {
                            c[0]++;
                        }
                        blockPairTotals.computeIfAbsent(key, k -> new long[1])[0]++;
                        totalN++;
                    }
                    prevCp = cp;
                    prevBlock = block;
                }
            }
        }

        System.out.printf("File: %s%n", file);
        System.out.printf("Total bigram occurrences: %,d%n%n", totalN);

        // Sort block-pair keys by total occurrences (descending).
        List<Map.Entry<String, long[]>> sorted = new ArrayList<>(blockPairTotals.entrySet());
        sorted.sort(Comparator.comparingLong(
                (Map.Entry<String, long[]> e) -> -e.getValue()[0]));

        System.out.printf("%-50s %14s %14s %12s %8s%n",
                "block_pair", "occurrences", "distinct", "singletons", "%total");
        System.out.println(repeat('-', 105));

        long distinctTotal = 0;
        long singletonsTotal = 0;
        for (Map.Entry<String, long[]> e : sorted) {
            String pair = e.getKey();
            long n = e.getValue()[0];
            Map<Long, long[]> set = byBlockPair.get(pair);
            int distinct = set.size();
            int singletons = 0;
            for (long[] c : set.values()) {
                if (c[0] == 1) singletons++;
            }
            distinctTotal += distinct;
            singletonsTotal += singletons;
            double pct = 100.0 * n / totalN;
            if (pct < 0.1 && n < 1000) {
                continue; // skip tail noise rows
            }
            System.out.printf("%-50s %,14d %,14d %,12d %7.2f%%%n",
                    pair, n, distinct, singletons, pct);
        }
        System.out.println(repeat('-', 105));
        System.out.printf("Total distinct pairs (incl. tail): %,d%n", distinctTotal);
        System.out.printf("Total singletons (incl. tail):     %,d%n", singletonsTotal);

        // Roll up by individual block (left side only) to see per-block distinct counts.
        System.out.println();
        System.out.println("=== Per-leading-block roll-up ===");
        Map<String, Long> distinctByLeadingBlock = new HashMap<>();
        Map<String, Long> occByLeadingBlock = new HashMap<>();
        for (Map.Entry<String, Map<Long, long[]>> e : byBlockPair.entrySet()) {
            String leading = e.getKey().substring(0, e.getKey().indexOf('|'));
            distinctByLeadingBlock.merge(leading, (long) e.getValue().size(), Long::sum);
            long sum = 0;
            for (long[] c : e.getValue().values()) sum += c[0];
            occByLeadingBlock.merge(leading, sum, Long::sum);
        }
        List<Map.Entry<String, Long>> rollup = new ArrayList<>(occByLeadingBlock.entrySet());
        rollup.sort(Comparator.comparingLong(
                (Map.Entry<String, Long> e) -> -e.getValue()));
        System.out.printf("%-35s %14s %14s%n",
                "leading_block", "occurrences", "distinct(rough)");
        System.out.println(repeat('-', 70));
        for (Map.Entry<String, Long> e : rollup) {
            System.out.printf("%-35s %,14d %,14d%n",
                    e.getKey(), e.getValue(),
                    distinctByLeadingBlock.get(e.getKey()));
        }
    }

    /**
     * Short-name for the Unicode block containing {@code cp}.  Compresses the
     * many CJK-related blocks into a handful of human-readable labels.
     *
     * <p>Splits ASCII into ASCII_DIGIT / ASCII_LETTER / ASCII_PUNCT so we can
     * distinguish numerals (which are content-bearing across all scripts) from
     * English-letter contamination and punctuation.
     */
    private static String blockShortName(int cp) {
        Character.UnicodeBlock b = Character.UnicodeBlock.of(cp);
        if (b == null) return "UNK";

        String name = b.toString();
        if (name.equals("BASIC_LATIN")) {
            if (cp >= '0' && cp <= '9') return "ASCII_DIGIT";
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) return "ASCII_LETTER";
            return "ASCII_PUNCT";
        }
        // Compress noisy block names for the report.
        if (name.startsWith("CJK_UNIFIED_IDEOGRAPHS_EXTENSION")) {
            return "CJK_EXT_" + name.substring(name.lastIndexOf('_') + 1);
        }
        if (name.equals("CJK_UNIFIED_IDEOGRAPHS")) return "CJK_UNIFIED";
        if (name.equals("CJK_SYMBOLS_AND_PUNCTUATION")) return "CJK_PUNCT";
        if (name.equals("CJK_COMPATIBILITY_IDEOGRAPHS")) return "CJK_COMPAT";
        if (name.equals("CJK_COMPATIBILITY_FORMS")) return "CJK_COMPAT_FORMS";
        if (name.equals("HALFWIDTH_AND_FULLWIDTH_FORMS")) return "HALF_FULL";
        if (name.equals("HIRAGANA")) return "HIRAGANA";
        if (name.equals("KATAKANA")) return "KATAKANA";
        if (name.equals("KATAKANA_PHONETIC_EXTENSIONS")) return "KATAKANA_EXT";
        if (name.equals("HANGUL_SYLLABLES")) return "HANGUL";
        if (name.equals("HANGUL_JAMO")) return "HANGUL_JAMO";
        if (name.equals("HANGUL_COMPATIBILITY_JAMO")) return "HANGUL_JAMO_C";
        if (name.equals("LATIN_1_SUPPLEMENT")) return "LATIN1";
        return name;
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }
}
