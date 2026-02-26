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
package org.apache.tika.serialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentInstantiator;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

/**
 * Utility methods for working with ParseContext objects in JSON-based configurations.
 * <p>
 * Uses friendly-name format for configuration:
 * <pre>
 * "parse-context": {
 *   "timeout-limits": {
 *     "progressTimeoutMillis": 60000
 *   },
 *   "pdf-parser": {
 *     "extractInlineImages": true
 *   }
 * }
 * </pre>
 * <p>
 * Components that implement {@link org.apache.tika.config.SelfConfiguring} are skipped
 * during resolution - they read their own config from jsonConfigs at runtime.
 */
public class ParseContextUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextUtils.class);
    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.getMapper();

    /**
     * Mapping of array config keys to their context keys and composite wrapper factories.
     * Key: config name (e.g., "metadata-filters")
     * Value: (contextKey, componentInterface)
     */
    private static final Map<String, ArrayConfigInfo> ARRAY_CONFIGS = Map.of(
            "metadata-filters", new ArrayConfigInfo(MetadataFilter.class, MetadataFilter.class)
    );

    /**
     * Holds information about how to process array configs.
     */
    private record ArrayConfigInfo(Class<?> contextKey, Class<?> componentInterface) {}

    /**
     * Resolves all JSON configs from ParseContext and adds them to the resolved cache.
     * <p>
     * Iterates through all entries in jsonConfigs, looks up the friendly name in
     * ComponentNameResolver (which searches all registered component registries),
     * deserializes the JSON, and caches the instance in resolvedConfigs.
     * <p>
     * Components that implement {@link org.apache.tika.config.SelfConfiguring} are skipped -
     * they read their own config at runtime via {@link ConfigDeserializer}.
     * <p>
     * The ParseContext key is determined by the contextKey from the .idx file, which is
     * auto-detected by the annotation processor from the service interface, or explicitly
     * specified via {@code @TikaComponent(contextKey=...)}. Falls back to the component
     * class if no contextKey is available.
     *
     * @param context the ParseContext to populate
     * @param classLoader the ClassLoader to use for loading component classes
     */
    public static void resolveAll(ParseContext context, ClassLoader classLoader)
            throws TikaConfigException {
        if (context == null) {
            return;
        }

        Map<String, JsonConfig> jsonConfigs = context.getJsonConfigs();
        if (jsonConfigs.isEmpty()) {
            return;
        }

        // First, process known array configs (e.g., "metadata-filters")
        // These don't depend on the parse-context registry
        for (String friendlyName : new ArrayList<>(jsonConfigs.keySet())) {
            if (ARRAY_CONFIGS.containsKey(friendlyName)) {
                JsonConfig jsonConfig = jsonConfigs.get(friendlyName);
                if (jsonConfig != null) {
                    resolveArrayConfig(friendlyName, jsonConfig, context, classLoader);
                }
            }
        }

        // Then, try to resolve single component configs using ComponentNameResolver
        // This searches all registered component registries, not just "parse-context"
        for (Map.Entry<String, JsonConfig> entry : jsonConfigs.entrySet()) {
            String friendlyName = entry.getKey();
            JsonConfig jsonConfig = entry.getValue();

            // Skip already resolved configs (including array configs)
            if (context.getResolvedConfig(friendlyName) != null) {
                continue;
            }

            // Try to find this friendly name in any registered component registry
            var optionalInfo = ComponentNameResolver.getComponentInfo(friendlyName);
            if (optionalInfo.isEmpty()) {
                // Not a registered component - that's okay, might be used for something else
                LOG.debug("'{}' not found in any component registry, skipping", friendlyName);
                continue;
            }

            ComponentInfo info = optionalInfo.get();

            // Skip self-configuring components - they handle their own config
            if (info.selfConfiguring()) {
                LOG.debug("'{}' is self-configuring, skipping resolution", friendlyName);
                continue;
            }

            // Determine the context key
            Class<?> contextKey = determineContextKey(info);

            try {
                // Deserialize and cache in resolvedConfigs, also add to context
                Object instance = MAPPER.readValue(jsonConfig.json(), info.componentClass());
                context.setResolvedConfig(friendlyName, instance);
                context.set((Class) contextKey, instance);

                LOG.debug("Resolved '{}' -> {} with key {}",
                        friendlyName, info.componentClass().getName(), contextKey.getName());
            } catch (IOException e) {
                throw new TikaConfigException("Failed to deserialize component '" +
                        friendlyName + "' of type " + info.componentClass().getName(), e);
            }
        }
    }

    /**
     * Determines the ParseContext key for a component.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Explicit contextKey from .idx file (via @TikaComponent annotation)</li>
     *   <li>Auto-detect from implemented interfaces (using TikaModule.COMPACT_FORMAT_INTERFACES)</li>
     *   <li>Fall back to the component class itself</li>
     * </ol>
     * <p>
     * Security note: This only determines the context key - it does NOT affect which
     * classes can be instantiated. Classes must still be registered via @TikaComponent.
     *
     * @param info the component info
     * @return the class to use as ParseContext key
     */
    private static Class<?> determineContextKey(ComponentInfo info) {
        // Use explicit contextKey from .idx file if specified
        if (info.contextKey() != null) {
            return info.contextKey();
        }
        // Auto-detect from implemented interfaces at runtime
        Class<?> contextKeyInterface = TikaModule.findContextKeyInterface(info.componentClass());
        if (contextKeyInterface != null) {
            return contextKeyInterface;
        }
        // Fall back to the component class itself
        return info.componentClass();
    }

    /**
     * Resolves an array config entry (e.g., "metadata-filters") to a composite component.
     * <p>
     * The array can contain either strings (friendly names) or objects:
     * <pre>
     * ["filter-name-1", "filter-name-2"]              // String shorthand
     * [{"filter-name-1": {}}, {"filter-name-2": {}}]  // Object format
     * </pre>
     *
     * @param configName the config name (e.g., "metadata-filters")
     * @param jsonConfig the JSON configuration (should be an array)
     * @param context the ParseContext to add the resolved component to
     * @param classLoader the ClassLoader to use for loading component classes
     * @return true if resolution was successful
     */
    @SuppressWarnings("unchecked")
    private static boolean resolveArrayConfig(String configName, JsonConfig jsonConfig,
                                              ParseContext context, ClassLoader classLoader)
            throws TikaConfigException {
        ArrayConfigInfo configInfo = ARRAY_CONFIGS.get(configName);
        if (configInfo == null) {
            return false;
        }

        try {
            JsonNode arrayNode = MAPPER.readTree(jsonConfig.json());
            if (!arrayNode.isArray()) {
                throw new TikaConfigException("Expected array for '" + configName +
                        "', got: " + arrayNode.getNodeType());
            }

            List<Object> components = new ArrayList<>();

            for (JsonNode item : arrayNode) {
                String typeName;
                JsonNode configNode;

                if (item.isTextual()) {
                    // String shorthand: "component-name"
                    typeName = item.asText();
                    configNode = MAPPER.createObjectNode();
                } else if (item.isObject() && item.size() == 1) {
                    // Object format: {"component-name": {...}}
                    typeName = item.fieldNames().next();
                    configNode = item.get(typeName);
                } else {
                    throw new TikaConfigException("Unexpected item format in '" +
                            configName + "': " + item);
                }

                Object component = ComponentInstantiator.instantiate(
                        typeName, configNode, MAPPER, classLoader);
                components.add(component);
                LOG.debug("Instantiated '{}' for '{}'", typeName, configName);
            }

            // Create the composite and add to ParseContext
            if (!components.isEmpty()) {
                Object composite = createComposite(configName, components, configInfo);
                if (composite != null) {
                    context.setResolvedConfig(configName, composite);
                    context.set((Class) configInfo.contextKey(), composite);
                    LOG.debug("Resolved '{}' -> {} with {} components",
                            configName, composite.getClass().getSimpleName(), components.size());
                    return true;
                }
            }
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse array config '" +
                    configName + "'", e);
        }

        return false;
    }

    /**
     * Creates a composite component from a list of individual components.
     *
     * @param configName the config name (for error messages)
     * @param components the list of components
     * @param configInfo the array config info
     * @return the composite component, or null if creation failed
     */
    @SuppressWarnings("unchecked")
    private static Object createComposite(String configName, List<Object> components,
                                          ArrayConfigInfo configInfo) {
        // Handle known composite types
        if (configInfo.componentInterface() == MetadataFilter.class) {
            List<MetadataFilter> filters = (List<MetadataFilter>) (List<?>) components;
            return new CompositeMetadataFilter(filters);
        }

        // Add more composite types as needed
        LOG.warn("No composite factory for '{}'", configName);
        return null;
    }
}
