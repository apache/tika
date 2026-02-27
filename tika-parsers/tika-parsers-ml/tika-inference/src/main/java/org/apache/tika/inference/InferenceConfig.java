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
 * Configuration for the inference metadata filters.
 * <p>
 * Controls both the chunking behaviour (how text is split before inference)
 * and the remote endpoint settings (URL, model, auth, timeout).
 */
public class InferenceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    // ---- endpoint settings ------------------------------------------------

    /** Base URL of the embeddings API (no trailing slash). */
    private String baseUrl = "http://127.0.0.1:8000";

    /** Model identifier sent in the embeddings request. */
    private String model = "";

    /** Optional API key. Empty means no auth. */
    private String apiKey = "";

    /** HTTP read timeout in seconds. */
    private int timeoutSeconds = 120;

    // ---- chunking settings ------------------------------------------------

    /**
     * Maximum number of characters per chunk. The chunker will try to break
     * at markdown heading or paragraph boundaries before hitting this limit.
     */
    private int maxChunkChars = 1500;

    /**
     * Number of characters of overlap between consecutive chunks.
     * Helps ensure no context is lost at chunk boundaries.
     */
    private int overlapChars = 200;

    /**
     * The metadata field to read the source text from.
     * Defaults to {@code tika:content}.
     */
    private String contentField = "tika:content";

    /**
     * The metadata field where the JSON chunk array is written.
     */
    private String outputField = ChunkSerializer.CHUNKS_FIELD;

    /**
     * If {@code true}, the content field (default {@code tika:content}) is
     * removed from metadata after chunking and embedding. This avoids storing
     * the full text twice (once as raw content, once inside the chunks).
     * Default is {@code false}.
     */
    private boolean clearContentAfterChunking = false;

    /**
     * Maximum number of chunk texts to send in a single embeddings API
     * request.  If a document produces more chunks than this, the filter
     * splits them into multiple HTTP calls.
     * <p>
     * OpenAI's embeddings endpoint caps at 2048 inputs per request;
     * the default here (256) is a safe value that works across most
     * providers while keeping request sizes reasonable.
     */
    private int maxBatchSize = 256;

    /**
     * Maximum number of chunks to produce per document.  If a document's
     * text generates more chunks than this, excess chunks are silently
     * dropped.  This prevents pathologically large documents from
     * triggering an unbounded number of embedding API calls.
     * <p>
     * Default is 1024.  Set to {@code -1} for no limit.
     */
    private int maxChunks = 1024;

    // ---- getters / setters ------------------------------------------------

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

    public int getMaxChunkChars() {
        return maxChunkChars;
    }

    public void setMaxChunkChars(int maxChunkChars) {
        this.maxChunkChars = maxChunkChars;
    }

    public int getOverlapChars() {
        return overlapChars;
    }

    public void setOverlapChars(int overlapChars) {
        this.overlapChars = overlapChars;
    }

    public String getContentField() {
        return contentField;
    }

    public void setContentField(String contentField) {
        this.contentField = contentField;
    }

    public String getOutputField() {
        return outputField;
    }

    public void setOutputField(String outputField) {
        this.outputField = outputField;
    }

    public boolean isClearContentAfterChunking() {
        return clearContentAfterChunking;
    }

    public void setClearContentAfterChunking(boolean clearContentAfterChunking) {
        this.clearContentAfterChunking = clearContentAfterChunking;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * Set the maximum number of chunks per embeddings API request.
     * Must be at least 1.
     */
    public void setMaxBatchSize(int maxBatchSize) {
        if (maxBatchSize < 1) {
            throw new IllegalArgumentException(
                    "maxBatchSize must be at least 1, got: " + maxBatchSize);
        }
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    /**
     * Set the maximum number of chunks per document.
     * Set to {@code -1} for no limit. Must be {@code -1} or at least {@code 1}.
     */
    public void setMaxChunks(int maxChunks) {
        if (maxChunks < 1 && maxChunks != -1) {
            throw new IllegalArgumentException(
                    "maxChunks must be -1 (no limit) or at least 1, got: " + maxChunks);
        }
        this.maxChunks = maxChunks;
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
    public static class RuntimeConfig extends InferenceConfig {

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
                            + "separate filter instance.");
        }

        @Override
        public void setMaxBatchSize(int maxBatchSize) {
            throw new IllegalStateException(
                    "Cannot modify maxBatchSize at runtime. "
                            + "Batch size must be configured at initialization time.");
        }

        @Override
        public void setMaxChunks(int maxChunks) {
            throw new IllegalStateException(
                    "Cannot modify maxChunks at runtime. "
                            + "Chunk limits must be configured at initialization time.");
        }
    }
}
