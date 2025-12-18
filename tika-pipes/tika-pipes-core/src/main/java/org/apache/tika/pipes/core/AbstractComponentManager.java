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
package org.apache.tika.pipes.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.config.InMemoryConfigStore;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaExtension;
import org.apache.tika.plugins.TikaExtensionFactory;

/**
 * Abstract base class for managing Tika components (Fetchers, Emitters, etc.).
 * Provides lazy instantiation, early validation, and optional runtime modifications.
 *
 * @param <T> the component type (e.g., Fetcher, Emitter)
 * @param <F> the factory type for creating components
 */
public abstract class AbstractComponentManager<T extends TikaExtension,
                                                F extends TikaExtensionFactory<T>> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComponentManager.class);

    protected final PluginManager pluginManager;
    private final ConfigStore configStore;
    private final Map<String, T> componentCache = new ConcurrentHashMap<>();
    private final boolean allowRuntimeModifications;

    protected AbstractComponentManager(PluginManager pluginManager,
                                      Map<String, ExtensionConfig> componentConfigs,
                                      boolean allowRuntimeModifications) {
        this(pluginManager, componentConfigs, allowRuntimeModifications, new InMemoryConfigStore());
    }

    protected AbstractComponentManager(PluginManager pluginManager,
                                      Map<String, ExtensionConfig> componentConfigs,
                                      boolean allowRuntimeModifications,
                                      ConfigStore configStore) {
        this.pluginManager = pluginManager;
        this.configStore = configStore;
        try {
            configStore.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ConfigStore", e);
        }
        componentConfigs.forEach(configStore::put);
        this.allowRuntimeModifications = allowRuntimeModifications;
    }

    /**
     * Returns the JSON configuration key for this component type (e.g., "fetchers", "emitters").
     */
    protected abstract String getConfigKey();

    /**
     * Returns the config store used by this manager.
     * Useful for subclasses that need direct access to the store.
     */
    protected ConfigStore getConfigStore() {
        return configStore;
    }

    /**
     * Returns the factory class for this component type.
     */
    protected abstract Class<F> getFactoryClass();

    /**
     * Returns the component name for error messages (e.g., "fetcher", "emitter").
     */
    protected abstract String getComponentName();

    /**
     * Creates a not-found exception for this component type.
     */
    protected abstract TikaException createNotFoundException(String message);

    /**
     * Validates the configuration and collects component configs without instantiating.
     */
    protected Map<String, ExtensionConfig> validateAndCollectConfigs(
            PluginManager pluginManager, JsonNode configNode) throws TikaConfigException, IOException {

        Map<String, F> factories = getFactories(pluginManager);
        Map<String, ExtensionConfig> configs = new HashMap<>();

        if (configNode != null && !configNode.isNull()) {
            // Outer loop: iterate over instance IDs
            Iterator<Map.Entry<String, JsonNode>> instanceFields = configNode.fields();
            while (instanceFields.hasNext()) {
                Map.Entry<String, JsonNode> instanceEntry = instanceFields.next();
                String instanceId = instanceEntry.getKey();
                JsonNode typeNode = instanceEntry.getValue();

                // Check for duplicate IDs (should not happen due to JSON parsing, but validate)
                if (configs.containsKey(instanceId)) {
                    throw new TikaConfigException("Duplicate " + getComponentName() +
                            " id: " + instanceId);
                }

                // Inner loop: extract the type name
                // The structure should be: { "type-name": { config } }
                // We expect exactly ONE type per instance
                Iterator<Map.Entry<String, JsonNode>> typeFields = typeNode.fields();

                if (!typeFields.hasNext()) {
                    throw new TikaConfigException(
                            "Invalid " + getComponentName() + " configuration for id '" + instanceId +
                            "': missing type specification. Expected format: {\"" + instanceId +
                            "\": {\"type-name\": {...}}}");
                }

                Map.Entry<String, JsonNode> typeEntry = typeFields.next();
                String typeName = typeEntry.getKey();
                JsonNode config = typeEntry.getValue();

                // Validate that there's only one type per instance
                if (typeFields.hasNext()) {
                    Map.Entry<String, JsonNode> extraTypeEntry = typeFields.next();
                    throw new TikaConfigException(
                            "Invalid " + getComponentName() + " configuration for id '" + instanceId +
                            "': multiple types specified ('" + typeName + "', '" +
                            extraTypeEntry.getKey() + "'). Each instance can only have one type.");
                }

                // Validate that factory exists for this type
                F factory = factories.get(typeName);
                if (factory == null) {
                    throw new TikaConfigException(
                            "Unknown " + getComponentName() + " type: '" + typeName +
                            "' for instance id '" + instanceId + "'. Available types: " +
                            factories.keySet());
                }

                configs.put(instanceId, new ExtensionConfig(instanceId, typeName,
                        toJsonString(config)));
            }
        }

        return configs;
    }

    protected Map<String, F> getFactories(PluginManager pluginManager) throws TikaConfigException {
        if (pluginManager.getStartedPlugins().isEmpty()) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }

        Map<String, F> factories = new HashMap<>();
        for (F factory : pluginManager.getExtensions(getFactoryClass())) {
            String name = factory.getName();
            ClassLoader cl = factory.getClass().getClassLoader();
            boolean isFromPlugin = cl instanceof org.pf4j.PluginClassLoader;

            F existing = factories.get(name);
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

    private static String toJsonString(final JsonNode node) throws TikaConfigException {
        try {
            return TikaObjectMapperFactory.getMapper().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to serialize config to JSON string", e);
        }
    }

    /**
     * Gets a component by ID, lazily instantiating it if needed.
     */
    public T getComponent(String id) throws IOException, TikaException {
        // Check cache first (fast path, no synchronization)
        T component = componentCache.get(id);
        if (component != null) {
            return component;
        }

        // Check if config exists
        ExtensionConfig config = configStore.get(id);
        if (config == null) {
            throw createNotFoundException(
                    "Can't find " + getComponentName() + " for id=" + id +
                    ". Available: " + configStore.keySet());
        }

        // Synchronized block to ensure only one thread builds the component
        synchronized (this) {
            // Double-check in case another thread built it while we were waiting
            component = componentCache.get(id);
            if (component != null) {
                return component;
            }

            // Build the component
            try {
                component = buildComponent(config);
                componentCache.put(id, component);
                LOG.debug("Lazily instantiated {}: {}", getComponentName(), id);
                return component;
            } catch (TikaConfigException e) {
                throw new IOException("Failed to build " + getComponentName() + ": " + id, e);
            }
        }
    }

    /**
     * Builds a component instance from its configuration.
     */
    private T buildComponent(ExtensionConfig config) throws TikaConfigException, IOException {
        Map<String, F> factories = getFactories(pluginManager);
        F factory = factories.get(config.name());

        if (factory == null) {
            // This shouldn't happen since we validated in load(), but check anyway
            throw new TikaConfigException(
                    "Unknown " + getComponentName() + " type: " + config.name() +
                    ". Available: " + factories.keySet());
        }

        return factory.buildExtension(config);
    }

    /**
     * Dynamically adds or updates a component configuration at runtime.
     * The component will not be instantiated until it is first requested via {@link #getComponent(String)}.
     * If a component with the same ID already exists, it will be replaced and the cached instance cleared.
     * <p>
     * This method is only available if the manager was loaded with allowRuntimeModifications=true.
     * <p>
     * Only authorized/authenticated users should be allowed to modify components. BE CAREFUL.
     *
     * @param config the extension configuration for the component
     * @throws TikaConfigException if the component type is unknown or if runtime modifications are not allowed
     * @throws IOException if there is an error accessing the plugin manager
     */
    public synchronized void saveComponent(ExtensionConfig config) throws TikaConfigException, IOException {
        if (!allowRuntimeModifications) {
            throw new TikaConfigException(
                    "Runtime modifications are not allowed. " + getClass().getSimpleName() +
                    " must be loaded with allowRuntimeModifications=true to use save" +
                    getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) + getComponentName().substring(1) + "()");
        }

        if (config == null) {
            throw new IllegalArgumentException("ExtensionConfig cannot be null");
        }

        String componentId = config.id();
        String typeName = config.name();

        // Validate that factory exists for this type
        Map<String, F> factories = getFactories(pluginManager);
        if (!factories.containsKey(typeName)) {
            throw new TikaConfigException(
                    "Unknown " + getComponentName() + " type: " + typeName +
                    ". Available: " + factories.keySet());
        }

        // If updating existing component, clear the cache so it gets re-instantiated
        if (configStore.containsKey(componentId)) {
            componentCache.remove(componentId);
            LOG.debug("Updating existing {} config: id={}, type={}", getComponentName(), componentId, typeName);
        } else {
            LOG.debug("Creating new {} config: id={}, type={}", getComponentName(), componentId, typeName);
        }

        // Store config without instantiating
        configStore.put(componentId, config);
    }

    /**
     * Returns the set of supported component IDs.
     */
    public Set<String> getSupported() {
        return configStore.keySet();
    }

    /**
     * Convenience method that returns a component if only one component
     * is configured. If 0 or > 1 components are configured, this throws an IllegalArgumentException.
     *
     * @return the single configured component
     */
    public T getComponent() throws IOException, TikaException {
        if (configStore.size() != 1) {
            throw new IllegalArgumentException(
                    "No-arg get" + getComponentName().substring(0, 1).toUpperCase(Locale.ROOT) +
                    getComponentName().substring(1) + "() requires exactly 1 configured " +
                    getComponentName() + ". Found: " + configStore.size() +
                    " (" + configStore.keySet() + ")");
        }
        // Get the single component id and use getComponent(id) for lazy loading
        String componentId = configStore.keySet().iterator().next();
        return getComponent(componentId);
    }
}
