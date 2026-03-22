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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.apache.tika.langdetect.charsoup.CharSoupLanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Quick diagnostic: run short English snippets through the detector,
 * print top-2 results with scores and entropy to understand margin behaviour.
 */
public class MarginDiagnostic {
    public static void main(String[] args) throws Exception {
        String floresPath = args.length > 0
                ? args[0]
                : System.getProperty("user.home") + "/datasets/flores-200/flores200_dev.tsv";
        int truncLen = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        String filterLang = args.length > 2 ? args[2] : "eng";

        CharSoupLanguageDetector det = new CharSoupLanguageDetector();
        det.loadModels();

        System.out.printf(Locale.ROOT, "%-22s  %-6s %7s  %-6s %7s  %-6s %7s  %s%n",
                "SNIPPET", "TOP", "SCORE", "2ND", "SCORE", "CONF", "ENTROPY", "RESULT");
        System.out.println("-".repeat(100));

        int count = 0;
        int unkCount = 0;
        int wrongCount = 0;
        int okCount = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(floresPath, StandardCharsets.UTF_8))) {
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
                float entropy = det.getDistributionEntropy();

                String top = results.size() > 0 ? results.get(0).getLanguage() : "?";
                float topScore = results.size() > 0 ? results.get(0).getRawScore() : 0;
                String topConf = results.size() > 0 ? results.get(0).getConfidence().name() : "?";
                String second = results.size() > 1 ? results.get(1).getLanguage() : "-";
                float secScore = results.size() > 1 ? results.get(1).getRawScore() : 0;

                String mark;
                if (top.isEmpty()) {
                    mark = "UNK";
                    unkCount++;
                } else if (top.equals(filterLang)) {
                    mark = "OK";
                    okCount++;
                } else {
                    mark = "WRONG:" + top;
                    wrongCount++;
                }

                System.out.printf(Locale.ROOT, "%-22s  %-6s %7.4f  %-6s %7.4f  %-6s %7.3f  %s%n",
                        text, top, topScore, second, secScore, topConf, entropy, mark);
                count++;
            }
        }
        System.out.println("-".repeat(100));
        System.out.printf(Locale.ROOT, "Total: %d  OK: %d (%.1f%%)  UNK: %d  WRONG: %d%n",
                count, okCount, 100.0 * okCount / count, unkCount, wrongCount);
    }
}
