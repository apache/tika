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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

public record OpenSearchEmitterConfig(String openSearchUrl, String idField, AttachmentStrategy attachmentStrategy,
                                      UpdateStrategy updateStrategy, int commitWithin,
                                      String embeddedFileFieldName, HttpClientConfig httpClientConfig,
                                      ChunkStrategy chunkStrategy) {
    /** Where {@code tk:chunks} goes: inside its document, or one document per chunk. */
    public enum ChunkStrategy {
        INLINE, DOCUMENTS
    }

    public OpenSearchEmitterConfig {
        chunkStrategy = chunkStrategy == null ? ChunkStrategy.INLINE : chunkStrategy;
    }

    /** The 4.0.0 shape: chunks stay inline. */
    public OpenSearchEmitterConfig(String openSearchUrl, String idField,
                                   AttachmentStrategy attachmentStrategy,
                                   UpdateStrategy updateStrategy, int commitWithin,
                                   String embeddedFileFieldName, HttpClientConfig httpClientConfig) {
        this(openSearchUrl, idField, attachmentStrategy, updateStrategy, commitWithin,
                embeddedFileFieldName, httpClientConfig, ChunkStrategy.INLINE);
    }

    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD,
    }

    public enum UpdateStrategy {
        OVERWRITE, UPSERT
    }

    public static OpenSearchEmitterConfig load(final String json)
            throws TikaConfigException {
        OpenSearchEmitterConfig config = PluginJson.read(json, OpenSearchEmitterConfig.class);
        config.validate();
        return config;
    }

    /** Chunk documents carry no join field, so they cannot live in a PARENT_CHILD index. */
    public void validate() throws TikaConfigException {
        if (chunkStrategy == ChunkStrategy.DOCUMENTS
                && attachmentStrategy == AttachmentStrategy.PARENT_CHILD) {
            throw new TikaConfigException("chunkStrategy DOCUMENTS needs attachmentStrategy "
                    + "SEPARATE_DOCUMENTS: chunk documents have no join field");
        }
    }

}
