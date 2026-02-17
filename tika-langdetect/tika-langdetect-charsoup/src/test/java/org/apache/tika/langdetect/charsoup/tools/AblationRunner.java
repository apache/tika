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
 * Ablation study runner for Phase 2 feature engineering.
 * Tests combinations of feature types across bucket sizes.
 * <p>
 * Usage: AblationRunner &lt;prepDir&gt; [trainFile]
 * <p>
 * If trainFile is not specified, uses train_5m.txt or
 * train.txt from prepDir.
 */
public class AblationRunner {

    static final int[] BUCKET_SIZES = {8192, 16384, 32768};

    static final String[][] CONFIGS = {
        // name, skipgrams, wordUnigrams, cjkUnigrams
        {"bigrams-only",       "false", "false", "false"},
        {"+skipgrams",         "true",  "false", "false"},
        {"+wordUnigrams",      "false", "true",  "false"},
        {"+cjkUnigrams",       "false", "false", "true"},
        {"all-features",       "true",  "true",  "true"},
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println(
                    "Usage: AblationRunner <prepDir> "
                            + "[trainFile]");
            System.exit(1);
        }

        Path prepDir = Paths.get(args[0]);
        Path trainFile;
        if (args.length > 1) {
            trainFile = Paths.get(args[1]);
        } else {
            trainFile = prepDir.resolve("train_5m.txt");
            if (!Files.exists(trainFile)) {
                trainFile = prepDir.resolve("train.txt");
            }
        }

        int threads = Runtime.getRuntime()
                .availableProcessors();

        System.out.println("Train file: " + trainFile);
        System.out.printf("Threads: %d%n", threads);

        // Load dev + test via reservoir sampling
        System.out.println("Loading dev + test...");
        long t0 = System.nanoTime();
        List<LabeledSentence> dev = readReservoir(
                prepDir.resolve("dev.txt"), 100_000);
        List<LabeledSentence> test = readReservoir(
                prepDir.resolve("test.txt"), 200_000);
        System.out.printf(Locale.US,
                "Loaded: dev=%,d (%d langs)  "
                        + "test=%,d (%d langs)  [%.1f s]%n%n",
                dev.size(), countLangs(dev),
                test.size(), countLangs(test),
                elapsed(t0));

        // Header
        System.out.printf(Locale.US,
                "%-20s  %7s  %4s  %4s  %4s  "
                        + "%8s  %8s  %8s  %7s%n",
                "config", "buckets",
                "skip", "word", "cjk",
                "devF1", "langs", "testAcc", "time_s");
        System.out.println("-".repeat(95));

        // Run ablation matrix
        for (String[] config : CONFIGS) {
            String name = config[0];
            boolean skip = Boolean.parseBoolean(config[1]);
            boolean word = Boolean.parseBoolean(config[2]);
            boolean cjk = Boolean.parseBoolean(config[3]);

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
                        "%-20s  %7d  %4s  %4s  %4s  "
                                + "%8.4f  %8d  %8.4f  %7.1f%n",
                        name, buckets,
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
        List<LabeledSentence> result =
                new ArrayList<>(fill);
        for (int i = 0; i < fill; i++) {
            result.add(reservoir[i]);
        }
        return result;
    }

    private static int countLangs(
            List<LabeledSentence> data) {
        Set<String> langs = new HashSet<>();
        for (LabeledSentence s : data) {
            langs.add(s.getLanguage());
        }
        return langs.size();
    }

    private static double elapsed(long startNanos) {
        return (System.nanoTime() - startNanos)
                / 1_000_000_000.0;
    }
}
