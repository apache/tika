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

    @Override
    public void serialize(ParseContext parseContext, JsonGenerator gen,
                         SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        ObjectMapper mapper = (ObjectMapper) gen.getCodec();

        // First, serialize typed objects from the context map under "typed" key
        Map<String, Object> contextMap = parseContext.getContextMap();
        boolean hasTypedObjects = false;

        for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
            String className = entry.getKey();
            String componentName = findComponentName(className);
            if (componentName != null) {
                if (!hasTypedObjects) {
                    gen.writeFieldName(TYPED);
                    gen.writeStartObject();
                    hasTypedObjects = true;
                }
                gen.writeFieldName(componentName);
                gen.writeRawValue(mapper.writeValueAsString(entry.getValue()));
            }
        }

        if (hasTypedObjects) {
            gen.writeEndObject();
        }

        // Then, serialize JSON configs at the top level
        Map<String, JsonConfig> jsonConfigs = parseContext.getJsonConfigs();
        for (Map.Entry<String, JsonConfig> entry : jsonConfigs.entrySet()) {
            gen.writeFieldName(entry.getKey());
            gen.writeRawValue(entry.getValue().json());
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
