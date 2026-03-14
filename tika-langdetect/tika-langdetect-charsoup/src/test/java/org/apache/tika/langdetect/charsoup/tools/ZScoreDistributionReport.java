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

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Report what fraction of sentences fall below various z-score thresholds,
 * at different text lengths. Uses {@link GenerativeLanguageModel#zScoreLengthAdjusted}.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java ZScoreDistributionReport \
 *       --model  generative.bin \
 *       --test   flores200_dev.tsv \
 *       [--langs eng,fra,deu,zho,ara,rus,jpn] \
 *       [--lengths 20,50,100,200,full]
 * </pre>
 */
public class ZScoreDistributionReport {

    private static final int[] DEFAULT_LENGTHS = {20, 50, 100, 200, 0};
    private static final float[] THRESHOLDS = {-1.0f, -1.5f, -2.0f, -2.5f, -3.0f, -4.0f, -5.0f};

    public static void main(String[] args) throws Exception {
        Path modelPath = null;
        Path testPath = null;
        Set<String> filterLangs = null;
        int[] lengths = DEFAULT_LENGTHS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--test":
                    testPath = Paths.get(args[++i]);
                    break;
                case "--langs":
                    filterLangs = new TreeSet<>(Arrays.asList(args[++i].split(",")));
                    break;
                case "--lengths": {
                    String[] parts = args[++i].split(",");
                    lengths = new int[parts.length];
                    for (int j = 0; j < parts.length; j++) {
                        String p = parts[j].trim();
                        lengths[j] = p.equalsIgnoreCase("full") ? 0 : Integer.parseInt(p);
                    }
                    break;
                }
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }
        if (modelPath == null || testPath == null) {
            System.err.println("Usage: ZScoreDistributionReport --model <.bin> --test <.tsv>");
            System.exit(1);
        }

        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(modelPath.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }

        List<LabeledSentence> data = EvalGenerativeModel.loadTestFile(testPath);
        // Normalize FLORES codes
        if (data.stream().anyMatch(s -> s.getLanguage().contains("_"))) {
            List<LabeledSentence> norm = new ArrayList<>(data.size());
            for (LabeledSentence s : data) {
                norm.add(new LabeledSentence(
                        EvalGenerativeModel.normalizeLang(s.getLanguage()), s.getText()));
            }
            data = norm;
        }

        // Group by language
        Set<String> modelLangs = new TreeSet<>(model.getLanguages());
        Map<String, List<String>> byLang = new HashMap<>();
        for (LabeledSentence s : data) {
            if (!modelLangs.contains(s.getLanguage())) {
                continue;
            }
            byLang.computeIfAbsent(s.getLanguage(), k -> new ArrayList<>())
                    .add(s.getText());
        }

        // Select languages to report
        List<String> reportLangs;
        if (filterLangs != null) {
            reportLangs = new ArrayList<>();
            for (String l : filterLangs) {
                if (byLang.containsKey(l)) {
                    reportLangs.add(l);
                } else {
                    System.err.println("Warning: " + l + " not found in test data");
                }
            }
        } else {
            reportLangs = new ArrayList<>(new TreeSet<>(byLang.keySet()));
        }

        System.out.printf(Locale.US, "Model: %s  (%d languages)%n", modelPath, model.getLanguages().size());
        System.out.printf(Locale.US, "Test:  %s  (%d languages, %,d sentences)%n%n",
                testPath, byLang.size(), data.size());

        for (int len : lengths) {
            String lenLabel = len > 0 ? len + " chars" : "full";
            System.out.printf(Locale.US, "=== Length: %s ===%n%n", lenLabel);

            // Header
            StringBuilder hdr = new StringBuilder();
            hdr.append(String.format(Locale.US, "%-8s  %5s  %6s  %6s", "Lang", "N", "mean-z", "std-z"));
            for (float t : THRESHOLDS) {
                hdr.append(String.format(Locale.US, "  z<%.1f", t));
            }
            System.out.println(hdr);
            System.out.println("-".repeat(hdr.length()));

            // Aggregate stats across all languages
            int totalN = 0;
            int[] totalBelow = new int[THRESHOLDS.length];

            for (String lang : reportLangs) {
                List<String> sentences = byLang.get(lang);
                if (sentences == null) {
                    continue;
                }
                float[] zScores = new float[sentences.size()];
                int valid = 0;
                for (int si = 0; si < sentences.size(); si++) {
                    String text = sentences.get(si);
                    if (len > 0 && text.length() > len) {
                        text = text.substring(0, len);
                    }
                    float z = model.zScoreLengthAdjusted(text, lang);
                    if (!Float.isNaN(z)) {
                        zScores[valid++] = z;
                    }
                }
                if (valid == 0) {
                    continue;
                }
                zScores = Arrays.copyOf(zScores, valid);
                Arrays.sort(zScores);

                double sum = 0;
                double sum2 = 0;
                for (int j = 0; j < valid; j++) {
                    sum += zScores[j];
                    sum2 += (double) zScores[j] * zScores[j];
                }
                double mean = sum / valid;
                double std = valid > 1 ? Math.sqrt((sum2 - sum * sum / valid) / (valid - 1)) : 0;

                StringBuilder row = new StringBuilder();
                row.append(String.format(Locale.US, "%-8s  %5d  %+6.2f  %6.3f", lang, valid, mean, std));
                for (int ti = 0; ti < THRESHOLDS.length; ti++) {
                    int below = countBelow(zScores, THRESHOLDS[ti]);
                    double pct = 100.0 * below / valid;
                    row.append(String.format(Locale.US, "  %5.1f%%", pct));
                    totalBelow[ti] += below;
                }
                totalN += valid;
                System.out.println(row);
            }

            // Summary row
            if (reportLangs.size() > 1) {
                System.out.println("-".repeat(hdr.length()));
                StringBuilder tot = new StringBuilder();
                tot.append(String.format(Locale.US, "%-8s  %5d  %6s  %6s", "ALL", totalN, "", ""));
                for (int ti = 0; ti < THRESHOLDS.length; ti++) {
                    double pct = 100.0 * totalBelow[ti] / totalN;
                    tot.append(String.format(Locale.US, "  %5.1f%%", pct));
                }
                System.out.println(tot);
            }
            System.out.println();
        }
    }

    private static int countBelow(float[] sorted, float threshold) {
        int idx = Arrays.binarySearch(sorted, threshold);
        if (idx < 0) {
            return -idx - 1;
        }
        while (idx < sorted.length && sorted[idx] <= threshold) {
            idx++;
        }
        return idx;
    }
}
