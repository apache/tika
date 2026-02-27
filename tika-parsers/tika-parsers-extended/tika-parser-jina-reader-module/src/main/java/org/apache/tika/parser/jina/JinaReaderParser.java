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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * Parser that sends document content to the
 * <a href="https://jina.ai/reader/">Jina Reader API</a> for clean-text
 * extraction and returns the result as XHTML.
 * <p>
 * <b>Supported types:</b>
 * <ul>
 *   <li>{@code application/pdf} — bytes are base64-encoded and sent as
 *       {@code {"pdf": "<base64>"}}</li>
 *   <li>{@code text/html} — raw HTML string sent as
 *       {@code {"html": "<html>..."}}</li>
 * </ul>
 * <p>
 * Authentication: set {@code apiKey} in the config; it is sent as a
 * {@code Authorization: Bearer <key>} header.
 * <p>
 * Configuration key: {@code "jina-reader-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "jina-reader-parser")
public class JinaReaderParser implements Parser, Initializable, Closeable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(JinaReaderParser.class);

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<org.apache.tika.mime.MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    org.apache.tika.mime.MediaType.application("pdf"),
                    org.apache.tika.mime.MediaType.text("html")
            )));

    private final JinaReaderConfig config;
    private transient OkHttpClient httpClient;

    public JinaReaderParser() {
        this(new JinaReaderConfig());
    }

    public JinaReaderParser(JinaReaderConfig config) {
        this.config = config;
        buildHttpClient();
    }

    public JinaReaderParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, JinaReaderConfig.class));
    }

    // ---- Parser -----------------------------------------------------------

    @Override
    public Set<org.apache.tika.mime.MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        JinaReaderConfig cfg = context.get(JinaReaderConfig.class, config);

        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        boolean isPdf = contentType != null && contentType.startsWith("application/pdf");

        String requestJson = buildRequestJson(tis, isPdf);

        String markdown = callJinaApi(cfg, requestJson);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.startElement(XHTML, "div", "div", new org.xml.sax.helpers.AttributesImpl());
        MarkdownToXHTMLEmitter.emit(markdown, xhtml);
        xhtml.endElement(XHTML, "div", "div");
        xhtml.endDocument();
    }

    // ---- Initializable ----------------------------------------------------

    @Override
    public void initialize() {
        buildHttpClient();
    }

    // ---- Closeable --------------------------------------------------------

    @Override
    public void close() {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    // ---- helpers ----------------------------------------------------------

    String buildRequestJson(TikaInputStream tis, boolean isPdf) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        if (isPdf) {
            byte[] bytes = tis.readAllBytes();
            root.put("pdf", Base64.getEncoder().encodeToString(bytes));
        } else {
            String html = new String(tis.readAllBytes(), StandardCharsets.UTF_8);
            root.put("html", html);
        }
        return root.toString();
    }

    private String callJinaApi(JinaReaderConfig cfg, String requestJson) throws TikaException {
        Request.Builder builder = new Request.Builder()
                .url(cfg.getBaseUrl())
                .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Return-Format", cfg.getReturnFormat());

        if (!StringUtils.isBlank(cfg.getApiKey())) {
            builder.header("Authorization", "Bearer " + cfg.getApiKey());
        }

        Request request = builder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new TikaException(
                        "Jina Reader API request failed with HTTP "
                                + response.code() + ": " + body);
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            return extractContent(responseBody);
        } catch (IOException e) {
            throw new TikaException("Jina Reader API request failed: " + e.getMessage(), e);
        }
    }

    String extractContent(String responseBody) throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null) {
                throw new TikaException(
                        "Jina Reader API response missing 'data' field: " + responseBody);
            }
            JsonNode content = data.get("content");
            if (content == null || content.isNull()) {
                return "";
            }
            return content.asText();
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to parse Jina Reader API response: " + e.getMessage(), e);
        }
    }

    private void buildHttpClient() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // ---- config getters/setters for XML/JSON config wiring ----------------

    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    public void setBaseUrl(String baseUrl) throws org.apache.tika.exception.TikaConfigException {
        config.setBaseUrl(baseUrl);
    }

    public String getApiKey() {
        return config.getApiKey();
    }

    public void setApiKey(String apiKey) throws org.apache.tika.exception.TikaConfigException {
        config.setApiKey(apiKey);
    }

    public int getTimeoutSeconds() {
        return config.getTimeoutSeconds();
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        config.setTimeoutSeconds(timeoutSeconds);
    }

    public String getReturnFormat() {
        return config.getReturnFormat();
    }

    public void setReturnFormat(String returnFormat) {
        config.setReturnFormat(returnFormat);
    }

    // package-visible for tests
    JinaReaderConfig getConfig() {
        return config;
    }
}
