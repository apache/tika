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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.api.fetcher.FetcherFactory;
import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.PluginConfigs;
import org.apache.tika.plugins.TikaPluginsManager;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers with the same pluginId
 */
public class FetcherManager {

    private static final Logger LOG = LoggerFactory.getLogger(FetcherManager.class);

    public static FetcherManager load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    public static FetcherManager load(InputStream is) throws IOException, TikaConfigException {
        //this will throw a TikaConfigException if "fetchers" is not loaded
        TikaPluginsManager tikaPluginsManager = TikaPluginsManager.load(is, true, TikaPluginsManager.PLUGIN_TYPES.FETCHERS);
        return load(tikaPluginsManager);
    }

    public static FetcherManager load(TikaPluginsManager tikaPluginsManager) throws IOException, TikaConfigException {
        Optional<PluginConfigs> fetcherPluginConfigsOpt = tikaPluginsManager.get(TikaPluginsManager.PLUGIN_TYPES.FETCHERS);
        if (fetcherPluginConfigsOpt.isEmpty()) {
            throw new TikaConfigException("Forgot to load 'fetchers'?");
        }
        PluginConfigs fetcherPluginConfigs = fetcherPluginConfigsOpt.get();
        PluginManager pluginManager = null;
        if (! tikaPluginsManager.getPluginsPaths().isEmpty()) {
            LOG.warn("LOADING WITH PLUGINS PATHS: {}", tikaPluginsManager.getPluginsPaths());
            pluginManager = new DefaultPluginManager(tikaPluginsManager.getPluginsPaths());
        } else {
            LOG.warn("NOT LOADING WITH PLUGINS PATHS: {}", tikaPluginsManager.getPluginsPaths());
            pluginManager = new DefaultPluginManager();
        }
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        Map<String, Fetcher> fetcherMap = new HashMap<>();
        for (FetcherFactory fetcherFactory : pluginManager.getExtensions(FetcherFactory.class)) {
            LOG.warn("Pf4j loaded plugin: " + fetcherFactory.getClass());
            PluginWrapper pluginWrapper = pluginManager.whichPlugin(fetcherFactory.getClass());
            if (pluginWrapper == null) {
                LOG.warn("Couldn't find plugin wrapper for class={}", fetcherFactory.getClass());
                continue;
            }
            String pluginId = pluginWrapper.getPluginId();
            Set<String> ids = fetcherPluginConfigs.getIdsByPluginId(pluginId);
            if (ids.isEmpty()) {
                LOG.warn("Couldn't find config for class={} pluginId={}. Skipping", fetcherFactory.getClass(), pluginId);
            }
            for (String id : ids) {
                Optional<PluginConfig> pluginConfigOpt = fetcherPluginConfigs.getById(id);
                if (pluginConfigOpt.isEmpty()) {
                    LOG.warn("Couldn't find config for id={}", id);
                } else {
                    PluginConfig pluginConfig = pluginConfigOpt.get();
                    Fetcher fetcher = fetcherFactory.buildPlugin(pluginConfig);
                    fetcherMap.put(pluginConfig.id(), fetcher);
                }
            }
        }
        return new FetcherManager(fetcherMap);
    }

    private final Map<String, Fetcher> fetcherMap = new ConcurrentHashMap<>();

    private FetcherManager(Map<String, Fetcher> fetcherMap) throws TikaConfigException {
        this.fetcherMap.putAll(fetcherMap);
    }


    public Fetcher getFetcher(String id) throws IOException, TikaException {
        Fetcher fetcher = fetcherMap.get(id);
        if (fetcher == null) {
            throw new IllegalArgumentException(
                    "Can't find fetcher for id=" + id + ". I've loaded: " +
                            fetcherMap.keySet());
        }
        return fetcher;
    }

    public Set<String> getSupported() {
        return fetcherMap.keySet();
    }

    /**
     * Convenience method that returns a fetcher if only one fetcher
     * is specified in the tika-config file.  If 0 or > 1 fetchers
     * are specified, this throws an IllegalArgumentException.
     * @return
     */
    public Fetcher getFetcher() {
        if (fetcherMap.isEmpty()) {
            throw new IllegalArgumentException("fetchers size must == 1 for the no arg call");
        }
        if (fetcherMap.size() > 1) {
            throw new IllegalArgumentException("need to specify 'fetcherId' if > 1 fetchers are" +
                    " available");
        }
        for (Fetcher fetcher : fetcherMap.values()) {
            return fetcher;
        }
        //this should be unreachable?!
        throw new IllegalArgumentException("fetchers size must == 0");
    }
}
