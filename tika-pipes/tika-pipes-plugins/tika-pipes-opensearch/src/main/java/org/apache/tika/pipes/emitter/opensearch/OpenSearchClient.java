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
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class OpenSearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchEmitter.class);

    //this includes the full url and the index, should not end in /
    //e.g. https://localhost:9200/my-index
    protected final HttpClient httpClient;

    private final MetadataToJsonWriter metadataToJsonWriter;
    private final OpenSearchEmitterConfig config;
    protected OpenSearchClient(OpenSearchEmitterConfig openSearchEmitterConfig, HttpClient httpClient) {
        this.config = openSearchEmitterConfig;
        this.httpClient = httpClient;
        this.metadataToJsonWriter = (config.updateStrategy() == OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE) ?
                new InsertMetadataToJsonWriter() : new UpsertMetadataToJsonWriter();

    }


    public void emitDocuments(List<? extends EmitData> emitData) throws IOException, TikaClientException {
        StringBuilder json = new StringBuilder();
        for (EmitData d : emitData) {
            appendDoc(d.getEmitKey(), d.getMetadataList(), json);
        }
        emitJson(json);
    }

    private void emitJson(StringBuilder json) throws IOException, TikaClientException {
        String requestUrl = config.openSearchUrl() + "/_bulk";
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
        String routing = (config.attachmentStrategy() == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD) ?
                emitKey : null;

        boolean chunkDocuments =
                config.chunkStrategy() == OpenSearchEmitterConfig.ChunkStrategy.DOCUMENTS;
        for (Metadata metadata : metadataList) {
            StringBuilder id = new StringBuilder(emitKey);
            if (i > 0) {
                id.append("-").append(UUID.randomUUID());
            }
            String indexJson = metadataToJsonWriter.getBulkJson(id.toString(), routing);
            json.append(indexJson).append("\n");
            JsonNode chunks = chunkDocuments ? chunkArray(metadata.get(CHUNKS_FIELD)) : null;
            Metadata body = chunks == null ? metadata : without(metadata, CHUNKS_FIELD);
            if (i == 0) {
                json.append(metadataToJsonWriter.writeContainer(body, config.attachmentStrategy()));
            } else {
                json.append(metadataToJsonWriter.writeEmbedded(body, config.attachmentStrategy(), emitKey, config.embeddedFileFieldName()));
            }
            json.append("\n");
            if (chunks != null) {
                appendChunks(id.toString(), emitKey, routing, chunks, json);
            }
            i++;
        }
    }

    static final String CHUNKS_FIELD = "tk:chunks";

    /**
     * Chunks as their own documents, one per unit: chunks sharing a correlator (a segment's
     * sound and picture, a region's text and crop) merge into one document whose vectors sit
     * under {@code v.<producer>}, so a search scores the unit and two vectors of one unit add
     * up on the same hit. Locators are flattened to fields and vectors decoded to float arrays.
     */
    /** The chunk array, or null when the value is not one and so stays on the document. */
    private static JsonNode chunkArray(String chunks) throws IOException {
        if (chunks == null || !isValidJson(chunks)) {
            return null;
        }
        JsonNode array = VALIDATION_MAPPER.readTree(chunks);
        return array.isArray() ? array : null;
    }

    private void appendChunks(String docId, String emitKey, String routing, JsonNode array,
                              StringBuilder json) throws IOException {
        int n = 0;
        for (ObjectNode doc : chunkDocuments(array, docId, emitKey)) {
            json.append(metadataToJsonWriter.getBulkJson(docId + "-chunk-" + n, routing))
                    .append("\n");
            json.append(metadataToJsonWriter.writeChunk(doc)).append("\n");
            n++;
        }
    }

    /** One document per correlator, in first-seen order; a chunk without one is its own. */
    static List<ObjectNode> chunkDocuments(JsonNode chunks, String docId, String emitKey) {
        List<ObjectNode> docs = new ArrayList<>();
        Map<String, ObjectNode> byCorrelator = new HashMap<>();
        for (JsonNode chunk : chunks) {
            String correlator = chunk.hasNonNull("correlator") ? chunk.get("correlator").asText() : null;
            ObjectNode doc = correlator == null ? null : byCorrelator.get(correlator);
            if (doc == null) {
                doc = VALIDATION_MAPPER.createObjectNode();
                doc.put("file_id", docId);
                doc.put("container_id", emitKey);
                if (correlator != null) {
                    doc.put("correlator", correlator);
                    byCorrelator.put(correlator, doc);
                }
                docs.add(doc);
            }
            mergeChunk(doc, chunk);
        }
        for (ObjectNode doc : docs) {
            if (doc.get("v").isEmpty()) {
                doc.remove("v");
            }
        }
        return docs;
    }

    /** Adds what the chunk knows: fields it is the first to set, and its vector under its producer. */
    private static void mergeChunk(ObjectNode doc, JsonNode chunk) {
        if (chunk.hasNonNull("text") && !doc.has("text")) {
            doc.put("text", chunk.get("text").asText());
        }
        JsonNode locators = chunk.get("locators");
        if (locators != null && locators.isObject()) {
            JsonNode t = first(locators, "temporal");
            if (t != null) {
                setIfAbsent(doc, "start_ms", t.get("start_ms"));
                setIfAbsent(doc, "end_ms", t.get("end_ms"));
            }
            JsonNode p = first(locators, "paginated");
            if (p != null) {
                setIfAbsent(doc, "page", p.get("page"));
                setIfAbsent(doc, "bbox", p.get("bbox"));
            }
            JsonNode s = first(locators, "spatial");
            if (s != null) {
                setIfAbsent(doc, "bbox", s.get("bbox"));
            }
            JsonNode x = first(locators, "text");
            if (x != null) {
                setIfAbsent(doc, "start_offset", x.get("start_offset"));
                setIfAbsent(doc, "end_offset", x.get("end_offset"));
            }
            JsonNode e = first(locators, "embedded");
            if (e != null) {
                setIfAbsent(doc, "embedded_id_path", e.get("id_path"));
                setIfAbsent(doc, "embedded_name", e.get("name"));
            }
        }
        ObjectNode v = doc.has("v") ? (ObjectNode) doc.get("v") : doc.putObject("v");
        if (chunk.hasNonNull("vector") && chunk.get("vector").isTextual()) {
            String key = chunk.hasNonNull("producer") ? chunk.get("producer").asText() : "default";
            try {
                floats(v.putArray(key), chunk.get("vector").asText());
            } catch (IllegalArgumentException e) {
                // a foreign writer's encoding: the document keeps its other fields
                LOG.warn("chunk vector for {} is not base64 float32; dropped", key);
                v.remove(key);
            }
        }
    }

    private static void setIfAbsent(ObjectNode doc, String field, JsonNode value) {
        if (value != null && !value.isNull() && !doc.has(field)) {
            doc.set(field, value);
        }
    }

    private static JsonNode first(JsonNode locators, String kind) {
        JsonNode list = locators.get(kind);
        return list != null && list.isArray() && !list.isEmpty() ? list.get(0) : null;
    }

    /** Base64 big-endian float32, as tika-inference writes vectors. */
    private static void floats(ArrayNode into, String base64) {
        FloatBuffer fb = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).asFloatBuffer();
        while (fb.hasRemaining()) {
            into.add(fb.get());
        }
    }

    private static Metadata without(Metadata metadata, String field) {
        Metadata copy = new Metadata();
        copy.putAll(metadata);
        copy.remove(field);
        return copy;
    }

    //Only here for testing. These may disappear without notice in the future.
    protected static String metadataToJsonContainerInsert(Metadata metadata,
                                                    OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy)
            throws IOException {
        return new InsertMetadataToJsonWriter().writeContainer(metadata, attachmentStrategy);
    }

    //Only here for testing. These may disappear without notice in the future.
    protected static String metadataToJsonEmbeddedInsert(Metadata metadata,
                                                         OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
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
                        LOG.trace("node: {}", node);
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
        String writeContainer(Metadata metadata, OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy)
                throws IOException;

        String writeEmbedded(Metadata metadata, OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
                             String emitKey, String embeddedFileFieldName) throws IOException;

        String getBulkJson(String id, String routing) throws IOException;

        String writeChunk(ObjectNode chunk) throws IOException;
    }

    private static class InsertMetadataToJsonWriter implements MetadataToJsonWriter {

        @Override
        public String writeChunk(ObjectNode chunk) {
            return chunk.toString();
        }

        @Override
        public String writeContainer(Metadata metadata,
                                     OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeStringField("relation_type", "container");
                }
                jsonGenerator.writeEndObject();
            }
            return writer.toString();
        }

        @Override
        public String writeEmbedded(Metadata metadata,
                                    OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
                                    String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();

                writeMetadata(metadata, jsonGenerator);

                if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeObjectFieldStart("relation_type");
                    jsonGenerator.writeStringField("name", embeddedFileFieldName);
                    jsonGenerator.writeStringField("parent", emitKey);
                    //end the relation type object
                    jsonGenerator.writeEndObject();
                } else if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS) {
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
        public String writeChunk(ObjectNode chunk) {
            ObjectNode wrapper = VALIDATION_MAPPER.createObjectNode();
            wrapper.set("doc", chunk);
            wrapper.put("doc_as_upsert", true);
            return wrapper.toString();
        }

        @Override
        public String writeContainer(Metadata metadata, OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("doc");
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD) {
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
                                    OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
                                    String emitKey, String embeddedFileFieldName)
                throws IOException {
            StringWriter writer = new StringWriter();
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeObjectFieldStart("doc");
                writeMetadata(metadata, jsonGenerator);
                if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD) {
                    jsonGenerator.writeObjectFieldStart("relation_type");
                    jsonGenerator.writeStringField("name", embeddedFileFieldName);
                    jsonGenerator.writeStringField("parent", emitKey);
                    //end the relation type object
                    jsonGenerator.writeEndObject();
                } else if (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS) {
                    jsonGenerator.writeStringField("parent", emitKey);
                }
                //end the "doc"
                jsonGenerator.writeEndObject();
                jsonGenerator.writeBooleanField("doc_as_upsert", true);
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

    /**
     * Metadata fields whose values are serialized JSON from the
     * tika-inference pipeline. These must be written as raw JSON
     * (arrays/objects) rather than escaped strings so that
     * OpenSearch can index vectors, locators, etc. natively.
     */
    static final Set<String> INFERENCE_JSON_FIELDS = Set.of(
            "tk:chunks");

    private static void writeMetadata(Metadata metadata, JsonGenerator jsonGenerator) throws IOException {
        //writes the metadata without the start { or the end }
        //to allow for other fields to be added
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
