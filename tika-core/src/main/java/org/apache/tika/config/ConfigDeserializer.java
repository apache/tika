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

/**
 * Deserializes JSON configuration without a compile-time dependency on Jackson.
 * <p>
 * Reads through tika-serialization (see {@link ConfigReader}), which also refuses untrusted JSON
 * that sets an {@link OperatorOnly} property. Throws if tika-serialization is not available.
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
    public static <T> T buildConfig(JsonConfig jsonConfig, Class<T> configClass) {
        ConfigReader reader = ConfigReader.find();
        if (reader == null) {
            throw new RuntimeException("Cannot parse JSON configuration for " + configClass.getName() +
                    ": tika-serialization is missing or failed to load. " +
                    "Add org.apache.tika:tika-serialization as a dependency.");
        }
        try {
            return reader.readConfig(jsonConfig.json(), configClass, jsonConfig.trusted());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + configClass.getName() +
                " configuration: " + e.getMessage(), e);
        }
    }
}
