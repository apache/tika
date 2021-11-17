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
import java.io.Serializable;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.sax.SecureContentHandler;

public class AutoDetectParser extends CompositeParser {

    /**
     * This config object can be used to tune how conservative we want to be
     * when parsing data that is extremely compressible and resembles a ZIP
     * bomb. Null values will be ignored and will not effect the default values
     * in SecureContentHandler.
     */
    public static class SecureContentHandlerConfig implements Serializable {

        /**
         * Desired output threshold in characters.
         */
        public final Long outputThreshold;

        /**
         * Desired maximum compression ratio.
         */
        public final Long maximumCompressionRatio;

        /**
         * Desired maximum XML nesting level.
         */
        public final Integer maximumDepth;

        /**
         * Desired maximum package entry nesting level.
         */
        public final Integer maximumPackageEntryDepth;

        /**
         * Creates a SecureContentHandlerConfig using the passed in parameters.
         * @param outputThreshold character output threshold.
         * @param maximumCompressionRatio max compression ratio allowed.
         * @param maximumDepth maximum XML element nesting level.
         * @param maximumPackageEntryDepth maximum package entry nesting level.
         */
        public SecureContentHandlerConfig(Long outputThreshold, Long maximumCompressionRatio,
                                          Integer maximumDepth,
                                          Integer maximumPackageEntryDepth) {
            this.outputThreshold = outputThreshold;
            this.maximumCompressionRatio = maximumCompressionRatio;
            this.maximumDepth = maximumDepth;
            this.maximumPackageEntryDepth = maximumPackageEntryDepth;
        }
    }

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 6110455808615143122L;
    //private final TikaConfig config;

    /**
     * The type detector used by this parser to auto-detect the type
     * of a document.
     */
    private Detector detector; // always set in the constructor

    /**
     * Configuration used when initializing a SecureContentHandler.
     */
    private SecureContentHandlerConfig secureContentHandlerConfig;

    /**
     * Creates an auto-detecting parser instance using the default Tika
     * configuration.
     */
    public AutoDetectParser() {
        this(TikaConfig.getDefaultConfig());
    }

    public AutoDetectParser(Detector detector) {
        this(TikaConfig.getDefaultConfig());
        setDetector(detector);
    }

    /**
     * Creates an auto-detecting parser instance using the specified set of parser.
     * This allows one to create a Tika configuration where only a subset of the
     * available parsers have their 3rd party jars included, as otherwise the
     * use of the default TikaConfig will throw various "ClassNotFound" exceptions.
     *
     * @param parsers
     */
    public AutoDetectParser(Parser... parsers) {
        this(new DefaultDetector(), parsers);
    }

    public AutoDetectParser(Detector detector, Parser... parsers) {
        super(MediaTypeRegistry.getDefaultRegistry(), parsers);
        setDetector(detector);
    }

    public AutoDetectParser(TikaConfig config) {
        super(config.getMediaTypeRegistry(), config.getParser());
        setDetector(config.getDetector());
    }

    /**
     * Returns the type detector used by this parser to auto-detect the type
     * of a document.
     *
     * @return type detector
     * @since Apache Tika 0.4
     */
    public Detector getDetector() {
        return detector;
    }

    /**
     * Sets the type detector used by this parser to auto-detect the type
     * of a document.
     *
     * @param detector type detector
     * @since Apache Tika 0.4
     */
    public void setDetector(Detector detector) {
        this.detector = detector;
    }

    /**
     * Sets the configuration that will be used to create SecureContentHandlers
     * that will be used for parsing.
     *
     * @param secureContentHandlerConfig type SecureContentHandlerConfig
     * @since Apache Tika 2.1.1
     */
    public void setSecureContentHandlerConfig(SecureContentHandlerConfig secureContentHandlerConfig) {
        this.secureContentHandlerConfig = secureContentHandlerConfig;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tis = TikaInputStream.get(stream, tmp);

            // Automatically detect the MIME type of the document
            MediaType type = detector.detect(tis, metadata);
            //update CONTENT_TYPE as long as it wasn't set by parser override
            if (metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE) == null ||
                    !metadata.get(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE)
                            .equals(type.toString())) {
                metadata.set(Metadata.CONTENT_TYPE, type.toString());
            }
            //check for zero-byte inputstream
            if (tis.getOpenContainer() == null) {
                tis.mark(1);
                if (tis.read() == -1) {
                    throw new ZeroByteFileException("InputStream must have > 0 bytes");
                }
                tis.reset();
            }
            // TIKA-216: Zip bomb prevention
            SecureContentHandler sch =
                    handler != null ?
                        createSecureContentHandler(handler, tis, secureContentHandlerConfig) : null;

            //pass self to handle embedded documents if
            //the caller hasn't specified one.
            if (context.get(EmbeddedDocumentExtractor.class) == null) {
                Parser p = context.get(Parser.class);
                if (p == null) {
                    context.set(Parser.class, this);
                }
                context.set(EmbeddedDocumentExtractor.class,
                        new ParsingEmbeddedDocumentExtractor(context));
            }

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

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, this);
        parse(stream, handler, metadata, context);
    }

    private SecureContentHandler createSecureContentHandler(ContentHandler handler, TikaInputStream tis,
                                                            SecureContentHandlerConfig config) {
        SecureContentHandler sch = new SecureContentHandler(handler, tis);
        if (config == null) {
            return sch;
        }

        if (config.outputThreshold != null) {
            sch.setOutputThreshold(config.outputThreshold);
        }

        if (config.maximumCompressionRatio != null) {
            sch.setMaximumCompressionRatio(config.maximumCompressionRatio);
        }

        if (config.maximumDepth != null) {
            sch.setMaximumDepth(config.maximumDepth);
        }

        if (config.maximumPackageEntryDepth != null) {
            sch.setMaximumPackageEntryDepth(config.maximumPackageEntryDepth);
        }
        return sch;
    }

}
