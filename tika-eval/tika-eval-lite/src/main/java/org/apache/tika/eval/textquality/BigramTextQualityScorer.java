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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tika.textquality.TextQualityResult;
import org.apache.tika.textquality.TextQualityScorer;

/**
 * Scores text quality using character bigram profiles derived from
 * language corpora. For each language, the profile contains the top 200
 * character bigrams and their log2-probabilities. Scoring computes the
 * average log2-likelihood per bigram against each language profile and
 * returns the best match.
 *
 * <p>Bigram profiles were generated from the common_tokens data in
 * tika-eval-core by decomposing words into character bigrams weighted
 * by term frequency.</p>
 *
 * <p>This should not be used as a language detector. It is more lightweight
 * and is meant to give an indication of "text quality".</p>
 */
public class BigramTextQualityScorer extends TextQualityScorer {

    private static final String PROFILES_PATH =
            "/org/apache/tika/eval/textquality/bigram_profiles/";

    // Fallback unseen logprob used when a profile lacks corpus
    // statistics needed for held-out estimation.
    private static final double FALLBACK_UNSEEN_LOG_PROB = -20.0;

    // Minimum total bigram count for a language profile to be loaded.
    // Profiles from tiny corpora (srd=80K, ssw=44K) have unreliable
    // probability estimates that cause false language matches.
    private static final long MIN_CORPUS_BIGRAMS = 500_000;

    // Default maximum characters to analyze. Beyond this, additional
    // text adds negligible precision to the score.
    private static final int DEFAULT_MAX_TEXT_LENGTH = 10_000;

    // Loaded lazily on first use
    private volatile Map<String, LanguageProfile> profiles;

    private int maxTextLength = DEFAULT_MAX_TEXT_LENGTH;

    public int getMaxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    @Override
    public TextQualityResult score(CharSequence text) {
        Map<String, LanguageProfile> profs = getProfiles();
        if (profs.isEmpty()) {
            return new TextQualityResult(0.0, "unk", 0.0, 0);
        }

        int len = Math.min(text.length(), maxTextLength);
        // Extract bigrams from input text
        Map<String, Integer> textBigrams = extractBigrams(text, len);
        int totalBigrams = 0;
        for (int count : textBigrams.values()) {
            totalBigrams += count;
        }

        if (totalBigrams == 0) {
            return new TextQualityResult(0.0, "unk", 0.0, 0);
        }

        // Score against each language profile
        String bestLang = "unk";
        double bestScore = Double.NEGATIVE_INFINITY;
        double secondBest = Double.NEGATIVE_INFINITY;

        for (Map.Entry<String, LanguageProfile> entry : profs.entrySet()) {
            double langScore = scoreAgainstProfile(textBigrams,
                    totalBigrams, entry.getValue());
            if (langScore > bestScore) {
                secondBest = bestScore;
                bestScore = langScore;
                bestLang = entry.getKey();
            } else if (langScore > secondBest) {
                secondBest = langScore;
            }
        }

        double confidence = (secondBest == Double.NEGATIVE_INFINITY)
                ? 0.0 : bestScore - secondBest;

        return new TextQualityResult(bestScore, bestLang,
                confidence, totalBigrams);
    }

    @Override
    public TextQualityResult score(CharSequence text, String language) {
        Map<String, LanguageProfile> profs = getProfiles();
        LanguageProfile profile = profs.get(language);
        if (profile == null) {
            return new TextQualityResult(0.0, language, 0.0, 0);
        }

        int len = Math.min(text.length(), maxTextLength);
        Map<String, Integer> textBigrams = extractBigrams(text, len);
        int totalBigrams = 0;
        for (int count : textBigrams.values()) {
            totalBigrams += count;
        }

        if (totalBigrams == 0) {
            return new TextQualityResult(0.0, language, 0.0, 0);
        }

        double langScore = scoreAgainstProfile(textBigrams,
                totalBigrams, profile);
        return new TextQualityResult(langScore, language,
                0.0, totalBigrams);
    }

    // Word-boundary marker used in boundary bigrams (_x for word-start,
    // x_ for word-end). Underscore is not a letter, so it won't collide
    // with any real character bigram.
    static final char BOUNDARY = '_';

    /**
     * Extract character bigrams from text, including word-boundary
     * bigrams. Input is first normalized to match the language
     * profiles: NFKD decomposition with combining mark removal
     * (equivalent to ICU folding used to generate common_tokens).
     *
     * <p>For each word (consecutive letters), emits:</p>
     * <ul>
     *   <li>Internal bigrams: consecutive lowercased letter pairs</li>
     *   <li>Word-start bigram: {@code _x} (boundary + first letter)</li>
     *   <li>Word-end bigram: {@code x_} (last letter + boundary)</li>
     * </ul>
     *
     * <p>Word-boundary bigrams are critical for RTL detection because
     * word-initial and word-final character patterns are highly
     * directional. For example, Hebrew final forms (sofit letters)
     * appear at word ends in forward text but word starts when
     * reversed.</p>
     */
    static Map<String, Integer> extractBigrams(CharSequence text) {
        return extractBigrams(text, text.length());
    }

    static Map<String, Integer> extractBigrams(CharSequence text, int len) {
        // Normalize to match profile generation: NFKD decomposition
        // strips compatibility differences, then we skip combining
        // marks (diacritics) in the loop below. This matches ICU
        // folding used to build common_tokens.
        String normalized = Normalizer.normalize(
                text.subSequence(0, len), Normalizer.Form.NFKD);

        Map<String, Integer> counts = new HashMap<>();
        char prev = 0;
        boolean prevIsLetter = false;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);

            // Skip combining marks (diacritics). After NFKD,
            // accented chars are decomposed to base + combining
            // mark(s). Arabic tashkeel (fatha, kasra, etc.) are
            // also combining marks. Skipping them prevents them
            // from breaking the bigram chain.
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }

            boolean isLetter = Character.isLetter(c);
            if (isLetter) {
                c = Character.toLowerCase(c);
                if (prevIsLetter) {
                    String bigram = String.valueOf(prev) + c;
                    counts.merge(bigram, 1, Integer::sum);
                } else {
                    String startBigram = String.valueOf(BOUNDARY) + c;
                    counts.merge(startBigram, 1, Integer::sum);
                }
                prev = c;
            } else if (prevIsLetter) {
                String endBigram = String.valueOf(prev) + BOUNDARY;
                counts.merge(endBigram, 1, Integer::sum);
            }
            prevIsLetter = isLetter;
        }
        if (prevIsLetter) {
            String endBigram = String.valueOf(prev) + BOUNDARY;
            counts.merge(endBigram, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Compute average log2-likelihood of the text bigrams under a
     * language profile. Unseen bigrams get the profile's floor
     * log-probability.
     */
    private static double scoreAgainstProfile(
            Map<String, Integer> textBigrams, int totalBigrams,
            LanguageProfile profile) {
        double totalLogProb = 0.0;

        for (Map.Entry<String, Integer> entry : textBigrams.entrySet()) {
            String bigram = entry.getKey();
            int count = entry.getValue();
            Double logProb = profile.bigramLogProbs.get(bigram);
            if (logProb == null) {
                logProb = profile.unseenLogProb;
            }
            totalLogProb += count * logProb;
        }

        return totalLogProb / totalBigrams;
    }

    private Map<String, LanguageProfile> getProfiles() {
        if (profiles == null) {
            synchronized (this) {
                if (profiles == null) {
                    profiles = loadAllProfiles();
                }
            }
        }
        return profiles;
    }

    private Map<String, LanguageProfile> loadAllProfiles() {
        Map<String, LanguageProfile> result = new ConcurrentHashMap<>();
        // Load the language list from the index file
        try (InputStream is = getClass().getResourceAsStream(
                PROFILES_PATH + "languages.lst")) {
            if (is == null) {
                return Collections.emptyMap();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String lang;
                while ((lang = reader.readLine()) != null) {
                    lang = lang.trim();
                    if (lang.isEmpty() || lang.startsWith("#")) {
                        continue;
                    }
                    LanguageProfile profile = loadProfile(lang);
                    if (profile != null) {
                        result.put(lang, profile);
                    }
                }
            }
        } catch (IOException e) {
            // Log and return whatever we have
        }
        return result;
    }

    private LanguageProfile loadProfile(String lang) {
        try (InputStream is = getClass().getResourceAsStream(
                PROFILES_PATH + lang)) {
            if (is == null) {
                return null;
            }
            Map<String, Double> logProbs = new HashMap<>();
            long totalBigrams = 0;
            double unseenLogProb = FALLBACK_UNSEEN_LOG_PROB;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#TOTAL_BIGRAMS\t")) {
                        totalBigrams = Long.parseLong(
                                line.substring("#TOTAL_BIGRAMS\t".length()));
                    } else if (line.startsWith("#UNSEEN_LOG_PROB\t")) {
                        unseenLogProb = Double.parseDouble(
                                line.substring(
                                        "#UNSEEN_LOG_PROB\t".length()));
                    } else if (line.startsWith("#")) {
                        continue;
                    } else {
                        int tab = line.indexOf('\t');
                        if (tab > 0) {
                            String bigram = line.substring(0, tab);
                            double logProb = Double.parseDouble(
                                    line.substring(tab + 1));
                            logProbs.put(bigram, logProb);
                        }
                    }
                }
            }

            if (totalBigrams < MIN_CORPUS_BIGRAMS) {
                return null;
            }

            return new LanguageProfile(logProbs, unseenLogProb);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    static class LanguageProfile {
        final Map<String, Double> bigramLogProbs;
        final double unseenLogProb;

        LanguageProfile(Map<String, Double> bigramLogProbs,
                        double unseenLogProb) {
            this.bigramLogProbs = bigramLogProbs;
            this.unseenLogProb = unseenLogProb;
        }
    }
}
