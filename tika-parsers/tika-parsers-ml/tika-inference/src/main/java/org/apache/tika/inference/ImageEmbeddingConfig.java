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

import org.apache.tika.config.OperatorOnly;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Configuration for image embedding parsers that call a CLIP-like
 * vector endpoint.
 *
 * @deprecated since 4.1.0, removed in 4.2.0 with {@link OpenAIImageEmbeddingParser}.
 */
@Deprecated
public class ImageEmbeddingConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String baseUrl = "http://localhost:8000";
    private String model = "";
    private String apiKey = "";
    private long timeoutMillis = 120_000;
    private long minFileSizeToEmbed = 0;
    private long maxFileSizeToEmbed = 50 * 1024 * 1024; // 50 MB
    private boolean skipEmbedding = false;

    /**
     * Metadata field to store the serialized chunk JSON containing the
     * image vector and locators. Defaults to the canonical chunks field
     * so image embeddings merge with text chunks in a single array.
     */
    private String outputField = TikaCoreProperties.TIKA_CHUNKS.getName();

    /**
     * Put a picture's vector on the document the picture appears in (a docx, an email, a
     * rendered page's PDF) rather than on the picture's own embedded document; attachments
     * always keep their own. See {@link ChunkTarget}.
     */
    private boolean liftToParent = true;

    public String getBaseUrl() {
        return baseUrl;
    }

    @OperatorOnly
    public void setBaseUrl(String baseUrl) throws TikaConfigException {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    @OperatorOnly
    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    @OperatorOnly
    public void setApiKey(String apiKey) throws TikaConfigException {
        this.apiKey = apiKey;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
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

    public boolean isLiftToParent() {
        return liftToParent;
    }

    public void setLiftToParent(boolean liftToParent) {
        this.liftToParent = liftToParent;
    }
}
