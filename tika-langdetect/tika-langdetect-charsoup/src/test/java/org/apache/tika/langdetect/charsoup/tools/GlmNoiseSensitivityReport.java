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
package org.apache.tika.langdetect.charsoup.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Measures GLM sensitivity to synthetic text corruption at multiple text lengths.
 * <p>
 * For each language and text length, randomly extracts runs of codepoints from the
 * concatenated FLORES-200 corpus (not sentence-initial truncation, which biases toward
 * sentence-opening patterns).  Each window is scored clean and under several noise types:
 * <ul>
 *   <li><b>clean</b>      — original run</li>
 *   <li><b>sub10/30/50</b> — X% of codepoints replaced with random same-script chars</li>
 *   <li><b>shuffle</b>    — codepoints randomly permuted (breaks context, preserves distribution)</li>
 *   <li><b>reversed</b>   — codepoint order reversed (directionality; PDFs store RTL visually)</li>
 *   <li><b>wrong-lang</b> — replaced with a run from a different language</li>
 *   <li><b>spc-ins</b>    — spaces injected randomly (PDF over-segmentation)</li>
 *   <li><b>spc-rem</b>    — existing spaces removed (PDF under-segmentation)</li>
 * </ul>
 * Output: one table per text length.  The "sep-*" columns are (clean − noised), showing
 * how much each noise type degrades the z-score — higher separation = better sensitivity.
 * <p>
 * Usage:
 * <pre>
 *   GlmNoiseSensitivityReport [floresDevTsv [samplesPerLang [seed [summaryTsv]]]]
 * </pre>
 */
public class GlmNoiseSensitivityReport {

    /** Text lengths (in codepoints) to evaluate. */
    private static final int[] LENGTHS = {20, 50, 100, 200};

    /** How many random windows to sample per language per length. */
    private static final int DEFAULT_SAMPLES = 500;

    /** Noise levels for the random-substitution sweep. */
    private static final double[] SUBST_RATES = {0.10, 0.30, 0.50};

    /** Rate at which spaces are inserted after non-space codepoints. */
    private static final double SPACE_INSERT_RATE = 0.20;

    /** Rate at which existing space codepoints are dropped. */
    private static final double SPACE_REMOVE_RATE = 0.80;

    // Column indices (must match buildNoiseLabels order)
    private static final Charset WIN1252 = Charset.forName("windows-1252");
    private static final Charset WIN1251 = Charset.forName("windows-1251");

    private static final int COL_CLEAN      = 0;
    private static final int COL_SUB10      = 1;
    private static final int COL_SUB30      = 2;
    private static final int COL_SUB50      = 3;
    private static final int COL_SHUFFLE    = 4;
    private static final int COL_REVERSED   = 5;
    private static final int COL_WRONGLANG  = 6;
    private static final int COL_SPC_INS    = 7;
    private static final int COL_SPC_REM    = 8;
    private static final int COL_MBK_LAT1   = 9;
    private static final int COL_MBK_WIN1252 = 10;
    private static final int COL_MBK_WIN1251 = 11;
    private static final int NUM_COLS        = 12;

    public static void main(String[] args) throws Exception {
        String floresPath = args.length > 0
                ? args[0]
                : System.getProperty("user.home") + "/datasets/flores-200/flores200_dev.tsv";
        int samplesPerLang = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_SAMPLES;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        String tsvPath = args.length > 3 ? args[3] : null;

        GenerativeLanguageModel glm = GenerativeLanguageModel.loadFromClasspath(
                GenerativeLanguageModel.DEFAULT_MODEL_RESOURCE);

        System.out.println("Loading FLORES-200 dev: " + floresPath);
        Map<String, List<String>> byLang = loadFloresByLang(floresPath);
        System.out.printf("Loaded %d languages, %d total sentences%n",
                byLang.size(),
                byLang.values().stream().mapToInt(List::size).sum());
        System.out.printf("Samples per language per length: %d  seed: %d%n%n",
                samplesPerLang, seed);

        // Build per-language codepoint pools (all sentences concatenated with a space)
        Map<String, int[]> langPools = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : byLang.entrySet()) {
            langPools.put(e.getKey(), buildPool(e.getValue()));
        }

        // Build a flat wrong-lang pool per language: concatenation of all OTHER languages
        // (lazily — we pick from a random other-lang pool at sample time)
        List<String> allLangCodes = new ArrayList<>(langPools.keySet());

        String[] noiseLabels = buildNoiseLabels();

        // Accumulate grand means per length for TSV output
        double[][] tsvMeans = new double[LENGTHS.length][];

        for (int li = 0; li < LENGTHS.length; li++) {
            int targetLen = LENGTHS[li];
            System.out.printf("=== Length @%d codepoints ===%n%n", targetLen);

            printHeader(noiseLabels);

            double[] grandSum = new double[NUM_COLS];
            int[]    grandN   = new int[NUM_COLS];
            int      langCount = 0;

            for (String lang : glm.getLanguages()) {
                int[] pool = langPools.get(lang);
                if (pool == null || pool.length < targetLen) {
                    continue;
                }

                // Pick a different language's pool for wrong-lang substitution
                int[] wrongPool = pickWrongLangPool(lang, allLangCodes, langPools,
                        new Random(seed), targetLen);

                double[] sums = new double[NUM_COLS];
                int[]    ns   = new int[NUM_COLS];
                Random rng = new Random(seed ^ lang.hashCode());

                for (int s = 0; s < samplesPerLang; s++) {
                    // Randomly extract a window of targetLen codepoints from the pool
                    int[] window = randomWindow(pool, targetLen, rng);

                    // clean
                    addScore(glm, lang, fromCodepoints(window), sums, ns, COL_CLEAN);

                    // random substitutions
                    addScore(glm, lang, substituteRandom(window, 0.10, pool, rng), sums, ns, COL_SUB10);
                    addScore(glm, lang, substituteRandom(window, 0.30, pool, rng), sums, ns, COL_SUB30);
                    addScore(glm, lang, substituteRandom(window, 0.50, pool, rng), sums, ns, COL_SUB50);

                    // shuffle
                    addScore(glm, lang, shuffleCodepoints(window, rng), sums, ns, COL_SHUFFLE);

                    // reversed
                    addScore(glm, lang, reverseCodepoints(window), sums, ns, COL_REVERSED);

                    // wrong-lang: random window from a different language's pool
                    if (wrongPool != null) {
                        int[] wrongWindow = randomWindow(wrongPool, targetLen, rng);
                        addScore(glm, lang, fromCodepoints(wrongWindow), sums, ns, COL_WRONGLANG);
                    }

                    // space insertion
                    addScore(glm, lang, insertSpaces(window, rng), sums, ns, COL_SPC_INS);

                    // space removal
                    addScore(glm, lang, removeSpaces(window, rng), sums, ns, COL_SPC_REM);

                    // mojibake: UTF-8 bytes misread as Latin-1 / Win-1252 / Win-1251
                    // Only score if the mis-decoded string actually differs (ASCII-only windows
                    // are unchanged and should not inflate the mean).
                    String clean = fromCodepoints(window);
                    addMojibakeScore(glm, lang, clean, StandardCharsets.ISO_8859_1, sums, ns, COL_MBK_LAT1);
                    addMojibakeScore(glm, lang, clean, WIN1252,                     sums, ns, COL_MBK_WIN1252);
                    addMojibakeScore(glm, lang, clean, WIN1251,                     sums, ns, COL_MBK_WIN1251);
                }

                double[] means = computeMeans(sums, ns);
                printLangRow(lang, samplesPerLang, means);

                for (int i = 0; i < NUM_COLS; i++) {
                    grandSum[i] += sums[i];
                    grandN[i]   += ns[i];
                }
                langCount++;
            }

            double[] grandMeans = computeMeans(grandSum, grandN);
            printSeparator(noiseLabels);
            printLangRow("MEAN(" + langCount + ")", -1, grandMeans);
            System.out.println();
            tsvMeans[li] = grandMeans;
        }

        System.out.println("Columns: " + Arrays.toString(noiseLabels));
        System.out.println("sep-sub10  = clean − sub10 z-score  (char noise sensitivity)");
        System.out.println("sep-rev    = clean − reversed       (directionality)");
        System.out.println("sep-spc+   = clean − spc-ins        (over-segmentation sensitivity)");
        System.out.println("sep-spc-   = clean − spc-rem        (under-segmentation sensitivity)");
        System.out.println("mbk-lat1   = UTF-8 misread as ISO-8859-1 (only non-ASCII windows counted)");
        System.out.println("mbk-1252   = UTF-8 misread as Windows-1252");
        System.out.println("mbk-1251   = UTF-8 misread as Windows-1251 (Cyrillic)");

        if (tsvPath != null) {
            writeSummaryTsv(tsvPath, noiseLabels, tsvMeans);
            System.out.println("\nSummary TSV written to: " + tsvPath);
        }
    }

    // ---- sampling ----

    /** Extract a random window of {@code len} codepoints from {@code pool}. */
    private static int[] randomWindow(int[] pool, int len, Random rng) {
        int start = rng.nextInt(pool.length - len + 1);
        return Arrays.copyOfRange(pool, start, start + len);
    }

    private static int[] pickWrongLangPool(String lang, List<String> allCodes,
                                            Map<String, int[]> pools,
                                            Random rng, int minLen) {
        // Try up to 20 random other languages with a large enough pool
        for (int attempt = 0; attempt < 20; attempt++) {
            String other = allCodes.get(rng.nextInt(allCodes.size()));
            if (!other.equals(lang)) {
                int[] p = pools.get(other);
                if (p != null && p.length >= minLen) return p;
            }
        }
        return null;
    }

    // ---- noise functions ----

    private static String substituteRandom(int[] cps, double rate, int[] pool, Random rng) {
        int[] out = cps.clone();
        for (int i = 0; i < out.length; i++) {
            if (rng.nextDouble() < rate && pool.length > 0) {
                out[i] = pool[rng.nextInt(pool.length)];
            }
        }
        return fromCodepoints(out);
    }

    private static String shuffleCodepoints(int[] cps, Random rng) {
        int[] out = cps.clone();
        for (int i = out.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = out[i]; out[i] = out[j]; out[j] = tmp;
        }
        return fromCodepoints(out);
    }

    private static String reverseCodepoints(int[] cps) {
        int[] out = new int[cps.length];
        for (int i = 0; i < cps.length; i++) out[i] = cps[cps.length - 1 - i];
        return fromCodepoints(out);
    }

    private static String insertSpaces(int[] cps, Random rng) {
        StringBuilder sb = new StringBuilder(cps.length * 2);
        for (int cp : cps) {
            sb.appendCodePoint(cp);
            if (cp != ' ' && rng.nextDouble() < SPACE_INSERT_RATE) sb.append(' ');
        }
        return sb.toString();
    }

    private static String removeSpaces(int[] cps, Random rng) {
        StringBuilder sb = new StringBuilder(cps.length);
        for (int cp : cps) {
            if (cp == ' ' && rng.nextDouble() < SPACE_REMOVE_RATE) continue;
            sb.appendCodePoint(cp);
        }
        return sb.toString();
    }

    // ---- output ----

    private static String[] buildNoiseLabels() {
        List<String> labels = new ArrayList<>();
        labels.add("clean");
        for (double r : SUBST_RATES) labels.add(String.format("sub%d%%", (int)(r * 100)));
        labels.add("shuffle");
        labels.add("reversed");
        labels.add("wrng-lng");
        labels.add("spc-ins");
        labels.add("spc-rem");
        labels.add("mbk-lat1");
        labels.add("mbk-1252");
        labels.add("mbk-1251");
        return labels.toArray(new String[0]);
    }

    private static void printHeader(String[] noiseLabels) {
        System.out.printf("%-14s", "lang");
        for (String label : noiseLabels) System.out.printf("  %8s", label);
        System.out.printf("  %8s  %8s  %8s  %8s%n", "sep-sub", "sep-rev", "sep-spc+", "sep-spc-");
        printSeparator(noiseLabels);
    }

    private static void printSeparator(String[] noiseLabels) {
        System.out.println("-".repeat(14 + noiseLabels.length * 10 + 38));
    }

    private static void printLangRow(String lang, int n, double[] means) {
        System.out.printf("%-14s", n >= 0 ? String.format("%s(%d)", lang, n) : lang);
        for (double m : means) System.out.printf("  %8.3f", m);
        double sepSub = means[COL_CLEAN] - means[COL_SUB10];
        double sepRev = means[COL_CLEAN] - means[COL_REVERSED];
        double sepSpcIns = means[COL_CLEAN] - means[COL_SPC_INS];
        double sepSpcRem = means[COL_CLEAN] - means[COL_SPC_REM];
        System.out.printf("  %8.3f  %8.3f  %8.3f  %8.3f%n", sepSub, sepRev, sepSpcIns, sepSpcRem);
    }

    // ---- TSV output ----

    /**
     * Write a compact TSV of grand-mean z-scores per length.
     * One header row, then one data row per length.
     * Separation columns (clean − noised) are appended for the key noise types.
     * <p>
     * Compare two runs with e.g.:
     * <pre>
     *   python3 -c "
     *   import csv, sys
     *   a = {r['length']: r for r in csv.DictReader(open(sys.argv[1]), delimiter='\t')}
     *   b = {r['length']: r for r in csv.DictReader(open(sys.argv[2]), delimiter='\t')}
     *   for ln in a:
     *       for k in a[ln]:
     *           if k != 'length':
     *               delta = float(b[ln][k]) - float(a[ln][k])
     *               if abs(delta) > 0.01: print(f'{ln}\t{k}\t{delta:+.3f}')
     *   " baseline.tsv new.tsv
     * </pre>
     */
    private static void writeSummaryTsv(String path, String[] noiseLabels,
                                         double[][] means) throws IOException {
        try (PrintWriter pw = new PrintWriter(path, StandardCharsets.UTF_8)) {
            // Header
            pw.print("length");
            for (String label : noiseLabels) {
                pw.print('\t');
                pw.print(label);
            }
            pw.print("\tsep-sub10\tsep-rev\tsep-spc+\tsep-spc-");
            pw.println();

            // One row per length
            for (int i = 0; i < LENGTHS.length; i++) {
                pw.print(LENGTHS[i]);
                double[] m = means[i];
                for (double v : m) {
                    pw.print('\t');
                    if (Double.isNaN(v)) pw.print("NaN");
                    else pw.printf("%.4f", v);
                }
                // Separation columns
                pw.printf("\t%.4f", m[COL_CLEAN] - m[COL_SUB10]);
                pw.printf("\t%.4f", m[COL_CLEAN] - m[COL_REVERSED]);
                pw.printf("\t%.4f", m[COL_CLEAN] - m[COL_SPC_INS]);
                pw.printf("\t%.4f", m[COL_CLEAN] - m[COL_SPC_REM]);
                pw.println();
            }
        }
    }

    // ---- helpers ----

    private static void addScore(GenerativeLanguageModel glm, String lang,
                                  String text, double[] sums, int[] ns, int col) {
        float z = glm.zScoreLengthAdjusted(text, lang);
        if (!Float.isNaN(z)) {
            sums[col] += z;
            ns[col]++;
        }
    }

    /**
     * Simulate charset misidentification: encode {@code clean} as UTF-8, then
     * decode those bytes as {@code wrongCharset}.  Only scores the result if it
     * actually differs from the original — pure-ASCII windows are unaffected by
     * this type of error and should not count toward the mean.
     */
    private static void addMojibakeScore(GenerativeLanguageModel glm, String lang,
                                          String clean, Charset wrongCharset,
                                          double[] sums, int[] ns, int col) {
        String mojibake = new String(clean.getBytes(StandardCharsets.UTF_8), wrongCharset);
        if (mojibake.equals(clean)) {
            return; // no non-ASCII content affected — skip this sample
        }
        float z = glm.zScoreLengthAdjusted(mojibake, lang);
        if (!Float.isNaN(z)) {
            sums[col] += z;
            ns[col]++;
        }
    }

    private static double[] computeMeans(double[] sums, int[] ns) {
        double[] means = new double[sums.length];
        for (int i = 0; i < sums.length; i++) {
            means[i] = ns[i] > 0 ? sums[i] / ns[i] : Double.NaN;
        }
        return means;
    }

    /**
     * Concatenate all sentences for a language into a single codepoint array,
     * joining with a space so sentence boundaries are natural.
     */
    private static int[] buildPool(List<String> sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(sentences.get(i));
        }
        return sb.toString().codePoints().toArray();
    }

    private static int[] toCodepoints(String s) {
        return s.codePoints().toArray();
    }

    private static String fromCodepoints(int[] cps) {
        StringBuilder sb = new StringBuilder(cps.length);
        for (int cp : cps) sb.appendCodePoint(cp);
        return sb.toString();
    }

    private static Map<String, List<String>> loadFloresByLang(String path) throws IOException {
        Map<String, List<String>> map = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 2);
                if (parts.length < 2) continue;
                String lang = parts[0].split("_")[0];
                map.computeIfAbsent(lang, k -> new ArrayList<>()).add(parts[1]);
            }
        }
        return map;
    }
}
