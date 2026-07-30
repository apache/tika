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
package org.apache.tika.metadata.schema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TIKA-4797 / TIKA-4799. Produces the 3.x-&gt;4.x metadata-key migration table
 * ({@code metadata-migration-3x-4x.json}) that {@code LegacyKeyMigrationFilter} loads, by joining the
 * committed 3.x and 4.x {@code {class, field, key}} field tables on field identity (Class#field):
 * <ul>
 *   <li>same field, changed key -&gt; a RENAME (auto);</li>
 *   <li>fields the join can't bridge (moves/drops, or fields renamed on both sides) -&gt; taken from the
 *       adjudicated {@code migration-overlay.tsv} ({@code v4 == DROPPED} = 4.x no longer emits it).</li>
 * </ul>
 * Core scope only ({@code org.apache.tika.metadata.*}) until the 3.x table is widened past its v1.
 *
 * <p>Dependency-free by design: tika-core carries the filter and must stay Jackson-free, so both this
 * generator and the filter emit/parse the flat JSON by hand. {@code MetadataMigrationTableTest}
 * regenerates in-memory and asserts the committed tika-core copy has not drifted.
 *
 * <pre>
 *   java ... MigrationTableGenerator &lt;3x-fields.json&gt; &lt;4x-fields.json&gt; &lt;overlay.tsv&gt; &lt;out.json&gt;
 * </pre>
 */
public final class MigrationTableGenerator {

    private static final Pattern ROW = Pattern.compile(
            "\\{\"class\":\"(.*?)\",\"field\":\"(.*?)\",\"key\":\"((?:[^\"\\\\]|\\\\.)*)\"}");
    private static final String CORE = "org.apache.tika.metadata.";

    private MigrationTableGenerator() {
    }

    /** Build the sorted {@code v3 -> v4} migration JSON from the three input contents. */
    public static String generate(String threeFieldsJson, String fourFieldsJson, String overlayTsv) {
        Map<String, String> three = readFields(threeFieldsJson);   // class#field -> key
        Map<String, String> four = readFields(fourFieldsJson);
        Set<String> v4keys = new HashSet<>(four.values());

        TreeMap<String, String> table = new TreeMap<>();           // v3 -> v4 (or "DROPPED")
        for (Map.Entry<String, String> e : three.entrySet()) {     // auto-renames by field identity
            String key4 = four.get(e.getKey());
            if (key4 != null && !e.getValue().equals(key4)) {
                table.put(e.getValue(), key4);
            }
        }
        for (String line : overlayTsv.split("\n")) {               // adjudicated moves/drops; overlay wins
            String t = line.strip();
            int tab = t.indexOf('\t');
            if (t.isEmpty() || t.startsWith("#") || tab < 0) {
                continue;
            }
            table.put(t.substring(0, tab).strip(), t.substring(tab + 1).strip());
        }
        for (Map.Entry<String, String> e : table.entrySet()) {     // every target must be a real 4.x key
            if (!"DROPPED".equals(e.getValue()) && !v4keys.contains(e.getValue())) {
                throw new IllegalStateException(
                        "migration target is not a 4.x key: " + e.getKey() + " -> " + e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        int n = table.size();
        for (Map.Entry<String, String> e : table.entrySet()) {
            sb.append("{\"v3\":\"").append(esc(e.getKey()))
              .append("\",\"v4\":\"").append(esc(e.getValue())).append("\"}")
              .append(++i < n ? "," : "").append('\n');
        }
        return sb.append("]\n").toString();
    }

    /** Core-scoped {@code class#field -> key} from a {class,field,key} table. */
    private static Map<String, String> readFields(String json) {
        Map<String, String> m = new LinkedHashMap<>();
        Matcher mt = ROW.matcher(json);
        while (mt.find()) {
            if (mt.group(1).startsWith(CORE)) {
                m.put(mt.group(1) + "#" + mt.group(2), unescape(mt.group(3)));
            }
        }
        return m;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public static void main(String[] args) throws Exception {
        String json = generate(Files.readString(Path.of(args[0])), Files.readString(Path.of(args[1])),
                Files.readString(Path.of(args[2])));
        Files.writeString(Path.of(args[3]), json);
        System.out.println("wrote " + args[3] + "  rows="
                + json.lines().filter(l -> l.startsWith("{\"v3\"")).count());
    }
}
