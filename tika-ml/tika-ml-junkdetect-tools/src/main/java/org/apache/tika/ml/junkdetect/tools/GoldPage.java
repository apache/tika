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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One gold train page from a halves file: tab-separated with a header holding {@code path}
 * (relative to the file's directory, or absolute), {@code split}, {@code label} (comma-separated
 * equivalent charsets, the pick first), {@code half} ({@code even}/{@code odd}) and
 * {@code text_quality}.
 */
final class GoldPage {

    final Path path;
    final List<String> labels;
    final String half;
    final String textQuality;

    private GoldPage(Path path, List<String> labels, String half, String textQuality) {
        this.path = path;
        this.labels = labels;
        this.half = half;
        this.textQuality = textQuality;
    }

    boolean isClean() {
        return "clean".equals(textQuality);
    }

    /** Reads every row; fails on any row whose split is not {@code train}. */
    static List<GoldPage> read(Path halves) throws IOException {
        List<String> lines = Files.readAllLines(halves, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("empty gold file " + halves);
        }
        Map<String, Integer> col = new HashMap<>();
        String[] head = lines.get(0).split("\t", -1);
        for (int i = 0; i < head.length; i++) {
            col.put(head[i], i);
        }
        for (String c : new String[]{"path", "split", "label", "half", "text_quality"}) {
            if (!col.containsKey(c)) {
                throw new IOException(halves + " has no column " + c);
            }
        }
        Path base = halves.toAbsolutePath().getParent();
        List<GoldPage> out = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split("\t", -1);
            if (!"train".equals(c[col.get("split")])) {
                throw new IOException(halves + " line " + (i + 1)
                        + ": split is not train; the trainer reads train data only");
            }
            String half = c[col.get("half")];
            if (!half.equals("even") && !half.equals("odd")) {
                throw new IOException(halves + " line " + (i + 1) + ": bad half " + half);
            }
            out.add(new GoldPage(base.resolve(c[col.get("path")]),
                    Collections.unmodifiableList(Arrays.asList(c[col.get("label")].split(","))),
                    half, c[col.get("text_quality")]));
        }
        return out;
    }
}
