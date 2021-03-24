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
package org.apache.tika.parser.odf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Handler for macros in flat open documents
 */
class FlatOpenDocumentMacroHandler extends ContentHandlerDecorator {

    static String MODULE = "module";
    static String NAME = "name";
    private static String SOURCE_CODE = "source-code";
    private final ContentHandler contentHandler;
    private final ParseContext parseContext;
    private final StringBuilder macroBuffer = new StringBuilder();
    String macroName = null;
    boolean inMacro = false;
    private EmbeddedDocumentExtractor embeddedDocumentExtractor;

    FlatOpenDocumentMacroHandler(ContentHandler contentHandler, ParseContext parseContext) {
        super(contentHandler);
        this.contentHandler = contentHandler;
        this.parseContext = parseContext;
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes attrs)
            throws SAXException {
        if (MODULE.equals(localName)) {
            macroName = XMLReaderUtils.getAttrValue(NAME, attrs);
        } else if (SOURCE_CODE.equals(localName)) {
            inMacro = true;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inMacro) {
            macroBuffer.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName)
            throws SAXException {
        if (SOURCE_CODE.equals(localName)) {
            try {
                handleMacro();
            } catch (IOException e) {
                throw new SAXException(e);
            } finally {
                resetMacroState();
            }
        }
    }

    protected void resetMacroState() {
        macroBuffer.setLength(0);
        macroName = null;
        inMacro = false;
    }

    protected void handleMacro() throws IOException, SAXException {

        byte[] bytes = macroBuffer.toString().getBytes(StandardCharsets.UTF_8);

        if (embeddedDocumentExtractor == null) {
            embeddedDocumentExtractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(parseContext);
        }
        Metadata embeddedMetadata = new Metadata();
        if (!StringUtils.isBlank(macroName)) {
            embeddedMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, macroName);
        }
        embeddedMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.MACRO.toString());

        if (embeddedDocumentExtractor.shouldParseEmbedded(embeddedMetadata)) {
            try (InputStream is = TikaInputStream.get(bytes)) {
                embeddedDocumentExtractor
                        .parseEmbedded(is, contentHandler, embeddedMetadata, false);
            }
        }
    }
}
