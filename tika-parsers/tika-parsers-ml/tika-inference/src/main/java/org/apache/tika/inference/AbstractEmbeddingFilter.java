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
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.SelfConfiguring;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

/**
 * Base class for metadata filters that chunk text content and call a remote
 * embeddings endpoint to produce vectors for each chunk.
 * <p>
 * The pipeline is:
 * <ol>
 *   <li>Read source text from {@code contentField} in metadata</li>
 *   <li>Chunk it with {@link MarkdownChunker}</li>
 *   <li>Call {@link #embed(List, InferenceConfig)} to get vectors</li>
 *   <li>Serialize chunks + vectors as JSON into {@code outputField}</li>
 * </ol>
 * <p>
 * The {@code MarkdownChunker} requires markdown-formatted content to split at
 * semantic boundaries (headings, paragraphs, etc.). The content handler type
 * must be set to {@code MARKDOWN} in the configuration. If the
 * {@link TikaCoreProperties#TIKA_CONTENT_HANDLER_TYPE} metadata field indicates
 * a different handler type, a warning is logged.
 * <p>
 * Subclasses implement {@link #embed} for their specific API format.
 * <p>
 * Thread safety: instances are safe for concurrent {@link #filter} calls once
 * fully constructed. Setters must not be called concurrently with
 * {@link #filter}.
 */
public abstract class AbstractEmbeddingFilter extends MetadataFilter implements SelfConfiguring {

    private static final long serialVersionUID = 1L;

    private static final String REQUIRED_HANDLER_TYPE = "MARKDOWN";

    private static final Logger LOG = LoggerFactory.getLogger(
            AbstractEmbeddingFilter.class);

    private InferenceConfig defaultConfig = new InferenceConfig();

    protected AbstractEmbeddingFilter() {
    }

    protected AbstractEmbeddingFilter(InferenceConfig config) {
        this.defaultConfig = config;
    }

    /**
     * Call the embeddings endpoint to fill in vectors on each chunk.
     * Implementations should set {@link Chunk#setVector(float[])} on
     * each chunk in the list.
     *
     * @param chunks       the text chunks to embed
     * @param config       the resolved config for this call
     * @param parseContext the task's ParseContext -- pass to
     *                     {@link org.apache.tika.http.TikaHttpClient} calls to bound the
     *                     request by the task's remaining budget and checkpoint while waiting
     * @throws IOException   on HTTP errors
     * @throws TikaException on API-level errors
     */
    protected abstract void embed(List<Chunk> chunks, InferenceConfig config, ParseContext parseContext)
            throws IOException, TikaException;

    /**
     * The {@code @TikaComponent} name this filter's per-request JSON config is keyed by
     * in parse-context (e.g. {@code {"openai-embedding-filter": {...}}}).
     */
    protected abstract String getComponentName();

    @Override
    protected void doFilter(List<Metadata> metadataList, ParseContext parseContext) throws TikaException {
        InferenceConfig config = resolveConfig(parseContext);
        if (config.isSkipEmbedding()) {
            return;
        }
        for (Metadata metadata : metadataList) {
            processOne(metadata, config, parseContext);
        }
    }

    /**
     * Per-request JSON config, validated through {@link InferenceConfig.RuntimeConfig}
     * (which rejects baseUrl/apiKey/model changes) and merged over the init-time defaults.
     * With no JSON, a class-keyed programmatic {@link InferenceConfig} is honored for
     * skipEmbedding only, preserving the pre-4.1 contract.
     */
    private InferenceConfig resolveConfig(ParseContext parseContext) throws TikaException {
        try {
            if (ParseContextConfig.hasConfig(parseContext, getComponentName())) {
                InferenceConfig.RuntimeConfig runtimeConfig = ParseContextConfig.getConfig(
                        parseContext, getComponentName(),
                        InferenceConfig.RuntimeConfig.class, new InferenceConfig.RuntimeConfig());
                if (runtimeConfig.isSkipEmbedding()) {
                    return runtimeConfig;
                }
                return ParseContextConfig.getConfig(parseContext, getComponentName(),
                        InferenceConfig.class, defaultConfig);
            }
        } catch (TikaConfigException | IOException e) {
            throw new TikaException("Failed to resolve per-request config for '"
                    + getComponentName() + "'", e);
        }
        InferenceConfig programmatic = parseContext.get(InferenceConfig.class);
        if (programmatic != null && programmatic.isSkipEmbedding()) {
            return programmatic;
        }
        return defaultConfig;
    }

    private void processOne(Metadata metadata, InferenceConfig config, ParseContext parseContext)
            throws TikaException {
        String content = metadata.get(config.getContentField());
        if (content == null) {
            LOG.debug("No content found at field '{}'; skipping embedding. "
                    + "If using this filter standalone, populate metadata using "
                    + "TikaCoreProperties.TIKA_CONTENT as the key.",
                    config.getContentField());
            return;
        }
        if (content.isBlank()) {
            return;
        }

        String handlerType = metadata.get(TikaCoreProperties.TIKA_CONTENT_HANDLER_TYPE);
        if (handlerType != null
                && !REQUIRED_HANDLER_TYPE.equals(
                        handlerType.toUpperCase(Locale.ROOT))) {
            LOG.warn("content-handler-factory type is '{}' but the "
                    + "MarkdownChunker requires MARKDOWN-formatted content "
                    + "for high-quality chunking. Set the "
                    + "content-handler-factory type to MARKDOWN.",
                    handlerType);
        }

        MarkdownChunker chunker = new MarkdownChunker(
                config.getMaxChunkChars(),
                config.getOverlapChars());

        List<Chunk> chunks = chunker.chunk(content);
        if (chunks.isEmpty()) {
            return;
        }

        int maxChunks = config.getMaxChunks();
        if (maxChunks > 0 && chunks.size() > maxChunks) {
            LOG.warn("Document produced {} chunks, truncating to maxChunks={}",
                    chunks.size(), maxChunks);
            chunks = chunks.subList(0, maxChunks);
        }

        try {
            int batchSize = config.getMaxBatchSize();
            for (int i = 0; i < chunks.size(); i += batchSize) {
                List<Chunk> batch = chunks.subList(
                        i, Math.min(i + batchSize, chunks.size()));
                embed(batch, config, parseContext);
            }
            ChunkSerializer.mergeInto(metadata, chunks, config.getOutputField());
        } catch (IOException e) {
            throw new TikaException(
                    "Embedding inference failed: " + e.getMessage(), e);
        }

        if (config.isClearContentAfterChunking()) {
            metadata.remove(config.getContentField());
        }
    }

    // ---- delegating config getters/setters --------------------------------

    public InferenceConfig getDefaultConfig() {
        return defaultConfig;
    }

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

    public long getTimeoutMillis() {
        return defaultConfig.getTimeoutMillis();
    }

    public void setTimeoutMillis(long timeoutMillis) {
        defaultConfig.setTimeoutMillis(timeoutMillis);
    }

    public int getMaxChunkChars() {
        return defaultConfig.getMaxChunkChars();
    }

    public void setMaxChunkChars(int maxChunkChars) {
        defaultConfig.setMaxChunkChars(maxChunkChars);
    }

    public int getOverlapChars() {
        return defaultConfig.getOverlapChars();
    }

    public void setOverlapChars(int overlapChars) {
        defaultConfig.setOverlapChars(overlapChars);
    }

    public String getContentField() {
        return defaultConfig.getContentField();
    }

    public void setContentField(String contentField) {
        defaultConfig.setContentField(contentField);
    }

    public String getOutputField() {
        return defaultConfig.getOutputField();
    }

    public void setOutputField(String outputField) {
        defaultConfig.setOutputField(outputField);
    }

    public boolean isSkipEmbedding() {
        return defaultConfig.isSkipEmbedding();
    }

    public void setSkipEmbedding(boolean skipEmbedding) {
        defaultConfig.setSkipEmbedding(skipEmbedding);
    }

    public boolean isClearContentAfterChunking() {
        return defaultConfig.isClearContentAfterChunking();
    }

    public void setClearContentAfterChunking(boolean clearContentAfterChunking) {
        defaultConfig.setClearContentAfterChunking(clearContentAfterChunking);
    }

    public int getMaxBatchSize() {
        return defaultConfig.getMaxBatchSize();
    }

    public void setMaxBatchSize(int maxBatchSize) {
        defaultConfig.setMaxBatchSize(maxBatchSize);
    }

    public int getMaxChunks() {
        return defaultConfig.getMaxChunks();
    }

    public void setMaxChunks(int maxChunks) {
        defaultConfig.setMaxChunks(maxChunks);
    }
}
