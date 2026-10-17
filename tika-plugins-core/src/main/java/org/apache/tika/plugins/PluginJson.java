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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.serialization.OperatorOnlyGuard;

/**
 * Reads plugin configuration JSON ({@link ExtensionConfig#json()}) with
 * {@link TikaObjectMapperFactory#createStrictPlainMapper()}: the main config loader's checks,
 * without Tika component serializers, so it is safe to share across the plugin classloader
 * boundary, provided the plugin does not bundle its own Jackson (the plugins parent pom
 * enforces that).
 */
public final class PluginJson {

    private static final ObjectMapper MAPPER = TikaObjectMapperFactory.createStrictPlainMapper();

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

    /**
     * As {@link #read(String, Class)}, but untrusted JSON (per-request config, see
     * {@link JsonConfig#trusted()}) may not set an {@link org.apache.tika.config.OperatorOnly}
     * property of {@code type}.
     */
    public static <T> T read(JsonConfig config, Class<T> type) throws TikaConfigException {
        if (!config.trusted()) {
            try {
                OperatorOnlyGuard.check(MAPPER, type.getSimpleName(), config.json(), type);
            } catch (IOException e) {
                throw new TikaConfigException(e.getMessage(), e);
            }
        }
        return read(config.json(), type);
    }
}
