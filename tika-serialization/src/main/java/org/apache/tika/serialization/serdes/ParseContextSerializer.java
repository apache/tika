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
package org.apache.tika.serialization.serdes;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Serializes ParseContext to JSON.
 * <p>
 * Typed objects from the context map are serialized under a "typed" key.
 * JSON configs are serialized at the top level.
 * <p>
 * Example output:
 * <pre>
 * {
 *   "typed": {
 *     "handler-config": {"type": "XML", "parseMode": "RMETA"}
 *   },
 *   "metadata-filters": ["mock-upper-case-filter"]
 * }
 * </pre>
 */
public class ParseContextSerializer extends JsonSerializer<ParseContext> {

    public static final String PARSE_CONTEXT = "parseContext";
    public static final String TYPED = "typed";

    // Plain mapper for serializing values without TikaModule's component wrapping
    private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper();

    static {
        // Allow serialization of classes with no properties
        PLAIN_MAPPER.disable(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // First, serialize typed objects from the context map under "typed" key
        Map<String, Object> contextMap = parseContext.getContextMap();
        boolean hasTypedObjects = false;

        for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
            String keyClassName = entry.getKey();
            Object value = entry.getValue();

            // Skip null values
            if (value == null) {
                continue;
            }

            // Use the actual value's class for serialization, not the key class (which may be an interface)
            // This ensures we can deserialize back to the concrete class
            String valueClassName = value.getClass().getName();

            // Try to find a friendly component name for the value's class, otherwise use FQCN
            String keyName = findComponentName(valueClassName);
            if (keyName == null) {
                keyName = valueClassName;
            }

            if (!hasTypedObjects) {
                gen.writeFieldName(TYPED);
                gen.writeStartObject();
                hasTypedObjects = true;
            }
            gen.writeFieldName(keyName);
            // Use writeTree instead of writeRawValue for binary format support (e.g., Smile)
            // and stricter validation (fails early if value can't be serialized)
            gen.writeTree(PLAIN_MAPPER.valueToTree(value));
        }

        if (hasTypedObjects) {
            gen.writeEndObject();
        }

        // Then, serialize JSON configs at the top level
        Map<String, JsonConfig> jsonConfigs = parseContext.getJsonConfigs();
        for (Map.Entry<String, JsonConfig> entry : jsonConfigs.entrySet()) {
            gen.writeFieldName(entry.getKey());
            // Parse the JSON string into a tree for binary format support
            gen.writeTree(PLAIN_MAPPER.readTree(entry.getValue().json()));
        }

        gen.writeEndObject();
    }

    /**
     * Finds the component name for a class.
     * Uses ComponentNameResolver for registry lookup. Only classes registered
     * in a component registry will be serialized.
     *
     * @param className the fully qualified class name
     * @return the component name, or null if not registered
     */
    private String findComponentName(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return ComponentNameResolver.getFriendlyName(clazz);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
