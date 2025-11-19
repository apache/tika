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
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public class TikaConfigs {

    public static final String PLUGINS_PATHS_KEY = "pluginsPaths";

    public enum EXTENSION_TYPES {
        FETCHERS,
        EMITTERS,
        PIPES_ITERATOR,
        REPORTERS
    }

    private static final Set<String> CONTROLLED_KEYS = Set.of(PLUGINS_PATHS_KEY);

    public static JsonNode loadRoot(Path p) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(p)) {
            return loadRoot(is);
        }
    }

    public static JsonNode loadRoot(InputStream is) throws IOException {
        return new ObjectMapper().readTree(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)));
    }

    public static TikaConfigs load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }

    public static TikaConfigs load(InputStream is) throws IOException, TikaConfigException {
        JsonNode root = loadRoot(is);
        ExtensionConfigs extensionConfigs = new ExtensionConfigs();
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            String id = it.next();
            if (CONTROLLED_KEYS.contains(id)) {
                continue;
            }
            loadConfigs(id, root.get(id), extensionConfigs);
        }
        //now handle controlled keys
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
        return new TikaConfigs(extensionConfigs, pluginsPaths);
    }


    public static void loadConfigs(String id, JsonNode extensionNode, ExtensionConfigs extensionConfigs) throws TikaConfigException {
        if (extensionNode == null) {
            return;
        }
        int configs = 0;
        int cnt = 0;
        for (Iterator<String> extensionIds = extensionNode.fieldNames(); extensionIds.hasNext(); ) {
            String extensionId = extensionIds.next();
            if (++cnt > 1) {
                throw new TikaConfigException("Can only have one extensionId per id: id=" + id + " extensionId=" + extensionId);
            }
            JsonNode pluginConfigNode = extensionNode.get(extensionId);
            ExtensionConfig pluginConfig = new ExtensionConfig(id, extensionId, pluginConfigNode.toString());
            extensionConfigs.add(pluginConfig);
        }
        if (cnt == 0) {
            throw new TikaConfigException("need to have at least one plugin node for " + id);
        }
        configs++;

    }

    private final List<String> pluginRoots;
    private final ExtensionConfigs extensionConfigs;


    public TikaConfigs(ExtensionConfigs extensionConfigs, List<String> pluginRoots) {
        this.extensionConfigs = extensionConfigs;
        this.pluginRoots = pluginRoots;
    }

    public List<Path> getPluginRoots() {
        List<Path> ret = new ArrayList<>();
        for (String p : pluginRoots) {
            ret.add(Paths.get(p));
        }
        return ret;
    }

    public ExtensionConfigs getExtensionConfigs() {
        return extensionConfigs;
    }
}
