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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                String json = mapper.writeValueAsString(value);
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

            try {
                // Look up the class for this component name
                Class<?> configClass = ComponentNameResolver.resolveClass(
                        componentName, ParseContextDeserializer.class.getClassLoader());

                // Deserialize and add to context
                Object config = mapper.treeToValue(configNode, configClass);
                parseContext.set((Class) configClass, config);

                LOG.debug("Deserialized typed object '{}' -> {}", componentName, configClass.getName());
            } catch (ClassNotFoundException e) {
                LOG.warn("Could not find class for typed component '{}', storing as JSON config",
                        componentName);
                // Fall back to storing as JSON config
                parseContext.setJsonConfig(componentName, mapper.writeValueAsString(configNode));
            }
        }
    }
}
