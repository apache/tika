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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ooxml.POIXMLDocument;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.EditType;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.parser.microsoft.ooxml.ParagraphProperties;
import org.apache.tika.parser.microsoft.ooxml.RunProperties;
import org.apache.tika.parser.microsoft.ooxml.XWPFBodyContentsHandler;
import org.apache.tika.parser.microsoft.ooxml.XWPFListManager;
import org.apache.tika.utils.XMLReaderUtils;

//TODO: move this into POI?

/**
 * Experimental class that is based on POI's XSSFEventBasedExcelExtractor
 */
public class XWPFEventBasedWordExtractor implements POIXMLTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(XWPFEventBasedWordExtractor.class);

    private OPCPackage container;
    private POIXMLProperties properties;
    private boolean includeGlossary = true;

    public XWPFEventBasedWordExtractor(OPCPackage container)
            throws XmlException, OpenXML4JException, IOException {
        this.container = container;
        // Properties are lazily initialized to avoid requiring ooxml-lite
        // when SAXBasedMetadataExtractor is used instead
    }

    private POIXMLProperties getOrCreateProperties() {
        if (properties == null) {
            try {
                properties = new POIXMLProperties(container);
            } catch (Exception e) {
                LOG.warn("Couldn't load properties", e);
            }
        }
        return properties;
    }

    public OPCPackage getPackage() {
        return this.container;
    }

    public void setIncludeGlossary(boolean includeGlossary) {
        this.includeGlossary = includeGlossary;
    }

    public POIXMLProperties.CoreProperties getCoreProperties() {
        POIXMLProperties props = getOrCreateProperties();
        return props != null ? props.getCoreProperties() : null;
    }

    public POIXMLProperties.ExtendedProperties getExtendedProperties() {
        POIXMLProperties props = getOrCreateProperties();
        return props != null ? props.getExtendedProperties() : null;
    }

    public POIXMLProperties.CustomProperties getCustomProperties() {
        POIXMLProperties props = getOrCreateProperties();
        return props != null ? props.getCustomProperties() : null;
    }

    @Override
    public POIXMLDocument getDocument() {
        return null;
    }


    @Override
    public String getText() {
        StringBuilder sb = new StringBuilder();
        //handle main document
        List<PackagePart> pps =
                container.getPartsByContentType(XWPFRelation.DOCUMENT.getContentType());
        if (pps != null) {
            for (PackagePart pp : pps) {
                //likely only one, but why not...
                try {
                    handleDocumentPart(pp, sb);
                } catch (IOException e) {
                    LOG.warn("IOException handling document part", e);
                } catch (SAXException e) {
                    if (WriteLimitReachedException.isWriteLimitReached(e)) {
                        throw new RuntimeSAXException(e);
                    }
                    //swallow this because we don't actually call it
                    LOG.warn("SAXException handling document part", e);
                } catch (TikaException e) {
                    LOG.warn("ParseException handling document part", e);
                }
            }
        }
        //handle glossary document
        if (includeGlossary) {
            pps = container.getPartsByContentType(
                    XWPFRelation.GLOSSARY_DOCUMENT.getContentType());

            if (pps != null) {
                for (PackagePart pp : pps) {
                    //likely only one, but why not...
                    try {
                        handleDocumentPart(pp, sb);
                    } catch (IOException e) {
                        LOG.warn("IOException handling glossary document part", e);
                    } catch (SAXException e) {
                        if (WriteLimitReachedException.isWriteLimitReached(e)) {
                            throw new RuntimeSAXException(e);
                        }
                        //swallow this because we don't actually call it
                        LOG.warn("SAXException handling glossary document part", e);
                    } catch (TikaException e) {
                        LOG.warn("ParseException handling document part", e);
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    public void setCloseFilesystem(boolean b) {

    }

    @Override
    public boolean isCloseFilesystem() {
        return false;
    }

    @Override
    public Closeable getFilesystem() {
        return null;
    }


    private void handleDocumentPart(PackagePart documentPart, StringBuilder sb)
            throws IOException, SAXException, TikaException {
        //load the numbering/list manager and styles from the main document part
        XWPFNumberingShim numbering = loadNumbering(documentPart);
        XWPFListManager xwpfListManager = new XWPFListManager(
                numbering != null ? numbering : XWPFNumberingShim.EMPTY);
        //TODO: XWPFStyles styles = loadStyles(documentPart);

        //headers
        try {
            PackageRelationshipCollection headersPRC =
                    documentPart.getRelationshipsByType(XWPFRelation.HEADER.getRelation());
            if (headersPRC != null) {
                for (int i = 0; i < headersPRC.size(); i++) {
                    PackagePart header = documentPart.getRelatedPart(headersPRC.getRelationship(i));
                    handlePart(header, xwpfListManager, sb);
                }
            }
        } catch (InvalidFormatException e) {
            LOG.warn("Invalid format", e);
        }

        //main document
        handlePart(documentPart, xwpfListManager, sb);

        //for now, just dump other components at end
        for (XWPFRelation rel : new XWPFRelation[]{XWPFRelation.FOOTNOTE, XWPFRelation.COMMENT,
                XWPFRelation.FOOTER, XWPFRelation.ENDNOTE}) {
            try {
                PackageRelationshipCollection prc =
                        documentPart.getRelationshipsByType(rel.getRelation());
                if (prc != null) {
                    for (int i = 0; i < prc.size(); i++) {
                        PackagePart packagePart =
                                documentPart.getRelatedPart(prc.getRelationship(i));
                        handlePart(packagePart, xwpfListManager, sb);
                    }
                }
            } catch (InvalidFormatException e) {
                LOG.warn("Invalid format", e);
            }
        }
    }

    private void handlePart(PackagePart packagePart, XWPFListManager xwpfListManager,
                            StringBuilder buffer) throws IOException, SAXException, TikaException {

        Map<String, String> hyperlinks = loadHyperlinkRelationships(packagePart);
        try (InputStream stream = packagePart.getInputStream()) {
            XMLReaderUtils.parseSAX(stream,
                    new OOXMLWordAndPowerPointTextHandler(new XWPFToTextContentHandler(buffer),
                    hyperlinks), new ParseContext());
        }

    }

    private Map<String, String> loadHyperlinkRelationships(PackagePart bodyPart) {
        Map<String, String> hyperlinks = new HashMap<>();
        try {
            PackageRelationshipCollection prc =
                    bodyPart.getRelationshipsByType(XWPFRelation.HYPERLINK.getRelation());
            for (int i = 0; i < prc.size(); i++) {
                PackageRelationship pr = prc.getRelationship(i);
                if (pr == null) {
                    continue;
                }
                String id = pr.getId();
                String url = (pr.getTargetURI() == null) ? null : pr.getTargetURI().toString();
                if (id != null && url != null) {
                    hyperlinks.put(id, url);
                }
            }
        } catch (InvalidFormatException e) {
            LOG.warn("Invalid format", e);
        }
        return hyperlinks;
    }

    private XWPFNumberingShim loadNumbering(PackagePart packagePart) {
        try {
            PackageRelationshipCollection numberingParts =
                    packagePart.getRelationshipsByType(XWPFRelation.NUMBERING.getRelation());
            if (numberingParts.size() > 0) {
                PackageRelationship numberingRelationShip = numberingParts.getRelationship(0);
                if (numberingRelationShip == null) {
                    return null;
                }
                PackagePart numberingPart = container.getPart(numberingRelationShip);
                if (numberingPart == null) {
                    return null;
                }
                return new XWPFNumberingShim(numberingPart, new ParseContext());
            }
        } catch (Exception e) {
            LOG.warn("Couldn't load numbering", e);
        }
        return null;
    }

    private static class XWPFToTextContentHandler
            implements XWPFBodyContentsHandler {
        private final StringBuilder buffer;

        public XWPFToTextContentHandler(StringBuilder buffer) {
            this.buffer = buffer;
        }

        @Override
        public void run(RunProperties runProperties, String contents) {
            buffer.append(contents);
        }

        @Override
        public void hyperlinkStart(String link) {
            //no-op
        }

        @Override
        public void hyperlinkEnd() {
            //no-op
        }

        @Override
        public void startParagraph(ParagraphProperties paragraphProperties) {
            //no-op
        }

        @Override
        public void endParagraph() {
            buffer.append("\n");
        }

        @Override
        public void startTable() {

        }

        @Override
        public void endTable() {

        }

        @Override
        public void startTableRow() {

        }

        @Override
        public void endTableRow() {
            buffer.append("\n");
        }

        @Override
        public void startTableCell() {

        }

        @Override
        public void endTableCell() {
            buffer.append("\t");
        }

        @Override
        public void startSDT() {

        }

        @Override
        public void endSDT() {
            buffer.append("\n");
        }

        @Override
        public void startEditedSection(String editor, Date date,
                                       EditType editType) {

        }

        @Override
        public void endEditedSection() {

        }

        @Override
        public boolean isIncludeDeletedText() {
            return true;
        }

        @Override
        public void footnoteReference(String id) {

        }

        @Override
        public void endnoteReference(String id) {

        }

        @Override
        public void commentReference(String id) {

        }

        @Override
        public boolean isIncludeMoveFromText() {
            return false;
        }

        @Override
        public void embeddedOLERef(String refId, String progId, String emfImageRId) {
            //no-op
        }

        @Override
        public void linkedOLERef(String refId) {
            //no-op
        }

        @Override
        public void embeddedPicRef(String picFileName, String picDescription) {
            //no-op
        }

        @Override
        public void startBookmark(String id, String name) {
            //no-op
        }

        @Override
        public void endBookmark(String id) {
            //no-op
        }
    }
}
