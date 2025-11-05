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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class PluginConfigs {

    Map<String, PluginConfig> pluginConfigsById = new HashMap<>();
    Map<String, Set<String>> pluginIdsToIds = new HashMap<>();

    public PluginConfigs() {

    }

    public PluginConfigs(Map<String, PluginConfig> map) {
        for (PluginConfig c : map.values()) {
            add(c);
        }
    }

    public void add(PluginConfig pluginConfig) {
        if (pluginConfigsById.containsKey(pluginConfig.id())) {
            throw new IllegalArgumentException("Can't overwrite existing plugin for id: " + pluginConfig.factoryPluginId());
        }
        pluginConfigsById.put(pluginConfig.id(), pluginConfig);
        pluginIdsToIds.computeIfAbsent(pluginConfig.factoryPluginId(), k -> new HashSet<>()).add(pluginConfig.id());
    }

    public Optional<PluginConfig> getById(String id) {
        return Optional.ofNullable(pluginConfigsById.get(id));
    }

    public Set<String> ids() {
        return pluginConfigsById.keySet();
    }

    public Set<String> getIdsByPluginId(String pluginId) {
        return pluginIdsToIds.getOrDefault(pluginId, Set.of());
    }
}
