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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Splits corpus data into train/dev/test sets, stratified by language.
 * <p>
 * The split is done per-language so each language is represented
 * proportionally in all three sets. Default split ratio is 80/10/10.
 * </p>
 * <p>
 * The split files are written to disk as tab-delimited files
 * ({@code language\ttext}) so the split is deterministic and reproducible.
 * </p>
 */
public class DataSplitter {

    public static final String TRAIN_FILE = "train.txt";
    public static final String DEV_FILE = "dev.txt";
    public static final String TEST_FILE = "test.txt";

    private final float trainRatio;
    private final float devRatio;
    // testRatio = 1 - trainRatio - devRatio

    private final long seed;

    public DataSplitter() {
        this(0.8f, 0.1f, 42L);
    }

    public DataSplitter(float trainRatio, float devRatio, long seed) {
        if (trainRatio + devRatio >= 1.0f) {
            throw new IllegalArgumentException(
                    "trainRatio + devRatio must be < 1.0: " + trainRatio + " + " + devRatio);
        }
        this.trainRatio = trainRatio;
        this.devRatio = devRatio;
        this.seed = seed;
    }

    /**
     * Split the given sentences into train/dev/test and write them to the output directory.
     *
     * @param sentences all labeled sentences
     * @param outputDir directory to write train.txt, dev.txt, test.txt
     * @return a SplitResult containing the three lists
     * @throws IOException if writing fails
     */
    public SplitResult splitAndWrite(List<LabeledSentence> sentences, Path outputDir)
            throws IOException {
        SplitResult result = split(sentences);
        Files.createDirectories(outputDir);
        writeFile(outputDir.resolve(TRAIN_FILE), result.train);
        writeFile(outputDir.resolve(DEV_FILE), result.dev);
        writeFile(outputDir.resolve(TEST_FILE), result.test);
        return result;
    }

    /**
     * Split sentences into train/dev/test, stratified by language.
     */
    public SplitResult split(List<LabeledSentence> sentences) {
        // Group by language
        Map<String, List<LabeledSentence>> byLang = new HashMap<>();
        for (LabeledSentence s : sentences) {
            byLang.computeIfAbsent(s.getLanguage(), k -> new ArrayList<>()).add(s);
        }

        List<LabeledSentence> train = new ArrayList<>();
        List<LabeledSentence> dev = new ArrayList<>();
        List<LabeledSentence> test = new ArrayList<>();

        Random rng = new Random(seed);

        for (Map.Entry<String, List<LabeledSentence>> entry : byLang.entrySet()) {
            List<LabeledSentence> langSentences = new ArrayList<>(entry.getValue());
            Collections.shuffle(langSentences, rng);

            int n = langSentences.size();
            int trainEnd = (int) (n * trainRatio);
            int devEnd = trainEnd + (int) (n * devRatio);

            train.addAll(langSentences.subList(0, trainEnd));
            dev.addAll(langSentences.subList(trainEnd, devEnd));
            test.addAll(langSentences.subList(devEnd, n));
        }

        return new SplitResult(train, dev, test);
    }

    /**
     * Read a split file back into labeled sentences.
     */
    public static List<LabeledSentence> readSplitFile(Path file) throws IOException {
        List<LabeledSentence> sentences = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab > 0) {
                sentences.add(new LabeledSentence(line.substring(0, tab),
                        line.substring(tab + 1)));
            }
        }
        return sentences;
    }

    private void writeFile(Path file, List<LabeledSentence> sentences) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (LabeledSentence s : sentences) {
                writer.write(s.getLanguage());
                writer.write('\t');
                writer.write(s.getText());
                writer.newLine();
            }
        }
    }

    public static class SplitResult {
        public final List<LabeledSentence> train;
        public final List<LabeledSentence> dev;
        public final List<LabeledSentence> test;

        SplitResult(List<LabeledSentence> train, List<LabeledSentence> dev,
                    List<LabeledSentence> test) {
            this.train = train;
            this.dev = dev;
            this.test = test;
        }
    }
}
