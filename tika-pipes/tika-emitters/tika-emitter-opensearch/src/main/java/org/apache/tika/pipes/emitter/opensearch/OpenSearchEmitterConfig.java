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
package org.apache.tika.pipes.emitter.opensearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record OpenSearchEmitterConfig(String openSearchUrl, String idField, AttachmentStrategy attachmentStrategy,
                                      UpdateStrategy updateStrategy, int commitWithin,
                                      String embeddedFileFieldName, HttpClientConfig httpClientConfig) {
    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD,
    }

    public enum UpdateStrategy {
        OVERWRITE, UPSERT
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static OpenSearchEmitterConfig load(String json) throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, OpenSearchEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException("Failed to parse OpenSearchEmitterConfig from JSON", e);
        }
    }

}
