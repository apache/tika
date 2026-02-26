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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class OpenAIEmbeddingFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private OpenAIEmbeddingFilter filter;
    private InferenceConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        config = new InferenceConfig();
        config.setBaseUrl(server.url("").toString().replaceAll("/+$", ""));
        config.setModel("text-embedding-3-small");
        config.setMaxChunkChars(500);
        config.setOverlapChars(0);
        config.setTimeoutSeconds(10);

        filter = new OpenAIEmbeddingFilter(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        filter.close();
        server.shutdown();
    }

    @Test
    void testEndToEnd() throws Exception {
        String content = "# Section A\n\nSome text about section A.\n\n"
                + "# Section B\n\nSome text about section B.";

        // Mock embeddings response with 2 vectors (3 dims each)
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(2, 3))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), content);

        List<Metadata> metadataList = new ArrayList<>();
        metadataList.add(metadata);
        filter.filter(metadataList);

        String chunksJson = metadata.get("tika:chunks");
        assertNotNull(chunksJson, "Should have tika:chunks");

        List<Chunk> chunks = ChunkSerializer.fromJson(chunksJson);
        assertEquals(2, chunks.size());

        assertTrue(chunks.get(0).getText().contains("Section A"));
        assertTrue(chunks.get(1).getText().contains("Section B"));
        assertNotNull(chunks.get(0).getVector());
        assertNotNull(chunks.get(1).getVector());
        assertEquals(3, chunks.get(0).getVector().length);

        // Verify the request
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/embeddings", request.getPath());
        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("text-embedding-3-small", body.get("model").asText());
        assertEquals(2, body.get("input").size());
    }

    @Test
    void testApiKeyHeader() throws Exception {
        config.setApiKey("sk-test-key");
        filter.close();
        filter = new OpenAIEmbeddingFilter(config);

        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(1, 3))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "Some text.");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        assertEquals("Bearer sk-test-key",
                server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void testEmptyContent() throws Exception {
        Metadata metadata = new Metadata();
        // No tika:content set
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        assertNull(metadata.get("tika:chunks"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testBlankContent() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "   ");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        assertNull(metadata.get("tika:chunks"));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testServerError() {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"error\":\"boom\"}"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "Some text.");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);

        assertThrows(TikaException.class, () -> filter.filter(list));
    }

    @Test
    void testBuildRequest() {
        List<Chunk> chunks = List.of(
                new Chunk("Hello", 0, 5),
                new Chunk("World", 6, 11));
        String json = filter.buildRequest(chunks, config);
        assertTrue(json.contains("\"model\":\"text-embedding-3-small\""));
        assertTrue(json.contains("\"Hello\""));
        assertTrue(json.contains("\"World\""));
    }

    @Test
    void testParseResponse() throws Exception {
        List<Chunk> chunks = List.of(
                new Chunk("A", 0, 1),
                new Chunk("B", 2, 3));

        filter.parseResponse(buildEmbeddingResponse(2, 4), chunks);

        assertNotNull(chunks.get(0).getVector());
        assertNotNull(chunks.get(1).getVector());
        assertEquals(4, chunks.get(0).getVector().length);
        assertEquals(4, chunks.get(1).getVector().length);
    }

    @Test
    void testVectorSerialization() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(1, 3))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "Single chunk of text.");
        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        String chunksJson = metadata.get("tika:chunks");
        JsonNode array = MAPPER.readTree(chunksJson);
        assertEquals(1, array.size());

        // Vector should be base64, not a JSON array
        String vectorField = array.get(0).get("vector").asText();
        assertNotNull(vectorField);
        // Should be decodable
        float[] decoded = VectorSerializer.decode(vectorField);
        assertEquals(3, decoded.length);
    }

    @Test
    void testMergeWithExistingChunks() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(1, 3))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.TIKA_CONTENT.getName(), "Some text.");

        // Pre-populate with an image chunk
        Chunk imgChunk = new Chunk(null,
                new org.apache.tika.inference.locator.Locators()
                        .addPaginated(
                                new org.apache.tika.inference.locator
                                        .PaginatedLocator(1)));
        imgChunk.setVector(new float[]{0.5f, 0.6f});
        metadata.set(TikaCoreProperties.TIKA_CHUNKS,
                ChunkSerializer.toJson(List.of(imgChunk)));

        List<Metadata> list = new ArrayList<>();
        list.add(metadata);
        filter.filter(list);

        List<Chunk> merged = ChunkSerializer.fromJson(
                metadata.get(TikaCoreProperties.TIKA_CHUNKS));
        // Should have the pre-existing image chunk + the new text chunk
        assertEquals(2, merged.size());
        // First is the image chunk (no text)
        assertNull(merged.get(0).getText());
        // Second is the text chunk
        assertNotNull(merged.get(1).getText());
        assertNotNull(merged.get(1).getVector());
    }

    /**
     * Build a mock OpenAI embeddings response.
     */
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
                sb.append(String.format(java.util.Locale.ROOT, "%.6f", (i + 1) * 0.1 + d * 0.01));
            }
            sb.append("]}");
        }
        sb.append("],\"model\":\"text-embedding-3-small\",");
        sb.append("\"usage\":{\"prompt_tokens\":10,\"total_tokens\":10}}");
        return sb.toString();
    }
}
