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
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.pipes.api.fetcher.FetcherNotFoundException;
import org.apache.tika.pipes.core.AbstractComponentManager;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId.
 * Fetchers are instantiated lazily on first use.
 */
public class FetcherManager extends AbstractComponentManager<Fetcher, FetcherFactory> {

    private static final String CONFIG_KEY = "fetchers";

    /**
     * Loads a FetcherManager without allowing runtime modifications.
     * Use {@link #load(PluginManager, TikaJsonConfig, boolean)} to enable runtime fetcher additions.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @return a FetcherManager that does not allow runtime modifications
     */
    public static FetcherManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig)
            throws TikaConfigException, IOException {
        return load(pluginManager, tikaJsonConfig, false);
    }

    /**
     * Loads a FetcherManager with optional support for runtime modifications.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @param allowRuntimeModifications if true, allows calling {@link #saveFetcher(ExtensionConfig)}
     *                                  to add fetchers at runtime
     * @return a FetcherManager
     */
    public static FetcherManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig,
                                     boolean allowRuntimeModifications)
            throws TikaConfigException, IOException {
        FetcherManager manager = new FetcherManager(pluginManager, allowRuntimeModifications);
        JsonNode fetchersNode = tikaJsonConfig.getRootNode().get(CONFIG_KEY);

        // Validate configuration and collect fetcher configs without instantiating
        Map<String, ExtensionConfig> configs = manager.validateAndCollectConfigs(pluginManager, fetchersNode);

        return new FetcherManager(pluginManager, configs, allowRuntimeModifications);
    }

    private FetcherManager(PluginManager pluginManager, boolean allowRuntimeModifications) {
        super(pluginManager, Map.of(), allowRuntimeModifications);
    }

    private FetcherManager(PluginManager pluginManager, Map<String, ExtensionConfig> fetcherConfigs,
                          boolean allowRuntimeModifications) {
        super(pluginManager, fetcherConfigs, allowRuntimeModifications);
    }

    @Override
    protected String getConfigKey() {
        return CONFIG_KEY;
    }

    @Override
    protected Class<FetcherFactory> getFactoryClass() {
        return FetcherFactory.class;
    }

    @Override
    protected String getComponentName() {
        return "fetcher";
    }

    @Override
    protected TikaException createNotFoundException(String message) {
        return new FetcherNotFoundException(message);
    }

    /**
     * Gets a fetcher by ID, lazily instantiating it if needed.
     *
     * @param id the fetcher ID
     * @return the fetcher
     * @throws FetcherNotFoundException if no fetcher with the given ID exists
     * @throws IOException if there's an error building the fetcher
     * @throws TikaException if there's a configuration error
     */
    public Fetcher getFetcher(String id) throws IOException, TikaException {
        return getComponent(id);
    }

    /**
     * Convenience method that returns a fetcher if only one fetcher
     * is configured. If 0 or > 1 fetchers are configured, this throws an IllegalArgumentException.
     *
     * @return the single configured fetcher
     * @throws IOException if there's an error building the fetcher
     * @throws TikaException if there's a configuration error
     */
    public Fetcher getFetcher() throws IOException, TikaException {
        return getComponent();
    }

    /**
     * Dynamically adds a fetcher configuration at runtime.
     * The fetcher will not be instantiated until it is first requested via {@link #getFetcher(String)}.
     * This allows for dynamic configuration without the overhead of immediate instantiation.
     * <p>
     * This method is only available if the FetcherManager was loaded with
     * {@link #load(PluginManager, TikaJsonConfig, boolean)} with allowRuntimeModifications=true.
     * <p>
     * Only authorized/authenticated users should be allowed to modify fetchers. BE CAREFUL.
     *
     * @param config the extension configuration for the fetcher
     * @throws TikaConfigException if the fetcher type is unknown, if a fetcher with the same ID
     *                             already exists, or if runtime modifications are not allowed
     * @throws IOException if there is an error accessing the plugin manager
     */
    public void saveFetcher(ExtensionConfig config) throws TikaConfigException, IOException {
        saveComponent(config);
    }
}
