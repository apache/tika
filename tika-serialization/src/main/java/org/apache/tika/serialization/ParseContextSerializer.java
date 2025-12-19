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

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;

/**
 * Serializes ParseContext to JSON using friendly names.
 * <p>
 * Serializes:
 * <ul>
 *   <li>ConfigContainer contents (JSON strings) - written as-is</li>
 *   <li>Objects in ParseContext that have registered friendly names - serialized via Jackson</li>
 * </ul>
 * <p>
 * Example output:
 * <pre>
 * {
 *   "pdf-parser": {"extractActions": true},
 *   "tika-task-timeout": {"timeoutMillis": 5000},
 *   "handler-config": {"type": "XML", "parseMode": "RMETA"}
 * }
 * </pre>
 */
public class ParseContextSerializer extends JsonSerializer<ParseContext> {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextSerializer.class);
    public static final String PARSE_CONTEXT = "parseContext";

    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.getMapper();

    // Lazily loaded registry for looking up friendly names
    private static volatile ComponentRegistry registry;

    private static ComponentRegistry getRegistry() {
        if (registry == null) {
            synchronized (ParseContextSerializer.class) {
                if (registry == null) {
                    try {
                        registry = new ComponentRegistry("other-configs",
                                ParseContextSerializer.class.getClassLoader());
                    } catch (TikaConfigException e) {
                        LOG.warn("Failed to load component registry for serialization", e);
                        // Return null - objects without friendly names won't be serialized
                    }
                }
            }
        }
        return registry;
    }

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator jsonGenerator,
                         SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();

        Set<String> writtenKeys = new HashSet<>();
        ConfigContainer configContainer = parseContext.get(ConfigContainer.class);

        // First, write ConfigContainer contents (these are already JSON strings)
        if (configContainer != null) {
            for (String key : configContainer.getKeys()) {
                jsonGenerator.writeFieldName(key);
                jsonGenerator.writeRawValue(configContainer.get(key).get().json());
                writtenKeys.add(key);
            }
        }

        // Then, serialize objects from ParseContext that have registered friendly names
        // or are stored under Tika type keys (for polymorphic custom subclasses)
        ComponentRegistry reg = getRegistry();
        Map<String, Object> contextMap = parseContext.getContextMap();
        for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
            // Skip ConfigContainer - already handled above
            if (entry.getKey().equals(ConfigContainer.class.getName())) {
                continue;
            }

            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            // Try to get friendly name for this object's class
            String friendlyName = (reg != null) ? reg.getFriendlyName(value.getClass()) : null;

            // Determine key: prefer friendly name, fall back to FQCN for Tika types
            String key;
            if (friendlyName != null) {
                // Use friendly name if available (deserializer will resolve via registry)
                key = friendlyName;
            } else if (entry.getKey().startsWith("org.apache.tika.")) {
                // For Tika types without friendly names, use the context key (FQCN)
                key = entry.getKey();
            } else {
                // Skip non-Tika types without friendly names (e.g., String, custom non-Tika classes)
                continue;
            }

            if (!writtenKeys.contains(key)) {
                jsonGenerator.writeFieldName(key);
                // Write wrapper object format with type info for polymorphic deserialization
                // Format: {"concrete-class-name": {properties...}}
                jsonGenerator.writeStartObject();
                String typeName = (friendlyName != null) ? friendlyName :
                        ComponentNameResolver.getFriendlyName(value.getClass());
                if (typeName == null) {
                    typeName = value.getClass().getName();
                }
                jsonGenerator.writeFieldName(typeName);
                MAPPER.writeValue(jsonGenerator, value);
                jsonGenerator.writeEndObject();
                writtenKeys.add(key);
            }
        }

        jsonGenerator.writeEndObject();
    }
}
