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

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.apache.tika.langdetect.charsoup.CharSoupFeatureExtractor;
import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Prints sentences from non-Korean languages that the CharSoup model
 * incorrectly predicts as Korean ("kor"), to help understand false positives.
 *
 * Usage: KoreanFalsePositives <flores_dev_tsv> <model_file> [--sample N]
 *   N = max sentences to print per source language (default 10)
 */
public class KoreanFalsePositives {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: KoreanFalsePositives <flores_dev_tsv> <model_file> [--sample N]");
            System.exit(1);
        }

        Path testFile  = Paths.get(args[0]);
        Path modelFile = Paths.get(args[1]);
        int maxPerLang = 10;
        for (int i = 2; i < args.length - 1; i++) {
            if ("--sample".equals(args[i])) {
                maxPerLang = Integer.parseInt(args[i + 1]);
            }
        }

        System.out.println("Loading model: " + modelFile);
        CharSoupModel model;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(modelFile))) {
            model = CharSoupModel.load(is);
        }
        FeatureExtractor extractor = model.createExtractor();
        System.out.printf(Locale.US, "Model: %d classes, %d buckets%n%n",
                model.getNumClasses(), model.getNumBuckets());

        System.out.println("Loading Flores data: " + testFile);
        List<LabeledSentence> allData = TrainLanguageModel.readPreprocessedFile(testFile);

        // Normalize Flores codes (same logic as CompareDetectors)
        boolean floresMode = allData.stream().anyMatch(s -> s.getLanguage().contains("_"));
        if (floresMode) {
            allData = allData.stream().map(s -> {
                String raw = s.getLanguage();
                String lang = CompareDetectors.FLORES_KEEP_SCRIPT_SUFFIX.contains(raw)
                        ? raw : CompareDetectors.normalizeLang(raw);
                return new LabeledSentence(lang, s.getText());
            }).collect(java.util.stream.Collectors.toList());
        }

        System.out.printf(Locale.US, "Total sentences: %,d%n%n", allData.size());

        // Collect false positives per source language
        java.util.Map<String, java.util.List<String[]>> falsePositives = new java.util.TreeMap<>();

        for (LabeledSentence s : allData) {
            if ("kor".equals(s.getLanguage())) {
                continue; // skip true Korean
            }

            String preprocessed = CharSoupFeatureExtractor.preprocess(s.getText());
            int[] features = extractor.extractFromPreprocessed(preprocessed);
            float[] probs = model.predict(features);

            int bestIdx = 0;
            for (int i = 1; i < probs.length; i++) {
                if (probs[i] > probs[bestIdx]) bestIdx = i;
            }
            String predicted = model.getLabel(bestIdx);
            if (!"kor".equals(predicted)) {
                continue;
            }

            float korProb = probs[bestIdx];
            // Find second-best
            int second = -1;
            for (int i = 0; i < probs.length; i++) {
                if (i == bestIdx) continue;
                if (second < 0 || probs[i] > probs[second]) second = i;
            }
            float secondProb = second >= 0 ? probs[second] : 0f;
            String secondLabel = second >= 0 ? model.getLabel(second) : "?";

            falsePositives
                    .computeIfAbsent(s.getLanguage(), k -> new java.util.ArrayList<>())
                    .add(new String[]{
                            s.getText(),
                            String.format(Locale.US, "kor=%.3f", korProb),
                            String.format(Locale.US, "%s=%.3f", secondLabel, secondProb)
                    });
        }

        // Print summary
        System.out.printf(Locale.US, "Source languages sending sentences to kor: %d%n%n",
                falsePositives.size());

        for (var entry : falsePositives.entrySet()) {
            String lang = entry.getKey();
            List<String[]> fps = entry.getValue();
            System.out.printf(Locale.US, "=== %s → kor (%d sentences) ===%n",
                    lang, fps.size());
            int shown = 0;
            for (String[] fp : fps) {
                if (shown++ >= maxPerLang) {
                    System.out.printf(Locale.US, "  ... (%d more not shown)%n",
                            fps.size() - maxPerLang);
                    break;
                }
                // Show first 120 chars of sentence
                String text = fp[0];
                if (text.length() > 120) text = text.substring(0, 117) + "...";
                System.out.printf(Locale.US, "  [%s, runner-up %s]%n    %s%n",
                        fp[1], fp[2], text);
            }
            System.out.println();
        }
    }
}
