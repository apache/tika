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
package org.apache.tika.ml.junkdetect.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import org.apache.tika.ml.junkdetect.HashedJunkModel;

/**
 * Training units from the {@link BuildJunkTrainingData} output: sentences, and sentences joined
 * into ~2 KB documents, capped per (script, language).  The built files carry no language, so
 * each sentence is looked up by the hash of its normalized text in the source corpus.
 */
final class WikipediaUnits {

    static final String NO_LANGUAGE = "?";

    /** One training text. */
    static final class Unit {
        final String script;
        final String language;
        final boolean doc;
        final String text;

        Unit(String script, String language, boolean doc, String text) {
            this.script = script;
            this.language = language;
            this.doc = doc;
            this.text = text;
        }
    }

    private WikipediaUnits() {
    }

    /** Normalized sentence hash to language directory name, over every sentence file. */
    static Map<Long, String> languageMap(Path corpus) throws IOException {
        Map<Long, String> lang = new HashMap<>();
        List<Path> dirs = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(corpus, Files::isDirectory)) {
            ds.forEach(dirs::add);
        }
        Collections.sort(dirs);
        for (Path l : dirs) {
            for (String name : new String[]{"sentences_wikipedia.txt", "sentences_madlad.txt"}) {
                Path f = l.resolve(name);
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                try (BufferedReader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    String s;
                    while ((s = r.readLine()) != null) {
                        for (String part : BuildJunkTrainingData.lineParts(s)) {
                            lang.put(hash(HashedJunkModel.normalize(part)), l.getFileName().toString());
                        }
                    }
                }
            }
        }
        return lang;
    }

    /**
     * Units of one split ({@code train}, {@code dev}, {@code test}), in a fixed order: files by
     * name, sentences shuffled by {@code seed}.
     */
    static List<Unit> units(Path built, String split, int maxSentences, int maxDocs, int docChars,
                            Map<Long, String> lang, long seed) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(built, "*." + split + ".gz")) {
            ds.forEach(files::add);
        }
        Collections.sort(files);
        List<Unit> out = new ArrayList<>();
        for (Path f : files) {
            String script = f.getFileName().toString().replace("." + split + ".gz", "")
                    .toUpperCase(Locale.ROOT);
            List<String> lines = lines(f);
            Collections.shuffle(lines, new Random(seed + script.hashCode()));
            Map<String, Integer> sentences = new HashMap<>();
            Map<String, Integer> docs = new HashMap<>();
            Map<String, StringBuilder> buf = new HashMap<>();
            for (String s : lines) {
                String lg = lang.getOrDefault(hash(s), NO_LANGUAGE);
                if (sentences.merge(lg, 1, Integer::sum) <= maxSentences) {
                    out.add(new Unit(script, lg, false, s));
                }
                StringBuilder sb = buf.computeIfAbsent(lg, k -> new StringBuilder());
                sb.append(s).append(' ');
                if (sb.length() > docChars) {
                    if (docs.merge(lg, 1, Integer::sum) <= maxDocs) {
                        out.add(new Unit(script, lg, true, sb.toString().trim()));
                    }
                    sb.setLength(0);
                }
            }
        }
        return out;
    }

    static List<String> lines(Path gz) throws IOException {
        List<String> r = new ArrayList<>();
        try (BufferedReader b = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(gz)), StandardCharsets.UTF_8))) {
            String s;
            while ((s = b.readLine()) != null) {
                if (!s.isEmpty()) {
                    r.add(s);
                }
            }
        }
        return r;
    }

    /** 64-bit FNV-1a over UTF-16 units. */
    static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h = (h ^ s.charAt(i)) * 0x100000001b3L;
        }
        return h;
    }
}
