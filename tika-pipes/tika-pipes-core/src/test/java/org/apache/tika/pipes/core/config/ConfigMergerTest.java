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
package org.apache.tika.pipes.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.pipes.core.EmitStrategy;

public class ConfigMergerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCreateNewConfig() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .addFetcher("my-fetcher", "file-system-fetcher",
                        Map.of("basePath", "/tmp/input", "allowAbsolutePaths", true))
                .setPipesConfig(4, 60000, null)
                .setEmitStrategy(EmitStrategy.PASSBACK_ALL)
                .setPluginRoots("plugins")
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        assertNotNull(result);
        assertNotNull(result.configPath());
        assertTrue(Files.exists(result.configPath()));

        // Verify config contents
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        // Check fetcher
        assertTrue(root.has("fetchers"));
        assertTrue(root.get("fetchers").has("my-fetcher"));
        JsonNode fetcherConfig = root.get("fetchers").get("my-fetcher").get("file-system-fetcher");
        assertEquals("/tmp/input", fetcherConfig.get("basePath").asText());
        assertTrue(fetcherConfig.get("allowAbsolutePaths").asBoolean());

        // Check pipes config
        assertTrue(root.has("pipes"));
        assertEquals(4, root.get("pipes").get("numClients").asInt());
        assertEquals(60000, root.get("pipes").get("timeoutMillis").asLong());

        // Check emit strategy
        assertEquals("PASSBACK_ALL", root.get("pipes").get("emitStrategy").get("type").asText());

        // Check plugin roots
        assertEquals("plugins", root.get("plugin-roots").asText());

        // Clean up
        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testMergeWithExistingConfig() throws IOException {
        // Create existing config
        String existingConfig = """
                {
                    "fetchers": {
                        "existing-fetcher": {
                            "file-system-fetcher": {
                                "basePath": "/existing/path"
                            }
                        }
                    },
                    "pipes": {
                        "numClients": 2
                    },
                    "plugin-roots": "existing-plugins"
                }
                """;
        Path existingPath = tempDir.resolve("existing-config.json");
        Files.writeString(existingPath, existingConfig);

        // Apply overrides
        ConfigOverrides overrides = ConfigOverrides.builder()
                .addFetcher("new-fetcher", "file-system-fetcher",
                        Map.of("basePath", "/new/path"))
                .setPipesConfig(8, 120000, null)
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(existingPath, overrides);

        assertNotNull(result);
        assertTrue(Files.exists(result.configPath()));

        // Verify merged config
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        // Existing fetcher should be preserved
        assertTrue(root.get("fetchers").has("existing-fetcher"));
        assertEquals("/existing/path",
                root.get("fetchers").get("existing-fetcher")
                        .get("file-system-fetcher").get("basePath").asText());

        // New fetcher should be added
        assertTrue(root.get("fetchers").has("new-fetcher"));
        assertEquals("/new/path",
                root.get("fetchers").get("new-fetcher")
                        .get("file-system-fetcher").get("basePath").asText());

        // Pipes config should be overridden
        assertEquals(8, root.get("pipes").get("numClients").asInt());
        assertEquals(120000, root.get("pipes").get("timeoutMillis").asLong());

        // Existing plugin-roots should be preserved (not overridden)
        assertEquals("existing-plugins", root.get("plugin-roots").asText());

        // Clean up
        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testGeneratedUuidFetcherId() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .addFetcher(null, "file-system-fetcher",  // null ID triggers UUID generation
                        Map.of("allowAbsolutePaths", true))
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        assertNotNull(result.fetcherId());
        assertTrue(result.fetcherId().startsWith("tika-internal-fetcher-"));

        // Verify fetcher exists with generated ID
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());
        assertTrue(root.get("fetchers").has(result.fetcherId()));

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testEmitterConfig() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .addEmitter("my-emitter", "file-system-emitter",
                        Map.of("basePath", "/tmp/output", "onExists", "REPLACE"))
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        assertNotNull(result);
        assertNull(result.fetcherId()); // No fetcher was added
        assertEquals("my-emitter", result.emitterId()); // Should be the explicit ID

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        assertTrue(root.has("emitters"));
        assertTrue(root.get("emitters").has("my-emitter"));
        JsonNode emitterConfig = root.get("emitters").get("my-emitter").get("file-system-emitter");
        assertEquals("/tmp/output", emitterConfig.get("basePath").asText());
        assertEquals("REPLACE", emitterConfig.get("onExists").asText());

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testJvmArgs() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .setPipesConfig(4, 60000, List.of("-Xmx512m", "-Dsome.prop=value"))
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        assertTrue(root.get("pipes").has("forkedJvmArgs"));
        JsonNode jvmArgs = root.get("pipes").get("forkedJvmArgs");
        assertTrue(jvmArgs.isArray());
        assertEquals(2, jvmArgs.size());
        assertEquals("-Xmx512m", jvmArgs.get(0).asText());
        assertEquals("-Dsome.prop=value", jvmArgs.get(1).asText());

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testFullPipesConfig() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .setPipesConfig(8, 120000, 300000, 5000, List.of("-Xmx1g"))
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        JsonNode pipes = root.get("pipes");
        assertEquals(8, pipes.get("numClients").asInt());
        assertEquals(120000, pipes.get("timeoutMillis").asLong());
        assertEquals(300000, pipes.get("startupTimeoutMillis").asLong());
        assertEquals(5000, pipes.get("maxFilesProcessedPerProcess").asInt());

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testPluginRootsNotOverriddenIfExists() throws IOException {
        // Create config with existing plugin-roots
        String existingConfig = """
                {
                    "plugin-roots": "user-plugins"
                }
                """;
        Path existingPath = tempDir.resolve("config-with-plugins.json");
        Files.writeString(existingPath, existingConfig);

        // Try to set different plugin-roots
        ConfigOverrides overrides = ConfigOverrides.builder()
                .setPluginRoots("default-plugins")
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(existingPath, overrides);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        // Should keep the existing value, not override
        assertEquals("user-plugins", root.get("plugin-roots").asText());

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testMultipleFetchers() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder()
                .addFetcher("fetcher1", "file-system-fetcher",
                        Map.of("basePath", "/path1"))
                .addFetcher("fetcher2", "file-system-fetcher",
                        Map.of("basePath", "/path2"))
                .build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());

        assertTrue(root.get("fetchers").has("fetcher1"));
        assertTrue(root.get("fetchers").has("fetcher2"));
        assertEquals("/path1",
                root.get("fetchers").get("fetcher1")
                        .get("file-system-fetcher").get("basePath").asText());
        assertEquals("/path2",
                root.get("fetchers").get("fetcher2")
                        .get("file-system-fetcher").get("basePath").asText());

        // Result should have first fetcher ID
        assertEquals("fetcher1", result.fetcherId());

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testEmptyOverrides() throws IOException {
        ConfigOverrides overrides = ConfigOverrides.builder().build();

        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(null, overrides);

        assertNotNull(result);
        assertTrue(Files.exists(result.configPath()));
        assertNull(result.fetcherId());
        assertNull(result.emitterId());

        // Config should be basically empty
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(result.configPath().toFile());
        assertFalse(root.has("fetchers"));
        assertFalse(root.has("emitters"));
        assertFalse(root.has("pipes"));

        Files.deleteIfExists(result.configPath());
    }

    @Test
    public void testNonExistentConfigPath() throws IOException {
        Path nonExistent = tempDir.resolve("does-not-exist.json");

        ConfigOverrides overrides = ConfigOverrides.builder()
                .addFetcher("test", "file-system-fetcher", Map.of("basePath", "/test"))
                .build();

        // Should create new config, not fail
        ConfigMerger.MergeResult result = ConfigMerger.mergeOrCreate(nonExistent, overrides);

        assertNotNull(result);
        assertTrue(Files.exists(result.configPath()));

        Files.deleteIfExists(result.configPath());
    }
}
