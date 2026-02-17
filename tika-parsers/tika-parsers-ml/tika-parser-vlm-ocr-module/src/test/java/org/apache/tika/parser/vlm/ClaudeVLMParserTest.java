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
package org.apache.tika.parser.vlm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

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
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class ClaudeVLMParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private ClaudeVLMParser parser;
    private VLMOCRConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        config = new VLMOCRConfig();
        config.setBaseUrl(server.url("").toString().replaceAll("/+$", ""));
        config.setModel("claude-sonnet-4-20250514");
        config.setPrompt("Extract all text.");
        config.setMaxTokens(4096);
        config.setTimeoutSeconds(10);
        config.setApiKey("sk-ant-test-key");

        parser = new ClaudeVLMParser(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void testSuccessfulImageOcr() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildClaudeResponse("Hello from Claude!", 200, 30))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        BodyContentHandler handler = new BodyContentHandler();

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'}))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("Hello from Claude!"));
        assertEquals("claude-sonnet-4-20250514",
                metadata.get(AbstractVLMParser.VLM_MODEL));
        assertEquals("200", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("30", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/messages", request.getPath());
        assertEquals("POST", request.getMethod());
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
        assertEquals("sk-ant-test-key", request.getHeader("x-api-key"));
        // Claude does NOT use Bearer auth
        assertNull(request.getHeader("Authorization"));

        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("claude-sonnet-4-20250514", body.get("model").asText());
        assertEquals(4096, body.get("max_tokens").asInt());

        JsonNode messages = body.get("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).get("role").asText());

        JsonNode parts = messages.get(0).get("content");
        assertEquals(2, parts.size());

        // First part: image
        JsonNode imagePart = parts.get(0);
        assertEquals("image", imagePart.get("type").asText());
        JsonNode source = imagePart.get("source");
        assertEquals("base64", source.get("type").asText());
        assertEquals("image/png", source.get("media_type").asText());
        assertNotNull(source.get("data").asText());

        // Second part: text prompt
        assertEquals("text", parts.get(1).get("type").asText());
        assertEquals("Extract all text.", parts.get(1).get("text").asText());
    }

    @Test
    void testPdfSupport() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildClaudeResponse("PDF text extracted by Claude", 500, 60))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        BodyContentHandler handler = new BodyContentHandler();
        byte[] fakePdf = "%PDF-1.4 fake".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(fakePdf))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("PDF text extracted by Claude"));

        RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        JsonNode parts = body.get("messages").get(0).get("content");

        // For PDFs, the content type should be "document" not "image"
        assertEquals("document", parts.get(0).get("type").asText());
        assertEquals("application/pdf",
                parts.get(0).get("source").get("media_type").asText());
    }

    @Test
    void testSupportedTypesIncludesPdf() {
        assertTrue(parser.getSupportedTypes(new ParseContext())
                .contains(MediaType.application("pdf")));
    }

    @Test
    void testSupportedTypesIncludesImages() {
        ParseContext ctx = new ParseContext();
        assertTrue(parser.getSupportedTypes(ctx).stream()
                .anyMatch(mt -> mt.toString().contains("png")));
        assertTrue(parser.getSupportedTypes(ctx).stream()
                .anyMatch(mt -> mt.toString().contains("jpeg")));
        assertTrue(parser.getSupportedTypes(ctx).stream()
                .anyMatch(mt -> mt.toString().contains("gif")));
        assertTrue(parser.getSupportedTypes(ctx).stream()
                .anyMatch(mt -> mt.toString().contains("webp")));
    }

    @Test
    void testApiKeyAsXApiKeyHeader() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(buildClaudeResponse("ok", 10, 5))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        RecordedRequest request = server.takeRequest();
        assertEquals("sk-ant-test-key", request.getHeader("x-api-key"));
        assertNull(request.getHeader("Authorization"));
        assertEquals("2023-06-01", request.getHeader("anthropic-version"));
    }

    @Test
    void testServerError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"error\":{\"type\":\"server_error\",\"message\":\"boom\"}}"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        assertThrows(TikaException.class, () -> {
            try (TikaInputStream tis = TikaInputStream.get(
                    new ByteArrayInputStream(new byte[]{1, 2, 3}))) {
                parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
            }
        });
    }

    @Test
    void testClaudeErrorResponse() {
        String errorJson =
                "{\"type\":\"error\","
                        + "\"error\":{\"type\":\"authentication_error\","
                        + "\"message\":\"Invalid API key\"}}";
        assertThrows(TikaException.class,
                () -> parser.extractResponseText(errorJson, new Metadata()));
    }

    @Test
    void testExtractResponseTextMultipleBlocks() throws Exception {
        String json = "{\"content\":["
                + "{\"type\":\"text\",\"text\":\"Block one\"},"
                + "{\"type\":\"text\",\"text\":\"Block two\"}"
                + "],\"usage\":{\"input_tokens\":100,\"output_tokens\":40},"
                + "\"stop_reason\":\"end_turn\"}";

        Metadata metadata = new Metadata();
        String result = parser.extractResponseText(json, metadata);
        assertEquals("Block one\nBlock two", result);
        assertEquals("100", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("40", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));
    }

    @Test
    void testBuildRequestJsonImage() {
        String json = parser.buildRequestJson(config, "AAAA", "image/png");
        assertFalse(json.contains("\"document\""));
        assertTrue(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"media_type\":\"image/png\""));
        assertTrue(json.contains("\"data\":\"AAAA\""));
        assertTrue(json.contains("\"max_tokens\":4096"));
    }

    @Test
    void testBuildRequestJsonPdf() {
        String json = parser.buildRequestJson(config, "AAAA", "application/pdf");
        assertTrue(json.contains("\"type\":\"document\""));
        assertFalse(json.contains("\"type\":\"image\""));
        assertTrue(json.contains("\"media_type\":\"application/pdf\""));
    }

    @Test
    void testSkipOcr() throws Exception {
        config.setSkipOcr(true);
        parser = new ClaudeVLMParser(config);

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testDefaultConfig() {
        ClaudeVLMParser defaultParser = new ClaudeVLMParser();
        assertEquals("https://api.anthropic.com", defaultParser.getBaseUrl());
        assertEquals("claude-sonnet-4-20250514", defaultParser.getModel());
    }

    private String buildClaudeResponse(String text, int inputTokens, int outputTokens) {
        return String.format(java.util.Locale.ROOT,
                "{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"%s\"}],"
                        + "\"stop_reason\":\"end_turn\","
                        + "\"usage\":{\"input_tokens\":%d,\"output_tokens\":%d}}",
                text.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n"),
                inputTokens, outputTokens);
    }
}
