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

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.config.ExceptionReporting;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;

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
    public void testConfigPrefixedNameRejected() {
        // tika-server gates /config endpoints on the path fragment, so such a
        // preset would be unreachable there; refuse it at definition time
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"config-fast": {}}}
                """));
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"CONFIGX": {}}}
                """));
        assertTrue(PresetRegistry.isValidName("fast-config"));
    }

    @Test
    public void testUnresolvablePresetFailsLoad() {
        // a known component with malformed content must fail at load, not first use
        assertThrows(TikaConfigException.class, () -> load("""
                {"presets": {"bad": {"basic-content-handler-factory": {"type": "NO_SUCH_TYPE"}}}}
                """));
    }

    @Test
    public void testNewParseContextIsTrustedAndFresh() throws Exception {
        // exception-reporting is wire-blocked for caller-supplied contexts; a preset is
        // operator config and must be able to bind it
        PresetRegistry registry = load("""
                {"presets": {"reporting": {"exception-reporting": {"maxLength": 512}}}}
                """);
        ParseContext first = registry.newParseContext("reporting");
        assertTrue(first.get(ExceptionReporting.class) != null);
        // fresh per call: callers mutate the result per request
        assertTrue(first != registry.newParseContext("reporting"));
        assertNull(registry.newParseContext("nope"));
        assertNull(registry.newParseContext(null));
    }

    @Test
    public void testSuppliesContentHandlerFactory() throws Exception {
        PresetRegistry registry = load("""
                {"presets": {
                  "with-chf": {"basic-content-handler-factory": {"type": "XML"}},
                  "without-chf": {"embedded-limits": {"maxDepth": 2}}}}
                """);
        assertTrue(registry.suppliesContentHandlerFactory("with-chf"));
        assertFalse(registry.suppliesContentHandlerFactory("without-chf"));
        assertFalse(registry.suppliesContentHandlerFactory("nope"));
        assertFalse(registry.suppliesContentHandlerFactory(null));
    }

    @Test
    public void testCatalogNameCollisionAcrossJarsFails() throws Exception {
        Path dirA = catalogDir("a", "colliding", "{\"embedded-limits\": {\"maxDepth\": 1}}");
        Path dirB = catalogDir("b", "colliding", "{\"embedded-limits\": {\"maxDepth\": 2}}");
        // parent is the test loader, so component classes still resolve; its own
        // catalog contributes only the distinct builtin-sample name
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{dirA.toUri().toURL(), dirB.toUri().toURL()},
                getClass().getClassLoader())) {
            assertThrows(TikaConfigException.class, () -> PresetRegistry.load(
                    config("{\"presets\": {\"colliding\": true}}"), loader));
        }
    }

    @Test
    public void testCatalogIdenticalDuplicateTolerated() throws Exception {
        // the same jar visible twice on a classpath is noise, not a conflict
        Path dirA = catalogDir("a2", "dup", "{\"embedded-limits\": {\"maxDepth\": 3}}");
        Path dirB = catalogDir("b2", "dup", "{\"embedded-limits\": {\"maxDepth\": 3}}");
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{dirA.toUri().toURL(), dirB.toUri().toURL()},
                getClass().getClassLoader())) {
            PresetRegistry registry = PresetRegistry.load(
                    config("{\"presets\": {\"dup\": true}}"), loader);
            assertTrue(registry.hasPreset("dup"));
        }
    }

    private Path catalogDir(String dirName, String presetName, String json) throws Exception {
        Path dir = tmp.resolve(dirName);
        Files.createDirectories(dir.resolve("META-INF/tika"));
        // resource path unique per dir: identical paths would shadow on the classpath
        Files.writeString(dir.resolve("META-INF/tika/presets.idx"),
                presetName + "=/presets-" + dirName + "/" + presetName + ".json\n");
        Files.createDirectories(dir.resolve("presets-" + dirName));
        Files.writeString(dir.resolve("presets-" + dirName + "/" + presetName + ".json"), json);
        return dir;
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
