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
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractorFactory;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.sax.SecureContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoDetectParser extends CompositeParser {

    /** Serial version UID */
    private static final long serialVersionUID = 6110455808615143122L;

    // private final TikaConfig config;

    /** The type detector used by this parser to auto-detect the type of a document. */
    private Detector detector; // always set in the constructor

    /** Configuration used when initializing a SecureContentHandler and the TikaInputStream. */
    private AutoDetectParserConfig autoDetectParserConfig;

    /** Creates an auto-detecting parser instance using the default Tika configuration. */
    public AutoDetectParser() {
        this(TikaConfig.getDefaultConfig());
    }

    public AutoDetectParser(Detector detector) {
        this(TikaConfig.getDefaultConfig());
        setDetector(detector);
    }

    /**
     * Creates an auto-detecting parser instance using the specified set of parser. This allows one
     * to create a Tika configuration where only a subset of the available parsers have their 3rd
     * party jars included, as otherwise the use of the default TikaConfig will throw various
     * "ClassNotFound" exceptions.
     *
     * @param parsers
     */
    public AutoDetectParser(Parser... parsers) {
        this(new DefaultDetector(), parsers);
    }

    public AutoDetectParser(Detector detector, Parser... parsers) {
        super(MediaTypeRegistry.getDefaultRegistry(), parsers);
        setDetector(detector);
        setAutoDetectParserConfig(AutoDetectParserConfig.DEFAULT);
    }

    public AutoDetectParser(TikaConfig config) {
        super(config.getMediaTypeRegistry(), getParser(config));
        setFallback(buildFallbackParser(config));
        setDetector(config.getDetector());
        setAutoDetectParserConfig(config.getAutoDetectParserConfig());
    }

    private static Parser buildFallbackParser(TikaConfig config) {
        Parser fallback = null;
        Parser p = config.getParser();
        if (p instanceof DefaultParser) {
            fallback = ((DefaultParser) p).getFallback();
        } else {
            fallback = new EmptyParser();
        }

        if (config.getAutoDetectParserConfig().getDigesterFactory() == null) {
            return fallback;
        } else {
            return new DigestingParser(
                    fallback,
                    config.getAutoDetectParserConfig().getDigesterFactory().build(),
                    config.getAutoDetectParserConfig()
                            .getDigesterFactory()
                            .isSkipContainerDocument());
        }
    }

    private static Parser getParser(TikaConfig config) {
        if (config.getAutoDetectParserConfig().getDigesterFactory() == null) {
            return config.getParser();
        }
        return new DigestingParser(
                config.getParser(),
                config.getAutoDetectParserConfig().getDigesterFactory().build(),
                config.getAutoDetectParserConfig().getDigesterFactory().isSkipContainerDocument());
    }

    /**
     * Returns the type detector used by this parser to auto-detect the type of a document.
     *
     * @return type detector
     * @since Apache Tika 0.4
     */
    public Detector getDetector() {
        return detector;
    }

    /**
     * Sets the type detector used by this parser to auto-detect the type of a document.
     *
     * @param detector type detector
     * @since Apache Tika 0.4
     */
    public void setDetector(Detector detector) {
        this.detector = detector;
    }

    /**
     * Sets the configuration that will be used to create SecureContentHandlers that will be used
     * for parsing.
     *
     * @param autoDetectParserConfig type SecureContentHandlerConfig
     * @since Apache Tika 2.1.1
     */
    public void setAutoDetectParserConfig(AutoDetectParserConfig autoDetectParserConfig) {
        this.autoDetectParserConfig = autoDetectParserConfig;
    }

    public AutoDetectParserConfig getAutoDetectParserConfig() {
        return this.autoDetectParserConfig;
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (autoDetectParserConfig.getMetadataWriteFilterFactory() != null) {
            metadata.setMetadataWriteFilter(
                    autoDetectParserConfig.getMetadataWriteFilterFactory().newInstance());
        }
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp, metadata);
            // figure out if we should spool to disk
            maybeSpool(tis, autoDetectParserConfig, metadata);

            // Automatically detect the MIME type of the document
            MediaType type = detector.detect(tis, metadata);
            // update CONTENT_TYPE as long as it wasn't set by parser override
            if (metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE) == null
                    || !metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE)
                            .equals(type.toString())) {
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
            }
            // check for zero-byte inputstream
            if (tis.getOpenContainer() == null) {
                if (autoDetectParserConfig.getThrowOnZeroBytes()) {
                    tis.mark(1);
                    if (tis.read() == -1) {
                        throw new ZeroByteFileException("InputStream must have > 0 bytes");
                    }
                    tis.reset();
                }
            }
            handler = decorateHandler(handler, metadata, context, autoDetectParserConfig);
            // TIKA-216: Zip bomb prevention
            SecureContentHandler sch =
                    handler != null
                            ? createSecureContentHandler(handler, tis, autoDetectParserConfig)
                            : null;

            initializeEmbeddedDocumentExtractor(metadata, context);
            try {
                // Parse the document
                super.parse(tis, sch, metadata, context);
            } catch (SAXException e) {
                // Convert zip bomb exceptions to TikaExceptions
                sch.throwIfCauseOf(e);
                throw e;
            }
        } finally {
            tmp.dispose();
        }
    }

    private ContentHandler decorateHandler(
            ContentHandler handler,
            Metadata metadata,
            ParseContext context,
            AutoDetectParserConfig autoDetectParserConfig) {
        if (context.get(RecursiveParserWrapper.RecursivelySecureContentHandler.class) != null) {
            // using the recursiveparserwrapper. we should decorate this handler
            return autoDetectParserConfig
                    .getContentHandlerDecoratorFactory()
                    .decorate(handler, metadata, context);
        }
        ParseRecord parseRecord = context.get(ParseRecord.class);
        if (parseRecord == null || parseRecord.getDepth() == 0) {
            return autoDetectParserConfig
                    .getContentHandlerDecoratorFactory()
                    .decorate(handler, metadata, context);
        }
        // else do not decorate
        return handler;
    }

    private void maybeSpool(
            TikaInputStream tis, AutoDetectParserConfig autoDetectParserConfig, Metadata metadata)
            throws IOException {
        if (tis.hasFile()) {
            return;
        }
        if (autoDetectParserConfig.getSpoolToDisk() == null) {
            return;
        }
        // whether or not a content-length has been sent in,
        // if spoolToDisk == 0, spool it
        if (autoDetectParserConfig.getSpoolToDisk() == 0) {
            tis.getPath();
            metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(tis.getLength()));
            return;
        }
        if (metadata.get(Metadata.CONTENT_LENGTH) != null) {
            long len = -1l;
            try {
                len = Long.parseLong(metadata.get(Metadata.CONTENT_LENGTH));
                if (len > autoDetectParserConfig.getSpoolToDisk()) {
                    tis.getPath();
                    metadata.set(HttpHeaders.CONTENT_LENGTH, Long.toString(tis.getLength()));
                }
            } catch (NumberFormatException e) {
                // swallow...maybe log?
            }
        }
    }

    private void initializeEmbeddedDocumentExtractor(Metadata metadata, ParseContext context) {
        if (context.get(EmbeddedDocumentExtractor.class) != null) {
            return;
        }
        // pass self to handle embedded documents if
        // the caller hasn't specified one.
        Parser p = context.get(Parser.class);
        if (p == null) {
            context.set(Parser.class, this);
        }
        EmbeddedDocumentExtractorFactory edxf =
                autoDetectParserConfig.getEmbeddedDocumentExtractorFactory();
        if (edxf == null) {
            edxf = new ParsingEmbeddedDocumentExtractorFactory();
        }
        EmbeddedDocumentExtractor edx = edxf.newInstance(metadata, context);
        context.set(EmbeddedDocumentExtractor.class, edx);
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, this);
        parse(stream, handler, metadata, context);
    }

    private SecureContentHandler createSecureContentHandler(
            ContentHandler handler, TikaInputStream tis, AutoDetectParserConfig config) {
        SecureContentHandler sch = new SecureContentHandler(handler, tis);
        if (config == null) {
            return sch;
        }

        if (config.getOutputThreshold() != null) {
            sch.setOutputThreshold(config.getOutputThreshold());
        }

        if (config.getMaximumCompressionRatio() != null) {
            sch.setMaximumCompressionRatio(config.getMaximumCompressionRatio());
        }

        if (config.getMaximumDepth() != null) {
            sch.setMaximumDepth(config.getMaximumDepth());
        }

        if (config.getMaximumPackageEntryDepth() != null) {
            sch.setMaximumPackageEntryDepth(config.getMaximumPackageEntryDepth());
        }
        return sch;
    }
}
