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
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class OpenAIVLMParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private OpenAIVLMParser parser;
    private VLMOCRConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        config = new VLMOCRConfig();
        config.setBaseUrl(server.url("").toString().replaceAll("/+$", ""));
        config.setModel("test-model");
        config.setPrompt("Extract text from this image.");
        config.setMaxTokens(1024);
        config.setTimeoutSeconds(10);

        parser = new OpenAIVLMParser(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void testSuccessfulOcr() throws Exception {
        String ocrText = "Hello, World!\nThis is extracted text.";

        server.enqueue(new MockResponse()
                .setBody(buildChatResponse(ocrText, 100, 20))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        BodyContentHandler handler = new BodyContentHandler();
        byte[] fakeImage = new byte[]{(byte) 0x89, 'P', 'N', 'G'};

        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(fakeImage))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("Hello, World!"));
        assertEquals("test-model", metadata.get(AbstractVLMParser.VLM_MODEL));
        assertEquals("100", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("20", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/chat/completions", request.getPath());
        assertEquals("POST", request.getMethod());

        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertEquals("test-model", body.get("model").asText());
        assertEquals(1024, body.get("max_tokens").asInt());

        JsonNode messages = body.get("messages");
        assertNotNull(messages);
        assertEquals("user", messages.get(0).get("role").asText());

        JsonNode parts = messages.get(0).get("content");
        assertEquals("text", parts.get(0).get("type").asText());
        assertEquals("image_url", parts.get(1).get("type").asText());
        assertTrue(parts.get(1).get("image_url").get("url").asText()
                .startsWith("data:image/png;base64,"));
    }

    @Test
    void testServerError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"error\":\"boom\"}"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        assertThrows(TikaException.class, () -> {
            try (TikaInputStream tis = TikaInputStream.get(
                    new ByteArrayInputStream(new byte[]{1, 2, 3, 4}))) {
                parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
            }
        });
    }

    @Test
    void testSkipOcr() throws Exception {
        config.setSkipOcr(true);
        parser = new OpenAIVLMParser(config);

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        BodyContentHandler handler = new BodyContentHandler();

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4}))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testFileSizeFiltering() throws Exception {
        config.setMinFileSizeToOcr(100);
        parser = new OpenAIVLMParser(config);

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[10]))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
    }

    @Test
    void testApiKeyHeader() throws Exception {
        config.setApiKey("sk-test-key");
        parser = new OpenAIVLMParser(config);

        server.enqueue(new MockResponse()
                .setBody(buildChatResponse("text", 10, 5))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals("Bearer sk-test-key", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    void testAzureStyleAuth() throws Exception {
        config.setApiKey("azure-key-123");
        parser = new OpenAIVLMParser(config);
        config.setCompletionsPath("/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01");
        parser = new OpenAIVLMParser(config);
        parser.setApiKeyHeaderName("api-key");
        parser.setApiKeyPrefix("");

        server.enqueue(new MockResponse()
                .setBody(buildChatResponse("text", 10, 5))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        var request = server.takeRequest();
        assertEquals("azure-key-123", request.getHeader("api-key"));
        assertNull(request.getHeader("Authorization"));
        assertTrue(request.getPath().startsWith(
                "/openai/deployments/gpt-4o/chat/completions"));
    }

    @Test
    void testCustomCompletionsPathSkipsHealthCheck() {
        config.setCompletionsPath("/custom/path");
        assertNull(parser.getHealthCheckUrl(config));
    }

    @Test
    void testDefaultCompletionsPathHasHealthCheck() {
        assertNotNull(parser.getHealthCheckUrl(config));
    }

    @Test
    void testPerRequestConfigOverride() throws Exception {
        VLMOCRConfig override = new VLMOCRConfig();
        override.setBaseUrl(server.url("").toString().replaceAll("/+$", ""));
        override.setModel("override-model");
        override.setPrompt("Custom.");
        override.setMaxTokens(2048);
        override.setTimeoutSeconds(10);

        server.enqueue(new MockResponse()
                .setBody(buildChatResponse("ok", 10, 5))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        ParseContext ctx = new ParseContext();
        ctx.set(VLMOCRConfig.class, override);

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, ctx);
        }

        JsonNode body = MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("override-model", body.get("model").asText());
        assertEquals(2048, body.get("max_tokens").asInt());
    }

    @Test
    void testBuildRequestJson() {
        String json = parser.buildRequestJson(config, "AAAA", "image/png");
        assertTrue(json.contains("\"model\":\"test-model\""));
        assertTrue(json.contains("data:image/png;base64,AAAA"));
    }

    @Test
    void testExtractResponseText() throws Exception {
        Metadata metadata = new Metadata();
        String result = parser.extractResponseText(
                buildChatResponse("Hello", 50, 10), metadata);
        assertEquals("Hello", result);
        assertEquals("50", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
    }

    @Test
    void testExtractResponseTextNoChoices() {
        assertThrows(TikaException.class,
                () -> parser.extractResponseText("{\"choices\":[]}", new Metadata()));
    }

    @Test
    void testSupportedTypes() {
        assertTrue(parser.getSupportedTypes(new ParseContext()).stream()
                .anyMatch(mt -> mt.toString().contains("png")));
    }

    @Test
    void testSupportedTypesWhenSkipped() {
        config.setSkipOcr(true);
        parser = new OpenAIVLMParser(config);
        assertEquals(0, parser.getSupportedTypes(new ParseContext()).size());
    }

    private String buildChatResponse(String content, int prompt, int completion) {
        return String.format(java.util.Locale.ROOT,
                "{\"choices\":[{\"message\":{\"content\":\"%s\"}}],"
                        + "\"usage\":{\"prompt_tokens\":%d,\"completion_tokens\":%d}}",
                content.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n"),
                prompt, completion);
    }
}
