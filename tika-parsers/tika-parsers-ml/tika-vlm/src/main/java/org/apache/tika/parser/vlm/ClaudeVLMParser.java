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
import java.util.HashMap;
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
 * VLM parser for the <b>Anthropic Claude</b> Messages API.
 * <p>
 * Supports images (JPEG, PNG, GIF, WebP) and PDFs natively. Claude
 * processes each PDF page as both extracted text and a rendered image,
 * understanding layout, charts, tables and diagrams.
 * <p>
 * Authentication uses the {@code x-api-key} header (not Bearer).
 * The required {@code anthropic-version} header is sent automatically.
 * <p>
 * Configuration key: {@code "claude-vlm-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(spi = false)
public class ClaudeVLMParser extends AbstractVLMParser {

    private static final long serialVersionUID = 1L;

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private static final String OCR = "ocr-";

    private static final Set<MediaType> SUPPORTED_TYPES;

    static {
        Set<MediaType> types = new HashSet<>(Arrays.asList(
                // images — Claude supports JPEG, PNG, GIF, WebP
                MediaType.image(OCR + "png"),
                MediaType.image(OCR + "jpeg"),
                MediaType.image(OCR + "gif"),
                MediaType.image("webp"),
                MediaType.image(OCR + "webp"),
                // PDFs — Claude handles these natively with vision
                MediaType.application("pdf")
        ));
        SUPPORTED_TYPES = Collections.unmodifiableSet(types);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ClaudeVLMParser() {
        super(claudeDefaults());
    }

    public ClaudeVLMParser(VLMOCRConfig config) {
        super(config);
    }

    public ClaudeVLMParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, VLMOCRConfig.class));
    }

    private static VLMOCRConfig claudeDefaults() {
        try {
            VLMOCRConfig cfg = new VLMOCRConfig();
            cfg.setBaseUrl("https://api.anthropic.com");
            cfg.setModel("claude-sonnet-4-20250514");
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
        String url = stripTrailingSlash(config.getBaseUrl()) + "/v1/messages";

        Map<String, String> headers = new HashMap<>();
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        if (!StringUtils.isBlank(config.getApiKey())) {
            headers.put("x-api-key", config.getApiKey());
        }

        return new HttpCall(url, json, headers);
    }

    @Override
    protected String extractResponseText(String responseBody, Metadata metadata)
            throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);

            // Check for error response
            JsonNode errorNode = root.get("error");
            if (errorNode != null) {
                String msg = errorNode.has("message")
                        ? errorNode.get("message").asText()
                        : errorNode.toString();
                throw new TikaException("Claude API error: " + msg);
            }

            // Usage metadata
            JsonNode usage = root.get("usage");
            if (usage != null) {
                if (usage.has("input_tokens")) {
                    metadata.set(VLM_PROMPT_TOKENS, usage.get("input_tokens").asInt());
                }
                if (usage.has("output_tokens")) {
                    metadata.set(VLM_COMPLETION_TOKENS, usage.get("output_tokens").asInt());
                }
            }

            // Content blocks
            JsonNode content = root.get("content");
            if (content == null || !content.isArray() || content.isEmpty()) {
                throw new TikaException(
                        "Claude response contains no content: " + responseBody);
            }

            // Concatenate all text blocks
            StringBuilder sb = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText())) {
                    JsonNode textNode = block.get("text");
                    if (textNode != null && !textNode.isNull()) {
                        if (sb.length() > 0) {
                            sb.append("\n");
                        }
                        sb.append(textNode.asText());
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to parse Claude response JSON: " + e.getMessage(), e);
        }
    }

    @Override
    protected Set<MediaType> getSupportedMediaTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    protected String configKey() {
        return "claude-vlm-parser";
    }

    @Override
    protected String getHealthCheckUrl(VLMOCRConfig config) {
        // Claude doesn't have a lightweight models endpoint; skip probe
        return null;
    }

    // -- package-visible for tests --

    String buildRequestJson(VLMOCRConfig config, String base64Data, String mimeType) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.getModel());
        root.put("max_tokens", config.getMaxTokens());

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode content = userMessage.putArray("content");

        // Content block: image or document depending on MIME type
        ObjectNode mediaPart = content.addObject();
        if (mimeType.equals("application/pdf")) {
            mediaPart.put("type", "document");
        } else {
            mediaPart.put("type", "image");
        }
        ObjectNode source = mediaPart.putObject("source");
        source.put("type", "base64");
        source.put("media_type", mimeType);
        source.put("data", base64Data);

        // Text block: the prompt
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", config.getPrompt());

        return root.toString();
    }
}
