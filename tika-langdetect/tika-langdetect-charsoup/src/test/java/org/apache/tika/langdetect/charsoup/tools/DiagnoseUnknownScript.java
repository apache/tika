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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.tika.langdetect.charsoup.CharSoupModel;
import org.apache.tika.langdetect.charsoup.FeatureExtractor;

/**
 * Prints top-N logits and softmax scores for Flores test sentences
 * belonging to languages that had 0% F1 (e.g. bod, dzo) to assess
 * whether mispredictions are confident or marginal.
 */
public class DiagnoseUnknownScript {

    private static final int TOP_N = 5;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println(
                    "Usage: DiagnoseUnknownScript <flores_tsv> <model_bin> <lang1> [lang2 ...]");
            System.exit(1);
        }
        String floresPath = args[0];
        String modelPath  = args[1];
        String[] targetLangs = Arrays.copyOfRange(args, 2, args.length);

        CharSoupModel model;
        try (DataInputStream dis = new DataInputStream(
                new FileInputStream(modelPath))) {
            model = CharSoupModel.load(dis);
        }
        String[] labels = model.getLabels();
        FeatureExtractor ext = model.createExtractor();
        int[] buf = new int[model.getNumBuckets()];

        // build label→index map
        java.util.Map<String, Integer> labelIndex = new java.util.HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            labelIndex.put(labels[i], i);
        }

        List<String> lines = Files.readAllLines(Paths.get(floresPath));

        for (String targetPrefix : targetLangs) {
            System.out.println("═══════════════════════════════════════════════");
            System.out.println("Language prefix: " + targetPrefix);
            System.out.println("═══════════════════════════════════════════════");

            int sentenceNum = 0;
            for (String line : lines) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String langCode = line.substring(0, tab);
                // match e.g. "bod" against "bod_Tibt"
                if (!langCode.startsWith(targetPrefix)) continue;

                String text = line.substring(tab + 1).trim();
                if (text.isEmpty()) continue;

                sentenceNum++;
                if (sentenceNum > 5) break;  // first 5 sentences is enough

                ext.extractFromPreprocessed(text, buf, true);
                float[] logits = model.predictLogits(buf);
                float[] probs  = CharSoupModel.softmax(logits.clone());

                // rank by logit descending
                Integer[] idx = new Integer[labels.length];
                for (int i = 0; i < idx.length; i++) idx[i] = i;
                Arrays.sort(idx, (a, b) -> Float.compare(logits[b], logits[a]));

                // max logit and entropy
                float maxLogit = logits[idx[0]];
                double entropy = 0.0;
                for (float p : probs) {
                    if (p > 0f) entropy -= p * Math.log(p);
                }
                double maxEntropy = Math.log(labels.length);

                System.out.printf(Locale.US,
                        "%nSentence %d [%s]: %.60s...%n", sentenceNum, langCode,
                        text);
                System.out.printf(Locale.US,
                        "  max_logit=%.3f  entropy=%.3f / %.3f (%.1f%% of max)%n",
                        maxLogit, entropy, maxEntropy,
                        100.0 * entropy / maxEntropy);
                System.out.printf(Locale.US, "  %-12s  %8s  %8s%n",
                        "Label", "Logit", "Softmax%");
                System.out.println("  " + "─".repeat(34));
                for (int r = 0; r < TOP_N; r++) {
                    int i = idx[r];
                    System.out.printf(Locale.US, "  %-12s  %8.3f  %7.2f%%%n",
                            labels[i], logits[i], probs[i] * 100f);
                }
                // also show the true language if it's in the model
                String trueLabel = langCode.contains("_")
                        ? langCode.substring(0, langCode.indexOf('_'))
                        : langCode;
                Integer trueIdx = labelIndex.get(trueLabel);
                if (trueIdx != null) {
                    int rank = 0;
                    for (int r = 0; r < idx.length; r++) {
                        if (idx[r] == trueIdx) {
                            rank = r + 1;
                            break;
                        }
                    }
                    if (rank > TOP_N) {
                        System.out.printf(Locale.US,
                                "  ... [true label '%s' is rank %d, "
                                + "logit=%.3f, softmax=%.4f%%]%n",
                                trueLabel, rank, logits[trueIdx],
                                probs[trueIdx] * 100f);
                    }
                } else {
                    System.out.printf(Locale.ROOT, "  [true label '%s' not in model]%n",
                            trueLabel);
                }
            }
            System.out.println();
        }
    }
}
