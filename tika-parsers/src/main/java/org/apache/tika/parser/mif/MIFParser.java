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
package org.apache.tika.parser.mif;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.EndDocumentShieldingContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MIFParser extends AbstractEncodingDetectorParser {

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    MediaType.application("vnd.mif"),
                    MediaType.application("x-maker"),
                    MediaType.application("x-mif"))));

    public MIFParser() {
        super();
    }

    public MIFParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        try (AutoDetectReader reader = new AutoDetectReader(
                new CloseShieldInputStream(stream), metadata, getEncodingDetector(context))) {

            Charset charset = reader.getCharset();
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());
            Optional<MediaType> firstElement = SUPPORTED_TYPES.stream().findFirst();
            metadata.set(Metadata.CONTENT_TYPE, firstElement.get().toString());

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();
            EndDocumentShieldingContentHandler parseHandler = new EndDocumentShieldingContentHandler(xhtml);
            MIFExtractor.parse(reader, getContentHandler(parseHandler, metadata));
            xhtml.endDocument();

            if (parseHandler.getEndDocumentWasCalled()) {
                parseHandler.reallyEndDocument();
            }
        }
    }

    /**
     * Get the content handler to use.
     *
     * @param handler the parent content handler.
     * @param metadata the metadata object.
     * @return the ContentHandler.
     */
    public ContentHandler getContentHandler(ContentHandler handler, Metadata metadata) {
        return new MIFContentHandler(handler, metadata);
    }
}
