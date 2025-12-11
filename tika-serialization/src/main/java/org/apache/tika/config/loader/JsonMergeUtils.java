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
package org.apache.tika.config.loader;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility methods for merging JSON configurations with default values.
 * <p>
 * Provides a consistent pattern for deserializing JSON while preserving
 * default values for unspecified fields. The original default object is
 * never modified - a deep copy is created first.
 */
public final class JsonMergeUtils {

    private JsonMergeUtils() {
        // Utility class
    }

    /**
     * Deserializes JSON and merges it with a default configuration object.
     * <p>
     * Creates a deep copy of the default object, then applies the JSON properties
     * on top. Fields not specified in the JSON retain their default values.
     * The original defaultConfig is never modified.
     *
     * @param mapper the ObjectMapper to use
     * @param json the JSON string to deserialize
     * @param configClass the configuration class
     * @param defaultConfig the default configuration (will NOT be modified)
     * @param <T> the configuration type
     * @return a new object with defaults merged with JSON properties
     * @throws IOException if deserialization fails
     */
    public static <T> T mergeWithDefaults(ObjectMapper mapper, String json,
                                          Class<T> configClass, T defaultConfig) throws IOException {
        if (defaultConfig == null) {
            return mapper.readValue(json, configClass);
        }

        // Create a deep copy of defaultConfig to preserve immutability
        T copy = mapper.convertValue(defaultConfig, configClass);

        // Merge JSON properties into the copy
        return mapper.readerForUpdating(copy).readValue(json);
    }

    /**
     * Deserializes a JsonNode and merges it with a default configuration object.
     *
     * @param mapper the ObjectMapper to use
     * @param node the JsonNode to deserialize
     * @param configClass the configuration class
     * @param defaultConfig the default configuration (will NOT be modified)
     * @param <T> the configuration type
     * @return a new object with defaults merged with JSON properties
     * @throws IOException if deserialization fails
     */
    public static <T> T mergeWithDefaults(ObjectMapper mapper, JsonNode node,
                                          Class<T> configClass, T defaultConfig) throws IOException {
        if (defaultConfig == null) {
            return mapper.treeToValue(node, configClass);
        }

        // Create a deep copy of defaultConfig to preserve immutability
        @SuppressWarnings("unchecked")
        T copy = mapper.convertValue(defaultConfig, (Class<T>) defaultConfig.getClass());

        // Merge JSON properties into the copy
        return mapper.readerForUpdating(copy).readValue(node);
    }

    /**
     * Deserializes JSON to a configuration object without merging.
     *
     * @param mapper the ObjectMapper to use
     * @param json the JSON string to deserialize
     * @param configClass the configuration class
     * @param <T> the configuration type
     * @return the deserialized object
     * @throws IOException if deserialization fails
     */
    public static <T> T deserialize(ObjectMapper mapper, String json,
                                    Class<T> configClass) throws IOException {
        return mapper.readValue(json, configClass);
    }
}
