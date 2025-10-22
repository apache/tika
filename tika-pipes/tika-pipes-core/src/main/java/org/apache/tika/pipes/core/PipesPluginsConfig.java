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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.pipes.api.fetcher.FetcherConfig;
import org.apache.tika.pipes.core.fetcher.config.FetcherConfigImpl;

public class PipesPluginsConfig {

    public static PipesPluginsConfig load(InputStream is) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
        JsonNode plugins = root.get("pipesPluginsConfig");
        Map<String, FetcherConfig> fetcherMap = new HashMap<>();
        if (plugins.has("fetchers")) {
            JsonNode fetchers = plugins.get("fetchers");
            Iterator<String> it = fetchers.fieldNames();
            while (it.hasNext()) {
                String pluginId = it.next();
                JsonNode fetcherConfig = fetchers.get(pluginId);
                fetcherMap.put(pluginId, new FetcherConfigImpl(pluginId, fetcherConfig.toString()));
            }
        }
        Path pluginsDir = null;
        if (plugins.has("pf4j.pluginsDir")) {
            pluginsDir = Paths.get(plugins.get("pf4j.pluginsDir").asText());
        }
        return new PipesPluginsConfig(fetcherMap, pluginsDir);
    }

    private final Map<String, FetcherConfig> fetcherMap;
    private final Path pluginsDir;
    private PipesPluginsConfig(Map<String, FetcherConfig> fetcherMap, Path pluginsDir) {
        this.fetcherMap = fetcherMap;
        this.pluginsDir = pluginsDir;
    }

    public Optional<FetcherConfig> getFetcherConfig(String pluginId) {
        return Optional.ofNullable(fetcherMap.get(pluginId));
    }

    public Optional<Path> getPluginsDir() {
        return Optional.ofNullable(pluginsDir);
    }
}
