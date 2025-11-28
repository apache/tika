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
import java.util.Locale;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

/**
 * Tests that users can serialize their own custom classes in ParseContext
 * by adding a META-INF/tika-serialization-allowlist.txt file to their JAR.
 *
 * <p>Custom classes MUST implement Serializable because the Pipes parser
 * uses Java serialization to pass ParseContext between processes.</p>
 *
 * <p>To enable JSON serialization of custom classes:</p>
 * <ol>
 *   <li>Implement Serializable</li>
 *   <li>Provide a no-arg constructor</li>
 *   <li>Follow JavaBean conventions (getters/setters)</li>
 *   <li>Add your package prefix to META-INF/tika-serialization-allowlist.txt</li>
 * </ol>
 */
public class CustomClassSerializationTest {

    /**
     * Example custom metadata filter that uppercases all values.
     * This simulates a user's custom class (e.g., in package com.acme).
     *
     * <p>Note: Extends Serializable MetadataFilter - this is REQUIRED for use with Pipes parser.</p>
     */
    public static class MyUpperCasingMetadataFilter extends MetadataFilter {
        private String prefix = "";

        public MyUpperCasingMetadataFilter() {
        }

        public MyUpperCasingMetadataFilter(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public java.util.List<Metadata> filter(java.util.List<Metadata> metadataList) {
            for (Metadata metadata : metadataList) {
                for (String name : metadata.names()) {
                    String[] values = metadata.getValues(name);
                    metadata.remove(name);
                    for (String value : values) {
                        metadata.add(name, prefix + value.toUpperCase(Locale.ROOT));
                    }
                }
            }
            return metadataList;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MyUpperCasingMetadataFilter)) return false;
            MyUpperCasingMetadataFilter that = (MyUpperCasingMetadataFilter) o;
            return prefix.equals(that.prefix);
        }

        @Override
        public int hashCode() {
            return prefix.hashCode();
        }
    }

    private ObjectMapper createMapper() {
        ObjectMapper mapper = PolymorphicObjectMapperFactory.getMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    public void testCustomMetadataFilterSerialization() throws Exception {
        // Create a custom metadata filter
        MyUpperCasingMetadataFilter customFilter = new MyUpperCasingMetadataFilter("TEST_");

        // Put it in ParseContext - store as MetadataFilter (the abstract base type)
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, (MetadataFilter) customFilter);

        // Serialize
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

        // Verify JSON contains type information
        assertTrue(json.contains("MyUpperCasingMetadataFilter"),
                "JSON should contain the custom class name");

        // Deserialize
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        MetadataFilter deserializedFilter = deserialized.get(MetadataFilter.class);

        // Verify polymorphic deserialization worked - we get back the concrete type
        assertNotNull(deserializedFilter, "MetadataFilter should not be null");
        assertTrue(deserializedFilter instanceof MyUpperCasingMetadataFilter,
                "Filter should be MyUpperCasingMetadataFilter (polymorphic deserialization)");

        MyUpperCasingMetadataFilter typedFilter = (MyUpperCasingMetadataFilter) deserializedFilter;
        assertEquals("TEST_", typedFilter.getPrefix(), "Prefix should be preserved");

        // Verify it works
        Metadata metadata = new Metadata();
        metadata.add("test", "value");
        java.util.List<Metadata> metadataList = new java.util.ArrayList<>();
        metadataList.add(metadata);
        typedFilter.filter(metadataList);
        assertEquals("TEST_VALUE", metadata.get("test"), "Filter should uppercase with prefix");
    }
}
