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

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.serdes.ParseContextDeserializer;
import org.apache.tika.serialization.serdes.ParseContextSerializer;

/**
 * Tests that custom classes can be serialized/deserialized via JSON configs.
 * <p>
 * With the jsonConfigs approach, serialization works via JSON strings stored
 * in ParseContext. Custom classes are deserialized at runtime.
 */
public class CustomClassSerializationTest {

    /**
     * Example custom metadata filter that uppercases all values.
     * This simulates a user's custom class (e.g., in package com.acme).
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
        ObjectMapper mapper = TikaObjectMapperFactory.getMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        module.addSerializer(ParseContext.class, new ParseContextSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    @Test
    public void testJsonConfigRoundTrip() throws Exception {
        // Create a ParseContext with JSON config
        ParseContext pc = new ParseContext();
        pc.setJsonConfig("my-filter", "{\"prefix\":\"TEST_\"}");

        // Serialize
        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(pc);

        // Verify JSON contains the config
        assertTrue(json.contains("my-filter"), "JSON should contain the config key");
        assertTrue(json.contains("TEST_"), "JSON should contain the prefix value");

        // Deserialize
        ParseContext deserialized = mapper.readValue(json, ParseContext.class);

        // Verify the JSON config is preserved
        assertTrue(deserialized.hasJsonConfig("my-filter"));
        String deserializedJson = deserialized.getJsonConfig("my-filter").json();
        assertTrue(deserializedJson.contains("TEST_"));
    }

    @Test
    public void testCustomClassDeserialization() throws Exception {
        // Test that a custom class can be deserialized from JSON
        // This is done via ConfigDeserializer.getConfig() which uses Jackson
        ParseContext pc = new ParseContext();
        pc.setJsonConfig("my-filter", "{\"prefix\":\"CUSTOM_\"}");

        // Use ConfigDeserializer to get and deserialize the config
        // This simulates what a custom component would do
        ObjectMapper mapper = createMapper();
        String jsonConfig = pc.getJsonConfig("my-filter").json();

        MyUpperCasingMetadataFilter filter = mapper.readValue(jsonConfig, MyUpperCasingMetadataFilter.class);

        assertNotNull(filter);
        assertEquals("CUSTOM_", filter.getPrefix());

        // Verify the filter works
        Metadata metadata = new Metadata();
        metadata.add("test", "value");
        java.util.List<Metadata> metadataList = new java.util.ArrayList<>();
        metadataList.add(metadata);
        filter.filter(metadataList);
        assertEquals("CUSTOM_VALUE", metadata.get("test"));
    }
}
