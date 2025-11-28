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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.ServiceLoaderUtils;

/**
 * Generic loader for Tika components (detectors, encoding detectors, filters, etc.).
 * Supports two loading modes:
 * <ul>
 *   <li>Array-based: explicit ordering, no SPI fallback (default for non-parsers)</li>
 *   <li>SPI-only: all components from ServiceLoader (when config section missing)</li>
 * </ul>
 *
 * @param <T> the component type
 */
public class CompositeComponentLoader<T> {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeComponentLoader.class);

    private final Class<T> componentInterface;
    private final String componentTypeName;
    private final String indexFileName;
    private final ClassLoader classLoader;
    private final ObjectMapper objectMapper;

    /**
     * Creates a component loader.
     *
     * @param componentInterface the component interface (e.g., Detector.class)
     * @param componentTypeName the JSON config key (e.g., "detectors")
     * @param indexFileName the index file name (e.g., "detectors")
     * @param classLoader the class loader
     * @param objectMapper the Jackson ObjectMapper
     */
    public CompositeComponentLoader(Class<T> componentInterface, String componentTypeName,
                                     String indexFileName, ClassLoader classLoader,
                                     ObjectMapper objectMapper) {
        this.componentInterface = componentInterface;
        this.componentTypeName = componentTypeName;
        this.indexFileName = indexFileName;
        this.classLoader = classLoader;
        this.objectMapper = objectMapper;
    }

    /**
     * Loads components from array-based JSON config.
     * If config section exists, uses only explicitly listed components (no SPI fallback).
     * If config section missing, uses SPI to discover all components.
     *
     * @param config the Tika JSON configuration
     * @return ordered list of component instances
     * @throws TikaConfigException if loading fails
     */
    public List<T> loadFromArray(TikaJsonConfig config) throws TikaConfigException {
        // Check if section exists in config
        if (!config.hasComponentSection(componentTypeName)) {
            // Section doesn't exist - use SPI fallback
            return loadAllFromSpi();
        }

        // Check if section is using object format instead of array format
        JsonNode sectionNode = config.getRootNode().get(componentTypeName);
        if (sectionNode != null && !sectionNode.isArray()) {
            throw new TikaConfigException(
                    "Configuration section '" + componentTypeName + "' must be an array, not an object. " +
                    "Expected format: \"" + componentTypeName + "\": [{\"component-name\": {...}}], " +
                    "Got object format: \"" + componentTypeName + "\": {\"component-name\": {...}}");
        }

        // Section exists - load only explicitly configured components (no SPI)
        List<Map.Entry<String, JsonNode>> arrayComponents = config.getArrayComponents(componentTypeName);

        if (arrayComponents.isEmpty()) {
            // Explicit empty array means no components
            return Collections.emptyList();
        }

        ComponentRegistry registry = new ComponentRegistry(indexFileName, classLoader);
        List<T> instances = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : arrayComponents) {
            String name = entry.getKey();
            JsonNode configNode = entry.getValue();

            T instance = loadComponent(name, configNode, registry);
            instances.add(instance);
        }

        return instances;
    }

    /**
     * Loads components from JSON config with SPI fallback (used by parsers).
     *
     * @param config the Tika JSON configuration
     * @return list of component instances
     * @throws TikaConfigException if loading fails
     */
    public List<T> load(TikaJsonConfig config) throws TikaConfigException {
        List<T> instances = new ArrayList<>();

        // Load configured components
        if (config.hasComponents(componentTypeName)) {
            ComponentRegistry registry = new ComponentRegistry(indexFileName, classLoader);
            Map<String, JsonNode> components = config.getComponents(componentTypeName);

            for (Map.Entry<String, JsonNode> entry : components.entrySet()) {
                String name = entry.getKey();
                JsonNode configNode = entry.getValue();

                T instance = loadConfiguredComponent(name, configNode, registry);
                instances.add(instance);
            }
        }

        // Add SPI-discovered components
        List<T> spiComponents = loadSpiComponents();
        instances.addAll(spiComponents);

        return instances;
    }

    private T loadConfiguredComponent(String name, JsonNode configNode,
                                       ComponentRegistry registry)
            throws TikaConfigException {
        try {
            // Get component class
            Class<?> componentClass = registry.getComponentClass(name);

            // Extract framework config
            FrameworkConfig frameworkConfig = FrameworkConfig.extract(configNode, objectMapper);

            // Instantiate component
            T instance = instantiateComponent(componentClass, frameworkConfig.getComponentConfigJson());

            return instance;

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load component '" + name + "' of type " +
                    componentTypeName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private T instantiateComponent(Class<?> componentClass, JsonConfig configJson)
            throws TikaConfigException {
        try {
            // Try constructor with JsonConfig parameter
            try {
                Constructor<?> constructor = componentClass.getConstructor(JsonConfig.class);
                return (T) constructor.newInstance(configJson);
            } catch (NoSuchMethodException e) {
                // Check if JSON config has actual configuration
                if (hasConfiguration(configJson)) {
                    throw new TikaConfigException(
                            "Component '" + componentClass.getName() + "' has configuration in JSON, " +
                            "but does not have a constructor that accepts JsonConfig. " +
                            "Please add a constructor: public " + componentClass.getSimpleName() + "(JsonConfig jsonConfig)");
                }
                // Fall back to zero-arg constructor if no configuration provided
                return (T) ServiceLoaderUtils.newInstance(componentClass,
                        new org.apache.tika.config.ServiceLoader(classLoader));
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new TikaConfigException("Failed to instantiate component: " +
                    componentClass.getName(), e);
        }
    }

    /**
     * Checks if the JsonConfig contains actual configuration (non-empty JSON object with fields).
     *
     * @param jsonConfig the JSON configuration
     * @return true if there's meaningful configuration, false if empty or just "{}"
     */
    private boolean hasConfiguration(JsonConfig jsonConfig) {
        if (jsonConfig == null) {
            return false;
        }
        String json = jsonConfig.json();
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        // Parse to check if it's an empty object or has actual fields
        try {
            JsonNode node = objectMapper.readTree(json);
            // Check if it's an object and has at least one field
            if (node.isObject() && node.size() > 0) {
                return true;
            }
            return false;
        } catch (Exception e) {
            // If we can't parse it, assume it has configuration to be safe
            return true;
        }
    }

    private List<T> loadSpiComponents() {
        List<T> result = new ArrayList<>();
        ServiceLoader<T> serviceLoader = ServiceLoader.load(componentInterface, classLoader);

        Iterator<T> iterator = serviceLoader.iterator();
        while (iterator.hasNext()) {
            try {
                T instance = iterator.next();
                result.add(instance);
            } catch (Exception e) {
                // Log and skip problematic SPI providers
                LOG.warn("Failed to load SPI component of type {}: {}", componentTypeName, e.getMessage(), e);
            }
        }

        return result;
    }

    private T loadComponent(String name, JsonNode configNode, ComponentRegistry registry)
            throws TikaConfigException {
        try {
            // Get component class
            Class<?> componentClass = registry.getComponentClass(name);

            // Wrap JSON string in JsonConfig
            String jsonString = objectMapper.writeValueAsString(configNode);
            JsonConfig jsonConfig = () -> jsonString;

            // Instantiate component
            return instantiateComponent(componentClass, jsonConfig);

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load component '" + name + "' of type " +
                    componentTypeName, e);
        }
    }

    private List<T> loadAllFromSpi() {
        List<T> result = new ArrayList<>();
        ServiceLoader<T> serviceLoader = ServiceLoader.load(componentInterface, classLoader);

        Iterator<T> iterator = serviceLoader.iterator();
        while (iterator.hasNext()) {
            try {
                T instance = iterator.next();
                result.add(instance);
            } catch (Exception e) {
                // Log and skip problematic SPI providers
                LOG.warn("Failed to load SPI component of type {}: {}", componentTypeName, e.getMessage(), e);
            }
        }

        return result;
    }
}
