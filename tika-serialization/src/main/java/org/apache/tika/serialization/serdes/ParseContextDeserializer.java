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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
     * <p>
     * Duplicate detection is performed within a single document: if multiple entries
     * resolve to the same context key (e.g., both "bouncy-castle-digester" and
     * "commons-digester" resolve to DigesterFactory), an IOException is thrown.
     *
     * @param jsonNode the JSON node containing the ParseContext data
     * @param mapper   the ObjectMapper for deserializing typed objects
     * @return the deserialized ParseContext
     * @throws IOException if deserialization fails or duplicate context keys are detected
     */
    public static ParseContext readParseContext(JsonNode jsonNode, ObjectMapper mapper)
            throws IOException {
        // Handle optional wrapper: { "parse-context": {...} }
        JsonNode contextNode = jsonNode.get(PARSE_CONTEXT);
        if (contextNode == null) {
            contextNode = jsonNode;
        }

        ParseContext parseContext = new ParseContext();

        if (!contextNode.isObject()) {
            return parseContext;
        }

        // Track context keys to detect duplicates within this document
        // Maps contextKey -> friendlyName for error messages
        Map<Class<?>, String> seenContextKeys = new HashMap<>();

        Iterator<String> fieldNames = contextNode.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode value = contextNode.get(name);

            if (TYPED.equals(name)) {
                // Deserialize typed objects directly to context map
                deserializeTypedObjects(value, parseContext, mapper, seenContextKeys);
            } else {
                // Check for duplicate context key before storing
                checkForDuplicateContextKey(name, seenContextKeys);

                // Store as JSON config for lazy resolution
                // Use plain JSON mapper since the main mapper may be binary (Smile)
                String json = JSON_MAPPER.writeValueAsString(value);
                parseContext.setJsonConfig(name, json);
            }
        }

        return parseContext;
    }

    /**
     * Checks if a JSON config entry would create a duplicate context key.
     * <p>
     * Looks up the friendly name in the component registry to determine its context key,
     * then checks if that key has already been seen in this document.
     *
     * @param friendlyName the friendly name of the config entry
     * @param seenContextKeys map of already-seen context keys to their friendly names
     * @throws IOException if a duplicate context key is detected
     */
    private static void checkForDuplicateContextKey(String friendlyName,
                                                     Map<Class<?>, String> seenContextKeys)
            throws IOException {
        Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(friendlyName);
        if (infoOpt.isEmpty()) {
            // Not a registered component - can't check for duplicates, that's okay
            return;
        }

        ComponentInfo info = infoOpt.get();
        Class<?> contextKey = info.contextKey() != null ? info.contextKey() : info.componentClass();

        String existingName = seenContextKeys.get(contextKey);
        if (existingName != null) {
            throw new IOException("Duplicate parse-context entries resolve to the same key " +
                    contextKey.getName() + ": '" + existingName + "' and '" + friendlyName + "'");
        }
        seenContextKeys.put(contextKey, friendlyName);
    }

    /**
     * Deserializes the "typed" section into typed objects in the context map.
     *
     * @param typedNode the JSON node containing typed objects
     * @param parseContext the ParseContext to add objects to
     * @param mapper the ObjectMapper for deserializing
     * @param seenContextKeys map tracking context keys to their friendly names (for duplicate detection)
     * @throws IOException if deserialization fails or duplicate context keys are detected
     */
    @SuppressWarnings("unchecked")
    private static void deserializeTypedObjects(JsonNode typedNode, ParseContext parseContext,
                                                 ObjectMapper mapper,
                                                 Map<Class<?>, String> seenContextKeys) throws IOException {
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

            // Check for duplicate context key
            String existingName = seenContextKeys.get(parseContextKey);
            if (existingName != null) {
                throw new IOException("Duplicate parse-context entries resolve to the same key " +
                        parseContextKey.getName() + ": '" + existingName + "' and '" + componentName + "'");
            }
            seenContextKeys.put(parseContextKey, componentName);

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
