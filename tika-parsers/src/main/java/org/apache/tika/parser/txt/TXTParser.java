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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Plain text parser. The text encoding of the document stream is
 * automatically detected based on the byte patterns found at the
 * beginning of the stream. The input metadata key
 * {@link HttpHeaders#CONTENT_ENCODING} is used as an encoding hint
 * if the automatic encoding detection fails.
 * <p>
 * This parser sets the following output metadata entries:
 * <dl>
 *   <dt>{@link HttpHeaders#CONTENT_TYPE}</dt>
 *   <dd><code>text/plain</code></dd>
 *   <dt>{@link HttpHeaders#CONTENT_ENCODING}</dt>
 *   <dd>The detected text encoding of the document.</dd>
 *   <dt>
 *     {@link HttpHeaders#CONTENT_LANGUAGE} and {@link DublinCore#LANGUAGE}
 *   </dt>
 *   <dd>
 *     The default language of the detected encoding. Only set if the
 *     detected encoding is associated with some specific language
 *     (for example KOI8-R with Russian or SJIS with Japanese).
 *   </dd>
 * </dl>
 */
public class TXTParser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "text/plain");

        // CharsetDetector expects a stream to support marks
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        // Detect the content encoding (the stream is reset to the beginning)
        // TODO: Better use of the possible encoding hint in input metadata
        CharsetMatch match = new CharsetDetector().setText(stream).detect();
        if (match != null) {
            metadata.set(Metadata.CONTENT_ENCODING, match.getName());

            // Is the encoding language-specific (KOI8-R, SJIS, etc.)?
            String language = match.getLanguage();
            if (language != null) {
                metadata.set(Metadata.CONTENT_LANGUAGE, match.getLanguage());
                metadata.set(Metadata.LANGUAGE, match.getLanguage());
            }
        }

        String encoding = metadata.get(Metadata.CONTENT_ENCODING);
        if (encoding == null) {
            throw new TikaException(
                    "Text encoding could not be detected and no encoding"
                    + " hint is available in document metadata");
        }

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

}
