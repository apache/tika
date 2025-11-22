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
package org.apache.tika.pipes.pipesiterator.gcs;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorConfig;

public class GCSPipesIteratorConfig implements PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static GCSPipesIteratorConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, GCSPipesIteratorConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse GCSPipesIteratorConfig from JSON", e);
        }
    }

    private String bucket;
    private String prefix = "";
    private String projectId = "";
    private PipesIteratorBaseConfig baseConfig = null;

    public String getBucket() {
        return bucket;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getProjectId() {
        return projectId;
    }

    @Override
    public PipesIteratorBaseConfig getBaseConfig() {
        return baseConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof GCSPipesIteratorConfig that)) {
            return false;
        }

        return Objects.equals(bucket, that.bucket) &&
                Objects.equals(prefix, that.prefix) &&
                Objects.equals(projectId, that.projectId) &&
                Objects.equals(baseConfig, that.baseConfig);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(bucket);
        result = 31 * result + Objects.hashCode(prefix);
        result = 31 * result + Objects.hashCode(projectId);
        result = 31 * result + Objects.hashCode(baseConfig);
        return result;
    }
}
