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
package org.apache.tika.pipes.emitter.azblob;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record AZBlobEmitterConfig(
        String sasToken,
        String endpoint,
        String container,
        String prefix,
        @JsonProperty(defaultValue = "json") String fileExtension,
        @JsonProperty(defaultValue = "false") boolean overwriteExisting
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static AZBlobEmitterConfig load(String json) throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, AZBlobEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to parse AZBlobEmitterConfig from JSON", e);
        }
    }

    public void validate() throws TikaConfigException {
        if (sasToken == null || sasToken.isBlank()) {
            throw new TikaConfigException("'sasToken' must not be empty");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new TikaConfigException("'endpoint' must not be empty");
        }
        if (container == null || container.isBlank()) {
            throw new TikaConfigException("'container' must not be empty");
        }
    }

    /**
     * Get the prefix, stripping any trailing slash.
     */
    public String getNormalizedPrefix() {
        if (prefix == null) {
            return "";
        }
        if (prefix.endsWith("/")) {
            return prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    public String getFileExtensionOrDefault() {
        return fileExtension != null ? fileExtension : "json";
    }
}
