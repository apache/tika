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
package org.apache.tika.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pf4j.PluginManager;

import org.apache.tika.exception.TikaConfigException;

public class PluginComponentLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Load a singleton component from config.
     * <p>
     * JSON structure: { "typeName": { config } }
     * </p>
     *
     * @return Optional containing the instance, or empty if configNode is null/empty
     */
    public static <T extends TikaExtension> Optional<T> loadSingleton(
            PluginManager pluginManager,
            Class<? extends TikaExtensionFactory<T>> factoryClass,
            JsonNode configNode) throws TikaConfigException, IOException {

        if (configNode == null || configNode.isNull() || configNode.isEmpty()) {
            return Optional.empty();
        }

        Map<String, TikaExtensionFactory<T>> factories = getFactories(pluginManager, factoryClass);

        String typeName = extractTypeName(configNode, "singleton");
        JsonNode config = configNode.get(typeName);

        TikaExtensionFactory<T> factory = factories.get(typeName);
        if (factory == null) {
            throw new TikaConfigException(
                    "Unknown type: " + typeName + ". Available: " + factories.keySet());
        }

        // Use typeName as id for singletons
        T instance = factory.buildExtension(
                new ExtensionConfig(typeName, typeName, toJsonString(config)));
        return Optional.of(instance);
    }

    /**
     * Load multiple named instances from config, grouped by type.
     * <p>
     * JSON structure:
     * <pre>
     * {
     *   "typeName": {
     *     "instanceId1": { config },
     *     "instanceId2": { config }
     *   },
     *   "typeName2": {
     *     "instanceId3": { config }
     *   }
     * }
     * </pre>
     * </p>
     */
    public static <T extends TikaExtension> Map<String, T> loadInstances(
            PluginManager pluginManager,
            Class<? extends TikaExtensionFactory<T>> factoryClass,
            JsonNode configNode) throws TikaConfigException, IOException {

        Map<String, TikaExtensionFactory<T>> factories = getFactories(pluginManager, factoryClass);

        Map<String, T> instances = new LinkedHashMap<>();
        if (configNode != null && !configNode.isNull()) {
            // Outer loop: iterate over type names
            Iterator<Map.Entry<String, JsonNode>> typeFields = configNode.fields();
            while (typeFields.hasNext()) {
                Map.Entry<String, JsonNode> typeEntry = typeFields.next();
                String typeName = typeEntry.getKey();
                JsonNode instancesNode = typeEntry.getValue();

                TikaExtensionFactory<T> factory = factories.get(typeName);
                if (factory == null) {
                    throw new TikaConfigException(
                            "Unknown type: " + typeName + ". Available: " + factories.keySet());
                }

                // Inner loop: iterate over instances of this type
                Iterator<Map.Entry<String, JsonNode>> instanceFields = instancesNode.fields();
                while (instanceFields.hasNext()) {
                    Map.Entry<String, JsonNode> instanceEntry = instanceFields.next();
                    String instanceId = instanceEntry.getKey();
                    JsonNode config = instanceEntry.getValue();

                    T instance = factory.buildExtension(
                            new ExtensionConfig(instanceId, typeName, toJsonString(config)));

                    if (instances.putIfAbsent(instanceId, instance) != null) {
                        throw new TikaConfigException("Duplicate instance id: " + instanceId);
                    }
                }
            }
        }

        return instances;
    }

    /**
     * Load multiple unnamed instances from config, keyed by type name.
     * <p>
     * JSON structure: { "typeName": { config }, "typeName2": { config2 }, ... }
     * </p>
     * <p>
     * Use this for composite components like reporters where each type appears once
     * and instances don't need individual names.
     * </p>
     *
     * @return List of instances in config order, empty list if configNode is null/empty
     */
    public static <T extends TikaExtension> List<T> loadUnnamedInstances(
            PluginManager pluginManager,
            Class<? extends TikaExtensionFactory<T>> factoryClass,
            JsonNode configNode) throws TikaConfigException, IOException {

        List<T> instances = new ArrayList<>();
        if (configNode == null || configNode.isNull() || configNode.isEmpty()) {
            return instances;
        }

        Map<String, TikaExtensionFactory<T>> factories = getFactories(pluginManager, factoryClass);

        Iterator<Map.Entry<String, JsonNode>> fields = configNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String typeName = entry.getKey();
            JsonNode config = entry.getValue();

            TikaExtensionFactory<T> factory = factories.get(typeName);
            if (factory == null) {
                throw new TikaConfigException(
                        "Unknown type: " + typeName + ". Available: " + factories.keySet());
            }

            // Use typeName as id for unnamed instances
            T instance = factory.buildExtension(
                    new ExtensionConfig(typeName, typeName, toJsonString(config)));
            instances.add(instance);
        }

        return instances;
    }

    private static <T extends TikaExtension> Map<String, TikaExtensionFactory<T>> getFactories(
            PluginManager pluginManager,
            Class<? extends TikaExtensionFactory<T>> factoryClass) throws TikaConfigException {

        if (pluginManager.getStartedPlugins().isEmpty()) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }

        Map<String, TikaExtensionFactory<T>> factories = new HashMap<>();
        for (TikaExtensionFactory<T> factory : pluginManager.getExtensions(factoryClass)) {
            String name = factory.getName();
            ClassLoader cl = factory.getClass().getClassLoader();
            boolean isFromPlugin = cl instanceof org.pf4j.PluginClassLoader;

            TikaExtensionFactory<T> existing = factories.get(name);
            if (existing != null) {
                boolean existingIsFromPlugin = existing.getClass().getClassLoader()
                        instanceof org.pf4j.PluginClassLoader;
                if (isFromPlugin && !existingIsFromPlugin) {
                    // Replace classpath version with plugin version
                    factories.put(name, factory);
                }
                // Otherwise skip duplicate (keep existing)
                continue;
            }
            factories.put(name, factory);
        }
        return factories;
    }

    private static String extractTypeName(JsonNode wrapper, String contextName)
            throws TikaConfigException {
        Iterator<String> fieldNames = wrapper.fieldNames();
        if (!fieldNames.hasNext()) {
            throw new TikaConfigException("'" + contextName + "' has no type wrapper");
        }
        String typeName = fieldNames.next();
        if (fieldNames.hasNext()) {
            throw new TikaConfigException("'" + contextName + "' has multiple type wrappers");
        }
        return typeName;
    }

    private static String toJsonString(final JsonNode node)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to serialize config to JSON string", e);
        }
    }
}
