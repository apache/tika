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
package org.apache.tika.pipes.emitter.qdrant;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.plugins.PluginJson;

/**
 * @param qdrantUrl        Base URL of the Qdrant REST API, e.g. {@code http://localhost:6333}
 * @param collection       Name of an existing collection with one named vector per producer
 * @param apiKey           Sent as {@code Authorization: Bearer <apiKey>}. Null or empty sends none
 * @param metadataFields   Metadata keys copied into each point's {@code metadata} payload.
 *                         Null copies none
 * @param batchSize        Points per upsert request. Null means {@value #DEFAULT_BATCH_SIZE}
 * @param replaceExisting  After a container's upserts, delete its points that they did not write.
 *                         Null means {@code true}
 * @param httpClientConfig HTTP connection settings (basic auth, timeouts, proxy, TLS)
 */
public record QdrantEmitterConfig(String qdrantUrl, String collection, String apiKey,
                                  List<String> metadataFields, Integer batchSize,
                                  Boolean replaceExisting, HttpClientConfig httpClientConfig) {

    public static final int DEFAULT_BATCH_SIZE = 64;

    public QdrantEmitterConfig {
        batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        replaceExisting = replaceExisting == null || replaceExisting;
        metadataFields = metadataFields == null ? List.of() : metadataFields;
    }

    public static QdrantEmitterConfig load(final String json) throws TikaConfigException {
        QdrantEmitterConfig config = PluginJson.read(json, QdrantEmitterConfig.class);
        config.validate();
        return config;
    }

    public void validate() throws TikaConfigException {
        if (qdrantUrl == null || qdrantUrl.isBlank()) {
            throw new TikaConfigException("qdrantUrl must be set");
        }
        URI uri;
        try {
            uri = new URI(qdrantUrl);
        } catch (URISyntaxException e) {
            throw new TikaConfigException("qdrantUrl is not a valid URL: " + e.getMessage());
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new TikaConfigException("qdrantUrl must start with http:// or https://");
        }
        if (uri.getHost() == null) {
            throw new TikaConfigException("qdrantUrl must name a host");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new TikaConfigException("qdrantUrl must not have a query or a fragment");
        }
        if (collection == null || collection.isBlank()) {
            throw new TikaConfigException("collection must be set");
        }
        if (collection.contains("/")) {
            throw new TikaConfigException("collection must not contain '/'");
        }
        if (metadataFields.stream().anyMatch(f -> f == null || f.isBlank())) {
            throw new TikaConfigException("metadataFields must not contain empty names");
        }
        if (batchSize < 1) {
            throw new TikaConfigException("batchSize must be 1 or more, not " + batchSize);
        }
        if (apiKey != null && !apiKey.isEmpty() && httpClientConfig != null
                && httpClientConfig.userName() != null && !httpClientConfig.userName().isEmpty()) {
            throw new TikaConfigException("set apiKey or httpClientConfig.userName, not both");
        }
    }

    /** Keeps {@code apiKey} out of logs. */
    @Override
    public String toString() {
        return "QdrantEmitterConfig{qdrantUrl='" + qdrantUrl + '\'' +
                ", collection='" + collection + '\'' +
                ", metadataFields=" + metadataFields +
                ", batchSize=" + batchSize +
                ", replaceExisting=" + replaceExisting +
                ", apiKey=" + (apiKey != null ? "[REDACTED]" : "null") +
                '}';
    }
}
