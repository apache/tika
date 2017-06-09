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

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xslf.extractor.XSLFPowerPointExtractor;
import org.apache.poi.xslf.usermodel.XSLFRelation;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.ooxml.xslf.XSLFEventBasedPowerPointExtractor;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX/Streaming pptx extractior
 */
public class SXSLFPowerPointExtractorDecorator extends AbstractOOXMLExtractor {

    private final static String HANDOUT_MASTER = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/handoutMaster";

    //a pptx file should have one of these "main story" parts
    private final static String[] MAIN_STORY_PART_RELATIONS = new String[]{
            XSLFRelation.MAIN.getContentType(),
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
    private PackagePart mainDocument = null;
    private final CommentAuthors commentAuthors = new CommentAuthors();

    public SXSLFPowerPointExtractorDecorator(Metadata metadata, ParseContext context, XSLFEventBasedPowerPointExtractor extractor) {
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
     * @see XSLFPowerPointExtractor#getText()
     */
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException, IOException {

        loadCommentAuthors();


        PackageRelationshipCollection slidesPRC =
                null;
        try {
            slidesPRC = mainDocument.getRelationshipsByType(XSLFRelation.SLIDE.getRelation());
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        if (slidesPRC != null && slidesPRC.size() > 0) {
            for (int i = 0; i < slidesPRC.size(); i++) {
                try {
                    handleSlidePart(mainDocument.getRelatedPart(slidesPRC.getRelationship(i)), xhtml);
                } catch (InvalidFormatException|ZipException e) {
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            ExceptionUtils.getStackTrace(e));
                }
            }
        }

        handleGeneralTextContainingPart(XSLFRelation.SLIDE_MASTER.getRelation(),
                "slide-master",
                mainDocument,
                metadata,
                new PlaceHolderSkipper(new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), new HashMap<String, String>())));

        handleGeneralTextContainingPart(HANDOUT_MASTER,
                "slide-handout-master",
                mainDocument,
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), new HashMap<String, String>())
        );
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
                context.getSAXParser().parse(
                        new CloseShieldInputStream(stream),
                        new OfflineContentHandler(new XSLFCommentAuthorHandler()));

            } catch (TikaException | SAXException | IOException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }

    }

    private void handleSlidePart(PackagePart slidePart, XHTMLContentHandler xhtml) throws IOException, SAXException {
        Map<String, String> linkedRelationships = loadLinkedRelationships(slidePart, false, metadata);

//        Map<String, String> hyperlinks = loadHyperlinkRelationships(packagePart);
        xhtml.startElement("div", "class", "slide-content");
        try (InputStream stream = slidePart.getInputStream()) {
            context.getSAXParser().parse(
                    new CloseShieldInputStream(stream),
                    new OfflineContentHandler(new EmbeddedContentHandler(
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships))));

        } catch (TikaException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        xhtml.endElement("div");


        handleGeneralTextContainingPart(XSLFRelation.SLIDE_LAYOUT.getRelation(),
                "slide-master-content", slidePart,
                metadata,
                new PlaceHolderSkipper(new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships))
                );

        handleGeneralTextContainingPart(XSLFRelation.NOTES.getRelation(),
                "slide-notes", slidePart,
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships));

        handleGeneralTextContainingPart(XSLFRelation.NOTES_MASTER.getRelation(),
                "slide-notes-master", slidePart,
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships));

        handleGeneralTextContainingPart(XSLFRelation.COMMENTS.getRelation(),
                null, slidePart,
                metadata,
                new XSLFCommentsHandler(xhtml));

        handleGeneralTextContainingPart(AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                "diagram-data", slidePart,
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships));

        handleGeneralTextContainingPart(XSLFRelation.CHART.getRelation(),
                "chart", slidePart,
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml), linkedRelationships));
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
        for (String rel : new String[]{
                XSLFRelation.SLIDE_MASTER.getRelation(),
                HANDOUT_MASTER}) {

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

        for (String relation : new String[]{
                XSLFRelation.VML_DRAWING.getRelation(),
                XSLFRelation.SLIDE_LAYOUT.getRelation(),
                XSLFRelation.NOTES_MASTER.getRelation(),
                XSLFRelation.NOTES.getRelation()
        }) {
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
                            relName = PackagingURIHelper.createPartName(packageRelationship.getTargetURI());
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

    private class XSLFCommentsHandler extends DefaultHandler {

        private String commentAuthorId = null;
        private StringBuilder commentBuffer = new StringBuilder();
        private XHTMLContentHandler xhtml;
        XSLFCommentsHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if ("cm".equals(localName)) {
                commentAuthorId = atts.getValue("", "authorId");
                //get date (dt)?
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            //TODO: require that we're in <p:text>?
            commentBuffer.append(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if ("cm".equals(localName)) {

                xhtml.startElement("p", "class", "slide-comment");

                String authorString = commentAuthors.getName(commentAuthorId);
                String authorInitials = commentAuthors.getInitials(commentAuthorId);
                if (authorString != null || authorInitials != null) {
                    xhtml.startElement("b");
                    boolean authorExists = false;
                    if (authorString != null) {
                        xhtml.characters(authorString.toString());
                        authorExists = true;
                    }
                    if (authorExists && authorInitials != null) {
                        xhtml.characters(" (");
                    }
                    if (authorInitials != null) {
                        xhtml.characters(authorInitials);
                    }
                    if (authorExists && authorInitials != null) {
                        xhtml.characters(")");
                    }
                    xhtml.endElement("b");
                }
                xhtml.characters(commentBuffer.toString());
                xhtml.endElement("p");

                commentBuffer.setLength(0);
                commentAuthorId = null;
            }
        }
    }

    private class XSLFCommentAuthorHandler extends DefaultHandler {
        String id = null;
        String name = null;
        String initials = null;
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if ("cmAuthor".equals(localName)) {
                for (int i = 0; i < atts.getLength(); i++) {
                    if ("id".equals(atts.getLocalName(i))) {
                        id = atts.getValue(i);
                    } else if ("name".equals(atts.getLocalName(i))) {
                        name = atts.getValue(i);
                    } else if ("initials".equals(atts.getLocalName(i))) {
                        initials = atts.getValue(i);
                    }
                }
                commentAuthors.add(id, name, initials);
                //clear out
                id = null; name = null; initials = null;
            }
        }

    }


    private static class PlaceHolderSkipper extends DefaultHandler {

        private final ContentHandler wrappedHandler;

        PlaceHolderSkipper(ContentHandler wrappedHandler) {
            this.wrappedHandler = wrappedHandler;
        }

        boolean inPH = false;
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if ("ph".equals(localName)) {
                inPH = true;
            }
            if (! inPH) {
                wrappedHandler.startElement(uri, localName, qName, atts);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {

            if (! inPH) {
                wrappedHandler.endElement(uri, localName, qName);
            }
            if ("sp".equals(localName)) {
                inPH = false;
            }
        }
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (! inPH) {
                wrappedHandler.characters(ch, start, length);
            }
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            if (! inPH) {
                wrappedHandler.characters(ch, start, length);
            }
        }


    }

    private class CommentAuthors {
        Map<String, String> nameMap = new HashMap<>();
        Map<String, String> initialMap = new HashMap<>();

        void add(String id, String name, String initials) {
            if (id == null) {
                return;
            }
            if (name != null) {
                nameMap.put(id, name);
            }
            if (initials != null) {
                initialMap.put(id, initials);
            }
        }

        String getName(String id) {
            if (id == null) {
                return null;
            }
            return nameMap.get(id);
        }

        String getInitials(String id) {
            if (id == null) {
                return null;
            }
            return initialMap.get(id);
        }
    }
}
