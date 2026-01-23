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
package org.apache.tika.config.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.JsonConfig;

/**
 * Extracts framework-level configuration from component JSON,
 * separating fields prefixed with underscore from component-specific config.
 *
 * <p>Framework fields (underscore prefix):
 * <ul>
 *   <li>{@code _mime-include} - Only handle these mime types</li>
 *   <li>{@code _mime-exclude} - Don't handle these mime types</li>
 * </ul>
 */
public class FrameworkConfig {

    private static final String MIME_INCLUDE_KEY = "_mime-include";
    private static final String MIME_EXCLUDE_KEY = "_mime-exclude";

    // Plain JSON mapper for converting JsonNodes to JSON strings.
    // This is needed because the main mapper may use a binary format (e.g., Smile)
    // which doesn't support writeValueAsString().
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final ParserDecoration decoration;
    private final JsonConfig componentConfigJson;
    private final JsonNode componentConfigNode;

    private FrameworkConfig(ParserDecoration decoration, JsonConfig componentConfigJson,
                            JsonNode componentConfigNode) {
        this.decoration = decoration;
        this.componentConfigJson = componentConfigJson;
        this.componentConfigNode = componentConfigNode;
    }

    /**
     * Extracts framework config from JSON node, returning the cleaned component config.
     *
     * @param configNode the configuration JSON node
     * @param objectMapper the Jackson ObjectMapper for serialization
     * @return the framework config
     * @throws IOException if JSON processing fails
     */
    public static FrameworkConfig extract(JsonNode configNode,
                                           ObjectMapper objectMapper) throws IOException {
        if (configNode == null || !configNode.isObject()) {
            // Use plain JSON mapper since the main mapper may be binary (Smile)
            String jsonString = JSON_MAPPER.writeValueAsString(configNode);
            JsonConfig jsonConfig = () -> jsonString;
            return new FrameworkConfig(null, jsonConfig, configNode);
        }

        ObjectNode objNode = (ObjectNode) configNode.deepCopy();

        // Extract mime filtering config (framework-level, underscore prefix)
        List<String> mimeInclude = parseStringList(objNode.remove(MIME_INCLUDE_KEY));
        List<String> mimeExclude = parseStringList(objNode.remove(MIME_EXCLUDE_KEY));

        ParserDecoration decoration = null;
        if (!mimeInclude.isEmpty() || !mimeExclude.isEmpty()) {
            decoration = new ParserDecoration(mimeInclude, mimeExclude);
        }

        // Remaining fields are component-specific config
        // Use plain JSON mapper since the main mapper may be binary (Smile)
        String jsonString = JSON_MAPPER.writeValueAsString(objNode);
        JsonConfig componentConfigJson = () -> jsonString;

        return new FrameworkConfig(decoration, componentConfigJson, objNode);
    }

    private static List<String> parseStringList(JsonNode node) {
        if (node == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    result.add(item.asText());
                }
            }
        } else if (node.isTextual()) {
            result.add(node.asText());
        }

        return result;
    }

    public ParserDecoration getDecoration() {
        return decoration;
    }

    public JsonConfig getComponentConfigJson() {
        return componentConfigJson;
    }

    public JsonNode getComponentConfigNode() {
        return componentConfigNode;
    }

    /**
     * Parser decoration configuration for mime type filtering.
     */
    public static class ParserDecoration {
        private final List<String> mimeInclude;
        private final List<String> mimeExclude;

        public ParserDecoration(List<String> mimeInclude, List<String> mimeExclude) {
            this.mimeInclude = Collections.unmodifiableList(mimeInclude);
            this.mimeExclude = Collections.unmodifiableList(mimeExclude);
        }

        public List<String> getMimeInclude() {
            return mimeInclude;
        }

        public List<String> getMimeExclude() {
            return mimeExclude;
        }

        public boolean hasFiltering() {
            return !mimeInclude.isEmpty() || !mimeExclude.isEmpty();
        }
    }
}
