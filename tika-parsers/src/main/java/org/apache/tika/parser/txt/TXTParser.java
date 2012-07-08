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
package org.apache.tika.parser.txt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Plain text parser. The text encoding of the document stream is
 * automatically detected based on the byte patterns found at the
 * beginning of the stream. The input metadata key
 * {@link org.apache.tika.metadata.HttpHeaders#CONTENT_ENCODING} is used
 * as an encoding hint if the automatic encoding detection fails.
 * <p>
 * This parser sets the following output metadata entries:
 * <dl>
 *   <dt>{@link org.apache.tika.metadata.HttpHeaders#CONTENT_TYPE}</dt>
 *   <dd><code>text/plain</code></dd>
 *   <dt>{@link org.apache.tika.metadata.HttpHeaders#CONTENT_ENCODING}</dt>
 *   <dd>The detected text encoding of the document.</dd>
 *   <dt>
 *     {@link org.apache.tika.metadata.HttpHeaders#CONTENT_LANGUAGE} and
 *     {@link org.apache.tika.metadata.DublinCore#LANGUAGE}
 *   </dt>
 * </dl>
 */
public class TXTParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -6656102320836888910L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.TEXT_PLAIN);

    private static final ServiceLoader LOADER =
            new ServiceLoader(TXTParser.class.getClassLoader());

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // Automatically detect the character encoding
        AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata, LOADER);
        try {
            metadata.set(Metadata.CONTENT_TYPE, "text/plain"); // TODO: charset
            metadata.set(Metadata.CONTENT_ENCODING, reader.getCharset().name());

            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.startElement("p");
            char[] buffer = new char[4096];
            int n = reader.read(buffer);
            while (n != -1) {
                xhtml.characters(buffer, 0, n);
                n = reader.read(buffer);
            }
            xhtml.endElement("p");

            xhtml.endDocument();
        } finally {
            reader.close();
        }
    }

}
