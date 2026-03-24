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
import java.util.LinkedHashMap;
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
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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

        List<PackagePart> orderedSlides = getOrderedSlideParts();

        int hiddenSlideCount = 0;
        for (PackagePart slidePart : orderedSlides) {
            try {
                hiddenSlideCount += handleSlidePart(slidePart, xhtml);
            } catch (ZipException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }
        if (hiddenSlideCount > 0) {
            metadata.set(Office.NUM_HIDDEN_SLIDES, hiddenSlideCount);
        }

        if (config.isIncludeSlideMasterContent()) {
            // Handout master is presentation-level, not per-slide
            handleTextPartWithCleanup(HANDOUT_MASTER, "slide-handout-master", mainDocument,
                    xhtml, new HashMap<>(), false);
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
                commentAuthorsPart = mainDocument.getRelatedPart(prc.getRelationship(i));
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
     * Returns the first related part for the given relationship type,
     * or null if none found.
     */
    private PackagePart getRelatedPartByType(PackagePart source, String relationType) {
        try {
            PackageRelationshipCollection prc = source.getRelationshipsByType(relationType);
            if (prc != null && prc.size() > 0) {
                return source.getRelatedPart(prc.getRelationship(0));
            }
        } catch (InvalidFormatException | IllegalArgumentException e) {
            // missing part
        }
        return null;
    }

    /**
     * Returns slide parts in presentation order by parsing the sldIdLst
     * from presentation.xml.  Any slides found in .rels but not in
     * the sldIdLst are appended at the end.
     */
    private List<PackagePart> getOrderedSlideParts() {
        // Step 1: parse presentation.xml to get ordered rIds from sldIdLst
        List<String> orderedRIds = new ArrayList<>();
        try (InputStream is = mainDocument.getInputStream()) {
            XMLReaderUtils.parseSAX(is, new DefaultHandler() {
                private boolean inSldIdLst = false;

                @Override
                public void startElement(String uri, String localName, String qName,
                                         Attributes atts) {
                    if ("sldIdLst".equals(localName)) {
                        inSldIdLst = true;
                    } else if (inSldIdLst && "sldId".equals(localName)) {
                        String rId = atts.getValue(
                                "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                                "id");
                        if (rId != null) {
                            orderedRIds.add(rId);
                        }
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if ("sldIdLst".equals(localName)) {
                        inSldIdLst = false;
                    }
                }
            }, context);
        } catch (Exception e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        // Step 2: build rId -> PackagePart map from relationships
        Map<String, PackagePart> rIdToSlide = new LinkedHashMap<>();
        try {
            PackageRelationshipCollection slidesPRC =
                    mainDocument.getRelationshipsByType(XSLFRelation.SLIDE.getRelation());
            if (slidesPRC != null) {
                for (int i = 0; i < slidesPRC.size(); i++) {
                    PackageRelationship rel = slidesPRC.getRelationship(i);
                    try {
                        PackagePart part = mainDocument.getRelatedPart(rel);
                        if (part != null) {
                            rIdToSlide.put(rel.getId(), part);
                        }
                    } catch (InvalidFormatException | IllegalArgumentException e) {
                        // skip missing parts
                    }
                }
            }
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        // Step 3: assemble in presentation order, then append orphans
        List<PackagePart> result = new ArrayList<>();
        for (String rId : orderedRIds) {
            PackagePart part = rIdToSlide.remove(rId);
            if (part != null) {
                result.add(part);
            }
        }
        // append any slides in .rels but not in sldIdLst
        if (!rIdToSlide.isEmpty()) {
            metadata.set(Office.NUM_UNLISTED_SLIDES, rIdToSlide.size());
            for (PackagePart part : rIdToSlide.values()) {
                metadata.add(Office.UNLISTED_SLIDE_NAMES, part.getPartName().getName());
            }
            result.addAll(rIdToSlide.values());
        }
        return result;
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
        OOXMLTikaBodyPartHandler bodyHandler = new OOXMLTikaBodyPartHandler(xhtml, metadata);
        try (InputStream stream = slidePart.getInputStream()) {
            OOXMLWordAndPowerPointTextHandler wordAndPPTHandler = new OOXMLWordAndPowerPointTextHandler(
                    bodyHandler, linkedRelationships);
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
        bodyHandler.closeInlineElements();
        xhtml.endElement("div");

        if (config.isIncludeSlideMasterContent()) {
            // Extract the slide layout (per-slide)
            PackagePart layoutPart = getRelatedPartByType(slidePart,
                    XSLFRelation.SLIDE_LAYOUT.getRelation());
            if (layoutPart != null) {
                handleTextPartWithCleanup(XSLFRelation.SLIDE_LAYOUT.getRelation(),
                        "slide-master-content", slidePart, xhtml, linkedRelationships, true);
                // Follow layout → slide master chain
                handleTextPartWithCleanup(XSLFRelation.SLIDE_MASTER.getRelation(),
                        "slide-master-content", layoutPart, xhtml, linkedRelationships, true);
            }
        }
        if (config.isIncludeSlideNotes()) {
            handleTextPartWithCleanup(XSLFRelation.NOTES.getRelation(), "slide-notes",
                    slidePart, xhtml, linkedRelationships, false);
            if (config.isIncludeSlideMasterContent()) {
                handleTextPartWithCleanup(XSLFRelation.NOTES_MASTER.getRelation(),
                        "slide-notes-master", slidePart, xhtml, linkedRelationships, false);
            }
        }
        handleGeneralTextContainingPart(XSLFRelation.COMMENTS.getRelation(), null, slidePart,
                metadata, new XSLFCommentsHandler(xhtml, commentAuthors));

        handleTextPartWithCleanup(AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                "diagram-data", slidePart, xhtml, linkedRelationships, false);

        handleTextPartWithCleanup(XSLFRelation.CHART.getRelation(), "chart", slidePart,
                xhtml, linkedRelationships, false);
        return hidden;
    }

    /**
     * Handles a text-containing part with guaranteed inline element cleanup.
     * Creates an OOXMLTikaBodyPartHandler, parses the part, then calls
     * closeInlineElements() to ensure no unclosed tags leak into subsequent output.
     *
     * @param usePlaceholderSkipper if true, wraps the handler in a PlaceHolderSkipper
     */
    private void handleTextPartWithCleanup(String contentType, String xhtmlClassLabel,
                                           PackagePart parentPart, XHTMLContentHandler xhtml,
                                           Map<String, String> linkedRelationships,
                                           boolean usePlaceholderSkipper) throws SAXException {
        OOXMLTikaBodyPartHandler bodyHandler = new OOXMLTikaBodyPartHandler(xhtml);
        OOXMLWordAndPowerPointTextHandler textHandler =
                new OOXMLWordAndPowerPointTextHandler(bodyHandler, linkedRelationships);
        DefaultHandler handler = usePlaceholderSkipper
                ? new PlaceHolderSkipper(textHandler) : textHandler;
        try {
            handleGeneralTextContainingPart(contentType, xhtmlClassLabel, parentPart,
                    metadata, handler);
        } finally {
            bodyHandler.closeInlineElements();
        }
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
                    slidePart = mainDocument.getRelatedPart(slidePRC.getRelationship(i));
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
                        pp = mainDocument.getRelatedPart(prc.getRelationship(i));
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
