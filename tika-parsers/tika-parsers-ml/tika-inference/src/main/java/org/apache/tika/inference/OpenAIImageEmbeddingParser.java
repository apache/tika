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

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.config.TikaProgressTracker;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaHttpClient;
import org.apache.tika.inference.locator.Locators;
import org.apache.tika.inference.locator.PaginatedLocator;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

/**
 * Parser that sends images to a CLIP-like embedding endpoint
 * (OpenAI-compatible {@code /v1/embeddings} with image input) and
 * stores the resulting vector in metadata.
 * <p>
 * This parser registers for the same {@code image/ocr-*} media types
 * used by the PDF renderer's OCR pipeline, so it slots into the
 * existing {@code ocrStrategy} mechanism. When configured, each
 * rendered page image is sent to the embedding endpoint and the
 * vector is stored as a serialized {@link Chunk} with a
 * {@link PaginatedLocator} (when page number metadata is available).
 * <p>
 * The image is sent in the Jina CLIP format:
 * {@code {"input": [{"image": "data:image/png;base64,..."}]}}.
 * <p>
 * Configuration key: {@code "openai-image-embedding-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "openai-image-embedding-parser", spi = false)
public class OpenAIImageEmbeddingParser implements Parser, Initializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(
            OpenAIImageEmbeddingParser.class);

    private static final String OCR = "ocr-";

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    MediaType.image(OCR + "png"),
                    MediaType.image(OCR + "jpeg"),
                    MediaType.image(OCR + "tiff"),
                    MediaType.image(OCR + "bmp"),
                    MediaType.image(OCR + "gif"),
                    MediaType.image("jp2"),
                    MediaType.image("jpx"),
                    MediaType.image("x-portable-pixmap"),
                    MediaType.image(OCR + "jp2"),
                    MediaType.image(OCR + "jpx"),
                    MediaType.image(OCR + "x-portable-pixmap"),
                    MediaType.image("webp"),
                    MediaType.image(OCR + "webp")
            )));

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ImageEmbeddingConfig defaultConfig;
    private transient TikaHttpClient httpClient;

    /** URL path for embeddings requests. Default: {@code /v1/embeddings}. */
    private String embeddingsPath = "/v1/embeddings";

    /** HTTP header name for API key auth. Default: {@code Authorization}. */
    private String apiKeyHeaderName = "Authorization";

    /** Prefix before API key value. Default: {@code "Bearer "}. */
    private String apiKeyPrefix = "Bearer ";

    public OpenAIImageEmbeddingParser() {
        this(new ImageEmbeddingConfig());
    }

    public OpenAIImageEmbeddingParser(ImageEmbeddingConfig config) {
        this.defaultConfig = config;
        this.httpClient = TikaHttpClient.build(30);
    }

    public OpenAIImageEmbeddingParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, ImageEmbeddingConfig.class));
    }

    // ---- Parser interface -------------------------------------------------

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (defaultConfig.isSkipEmbedding()) {
            return Collections.emptySet();
        }
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler,
                      Metadata metadata, ParseContext parseContext)
            throws IOException, SAXException, TikaException {

        ImageEmbeddingConfig config = getConfig(parseContext);

        if (config.isSkipEmbedding()) {
            return;
        }

        long size = tis.getLength();
        if (size >= 0
                && (size < config.getMinFileSizeToEmbed()
                || size > config.getMaxFileSizeToEmbed())) {
            return;
        }

        byte[] imageBytes = tis.readAllBytes();
        String mimeType = detectMimeType(metadata);
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        long timeoutMillis = TimeoutLimits.getProcessTimeoutMillis(
                parseContext, config.getTimeoutSeconds() * 1000L);
        int timeoutSeconds = (int) (timeoutMillis / 1000L);

        float[] vector = callEmbeddingEndpoint(config, mimeType, base64Data, timeoutSeconds);
        TikaProgressTracker.update(parseContext);

        Locators locators = buildLocators(metadata);
        Chunk chunk = new Chunk(null, locators);
        chunk.setVector(vector);

        ChunkSerializer.mergeInto(metadata, List.of(chunk));

        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                handler, metadata, parseContext);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    // ---- Initializable ----------------------------------------------------

    @Override
    public void initialize() throws TikaConfigException {
        this.httpClient = TikaHttpClient.build(30);
    }

    // ---- internals --------------------------------------------------------

    float[] callEmbeddingEndpoint(ImageEmbeddingConfig config,
                                  String mimeType, String base64Data,
                                  int timeoutSeconds)
            throws IOException, TikaException {

        String requestJson = buildRequest(config, mimeType, base64Data);
        String url = config.getBaseUrl().replaceAll("/+$", "") + embeddingsPath;

        Map<String, String> headers = new HashMap<>();
        if (!StringUtils.isBlank(config.getApiKey())) {
            headers.put(apiKeyHeaderName, apiKeyPrefix + config.getApiKey());
        }

        String responseBody = httpClient.postJson(url, requestJson, headers, timeoutSeconds);
        return parseResponse(responseBody);
    }

    String buildRequest(ImageEmbeddingConfig config, String mimeType,
                        String base64Data) {
        ObjectNode root = MAPPER.createObjectNode();
        if (!StringUtils.isBlank(config.getModel())) {
            root.put("model", config.getModel());
        }

        ArrayNode input = root.putArray("input");
        ObjectNode imageEntry = input.addObject();
        imageEntry.put("image", "data:" + mimeType + ";base64," + base64Data);

        return root.toString();
    }

    float[] parseResponse(String responseBody) throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                throw new TikaException(
                        "Embedding response has no data array: " + responseBody);
            }

            JsonNode embedding = data.get(0).get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new TikaException(
                        "Embedding response has no embedding array: "
                                + responseBody);
            }

            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    Locators buildLocators(Metadata metadata) {
        Locators locators = new Locators();

        String pageStr = metadata.get(TikaPagedText.PAGE_NUMBER);
        if (pageStr != null) {
            try {
                int page = Integer.parseInt(pageStr);
                locators.addPaginated(new PaginatedLocator(page));
            } catch (NumberFormatException e) {
                // skip
            }
        }

        return locators;
    }

    private String detectMimeType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null) {
            contentType = contentType.replace("ocr-", "");
            if (contentType.startsWith("image/")) {
                return contentType;
            }
        }
        return "image/png";
    }

    private ImageEmbeddingConfig getConfig(ParseContext parseContext)
            throws TikaConfigException, IOException {
        String key = "openai-image-embedding-parser";
        if (parseContext.hasJsonConfig(key)) {
            ImageEmbeddingConfig.RuntimeConfig runtimeConfig =
                    ParseContextConfig.getConfig(
                            parseContext, key,
                            ImageEmbeddingConfig.RuntimeConfig.class,
                            new ImageEmbeddingConfig.RuntimeConfig());

            if (runtimeConfig.isSkipEmbedding()) {
                return runtimeConfig;
            }

            return ParseContextConfig.getConfig(
                    parseContext, key, ImageEmbeddingConfig.class,
                    defaultConfig);
        }
        return defaultConfig;
    }

    // ---- delegating config getters/setters --------------------------------

    public String getBaseUrl() {
        return defaultConfig.getBaseUrl();
    }

    public void setBaseUrl(String baseUrl) throws TikaConfigException {
        defaultConfig.setBaseUrl(baseUrl);
    }

    public String getModel() {
        return defaultConfig.getModel();
    }

    public void setModel(String model) {
        defaultConfig.setModel(model);
    }

    public String getApiKey() {
        return defaultConfig.getApiKey();
    }

    public void setApiKey(String apiKey) throws TikaConfigException {
        defaultConfig.setApiKey(apiKey);
    }

    public int getTimeoutSeconds() {
        return defaultConfig.getTimeoutSeconds();
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        defaultConfig.setTimeoutSeconds(timeoutSeconds);
    }

    public boolean isSkipEmbedding() {
        return defaultConfig.isSkipEmbedding();
    }

    public void setSkipEmbedding(boolean skipEmbedding) {
        defaultConfig.setSkipEmbedding(skipEmbedding);
    }

    public long getMinFileSizeToEmbed() {
        return defaultConfig.getMinFileSizeToEmbed();
    }

    public void setMinFileSizeToEmbed(long minFileSizeToEmbed) {
        defaultConfig.setMinFileSizeToEmbed(minFileSizeToEmbed);
    }

    public long getMaxFileSizeToEmbed() {
        return defaultConfig.getMaxFileSizeToEmbed();
    }

    public void setMaxFileSizeToEmbed(long maxFileSizeToEmbed) {
        defaultConfig.setMaxFileSizeToEmbed(maxFileSizeToEmbed);
    }

    // ---- Azure / endpoint config getters/setters ----------------------------

    public String getEmbeddingsPath() {
        return embeddingsPath;
    }

    /**
     * Set the URL path for embeddings requests.
     * Default is {@code /v1/embeddings}.
     * For Azure OpenAI, use
     * {@code /openai/deployments/{deployment}/embeddings?api-version=2024-02-01}.
     */
    public void setEmbeddingsPath(String embeddingsPath) {
        this.embeddingsPath = embeddingsPath;
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    /**
     * Set the HTTP header name for API key authentication.
     * Default is {@code Authorization}. For Azure OpenAI, set to {@code api-key}.
     */
    public void setApiKeyHeaderName(String apiKeyHeaderName) {
        this.apiKeyHeaderName = apiKeyHeaderName;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    /**
     * Set the prefix prepended to the API key in the auth header.
     * Default is {@code "Bearer "} (with trailing space).
     * For Azure OpenAI, set to {@code ""} (empty string).
     */
    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }
}
