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
     * Read sentence files from a language directory using reservoir sampling
     * to cap at {@code maxPerLang} sentences. This reads through the entire
     * file but only keeps a fixed-size sample in memory.
     */
    static void readLanguageDirSampled(Path langDir, String language,
                                       int maxPerLang,
                                       List<LabeledSentence> allSentences)
            throws IOException {
        // Reservoir sampling: read all lines, keep at most maxPerLang
        List<LabeledSentence> reservoir = new ArrayList<>(maxPerLang);
        Random rng = new Random(language.hashCode()); // deterministic per language
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
                        count++;
                        if (reservoir.size() < maxPerLang) {
                            reservoir.add(new LabeledSentence(language, text));
                        } else {
                            // Replace with decreasing probability
                            int j = rng.nextInt(count);
                            if (j < maxPerLang) {
                                reservoir.set(j, new LabeledSentence(language, text));
                            }
                        }
                    }
                }
            }
        }
        allSentences.addAll(reservoir);
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
                    sentences.add(new LabeledSentence(language, text));
                }
            }
        }
    }
}
