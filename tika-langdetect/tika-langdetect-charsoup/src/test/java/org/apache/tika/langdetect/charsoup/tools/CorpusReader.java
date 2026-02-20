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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reads Leipzig-format sentence files from a directory tree.
 * <p>
 * Expected structure:
 * <pre>
 *   corpusDir/
 *     eng/
 *       sentences.txt     (lineNum\tsentence\n)
 *     deu/
 *       sentences.txt
 *     ...
 * </pre>
 * <p>
 * The directory name is used as the ISO 639-3 language code.
 * Each sentence file has tab-delimited lines: {@code lineNumber\tsentence text}.
 * Files ending in {@code .txt} under each language directory are read.
 * <p>
 * Lines containing three or more consecutive tilde characters ({@code ~~~}) are
 * rejected as web-crawl noise: they indicate paywalled/redacted content where
 * the body text was replaced by tildes but the page was still tagged with a
 * language label. This pattern is particularly prevalent in Breton (bre) MADLAD
 * data, where French blog posts were mislabeled due to Breton-language navigation
 * wrappers on otherwise French pages.
 */
public class CorpusReader {

    /** Lines containing this many or more consecutive tildes are discarded as crawl noise. */
    private static final String TILDE_NOISE_MARKER = "~~~";

    private CorpusReader() {
    }

    private static boolean isNoisyLine(String text) {
        return text.contains(TILDE_NOISE_MARKER);
    }

    /**
     * Split a sentence on literal {@code \n} escape sequences that corpus writers
     * embed to represent paragraph breaks within a single tab-delimited field.
     * Each non-empty, non-noisy segment becomes a separate training example.
     * This is critical for languages like Dhivehi (dv) whose MADLAD entries begin
     * with a romanized Latin headline followed by a Thaana-script body — without
     * splitting, the 20-char evaluation window always hits the Latin prefix.
     */
    private static void addSegments(String language, String text,
                                    List<LabeledSentence> out) {
        if (!text.contains("\\n")) {
            if (!isNoisyLine(text)) {
                out.add(new LabeledSentence(language, text));
            }
            return;
        }
        for (String seg : text.split("\\\\n")) {
            seg = seg.trim();
            if (!seg.isEmpty() && !isNoisyLine(seg)) {
                out.add(new LabeledSentence(language, seg));
            }
        }
    }

    /**
     * Read all labeled sentences from the corpus directory.
     *
     * @param corpusDir root directory containing per-language subdirectories
     * @return list of labeled sentences
     * @throws IOException if reading fails
     */
    public static List<LabeledSentence> readAll(Path corpusDir) throws IOException {
        return readAll(corpusDir, 0);
    }

    /**
     * Read labeled sentences from the corpus directory, optionally sampling
     * at most {@code maxPerLang} sentences per language using reservoir sampling.
     * This avoids loading the entire corpus into memory when the on-disk corpus
     * is much larger than the training budget.
     *
     * @param corpusDir  root directory containing per-language subdirectories
     * @param maxPerLang maximum sentences per language (0 = unlimited)
     * @return list of labeled sentences
     * @throws IOException if reading fails
     */
    public static List<LabeledSentence> readAll(Path corpusDir, int maxPerLang)
            throws IOException {
        List<LabeledSentence> sentences = new ArrayList<>();
        try (DirectoryStream<Path> langDirs = Files.newDirectoryStream(corpusDir,
                Files::isDirectory)) {
            for (Path langDir : langDirs) {
                String language = langDir.getFileName().toString();
                if (maxPerLang > 0) {
                    readLanguageDirSampled(langDir, language, maxPerLang, sentences);
                } else {
                    readLanguageDir(langDir, language, sentences);
                }
            }
        }
        return sentences;
    }

    /**
     * Read all sentence files from a single language directory.
     */
    static void readLanguageDir(Path langDir, String language,
                                List<LabeledSentence> sentences) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(langDir, "*.txt")) {
            for (Path file : files) {
                readSentenceFile(file, language, sentences);
            }
        }
    }

    /**
     * Read sentence files from a language directory using reservoir sampling
     * to cap at {@code maxPerLang} sentences. This reads through the entire
     * file but only keeps a fixed-size sample in memory.
     * <p>
     * Prefer {@link #readLanguageDirHead} when the corpus files are already
     * randomly ordered (e.g. written by a reservoir-sampling downloader),
     * since that method stops reading after {@code maxPerLang} lines and is
     * orders of magnitude faster on large files.
     */
    static void readLanguageDirSampled(Path langDir, String language,
                                       int maxPerLang,
                                       List<LabeledSentence> allSentences)
            throws IOException {
        List<LabeledSentence> reservoir = new ArrayList<>(maxPerLang);
        Random rng = new Random(language.hashCode());
        int count = 0;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(langDir, "*.txt")) {
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
                        List<LabeledSentence> segs = new ArrayList<>();
                        addSegments(language, text, segs);
                        for (LabeledSentence seg : segs) {
                            count++;
                            if (reservoir.size() < maxPerLang) {
                                reservoir.add(seg);
                            } else {
                                int j = rng.nextInt(count);
                                if (j < maxPerLang) {
                                    reservoir.set(j, seg);
                                }
                            }
                        }
                    }
                }
            }
        }
        allSentences.addAll(reservoir);
    }

    /**
     * Read the first {@code maxLines} sentences from a language directory,
     * stopping as soon as the limit is reached.
     * <p>
     * This is much faster than {@link #readLanguageDirSampled} when the
     * corpus files are already randomly ordered, because it avoids scanning
     * the entire (potentially multi-gigabyte) file. Use this whenever the
     * corpus was written by a reservoir-sampling downloader such as
     * {@code download_madlad.py}.
     */
    static void readLanguageDirHead(Path langDir, String language,
                                    int maxLines,
                                    List<LabeledSentence> allSentences)
            throws IOException {
        int count = 0;
        outer:
        try (DirectoryStream<Path> files = Files.newDirectoryStream(langDir, "*.txt")) {
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
                        int before = allSentences.size();
                        addSegments(language, text, allSentences);
                        count += allSentences.size() - before;
                        if (count >= maxLines) {
                            break outer;
                        }
                    }
                }
            }
        }
    }

    /**
     * Read a single Leipzig-format sentence file.
     * Each line: {@code lineNumber\tsentence text}
     * Lines without a tab or with empty text are skipped.
     */
    static void readSentenceFile(Path file, String language,
                                 List<LabeledSentence> sentences) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String text = line.substring(tab + 1).trim();
                if (!text.isEmpty()) {
                    addSegments(language, text, sentences);
                }
            }
        }
    }
}
