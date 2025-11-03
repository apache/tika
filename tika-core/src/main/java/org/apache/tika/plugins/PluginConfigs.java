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
import java.util.Map;
import java.util.Optional;

public class PluginConfigs {

    Map<String, PluginConfig> pluginConfigs = new HashMap<>();

    public PluginConfigs() {

    }

    public PluginConfigs(Map<String, PluginConfig> map) {
        pluginConfigs.putAll(map);
    }

    public void add(PluginConfig pluginConfig) {
        if (pluginConfigs.containsKey(pluginConfig.pluginId())) {
            throw new IllegalArgumentException("Can't overwrite existing plugin for id: " + pluginConfig.pluginId());
        }
        pluginConfigs.put(pluginConfig.pluginId(), pluginConfig);
    }

    public Optional<PluginConfig> get(String pluginId) {
        return Optional.ofNullable(pluginConfigs.get(pluginId));
    }

}
