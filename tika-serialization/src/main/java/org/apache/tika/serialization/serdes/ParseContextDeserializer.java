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

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ConfigDeserializer;

/**
 * Deserializes ParseContext from JSON.
 * <p>
 * Each field in the JSON object is stored as a JSON config in the ParseContext.
 * Resolution to typed objects happens later via {@link ConfigDeserializer}.
 * <p>
 * Example input:
 * <pre>
 * {
 *   "pdf-parser": {"ocrStrategy": "AUTO"},
 *   "handler-config": {"type": "XML", "parseMode": "RMETA"}
 * }
 * </pre>
 */
public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {

    @Override
    public ParseContext deserialize(JsonParser jsonParser, DeserializationContext ctxt)
            throws IOException {
        JsonNode root = jsonParser.readValueAsTree();
        return readParseContext(root, (ObjectMapper) jsonParser.getCodec());
    }

    /**
     * Deserializes a ParseContext from a JsonNode.
     * <p>
     * Each field is stored as a JSON config string in the ParseContext's jsonConfigs map.
     * The configs can later be resolved to typed objects via {@link ConfigDeserializer}.
     *
     * @param jsonNode the JSON node containing the ParseContext data
     * @param mapper   the ObjectMapper for serializing field values back to JSON strings
     * @return the deserialized ParseContext with jsonConfigs populated
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

        // Store each field as a JSON config
        Iterator<String> fieldNames = contextNode.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode value = contextNode.get(name);
            // Store the JSON string for later resolution
            String json = mapper.writeValueAsString(value);
            parseContext.setJsonConfig(name, json);
        }

        return parseContext;
    }
}
