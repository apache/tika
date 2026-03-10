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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.IntFunction;

import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Analyzes bucket saturation for different bucket sizes and extractors.
 * <p>
 * For each bucket size, measures:
 * <ul>
 *   <li><b>Non-zero buckets per sentence</b> — how many buckets a typical
 *       sentence activates (mean, median, p95)</li>
 *   <li><b>Saturation %</b> — what fraction of all buckets are non-zero
 *       for a typical sentence</li>
 *   <li><b>Corpus-wide unique buckets</b> — across all sentences for a
 *       language, how many distinct buckets are ever activated (measures
 *       effective hash space usage)</li>
 *   <li><b>Max bucket count</b> — hottest bucket per sentence (indicates
 *       collision severity)</li>
 * </ul>
 * <p>
 * Usage: {@code BucketSaturationAnalyzer <preprocessedTestFile> [scriptaware]}
 */
public class BucketSaturationAnalyzer {

    private static final int[] BUCKET_SIZES = {32768, 16384, 8192, 4096, 2048, 1024};
    private static final int MAX_SENTENCES = 50_000; // sample for speed

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println(
                    "Usage: BucketSaturationAnalyzer <preprocessedTestFile>");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);

        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> allData = TrainLanguageModel.readPreprocessedFile(testFile);
        // Shuffle then sample so all languages are represented
        Collections.shuffle(allData, new Random(42));
        if (allData.size() > MAX_SENTENCES) {
            allData = allData.subList(0, MAX_SENTENCES);
        }
        System.out.printf(Locale.US, "Analyzing %,d sentences%n%n",
                allData.size());

        // (numBuckets, useTrigrams, useSkipBigrams, useSuffixes, useSuffix4,
        //              usePrefix, useWordUnigrams, useCharUnigrams, use4grams, use5grams)
        Map<String, IntFunction<FeatureExtractor>> configs = new LinkedHashMap<>();
        configs.put("bigrams-only",
                n -> new ResearchFeatureExtractor(n, false, false, false, false, false, false, false, false, false));
        configs.put("bigrams+words",
                n -> new ResearchFeatureExtractor(n));
        configs.put("bigrams+tri+suf+pre+words",
                n -> new ResearchFeatureExtractor(n, true, false, true, false, true, true, false, false, false));
        configs.put("full",
                n -> new ResearchFeatureExtractor(n, true, true, true, true, true, true, true, true, true));

        for (Map.Entry<String, IntFunction<FeatureExtractor>> configEntry : configs.entrySet()) {
            String config = configEntry.getKey();
            IntFunction<FeatureExtractor> factory = configEntry.getValue();
            System.out.println("=== Research " + config + " ===");
            System.out.printf(Locale.US,
                    "%-8s  %8s  %8s  %8s  %8s  %8s  %8s%n",
                    "Buckets", "MeanNZ", "MedianNZ", "P95-NZ", "Sat%",
                    "MaxCount", "CorpusNZ");
            System.out.println("-".repeat(76));

        for (int numBuckets : BUCKET_SIZES) {
            FeatureExtractor extractor = factory.apply(numBuckets);

            int[] nzPerSentence = new int[allData.size()];
            int[] maxCountPerSentence = new int[allData.size()];
            boolean[] corpusBucketsUsed = new boolean[numBuckets];

            for (int i = 0; i < allData.size(); i++) {
                int[] features = extractor.extractFromPreprocessed(
                        allData.get(i).getText());

                int nz = 0;
                int maxCount = 0;
                for (int b = 0; b < numBuckets; b++) {
                    if (features[b] != 0) {
                        nz++;
                        corpusBucketsUsed[b] = true;
                        if (features[b] > maxCount) {
                            maxCount = features[b];
                        }
                    }
                }
                nzPerSentence[i] = nz;
                maxCountPerSentence[i] = maxCount;
            }

            // Sort for percentiles
            java.util.Arrays.sort(nzPerSentence);
            java.util.Arrays.sort(maxCountPerSentence);

            double meanNZ = 0;
            for (int nz : nzPerSentence) {
                meanNZ += nz;
            }
            meanNZ /= nzPerSentence.length;

            int medianNZ = nzPerSentence[nzPerSentence.length / 2];
            int p95NZ = nzPerSentence[(int) (nzPerSentence.length * 0.95)];
            double satPct = 100.0 * meanNZ / numBuckets;

            int corpusNZ = 0;
            for (boolean used : corpusBucketsUsed) {
                if (used) {
                    corpusNZ++;
                }
            }

            int maxCount = maxCountPerSentence[maxCountPerSentence.length - 1];

            System.out.printf(Locale.US,
                    "%-8d  %8.1f  %8d  %8d  %7.2f%%  %8d  %8d%n",
                    numBuckets, meanNZ, medianNZ, p95NZ, satPct, maxCount, corpusNZ);
        }
        System.out.println();
        } // end config loop

        // Per-language breakdown at 8k (production-like config)
        int perLangBuckets = 8192;
        FeatureExtractor extPerLang =
                new ResearchFeatureExtractor(perLangBuckets, true, false, true, false, true, true, false, false, false);

        // Group by language
        Map<String, List<LabeledSentence>> byLang = new HashMap<>();
        for (LabeledSentence s : allData) {
            byLang.computeIfAbsent(s.getLanguage(), k -> new java.util.ArrayList<>()).add(s);
        }

        Map<String, double[]> langStats = new TreeMap<>();
        for (String lang : byLang.keySet()) {
            List<LabeledSentence> langData = byLang.get(lang);
            if (langData == null || langData.isEmpty()) {
                continue;
            }

            boolean[] corpusBuckets = new boolean[perLangBuckets];
            double totalNZ = 0;
            for (LabeledSentence s : langData) {
                int[] features = extPerLang.extractFromPreprocessed(s.getText());
                int nz = 0;
                for (int b = 0; b < perLangBuckets; b++) {
                    if (features[b] != 0) {
                        nz++;
                        corpusBuckets[b] = true;
                    }
                }
                totalNZ += nz;
            }
            double meanNZ = totalNZ / langData.size();
            int corpNZ = 0;
            for (boolean u : corpusBuckets) {
                if (u) {
                    corpNZ++;
                }
            }
            langStats.put(lang, new double[]{langData.size(), meanNZ, corpNZ});
        }

        System.out.println("Per-language saturation at " + perLangBuckets + " buckets (bigrams+tri+suf+pre+words):");
        System.out.printf(Locale.US,
                "%-8s  %6s  %8s  %8s  %7s%n",
                "Lang", "Count", "MeanNZ", "Sat%", "CorpNZ");
        System.out.println("-".repeat(46));
        for (var e : langStats.entrySet()) {
            double[] s = e.getValue();
            System.out.printf(Locale.US,
                    "%-8s  %6.0f  %8.1f  %6.2f%%  %8.0f%n",
                    e.getKey(), s[0], s[1], 100.0 * s[1] / perLangBuckets, s[2]);
        }
    }
}
