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
package org.apache.tika.parser.xml;

import org.apache.commons.codec.binary.Base64;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

public class FictionBookParser extends XMLParser {
    private static final long serialVersionUID = 4195954546491524374L;
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(MediaType.application("x-fictionbook+xml"));
    }

    @Override
    protected ContentHandler getContentHandler(ContentHandler handler, Metadata metadata, ParseContext context) {
        EmbeddedDocumentExtractor ex = context.get(EmbeddedDocumentExtractor.class);

        if (ex == null) {
            ex = new ParsingEmbeddedDocumentExtractor(context);
        }

        return new BinaryElementsDataHandler(ex, handler);
    }

    private static class BinaryElementsDataHandler extends DefaultHandler {
        private static final String ELEMENT_BINARY = "binary";

        private boolean binaryMode = false;
        private static final String ATTRIBUTE_ID = "id";

        private final EmbeddedDocumentExtractor partExtractor;
        private final ContentHandler handler;
        private final StringBuilder binaryData = new StringBuilder();
        private Metadata metadata;
        private static final String ATTRIBUTE_CONTENT_TYPE = "content-type";

        private BinaryElementsDataHandler(EmbeddedDocumentExtractor partExtractor, ContentHandler handler) {
            this.partExtractor = partExtractor;
            this.handler = handler;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            binaryMode = ELEMENT_BINARY.equals(localName);
            if (binaryMode) {
                binaryData.setLength(0);
                metadata = new Metadata();

                metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, attributes.getValue(ATTRIBUTE_ID));
                metadata.set(Metadata.CONTENT_TYPE, attributes.getValue(ATTRIBUTE_CONTENT_TYPE));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (binaryMode) {
                try {
                    partExtractor.parseEmbedded(
                            new ByteArrayInputStream(Base64.decodeBase64(binaryData.toString())),
                            handler,
                            metadata,
                            true
                    );
                } catch (IOException e) {
                    throw new SAXException("IOException in parseEmbedded", e);
                }

                binaryMode = false;
                binaryData.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (!binaryMode) {
                handler.characters(ch, start, length);
            } else {
                binaryData.append(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            handler.ignorableWhitespace(ch, start, length);
        }
    }
}
