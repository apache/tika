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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;
import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;

/**
 * Measures corpus diversity for each language in a flat-file corpus directory.
 *
 * <p>Three complementary metrics are computed entirely from the training
 * sentences — no external evaluation set required:
 *
 * <ol>
 *   <li><b>Bigram bucket fill %</b>: fraction of the bigram hash table that
 *       has at least one count after seeing all training sentences.  A corpus
 *       of near-identical stubs reuses the same n-grams over and over and
 *       fills a small fraction of buckets regardless of corpus size.</li>
 *   <li><b>Normalised bigram entropy</b>: Shannon entropy of the bigram count
 *       distribution divided by log2(filled buckets).  A perfectly uniform
 *       distribution scores 1.0; a corpus dominated by a handful of repeated
 *       patterns scores near 0.</li>
 *   <li><b>Unique sentence %</b>: fraction of distinct lines.  Templated
 *       corpora have many near- or exact-duplicate sentences.</li>
 * </ol>
 *
 * <p>Languages whose fill% and entropy fall far below the median are flagged
 * as potentially low-quality.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java CorpusDiversityAnalyzer \
 *       --corpus /path/to/pool_filtered \
 *       [--max-per-lang 100000] \
 *       [--flag-below 0.5]
 * </pre>
 */
public class CorpusDiversityAnalyzer {

    private static final int DEFAULT_MAX_PER_LANG = 100_000;
    private static final double DEFAULT_FLAG_BELOW = 0.5;

    public static void main(String[] args) throws Exception {
        Path   corpus     = null;
        int    maxPerLang = DEFAULT_MAX_PER_LANG;
        double flagBelow  = DEFAULT_FLAG_BELOW;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--corpus":
                    corpus = Paths.get(args[++i]);
                    break;
                case "--max-per-lang":
                    maxPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--flag-below":
                    flagBelow = Double.parseDouble(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }
        if (corpus == null) {
            System.err.println("Usage: CorpusDiversityAnalyzer --corpus <dir> "
                    + "[--max-per-lang N] [--flag-below 0.5]");
            System.exit(1);
        }

        List<Path> langPaths = listRegularFiles(corpus);
        System.out.printf(Locale.US, "Analysing %d languages in %s  "
                + "(max %,d sentences each)%n%n",
                langPaths.size(), corpus, maxPerLang);

        System.out.printf(Locale.US,
                "%-14s  %10s  %10s  %8s  %10s  %10s  %s%n",
                "Language", "Sentences", "Unique%",
                "Fill%", "Entropy", "NormEntropy", "Flag");
        System.out.println("-".repeat(80));

        List<LangStats> stats = new ArrayList<>();
        for (Path p : langPaths) {
            LangStats s = analyze(p, maxPerLang);
            stats.add(s);
        }

        // Sort by normalised entropy ascending (worst first)
        stats.sort((a, b) -> Double.compare(a.normEntropy, b.normEntropy));

        for (LangStats s : stats) {
            String flag = (s.fillPct < flagBelow * 100
                    || s.normEntropy < flagBelow) ? "  <<< LOW DIVERSITY" : "";
            System.out.printf(Locale.US,
                    "%-14s  %,10d  %9.1f%%  %7.1f%%  %9.3f  %11.3f  %s%n",
                    s.lang, s.sentences, s.uniquePct,
                    s.fillPct, s.entropy, s.normEntropy, flag);
        }
    }

    // ---- Analysis ----

    static LangStats analyze(Path langFile, int maxPerLang) throws IOException {
        String lang = langFile.getFileName().toString();

        // Determine CJK by probing first 200 sentences
        boolean cjk = probeCjk(langFile, 200);

        int numBuckets = cjk
                ? GenerativeLanguageModel.CJK_BIGRAM_BUCKETS
                : GenerativeLanguageModel.NONCJK_BIGRAM_BUCKETS;

        long[] bigramCounts = new long[numBuckets];
        Set<String> seen    = new HashSet<>();
        long sentences      = 0;
        long uniqueSentences = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                langFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (text.isEmpty()) continue;

                String pp = CharSoupFeatureExtractor.preprocess(text);
                if (pp.isEmpty()) continue;

                if (seen.add(text)) {
                    uniqueSentences++;
                }
                sentences++;

                if (cjk) {
                    GenerativeLanguageModel.extractCjkNgrams(pp,
                            h -> { /* skip unigrams */ },
                            h -> bigramCounts[h
                                    % GenerativeLanguageModel.CJK_BIGRAM_BUCKETS]++);
                } else {
                    GenerativeLanguageModel.extractNonCjkNgrams(pp,
                            h -> { /* skip unigrams */ },
                            h -> bigramCounts[h
                                    % GenerativeLanguageModel.NONCJK_BIGRAM_BUCKETS]++,
                            h -> { /* skip trigrams */ });
                }

                if (maxPerLang > 0 && sentences >= maxPerLang) {
                    break;
                }
            }
        }

        // Metrics
        long filledBuckets = 0;
        long total         = 0;
        for (long c : bigramCounts) {
            if (c > 0) {
                filledBuckets++;
                total += c;
            }
        }

        double fillPct = 100.0 * filledBuckets / numBuckets;

        // Shannon entropy over filled buckets (bits)
        double entropy = 0.0;
        if (total > 0) {
            for (long c : bigramCounts) {
                if (c > 0) {
                    double p = (double) c / total;
                    entropy -= p * (Math.log(p) / Math.log(2));
                }
            }
        }

        // Normalised entropy: H / log2(filledBuckets)  ∈ [0, 1]
        double normEntropy = filledBuckets > 1
                ? entropy / (Math.log(filledBuckets) / Math.log(2)) : 0.0;

        double uniquePct = sentences > 0
                ? 100.0 * uniqueSentences / sentences : 0.0;

        return new LangStats(lang, sentences, uniquePct,
                fillPct, entropy, normEntropy);
    }

    // ---- Helpers ----

    private static boolean probeCjk(Path file, int maxLines) throws IOException {
        long cjk   = 0;
        long total = 0;
        int  lines  = 0;
        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && lines < maxLines) {
                int i = 0;
                while (i < line.length()) {
                    int cp = line.codePointAt(i);
                    i += Character.charCount(cp);
                    if (Character.isLetter(cp)) {
                        total++;
                        if (ScriptAwareFeatureExtractor.isCjkOrKana(
                                Character.toLowerCase(cp))) {
                            cjk++;
                        }
                    }
                }
                lines++;
            }
        }
        return total > 0 && (double) cjk / total >= 0.60;
    }

    private static List<Path> listRegularFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                dir, Files::isRegularFile)) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        Collections.sort(files);
        return files;
    }

    // ---- Result record ----

    static class LangStats {
        final String lang;
        final long   sentences;
        final double uniquePct;
        final double fillPct;
        final double entropy;
        final double normEntropy;

        LangStats(String lang, long sentences, double uniquePct,
                  double fillPct, double entropy, double normEntropy) {
            this.lang        = lang;
            this.sentences   = sentences;
            this.uniquePct   = uniquePct;
            this.fillPct     = fillPct;
            this.entropy     = entropy;
            this.normEntropy = normEntropy;
        }
    }
}
