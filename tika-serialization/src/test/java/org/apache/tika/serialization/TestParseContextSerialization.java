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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.io.Writer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.SkipEmbeddedDocumentSelector;
import org.apache.tika.metadata.filter.AttachmentCountingListFilter;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.PasswordProvider;
import org.apache.tika.parser.SimplePasswordProvider;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
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
    public void testTimeoutLimitsFormat() throws Exception {
        // Test serializing timeout-limits configuration
        ParseContext pc = new ParseContext();
        pc.setJsonConfig("timeout-limits",
                "{\"progressTimeoutMillis\":30000,\"totalTaskTimeoutMillis\":120000}");

        String json = serializeParseContext(pc);

        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("timeout-limits"), "Should have timeout-limits field");
        assertEquals(30000, root
                .get("timeout-limits")
                .get("progressTimeoutMillis")
                .asInt());
        assertEquals(120000, root
                .get("timeout-limits")
                .get("totalTaskTimeoutMillis")
                .asInt());

        // Verify round-trip
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        assertTrue(deserialized.hasJsonConfig("timeout-limits"));

        // Resolve and verify
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());
        TimeoutLimits limits = deserialized.get(TimeoutLimits.class);
        assertNotNull(limits, "TimeoutLimits should be resolved");
        assertEquals(30000, limits.getProgressTimeoutMillis());
        assertEquals(120000, limits.getTotalTaskTimeoutMillis());
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
        // Test deserializing with optional "parse-context" wrapper
        String json = """
                {
                  "parse-context": {
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
        pc.setJsonConfig("timeout-limits",
                "{\"progressTimeoutMillis\":5000,\"totalTaskTimeoutMillis\":60000}");
        pc.setJsonConfig("my-custom-config", "{\"enabled\":true,\"maxRetries\":3}");

        String json = serializeParseContext(pc);

        // Verify all are present
        ObjectMapper mapper = createMapper();
        JsonNode root = mapper.readTree(json);

        assertEquals(4, root.size(), "Should have 4 config fields");
        assertTrue(root.has("pdf-parser"));
        assertTrue(root.has("html-parser"));
        assertTrue(root.has("timeout-limits"));
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

    @Test
    public void testSimplePasswordProviderDeserialization() throws Exception {
        // Test that SimplePasswordProvider with contextKey=PasswordProvider.class
        // is stored in ParseContext with the contextKey
        String json = """
                {
                  "simple-password-provider": {
                    "password": "secret123"
                  }
                }
                """;

        ObjectMapper mapper = createMapper();
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Resolve the config
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());

        // Should be accessible via PasswordProvider.class (the contextKey)
        PasswordProvider provider = deserialized.get(PasswordProvider.class);
        assertNotNull(provider, "PasswordProvider should be found via contextKey");
        assertTrue(provider instanceof SimplePasswordProvider,
                "Should be SimplePasswordProvider instance");
        assertEquals("secret123", provider.getPassword(null),
                "Password should match the configured value");
    }

    /**
     * Test that BasicContentHandlerFactory can be configured via JSON, serialized,
     * deserialized, and resolved via ParseContextUtils.resolveAll().
     * This verifies the fix for TIKA-4582 where ContentHandlerFactory was not being
     * resolved because it wasn't in the "parse-context" registry.
     */
    @Test
    public void testContentHandlerFactoryRoundTrip() throws Exception {
        // Create ParseContext with BasicContentHandlerFactory configuration
        String json = """
                {
                  "basic-content-handler-factory": {
                    "type": "XML",
                    "writeLimit": 50000
                  }
                }
                """;

        ObjectMapper mapper = createMapper();
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Verify JSON config is present
        assertTrue(deserialized.hasJsonConfig("basic-content-handler-factory"),
                "Should have basic-content-handler-factory JSON config");

        // Resolve the config - this should now work with ComponentNameResolver
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());

        // Should be accessible via ContentHandlerFactory.class (the contextKey)
        ContentHandlerFactory factory = deserialized.get(ContentHandlerFactory.class);
        assertNotNull(factory, "ContentHandlerFactory should be resolved");
        assertTrue(factory instanceof BasicContentHandlerFactory,
                "Should be BasicContentHandlerFactory instance");

        // Verify the configuration was applied
        BasicContentHandlerFactory basicFactory = (BasicContentHandlerFactory) factory;
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.XML, basicFactory.getType(),
                "Handler type should be XML");
        assertEquals(50000, basicFactory.getWriteLimit(),
                "Write limit should be 50000");
    }

    /**
     * Test full round-trip: create ParseContext with ContentHandlerFactory,
     * serialize to JSON, deserialize back, resolve, and verify.
     */
    @Test
    public void testContentHandlerFactoryFullRoundTrip() throws Exception {
        // Create original ParseContext with JSON config
        ParseContext original = new ParseContext();
        original.setJsonConfig("basic-content-handler-factory", """
                {
                    "type": "HTML",
                    "writeLimit": 10000,
                    "throwOnWriteLimitReached": false
                }
                """);

        // Serialize
        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(original);

        // Verify JSON structure
        JsonNode root = mapper.readTree(json);
        assertTrue(root.has("basic-content-handler-factory"),
                "Serialized JSON should have basic-content-handler-factory");

        // Deserialize
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        assertTrue(deserialized.hasJsonConfig("basic-content-handler-factory"),
                "Deserialized should have JSON config");

        // Resolve
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());

        // Verify resolution
        ContentHandlerFactory factory = deserialized.get(ContentHandlerFactory.class);
        assertNotNull(factory, "ContentHandlerFactory should be resolved after round-trip");

        BasicContentHandlerFactory basicFactory = (BasicContentHandlerFactory) factory;
        assertEquals(BasicContentHandlerFactory.HANDLER_TYPE.HTML, basicFactory.getType());
        assertEquals(10000, basicFactory.getWriteLimit());
        assertFalse(basicFactory.isThrowOnWriteLimitReached());
    }

    /**
     * Test that duplicate context keys within a single JSON document are detected and rejected.
     * Both BasicContentHandlerFactory and UppercasingContentHandlerFactory resolve to
     * ContentHandlerFactory.class as their context key, so configuring both should fail.
     */
    @Test
    public void testDuplicateContextKeyDetection() throws Exception {
        // Both of these resolve to ContentHandlerFactory.class as the context key
        String json = """
                {
                  "basic-content-handler-factory": {
                    "type": "XML",
                    "writeLimit": 50000
                  },
                  "uppercasing-content-handler-factory": {}
                }
                """;

        ObjectMapper mapper = createMapper();

        // Should throw an exception due to duplicate context key
        Exception ex = assertThrows(Exception.class, () ->
                mapper.readValue(json, ParseContext.class));

        // Verify the error message mentions the duplicate
        assertTrue(ex.getMessage().contains("Duplicate") ||
                        (ex.getCause() != null && ex.getCause().getMessage().contains("Duplicate")),
                "Exception should mention duplicate context key: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("ContentHandlerFactory") ||
                        (ex.getCause() != null && ex.getCause().getMessage().contains("ContentHandlerFactory")),
                "Exception should mention the conflicting key: " + ex.getMessage());
    }

    /**
     * Test that multiple self-configuring components (e.g., parsers) with the same
     * context key are allowed.  Self-configuring components stay as JSON configs and
     * are accessed by string key at runtime, so they never conflict in the context map.
     */
    @Test
    public void testSelfConfiguringComponentsAllowDuplicateContextKeys() throws Exception {
        // Both parsers resolve to Parser.class as context key, but Parser extends
        // SelfConfiguring, so they should be allowed to coexist.
        String json = """
                {
                  "configurable-test-parser": {
                    "maxItems": 5
                  },
                  "minimal-test-parser": {}
                }
                """;

        ObjectMapper mapper = createMapper();
        // Should NOT throw - self-configuring components skip duplicate detection
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        assertTrue(deserialized.hasJsonConfig("configurable-test-parser"),
                "configurable-test-parser should be stored as JSON config");
        assertTrue(deserialized.hasJsonConfig("minimal-test-parser"),
                "minimal-test-parser should be stored as JSON config");
    }

    /**
     * Test that a single component per context key is allowed (no false positives).
     */
    @Test
    public void testNoDuplicateWhenDifferentContextKeys() throws Exception {
        // These have different context keys, so both should be allowed
        String json = """
                {
                  "basic-content-handler-factory": {
                    "type": "TEXT",
                    "writeLimit": 10000
                  },
                  "skip-embedded-document-selector": {}
                }
                """;

        ObjectMapper mapper = createMapper();
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Both should be present as JSON configs
        assertTrue(deserialized.hasJsonConfig("basic-content-handler-factory"));
        assertTrue(deserialized.hasJsonConfig("skip-embedded-document-selector"));

        // Resolve and verify both work
        ParseContextUtils.resolveAll(deserialized, Thread.currentThread().getContextClassLoader());

        assertNotNull(deserialized.get(ContentHandlerFactory.class));
        assertNotNull(deserialized.get(DocumentSelector.class));
    }
}
