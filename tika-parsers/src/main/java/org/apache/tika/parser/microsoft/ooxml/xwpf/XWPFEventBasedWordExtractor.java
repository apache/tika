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

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.POIXMLDocument;
import org.apache.poi.POIXMLProperties;
import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.util.SAXHelper;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.parser.microsoft.ooxml.OOXMLWordAndPowerPointTextHandler;
import org.apache.tika.parser.microsoft.ooxml.ParagraphProperties;
import org.apache.tika.parser.microsoft.ooxml.RunProperties;
import org.apache.tika.parser.microsoft.ooxml.XWPFListManager;
import org.apache.xmlbeans.XmlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

//TODO: move this into POI?
/**
 * Experimental class that is based on POI's XSSFEventBasedExcelExtractor
 *
 */
public class XWPFEventBasedWordExtractor extends POIXMLTextExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(XWPFEventBasedWordExtractor.class);

    private OPCPackage container;
    private POIXMLProperties properties;

    public XWPFEventBasedWordExtractor(String path) throws XmlException, OpenXML4JException, IOException {
        this(OPCPackage.open(path));
    }

    public XWPFEventBasedWordExtractor(OPCPackage container) throws XmlException, OpenXML4JException, IOException {
        super((POIXMLDocument) null);
        this.container = container;
        this.properties = new POIXMLProperties(container);
    }


    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Use:");
            System.err.println("  XWPFEventBasedWordExtractor <filename.xlsx>");
            System.exit(1);
        }

        XWPFEventBasedWordExtractor extractor = new XWPFEventBasedWordExtractor(args[0]);
        System.out.println(extractor.getText());
        extractor.close();
    }

    public OPCPackage getPackage() {
        return this.container;
    }

    public POIXMLProperties.CoreProperties getCoreProperties() {
        return this.properties.getCoreProperties();
    }

    public POIXMLProperties.ExtendedProperties getExtendedProperties() {
        return this.properties.getExtendedProperties();
    }

    public POIXMLProperties.CustomProperties getCustomProperties() {
        return this.properties.getCustomProperties();
    }


    @Override
    public String getText() {
        StringBuilder sb = new StringBuilder();
        //handle main document
        List<PackagePart> pps = container.getPartsByContentType(XWPFRelation.DOCUMENT.getContentType());
        if (pps != null) {
            for (PackagePart pp : pps) {
                //likely only one, but why not...
                try {
                    handleDocumentPart(pp, sb);
                } catch (IOException e) {
                    LOG.warn("IOException handling document part", e);
                } catch (SAXException e) {
                    //swallow this because we don't actually call it
                    LOG.warn("SAXException handling document part", e);
                }
            }
        }
        //handle glossary document
        pps = container.getPartsByContentType(XWPFRelation.GLOSSARY_DOCUMENT.getContentType());

        if (pps != null) {
            for (PackagePart pp : pps) {
                //likely only one, but why not...
                try {
                    handleDocumentPart(pp, sb);
                } catch (IOException e) {
                    LOG.warn("IOException handling glossary document part", e);
                } catch (SAXException e) {
                    //swallow this because we don't actually call it
                    LOG.warn("SAXException handling glossary document part", e);
                }
            }
        }

        return sb.toString();
    }


    private void handleDocumentPart(PackagePart documentPart, StringBuilder sb) throws IOException, SAXException {
        //load the numbering/list manager and styles from the main document part
        XWPFNumbering numbering = loadNumbering(documentPart);
        XWPFListManager xwpfListManager = new XWPFListManager(numbering);
        //TODO: XWPFStyles styles = loadStyles(documentPart);

        //headers
        try {
            PackageRelationshipCollection headersPRC = documentPart.getRelationshipsByType(XWPFRelation.HEADER.getRelation());
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
        for (XWPFRelation rel : new XWPFRelation[]{
                XWPFRelation.FOOTNOTE,
                XWPFRelation.COMMENT,
                XWPFRelation.FOOTER,
                XWPFRelation.ENDNOTE
        }) {
            try {
                PackageRelationshipCollection prc = documentPart.getRelationshipsByType(rel.getRelation());
                if (prc != null) {
                    for (int i = 0; i < prc.size(); i++) {
                        PackagePart packagePart = documentPart.getRelatedPart(prc.getRelationship(i));
                        handlePart(packagePart, xwpfListManager, sb);
                    }
                }
            } catch (InvalidFormatException e) {
                LOG.warn("Invalid format", e);
            }
        }
    }

    private void handlePart(PackagePart packagePart,
                            XWPFListManager xwpfListManager, StringBuilder buffer) throws IOException, SAXException {

        Map<String, String> hyperlinks = loadHyperlinkRelationships(packagePart);
        try (InputStream stream = packagePart.getInputStream()) {
            XMLReader reader = SAXHelper.newXMLReader();
            reader.setContentHandler(new OOXMLWordAndPowerPointTextHandler(
                    new XWPFToTextContentHandler(buffer), hyperlinks));
            reader.parse(new InputSource(new CloseShieldInputStream(stream)));

        } catch (ParserConfigurationException e) {
            LOG.warn("Can't configure XMLReader", e);
        }

    }

    private Map<String, String> loadHyperlinkRelationships(PackagePart bodyPart) {
        Map<String, String> hyperlinks = new HashMap<>();
        try {
            PackageRelationshipCollection prc = bodyPart.getRelationshipsByType(XWPFRelation.HYPERLINK.getRelation());
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

    private XWPFNumbering loadNumbering(PackagePart packagePart) {
        try {
            PackageRelationshipCollection numberingParts = packagePart.getRelationshipsByType(XWPFRelation.NUMBERING.getRelation());
            if (numberingParts.size() > 0) {
                PackageRelationship numberingRelationShip = numberingParts.getRelationship(0);
                if (numberingRelationShip == null) {
                    return null;
                }
                PackagePart numberingPart = container.getPart(numberingRelationShip);
                if (numberingPart == null) {
                    return null;
                }
                return new XWPFNumbering(numberingPart);
            }
        } catch (IOException | OpenXML4JException e) {
            LOG.warn("Couldn't load numbering", e);
        }
        return null;
    }

    private class XWPFToTextContentHandler implements OOXMLWordAndPowerPointTextHandler.XWPFBodyContentsHandler {
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
        public void startEditedSection(String editor, Date date, OOXMLWordAndPowerPointTextHandler.EditType editType) {

        }

        @Override
        public void endEditedSection() {

        }

        @Override
        public boolean getIncludeDeletedText() {
            return true;
        }

        @Override
        public void footnoteReference(String id) {

        }

        @Override
        public void endnoteReference(String id) {

        }

        @Override
        public boolean getIncludeMoveFromText() {
            return false;
        }

        @Override
        public void embeddedOLERef(String refId) {
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

