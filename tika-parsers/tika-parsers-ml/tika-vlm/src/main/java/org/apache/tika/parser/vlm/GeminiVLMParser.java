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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.StringUtils;

/**
 * VLM parser for the <b>Google Gemini</b> {@code generateContent} API.
 * <p>
 * Supports both images and PDFs natively (Gemini processes PDFs with native
 * vision, understanding layout, charts, tables, and diagrams — not just
 * extracting text).
 * <p>
 * The API key is sent as a {@code key} query parameter (not a Bearer header).
 * <p>
 * Default base URL points to the public Gemini API; change it for Vertex AI
 * or a proxy.
 * <p>
 * Configuration key: {@code "gemini-vlm-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(spi = false)
public class GeminiVLMParser extends AbstractVLMParser {

    private static final long serialVersionUID = 1L;

    private static final String OCR = "ocr-";

    private static final Set<MediaType> SUPPORTED_TYPES;

    static {
        Set<MediaType> types = new HashSet<>(Arrays.asList(
                // images
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
                MediaType.image(OCR + "webp"),
                MediaType.image("heic"),
                MediaType.image("heif"),
                // PDFs — Gemini handles these natively with vision
                MediaType.application("pdf")
        ));
        SUPPORTED_TYPES = Collections.unmodifiableSet(types);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public GeminiVLMParser() {
        super(geminiDefaults());
    }

    public GeminiVLMParser(VLMOCRConfig config) {
        super(config);
    }

    public GeminiVLMParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, VLMOCRConfig.class));
    }

    private static VLMOCRConfig geminiDefaults() {
        try {
            VLMOCRConfig cfg = new VLMOCRConfig();
            cfg.setBaseUrl("https://generativelanguage.googleapis.com");
            cfg.setModel("gemini-2.5-flash");
            return cfg;
        } catch (TikaConfigException e) {
            // Should never happen on base VLMOCRConfig
            throw new RuntimeException(e);
        }
    }

    @Override
    protected HttpCall buildHttpCall(VLMOCRConfig config,
                                     String base64Data, String mimeType) {
        String json = buildRequestJson(config, base64Data, mimeType);

        String baseUrl = stripTrailingSlash(config.getBaseUrl());
        String url = baseUrl + "/v1beta/models/" + config.getModel() + ":generateContent";

        if (!StringUtils.isBlank(config.getApiKey())) {
            url += "?key=" + config.getApiKey();
        }

        return new HttpCall(url, json, Map.of());
    }

    @Override
    protected String extractResponseText(String responseBody, Metadata metadata)
            throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);

            // Gemini usage metadata
            JsonNode usage = root.get("usageMetadata");
            if (usage != null) {
                if (usage.has("promptTokenCount")) {
                    metadata.set(VLM_PROMPT_TOKENS, usage.get("promptTokenCount").asInt());
                }
                if (usage.has("candidatesTokenCount")) {
                    metadata.set(VLM_COMPLETION_TOKENS,
                            usage.get("candidatesTokenCount").asInt());
                }
            }

            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                // Check for Gemini error response
                JsonNode error = root.get("error");
                if (error != null) {
                    throw new TikaException("Gemini API error: " + error.toString());
                }
                throw new TikaException("Gemini response contains no candidates: " + responseBody);
            }

            JsonNode content = candidates.get(0).get("content");
            if (content == null) {
                throw new TikaException("Gemini candidate has no content: " + responseBody);
            }

            JsonNode parts = content.get("parts");
            if (parts == null || !parts.isArray() || parts.isEmpty()) {
                return "";
            }

            // Concatenate all text parts
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : parts) {
                JsonNode textNode = part.get("text");
                if (textNode != null && !textNode.isNull()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(textNode.asText());
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to parse Gemini response JSON: " + e.getMessage(), e);
        }
    }

    @Override
    protected Set<MediaType> getSupportedMediaTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    protected String configKey() {
        return "gemini-vlm-parser";
    }

    @Override
    protected String getHealthCheckUrl(VLMOCRConfig config) {
        String baseUrl = stripTrailingSlash(config.getBaseUrl());
        String url = baseUrl + "/v1beta/models";
        if (!StringUtils.isBlank(config.getApiKey())) {
            url += "?key=" + config.getApiKey();
        }
        return url;
    }

    // -- package-visible for tests --

    String buildRequestJson(VLMOCRConfig config, String base64Data, String mimeType) {
        ObjectNode root = MAPPER.createObjectNode();

        // contents
        ArrayNode contents = root.putArray("contents");
        ObjectNode userContent = contents.addObject();
        ArrayNode parts = userContent.putArray("parts");

        // text part (prompt)
        ObjectNode textPart = parts.addObject();
        textPart.put("text", config.getPrompt());

        // inline data part (image or PDF)
        ObjectNode dataPart = parts.addObject();
        ObjectNode inlineData = dataPart.putObject("inline_data");
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Data);

        // generation config
        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("maxOutputTokens", config.getMaxTokens());

        return root.toString();
    }
}
