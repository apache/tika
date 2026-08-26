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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
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
     * Field-access mappers used only to clone an already-valid default, keyed by the
     * mapper they were derived from ({@code copy()} is expensive and the set of source
     * mappers is tiny and long-lived).
     * <p>
     * The clone must not run through setters. Runtime-config subclasses override their
     * setters to reject caller input -- often as "any non-empty value is a modification"
     * -- so re-applying the default's own values through them throws, and the caller's
     * JSON is never even reached. Copying by field also preserves init-time state that
     * has no getter (e.g. VLMOCRConfig.RuntimeConfig's initMaxTokens baseline), which a
     * serialization round-trip silently reset to the class default.
     */
    private static final Map<ObjectMapper, ObjectMapper> COPY_MAPPERS = new ConcurrentHashMap<>();

    private static ObjectMapper copyMapper(ObjectMapper mapper) {
        return COPY_MAPPERS.computeIfAbsent(mapper, m -> m.copy()
                .setVisibility(PropertyAccessor.ALL, Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, Visibility.ANY));
    }

    /** Clones an already-validated default without invoking its setters. */
    private static <T> T copyDefaults(ObjectMapper mapper, Class<T> configClass, T defaultConfig) {
        return copyMapper(mapper).convertValue(defaultConfig, configClass);
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

        T copy = copyDefaults(mapper, configClass, defaultConfig);

        // Only the caller's JSON goes through setters -- that is what validation guards are for
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

        @SuppressWarnings("unchecked")
        T copy = copyDefaults(mapper, (Class<T>) defaultConfig.getClass(), defaultConfig);

        // Only the caller's JSON goes through setters -- that is what validation guards are for
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
