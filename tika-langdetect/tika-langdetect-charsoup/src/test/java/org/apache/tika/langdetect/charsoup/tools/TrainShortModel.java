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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.ShortTextFeatureExtractor;

/**
 * Trains and saves the production short-text language model.
 * Always uses the ShortTextFeatureExtractor feature set
 * (flags 0x0a1: trigrams + word unigrams + 4-grams) at 32 768 buckets.
 *
 * Usage:
 *   TrainShortModel prepDir trainFile outputModel
 *     --allowed-langs file   one lang code per line (# = comment)
 *     --flores file          FLORES-200 dev TSV for post-train eval
 */
public class TrainShortModel {

    private static final int NUM_BUCKETS = 32_768;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: TrainShortModel <prepDir> <trainFile> <outputModel>");
            System.err.println("  --allowed-langs <file>");
            System.err.println("  --flores <file>   FLORES-200 dev TSV");
            System.exit(1);
        }

        Path prepDir     = Paths.get(args[0]);
        Path trainFile   = Paths.get(args[1]);
        Path outputModel = Paths.get(args[2]);

        Path allowedFile = null;
        Path floresFile  = null;

        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--allowed-langs":
                    allowedFile = Paths.get(args[++i]);
                    break;
                case "--flores":
                    floresFile = Paths.get(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
                    System.exit(1);
            }
        }

        Set<String> allowedLangs = loadAllowedLangs(allowedFile);
        List<LabeledSentence> floresData =
                AblationRunner.loadFlores(floresFile);

        int threads = Runtime.getRuntime().availableProcessors();
        System.out.printf(Locale.ROOT,
                "Training short-text model: buckets=%d  flags=0x%03x  threads=%d%n",
                NUM_BUCKETS, ShortTextFeatureExtractor.FEATURE_FLAGS, threads);
        System.out.println("Train file    : " + trainFile);
        System.out.println("Allowed langs : "
                + (allowedLangs != null ? allowedLangs.size() + " langs" : "all"));
        System.out.println("Output        : " + outputModel);
        System.out.println();

        long t0 = System.nanoTime();
        List<LabeledSentence> dev =
                AblationRunner.readReservoir(prepDir.resolve("dev.txt"), 100_000, allowedLangs);
        System.out.printf(Locale.ROOT, "Dev loaded: %,d sentences (%d langs)%n%n",
                dev.size(), countLangs(dev));

        Phase2Trainer trainer = new Phase2Trainer(NUM_BUCKETS)
                .setAdamLr(0.001f)
                .setSgdLr(0.01f, 0.001f)
                .setAdamEpochs(2)
                .setMaxEpochs(6)
                .setCheckpointInterval(500_000)
                .setPatience(2)
                .setDevSubsampleSize(10_000)
                .setNumThreads(threads)
                .setVerbose(true)
                .setPreprocessed(true)
                .setUseWordUnigrams(true)
                .setUseTrigrams(true)
                .setUseWordSuffixes(false)
                .setUseWordPrefix(false)
                .setUse4grams(true)
                .setUse5grams(false)
                .setAllowedLangs(allowedLangs);

        trainer.train(trainFile, dev);
        double trainSecs = (System.nanoTime() - t0) / 1e9;
        System.out.printf(Locale.ROOT, "%nTraining complete in %.1f s%n", trainSecs);

        int flags = ShortTextFeatureExtractor.FEATURE_FLAGS;
        CharSoupModel model = ModelQuantizer.quantize(
                trainer.getLabels(),
                trainer.getWeightsClassMajor(),
                trainer.getBiases(),
                trainer.getNumBuckets(),
                flags);

        if (outputModel.getParent() != null) {
            Files.createDirectories(outputModel.getParent());
        }
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(outputModel))) {
            model.save(os);
        }
        System.out.println("Model saved: " + outputModel);
        System.out.printf(Locale.ROOT, "  classes=%d  buckets=%d  flags=0x%03x%n",
                model.getNumClasses(), model.getNumBuckets(), model.getFeatureFlags());

        if (floresData != null) {
            Set<String> known = new HashSet<>(trainer.getLabelIndex().keySet());
            List<LabeledSentence> ff = floresData.stream()
                    .filter(s -> known.contains(s.getLanguage()))
                    .collect(Collectors.toList());
            System.out.println();
            for (int len : new int[]{20, 50, 100}) {
                List<LabeledSentence> trunc =
                        CompareDetectors.truncate(ff, len);
                double f1 = trainer.evaluateMacroF1(trunc).f1;
                System.out.printf(Locale.ROOT, "  FLORES @%-4d macro-F1: %.2f%%%n",
                        len, 100 * f1);
            }
        }
    }

    private static Set<String> loadAllowedLangs(Path file) throws Exception {
        if (file == null) {
            return null;
        }
        Set<String> set = new HashSet<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    set.add(line);
                }
            }
        }
        return set;
    }

    private static int countLangs(List<LabeledSentence> data) {
        Set<String> langs = new HashSet<>();
        for (LabeledSentence s : data) {
            langs.add(s.getLanguage());
        }
        return langs.size();
    }
}
