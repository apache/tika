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


import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class Word2006MLDocHandler extends DefaultHandler {

    final static String PKG_NS = "http://schemas.microsoft.com/office/2006/xmlPackage";


    private final XHTMLContentHandler xhtml;
    private final Metadata metadata;
    private final ParseContext parseContext;

    private final Map<String, PartHandler> partHandlers = new HashMap<>();
    private final BinaryDataHandler binaryDataHandler;
    private final RelationshipsManager relationshipsManager = new RelationshipsManager();
    private PartHandler currentPartHandler = null;

    public Word2006MLDocHandler(XHTMLContentHandler xhtml, Metadata metadata,
                                ParseContext context) {
        this.xhtml = xhtml;
        this.metadata = metadata;
        this.parseContext = context;
        OfficeParserConfig officeParserConfig = context.get(OfficeParserConfig.class);

        addPartHandler(new RelationshipsHandler(relationshipsManager));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                XWPFRelation.DOCUMENT.getContentType(),
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                XWPFRelation.FOOTNOTE.getContentType(),
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml",
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                XWPFRelation.HEADER.getContentType(),
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                XWPFRelation.FOOTER.getContentType(),
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
                xhtml, relationshipsManager, officeParserConfig));


        addPartHandler(new WordAndPowerPointTextPartHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml",
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new WordAndPowerPointTextPartHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml",
                xhtml, relationshipsManager, officeParserConfig));

        addPartHandler(new CorePropertiesHandler(metadata));
        addPartHandler(new ExtendedPropertiesHandler(metadata));
        binaryDataHandler = new BinaryDataHandler(xhtml, metadata, context);
    }

    private void addPartHandler(PartHandler partHandler) {
        partHandlers.put(partHandler.getContentType(), partHandler);
    }


    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {

    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        if (uri.equals(PKG_NS) && localName.equals("part")) {
            //start of a package
            String name = atts.getValue(PKG_NS, "name");
            String contentType = atts.getValue(PKG_NS, "contentType");
            currentPartHandler = partHandlers.get(contentType);
            //for now treat every unknown part type
            //as if it contained binary data
            if (currentPartHandler == null) {
                currentPartHandler = binaryDataHandler;
            }
            if (currentPartHandler != null) {
                currentPartHandler.setName(name);
            }
        } else if (currentPartHandler != null) {
            currentPartHandler.startElement(uri, localName, qName, atts);
        }

    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (uri.equals(PKG_NS) && localName.equals("part")) {
            //do post processing
            if (currentPartHandler != null) {
                try {
                    currentPartHandler.endPart();
                } catch (TikaException e) {
                    throw new SAXException(e);
                }
            }
            //then reset
            currentPartHandler = null;
        } else if (currentPartHandler != null) {
            currentPartHandler.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (currentPartHandler != null) {
            currentPartHandler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (currentPartHandler != null) {
            currentPartHandler.characters(ch, start, length);
        }

    }

}
