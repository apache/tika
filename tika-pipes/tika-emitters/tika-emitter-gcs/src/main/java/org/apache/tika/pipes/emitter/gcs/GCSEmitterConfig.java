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
package org.apache.tika.pipes.emitter.gcs;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record GCSEmitterConfig(
        String projectId,
        String bucket,
        String prefix,
        @JsonProperty(defaultValue = "json") String fileExtension
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static GCSEmitterConfig load(JsonNode jsonNode) throws IOException {
        return OBJECT_MAPPER.treeToValue(jsonNode, GCSEmitterConfig.class);
    }

    public void validate() throws TikaConfigException {
        if (projectId == null || projectId.isBlank()) {
            throw new TikaConfigException("'projectId' must not be empty");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new TikaConfigException("'bucket' must not be empty");
        }
    }

    /**
     * Get the prefix, stripping any trailing slash.
     */
    public String getNormalizedPrefix() {
        if (prefix == null) {
            return null;
        }
        if (prefix.endsWith("/")) {
            return prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
