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

import static org.apache.tika.serialization.ParseContextSerializer.PARSE_CONTEXT;

import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.SelfConfiguring;
import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;

/**
 * Deserializes ParseContext from JSON using friendly names.
 * <p>
 * Fields with friendly names are stored in ConfigContainer for later resolution.
 * Fields with full class names (org.apache.tika.*) are deserialized directly.
 * <p>
 * Example input:
 * <pre>
 * {
 *   "pdf-parser": {"extractActions": true},
 *   "tika-task-timeout": {"timeoutMillis": 5000}
 * }
 * </pre>
 */
public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextDeserializer.class);
    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.getMapper();

    @Override
    public ParseContext deserialize(JsonParser jsonParser,
                                    DeserializationContext deserializationContext)
            throws IOException, JacksonException {
        JsonNode root = jsonParser.readValueAsTree();
        return readParseContext(root);
    }

    /**
     * Deserializes a ParseContext from a JsonNode.
     * <p>
     * Fields with friendly names are stored as JSON in ConfigContainer for later
     * resolution via {@link ParseContextUtils#resolveAll}.
     * <p>
     * Fields with full class names (org.apache.tika.*) are deserialized directly
     * into the ParseContext for immediate use.
     *
     * @param jsonNode the JSON node containing the ParseContext data
     * @return the deserialized ParseContext with ConfigContainer populated
     * @throws IOException if deserialization fails
     */
    public static ParseContext readParseContext(JsonNode jsonNode) throws IOException {
        // Some use cases include the wrapper node, e.g. { "parseContext": {}}
        // Some include the contents only.
        // Try to find "parseContext" to start. If that doesn't exist, assume jsonNode is the contents.
        JsonNode contextNode = jsonNode.get(PARSE_CONTEXT);
        if (contextNode == null) {
            contextNode = jsonNode;
        }

        ParseContext parseContext = new ParseContext();

        // Store all fields as named configurations in ConfigContainer
        // Resolution to actual objects happens via ParseContextUtils.resolveAll()
        ConfigContainer configContainer = null;
        for (Iterator<String> it = contextNode.fieldNames(); it.hasNext(); ) {
            String fieldName = it.next();
            JsonNode fieldValue = contextNode.get(fieldName);

            // Try to resolve fieldName - either as FQCN or friendly name from registry
            Class<?> keyClass = null;

            // Check if fieldName is a full class name (for directly serialized Tika types)
            if (fieldName.startsWith("org.apache.tika.")) {
                try {
                    keyClass = Class.forName(fieldName);
                } catch (ClassNotFoundException e) {
                    LOG.debug("Class not found for key '{}', will check registry", fieldName);
                }
            }

            // If not found as FQCN, check registry for friendly name
            // Use ComponentNameResolver to ensure consistency with TikaObjectMapperFactory's registries
            boolean isSelfConfiguring = false;
            Class<?> contextKey = null;  // The key to use when adding to ParseContext
            if (keyClass == null) {
                Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(fieldName);
                if (infoOpt.isPresent()) {
                    ComponentInfo info = infoOpt.get();
                    keyClass = info.componentClass();
                    isSelfConfiguring = info.selfConfiguring();
                    contextKey = info.contextKey();
                    LOG.debug("Resolved friendly name '{}' to class {} (selfConfiguring={}, contextKey={})",
                            fieldName, keyClass.getName(), isSelfConfiguring,
                            contextKey != null ? contextKey.getName() : "null");
                }
            } else {
                // For FQCN resolution, check SelfConfiguring directly
                isSelfConfiguring = SelfConfiguring.class.isAssignableFrom(keyClass);
            }

            // If we found a class, check if it's SelfConfiguring
            if (keyClass != null) {
                // SelfConfiguring components (Parsers, Detectors, etc.) handle their own config
                // at runtime - keep their config in ConfigContainer for later access
                if (isSelfConfiguring) {
                    LOG.debug("'{}' maps to SelfConfiguring class {}, keeping in ConfigContainer",
                            fieldName, keyClass.getName());
                    // Fall through to ConfigContainer storage below
                } else {
                    // Non-SelfConfiguring - deserialize directly into ParseContext
                    try {
                        // Check if fieldValue is a wrapper object format: {"concrete-class": {props}}
                        Object value;
                        if (fieldValue.isObject() && fieldValue.size() == 1) {
                            String typeName = fieldValue.fieldNames().next();
                            JsonNode configNode = fieldValue.get(typeName);
                            // Try to resolve the concrete class
                            try {
                                Class<?> concreteClass = ComponentNameResolver.resolveClass(typeName,
                                        ParseContextDeserializer.class.getClassLoader());
                                value = MAPPER.treeToValue(configNode, concreteClass);
                            } catch (ClassNotFoundException ex) {
                                // Fall back to key class
                                value = MAPPER.treeToValue(configNode, keyClass);
                            }
                        } else {
                            // Not wrapper format, deserialize directly
                            value = MAPPER.treeToValue(fieldValue, keyClass);
                        }
                        // Use contextKey if specified, otherwise use the component class
                        Class<?> parseContextKey = (contextKey != null) ? contextKey : keyClass;
                        parseContext.set((Class) parseContextKey, value);
                        continue;
                    } catch (Exception e) {
                        throw new IOException("Failed to deserialize '" + fieldName + "': " + e.getMessage(), e);
                    }
                }
            }

            // Store as config for later resolution
            if (configContainer == null) {
                configContainer = new ConfigContainer();
            }
            configContainer.set(fieldName, fieldValue.toString());
        }

        if (configContainer != null) {
            parseContext.set(ConfigContainer.class, configContainer);
        }

        // Resolve array configs (e.g., "metadata-filters") and non-SelfConfiguring components
        ParseContextUtils.resolveAll(parseContext, ParseContextDeserializer.class.getClassLoader());

        return parseContext;
    }
}
