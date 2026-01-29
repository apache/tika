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
package org.apache.tika.parser.mail;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.MimeConfig;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Uses apache-mime4j to parse emails. Each part is treated with the
 * corresponding parser and displayed within elements.
 * <p/>
 * A {@link MimeConfig} object can be passed in the parsing context
 * to better control the parsing process.
 *
 * @author jnioche@digitalpebble.com
 */
@TikaComponent(name = "rfc822-parser")
public class RFC822Parser implements Parser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -5504243905998074168L;

    /**
     * Configuration class for JSON deserialization.
     */
    public static class Config {
        public boolean extractAllAlternatives = false;
    }

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.parse("message/rfc822"));

    //rely on the detector to be thread-safe
    //built lazily and then reused
    private Detector detector;

    private boolean extractAllAlternatives = false;

    public RFC822Parser() {
    }

    /**
     * Constructor with explicit Config object.
     *
     * @param config the configuration
     */
    public RFC822Parser(Config config) {
        this.extractAllAlternatives = config.extractAllAlternatives;
    }

    /**
     * Constructor for JSON configuration.
     * Requires Jackson on the classpath.
     *
     * @param jsonConfig JSON configuration
     */
    public RFC822Parser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, Config.class));
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        // Get the mime4j configuration, or use a default one
        MimeConfig config =
                new MimeConfig.Builder().setMaxLineLen(100000).setMaxHeaderLen(100000).build();

        config = context.get(MimeConfig.class, config);
        Detector localDetector = context.get(Detector.class);
        if (localDetector == null) {
            //lazily load this if necessary
            if (detector == null) {
                EmbeddedDocumentUtil embeddedDocumentUtil = new EmbeddedDocumentUtil(context);
                detector = embeddedDocumentUtil.getDetector();
            }
            localDetector = detector;
        }
        MimeStreamParser parser =
                new MimeStreamParser(config, null, new DefaultBodyDescriptorBuilder());
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);

        MailContentHandler mch = new MailContentHandler(xhtml, localDetector, metadata, context,
                config.isStrictParsing(), extractAllAlternatives);
        parser.setContentHandler(mch);
        parser.setContentDecoding(true);
        parser.setNoRecurse();
        xhtml.startDocument();
        checkForZeroByte(tis);//avoid stackoverflow
        try {
            parser.parse(tis);
        } catch (IOException e) {
            tis.throwIfCauseOf(e);
            throw new TikaException("Failed to parse an email message", e);
        } catch (MimeException e) {
            // Unwrap the exception in case it was not thrown by mime4j
            Throwable cause = e.getCause();
            if (cause instanceof TikaException) {
                throw (TikaException) cause;
            } else if (cause instanceof SAXException) {
                throw (SAXException) cause;
            } else {
                throw new TikaException("Failed to parse an email message", e);
            }
        }
        xhtml.endDocument();
    }

    private void checkForZeroByte(TikaInputStream tstream) throws IOException, ZeroByteFileException {
        tstream.mark(1);
        try {
            if (tstream.read() < 0) {
                throw new ZeroByteFileException("rfc822 parser found zero bytes");
            }
        } finally {
            tstream.reset();
        }
    }

    /**
     * Until version 1.17, Tika handled all body parts as embedded objects (see TIKA-2478).
     * In 1.17, we modified the parser to select only the best alternative body
     * parts for multipart/alternative sections, and we inline the content
     * as we do for .msg files.
     * <p>
     * The legacy behavior can be set by setting {@link #extractAllAlternatives}
     * to <code>true</code>.  As of 1.17, the default value is <code>false</code>
     *
     * @param extractAllAlternatives whether or not to extract all alternative parts
     * @since 1.17
     */
    public void setExtractAllAlternatives(boolean extractAllAlternatives) {
        this.extractAllAlternatives = extractAllAlternatives;
    }
}
