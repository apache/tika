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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.ParseContextConfig;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ParentContentHandler;
import org.apache.tika.http.TikaHttpClient;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Abstract base class for parsers that delegate to a remote Vision-Language
 * Model (VLM) endpoint for OCR and document understanding.
 * <p>
 * Subclasses only need to implement the API-specific request/response
 * serialization and declare their supported media types. All common logic
 * (HTTP transport, timeout handling, inline content, markdown-to-XHTML
 * rendering, config resolution) lives here.
 *
 * @since Apache Tika 4.0
 */
public abstract class AbstractVLMParser implements Parser, Initializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractVLMParser.class);

    /** Metadata namespace for VLM properties. */
    public static final String VLM_META = "vlm:";

    public static final Property VLM_MODEL = Property.externalText(VLM_META + "model");
    public static final Property VLM_PROMPT_TOKENS =
            Property.externalInteger(VLM_META + "prompt_tokens");
    public static final Property VLM_COMPLETION_TOKENS =
            Property.externalInteger(VLM_META + "completion_tokens");

    /**
     * Encapsulates a fully built HTTP request for a VLM API call.
     *
     * @param url     full request URL (base + path)
     * @param json    serialized JSON request body
     * @param headers additional HTTP headers (e.g. Authorization)
     */
    protected record HttpCall(String url, String json, Map<String, String> headers) {}

    private VLMOCRConfig defaultConfig;
    private transient TikaHttpClient httpClient;
    private boolean serverAvailable = false;

    protected AbstractVLMParser(VLMOCRConfig config) {
        this.defaultConfig = config;
        this.httpClient = buildHttpClient();
    }

    // ---- abstract contract for subclasses ---------------------------------

    /**
     * Build a fully formed {@link HttpCall} for the target API.
     *
     * @param config     resolved config for this parse
     * @param base64Data base64-encoded version of the file bytes
     * @param mimeType   the MIME type of the input (e.g. {@code image/png})
     * @return a ready-to-execute {@link HttpCall}
     */
    protected abstract HttpCall buildHttpCall(VLMOCRConfig config,
                                              String base64Data, String mimeType);

    /**
     * Parse the API response body and extract the model's text output.
     * Implementations should also populate {@link #VLM_PROMPT_TOKENS} and
     * {@link #VLM_COMPLETION_TOKENS} in metadata when the information is
     * available.
     *
     * @param responseBody raw JSON response body
     * @param metadata     metadata to enrich with token counts
     * @return the extracted text content
     */
    protected abstract String extractResponseText(String responseBody, Metadata metadata)
            throws TikaException;

    /**
     * @return the set of media types this parser handles (images, PDFs, etc.)
     */
    protected abstract Set<MediaType> getSupportedMediaTypes();

    /**
     * @return the JSON config key for {@link ParseContextConfig} lookup
     *         (e.g. {@code "openai-vlm-parser"}, {@code "gemini-vlm-parser"})
     */
    protected abstract String configKey();

    /**
     * @return an optional health-check URL to probe at init time, or
     *         {@code null} to skip the probe
     */
    protected abstract String getHealthCheckUrl(VLMOCRConfig config);

    // ---- Parser interface -------------------------------------------------

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (!serverAvailable) {
            return Collections.emptySet();
        }
        VLMOCRConfig config = context.get(VLMOCRConfig.class);
        if (config != null && config.isSkipOcr()) {
            return Collections.emptySet();
        }
        if (defaultConfig.isSkipOcr()) {
            return Collections.emptySet();
        }
        return getSupportedMediaTypes();
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext parseContext) throws IOException, SAXException, TikaException {

        VLMOCRConfig config = getConfig(parseContext);

        if (config.isSkipOcr()) {
            return;
        }

        long size = tis.getLength();
        if (size >= 0
                && (size < config.getMinFileSizeToOcr()
                || size > config.getMaxFileSizeToOcr())) {
            return;
        }

        String mimeType = detectMimeType(metadata);

        long maxPixels = config.getMaxImagePixels();
        if (maxPixels > 0 && mimeType.startsWith("image/")) {
            tis.mark((int) Math.min(tis.getLength() + 1, 1024 * 1024));
            try {
                long pixels = getImagePixels(tis);
                if (pixels > maxPixels) {
                    LOG.warn("Image has {} pixels, exceeding maxImagePixels={}. "
                            + "Skipping VLM OCR.", pixels, maxPixels);
                    return;
                }
            } catch (IOException e) {
                LOG.debug("Could not read image dimensions; "
                        + "skipping pixel-limit check", e);
            } finally {
                tis.reset();
            }
        }

        byte[] fileBytes = readFully(tis);
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

        long timeoutMillis = TikaTaskTimeout.getTimeoutMillis(
                parseContext, config.getTimeoutSeconds() * 1000L);
        int timeoutSeconds = (int) (timeoutMillis / 1000L);

        HttpCall call = buildHttpCall(config, base64Data, mimeType);

        String responseText;
        try {
            String responseBody = httpClient.postJson(
                    call.url(), call.json(), call.headers(), timeoutSeconds);
            responseText = extractResponseText(responseBody, metadata);
        } catch (TikaException e) {
            throw e;
        } catch (IOException e) {
            throw new TikaException("VLM request failed: " + e.getMessage(), e);
        }

        metadata.set(VLM_MODEL, config.getModel());

        ContentHandler baseHandler = getContentHandler(
                config.isInlineContent(), handler, metadata, parseContext);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(baseHandler, metadata, parseContext);
        xhtml.startDocument();
        writeOutput(xhtml, responseText);
        xhtml.endDocument();
    }

    // ---- Initializable ----------------------------------------------------

    @Override
    public void initialize() throws TikaConfigException {
        this.httpClient = buildHttpClient();
        String healthUrl = getHealthCheckUrl(defaultConfig);
        if (healthUrl == null) {
            // No health check configured (e.g. Claude) — assume available
            serverAvailable = true;
            return;
        }
        try {
            httpClient.get(healthUrl, Map.of(), defaultConfig.getTimeoutSeconds());
            serverAvailable = true;
            LOG.info("VLM server is available at {}", defaultConfig.getBaseUrl());
        } catch (TikaException e) {
            LOG.warn("VLM server returned error at {}: {}",
                    defaultConfig.getBaseUrl(), e.getMessage());
            serverAvailable = false;
        } catch (IOException e) {
            LOG.warn("VLM server is not available at {}: {}",
                    defaultConfig.getBaseUrl(), e.getMessage());
            serverAvailable = false;
        }
    }

    // ---- shared helpers ---------------------------------------------------

    protected VLMOCRConfig getConfig(ParseContext parseContext)
            throws TikaConfigException, IOException {
        String key = configKey();
        if (parseContext.hasJsonConfig(key)) {
            VLMOCRConfig.RuntimeConfig runtimeConfig = ParseContextConfig.getConfig(
                    parseContext, key, VLMOCRConfig.RuntimeConfig.class,
                    new VLMOCRConfig.RuntimeConfig(defaultConfig));

            if (runtimeConfig.isSkipOcr()) {
                return runtimeConfig;
            }

            return ParseContextConfig.getConfig(
                    parseContext, key, VLMOCRConfig.class, defaultConfig);
        }
        VLMOCRConfig userConfig = parseContext.get(VLMOCRConfig.class);
        if (userConfig != null) {
            return userConfig;
        }
        return defaultConfig;
    }

    protected static String stripTrailingSlash(String url) {
        return url.replaceAll("/+$", "");
    }

    String detectMimeType(Metadata metadata) {
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null) {
            contentType = contentType.replace("ocr-", "");
            if (contentType.startsWith("image/") || contentType.equals("application/pdf")) {
                return contentType;
            }
        }
        return "image/png";
    }

    private ContentHandler getContentHandler(boolean isInlineContent, ContentHandler handler,
                                             Metadata metadata, ParseContext parseContext) {
        if (!isInlineContent) {
            return handler;
        }
        ParentContentHandler parentContentHandler =
                parseContext.get(ParentContentHandler.class);
        if (parentContentHandler == null) {
            return handler;
        }
        String embeddedType = metadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        if (!TikaCoreProperties.EmbeddedResourceType.INLINE.name().equals(embeddedType)) {
            return handler;
        }
        return new TeeContentHandler(
                new EmbeddedContentHandler(
                        new BodyContentHandler(
                                parentContentHandler.getContentHandler())),
                handler);
    }

    private void writeOutput(XHTMLContentHandler xhtml, String text) throws SAXException {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "ocr");
        xhtml.startElement(XHTML, "div", "div", attrs);
        if (text != null && !text.isEmpty()) {
            MarkdownToXHTMLEmitter.emit(text, xhtml);
        }
        xhtml.endElement(XHTML, "div", "div");
    }

    private byte[] readFully(InputStream is) throws IOException {
        return is.readAllBytes();
    }

    /**
     * Reads only the image header to determine width &times; height
     * without decoding the full raster. Returns {@code -1} if dimensions
     * cannot be determined.
     */
    private long getImagePixels(InputStream is) throws IOException {
        try (javax.imageio.stream.ImageInputStream iis =
                     javax.imageio.ImageIO.createImageInputStream(is)) {
            if (iis == null) {
                return -1;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers =
                    javax.imageio.ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return -1;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long w = reader.getWidth(0);
                long h = reader.getHeight(0);
                return w * h;
            } finally {
                reader.dispose();
            }
        }
    }

    private TikaHttpClient buildHttpClient() {
        return TikaHttpClient.build(30);
    }

    // ---- delegating config getters/setters --------------------------------

    protected VLMOCRConfig getDefaultConfig() {
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

    public String getPrompt() {
        return defaultConfig.getPrompt();
    }

    public void setPrompt(String prompt) {
        defaultConfig.setPrompt(prompt);
    }

    public int getMaxTokens() {
        return defaultConfig.getMaxTokens();
    }

    public void setMaxTokens(int maxTokens) {
        defaultConfig.setMaxTokens(maxTokens);
    }

    public int getTimeoutSeconds() {
        return defaultConfig.getTimeoutSeconds();
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        defaultConfig.setTimeoutSeconds(timeoutSeconds);
    }

    public String getApiKey() {
        return defaultConfig.getApiKey();
    }

    public void setApiKey(String apiKey) throws TikaConfigException {
        defaultConfig.setApiKey(apiKey);
    }

    public boolean isInlineContent() {
        return defaultConfig.isInlineContent();
    }

    public void setInlineContent(boolean inlineContent) {
        defaultConfig.setInlineContent(inlineContent);
    }

    public boolean isSkipOcr() {
        return defaultConfig.isSkipOcr();
    }

    public void setSkipOcr(boolean skipOcr) {
        defaultConfig.setSkipOcr(skipOcr);
    }

    public long getMinFileSizeToOcr() {
        return defaultConfig.getMinFileSizeToOcr();
    }

    public void setMinFileSizeToOcr(long minFileSizeToOcr) {
        defaultConfig.setMinFileSizeToOcr(minFileSizeToOcr);
    }

    public long getMaxFileSizeToOcr() {
        return defaultConfig.getMaxFileSizeToOcr();
    }

    public void setMaxFileSizeToOcr(long maxFileSizeToOcr) {
        defaultConfig.setMaxFileSizeToOcr(maxFileSizeToOcr);
    }

    public boolean isServerAvailable() {
        return serverAvailable;
    }
}
