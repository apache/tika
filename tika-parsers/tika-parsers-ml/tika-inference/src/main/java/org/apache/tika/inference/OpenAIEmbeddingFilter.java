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
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;

/**
 * Metadata filter that calls an OpenAI-compatible {@code /v1/embeddings}
 * endpoint to produce vectors for each text chunk.
 * <p>
 * Works with OpenAI, vLLM, Ollama, sentence-transformers servers, and
 * any endpoint that implements the
 * <a href="https://platform.openai.com/docs/api-reference/embeddings">
 * OpenAI Embeddings API</a>.
 * <p>
 * Configuration key: {@code "openai-embedding-filter"}
 *
 * @since Apache Tika 4.0
 */
@TikaComponent(name = "openai-embedding-filter", spi = false)
public class OpenAIEmbeddingFilter extends AbstractEmbeddingFilter {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.parse("application/json; charset=utf-8");

    private transient OkHttpClient httpClient;

    /**
     * URL path appended to {@code baseUrl} for embeddings requests.
     * Default: {@code /v1/embeddings}. For Azure OpenAI, set to
     * {@code /openai/deployments/{deployment}/embeddings?api-version=2024-02-01}.
     */
    private String embeddingsPath = "/v1/embeddings";

    /** HTTP header name for API key auth. Default: {@code Authorization}. */
    private String apiKeyHeaderName = "Authorization";

    /** Prefix before API key value. Default: {@code "Bearer "}. */
    private String apiKeyPrefix = "Bearer ";

    public OpenAIEmbeddingFilter() {
        super();
        buildHttpClient();
    }

    public OpenAIEmbeddingFilter(InferenceConfig config) {
        super(config);
        buildHttpClient();
    }

    @Override
    protected void embed(List<Chunk> chunks, InferenceConfig config)
            throws IOException, TikaException {

        if (chunks.isEmpty()) {
            return;
        }

        // Build the request with all chunk texts in one batch
        String requestJson = buildRequest(chunks, config);
        String url = config.getBaseUrl().replaceAll("/+$", "") + embeddingsPath;

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(requestJson, JSON_MEDIA_TYPE));

        if (!StringUtils.isBlank(config.getApiKey())) {
            builder.header(apiKeyHeaderName, apiKeyPrefix + config.getApiKey());
        }

        OkHttpClient client = getClientWithTimeout(config);

        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null
                        ? response.body().string() : "";
                throw new TikaException(
                        "Embedding request failed with HTTP "
                                + response.code() + ": " + body);
            }

            String responseBody = response.body() != null
                    ? response.body().string() : "";
            parseResponse(responseBody, chunks);
        }
    }

    String buildRequest(List<Chunk> chunks, InferenceConfig config) {
        ObjectNode root = MAPPER.createObjectNode();
        if (!StringUtils.isBlank(config.getModel())) {
            root.put("model", config.getModel());
        }

        ArrayNode input = root.putArray("input");
        for (Chunk chunk : chunks) {
            input.add(chunk.getText());
        }

        return root.toString();
    }

    void parseResponse(String responseBody, List<Chunk> chunks)
            throws TikaException {
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                throw new TikaException(
                        "Embedding response has no data array: " + responseBody);
            }

            for (JsonNode item : data) {
                int index = item.get("index").asInt();
                if (index < 0 || index >= chunks.size()) {
                    continue;
                }

                JsonNode embedding = item.get("embedding");
                if (embedding != null && embedding.isArray()) {
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        vector[i] = (float) embedding.get(i).asDouble();
                    }
                    chunks.get(index).setVector(vector);
                }
            }
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    private void buildHttpClient() {
        int timeout = getDefaultConfig().getTimeoutSeconds();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private OkHttpClient getClientWithTimeout(InferenceConfig config) {
        long defaultMs = getDefaultConfig().getTimeoutSeconds() * 1000L;
        long requestMs = config.getTimeoutSeconds() * 1000L;
        if (requestMs == defaultMs) {
            return httpClient;
        }
        return httpClient.newBuilder()
                .readTimeout(requestMs, TimeUnit.MILLISECONDS)
                .build();
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
