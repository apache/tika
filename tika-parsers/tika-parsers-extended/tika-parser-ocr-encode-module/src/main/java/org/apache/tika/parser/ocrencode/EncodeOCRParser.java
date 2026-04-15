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
package org.apache.tika.parser.ocrencode;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ParentContentHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractExternalProcessParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser that base64-encodes image content instead of performing OCR
 * text extraction. This is useful when you need to preserve the original
 * image data in the parsed output for downstream processing by an
 * external service.
 * <p>
 * To configure this parser, pass an {@link EncodeOCRConfig} object
 * through the ParseContext, or configure it via tika-config.xml/json.
 */
@TikaComponent(spi = false)
public class EncodeOCRParser
        extends AbstractExternalProcessParser
        implements Initializable {

    private static final String OCR = "ocr-";
    private static final Logger LOG = LoggerFactory.getLogger(
            EncodeOCRParser.class);
    private static final Object[] LOCK = new Object[0];
    private static final long serialVersionUID = -8167538283213097266L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    MediaType.image(OCR + "png"),
                    MediaType.image(OCR + "jpeg"),
                    MediaType.image(OCR + "tiff"),
                    MediaType.image(OCR + "bmp"),
                    MediaType.image(OCR + "gif"),
                    // these are not currently covered by other parsers
                    MediaType.image("jp2"),
                    MediaType.image("jpx"),
                    MediaType.image("x-portable-pixmap"),
                    // add the ocr- versions as well
                    MediaType.image(OCR + "jp2"),
                    MediaType.image(OCR + "jpx"),
                    MediaType.image(OCR + "x-portable-pixmap")
            )));
    private static volatile boolean hasWarned = false;

    private EncodeOCRConfig defaultConfig = new EncodeOCRConfig();

    public EncodeOCRParser() {
    }

    public EncodeOCRParser(EncodeOCRConfig config) {
        this.defaultConfig = config;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public EncodeOCRParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(
                jsonConfig, EncodeOCRConfig.class));
    }

    @Override
    public void initialize() throws TikaConfigException {
        //no-op
    }

    public void checkInitialization() throws TikaConfigException {
        //no-op
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        EncodeOCRConfig config = resolveConfig(context);
        if (config.isSkipOcr()) {
            return Collections.emptySet();
        }
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(
            TikaInputStream tis,
            ContentHandler handler,
            Metadata metadata,
            ParseContext parseContext
    ) throws IOException, SAXException, TikaException {
        normalizeOCRMimeMetadata(metadata);

        ParseContext workingContext =
                parseContext != null ? parseContext : new ParseContext();

        EncodeOCRConfig config = resolveConfig(workingContext);
        if (config.isSkipOcr()) {
            return;
        }

        ContentHandler baseHandler = getContentHandler(
                config.isInlineContent(),
                handler,
                metadata,
                workingContext);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(
                baseHandler, metadata, workingContext);
        xhtml.startDocument();
        doEncode(tis, xhtml, workingContext, config);
        xhtml.endDocument();
    }

    private EncodeOCRConfig resolveConfig(ParseContext context) {
        EncodeOCRConfig userConfig = context.get(EncodeOCRConfig.class);
        return userConfig != null ? userConfig : defaultConfig;
    }

    private ContentHandler getContentHandler(
            boolean isInlineContent,
            ContentHandler handler,
            Metadata metadata,
            ParseContext parseContext) {
        if (!isInlineContent) {
            return handler;
        }
        ParentContentHandler parentContentHandler = parseContext.get(
                ParentContentHandler.class);
        if (parentContentHandler == null) {
            return handler;
        }
        String embeddedType = metadata.get(
                TikaCoreProperties.EMBEDDED_RESOURCE_TYPE);
        if (!TikaCoreProperties.EmbeddedResourceType.INLINE.name()
                .equals(embeddedType)) {
            return handler;
        }
        return new TeeContentHandler(
                new EmbeddedContentHandler(
                        new BodyContentHandler(
                                parentContentHandler.getContentHandler())),
                handler);
    }

    private void normalizeOCRMimeMetadata(Metadata metadata) {
        String parserOverride = metadata.get(
                TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE);
        if (parserOverride != null) {
            MediaType overrideType = MediaType.parse(parserOverride);
            if (overrideType != null
                    && overrideType.getSubtype().startsWith(OCR)) {
                metadata.remove(TikaCoreProperties
                        .CONTENT_TYPE_PARSER_OVERRIDE.getName());
            }
        }
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (contentType != null) {
            MediaType parsedType = MediaType.parse(contentType);
            if (parsedType != null
                    && parsedType.getSubtype().startsWith(OCR)) {
                metadata.set(Metadata.CONTENT_TYPE,
                        new MediaType(parsedType.getType(),
                                parsedType.getSubtype().substring(
                                        OCR.length())).toString());
            }
        }
    }

    private void doEncode(
            TikaInputStream tikaInputStream,
            ContentHandler xhtml,
            ParseContext parseContext,
            EncodeOCRConfig config
    ) throws IOException, SAXException, TikaException {
        warnOnFirstParse();

        long size = tikaInputStream.getLength();
        if (size >= config.getMinFileSizeToOcr()
                && size <= config.getMaxFileSizeToOcr()) {
            if (!reserveImageSlot(parseContext, config)) {
                OCRImageCounter counter = parseContext.get(
                        OCRImageCounter.class);
                int processed = counter != null
                        ? counter.get()
                        : config.getMaxImagesToOcr();
                LOG.info("Skipping OCR encode for image because "
                                + "the configured limit of {} images "
                                + "has been reached ({} already processed)",
                        config.getMaxImagesToOcr(), processed);
                return;
            }
            encodeToBase64(tikaInputStream, size, xhtml);
        } else {
            LOG.debug("File size {} is outside the allowed "
                            + "range for OCR encode: {} - {}",
                    size,
                    config.getMinFileSizeToOcr(),
                    config.getMaxFileSizeToOcr());
        }
    }

    private boolean reserveImageSlot(
            ParseContext parseContext,
            EncodeOCRConfig config) {
        OCRImageCounter counter = parseContext.get(OCRImageCounter.class);
        if (counter == null) {
            counter = new OCRImageCounter();
            parseContext.set(OCRImageCounter.class, counter);
        }
        return counter.tryIncrement(config.getMaxImagesToOcr());
    }

    private void encodeToBase64(
            InputStream input,
            long fileSize,
            ContentHandler xhtml
    ) throws IOException, SAXException {
        long startTime = System.nanoTime();

        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "ocr");
        xhtml.startElement(XHTML, "div", "div", attrs);

        String beginMarker = "\n<<<---IMAGE-BASE64-ENCODED-BEGIN--->>>\n";
        xhtml.characters(
                beginMarker.toCharArray(), 0, beginMarker.length());

        CharForwardingOutputStream sink =
                new CharForwardingOutputStream(xhtml);
        try (OutputStream base64Out = Base64.getEncoder().wrap(sink)) {
            input.transferTo(base64Out);
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            }
            throw e;
        }

        String endMarker = "\n<<<---IMAGE-BASE64-ENCODED-END--->>>\n";
        xhtml.characters(
                endMarker.toCharArray(), 0, endMarker.length());
        xhtml.endElement(XHTML, "div", "div");

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        LOG.info("OCR encoding - input file size: {} bytes, "
                        + "output size: {} characters, "
                        + "time taken: {} ms",
                fileSize, sink.totalChars(), durationMs);
    }

    private void warnOnFirstParse() {
        if (!hasWarned) {
            synchronized (LOCK) {
                if (!hasWarned) {
                    LOG.info("OCR encode is being invoked. "
                            + "This can add greatly to processing time.");
                    hasWarned = true;
                }
            }
        }
    }

    public EncodeOCRConfig getDefaultConfig() {
        return defaultConfig;
    }

    private static final class OCRImageCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        boolean tryIncrement(int maxAllowed) {
            while (true) {
                int current = count.get();
                if (current >= maxAllowed) {
                    return false;
                }
                if (count.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }

        int get() {
            return count.get();
        }
    }

    /**
     * Converts each written byte (which is always an ASCII character
     * produced by {@link Base64.Encoder}) into a char and forwards it
     * to the wrapped {@link ContentHandler} via
     * {@link ContentHandler#characters(char[], int, int)}.
     */
    private static final class CharForwardingOutputStream
            extends OutputStream {
        private final ContentHandler xhtml;
        private final AtomicLong totalChars = new AtomicLong();

        CharForwardingOutputStream(ContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void write(int b) throws IOException {
            try {
                char[] c = {(char) (b & 0xFF)};
                xhtml.characters(c, 0, 1);
                totalChars.incrementAndGet();
            } catch (SAXException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len <= 0) {
                return;
            }
            char[] buf = new char[len];
            for (int i = 0; i < len; i++) {
                buf[i] = (char) (b[off + i] & 0xFF);
            }
            try {
                xhtml.characters(buf, 0, len);
                totalChars.addAndGet(len);
            } catch (SAXException e) {
                throw new IOException(e);
            }
        }

        long totalChars() {
            return totalChars.get();
        }
    }
}
