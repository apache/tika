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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
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

    public static final String PARSE_CONTEXT = "parse-context";
    public static final String TYPED = "typed";

    private static ObjectMapper plainMapper() {
        return TikaObjectMapperFactory.getPlainMapper();
    }

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        // Track which friendly names have been serialized under "typed"
        // so we can skip them when serializing jsonConfigs (avoid duplicates)
        Set<String> serializedNames = new HashSet<>();

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

            // Try to find a friendly component name for the value's class, otherwise use FQCN
            String keyName = ComponentNameResolver.getFriendlyName(value.getClass());
            if (keyName == null) {
                keyName = value.getClass().getName();
            }

            if (!hasTypedObjects) {
                gen.writeFieldName(TYPED);
                gen.writeStartObject();
                hasTypedObjects = true;
            }
            gen.writeFieldName(keyName);
            // Use writeTree instead of writeRawValue for binary format support (e.g., Smile)
            // and stricter validation (fails early if value can't be serialized)
            gen.writeTree(plainMapper().valueToTree(value));

            // Track this name so we skip it in jsonConfigs
            serializedNames.add(keyName);
        }

        if (hasTypedObjects) {
            gen.writeEndObject();
        }

        // Then, serialize JSON configs at the top level
        // Skip entries that were already serialized under "typed" (they've been resolved)
        Map<String, JsonConfig> jsonConfigs = parseContext.getJsonConfigs();
        for (Map.Entry<String, JsonConfig> entry : jsonConfigs.entrySet()) {
            if (serializedNames.contains(entry.getKey())) {
                // Already serialized under "typed", skip to avoid duplicate
                continue;
            }
            gen.writeFieldName(entry.getKey());
            // Parse the JSON string into a tree for binary format support
            gen.writeTree(plainMapper().readTree(entry.getValue().json()));
        }

        gen.writeEndObject();
    }

}
