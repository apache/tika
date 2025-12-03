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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ParseContext;

/**
 * Facade for accessing runtime configuration from ParseContext's ConfigContainer.
 * <p>
 * This wrapper provides a safe way for parsers to access runtime configuration
 * without directly depending on tika-serialization. It performs these critical checks:
 * <ul>
 *   <li>If ConfigContainer has config for the requested key but ConfigDeserializer
 *       is not on the classpath, throws IllegalStateException with a clear error message</li>
 *   <li>If ConfigDeserializer is available, delegates to it for deserialization</li>
 *   <li>If no config is present, returns the default config</li>
 * </ul>
 * <p>
 * Usage in parsers:
 * <pre>
 * PDFParserConfig localConfig = ParseContextConfig.getConfig(
 *     context, "pdf-parser", PDFParserConfig.class, defaultConfig);
 * </pre>
 *
 * @since Apache Tika 4.0
 */
public class ParseContextConfig {

    private static final Class<?> CONFIG_DESERIALIZER_CLASS;
    private static final Method GET_CONFIG_METHOD;
    private static final Method HAS_CONFIG_METHOD;

    static {
        Class<?> clazz = null;
        Method getMethod = null;
        Method hasMethod = null;
        try {
            clazz = Class.forName("org.apache.tika.serialization.ConfigDeserializer");
            getMethod = clazz.getMethod("getConfig", 
                ParseContext.class, String.class, Class.class, Object.class);
            hasMethod = clazz.getMethod("hasConfig", ParseContext.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // ConfigDeserializer not on classpath - will check at runtime if needed
        }
        CONFIG_DESERIALIZER_CLASS = clazz;
        GET_CONFIG_METHOD = getMethod;
        HAS_CONFIG_METHOD = hasMethod;
    }

    /**
     * Retrieves runtime configuration from ParseContext's ConfigContainer.
     * <p>
     * This method performs defensive checking: if the ConfigContainer has configuration
     * for the requested key but the ConfigDeserializer is not available on the classpath,
     * it throws IllegalStateException. This prevents silent failures where users expect
     * their runtime config to be used but it's silently ignored.
     *
     * @param context the parse context (may be null)
     * @param configKey the configuration key (e.g., "pdf-parser", "html-parser")
     * @param configClass the configuration class
     * @param defaultConfig the default configuration to use if no runtime config exists
     * @param <T> the configuration type
     * @return the runtime config merged with defaults, or the default config if no runtime config
     * @throws TikaConfigException if ConfigContainer has config but ConfigDeserializer is not on classpath
     * @throws IOException if deserialization fails
     */
    public static <T> T getConfig(ParseContext context, String configKey,
                                   Class<T> configClass, T defaultConfig)
            throws TikaConfigException, IOException {
        if (context == null) {
            return defaultConfig;
        }

        ConfigContainer configContainer = context.get(ConfigContainer.class);
        if (configContainer == null) {
            return defaultConfig;
        }

        // Check if there's config for this specific key
        boolean hasConfigForKey = configContainer.get(configKey).isPresent();
        if (!hasConfigForKey) {
            return defaultConfig;
        }

        // Config exists for this key - ConfigDeserializer MUST be available
        if (CONFIG_DESERIALIZER_CLASS == null) {
            throw new TikaConfigException(String.format(Locale.ROOT,
                "ParseContext contains ConfigContainer with configuration for '%s' " +
                "but org.apache.tika.serialization.ConfigDeserializer is not on the classpath. " +
                "This means your runtime configuration will be ignored. " +
                "To fix: add tika-serialization as a dependency, or remove the ConfigContainer " +
                "from ParseContext if runtime configuration via ConfigContainer is not needed.",
                configKey));
        }

        // ConfigDeserializer is available - delegate to it
        try {
            @SuppressWarnings("unchecked")
            T result = (T) GET_CONFIG_METHOD.invoke(null, context, configKey, configClass, defaultConfig);
            return result;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Failed to deserialize config for '" + configKey + "': " + 
                cause.getMessage(), cause);
        }
    }

    /**
     * Checks if runtime configuration exists for the given key.
     * <p>
     * Unlike {@link #getConfig}, this method does NOT throw if ConfigDeserializer
     * is missing - it only checks for the presence of config.
     *
     * @param context the parse context
     * @param configKey the configuration key
     * @return true if config exists for this key
     */
    public static boolean hasConfig(ParseContext context, String configKey) {
        if (context == null) {
            return false;
        }

        ConfigContainer configContainer = context.get(ConfigContainer.class);
        if (configContainer == null) {
            return false;
        }

        return configContainer.get(configKey).isPresent();
    }

    /**
     * Checks if ConfigDeserializer is available on the classpath.
     *
     * @return true if tika-serialization is available
     */
    public static boolean isConfigDeserializerAvailable() {
        return CONFIG_DESERIALIZER_CLASS != null;
    }
}
