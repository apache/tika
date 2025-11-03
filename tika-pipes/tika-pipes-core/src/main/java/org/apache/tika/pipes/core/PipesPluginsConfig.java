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
import java.util.Iterator;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.PluginConfigs;


public class PipesPluginsConfig {

    public static PipesPluginsConfig load(InputStream is) throws IOException {
        JsonNode root = new ObjectMapper().readTree(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
        PluginConfigs fetchers = null;
        PluginConfigs emitters = null;
        PluginConfigs iterators = null;
        PluginConfigs reporters = null;

        JsonNode plugins = root.get("plugins");
        if (plugins.has("fetchers")) {
            fetchers = load(plugins.get("fetchers"));
        }
        if (plugins.has("emitters")) {
            emitters = load(plugins.get("emitters"));
        }
        if (plugins.has("iterators")) {
            iterators = load(plugins.get("iterators"));
        }
        if (plugins.has("reporters")) {
            reporters = load(plugins.get("reporters"));
        }

        Path pluginsDir = null;
        if (plugins.has("pf4j.pluginsDir")) {
            pluginsDir = Paths.get(plugins.get("pf4j.pluginsDir").asText());
        }
        return new PipesPluginsConfig(fetchers, emitters, iterators, reporters, pluginsDir);
    }

    private static PluginConfigs load(JsonNode pluginsNode) {
        PluginConfigs manager = new PluginConfigs();
        Iterator<String> it = pluginsNode.fieldNames();
        manager = new PluginConfigs();
        while (it.hasNext()) {
            String pluginId = it.next();
            JsonNode jsonConfig = pluginsNode.get(pluginId);
            manager.add(new PluginConfig(pluginId, jsonConfig.toString()));
        }
        return manager;
    }

    private final PluginConfigs fetchers;
    private final PluginConfigs emitters;
    private final PluginConfigs iterators;
    private final PluginConfigs reporters;


    private final Path pluginsDir;

    public PipesPluginsConfig(PluginConfigs fetchers, PluginConfigs emitters,
                              PluginConfigs iterators, PluginConfigs reporters, Path pluginsDir) {
        this.fetchers = fetchers;
        this.emitters = emitters;
        this.iterators = iterators;
        this.reporters = reporters;
        this.pluginsDir = pluginsDir;
    }

    public Optional<PluginConfig> getFetcherConfig(String pluginId) {
        if (fetchers == null) {
            throw new IllegalArgumentException("fetchers element was not loaded");
        }
        return fetchers.get(pluginId);
    }

    public Optional<PluginConfig> getEmitterConfig(String pluginId) {
        if (emitters == null) {
            throw new IllegalArgumentException("emitters element was not loaded");
        }
        return emitters.get(pluginId);
    }

    public Optional<PluginConfig> getIteratorConfig(String pluginId) {
        if (iterators == null) {
            throw new IllegalArgumentException("iterators element was not loaded");
        }
        return iterators.get(pluginId);
    }

    public Optional<PluginConfig> getReporterConfig(String pluginId) {
        if (reporters == null) {
            throw new IllegalArgumentException("reporters element was not loaded");
        }
        return reporters.get(pluginId);
    }

    public Optional<Path> getPluginsDir() {
        return Optional.ofNullable(pluginsDir);
    }
}
