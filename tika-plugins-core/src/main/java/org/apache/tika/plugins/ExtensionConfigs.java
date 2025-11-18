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

public class ExtensionConfigs {

    Map<String, ExtensionConfig> extensionConfigsById = new HashMap<>();
    Map<String, Set<String>> extensionIdsToId = new HashMap<>();

    public ExtensionConfigs() {

    }

    public ExtensionConfigs(Map<String, ExtensionConfig> map) {
        for (ExtensionConfig c : map.values()) {
            add(c);
        }
    }

    public void add(ExtensionConfig pluginConfig) {
        if (extensionConfigsById.containsKey(pluginConfig.id())) {
            throw new IllegalArgumentException("Can't overwrite existing extension config for id: " + pluginConfig.extensionId());
        }
        extensionConfigsById.put(pluginConfig.id(), pluginConfig);
        extensionIdsToId.computeIfAbsent(pluginConfig.extensionId(), k -> new HashSet<>()).add(pluginConfig.id());
    }

    public Optional<ExtensionConfig> getById(String id) {
        return Optional.ofNullable(extensionConfigsById.get(id));
    }

    public Set<String> ids() {
        return extensionConfigsById.keySet();
    }

    public Set<String> getIdsByExtensionId(String extensionId) {
        return extensionIdsToId.getOrDefault(extensionId, Set.of());
    }

    public Set<String> getExtensionIds() {
        return extensionIdsToId.keySet();
    }
}
