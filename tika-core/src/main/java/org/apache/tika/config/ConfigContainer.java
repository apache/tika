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
package org.apache.tika.config;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This is effectively a copy of ParseContext that is to be used when serialization
 * intervenes between the caller and the processor as in tika-pipes, and elsewhere.
 *
 * The goal of this is to delegate deserialization to the consumers/receivers.
 * <p>
 * Stores configuration as named JSON strings, allowing parsers, fetchers, emitters,
 * and other components to look up their config by friendly name (e.g., "pdf-parser",
 * "fs-fetcher-1") and deserialize it on-demand.
 */
public class ConfigContainer implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> configs = new HashMap<>();

    public <T> void set(Class<T> key, String value) {
        if (value != null) {
            String keyName = key.getName();
            if (configs.containsKey(keyName)) {
                throw new IllegalArgumentException(
                    "Config key '" + keyName + "' already exists. Cannot overwrite existing configuration.");
            }
            configs.put(keyName, value);
        }
    }

    public void set(String name, String value) {
        if (value != null) {
            if (configs.containsKey(name)) {
                throw new IllegalArgumentException(
                    "Config key '" + name + "' already exists. Cannot overwrite existing configuration.");
            }
            configs.put(name, value);
        }
    }

    public <T> Optional<JsonConfig> get(Class<T> key) {
        String json = configs.get(key.getName());
        return json == null ? Optional.empty() : Optional.of(() -> json);
    }

    public Optional<JsonConfig> get(String key) {
        String json = configs.get(key);
        return json == null ? Optional.empty() : Optional.of(() -> json);
    }

    public JsonConfig get(String key, String defaultMissing) {
        String val = configs.get(key);
        if (val == null) {
            val = defaultMissing;
        }
        final String jsonValue = val;
        return () -> jsonValue;
    }

    public Set<String> getKeys() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    public boolean isEmpty() {
        return configs.isEmpty();
    }
}
