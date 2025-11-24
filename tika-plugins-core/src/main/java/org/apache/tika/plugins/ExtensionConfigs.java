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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ExtensionConfigs {

    Map<String, ExtensionConfig> idToConfig = new HashMap<>();
    Map<String, List<ExtensionConfig>> extensionIdsToConfig = new HashMap<>();

    public ExtensionConfigs() {

    }

    public ExtensionConfigs(Map<String, ExtensionConfig> map) {
        for (ExtensionConfig c : map.values()) {
            add(c);
        }
    }

    public void add(ExtensionConfig extensionConfig) {
        if (idToConfig.containsKey(extensionConfig.id())) {
            throw new IllegalArgumentException("Can't overwrite existing extension config for extensionName: " + extensionConfig.name());
        }
        idToConfig.put(extensionConfig.id(), extensionConfig);
        extensionIdsToConfig
                .computeIfAbsent(extensionConfig.name(), k -> new ArrayList<>()).add(extensionConfig);
    }

    public Optional<ExtensionConfig> getById(String id) {
        return Optional.ofNullable(idToConfig.get(id));
    }

    public List<ExtensionConfig> getByExtensionName(String extensionName) {
        List<ExtensionConfig> configs = extensionIdsToConfig.get(extensionName);
        if (configs == null) {
            return List.of();
        }
        return configs;
    }

    public Set<String> ids() {
        return idToConfig.keySet();
    }

}
