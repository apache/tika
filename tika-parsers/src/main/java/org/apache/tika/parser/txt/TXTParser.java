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
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Plain text parser. The text encoding of the document stream is
 * automatically detected based on the byte patterns found at the
 * beginning of the stream and the given document metadata, most
 * notably the <code>charset</code> parameter of a
 * {@link org.apache.tika.metadata.HttpHeaders#CONTENT_TYPE} value.
 * <p/>
 * This parser sets the following output metadata entries:
 * <dl>
 * <dt>{@link org.apache.tika.metadata.HttpHeaders#CONTENT_TYPE}</dt>
 * <dd><code>text/plain; charset=...</code></dd>
 * </dl>
 */
public class TXTParser extends AbstractEncodingDetectorParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -6656102320836888910L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.TEXT_PLAIN);

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public TXTParser() {
        super();
    }

    public TXTParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // Automatically detect the character encoding
        try (AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata, getEncodingDetector(context))) {
            //try to get detected content type; could be a subclass of text/plain
            //such as vcal, etc.
            String incomingMime = metadata.get(Metadata.CONTENT_TYPE);
            MediaType mediaType = MediaType.TEXT_PLAIN;
            if (incomingMime != null) {
                MediaType tmpMediaType = MediaType.parse(incomingMime);
                if (tmpMediaType != null) {
                    mediaType = tmpMediaType;
                }
            }
            Charset charset = reader.getCharset();
            MediaType type = new MediaType(mediaType, charset);
            metadata.set(Metadata.CONTENT_TYPE, type.toString());
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

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
        }
    }

}
