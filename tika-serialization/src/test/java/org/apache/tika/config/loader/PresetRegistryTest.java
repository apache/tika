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
package org.apache.tika.config.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;

public class PresetRegistryTest {

    @TempDir
    Path tmp;

    private TikaJsonConfig config(String json) throws Exception {
        Path p = tmp.resolve("config-" + json.hashCode() + ".json");
        Files.writeString(p, json);
        return TikaJsonConfig.load(p);
    }

    private PresetRegistry load(String json) throws Exception {
        return PresetRegistry.load(config(json), getClass().getClassLoader());
    }

    @Test
    public void testCatalogPresetInertUntilActivated() throws Exception {
        // src/test/resources/META-INF/tika/presets.idx contributes builtin-sample,
        // but a catalog jar must never activate itself
        PresetRegistry registry = PresetRegistry.load(null, getClass().getClassLoader());
        assertTrue(registry.names().isEmpty());
        assertNull(registry.parseContextJson("builtin-sample"));

        assertNull(load("{}").parseContextJson("builtin-sample"));
    }

    @Test
    public void testTrueActivatesCatalogDefinition() throws Exception {
        PresetRegistry registry = load("""
                {"presets": {"builtin-sample": true}}
                """);
        JsonNode content =
                new ObjectMapper().readTree(registry.parseContextJson("builtin-sample"));
        assertEquals("TEXT", content.get("basic-content-handler-factory").get("type").asText());
    }

    @Test
    public void testTrueWithoutCatalogDefinitionFailsStartup() {
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"no-such-catalog-entry": true}}
                """));
    }

    @Test
    public void testConfigDefinesPreset() throws Exception {
        PresetRegistry registry = load("""
                {"presets": {"my-preset": {"basic-content-handler-factory": {"type": "XML"}}}}
                """);
        JsonNode content = new ObjectMapper().readTree(registry.parseContextJson("my-preset"));
        assertEquals("XML", content.get("basic-content-handler-factory").get("type").asText());
    }

    @Test
    public void testConfigOverridesCatalogDefinitionWholesale() throws Exception {
        PresetRegistry registry = load("""
                {"presets": {"builtin-sample": {"embedded-limits": {"maxDepth": 2}}}}
                """);
        JsonNode content =
                new ObjectMapper().readTree(registry.parseContextJson("builtin-sample"));
        assertNull(content.get("basic-content-handler-factory"),
                "an override replaces the whole preset, it does not merge");
        assertEquals(2, content.get("embedded-limits").get("maxDepth").asInt());
    }

    @Test
    public void testFalseAndNullAreExplicitNoOps() throws Exception {
        PresetRegistry registry = load("""
                {"presets": {"builtin-sample": false, "other": null}}
                """);
        assertFalse(registry.names().contains("builtin-sample"));
        assertNull(registry.parseContextJson("builtin-sample"));
        assertNull(registry.parseContextJson("other"));
    }

    @Test
    public void testUnknownPresetIsNull() throws Exception {
        assertNull(load("{}").parseContextJson("nope"));
        assertNull(load("{}").parseContextJson(null));
    }

    @Test
    public void testInvalidNameRejected() {
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"bad/name": {}}}
                """));
    }

    @Test
    public void testNonObjectPresetRejected() {
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"bad": "a string"}}
                """));
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"bad": 42}}
                """));
    }

    @Test
    public void testNonObjectPresetsBlockRejected() {
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": ["not", "an", "object"]}
                """));
    }
}
