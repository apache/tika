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


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.ooxml.xwpf.XWPFStylesShim;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class OOXMLTikaBodyPartHandler
        implements XWPFBodyContentsHandler {

    private static final String P = "p";

    private static final char[] NEWLINE = new char[]{'\n'};

    private final XHTMLContentHandler xhtml;
    private final XWPFListManager listManager;
    private final boolean includeDeletedText;
    private final boolean includeMoveFromText;
    private final XWPFStylesShim styles;
    private final Metadata metadata;

    private int pDepth = 0; //paragraph depth
    private int tableDepth = 0;//table depth
    private int sdtDepth = 0;//
    private FormattingTagManager formattingTags;

    //TODO: fix this
    //pWithinCell should be an array/stack of given cell depths
    //so that when you get to the end of an embedded table, e.g.,
    //you know what your paragraph count was in the parent cell.
    //<tc><p/><p/><table><tr><tc></p></p></tc></tr></table>...
    private int tableCellDepth = 0;
    private int pWithinCell = 0;

    //will need to replace this with a stack
    //if we're marking more that the first level <p/> element
    private String paragraphTag = null;

    private OOXMLInlineBodyPartMap inlinePartMap = OOXMLInlineBodyPartMap.EMPTY;
    private ParseContext parseContext = null;
    private final java.util.List<String> pendingCommentIds = new java.util.ArrayList<>();
    private final java.util.Set<String> emittedCommentIds = new java.util.HashSet<>();
    private final Map<String, EmbeddedPartMetadata> embeddedPartMetadataMap = new HashMap<>();

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml) {
        this(xhtml, null);
    }

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
        this.formattingTags = new FormattingTagManager(xhtml);
        this.styles = XWPFStylesShim.EMPTY_STYLES;
        this.listManager = XWPFListManager.EMPTY_LIST;
        this.includeDeletedText = false;
        this.includeMoveFromText = false;
    }

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml, XWPFStylesShim styles,
                                    XWPFListManager listManager,
                                    OfficeParserConfig parserConfig) {
        this(xhtml, styles, listManager, parserConfig, null);
    }

    public OOXMLTikaBodyPartHandler(XHTMLContentHandler xhtml, XWPFStylesShim styles,
                                    XWPFListManager listManager,
                                    OfficeParserConfig parserConfig, Metadata metadata) {
        this.xhtml = xhtml;
        this.metadata = metadata;
        this.formattingTags = new FormattingTagManager(xhtml);
        this.styles = styles;
        this.listManager = listManager;
        this.includeDeletedText = parserConfig.isIncludeDeletedContent();
        this.includeMoveFromText = parserConfig.isIncludeMoveFromContent();
    }

    /**
     * Sets pre-parsed inline body part content (footnotes, endnotes, comments)
     * so that references encountered during main document parsing can be
     * resolved inline.
     */
    public void setInlineBodyPartMap(OOXMLInlineBodyPartMap inlinePartMap,
            ParseContext parseContext) {
        this.inlinePartMap = inlinePartMap != null ? inlinePartMap : OOXMLInlineBodyPartMap.EMPTY;
        this.parseContext = parseContext;
    }

    @Override
    public void run(RunProperties runProperties, String contents) throws SAXException {
        formattingTags.applyFormatting(runProperties);
        xhtml.characters(contents);
    }

    @Override
    public void hyperlinkStart(String link) throws SAXException {
        formattingTags.openHyperlink(link);
    }

    @Override
    public void hyperlinkEnd() throws SAXException {
        formattingTags.closeHyperlink();
    }

    @Override
    public void startParagraph(ParagraphProperties paragraphProperties) throws SAXException {

        //if you're in a table cell and your after the first paragraph
        //make sure to prepend a \n
        if (tableCellDepth > 0 && pWithinCell > 0) {
            xhtml.characters(NEWLINE, 0, 1);
        }

        if (pDepth == 0 && tableDepth == 0 && sdtDepth == 0) {
            paragraphTag = P;
            String styleClass = null;
            //TIKA-2144 check that styles is not null
            if (paragraphProperties.getStyleID() != null && styles != null) {
                String styleName = styles.getStyleName(paragraphProperties.getStyleID());
                if (styleName != null) {
                    WordExtractor.TagAndStyle tas =
                            WordExtractor.buildParagraphTagAndStyle(styleName, false);
                    paragraphTag = tas.getTag();
                    styleClass = tas.getStyleClass();
                }
            }


            if (styleClass == null) {
                xhtml.startElement(paragraphTag);
            } else {
                xhtml.startElement(paragraphTag, "class", styleClass);
            }
        }

        writeParagraphNumber(paragraphProperties.getNumId(), paragraphProperties.getIlvl(),
                listManager, xhtml);
        pDepth++;
    }


    @Override
    public void endParagraph() throws SAXException {
        formattingTags.closeAll();
        if (pDepth == 1 && tableDepth == 0) {
            xhtml.endElement(paragraphTag);
        } else if (tableCellDepth > 0 && pWithinCell > 0) {
            xhtml.characters(NEWLINE, 0, 1);
        } else if (tableCellDepth == 0) {
            xhtml.characters(NEWLINE, 0, 1);
        }

        // Emit any pending comment content after the paragraph closes
        // (matching the DOM parser's behavior of appending comments after paragraphs)
        emitPendingComments();

        if (tableCellDepth > 0) {
            pWithinCell++;
        }
        pDepth--;
    }

    private void emitPendingComments() throws SAXException {
        if (pendingCommentIds.isEmpty()) {
            return;
        }
        for (String id : pendingCommentIds) {
            byte[] xml = inlinePartMap.getComment(id);
            if (xml != null) {
                inlineNoteContent(xml, "comment");
                emittedCommentIds.add(id);
            }
        }
        pendingCommentIds.clear();
    }

    /**
     * Returns the set of comment IDs that were inlined during parsing.
     * Used by the decorator to skip these when dumping remaining comments.
     */
    public java.util.Set<String> getEmittedCommentIds() {
        return emittedCommentIds;
    }

    @Override
    public void startTable() throws SAXException {

        xhtml.startElement("table");
        tableDepth++;

    }

    @Override
    public void endTable() throws SAXException {

        xhtml.endElement("table");
        tableDepth--;

    }

    @Override
    public void startTableRow() throws SAXException {
        xhtml.startElement("tr");
    }

    @Override
    public void endTableRow() throws SAXException {
        xhtml.endElement("tr");
    }

    @Override
    public void startTableCell() throws SAXException {
        xhtml.startElement("td");
        tableCellDepth++;
    }

    @Override
    public void endTableCell() throws SAXException {
        xhtml.endElement("td");
        pWithinCell = 0;
        tableCellDepth--;
    }

    @Override
    public void startSDT() throws SAXException {
        formattingTags.closeAll();
        sdtDepth++;
    }

    @Override
    public void endSDT() {
        sdtDepth--;
    }

    @Override
    public void startEditedSection(String editor, Date date,
                                   EditType editType) {
        //no-op
    }

    @Override
    public void endEditedSection() {
        //no-op
    }

    @Override
    public boolean isIncludeDeletedText() {
        return includeDeletedText;
    }

    @Override
    public void footnoteReference(String id) throws SAXException {
        if (id == null) {
            return;
        }
        byte[] xml = inlinePartMap.getFootnote(id);
        if (xml != null) {
            inlineNoteContent(xml, "footnote");
        } else {
            xhtml.characters("[");
            xhtml.characters(id);
            xhtml.characters("]");
        }
    }

    @Override
    public void endnoteReference(String id) throws SAXException {
        if (id == null) {
            return;
        }
        byte[] xml = inlinePartMap.getEndnote(id);
        if (xml != null) {
            inlineNoteContent(xml, "endnote");
        } else {
            xhtml.characters("[");
            xhtml.characters(id);
            xhtml.characters("]");
        }
    }

    @Override
    public void commentReference(String id) throws SAXException {
        if (id != null) {
            pendingCommentIds.add(id);
        }
    }

    private void inlineNoteContent(byte[] xml, String cssClass) throws SAXException {
        // Use the inline part map's relationship map which includes relationships
        // from the footnote/endnote parts (needed for picture resolution)
        Map<String, String> noteRelationships = inlinePartMap.getLinkedRelationships();
        xhtml.startElement("div", "class", cssClass);
        try {
            XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml),
                    new EmbeddedContentHandler(
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml),
                                    noteRelationships)),
                    parseContext);
        } catch (TikaException | IOException e) {
            xhtml.characters("[" + cssClass + " parse error]");
        }
        xhtml.endElement("div");
    }

    @Override
    public boolean isIncludeMoveFromText() {
        return includeMoveFromText;
    }

    @Override
    public void embeddedOLERef(String relId, String progId, String emfImageRId)
            throws SAXException {
        if (relId == null) {
            return;
        }
        if ((progId != null && !progId.isEmpty()) ||
                (emfImageRId != null && !emfImageRId.isEmpty())) {
            EmbeddedPartMetadata epm = new EmbeddedPartMetadata(emfImageRId);
            if (progId != null && !progId.isEmpty()) {
                epm.setProgId(progId);
            }
            embeddedPartMetadataMap.put(relId, epm);
        }
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        attributes.addAttribute("", "id", "id", "CDATA", relId);
        xhtml.startElement("div", attributes);
        xhtml.endElement("div");
    }

    public Map<String, EmbeddedPartMetadata> getEmbeddedPartMetadataMap() {
        return embeddedPartMetadataMap;
    }

    @Override
    public void linkedOLERef(String relId) throws SAXException {
        if (relId == null) {
            return;
        }
        if (metadata != null) {
            metadata.set(Office.HAS_LINKED_OLE_OBJECTS, true);
        }
        // Emit as an external reference anchor - linked OLE objects reference external files
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "external-ref-linkedOle");
        attributes.addAttribute("", "id", "id", "CDATA", relId);
        xhtml.startElement("a", attributes);
        xhtml.endElement("a");
    }

    @Override
    public void embeddedPicRef(String picFileName, String picDescription) throws SAXException {

        AttributesImpl attr = new AttributesImpl();
        if (picFileName != null) {
            attr.addAttribute("", "src", "src", "CDATA", "embedded:" + picFileName);
        }
        if (picDescription != null) {
            attr.addAttribute("", "alt", "alt", "CDATA", picDescription);
        }

        xhtml.startElement("img", attr);
        xhtml.endElement("img");


    }

    @Override
    public void fieldCodeHyperlinkStart(String link) throws SAXException {
        if (metadata != null) {
            metadata.set(Office.HAS_FIELD_HYPERLINKS, true);
        }
        hyperlinkStart(link);
    }

    @Override
    public void externalRef(String fieldType, String url) throws SAXException {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (metadata != null) {
            if ("hlinkHover".equals(fieldType)) {
                metadata.set(Office.HAS_HOVER_HYPERLINKS, true);
            } else if ("vml-shape-href".equals(fieldType)) {
                metadata.set(Office.HAS_VML_HYPERLINKS, true);
            } else {
                metadata.set(Office.HAS_FIELD_HYPERLINKS, true);
            }
        }
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute("", "class", "class", "CDATA", "external-ref-" + fieldType);
        attr.addAttribute("", "href", "href", "CDATA", url);
        xhtml.startElement("a", attr);
        xhtml.endElement("a");
    }

    @Override
    public void startBookmark(String id, String name) throws SAXException {
        //skip bookmarks within hyperlinks
        if (name != null && !formattingTags.isHyperlinkActive()) {
            xhtml.startElement("a", "name", name);
            xhtml.endElement("a");
        }
    }

    @Override
    public void endBookmark(String id) {
        //no-op
    }

    private void writeParagraphNumber(int numId, int ilvl, XWPFListManager listManager,
                                      XHTMLContentHandler xhtml) throws SAXException {

        if (ilvl < 0 || numId < 0 || listManager == null) {
            return;
        }
        String number = listManager.getFormattedNumber(BigInteger.valueOf(numId), ilvl);
        if (number != null) {
            xhtml.characters(number);
        }

    }
}
