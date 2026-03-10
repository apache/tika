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
import java.util.List;
import java.util.Locale;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Evaluates the generative model as a training-data filter for a single
 * target language.
 *
 * <p>Constructs a synthetic contaminated corpus from a FLORES-200 TSV:
 * all sentences labelled as {@code --lang} are <em>signal</em> (should be
 * kept); sentences from all other languages present in the model are
 * <em>noise</em> (should be dropped).
 *
 * <p>The filter z-scores each sentence against the target language's model:
 * <pre>
 *   z = (score(sentence, targetLang) - μ) / σ
 *   keep = z >= threshold
 * </pre>
 * where μ and σ are the mean/stddev of scores on the language's training
 * corpus (baked into the model file).
 *
 * <p>Sweeping {@code threshold} traces a precision/recall curve: lower
 * threshold = permissive filter (keeps more, misses more noise); higher
 * threshold = strict filter (drops more noise but may drop real signal).
 *
 * <h3>Usage</h3>
 * <pre>
 *   mvn -pl tika-langdetect/tika-langdetect-charsoup exec:java \
 *       -Dexec.mainClass=...tools.FilterBenchmark \
 *       -Dexec.args="--model /path/generative.bin \
 *                    --test  /path/flores200_dev.tsv \
 *                    --lang  zho \
 *                    [--noise-ratio 1.0] \
 *                    [--steps 20]"
 * </pre>
 */
public class FilterBenchmark {

    private static final double DEFAULT_NOISE_RATIO = 1.0;
    private static final int    DEFAULT_STEPS       = 20;

    public static void main(String[] args) throws Exception {
        Path   modelPath  = null;
        Path   testPath   = null;
        String targetLang = null;
        double noiseRatio = DEFAULT_NOISE_RATIO;
        int    steps      = DEFAULT_STEPS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--test":
                    testPath = Paths.get(args[++i]);
                    break;
                case "--lang":
                    targetLang = args[++i];
                    break;
                case "--noise-ratio":
                    noiseRatio = Double.parseDouble(args[++i]);
                    break;
                case "--steps":
                    steps = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }

        if (modelPath == null || testPath == null || targetLang == null) {
            System.err.println(
                    "Usage: FilterBenchmark --model <bin> --test <flores.tsv> --lang <code> "
                    + "[--noise-ratio 1.0] [--steps 20]");
            System.exit(1);
        }

        System.out.println("Loading model: " + modelPath);
        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(modelPath.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }

        if (!model.getLanguages().contains(targetLang)) {
            System.err.printf(Locale.US,
                    "Language '%s' not found in model. Available: %s%n",
                    targetLang, model.getLanguages());
            System.exit(1);
        }

        System.out.println("Loading test data: " + testPath);
        List<LabeledSentence> all = EvalGenerativeModel.loadTestFile(testPath);

        List<String> signal = new ArrayList<>();
        List<String> noise  = new ArrayList<>();
        for (LabeledSentence s : all) {
            String lang = EvalGenerativeModel.normalizeLang(s.getLanguage());
            if (targetLang.equals(lang)) {
                signal.add(s.getText());
            } else {
                // include all other languages as potential noise
                noise.add(s.getText());
            }
        }

        int noiseCount = (int) Math.min(noise.size(),
                Math.round(signal.size() * noiseRatio));
        noise = noise.subList(0, noiseCount);

        System.out.printf(Locale.US,
                "Target: %s  |  signal: %,d  |  noise: %,d  (%.1fx ratio)%n%n",
                targetLang, signal.size(), noiseCount,
                (double) noiseCount / signal.size());

        // Z-score every sentence against the target language model
        float[] sigZ   = zScores(model, targetLang, signal);
        float[] noiseZ = zScores(model, targetLang, noise);

        // Sweep range: span the full observed z-score distribution
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (float z : sigZ) {
            if (!Float.isNaN(z)) {
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        for (float z : noiseZ) {
            if (!Float.isNaN(z)) {
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }

        System.out.printf(Locale.US,
                "Z-score range: [%.2f, %.2f]  "
                + "(std devs from '%s' training mean)%n%n",
                minZ, maxZ, targetLang);

        System.out.printf(Locale.US,
                "%-12s  %8s  %8s  %8s  %9s  %10s  %10s%n",
                "Z-thresh", "Prec", "Recall", "F1",
                "SigKept%", "NoiseDrop%", "FalseDrop%");
        System.out.println("-".repeat(76));

        float stepSize = (maxZ - minZ) / steps;
        for (int i = 0; i <= steps; i++) {
            float threshold = minZ + i * stepSize;
            printRow(threshold, sigZ, noiseZ, signal.size());
        }
    }

    private static void printRow(float threshold,
                                  float[] sigScores,
                                  float[] noiseScores,
                                  int signalSize) {
        int tp = 0; // noise correctly dropped  (score < threshold)
        int fn = 0; // noise incorrectly kept   (score >= threshold)
        int fp = 0; // signal incorrectly dropped
        int tn = 0; // signal correctly kept

        for (float s : noiseScores) {
            if (Float.isNaN(s) || s < threshold) {
                tp++;
            } else {
                fn++;
            }
        }
        for (float s : sigScores) {
            if (Float.isNaN(s) || s < threshold) {
                fp++;
            } else {
                tn++;
            }
        }

        double precision = (tp + fp) > 0
                ? (double) tp / (tp + fp) : 1.0;
        double recall    = (tp + fn) > 0
                ? (double) tp / (tp + fn) : 0.0;
        double f1        = (precision + recall) > 0
                ? 2 * precision * recall / (precision + recall) : 0.0;
        double keptPct   = 100.0 * tn / signalSize;
        double noisePct  = 100.0 * tp / noiseScores.length;
        double falsePct  = 100.0 * fp / signalSize;

        System.out.printf(Locale.US,
                "%12.4f  %8.3f  %8.3f  %8.3f  %8.1f%%  %9.1f%%  %9.1f%%%n",
                threshold, precision, recall, f1,
                keptPct, noisePct, falsePct);
    }

    /** Z-scores each sentence under the target language model. */
    private static float[] zScores(GenerativeLanguageModel model,
                                    String targetLang,
                                    List<String> sentences) {
        float[] result = new float[sentences.size()];
        for (int i = 0; i < sentences.size(); i++) {
            result[i] = model.zScore(sentences.get(i), targetLang);
        }
        return result;
    }
}
