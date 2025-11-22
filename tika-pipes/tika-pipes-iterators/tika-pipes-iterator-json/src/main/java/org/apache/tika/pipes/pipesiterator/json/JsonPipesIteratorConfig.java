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
package org.apache.tika.pipes.pipesiterator.json;

import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorConfig;

public class JsonPipesIteratorConfig implements PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static JsonPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json,
                    JsonPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse JsonPipesIteratorConfig from JSON", e);
        }
    }

    private Path jsonPath;
    private PipesIteratorBaseConfig baseConfig = null;

    public Path getJsonPath() {
        return jsonPath;
    }

    @Override
    public PipesIteratorBaseConfig getBaseConfig() {
        return baseConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof JsonPipesIteratorConfig that)) {
            return false;
        }

        return Objects.equals(jsonPath, that.jsonPath) &&
                Objects.equals(baseConfig, that.baseConfig);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(jsonPath);
        result = 31 * result + Objects.hashCode(baseConfig);
        return result;
    }
}
