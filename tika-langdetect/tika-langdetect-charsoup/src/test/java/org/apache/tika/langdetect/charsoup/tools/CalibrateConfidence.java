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

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Empirically calibrates per-class confidence by computing the distribution
 * of (max_logit, margin) over true-positive predictions on the Flores dev set.
 *
 * For each sentence:
 *   - predicted label = argmax(logits)
 *   - max_logit       = logits[predicted]
 *   - margin          = logits[0] - logits[1]  (top-1 minus top-2)
 *
 * True positives (ground truth == predicted) build the per-class calibration
 * distributions.  False positives are then z-scored against the predicted
 * class's true-positive distribution to surface "implausible" predictions.
 *
 * Usage:
 *   CalibrateConfidence <flores_tsv> <model_bin> [focus_lang ...]
 *
 * If focus_lang values are given, detailed false-positive z-scores are printed
 * only for those (Flores) language prefixes (e.g. "bod", "dzo").
 * Without focus_lang, a full per-class calibration table is printed.
 */
public class CalibrateConfidence {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(
                    "Usage: CalibrateConfidence <flores_tsv> <model_bin> "
                    + "[focus_lang ...]");
            System.exit(1);
        }
        String floresPath = args[0];
        String modelPath  = args[1];
        List<String> focusLangs = args.length > 2
                ? Arrays.asList(Arrays.copyOfRange(args, 2, args.length))
                : List.of();

        CharSoupModel model;
        try (DataInputStream dis = new DataInputStream(
                new FileInputStream(modelPath))) {
            model = CharSoupModel.load(dis);
        }
        String[] labels = model.getLabels();
        FeatureExtractor ext = model.createExtractor();
        int[] buf = new int[model.getNumBuckets()];

        // label → model index
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            labelIndex.put(labels[i], i);
        }

        // per-class accumulators for true positives
        // stats[classIdx] = list of (maxLogit, margin)
        Map<Integer, List<float[]>> tpStats = new HashMap<>();

        // false positives we want to z-score:
        // list of (floresLang, text, predictedIdx, maxLogit, margin)
        List<Object[]> focusFP = new ArrayList<>();

        List<String> lines = Files.readAllLines(Paths.get(floresPath));
        for (String line : lines) {
            int tab = line.indexOf('\t');
            if (tab < 0) continue;
            String floresLang = line.substring(0, tab);
            String text = line.substring(tab + 1).trim();
            if (text.isEmpty()) continue;

            // map flores lang code (e.g. "kor_Hang") → model label (e.g. "kor")
            String trueLabel = floresLang.contains("_")
                    ? floresLang.substring(0, floresLang.indexOf('_'))
                    : floresLang;

            ext.extractFromPreprocessed(text, buf, true);
            float[] logits = model.predictLogits(buf);

            // rank
            Integer[] idx = new Integer[logits.length];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Float.compare(logits[b], logits[a]));

            int predIdx   = idx[0];
            float maxLogit = logits[predIdx];
            float margin   = logits[idx[0]] - logits[idx[1]];
            String predLabel = labels[predIdx];

            Integer trueIdx = labelIndex.get(trueLabel);
            boolean correct = trueIdx != null && trueIdx == predIdx;

            if (correct) {
                tpStats.computeIfAbsent(predIdx, k -> new ArrayList<>())
                       .add(new float[]{maxLogit, margin});
            }

            // collect focus-language false positives
            if (!focusLangs.isEmpty()) {
                boolean isFocus = focusLangs.stream()
                        .anyMatch(fl -> floresLang.startsWith(fl));
                if (isFocus && !correct) {
                    focusFP.add(new Object[]{
                            floresLang, text, predIdx, maxLogit, margin});
                }
            }
        }

        // compute per-class mean/std for maxLogit and margin
        // classStats[classIdx] = {meanLogit, stdLogit, meanMargin, stdMargin, n}
        Map<Integer, double[]> classStats = new HashMap<>();
        for (var e : tpStats.entrySet()) {
            List<float[]> samples = e.getValue();
            int n = samples.size();
            double sumL = 0, sumM = 0;
            for (float[] s : samples) {
                sumL += s[0];
                sumM += s[1];
            }
            double meanL = sumL / n, meanM = sumM / n;
            double varL = 0, varM = 0;
            for (float[] s : samples) {
                varL += (s[0] - meanL) * (s[0] - meanL);
                varM += (s[1] - meanM) * (s[1] - meanM);
            }
            double stdL = n > 1 ? Math.sqrt(varL / (n - 1)) : 0;
            double stdM = n > 1 ? Math.sqrt(varM / (n - 1)) : 0;
            classStats.put(e.getKey(), new double[]{meanL, stdL, meanM, stdM, n});
        }

        if (focusLangs.isEmpty()) {
            // print full calibration table sorted by label
            System.out.printf(Locale.US, "%-12s  %5s  %8s  %7s  %8s  %7s%n",
                    "Label", "N_tp",
                    "logit_mu", "logit_σ", "margin_mu", "margin_σ");
            System.out.println("─".repeat(62));
            for (int i = 0; i < labels.length; i++) {
                double[] st = classStats.get(i);
                if (st == null) continue;
                System.out.printf(Locale.US,
                        "%-12s  %5.0f  %8.2f  %7.2f  %8.2f  %7.2f%n",
                        labels[i], st[4], st[0], st[1], st[2], st[3]);
            }
        } else {
            // print focus false-positive z-scores
            System.out.printf(Locale.US,
                    "%-14s  %-12s  %9s  %7s  %9s  %7s  %60s%n",
                    "FloresLang", "Predicted",
                    "maxLogit", "z_logit", "margin", "z_marg", "Text");
            System.out.println("─".repeat(120));
            for (Object[] fp : focusFP) {
                String floresLang = (String) fp[0];
                String text       = (String) fp[1];
                int predIdx       = (int)    fp[2];
                float maxLogit    = (float)  fp[3];
                float margin      = (float)  fp[4];

                double[] st = classStats.get(predIdx);
                double zLogit = st != null && st[1] > 0
                        ? (maxLogit - st[0]) / st[1] : Double.NaN;
                double zMarg  = st != null && st[3] > 0
                        ? (margin   - st[2]) / st[3] : Double.NaN;

                System.out.printf(Locale.US,
                        "%-14s  %-12s  %9.2f  %7.2f  %9.2f  %7.2f  %.60s%n",
                        floresLang, labels[predIdx],
                        maxLogit, zLogit, margin, zMarg, text);
            }

            // also show the calibration stats for predicted classes involved
            System.out.println();
            System.out.println("Calibration stats for predicted classes:");
            System.out.printf(Locale.US, "%-12s  %5s  %8s  %7s  %8s  %7s%n",
                    "Label", "N_tp",
                    "logit_mu", "logit_σ", "margin_mu", "margin_σ");
            System.out.println("─".repeat(62));
            focusFP.stream()
                    .mapToInt(fp -> (int) fp[2])
                    .distinct()
                    .forEach(predIdx -> {
                        double[] st = classStats.get(predIdx);
                        if (st == null) return;
                        System.out.printf(Locale.US,
                                "%-12s  %5.0f  %8.2f  %7.2f  %8.2f  %7.2f%n",
                                labels[predIdx], st[4],
                                st[0], st[1], st[2], st[3]);
                    });
        }
    }
}
