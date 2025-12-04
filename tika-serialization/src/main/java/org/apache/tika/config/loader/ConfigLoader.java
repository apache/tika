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

import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Loader for custom configuration objects from the "other-configs" section.
 * <p>
 * This class handles custom POJOs and test configurations that are not part of
 * Tika's official configuration schema. All configurations loaded via ConfigLoader
 * must be placed under the "other-configs" top-level node in the JSON.
 * <p>
 * For official Tika components and configurations (parsers, detectors, async, server, etc.),
 * use the specific methods on {@link TikaLoader} or load directly from {@link TikaJsonConfig}.
 *
 * <p>Usage:
 * <pre>
 * TikaLoader loader = TikaLoader.load(configPath);
 *
 * // Load by explicit key
 * HandlerConfig config = loader.configs().load("handler-config", HandlerConfig.class);
 *
 * // Load by class name (auto-converts to kebab-case)
 * HandlerConfig config = loader.configs().load(HandlerConfig.class);
 * </pre>
 *
 * <p>JSON configuration example:
 * <pre>
 * {
 *   // Official Tika configs at root level (NOT loaded via configs())
 *   "parsers": [...],
 *   "detectors": [...],
 *   "pipes": {...},
 *   "server": {...},
 *
 *   // Custom configs MUST be in "other-configs" (loaded via configs())
 *   "other-configs": {
 *     "handler-config": {
 *       "timeout": 5000,
 *       "retries": 3
 *     },
 *     "my-custom-config": {
 *       "enabled": true
 *     }
 *   }
 * }
 * </pre>
 */
public class ConfigLoader {

    /**
     * Reserved keys for complex components that require special handling.
     * These cannot be loaded via ConfigLoader - use TikaLoader methods instead.
     */
    private static final Set<String> PROHIBITED_KEYS =
            Set.of("parsers", "detectors", "encoding-detectors", "encodingDetectors", "metadata-filters", "metadataFilters", "renderers", "translators");

    private static final Set<String> PROHIBITED_CLASSES =
            Set.of("org.apache.tika.parser.Parser", "org.apache.tika.detect.Detector",
                    "org.apache.tika.renderer.Renderer",
                    "org.apache.tika.detect.EncodingDetector", "org.apache.tika.metadata.filter.MetadataFilter");

    private final TikaJsonConfig config;
    private final ObjectMapper objectMapper;

    ConfigLoader(TikaJsonConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads a configuration object using the class name converted to kebab-case.
     * <p>
     * For example, {@code HandlerConfig.class} will look for key "handler-config".
     * Class name suffixes like "Config", "Configuration", "Settings" are stripped first.
     * <p>
     * For interfaces, the JSON must specify the implementation (see {@link #load(String, Class)}).
     *
     * @param clazz The class to deserialize into (can be interface, abstract, or concrete)
     * @param <T> The type to load
     * @return the deserialized object, or null if key not found in config
     * @throws TikaConfigException if loading fails or class is not instantiable
     */
    public <T> T load(Class<T> clazz) throws TikaConfigException {
        String key = deriveKeyFromClass(clazz);
        return load(key, clazz);
    }

    /**
     * Loads a configuration object using the class name, with a default value.
     *
     * @param clazz The class to deserialize into
     * @param defaultValue The value to return if key not found in config
     * @param <T> The type to load
     * @return the deserialized object, or defaultValue if not present
     * @throws TikaConfigException if loading fails or class is not instantiable
     */
    public <T> T load(Class<T> clazz, T defaultValue) throws TikaConfigException {
        T result = load(clazz);
        return result != null ? result : defaultValue;
    }

    /**
     * Loads a configuration object from the specified JSON key.
     * <p>
     * Supports three formats for interfaces:
     * <ul>
     *   <li>String value: treated as class name or component name to look up</li>
     *   <li>Object with "@class": explicit type specification</li>
     *   <li>Object without "@class": attempts direct deserialization (works for concrete classes)</li>
     * </ul>
     *
     * @param key The JSON key to load from
     * @param clazz The class to deserialize into (can be interface, abstract, or concrete)
     * @param <T> The type to load
     * @return the deserialized object, or null if key not found
     * @throws TikaConfigException if loading fails or class cannot be instantiated
     */
    public <T> T load(String key, Class<T> clazz) throws TikaConfigException {
        validateKey(key);
        validateClass(clazz);

        JsonNode node = getNode(key);
        if (node == null || node.isNull()) {
            return null;
        }

        try {
            // Strategy 1: String value - treat as class name
            if (node.isTextual()) {
                return loadFromClassName(node.asText(), clazz);
            }

            // Strategy 2: Let Jackson handle everything else
            // Jackson's activateDefaultTyping will automatically handle @class fields
            // for interfaces/abstract classes via the PolymorphicObjectMapperFactory configuration
            return objectMapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                "Failed to deserialize '" + key + "' into " + clazz.getName(), e);
        }
    }


    /**
     * Loads a class from a string (fully qualified class name).
     */
    @SuppressWarnings("unchecked")
    private <T> T loadFromClassName(String className, Class<T> expectedType)
            throws TikaConfigException {
        try {
            Class<?> clazz = Class.forName(className);
            if (!expectedType.isAssignableFrom(clazz)) {
                throw new TikaConfigException(
                    "Class " + className + " is not assignable to " + expectedType.getName());
            }

            // Try to instantiate with no-arg constructor
            return (T) clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            throw new TikaConfigException("Class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            throw new TikaConfigException(
                "Failed to instantiate " + className +
                ". Ensure it has a public no-argument constructor.", e);
        }
    }

    /**
     * Loads a configuration object from the specified JSON key, with a default value.
     *
     * @param key The JSON key to load from
     * @param clazz The class to deserialize into
     * @param defaultValue The value to return if key not found in config
     * @param <T> The type to load
     * @return the deserialized object, or defaultValue if not present
     * @throws TikaConfigException if loading fails or class is not instantiable
     */
    public <T> T load(String key, Class<T> clazz, T defaultValue) throws TikaConfigException {
        T result = load(key, clazz);
        return result != null ? result : defaultValue;
    }

    /**
     * Loads a configuration object by merging JSON properties into a copy of the default instance.
     * <p>
     * This allows partial configuration where only some properties are specified in JSON,
     * and the rest retain their default values. The original defaultValue object is NOT modified.
     *
     * <p>Example:
     * <pre>
     * HandlerConfig defaults = new HandlerConfig();
     * defaults.setTimeout(30000);
     * defaults.setRetries(2);
     * defaults.setEnabled(false);
     *
     * // JSON: { "enabled": true }
     * // Result: timeout=30000, retries=2, enabled=true (merged!)
     * // Note: 'defaults' object remains unchanged
     * HandlerConfig config = loader.configs().loadWithDefaults("handler-config",
     *                                                           HandlerConfig.class,
     *                                                           defaults);
     * </pre>
     *
     * @param key The JSON key to load from
     * @param clazz The class type (not used for deserialization, but for type safety)
     * @param defaultValue The object with default values (will NOT be modified)
     * @param <T> The type to load
     * @return a new object with defaults merged with JSON properties, or the original default if key not found
     * @throws TikaConfigException if loading fails
     */
    public <T> T loadWithDefaults(String key, Class<T> clazz, T defaultValue)
            throws TikaConfigException {
        validateKey(key);
        validateClass(clazz);

        JsonNode node = getNode(key);
        if (node == null || node.isNull()) {
            return defaultValue;
        }

        try {
            // Create a deep copy of defaultValue to avoid mutating the original
            // Using convertValue is efficient and doesn't require serializing to bytes
            @SuppressWarnings("unchecked")
            T copy = objectMapper.convertValue(defaultValue, (Class<T>) defaultValue.getClass());

            // Merge JSON properties into the copy
            return objectMapper.readerForUpdating(copy).readValue(node);
        } catch (Exception e) {
            throw new TikaConfigException(
                "Failed to merge '" + key + "' into " + clazz.getName(), e);
        }
    }

    /**
     * Loads a configuration object by class name with defaults, merging JSON properties.
     *
     * @param clazz The class to deserialize into
     * @param defaultValue The object with default values to merge into
     * @param <T> The type to load
     * @return the default object updated with JSON properties, or the original default if key not found
     * @throws TikaConfigException if loading fails
     */
    public <T> T loadWithDefaults(Class<T> clazz, T defaultValue) throws TikaConfigException {
        String key = deriveKeyFromClass(clazz);
        return loadWithDefaults(key, clazz, defaultValue);
    }

    /**
     * Checks if a configuration key exists in the JSON config.
     *
     * @param key The JSON key to check
     * @return true if the key exists and is not null
     */
    public boolean hasKey(String key) {
        JsonNode node = getNode(key);
        return node != null && !node.isNull();
    }

    /**
     * Gets a node by key from the "other-configs".
     *
     * @param key The JSON key to look for
     * @return the node, or null if not found
     */
    private JsonNode getNode(String key) {

        JsonNode otherConfigs = config.getRootNode().get("other-configs");
        if (otherConfigs != null && otherConfigs.isObject()) {
            return otherConfigs.get(key);
        }

        return null;
    }

    /**
     * Derives a kebab-case key from a class name.
     * <p>
     * Uses the full class name converted to kebab-case for consistency with
     * the annotation processor's component naming.
     *
     * @param clazz the class to derive the key from
     * @return kebab-case version of the class name
     */
    private String deriveKeyFromClass(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return toKebabCase(simpleName);
    }

    /**
     * Converts a camelCase or PascalCase string to kebab-case.
     * Delegates to {@link KebabCaseConverter} for consistent behavior
     * with the annotation processor.
     */
    private String toKebabCase(String name) {
        return KebabCaseConverter.toKebabCase(name);
    }

    /**
     * Validates that the key is not reserved for complex components.
     */
    private void validateKey(String key) throws TikaConfigException {
        if (PROHIBITED_KEYS.contains(key)) {
            throw new TikaConfigException(
                "Cannot load '" + key + "' via ConfigLoader. " +
                "This is a complex component that requires special handling. " +
                "Use TikaLoader.load" + toPascalCase(key) + "() instead.");
        }
    }

    /**
     * Validates that complex Tika components aren't loaded via this method.
     * Interfaces and abstract classes are allowed, but require explicit type info in JSON.
     */
    private void validateClass(Class<?> clazz) throws TikaConfigException {
        // Check for known complex component types (defense in depth)
        String className = clazz.getName();
        if (PROHIBITED_CLASSES.contains(className)) {
            throw new TikaConfigException(
                clazz.getSimpleName() + " is a Tika component interface. " +
                "Use the appropriate TikaLoader method (e.g., loadParsers(), loadDetectors()).");
        }
    }

    /**
     * Converts kebab-case to PascalCase for error messages.
     */
    private String toPascalCase(String kebabCase) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : kebabCase.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
