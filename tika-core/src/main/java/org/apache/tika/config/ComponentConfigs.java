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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Container for component-specific JSON configuration strings.
 * <p>
 * This holds configuration for fetchers, emitters, and other pipeline
 * components as JSON strings, keyed by component name. These configurations
 * are separate from parser-specific configuration objects (HandlerConfig,
 * MetadataFilter, etc.) which are managed in ParseContext.
 * <p>
 * Example usage:
 * <pre>
 * ComponentConfigs configs = new ComponentConfigs();
 * configs.set("my-fetcher", "{\"basePath\": \"/data\", \"timeout\": 5000}");
 * configs.set("my-emitter", "{\"format\": \"json\", \"prettyPrint\": true}");
 *
 * Optional&lt;String&gt; fetcherConfig = configs.get("my-fetcher");
 * </pre>
 */
public class ComponentConfigs implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final ComponentConfigs EMPTY = new ComponentConfigs(Collections.emptyMap());

    private final Map<String, String> configs;

    public ComponentConfigs() {
        this.configs = new HashMap<>();
    }

    public ComponentConfigs(Map<String, String> configs) {
        this.configs = new HashMap<>(configs);
    }

    /**
     * Sets a component configuration.
     *
     * @param componentName the component name (e.g., "my-fetcher", "my-emitter")
     * @param jsonConfig the JSON configuration string
     */
    public void set(String componentName, String jsonConfig) {
        if (componentName != null && jsonConfig != null) {
            configs.put(componentName, jsonConfig);
        }
    }

    /**
     * Gets a component configuration.
     *
     * @param componentName the component name
     * @return optional containing the JSON config string, or empty if not found
     */
    public Optional<String> get(String componentName) {
        return Optional.ofNullable(configs.get(componentName));
    }

    /**
     * Gets a component configuration with a default value.
     *
     * @param componentName the component name
     * @param defaultValue the default value if not found
     * @return the JSON config string or default value
     */
    public String get(String componentName, String defaultValue) {
        return configs.getOrDefault(componentName, defaultValue);
    }

    /**
     * Gets all component names that have configurations.
     *
     * @return unmodifiable set of component names
     */
    public Set<String> getComponentNames() {
        return Collections.unmodifiableSet(configs.keySet());
    }

    /**
     * Gets all configurations as an unmodifiable map.
     *
     * @return unmodifiable map of component name to JSON config
     */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(configs);
    }

    /**
     * Checks if this container is empty.
     *
     * @return true if no configurations are present
     */
    public boolean isEmpty() {
        return configs.isEmpty();
    }

    /**
     * Gets the number of configurations.
     *
     * @return the number of component configurations
     */
    public int size() {
        return configs.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ComponentConfigs that = (ComponentConfigs) o;
        return Objects.equals(configs, that.configs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configs);
    }

    @Override
    public String toString() {
        return "ComponentConfigs{" + "configs=" + configs + '}';
    }
}
