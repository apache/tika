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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.TikaClientException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.utils.StringUtils;

/**
 * Writes {@code tk:chunks} to a Qdrant collection over the REST API, one point per unit of
 * chunks that share a correlator, with one named vector per producer.
 */
public class QdrantClient {

    static final String CHUNKS_FIELD = TikaCoreProperties.TIKA_CHUNKS.getName();
    static final String DEFAULT_VECTOR_NAME = "default";
    static final String CONTAINER_ID = "container_id";

    private static final Logger LOG = LoggerFactory.getLogger(QdrantClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ERROR_CHARS = 2000;
    private static final int SCROLL_PAGE = 256;
    private static final int DELETE_BATCH = 1000;

    private record LocatorField(String locator, String field, String payloadKey) {
    }

    private static final List<LocatorField> LOCATOR_FIELDS = List.of(
            new LocatorField("temporal", "start_ms", "start_ms"),
            new LocatorField("temporal", "end_ms", "end_ms"),
            new LocatorField("paginated", "page", "page"),
            new LocatorField("paginated", "bbox", "bbox"),
            new LocatorField("spatial", "bbox", "bbox"),
            new LocatorField("spatial", "label", "label"),
            new LocatorField("text", "start_offset", "start_offset"),
            new LocatorField("text", "end_offset", "end_offset"),
            new LocatorField("embedded", "id_path", "embedded_id_path"),
            new LocatorField("embedded", "name", "embedded_name"));

    private final QdrantEmitterConfig config;
    private final HttpClient httpClient;
    private final String pointsUrl;

    QdrantClient(QdrantEmitterConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        String base = config.qdrantUrl().replaceAll("/+$", "");
        this.pointsUrl = base + "/collections/"
                + URLEncoder.encode(config.collection(), StandardCharsets.UTF_8).replace("+", "%20")
                + "/points";
    }

    /** A container that Qdrant rejects does not stop the others. */
    public void emitDocuments(List<? extends EmitData> emitData)
            throws IOException, TikaClientException {
        List<String> failed = new ArrayList<>();
        TikaClientException firstFailure = null;
        for (EmitData d : emitData) {
            try {
                emitDocument(d.getEmitKey(), d.getMetadataList());
            } catch (TikaClientException e) {
                LOG.warn("Qdrant rejected {}: {}", d.getEmitKey(), e.getMessage());
                failed.add(d.getEmitKey());
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }
        if (firstFailure != null) {
            throw new TikaClientException("Qdrant rejected " + failed.size() + " of "
                    + emitData.size() + " containers " + failed + ". First error: "
                    + firstFailure.getMessage(), firstFailure);
        }
    }

    public void emitDocument(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaClientException {
        if (StringUtils.isBlank(emitKey)) {
            throw new TikaClientException("emit key must not be blank");
        }
        List<String> ids = new ArrayList<>();
        List<ObjectNode> batch = new ArrayList<>();
        forEachPoint(emitKey, metadataList == null ? List.of() : metadataList,
                config.metadataFields(), point -> {
                    ids.add(point.get("id").asText());
                    batch.add(point);
                    if (batch.size() == config.batchSize()) {
                        upsert(batch);
                        batch.clear();
                    }
                });
        if (!batch.isEmpty()) {
            upsert(batch);
        }
        // no points can mean a failed embedding request, which must not erase the earlier points
        if (config.replaceExisting() && !ids.isEmpty()) {
            deleteStale(emitKey, new HashSet<>(ids));
        }
        LOG.debug("wrote {} points for {}", ids.size(), emitKey);
    }

    private void upsert(List<ObjectNode> batch) throws IOException, TikaClientException {
        send(new HttpPut(pointsUrl + "?wait=true"), upsertJson(batch));
    }

    /** Pages through the container's point ids and deletes, by id, those not in {@code keep}. */
    private void deleteStale(String containerId, Set<String> keep)
            throws IOException, TikaClientException {
        List<JsonNode> stale = new ArrayList<>();
        JsonNode offset = null;
        do {
            JsonNode result = MAPPER.readTree(send(new HttpPost(pointsUrl + "/scroll"),
                    scrollJson(containerId, offset))).path("result");
            for (JsonNode point : result.path("points")) {
                if (!keep.contains(point.path("id").asText())) {
                    stale.add(point.get("id"));
                }
            }
            JsonNode next = result.path("next_page_offset");
            if (next.equals(offset)) {
                throw new TikaClientException("Qdrant scroll returned the same offset twice: " + next);
            }
            offset = next.isMissingNode() || next.isNull() ? null : next;
        } while (offset != null);
        for (int start = 0; start < stale.size(); start += DELETE_BATCH) {
            send(new HttpPost(pointsUrl + "/delete?wait=true"),
                    deleteJson(stale.subList(start, Math.min(stale.size(), start + DELETE_BATCH))));
        }
    }

    static String scrollJson(String containerId, JsonNode offset) {
        ObjectNode body = MAPPER.createObjectNode();
        body.putObject("filter").putArray("must").addObject().put("key", CONTAINER_ID)
                .putObject("match").put("value", containerId);
        body.put("limit", SCROLL_PAGE);
        body.put("with_payload", false);
        body.put("with_vector", false);
        if (offset != null) {
            body.set("offset", offset);
        }
        return body.toString();
    }

    static String deleteJson(List<JsonNode> ids) {
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("points").addAll(ids);
        return body.toString();
    }

    static String upsertJson(List<ObjectNode> points) {
        ObjectNode body = MAPPER.createObjectNode();
        body.putArray("points").addAll(points);
        return body.toString();
    }

    interface PointSink {
        void accept(ObjectNode point) throws IOException, TikaClientException;
    }

    /** Builds points one at a time, so only the caller's batch is held as JSON. */
    static void forEachPoint(String emitKey, List<Metadata> metadataList, List<String> metadataFields,
                             PointSink sink) throws IOException, TikaClientException {
        for (int fileIndex = 0; fileIndex < metadataList.size(); fileIndex++) {
            Metadata metadata = metadataList.get(fileIndex);
            JsonNode chunks = chunkArray(metadata.get(CHUNKS_FIELD));
            if (chunks == null) {
                continue;
            }
            ObjectNode metadataPayload = metadataPayload(metadata, metadataFields);
            List<Unit> units = units(chunks);
            for (int chunkIndex = 0; chunkIndex < units.size(); chunkIndex++) {
                Unit unit = units.get(chunkIndex);
                if (unit.vectors().isEmpty()) {
                    continue;
                }
                ObjectNode point = MAPPER.createObjectNode();
                point.put("id", pointId(emitKey, fileIndex, chunkIndex).toString());
                ObjectNode vector = point.putObject("vector");
                unit.vectors().forEach((name, values) -> {
                    ArrayNode array = vector.putArray(name);
                    for (float f : values) {
                        array.add(f);
                    }
                });
                ObjectNode payload = point.putObject("payload");
                payload.put(CONTAINER_ID, emitKey);
                payload.put("file_index", fileIndex);
                payload.put("chunk_index", chunkIndex);
                payload.setAll(unit.payload());
                if (!metadataPayload.isEmpty()) {
                    payload.set("metadata", metadataPayload);
                }
                sink.accept(point);
            }
        }
    }

    static UUID pointId(String emitKey, int fileIndex, int chunkIndex) {
        ArrayNode key = MAPPER.createArrayNode().add(emitKey).add(fileIndex).add(chunkIndex);
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode chunkArray(String chunks) {
        if (StringUtils.isBlank(chunks)) {
            return null;
        }
        try {
            JsonNode array = MAPPER.readTree(chunks);
            return array.isArray() ? array : null;
        } catch (IOException e) {
            LOG.warn("skipping a file whose {} is not valid JSON", CHUNKS_FIELD);
            return null;
        }
    }

    private static ObjectNode metadataPayload(Metadata metadata, List<String> fields) {
        ObjectNode payload = MAPPER.createObjectNode();
        for (String name : fields) {
            if (CHUNKS_FIELD.equals(name)) {
                continue;
            }
            String[] values = metadata.getValues(name);
            if (values.length == 1) {
                payload.put(name, values[0]);
            } else if (values.length > 1) {
                ArrayNode array = payload.putArray(name);
                for (String v : values) {
                    array.add(v);
                }
            }
        }
        return payload;
    }

    record Unit(ObjectNode payload, Map<String, float[]> vectors) {
    }

    /**
     * Groups chunks by correlator, in first-seen order. A chunk without a correlator, or whose
     * producer already has a vector in its correlator's unit, starts a unit of its own.
     */
    static List<Unit> units(JsonNode chunks) {
        List<Unit> units = new ArrayList<>();
        Map<String, Unit> byCorrelator = new HashMap<>();
        int dropped = 0;
        String firstReason = null;
        for (JsonNode chunk : chunks) {
            if (!chunk.isObject()) {
                continue;
            }
            String correlator = chunk.hasNonNull("correlator") ? chunk.get("correlator").asText() : null;
            Unit unit = correlator == null ? null : byCorrelator.get(correlator);
            if (unit != null && chunk.hasNonNull("vector")
                    && unit.vectors().containsKey(vectorName(chunk))) {
                unit = null;
            }
            if (unit == null) {
                unit = new Unit(MAPPER.createObjectNode(), new LinkedHashMap<>());
                if (correlator != null) {
                    unit.payload().put("correlator", correlator);
                    byCorrelator.putIfAbsent(correlator, unit);
                }
                units.add(unit);
            }
            String reason = merge(unit, chunk);
            if (reason != null && dropped++ == 0) {
                firstReason = reason;
            }
        }
        if (dropped > 0) {
            LOG.warn("dropped {} vectors that are not finite base64 float32, first: {}", dropped,
                    firstReason);
        }
        return units;
    }

    /** The first chunk to set a payload field wins. Returns why its vector was dropped, or null. */
    private static String merge(Unit unit, JsonNode chunk) {
        ObjectNode payload = unit.payload();
        if (chunk.hasNonNull("text") && !payload.has("text")) {
            payload.put("text", chunk.get("text").asText());
        }
        JsonNode locators = chunk.get("locators");
        if (locators != null && locators.isObject()) {
            for (LocatorField f : LOCATOR_FIELDS) {
                JsonNode locator = first(locators, f.locator());
                if (locator != null) {
                    setIfAbsent(payload, f.payloadKey(), locator.get(f.field()));
                }
            }
        }
        if (chunk.hasNonNull("vector") && chunk.get("vector").isTextual()) {
            String name = vectorName(chunk);
            try {
                unit.vectors().put(name, floats(chunk.get("vector").asText()));
            } catch (IllegalArgumentException e) {
                return name + ": " + e.getMessage();
            }
        }
        return null;
    }

    private static String vectorName(JsonNode chunk) {
        return chunk.hasNonNull("producer") ? chunk.get("producer").asText() : DEFAULT_VECTOR_NAME;
    }

    private static void setIfAbsent(ObjectNode payload, String field, JsonNode value) {
        if (value != null && !value.isNull() && !payload.has(field)) {
            payload.set(field, value);
        }
    }

    private static JsonNode first(JsonNode locators, String kind) {
        JsonNode list = locators.get(kind);
        return list != null && list.isArray() && !list.isEmpty() ? list.get(0) : null;
    }

    /** Decodes base64 big-endian float32, the encoding tika-inference writes. */
    static float[] floats(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException(bytes.length + " bytes is not a float32 array");
        }
        float[] values = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).asFloatBuffer().get(values);
        for (float f : values) {
            // JSON has no NaN or Infinity, and Qdrant rejects the whole request for one
            if (!Float.isFinite(f)) {
                throw new IllegalArgumentException("contains " + f);
            }
        }
        return values;
    }

    /** Returns the response body. */
    private String send(HttpEntityEnclosingRequestBase request, String json)
            throws IOException, TikaClientException {
        request.setEntity(new StringEntity(json, StandardCharsets.UTF_8));
        request.setHeader("Accept", "application/json");
        request.setHeader("Content-type", "application/json; charset=utf-8");
        if (!StringUtils.isEmpty(config.apiKey())) {
            // not api-key: HttpClientFactory strips only Authorization on a cross-host redirect
            request.setHeader("Authorization", "Bearer " + config.apiKey());
        }
        try {
            HttpResponse response = httpClient.execute(request);
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? ""
                    : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status != 200) {
                throw new TikaClientException("Qdrant " + request.getMethod() + " "
                        + request.getURI().getPath() + " returned " + status + ": "
                        + (body.length() > MAX_ERROR_CHARS
                                ? body.substring(0, MAX_ERROR_CHARS) + "..." : body));
            }
            return body;
        } finally {
            request.releaseConnection();
        }
    }
}
