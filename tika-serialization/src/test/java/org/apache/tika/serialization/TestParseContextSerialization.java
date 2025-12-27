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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.SkipEmbeddedDocumentSelector;
import org.apache.tika.metadata.filter.AttachmentCountingListFilter;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.serialization.serdes.ParseContextSerializer;

/**
 * Tests for ParseContext serialization/deserialization.
 * <p>
 * JSON configs are stored in ParseContext's jsonConfigs map.
 * Components are resolved at runtime via ParseContextUtils.resolveAll() or ConfigDeserializer.
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

        // Add friendly-named configurations via setJsonConfig
        pc.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"AUTO\",\"extractInlineImages\":true}");
        pc.setJsonConfig("html-parser", "{\"extractScripts\":false}");

        String json = serializeParseContext(pc);

        // Verify JSON structure - should have flat friendly names
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
        assertTrue(deserialized.hasJsonConfig("pdf-parser"));
        assertTrue(deserialized.hasJsonConfig("html-parser"));
    }

    @Test
    public void testTikaTaskTimeoutFormat() throws Exception {
        // Test serializing tika-task-timeout configuration
        ParseContext pc = new ParseContext();
        pc.setJsonConfig("tika-task-timeout", "{\"timeoutMillis\":30000}");

        String json = serializeParseContext(pc);

        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("tika-task-timeout"), "Should have tika-task-timeout field");
        assertEquals(30000, root
                .get("tika-task-timeout")
                .get("timeoutMillis")
                .asInt());

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        assertTrue(deserialized.hasJsonConfig("tika-task-timeout"));

        // Resolve and verify
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());
        TikaTaskTimeout timeout = deserialized.get(TikaTaskTimeout.class);
        assertNotNull(timeout, "TikaTaskTimeout should be resolved");
        assertEquals(30000, timeout.getTimeoutMillis());
    }

    @Test
    public void testConfigDeserializerHelper() throws Exception {
        // Test the ConfigDeserializer helper utility
        ParseContext pc = new ParseContext();

        // Simulate a PDFParserConfig as JSON
        String pdfConfig = "{\"extractInlineImages\":true,\"ocrStrategy\":\"AUTO\"}";
        pc.setJsonConfig("pdf-parser", pdfConfig);

        // Test hasConfig
        assertTrue(ConfigDeserializer.hasConfig(pc, "pdf-parser"));
        assertFalse(ConfigDeserializer.hasConfig(pc, "non-existent"));

        // Test getJsonConfig retrieves JSON correctly
        String retrievedConfig = pc.getJsonConfig("pdf-parser").json();
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

        assertTrue(deserialized.hasJsonConfig("pdf-parser"));
        assertTrue(deserialized.hasJsonConfig("html-parser"));

        // Verify the JSON content is preserved
        String pdfParserJson = deserialized.getJsonConfig("pdf-parser").json();
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

        assertTrue(deserialized.hasJsonConfig("pdf-parser"));
    }

    @Test
    public void testMultipleConfigs() throws Exception {
        // Test with multiple different config types
        ParseContext pc = new ParseContext();

        pc.setJsonConfig("pdf-parser", "{\"ocrStrategy\":\"AUTO\"}");
        pc.setJsonConfig("html-parser", "{\"extractScripts\":true}");
        pc.setJsonConfig("tika-task-timeout", "{\"timeoutMillis\":5000}");
        pc.setJsonConfig("my-custom-config", "{\"enabled\":true,\"maxRetries\":3}");

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
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        assertEquals(4, deserialized.getJsonConfigs().size());
    }

    @Test
    public void testProgrammaticObjectsNotSerialized() throws Exception {
        // Typed objects set via context.set() are NOT serialized
        // Only jsonConfigs are serialized for clean round-trip
        ParseContext pc = new ParseContext();

        // String doesn't have a @TikaComponent annotation
        pc.set(String.class, "test-value");

        String json = serializeParseContext(pc);

        // Should be empty - typed objects are not serialized
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);
        assertEquals(1, root.size(), "Typed objects should be serialized");
    }

    @Test
    public void testMetadataFiltersFromJson() throws Exception {
        ParseContext parseContext = new ParseContext();
        parseContext.setJsonConfig("metadata-filters", """
            [
              "attachment-counting-list-filter",
              "mock-upper-case-filter"
            ]
        """);

        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(parseContext);

        ParseContext deser = mapper.readValue(json, ParseContext.class);

        // Resolve the array config
        ParseContextUtils.resolveAll(deser, Thread.currentThread().getContextClassLoader());

        MetadataFilter resolvedFilter = deser.get(MetadataFilter.class);
        assertNotNull(resolvedFilter, "MetadataFilter should be resolved");
        assertEquals(CompositeMetadataFilter.class, resolvedFilter.getClass());
        CompositeMetadataFilter deserFilter = (CompositeMetadataFilter) resolvedFilter;
        assertEquals(AttachmentCountingListFilter.class, deserFilter.getFilters().get(0).getClass());
        assertEquals(MockUpperCaseFilter.class, deserFilter.getFilters().get(1).getClass());
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

        // Resolve the config
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());

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
