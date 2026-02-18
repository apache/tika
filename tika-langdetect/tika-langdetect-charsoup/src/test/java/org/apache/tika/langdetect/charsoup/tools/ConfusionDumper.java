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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Dumps a confusion matrix for a specified set of languages.
 * For each target language, shows what the model predicted instead
 * (top-N confusions by count), plus accuracy.
 * <p>
 * Usage: {@code ConfusionDumper <testSplitFile> <modelFile> [lang1,lang2,...]}
 * <p>
 * If no languages are specified, a default set of known weak languages is used.
 */
public class ConfusionDumper {

    private static final String[] DEFAULT_LANGUAGES = {
            "sqi", "tat", "ita", "sun", "mad", "pus", "mkd",
            "ban", "sme", "spa"
    };

    /** Show top-N confusions per language. */
    private static final int TOP_N = 10;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: ConfusionDumper <testSplitFile> <modelFile> [lang1,lang2,...]");
            System.exit(1);
        }

        Path testFile = Paths.get(args[0]);
        Path modelFile = Paths.get(args[1]);

        Set<String> targetLangs;
        if (args.length >= 3) {
            targetLangs = new HashSet<>(Arrays.asList(args[2].split(",")));
        } else {
            targetLangs = new HashSet<>(Arrays.asList(DEFAULT_LANGUAGES));
        }

        // Load model
        System.out.println("Loading model: " + modelFile);
        CharSoupModel model;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(modelFile))) {
            model = CharSoupModel.load(is);
        }
        FeatureExtractor extractor = model.createExtractor();
        System.out.printf(Locale.US, "  %d classes, %d buckets%n",
                model.getNumClasses(), model.getNumBuckets());

        // Load test data
        System.out.println("Loading test data: " + testFile);
        List<LabeledSentence> data = TrainLanguageModel.readPreprocessedFile(testFile);
        System.out.printf(Locale.US, "  %,d sentences%n%n", data.size());

        // For each target language: map of predicted_label -> count
        // Using LinkedHashMap to preserve insertion order for target langs
        Map<String, Map<String, Integer>> confusions = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, Integer> corrects = new LinkedHashMap<>();

        for (String lang : targetLangs) {
            confusions.put(lang, new TreeMap<>());
            totals.put(lang, 0);
            corrects.put(lang, 0);
        }

        // Evaluate
        for (LabeledSentence s : data) {
            String truth = s.getLanguage();
            if (!targetLangs.contains(truth)) {
                continue;
            }

            int[] features = extractor.extract(s.getText());
            float[] probs = model.predict(features);
            int predicted = argmax(probs);
            String predLabel = model.getLabel(predicted);

            totals.merge(truth, 1, Integer::sum);
            if (predLabel.equals(truth)) {
                corrects.merge(truth, 1, Integer::sum);
            } else {
                confusions.get(truth).merge(predLabel, 1, Integer::sum);
            }
        }

        // Print results
        for (String lang : targetLangs) {
            int total = totals.getOrDefault(lang, 0);
            int correct = corrects.getOrDefault(lang, 0);
            if (total == 0) {
                System.out.printf(Locale.US, "%s: no test samples found%n%n", lang);
                continue;
            }

            System.out.printf(Locale.US, "%s: %,d/%,d correct (%.1f%%)%n",
                    lang, correct, total, 100.0 * correct / total);

            Map<String, Integer> confused = confusions.get(lang);
            if (confused.isEmpty()) {
                System.out.println("  (no confusions)\n");
                continue;
            }

            // Sort by count descending, take top N
            confused.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(TOP_N)
                    .forEach(e -> {
                        System.out.printf(Locale.US,
                                "  -> %-12s %,6d  (%5.1f%% of errors, %5.1f%% of total)%n",
                                e.getKey(), e.getValue(),
                                100.0 * e.getValue() / (total - correct),
                                100.0 * e.getValue() / total);
                    });

            // Show how many other distinct confusions there are
            long shown = Math.min(TOP_N, confused.size());
            if (confused.size() > shown) {
                int otherCount = confused.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .skip(TOP_N)
                        .mapToInt(Map.Entry::getValue)
                        .sum();
                System.out.printf(Locale.US,
                        "  -> (+ %d other languages, %,d errors)%n",
                        confused.size() - shown, otherCount);
            }
            System.out.println();
        }
    }

    private static int argmax(float[] arr) {
        int best = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[best]) {
                best = i;
            }
        }
        return best;
    }
}
