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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.OutputStream;
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

import org.apache.tika.ml.LinearModel;

/**
 * Smoke test for Phase2Trainer. Streams training data from
 * disk, trains at 8k buckets, quantizes, evaluates.
 * <p>
 * Usage: Phase2SmokeTest &lt;prepDir&gt; [outputModel]
 * <p>
 * Expects prepDir to contain train.txt (or train_2m.txt),
 * dev.txt, and test.txt in tab-separated format.
 */
public class Phase2SmokeTest {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println(
                    "Usage: Phase2SmokeTest <prepDir> "
                            + "[outputModel]");
            System.exit(1);
        }

        Path prepDir = Paths.get(args[0]);
        Path outputModel = args.length > 1
                ? Paths.get(args[1]) : null;

        int numBuckets = 8192;
        int threads = Runtime.getRuntime()
                .availableProcessors();

        // Determine train file
        Path trainFile = prepDir.resolve("train_2m.txt");
        if (!Files.exists(trainFile)) {
            trainFile = prepDir.resolve("train.txt");
        }
        System.out.println("Train file: " + trainFile);

        // Load dev and test (these are small enough for RAM)
        System.out.println("Loading dev + test...");
        long t0 = System.nanoTime();
        List<LabeledSentence> dev = readCapped(
                prepDir.resolve("dev.txt"), 100_000);
        List<LabeledSentence> test = readCapped(
                prepDir.resolve("test.txt"), 200_000);
        System.out.printf(Locale.US,
                "Loaded: dev=%,d (%d langs)  "
                        + "test=%,d (%d langs)  [%.1f s]%n",
                dev.size(), countLangs(dev),
                test.size(), countLangs(test),
                elapsed(t0));

        // Train — streams from disk
        System.out.println(
                "\n=== Phase 2 Training (8k, streaming) ===");
        float adamLr = Float.parseFloat(
                System.getProperty("adamLr", "0.001"));
        int adamT = Integer.parseInt(
                System.getProperty("adamThreads", "1"));
        int sgdT = Integer.parseInt(
                System.getProperty("sgdThreads",
                        String.valueOf(threads)));

        Phase2Trainer trainer =
                new Phase2Trainer(numBuckets)
                        .setAdamLr(adamLr)
                        .setSgdLr(0.01f, 0.001f)
                        .setAdamEpochs(2)
                        .setMaxEpochs(4)
                        .setCheckpointInterval(200_000)
                        .setPatience(2)
                        .setDevSubsampleSize(10_000)
                        .setAdamThreads(adamT)
                        .setSgdThreads(sgdT)
                        .setPreprocessed(true);

        t0 = System.nanoTime();
        trainer.train(trainFile, dev);
        System.out.printf(Locale.US,
                "Training time: %.1f s%n", elapsed(t0));

        // Quantize
        System.out.println("\nQuantizing...");
        t0 = System.nanoTime();
        LinearModel model =
                ModelQuantizer.quantize(trainer);
        System.out.printf(Locale.US,
                "Quantized [%.1f s]%n", elapsed(t0));

        // Evaluate
        System.out.println(
                "Evaluating on test set...");
        t0 = System.nanoTime();
        double testAcc =
                TrainLanguageModel.evaluateQuantized(
                        model, test, false, false);
        System.out.printf(Locale.US,
                "Test accuracy (quantized): %.4f  "
                        + "[%.1f s]%n",
                testAcc, elapsed(t0));

        // Float32 dev macro F1
        Phase2Trainer.F1Result devF1 =
                trainer.evaluateMacroF1(dev);
        System.out.printf(Locale.US,
                "Dev macro F1 (float32): %.4f (%d langs)%n",
                devF1.f1, devF1.numLangs);

        // Save
        if (outputModel != null) {
            Files.createDirectories(
                    outputModel.getParent() != null
                            ? outputModel.getParent()
                            : Paths.get("."));
            try (OutputStream os = new BufferedOutputStream(
                    Files.newOutputStream(outputModel))) {
                model.save(os);
            }
            System.out.printf(Locale.US,
                    "Model saved: %s (%.1f KB)%n",
                    outputModel,
                    Files.size(outputModel) / 1024.0);
        }

        System.out.println("\nDone.");
    }

    /**
     * Reservoir-sample up to {@code maxLines} from a
     * preprocessed file. Guarantees a uniform random
     * sample across the entire file, critical when the
     * file is sorted by language.
     */
    private static List<LabeledSentence> readCapped(
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
