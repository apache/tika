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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.serialization.serdes.ParseContextSerializer;

/**
 * Tests for round-trip serialization of ParseContext.
 * Verifies that JSON configs can be serialized and deserialized
 * with all values preserved.
 */
public class RoundTripSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = TikaObjectMapperFactory.getMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        mapper.registerModule(module);
    }

    // ==================== Basic Round-Trip Tests ====================

    @Test
    void testEmptyContextRoundTrip() throws Exception {
        ParseContext original = new ParseContext();

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        assertTrue(reloaded.getJsonConfigs().isEmpty());
    }

    @Test
    void testSingleConfigRoundTrip() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"AUTO\",\"extractInlineImages\":true}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        assertTrue(reloaded.hasJsonConfig("pdf-parser"));
        String reloadedConfig = reloaded.getJsonConfig("pdf-parser").json();
        assertTrue(reloadedConfig.contains("AUTO"));
        assertTrue(reloadedConfig.contains("extractInlineImages"));
    }

    @Test
    void testMultipleConfigsRoundTrip() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"AUTO\"}");
        original.setJsonConfig("html-parser", "{\"extractScripts\":false}");
        original.setJsonConfig("timeout-limits",
                "{\"progressTimeoutMillis\":30000,\"totalTaskTimeoutMillis\":120000}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        assertEquals(3, reloaded.getJsonConfigs().size());
        assertTrue(reloaded.hasJsonConfig("pdf-parser"));
        assertTrue(reloaded.hasJsonConfig("html-parser"));
        assertTrue(reloaded.hasJsonConfig("timeout-limits"));
    }

    @Test
    void testNestedJsonRoundTrip() throws Exception {
        ParseContext original = new ParseContext();
        String nestedJson = """
            {"level1":{"level2":{"array":[1,2,3],"nested":{"deep":"value"}}}}
            """.trim();
        original.setJsonConfig("complex-config", nestedJson);

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        String reloadedConfig = reloaded.getJsonConfig("complex-config").json();
        JsonNode node = mapper.readTree(reloadedConfig);
        assertEquals("value", node.get("level1").get("level2").get("nested").get("deep").asText());
        assertEquals(3, node.get("level1").get("level2").get("array").size());
    }

    // ==================== Multiple Round-Trips Stability ====================

    @Test
    void testMultipleRoundTripsStability() throws Exception {
        ParseContext context = new ParseContext();
        context.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"NO_OCR\"}");
        context.setJsonConfig("timeout-limits",
                "{\"progressTimeoutMillis\":45000,\"totalTaskTimeoutMillis\":180000}");

        // Perform 5 round-trips
        for (int i = 0; i < 5; i++) {
            String json = mapper.writeValueAsString(context);
            context = mapper.readValue(json, ParseContext.class);
        }

        // Verify values preserved after multiple round-trips
        assertEquals(2, context.getJsonConfigs().size());
        assertTrue(context.getJsonConfig("pdf-parser").json().contains("NO_OCR"));
        assertTrue(context.getJsonConfig("timeout-limits").json().contains("45000"));
    }

    // ==================== TimeoutLimits Round-Trip ====================

    @Test
    void testTimeoutLimitsRoundTrip() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("timeout-limits",
                "{\"progressTimeoutMillis\":60000,\"totalTaskTimeoutMillis\":3600000}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        // Resolve and verify
        ParseContextUtils.resolveAll(reloaded, Thread.currentThread().getContextClassLoader());
        TimeoutLimits limits = reloaded.get(TimeoutLimits.class);

        assertNotNull(limits);
        assertEquals(60000, limits.getProgressTimeoutMillis());
        assertEquals(3600000, limits.getTotalTaskTimeoutMillis());
    }

    @Test
    void testTimeoutLimitsDifferentValues() throws Exception {
        long[] testValues = {1000, 5000, 30000, 120000, 600000};

        for (long value : testValues) {
            ParseContext original = new ParseContext();
            original.setJsonConfig("timeout-limits",
                    "{\"progressTimeoutMillis\":" + value +
                            ",\"totalTaskTimeoutMillis\":" + (value * 10) + "}");

            String json = mapper.writeValueAsString(original);
            ParseContext reloaded = mapper.readValue(json, ParseContext.class);

            ParseContextUtils.resolveAll(reloaded, Thread.currentThread().getContextClassLoader());
            TimeoutLimits limits = reloaded.get(TimeoutLimits.class);

            assertEquals(value, limits.getProgressTimeoutMillis(),
                    "progressTimeoutMillis " + value + " should survive round-trip");
        }
    }

    // ==================== JSON Structure Preservation ====================

    @Test
    void testArrayValuesPreserved() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"items\":[\"a\",\"b\",\"c\"],\"numbers\":[1,2,3]}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        JsonNode node = mapper.readTree(reloaded.getJsonConfig("test-config").json());
        assertEquals(3, node.get("items").size());
        assertEquals("b", node.get("items").get(1).asText());
        assertEquals(2, node.get("numbers").get(1).asInt());
    }

    @Test
    void testBooleanValuesPreserved() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"enabled\":true,\"disabled\":false}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        JsonNode node = mapper.readTree(reloaded.getJsonConfig("test-config").json());
        assertTrue(node.get("enabled").asBoolean());
        assertFalse(node.get("disabled").asBoolean());
    }

    @Test
    void testNullValuesPreserved() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"value\":null,\"other\":\"text\"}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        JsonNode node = mapper.readTree(reloaded.getJsonConfig("test-config").json());
        assertTrue(node.get("value").isNull());
        assertEquals("text", node.get("other").asText());
    }

    // ==================== parse-context Wrapper Format ====================

    @Test
    void testParseContextWrapperFormat() throws Exception {
        // Test that we can deserialize from wrapped format
        String wrappedJson = """
            {
              "parse-context": {
                "pdf-parser": {"ocrStrategy": "AUTO"}
              }
            }
            """;

        ParseContext reloaded = mapper.readValue(wrappedJson, ParseContext.class);

        assertTrue(reloaded.hasJsonConfig("pdf-parser"));
        assertTrue(reloaded.getJsonConfig("pdf-parser").json().contains("AUTO"));
    }

    @Test
    void testFlatFormatPreferred() throws Exception {
        // Verify serialization uses flat format (no wrapper)
        ParseContext original = new ParseContext();
        original.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"AUTO\"}");

        String json = mapper.writeValueAsString(original);

        assertFalse(json.contains("parse-context"), "Should serialize in flat format");
        assertTrue(json.contains("pdf-parser"));
    }

    // ==================== Edge Cases ====================

    @Test
    void testEmptyObjectConfig() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("empty-config", "{}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        assertTrue(reloaded.hasJsonConfig("empty-config"));
        assertEquals("{}", reloaded.getJsonConfig("empty-config").json());
    }

    @Test
    void testSpecialCharactersInValues() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"path\":\"/path/to/file\",\"regex\":\"\\\\d+\"}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        JsonNode node = mapper.readTree(reloaded.getJsonConfig("test-config").json());
        assertEquals("/path/to/file", node.get("path").asText());
    }

    @Test
    void testUnicodeInValues() throws Exception {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"message\":\"Hello 世界\",\"emoji\":\"🎉\"}");

        String json = mapper.writeValueAsString(original);
        ParseContext reloaded = mapper.readValue(json, ParseContext.class);

        JsonNode node = mapper.readTree(reloaded.getJsonConfig("test-config").json());
        assertEquals("Hello 世界", node.get("message").asText());
        assertEquals("🎉", node.get("emoji").asText());
    }
}
