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
 * Scores every sentence in a training corpus against its own language model
 * and reports how many would be dropped at various z-score thresholds.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java CorpusFilterReport \
 *       --model  generative.bin \
 *       --corpus /path/to/pool_filtered \
 *       [--max-per-lang 500000] \
 *       [--show-drops 10]   (print N worst-scoring sentences per language)
 * </pre>
 */
public class CorpusFilterReport {

    private static final int DEFAULT_MAX_PER_LANG = 500_000;
    private static final int DEFAULT_SHOW_DROPS   = 0;

    public static void main(String[] args) throws Exception {
        Path   modelPath  = null;
        Path   corpusPath = null;
        int    maxPerLang = DEFAULT_MAX_PER_LANG;
        int    showDrops  = DEFAULT_SHOW_DROPS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--model":
                    modelPath = Paths.get(args[++i]);
                    break;
                case "--corpus":
                    corpusPath = Paths.get(args[++i]);
                    break;
                case "--max-per-lang":
                    maxPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--show-drops":
                    showDrops = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(1);
            }
        }

        if (modelPath == null || corpusPath == null) {
            System.err.println(
                    "Usage: CorpusFilterReport --model <bin> --corpus <dir> "
                    + "[--max-per-lang N] [--show-drops N]");
            System.exit(1);
        }

        GenerativeLanguageModel model;
        try (InputStream is = new FileInputStream(modelPath.toFile())) {
            model = GenerativeLanguageModel.load(is);
        }

        boolean flat = isFlatLayout(corpusPath);
        List<Path> langPaths = listLangPaths(corpusPath, flat);

        System.out.printf(Locale.US,
                "%-14s  %8s  %8s  %8s  %8s  %8s  %8s%n",
                "Language", "Total", "z<-2", "z<-3", "z<-4", "z<-2%", "z<-3%");
        System.out.println("-".repeat(80));

        long grandTotal = 0;
        long grandDrop2 = 0;
        long grandDrop3 = 0;
        long grandDrop4 = 0;

        for (Path langPath : langPaths) {
            String lang = langPath.getFileName().toString();
            if (!model.getLanguages().contains(lang)) {
                continue;
            }

            List<ScoredLine> scored = scoreLang(
                    model, lang, langPath, flat, maxPerLang);

            long total = scored.size();
            long drop2 = 0;
            long drop3 = 0;
            long drop4 = 0;
            for (ScoredLine sl : scored) {
                if (sl.z < -2) {
                    drop2++;
                }
                if (sl.z < -3) {
                    drop3++;
                }
                if (sl.z < -4) {
                    drop4++;
                }
            }

            System.out.printf(Locale.US,
                    "%-14s  %,8d  %,8d  %,8d  %,8d  %7.2f%%  %7.2f%%%n",
                    lang, total, drop2, drop3, drop4,
                    100.0 * drop2 / total, 100.0 * drop3 / total);

            if (showDrops > 0 && drop3 > 0) {
                scored.sort((a, b) -> Float.compare(a.z, b.z));
                int n = (int) Math.min(showDrops, drop3);
                for (int i = 0; i < n; i++) {
                    ScoredLine sl = scored.get(i);
                    String preview = sl.text.length() > 80
                            ? sl.text.substring(0, 80) + "…" : sl.text;
                    System.out.printf(Locale.US,
                            "    z=%6.2f  %s%n", sl.z, preview);
                }
            }

            grandTotal += total;
            grandDrop2 += drop2;
            grandDrop3 += drop3;
            grandDrop4 += drop4;
        }

        System.out.println("-".repeat(80));
        System.out.printf(Locale.US,
                "%-14s  %,8d  %,8d  %,8d  %,8d  %7.2f%%  %7.2f%%%n",
                "TOTAL", grandTotal, grandDrop2, grandDrop3, grandDrop4,
                100.0 * grandDrop2 / grandTotal, 100.0 * grandDrop3 / grandTotal);
    }

    private static List<ScoredLine> scoreLang(
            GenerativeLanguageModel model, String lang,
            Path langPath, boolean flat, int maxPerLang) throws Exception {

        List<ScoredLine> result = new ArrayList<>();

        if (flat) {
            try (BufferedReader reader = Files.newBufferedReader(
                    langPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line.trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    float z = model.zScore(text, lang);
                    if (!Float.isNaN(z)) {
                        result.add(new ScoredLine(text, z));
                    }
                    if (maxPerLang > 0 && result.size() >= maxPerLang) {
                        break;
                    }
                }
            }
        } else {
            List<Path> files = listTxtFiles(langPath);
            outer:
            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(
                        file, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab < 0) {
                            continue;
                        }
                        String text = line.substring(tab + 1).trim();
                        if (text.isEmpty()) {
                            continue;
                        }
                        float z = model.zScore(text, lang);
                        if (!Float.isNaN(z)) {
                            result.add(new ScoredLine(text, z));
                        }
                        if (maxPerLang > 0 && result.size() >= maxPerLang) {
                            break outer;
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isFlatLayout(Path dir) throws Exception {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                return Files.isRegularFile(p);
            }
        }
        return true;
    }

    private static List<Path> listLangPaths(Path dir, boolean flat) throws Exception {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir,
                p -> flat ? Files.isRegularFile(p) : Files.isDirectory(p))) {
            for (Path p : stream) {
                paths.add(p);
            }
        }
        Collections.sort(paths);
        return paths;
    }

    private static List<Path> listTxtFiles(Path dir) throws Exception {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        Collections.sort(files);
        return files;
    }

    private static class ScoredLine {
        final String text;
        final float  z;

        ScoredLine(String text, float z) {
            this.text = text;
            this.z = z;
        }
    }
}
