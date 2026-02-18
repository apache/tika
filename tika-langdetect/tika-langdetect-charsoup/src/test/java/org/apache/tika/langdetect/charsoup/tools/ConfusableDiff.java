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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;


/**
 * Trains two models (bigrams-only vs bigrams+wordUnigrams) and
 * compares per-language F1 to see where word unigrams help most.
 * Highlights known confusable language groups.
 *
 * Usage: ConfusableDiff &lt;prepDir&gt;
 */
public class ConfusableDiff {

    private static final String[][] CONFUSABLE_GROUPS = {
        {"hrv", "bos", "srp"},
        {"nob", "nno", "dan", "swe"},
        {"ces", "slk"},
        {"ind", "msa", "zlm"},
        {"por", "glg", "spa"},
        {"aze", "tur", "tuk"},
        {"ukr", "rus", "bel"},
        {"bul", "mkd"},
        {"hin", "mar"},
        {"urd", "fas"},
        {"nld", "afr"},
        {"cat", "oci"},
        {"zho", "jpn", "kor"},
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ConfusableDiff <prepDir>");
            System.exit(1);
        }
        Path prepDir = Paths.get(args[0]);
        Path trainFile = prepDir.resolve("train_5m.txt");
        if (!Files.exists(trainFile)) {
            trainFile = prepDir.resolve("train.txt");
        }

        int threads = Runtime.getRuntime().availableProcessors();
        int buckets = 16384;

        System.out.println("Loading dev + test...");
        List<LabeledSentence> dev = readReservoir(
                prepDir.resolve("dev.txt"), 100_000);
        List<LabeledSentence> test = readReservoir(
                prepDir.resolve("test.txt"), 200_000);
        System.out.printf(Locale.US, "dev=%,d (%d langs)  test=%,d (%d langs)%n%n",
                dev.size(), countLangs(dev), test.size(), countLangs(test));

        // Train bigrams-only model
        System.out.println("=== Training: bigrams-only ===");
        Phase2Trainer bigramOnly = new Phase2Trainer(buckets)
                .setAdamLr(0.001f)
                .setSgdLr(0.01f, 0.001f)
                .setAdamEpochs(2)
                .setMaxEpochs(6)
                .setPatience(2)
                .setCheckpointInterval(500_000)
                .setDevSubsampleSize(10_000)
                .setNumThreads(threads)
                .setVerbose(false)
                .setPreprocessed(true);
        bigramOnly.train(trainFile, dev);

        // Train bigrams+wordUnigrams model
        System.out.println("=== Training: bigrams+wordUnigrams ===");
        Phase2Trainer withWords = new Phase2Trainer(buckets)
                .setAdamLr(0.001f)
                .setSgdLr(0.01f, 0.001f)
                .setAdamEpochs(2)
                .setMaxEpochs(6)
                .setPatience(2)
                .setCheckpointInterval(500_000)
                .setDevSubsampleSize(10_000)
                .setNumThreads(threads)
                .setVerbose(false)
                .setPreprocessed(true);
        withWords.train(trainFile, dev);

        // Compute per-language F1 on test set
        Map<String, Double> f1Bigram = perLanguageF1(bigramOnly, test);
        Map<String, Double> f1Words = perLanguageF1(withWords, test);

        // Build confusable group lookup
        Map<String, String> groupLabel = new HashMap<>();
        for (String[] group : CONFUSABLE_GROUPS) {
            String label = String.join("/", group);
            for (String lang : group) {
                groupLabel.put(lang, label);
            }
        }

        // Compute diffs sorted by improvement
        TreeMap<Double, String> byDelta = new TreeMap<>();
        Set<String> allLangs = new HashSet<>();
        allLangs.addAll(f1Bigram.keySet());
        allLangs.addAll(f1Words.keySet());

        System.out.println();
        System.out.printf(Locale.US,
                "%-8s  %8s  %8s  %8s  %-25s%n",
                "lang", "bigram", "+words", "delta", "confusable_group");
        System.out.println("-".repeat(75));

        // Collect and sort
        List<String[]> rows = new ArrayList<>();
        for (String lang : allLangs) {
            double fb = f1Bigram.getOrDefault(lang, 0.0);
            double fw = f1Words.getOrDefault(lang, 0.0);
            double delta = fw - fb;
            rows.add(new String[]{
                    lang,
                    String.format(Locale.US, "%.4f", fb),
                    String.format(Locale.US, "%.4f", fw),
                    String.format(Locale.US, "%+.4f", delta),
                    groupLabel.getOrDefault(lang, "")
            });
        }
        rows.sort((a, b) -> {
            double da = Double.parseDouble(a[3]);
            double db = Double.parseDouble(b[3]);
            return Double.compare(db, da);
        });

        double confGainSum = 0;
        int confGainCount = 0;
        double nonConfGainSum = 0;
        int nonConfGainCount = 0;

        for (String[] row : rows) {
            String lang = row[0];
            double delta = Double.parseDouble(row[3]);
            boolean isConf = groupLabel.containsKey(lang);
            String marker = "";
            if (isConf) {
                marker = " <<";
                confGainSum += delta;
                confGainCount++;
            } else {
                nonConfGainSum += delta;
                nonConfGainCount++;
            }
            System.out.printf(Locale.US,
                    "%-8s  %8s  %8s  %8s  %-25s%s%n",
                    row[0], row[1], row[2], row[3], row[4], marker);
        }

        System.out.println();
        System.out.printf(Locale.US,
                "Avg delta (confusable langs, n=%d): %+.4f%n",
                confGainCount,
                confGainCount > 0 ? confGainSum / confGainCount : 0.0);
        System.out.printf(Locale.US,
                "Avg delta (other langs, n=%d):       %+.4f%n",
                nonConfGainCount,
                nonConfGainCount > 0 ? nonConfGainSum / nonConfGainCount : 0.0);

        // Top confusion pairs for each model
        System.out.println("\n=== Top-20 confusion pairs: bigrams-only ===");
        printTopConfusions(bigramOnly, test, 20);

        System.out.println("\n=== Top-20 confusion pairs: +wordUnigrams ===");
        printTopConfusions(withWords, test, 20);
    }

    private static Map<String, Double> perLanguageF1(
            Phase2Trainer model, List<LabeledSentence> data) {
        Map<String, int[]> counts = new HashMap<>();

        for (LabeledSentence s : data) {
            String trueLabel = s.getLanguage();
            String predicted = model.predict(s.getText());

            counts.computeIfAbsent(trueLabel,
                    k -> new int[3]);
            if (predicted.equals(trueLabel)) {
                counts.get(trueLabel)[0]++;
            } else {
                counts.get(trueLabel)[2]++;
                counts.computeIfAbsent(predicted,
                        k -> new int[3])[1]++;
            }
        }

        Map<String, Double> f1Map = new HashMap<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            int tp = e.getValue()[0];
            int fp = e.getValue()[1];
            int fn = e.getValue()[2];
            if (tp + fn == 0) {
                continue;
            }
            double p = tp + fp > 0 ? (double) tp / (tp + fp) : 0;
            double r = (double) tp / (tp + fn);
            double f1 = p + r > 0 ? 2 * p * r / (p + r) : 0;
            f1Map.put(e.getKey(), f1);
        }
        return f1Map;
    }

    private static void printTopConfusions(
            Phase2Trainer model, List<LabeledSentence> data,
            int topN) {
        Map<String, Integer> confPairs = new HashMap<>();

        for (LabeledSentence s : data) {
            String predicted = model.predict(s.getText());
            if (!predicted.equals(s.getLanguage())) {
                String key = s.getLanguage() + " -> " + predicted;
                confPairs.merge(key, 1, Integer::sum);
            }
        }

        confPairs.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(topN)
                .forEach(e -> System.out.printf(
                        Locale.US, "  %5d  %s%n",
                        e.getValue(), e.getKey()));
    }

    private static int countLangs(List<LabeledSentence> data) {
        Set<String> langs = new HashSet<>();
        for (LabeledSentence s : data) {
            langs.add(s.getLanguage());
        }
        return langs.size();
    }

    private static List<LabeledSentence> readReservoir(
            Path file, int maxLines) throws Exception {
        LabeledSentence[] reservoir =
                new LabeledSentence[maxLines];
        Random rng = new Random(42);
        int seen = 0;
        try (BufferedReader br = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                LabeledSentence s = new LabeledSentence(
                        line.substring(0, tab),
                        line.substring(tab + 1));
                if (seen < maxLines) {
                    reservoir[seen] = s;
                } else {
                    int j = rng.nextInt(seen + 1);
                    if (j < maxLines) {
                        reservoir[j] = s;
                    }
                }
                seen++;
            }
        }
        int fill = Math.min(seen, maxLines);
        List<LabeledSentence> result = new ArrayList<>(fill);
        for (int i = 0; i < fill; i++) {
            result.add(reservoir[i]);
        }
        return result;
    }
}
