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
package org.apache.tika.pipes.core.emitter;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.api.emitter.EmitterFactory;
import org.apache.tika.pipes.api.emitter.EmitterNotFoundException;
import org.apache.tika.pipes.core.AbstractComponentManager;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Utility class that will apply the appropriate emitter
 * to the emitterString based on the prefix.
 * <p>
 * This does not allow multiple emitters supporting the same prefix.
 * Emitters are instantiated lazily on first use.
 */
public class EmitterManager extends AbstractComponentManager<Emitter, EmitterFactory> {

    private static final String CONFIG_KEY = "emitters";

    /**
     * Loads an EmitterManager without allowing runtime modifications.
     * Use {@link #load(PluginManager, TikaJsonConfig, boolean)} to enable runtime emitter additions.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @return an EmitterManager that does not allow runtime modifications
     */
    public static EmitterManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig)
            throws IOException, TikaConfigException {
        return load(pluginManager, tikaJsonConfig, false);
    }

    /**
     * Loads an EmitterManager with optional support for runtime modifications.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @param allowRuntimeModifications if true, allows calling {@link #saveEmitter(ExtensionConfig)}
     *                                  to add emitters at runtime
     * @return an EmitterManager
     */
    public static EmitterManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig,
                                     boolean allowRuntimeModifications)
            throws IOException, TikaConfigException {
        return load(pluginManager, tikaJsonConfig, allowRuntimeModifications, null);
    }

    /**
     * Loads an EmitterManager with optional support for runtime modifications and a custom config store.
     *
     * @param pluginManager the plugin manager
     * @param tikaJsonConfig the configuration
     * @param allowRuntimeModifications if true, allows calling {@link #saveEmitter(ExtensionConfig)}
     *                                  to add emitters at runtime
     * @param configStore custom config store implementation, or null to use default in-memory store
     * @return an EmitterManager
     */
    public static EmitterManager load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig,
                                     boolean allowRuntimeModifications,
                                     org.apache.tika.pipes.core.config.ConfigStore configStore)
            throws IOException, TikaConfigException {
        EmitterManager manager = new EmitterManager(pluginManager, allowRuntimeModifications);
        JsonNode emittersNode = tikaJsonConfig.getRootNode().get(CONFIG_KEY);

        // Validate configuration and collect emitter configs without instantiating
        Map<String, ExtensionConfig> configs = manager.validateAndCollectConfigs(pluginManager, emittersNode);

        if (configStore != null) {
            return new EmitterManager(pluginManager, configs, allowRuntimeModifications, configStore);
        }
        return new EmitterManager(pluginManager, configs, allowRuntimeModifications);
    }

    private EmitterManager(PluginManager pluginManager, boolean allowRuntimeModifications) {
        super(pluginManager, Map.of(), allowRuntimeModifications);
    }

    private EmitterManager(PluginManager pluginManager, Map<String, ExtensionConfig> emitterConfigs,
                          boolean allowRuntimeModifications) {
        super(pluginManager, emitterConfigs, allowRuntimeModifications);
    }

    private EmitterManager(PluginManager pluginManager, Map<String, ExtensionConfig> emitterConfigs,
                          boolean allowRuntimeModifications,
                          org.apache.tika.pipes.core.config.ConfigStore configStore) {
        super(pluginManager, emitterConfigs, allowRuntimeModifications, configStore);
    }

    @Override
    protected String getConfigKey() {
        return CONFIG_KEY;
    }

    @Override
    protected Class<EmitterFactory> getFactoryClass() {
        return EmitterFactory.class;
    }

    @Override
    protected String getComponentName() {
        return "emitter";
    }

    @Override
    protected TikaException createNotFoundException(String message) {
        return new EmitterNotFoundException(message);
    }

    /**
     * Gets an emitter by ID, lazily instantiating it if needed.
     *
     * @param emitterName the emitter ID
     * @return the emitter
     * @throws EmitterNotFoundException if no emitter with the given ID exists
     * @throws IOException if there's an error building the emitter
     * @throws TikaException if there's a configuration error
     */
    public Emitter getEmitter(String emitterName) throws IOException, TikaException {
        return getComponent(emitterName);
    }

    /**
     * Convenience method that returns an emitter if only one emitter
     * is configured. If 0 or > 1 emitters are configured, this throws an IllegalArgumentException.
     *
     * @return the single configured emitter
     * @throws IOException if there's an error building the emitter
     * @throws TikaException if there's a configuration error
     */
    public Emitter getEmitter() throws IOException, TikaException {
        return getComponent();
    }

    /**
     * Dynamically adds an emitter configuration at runtime.
     * The emitter will not be instantiated until it is first requested via {@link #getEmitter(String)}.
     * This allows for dynamic configuration without the overhead of immediate instantiation.
     * <p>
     * This method is only available if the EmitterManager was loaded with
     * {@link #load(PluginManager, TikaJsonConfig, boolean)} with allowRuntimeModifications=true.
     * <p>
     * Only authorized/authenticated users should be allowed to modify emitters. BE CAREFUL.
     *
     * @param config the extension configuration for the emitter
     * @throws TikaConfigException if the emitter type is unknown, if an emitter with the same ID
     *                             already exists, or if runtime modifications are not allowed
     * @throws IOException if there is an error accessing the plugin manager
     */
    public void saveEmitter(ExtensionConfig config) throws TikaConfigException, IOException {
        saveComponent(config);
    }
}
