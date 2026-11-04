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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/** A tiny synthetic corpus, built data, gold halves and real-decision sample for trainer tests. */
final class HashedTrainingFixture {

    static final String[] RU = ("в на по из за от до под над при без для про через после перед между "
            + "город страна река история война мир наука школа книга язык народ время год жизнь работа "
            + "человек дом земля море поле лес гора небо солнце вода огонь большой новый старый русский "
            + "первый последний главный известный был была было стал стала говорит пишет живёт работает").split(" ");
    static final String[] FR = ("le la les un une des du de et à en dans pour avec sur par été être "
            + "ville pays rivière histoire guerre paix science école livre langue peuple temps année vie "
            + "travail homme maison terre mer champ forêt montagne ciel soleil eau feu grand nouveau vieux "
            + "français première dernière célèbre était devenu écrit habite où très déjà même côté").split(" ");

    final Path corpus;
    final Path built;
    final Path gold;
    final Path sample;
    final Path sampleDocs;

    HashedTrainingFixture(Path root) throws IOException {
        corpus = Files.createDirectories(root.resolve("corpus"));
        built = Files.createDirectories(root.resolve("built"));
        Random r = new Random(5027);
        writeLanguage("rus", RU, Character.UnicodeScript.CYRILLIC, "cyrillic", r);
        writeLanguage("fra", FR, Character.UnicodeScript.LATIN, "latin", r);

        Path g = Files.createDirectories(root.resolve("gold"));
        List<String> rows = new ArrayList<>();
        rows.add("path\tsplit\tlabel\thalf\ttext_quality");
        for (int i = 0; i < 24; i++) {
            String half = i % 2 == 0 ? "even" : "odd";
            boolean ru = i % 3 != 0;
            String cs = ru ? (i % 4 == 1 ? "KOI8-R" : "windows-1251") : (i % 4 == 0 ? "UTF-8" : "windows-1252");
            boolean meta = i % 5 != 0;
            String name = String.format(Locale.ROOT, "docs/p%02d.html", i);
            write(g.resolve(name), page(ru ? RU : FR, r, meta ? cs : null).getBytes(Charset.forName(cs)));
            rows.add(name + "\ttrain\t" + cs + "\t" + half + "\t" + (i == 8 ? "damaged" : "clean"));
        }
        gold = g.resolve("halves.tsv");
        Files.write(gold, rows, StandardCharsets.UTF_8);

        Path s = Files.createDirectories(root.resolve("sample"));
        sampleDocs = Files.createDirectories(s.resolve("docs"));
        List<String> srows = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            boolean ru = i % 2 == 0;
            String cs = ru ? "windows-1251" : "UTF-8";
            String name = String.format(Locale.ROOT, "s%02d.html", i);
            write(sampleDocs.resolve(name), page(ru ? RU : FR, r, cs).getBytes(Charset.forName(cs)));
            srows.add(name + "\t" + cs);
        }
        sample = s.resolve("sample.tsv");
        Files.write(sample, srows, StandardCharsets.UTF_8);
    }

    static String sentence(String[] words, Random r) {
        StringBuilder sb = new StringBuilder();
        int n = 8 + r.nextInt(10);
        for (int i = 0; i < n; i++) {
            String w = words[r.nextInt(words.length)];
            sb.append(i == 0 ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : " " + w);
        }
        return sb.append('.').toString();
    }

    static String page(String[] words, Random r, String metaCharset) {
        StringBuilder sb = new StringBuilder("<html><head>");
        if (metaCharset != null) {
            sb.append("<meta charset=\"").append(metaCharset).append("\">");
        }
        sb.append("<title>").append(sentence(words, r)).append("</title></head><body>");
        for (int i = 0; i < 12; i++) {
            sb.append("<p>").append(sentence(words, r)).append(' ').append(sentence(words, r)).append("</p>\n");
        }
        return sb.append("</body></html>").toString();
    }

    private void writeLanguage(String lang, String[] words, Character.UnicodeScript script, String bucket,
                               Random r) throws IOException {
        Path dir = Files.createDirectories(corpus.resolve(lang));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String s = sentence(words, r);
            // the builder's line rewriting: runs of spaces, an escaped newline, a fullwidth letter
            if (i % 10 == 0) {
                s = s.replace(" ", "   ");
            } else if (i % 10 == 1) {
                s = s + "\\n" + sentence(words, r);
            } else if (i % 10 == 2) {
                s = s + " ＡＢＣ";
            }
            lines.add(i + "\t" + s);
        }
        Files.write(dir.resolve("sentences_wikipedia.txt"), lines, StandardCharsets.UTF_8);
        List<String> sentences = new ArrayList<>();
        BuildJunkTrainingData.loadSentences(dir, Long.MAX_VALUE, 1, 1.0, script, 0.0, sentences);
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(
                Files.newOutputStream(built.resolve(bucket + ".train.gz"))), StandardCharsets.UTF_8))) {
            for (String s : sentences) {
                w.write(s);
                w.newLine();
            }
        }
    }

    private static void write(Path p, byte[] b) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, b);
    }
}
