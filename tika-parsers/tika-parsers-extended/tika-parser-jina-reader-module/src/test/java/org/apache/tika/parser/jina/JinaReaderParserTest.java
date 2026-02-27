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
package org.apache.tika.parser.jina;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

public class JinaReaderParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private JinaReaderParser parser;
    private JinaReaderConfig config;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        config = new JinaReaderConfig();
        config.setBaseUrl(server.url("/").toString());
        config.setApiKey("test-key");
        config.setTimeoutSeconds(10);

        parser = new JinaReaderParser(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
        parser.close();
    }

    @Test
    void testPdfParsing() throws Exception {
        String markdown = "# My PDF Title\n\nSome paragraph text.";
        server.enqueue(new MockResponse()
                .setBody(buildJinaResponse(markdown))
                .setHeader("Content-Type", "application/json"));

        byte[] fakePdf = "%PDF-1.4 fake pdf content".getBytes(StandardCharsets.UTF_8);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");

        BodyContentHandler handler = new BodyContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(fakePdf))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("My PDF Title"));
        assertTrue(handler.toString().contains("Some paragraph text."));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("Bearer test-key", request.getHeader("Authorization"));
        assertEquals("markdown", request.getHeader("X-Return-Format"));

        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertTrue(body.has("pdf"), "Request should have 'pdf' field");
        String decoded = new String(Base64.getDecoder().decode(body.get("pdf").asText()),
                StandardCharsets.UTF_8);
        assertTrue(decoded.startsWith("%PDF"));
    }

    @Test
    void testHtmlParsing() throws Exception {
        String markdown = "## Article Heading\n\nClean content here.";
        server.enqueue(new MockResponse()
                .setBody(buildJinaResponse(markdown))
                .setHeader("Content-Type", "application/json"));

        String html = "<html><body><nav>skip</nav><article><h1>Article</h1>"
                + "<p>Content</p></article></body></html>";
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html");

        BodyContentHandler handler = new BodyContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(handler.toString().contains("Article Heading"));

        RecordedRequest request = server.takeRequest();
        JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
        assertTrue(body.has("html"), "Request should have 'html' field");
        assertTrue(body.get("html").asText().contains("<html>"));
    }

    @Test
    void testApiError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":\"No URL provided\"}"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "application/pdf");

        assertThrows(TikaException.class, () -> {
            try (TikaInputStream tis = TikaInputStream.get(
                    new ByteArrayInputStream(new byte[]{1, 2, 3}))) {
                parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
            }
        });
    }

    @Test
    void testExtractContent() throws TikaException {
        String response = buildJinaResponse("Hello **world**");
        String content = parser.extractContent(response);
        assertEquals("Hello **world**", content);
    }

    @Test
    void testExtractContentMissingData() {
        assertThrows(TikaException.class,
                () -> parser.extractContent("{\"code\":200}"));
    }

    @Test
    void testSupportedTypes() {
        Set<org.apache.tika.mime.MediaType> types =
                parser.getSupportedTypes(new ParseContext());
        assertTrue(types.stream().anyMatch(mt -> mt.toString().equals("application/pdf")));
        assertTrue(types.stream().anyMatch(mt -> mt.toString().equals("text/html")));
    }

    @Test
    void testNoApiKeyHeader() throws Exception {
        config.setApiKey("");
        parser = new JinaReaderParser(config);

        server.enqueue(new MockResponse()
                .setBody(buildJinaResponse("content"))
                .setHeader("Content-Type", "application/json"));

        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html");

        try (TikaInputStream tis = TikaInputStream.get(
                new ByteArrayInputStream("<html/>".getBytes(StandardCharsets.UTF_8)))) {
            parser.parse(tis, new BodyContentHandler(), metadata, new ParseContext());
        }

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getHeader("Authorization") == null
                || request.getHeader("Authorization").isEmpty(),
                "No auth header expected when apiKey is blank");
    }

    private String buildJinaResponse(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n");
        return "{\"code\":200,\"data\":{\"content\":\"" + escaped + "\"}}";
    }
}
