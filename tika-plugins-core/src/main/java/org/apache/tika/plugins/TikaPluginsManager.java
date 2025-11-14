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
package org.apache.tika.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public class TikaPluginsManager {

    public enum PLUGIN_TYPES {
        FETCHERS,
        EMITTERS,
        PIPES_ITERATOR,
        REPORTERS
    }

    public static JsonNode loadRoot(Path p) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(p)) {
            return loadRoot(is);
        }
    }


    public static JsonNode loadRoot(InputStream is) throws IOException {
        return new ObjectMapper().readTree(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
    }
    public static TikaPluginsManager load(InputStream is, String ... items) throws IOException, TikaConfigException {
        return load(is, true, items);
    }

    public static TikaPluginsManager load(InputStream is, boolean throwOnMissing, String ... items) throws IOException, TikaConfigException {
        JsonNode root = loadRoot(is);
        JsonNode plugins = root.get("plugins");
        if (plugins == null) {
            if (throwOnMissing) {
                throw new TikaConfigException("Couldn't find 'plugins' node");
            }
            return new TikaPluginsManager(Map.of(), List.of());
        }

        Map<String, PluginConfigs> pluginConfigsMap = new HashMap<>();
        for (String item : items) {
            pluginConfigsMap.put(item, loadPluginConfigs(item, plugins, throwOnMissing));
        }
        List<String> pluginsPaths = new ArrayList<>();
        if (root.has("pluginsPaths")) {
            JsonNode pluginsPathsNode = root.get("pluginsPaths");
            if (pluginsPathsNode.isArray()) {
                Iterator<JsonNode> elements = pluginsPathsNode.elements();
                while (elements.hasNext()) {
                    JsonNode n = elements.next();
                    pluginsPaths.add(n.asText());
                }
            } else {
                pluginsPaths.add(pluginsPathsNode.asText());
            }
        }
        return new TikaPluginsManager(pluginConfigsMap, pluginsPaths);
    }

    public static TikaPluginsManager load(InputStream is, boolean throwOnMissing, PLUGIN_TYPES ... types) throws IOException, TikaConfigException {
        String[] args = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            args[i] = types[i].name().toLowerCase(Locale.ROOT);
        }
        return load(is, throwOnMissing, args);
    }

    public static PluginConfigs loadPluginConfigs(PLUGIN_TYPES type, JsonNode plugins, boolean throwOnMissing) throws TikaConfigException {
        return loadPluginConfigs(type.name().toLowerCase(Locale.ROOT), plugins, throwOnMissing);
    }

    public static PluginConfigs loadPluginConfigs(String item, JsonNode plugins, boolean throwOnMissing) throws TikaConfigException {
        JsonNode itemNode = plugins.get(item);
        if (itemNode == null) {
            if (throwOnMissing) {
                throw new TikaConfigException("Couldn't find " + item + " under 'plugins'");
            } else {
                return new PluginConfigs();
            }
        }
        PluginConfigs pluginConfigs = new PluginConfigs();
        int configs = 0;
        for (Iterator<String> it = itemNode.fieldNames(); it.hasNext(); ) {
            String id = it.next();
            JsonNode pluginNode = itemNode.get(id);
            if (pluginNode == null) {
                throw new TikaConfigException("Couldn't find node for item=" + item + " id=" + id);
            }
            int cnt = 0;
            for (Iterator<String> pluginIds = pluginNode.fieldNames(); pluginIds.hasNext(); ) {
                String pluginId = pluginIds.next();
                if (++cnt > 1) {
                    throw new TikaConfigException("Can only have one pluginId per id: id=" + id + " pluginId=" + pluginId);
                }
                JsonNode pluginConfigNode = pluginNode.get(pluginId);
                PluginConfig pluginConfig = new PluginConfig(id, pluginId, pluginConfigNode.toString());
                pluginConfigs.add(pluginConfig);
            }
            if (cnt == 0) {
                throw new TikaConfigException("need to have at least one plugin node for " + id);
            }
            configs++;
        }
        if (configs == 0) {
            throw new TikaConfigException("Couldn't find any items for item=" + item);
        }
        return pluginConfigs;
    }

    private final List<String> pluginsPaths;
    private final Map<String, PluginConfigs> pluginConfigsMap;


    public TikaPluginsManager(Map<String, PluginConfigs> pluginConfigsMap, List<String> pluginsPaths) {
        this.pluginConfigsMap = pluginConfigsMap;
        this.pluginsPaths = pluginsPaths;
    }

    public Optional<PluginConfigs> get(PLUGIN_TYPES pluginType) {
        return get(pluginType.name().toLowerCase(Locale.ROOT));
    }

    private Optional<PluginConfigs> get(String lowerCase) {
        return Optional.ofNullable(pluginConfigsMap.get(lowerCase));
    }

    public List<Path> getPluginsPaths() {
        List<Path> ret = new ArrayList<>();
        for (String p : pluginsPaths) {
            ret.add(Paths.get(p));
        }
        return ret;
    }


}
