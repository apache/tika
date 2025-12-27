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
package org.apache.tika.serialization;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.JsonMergeUtils;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;

/**
 * Helper utility for {@link org.apache.tika.config.SelfConfiguring} components
 * to deserialize their configuration from ParseContext at run time.
 * <p>
 * <strong>Note for Parser Developers:</strong> Instead of calling this class directly,
 * use {@link org.apache.tika.config.ParseContextConfig} which provides the same functionality
 * but with better error handling. ParseContextConfig will throw a clear exception if
 * tika-serialization is not on the classpath.
 * <p>
 * This allows parsers to retrieve their configuration using the same friendly names
 * as in tika-config.json (e.g., "pdf-parser", "html-parser") from per-request
 * configurations sent via FetchEmitTuple or other serialization mechanisms.
 * <p>
 * The helper automatically merges user configuration with parser defaults, eliminating
 * the need for config-specific cloneAndUpdate methods.
 * <p>
 * Example usage in a parser:
 * <pre>
 * // Recommended: Use ParseContextConfig wrapper (in tika-core)
 * PDFParserConfig localConfig = ParseContextConfig.getConfig(
 *     context, "pdf-parser", PDFParserConfig.class, defaultConfig);
 * </pre>
 *
 * @see org.apache.tika.config.ParseContextConfig
 */
public class ConfigDeserializer {

    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.getMapper();

    /**
     * Retrieves and deserializes a configuration from ParseContext.
     * <p>
     * Resolution order:
     * <ol>
     *   <li>Check resolved configs cache (already deserialized)</li>
     *   <li>Check JSON configs (deserialize, merge with default, cache)</li>
     *   <li>Return default config if nothing found</li>
     * </ol>
     * <p>
     * The resolved config is cached in ParseContext's resolvedConfigs map and also
     * set in the main context map so components can find it via {@code parseContext.get(configClass)}.
     *
     * @param context       the parse context
     * @param configKey     the configuration key (e.g., "pdf-parser", "handler-config")
     * @param configClass   the configuration class to deserialize into
     * @param defaultConfig optional default config to merge with user config (can be null)
     * @param <T>           the configuration type
     * @return the merged configuration, the default config if no user config found, or null if neither exists
     * @throws IOException if deserialization fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T getConfig(ParseContext context, String configKey, Class<T> configClass, T defaultConfig)
            throws IOException {
        if (context == null) {
            return defaultConfig;
        }

        // Check resolved cache first
        T resolved = context.getResolvedConfig(configKey);
        if (resolved != null) {
            return resolved;
        }

        // Check for JSON config
        JsonConfig jsonConfig = context.getJsonConfig(configKey);
        if (jsonConfig == null) {
            return defaultConfig;
        }

        // Deserialize and merge with default
        T config = JsonMergeUtils.mergeWithDefaults(MAPPER, jsonConfig.json(), configClass, defaultConfig);

        // Cache in resolved configs
        context.setResolvedConfig(configKey, config);

        // Also set in main context so other components can find it via parseContext.get(configClass)
        context.set(configClass, config);

        return config;
    }

    /**
     * Retrieves and deserializes a configuration from ParseContext.
     * This version does not merge with any default config.
     *
     * @param context     the parse context
     * @param configKey   the configuration key (e.g., "pdf-parser", "handler-config")
     * @param configClass the configuration class to deserialize into
     * @param <T>         the configuration type
     * @return the deserialized configuration, or null if not found
     * @throws IOException if deserialization fails
     */
    public static <T> T getConfig(ParseContext context, String configKey, Class<T> configClass)
            throws IOException {
        return getConfig(context, configKey, configClass, null);
    }

    /**
     * Checks if a configuration exists in the ParseContext.
     *
     * @param context   the parse context
     * @param configKey the configuration key to check
     * @return true if the configuration exists (either as JSON or already resolved)
     */
    public static boolean hasConfig(ParseContext context, String configKey) {
        if (context == null) {
            return false;
        }
        return context.hasJsonConfig(configKey) || context.getResolvedConfig(configKey) != null;
    }
}
