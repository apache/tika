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
package org.apache.tika.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class JinaEmbeddingFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TikaTestHttpServer server;
    private JinaEmbeddingFilter filter;
    private InferenceConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();

        config = new InferenceConfig();
        config.setBaseUrl(server.url());
        config.setModel("jina-embeddings-v3");
        config.setMaxChunkChars(500);
        config.setOverlapChars(0);
        config.setTimeoutSeconds(10);

        filter = new JinaEmbeddingFilter(config);
    }

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void testDefaultTaskInRequest() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildEmbeddingResponse(1, 3)));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "Some document text.");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.body());
        assertEquals("retrieval.passage", body.get("task").asText(),
                "Default task should be retrieval.passage");
        assertEquals("jina-embeddings-v3", body.get("model").asText());
    }

    @Test
    void testCustomTask() throws Exception {
        filter.setTask("retrieval.query");
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildEmbeddingResponse(1, 3)));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "What is Tika?");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.body());
        assertEquals("retrieval.query", body.get("task").asText());
    }

    @Test
    void testBuildRequestJsonShape() {
        List<Chunk> chunks = List.of(
                new Chunk("Hello", 0, 5),
                new Chunk("World", 6, 11));
        String json = filter.buildRequest(chunks, config);

        assertTrue(json.contains("\"task\":\"retrieval.passage\""),
                "Should include task field: " + json);
        assertTrue(json.contains("\"model\":\"jina-embeddings-v3\""),
                "Should include model field: " + json);
        assertTrue(json.contains("\"Hello\""), "Should include first chunk");
        assertTrue(json.contains("\"World\""), "Should include second chunk");
    }

    @Test
    void testEndToEnd() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildEmbeddingResponse(2, 4)));

        String content = "# Introduction\n\nFirst section text.\n\n"
                + "# Background\n\nSecond section text.";

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), content);
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        String chunksJson = metadata.get("tika:chunks");
        assertNotNull(chunksJson, "Should have tika:chunks");

        List<Chunk> chunks = ChunkSerializer.fromJson(chunksJson);
        assertEquals(2, chunks.size());
        assertNotNull(chunks.get(0).getVector());
        assertNotNull(chunks.get(1).getVector());
        assertEquals(4, chunks.get(0).getVector().length);
    }

    private String buildEmbeddingResponse(int numVectors, int dims) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"object\":\"list\",\"data\":[");
        for (int i = 0; i < numVectors; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{\"object\":\"embedding\",\"index\":").append(i);
            sb.append(",\"embedding\":[");
            for (int d = 0; d < dims; d++) {
                if (d > 0) {
                    sb.append(",");
                }
                sb.append(String.format(java.util.Locale.ROOT,
                        "%.6f", (i + 1) * 0.1 + d * 0.01));
            }
            sb.append("]}");
        }
        sb.append("],\"model\":\"jina-embeddings-v3\",");
        sb.append("\"usage\":{\"prompt_tokens\":10,\"total_tokens\":10}}");
        return sb.toString();
    }
}
