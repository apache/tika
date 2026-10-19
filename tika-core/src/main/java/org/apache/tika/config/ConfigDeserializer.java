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

import java.lang.reflect.Method;

import org.apache.tika.utils.SuppressForbidden;

/**
 * Deserializes JSON configuration without a compile-time dependency on Jackson.
 * <p>
 * Reads through tika-serialization (by reflection), which also refuses untrusted JSON that sets
 * an {@link OperatorOnly} property. Throws if tika-serialization is not on the classpath.
 * <p>
 * Usage pattern in parsers, detectors, and other Tika components:
 * <pre>
 * public class MyParser implements Parser {
 *     public static class Config {
 *         public int timeout = 30;
 *         public boolean verbose = false;
 *     }
 *
 *     public MyParser() {
 *         // Default constructor for SPI
 *     }
 *
 *     public MyParser(Config config) {
 *         // Constructor with explicit config object
 *         this.timeout = config.timeout;
 *         this.verbose = config.verbose;
 *     }
 *
 *     public MyParser(JsonConfig jsonConfig) {
 *         this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
 *     }
 * }
 * </pre>
 *
 * @since Apache Tika 4.0
 */
public class ConfigDeserializer {

    private static final String READER_CLASS = "org.apache.tika.config.loader.TikaObjectMapperFactory";

    private static final Method READ_CONFIG_METHOD;

    static {
        Method method = null;
        try {
            method = Class.forName(READER_CLASS)
                    .getMethod("readConfig", String.class, Class.class, boolean.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            // not on the classpath; buildConfig throws
        }
        READ_CONFIG_METHOD = method;
    }

    /**
     * Deserializes a JSON configuration to a configuration object. Untrusted JSON (see
     * {@link JsonConfig#trusted()}) may not set an {@link OperatorOnly} property.
     *
     * @param jsonConfig the JSON configuration
     * @param configClass the configuration class
     * @param <T> the configuration type
     * @return the deserialized configuration object
     * @throws RuntimeException if tika-serialization is not on the classpath, deserialization
     *         fails, or untrusted JSON sets an operator-only property
     */
    @SuppressForbidden
    public static <T> T buildConfig(JsonConfig jsonConfig, Class<T> configClass) {
        if (READ_CONFIG_METHOD == null) {
            throw new RuntimeException("Cannot parse JSON configuration for " +
                    configClass.getSimpleName() + ": tika-serialization is not on the classpath. " +
                    "Add org.apache.tika:tika-serialization as a dependency.");
        }
        try {
            return configClass.cast(READ_CONFIG_METHOD.invoke(null, jsonConfig.json(), configClass,
                    jsonConfig.trusted()));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Failed to parse " + configClass.getSimpleName() +
                " configuration: " + cause.getMessage(), cause);
        }
    }
}
