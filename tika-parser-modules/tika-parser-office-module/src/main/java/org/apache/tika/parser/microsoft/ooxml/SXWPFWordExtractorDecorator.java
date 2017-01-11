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
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFNumberingShim;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFStylesShim;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.SAXException;

/**
 * This is an experimental, alternative extractor for docx files.
 * This streams the main document content rather than loading the
 * full document into memory.
 * <p>
 * This will be better for some use cases than the classic docx extractor; and,
 * it will be worse for others.
 * </p>
 *
 * @since 1.15
 */
public class SXWPFWordExtractorDecorator extends AbstractOOXMLExtractor {


    //include all parts that might have embedded objects
    private final static String[] MAIN_PART_RELATIONS = new String[]{
            XWPFRelation.HEADER.getRelation(),
            XWPFRelation.FOOTER.getRelation(),
            XWPFRelation.FOOTNOTE.getRelation(),
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments"
    };

    //a docx file should have one of these "main story" parts
    private final static String[] MAIN_STORY_PART_RELATIONS = new String[]{
            XWPFRelation.DOCUMENT.getContentType(),
            XWPFRelation.MACRO_DOCUMENT.getContentType(),
            XWPFRelation.TEMPLATE.getContentType(),
            XWPFRelation.MACRO_TEMPLATE_DOCUMENT.getContentType()

    };

    private final OPCPackage opcPackage;
    private final ParseContext context;


    public SXWPFWordExtractorDecorator(ParseContext context,
                                       XWPFEventBasedWordExtractor extractor) {
        super(context, extractor);
        this.context = context;
        this.opcPackage = extractor.getPackage();
    }


    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        //handle main document
        List<PackagePart> pps = getStoryDocumentParts();
        if (pps != null) {
            for (PackagePart pp : pps) {
                //likely only one, but why not...
                handleDocumentPart(pp, xhtml);
            }
        }
        //handle glossary document
        pps = opcPackage.getPartsByContentType(XWPFRelation.GLOSSARY_DOCUMENT.getContentType());
        if (pps != null) {
            if (pps.size() > 0) {
                xhtml.startElement("div", "class", "glossary");

                for (PackagePart pp : pps) {
                    //likely only one, but why not...
                    handleDocumentPart(pp, xhtml);
                }
                xhtml.endElement("div");
            }
        }
    }

    private void handleDocumentPart(PackagePart documentPart, XHTMLContentHandler xhtml) throws IOException, SAXException {
        //load the numbering/list manager and styles from the main document part
        XWPFNumbering numbering = loadNumbering(documentPart);
        XWPFListManager listManager = new XWPFListManager(numbering);
        XWPFStylesShim styles = loadStyles(documentPart);

        //headers
        try {
            PackageRelationshipCollection headersPRC = documentPart.getRelationshipsByType(XWPFRelation.HEADER.getRelation());
            if (headersPRC != null) {
                for (int i = 0; i < headersPRC.size(); i++) {
                    PackagePart header = documentPart.getRelatedPart(headersPRC.getRelationship(i));
                    handlePart(header, styles, listManager, xhtml);
                }
            }
        } catch (InvalidFormatException e) {
            //swallow
        }

        //main document
        handlePart(documentPart, styles, listManager, xhtml);

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
                        handlePart(packagePart, styles, listManager, xhtml);
                    }
                }
            } catch (InvalidFormatException e) {
                //swallow
            }
        }
    }

    private void handlePart(PackagePart packagePart, XWPFStylesShim styles,
                            XWPFListManager listManager, XHTMLContentHandler xhtml) throws IOException, SAXException {

        Map<String, String> linkedRelationships = loadLinkedRelationships(packagePart, true);
        try (InputStream stream = packagePart.getInputStream()) {
            context.getSAXParser().parse(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml, styles, listManager,
                                            context.get(OfficeParserConfig.class)), linkedRelationships))));
        } catch (TikaException e) {
            //swallow
        }

    }



    private XWPFStylesShim loadStyles(PackagePart packagePart) {
        try {
            PackageRelationshipCollection stylesParts =
                    packagePart.getRelationshipsByType(XWPFRelation.STYLES.getRelation());
            if (stylesParts.size() > 0) {
                PackageRelationship stylesRelationShip = stylesParts.getRelationship(0);
                if (stylesRelationShip == null) {
                    return null;
                }
                PackagePart stylesPart = packagePart.getRelatedPart(stylesRelationShip);
                if (stylesPart == null) {
                    return null;
                }

                return new XWPFStylesShim(stylesPart, context);
            }
        } catch (OpenXML4JException e) {
            //swallow
        }
        return null;

    }

    private XWPFNumbering loadNumbering(PackagePart packagePart) {
        try {
            PackageRelationshipCollection numberingParts = packagePart.getRelationshipsByType(XWPFRelation.NUMBERING.getRelation());
            if (numberingParts.size() > 0) {
                PackageRelationship numberingRelationShip = numberingParts.getRelationship(0);
                if (numberingRelationShip == null) {
                    return null;
                }
                PackagePart numberingPart = packagePart.getRelatedPart(numberingRelationShip);
                if (numberingPart == null) {
                    return null;
                }
                return new XWPFNumberingShim(numberingPart);
            }
        } catch (IOException | OpenXML4JException e) {
            //swallow
        }
        return null;
    }

    /**
     * This returns all items that might contain embedded objects:
     * main document, headers, footers, comments, etc.
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() {

        List<PackagePart> mainStoryDocs = getStoryDocumentParts();
        List<PackagePart> relatedParts = new ArrayList<>();

        mainStoryDocs.addAll(
                opcPackage.getPartsByContentType(
                        XWPFRelation.GLOSSARY_DOCUMENT.getContentType()));


        for (PackagePart pp : mainStoryDocs) {
            addRelatedParts(pp, relatedParts);
        }
        relatedParts.addAll(mainStoryDocs);
        return relatedParts;
    }

    private void addRelatedParts(PackagePart documentPart, List<PackagePart> relatedParts) {
        for (String relation : MAIN_PART_RELATIONS) {
            PackageRelationshipCollection prc = null;
            try {
                prc = documentPart.getRelationshipsByType(relation);
                if (prc != null) {
                    for (int i = 0; i < prc.size(); i++) {
                        PackagePart packagePart = documentPart.getRelatedPart(prc.getRelationship(i));
                        relatedParts.add(packagePart);
                    }
                }
            } catch (InvalidFormatException e) {
            }
        }

    }

    /**
     *
     * @return the first non-empty main story document part; empty list if no
     * main story is found.
     */
    private List<PackagePart> getStoryDocumentParts() {

        for (String contentType : MAIN_STORY_PART_RELATIONS) {
            List<PackagePart> pps = opcPackage.getPartsByContentType(contentType);
            if (pps.size() > 0) {
                return pps;
            }
        }
        return new ArrayList<>();
    }
}
