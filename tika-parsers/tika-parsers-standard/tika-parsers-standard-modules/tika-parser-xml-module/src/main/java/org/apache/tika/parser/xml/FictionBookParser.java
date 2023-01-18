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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

public class FictionBookParser extends XMLParser {
    private static final long serialVersionUID = 4195954546491524374L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-fictionbook+xml"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    protected ContentHandler getContentHandler(ContentHandler handler, Metadata metadata,
                                               ParseContext context) {
        return new BinaryElementsDataHandler(
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context), handler);
    }

    private static class BinaryElementsDataHandler extends DefaultHandler {
        private static final String ELEMENT_BINARY = "binary";
        private static final String ATTRIBUTE_ID = "id";
        private static final String ATTRIBUTE_CONTENT_TYPE = "content-type";
        private final EmbeddedDocumentExtractor partExtractor;
        private final ContentHandler handler;
        private final StringBuilder binaryData = new StringBuilder();
        private boolean binaryMode = false;
        private Metadata metadata;

        private BinaryElementsDataHandler(EmbeddedDocumentExtractor partExtractor,
                                          ContentHandler handler) {
            this.partExtractor = partExtractor;
            this.handler = handler;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            binaryMode = ELEMENT_BINARY.equals(localName);
            if (binaryMode) {
                binaryData.setLength(0);
                metadata = new Metadata();

                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                        attributes.getValue(ATTRIBUTE_ID));
                metadata.set(Metadata.CONTENT_TYPE, attributes.getValue(ATTRIBUTE_CONTENT_TYPE));
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (binaryMode) {
                try (InputStream stream =
                             new UnsynchronizedByteArrayInputStream(Base64.decodeBase64(binaryData.toString()))) {
                    partExtractor.parseEmbedded(
                            stream, handler, metadata, true);
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
