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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.internal.FileHelper;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFDocumentXMLBodyHandler;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFTikaBodyPartHandler;
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

    private final static String[] EMBEDDED_RELATIONSHIPS = new String[]{
            RELATION_AUDIO,
            RELATION_IMAGE,
            RELATION_PACKAGE,
            RELATION_OFFICE_DOCUMENT
    };

    //include all parts that might have embedded objects
    private final static String[] MAIN_PART_RELATIONS = new String[]{
            XWPFRelation.HEADER.getRelation(),
            XWPFRelation.FOOTER.getRelation(),
            XWPFRelation.FOOTNOTE.getRelation(),
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments"
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
            for (PackagePart pp : pps) {
                //likely only one, but why not...
                handleDocumentPart(pp, xhtml);
            }
        }
    }

    private void handleDocumentPart(PackagePart documentPart, XHTMLContentHandler xhtml) throws IOException, SAXException {
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
                    handlePart(header, xwpfListManager, xhtml);
                }
            }
        } catch (InvalidFormatException e) {
            //swallow
        }

        //main document
        handlePart(documentPart, xwpfListManager, xhtml);

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
                        handlePart(packagePart, xwpfListManager, xhtml);
                    }
                }
            } catch (InvalidFormatException e) {
                //swallow
            }
        }
    }

    private void handlePart(PackagePart packagePart,
                            XWPFListManager xwpfListManager, XHTMLContentHandler xhtml) throws IOException, SAXException {

        Map<String, String> linkedRelationships = loadLinkedRelationships(packagePart);
        try (InputStream stream = packagePart.getInputStream()) {
            context.getSAXParser().parse(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            new XWPFDocumentXMLBodyHandler(
                                    new XWPFTikaBodyPartHandler(xhtml, xwpfListManager,
                                            context.get(OfficeParserConfig.class)), linkedRelationships))));
        } catch (TikaException e) {
            //swallow
        }

    }

    private Map<String, String> loadLinkedRelationships(PackagePart bodyPart) {
        Map<String, String> linkedRelationships = new HashMap<>();
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
                    linkedRelationships.put(id, url);
                }
            }

            for (String rel : EMBEDDED_RELATIONSHIPS) {

                prc = bodyPart.getRelationshipsByType(rel);
                for (int i = 0; i < prc.size(); i++) {
                    PackageRelationship pr = prc.getRelationship(i);
                    if (pr == null) {
                        continue;
                    }
                    String id = pr.getId();
                    String uriString = (pr.getTargetURI() == null) ? null : pr.getTargetURI().toString();
                    String fileName = uriString;
                    if (pr.getTargetURI() != null) {
                        try {
                            fileName = FileHelper.getFilename(new File(fileName));
                        } catch (Exception e) {
                            fileName = uriString;
                        }
                    }
                    if (id != null) {
                        fileName = (fileName == null) ? "" : fileName;
                        linkedRelationships.put(id, fileName);
                    }
                }
            }

        } catch (InvalidFormatException e) {
        }
        return linkedRelationships;
    }
/*
    private XWPFStyles loadStyles(PackagePart packagePart) {
        try {
            PackageRelationshipCollection stylesParts =
                    packagePart.getRelationshipsByType(XWPFRelation.STYLES.getRelation());
            if (stylesParts.size() > 0) {
                PackageRelationship stylesRelationShip = stylesParts.getRelationship(0);
                if (stylesRelationShip == null) {
                    return null;
                }
                PackagePart stylesPart = opcPackage.getPart(stylesRelationShip);
                if (stylesPart == null) {
                    return null;
                }
                return new XWPFStyles(stylesPart);
            }
        } catch (IOException|OpenXML4JException e) {
            //swallow
        }
        return null;

    }
*/
    private XWPFNumbering loadNumbering(PackagePart packagePart) {
        try {
            PackageRelationshipCollection numberingParts = packagePart.getRelationshipsByType(XWPFRelation.NUMBERING.getRelation());
            if (numberingParts.size() > 0) {
                PackageRelationship numberingRelationShip = numberingParts.getRelationship(0);
                if (numberingRelationShip == null) {
                    return null;
                }
                PackagePart numberingPart = opcPackage.getPart(numberingRelationShip);
                if (numberingPart == null) {
                    return null;
                }
                return new XWPFNumbering(numberingPart);
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

    private List<PackagePart> getStoryDocumentParts() {
        List<PackagePart> pps = opcPackage.getPartsByContentType(XWPFRelation.DOCUMENT.getContentType());
        if (pps.size() == 0) {
            pps = opcPackage.getPartsByContentType(XWPFRelation.MACRO_DOCUMENT.getContentType());
            if (pps.size() == 0) {
                pps = opcPackage.getPartsByContentType(XWPFRelation.MACRO_TEMPLATE_DOCUMENT.getContentType());
            }
        }
        return pps;
    }
}
