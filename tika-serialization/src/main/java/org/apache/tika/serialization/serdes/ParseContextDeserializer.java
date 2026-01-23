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

import static org.apache.tika.serialization.serdes.ParseContextSerializer.PARSE_CONTEXT;
import static org.apache.tika.serialization.serdes.ParseContextSerializer.TYPED;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Deserializes ParseContext from JSON.
 * <p>
 * Handles two types of entries:
 * <ul>
 *   <li>"typed" section: Deserialized directly to typed objects in the context map</li>
 *   <li>Other entries: Stored as JSON configs for lazy resolution</li>
 * </ul>
 * <p>
 * Example input:
 * <pre>
 * {
 *   "typed": {
 *     "handler-config": {"type": "XML", "parseMode": "RMETA"}
 *   },
 *   "metadata-filters": ["mock-upper-case-filter"]
 * }
 * </pre>
 */
public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextDeserializer.class);

    // Plain JSON mapper for converting JsonNodes to JSON strings.
    // This is needed because the main mapper may use a binary format (e.g., Smile)
    // which doesn't support writeValueAsString().
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Override
    public ParseContext deserialize(JsonParser jsonParser, DeserializationContext ctxt)
            throws IOException {
        JsonNode root = jsonParser.readValueAsTree();
        return readParseContext(root, (ObjectMapper) jsonParser.getCodec());
    }

    /**
     * Deserializes a ParseContext from a JsonNode.
     * <p>
     * The "typed" section is deserialized directly to typed objects in the context map.
     * All other fields are stored as JSON config strings for lazy resolution.
     *
     * @param jsonNode the JSON node containing the ParseContext data
     * @param mapper   the ObjectMapper for deserializing typed objects
     * @return the deserialized ParseContext
     * @throws IOException if deserialization fails
     */
    public static ParseContext readParseContext(JsonNode jsonNode, ObjectMapper mapper)
            throws IOException {
        // Handle optional wrapper: { "parseContext": {...} }
        JsonNode contextNode = jsonNode.get(PARSE_CONTEXT);
        if (contextNode == null) {
            contextNode = jsonNode;
        }

        ParseContext parseContext = new ParseContext();

        if (!contextNode.isObject()) {
            return parseContext;
        }

        Iterator<String> fieldNames = contextNode.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode value = contextNode.get(name);

            if (TYPED.equals(name)) {
                // Deserialize typed objects directly to context map
                deserializeTypedObjects(value, parseContext, mapper);
            } else {
                // Store as JSON config for lazy resolution
                // Use plain JSON mapper since the main mapper may be binary (Smile)
                String json = JSON_MAPPER.writeValueAsString(value);
                parseContext.setJsonConfig(name, json);
            }
        }

        return parseContext;
    }

    /**
     * Deserializes the "typed" section into typed objects in the context map.
     */
    @SuppressWarnings("unchecked")
    private static void deserializeTypedObjects(JsonNode typedNode, ParseContext parseContext,
                                                 ObjectMapper mapper) throws IOException {
        if (!typedNode.isObject()) {
            return;
        }

        Iterator<String> fieldNames = typedNode.fieldNames();
        while (fieldNames.hasNext()) {
            String componentName = fieldNames.next();
            JsonNode configNode = typedNode.get(componentName);

            Class<?> configClass = null;
            Class<?> contextKeyClass = null;

            // First, try component registry lookup (for friendly names like "pdf-parser-config")
            Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(componentName);
            if (infoOpt.isPresent()) {
                ComponentInfo info = infoOpt.get();
                configClass = info.componentClass();
                contextKeyClass = info.contextKey();
            }

            // If not found in registry, try as fully qualified class name
            if (configClass == null) {
                try {
                    configClass = Class.forName(componentName);
                    // Check if the class has a contextKey via its annotation
                    contextKeyClass = ComponentNameResolver.getContextKey(configClass);
                } catch (ClassNotFoundException e) {
                    LOG.warn("Could not find class for typed component '{}', storing as JSON config",
                            componentName);
                    // Fall back to storing as JSON config (use plain JSON mapper)
                    parseContext.setJsonConfig(componentName, JSON_MAPPER.writeValueAsString(configNode));
                    continue;
                }
            }

            // Use contextKey if available, otherwise use the config class itself
            Class<?> parseContextKey = (contextKeyClass != null) ? contextKeyClass : configClass;

            // Deserialize and add to context
            try {
                Object config = mapper.treeToValue(configNode, configClass);
                parseContext.set((Class) parseContextKey, config);
                LOG.debug("Deserialized typed object '{}' -> {} (contextKey={})",
                        componentName, configClass.getName(), parseContextKey.getName());
            } catch (Exception e) {
                LOG.warn("Failed to deserialize typed component '{}' as {}, storing as JSON config",
                        componentName, configClass.getName(), e);
                // Use plain JSON mapper since main mapper may be binary (Smile)
                parseContext.setJsonConfig(componentName, JSON_MAPPER.writeValueAsString(configNode));
            }
        }
    }
}
