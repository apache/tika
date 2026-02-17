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
package org.apache.tika.pipes.emitter.elasticsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.TikaClientException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

/**
 * Plain HTTP client for Elasticsearch's REST API.
 *
 * <p>This does <b>not</b> use the Elasticsearch Java client library
 * (which is SSPL / Elastic License). Instead it talks directly to
 * Elasticsearch's {@code _bulk} REST endpoint using Apache HttpClient
 * (ASL v2).
 *
 * <p>Supports API key authentication ({@code Authorization: ApiKey ...})
 * as well as basic auth via the underlying {@link HttpClient}.
 */
public class ElasticsearchClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(ElasticsearchClient.class);

    protected final HttpClient httpClient;

    private final MetadataToJsonWriter metadataToJsonWriter;
    private final ElasticsearchEmitterConfig config;

    protected ElasticsearchClient(ElasticsearchEmitterConfig config,
                                  HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.metadataToJsonWriter =
                (config.updateStrategy() ==
                        ElasticsearchEmitterConfig.UpdateStrategy.OVERWRITE)
                        ? new InsertMetadataToJsonWriter()
                        : new UpsertMetadataToJsonWriter();
    }

    public void emitDocuments(List<? extends EmitData> emitData)
            throws IOException, TikaClientException {
        StringBuilder json = new StringBuilder();
        for (EmitData d : emitData) {
            appendDoc(d.getEmitKey(), d.getMetadataList(), json);
        }
        emitJson(json);
    }

    public void emitDocument(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaClientException {
        StringBuilder json = new StringBuilder();
        appendDoc(emitKey, metadataList, json);
        emitJson(json);
    }

    private void emitJson(StringBuilder json)
            throws IOException, TikaClientException {
        String requestUrl = config.elasticsearchUrl() + "/_bulk";
        JsonResponse response = postJson(requestUrl, json.toString());
        if (response.getStatus() != 200) {
            throw new TikaClientException(response.getMsg());
        } else {
            // If there's a single error in the bulk response, throw
            JsonNode errorNode = response.getJson().get("errors");
            if (errorNode != null && errorNode.asText().equals("true")) {
                throw new TikaClientException(
                        response.getJson().toString());
            }
        }
    }

    private void appendDoc(String emitKey, List<Metadata> metadataList,
                           StringBuilder json) throws IOException {
        int i = 0;
        String routing =
                (config.attachmentStrategy() ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD) ? emitKey : null;

        for (Metadata metadata : metadataList) {
            StringBuilder id = new StringBuilder(emitKey);
            if (i > 0) {
                id.append("-").append(UUID.randomUUID());
            }
            String indexJson =
                    metadataToJsonWriter.getBulkJson(id.toString(), routing);
            json.append(indexJson).append("\n");
            if (i == 0) {
                json.append(metadataToJsonWriter.writeContainer(
                        metadata, config.attachmentStrategy()));
            } else {
                json.append(metadataToJsonWriter.writeEmbedded(
                        metadata, config.attachmentStrategy(), emitKey,
                        config.embeddedFileFieldName()));
            }
            json.append("\n");
            i++;
        }
    }

    // Package-private for testing
    static String metadataToJsonContainerInsert(
            Metadata metadata,
            ElasticsearchEmitterConfig.AttachmentStrategy attachmentStrategy)
            throws IOException {
        return new InsertMetadataToJsonWriter().writeContainer(
                metadata, attachmentStrategy);
    }

    // Package-private for testing
    static String metadataToJsonEmbeddedInsert(
            Metadata metadata,
            ElasticsearchEmitterConfig.AttachmentStrategy attachmentStrategy,
            String emitKey, String embeddedFileFieldName)
            throws IOException {
        return new InsertMetadataToJsonWriter().writeEmbedded(
                metadata, attachmentStrategy, emitKey,
                embeddedFileFieldName);
    }

    public JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        StringEntity entity =
                new StringEntity(json, StandardCharsets.UTF_8);
        httpRequest.setEntity(entity);
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type",
                "application/json; charset=utf-8");

        // ES 8.x API key auth
        if (!StringUtils.isEmpty(config.apiKey())) {
            httpRequest.setHeader("Authorization",
                    "ApiKey " + config.apiKey());
        }

        HttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                try (Reader reader = new BufferedReader(
                        new InputStreamReader(
                                response.getEntity().getContent(),
                                StandardCharsets.UTF_8))) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(reader);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("node: {}", node);
                    }
                    return new JsonResponse(200, node);
                }
            } else {
                return new JsonResponse(status,
                        new String(
                                EntityUtils.toByteArray(
                                        response.getEntity()),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
            httpRequest.releaseConnection();
        }
    }

    // -----------------------------------------------------------------------
    // JSON writers for _bulk API
    // -----------------------------------------------------------------------

    private interface MetadataToJsonWriter {
        String writeContainer(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy)
                throws IOException;

        String writeEmbedded(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy,
                String emitKey, String embeddedFileFieldName)
                throws IOException;

        String getBulkJson(String id, String routing) throws IOException;
    }

    private static class InsertMetadataToJsonWriter
            implements MetadataToJsonWriter {

        @Override
        public String writeContainer(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                writeMetadata(metadata, jg);
                if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD) {
                    jg.writeStringField("relation_type", "container");
                }
                jg.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String writeEmbedded(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy,
                String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                writeMetadata(metadata, jg);
                if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD) {
                    jg.writeObjectFieldStart("relation_type");
                    jg.writeStringField("name", embeddedFileFieldName);
                    jg.writeStringField("parent", emitKey);
                    jg.writeEndObject();
                } else if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS) {
                    jg.writeStringField("parent", emitKey);
                }
                jg.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String getBulkJson(String id, String routing)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                jg.writeObjectFieldStart("index");
                jg.writeStringField("_id", id);
                if (!StringUtils.isEmpty(routing)) {
                    jg.writeStringField("routing", routing);
                }
                jg.writeEndObject();
                jg.writeEndObject();
            }
            return writer.toString();
        }
    }

    private static class UpsertMetadataToJsonWriter
            implements MetadataToJsonWriter {

        @Override
        public String writeContainer(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                jg.writeObjectFieldStart("doc");
                writeMetadata(metadata, jg);
                if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD) {
                    jg.writeStringField("relation_type", "container");
                }
                jg.writeEndObject();
                jg.writeBooleanField("doc_as_upsert", true);
                jg.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String writeEmbedded(
                Metadata metadata,
                ElasticsearchEmitterConfig.AttachmentStrategy strategy,
                String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                jg.writeObjectFieldStart("doc");
                writeMetadata(metadata, jg);
                if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .PARENT_CHILD) {
                    jg.writeObjectFieldStart("relation_type");
                    jg.writeStringField("name", embeddedFileFieldName);
                    jg.writeStringField("parent", emitKey);
                    jg.writeEndObject();
                } else if (strategy ==
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS) {
                    jg.writeStringField("parent", emitKey);
                }
                jg.writeEndObject();
                jg.writeBooleanField("doc_as_upsert", true);
                jg.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String getBulkJson(String id, String routing)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jg =
                         new JsonFactory().createGenerator(writer)) {
                jg.writeStartObject();
                jg.writeObjectFieldStart("update");
                jg.writeStringField("_id", id);
                if (!StringUtils.isEmpty(routing)) {
                    jg.writeStringField("routing", routing);
                }
                jg.writeNumberField("retry_on_conflict", 3);
                jg.writeEndObject();
                jg.writeEndObject();
            }
            return writer.toString();
        }
    }

    /**
     * Metadata fields whose values are serialized JSON from the
     * tika-inference pipeline. These must be written as raw JSON
     * (arrays/objects) rather than escaped strings so that
     * Elasticsearch can index vectors, locators, etc. natively.
     */
    static final Set<String> INFERENCE_JSON_FIELDS = Set.of(
            "tika:chunks");

    private static void writeMetadata(Metadata metadata,
                                      JsonGenerator jsonGenerator)
            throws IOException {
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 1) {
                if (INFERENCE_JSON_FIELDS.contains(n)
                        && isValidJson(vals[0])) {
                    jsonGenerator.writeFieldName(n);
                    jsonGenerator.writeRawValue(vals[0]);
                } else {
                    jsonGenerator.writeStringField(n, vals[0]);
                }
            } else {
                jsonGenerator.writeArrayFieldStart(n);
                for (String v : vals) {
                    jsonGenerator.writeString(v);
                }
                jsonGenerator.writeEndArray();
            }
        }
    }

    private static final ObjectMapper VALIDATION_MAPPER = new ObjectMapper();

    /**
     * Validates that the value is well-formed JSON (array or object)
     * before writing it as raw JSON. This prevents injection of
     * arbitrary content into the bulk request payload.
     */
    private static boolean isValidJson(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        if (first != '[' && first != '{') {
            return false;
        }
        try {
            VALIDATION_MAPPER.readTree(value);
            return true;
        } catch (IOException e) {
            LOG.warn("Field value starts with '{}' but is not valid JSON; "
                    + "writing as escaped string", first);
            return false;
        }
    }
}
