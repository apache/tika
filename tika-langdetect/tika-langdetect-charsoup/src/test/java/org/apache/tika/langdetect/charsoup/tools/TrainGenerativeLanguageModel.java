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
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.apache.tika.langdetect.charsoup.ScriptAwareFeatureExtractor;

/**
 * Trains a {@link GenerativeLanguageModel} from a Leipzig-format corpus.
 *
 * <h3>Corpus format</h3>
 * <pre>
 *   corpusDir/
 *     eng/
 *       sentences.txt   (lineNum TAB sentence)
 *     zho/
 *       sentences.txt
 *     jpn/
 *       sentences.txt
 *     ...
 * </pre>
 * Each directory name is used as the language code.  Any {@code .txt} file
 * directly under a language directory is read; each line must contain at
 * least one tab, and the text after the first tab is the sentence.
 *
 * <h3>CJK detection</h3>
 * A language is treated as CJK if at least 60% of the letter codepoints
 * in a random sample of sentences are CJK/kana characters.  You can
 * override this with an explicit {@code --cjk} list on the command line.
 *
 * <h3>Usage</h3>
 * <pre>
 *   java TrainGenerativeLanguageModel \
 *       --corpus  /path/to/Leipzig-corpus \
 *       --output  generative.bin \
 *       [--max-per-lang 500000] \
 *       [--add-k 0.01] \
 *       [--cjk zho,jpn,cmn]
 * </pre>
 */
public class TrainGenerativeLanguageModel {

    private static final int   DEFAULT_MAX_PER_LANG  = 500_000;
    private static final float DEFAULT_ADD_K         = 0.01f;
    /** Fraction of letter codepoints that must be CJK to classify a language as CJK. */
    private static final float CJK_LETTER_THRESHOLD  = 0.60f;
    /** Number of sentences used to probe the script of an unknown language. */
    private static final int   CJK_PROBE_SENTENCES   = 500;

    public static void main(String[] args) throws Exception {
        Path   corpus     = null;
        Path   output     = null;
        int    maxPerLang = DEFAULT_MAX_PER_LANG;
        float  addK       = DEFAULT_ADD_K;
        List<String> forceCjk = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--corpus":
                    corpus = Paths.get(args[++i]);
                    break;
                case "--output":
                    output = Paths.get(args[++i]);
                    break;
                case "--max-per-lang":
                    maxPerLang = Integer.parseInt(args[++i]);
                    break;
                case "--add-k":
                    addK = Float.parseFloat(args[++i]);
                    break;
                case "--cjk": {
                    for (String code : args[++i].split(",")) {
                        forceCjk.add(code.trim());
                    }
                    break;
                }
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (corpus == null || output == null) {
            printUsage();
            System.exit(1);
        }

        new TrainGenerativeLanguageModel().run(corpus, output, maxPerLang, addK, forceCjk);
    }

    private void run(Path corpusDir, Path outputPath,
                     int maxPerLang, float addK,
                     List<String> forceCjkList) throws IOException {

        // Support two corpus layouts:
        //   flat:  corpusDir/{langCode}          (one sentence per line, no tab prefix)
        //   Leipzig: corpusDir/{langCode}/*.txt  (lineNum TAB sentence)
        boolean flatLayout = isFlatLayout(corpusDir);
        System.out.printf(Locale.US, "Corpus layout: %s%n", flatLayout ? "flat" : "Leipzig");

        List<Path> langPaths = listLangPaths(corpusDir, flatLayout);
        System.out.printf(Locale.US, "Found %d languages in %s%n", langPaths.size(), corpusDir);

        GenerativeLanguageModel.Builder builder = GenerativeLanguageModel.builder();

        for (Path langPath : langPaths) {
            String lang = langPath.getFileName().toString();
            boolean cjk = forceCjkList.contains(lang)
                    || probeCjk(langPath, flatLayout, CJK_PROBE_SENTENCES);

            System.out.printf(Locale.US, "  %-12s  %s%n", lang, cjk ? "CJK" : "non-CJK");
            builder.registerLanguage(lang, cjk);
        }

        System.out.println("Accumulating n-gram counts …");
        long totalSentences = 0;

        for (Path langPath : langPaths) {
            String lang    = langPath.getFileName().toString();
            long   counted = feedLanguage(builder, lang, langPath, flatLayout, maxPerLang);
            totalSentences += counted;
            System.out.printf(Locale.US, "  %-12s  %,d sentences%n", lang, counted);
        }

        System.out.printf(Locale.US, "Total sentences: %,d%n", totalSentences);
        System.out.printf(Locale.US, "Building model (add-k=%.4f) …%n", addK);

        GenerativeLanguageModel model = builder.build(addK);

        // Second pass: score training data to compute per-language μ and σ
        System.out.println("Calibrating z-scores (second pass) …");
        for (Path langPath : langPaths) {
            String lang = langPath.getFileName().toString();
            double[] stats = calibrateLanguage(model, lang, langPath, flatLayout, maxPerLang);
            model.setStats(lang, (float) stats[0], (float) stats[1]);
            System.out.printf(Locale.US,
                    "  %-12s  μ=%8.4f  σ=%6.4f  (n=%d)%n",
                    lang, stats[0], stats[1], (long) stats[2]);
        }

        System.out.printf(Locale.US, "Writing model to %s …%n", outputPath);
        try (OutputStream os = new FileOutputStream(outputPath.toFile())) {
            model.save(os);
        }

        long bytes = Files.size(outputPath);
        System.out.printf(Locale.US, "Done. Model size: %,.0f KB%n", bytes / 1024.0);
    }

    // ---- Corpus helpers ----

    /**
     * Returns true if the corpus uses the flat layout (files named by language
     * code, one sentence per line) rather than the Leipzig layout (subdirectories
     * containing {@code *.txt} files with {@code lineNum TAB sentence} lines).
     */
    private static boolean isFlatLayout(Path corpusDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(corpusDir)) {
            for (Path p : stream) {
                return Files.isRegularFile(p);
            }
        }
        return true;
    }

    /**
     * List all language paths in the corpus directory, sorted.
     * For flat layout: regular files. For Leipzig layout: subdirectories.
     */
    private static List<Path> listLangPaths(Path corpusDir,
                                             boolean flat) throws IOException {
        List<Path> paths = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(corpusDir,
                p -> flat ? Files.isRegularFile(p) : Files.isDirectory(p))) {
            for (Path p : stream) {
                paths.add(p);
            }
        }
        Collections.sort(paths);
        return paths;
    }

    /**
     * Feed up to {@code maxPerLang} sentences from {@code langPath} into the builder.
     *
     * @return number of sentences consumed
     */
    private static long feedLanguage(GenerativeLanguageModel.Builder builder,
                                     String lang, Path langPath,
                                     boolean flat,
                                     int maxPerLang) throws IOException {
        long count = 0;
        if (flat) {
            try (BufferedReader reader = Files.newBufferedReader(langPath,
                    StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line.trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    builder.addSample(lang, text);
                    count++;
                    if (maxPerLang > 0 && count >= maxPerLang) {
                        break;
                    }
                }
            }
        } else {
            List<Path> files = listTxtFiles(langPath);
            outer:
            for (Path file : files) {
                try (BufferedReader reader = Files.newBufferedReader(file,
                        StandardCharsets.UTF_8)) {
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
                        builder.addSample(lang, text);
                        count++;
                        if (maxPerLang > 0 && count >= maxPerLang) {
                            break outer;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Score every training sentence for {@code lang} against the built model
     * and return {@code [mean, stdDev, count]} using Welford's online algorithm.
     */
    private static double[] calibrateLanguage(
            GenerativeLanguageModel model, String lang,
            Path langPath, boolean flat, int maxPerLang) throws IOException {
        long   n    = 0;
        double mean = 0.0;
        double m2   = 0.0;

        if (flat) {
            try (BufferedReader reader = Files.newBufferedReader(
                    langPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text = line.trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    float s = model.score(text, lang);
                    if (Float.isNaN(s)) {
                        continue;
                    }
                    n++;
                    double delta = s - mean;
                    mean += delta / n;
                    m2   += delta * (s - mean);
                    if (maxPerLang > 0 && n >= maxPerLang) {
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
                        float s = model.score(text, lang);
                        if (Float.isNaN(s)) {
                            continue;
                        }
                        n++;
                        double delta = s - mean;
                        mean += delta / n;
                        m2   += delta * (s - mean);
                        if (maxPerLang > 0 && n >= maxPerLang) {
                            break outer;
                        }
                    }
                }
            }
        }

        double stdDev = n > 1 ? Math.sqrt(m2 / (n - 1)) : 0.0;
        return new double[]{mean, stdDev, n};
    }

    /**
     * Probe a language path to decide whether it is CJK.
     */
    private static boolean probeCjk(Path langPath, boolean flat,
                                     int maxSentences) throws IOException {
        long cjkLetters   = 0;
        long totalLetters = 0;
        int  sentences    = 0;

        List<Path> files = flat
                ? Collections.singletonList(langPath) : listTxtFiles(langPath);

        outer:
        for (Path file : files) {
            try (BufferedReader reader = Files.newBufferedReader(file,
                    StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String text;
                    if (flat) {
                        text = line.trim();
                    } else {
                        int tab = line.indexOf('\t');
                        if (tab < 0) continue;
                        text = line.substring(tab + 1);
                    }
                    if (text.isEmpty()) continue;
                    int i = 0;
                    while (i < text.length()) {
                        int cp = text.codePointAt(i);
                        i += Character.charCount(cp);
                        if (Character.isLetter(cp)) {
                            totalLetters++;
                            if (ScriptAwareFeatureExtractor.isCjkOrKana(
                                    Character.toLowerCase(cp))) {
                                cjkLetters++;
                            }
                        }
                    }
                    sentences++;
                    if (sentences >= maxSentences) {
                        break outer;
                    }
                }
            }
        }

        if (totalLetters == 0) {
            return false;
        }
        return (double) cjkLetters / totalLetters >= CJK_LETTER_THRESHOLD;
    }

    private static List<Path> listTxtFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path p : stream) {
                files.add(p);
            }
        }
        Collections.sort(files);
        return files;
    }

    private static void printUsage() {
        System.err.println("Usage: TrainGenerativeLanguageModel");
        System.err.println("         --corpus <corpusDir>");
        System.err.println("         --output <outputFile>");
        System.err.println("         [--max-per-lang <N>]   (default 500000)");
        System.err.println("         [--add-k <k>]           (default 0.01)");
        System.err.println("         [--cjk lang1,lang2,...] (override auto-detection)");
    }
}
