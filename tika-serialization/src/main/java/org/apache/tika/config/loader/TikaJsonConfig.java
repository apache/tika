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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 *     { "pdf-parser": { "_mime-include": ["application/pdf"], "ocrStrategy": "AUTO" } },
 *     "html-parser",                    // String shorthand for no-config components
 *     { "default-parser": { "_exclude": ["ocr-parser"] } }
 *   ],
 *   "detectors": [
 *     "poifs-container-detector",       // String shorthand
 *     { "mime-types": { "markLimit": 10000 } }
 *   ],
 *
 *   // Pipes components (validated by validateKeys())
 *   "plugin-roots": ["/path/to/plugins"],
 *   "fetchers": [...],
 *   "emitters": [...]
 * }
 * </pre>
 *
 * <p>All components use array format for explicit ordering.
 * Components without configuration can use string shorthand: "component-name"
 * instead of { "component-name": {} }.
 * Parsers support mime filtering via "_mime-include" and "_mime-exclude" fields.
 * Special "default-parser" entry enables SPI fallback for unlisted parsers.
 */
public class TikaJsonConfig {

    /**
     * Known top-level configuration keys across core Tika and pipes/plugins.
     * Only kebab-case names are allowed.
     */
    private static final Set<String> KNOWN_KEYS = Set.of(
            // Globals
            "maxJsonStringFieldLength",
            "service-loader",
            "xml-reader-utils",
            // Core Tika component keys
            "parsers",
            "detectors",
            "encoding-detectors",
            "metadata-filters",
            "renderers",
            "translator",
            "auto-detect-parser",
            "server",

            // Pipes/plugin keys
            "fetchers",
            "emitters",
            "pipes-iterator",
            "pipes-reporters",
            "pipes",
            "plugin-roots"
    );

    private static final ObjectMapper OBJECT_MAPPER =
            PolymorphicObjectMapperFactory.getMapper();

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
            TikaJsonConfig tikaJsonConfig = new TikaJsonConfig(rootNode);
            tikaJsonConfig.validateKeys();
            return tikaJsonConfig;
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse JSON configuration", e);
        }
    }

    /**
     * Creates an empty configuration (no config file).
     * All components will be loaded from SPI.
     *
     * @return an empty configuration
     */
    public static TikaJsonConfig loadDefault() {
        JsonNode emptyNode = OBJECT_MAPPER.createObjectNode();
        return new TikaJsonConfig(emptyNode);
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
                if (arrayItem.isTextual()) {
                    // String shorthand: "component-name" -> treat as { "component-name": {} }
                    String componentName = arrayItem.asText();
                    components.add(Map.entry(componentName, OBJECT_MAPPER.createObjectNode()));
                } else if (arrayItem.isObject()) {
                    // Object syntax: { "component-name": {...config...} }
                    for (Map.Entry<String, JsonNode> componentEntry : arrayItem.properties()) {
                        components.add(Map.entry(componentEntry.getKey(), componentEntry.getValue()));
                        break; // Only take the first field
                    }
                }
                // Skip other types (null, numbers, arrays, etc.)
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
     * Validates that all top-level configuration keys are known or custom extensions.
     * <p>
     * This catches typos like "parser" instead of "parsers" or "pipes-reporter"
     * instead of "pipes-reporters".
     * <p>
     * The "other-configs" node is allowed for custom configurations.
     *
     * @throws TikaConfigException if unknown keys are found
     */
    private void validateKeys() throws TikaConfigException {
        if (rootNode == null || !rootNode.isObject()) {
            return;
        }

        Iterator<String> fieldNames = rootNode.fieldNames();
        List<String> unknownKeys = new ArrayList<>();

        while (fieldNames.hasNext()) {
            String key = fieldNames.next();

            // Ignore custom configs node
            if (key.equals("other-configs")) {
                continue;
            }

            // Must be a known key
            if (!KNOWN_KEYS.contains(key)) {
                unknownKeys.add(key);
            }
        }

        if (!unknownKeys.isEmpty()) {
            throw new TikaConfigException(
                    "Unknown configuration key(s): " + unknownKeys + ". " +
                    "Valid keys: " + KNOWN_KEYS + " " +
                    "(or use 'other-configs' node for custom keys)");
        }
    }

    @Override
    public String toString() {
        return "TikaJsonConfig{" + "rootNode=" + rootNode + '}';
    }
}
