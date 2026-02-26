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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class GeminiVLMParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TikaTestHttpServer server;
    private GeminiVLMParser parser;
    private VLMOCRConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();

        config = new VLMOCRConfig();
        config.setBaseUrl(server.url());
        config.setModel("gemini-2.5-flash");
        config.setPrompt("Extract all text from this document.");
        config.setMaxTokens(4096);
        config.setTimeoutSeconds(10);
        config.setApiKey("test-gemini-key");

        // Queue 200 for the GET /v1beta/models health check in initialize()
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"models\":[]}"));
        parser = new GeminiVLMParser(config);
        parser.initialize();
        server.clearRequests(); // discard the health-check request from the log
    }

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void testSuccessfulImageOcr() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildGeminiResponse("Hello from Gemini!", 80, 15)));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        BodyContentHandler handler = new BodyContentHandler();

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'}))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("Hello from Gemini!"));
        assertEquals("gemini-2.5-flash", metadata.get(AbstractVLMParser.VLM_MODEL));
        assertEquals("80", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("15", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        assertTrue(request.path().contains("/v1beta/models/gemini-2.5-flash:generateContent"));
        assertTrue(request.path().contains("key=test-gemini-key"));
        assertEquals("POST", request.method());

        JsonNode body = MAPPER.readTree(request.body());
        JsonNode contents = body.get("contents");
        assertNotNull(contents);
        assertEquals(1, contents.size());

        JsonNode parts = contents.get(0).get("parts");
        assertEquals(2, parts.size());
        assertEquals("Extract all text from this document.", parts.get(0).get("text").asText());

        JsonNode inlineData = parts.get(1).get("inline_data");
        assertNotNull(inlineData);
        assertEquals("image/png", inlineData.get("mime_type").asText());
        assertNotNull(inlineData.get("data").asText());

        assertEquals(4096, body.get("generationConfig").get("maxOutputTokens").asInt());
    }

    @Test
    void testPdfSupport() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildGeminiResponse("PDF content extracted", 200, 50)));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");
        BodyContentHandler handler = new BodyContentHandler();

        byte[] fakePdf = "%PDF-1.4 fake content".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(fakePdf))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("PDF content extracted"));

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.body());
        JsonNode inlineData =
                body.get("contents").get(0).get("parts").get(1).get("inline_data");
        assertEquals("application/pdf", inlineData.get("mime_type").asText());
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
                .anyMatch(mt -> mt.toString().contains("heic")));
    }

    @Test
    void testApiKeyAsQueryParam() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildGeminiResponse("ok", 10, 5)));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        assertTrue(request.path().contains("key=test-gemini-key"),
                "API key should be in query params, not header");
        // Gemini does NOT use Bearer auth
        assertEquals(null, request.header("authorization"));
    }

    @Test
    void testServerError() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(500,
                "{\"error\":{\"message\":\"internal\"}}"));

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
    void testGeminiErrorResponse() {
        String errorJson = "{\"error\":{\"code\":400,\"message\":\"Invalid API key\"}}";
        assertThrows(TikaException.class,
                () -> parser.extractResponseText(errorJson, new Metadata()));
    }

    @Test
    void testExtractResponseTextMultipleParts() throws Exception {
        String json = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"Part one\"},"
                + "{\"text\":\"Part two\"}"
                + "],\"role\":\"model\"}}],"
                + "\"usageMetadata\":{\"promptTokenCount\":50,\"candidatesTokenCount\":20}}";

        Metadata metadata = new Metadata();
        String result = parser.extractResponseText(json, metadata);
        assertEquals("Part one\nPart two", result);
        assertEquals("50", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("20", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));
    }

    @Test
    void testBuildRequestJson() {
        String json = parser.buildRequestJson(config, "AAAA", "application/pdf");
        assertTrue(json.contains("\"mime_type\":\"application/pdf\""));
        assertTrue(json.contains("\"data\":\"AAAA\""));
        assertTrue(json.contains("\"maxOutputTokens\":4096"));
        assertTrue(json.contains("Extract all text from this document."));
        assertTrue(!json.contains("\"messages\""));
        assertTrue(!json.contains("\"max_tokens\""));
    }

    @Test
    void testSkipOcr() throws Exception {
        config.setSkipOcr(true);
        parser = new GeminiVLMParser(config);

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, "image/png");
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testDefaultConfig() {
        GeminiVLMParser defaultParser = new GeminiVLMParser();
        assertEquals("https://generativelanguage.googleapis.com", defaultParser.getBaseUrl());
        assertEquals("gemini-2.5-flash", defaultParser.getModel());
    }

    private String buildGeminiResponse(String text, int promptTokens, int completionTokens) {
        return String.format(java.util.Locale.ROOT,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"%s\"}],"
                        + "\"role\":\"model\"},\"finishReason\":\"STOP\"}],"
                        + "\"usageMetadata\":{\"promptTokenCount\":%d,"
                        + "\"candidatesTokenCount\":%d,\"totalTokenCount\":%d}}",
                text.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n"),
                promptTokens, completionTokens, promptTokens + completionTokens);
    }
}
