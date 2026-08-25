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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.tika.exception.TikaConfigException;

/**
 * The one {@link ObjectMapper} for plugin configuration JSON ({@link ExtensionConfig#json()}).
 * <p>
 * Fails fast on what a hand-written config gets wrong: unknown keys, a number where an
 * enum name belongs, duplicate keys. Comments are
 * accepted, as in the main config loader. Plain JSON only -- no Tika component serializers
 * -- so it is safe to share across the plugin classloader boundary, provided the plugin
 * does not bundle its own Jackson (the plugins parent pom enforces that).
 */
public final class PluginJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            // parser-level: FAIL_ON_READING_DUP_TREE_KEY only covers readTree, not readValue
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // Not FAIL_ON_NULL_FOR_PRIMITIVES: it also fires for a primitive record component
            // that is simply absent, and optional primitives with defaults are the norm here.
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private PluginJson() {
    }

    /** Deserializes {@code json} into {@code type}, reporting failures as config errors. */
    public static <T> T read(String json, Class<T> type) throws TikaConfigException {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse " + type.getSimpleName() + ": " + e.getOriginalMessage(), e);
        }
    }
}
