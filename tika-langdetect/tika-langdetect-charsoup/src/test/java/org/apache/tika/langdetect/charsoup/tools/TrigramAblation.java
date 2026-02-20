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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Quick ablation: bigrams+trigrams at 8k/16k/32k and
 * bigrams+trigrams+wordUnigrams for comparison.
 *
 * Usage: TrigramAblation &lt;prepDir&gt;
 */
public class TrigramAblation {

    static final int[] BUCKET_SIZES = {8192, 16384, 32768};

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: TrigramAblation <prepDir>");
            System.exit(1);
        }

        Path prepDir = Paths.get(args[0]);
        Path trainFile = prepDir.resolve("train_5m.txt");
        if (!Files.exists(trainFile)) {
            trainFile = prepDir.resolve("train.txt");
        }

        int threads = Runtime.getRuntime().availableProcessors();

        System.out.println("Train file: " + trainFile);
        System.out.printf(Locale.US, "Threads: %d%n", threads);

        System.out.println("Loading dev + test...");
        long t0 = System.nanoTime();
        List<LabeledSentence> dev = readReservoir(
                prepDir.resolve("dev.txt"), 100_000);
        List<LabeledSentence> test = readReservoir(
                prepDir.resolve("test.txt"), 200_000);
        System.out.printf(Locale.US,
                "Loaded: dev=%,d (%d langs)  test=%,d (%d langs)  [%.1f s]%n%n",
                dev.size(), countLangs(dev),
                test.size(), countLangs(test),
                elapsed(t0));

        // Header (matches AblationRunner format)
        System.out.printf(Locale.US,
                "%-28s  %7s  %4s  %4s  %4s  %4s  "
                        + "%8s  %8s  %8s  %7s%n",
                "config", "buckets",
                "tri", "skip", "word", "cjk",
                "devF1", "langs", "testAcc", "time_s");
        System.out.println("-".repeat(105));

        // Configs: trigrams, skipgrams, wordUnigrams, cjkUnigrams
        String[][] configs = {
            {"+trigrams",                "true",  "false", "false", "false"},
            {"+trigrams+wordUnigrams",   "true",  "false", "true",  "false"},
        };

        for (String[] config : configs) {
            String name = config[0];
            boolean tri = Boolean.parseBoolean(config[1]);
            boolean skip = Boolean.parseBoolean(config[2]);
            boolean word = Boolean.parseBoolean(config[3]);
            boolean cjk = Boolean.parseBoolean(config[4]);

            for (int buckets : BUCKET_SIZES) {
                t0 = System.nanoTime();

                Phase2Trainer trainer = new Phase2Trainer(
                        buckets)
                        .setAdamLr(0.001f)
                        .setSgdLr(0.01f, 0.001f)
                        .setAdamEpochs(2)
                        .setMaxEpochs(6)
                        .setCheckpointInterval(500_000)
                        .setPatience(2)
                        .setDevSubsampleSize(10_000)
                        .setNumThreads(threads)
                        .setVerbose(false)
                        .setPreprocessed(true);

                trainer.train(trainFile, dev);

                Phase2Trainer.F1Result devF1 =
                        trainer.evaluateMacroF1(dev);
                Phase2Trainer.F1Result testF1 =
                        trainer.evaluateMacroF1(test);

                double secs = elapsed(t0);

                System.out.printf(Locale.US,
                        "%-28s  %7d  %4s  %4s  %4s  %4s  "
                                + "%8.4f  %8d  %8.4f  %7.1f%n",
                        name, buckets,
                        tri ? "Y" : "-",
                        skip ? "Y" : "-",
                        word ? "Y" : "-",
                        cjk ? "Y" : "-",
                        devF1.f1, devF1.numLangs,
                        testF1.f1, secs);
            }
        }

        System.out.println("\nDone.");
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

    private static int countLangs(List<LabeledSentence> data) {
        Set<String> langs = new HashSet<>();
        for (LabeledSentence s : data) {
            langs.add(s.getLanguage());
        }
        return langs.size();
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }
}
