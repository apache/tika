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

package org.apache.tika.parser.microsoft.ooxml.xwpf;


import java.util.HashMap;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

class Word2006MLHandler extends DefaultHandler {

    final static String PKG_NS = "http://schemas.microsoft.com/office/2006/xmlPackage";


    private final XHTMLContentHandler handler;
    private final Metadata metadata;
    private final ParseContext parseContext;

    private final Map<String, PartHandler> partHandlers = new HashMap<>();
    private final BinaryDataHandler binaryDataHandler;
    private final RelationshipsManager relationshipsManager = new RelationshipsManager();
    private PartHandler currentPartHandler = null;

    public Word2006MLHandler(XHTMLContentHandler handler, Metadata metadata, ParseContext context) {
        this.handler = handler;
        this.metadata = metadata;
        this.parseContext = context;

        addPackageHandler(new RelationshipsHandler(relationshipsManager));

        addPackageHandler(new BodyContentHandler(
                XWPFRelation.DOCUMENT.getContentType(),
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                XWPFRelation.FOOTNOTE.getContentType(),
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.endnotes+xml",
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                XWPFRelation.HEADER.getContentType(),
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                XWPFRelation.FOOTER.getContentType(),
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.comments+xml",
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document.glossary+xml",
                relationshipsManager,
                handler, metadata, context));
        addPackageHandler(new BodyContentHandler(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.footnotes+xml",
                relationshipsManager,
                handler, metadata, context));

        addPackageHandler(new CorePropertiesHandler(metadata));
        addPackageHandler(new ExtendedPropertiesHandler(metadata));
        binaryDataHandler = new BinaryDataHandler(handler, metadata, context);
    }

    private void addPackageHandler(PartHandler partHandler) {
        partHandlers.put(partHandler.getPartContentType(), partHandler);
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
