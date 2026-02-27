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

import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for image embedding parsers that call a CLIP-like
 * vector endpoint.
 */
public class ImageEmbeddingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseUrl = "http://localhost:8000";
    private String model = "";
    private String apiKey = "";
    private int timeoutSeconds = 120;
    private long minFileSizeToEmbed = 0;
    private long maxFileSizeToEmbed = 50 * 1024 * 1024; // 50 MB
    private boolean skipEmbedding = false;

    /**
     * Metadata field to store the serialized chunk JSON containing the
     * image vector and locators. Defaults to the canonical chunks field
     * so image embeddings merge with text chunks in a single array.
     */
    private String outputField = ChunkSerializer.CHUNKS_FIELD;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) throws TikaConfigException {
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

    public void setApiKey(String apiKey) throws TikaConfigException {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getMinFileSizeToEmbed() {
        return minFileSizeToEmbed;
    }

    public void setMinFileSizeToEmbed(long minFileSizeToEmbed) {
        this.minFileSizeToEmbed = minFileSizeToEmbed;
    }

    public long getMaxFileSizeToEmbed() {
        return maxFileSizeToEmbed;
    }

    public void setMaxFileSizeToEmbed(long maxFileSizeToEmbed) {
        this.maxFileSizeToEmbed = maxFileSizeToEmbed;
    }

    public boolean isSkipEmbedding() {
        return skipEmbedding;
    }

    public void setSkipEmbedding(boolean skipEmbedding) {
        this.skipEmbedding = skipEmbedding;
    }

    public String getOutputField() {
        return outputField;
    }

    public void setOutputField(String outputField) {
        this.outputField = outputField;
    }

    /**
     * Runtime-only config that prevents modification of security-sensitive
     * and cost-sensitive fields ({@code baseUrl}, {@code apiKey},
     * {@code model}) at parse time.
     * <p>
     * These fields must be set at initialization via the config file.
     * If a runtime {@code ParseContext} JSON config attempts to set them,
     * the overridden setters throw {@link TikaConfigException}.
     */
    public static class RuntimeConfig extends ImageEmbeddingConfig {

        @Override
        public void setBaseUrl(String baseUrl) throws TikaConfigException {
            if (!StringUtils.isBlank(baseUrl)) {
                throw new TikaConfigException(
                        "Cannot modify baseUrl at runtime. "
                                + "URLs must be configured at initialization time.");
            }
        }

        @Override
        public void setApiKey(String apiKey) throws TikaConfigException {
            if (!StringUtils.isBlank(apiKey)) {
                throw new TikaConfigException(
                        "Cannot modify apiKey at runtime. "
                                + "API keys must be configured at initialization time.");
            }
        }

        @Override
        public void setModel(String model) {
            throw new IllegalStateException(
                    "Cannot modify model at runtime. "
                            + "Models must be configured at initialization time. "
                            + "If you need a different model, configure a "
                            + "separate parser instance.");
        }
    }
}
