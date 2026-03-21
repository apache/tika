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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.tika.langdetect.charsoup.GenerativeLanguageModel;

/**
 * Recomputes per-language z-score calibration (μ, σ) for an existing GLM
 * binary without rebuilding the n-gram or script tables.
 *
 * <p>Use this when the scoring function changes (e.g. script normalization)
 * but the underlying frequency tables are unchanged, so a full retrain is
 * unnecessary.
 *
 * <p>Usage:
 * <pre>
 *   RecalibrateGlm \
 *       --input   langdetect-generative-v2.bin \
 *       --corpus  /path/to/corpus \
 *       --output  langdetect-generative-v3.bin \
 *       [--max-per-lang 500000]
 * </pre>
 */
public class RecalibrateGlm {

    private static final int DEFAULT_MAX_PER_LANG = 500_000;

    public static void main(String[] args) throws Exception {
        Path input      = null;
        Path corpusDir  = null;
        Path output     = null;
        int  maxPerLang = DEFAULT_MAX_PER_LANG;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input":
                    input = Paths.get(args[++i]);
                    break;
                case "--corpus":
                    corpusDir = Paths.get(args[++i]);
                    break;
                case "--output":
                    output = Paths.get(args[++i]);
                    break;
                case "--max-per-lang":
                    maxPerLang = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (input == null || corpusDir == null || output == null) {
            printUsage();
            System.exit(1);
        }

        System.out.printf(Locale.US, "Loading model: %s%n", input);
        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(input.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }

        List<String> langs = model.getLanguages();
        System.out.printf(Locale.US, "Languages: %d%n", langs.size());

        boolean flatLayout = isFlatLayout(corpusDir);
        System.out.printf(Locale.US, "Corpus layout: %s%n", flatLayout ? "flat" : "Leipzig");

        System.out.println("Calibrating z-scores …");
        for (String lang : langs) {
            String dirName = reverseAlias(lang);
            Path langPath = corpusDir.resolve(dirName);
            if (!Files.exists(langPath)) {
                System.out.printf(Locale.US, "  %-12s  MISSING corpus dir — skipping%n", lang);
                continue;
            }
            double[] stats = calibrateLanguage(model, lang, langPath, flatLayout, maxPerLang);
            model.setStats(lang, (float) stats[0], (float) stats[1]);
            System.out.printf(Locale.US,
                    "  %-12s  μ=%8.4f  σ=%6.4f  (n=%d)%n",
                    lang, stats[0], stats[1], (long) stats[2]);
        }

        System.out.printf(Locale.US, "Writing model to %s …%n", output);
        try (OutputStream os = new FileOutputStream(output.toFile())) {
            model.save(os);
        }
        long bytes = Files.size(output);
        System.out.printf(Locale.US, "Done. Model size: %,.0f KB%n", bytes / 1024.0);
    }

    /** Reverse-lookup: canonical lang code → corpus directory name. */
    private static String reverseAlias(String lang) {
        for (java.util.Map.Entry<String, String> e : CorpusAliases.LANG_MERGE_MAP.entrySet()) {
            if (e.getValue().equals(lang)) return e.getKey();
        }
        return lang;
    }

    private static boolean isFlatLayout(Path corpusDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(corpusDir)) {
            for (Path p : stream) {
                return Files.isRegularFile(p);
            }
        }
        return true;
    }

    private static double[] calibrateLanguage(
            GenerativeLanguageModel model, String lang,
            Path langPath, boolean flat, int maxPerLang) throws IOException {
        long   n    = 0;
        double mean = 0.0;
        double m2   = 0.0;

        List<Path> files = flat
                ? Collections.singletonList(langPath) : listTxtFiles(langPath);

        outer:
        for (Path file : files) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text;
                    if (flat) {
                        text = line.trim();
                    } else {
                        int tab = line.indexOf('\t');
                        if (tab < 0) continue;
                        text = line.substring(tab + 1).trim();
                    }
                    if (text.isEmpty()) continue;
                    float s = model.score(text, lang);
                    if (Float.isNaN(s)) continue;
                    n++;
                    double delta = s - mean;
                    mean += delta / n;
                    m2   += delta * (s - mean);
                    if (maxPerLang > 0 && n >= maxPerLang) break outer;
                }
            }
        }

        double stdDev = n > 1 ? Math.sqrt(m2 / (n - 1)) : 0.0;
        return new double[]{mean, stdDev, n};
    }

    private static List<Path> listTxtFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) files.add(p);
        }
        Collections.sort(files);
        return files;
    }

    private static void printUsage() {
        System.err.println("Usage: RecalibrateGlm");
        System.err.println("         --input       <existingModel.bin>");
        System.err.println("         --corpus      <corpusDir>");
        System.err.println("         --output      <newModel.bin>");
        System.err.println("         [--max-per-lang <N>]  (default 500000)");
    }
}
