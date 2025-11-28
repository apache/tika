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
 * <p>Framework fields:
 * <ul>
 *   <li>{@code _decorate} - Parser decoration config (mime filtering, fallbacks)</li>
 * </ul>
 */
public class FrameworkConfig {

    private static final String DECORATE_KEY = "_decorate";

    private final ParserDecoration decoration;
    private final JsonConfig componentConfigJson;

    private FrameworkConfig(ParserDecoration decoration, JsonConfig componentConfigJson) {
        this.decoration = decoration;
        this.componentConfigJson = componentConfigJson;
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
            String jsonString = objectMapper.writeValueAsString(configNode);
            JsonConfig jsonConfig = () -> jsonString;
            return new FrameworkConfig(null, jsonConfig);
        }

        ObjectNode objNode = (ObjectNode) configNode.deepCopy();

        // Extract decoration (parser-specific)
        ParserDecoration decoration = null;
        if (objNode.has(DECORATE_KEY)) {
            JsonNode decorateNode = objNode.remove(DECORATE_KEY);
            decoration = parseDecoration(decorateNode);
        }

        // Remaining fields are component-specific config
        String jsonString = objectMapper.writeValueAsString(objNode);
        JsonConfig componentConfigJson = () -> jsonString;

        return new FrameworkConfig(decoration, componentConfigJson);
    }

    private static ParserDecoration parseDecoration(JsonNode decorateNode) {
        if (decorateNode == null || !decorateNode.isObject()) {
            return null;
        }

        List<String> mimeInclude = parseStringList(decorateNode.get("mimeInclude"));
        List<String> mimeExclude = parseStringList(decorateNode.get("mimeExclude"));
        List<String> fallbacks = parseStringList(decorateNode.get("fallbacks"));

        if (mimeInclude.isEmpty() && mimeExclude.isEmpty() && fallbacks.isEmpty()) {
            return null;
        }

        return new ParserDecoration(mimeInclude, mimeExclude, fallbacks);
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

    /**
     * Parser decoration configuration for mime type filtering and fallbacks.
     */
    public static class ParserDecoration {
        private final List<String> mimeInclude;
        private final List<String> mimeExclude;
        private final List<String> fallbacks;

        public ParserDecoration(List<String> mimeInclude, List<String> mimeExclude,
                                 List<String> fallbacks) {
            this.mimeInclude = Collections.unmodifiableList(mimeInclude);
            this.mimeExclude = Collections.unmodifiableList(mimeExclude);
            this.fallbacks = Collections.unmodifiableList(fallbacks);
        }

        public List<String> getMimeInclude() {
            return mimeInclude;
        }

        public List<String> getMimeExclude() {
            return mimeExclude;
        }

        public List<String> getFallbacks() {
            return fallbacks;
        }

        public boolean hasFiltering() {
            return !mimeInclude.isEmpty() || !mimeExclude.isEmpty();
        }

        public boolean hasFallbacks() {
            return !fallbacks.isEmpty();
        }
    }
}
