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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ConfigContainer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

/**
 * Utility methods for working with ParseContext objects in JSON-based configurations.
 * <p>
 * Supports both legacy verbose format and new friendly-name format:
 * <pre>
 * // Legacy format:
 * "parse-context": {
 *   "objects": {
 *     "org.apache.tika.config.TikaTaskTimeout": {
 *       "@class": "org.apache.tika.config.TikaTaskTimeout",
 *       "timeoutMillis": 30000
 *     }
 *   }
 * }
 *
 * // New friendly-name format:
 * "parse-context": {
 *   "tika-task-timeout": {
 *     "timeoutMillis": 30000
 *   }
 * }
 * </pre>
 * <p>
 * Components that implement {@link org.apache.tika.config.SelfConfiguring} are skipped
 * during resolution - they read their own config from ConfigContainer at runtime.
 */
public class ParseContextUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextUtils.class);
    private static final ObjectMapper MAPPER = PolymorphicObjectMapperFactory.getMapper();

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

        try {
            // Load the "other-configs" registry which includes parse-context components
            ComponentRegistry registry = new ComponentRegistry("other-configs", classLoader);

            // Iterate through all configs in the container
            for (String friendlyName : container.getKeys()) {
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
            LOG.warn("Failed to load other-configs registry for parse-context resolution", e);
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
}
