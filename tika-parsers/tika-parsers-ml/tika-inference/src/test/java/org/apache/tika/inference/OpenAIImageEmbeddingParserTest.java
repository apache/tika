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

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.parser.ParseContext;

public class OpenAIImageEmbeddingParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private OpenAIImageEmbeddingParser parser;
    private ImageEmbeddingConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        config = new ImageEmbeddingConfig();
        config.setBaseUrl(server.url("").toString().replaceAll("/+$", ""));
        config.setModel("jina-clip-v2");
        config.setTimeoutSeconds(10);

        parser = new OpenAIImageEmbeddingParser(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        parser.close();
        server.shutdown();
    }

    @Test
    void testEndToEnd() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(3))
                .setHeader("Content-Type", "application/json"));

        // Fake 1x1 PNG bytes (just needs to be non-empty)
        byte[] fakeImage = new byte[]{(byte) 0x89, 'P', 'N', 'G'};

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/ocr-png");

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        String output = metadata.get(TikaCoreProperties.TIKA_CHUNKS);
        assertNotNull(output, "Should have tika:chunks");

        List<Chunk> chunks = ChunkSerializer.fromJson(output);
        assertEquals(1, chunks.size());
        assertNull(chunks.get(0).getText());
        assertNotNull(chunks.get(0).getVector());
        assertEquals(3, chunks.get(0).getVector().length);

        // Verify request format
        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/embeddings", request.getPath());
        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("jina-clip-v2", body.get("model").asText());
        assertTrue(body.get("input").get(0).get("image").asText()
                .startsWith("data:image/png;base64,"));
    }

    @Test
    void testPageNumberLocator() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(2))
                .setHeader("Content-Type", "application/json"));

        byte[] fakeImage = new byte[]{1, 2, 3};

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/ocr-png");
        metadata.set(TikaPagedText.PAGE_NUMBER, 7);

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        List<Chunk> chunks = ChunkSerializer.fromJson(
                metadata.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(1, chunks.size());

        assertNotNull(chunks.get(0).getLocators().getPaginated());
        assertEquals(1, chunks.get(0).getLocators().getPaginated().size());
        assertEquals(7,
                chunks.get(0).getLocators().getPaginated().get(0).getPage());
    }

    @Test
    void testOcrPrefixStripped() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(2))
                .setHeader("Content-Type", "application/json"));

        byte[] fakeImage = new byte[]{1, 2, 3};

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/ocr-jpeg");

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        // Should strip "ocr-" prefix: image/ocr-jpeg -> image/jpeg
        assertTrue(body.get("input").get(0).get("image").asText()
                .startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void testApiKeyHeader() throws Exception {
        config.setApiKey("sk-test-clip-key");
        parser.close();
        parser = new OpenAIImageEmbeddingParser(config);

        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(2))
                .setHeader("Content-Type", "application/json"));

        byte[] fakeImage = new byte[]{1};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        assertEquals("Bearer sk-test-clip-key",
                server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void testSkipEmbedding() throws Exception {
        config.setSkipEmbedding(true);
        parser.close();
        parser = new OpenAIImageEmbeddingParser(config);

        byte[] fakeImage = new byte[]{1, 2};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        assertNull(metadata.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testFileSizeFilter() throws Exception {
        config.setMinFileSizeToEmbed(100);
        parser.close();
        parser = new OpenAIImageEmbeddingParser(config);

        // 4 bytes -- below minimum
        byte[] tinyImage = new byte[]{1, 2, 3, 4};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(tinyImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        assertNull(metadata.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testServerError() {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"internal error\"}"));

        byte[] fakeImage = new byte[]{1};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        assertThrows(TikaException.class, () -> {
            try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
                parser.parse(tis, new DefaultHandler(), metadata,
                        new ParseContext());
            }
        });
    }

    @Test
    void testMergeWithExistingChunks() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(4))
                .setHeader("Content-Type", "application/json"));

        byte[] fakeImage = new byte[]{1};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        // Pre-populate with a text chunk (simulating text chunker ran first)
        Chunk textChunk = new Chunk("existing text", 0, 13);
        textChunk.setVector(new float[]{0.1f, 0.2f});
        metadata.set(TikaCoreProperties.TIKA_CHUNKS,
                ChunkSerializer.toJson(List.of(textChunk)));

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        List<Chunk> merged = ChunkSerializer.fromJson(
                metadata.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(2, merged.size());
        // First chunk is the pre-existing text chunk
        assertEquals("existing text", merged.get(0).getText());
        assertNotNull(merged.get(0).getVector());
        // Second chunk is the image embedding (no text)
        assertNull(merged.get(1).getText());
        assertNotNull(merged.get(1).getVector());
        assertEquals(4, merged.get(1).getVector().length);
    }

    @Test
    void testSupportedTypes() {
        assertTrue(parser.getSupportedTypes(new ParseContext())
                .contains(MediaType.image("ocr-png")));
        assertTrue(parser.getSupportedTypes(new ParseContext())
                .contains(MediaType.image("ocr-jpeg")));
        assertTrue(parser.getSupportedTypes(new ParseContext())
                .contains(MediaType.image("webp")));
    }

    @Test
    void testSupportedTypesWhenSkipped() throws Exception {
        config.setSkipEmbedding(true);
        parser.close();
        parser = new OpenAIImageEmbeddingParser(config);
        assertTrue(parser.getSupportedTypes(new ParseContext()).isEmpty());
    }

    @Test
    void testBuildRequest() {
        String json = parser.buildRequest(config, "image/png", "AAAA");
        assertTrue(json.contains("\"model\":\"jina-clip-v2\""));
        assertTrue(json.contains("data:image/png;base64,AAAA"));
    }

    @Test
    void testParseResponse() throws Exception {
        float[] vector = parser.parseResponse(buildEmbeddingResponse(5));
        assertEquals(5, vector.length);
    }

    @Test
    void testVectorSerializedAsBase64() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildEmbeddingResponse(3))
                .setHeader("Content-Type", "application/json"));

        byte[] fakeImage = new byte[]{1};
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(fakeImage)) {
            parser.parse(tis, new DefaultHandler(), metadata,
                    new ParseContext());
        }

        String output = metadata.get(TikaCoreProperties.TIKA_CHUNKS);
        JsonNode array = MAPPER.readTree(output);
        // Vector should be base64-encoded string, not a JSON array
        String vectorField = array.get(0).get("vector").asText();
        assertNotNull(vectorField);
        float[] decoded = VectorSerializer.decode(vectorField);
        assertEquals(3, decoded.length);
    }

    private String buildEmbeddingResponse(int dims) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"object\":\"list\",\"data\":[");
        sb.append("{\"object\":\"embedding\",\"index\":0,\"embedding\":[");
        for (int d = 0; d < dims; d++) {
            if (d > 0) {
                sb.append(",");
            }
            sb.append(String.format(java.util.Locale.ROOT, "%.6f", 0.1 + d * 0.01));
        }
        sb.append("]}],\"model\":\"jina-clip-v2\",");
        sb.append("\"usage\":{\"prompt_tokens\":10,\"total_tokens\":10}}");
        return sb.toString();
    }

    // Local MediaType helper since we don't want to import tika-core's
    // MediaType in the static context of these asserts
    private static class MediaType {
        static org.apache.tika.mime.MediaType image(String subtype) {
            return org.apache.tika.mime.MediaType.image(subtype);
        }
    }
}
