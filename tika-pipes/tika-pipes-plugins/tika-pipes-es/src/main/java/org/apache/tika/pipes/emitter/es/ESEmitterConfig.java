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
package org.apache.tika.pipes.emitter.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Configuration for the ES emitter.
 *
 * @param esUrl              Full URL including index, e.g. {@code https://localhost:9200/my-index}
 * @param idField            Metadata field to use as the document {@code _id}
 * @param attachmentStrategy How to handle embedded documents
 * @param updateStrategy     Whether to overwrite or upsert
 * @param commitWithin       Not used by ES, kept for API parity with OpenSearch emitter
 * @param embeddedFileFieldName Field name for embedded-file relation
 * @param apiKey             API key for authentication (Base64-encoded {@code id:api_key}).
 *                           Sent as {@code Authorization: ApiKey <value>}.
 *                           If null/empty, falls back to httpClientConfig's userName/password
 *                           for basic auth.
 * @param httpClientConfig   HTTP connection settings (basic auth, timeouts, proxy)
 */
public record ESEmitterConfig(String esUrl, String idField,
                              AttachmentStrategy attachmentStrategy,
                              UpdateStrategy updateStrategy, int commitWithin,
                              String embeddedFileFieldName, String apiKey,
                              HttpClientConfig httpClientConfig) {
    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD,
    }

    public enum UpdateStrategy {
        OVERWRITE, UPSERT
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ESEmitterConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, ESEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse ESEmitterConfig from JSON", e);
        }
    }

    /** Overrides the record default to prevent {@code apiKey} leaking into logs. */
    @Override
    public String toString() {
        return "ESEmitterConfig{esUrl='" + esUrl + '\'' +
                ", idField='" + idField + '\'' +
                ", attachmentStrategy=" + attachmentStrategy +
                ", updateStrategy=" + updateStrategy +
                ", apiKey=" + (apiKey != null ? "[REDACTED]" : "null") +
                '}';
    }
}
