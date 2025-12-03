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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;

/**
 * Loads and validates Tika pipes/plugin configuration from JSON.
 * <p>
 * This class validates pipes-specific configuration keys and delegates to
 * {@link TikaJsonConfig} for parsing. Core Tika keys (parsers, detectors, etc.)
 * are ignored by this validator - they are handled by TikaLoader.
 */
public class TikaConfigs {

    /**
     * Pipes-specific configuration keys.
     */
    private static final Set<String> PIPES_KEYS = Set.of(
            "fetchers",
            "emitters",
            "pipes-iterator",
            "pipes-reporters",
            "async",
            "plugin-roots"
    );

    /**
     * Core Tika configuration keys (handled by TikaLoader, not validated here).
     */
    private static final Set<String> CORE_TIKA_KEYS = Set.of(
            "parsers",
            "detectors",
            "encoding-detectors",
            "encodingDetectors",
            "metadata-filters",
            "metadataFilters",
            "renderers",
            "translators",
            "auto-detect-parser-config",
            "autoDetectParserConfig",
            "server"
    );

    private final TikaJsonConfig tikaJsonConfig;

    /**
     * Loads pipes configuration from a pre-parsed TikaJsonConfig.
     * This is the preferred method when sharing configuration across
     * core Tika and pipes components.
     *
     * @param tikaJsonConfig the pre-parsed JSON configuration
     * @return the pipes configuration
     * @throws TikaConfigException if validation fails
     */
    public static TikaConfigs load(TikaJsonConfig tikaJsonConfig) throws TikaConfigException {
        TikaConfigs configs = new TikaConfigs(tikaJsonConfig);
        configs.validatePipesKeys();
        return configs;
    }

    /**
     * Loads pipes configuration from a file.
     * For backwards compatibility - prefer {@link #load(TikaJsonConfig)} when possible.
     *
     * @param path the path to the JSON configuration file
     * @return the pipes configuration
     * @throws IOException if reading fails
     * @throws TikaConfigException if validation fails
     */
    public static TikaConfigs load(Path path) throws IOException, TikaConfigException {
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(path);
        return load(tikaJsonConfig);
    }

    private TikaConfigs(TikaJsonConfig tikaJsonConfig) {
        this.tikaJsonConfig = tikaJsonConfig;
    }

    /**
     * Gets the underlying TikaJsonConfig.
     *
     * @return the TikaJsonConfig
     */
    public TikaJsonConfig getTikaJsonConfig() {
        return tikaJsonConfig;
    }

    /**
     * Deserializes a configuration value for the given key.
     *
     * @param clazz the target class
     * @param key the configuration key
     * @param <T> the type to deserialize to
     * @return the deserialized value
     * @throws IOException if deserialization fails
     */
    public <T> T deserialize(Class<T> clazz, String key) throws IOException {
        return tikaJsonConfig.deserialize(key, clazz);
    }

    /**
     * Validates that pipes-specific keys are correct.
     * This catches typos like "pipes-reporter" instead of "pipes-reporters".
     * <p>
     * Core Tika keys (parsers, detectors, etc.) are ignored - they are
     * validated by TikaLoader.
     * <p>
     * Keys prefixed with "x-" are allowed for custom extensions.
     *
     * @throws TikaConfigException if unknown pipes keys are found
     */
    private void validatePipesKeys() throws TikaConfigException {
        JsonNode root = tikaJsonConfig.getRootNode();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();

            // Ignore core Tika keys - TikaLoader validates those
            if (CORE_TIKA_KEYS.contains(key)) {
                continue;
            }

            // Ignore custom extension keys
            if (key.startsWith("x-")) {
                continue;
            }

            // Must be a known pipes key
            if (!PIPES_KEYS.contains(key)) {
                throw new TikaConfigException("Unknown pipes config key: '" + key +
                        "'. Valid pipes keys: " + PIPES_KEYS +
                        " (or use 'x-' prefix for custom keys). " +
                        "Core Tika keys like 'parsers', 'detectors' should be configured separately.");
            }
        }
    }
}
