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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.FrameworkConfig;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Tests that serialization works correctly with Jackson's Smile binary format.
 * <p>
 * Smile is a binary JSON format that doesn't support certain text-based operations
 * like writeValueAsString(). These tests ensure our serializers handle this correctly.
 */
public class SmileFormatTest {

    private ObjectMapper smileMapper;

    @BeforeEach
    public void setUp() {
        SmileFactory smileFactory = new SmileFactory();
        smileMapper = TikaObjectMapperFactory.createMapper(smileFactory);
    }

    @Test
    public void testMetadataRoundTripWithSmile() throws IOException {
        Metadata original = new Metadata();
        original.set("title", "Test Document");
        original.set("author", "Test Author");
        original.add("keywords", "test");
        original.add("keywords", "smile");

        // Serialize to Smile binary format
        byte[] bytes = smileMapper.writeValueAsBytes(original);

        // Deserialize from Smile binary format
        Metadata deserialized = smileMapper.readValue(bytes, Metadata.class);

        assertEquals("Test Document", deserialized.get("title"));
        assertEquals("Test Author", deserialized.get("author"));
        String[] keywords = deserialized.getValues("keywords");
        assertEquals(2, keywords.length);
    }

    @Test
    public void testParseContextRoundTripWithSmile() throws IOException {
        ParseContext original = new ParseContext();
        original.setJsonConfig("test-config", "{\"key\": \"value\"}");

        // Serialize to Smile binary format
        byte[] bytes = smileMapper.writeValueAsBytes(original);

        // Deserialize from Smile binary format
        ParseContext deserialized = smileMapper.readValue(bytes, ParseContext.class);

        assertNotNull(deserialized.getJsonConfig("test-config"));
        // Compare parsed JSON to avoid whitespace differences
        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode originalNode = jsonMapper.readTree("{\"key\": \"value\"}");
        JsonNode deserializedNode = jsonMapper.readTree(deserialized.getJsonConfig("test-config").json());
        assertEquals(originalNode, deserializedNode);
    }

    @Test
    public void testFrameworkConfigExtractWithSmile() throws IOException {
        // Create a config node using the Smile mapper
        String json = "{\"option1\": \"value1\", \"option2\": 42}";
        ObjectMapper jsonMapper = new ObjectMapper();
        JsonNode configNode = jsonMapper.readTree(json);

        // Extract framework config - this should work even when passed a Smile mapper
        // because FrameworkConfig uses a plain JSON mapper internally for writeValueAsString
        FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, smileMapper);

        assertNotNull(frameworkConfig.getComponentConfigJson());
        // The JSON string should be valid
        String componentJson = frameworkConfig.getComponentConfigJson().json();
        assertNotNull(componentJson);
        // Parse it back to verify it's valid JSON
        JsonNode parsed = jsonMapper.readTree(componentJson);
        assertEquals("value1", parsed.get("option1").asText());
        assertEquals(42, parsed.get("option2").asInt());
    }
}
