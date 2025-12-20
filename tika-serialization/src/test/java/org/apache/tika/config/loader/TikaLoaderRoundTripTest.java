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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Tests for TikaLoader round-trip serialization (load -> toJson -> reload).
 */
public class TikaLoaderRoundTripTest {

    @Test
    void testBasicRoundTrip() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        // Load config
        TikaLoader loader = TikaLoader.load(configPath);

        // Force component loading
        Parser parser = loader.get(Parser.class);
        assertNotNull(parser);

        // Serialize
        String json = loader.toJson();
        assertNotNull(json);
        assertTrue(json.contains("parsers"));

        // Reload from serialized JSON
        Path tempFile = Files.createTempFile("tika-roundtrip-", ".json");
        try {
            Files.writeString(tempFile, json);
            TikaLoader reloaded = TikaLoader.load(tempFile);

            Parser reloadedParser = reloaded.get(Parser.class);
            assertNotNull(reloadedParser);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testExclusionsPreservedInRoundTrip() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        // Load config with exclusions
        TikaLoader loader = TikaLoader.load(configPath);
        CompositeParser parser = (CompositeParser) loader.get(Parser.class);

        // Verify exclusions are applied
        ParseContext context = new ParseContext();
        assertFalse(parser.getSupportedTypes(context).contains(MediaType.parse("application/test+minimal")),
                "minimal-test-parser should be excluded");

        // Round-trip
        String json = loader.toJson();
        Path tempFile = Files.createTempFile("tika-exclusions-", ".json");
        try {
            Files.writeString(tempFile, json);
            TikaLoader reloaded = TikaLoader.load(tempFile);

            CompositeParser reloadedParser = (CompositeParser) reloaded.get(Parser.class);

            // Exclusions should still be applied
            assertFalse(reloadedParser.getSupportedTypes(context)
                            .contains(MediaType.parse("application/test+minimal")),
                    "Exclusions should survive round-trip");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testMultipleRoundTripsStability() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        loader.get(Parser.class); // Force loading

        // Perform 3 round-trips
        for (int i = 0; i < 3; i++) {
            String json = loader.toJson();
            Path tempFile = Files.createTempFile("tika-multi-", ".json");
            try {
                Files.writeString(tempFile, json);
                loader = TikaLoader.load(tempFile);
                loader.get(Parser.class); // Force loading
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        // Should still work
        Parser parser = loader.get(Parser.class);
        assertNotNull(parser);
    }

    @Test
    void testUnloadedComponentsPreserved() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        // Load but DON'T access Parser (lazy loading)
        TikaLoader loader = TikaLoader.load(configPath);

        // Serialize without loading Parser
        String json = loader.toJson();

        // Should still contain parsers section from original JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        assertTrue(node.has("parsers"), "Unloaded component should be preserved");
    }

    @Test
    void testToJsonFormat() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        loader.get(Parser.class);

        String json = loader.toJson();

        // Should be pretty-printed
        assertTrue(json.contains("\n"), "Should be pretty-printed");

        // Should be valid JSON
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        assertNotNull(node);
    }

    @Test
    void testSaveToFile() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        loader.get(Parser.class);

        // Save to file
        Path tempFile = Files.createTempFile("tika-save-", ".json");
        try {
            loader.save(tempFile.toFile());

            // Verify file was written and is valid JSON
            String content = Files.readString(tempFile);
            assertTrue(content.contains("parsers"));

            // Should be loadable
            TikaLoader reloaded = TikaLoader.load(tempFile);
            assertNotNull(reloaded.get(Parser.class));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testDefaultLoaderToJson() throws Exception {
        // Default loader with no config file
        TikaLoader loader = TikaLoader.loadDefault();
        loader.get(Parser.class);

        String json = loader.toJson();
        assertNotNull(json);

        // Should produce valid JSON with parsers
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node = mapper.readTree(json);
        assertTrue(node.has("parsers"), "Default loader should serialize parsers");
    }

    @Test
    void testExcludeFieldInOutput() throws Exception {
        URL configUrl = getClass().getResource("/configs/test-default-parser-with-exclusions.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        loader.get(Parser.class);

        String json = loader.toJson();

        // Should contain exclude in output (for default-parser exclusions)
        // The config has _exclude, serializer outputs exclude
        assertTrue(json.contains("exclude") || json.contains("_exclude"),
                "Exclusions should be in output");
    }

    @Test
    void testMimeFilteringRoundTrip() throws Exception {
        // Test that _mime-include/_mime-exclude survives round-trip
        URL configUrl = getClass().getResource("/configs/test-decoration-config.json");
        Path configPath = Path.of(configUrl.toURI());

        // Load config with mime filtering
        TikaLoader loader = TikaLoader.load(configPath);
        Parser parser = loader.get(Parser.class);
        ParseContext context = new ParseContext();

        // Verify initial filtering works
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf")),
                "Should support application/pdf");
        assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("text/plain")),
                "Should support text/plain");
        assertFalse(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf+fdf")),
                "Should NOT support application/pdf+fdf (excluded)");

        // Round-trip
        String json = loader.toJson();

        // Verify JSON contains the mime filter fields
        assertTrue(json.contains("_mime-include") || json.contains("mime-include"),
                "JSON should contain mime-include");

        // Reload and verify filtering still works
        Path tempFile = Files.createTempFile("tika-mime-filter-", ".json");
        try {
            Files.writeString(tempFile, json);
            TikaLoader reloaded = TikaLoader.load(tempFile);
            Parser reloadedParser = reloaded.get(Parser.class);

            assertTrue(reloadedParser.getSupportedTypes(context).contains(MediaType.parse("application/pdf")),
                    "After round-trip: Should support application/pdf");
            assertTrue(reloadedParser.getSupportedTypes(context).contains(MediaType.parse("text/plain")),
                    "After round-trip: Should support text/plain");
            assertFalse(reloadedParser.getSupportedTypes(context).contains(MediaType.parse("application/pdf+fdf")),
                    "After round-trip: Should NOT support application/pdf+fdf");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testMimeFilteringMultipleRoundTrips() throws Exception {
        // Test stability across multiple round-trips
        URL configUrl = getClass().getResource("/configs/test-decoration-config.json");
        Path configPath = Path.of(configUrl.toURI());

        TikaLoader loader = TikaLoader.load(configPath);
        ParseContext context = new ParseContext();

        // Perform 3 round-trips
        for (int i = 0; i < 3; i++) {
            Parser parser = loader.get(Parser.class);

            // Verify filtering works after each round-trip
            assertTrue(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf")),
                    "Round " + i + ": Should support application/pdf");
            assertFalse(parser.getSupportedTypes(context).contains(MediaType.parse("application/pdf+fdf")),
                    "Round " + i + ": Should NOT support application/pdf+fdf");

            String json = loader.toJson();
            Path tempFile = Files.createTempFile("tika-mime-multi-", ".json");
            try {
                Files.writeString(tempFile, json);
                loader = TikaLoader.load(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
