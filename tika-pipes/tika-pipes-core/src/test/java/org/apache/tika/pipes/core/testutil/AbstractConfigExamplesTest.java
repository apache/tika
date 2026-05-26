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
package org.apache.tika.pipes.core.testutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;

/**
 * Base class for plugin {@code ConfigExamplesTest} classes.
 * <p>
 * Reads JSON examples from {@code src/test/resources/config-examples/} and
 * verifies that each one loads via {@link TikaLoader} <strong>and</strong> that
 * the {@code fetchers}/{@code emitters}/{@code pipes-iterator} sections have
 * the shape the pipes loader actually consumes
 * ({@code {"id": {"type-name": {...config...}}}}).
 * <p>
 * Without the shape check, an example using an unsupported array form like
 * <pre>
 *     "fetchers": [ {"file-system-fetcher": {"id": "fsf", ...}} ]
 * </pre>
 * loads successfully but registers <em>zero</em> components, which surfaces only
 * at runtime as {@code "Can't find fetcher for id=fsf. Available: []"}.
 */
public abstract class AbstractConfigExamplesTest {

    protected static final String EXAMPLES_DIR = "/config-examples/";
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    protected Path tempDir;

    protected String readExample(String resourceName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(EXAMPLES_DIR + resourceName)) {
            assertNotNull(is, "Resource not found: " + EXAMPLES_DIR + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes the example to a temp file, loads it through {@link TikaLoader},
     * and validates the shape of every pipes-component section. Returns the
     * parsed JSON for any further plugin-specific assertions.
     */
    protected JsonNode loadAndValidate(String resourceName) throws Exception {
        String json = readExample(resourceName);
        Path configFile = tempDir.resolve("tika-config.json");
        Files.writeString(configFile, json, StandardCharsets.UTF_8);
        TikaLoader loader = TikaLoader.load(configFile);
        assertNotNull(loader, "TikaLoader should not be null for: " + resourceName);

        JsonNode root = OBJECT_MAPPER.readTree(json);
        assertIdKeyedSection(root, "fetchers", resourceName);
        assertIdKeyedSection(root, "emitters", resourceName);
        assertSingletonSection(root, "pipes-iterator", resourceName);
        assertSingletonSection(root, "pipes-reporters", resourceName);
        return root;
    }

    /**
     * Sections like {@code fetchers} and {@code emitters} are
     * {@code {"id": {"type-name": {...}}}}. Catches array-form drift.
     */
    private void assertIdKeyedSection(JsonNode root, String section, String fixture) {
        JsonNode node = root.get(section);
        if (node == null || node.isNull()) {
            return;
        }
        assertTrue(node.isObject(),
                "[" + fixture + "] '" + section + "' must be a JSON object keyed by "
                        + "instance ID, e.g. {\"my-id\": {\"type-name\": {...}}}; got "
                        + node.getNodeType());
        Iterator<Map.Entry<String, JsonNode>> ids = node.fields();
        while (ids.hasNext()) {
            Map.Entry<String, JsonNode> idEntry = ids.next();
            JsonNode typed = idEntry.getValue();
            String path = section + "." + idEntry.getKey();
            assertTrue(typed.isObject(),
                    "[" + fixture + "] '" + path + "' must be a JSON object wrapping "
                            + "exactly one type entry");
            assertEquals(1, typed.size(),
                    "[" + fixture + "] '" + path + "' must contain exactly one type entry; "
                            + "got " + typed.size() + " fields");
        }
    }

    /**
     * {@code pipes-iterator} is a single component without an ID layer: just
     * {@code {"type-name": {...}}}.
     */
    private void assertSingletonSection(JsonNode root, String section, String fixture) {
        JsonNode node = root.get(section);
        if (node == null || node.isNull()) {
            return;
        }
        assertTrue(node.isObject(),
                "[" + fixture + "] '" + section + "' must be a JSON object containing "
                        + "exactly one type entry; got " + node.getNodeType());
        assertEquals(1, node.size(),
                "[" + fixture + "] '" + section + "' must contain exactly one type entry; "
                        + "got " + node.size() + " fields");
    }

    /**
     * Extract the inner per-type config object: {@code root[section][id][typeName]}.
     * If {@code id} is null, the section is treated as the singleton form (e.g.,
     * {@code pipes-iterator}).
     */
    protected JsonNode innerComponent(String json, String section, String id, String typeName)
            throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode sectionNode = root.get(section);
        assertNotNull(sectionNode, "Missing section: " + section);
        JsonNode idNode = id == null ? sectionNode : sectionNode.get(id);
        assertNotNull(idNode, "Missing id: " + id);
        JsonNode typed = idNode.get(typeName);
        assertNotNull(typed, "Missing type: " + typeName);
        return typed;
    }
}
