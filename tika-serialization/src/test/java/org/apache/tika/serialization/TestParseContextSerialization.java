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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.DateNormalizingMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

public class TestParseContextSerialization {

    private ObjectMapper createMapper() {
        // Start with the properly configured mapper that has polymorphic type handling
        ObjectMapper mapper = PolymorphicObjectMapperFactory.getMapper();

        // Register our custom serializer/deserializer on top
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    private String serializeParseContext(ParseContext pc) throws Exception {
        ObjectMapper mapper = createMapper();
        try (Writer writer = new StringWriter()) {
            try (JsonGenerator jsonGenerator = mapper
                    .getFactory()
                    .createGenerator(writer)) {
                ParseContextSerializer serializer = new ParseContextSerializer();
                serializer.serialize(pc, jsonGenerator, null);
            }
            return writer.toString();
        }
    }


    @Test
    public void testBasic() throws Exception {
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new DateNormalizingMetadataFilter()));
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, metadataFilter);

        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set(EmbeddedDocumentBytesHandler.class, """
                {"k1":1,"k2":"val3" }
                """);
        pc.set(ConfigContainer.class, configContainer);
        ObjectMapper mapper = createMapper();
        String json;
        try (Writer writer = new StringWriter()) {
            try (JsonGenerator jsonGenerator = mapper
                    .getFactory()
                    .createGenerator(writer)) {
                ParseContextSerializer serializer = new ParseContextSerializer();
                serializer.serialize(pc, jsonGenerator, null);
            }
            json = writer.toString();
        }

        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        MetadataFilter dMetadataFilter = deserialized.get(MetadataFilter.class);
        assertTrue(dMetadataFilter instanceof CompositeMetadataFilter);
        List<MetadataFilter> metadataFilters = ((CompositeMetadataFilter) dMetadataFilter).getFilters();
        assertEquals(1, metadataFilters.size());
        assertTrue(metadataFilters.get(0) instanceof DateNormalizingMetadataFilter);
    }

    @Test
    public void testFriendlyNameFormat() throws Exception {
        // Test the new friendly-name format matching tika-config.json
        ParseContext pc = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();

        // Add friendly-named configurations
        configContainer.set("pdf-parser", "{\"ocrStrategy\":\"AUTO\",\"extractInlineImages\":true}");
        configContainer.set("html-parser", "{\"extractScripts\":false}");

        pc.set(ConfigContainer.class, configContainer);

        String json = serializeParseContext(pc);

        // Verify JSON structure
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("pdf-parser"), "Should have pdf-parser field");
        assertTrue(root.has("html-parser"), "Should have html-parser field");
        assertEquals("AUTO", root
                .get("pdf-parser")
                .get("ocrStrategy")
                .asText());
        assertEquals(false, root
                .get("html-parser")
                .get("extractScripts")
                .asBoolean());

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        ConfigContainer deserializedConfig = deserialized.get(ConfigContainer.class);
        assertNotNull(deserializedConfig);
        assertTrue(deserializedConfig
                .get("pdf-parser")
                .isPresent());
        assertTrue(deserializedConfig
                .get("html-parser")
                .isPresent());
    }

    @Test
    public void testLegacyObjectsFormat() throws Exception {
        // Test the legacy format with "objects" field
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new DateNormalizingMetadataFilter()));
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, metadataFilter);

        String json = serializeParseContext(pc);

        // Verify JSON has "objects" field
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);
        assertTrue(root.has("objects"), "Should have objects field for legacy format");

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        MetadataFilter deserializedFilter = deserialized.get(MetadataFilter.class);
        assertNotNull(deserializedFilter);
        assertTrue(deserializedFilter instanceof CompositeMetadataFilter);
    }

    @Test
    public void testMixedFormat() throws Exception {
        // Test that both legacy objects and new friendly names can coexist
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new DateNormalizingMetadataFilter()));
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, metadataFilter);

        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("pdf-parser", "{\"ocrStrategy\":\"NO_OCR\"}");
        pc.set(ConfigContainer.class, configContainer);

        String json = serializeParseContext(pc);

        // Verify both formats are present
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);
        assertTrue(root.has("objects"), "Should have objects field");
        assertTrue(root.has("pdf-parser"), "Should have pdf-parser field");

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Check legacy object
        MetadataFilter deserializedFilter = deserialized.get(MetadataFilter.class);
        assertNotNull(deserializedFilter);
        assertTrue(deserializedFilter instanceof CompositeMetadataFilter);

        // Check friendly-name config
        ConfigContainer deserializedConfig = deserialized.get(ConfigContainer.class);
        assertNotNull(deserializedConfig);
        assertTrue(deserializedConfig
                .get("pdf-parser")
                .isPresent());
    }

    @Test
    public void testConfigDeserializerHelper() throws Exception {
        // Test the ConfigDeserializer helper utility
        ParseContext pc = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();

        // Simulate a PDFParserConfig as JSON
        String pdfConfig = "{\"extractInlineImages\":true,\"ocrStrategy\":\"AUTO\"}";
        configContainer.set("pdf-parser", pdfConfig);

        pc.set(ConfigContainer.class, configContainer);

        // Test hasConfig
        assertTrue(ConfigDeserializer.hasConfig(pc, "pdf-parser"));

        // Test getConfig with a simple JSON deserialization
        // We can't use actual PDFParserConfig here since we don't have the dependency,
        // but we can verify the JSON is retrieved correctly
        String retrievedConfig = pc
                .get(ConfigContainer.class)
                .get("pdf-parser").get().json();
        assertNotNull(retrievedConfig);
        assertTrue(retrievedConfig.contains("extractInlineImages"));
    }

    @Test
    public void testDeserializeFriendlyNameFromJSON() throws Exception {
        // Test deserializing friendly-name format from raw JSON string
        String json = """
                {
                  "pdf-parser": {
                    "ocrStrategy": "AUTO",
                    "extractInlineImages": true
                  },
                  "html-parser": {
                    "extractScripts": false
                  }
                }
                """;

        ObjectMapper mapper = createMapper();
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        ConfigContainer config = deserialized.get(ConfigContainer.class);
        assertNotNull(config);
        assertTrue(config
                .get("pdf-parser")
                .isPresent());
        assertTrue(config
                .get("html-parser")
                .isPresent());

        // Verify the JSON content
        String pdfParserJson = config
                .get("pdf-parser").get().json();
        assertTrue(pdfParserJson.contains("AUTO"));
        assertTrue(pdfParserJson.contains("extractInlineImages"));
    }

    @Test
    public void testDeserializeMixedFromJSON() throws Exception {
        // Test deserializing JSON with both legacy objects and friendly names
        // First create the ParseContext and serialize it to get the correct format
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new DateNormalizingMetadataFilter()));
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, metadataFilter);

        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("pdf-parser", "{\"ocrStrategy\":\"AUTO\"}");
        pc.set(ConfigContainer.class, configContainer);

        // Serialize to JSON
        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(pc);

        // Now deserialize it back
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Verify legacy object was deserialized
        MetadataFilter filter = deserialized.get(MetadataFilter.class);
        assertNotNull(filter);
        assertTrue(filter instanceof CompositeMetadataFilter);

        // Verify friendly-name config was stored
        ConfigContainer config = deserialized.get(ConfigContainer.class);
        assertNotNull(config);
        assertTrue(config
                .get("pdf-parser")
                .isPresent());
    }
}
