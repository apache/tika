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
 * <p>This class serves as the single source of truth for JSON parsing across
 * core Tika (parsers, detectors) and tika-pipes (fetchers, emitters) components.
 * It performs no validation - consumers validate only their own keys.
 *
 * <p><b>Unified Configuration Usage:</b>
 * <pre>
 * // Parse config once
 * TikaJsonConfig jsonConfig = TikaJsonConfig.load(Paths.get("config.json"));
 *
 * // Load core Tika components (same classloader)
 * TikaLoader tikaLoader = TikaLoader.load(jsonConfig);
 * Parser parser = tikaLoader.loadParsers();
 * Detector detector = tikaLoader.loadDetectors();
 *
 * // Load pipes/plugin components (different classloader)
 * TikaPluginManager pluginManager = TikaPluginManager.load(jsonConfig);
 * pluginManager.loadPlugins();
 * pluginManager.startPlugins();
 *
 * // Extract config for plugins (crosses classloader boundary as string)
 * JsonNode fetchersNode = jsonConfig.getRootNode().get("fetchers");
 * if (fetchersNode != null) {
 *     String fetcherConfigJson = fetchersNode.toString();
 *     // Pass string to plugin - safe across classloader boundary
 * }
 * </pre>
 *
 * <p><b>JSON structure:</b>
 * <pre>
 * {
 *   // Core Tika components (validated by TikaLoader)
 *   "parsers": [
 *     { "pdf-parser": { "_decorate": {...}, "ocrStrategy": "AUTO", ... } },
 *     { "html-parser": { ... } },
 *     { "default-parser": {} }
 *   ],
 *   "detectors": [
 *     { "mime-magic-detector": {} },
 *     { "zip-container-detector": { "maxDepth": 10 } }
 *   ],
 *
 *   // Pipes components (validated by TikaConfigs)
 *   "plugin-roots": ["/path/to/plugins"],
 *   "fetchers": [...],
 *   "emitters": [...],
 *
 *   // Custom extensions (prefix with x-)
 *   "x-my-custom-config": { ... }
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
     * Deserializes a configuration value for the given key.
     *
     * @param key the configuration key
     * @param clazz the target class
     * @param <T> the type to deserialize to
     * @return the deserialized value, or null if key doesn't exist
     * @throws IOException if deserialization fails
     */
    public <T> T deserialize(String key, Class<T> clazz) throws IOException {
        JsonNode node = rootNode.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        return OBJECT_MAPPER.treeToValue(node, clazz);
    }

    /**
     * Checks if a configuration key exists.
     *
     * @param key the configuration key
     * @return true if the key exists and is not null
     */
    public boolean hasKey(String key) {
        return rootNode.has(key) && !rootNode.get(key).isNull();
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
