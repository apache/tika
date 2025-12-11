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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JsonConfigHelperTest {

    @Test
    public void testStringReplacement() throws Exception {
        String template = """
                {
                  "name": "COMPONENT_NAME",
                  "description": "A test component"
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("COMPONENT_NAME", "my-fetcher"));

        assertEquals("my-fetcher", result.get("name").asText());
        assertEquals("A test component", result.get("description").asText());
    }

    @Test
    public void testIntegerReplacement() throws Exception {
        String template = """
                {
                  "maxFiles": "MAX_FILES",
                  "timeout": "TIMEOUT_MS"
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("MAX_FILES", 100, "TIMEOUT_MS", 5000L));

        assertTrue(result.get("maxFiles").isNumber());
        assertEquals(100, result.get("maxFiles").asInt());
        assertTrue(result.get("timeout").isNumber());
        assertEquals(5000L, result.get("timeout").asLong());
    }

    @Test
    public void testDoubleReplacement() throws Exception {
        String template = """
                {
                  "threshold": "THRESHOLD",
                  "rate": "RATE"
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("THRESHOLD", 0.95, "RATE", 1.5f));

        assertTrue(result.get("threshold").isNumber());
        assertEquals(0.95, result.get("threshold").asDouble(), 0.001);
        assertTrue(result.get("rate").isNumber());
        assertEquals(1.5, result.get("rate").asDouble(), 0.001);
    }

    @Test
    public void testBooleanReplacement() throws Exception {
        String template = """
                {
                  "enabled": "ENABLED",
                  "debug": "DEBUG_MODE"
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("ENABLED", true, "DEBUG_MODE", false));

        assertTrue(result.get("enabled").isBoolean());
        assertTrue(result.get("enabled").asBoolean());
        assertTrue(result.get("debug").isBoolean());
        assertFalse(result.get("debug").asBoolean());
    }

    @Test
    public void testPathReplacement(@TempDir Path tmpDir) throws Exception {
        String template = """
                {
                  "basePath": "BASE_PATH",
                  "outputDir": "OUTPUT_DIR"
                }
                """;

        Path inputPath = tmpDir.resolve("input");
        Path outputPath = tmpDir.resolve("output");

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("BASE_PATH", inputPath, "OUTPUT_DIR", outputPath));

        // Should be strings with forward slashes (even on Windows)
        assertTrue(result.get("basePath").isTextual());
        assertFalse(result.get("basePath").asText().contains("\\"),
                "Path should use forward slashes");
        assertTrue(result.get("basePath").asText().endsWith("input"));

        assertTrue(result.get("outputDir").isTextual());
        assertFalse(result.get("outputDir").asText().contains("\\"),
                "Path should use forward slashes");
        assertTrue(result.get("outputDir").asText().endsWith("output"));
    }

    @Test
    public void testNestedReplacement() throws Exception {
        String template = """
                {
                  "fetchers": {
                    "fs": {
                      "file-system-fetcher": {
                        "basePath": "FETCHER_PATH",
                        "maxSize": "MAX_SIZE"
                      }
                    }
                  },
                  "emitters": {
                    "out": {
                      "file-system-emitter": {
                        "basePath": "EMITTER_PATH"
                      }
                    }
                  }
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of(
                        "FETCHER_PATH", "/tmp/input",
                        "EMITTER_PATH", "/tmp/output",
                        "MAX_SIZE", 1000
                ));

        assertEquals("/tmp/input",
                result.at("/fetchers/fs/file-system-fetcher/basePath").asText());
        assertEquals(1000,
                result.at("/fetchers/fs/file-system-fetcher/maxSize").asInt());
        assertEquals("/tmp/output",
                result.at("/emitters/out/file-system-emitter/basePath").asText());
    }

    @Test
    public void testArrayReplacement() throws Exception {
        String template = """
                {
                  "items": ["ITEM_1", "ITEM_2", "static-item"],
                  "counts": ["COUNT_1", "COUNT_2"]
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of(
                        "ITEM_1", "first",
                        "ITEM_2", "second",
                        "COUNT_1", 10,
                        "COUNT_2", 20
                ));

        assertEquals("first", result.get("items").get(0).asText());
        assertEquals("second", result.get("items").get(1).asText());
        assertEquals("static-item", result.get("items").get(2).asText());
        assertEquals(10, result.get("counts").get(0).asInt());
        assertEquals(20, result.get("counts").get(1).asInt());
    }

    @Test
    public void testUnmatchedPlaceholdersLeftAlone() throws Exception {
        String template = """
                {
                  "matched": "WILL_MATCH",
                  "unmatched": "NOT_IN_MAP"
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of("WILL_MATCH", "replaced-value"));

        assertEquals("replaced-value", result.get("matched").asText());
        assertEquals("NOT_IN_MAP", result.get("unmatched").asText());
    }

    @Test
    public void testWriteConfig(@TempDir Path tmpDir) throws Exception {
        String template = """
                {
                  "path": "THE_PATH",
                  "count": "THE_COUNT"
                }
                """;

        Path templateFile = tmpDir.resolve("template.json");
        Files.writeString(templateFile, template);

        Path outputFile = tmpDir.resolve("output.json");
        Path inputDir = tmpDir.resolve("input");

        JsonConfigHelper.writeConfig(templateFile,
                Map.of("THE_PATH", inputDir, "THE_COUNT", 42),
                outputFile);

        // Read back and verify
        JsonNode result = JsonConfigHelper.getMapper().readTree(outputFile.toFile());
        assertTrue(result.get("path").asText().endsWith("input"));
        assertFalse(result.get("path").asText().contains("\\"));
        assertEquals(42, result.get("count").asInt());
    }

    @Test
    public void testMixedTypes() throws Exception {
        String template = """
                {
                  "config": {
                    "stringVal": "STRING_VAL",
                    "intVal": "INT_VAL",
                    "longVal": "LONG_VAL",
                    "doubleVal": "DOUBLE_VAL",
                    "floatVal": "FLOAT_VAL",
                    "boolVal": "BOOL_VAL",
                    "pathVal": "PATH_VAL"
                  }
                }
                """;

        JsonNode result = JsonConfigHelper.loadFromString(template,
                Map.of(
                        "STRING_VAL", "hello",
                        "INT_VAL", 42,
                        "LONG_VAL", 9999999999L,
                        "DOUBLE_VAL", 3.14159,
                        "FLOAT_VAL", 2.5f,
                        "BOOL_VAL", true,
                        "PATH_VAL", Path.of("/tmp/test")
                ));

        JsonNode config = result.get("config");
        assertEquals("hello", config.get("stringVal").asText());
        assertEquals(42, config.get("intVal").asInt());
        assertEquals(9999999999L, config.get("longVal").asLong());
        assertEquals(3.14159, config.get("doubleVal").asDouble(), 0.00001);
        assertEquals(2.5, config.get("floatVal").asDouble(), 0.01);
        assertTrue(config.get("boolVal").asBoolean());
        assertEquals("/tmp/test", config.get("pathVal").asText());
    }

    @Test
    public void testToJsonPath() {
        // Test that backslashes are converted to forward slashes
        Path path = Path.of("/some/path/to/file");
        String jsonPath = JsonConfigHelper.toJsonPath(path);
        assertFalse(jsonPath.contains("\\"), "Should not contain backslashes");
        assertTrue(jsonPath.contains("/"), "Should contain forward slashes");
    }

    @Test
    public void testLoadFromResource(@TempDir Path tmpDir) throws Exception {
        Path fetcherPath = tmpDir.resolve("fetcher-base");
        Path emitterPath = tmpDir.resolve("emitter-base");

        JsonNode result = JsonConfigHelper.loadFromResource(
                "/configs/template-test.json",
                JsonConfigHelperTest.class,
                Map.of(
                        "FETCHER_BASE_PATH", fetcherPath,
                        "EMITTER_BASE_PATH", emitterPath,
                        "MAX_FILES", 500,
                        "EMIT_INTERMEDIATE", true
                ));

        // Verify nested paths were replaced
        String fetcherBasePath = result.at("/fetchers/fs/file-system-fetcher/basePath").asText();
        assertTrue(fetcherBasePath.endsWith("fetcher-base"));
        assertFalse(fetcherBasePath.contains("\\"));

        String emitterBasePath = result.at("/emitters/out/file-system-emitter/basePath").asText();
        assertTrue(emitterBasePath.endsWith("emitter-base"));
        assertFalse(emitterBasePath.contains("\\"));

        // Verify numeric and boolean replacements
        assertEquals(500, result.at("/pipes/maxFilesWaitingInQueue").asInt());
        assertTrue(result.at("/pipes/emitIntermediateResults").asBoolean());
    }

    @Test
    public void testWriteConfigFromResource(@TempDir Path tmpDir) throws Exception {
        Path fetcherPath = tmpDir.resolve("input");
        Path emitterPath = tmpDir.resolve("output");
        Path outputFile = tmpDir.resolve("generated-config.json");

        JsonConfigHelper.writeConfigFromResource(
                "/configs/template-test.json",
                JsonConfigHelperTest.class,
                Map.of(
                        "FETCHER_BASE_PATH", fetcherPath,
                        "EMITTER_BASE_PATH", emitterPath,
                        "MAX_FILES", 1000,
                        "EMIT_INTERMEDIATE", false
                ),
                outputFile);

        // Verify file was written
        assertTrue(Files.exists(outputFile));

        // Read back and verify
        JsonNode result = JsonConfigHelper.getMapper().readTree(outputFile.toFile());
        assertTrue(result.at("/fetchers/fs/file-system-fetcher/basePath").asText().endsWith("input"));
        assertEquals(1000, result.at("/pipes/maxFilesWaitingInQueue").asInt());
        assertFalse(result.at("/pipes/emitIntermediateResults").asBoolean());
    }
}
