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
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Uses apache-mime4j to parse emails. Each part is treated with the
 * corresponding parser and displayed within elements.
 * <p/>
 * A {@link MimeConfig} object can be passed in the parsing context
 * to better control the parsing process.
 *
 * @author jnioche@digitalpebble.com
 */
public class RFC822Parser extends AbstractParser {
    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -5504243905998074168L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse("message/rfc822"));

    @Field
    private boolean extractAllAlternatives = false;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        // Get the mime4j configuration, or use a default one
        MimeConfig config = new MimeConfig.Builder()
                .setMaxLineLen(100000)
                .setMaxHeaderLen(100000)
                .build();

        config = context.get(MimeConfig.class, config);

        MimeStreamParser parser = new MimeStreamParser(config, null, new DefaultBodyDescriptorBuilder());
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        MailContentHandler mch = new MailContentHandler(
                xhtml, metadata, context, config.isStrictParsing(),
                extractAllAlternatives);
        parser.setContentHandler(mch);
        parser.setContentDecoding(true);
        
        TikaInputStream tstream = TikaInputStream.get(stream);
        try {
            parser.parse(tstream);
        } catch (IOException e) {
            tstream.throwIfCauseOf(e);
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
    }

    /**
     * Until version 1.17, Tika handled all body parts as embedded objects (see TIKA-2478).
     * In 1.17, we modified the parser to select only the best alternative body
     * parts for multipart/alternative sections, and we inline the content
     * as we do for .msg files.
     *
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
