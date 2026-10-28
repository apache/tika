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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * A key's valueType or cardinality may change only when type-changes.json declares it. Clients
 * that read typed values (gRPC type hints, typed /rmeta JSON) see such a change, so it must be
 * reviewed and noted in CHANGES, never slip in through a regenerated registry.
 * <p>
 * At a release, refresh the baseline and clear type-changes.json:
 * {@code ./mvnw -pl tika-metadata-schema test -Dtest=MetadataTypeChangesTest
 * -Dtika.metadata.refreshTypeBaseline=true}
 */
public class MetadataTypeChangesTest {

    private static final String REGISTRY = "/org/apache/tika/metadata/metadata-keys.json";
    private static final Path DIR = Paths.get("src/test/resources/org/apache/tika/metadata/schema");
    private static final Path BASELINE = DIR.resolve("metadata-key-types-baseline.json");
    private static final Path CHANGES = DIR.resolve("type-changes.json");
    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*}");

    @Test
    public void typeChangesAreDeclared() throws Exception {
        Map<String, String> current = types(readRegistry());
        if (Boolean.getBoolean("tika.metadata.refreshTypeBaseline")) {
            writeBaseline(current);
            Files.writeString(CHANGES, "[\n]\n", StandardCharsets.UTF_8);
            return;
        }
        Map<String, String> baseline = types(Files.readString(BASELINE, StandardCharsets.UTF_8));

        Map<String, String> declared = new TreeMap<>();
        Matcher m = OBJECT.matcher(Files.readString(CHANGES, StandardCharsets.UTF_8));
        while (m.find()) {
            String record = m.group();
            String key = field(record, "key");
            assertNotNull(key, "type-changes.json record without a key: " + record);
            String jira = field(record, "jira");
            assertTrue(jira != null && !jira.isBlank(), "type-changes.json: " + key + " needs a jira");
            declared.put(key, field(record, "from") + " -> " + field(record, "to"));
        }

        List<String> undeclared = new ArrayList<>();
        for (Map.Entry<String, String> e : baseline.entrySet()) {
            String now = current.get(e.getKey());
            if (now == null || now.equals(e.getValue())) {
                continue;
            }
            String change = e.getValue() + " -> " + now;
            if (!change.equals(declared.remove(e.getKey()))) {
                undeclared.add(e.getKey() + ": " + change);
            }
        }
        assertTrue(undeclared.isEmpty(), "metadata key types changed without a type-changes.json entry "
                + "(add {\"key\", \"from\", \"to\", \"jira\"} and a CHANGES row, or revert): "
                + undeclared);
        assertTrue(declared.isEmpty(),
                "type-changes.json declares changes the registry does not show: " + declared);
    }

    /** key -> "VALUETYPE/CARDINALITY" */
    private static Map<String, String> types(String json) {
        Map<String, String> types = new TreeMap<>();
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            String record = m.group();
            String key = field(record, "key");
            if (key != null) {
                types.put(key, field(record, "valueType") + "/" + field(record, "cardinality"));
            }
        }
        return types;
    }

    private static String field(String record, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(record);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static String readRegistry() throws Exception {
        try (InputStream in = MetadataTypeChangesTest.class.getResourceAsStream(REGISTRY)) {
            assertNotNull(in, "missing " + REGISTRY);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void writeBaseline(Map<String, String> types) throws Exception {
        StringBuilder sb = new StringBuilder("[\n");
        int i = 0;
        for (Map.Entry<String, String> e : types.entrySet()) {
            String[] parts = e.getValue().split("/", 2);
            sb.append("  {\"key\": \"").append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\", \"valueType\": \"").append(parts[0])
                    .append("\", \"cardinality\": \"").append(parts[1]).append("\"}")
                    .append(++i < types.size() ? ",\n" : "\n");
        }
        sb.append("]\n");
        Files.createDirectories(DIR);
        Files.writeString(BASELINE, sb.toString(), StandardCharsets.UTF_8);
    }
}
