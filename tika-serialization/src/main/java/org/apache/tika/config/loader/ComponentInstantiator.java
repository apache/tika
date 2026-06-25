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

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Utility class for instantiating Tika components from JSON configuration.
 * Provides common logic for all component loaders to avoid code duplication.
 */
public class ComponentInstantiator {

    /**
     * Instantiates a component from a JsonNode configuration.
     * <p>
     * Instantiation strategy:
     * <ol>
     *   <li>Try constructor with JsonConfig parameter</li>
     *   <li>Fall back to Jackson bean deserialization if config is provided</li>
     *   <li>Fall back to zero-arg constructor if no config</li>
     * </ol>
     *
     * @param componentClass the component class to instantiate
     * @param configNode the JSON configuration node (may be null or empty)
     * @param objectMapper the Jackson ObjectMapper for deserialization
     * @param <T> the component type
     * @return the instantiated component
     * @throws TikaConfigException if instantiation fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T instantiate(Class<?> componentClass,
                                     JsonNode configNode,
                                     ObjectMapper objectMapper)
            throws TikaConfigException {
        try {
            // Try JsonConfig constructor first
            try {
                Constructor<?> constructor = componentClass.getConstructor(JsonConfig.class);
                String jsonString = configNode != null ? configNode.toString() : "{}";
                JsonConfig jsonConfig = () -> jsonString;
                T component = (T) constructor.newInstance(jsonConfig);
                initializeIfNeeded(component);
                return component;
            } catch (NoSuchMethodException e) {
                // No JsonConfig constructor, fall back to other methods
            }

            // Fall back to no-arg constructor + Jackson bean deserialization (readerForUpdating)
            // Using readerForUpdating preserves defaults from the no-arg constructor,
            // unlike treeToValue which would null out unspecified fields.
            T component;
            component = (T) componentClass.getDeclaredConstructor().newInstance();
            if (configNode != null && !configNode.isEmpty()) {
                objectMapper.readerForUpdating(component).readValue(configNode);
            }

            // Call initialize() on Initializable components
            initializeIfNeeded(component);

            return component;

        } catch (TikaConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaConfigException(
                    "Failed to instantiate component '" + componentClass.getName() + "': " + e.getMessage(), e);
        }
    }

    /**
     * Instantiates a component by resolving a friendly name or FQCN to a class.
     * <p>
     * This is a convenience method that combines name resolution with instantiation.
     *
     * @param typeName the component type name (friendly name like "pdf-parser" or FQCN)
     * @param configNode the JSON configuration node (may be null or empty)
     * @param objectMapper the Jackson ObjectMapper for deserialization
     * @param classLoader the class loader for name resolution
     * @param <T> the component type
     * @return the instantiated component
     * @throws TikaConfigException if instantiation fails or type name is unknown
     */
    public static <T> T instantiate(String typeName,
                                     JsonNode configNode,
                                     ObjectMapper objectMapper,
                                     ClassLoader classLoader)
            throws TikaConfigException {
        try {
            Class<?> componentClass = ComponentNameResolver.resolveClass(typeName, classLoader);
            return instantiate(componentClass, configNode, objectMapper);
        } catch (ClassNotFoundException e) {
            throw new TikaConfigException("Unknown component type: '" + typeName + "'", e);
        }
    }

    /**
     * Instantiates a Tika component with full special-case handling.
     * <p>
     * This is the primary entry point for component instantiation from JSON configuration.
     * Handles:
     * <ul>
     *   <li>Type resolution via {@link ComponentNameResolver#resolveClass}</li>
     *   <li>Type compatibility validation against expectedType</li>
     *   <li>Special cases: DefaultParser/DefaultDetector rejection, MimeTypes singleton</li>
     *   <li>{@code _mime-include}/{@code _mime-exclude} extraction and stripping</li>
     *   <li>Three-step instantiation: JsonConfig ctor → readerForUpdating → no-arg</li>
     *   <li>{@link Initializable#initialize()} callback</li>
     *   <li>Parser MIME filter wrapping</li>
     * </ul>
     *
     * @param typeName the component type name (friendly name or FQCN)
     * @param configNode the JSON configuration node (may be null)
     * @param mapper the ObjectMapper for deserialization
     * @param classLoader the class loader for name resolution
     * @param expectedType the expected interface/base type (for validation), or null to skip
     * @return the instantiated component
     * @throws TikaConfigException if instantiation fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T instantiateComponent(String typeName, JsonNode configNode,
                                              ObjectMapper mapper, ClassLoader classLoader,
                                              Class<?> expectedType)
            throws TikaConfigException {
        // Resolve the class using ComponentNameResolver
        Class<?> clazz;
        try {
            clazz = ComponentNameResolver.resolveClass(typeName, classLoader);
        } catch (ClassNotFoundException e) {
            throw new TikaConfigException("Unknown type: " + typeName, e);
        }

        // Verify type compatibility
        if (expectedType != null && !expectedType.isAssignableFrom(clazz)) {
            throw new TikaConfigException("Type " + typeName + " (" + clazz.getName() +
                    ") is not assignable to " + expectedType.getName());
        }

        // DefaultParser and DefaultDetector must be loaded via TikaLoader
        if (clazz == DefaultParser.class) {
            throw new TikaConfigException("DefaultParser must be loaded via TikaLoader, not " +
                    "directly via Jackson deserialization. Use TikaLoader.load() to load configuration.");
        } else if (clazz == DefaultDetector.class) {
            throw new TikaConfigException("DefaultDetector must be loaded via TikaLoader, not " +
                    "directly via Jackson deserialization. Use TikaLoader.load() to load configuration.");
        }

        // Extract mime filter fields before stripping them
        Set<MediaType> includeTypes = extractMimeTypes(configNode, "_mime-include");
        Set<MediaType> excludeTypes = extractMimeTypes(configNode, "_mime-exclude");

        // Strip decorator fields before passing to component
        JsonNode cleanedConfig = stripDecoratorFields(configNode);

        try {
            Object instance;

            if (clazz == MimeTypes.class) {
                // MimeTypes must use the singleton to have all type definitions loaded
                instance = MimeTypes.getDefaultMimeTypes();
            } else if (cleanedConfig == null || cleanedConfig.isEmpty()) {
                // If no config, use default constructor
                instance = clazz.getDeclaredConstructor().newInstance();
            } else {
                // Try JsonConfig constructor first
                Constructor<?> jsonConfigCtor = findJsonConfigConstructor(clazz);
                if (jsonConfigCtor != null) {
                    // Use plain JSON mapper since the main mapper may be binary (Smile)
                    String json = TikaObjectMapperFactory.getPlainMapper()
                            .writeValueAsString(cleanedConfig);
                    instance = jsonConfigCtor.newInstance((JsonConfig) () -> json);
                } else {
                    // Fall back to no-arg constructor + Jackson bean deserialization
                    instance = clazz.getDeclaredConstructor().newInstance();
                    mapper.readerForUpdating(instance).readValue(cleanedConfig);
                }
            }

            // Call initialize() on Initializable components
            initializeIfNeeded(instance);

            // Wrap parser with mime filtering if include/exclude types specified
            if (instance instanceof Parser && (!includeTypes.isEmpty() || !excludeTypes.isEmpty())) {
                instance = ParserDecorator.withMimeFilters(
                        (Parser) instance, includeTypes, excludeTypes);
            }

            return (T) instance;

        } catch (TikaConfigException e) {
            throw e;
        } catch (Exception e) {
            throw new TikaConfigException("Failed to instantiate: " + typeName, e);
        }
    }

    private static Set<MediaType> extractMimeTypes(JsonNode configNode, String fieldName) {
        Set<MediaType> types = new HashSet<>();
        if (configNode == null || !configNode.has(fieldName)) {
            return types;
        }
        JsonNode arrayNode = configNode.get(fieldName);
        if (arrayNode.isArray()) {
            for (JsonNode typeNode : arrayNode) {
                types.add(MediaType.parse(typeNode.asText()));
            }
        }
        return types;
    }

    private static Constructor<?> findJsonConfigConstructor(Class<?> clazz) {
        try {
            return clazz.getConstructor(JsonConfig.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Strips decorator fields ({@code _mime-include}, {@code _mime-exclude}) from a real
     * component's config node. These directives are applied by {@link
     * org.apache.tika.config.loader.TikaLoader} as a wrapper around the component, not
     * consumed by the component itself, so they must be stripped before deserialization.
     * <p>
     * Convention: directives that share a JSON object with a real component's own
     * config properties carry a leading underscore to avoid namespace collisions
     * (e.g., a parser could legitimately have a config key named {@code mime-include}).
     * Directives on marker entries that have no component-config namespace —
     * {@code "exclude"} on {@code default-parser}/{@code default-detector} — need no
     * prefix; those are read directly by {@link AbstractSpiComponentLoader}.
     */
    private static JsonNode stripDecoratorFields(JsonNode configNode) {
        if (configNode == null || !configNode.isObject()) {
            return configNode;
        }
        ObjectNode cleaned = configNode.deepCopy();
        cleaned.remove("_mime-include");
        cleaned.remove("_mime-exclude");
        return cleaned;
    }

    /**
     * Calls initialize() on the component if it implements Initializable.
     *
     * @param component the component to initialize
     * @param <T> the component type
     * @throws TikaConfigException if initialization fails
     */
    private static <T> void initializeIfNeeded(T component) throws TikaConfigException {
        if (component instanceof Initializable) {
            ((Initializable) component).initialize();
        }
    }
}
