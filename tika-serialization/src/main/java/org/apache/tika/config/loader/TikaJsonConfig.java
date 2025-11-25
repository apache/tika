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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Parsed representation of a Tika JSON configuration file.
 * Provides access to component configurations by type (parsers, detectors, etc.).
 *
 * <p>JSON structure:
 * <pre>
 * {
 *   "parsers": [
 *     { "pdf-parser": { "_decorate": {...}, "ocrStrategy": "AUTO", ... } },
 *     { "html-parser": { ... } },
 *     { "default-parser": {} }
 *   ],
 *   "detectors": [
 *     { "mime-magic-detector": {} },
 *     { "zip-container-detector": { "maxDepth": 10 } }
 *   ],
 *   ...
 * }
 * </pre>
 *
 * <p>All components use array format for explicit ordering.
 * Parsers support decoration via "_decorate" field.
 * Special "default-parser" entry enables SPI fallback for unlisted parsers.
 */
public class TikaJsonConfig {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Fail on unknown properties to catch configuration errors early
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

        // Prevent null values being assigned to primitive fields (int, boolean, etc.)
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);

        // Ensure enums are properly validated (not just numeric values)
        mapper.configure(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, true);

        // Catch duplicate keys in JSON objects
        mapper.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);

        return mapper;
    }

    private final JsonNode rootNode;
    private final Map<String, Map<String, JsonNode>> componentsByType;
    private final Map<String, List<Map.Entry<String, JsonNode>>> arrayComponentsByType;

    private TikaJsonConfig(JsonNode rootNode) {
        this.rootNode = rootNode;
        this.componentsByType = parseObjectComponents(rootNode);
        this.arrayComponentsByType = parseArrayComponents(rootNode);
    }

    /**
     * Loads configuration from a file.
     *
     * @param configPath the path to the JSON configuration file
     * @return the parsed configuration
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaJsonConfig load(Path configPath) throws TikaConfigException {
        try (InputStream in = Files.newInputStream(configPath)) {
            return load(in);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to load config from: " + configPath, e);
        }
    }

    /**
     * Loads configuration from an input stream.
     *
     * @param inputStream the input stream containing JSON configuration
     * @return the parsed configuration
     * @throws TikaConfigException if loading or parsing fails
     */
    public static TikaJsonConfig load(InputStream inputStream) throws TikaConfigException {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(inputStream);
            return new TikaJsonConfig(rootNode);
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse JSON configuration", e);
        }
    }

    /**
     * Gets component configurations for a specific type (object format - used for parsers).
     *
     * @param componentType the component type (e.g., "parsers")
     * @return map of component name to configuration JSON, or empty map if type not found
     */
    public Map<String, JsonNode> getComponents(String componentType) {
        return componentsByType.getOrDefault(componentType, Collections.emptyMap());
    }

    /**
     * Gets component configurations for a specific type (array format - used for detectors, etc.).
     *
     * @param componentType the component type (e.g., "detectors")
     * @return ordered list of (name, config) entries, or empty list if type not found
     */
    public List<Map.Entry<String, JsonNode>> getArrayComponents(String componentType) {
        return arrayComponentsByType.getOrDefault(componentType, Collections.emptyList());
    }

    /**
     * Checks if a component type has any configured components (object format).
     *
     * @param componentType the component type
     * @return true if the type has configurations
     */
    public boolean hasComponents(String componentType) {
        Map<String, JsonNode> components = componentsByType.get(componentType);
        return components != null && !components.isEmpty();
    }

    /**
     * Checks if a component type has any configured components (array format).
     *
     * @param componentType the component type
     * @return true if the type has configurations
     */
    public boolean hasArrayComponents(String componentType) {
        List<Map.Entry<String, JsonNode>> components = arrayComponentsByType.get(componentType);
        return components != null && !components.isEmpty();
    }

    /**
     * Checks if a component type section exists in the config (even if empty).
     *
     * @param componentType the component type
     * @return true if the section exists
     */
    public boolean hasComponentSection(String componentType) {
        return rootNode.has(componentType);
    }

    /**
     * Gets the raw root JSON node.
     *
     * @return the root node
     */
    public JsonNode getRootNode() {
        return rootNode;
    }

    private Map<String, Map<String, JsonNode>> parseObjectComponents(JsonNode root) {
        Map<String, Map<String, JsonNode>> result = new LinkedHashMap<>();

        if (root == null || !root.isObject()) {
            return result;
        }

        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String componentType = entry.getKey();
            JsonNode typeNode = entry.getValue();

            // Only process object nodes (used for parsers)
            if (!typeNode.isObject()) {
                continue;
            }

            Map<String, JsonNode> components = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> componentEntry : typeNode.properties()) {
                components.put(componentEntry.getKey(), componentEntry.getValue());
            }

            if (!components.isEmpty()) {
                result.put(componentType, components);
            }
        }

        return result;
    }

    private Map<String, List<Map.Entry<String, JsonNode>>> parseArrayComponents(JsonNode root) {
        Map<String, List<Map.Entry<String, JsonNode>>> result = new LinkedHashMap<>();

        if (root == null || !root.isObject()) {
            return result;
        }

        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String componentType = entry.getKey();
            JsonNode typeNode = entry.getValue();

            // Only process array nodes (used for detectors, filters, etc.)
            if (!typeNode.isArray()) {
                continue;
            }

            List<Map.Entry<String, JsonNode>> components = new ArrayList<>();

            for (JsonNode arrayItem : typeNode) {
                if (!arrayItem.isObject()) {
                    continue;
                }

                // Each array item should have exactly one field: { "component-name": {...config...} }
                for (Map.Entry<String, JsonNode> componentEntry : arrayItem.properties()) {
                    components.add(Map.entry(componentEntry.getKey(), componentEntry.getValue()));
                    break; // Only take the first field
                }
            }

            if (!components.isEmpty()) {
                result.put(componentType, components);
            }
        }

        return result;
    }

    /**
     * Gets the ObjectMapper used for JSON processing.
     *
     * @return the object mapper
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
