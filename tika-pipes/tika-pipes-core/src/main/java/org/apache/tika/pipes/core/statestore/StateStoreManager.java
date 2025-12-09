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
package org.apache.tika.pipes.core.statestore;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.statestore.StateStore;
import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.pipes.api.statestore.StateStoreFactory;
import org.apache.tika.plugins.PluginComponentLoader;

/**
 * Manager for loading StateStore implementations from configuration.
 * This follows the same pattern as FetcherManager and EmitterManager.
 */
public class StateStoreManager {

    public static final String CONFIG_KEY = "stateStore";
    private static final Logger LOG = LoggerFactory.getLogger(StateStoreManager.class);

    /**
     * Load a StateStore from the given configuration.
     * If no state store is configured, returns an InMemoryStateStore by default.
     *
     * @param pluginManager the plugin manager for loading factories
     * @param tikaJsonConfig the Tika configuration
     * @return a configured StateStore instance
     * @throws IOException if loading fails
     * @throws TikaConfigException if configuration is invalid
     */
    public static StateStore load(PluginManager pluginManager, TikaJsonConfig tikaJsonConfig)
            throws IOException, TikaConfigException {
        JsonNode stateStoreNode = tikaJsonConfig.getRootNode().get(CONFIG_KEY);

        if (stateStoreNode == null) {
            LOG.info("No state store configured, using default InMemoryStateStore");
            InMemoryStateStore store = new InMemoryStateStore();
            try {
                store.initialize(null);
            } catch (StateStoreException e) {
                throw new TikaConfigException("Failed to initialize default InMemoryStateStore", e);
            }
            return store;
        }

        try {
            Map<String, StateStore> stores =
                PluginComponentLoader.loadInstances(pluginManager,
                                                   StateStoreFactory.class,
                                                   stateStoreNode);

            if (stores.isEmpty()) {
                throw new TikaConfigException("No state store instances loaded from configuration");
            }

            if (stores.size() > 1) {
                LOG.warn("Multiple state stores configured, using the first one: {}",
                        stores.keySet().iterator().next());
            }

            // Return the first (and typically only) state store
            StateStore store = stores.values().iterator().next();
            LOG.info("Loaded state store: {}", store.getClass().getName());
            return store;

        } catch (Exception e) {
            throw new TikaConfigException("Failed to load state store from configuration", e);
        }
    }

    /**
     * Create a default in-memory state store.
     * This is useful for testing and single-node deployments.
     *
     * @return a new InMemoryStateStore instance
     * @throws TikaConfigException if initialization fails
     */
    public static StateStore createDefault() throws TikaConfigException {
        InMemoryStateStore store = new InMemoryStateStore();
        try {
            store.initialize(null);
        } catch (StateStoreException e) {
            throw new TikaConfigException("Failed to initialize default InMemoryStateStore", e);
        }
        return store;
    }
}
