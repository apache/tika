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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.api.fetcher.Fetcher;

/**
 * Utility class to hold multiple fetchers.
 * <p>
 * This forbids multiple fetchers supporting the same name.
 */
public class FetcherManager {

    private static final Logger LOG = LoggerFactory.getLogger(FetcherManager.class);

    public static FetcherManager load() throws IOException, TikaConfigException {
        PluginManager pluginManager = new DefaultPluginManager();
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        List<Path> pluginRoots = pluginManager.getPluginsRoots();
        Map<String, Fetcher> fetcherMap = new HashMap<>();
        List<Fetcher> fetchers = pluginManager.getExtensions(Fetcher.class);
        System.out.println("HERE " + fetchers.size());
        //if (LOG.isDebugEnabled()) {
            loadDebug(pluginRoots, fetchers);
        //}
        for (Fetcher fetcher : pluginManager.getExtensions(Fetcher.class)) {
            Path p = findConfig(pluginRoots, fetcher.getName());
            if (p == null) {
                LOG.warn("couldn't find config for {}", fetcher.getName());
            } else {
                try (InputStream is = Files.newInputStream(p)) {
                    fetcher.loadDefaultConfig(is);
                }
            }
            fetcherMap.put(fetcher.getName(), fetcher);
        }
        return new FetcherManager(fetcherMap);

    }

    private static void loadDebug(List<Path> pluginRoots, List<Fetcher> fetchers) {
        for (Path p : pluginRoots) {
            LOG.warn("plugin root: {}", p.toAbsolutePath());
        }
        LOG.warn("loaded {} fetchers", fetchers.size());
        for (Fetcher f : fetchers) {
            LOG.warn("fetcher name={} class={}", f.getName(), f.getClass());
        }
    }

    private static Path findConfig(List<Path> pluginRoots, String name) {
        String target = name + ".json";
        for (Path p : pluginRoots) {
            Path candidate = p.toAbsolutePath().resolve(target);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private final Map<String, Fetcher> fetcherMap = new ConcurrentHashMap<>();

    private FetcherManager(Map<String, Fetcher> fetcherMap) throws TikaConfigException {
        this.fetcherMap.putAll(fetcherMap);
    }


    public Fetcher getFetcher(String fetcherName) throws IOException, TikaException {
        Fetcher fetcher = fetcherMap.get(fetcherName);
        if (fetcher == null) {
            throw new IllegalArgumentException(
                    "Can't find fetcher for fetcherName: " + fetcherName + ". I've loaded: " +
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
        if (fetcherMap.size() == 0) {
            throw new IllegalArgumentException("fetchers size must == 1 for the no arg call");
        }
        if (fetcherMap.size() > 1) {
            throw new IllegalArgumentException("need to specify 'fetcherName' if > 1 fetchers are" +
                    " available");
        }
        for (Fetcher fetcher : fetcherMap.values()) {
            return fetcher;
        }
        //this should be unreachable?!
        throw new IllegalArgumentException("fetchers size must == 0");
    }
}
