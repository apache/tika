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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.loader.TikaLoader;

/**
 * Shared base class for plugin {@code ConfigExamplesTest}s. Loads JSON
 * configuration examples from {@code /config-examples/} on the test
 * classpath, validates that {@link TikaLoader} can parse them, and exposes
 * helpers for drilling into the inner component config block.
 */
public abstract class AbstractConfigExamplesTest {

    private static final String EXAMPLES_DIR = "/config-examples/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    protected Path tempDir;

    /**
     * Reads a JSON example from the {@code /config-examples/} classpath dir.
     */
    protected String readExample(String resourceName) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(EXAMPLES_DIR + resourceName)) {
            assertNotNull(is, "Resource not found: " + EXAMPLES_DIR + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Reads the named example, writes it to a temp file, and asserts that
     * {@link TikaLoader#load(Path)} returns a non-null config.
     */
    protected void loadAndValidate(String resourceName) throws Exception {
        String json = readExample(resourceName);
        Path configFile = tempDir.resolve("tika-config.json");
        Files.writeString(configFile, json, StandardCharsets.UTF_8);
        assertNotNull(TikaLoader.load(configFile));
    }

    /**
     * Returns the inner component-config node from a Tika pipes JSON document.
     * <p>
     * Tika pipes configs nest fetchers/emitters as
     * {@code section -> id -> type -> {config}}, while pipes-iterators and
     * reporters omit the id level: {@code section -> type -> {config}}. Pass
     * {@code id == null} to skip that level.
     */
    protected JsonNode innerComponent(String json, String section, String id, String type)
            throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(json);
        JsonNode node = root.get(section);
        assertNotNull(node, "Missing section '" + section + "' in JSON");
        if (id != null) {
            node = node.get(id);
            assertNotNull(node, "Missing id '" + id + "' under section '" + section + "'");
        }
        JsonNode inner = node.get(type);
        assertNotNull(inner, "Missing type '" + type + "' under "
                + (id != null ? "id '" + id + "'" : "section '" + section + "'"));
        return inner;
    }
}
