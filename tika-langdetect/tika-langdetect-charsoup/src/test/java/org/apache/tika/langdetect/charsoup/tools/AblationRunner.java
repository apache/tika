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
import java.io.BufferedWriter;
import java.io.OutputStream;
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

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

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

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println(
                    "Usage: AblationRunner <prepDir> <trainFile> "
                    + "<numBuckets> [flags] [outputFile]");
            System.err.println("  --skip         enable skip bigrams");
            System.err.println("  --trigrams     enable character trigrams");
            System.err.println("  --suffix       enable word suffix (last 3 chars)");
            System.err.println("  --suffix4      enable word suffix (last 4 chars)");
            System.err.println("  --prefix       enable word prefix (first 3 chars)");
            System.err.println("  --no-words     disable word unigrams");
            System.err.println("  --save-model <path>  quantize and save model to disk");
            System.exit(1);
        }

        Path prepDir   = Paths.get(args[0]);
        Path trainFile = Paths.get(args[1]);
        int  numBuckets = Integer.parseInt(args[2]);

        boolean skipBigrams     = false;
        boolean useTrigrams     = false;
        boolean useSuffixes     = false;
        boolean useSuffix4      = false;
        boolean usePrefix       = false;
        boolean useWordUnigrams = true;
        boolean useCharUnigrams = false;
        Path outFile   = null;
        Path modelFile = null;
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--skip":
                    skipBigrams = true;
                    break;
                case "--trigrams":
                    useTrigrams = true;
                    break;
                case "--suffix":
                    useSuffixes = true;
                    break;
                case "--suffix4":
                    useSuffix4 = true;
                    break;
                case "--prefix":
                    usePrefix = true;
                    break;
                case "--no-words":
                    useWordUnigrams = false;
                    break;
                case "--unigrams":
                    useCharUnigrams = true;
                    break;
                case "--save-model":
                    i++;
                    modelFile = Paths.get(args[i]);
                    break;
                default:
                    outFile = Paths.get(args[i]);
                    break;
            }
        }

        int threads = Runtime.getRuntime().availableProcessors();

        String baseName = String.format(Locale.US, "flat-%dk", numBuckets / 1024);

        // Experimental name: baseline + active flags
        String expName = baseName;
        if (skipBigrams)      expName += "+skip";
        if (useTrigrams)      expName += "+tri";
        if (useSuffixes)      expName += "+suf";
        if (useSuffix4)       expName += "+suf4";
        if (usePrefix)        expName += "+pre";
        if (!useWordUnigrams) expName += "+nw";
        if (useCharUnigrams)  expName += "+uni";

        boolean isBaseline = expName.equals(baseName);

        System.out.println("Train file:  " + trainFile);
        System.out.println("Output file: " + (outFile   != null ? outFile   : "(stdout only)"));
        System.out.println("Model file:  " + (modelFile != null ? modelFile : "(not saved)"));
        System.out.printf(Locale.US, "Threads: %d%n%n", threads);

        // Load dev + test via reservoir sampling
        System.out.println("Loading dev + test...");
        long t0 = System.nanoTime();
        List<LabeledSentence> dev = readReservoir(
                prepDir.resolve("dev.txt"), 100_000);
        Path testPath = prepDir.resolve("test.txt");
        if (!Files.exists(testPath)) {
            testPath = prepDir.resolve("test_raw.txt");
        }
        List<LabeledSentence> test = readReservoir(testPath, 200_000);
        System.out.printf(Locale.US,
                "Loaded: dev=%,d (%d langs)  test=%,d (%d langs)  [%.1f s]%n%n",
                dev.size(), countLangs(dev),
                test.size(), countLangs(test), elapsed(t0));

        StringBuilder report = new StringBuilder();

        // Table header (printed once)
        String header = String.format(Locale.US,
                "%-26s  %10s  %8s  %8s  %8s  %7s  %10s%n",
                "config", "numBuckets",
                "devF1", "devLangs", "testF1", "train_s", "sent/s");
        String sep = "-".repeat(85) + "\n";
        report.append(header).append(sep);
        System.out.print(header);
        System.out.print(sep);

        Phase2Trainer expTrainer;

        if (!isBaseline) {
            // --- Baseline run ---
            System.out.printf(Locale.US,
                    "=== Baseline: %s ===%n%n", baseName);
            t0 = System.nanoTime();
            Phase2Trainer baseline = buildTrainer(numBuckets, threads,
                    false, false, false, false, false, true, false);
            baseline.train(trainFile, dev);
            runConfig(baseline, baseName, numBuckets, dev, test,
                    elapsed(t0), report, false);

            report.append("\n");
            System.out.printf(Locale.US,
                    "%n=== Experimental: %s ===%n%n", expName);
        }

        // --- Experimental (or sole) run ---
        t0 = System.nanoTime();
        expTrainer = buildTrainer(numBuckets, threads,
                skipBigrams, useTrigrams, useSuffixes,
                useSuffix4, usePrefix, useWordUnigrams,
                useCharUnigrams);
        expTrainer.train(trainFile, dev);
        runConfig(expTrainer, expName, numBuckets, dev, test,
                elapsed(t0), report, true);

        report.append("\nDone.\n");
        System.out.println("\nDone.");

        if (outFile != null) {
            try (BufferedWriter w = Files.newBufferedWriter(
                    outFile, StandardCharsets.UTF_8)) {
                w.write(report.toString());
            }
            System.out.println("Results written to: " + outFile);
        }

        if (modelFile != null) {
            if (modelFile.getParent() != null) {
                Files.createDirectories(modelFile.getParent());
            }
            CharSoupModel model = ModelQuantizer.quantize(expTrainer);
            try (OutputStream os = new BufferedOutputStream(
                    Files.newOutputStream(modelFile))) {
                model.save(os);
            }
            System.out.println("Model saved to: " + modelFile);
        }
    }

    /** Build and configure a Phase2Trainer with the given feature flags. */
    private static Phase2Trainer buildTrainer(
            int numBuckets, int threads,
            boolean skipBigrams, boolean useTrigrams,
            boolean useSuffixes, boolean useSuffix4,
            boolean usePrefix, boolean useWordUnigrams,
            boolean useCharUnigrams) {
        return new Phase2Trainer(numBuckets)
                .setAdamLr(0.001f)
                .setSgdLr(0.01f, 0.001f)
                .setAdamEpochs(2)
                .setMaxEpochs(6)
                .setCheckpointInterval(500_000)
                .setPatience(2)
                .setDevSubsampleSize(10_000)
                .setNumThreads(threads)
                .setVerbose(false)
                .setPreprocessed(true)
                .setUseSkipBigrams(skipBigrams)
                .setUseTrigrams(useTrigrams)
                .setUseWordSuffixes(useSuffixes)
                .setUseWordSuffix4(useSuffix4)
                .setUseWordPrefix(usePrefix)
                .setUseWordUnigrams(useWordUnigrams)
                .setUseCharUnigrams(useCharUnigrams);
    }

    /**
     * Evaluate a trained model and append results to the report.
     *
     * @param showPerLang if true, appends the full per-language dev F1 table
     */
    private static void runConfig(
            Phase2Trainer trainer, String name,
            int numBuckets,
            List<LabeledSentence> dev,
            List<LabeledSentence> test,
            double trainSecs,
            StringBuilder report,
            boolean showPerLang) {

        Map<String, Double> perLang = new HashMap<>();
        Phase2Trainer.F1Result devF1 = trainer.evaluateMacroF1(dev, perLang);

        long inferStart = System.nanoTime();
        Phase2Trainer.F1Result testF1 = trainer.evaluateMacroF1(test);
        double inferSecs = (System.nanoTime() - inferStart) / 1e9;
        double sentPerSec = test.size() / inferSecs;

        String row = String.format(Locale.US,
                "%-26s  %10d  %8.4f  %8d  %8.4f  %7.1f  %10.0f%n",
                name, numBuckets,
                devF1.f1, devF1.numLangs, testF1.f1, trainSecs, sentPerSec);
        System.out.print(row);
        report.append(row);

        // Length-stratified test F1
        StringBuilder lenBlock = new StringBuilder();
        lenBlock.append(String.format(Locale.US,
                "%nLength-stratified test F1 for %s:%n", name));
        lenBlock.append(String.format(Locale.US,
                "  %8s  %8s  %9s%n", "maxChars", "testF1", "testLangs"));
        lenBlock.append("  ").append("-".repeat(30)).append("\n");
        for (int maxChars : CompareDetectors.EVAL_LENGTHS) {
            List<LabeledSentence> truncated =
                    CompareDetectors.truncate(test, maxChars);
            Phase2Trainer.F1Result r = trainer.evaluateMacroF1(truncated);
            String tag = maxChars == Integer.MAX_VALUE
                    ? "full" : String.valueOf(maxChars);
            lenBlock.append(String.format(Locale.US,
                    "  %8s  %8.4f  %9d%n", tag, r.f1, r.numLangs));
        }
        System.out.print(lenBlock);
        report.append(lenBlock);

        // Confusion analysis at 20 chars
        List<LabeledSentence> test20 = CompareDetectors.truncate(test, 20);
        String confBlock = dumpTopConfusions(trainer, test20, 20, 35);
        System.out.print(confBlock);
        report.append(confBlock);

        if (!showPerLang) {
            return;
        }

        // Per-language dev F1 (experimental config only)
        StringBuilder perLangBlock = new StringBuilder();
        perLangBlock.append(String.format(Locale.ROOT,
                "%nPer-language dev F1 for %s:%n", name));
        perLangBlock.append(String.format(Locale.US,
                "  %-12s  %9s%n", "lang", "devF1"));
        perLangBlock.append("  ").append("-".repeat(24)).append("\n");
        perLang.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> perLangBlock.append(String.format(Locale.US,
                        "  %-12s  %8.2f%%%n",
                        e.getKey(), 100 * e.getValue())));
        System.out.print(perLangBlock);
        report.append(perLangBlock);
    }

    private static String dumpTopConfusions(
            Phase2Trainer trainer,
            List<LabeledSentence> data,
            int maxChars, int topN) {
        FeatureExtractor ext = trainer.getExtractor();
        int[] featureBuf = new int[trainer.getNumBuckets()];
        float[] logitBuf = new float[trainer.getNumClasses()];
        Map<String, Integer> pairCounts = new HashMap<>();
        for (LabeledSentence s : data) {
            String pred = trainer.predictBuffered(
                    s.getText(), ext, featureBuf, logitBuf);
            if (!pred.equals(s.getLanguage())) {
                String key = s.getLanguage() + " -> " + pred;
                pairCounts.merge(key, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries =
                new ArrayList<>(pairCounts.entrySet());
        entries.sort(
                Map.Entry.<String, Integer>comparingByValue()
                        .reversed());
        int show = Math.min(topN, entries.size());
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "%nTop %d confusions at maxChars=%d:%n",
                show, maxChars));
        sb.append(String.format(Locale.US,
                "  %-34s  %6s%n", "true -> predicted", "count"));
        sb.append("  ").append("-".repeat(44)).append("\n");
        for (int i = 0; i < show; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            sb.append(String.format(Locale.US,
                    "  %-34s  %6d%n",
                    e.getKey(), e.getValue()));
        }
        return sb.toString();
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
