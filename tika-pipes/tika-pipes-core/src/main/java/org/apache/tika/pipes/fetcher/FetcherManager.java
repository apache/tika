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
package org.apache.tika.pipes.fetcher;

import java.util.Set;
import java.util.stream.Collectors;

import org.pf4j.PluginManager;

import org.apache.tika.config.ConfigBase;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.exception.PipesRuntimeException;
import org.apache.tika.pipes.plugin.TikaPluginManager;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers supporting the same name.
 */
public class FetcherManager extends ConfigBase {
    private final PluginManager pluginManager;

    public FetcherManager() throws TikaConfigException {
        pluginManager = new TikaPluginManager();
    }

    public FetcherManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public static FetcherManager load(PluginManager pluginManager) {
        return new FetcherManager(pluginManager);
    }

    public Fetcher getFetcher(String pluginId) {
        return pluginManager.getExtensions(Fetcher.class, pluginId)
                            .stream()
                            .findFirst()
                            .orElseThrow(() -> new PipesRuntimeException("Could not find Fetcher extension for plugin " + pluginId));
    }

    public Set<String> getSupported() {
        return pluginManager.getExtensions(Fetcher.class)
                            .stream()
                            .map(Fetcher::getPluginId)
                            .collect(Collectors.toSet());
    }

    /**
     * Convenience method that returns a fetcher if only one fetcher
     * is specified in the tika-config file.  If 0 or > 1 fetchers
     * are specified, this throws an IllegalArgumentException.
     * @return
     */
    public Fetcher getFetcher() {
        return pluginManager.getExtensions(Fetcher.class)
                            .stream()
                            .findFirst()
                            .orElseThrow(() -> new PipesRuntimeException("Could not find any instances of the Fetcher extension"));
    }
}
