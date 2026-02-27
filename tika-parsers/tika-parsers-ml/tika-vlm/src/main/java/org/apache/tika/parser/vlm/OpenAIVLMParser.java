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
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.StringUtils;

/**
 * VLM parser for <b>OpenAI-compatible</b> chat completions endpoints
 * (OpenAI, Azure OpenAI, OpenRouter, vLLM, Ollama, LiteLLM, Together AI,
 * Groq, Fireworks, Mistral, NVIDIA NIM, Jina, local FastAPI wrappers, etc.).
 * <p>
 * Images are base64-encoded and sent as {@code image_url} content parts.
 * <p>
 * <b>Azure OpenAI</b> is supported by configuring:
 * <ul>
 *   <li>{@code baseUrl} — {@code https://{resource}.openai.azure.com}</li>
 *   <li>{@code completionsPath} —
 *       {@code /openai/deployments/{deployment}/chat/completions?api-version=2024-02-01}</li>
 *   <li>{@code apiKeyHeaderName} — {@code api-key}</li>
 *   <li>{@code apiKeyPrefix} — {@code ""} (empty string)</li>
 * </ul>
 * <p>
 * Configuration key: {@code "openai-vlm-parser"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "openai-vlm-parser")
public class OpenAIVLMParser extends AbstractVLMParser {

    private static final long serialVersionUID = 1L;

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

    /**
     * URL path appended to {@code baseUrl} for chat completions requests.
     * <p>
     * Default: {@code /v1/chat/completions} (standard OpenAI path).
     * <p>
     * For Azure OpenAI, set to
     * {@code /openai/deployments/{deployment}/chat/completions?api-version=2024-02-01}.
     */
    private String completionsPath = "/v1/chat/completions";

    /**
     * HTTP header name used to send the API key.
     * <p>
     * Default: {@code Authorization} (standard OpenAI / Bearer auth).
     * For Azure OpenAI, set to {@code api-key}.
     */
    private String apiKeyHeaderName = "Authorization";

    /**
     * Prefix prepended to the API key value in the auth header.
     * <p>
     * Default: {@code "Bearer "} (note the trailing space).
     * For Azure OpenAI, set to {@code ""} (empty string) since Azure
     * sends the raw key in the {@code api-key} header.
     */
    private String apiKeyPrefix = "Bearer ";

    public OpenAIVLMParser() {
        super(new VLMOCRConfig());
    }

    public OpenAIVLMParser(VLMOCRConfig config) {
        super(config);
    }

    public OpenAIVLMParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, VLMOCRConfig.class));
    }

    @Override
    protected HttpCall buildHttpCall(VLMOCRConfig config,
                                     String base64Data, String mimeType) {
        String json = buildRequestJson(config, base64Data, mimeType);
        String url = stripTrailingSlash(config.getBaseUrl()) + completionsPath;

        Map<String, String> headers = new HashMap<>();
        if (!StringUtils.isBlank(config.getApiKey())) {
            headers.put(apiKeyHeaderName, apiKeyPrefix + config.getApiKey());
        }
        return new HttpCall(url, json, headers);
    }

    @Override
    protected String extractResponseText(String responseBody, Metadata metadata)
            throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);

            JsonNode usage = root.get("usage");
            if (usage != null) {
                if (usage.has("prompt_tokens")) {
                    metadata.set(VLM_PROMPT_TOKENS, usage.get("prompt_tokens").asInt());
                }
                if (usage.has("completion_tokens")) {
                    metadata.set(VLM_COMPLETION_TOKENS, usage.get("completion_tokens").asInt());
                }
            }

            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                throw new TikaException("VLM response contains no choices: " + responseBody);
            }

            JsonNode message = choices.get(0).get("message");
            if (message == null) {
                throw new TikaException("VLM response choice has no message: " + responseBody);
            }

            JsonNode contentNode = message.get("content");
            if (contentNode == null || contentNode.isNull()) {
                return "";
            }
            return contentNode.asText();
        } catch (IOException e) {
            throw new TikaException("Failed to parse VLM response JSON: " + e.getMessage(), e);
        }
    }

    @Override
    protected Set<MediaType> getSupportedMediaTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    protected String configKey() {
        return "openai-vlm-parser";
    }

    @Override
    protected String getHealthCheckUrl(VLMOCRConfig config) {
        // Azure and custom endpoints may not have /v1/models;
        // skip health check if using a non-default completions path
        if (!"/v1/chat/completions".equals(completionsPath)) {
            return null;
        }
        return stripTrailingSlash(config.getBaseUrl()) + "/v1/models";
    }

    // ---- Azure / endpoint config getters/setters ----------------------------

    public String getCompletionsPath() {
        return completionsPath;
    }

    /**
     * Set the URL path for chat completions requests.
     * Default is {@code /v1/chat/completions}.
     * <p>
     * For Azure OpenAI, use something like
     * {@code /openai/deployments/my-gpt4o/chat/completions?api-version=2024-02-01}.
     */
    public void setCompletionsPath(String completionsPath) {
        this.completionsPath = completionsPath;
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    /**
     * Set the HTTP header name for API key authentication.
     * Default is {@code Authorization}.
     * For Azure OpenAI, set to {@code api-key}.
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

    // -- package-visible for tests --

    String buildRequestJson(VLMOCRConfig config, String base64Data, String mimeType) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", config.getModel());
        root.put("max_tokens", config.getMaxTokens());

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        ArrayNode content = userMessage.putArray("content");

        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", config.getPrompt());

        ObjectNode imagePart = content.addObject();
        imagePart.put("type", "image_url");
        ObjectNode imageUrl = imagePart.putObject("image_url");
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Data);

        return root.toString();
    }
}
