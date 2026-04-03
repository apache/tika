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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * SAX/Streaming pptx extractior
 */
public class SXSLFPowerPointExtractorDecorator extends AbstractOOXMLExtractor {

    private final static String HANDOUT_MASTER =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/handoutMaster";

    //a pptx file should have one of these "main story" parts
    private final static String[] MAIN_STORY_PART_RELATIONS =
            new String[]{XSLFRelation.MAIN.getContentType(),
                    XSLFRelation.PRESENTATION_MACRO.getContentType(),
                    XSLFRelation.PRESENTATIONML.getContentType(),
                    XSLFRelation.PRESENTATIONML_TEMPLATE.getContentType(),
                    XSLFRelation.MACRO.getContentType(),
                    XSLFRelation.MACRO_TEMPLATE.getContentType(),
                    XSLFRelation.THEME_MANAGER.getContentType()


                    //TODO: what else
            };

    private final OPCPackage opcPackage;
    private final ParseContext context;
    private final Metadata metadata;
    private final CommentAuthors commentAuthors = new CommentAuthors();
    private PackagePart mainDocument = null;

    public SXSLFPowerPointExtractorDecorator(Metadata metadata, ParseContext context,
                                             XSLFEventBasedPowerPointExtractor extractor) {
        super(context, extractor);
        this.metadata = metadata;
        this.context = context;
        this.opcPackage = extractor.getPackage();
        for (String contentType : MAIN_STORY_PART_RELATIONS) {
            List<PackagePart> pps = opcPackage.getPartsByContentType(contentType);
            if (pps.size() > 0) {
                mainDocument = pps.get(0);
                break;
            }
        }
        //if mainDocument == null, throw exception
    }

    /**
     * @see org.apache.poi.xslf.extractor.XSLFPowerPointExtractor#getText()
     */
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException, IOException {

        loadCommentAuthors();
        addCommentAuthorMetadata();

        PackageRelationshipCollection slidesPRC = null;
        try {
            slidesPRC = mainDocument.getRelationshipsByType(XSLFRelation.SLIDE.getRelation());
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        int hiddenSlideCount = 0;
        if (slidesPRC != null && slidesPRC.size() > 0) {
            for (int i = 0; i < slidesPRC.size(); i++) {
                try {
                    PackagePart slidePart =
                            safeGetRelatedPart(mainDocument, slidesPRC.getRelationship(i));
                    if (slidePart == null) {
                        continue;
                    }
                    hiddenSlideCount += handleSlidePart(slidePart, xhtml);
                } catch (InvalidFormatException | ZipException e) {
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            ExceptionUtils.getStackTrace(e));
                }
            }
        }
        if (hiddenSlideCount > 0) {
            metadata.set(Office.NUM_HIDDEN_SLIDES, hiddenSlideCount);
        }

        if (config.isIncludeSlideMasterContent()) {
            handleGeneralTextContainingPart(XSLFRelation.SLIDE_MASTER.getRelation(), "slide-master",
                    mainDocument, metadata, new PlaceHolderSkipper(
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml),
                                    new HashMap<>())));

            handleGeneralTextContainingPart(HANDOUT_MASTER, "slide-handout-master", mainDocument,
                    metadata,
                    new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                            new HashMap<>()));
        }
    }

    private void loadCommentAuthors() {
        PackageRelationshipCollection prc = null;
        try {
            prc = mainDocument.getRelationshipsByType(XSLFRelation.COMMENT_AUTHORS.getRelation());
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }
        if (prc == null || prc.size() == 0) {
            return;
        }

        for (int i = 0; i < prc.size(); i++) {
            PackagePart commentAuthorsPart = null;
            try {
                commentAuthorsPart = safeGetRelatedPart(mainDocument, prc.getRelationship(i));
            } catch (InvalidFormatException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
            if (commentAuthorsPart == null) {
                continue;
            }
            try (InputStream stream = commentAuthorsPart.getInputStream()) {
                XMLReaderUtils.parseSAX(stream,
                        new XSLFCommentAuthorHandler(commentAuthors), context);

            } catch (TikaException | SAXException | IOException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }

    }

    private void addCommentAuthorMetadata() {
        for (String name : commentAuthors.nameMap.values()) {
            if (name != null && !name.isBlank()) {
                metadata.add(Office.COMMENT_PERSONS, name);
            }
        }
    }

    /**
     * @return 1 if the slide is hidden, 0 otherwise
     */
    private int handleSlidePart(PackagePart slidePart, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        Map<String, String> linkedRelationships =
                loadLinkedRelationships(slidePart, false, metadata);

        int hidden = 0;
        xhtml.startElement("div", "class", "slide-content");
        try (InputStream stream = slidePart.getInputStream()) {
            OOXMLWordAndPowerPointTextHandler wordAndPPTHandler = new OOXMLWordAndPowerPointTextHandler(
                    new OOXMLTikaBodyPartHandler(xhtml, metadata), linkedRelationships);
            XMLReaderUtils.parseSAX(stream,
                    new EmbeddedContentHandler(wordAndPPTHandler), context);
            if (wordAndPPTHandler.isHiddenSlide()) {
                metadata.set(Office.HAS_HIDDEN_SLIDES, true);
                hidden = 1;
            }
            if (wordAndPPTHandler.hasAnimations()) {
                metadata.set(Office.HAS_ANIMATIONS, true);
            }
        } catch (TikaException | IOException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        xhtml.endElement("div");

        if (config.isIncludeSlideMasterContent()) {
            handleGeneralTextContainingPart(XSLFRelation.SLIDE_LAYOUT.getRelation(),
                    "slide-master-content", slidePart, metadata, new PlaceHolderSkipper(
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships)));
        }
        if (config.isIncludeSlideNotes()) {
            handleGeneralTextContainingPart(XSLFRelation.NOTES.getRelation(), "slide-notes",
                    slidePart, metadata,
                    new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                            linkedRelationships));
            if (config.isIncludeSlideMasterContent()) {
                handleGeneralTextContainingPart(XSLFRelation.NOTES_MASTER.getRelation(),
                        "slide-notes-master", slidePart, metadata,
                        new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                                linkedRelationships));

            }
        }
        handleGeneralTextContainingPart(XSLFRelation.COMMENTS.getRelation(), null, slidePart,
                metadata, new XSLFCommentsHandler(xhtml, commentAuthors));

        handleGeneralTextContainingPart(AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                "diagram-data", slidePart, metadata,
                new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                        linkedRelationships));

        handleGeneralTextContainingPart(XSLFRelation.CHART.getRelation(), "chart", slidePart,
                metadata, new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                        linkedRelationships));
        return hidden;
    }

    /**
     * In PowerPoint files, slides have things embedded in them,
     * and slide drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() {
        List<PackagePart> parts = new ArrayList<>();
        //TODO: consider: getPackage().getPartsByName(Pattern.compile("/ppt/embeddings/.*?
        //TODO: consider: getPackage().getPartsByName(Pattern.compile("/ppt/media/.*?
        PackageRelationshipCollection slidePRC = null;
        try {
            slidePRC = mainDocument.getRelationshipsByType(XSLFRelation.SLIDE.getRelation());
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));

        }
        if (slidePRC != null) {
            for (int i = 0; i < slidePRC.size(); i++) {
                PackagePart slidePart = null;
                try {
                    slidePart = safeGetRelatedPart(mainDocument, slidePRC.getRelationship(i));
                } catch (InvalidFormatException e) {
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            ExceptionUtils.getStackTrace(e));
                }
                addSlideParts(slidePart, parts);
            }
        }

        parts.add(mainDocument);
        for (String rel : new String[]{XSLFRelation.SLIDE_MASTER.getRelation(), HANDOUT_MASTER}) {

            PackageRelationshipCollection prc = null;
            try {
                prc = mainDocument.getRelationshipsByType(rel);
            } catch (InvalidFormatException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
            if (prc != null) {
                for (int i = 0; i < prc.size(); i++) {
                    PackagePart pp = null;
                    try {
                        pp = safeGetRelatedPart(mainDocument, prc.getRelationship(i));
                    } catch (InvalidFormatException e) {
                        metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                                ExceptionUtils.getStackTrace(e));
                    }
                    if (pp != null) {
                        parts.add(pp);
                    }
                }
            }
        }

        return parts;
    }

    private void addSlideParts(PackagePart slidePart, List<PackagePart> parts) {

        for (String relation : new String[]{XSLFRelation.VML_DRAWING.getRelation(),
                XSLFRelation.SLIDE_LAYOUT.getRelation(), XSLFRelation.NOTES_MASTER.getRelation(),
                XSLFRelation.NOTES.getRelation(), XSLFRelation.CHART.getRelation(),
                XSLFRelation.DIAGRAM_DRAWING.getRelation()}) {
            PackageRelationshipCollection prc = null;
            try {
                prc = slidePart.getRelationshipsByType(relation);
            } catch (InvalidFormatException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
            if (prc != null) {
                for (PackageRelationship packageRelationship : prc) {
                    if (packageRelationship.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName = null;
                        try {
                            relName = PackagingURIHelper
                                    .createPartName(packageRelationship.getTargetURI());
                        } catch (InvalidFormatException e) {
                            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                                    ExceptionUtils.getStackTrace(e));
                        }
                        if (relName != null) {
                            parts.add(packageRelationship.getPackage().getPart(relName));
                        }
                    }
                }
            }
        }
        //and slide of course
        parts.add(slidePart);

    }

}
