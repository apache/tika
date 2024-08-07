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
package org.apache.tika.eval.app.tools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LeipzigSampler {

    public static void main(String[] args) throws Exception {
        int sentsPerLanguage = 10;
        Path leipzigDir = Paths.get(args[0]);
        Path sampleFile = Paths.get(args[1]);
        LeipzigSampler leipzigSampler = new LeipzigSampler();
        try (BufferedWriter writer = Files.newBufferedWriter(sampleFile, StandardCharsets.UTF_8)) {
            leipzigSampler.execute(leipzigDir, sentsPerLanguage, writer);
        }
    }

    private void execute(Path leipzigDir, int sentsPerLang, BufferedWriter writer) throws IOException {
        Map<String, List<Path>> fileMap = LeipzigHelper.getFiles(leipzigDir);
        for (Map.Entry<String, List<Path>> e : fileMap.entrySet()) {
            List<String> sentences = new ArrayList<>();
            for (Path p : e.getValue()) {
                addSentences(p, sentences);
            }
            Collections.shuffle(sentences);
            String lang = e.getKey();
            for (int i = 0; i < sentsPerLang; i++) {
                writer.write(row(lang, sentences.get(i)));
            }
        }
    }

    private void addSentences(Path p, List<String> sentences) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while (line != null) {
                int tab = line.indexOf("\t");
                if (tab > -1) {
                    line = line.substring(tab + 1);
                }
                sentences.add(line);
                line = reader.readLine();
            }
        }
    }

    private String row(String lang, String s) {
        s = s.replaceAll("\\s+", " ");
        return lang + "\t" + s + "\n";
    }
}
