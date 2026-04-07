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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX-based extractor for Visio OOXML (.vsdx) files.
 * Extracts text from {@code <Text>} elements inside shapes on each page.
 */
public class VSDXExtractorDecorator extends AbstractOOXMLExtractor {

    private static final String VISIO_DOCUMENT_REL =
            "http://schemas.microsoft.com/visio/2010/relationships/document";
    private static final String VISIO_PAGES_REL =
            "http://schemas.microsoft.com/visio/2010/relationships/pages";
    private static final String VISIO_PAGE_REL =
            "http://schemas.microsoft.com/visio/2010/relationships/page";

    private final ParseContext context;

    public VSDXExtractorDecorator(ParseContext context, OPCPackage pkg) {
        super(context, pkg);
        this.context = context;
    }

    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        try {
            List<PackagePart> pageParts = getPageParts();
            for (PackagePart pagePart : pageParts) {
                xhtml.startElement("div", "class", "page");
                try (InputStream is = pagePart.getInputStream()) {
                    XMLReaderUtils.parseSAX(is, new VisioPageHandler(xhtml), context);
                } catch (TikaException e) {
                    throw new SAXException(e);
                }
                xhtml.endElement("div");
            }
        } catch (InvalidFormatException e) {
            throw new SAXException("Error reading VSDX pages", e);
        }
    }

    private List<PackagePart> getPageParts() throws InvalidFormatException {
        // Root -> visio/document.xml
        PackagePart documentPart = getRelatedPart(opcPackage, VISIO_DOCUMENT_REL);
        if (documentPart == null) {
            return Collections.emptyList();
        }

        // document.xml -> pages/pages.xml
        PackagePart pagesPart = getRelatedPart(documentPart, VISIO_PAGES_REL);
        if (pagesPart == null) {
            return Collections.emptyList();
        }

        // pages.xml -> page1.xml, page2.xml, ...
        List<PackagePart> pageParts = new ArrayList<>();
        PackageRelationshipCollection pageRels =
                pagesPart.getRelationshipsByType(VISIO_PAGE_REL);
        for (PackageRelationship rel : pageRels) {
            PackagePart pagePart = pagesPart.getRelatedPart(rel);
            if (pagePart != null) {
                pageParts.add(pagePart);
            }
        }
        return pageParts;
    }

    private PackagePart getRelatedPart(OPCPackage pkg, String relType)
            throws InvalidFormatException {
        PackageRelationshipCollection rels = pkg.getRelationshipsByType(relType);
        if (rels.isEmpty()) {
            return null;
        }
        return pkg.getPart(rels.getRelationship(0));
    }

    private PackagePart getRelatedPart(PackagePart part, String relType)
            throws InvalidFormatException {
        PackageRelationshipCollection rels = part.getRelationshipsByType(relType);
        if (rels.isEmpty()) {
            return null;
        }
        return part.getRelatedPart(rels.getRelationship(0));
    }

    @Override
    protected List<PackagePart> getMainDocumentParts() {
        return Collections.emptyList();
    }

    /**
     * SAX handler for Visio page XML. Extracts text from {@code <Text>}
     * elements inside {@code <Shape>} elements.
     */
    private static class VisioPageHandler extends DefaultHandler {

        private static final String VISIO_NS =
                "http://schemas.microsoft.com/office/visio/2012/main";

        private final XHTMLContentHandler xhtml;
        private boolean inText;
        private final StringBuilder textBuffer = new StringBuilder();

        VisioPageHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) {
            if ("Text".equals(localName) && VISIO_NS.equals(uri)) {
                inText = true;
                textBuffer.setLength(0);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if ("Text".equals(localName) && VISIO_NS.equals(uri)) {
                inText = false;
                String text = textBuffer.toString().trim();
                if (!text.isEmpty()) {
                    xhtml.startElement("p");
                    xhtml.characters(text);
                    xhtml.endElement("p");
                }
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inText) {
                textBuffer.append(ch, start, length);
            }
        }
    }
}
