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

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.utils.XMLReaderUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;


class OpenDocumentMacroHandler extends FlatOpenDocumentMacroHandler {

    OpenDocumentMacroHandler(ContentHandler contentHandler, ParseContext parseContext) {
        super(contentHandler, parseContext);
    }

    @Override
    public void startElement(
            String namespaceURI, String localName, String qName,
            Attributes attrs) throws SAXException {
        inMacro = true;
        macroName = XMLReaderUtils.getAttrValue(NAME, attrs);
    }


    @Override
    public void endElement(
            String namespaceURI, String localName, String qName) throws SAXException {
        if (MODULE.equals(localName)) {
            try {
                handleMacro();
            } catch (IOException e) {
                throw new SAXException(e);
            }
        }
    }
}