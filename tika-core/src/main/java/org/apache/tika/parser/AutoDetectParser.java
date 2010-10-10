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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CountingInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.sax.SecureContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoDetectParser extends CompositeParser {

    /** Serial version UID */
    private static final long serialVersionUID = 6110455808615143122L;

    /**
     * The type detector used by this parser to auto-detect the type
     * of a document.
     */
    private Detector detector; // always set in the constructor

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
     * @param detector Detector to use
     * @param parsers
     */
    public AutoDetectParser(Parser...parsers) {
        this(MimeTypes.getDefaultMimeTypes(), parsers);
    }

    public AutoDetectParser(Detector detector, Parser...parsers) {
        super(MediaTypeRegistry.getDefaultRegistry(), parsers);
        setDetector(detector);
    }

    public AutoDetectParser(TikaConfig config) {
        super(config.getMediaTypeRegistry(), config.getParser());
        setDetector(config.getMimeRepository());
    }

    /**
     * @deprecated This method will be removed in Tika 1.0
     */
    public void setConfig(TikaConfig config) {
        setParsers(config.getParsers());
        setDetector(config.getMimeRepository());
        setMediaTypeRegistry(config.getMediaTypeRegistry());
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

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if(stream instanceof TikaInputStream || stream instanceof BufferedInputStream) {
           // Input stream can be trusted for type detection
        } else {
           // We need (reliable!) mark support for type detection before parsing
           stream = new BufferedInputStream(stream);
        }

        // Automatically detect the MIME type of the document
        MediaType type = detector.detect(stream, metadata);
        metadata.set(Metadata.CONTENT_TYPE, type.toString());

        // TIKA-216: Zip bomb prevention
        CountingInputStream count = new CountingInputStream(stream);
        SecureContentHandler secure = new SecureContentHandler(handler, count);

        // Parse the document
        try {
            super.parse(count, secure, metadata, context);
        } catch (SAXException e) {
            // Convert zip bomb exceptions to TikaExceptions
            secure.throwIfCauseOf(e);
            throw e;
        }
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        ParseContext context = new ParseContext();
        context.set(Parser.class, this);
        parse(stream, handler, metadata, context);
    }

}
