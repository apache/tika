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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

public class OpenSearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchEmitter.class);

    //this includes the full url and the index, should not end in /
    //e.g. https://localhost:9200/my-index
    protected final String openSearchUrl;
    protected final HttpClient httpClient;
    private final OpenSearchEmitter.AttachmentStrategy attachmentStrategy;

    private final MetadataToJsonWriter metadataToJsonWriter;
    private final String embeddedFileFieldName;
    protected OpenSearchClient(String openSearchUrl, HttpClient httpClient,
                               OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                               OpenSearchEmitter.UpdateStrategy updateStrategy,
                               String embeddedFileFieldName) {
        this.openSearchUrl = openSearchUrl;
        this.httpClient = httpClient;
        this.attachmentStrategy = attachmentStrategy;
        this.metadataToJsonWriter = (updateStrategy == OpenSearchEmitter.UpdateStrategy.OVERWRITE) ?
                new InsertMetadataToJsonWriter() : new UpsertMetadataToJsonWriter();
        this.embeddedFileFieldName = embeddedFileFieldName;
    }


    public void emitDocuments(List<? extends EmitData> emitData) throws IOException, TikaClientException {
        StringBuilder json = new StringBuilder();
        for (EmitData d : emitData) {
            appendDoc(d.getEmitKey().getEmitKey(), d.getMetadataList(), json);
        }
        emitJson(json);
    }

    private void emitJson(StringBuilder json) throws IOException, TikaClientException {
        String requestUrl = openSearchUrl + "/_bulk";
        JsonResponse response = postJson(requestUrl, json.toString());
        if (response.getStatus() != 200) {
            throw new TikaClientException(response.getMsg());
        } else {
            //if there's a single error, throw the full json.
            //this has not been thoroughly tested with versions of es < 7
            JsonNode errorNode = response.getJson().get("errors");
            if (errorNode.asText().equals("true")) {
                throw new TikaClientException(response.getJson().toString());
            }
        }
    }


    public void emitDocument(String emitKey, List<Metadata> metadataList) throws IOException,
            TikaClientException {

        StringBuilder json = new StringBuilder();
        appendDoc(emitKey, metadataList, json);
        emitJson(json);
    }

    private void appendDoc(String emitKey, List<Metadata> metadataList, StringBuilder json)
            throws IOException {
        int i = 0;
        String routing = (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) ?
                emitKey : null;

        for (Metadata metadata : metadataList) {
            StringBuilder id = new StringBuilder(emitKey);
            if (i > 0) {
                id.append("-").append(UUID.randomUUID());
            }
            String indexJson = metadataToJsonWriter.getBulkJson(id.toString(), routing);
            json.append(indexJson).append("\n");
            if (i == 0) {
                json.append(metadataToJsonWriter.writeContainer(metadata, attachmentStrategy));
            } else {
                json.append(metadataToJsonWriter.writeEmbedded(metadata, attachmentStrategy, emitKey,
                        embeddedFileFieldName));
            }
            json.append("\n");
            i++;
        }
    }

    //Only here for testing. These may disappear without notice in the future.
    protected static String metadataToJsonContainerInsert(Metadata metadata,
                                                    OpenSearchEmitter.AttachmentStrategy attachmentStrategy)
            throws IOException {
        return new InsertMetadataToJsonWriter().writeContainer(metadata, attachmentStrategy);
    }

    //Only here for testing. These may disappear without notice in the future.
    protected static String metadataToJsonEmbeddedInsert(Metadata metadata,
                                                         OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                                         String emitKey, String embeddedFileFieldName)
            throws IOException {
        return new InsertMetadataToJsonWriter().writeEmbedded(metadata,
                attachmentStrategy, emitKey, embeddedFileFieldName);
    }

    public JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        StringEntity entity = new StringEntity(json, StandardCharsets.UTF_8);
        httpRequest.setEntity(entity);
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        //At one point, this was required because of connection already
        // bound exceptions on windows :(
        //httpPost.setHeader("Connection", "close");

        //try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

        HttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                try (Reader reader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(),
                                StandardCharsets.UTF_8))) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(reader);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("node:", node);
                    }
                    return new JsonResponse(200, node);
                }
            } else {
                return new JsonResponse(status,
                        new String(EntityUtils.toByteArray(response.getEntity()),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (response != null && response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse)response).close();
            }
            httpRequest.releaseConnection();
        }
    }

    private interface MetadataToJsonWriter {
        String writeContainer(Metadata metadata, OpenSearchEmitter.AttachmentStrategy attachmentStrategy)
                throws IOException;

        String writeEmbedded(Metadata metadata, OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                             String emitKey, String embeddedFileFieldName) throws IOException;

        String getBulkJson(String id, String routing) throws IOException;
    }

    private static class InsertMetadataToJsonWriter implements MetadataToJsonWriter {

        @Override
        public String writeContainer(Metadata metadata,
                                     OpenSearchEmitter.AttachmentStrategy attachmentStrategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeStringField("relation_type", "container");
                }
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String writeEmbedded(Metadata metadata,
                                    OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                    String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();

                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeObjectFieldStart("relation_type");
                    jsonGenerator.writeStringField("name", embeddedFileFieldName);
                    jsonGenerator.writeStringField("parent", emitKey);
                    //end the relation type object
                    jsonGenerator.writeEndObject();
                } else if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS) {
                    jsonGenerator.writeStringField("parent", emitKey);
                }
                //end the metadata object
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String getBulkJson(String id, String routing) throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("index");
                jsonGenerator.writeStringField("_id", id);
                if (!StringUtils.isEmpty(routing)) {
                    jsonGenerator.writeStringField("routing", routing);
                }
                jsonGenerator.writeEndObject();
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }
    }

    private static class UpsertMetadataToJsonWriter implements MetadataToJsonWriter {

        @Override
        public String writeContainer(Metadata metadata,
                                     OpenSearchEmitter.AttachmentStrategy attachmentStrategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("doc");
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeStringField("relation_type", "container");
                }
                jsonGenerator.writeEndObject();
                jsonGenerator.writeBooleanField("doc_as_upsert", true);
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String writeEmbedded(Metadata metadata,
                                    OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                    String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("doc");
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeObjectFieldStart("relation_type");
                    jsonGenerator.writeStringField("name", embeddedFileFieldName);
                    jsonGenerator.writeStringField("parent", emitKey);
                    //end the relation type object
                    jsonGenerator.writeEndObject();
                } else if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS) {
                    jsonGenerator.writeStringField("parent", emitKey);
                }
                //end the "doc"
                jsonGenerator.writeEndObject();
                //end the metadata object
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String getBulkJson(String id, String routing) throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("update");
                jsonGenerator.writeStringField("_id", id);
                if (!StringUtils.isEmpty(routing)) {
                    jsonGenerator.writeStringField("routing", routing);
                }
                jsonGenerator.writeNumberField("retry_on_conflict", 3);
                jsonGenerator.writeEndObject();
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }
    }

    private static void writeMetadata(Metadata metadata, JsonGenerator jsonGenerator) throws IOException {
        //writes the metadata without the start { or the end }
        //to allow for other fields to be added
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 1) {
                jsonGenerator.writeStringField(n, vals[0]);
            } else {
                jsonGenerator.writeArrayFieldStart(n);
                for (String v : vals) {
                    jsonGenerator.writeString(v);
                }
                jsonGenerator.writeEndArray();
            }
        }
    }
}
