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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;
import org.apache.tika.langdetect.charsoup.TextFeatureExtractor;

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
                    "Usage: BucketSaturationAnalyzer <preprocessedTestFile> [scriptaware]");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);

        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> allData = TrainLanguageModel.readPreprocessedFile(testFile);
        // Sample if too large
        if (allData.size() > MAX_SENTENCES) {
            allData = allData.subList(0, MAX_SENTENCES);
        }
        System.out.printf(Locale.US, "Analyzing %,d sentences%n%n",
                allData.size());

        // Header
        System.out.printf(Locale.US,
                "%-8s  %8s  %8s  %8s  %8s  %8s  %8s%n",
                "Buckets", "MeanNZ", "MedianNZ", "P95-NZ", "Sat%",
                "MaxCount", "CorpusNZ");
        System.out.println("-".repeat(76));

        for (int numBuckets : BUCKET_SIZES) {
            TextFeatureExtractor extractor =
                    new ScriptAwareFeatureExtractor(numBuckets);

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

        // Also do per-language breakdown for a few interesting languages at 1k
        System.out.println("\n\nPer-language saturation at 1024 buckets:");
        System.out.printf(Locale.US,
                "%-8s  %6s  %8s  %8s  %7s%n",
                "Lang", "Count", "MeanNZ", "Sat%", "CorpNZ");
        System.out.println("-".repeat(46));

        TextFeatureExtractor ext1k =
                new ScriptAwareFeatureExtractor(1024);

        // Group by language
        Map<String, List<LabeledSentence>> byLang = new HashMap<>();
        for (LabeledSentence s : allData) {
            byLang.computeIfAbsent(s.getLanguage(), k -> new java.util.ArrayList<>()).add(s);
        }

        // Pick representative languages
        String[] interestingLangs = {
                "eng", "deu", "fra", "zho", "cmn", "jpn", "kor",
                "ara", "hin", "rus", "tha", "heb"
        };

        Map<String, double[]> langStats = new TreeMap<>();
        for (String lang : interestingLangs) {
            List<LabeledSentence> langData = byLang.get(lang);
            if (langData == null || langData.isEmpty()) {
                continue;
            }

            boolean[] corpusBuckets = new boolean[1024];
            double totalNZ = 0;
            for (LabeledSentence s : langData) {
                int[] features = ext1k.extractFromPreprocessed(s.getText());
                int nz = 0;
                for (int b = 0; b < 1024; b++) {
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

        for (var e : langStats.entrySet()) {
            double[] s = e.getValue();
            System.out.printf(Locale.US,
                    "%-8s  %6.0f  %8.1f  %6.2f%%  %8.0f%n",
                    e.getKey(), s[0], s[1], 100.0 * s[1] / 1024, s[2]);
        }
    }
}
