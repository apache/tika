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

/**
 * Utility for deserializing JSON configuration without compile-time dependency on Jackson.
 * <p>
 * This class uses reflection to call Jackson's ObjectMapper when available on the classpath.
 * If Jackson is not available and JSON deserialization is attempted, it throws a clear error message.
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

    private static final Class<?> OBJECT_MAPPER_CLASS;
    private static final Object OBJECT_MAPPER_INSTANCE;
    private static final Method READ_VALUE_METHOD;

    static {
        Class<?> clazz = null;
        Object instance = null;
        Method method = null;
        try {
            clazz = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            // Use a plain ObjectMapper for simple config deserialization.
            // The polymorphic mapper from tika-serialization is meant for ParseContext
            // serialization with actual polymorphic types, not for simple config classes.
            instance = clazz.getDeclaredConstructor().newInstance();
            method = clazz.getMethod("readValue", String.class, Class.class);
        } catch (Exception e) {
            // Jackson not on classpath - will fail at runtime if JSON deserialization is attempted
        }
        OBJECT_MAPPER_CLASS = clazz;
        OBJECT_MAPPER_INSTANCE = instance;
        READ_VALUE_METHOD = method;
    }

    /**
     * Deserializes a JSON configuration to a configuration object.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig the JSON configuration
     * @param configClass the configuration class
     * @param <T> the configuration type
     * @return the deserialized configuration object
     * @throws RuntimeException if Jackson is not on the classpath or deserialization fails
     */
    public static <T> T buildConfig(JsonConfig jsonConfig, Class<T> configClass) {
        if (OBJECT_MAPPER_CLASS == null) {
            throw new RuntimeException(
                "Cannot parse JSON configuration for " + configClass.getSimpleName() +
                ": Jackson is not on the classpath. " +
                "Add com.fasterxml.jackson.core:jackson-databind as a dependency.");
        }

        try {
            @SuppressWarnings("unchecked")
            T config = (T) READ_VALUE_METHOD.invoke(OBJECT_MAPPER_INSTANCE, jsonConfig.json(), configClass);
            return config;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Failed to parse " + configClass.getSimpleName() +
                " configuration: " + cause.getMessage(), cause);
        }
    }

    /**
     * Checks if Jackson ObjectMapper is available on the classpath.
     *
     * @return true if Jackson is available for JSON deserialization
     */
    public static boolean isJacksonAvailable() {
        return OBJECT_MAPPER_CLASS != null;
    }
}
