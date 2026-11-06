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
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Initializable;
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
     * Array configs whose composite is not itself a registered component. Any other array
     * entry resolves through a registered component with a {@code (List<T>)} constructor;
     * see {@link #listComposite(String)}.
     */
    private static final Map<String, ArrayConfigInfo> ARRAY_CONFIGS = Map.of(
            "metadata-filters", new ArrayConfigInfo(MetadataFilter.class, MetadataFilter.class,
                    components -> new CompositeMetadataFilter(castList(components)))
    );

    private record ArrayConfigInfo(Class<?> contextKey, Class<?> componentInterface,
                                   Function<List<Object>, Object> factory) {}

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
            JsonConfig jsonConfig = jsonConfigs.get(friendlyName);
            if (jsonConfig == null) {
                continue;
            }
            ArrayConfigInfo configInfo = ARRAY_CONFIGS.get(friendlyName);
            if (configInfo == null && jsonConfig.json().stripLeading().startsWith("[")) {
                configInfo = listComposite(friendlyName);
            }
            if (configInfo != null) {
                resolveArrayConfig(friendlyName, jsonConfig, context, classLoader, configInfo);
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
                // Fail rather than warn: parse-context is where the DoS limits live
                // (timeout-limits, embedded-limits, output-limits), so a typo'd name
                // silently reverts to defaults an operator believes they overrode.
                throw new TikaConfigException("Unrecognized parse-context entry '" + friendlyName
                        + "'. Check for a typo; it does not match any registered component.");
            }

            ComponentInfo info = optionalInfo.get();

            // Skip self-configuring components - they handle their own config
            if (info.selfConfiguring()) {
                LOG.debug("'{}' is self-configuring, skipping resolution", friendlyName);
                continue;
            }

            // Determine the context key
            Class<?> contextKey = ComponentNameResolver.determineContextKey(info);

            try {
                // A component whose config binds only through a (JsonConfig) constructor
                // (e.g. LegacyKeyMigrationFilter) takes the same instantiation path as
                // array configs; everything else keeps vanilla Jackson (enums like
                // parse-mode and @JsonCreator beans have no no-arg constructor).
                Object instance;
                boolean trusted = jsonConfig.trusted();
                if (ComponentInstantiator.hasJsonConfigConstructor(info.componentClass())) {
                    instance = ComponentInstantiator.instantiateComponent(
                            friendlyName, MAPPER.readTree(jsonConfig.json()),
                            MAPPER, classLoader, null, trusted);
                } else {
                    if (!trusted) {
                        ComponentInstantiator.assertRequestMayConfigure(friendlyName,
                                info.componentClass(), MAPPER.readTree(jsonConfig.json()), MAPPER);
                    }
                    instance = ComponentInstantiator.withTrust(MAPPER.readerFor(info.componentClass()),
                            trusted).readValue(jsonConfig.json());
                    // Cross-field validation hook; the ComponentInstantiator paths call
                    // initialize() themselves, this path deserializes directly.
                    if (instance instanceof Initializable initializable) {
                        initializable.initialize();
                    }
                }
                context.setResolvedConfig(friendlyName, instance);
                context.set((Class) contextKey, instance);

                LOG.debug("Resolved '{}' -> {} with key {}",
                        friendlyName, info.componentClass().getName(), contextKey.getName());
            } catch (IOException e) {
                throw new TikaConfigException("Failed to deserialize component '" + friendlyName
                        + "' of type " + info.componentClass().getName() + ": "
                        + ComponentInstantiator.reason(e), e);
            }
        }
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
                                              ParseContext context, ClassLoader classLoader,
                                              ArrayConfigInfo configInfo)
            throws TikaConfigException {
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

                // Check assignability before constructing, so an element of the wrong type is
                // rejected up front rather than instantiated and later discarded.
                Object component = ComponentInstantiator.instantiateComponent(typeName, configNode,
                        MAPPER, classLoader, configInfo.componentInterface(), jsonConfig.trusted());
                components.add(component);
                LOG.debug("Instantiated '{}' for '{}'", typeName, configName);
            }

            // Create the composite and add to ParseContext
            if (!components.isEmpty() || !ARRAY_CONFIGS.containsKey(configName)) {
                Object composite = configInfo.factory().apply(components);
                context.setResolvedConfig(configName, composite);
                context.set((Class) configInfo.contextKey(), composite);
                LOG.debug("Resolved '{}' -> {} with {} components",
                        configName, composite.getClass().getSimpleName(), components.size());
                return true;
            }
        } catch (IOException e) {
            throw new TikaConfigException("Failed to parse array config '" +
                    configName + "'", e);
        }

        return false;
    }

    /**
     * A registered component with a public {@code (List<T>)} constructor is a composite: its
     * name takes a JSON array of {@code T} components, e.g. {@code "jsoup-filters": [...]}.
     *
     * @return null if {@code friendlyName} is not such a component
     */
    private static ArrayConfigInfo listComposite(String friendlyName) {
        var optionalInfo = ComponentNameResolver.getComponentInfo(friendlyName);
        if (optionalInfo.isEmpty()) {
            return null;
        }
        ComponentInfo info = optionalInfo.get();
        for (Constructor<?> c : info.componentClass().getConstructors()) {
            Type[] params = c.getGenericParameterTypes();
            if (params.length == 1 && params[0] instanceof ParameterizedType pt
                    && pt.getRawType() == List.class
                    && pt.getActualTypeArguments()[0] instanceof Class<?> elementType) {
                return new ArrayConfigInfo(ComponentNameResolver.determineContextKey(info),
                        elementType, components -> newComposite(c, components));
            }
        }
        return null;
    }

    private static Object newComposite(Constructor<?> c, List<Object> components) {
        try {
            return c.newInstance(components);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot construct " + c.getDeclaringClass().getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(List<Object> components) {
        return (List<T>) (List<?>) components;
    }
}
