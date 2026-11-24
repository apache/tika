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
package org.apache.tika.pipes.qdrant.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.plugins.TikaPluginManager;

/**
 * Runs the Qdrant emitter, loaded from its plugin zip, against a Qdrant container.
 * The chunks are written directly, so no inference engine is needed.
 */
@Testcontainers(disabledWithoutDocker = true)
public class QdrantTest {

    private static final DockerImageName QDRANT_IMAGE = DockerImageName.parse("qdrant/qdrant:v1.20.2");
    private static final String COLLECTION = "tika-chunks";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static GenericContainer<?> CONTAINER;

    @BeforeAll
    public static void setUp() {
        CONTAINER = new GenericContainer<>(QDRANT_IMAGE)
                .withExposedPorts(6333)
                .waitingFor(new HttpWaitStrategy().forPort(6333).forPath("/readyz")
                        .forStatusCode(200));
        CONTAINER.start();
    }

    @AfterAll
    public static void tearDown() {
        CONTAINER.close();
    }

    @BeforeEach
    public void createCollection() throws Exception {
        send("DELETE", "/collections/" + COLLECTION, null);
        JsonNode created = send("PUT", "/collections/" + COLLECTION,
                "{\"vectors\":{"
                        + "\"text-vectors\":{\"size\":3,\"distance\":\"Cosine\"},"
                        + "\"page-vectors\":{\"size\":2,\"distance\":\"Dot\"}}}");
        assertTrue(created.get("result").asBoolean(), created.toString());
    }

    @Test
    public void testVectorSearchRanksChunks(@TempDir Path dir) throws Exception {
        Emitter emitter = emitter(dir, "\"metadataFields\": [\"dc:title\"]");
        // cosine from the query [1, 0, 0]: near 0.99, mid 0.71, far 0
        List<Object[]> docs = new ArrayList<>(List.of(
                new Object[]{"near", new float[]{0.99f, 0.14f, 0f}},
                new Object[]{"mid", new float[]{1f, 1f, 0f}},
                new Object[]{"far", new float[]{0f, 1f, 0f}}));
        Collections.shuffle(docs);
        for (Object[] doc : docs) {
            String id = (String) doc[0];
            Metadata metadata = withChunks(chunk("text-vectors", (float[]) doc[1],
                    "\"text\":\"" + id + " text\""));
            metadata.set("dc:title", id + " title");
            metadata.setTrusted(TikaCoreProperties.TIKA_CONTENT.getName(), "the whole " + id);
            emitter.emit(id + ".txt", List.of(metadata), new ParseContext());
        }

        JsonNode points = query("[1.0, 0.0, 0.0]", "text-vectors", 3);
        assertEquals(3, points.size());
        assertEquals("near.txt", points.get(0).get("payload").get("container_id").asText());
        assertEquals("mid.txt", points.get(1).get("payload").get("container_id").asText());
        assertEquals("far.txt", points.get(2).get("payload").get("container_id").asText());

        JsonNode payload = points.get(0).get("payload");
        assertEquals("near text", payload.get("text").asText());
        assertEquals("near title", payload.get("metadata").get("dc:title").asText());
        assertFalse(payload.get("metadata").has(TikaCoreProperties.TIKA_CONTENT.getName()));
    }

    @Test
    public void testCorrelatedChunksShareOnePoint(@TempDir Path dir) throws Exception {
        String chunks = chunk("text-vectors", new float[]{1f, 0f, 0f},
                "\"text\":\"page one\",\"correlator\":\"p1\"")
                + "," + chunk("page-vectors", new float[]{1f, 0f}, "\"correlator\":\"p1\"")
                + "," + chunk("page-vectors", new float[]{0f, 1f}, "\"correlator\":\"p2\"");
        emitter(dir, null).emit("report.pdf", List.of(withChunks(chunks)), new ParseContext());

        assertEquals(2, count("report.pdf"));
        JsonNode byPage = query("[1.0, 0.0]", "page-vectors", 10);
        assertEquals(2, byPage.size());
        assertEquals("p1", byPage.get(0).get("payload").get("correlator").asText());
        assertEquals("page one", byPage.get(0).get("payload").get("text").asText());
        JsonNode byText = query("[1.0, 0.0, 0.0]", "text-vectors", 10);
        assertEquals(1, byText.size(), "the p2 point has no text vector");
        assertEquals(byPage.get(0).get("id"), byText.get(0).get("id"));
    }

    @Test
    public void testReEmitReplacesEarlierPoints(@TempDir Path dir) throws Exception {
        Emitter emitter = emitter(dir, "\"batchSize\": 2");
        emitter.emit("a.txt", List.of(withChunks(textChunks(5))), new ParseContext());
        emitter.emit("b.txt", List.of(withChunks(textChunks(1))), new ParseContext());
        assertEquals(5, count("a.txt"));

        emitter.emit("a.txt", List.of(withChunks(textChunks(2))), new ParseContext());
        assertEquals(2, count("a.txt"));
        assertEquals(1, count("b.txt"), "a re-emit leaves other containers alone");

        emitter.emit("a.txt", List.of(new Metadata()), new ParseContext());
        assertEquals(2, count("a.txt"), "an emit without points keeps the earlier points");

        Emitter keep = emitter(dir, "\"replaceExisting\": false");
        keep.emit("b.txt", List.of(withChunks(textChunks(3))), new ParseContext());
        keep.emit("b.txt", List.of(withChunks(textChunks(1))), new ParseContext());
        assertEquals(3, count("b.txt"), "without replaceExisting, surplus points remain");
    }

    @Test
    public void testStaleDeletePagesThroughManyPoints(@TempDir Path dir) throws Exception {
        Emitter emitter = emitter(dir, null);
        emitter.emit("many.txt", List.of(withChunks(textChunks(600))), new ParseContext());
        assertEquals(600, count("many.txt"));
        emitter.emit("many.txt", List.of(withChunks(textChunks(300))), new ParseContext());
        assertEquals(300, count("many.txt"));
    }

    @Test
    public void testStrictModeNeedsTheContainerIndex(@TempDir Path dir) throws Exception {
        String strict = "strict";
        send("DELETE", "/collections/" + strict, null);
        send("PUT", "/collections/" + strict, "{\"vectors\":{\"text-vectors\":{\"size\":3,"
                + "\"distance\":\"Cosine\"}},\"strict_mode_config\":{\"enabled\":true,"
                + "\"unindexed_filtering_retrieve\":false,\"unindexed_filtering_update\":false}}");
        Emitter emitter = emitter(dir, url(CONTAINER), strict, null);
        IOException e = assertThrows(IOException.class,
                () -> emitter.emit("a.txt", List.of(withChunks(textChunks(3))), new ParseContext()));
        assertTrue(e.getMessage().contains("Index required"), e.getMessage());

        send("PUT", "/collections/" + strict + "/index?wait=true",
                "{\"field_name\":\"container_id\",\"field_schema\":\"keyword\"}");
        emitter.emit("a.txt", List.of(withChunks(textChunks(3))), new ParseContext());
        emitter.emit("a.txt", List.of(withChunks(textChunks(1))), new ParseContext());
        assertEquals(1, count(strict, "a.txt"));
    }

    @Test
    public void testRejectedReEmitKeepsEarlierPoints(@TempDir Path dir) throws Exception {
        Emitter emitter = emitter(dir, null);
        emitter.emit("x.txt", List.of(withChunks(textChunks(3))), new ParseContext());
        Metadata metadata = withChunks(chunk("no-such-vector", new float[]{1f}, null));
        IOException e = assertThrows(IOException.class,
                () -> emitter.emit("x.txt", List.of(metadata), new ParseContext()));
        assertTrue(e.getMessage().contains("no-such-vector"), e.getMessage());
        assertEquals(3, count("x.txt"));
    }

    @Test
    public void testApiKeyIsSentAsBearerToken(@TempDir Path dir) throws Exception {
        try (GenericContainer<?> secured = new GenericContainer<>(QDRANT_IMAGE)
                .withExposedPorts(6333)
                .withEnv("QDRANT__SERVICE__API_KEY", "test-key")
                .waitingFor(new HttpWaitStrategy().forPort(6333).forPath("/readyz")
                        .forStatusCode(200))) {
            secured.start();
            String url = url(secured);
            HttpResponse<String> created = HTTP.send(HttpRequest.newBuilder(
                            URI.create(url + "/collections/" + COLLECTION))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer test-key")
                    .PUT(HttpRequest.BodyPublishers.ofString(
                            "{\"vectors\":{\"text-vectors\":{\"size\":3,\"distance\":\"Cosine\"}}}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, created.statusCode(), created.body());

            Metadata metadata = withChunks(textChunks(1));
            emitter(dir, url, "\"apiKey\": \"test-key\"")
                    .emit("a.txt", List.of(metadata), new ParseContext());
            IOException e = assertThrows(IOException.class, () -> emitter(dir, url, null)
                    .emit("a.txt", List.of(metadata), new ParseContext()));
            assertTrue(e.getMessage().contains("401") || e.getMessage().contains("403"),
                    e.getMessage());
        }
    }

    private static String url(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(6333);
    }

    private static Emitter emitter(Path dir, String extraConfig) throws Exception {
        return emitter(dir, url(CONTAINER), extraConfig);
    }

    private static Emitter emitter(Path dir, String url, String extraConfig) throws Exception {
        return emitter(dir, url, COLLECTION, extraConfig);
    }

    private static Emitter emitter(Path dir, String url, String collection, String extraConfig)
            throws Exception {
        String json = "{\"emitters\":{\"qdrant\":{\"qdrant-emitter\":{"
                + "\"qdrantUrl\":\"" + url + "\",\"collection\":\"" + collection + "\""
                + (extraConfig == null ? "" : "," + extraConfig)
                + "}}},\"plugin-roots\":\"target/plugins\"}";
        Path config = Files.createTempFile(dir, "tika-config", ".json");
        Files.writeString(config, json, StandardCharsets.UTF_8);
        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(config);
        return EmitterManager.load(TikaPluginManager.load(tikaJsonConfig), tikaJsonConfig)
                .getEmitter().get();
    }

    private static Metadata withChunks(String chunks) {
        Metadata metadata = new Metadata();
        metadata.setTrusted(TikaCoreProperties.TIKA_CHUNKS.getName(), "[" + chunks + "]");
        return metadata;
    }

    private static String textChunks(int n) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            chunks.add(chunk("text-vectors", new float[]{1f, i, 0f}, null));
        }
        return String.join(",", chunks);
    }

    private static String chunk(String producer, float[] vector, String extra) {
        ByteBuffer bb = ByteBuffer.allocate(vector.length * Float.BYTES);
        for (float f : vector) {
            bb.putFloat(f);
        }
        return "{\"producer\":\"" + producer + "\",\"vector\":\""
                + Base64.getEncoder().encodeToString(bb.array()) + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    private static JsonNode query(String vector, String using, int limit) throws Exception {
        return send("POST", "/collections/" + COLLECTION + "/points/query",
                "{\"query\":" + vector + ",\"using\":\"" + using + "\",\"limit\":" + limit
                        + ",\"with_payload\":true}").get("result").get("points");
    }

    private static int count(String containerId) throws Exception {
        return count(COLLECTION, containerId);
    }

    private static int count(String collection, String containerId) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("exact", true);
        ArrayNode must = body.putObject("filter").putArray("must");
        must.addObject().put("key", "container_id").putObject("match").put("value", containerId);
        return send("POST", "/collections/" + collection + "/points/count", body.toString())
                .get("result").get("count").asInt();
    }

    private static JsonNode send(String method, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(CONTAINER) + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body());
    }
}
