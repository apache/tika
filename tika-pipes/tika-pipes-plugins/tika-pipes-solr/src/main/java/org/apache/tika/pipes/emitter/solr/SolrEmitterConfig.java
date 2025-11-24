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
package org.apache.tika.pipes.emitter.solr;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

public record SolrEmitterConfig(
        String solrCollection,
        List<String> solrUrls,
        List<String> solrZkHosts,
        String solrZkChroot,
        @JsonProperty(defaultValue = "id") String idField,
        @JsonProperty(defaultValue = "1000") int commitWithin,
        @JsonProperty(defaultValue = "10000") int connectionTimeout,
        @JsonProperty(defaultValue = "60000") int socketTimeout,
        @JsonProperty(defaultValue = "PARENT_CHILD") String attachmentStrategy,
        @JsonProperty(defaultValue = "ADD") String updateStrategy,
        @JsonProperty(defaultValue = "embedded") String embeddedFileFieldName,
        String userName,
        String password,
        String authScheme,
        String proxyHost,
        Integer proxyPort
) {

    public enum AttachmentStrategy {
        SEPARATE_DOCUMENTS, PARENT_CHILD
    }

    public enum UpdateStrategy {
        ADD, UPDATE_MUST_EXIST, UPDATE_MUST_NOT_EXIST
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static SolrEmitterConfig load(final String json)
            throws TikaConfigException {
        try {
            return OBJECT_MAPPER.readValue(json, SolrEmitterConfig.class);
        } catch (JsonProcessingException e) {
            throw new TikaConfigException(
                    "Failed to parse SolrEmitterConfig from JSON", e);
        }
    }

    public void validate() throws TikaConfigException {
        if (solrCollection == null || solrCollection.isBlank()) {
            throw new TikaConfigException("'solrCollection' must not be empty");
        }
        if ((solrUrls == null || solrUrls.isEmpty()) && (solrZkHosts == null || solrZkHosts.isEmpty())) {
            throw new TikaConfigException("Either 'solrUrls' or 'solrZkHosts' must be specified");
        }
        if (solrUrls != null && !solrUrls.isEmpty() && solrZkHosts != null && !solrZkHosts.isEmpty()) {
            throw new TikaConfigException("Only one of 'solrUrls' or 'solrZkHosts' can be specified, not both");
        }
    }

    public AttachmentStrategy getAttachmentStrategyEnum() {
        if (attachmentStrategy == null) {
            return AttachmentStrategy.PARENT_CHILD;
        }
        return AttachmentStrategy.valueOf(attachmentStrategy.toUpperCase(Locale.US));
    }

    public UpdateStrategy getUpdateStrategyEnum() {
        if (updateStrategy == null) {
            return UpdateStrategy.ADD;
        }
        return UpdateStrategy.valueOf(updateStrategy.toUpperCase(Locale.US));
    }

    public String getIdFieldOrDefault() {
        return idField != null ? idField : "id";
    }

    public int getCommitWithinOrDefault() {
        return commitWithin > 0 ? commitWithin : 1000;
    }

    public int getConnectionTimeoutOrDefault() {
        return connectionTimeout > 0 ? connectionTimeout : 10000;
    }

    public int getSocketTimeoutOrDefault() {
        return socketTimeout > 0 ? socketTimeout : 60000;
    }

    public String getEmbeddedFileFieldNameOrDefault() {
        return embeddedFileFieldName != null ? embeddedFileFieldName : "embedded";
    }
}
