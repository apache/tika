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
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.tika.langdetect.charsoup.CharSoupDetectorConfig;
import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;
import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Evaluates GLM reranking accuracy on top-N discriminative candidates.
 * <p>
 * For each FLORES-200 dev sentence at each text length, runs the discriminative
 * model (STANDARD strategy, no GLM) to get the top-N ranked candidates, then uses
 * GLM z-scores to rerank them.  Reports the four outcome categories:
 * <ul>
 *   <li><b>DISC_RIGHT / GLM_KEPT</b>  — disc was right, GLM agreed (no harm done)</li>
 *   <li><b>DISC_RIGHT / GLM_BROKE</b> — disc was right, GLM flipped to wrong answer</li>
 *   <li><b>DISC_WRONG / GLM_RESCUED</b> — disc was wrong, correct answer was in top-N,
 *       GLM promoted it</li>
 *   <li><b>DISC_WRONG / GLM_MISSED</b>  — disc was wrong, correct answer was in top-N,
 *       GLM failed to promote it</li>
 *   <li><b>DISC_WRONG / OUT_OF_TOPN</b> — disc was wrong and correct answer wasn't in
 *       top-N; GLM cannot help regardless</li>
 * </ul>
 * Net lift = RESCUED − BROKE (as percentage of total sentences).
 * <p>
 * Usage:
 * <pre>
 *   GlmRerankerEval [floresDevTsv [topN [lengths]]]
 *   e.g.  GlmRerankerEval ~/datasets/flores-200/flores200_dev.tsv 5 20,50,100,200
 * </pre>
 */
public class GlmRerankerEval {

    private static final int[]   DEFAULT_LENGTHS = {20, 50, 100, 200};
    private static final int     DEFAULT_TOP_N   = 5;

    public static void main(String[] args) throws Exception {
        String floresPath = args.length > 0
                ? args[0]
                : System.getProperty("user.home") + "/datasets/flores-200/flores200_dev.tsv";
        int topN = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TOP_N;
        int[] lengths = args.length > 2 ? parseLengths(args[2]) : DEFAULT_LENGTHS;

        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "STANDARD"));
        CharSoupLanguageDetector det = new CharSoupLanguageDetector(cfg);
        det.loadModels();

        GenerativeLanguageModel glm = GenerativeLanguageModel.loadFromClasspath(
                GenerativeLanguageModel.DEFAULT_MODEL_RESOURCE);

        System.out.println("Loading FLORES-200 dev: " + floresPath);
        List<String[]> rows = loadFlores(floresPath);  // [lang, text]
        System.out.printf(Locale.ROOT, "Loaded %d sentences, topN=%d%n%n", rows.size(), topN);

        // Header
        System.out.printf(Locale.ROOT, "%-8s  %8s  %8s  %8s  %8s  %8s  %8s  %8s  %8s  %8s  %8s  %10s%n",
                "length", "total", "discAcc%", "glmAcc%", "netLift%",
                "kept", "broke", "rescued", "missed", "outTopN",
                "adjRate%", "rescued/broke");
        System.out.println("-".repeat(120));

        for (int len : lengths) {
            Counters c = evalAtLength(rows, det, glm, topN, len);
            printRow(len, c);
        }

        System.out.println();

        // Per-language breakdown at @20 (most interesting)
        System.out.println("=== Per-language breakdown @20 chars ===");
        System.out.println("(languages where GLM rescued OR broke >= 3% of sentences)");
        System.out.println();
        evalPerLang(rows, det, glm, topN, 20);
    }

    // ---- evaluation ----

    private static Counters evalAtLength(List<String[]> rows,
                                          CharSoupLanguageDetector det,
                                          GenerativeLanguageModel glm,
                                          int topN, int len) {
        Counters c = new Counters();
        for (String[] row : rows) {
            String trueLang = row[0];
            String text = truncate(row[1], len);

            List<LanguageResult> topResults = getTopN(det, text, topN);
            if (topResults.isEmpty()) {
                c.total++;
                c.outTopN++;
                continue;
            }

            String discPick = topResults.get(0).getLanguage();
            boolean discRight = trueLang.equals(discPick);
            boolean inTopN = topResults.stream()
                    .anyMatch(r -> trueLang.equals(r.getLanguage()));

            String glmPick = rerank(glm, topResults, text);
            boolean glmRight = trueLang.equals(glmPick);

            c.total++;
            if (discRight) {
                c.discRight++;
                if (glmRight) c.kept++;
                else          c.broke++;
            } else if (inTopN) {
                if (glmRight) c.rescued++;
                else          c.missed++;
            } else {
                c.outTopN++;
            }
        }
        return c;
    }

    private static void evalPerLang(List<String[]> rows,
                                     CharSoupLanguageDetector det,
                                     GenerativeLanguageModel glm,
                                     int topN, int len) {
        // Per-language counters
        Map<String, Counters> perLang = new TreeMap<>();
        for (String[] row : rows) {
            String trueLang = row[0];
            String text = truncate(row[1], len);

            List<LanguageResult> topResults = getTopN(det, text, topN);
            Counters c = perLang.computeIfAbsent(trueLang, k -> new Counters());
            c.total++;

            if (topResults.isEmpty()) {
                c.outTopN++;
                continue;
            }

            String discPick = topResults.get(0).getLanguage();
            boolean discRight = trueLang.equals(discPick);
            boolean inTopN = topResults.stream()
                    .anyMatch(r -> trueLang.equals(r.getLanguage()));
            String glmPick = rerank(glm, topResults, text);
            boolean glmRight = trueLang.equals(glmPick);

            if (discRight) {
                c.discRight++;
                if (glmRight) {
                    c.kept++;
                } else {
                    c.broke++;
                }
            } else if (inTopN) {
                if (glmRight) {
                    c.rescued++;
                } else {
                    c.missed++;
                }
            } else {
                c.outTopN++;
            }
        }

        System.out.printf(Locale.ROOT, "%-12s  %8s  %8s  %8s  %8s  %8s  %8s  %8s%n",
                "lang", "total", "discAcc%", "glmAcc%", "netLift%",
                "rescued", "broke", "outTopN");
        System.out.println("-".repeat(90));

        perLang.entrySet().stream()
                .filter(e -> {
                    Counters c = e.getValue();
                    double rescuedPct = 100.0 * c.rescued / c.total;
                    double brokePct   = 100.0 * c.broke   / c.total;
                    return rescuedPct >= 3.0 || brokePct >= 3.0;
                })
                .sorted((a, b) -> {
                    double netA = (double)(a.getValue().rescued - a.getValue().broke) / a.getValue().total;
                    double netB = (double)(b.getValue().rescued - b.getValue().broke) / b.getValue().total;
                    return Double.compare(netB, netA); // descending by net lift
                })
                .forEach(e -> {
                    Counters c = e.getValue();
                    System.out.printf(Locale.ROOT, "%-12s  %8d  %8.2f  %8.2f  %8.2f  %8d  %8d  %8d%n",
                            e.getKey(), c.total,
                            100.0 * c.discRight / c.total,
                            100.0 * (c.discRight - c.broke + c.rescued) / c.total,
                            100.0 * (c.rescued - c.broke) / c.total,
                            c.rescued, c.broke, c.outTopN);
                });
    }

    // ---- GLM reranking ----

    /**
     * Rerank {@code candidates} by GLM z-score and return the top pick.
     * Falls back to the discriminative winner if GLM produces no finite scores.
     */
    private static String rerank(GenerativeLanguageModel glm,
                                  List<LanguageResult> candidates,
                                  String text) {
        String best = candidates.get(0).getLanguage();
        float  bestZ = Float.NEGATIVE_INFINITY;
        for (LanguageResult r : candidates) {
            String lang = r.getLanguage();
            if (lang.isEmpty()) continue;
            float z = glm.zScoreLengthAdjusted(text, lang);
            if (!Float.isNaN(z) && z > bestZ) {
                bestZ = z;
                best  = lang;
            }
        }
        return best;
    }

    // ---- discriminative model ----

    private static List<LanguageResult> getTopN(CharSoupLanguageDetector det,
                                                  String text, int topN) {
        det.reset();
        det.addText(text);
        List<LanguageResult> all = det.detectAll();
        return all.size() <= topN ? all : all.subList(0, topN);
    }

    // ---- output ----

    private static void printRow(int len, Counters c) {
        int discAcc   = c.discRight;
        int glmAcc    = c.discRight - c.broke + c.rescued;
        int adjudicated = c.rescued + c.missed + c.broke + c.kept - c.discRight; // hmm
        // adjudicated = all cases where GLM differed from disc
        int glmChanged = c.rescued + c.broke;
        double adjRate = 100.0 * glmChanged / c.total;
        double rescuedOverBroke = c.broke > 0
                ? (double) c.rescued / c.broke : Double.POSITIVE_INFINITY;

        System.out.printf(Locale.ROOT, "@%-7d  %8d  %8.2f  %8.2f  %+8.2f  %8d  %8d  %8d  %8d  %8d  %8.1f  %10.2f%n",
                len, c.total,
                100.0 * discAcc / c.total,
                100.0 * glmAcc  / c.total,
                100.0 * (c.rescued - c.broke) / c.total,
                c.kept, c.broke, c.rescued, c.missed, c.outTopN,
                adjRate, rescuedOverBroke);
    }

    // ---- helpers ----

    private static String truncate(String text, int len) {
        // Truncate at codepoint boundary
        if (text.length() <= len) return text;
        int cpCount = 0, i = 0;
        while (i < text.length() && cpCount < len) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            cpCount++;
        }
        return text.substring(0, i);
    }

    private static int[] parseLengths(String s) {
        String[] parts = s.split(",");
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) arr[i] = Integer.parseInt(parts[i].trim());
        return arr;
    }

    private static List<String[]> loadFlores(String path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t", 2);
                if (parts.length < 2) continue;
                // Normalize FLORES lang codes (strip script suffix, apply remaps)
                String lang = EvalGenerativeModel.normalizeLang(parts[0].trim());
                rows.add(new String[]{lang, parts[1].trim()});
            }
        }
        return rows;
    }

    // ---- counters ----

    private static class Counters {
        int total, discRight, kept, broke, rescued, missed, outTopN;
    }
}
