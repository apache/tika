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
 */
public class CorpusReader {

    private CorpusReader() {
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
     * When the candidate pool exceeds this multiple of {@code maxPerLang} the
     * reservoir is statistically well-mixed and we stop reading early.
     * Keeps large-language files (e.g. 500k-line English) from dominating I/O
     * when only a small sample is needed.
     */
    private static final int RESERVOIR_EARLY_STOP_FACTOR = 10;

    /**
     * Read sentence files from a language directory using reservoir sampling
     * to cap at {@code maxPerLang} sentences.  Reading stops as soon as
     * {@code RESERVOIR_EARLY_STOP_FACTOR * maxPerLang} candidates have been
     * seen, which is sufficient to produce a well-mixed reservoir while
     * avoiding a full scan of very large files.
     */
    static void readLanguageDirSampled(Path langDir, String language,
                                       int maxPerLang,
                                       List<LabeledSentence> allSentences)
            throws IOException {
        List<LabeledSentence> reservoir = new ArrayList<>(maxPerLang);
        Random rng = new Random(language.hashCode()); // deterministic per language
        int count = 0;
        final int earlyStop = maxPerLang * RESERVOIR_EARLY_STOP_FACTOR;
        boolean done = false;

        try (DirectoryStream<Path> files = Files.newDirectoryStream(langDir, "*.txt")) {
            for (Path file : files) {
                if (done) {
                    break;
                }
                try (BufferedReader reader = Files.newBufferedReader(file,
                        StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int tab = line.indexOf('\t');
                        if (tab < 0) {
                            continue;
                        }
                        String doc = line.substring(tab + 1);
                        for (String part : doc.split("\\\\n")) {
                            String text = part.trim();
                            if (text.length() < MIN_SENTENCE_CHARS) {
                                continue;
                            }
                            count++;
                            if (reservoir.size() < maxPerLang) {
                                reservoir.add(new LabeledSentence(language, text));
                            } else {
                                int j = rng.nextInt(count);
                                if (j < maxPerLang) {
                                    reservoir.set(j, new LabeledSentence(language, text));
                                }
                            }
                            if (count >= earlyStop) {
                                done = true;
                                break;
                            }
                        }
                        if (done) {
                            break;
                        }
                    }
                }
            }
        }
        allSentences.addAll(reservoir);
    }

    private static final int MIN_SENTENCE_CHARS = 20;

    /**
     * Read a single MADLAD-format sentence file.
     * Each line: {@code lineNumber\tdocument text}
     * Documents contain sentences separated by the literal two-character
     * sequence {@code \n} (backslash + n). Lines without a tab or with
     * empty text are skipped. Sentence fragments shorter than
     * {@link #MIN_SENTENCE_CHARS} characters are also skipped.
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
                String doc = line.substring(tab + 1);
                for (String part : doc.split("\\\\n")) {
                    String text = part.trim();
                    if (text.length() >= MIN_SENTENCE_CHARS) {
                        sentences.add(new LabeledSentence(language, text));
                    }
                }
            }
        }
    }
}
