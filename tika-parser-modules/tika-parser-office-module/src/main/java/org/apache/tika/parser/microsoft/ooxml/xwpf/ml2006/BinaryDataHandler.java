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

package org.apache.tika.parser.microsoft.ooxml.xwpf.ml2006;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.codec.binary.Base64;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

class BinaryDataHandler extends AbstractPartHandler {

    private final XHTMLContentHandler handler;
    private final Metadata metadata;
    private final ParseContext parseContext;

    private boolean inBinaryData = false;
    private StringBuilder buffer = new StringBuilder();

    final Base64 base64 = new Base64();


    public BinaryDataHandler(XHTMLContentHandler handler, Metadata metadata, ParseContext context) {
        this.handler = handler;
        this.metadata = metadata;
        this.parseContext = context;
    }


    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {

    }

    @Override
    public void endPart() throws SAXException, TikaException {
        if (hasData()) {
            EmbeddedDocumentExtractor embeddedDocumentExtractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
            Metadata embeddedMetadata = new Metadata();
            try (TikaInputStream stream = TikaInputStream.get(getInputStream())) {
                embeddedDocumentExtractor.parseEmbedded(stream, handler, embeddedMetadata, false);
            } catch (IOException e) {
                throw new TikaException("error in finishing part", e);
            }
            buffer.setLength(0);
        }

    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {

        if (uri.equals(Word2006MLDocHandler.PKG_NS) && localName.equals("binaryData")) {
            inBinaryData = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (uri.equals(Word2006MLDocHandler.PKG_NS) && localName.equals("binaryData")) {
            inBinaryData = false;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inBinaryData) {
            buffer.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

    }

    @Override
    public String getContentType() {
        return "";
    }

    boolean hasData() {
        return buffer.length() > 0;
    }

    private InputStream getInputStream() {
        byte[] bytes = base64.decode(buffer.toString());
        return new ByteArrayInputStream(bytes);
    }
}
