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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class OpenAIVLMParserTest {

    @Test
    public void testSetMaxRetriesReachesTheClient() throws Exception {
        OpenAIVLMParser parser = new OpenAIVLMParser();
        assertEquals(4, parser.httpClient().getMaxRetries());
        parser.setMaxRetries(0);
        assertEquals(0, parser.httpClient().getMaxRetries());
    }


    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TikaTestHttpServer server;
    private OpenAIVLMParser parser;
    private VLMOCRConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();

        config = new VLMOCRConfig();
        config.setBaseUrl(server.url());
        config.setModel("test-model");
        config.setPrompt("Extract text from this image.");
        config.setMaxTokens(1024);
        config.setTimeoutMillis(10_000);

        // Queue 200 for the GET /v1/models health check in initialize()
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"object\":\"list\"}"));
        parser = new OpenAIVLMParser(config);
        parser.initialize();
        server.clearRequests(); // discard the health-check request from the log
    }

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void testSuccessfulOcr() throws Exception {
        String ocrText = "Hello, World!\nThis is extracted text.";

        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildChatResponse(ocrText, 100, 20)));

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");

        BodyContentHandler handler = new BodyContentHandler();
        byte[] fakeImage = new byte[]{(byte) 0x89, 'P', 'N', 'G'};

        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(fakeImage))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("Hello, World!"));
        assertEquals("test-model", metadata.get(AbstractVLMParser.VLM_MODEL));
        assertEquals("100", metadata.get(AbstractVLMParser.VLM_PROMPT_TOKENS));
        assertEquals("20", metadata.get(AbstractVLMParser.VLM_COMPLETION_TOKENS));

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        assertEquals("/v1/chat/completions", request.path());
        assertEquals("POST", request.method());

        JsonNode body = MAPPER.readTree(request.body());
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
        server.enqueue(new TikaTestHttpServer.MockResponse(500, "{\"error\":\"boom\"}"));

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");

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
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
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
        reinitialize();

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[10]))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
        assertEquals("file-too-small", metadata.get(AbstractVLMParser.VLM_SKIPPED_REASON));
    }

    @Test
    void testFileTooLargeRecordsSkip() throws Exception {
        config.setMaxFileSizeToOcr(5);
        reinitialize();

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[10]))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
        assertEquals("file-too-large", metadata.get(AbstractVLMParser.VLM_SKIPPED_REASON));
    }

    @Test
    void testImageTooLargeRecordsSkip() throws Exception {
        config.setMaxImagePixels(3);
        reinitialize();

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(png(2, 2)))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals(0, server.getRequestCount());
        assertEquals("image-too-large", metadata.get(AbstractVLMParser.VLM_SKIPPED_REASON));
        assertEquals("4", metadata.get(AbstractVLMParser.VLM_IMAGE_PIXELS));
    }

    private void reinitialize() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"object\":\"list\"}"));
        parser = new OpenAIVLMParser(config);
        parser.initialize();
        server.clearRequests();
    }

    /** A white image with a black block over its left third, so it is not blank. */
    private static byte[] png(int width, int height) throws Exception {
        return png(width, height, true);
    }

    private static byte[] png(int width, int height, boolean ink) throws Exception {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, width, height);
        if (ink) {
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(0, 0, Math.max(1, width / 3), height);
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void testApiKeyHeader() throws Exception {
        config.setApiKey("sk-test-key");
        // Default completions path -> initialize() will probe /v1/models.
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"object\":\"list\"}"));
        parser = new OpenAIVLMParser(config);
        parser.initialize();
        server.clearRequests();

        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildChatResponse("text", 10, 5)));

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        assertEquals("Bearer sk-test-key", server.takeRequest().header("authorization"));
    }

    @Test
    void testAzureStyleAuth() throws Exception {
        config.setApiKey("azure-key-123");
        config.setCompletionsPath("/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01");
        parser = new OpenAIVLMParser(config);
        parser.setApiKeyHeaderName("api-key");
        parser.setApiKeyPrefix("");
        parser.setCompletionsPath(
                "/openai/deployments/gpt-4o/chat/completions?api-version=2024-02-01");
        // Non-default completions path -> health check is skipped.
        parser.initialize();

        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildChatResponse("text", 10, 5)));

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xD8}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        assertEquals("azure-key-123", request.header("api-key"));
        assertNull(request.header("authorization"));
        assertTrue(request.path().startsWith(
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
        override.setBaseUrl(server.url());
        override.setModel("override-model");
        override.setPrompt("Custom.");
        override.setMaxTokens(2048);
        override.setTimeoutMillis(10_000);

        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                buildChatResponse("ok", 10, 5)));

        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        ParseContext ctx = new ParseContext();
        ctx.set(VLMOCRConfig.class, override);

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{1, 2}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, ctx);
        }

        JsonNode body = MAPPER.readTree(server.takeRequest().body());
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
    void testTemperatureSentOnlyWhenSet() {
        assertFalse(parser.buildRequestJson(config, "AAAA", "image/png").contains("temperature"));
        config.setTemperature(0.0);
        assertTrue(parser.buildRequestJson(config, "AAAA", "image/png")
                .contains("\"temperature\":0.0"));
    }

    @Test
    void testTemperatureOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> config.setTemperature(-0.1));
        assertThrows(IllegalArgumentException.class, () -> config.setTemperature(2.1));
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

    /**
     * No VLM parser may auto-register via SPI; VLM parsers must be selected by name
     * in config (TIKA-4871).
     */
    @Test
    void testNoVlmParserAutoRegisters() throws Exception {
        java.util.Enumeration<java.net.URL> resources = getClass().getClassLoader()
                .getResources("META-INF/services/org.apache.tika.parser.Parser");
        while (resources.hasMoreElements()) {
            java.net.URL url = resources.nextElement();
            String content = new String(url.openStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(!content.contains("org.apache.tika.parser.vlm."),
                    "VLM parser auto-registered via SPI in " + url + ":\n" + content);
        }
    }

    private String buildChatResponse(String content, int prompt, int completion) {
        return String.format(java.util.Locale.ROOT,
                "{\"choices\":[{\"message\":{\"content\":\"%s\"}}],"
                        + "\"usage\":{\"prompt_tokens\":%d,\"completion_tokens\":%d}}",
                content.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n"),
                prompt, completion);
    }

    private Metadata parseImage(String responseBody, ParseContext context, Metadata metadata)
            throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, responseBody));
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'}))) {
            parser.parse(tis, new BodyContentHandler(), metadata, context);
        }
        return metadata;
    }

    @Test
    void testNoWarningsOnOrdinaryText() throws Exception {
        Metadata m = parseImage(buildChatResponse("Hello, World!", 10, 5),
                new ParseContext(), new Metadata());
        assertEquals(0, m.getValues(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS).length);
    }

    @Test
    void testTruncatedWarning() throws Exception {
        String body = "{\"choices\":[{\"finish_reason\":\"length\","
                + "\"message\":{\"content\":\"cut off\"}}]}";
        Metadata m = parseImage(body, new ParseContext(), new Metadata());
        assertEquals("truncated", m.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    void testEmptyWarning() throws Exception {
        Metadata m = parseImage(buildChatResponse("  ", 10, 5), new ParseContext(),
                new Metadata());
        assertEquals("empty", m.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    void testRepetitionWarningKeepsText() throws Exception {
        String loop = "SUEZ (D)\n".repeat(400);
        server.enqueue(new TikaTestHttpServer.MockResponse(200, buildChatResponse(loop, 10, 5)));
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/png");
        BodyContentHandler handler = new BodyContentHandler(-1);
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(new byte[]{(byte) 0x89, 'P', 'N', 'G'}))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }
        assertEquals("repetition", metadata.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
        assertTrue(handler.toString().contains("SUEZ (D)"));
    }

    @Test
    void testAShortLoopIsARepetition() {
        // 1,000 to 2,000 bytes: a loop the model's token cap cuts off early
        String loop = "TAPAN ".repeat(250);
        assertTrue(loop.length() >= 1000 && loop.length() < 2000);
        assertTrue(AbstractVLMParser.looksLikeLoop(loop));
        assertFalse(AbstractVLMParser.looksLikeLoop("TAPAN ".repeat(100)));
    }

    @Test
    void testProseIsNotARepetition() {
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 600; i++) {
            sb.append(Integer.toString(random.nextInt(1_000_000), 36)).append(' ');
        }
        assertFalse(AbstractVLMParser.looksLikeLoop(sb.toString()));
        assertFalse(AbstractVLMParser.looksLikeLoop("short ".repeat(10)));
        // 1,000 to 2,000 bytes of ordinary varied text
        assertFalse(AbstractVLMParser.looksLikeLoop(sb.substring(0, 1500)));
    }

    @Test
    void testInlineImageFlagsTheParent() throws Exception {
        Metadata parent = new Metadata();
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.extractor.ParentMetadata.class,
                new org.apache.tika.extractor.ParentMetadata(parent));
        Metadata child = new Metadata();
        child.set(org.apache.tika.metadata.TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                org.apache.tika.metadata.TikaCoreProperties.EmbeddedResourceType.INLINE.name());
        parseImage(buildChatResponse(" ", 10, 5), context, child);
        assertEquals("empty", child.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
        assertEquals("empty", parent.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    void testNonInlineImageLeavesTheParentAlone() throws Exception {
        Metadata parent = new Metadata();
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.extractor.ParentMetadata.class,
                new org.apache.tika.extractor.ParentMetadata(parent));
        parseImage(buildChatResponse(" ", 10, 5), context, new Metadata());
        assertNull(parent.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
    }

    @Test
    void testSkippedInlineImageFlagsTheParent() throws Exception {
        config.setMinFileSizeToOcr(100);
        reinitialize();
        Metadata parent = new Metadata();
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.extractor.ParentMetadata.class,
                new org.apache.tika.extractor.ParentMetadata(parent));
        Metadata child = new Metadata();
        child.set(org.apache.tika.metadata.TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                org.apache.tika.metadata.TikaCoreProperties.EmbeddedResourceType.INLINE.name());
        child.set(HttpHeaders.CONTENT_TYPE, "image/png");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(new byte[10]))) {
            parser.parse(tis, new BodyContentHandler(), child, context);
        }
        assertEquals("file-too-small", child.get(AbstractVLMParser.VLM_SKIPPED_REASON));
        assertEquals("skipped:file-too-small", parent.get(org.apache.tika.metadata.TikaCoreProperties.OCR_WARNINGS));
    }

    /** The capability follows the configuration: a captioning prompt is not a recognizer. */
    @Test
    void testRecognizesTextFollowsConfig() {
        assertTrue(parser.recognizesText(new ParseContext()));

        VLMOCRConfig captioner = new VLMOCRConfig();
        captioner.setTextRecognizer(false);
        ParseContext context = new ParseContext();
        context.set(VLMOCRConfig.class, captioner);
        assertFalse(parser.recognizesText(context));

        VLMOCRConfig skipped = new VLMOCRConfig();
        skipped.setSkipOcr(true);
        context.set(VLMOCRConfig.class, skipped);
        assertFalse(parser.recognizesText(context));
    }
}
