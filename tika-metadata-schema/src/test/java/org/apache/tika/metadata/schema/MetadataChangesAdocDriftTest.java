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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Mechanical drift guard: every literal {@code v3 -> v4} row in {@code metadata-changes-4x.adoc}'s
 * migration tables must resolve to the same target in the committed
 * {@code metadata-migration-3x-4x.json}, so the hand-authored doc and the generated filter table
 * can't silently diverge again. Rows with a wildcard ({@code *}) or a parenthetical are open-vocabulary
 * prefix families or descriptive text, not literal keys, and are skipped.
 */
public class MetadataChangesAdocDriftTest {

    private static final String ADOC_RELATIVE_PATH =
            "docs/modules/ROOT/pages/migration-to-4x/metadata-changes-4x.adoc";
    private static final String MIGRATION_TABLE = "/org/apache/tika/metadata/metadata-migration-3x-4x.json";

    // one table row per line: |`v3` |`v4`
    private static final Pattern ADOC_ROW = Pattern.compile("^\\|`([^`]*)`\\s*\\|`([^`]*)`\\s*$");
    private static final Pattern TABLE_ROW =
            Pattern.compile("\\{\"v3\":\"((?:[^\"\\\\]|\\\\.)*)\",\"v4\":\"((?:[^\"\\\\]|\\\\.)*)\"}");

    @Test
    public void adocMigrationRowsMatchCommittedTable() throws Exception {
        Map<String, String> table = readTable();
        List<String[]> rows = readAdocRows();
        assertFalse(rows.isEmpty(), "parsed zero rows out of the adoc -- table shape probably changed");

        List<String> missing = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        for (String[] row : rows) {
            String v3 = row[0];
            String v4 = row[1];
            String actual = table.get(v3);
            if (actual == null) {
                missing.add(v3 + " -> " + v4);
            } else if (!actual.equals(v4)) {
                mismatched.add(v3 + ": adoc says " + v4 + ", table says " + actual);
            }
        }
        assertEquals(List.of(), missing, "adoc rows with no matching row in metadata-migration-3x-4x.json");
        assertEquals(List.of(), mismatched, "adoc/table target mismatch");
    }

    private static List<String[]> readAdocRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(findAdoc(), StandardCharsets.UTF_8)) {
            Matcher m = ADOC_ROW.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String v3 = m.group(1);
            String v4 = m.group(2);
            if (v3.contains("*") || v4.contains("*") || v3.contains("(") || v4.contains("(")) {
                continue;
            }
            rows.add(new String[]{v3, v4});
        }
        return rows;
    }

    /** The adoc lives outside any module, so walk up from the working directory to the repo root. */
    private static Path findAdoc() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(ADOC_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "could not locate " + ADOC_RELATIVE_PATH + " above " + Path.of("").toAbsolutePath());
    }

    private static Map<String, String> readTable() throws Exception {
        Map<String, String> table = new HashMap<>();
        try (InputStream in = MetadataChangesAdocDriftTest.class.getResourceAsStream(MIGRATION_TABLE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource on the test classpath: " + MIGRATION_TABLE);
            }
            Matcher m = TABLE_ROW.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            while (m.find()) {
                table.put(unescape(m.group(1)), unescape(m.group(2)));
            }
        }
        return table;
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
