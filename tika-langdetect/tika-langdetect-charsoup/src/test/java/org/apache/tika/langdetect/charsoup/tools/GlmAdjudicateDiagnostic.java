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

import java.io.*;
import java.util.List;

import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;
import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Diagnostic: when the discriminative model is uncertain (@20 chars),
 * use the generative model to adjudicate among the top N candidates.
 */
public class GlmAdjudicateDiagnostic {
    public static void main(String[] args) throws Exception {
        String floresPath = args.length > 0
                ? args[0]
                : System.getProperty("user.home") + "/datasets/flores-200/flores200_dev.tsv";
        int truncLen = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        String filterLang = args.length > 2 ? args[2] : "eng";
        int topN = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        float adjudicateBelow = args.length > 4 ? Float.parseFloat(args[4]) : 0.70f;

        CharSoupLanguageDetector det = new CharSoupLanguageDetector();
        det.loadModels();

        GenerativeLanguageModel glm = GenerativeLanguageModel.loadFromClasspath(
                GenerativeLanguageModel.DEFAULT_MODEL_RESOURCE);

        System.out.printf("Adjudicating with GLM when sigmoid(margin) < %.2f, topN=%d%n%n", adjudicateBelow, topN);
        System.out.printf("%-22s  %-6s %7s  %-6s %7s  %-6s  %-8s  %s%n",
                "SNIPPET", "DISC", "SCORE", "GLM", "ZSCORE", "FINAL", "METHOD", "RESULT");
        System.out.println("-".repeat(110));

        int count = 0;
        int okDisc = 0, okGlm = 0, okFinal = 0;
        int wrongDisc = 0, wrongGlm = 0, wrongFinal = 0;
        int adjudicated = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(floresPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 2);
                if (parts.length < 2) continue;
                String lang = parts[0].split("_")[0];
                if (!lang.equals(filterLang)) continue;

                String text = parts[1];
                if (text.length() > truncLen) text = text.substring(0, truncLen);

                det.reset();
                det.addText(text);
                List<LanguageResult> results = det.detectAll();

                String discTop = results.size() > 0 ? results.get(0).getLanguage() : "";
                float discScore = results.size() > 0 ? results.get(0).getRawScore() : 0;

                String finalLang;
                String method;
                String glmPick = "-";
                float glmBestZ = Float.NaN;

                if (discTop.isEmpty() || discScore < adjudicateBelow) {
                    // Use GLM to adjudicate among top N from discriminative model
                    adjudicated++;
                    float bestZ = Float.NEGATIVE_INFINITY;
                    String bestLang = discTop;
                    int checked = 0;
                    for (int i = 0; i < Math.min(topN, results.size()); i++) {
                        String candidate = results.get(i).getLanguage();
                        if (candidate.isEmpty()) continue;
                        float z = glm.zScoreLengthAdjusted(text, candidate);
                        if (!Float.isNaN(z) && z > bestZ) {
                            bestZ = z;
                            bestLang = candidate;
                        }
                        checked++;
                    }
                    glmPick = bestLang;
                    glmBestZ = bestZ;
                    finalLang = bestLang;
                    method = "GLM(" + checked + ")";
                } else {
                    finalLang = discTop;
                    method = "DISC";
                }

                boolean discOk = discTop.equals(filterLang);
                boolean finalOk = finalLang.equals(filterLang);

                if (discOk) okDisc++; else wrongDisc++;
                if (finalOk) okFinal++; else wrongFinal++;

                String result;
                if (finalOk && !discOk) result = "RESCUED";
                else if (!finalOk && discOk) result = "BROKEN";
                else if (finalOk) result = "OK";
                else result = "WRONG:" + finalLang;

                if (!method.equals("DISC")) {
                    System.out.printf("%-22s  %-6s %7.4f  %-6s %7.2f  %-6s  %-8s  %s%n",
                            text, discTop, discScore, glmPick, glmBestZ, finalLang, method, result);
                }
                count++;
            }
        }
        System.out.println("-".repeat(110));
        System.out.printf("Total: %d   Adjudicated: %d (%.1f%%)%n", count, adjudicated, 100.0 * adjudicated / count);
        System.out.printf("Disc-only:  OK=%d (%.1f%%)  WRONG=%d%n", okDisc, 100.0 * okDisc / count, wrongDisc);
        System.out.printf("With GLM:   OK=%d (%.1f%%)  WRONG=%d%n", okFinal, 100.0 * okFinal / count, wrongFinal);
        System.out.printf("Delta: %+d correct (%.2f pp)%n", okFinal - okDisc, 100.0 * (okFinal - okDisc) / count);
    }
}
