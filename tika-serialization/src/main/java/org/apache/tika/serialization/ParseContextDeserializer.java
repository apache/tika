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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.parser.ParseContext;

/**
 * Deserializes ParseContext from JSON using friendly names.
 * <p>
 * All fields are stored in ConfigContainer for later resolution.
 * Components are resolved at runtime via {@link ParseContextUtils#resolveAll}.
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
     * All fields are stored as JSON in ConfigContainer. Resolution to actual
     * objects happens later via {@link ParseContextUtils#resolveAll}.
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
            if (configContainer == null) {
                configContainer = new ConfigContainer();
            }
            configContainer.set(fieldName, contextNode.get(fieldName).toString());
        }

        if (configContainer != null) {
            parseContext.set(ConfigContainer.class, configContainer);
        }

        return parseContext;
    }
}
