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
 *       adjudicated {@code migration-overlay.tsv} ({@code v4 == DROPPED} = 4.x no longer emits it;
 *       a third-column {@code EGRESS_ONLY} marker = the row applies only V4_TO_V3, for 3.x
 *       spellings that are ambiguous or unsafe to rewrite on ingest).</li>
 * </ul>
 * The auto-join is core scope only ({@code org.apache.tika.metadata.*}) until the 3.x table is
 * widened past its v1 (the 3.x snapshot only has core-declared fields, so widening the join side
 * would never find extra matches). Overlay-target validation is <strong>not</strong> core-restricted
 * (TIKA-4816 rename batch): an adjudicated row may rename a bare/legacy key to a Property declared
 * in a parser module (e.g. {@code geotopic:name} in {@code GeoParser}, {@code grobid:tei:title} in
 * {@code TEIDOMParser}).
 *
 * <p>Overlay targets validate against {@code metadata-keys.json} (the closed-key registry), not the
 * field table: {@code fieldTable()}'s reflection-based field discovery eagerly resolves every
 * declared field's type, so a class with any field typed on a not-transitively-visible dependency
 * (e.g. GeoParser's {@code NameFinderME}, which pulls in {@code opennlp-tools} only via the
 * {@code provided}-scope {@code ctakes-core}) throws {@code NoClassDefFoundError} and drops the
 * WHOLE class. The closed-key registry has no such gap: it fully loads each class instead of just
 * inspecting field types.
 *
 * <p>Dependency-free by design: tika-core carries the filter and must stay Jackson-free, so both this
 * generator and the filter emit/parse the flat JSON by hand. {@code MetadataMigrationTableTest}
 * regenerates in-memory and asserts the committed tika-core copy has not drifted.
 *
 * <pre>
 *   java ... MigrationTableGenerator &lt;3x-fields.json&gt; &lt;4x-fields.json&gt; &lt;4x-keys.json&gt; &lt;overlay.tsv&gt; &lt;out.json&gt;
 * </pre>
 */
public final class MigrationTableGenerator {

    private static final Pattern ROW = Pattern.compile(
            "\\{\"class\":\"(.*?)\",\"field\":\"(.*?)\",\"key\":\"((?:[^\"\\\\]|\\\\.)*)\"}");
    // metadata-keys.json rows: {"key":"...","namespace":"...","valueType":"...", ...}; only the
    // leading "key" field is needed here.
    private static final Pattern KEY_ROW = Pattern.compile("\\{\"key\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final String CORE = "org.apache.tika.metadata.";

    private MigrationTableGenerator() {
    }

    /** Build the sorted {@code v3 -> v4} migration JSON from the four input contents. */
    public static String generate(String threeFieldsJson, String fourFieldsJson, String fourKeysJson,
            String overlayTsv) {
        Map<String, String> three = readFields(threeFieldsJson, true);    // class#field -> key (core only)
        Map<String, String> four = readFields(fourFieldsJson, true);      // class#field -> key (core only)
        Set<String> v4keys = readKeys(fourKeysJson);                      // the full closed-key registry

        TreeMap<String, String> table = new TreeMap<>();           // v3 -> v4 (or "DROPPED")
        TreeMap<String, String> egressOnly = new TreeMap<>();      // v4 -> v3 (v3 may repeat)
        for (Map.Entry<String, String> e : three.entrySet()) {     // auto-renames by field identity
            String key4 = four.get(e.getKey());
            if (key4 != null && !e.getValue().equals(key4)) {
                table.put(e.getValue(), key4);
            }
        }
        for (String line : overlayTsv.split("\n")) {               // adjudicated moves/drops; overlay wins
            String stripped = line.strip();
            int tab = line.indexOf('\t');
            if (stripped.isEmpty() || stripped.startsWith("#") || tab < 0) {
                continue;
            }
            // v3 is taken verbatim (not stripped): a handful of ISO19115 keys (TIKA-4816) carry a
            // meaningful trailing space that must survive into the migration bridge. v4 is a
            // freshly-authored key with no such literal, so trimming stray line-end whitespace there
            // (including a trailing \r) is safe.
            String v3 = line.substring(0, tab);
            String rest = line.substring(tab + 1);
            int tab2 = rest.indexOf('\t');
            if (tab2 < 0) {
                table.put(v3, rest.strip());
            } else if ("EGRESS_ONLY".equals(rest.substring(tab2 + 1).strip())) {
                // keyed by v4 (unique); several v4 keys may legitimately share one v3 spelling
                egressOnly.put(rest.substring(0, tab2).strip(), v3);
            } else {
                throw new IllegalStateException("unrecognized overlay marker on row: " + line);
            }
        }
        for (Map.Entry<String, String> e : table.entrySet()) {     // every target must be a real 4.x key
            if (!"DROPPED".equals(e.getValue()) && !v4keys.contains(e.getValue())) {
                throw new IllegalStateException(
                        "migration target is not a 4.x key: " + e.getKey() + " -> " + e.getValue());
            }
        }
        for (Map.Entry<String, String> e : egressOnly.entrySet()) {
            if (!v4keys.contains(e.getKey())) {
                throw new IllegalStateException(
                        "egress-only source is not a 4.x key: " + e.getKey() + " -> " + e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        int n = table.size() + egressOnly.size();
        for (Map.Entry<String, String> e : table.entrySet()) {
            sb.append("{\"v3\":\"").append(esc(e.getKey()))
              .append("\",\"v4\":\"").append(esc(e.getValue())).append("\"}")
              .append(++i < n ? "," : "").append('\n');
        }
        // egress-only rows last, sorted by v4: applied only V4_TO_V3 by LegacyKeyMigrationFilter
        for (Map.Entry<String, String> e : egressOnly.entrySet()) {
            sb.append("{\"v3\":\"").append(esc(e.getValue()))
              .append("\",\"v4\":\"").append(esc(e.getKey()))
              .append("\",\"egressOnly\":true}")
              .append(++i < n ? "," : "").append('\n');
        }
        return sb.append("]\n").toString();
    }

    /** {@code class#field -> key} from a {class,field,key} table, optionally restricted to core. */
    private static Map<String, String> readFields(String json, boolean coreOnly) {
        Map<String, String> m = new LinkedHashMap<>();
        Matcher mt = ROW.matcher(json);
        while (mt.find()) {
            if (!coreOnly || mt.group(1).startsWith(CORE)) {
                m.put(mt.group(1) + "#" + mt.group(2), unescape(mt.group(3)));
            }
        }
        return m;
    }

    /** Every declared key from a {@code metadata-keys.json}-shaped closed-key registry. */
    private static Set<String> readKeys(String json) {
        Set<String> keys = new HashSet<>();
        Matcher mt = KEY_ROW.matcher(json);
        while (mt.find()) {
            keys.add(unescape(mt.group(1)));
        }
        return keys;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public static void main(String[] args) throws Exception {
        String json = generate(Files.readString(Path.of(args[0])), Files.readString(Path.of(args[1])),
                Files.readString(Path.of(args[2])), Files.readString(Path.of(args[3])));
        Files.writeString(Path.of(args[4]), json);
        System.out.println("wrote " + args[4] + "  rows="
                + json.lines().filter(l -> l.startsWith("{\"v3\"")).count());
    }
}
