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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TikaJsonConfig parsing functionality.
 */
public class TikaJsonConfigTest {

    @Test
    public void testStringShorthandForParsers() throws Exception {
        String json = """
            {
              "parsers": [
                "html-parser",
                { "pdf-parser": { "ocrStrategy": "AUTO" } },
                "txt-parser"
              ]
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> parsers = config.getArrayComponents("parsers");
        assertEquals(3, parsers.size(), "Should have 3 parsers");

        // First parser: string shorthand
        assertEquals("html-parser", parsers.get(0).getKey());
        assertTrue(parsers.get(0).getValue().isEmpty(), "Should have empty config for shorthand");

        // Second parser: full object syntax
        assertEquals("pdf-parser", parsers.get(1).getKey());
        assertEquals("AUTO", parsers.get(1).getValue().get("ocrStrategy").asText());

        // Third parser: string shorthand
        assertEquals("txt-parser", parsers.get(2).getKey());
        assertTrue(parsers.get(2).getValue().isEmpty(), "Should have empty config for shorthand");
    }

    @Test
    public void testStringShorthandForDetectors() throws Exception {
        String json = """
            {
              "detectors": [
                "poifs-container-detector",
                { "default-detector": { "spoolTypes": ["application/zip", "application/pdf"] } },
                "zip-container-detector"
              ]
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> detectors = config.getArrayComponents("detectors");
        assertEquals(3, detectors.size(), "Should have 3 detectors");

        assertEquals("poifs-container-detector", detectors.get(0).getKey());
        assertTrue(detectors.get(0).getValue().isEmpty());

        assertEquals("default-detector", detectors.get(1).getKey());
        assertTrue(detectors.get(1).getValue().get("spoolTypes").isArray());

        assertEquals("zip-container-detector", detectors.get(2).getKey());
        assertTrue(detectors.get(2).getValue().isEmpty());
    }

    @Test
    public void testStringShorthandForMetadataFilters() throws Exception {
        String json = """
            {
              "metadata-filters": [
                "date-normalizing-metadata-filter",
                { "field-name-mapping-filter": { "excludeUnmapped": true } }
              ]
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> filters = config.getArrayComponents("metadata-filters");
        assertEquals(2, filters.size(), "Should have 2 filters");

        assertEquals("date-normalizing-metadata-filter", filters.get(0).getKey());
        assertTrue(filters.get(0).getValue().isEmpty());

        assertEquals("field-name-mapping-filter", filters.get(1).getKey());
        assertTrue(filters.get(1).getValue().get("excludeUnmapped").asBoolean());
    }

    @Test
    public void testMixedShorthandAndObjectSyntax() throws Exception {
        String json = """
            {
              "parsers": [
                "first-parser",
                { "second-parser": { "option": "value" } },
                "third-parser",
                { "fourth-parser": {} },
                "fifth-parser"
              ]
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> parsers = config.getArrayComponents("parsers");
        assertEquals(5, parsers.size(), "Should have 5 parsers");

        // Verify ordering is preserved
        assertEquals("first-parser", parsers.get(0).getKey());
        assertEquals("second-parser", parsers.get(1).getKey());
        assertEquals("third-parser", parsers.get(2).getKey());
        assertEquals("fourth-parser", parsers.get(3).getKey());
        assertEquals("fifth-parser", parsers.get(4).getKey());

        // Verify configs
        assertTrue(parsers.get(0).getValue().isEmpty());
        assertEquals("value", parsers.get(1).getValue().get("option").asText());
        assertTrue(parsers.get(2).getValue().isEmpty());
        assertTrue(parsers.get(3).getValue().isEmpty());
        assertTrue(parsers.get(4).getValue().isEmpty());
    }

    @Test
    public void testAllStringsShorthand() throws Exception {
        String json = """
            {
              "detectors": [
                "detector-a",
                "detector-b",
                "detector-c"
              ]
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> detectors = config.getArrayComponents("detectors");
        assertEquals(3, detectors.size());

        for (Map.Entry<String, JsonNode> entry : detectors) {
            assertNotNull(entry.getKey());
            assertTrue(entry.getValue().isEmpty(),
                    "All shorthand entries should have empty config");
        }
    }

    @Test
    public void testEmptyArrayWithShorthand() throws Exception {
        String json = """
            {
              "parsers": []
            }
            """;

        TikaJsonConfig config = TikaJsonConfig.load(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        List<Map.Entry<String, JsonNode>> parsers = config.getArrayComponents("parsers");
        assertTrue(parsers.isEmpty(), "Empty array should return empty list");
    }
}
