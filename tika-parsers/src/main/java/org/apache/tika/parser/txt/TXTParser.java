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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
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

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // We need mark support for detecting the character encoding
        stream = new BufferedInputStream(stream);

        MediaType type = detectEncoding(stream, metadata);
        String encoding = type.getParameters().get("charset");
        if (encoding != null) {
            metadata.set(Metadata.CONTENT_ENCODING, encoding);
        } else {
            throw new TikaException(
                    "Text encoding could not be detected and no encoding"
                    + " hint is available in document metadata");
        }

        // TIKA-341: Only stomp on content-type after we're done trying to
        // use it to guess at the charset.
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        try {
            Reader reader =
                    new BufferedReader(new InputStreamReader(stream, encoding));

            // TIKA-240: Drop the BOM when extracting plain text
            reader.mark(1);
            int bom = reader.read();
            if (bom != '\ufeff') { // zero-width no-break space
                reader.reset();
            }

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
        } catch (UnsupportedEncodingException e) {
            throw new TikaException(
                    "Unsupported text encoding: " + encoding, e);
        }
    }

    private MediaType detectEncoding(InputStream stream, Metadata metadata)
            throws IOException {
        ServiceLoader loader =
                new ServiceLoader(TXTParser.class.getClassLoader());
        for (EncodingDetector detector
                : loader.loadServiceProviders(EncodingDetector.class)) {
            MediaType type = detector.detect(stream, metadata);
            if (!MediaType.OCTET_STREAM.equals(type)) {
                return type;
            }
        }
        return MediaType.OCTET_STREAM;
    }

}
