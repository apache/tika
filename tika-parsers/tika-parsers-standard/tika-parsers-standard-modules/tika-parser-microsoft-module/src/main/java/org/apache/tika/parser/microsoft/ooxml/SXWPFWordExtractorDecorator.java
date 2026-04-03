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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.EMFParser;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFEventBasedWordExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFFeatureExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFNumberingShim;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFStylesShim;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.XMLReaderUtils;

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
    private final static String[] MAIN_PART_RELATIONS =
            new String[]{XWPFRelation.HEADER.getRelation(), XWPFRelation.FOOTER.getRelation(),
                    XWPFRelation.FOOTNOTE.getRelation(),
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes",
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments",
                    AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA};

    // Relationship types for Word settings
    private static final String SETTINGS_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings";
    private static final String WEB_SETTINGS_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/webSettings";
    private static final String ATTACHED_TEMPLATE_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/attachedTemplate";
    private static final String SUBDOCUMENT_RELATION =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/subDocument";

    //a docx file should have one of these "main story" parts
    private final static String[] MAIN_STORY_PART_RELATIONS =
            new String[]{XWPFRelation.DOCUMENT.getContentType(),
                    XWPFRelation.MACRO_DOCUMENT.getContentType(),
                    XWPFRelation.TEMPLATE.getContentType(),
                    XWPFRelation.MACRO_TEMPLATE_DOCUMENT.getContentType()

            };

    private final OPCPackage opcPackage;
    private final ParseContext context;
    private final Metadata metadata;
    private final Map<String, EmbeddedPartMetadata> embeddedPartMetadataMap = new HashMap<>();


    public SXWPFWordExtractorDecorator(Metadata metadata, ParseContext context,
                                       XWPFEventBasedWordExtractor extractor) {
        super(context, extractor);
        this.metadata = metadata;
        this.context = context;
        this.opcPackage = extractor.getPackage();
    }

    @Override
    public MetadataExtractor getMetadataExtractor() {
        return new SAXBasedMetadataExtractor(opcPackage, context);
    }

    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, IOException {
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

        // Detect security-relevant features in main document
        pps = getStoryDocumentParts();
        if (pps != null && !pps.isEmpty()) {
            PackagePart mainDoc = pps.get(0);
            detectSecurityFeatures(mainDoc, xhtml);
        }
    }

    /**
     * Detects security-relevant features like mail merge, attached templates,
     * subdocuments, and framesets.
     */
    private void detectSecurityFeatures(PackagePart documentPart, XHTMLContentHandler xhtml) {
        // Extract document features (hidden text, track changes, comments, comment persons)
        new XWPFFeatureExtractor().process(documentPart, metadata, context);

        // Check for attached template (external template reference)
        try {
            PackageRelationshipCollection templateRels =
                    documentPart.getRelationshipsByType(ATTACHED_TEMPLATE_RELATION);
            if (templateRels != null && templateRels.size() > 0) {
                metadata.set(Office.HAS_ATTACHED_TEMPLATE, true);
                for (PackageRelationship rel : templateRels) {
                    if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                        emitExternalRef(xhtml, "attachedTemplate", rel.getTargetURI().toString());
                    }
                }
            }
        } catch (InvalidFormatException | SAXException e) {
            // swallow
        }

        // Check for subdocuments (master document with external subdocs)
        try {
            PackageRelationshipCollection subDocRels =
                    documentPart.getRelationshipsByType(SUBDOCUMENT_RELATION);
            if (subDocRels != null && subDocRels.size() > 0) {
                metadata.set(Office.HAS_SUBDOCUMENTS, true);
                for (PackageRelationship rel : subDocRels) {
                    if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                        emitExternalRef(xhtml, "subDocument", rel.getTargetURI().toString());
                    }
                }
            }
        } catch (InvalidFormatException | SAXException e) {
            // swallow
        }

        // Check settings.xml for mail merge
        try {
            PackageRelationshipCollection settingsRels =
                    documentPart.getRelationshipsByType(SETTINGS_RELATION);
            if (settingsRels != null && settingsRels.size() > 0) {
                PackagePart settingsPart = safeGetRelatedPart(documentPart, settingsRels.getRelationship(0));
                if (settingsPart != null) {
                    try (InputStream is = settingsPart.getInputStream()) {
                        WordSettingsHandler handler = new WordSettingsHandler(xhtml);
                        XMLReaderUtils.parseSAX(is, handler, context);
                        if (handler.hasMailMerge()) {
                            metadata.set(Office.HAS_MAIL_MERGE, true);
                        }
                    }
                }
            }
        } catch (InvalidFormatException | IOException | TikaException | SAXException e) {
            // swallow
        }

        // Check webSettings.xml for framesets
        try {
            PackageRelationshipCollection webSettingsRels =
                    documentPart.getRelationshipsByType(WEB_SETTINGS_RELATION);
            if (webSettingsRels != null && webSettingsRels.size() > 0) {
                PackagePart webSettingsPart = safeGetRelatedPart(documentPart, webSettingsRels.getRelationship(0));
                if (webSettingsPart != null) {
                    try (InputStream is = webSettingsPart.getInputStream()) {
                        WebSettingsHandler handler = new WebSettingsHandler(xhtml);
                        XMLReaderUtils.parseSAX(is, handler, context);
                        if (handler.hasFrameset()) {
                            metadata.set(Office.HAS_FRAMESETS, true);
                        }
                    }
                }
            }
        } catch (InvalidFormatException | IOException | TikaException | SAXException |
                 IllegalArgumentException e) {
            // swallow -- POI throws IllegalArgumentException when
            // a relationship references a part missing from the package
        }
    }

    /**
     * Safely resolves a related part from a relationship.  Returns {@code null}
     * instead of throwing {@link IllegalArgumentException} when the target
     * part is missing from the package (e.g. truncated / salvaged zips).
     */
    private static PackagePart safeGetRelatedPart(PackagePart source, PackageRelationship rel) {
        try {
            return source.getRelatedPart(rel);
        } catch (InvalidFormatException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Emits an external reference as an anchor element.
     */
    private void emitExternalRef(XHTMLContentHandler xhtml, String refType, String url)
            throws SAXException {
        if (url == null || url.isEmpty()) {
            return;
        }
        org.xml.sax.helpers.AttributesImpl attrs = new org.xml.sax.helpers.AttributesImpl();
        attrs.addAttribute("", "class", "class", "CDATA", "external-ref-" + refType);
        attrs.addAttribute("", "href", "href", "CDATA", url);
        xhtml.startElement("a", attrs);
        xhtml.endElement("a");
    }

    private void handleDocumentPart(PackagePart documentPart, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        //load the numbering/list manager and styles from the main document part
        XWPFNumberingShim numbering = loadNumbering(documentPart);
        XWPFListManager listManager = new XWPFListManager(
                numbering != null ? numbering : XWPFNumberingShim.EMPTY);
        XWPFStylesShim styles = null;
        try {
            styles = loadStyles(documentPart);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }

        if (config.isIncludeHeadersAndFooters()) {
            //TODO: the DOM extractor handles per-section headers/footers by detecting
            // sectPr within paragraphs. We extract all headers/footers at the document level,
            // which is fine for text extraction since OOXML is flow-based, not page-based.
            //headers
            try {
                PackageRelationshipCollection headersPRC =
                        documentPart.getRelationshipsByType(XWPFRelation.HEADER.getRelation());
                if (headersPRC != null) {
                    for (int i = 0; i < headersPRC.size(); i++) {
                        PackagePart header =
                                safeGetRelatedPart(documentPart, headersPRC.getRelationship(i));
                        if (header != null) {
                            handlePart(header, styles, listManager, xhtml,
                                    OOXMLInlineBodyPartMap.EMPTY);
                        }
                    }
                }
            } catch (InvalidFormatException | ZipException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }

        // Pre-collect footnotes, endnotes, and comments so they can be
        // inlined at the point of reference in the main document
        OOXMLInlineBodyPartMap inlinePartMap = collectInlineParts(documentPart);

        //main document — keep reference to body handler for emitted comment tracking
        java.util.Set<String> emittedCommentIds = java.util.Collections.emptySet();
        try {
            OOXMLTikaBodyPartHandler mainBodyHandler =
                    handlePart(documentPart, styles, listManager, xhtml, inlinePartMap);
            emittedCommentIds = mainBodyHandler.getEmittedCommentIds();
        } catch (ZipException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }
        //dump remaining components at end (diagrams, charts, footers)
        for (String rel : new String[]{AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                XSSFRelation.CHART.getRelation(),
                XWPFRelation.FOOTER.getRelation()}) {
            //skip footers if we shouldn't extract them
            if (!config.isIncludeHeadersAndFooters() &&
                    rel.equals(XWPFRelation.FOOTER.getRelation())) {
                continue;
            }
            try {
                PackageRelationshipCollection prc = documentPart.getRelationshipsByType(rel);
                if (prc != null) {
                    for (int i = 0; i < prc.size(); i++) {
                        PackagePart packagePart =
                                safeGetRelatedPart(documentPart, prc.getRelationship(i));
                        if (packagePart != null) {
                            handlePart(packagePart, styles, listManager, xhtml,
                                    OOXMLInlineBodyPartMap.EMPTY);
                        }
                    }
                }
            } catch (InvalidFormatException | ZipException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }
        //dump any comments that were NOT inlined via commentReference
        handleUnreferencedComments(documentPart, styles, listManager, xhtml,
                inlinePartMap, emittedCommentIds);
    }

    private void handleUnreferencedComments(PackagePart documentPart,
            XWPFStylesShim styles, XWPFListManager listManager,
            XHTMLContentHandler xhtml, OOXMLInlineBodyPartMap inlinePartMap,
            java.util.Set<String> emittedCommentIds) {
        if (!inlinePartMap.hasComments()) {
            return;
        }
        Map<String, String> linkedRelationships = inlinePartMap.getLinkedRelationships();
        for (Map.Entry<String, byte[]> entry :
                inlinePartMap.getCommentEntries()) {
            if (emittedCommentIds.contains(entry.getKey())) {
                continue;
            }
            try {
                xhtml.startElement("div", "class", "comment");
                XMLReaderUtils.parseSAX(
                        new java.io.ByteArrayInputStream(entry.getValue()),
                        new EmbeddedContentHandler(
                                new OOXMLWordAndPowerPointTextHandler(
                                        new OOXMLTikaBodyPartHandler(xhtml),
                                        linkedRelationships)),
                        context);
                xhtml.endElement("div");
            } catch (TikaException | IOException | SAXException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private OOXMLTikaBodyPartHandler handlePart(PackagePart packagePart,
                            XWPFStylesShim styles,
                            XWPFListManager listManager, XHTMLContentHandler xhtml,
                            OOXMLInlineBodyPartMap inlinePartMap)
            throws IOException, SAXException {

        Map<String, String> linkedRelationships =
                loadLinkedRelationships(packagePart, true, metadata);
        OOXMLTikaBodyPartHandler bodyHandler =
                new OOXMLTikaBodyPartHandler(xhtml, styles, listManager, config, metadata);
        bodyHandler.setInlineBodyPartMap(inlinePartMap, context);
        try (InputStream stream = packagePart.getInputStream()) {
            XMLReaderUtils.parseSAX(stream,
                    new EmbeddedContentHandler(new OOXMLWordAndPowerPointTextHandler(
                            bodyHandler,
                            linkedRelationships, config.isIncludeShapeBasedContent(),
                            config.isConcatenatePhoneticRuns(),
                            config.isPreferAlternateContentChoice())), context);
        } catch (TikaException | IOException | SAXException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
        }
        bodyHandler.closeInlineElements();
        Map<String, EmbeddedPartMetadata> partMetadata = bodyHandler.getEmbeddedPartMetadataMap();
        resolveEmfNames(packagePart, partMetadata);
        embeddedPartMetadataMap.putAll(partMetadata);
        return bodyHandler;
    }

    private void resolveEmfNames(PackagePart documentPart,
                                 Map<String, EmbeddedPartMetadata> metadataMap) {
        for (EmbeddedPartMetadata epm : metadataMap.values()) {
            String emfRId = epm.getEmfRelationshipId();
            if (emfRId == null || emfRId.isEmpty()) {
                continue;
            }
            try {
                PackagePart emfPart = documentPart.getRelatedPart(
                        documentPart.getRelationship(emfRId));
                if (emfPart == null || emfPart.getContentType() == null) {
                    continue;
                }
                if ("image/x-emf".equals(emfPart.getContentType())) {
                    try (TikaInputStream tis = TikaInputStream.get(emfPart.getInputStream())) {
                        EMFParser p = new EMFParser();
                        Metadata m = Metadata.newInstance(context);
                        p.parse(tis, new org.apache.tika.sax.ToTextContentHandler(), m, context);
                        epm.setFullName(m.get(EMFParser.EMF_ICON_STRING));
                    }
                }
            } catch (Exception e) {
                //swallow
            }
        }
    }

    @Override
    protected Map<String, EmbeddedPartMetadata> getEmbeddedPartMetadataMap() {
        return embeddedPartMetadataMap;
    }

    private OOXMLInlineBodyPartMap collectInlineParts(PackagePart documentPart) {
        Map<String, String> allRelationships = new java.util.HashMap<>();
        Map<String, byte[]> footnoteMap = collectPartContent(documentPart,
                XWPFRelation.FOOTNOTE.getRelation(), Set.of("footnote"),
                allRelationships);
        String endnoteRel =
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes";
        Map<String, byte[]> endnoteMap = collectPartContent(documentPart,
                endnoteRel, Set.of("endnote"), allRelationships);
        String commentsRel =
                "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments";
        Map<String, byte[]> commentMap = collectPartContent(documentPart,
                commentsRel, Set.of("comment"),
                allRelationships, Collections.emptySet());
        return new OOXMLInlineBodyPartMap(footnoteMap, endnoteMap,
                commentMap, allRelationships);
    }

    private Map<String, byte[]> collectPartContent(PackagePart documentPart,
            String relationshipType, Set<String> wrapperElements,
            Map<String, String> allRelationships) {
        return collectPartContent(documentPart, relationshipType, wrapperElements,
                allRelationships, Set.of("0", "-1"));
    }

    private Map<String, byte[]> collectPartContent(PackagePart documentPart,
            String relationshipType, Set<String> wrapperElements,
            Map<String, String> allRelationships, Set<String> skipIds) {
        try {
            PackageRelationshipCollection prc =
                    documentPart.getRelationshipsByType(relationshipType);
            if (prc == null || prc.size() == 0) {
                return Collections.emptyMap();
            }
            OOXMLPartContentCollector collector =
                    new OOXMLPartContentCollector(wrapperElements, skipIds);
            for (int i = 0; i < prc.size(); i++) {
                try {
                    PackagePart part = documentPart.getRelatedPart(prc.getRelationship(i));
                    // collect the part's linked relationships (for picture resolution)
                    Map<String, String> partRels =
                            loadLinkedRelationships(part, true, metadata);
                    allRelationships.putAll(partRels);
                    try (InputStream stream = part.getInputStream()) {
                        XMLReaderUtils.parseSAX(stream, collector, context);
                    }
                } catch (InvalidFormatException | IOException | TikaException |
                         SAXException e) {
                    metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                            ExceptionUtils.getStackTrace(e));
                }
            }
            return collector.getContentMap();
        } catch (InvalidFormatException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
            return Collections.emptyMap();
        }
    }


    private XWPFStylesShim loadStyles(PackagePart packagePart)
            throws InvalidFormatException, TikaException, IOException, SAXException {
        PackageRelationshipCollection stylesParts =
                packagePart.getRelationshipsByType(XWPFRelation.STYLES.getRelation());
        if (stylesParts.size() > 0) {
            PackageRelationship stylesRelationShip = stylesParts.getRelationship(0);
            if (stylesRelationShip == null) {
                return null;
            }
            PackagePart stylesPart = safeGetRelatedPart(packagePart, stylesRelationShip);
            if (stylesPart == null) {
                return null;
            }

            return new XWPFStylesShim(stylesPart, context);
        }
        return null;

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
                PackagePart numberingPart = safeGetRelatedPart(packagePart, numberingRelationShip);
                if (numberingPart == null) {
                    return null;
                }
                return new XWPFNumberingShim(numberingPart, context);
            }
        } catch (Exception e) {
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

        mainStoryDocs.addAll(opcPackage
                .getPartsByContentType(XWPFRelation.GLOSSARY_DOCUMENT.getContentType()));


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
                        PackagePart packagePart =
                                safeGetRelatedPart(documentPart, prc.getRelationship(i));
                        if (packagePart != null) {
                            relatedParts.add(packagePart);
                        }
                    }
                }
            } catch (InvalidFormatException e) {
                //swallow
            }
        }

    }

    /**
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

    /**
     * Handler for parsing Word settings.xml to detect mail merge and other features.
     */
    private static class WordSettingsHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        private boolean hasMailMerge = false;

        WordSettingsHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            // Mail merge element indicates document has mail merge data source
            if ("mailMerge".equals(localName)) {
                hasMailMerge = true;
            }
            // dataSource element contains the external data source reference
            if ("dataSource".equals(localName) || "query".equals(localName)) {
                String rId = atts.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                // The actual data source location is in the relationship
            }
        }

        boolean hasMailMerge() {
            return hasMailMerge;
        }
    }

    /**
     * Handler for parsing Word webSettings.xml to detect framesets.
     */
    private static class WebSettingsHandler extends DefaultHandler {
        private final XHTMLContentHandler xhtml;
        private boolean hasFrameset = false;

        WebSettingsHandler(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            // Frameset element indicates document contains frames
            if ("frameset".equals(localName)) {
                hasFrameset = true;
            }
            // Frame with src attribute contains URL
            if ("frame".equals(localName)) {
                String src = atts.getValue("src");
                if (src != null && !src.isEmpty()) {
                    // Frame references an external URL
                    hasFrameset = true;
                }
            }
        }

        boolean hasFrameset() {
            return hasFrameset;
        }
    }
}
