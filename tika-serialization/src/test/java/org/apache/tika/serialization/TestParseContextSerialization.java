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

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.SkipEmbeddedDocumentSelector;
import org.apache.tika.metadata.filter.AttachmentCountingListFilter;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.parser.ParseContext;

/**
 * Tests for ParseContext serialization/deserialization.
 * <p>
 * All configs use friendly names and are stored in ConfigContainer.
 * Components are resolved at runtime via ParseContextUtils.resolveAll().
 */
public class TestParseContextSerialization {

    private ObjectMapper createMapper() {
        // Start with the properly configured mapper that has polymorphic type handling
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();

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
    public void testEmptyParseContext() throws Exception {
        ParseContext pc = new ParseContext();
        String json = serializeParseContext(pc);

        // Empty ParseContext should serialize to empty object
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);
        assertEquals(0, root.size(), "Empty ParseContext should have no fields");

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        assertNotNull(deserialized);
    }

    @Test
    public void testFriendlyNameFormat() throws Exception {
        // Test the friendly-name format
        ParseContext pc = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();

        // Add friendly-named configurations
        configContainer.set("pdf-parser", "{\"ocrStrategy\":\"AUTO\",\"extractInlineImages\":true}");
        configContainer.set("html-parser", "{\"extractScripts\":false}");

        pc.set(ConfigContainer.class, configContainer);

        String json = serializeParseContext(pc);

        // Verify JSON structure - should have flat friendly names, no "objects" wrapper
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertFalse(root.has("objects"), "Should NOT have objects field");
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
    public void testTikaTaskTimeoutFormat() throws Exception {
        // Test serializing tika-task-timeout configuration
        ParseContext pc = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();

        configContainer.set("tika-task-timeout", "{\"timeoutMillis\":30000}");
        pc.set(ConfigContainer.class, configContainer);

        String json = serializeParseContext(pc);

        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("tika-task-timeout"), "Should have tika-task-timeout field");
        assertEquals(30000, root
                .get("tika-task-timeout")
                .get("timeoutMillis")
                .asInt());

        // Verify round-trip - TikaTaskTimeout is NOT SelfConfiguring,
        // so it gets resolved directly into ParseContext (not ConfigContainer)
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        TikaTaskTimeout timeout = deserialized.get(TikaTaskTimeout.class);
        assertNotNull(timeout, "TikaTaskTimeout should be resolved directly into ParseContext");
        assertEquals(30000, timeout.getTimeoutMillis());
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
        assertFalse(ConfigDeserializer.hasConfig(pc, "non-existent"));

        // Test getConfig retrieves JSON correctly
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

        // Verify the JSON content is preserved
        String pdfParserJson = config
                .get("pdf-parser").get().json();
        assertTrue(pdfParserJson.contains("AUTO"));
        assertTrue(pdfParserJson.contains("extractInlineImages"));
    }

    @Test
    public void testDeserializeWithParseContextWrapper() throws Exception {
        // Test deserializing with optional "parseContext" wrapper
        String json = """
                {
                  "parseContext": {
                    "pdf-parser": {
                      "ocrStrategy": "NO_OCR"
                    }
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
    }

    @Test
    public void testMultipleConfigs() throws Exception {
        // Test with multiple different config types
        ParseContext pc = new ParseContext();
        ConfigContainer configContainer = new ConfigContainer();

        configContainer.set("pdf-parser", "{\"ocrStrategy\":\"AUTO\"}");
        configContainer.set("html-parser", "{\"extractScripts\":true}");
        configContainer.set("tika-task-timeout", "{\"timeoutMillis\":5000}");
        configContainer.set("my-custom-config", "{\"enabled\":true,\"maxRetries\":3}");

        pc.set(ConfigContainer.class, configContainer);

        String json = serializeParseContext(pc);

        // Verify all are present
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertEquals(4, root.size(), "Should have 4 config fields");
        assertTrue(root.has("pdf-parser"));
        assertTrue(root.has("html-parser"));
        assertTrue(root.has("tika-task-timeout"));
        assertTrue(root.has("my-custom-config"));

        // Verify round-trip
        // After deserialization:
        // - pdf-parser, html-parser → Parsers are SelfConfiguring → stay in ConfigContainer
        // - my-custom-config → not in registry → stays in ConfigContainer
        // - tika-task-timeout → TikaTaskTimeout is NOT SelfConfiguring → resolved directly
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        ConfigContainer deserializedConfig = deserialized.get(ConfigContainer.class);
        assertEquals(3, deserializedConfig.getKeys().size(),
                "Should have 3 configs in ConfigContainer (SelfConfiguring + unknown)");
        assertTrue(deserializedConfig.get("pdf-parser").isPresent());
        assertTrue(deserializedConfig.get("html-parser").isPresent());
        assertTrue(deserializedConfig.get("my-custom-config").isPresent());

        // TikaTaskTimeout should be resolved directly into ParseContext
        TikaTaskTimeout timeout = deserialized.get(TikaTaskTimeout.class);
        assertNotNull(timeout, "TikaTaskTimeout should be resolved directly");
        assertEquals(5000, timeout.getTimeoutMillis());
    }

    @Test
    public void testProgrammaticObjectsWithoutFriendlyName() throws Exception {
        // Objects without a registered friendly name are NOT serialized
        ParseContext pc = new ParseContext();

        // String doesn't have a @TikaComponent annotation, so it won't serialize
        pc.set(String.class, "test-value");

        String json = serializeParseContext(pc);

        // Should be empty - String doesn't have a friendly name
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);
        assertEquals(0, root.size(), "Objects without friendly names should not be serialized");
    }

    @Test
    public void testMetadataListConfigContainer() throws Exception {
        ConfigContainer configContainer = new ConfigContainer();
        configContainer.set("metadata-filters", """
            [
              "attachment-counting-list-filter",
              "mock-upper-case-filter"
            ]
        """);
        ParseContext parseContext = new ParseContext();
        parseContext.set(ConfigContainer.class, configContainer);

        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(parseContext);

        ParseContext deser = mapper.readValue(json, ParseContext.class);
        MetadataFilter resolvedFilter = deser.get(MetadataFilter.class);
        assertNotNull(resolvedFilter, "MetadataFilter should be resolved");
        assertEquals(CompositeMetadataFilter.class, resolvedFilter.getClass());
        CompositeMetadataFilter deserFilter = (CompositeMetadataFilter) resolvedFilter;
        assertEquals(AttachmentCountingListFilter.class, deserFilter.getFilters().get(0).getClass());
    }


    @Test
    public void testMetadataListPOJO() throws Exception {
        CompositeMetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new AttachmentCountingListFilter(), new MockUpperCaseFilter()));

        ParseContext parseContext = new ParseContext();
        parseContext.set(MetadataFilter.class, metadataFilter);

        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(parseContext);

        ParseContext deser = mapper.readValue(json, ParseContext.class);
        MetadataFilter resolvedFilter = deser.get(MetadataFilter.class);
        assertNotNull(resolvedFilter, "MetadataFilter should be resolved");
        assertEquals(CompositeMetadataFilter.class, resolvedFilter.getClass());
        CompositeMetadataFilter deserFilter = (CompositeMetadataFilter) resolvedFilter;
        assertEquals(AttachmentCountingListFilter.class, deserFilter.getFilters().get(0).getClass());
    }

    @Test
    public void testContextKeyDeserialization() throws Exception {
        // Test that components with @TikaComponent(contextKey=...) are stored
        // in ParseContext with the contextKey, not the component class.
        // SkipEmbeddedDocumentSelector has contextKey=DocumentSelector.class
        String json = """
                {
                  "skip-embedded-document-selector": {}
                }
                """;

        ObjectMapper mapper = createMapper();
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Should be accessible via DocumentSelector.class (the contextKey)
        DocumentSelector selector = deserialized.get(DocumentSelector.class);
        assertNotNull(selector, "DocumentSelector should be found via contextKey");
        assertTrue(selector instanceof SkipEmbeddedDocumentSelector,
                "Should be SkipEmbeddedDocumentSelector instance");

        // The selector should skip all embedded documents (return false)
        assertFalse(selector.select(new org.apache.tika.metadata.Metadata()),
                "SkipEmbeddedDocumentSelector should return false for all documents");
    }
}
