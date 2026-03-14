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
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Measures how score mean and stddev vary with text length for selected
 * languages. Used to decide whether z-scores need length normalization
 * at runtime.
 *
 * <p>For each language, truncates training sentences to various character
 * lengths, scores them, and reports per-bucket (μ, σ, n). If σ follows
 * 1/√(charLen), a simple correction factor suffices at runtime.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java LengthCalibrationReport \
 *       --model  generative.bin \
 *       --corpus /path/to/pool_filtered \
 *       --langs  eng,fra,zho,jpn,ara,kor \
 *       [--max-per-lang 50000]
 * </pre>
 */
public class LengthCalibrationReport {

    private static final int   DEFAULT_MAX = 50_000;
    private static final int[] CHAR_LENGTHS = {10, 20, 30, 50, 75, 100, 150, 200, 500, 99999};

    public static void main(String[] args) throws Exception {
        Path   modelPath  = null;
        Path   corpusPath = null;
        String langsArg   = "eng,fra,zho,jpn,ara";
        int    max        = DEFAULT_MAX;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--corpus":
                    corpusPath = Paths.get(args[++i]);
                    break;
                case "--langs":
                    langsArg = args[++i];
                    break;
                case "--max-per-lang":
                    max = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }

        if (modelPath == null || corpusPath == null) {
            System.err.println(
                    "Usage: LengthCalibrationReport --model <bin> --corpus <dir> "
                    + "[--langs eng,fra,zho] [--max-per-lang 50000]");
            System.exit(1);
        }

        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(modelPath.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }

        String[] langs = langsArg.split(",");

        for (String lang : langs) {
            lang = lang.trim();
            if (!model.getLanguages().contains(lang)) {
                System.err.println("Skipping unknown language: " + lang);
                continue;
            }

            Path langFile = corpusPath.resolve(lang);
            if (!Files.exists(langFile)) {
                System.err.println("No corpus file for: " + lang);
                continue;
            }

            System.out.printf(Locale.US, "%n=== %s ===%n", lang);
            System.out.printf(Locale.US,
                    "%-10s  %8s  %10s  %10s  %12s  %12s%n",
                    "MaxChars", "N", "μ(score)", "σ(score)",
                    "σ*√(len/50)", "μ(z-full)");
            System.out.println("-".repeat(70));

            // Read sentences once
            String[] sentences = readSentences(langFile, max);

            for (int maxLen : CHAR_LENGTHS) {
                // Welford's online algorithm
                long   n    = 0;
                double mean = 0.0;
                double m2   = 0.0;
                double zSum = 0.0;

                for (String sentence : sentences) {
                    String text = sentence.length() > maxLen
                            ? sentence.substring(0, maxLen) : sentence;
                    float score = model.score(text, lang);
                    if (Float.isNaN(score)) {
                        continue;
                    }
                    n++;
                    double delta = score - mean;
                    mean += delta / n;
                    m2   += delta * (score - mean);

                    float z = model.zScore(text, lang);
                    if (!Float.isNaN(z)) {
                        zSum += z;
                    }
                }

                double stdDev = n > 1 ? Math.sqrt(m2 / (n - 1)) : 0.0;
                // If σ ~ 1/√len, then σ*√(len/50) should be roughly constant
                double normalized = stdDev * Math.sqrt((double) Math.min(maxLen, 200) / 50.0);
                double meanZ = n > 0 ? zSum / n : 0.0;

                String label = maxLen >= 99999 ? "full" : String.valueOf(maxLen);
                System.out.printf(Locale.US,
                        "%-10s  %,8d  %10.4f  %10.4f  %12.4f  %12.4f%n",
                        label, n, mean, stdDev, normalized, meanZ);
            }
        }
    }

    private static String[] readSentences(Path file, int max) throws Exception {
        Map<Integer, String> lines = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null && idx < max) {
                String text = line.trim();
                if (!text.isEmpty()) {
                    lines.put(idx++, text);
                }
            }
        }
        return lines.values().toArray(new String[0]);
    }
}
