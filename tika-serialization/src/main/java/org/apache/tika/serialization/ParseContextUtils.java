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

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentInstantiator;
import org.apache.tika.config.loader.ComponentRegistry;
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
 *   "tika-task-timeout": {
 *     "timeoutMillis": 30000
 *   },
 *   "pdf-parser": {
 *     "extractInlineImages": true
 *   }
 * }
 * </pre>
 * <p>
 * Components that implement {@link org.apache.tika.config.SelfConfiguring} are skipped
 * during resolution - they read their own config from ConfigContainer at runtime.
 */
public class ParseContextUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextUtils.class);
    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.getMapper();

    /**
     * Known interfaces that should be used as ParseContext keys.
     * When a component implements one of these interfaces, the interface is used as
     * the key in ParseContext instead of the concrete class.
     * <p>
     * These are NOT auto-discovered via SPI - they require explicit configuration.
     */
    private static final List<Class<?>> KNOWN_CONTEXT_INTERFACES = List.of(
            MetadataFilter.class
            // Add other known interfaces as needed
    );

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
     * Resolves all friendly-named components from ConfigContainer and adds them to ParseContext.
     * <p>
     * Iterates through all entries in ConfigContainer, looks up the friendly name in ComponentRegistry,
     * deserializes the JSON, and adds the instance to ParseContext.
     * <p>
     * Components that implement {@link org.apache.tika.config.SelfConfiguring} are skipped -
     * they read their own config from ConfigContainer at runtime.
     * <p>
     * The ParseContext key is determined by:
     * <ol>
     *   <li>Explicit contextKey from @TikaComponent annotation (if specified)</li>
     *   <li>Auto-detected from {@link #KNOWN_CONTEXT_INTERFACES} (if component implements one)</li>
     *   <li>The component's own class (default)</li>
     * </ol>
     * <p>
     * After resolution, resolved configs are removed from the ConfigContainer. If the
     * ConfigContainer becomes empty, it is removed from the ParseContext.
     *
     * @param context the ParseContext to populate
     * @param classLoader the ClassLoader to use for loading component classes
     */
    public static void resolveAll(ParseContext context, ClassLoader classLoader) {
        if (context == null) {
            return;
        }

        ConfigContainer container = context.get(ConfigContainer.class);
        if (container == null) {
            return;
        }

        List<String> resolvedKeys = new ArrayList<>();

        // First, process known array configs (e.g., "metadata-filters")
        // These don't depend on the other-configs registry
        for (String friendlyName : new ArrayList<>(container.getKeys())) {
            if (ARRAY_CONFIGS.containsKey(friendlyName)) {
                JsonConfig jsonConfig = container.get(friendlyName, null);
                if (jsonConfig != null && resolveArrayConfig(friendlyName, jsonConfig, context, classLoader)) {
                    resolvedKeys.add(friendlyName);
                }
            }
        }

        // Then, try to load the "other-configs" registry for single component configs
        try {
            ComponentRegistry registry = new ComponentRegistry("other-configs", classLoader);

            for (String friendlyName : container.getKeys()) {
                // Skip already resolved array configs
                if (resolvedKeys.contains(friendlyName)) {
                    continue;
                }

                JsonConfig jsonConfig = container.get(friendlyName, null);
                if (jsonConfig == null) {
                    continue;
                }

                ComponentInfo info = null;
                try {
                    // Try to find this friendly name in the registry
                    info = registry.getComponentInfo(friendlyName);

                    // Skip self-configuring components - they handle their own config
                    if (info.selfConfiguring()) {
                        LOG.debug("'{}' is self-configuring, skipping resolution", friendlyName);
                        continue;
                    }

                    // Determine the context key
                    Class<?> contextKey = determineContextKey(info, friendlyName);

                    // Deserialize and add to ParseContext
                    Object instance = MAPPER.readValue(jsonConfig.json(), info.componentClass());
                    context.set((Class) contextKey, instance);
                    resolvedKeys.add(friendlyName);

                    LOG.debug("Resolved '{}' -> {} with key {}",
                            friendlyName, info.componentClass().getName(), contextKey.getName());
                } catch (TikaConfigException e) {
                    // Not a registered component - that's okay, might be used for something else
                    LOG.debug("'{}' not found in other-configs registry, skipping", friendlyName);
                } catch (IOException e) {
                    LOG.warn("Failed to deserialize component '{}' of type {}", friendlyName,
                            info != null ? info.componentClass().getName() : "unknown", e);
                }
            }
        } catch (TikaConfigException e) {
            // other-configs registry not available - that's okay, array configs were still processed
            LOG.debug("other-configs registry not available: {}", e.getMessage());
        }

        // Remove resolved configs from the container
        for (String key : resolvedKeys) {
            container.remove(key);
        }

        // If the container is now empty, remove it from the ParseContext
        if (container.isEmpty()) {
            context.set(ConfigContainer.class, null);
        }
    }

    /**
     * Determines the ParseContext key for a component.
     *
     * @param info the component info
     * @param friendlyName the component's friendly name (for error messages)
     * @return the class to use as ParseContext key
     * @throws TikaConfigException if the component implements multiple known interfaces
     *                             and no explicit contextKey is specified
     */
    private static Class<?> determineContextKey(ComponentInfo info, String friendlyName)
            throws TikaConfigException {
        // Use explicit contextKey if provided
        if (info.contextKey() != null) {
            return info.contextKey();
        }

        // Auto-detect from known interfaces
        List<Class<?>> matches = new ArrayList<>();
        for (Class<?> iface : KNOWN_CONTEXT_INTERFACES) {
            if (iface.isAssignableFrom(info.componentClass())) {
                matches.add(iface);
            }
        }

        if (matches.size() > 1) {
            throw new TikaConfigException(
                    "Component '" + friendlyName + "' (" + info.componentClass().getName() +
                    ") implements multiple known context interfaces: " + matches +
                    ". Use @TikaComponent(contextKey=...) to specify which one to use.");
        }

        // Use the single matched interface, or fall back to the component class
        return matches.isEmpty() ? info.componentClass() : matches.get(0);
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
                                              ParseContext context, ClassLoader classLoader) {
        ArrayConfigInfo configInfo = ARRAY_CONFIGS.get(configName);
        if (configInfo == null) {
            return false;
        }

        try {
            JsonNode arrayNode = MAPPER.readTree(jsonConfig.json());
            if (!arrayNode.isArray()) {
                LOG.warn("Expected array for '{}', got: {}", configName, arrayNode.getNodeType());
                return false;
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
                    LOG.warn("Unexpected item format in '{}': {}", configName, item);
                    continue;
                }

                try {
                    Object component = ComponentInstantiator.instantiate(
                            typeName, configNode, MAPPER, classLoader);
                    components.add(component);
                    LOG.debug("Instantiated '{}' for '{}'", typeName, configName);
                } catch (TikaConfigException e) {
                    LOG.warn("Failed to instantiate '{}' for '{}': {}", typeName, configName, e.getMessage());
                }
            }

            // Create the composite and add to ParseContext
            if (!components.isEmpty()) {
                Object composite = createComposite(configName, components, configInfo);
                if (composite != null) {
                    context.set((Class) configInfo.contextKey(), composite);
                    LOG.debug("Resolved '{}' -> {} with {} components",
                            configName, composite.getClass().getSimpleName(), components.size());
                    return true;
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to parse array config '{}': {}", configName, e.getMessage());
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
