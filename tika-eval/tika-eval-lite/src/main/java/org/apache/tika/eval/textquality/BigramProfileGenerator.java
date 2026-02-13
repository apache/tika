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
package org.apache.tika.eval.textquality;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates character bigram profile files from tika-eval-core's
 * common_tokens data. Each profile contains the top-N bigrams with
 * log2-probabilities, plus precomputed statistics:
 *
 * <ul>
 *   <li>{@code #TOTAL_BIGRAMS} — total bigram occurrences in corpus</li>
 *   <li>{@code #UNIQUE_BIGRAMS} — distinct bigram types in corpus</li>
 *   <li>{@code #UNSEEN_LOG_PROB} — estimated log2-probability for
 *       bigrams not in the profile (see {@link #computeUnseenLogProb})</li>
 *   <li>{@code #EXPECTED_SCORE} — expected average log2-likelihood
 *       for perfect text (see {@link #computeExpectedScore})</li>
 * </ul>
 *
 * <p>Usage: {@code java BigramProfileGenerator <common_tokens_dir>
 * <output_dir> [topN]}</p>
 *
 */
public class BigramProfileGenerator {

    private static final int DEFAULT_TOP_N = 500;
    private static final double FALLBACK_UNSEEN_LOG_PROB = -20.0;

    /**
     * Estimate log2-probability for bigrams not in the top-N profile,
     * using held-out corpus statistics.
     *
     * <p>The profile contains the top-N bigrams covering most of the
     * probability mass. The remaining mass is distributed among all
     * other unique bigrams observed in the corpus:</p>
     *
     * <pre>
     *   coveredMass = sum(2^logprob for each profile bigram)
     *   remainingMass = 1.0 - coveredMass
     *   unseenCount = uniqueBigrams - profileSize
     *   unseenLogProb = log2(remainingMass / unseenCount)
     * </pre>
     *
     * <p>This gives a per-language calibrated penalty. Languages with
     * large character inventories (e.g., CJK with ~30K unique bigrams)
     * get a moderate unseen penalty because the remaining mass is
     * spread over many bigrams. Languages with small alphabets
     * (e.g., English with ~600 unique bigrams) get a harsh penalty
     * because there are few unseen bigrams and little remaining mass.</p>
     *
     * @param profileLogProbs log2-probabilities of the top-N bigrams
     * @param uniqueBigrams   total unique bigram types in the corpus
     * @return estimated log2-probability for unseen bigrams
     */
    public static double computeUnseenLogProb(
            Map<String, Double> profileLogProbs, long uniqueBigrams) {
        if (uniqueBigrams <= profileLogProbs.size()) {
            // Profile covers all known bigrams; use min-seen as floor
            double minLogProb = FALLBACK_UNSEEN_LOG_PROB;
            for (double lp : profileLogProbs.values()) {
                if (lp < minLogProb) {
                    minLogProb = lp;
                }
            }
            return minLogProb;
        }

        double coveredMass = 0.0;
        for (double lp : profileLogProbs.values()) {
            coveredMass += Math.pow(2.0, lp);
        }
        double remainingMass = Math.max(0.0, 1.0 - coveredMass);
        long unseenCount = uniqueBigrams - profileLogProbs.size();
        double avgUnseenProb = remainingMass / unseenCount;

        if (avgUnseenProb <= 0.0) {
            return FALLBACK_UNSEEN_LOG_PROB;
        }
        return Math.log(avgUnseenProb) / Math.log(2.0);
    }

    /**
     * Compute the expected average log2-likelihood per bigram for
     * perfect text drawn from this language's distribution.
     *
     * <pre>
     *   expectedScore = sum(p * log2(p) for each profile bigram)
     *                 + remainingMass * unseenLogProb
     * </pre>
     *
     * <p>This is the negative entropy of the language model. Stored
     * in profile files for reference but not used at scoring time
     * (all use cases compare two text variants, so normalization
     * is unnecessary).</p>
     *
     * @param profileLogProbs log2-probabilities of the top-N bigrams
     * @param unseenLogProb   log2-probability for unseen bigrams
     * @return expected score (negative, since log-probs are negative)
     */
    public static double computeExpectedScore(
            Map<String, Double> profileLogProbs, double unseenLogProb) {
        double score = 0.0;
        double coverageMass = 0.0;
        for (double logProb : profileLogProbs.values()) {
            double p = Math.pow(2.0, logProb);
            score += p * logProb;
            coverageMass += p;
        }
        double unseenMass = Math.max(0.0, 1.0 - coverageMass);
        score += unseenMass * unseenLogProb;
        return score;
    }

    /**
     * Word-boundary marker. Must match
     * {@link BigramTextQualityScorer#BOUNDARY}.
     */
    static final char BOUNDARY = '_';

    /**
     * Extract character bigrams from a word, including word-boundary
     * bigrams ({@code _x} for word-start, {@code x_} for word-end).
     * Only alphabetic characters are included, after lowercasing.
     */
    static Map<String, Long> extractBigramsFromWord(
            String word, long termFreq) {
        Map<String, Long> bigrams = new HashMap<>();
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (Character.isLetter(c)) {
                chars.add(Character.toLowerCase(c));
            }
        }
        if (chars.isEmpty()) {
            return bigrams;
        }
        // Word-start boundary bigram
        bigrams.merge(String.valueOf(BOUNDARY) + chars.get(0),
                termFreq, Long::sum);
        // Internal bigrams
        for (int i = 0; i < chars.size() - 1; i++) {
            String bg = String.valueOf(chars.get(i)) + chars.get(i + 1);
            bigrams.merge(bg, termFreq, Long::sum);
        }
        // Word-end boundary bigram
        bigrams.merge(String.valueOf(chars.get(chars.size() - 1)) + BOUNDARY,
                termFreq, Long::sum);
        return bigrams;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: BigramProfileGenerator "
                    + "<common_tokens_dir> <output_dir> [topN]");
            System.exit(1);
        }
        Path tokensDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);
        int topN = args.length >= 3
                ? Integer.parseInt(args[2]) : DEFAULT_TOP_N;

        Files.createDirectories(outputDir);
        int profileCount = 0;

        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(tokensDir)) {
            for (Path langFile : stream) {
                if (!Files.isRegularFile(langFile)) {
                    continue;
                }
                String lang = langFile.getFileName().toString();
                if (generateProfile(langFile, outputDir, lang, topN)) {
                    profileCount++;
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "Generated %d profiles in %s%n", profileCount, outputDir);
    }

    private static boolean generateProfile(
            Path langFile, Path outputDir, String lang, int topN)
            throws IOException {
        Map<String, Long> bigramCounts = new HashMap<>();
        long totalBigrams = 0;

        try (BufferedReader reader = Files.newBufferedReader(
                langFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")
                        || line.startsWith("___")) {
                    continue;
                }
                String[] parts = line.split("\t");
                String token = parts[0];
                long termFreq;
                if (parts.length >= 3) {
                    termFreq = Long.parseLong(parts[2]);
                } else if (parts.length >= 2) {
                    termFreq = Long.parseLong(parts[1]);
                } else {
                    termFreq = 1;
                }

                Map<String, Long> wordBigrams =
                        extractBigramsFromWord(token, termFreq);
                for (Map.Entry<String, Long> e : wordBigrams.entrySet()) {
                    bigramCounts.merge(e.getKey(), e.getValue(), Long::sum);
                    totalBigrams += e.getValue();
                }
            }
        }

        if (totalBigrams == 0) {
            return false;
        }

        // Sort by frequency descending, take top-N
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(
                bigramCounts.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<Map.Entry<String, Long>> top = sorted.subList(
                0, Math.min(topN, sorted.size()));

        // Compute log2-probabilities
        Map<String, Double> logProbs = new TreeMap<>();
        for (Map.Entry<String, Long> e : top) {
            double prob = (double) e.getValue() / totalBigrams;
            logProbs.put(e.getKey(),
                    Math.log(prob) / Math.log(2.0));
        }

        long uniqueBigrams = bigramCounts.size();
        double unseenLogProb = computeUnseenLogProb(
                logProbs, uniqueBigrams);
        double expectedScore = computeExpectedScore(
                logProbs, unseenLogProb);

        // Write profile
        Path outPath = outputDir.resolve(lang);
        try (BufferedWriter writer = Files.newBufferedWriter(
                outPath, StandardCharsets.UTF_8)) {
            writer.write(String.format(Locale.ROOT,
                    "#TOTAL_BIGRAMS\t%d%n", totalBigrams));
            writer.write(String.format(Locale.ROOT,
                    "#UNIQUE_BIGRAMS\t%d%n", uniqueBigrams));
            writer.write(String.format(Locale.ROOT,
                    "#UNSEEN_LOG_PROB\t%.6f%n", unseenLogProb));
            writer.write(String.format(Locale.ROOT,
                    "#EXPECTED_SCORE\t%.6f%n", expectedScore));
            for (Map.Entry<String, Long> e : top) {
                double prob = (double) e.getValue() / totalBigrams;
                double lp = Math.log(prob) / Math.log(2.0);
                writer.write(String.format(Locale.ROOT,
                        "%s\t%.6f%n", e.getKey(), lp));
            }
        }
        return true;
    }
}
