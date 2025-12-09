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
package org.apache.tika.pipes.core.fetcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.PolymorphicObjectMapperFactory;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId.
 * Fetchers are instantiated lazily on first use.
 */
public class FetcherManager {

    public static final String CONFIG_KEY = "fetchers";
    private static final Logger LOG = LoggerFactory.getLogger(FetcherManager.class);

    /**
     * Loads a FetcherManager without allowing runtime modifications.
     * Use {@link #load(PluginManager, TikaJsonConfig, boolean)} to enable runtime fetcher additions.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @return a FetcherManager that does not allow runtime modifications
     */
    public static FetcherManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig) throws TikaConfigException, IOException {
        return load(pluginManager, tikaJsonConfig, false);
    }

    /**
     * Loads a FetcherManager with optional support for runtime modifications.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @param allowRuntimeModifications if true, allows calling {@link #saveFetcher(ExtensionConfig)} to add fetchers at runtime
     * @return a FetcherManager
     */
    public static FetcherManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig, boolean allowRuntimeModifications) throws TikaConfigException, IOException {
        JsonNode fetchersNode = tikaJsonConfig.getRootNode().get(CONFIG_KEY);

        // Validate configuration and collect fetcher configs without instantiating
        Map<String, ExtensionConfig> configs = validateAndCollectConfigs(pluginManager, fetchersNode);

        return new FetcherManager(pluginManager, configs, allowRuntimeModifications);
    }

    /**
     * Validates the configuration by checking that factories exist for all types,
     * and collects the configuration data without instantiating fetchers.
     */
    private static Map<String, ExtensionConfig> validateAndCollectConfigs(
            PluginManager pluginManager, JsonNode configNode) throws TikaConfigException, IOException {

        Map<String, FetcherFactory> factories = getFactories(pluginManager);
        Map<String, ExtensionConfig> configs = new HashMap<>();

        if (configNode != null && !configNode.isNull()) {
            // Outer loop: iterate over type names
            Iterator<Map.Entry<String, JsonNode>> typeFields = configNode.fields();
            while (typeFields.hasNext()) {
                Map.Entry<String, JsonNode> typeEntry = typeFields.next();
                String typeName = typeEntry.getKey();
                JsonNode instancesNode = typeEntry.getValue();

                // Validate that factory exists
                FetcherFactory factory = factories.get(typeName);
                if (factory == null) {
                    throw new TikaConfigException(
                            "Unknown fetcher type: " + typeName + ". Available: " + factories.keySet());
                }

                // Inner loop: iterate over instances of this type
                Iterator<Map.Entry<String, JsonNode>> instanceFields = instancesNode.fields();
                while (instanceFields.hasNext()) {
                    Map.Entry<String, JsonNode> instanceEntry = instanceFields.next();
                    String instanceId = instanceEntry.getKey();
                    JsonNode config = instanceEntry.getValue();

                    if (configs.containsKey(instanceId)) {
                        throw new TikaConfigException("Duplicate fetcher id: " + instanceId);
                    }

                    configs.put(instanceId, new ExtensionConfig(instanceId, typeName, toJsonString(config)));
                }
            }
        }

        return configs;
    }

    private static Map<String, FetcherFactory> getFactories(PluginManager pluginManager) throws TikaConfigException {
        if (pluginManager.getStartedPlugins().isEmpty()) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }

        Map<String, FetcherFactory> factories = new HashMap<>();
        for (FetcherFactory factory : pluginManager.getExtensions(FetcherFactory.class)) {
            String name = factory.getName();
            ClassLoader cl = factory.getClass().getClassLoader();
            boolean isFromPlugin = cl instanceof org.pf4j.PluginClassLoader;

            FetcherFactory existing = factories.get(name);
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
            return PolymorphicObjectMapperFactory.getMapper().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to serialize config to JSON string", e);
        }
    }

    private final PluginManager pluginManager;
    private final Map<String, ExtensionConfig> fetcherConfigs = new ConcurrentHashMap<>();
    private final Map<String, Fetcher> fetcherCache = new ConcurrentHashMap<>();
    private final boolean allowRuntimeModifications;

    private FetcherManager(PluginManager pluginManager, Map<String, ExtensionConfig> fetcherConfigs, boolean allowRuntimeModifications) {
        this.pluginManager = pluginManager;
        this.fetcherConfigs.putAll(fetcherConfigs);
        this.allowRuntimeModifications = allowRuntimeModifications;
    }


    public Fetcher getFetcher(String id) throws IOException, TikaException {
        // Check cache first (fast path, no synchronization)
        Fetcher fetcher = fetcherCache.get(id);
        if (fetcher != null) {
            return fetcher;
        }

        // Check if config exists
        ExtensionConfig config = fetcherConfigs.get(id);
        if (config == null) {
            throw new FetcherNotFoundException(
                    "Can't find fetcher for id=" + id + ". Available: " + fetcherConfigs.keySet());
        }

        // Synchronized block to ensure only one thread builds the fetcher
        synchronized (this) {
            // Double-check in case another thread built it while we were waiting
            fetcher = fetcherCache.get(id);
            if (fetcher != null) {
                return fetcher;
            }

            // Build the fetcher
            try {
                fetcher = buildFetcher(config);
                fetcherCache.put(id, fetcher);
                LOG.debug("Lazily instantiated fetcher: {}", id);
                return fetcher;
            } catch (TikaConfigException e) {
                throw new IOException("Failed to build fetcher: " + id, e);
            }
        }
    }

    /**
     * Builds a fetcher instance from its configuration.
     */
    private Fetcher buildFetcher(ExtensionConfig config) throws TikaConfigException, IOException {
        Map<String, FetcherFactory> factories = getFactories(pluginManager);
        FetcherFactory factory = factories.get(config.name());

        if (factory == null) {
            // This shouldn't happen since we validated in load(), but check anyway
            throw new TikaConfigException(
                    "Unknown fetcher type: " + config.name() + ". Available: " + factories.keySet());
        }

        return factory.buildExtension(config);
    }

    /**
     * Dynamically adds a fetcher configuration at runtime.
     * The fetcher will not be instantiated until it is first requested via {@link #getFetcher(String)}.
     * This allows for dynamic configuration without the overhead of immediate instantiation.
     * <p>
     * This method is only available if the FetcherManager was loaded with
     * {@link #load(PluginManager, TikaJsonConfig, boolean)} with allowRuntimeModifications=true
     * <p>
     * Only authorized/authenticated users should be allowed to modify fetchers. BE CAREFUL.
     *
     * @param config the extension configuration for the fetcher
     * @throws TikaConfigException if the fetcher type is unknown, if a fetcher with the same ID already exists,
     *         or if runtime modifications are not allowed
     * @throws IOException if there is an error accessing the plugin manager
     */
    public synchronized void saveFetcher(ExtensionConfig config) throws TikaConfigException, IOException {
        if (!allowRuntimeModifications) {
            throw new TikaConfigException(
                    "Runtime modifications are not allowed. FetcherManager must be loaded with " +
                    "allowRuntimeModifications=true to use saveFetcher()");
        }

        if (config == null) {
            throw new IllegalArgumentException("ExtensionConfig cannot be null");
        }

        String fetcherId = config.id();
        String typeName = config.name();

        // Check for duplicate ID
        if (fetcherConfigs.containsKey(fetcherId)) {
            throw new TikaConfigException("Fetcher with id '" + fetcherId + "' already exists");
        }

        // Validate that factory exists for this type
        Map<String, FetcherFactory> factories = getFactories(pluginManager);
        if (!factories.containsKey(typeName)) {
            throw new TikaConfigException(
                    "Unknown fetcher type: " + typeName + ". Available: " + factories.keySet());
        }

        // Store config without instantiating
        fetcherConfigs.put(fetcherId, config);
        LOG.debug("Saved fetcher config: id={}, type={}", fetcherId, typeName);
    }

    public Set<String> getSupported() {
        return fetcherConfigs.keySet();
    }

    /**
     * Convenience method that returns a fetcher if only one fetcher
     * is specified in the tika-config file.  If 0 or > 1 fetchers
     * are specified, this throws an IllegalArgumentException.
     * @return the single configured fetcher
     */
    public Fetcher getFetcher() throws IOException, TikaException {
        if (fetcherConfigs.size() > 1) {
            throw new IllegalArgumentException("need to specify 'fetcherId' if > 1 fetchers are" +
                    " available");
        }
        // Get the single fetcher id and use getFetcher(id) for lazy loading
        String fetcherId = fetcherConfigs.keySet().iterator().next();
        return getFetcher(fetcherId);
    }
}
