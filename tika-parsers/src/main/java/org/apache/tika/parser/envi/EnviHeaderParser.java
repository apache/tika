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
 * 
 */
package org.apache.tika.parser.envi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.nio.charset.Charset;

import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.sax.XHTMLContentHandler;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class EnviHeaderParser extends AbstractParser {

    private static final long serialVersionUID = -1479368523072408091L;

    public static final String ENVI_MIME_TYPE = "application/envi.hdr";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("envi.hdr"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        // Only outputting the MIME type as metadata
        metadata.set(Metadata.CONTENT_TYPE, ENVI_MIME_TYPE);

        // The following code was taken from the TXTParser
        // Automatically detect the character encoding
        AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata);

        try {
            Charset charset = reader.getCharset();
            MediaType type = new MediaType(MediaType.TEXT_PLAIN, charset);
            // deprecated, see TIKA-431
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler,
                    metadata);

            xhtml.startDocument();

            // text contents of the xhtml
            String line;
            while ((line = reader.readLine()) != null) {
                xhtml.startElement("p");
                xhtml.characters(line);
                xhtml.endElement("p");
            }
            
            xhtml.endDocument();
        } finally {
            reader.close();
        }
    }
}
