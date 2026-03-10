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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;

/**
 * Confusion analysis for a CharSoup model against a TSV test file.
 *
 * Two modes:
 *
 *   Recall mode (default):
 *     Filters to rows where true label == targetLang and shows
 *     what the model predicted for them (where does targetLang bleed out).
 *
 *   False-positive mode (--fp flag):
 *     Scans ALL rows, keeps rows where predicted == targetLang but
 *     true label != targetLang, and shows which true labels are
 *     bleeding into targetLang.
 *
 * Prediction goes through the full CharSoupLanguageDetector pipeline
 * (script gating + length-gated confusables + confusable-group collapse),
 * so results reflect production behaviour. The modelFile argument is
 * accepted for API compatibility but ignored; the detector loads its
 * model from the classpath resource slot.
 *
 * Usage:
 *   ConfusionDump <testFile> <modelFile> <targetLang> [maxChars]
 *   ConfusionDump <testFile> <modelFile> <targetLang> --fp [maxChars]
 */
public class ConfusionDump {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println(
                    "Usage: ConfusionDump <testFile> <modelFile> <targetLang> [--fp] [maxChars]");
            System.exit(1);
        }
        String testFile = args[0];
        // args[1] is the model file path — accepted for CLI compatibility but
        // the detector always loads from its classpath resource slot.
        String targetLang = args[2];

        boolean fpMode = false;
        boolean showSentences = false;
        String filterPredicted = null;
        int maxChars = Integer.MAX_VALUE;
        for (int i = 3; i < args.length; i++) {
            if ("--fp".equals(args[i])) {
                fpMode = true;
            } else if ("--show".equals(args[i])) {
                showSentences = true;
            } else if (args[i].startsWith("--predicted=")) {
                filterPredicted = args[i].substring("--predicted=".length());
            } else {
                maxChars = Integer.parseInt(args[i]);
            }
        }

        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();

        if (fpMode) {
            runFalsePositiveMode(testFile, detector, targetLang, maxChars);
        } else {
            runRecallMode(testFile, detector, targetLang, maxChars,
                    showSentences, filterPredicted);
        }
    }

    private static void runRecallMode(String testFile, CharSoupLanguageDetector detector,
            String targetLang, int maxChars, boolean showSentences, String filterPredicted)
            throws Exception {
        int total = 0, correct = 0;
        Map<String, Integer> predicted = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(
                Paths.get(testFile), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String lang = line.substring(0, tab).trim();
                if (!lang.equals(targetLang)) continue;
                String text = line.substring(tab + 1).trim();
                if (text.isEmpty()) continue;
                if (maxChars < text.length()) text = text.substring(0, maxChars);

                String pred = predict(detector, text);
                total++;
                if (pred.equals(targetLang)) correct++;
                predicted.merge(pred, 1, Integer::sum);

                if (showSentences
                        && !pred.equals(targetLang)
                        && (filterPredicted == null || filterPredicted.equals(pred))) {
                    System.out.printf(Locale.US, "[%s→%s] %s%n", targetLang, pred,
                            text.length() > 120 ? text.substring(0, 120) + "…" : text);
                }
            }
        }

        System.out.printf(Locale.US,
                "%nRecall for '%s': %d correct / %d total = %.2f%%%n%n",
                targetLang, correct, total, 100.0 * correct / total);
        printHistogram(predicted, total);
    }

    private static void runFalsePositiveMode(String testFile,
            CharSoupLanguageDetector detector, String targetLang, int maxChars)
            throws Exception {
        int totalFp = 0;
        Map<String, Integer> trueLangs = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(
                Paths.get(testFile), StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) continue;
                String trueLang = line.substring(0, tab).trim();
                if (trueLang.equals(targetLang)) continue;
                String text = line.substring(tab + 1).trim();
                if (text.isEmpty()) continue;
                if (maxChars < text.length()) text = text.substring(0, maxChars);

                String pred = predict(detector, text);
                if (pred.equals(targetLang)) {
                    totalFp++;
                    trueLangs.merge(trueLang, 1, Integer::sum);
                }
            }
        }

        System.out.printf(Locale.US,
                "False positives for '%s': %d sentences from other languages predicted as '%s'%n%n",
                targetLang, totalFp, targetLang);
        printHistogram(trueLangs, totalFp);
    }

    private static String predict(CharSoupLanguageDetector detector, String text) {
        detector.reset();
        detector.addText(text.toCharArray(), 0, text.length());
        List<org.apache.tika.language.detect.LanguageResult> results = detector.detectAll();
        if (results.isEmpty()) return "";
        String lang = results.get(0).getLanguage();
        return lang == null ? "" : lang;
    }

    private static void printHistogram(Map<String, Integer> counts, int total) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        System.out.printf(Locale.US, "  %-16s  %6s  %7s%n", "Language", "Count", "Share");
        System.out.println("  " + "-".repeat(34));
        for (Map.Entry<String, Integer> e : entries) {
            System.out.printf(Locale.US, "  %-16s  %6d  %6.1f%%%n",
                    e.getKey(), e.getValue(),
                    total > 0 ? 100.0 * e.getValue() / total : 0.0);
        }
    }
}
