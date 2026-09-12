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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.ParseTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaHttpClient;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.StringUtils;

/**
 * An OpenAI-compatible embeddings endpoint ({@code POST /v1/embeddings}), named in
 * {@code "engines"}. Sends up to {@code maxBatchSize} images per request as
 * {@code {"image": "data:<mime>;base64,..."}} inputs and reads the vectors back by index.
 */
@TikaComponent(name = "openai-embedding-engine", spi = false)
public class OpenAIEmbeddingEngine implements EmbeddingEngine, Initializable, Closeable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String baseUrl = "http://localhost:8000";
    private String model = "";
    private String apiKey = "";
    private long timeoutMillis = 120_000;
    private int maxBatchSize = 32;
    private String embeddingsPath = "/v1/embeddings";
    private String apiKeyHeaderName = "Authorization";
    private String apiKeyPrefix = "Bearer ";

    private TikaHttpClient httpClient;

    @Override
    public void initialize() throws TikaConfigException {
        if (StringUtils.isBlank(baseUrl)) {
            throw new TikaConfigException("openai-embedding-engine needs a baseUrl");
        }
        if (maxBatchSize < 1) {
            throw new TikaConfigException("maxBatchSize must be at least 1");
        }
        httpClient = TikaHttpClient.build(30);
    }

    @Override
    public List<float[]> embedImages(List<byte[]> images, List<String> mimeTypes,
                                     ParseContext context) throws IOException, TikaException {
        ObjectNode root = request();
        ArrayNode input = root.putArray("input");
        for (int i = 0; i < images.size(); i++) {
            input.addObject().put("image", "data:" + mimeTypes.get(i) + ";base64,"
                    + Base64.getEncoder().encodeToString(images.get(i)));
        }
        return post(root, images.size(), context);
    }

    @Override
    public List<float[]> embedTexts(List<String> texts, ParseContext context)
            throws IOException, TikaException {
        ObjectNode root = request();
        ArrayNode input = root.putArray("input");
        for (String text : texts) {
            input.add(text);
        }
        return post(root, texts.size(), context);
    }

    private ObjectNode request() {
        ObjectNode root = MAPPER.createObjectNode();
        if (!StringUtils.isBlank(model)) {
            root.put("model", model);
        }
        return root;
    }

    private List<float[]> post(ObjectNode root, int expected, ParseContext context)
            throws IOException, TikaException {
        if (httpClient == null) {
            httpClient = TikaHttpClient.build(30);
        }
        Map<String, String> headers = new HashMap<>();
        if (!StringUtils.isBlank(apiKey)) {
            headers.put(apiKeyHeaderName, apiKeyPrefix + apiKey);
        }
        String url = baseUrl.replaceAll("/+$", "") + embeddingsPath;
        String body = httpClient.postJson(url, root.toString(), headers, timeoutMillis, context);
        ParseTimeout.checkpoint(context);
        return parseResponse(body, expected);
    }

    static List<float[]> parseResponse(String body, int expected) throws TikaException {
        try {
            JsonNode data = MAPPER.readTree(body).get("data");
            if (data == null || !data.isArray() || data.size() != expected) {
                throw new TikaException("Embedding response has " + (data == null ? "no"
                        : data.size()) + " data entries; expected " + expected);
            }
            List<float[]> vectors = new ArrayList<>(expected);
            for (int i = 0; i < expected; i++) {
                vectors.add(null);
            }
            for (int i = 0; i < data.size(); i++) {
                JsonNode item = data.get(i);
                int index = item.path("index").asInt(i);
                JsonNode embedding = item.get("embedding");
                if (index < 0 || index >= expected || embedding == null || !embedding.isArray()) {
                    throw new TikaException("Embedding response entry " + i + " is malformed");
                }
                float[] vector = new float[embedding.size()];
                for (int d = 0; d < vector.length; d++) {
                    vector[d] = (float) embedding.get(d).asDouble();
                    if (Float.isNaN(vector[d]) || Float.isInfinite(vector[d])) {
                        throw new TikaException("Embedding " + index + " has an invalid float");
                    }
                }
                if (vectors.get(index) != null) {
                    throw new TikaException("Embedding response repeats index " + index);
                }
                vectors.set(index, vector);
            }
            for (int i = 0; i < expected; i++) {
                if (vectors.get(i) == null) {
                    throw new TikaException("Embedding response has no entry for index " + i);
                }
            }
            return vectors;
        } catch (IOException e) {
            throw new TikaException("Failed to parse embedding response", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public String getEmbeddingsPath() {
        return embeddingsPath;
    }

    public void setEmbeddingsPath(String embeddingsPath) {
        this.embeddingsPath = embeddingsPath;
    }

    public String getApiKeyHeaderName() {
        return apiKeyHeaderName;
    }

    public void setApiKeyHeaderName(String apiKeyHeaderName) {
        this.apiKeyHeaderName = apiKeyHeaderName;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }
}
