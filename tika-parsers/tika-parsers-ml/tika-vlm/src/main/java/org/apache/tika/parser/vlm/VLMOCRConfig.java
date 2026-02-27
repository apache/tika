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

import java.io.Serializable;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.StringUtils;

/**
 * Configuration for {@link VLMOCRParser}.
 * <p>
 * The parser expects an OpenAI-compatible chat completions endpoint
 * (e.g. from vLLM, Ollama, or a local FastAPI server). The image is
 * base64-encoded and sent as a {@code image_url} content part.
 * <p>
 * This class is not thread safe and must be synchronized externally.
 */
public class VLMOCRConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Base URL of the OpenAI-compatible API (no trailing slash). */
    private String baseUrl = "http://127.0.0.1:8000";

    /** Model identifier sent in the chat completions request. */
    private String model = "jinaai/jina-vlm";

    /**
     * System prompt instructing the VLM how to OCR the image.
     * Override this to request different output formats (markdown, plain text, etc.).
     */
    private String prompt = "Extract all visible text from this image. "
            + "Return the text in markdown format, preserving the original structure "
            + "(headings, lists, tables, paragraphs). "
            + "Do not describe the image. Only return the extracted text.";

    /** Maximum number of tokens the model may generate. */
    private int maxTokens = 4096;

    /**
     * HTTP timeout in seconds for the chat completions request.
     * VLM inference can be slow; default is generous.
     */
    private int timeoutSeconds = 300;

    /** Optional API key for authenticated endpoints. Empty means no auth. */
    private String apiKey = "";

    /**
     * If {@code true}, when this parser is used on an inline image (embedded
     * resource type {@code INLINE}), the OCR text is written into the parent
     * document's content stream as well as the embedded handler. This mirrors
     * the behaviour of {@code TesseractOCRParser}'s inline-content mode.
     */
    private boolean inlineContent = true;

    /** Whether to skip VLM OCR entirely (runtime kill-switch). */
    private boolean skipOcr = false;

    /** Minimum file size (bytes) to submit to VLM OCR. */
    private long minFileSizeToOcr = 0;

    /** Maximum file size (bytes) to submit to VLM OCR. */
    private long maxFileSizeToOcr = 50 * 1024 * 1024; // 50 MB

    /**
     * Maximum total pixels (width &times; height) allowed for an image
     * before it is skipped. Prevents sending enormous base64 payloads
     * to the VLM endpoint.
     * <p>
     * Default is 100,000,000 (100 megapixels). Set to {@code -1} for
     * no limit (not recommended). Does not apply to PDF inputs.
     */
    private long maxImagePixels = 100_000_000L;

    /**
     * If {@code true}, the prompt may be overridden at parse time via
     * runtime configuration (ParseContext JSON config).  Defaults to
     * {@code false} so that the prompt is locked at initialization time.
     * <p>
     * Enable this only if you trust the source of per-request configuration.
     */
    private boolean allowRuntimePrompt = false;

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

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) throws TikaConfigException {
        this.apiKey = apiKey;
    }

    public boolean isInlineContent() {
        return inlineContent;
    }

    public void setInlineContent(boolean inlineContent) {
        this.inlineContent = inlineContent;
    }

    public boolean isSkipOcr() {
        return skipOcr;
    }

    public void setSkipOcr(boolean skipOcr) {
        this.skipOcr = skipOcr;
    }

    public long getMinFileSizeToOcr() {
        return minFileSizeToOcr;
    }

    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        this.minFileSizeToOcr = minFileSizeToOcr;
    }

    public long getMaxFileSizeToOcr() {
        return maxFileSizeToOcr;
    }

    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        this.maxFileSizeToOcr = maxFileSizeToOcr;
    }

    public long getMaxImagePixels() {
        return maxImagePixels;
    }

    /**
     * Set the maximum total pixels (width &times; height) for an image.
     * Default is 100,000,000. Set to {@code -1} for no limit (not recommended).
     */
    public void setMaxImagePixels(long maxImagePixels) {
        if (maxImagePixels < 1 && maxImagePixels != -1) {
            throw new IllegalArgumentException(
                    "maxImagePixels must be -1 (no limit) or at least 1, got: "
                            + maxImagePixels);
        }
        this.maxImagePixels = maxImagePixels;
    }

    public boolean isAllowRuntimePrompt() {
        return allowRuntimePrompt;
    }

    public void setAllowRuntimePrompt(boolean allowRuntimePrompt) {
        this.allowRuntimePrompt = allowRuntimePrompt;
    }

    /**
     * Runtime-only config that prevents modification of security-sensitive
     * and cost-sensitive fields at parse time.
     * <p>
     * <b>Always blocked:</b> {@code baseUrl}, {@code apiKey}, {@code model},
     * {@code maxTokens}, {@code allowRuntimePrompt}.
     * <p>
     * <b>Blocked by default (opt-in):</b> {@code prompt} — set
     * {@code allowRuntimePrompt=true} at initialization time to permit
     * per-request prompt overrides.
     * <p>
     * The {@code model} field is unconditionally blocked because there is
     * no legitimate reason to swap models per-request; if a different model
     * is needed, configure a separate parser instance.
     * <p>
     * The {@code maxTokens} field cannot be raised above the init-time
     * value at runtime to prevent cost attacks on paid API endpoints.
     */
    public static class RuntimeConfig extends VLMOCRConfig {

        /** Init-time maxTokens ceiling — runtime requests cannot exceed this. */
        private int initMaxTokens = 4096;

        public RuntimeConfig() {
        }

        /**
         * Creates a RuntimeConfig that inherits the init-time
         * {@code allowRuntimePrompt} setting and the {@code maxTokens}
         * ceiling from the given parent config.
         */
        public RuntimeConfig(VLMOCRConfig initConfig) {
            super.setAllowRuntimePrompt(initConfig.isAllowRuntimePrompt());
            this.initMaxTokens = initConfig.getMaxTokens();
        }

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

        @Override
        public void setPrompt(String prompt) {
            if (!isAllowRuntimePrompt()) {
                throw new IllegalStateException(
                        "Cannot modify prompt at runtime. "
                                + "Set allowRuntimePrompt=true at initialization time "
                                + "to permit per-request prompt overrides.");
            }
            super.setPrompt(prompt);
        }

        @Override
        public void setMaxTokens(int maxTokens) {
            if (maxTokens > initMaxTokens) {
                throw new IllegalStateException(
                        "Cannot increase maxTokens beyond the init-time value ("
                                + initMaxTokens + ") at runtime. "
                                + "Requested: " + maxTokens);
            }
            super.setMaxTokens(maxTokens);
        }

        @Override
        public void setAllowRuntimePrompt(boolean allowRuntimePrompt) {
            throw new IllegalStateException(
                    "Cannot modify allowRuntimePrompt at runtime. "
                            + "This must be configured at initialization time.");
        }

        @Override
        public void setMaxImagePixels(long maxImagePixels) {
            throw new IllegalStateException(
                    "Cannot modify maxImagePixels at runtime. "
                            + "Image size limits must be configured at "
                            + "initialization time.");
        }
    }
}
