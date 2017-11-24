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

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.model.XWPFCommentsDecorator;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.BodyType;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ICell;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.ISDTContent;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFSDTCell;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.WordExtractor.TagAndStyle;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class XWPFWordExtractorDecorator extends AbstractOOXMLExtractor {

    // could be improved by using the real delimiter in xchFollow [MS-DOC], v20140721, 2.4.6.3, Part 3, Step 3
    private static final String LIST_DELIMITER = " ";


    //include all parts that might have embedded objects
    private final static String[] MAIN_PART_RELATIONS = new String[]{
            XWPFRelation.HEADER.getRelation(),
            XWPFRelation.FOOTER.getRelation(),
            XWPFRelation.FOOTNOTE.getRelation(),
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments",
            AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA
    };


    private XWPFDocument document;
    private XWPFStyles styles;
    private Metadata metadata;

    public XWPFWordExtractorDecorator(Metadata metadata, ParseContext context, XWPFWordExtractor extractor) {
        super(context, extractor);
        this.metadata = metadata;
        document = (XWPFDocument) extractor.getDocument();
        styles = document.getStyles();
    }

    /**
     * @deprecated use {@link XWPFWordExtractorDecorator#XWPFWordExtractorDecorator(Metadata, ParseContext, XWPFWordExtractor)}
     * @param context
     * @param extractor
     */
    @Deprecated
    public XWPFWordExtractorDecorator(ParseContext context, XWPFWordExtractor extractor) {
        this(new Metadata(), context, extractor);
    }

    /**
     * @see org.apache.poi.xwpf.extractor.XWPFWordExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        XWPFHeaderFooterPolicy hfPolicy = document.getHeaderFooterPolicy();
        XWPFListManager listManager = new XWPFListManager(document.getNumbering());
        // headers
        if (hfPolicy != null && config.getIncludeHeadersAndFooters()) {
            extractHeaders(xhtml, hfPolicy, listManager);
        }

        // process text in the order that it occurs in
        extractIBodyText(document, listManager, xhtml);

        //handle the diagram data
        handleGeneralTextContainingPart(
                RELATION_DIAGRAM_DATA,
                "diagram-data",
                document.getPackagePart(),
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml),
                        new HashMap<String, String>()//empty
                )
        );
        //handle chart data
        handleGeneralTextContainingPart(
                XSSFRelation.CHART.getRelation(),
                "chart",
                document.getPackagePart(),
                metadata,
                new OOXMLWordAndPowerPointTextHandler(
                        new OOXMLTikaBodyPartHandler(xhtml),
                        new HashMap<String, String>()//empty
                )
        );

        // then all document footers
        if (hfPolicy != null && config.getIncludeHeadersAndFooters()) {
            extractFooters(xhtml, hfPolicy, listManager);
        }
    }

    private void extractIBodyText(IBody bodyElement, XWPFListManager listManager,
                                  XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        for (IBodyElement element : bodyElement.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph paragraph = (XWPFParagraph) element;
                extractParagraph(paragraph, listManager, xhtml);
            }
            if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                extractTable(table, listManager, xhtml);
            }
            if (element instanceof XWPFSDT) {
                extractSDT((XWPFSDT) element, xhtml);
            }

        }
    }

    private void extractSDT(XWPFSDT element, XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        ISDTContent content = element.getContent();
        String tag = "p";
        xhtml.startElement(tag);
        xhtml.characters(content.getText());
        xhtml.endElement(tag);
    }

    private void extractParagraph(XWPFParagraph paragraph, XWPFListManager listManager,
                                  XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        // If this paragraph is actually a whole new section, then
        //  it could have its own headers and footers
        // Check and handle if so
        XWPFHeaderFooterPolicy headerFooterPolicy = null;
        if (paragraph.getCTP().getPPr() != null) {
            CTSectPr ctSectPr = paragraph.getCTP().getPPr().getSectPr();
            if (ctSectPr != null && config.getIncludeHeadersAndFooters()) {
                headerFooterPolicy =
                        new XWPFHeaderFooterPolicy(document, ctSectPr);
                extractHeaders(xhtml, headerFooterPolicy, listManager);
            }
        }

        // Is this a paragraph, or a heading?
        String tag = "p";
        String styleClass = null;
        //TIKA-2144 check that styles is not null
        if (paragraph.getStyleID() != null && styles != null) {
            XWPFStyle style = styles.getStyle(
                    paragraph.getStyleID()
            );

            if (style != null && style.getName() != null) {
                TagAndStyle tas = WordExtractor.buildParagraphTagAndStyle(
                        style.getName(), paragraph.getPartType() == BodyType.TABLECELL
                );
                tag = tas.getTag();
                styleClass = tas.getStyleClass();
            }
        }

        if (styleClass == null) {
            xhtml.startElement(tag);
        } else {
            xhtml.startElement(tag, "class", styleClass);
        }

        writeParagraphNumber(paragraph, listManager, xhtml);
        // Output placeholder for any embedded docs:

        // TODO: replace w/ XPath/XQuery:
        for (XWPFRun run : paragraph.getRuns()) {
            XmlCursor c = run.getCTR().newCursor();
            c.selectPath("./*");
            while (c.toNextSelection()) {
                XmlObject o = c.getObject();
                if (o instanceof CTObject) {
                    XmlCursor c2 = o.newCursor();
                    c2.selectPath("./*");
                    while (c2.toNextSelection()) {
                        XmlObject o2 = c2.getObject();

                        XmlObject embedAtt = o2.selectAttribute(new QName("Type"));
                        if (embedAtt != null && embedAtt.getDomNode().getNodeValue().equals("Embed")) {
                            // Type is "Embed"
                            XmlObject relIDAtt = o2.selectAttribute(new QName("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"));
                            if (relIDAtt != null) {
                                String relID = relIDAtt.getDomNode().getNodeValue();
                                AttributesImpl attributes = new AttributesImpl();
                                attributes.addAttribute("", "class", "class", "CDATA", "embedded");
                                attributes.addAttribute("", "id", "id", "CDATA", relID);
                                xhtml.startElement("div", attributes);
                                xhtml.endElement("div");
                            }
                        }
                    }
                    c2.dispose();
                }
            }

            c.dispose();
        }

        // Attach bookmarks for the paragraph
        // (In future, we might put them in the right place, for now
        //  we just put them in the correct paragraph)
        for (int i = 0; i < paragraph.getCTP().sizeOfBookmarkStartArray(); i++) {
            CTBookmark bookmark = paragraph.getCTP().getBookmarkStartArray(i);
            xhtml.startElement("a", "name", bookmark.getName());
            xhtml.endElement("a");
        }

        TmpFormatting fmtg = new TmpFormatting(false, false, false, false);

        //hyperlinks may or may not have hyperlink ids
        String lastHyperlinkId = null;
        boolean inHyperlink = false;
        // Do the iruns
        for (IRunElement run : paragraph.getIRuns()) {

            if (run instanceof XWPFHyperlinkRun) {
                XWPFHyperlinkRun hyperlinkRun = (XWPFHyperlinkRun) run;
                if (hyperlinkRun.getHyperlinkId() == null ||
                        !hyperlinkRun.getHyperlinkId().equals(lastHyperlinkId)) {
                    if (inHyperlink) {
                        //close out the old one
                        xhtml.endElement("a");
                        inHyperlink = false;
                    }
                    lastHyperlinkId = hyperlinkRun.getHyperlinkId();
                    fmtg = closeStyleTags(xhtml, fmtg);
                    XWPFHyperlink link = hyperlinkRun.getHyperlink(document);
                    if (link != null && link.getURL() != null) {
                        xhtml.startElement("a", "href", link.getURL());
                        inHyperlink = true;
                    } else if (hyperlinkRun.getAnchor() != null && hyperlinkRun.getAnchor().length() > 0) {
                        xhtml.startElement("a", "href", "#" + hyperlinkRun.getAnchor());
                        inHyperlink = true;
                    }
                }
            } else if (inHyperlink) {
                //if this isn't a hyperlink, but the last one was
                closeStyleTags(xhtml, fmtg);
                xhtml.endElement("a");
                lastHyperlinkId = null;
                inHyperlink = false;
            }

            if (run instanceof XWPFSDT) {
                fmtg = closeStyleTags(xhtml, fmtg);
                processSDTRun((XWPFSDT) run, xhtml);
                //for now, we're ignoring formatting in sdt
                //if you hit an sdt reset to false
                fmtg.setBold(false);
                fmtg.setItalic(false);
            } else {
                fmtg = processRun((XWPFRun) run, paragraph, xhtml, fmtg);
            }
        }
        closeStyleTags(xhtml, fmtg);
        if (inHyperlink) {
            xhtml.endElement("a");
        }


        // Now do any comments for the paragraph
        XWPFCommentsDecorator comments = new XWPFCommentsDecorator(paragraph, null);
        String commentText = comments.getCommentText();
        if (commentText != null && commentText.length() > 0) {
            xhtml.characters(commentText);
        }

        String footnameText = paragraph.getFootnoteText();
        if (footnameText != null && footnameText.length() > 0) {
            xhtml.characters(footnameText + "\n");
        }

        // Also extract any paragraphs embedded in text boxes:
        if (config.getIncludeShapeBasedContent()) {
            for (XmlObject embeddedParagraph : paragraph.getCTP().selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' declare namespace wps='http://schemas.microsoft.com/office/word/2010/wordprocessingShape' .//*/wps:txbx/w:txbxContent/w:p")) {
                extractParagraph(new XWPFParagraph(CTP.Factory.parse(embeddedParagraph.xmlText()), paragraph.getBody()), listManager, xhtml);
            }
        }

        // Finish this paragraph
        xhtml.endElement(tag);

        if (headerFooterPolicy != null && config.getIncludeHeadersAndFooters()) {
            extractFooters(xhtml, headerFooterPolicy, listManager);
        }
    }

    private void writeParagraphNumber(XWPFParagraph paragraph,
                                      XWPFListManager listManager,
                                      XHTMLContentHandler xhtml) throws SAXException {
        if (paragraph.getNumIlvl() == null) {
            return;
        }
        String number = listManager.getFormattedNumber(paragraph);
        if (number != null) {
            xhtml.characters(number);
        }

    }

    private TmpFormatting closeStyleTags(XHTMLContentHandler xhtml,
                                         TmpFormatting fmtg) throws SAXException {
        // Close any still open style tags
        if (fmtg.isItalic()) {
            xhtml.endElement("i");
            fmtg.setItalic(false);
        }
        if (fmtg.isBold()) {
            xhtml.endElement("b");
            fmtg.setBold(false);
        }
        if (fmtg.isUnderline()) {
        	xhtml.endElement("u");
        	fmtg.setUnderline(false);
        }
        if (fmtg.isStrikeThrough()) {
            xhtml.endElement("strike");
            fmtg.setStrikeThrough(false);
        }
        return fmtg;
    }

    private TmpFormatting processRun(XWPFRun run, XWPFParagraph paragraph,
                                     XHTMLContentHandler xhtml, TmpFormatting tfmtg)
            throws SAXException, XmlException, IOException {
        // True if we are currently in the named style tag:
        if (run.isBold() != tfmtg.isBold()) {
            if (tfmtg.isStrikeThrough()) {
                xhtml.endElement("strike");
                tfmtg.setStrikeThrough(false);
            }
            if (tfmtg.isUnderline()) {
                xhtml.endElement("u");
                tfmtg.setUnderline(false);
            }
            if (tfmtg.isItalic()) {
                xhtml.endElement("i");
                tfmtg.setItalic(false);
            }
            if (run.isBold()) {
                xhtml.startElement("b");
            } else {
                xhtml.endElement("b");
            }
            tfmtg.setBold(run.isBold());
        }

        if (run.isItalic() != tfmtg.isItalic()) {
            if (tfmtg.isStrikeThrough()) {
                xhtml.endElement("strike");
                tfmtg.setStrikeThrough(false);
            }
            if (tfmtg.isUnderline()) {
                xhtml.endElement("u");
                tfmtg.setUnderline(false);
            }
            if (run.isItalic()) {
                xhtml.startElement("i");
            } else {
                xhtml.endElement("i");
            }
            tfmtg.setItalic(run.isItalic());
        }

        if (run.isStrikeThrough() != tfmtg.isStrikeThrough()) {
            if (tfmtg.isUnderline()) {
                xhtml.endElement("u");
                tfmtg.setUnderline(false);
            }
            if (run.isStrikeThrough()) {
                xhtml.startElement("strike");
            } else {
                xhtml.endElement("strike");
            }
            tfmtg.setStrikeThrough(run.isStrikeThrough());
        }

        boolean isUnderline = run.getUnderline() != UnderlinePatterns.NONE;
        if (isUnderline != tfmtg.isUnderline()) {
            if (isUnderline) {
                xhtml.startElement("u");
            } else {
                xhtml.endElement("u");
            }
            tfmtg.setUnderline(isUnderline);
        }

        if (config.getConcatenatePhoneticRuns()) {
            xhtml.characters(run.toString());
        } else {
            xhtml.characters(run.text());
        }

        // If we have any pictures, output them
        for (XWPFPicture picture : run.getEmbeddedPictures()) {
            if (paragraph.getDocument() != null) {
                XWPFPictureData data = picture.getPictureData();
                if (data != null) {
                    AttributesImpl attr = new AttributesImpl();

                    attr.addAttribute("", "src", "src", "CDATA", "embedded:" + data.getFileName());
                    attr.addAttribute("", "alt", "alt", "CDATA", picture.getDescription());

                    xhtml.startElement("img", attr);
                    xhtml.endElement("img");
                }
            }
        }

        return tfmtg;
    }

    private void processSDTRun(XWPFSDT run, XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        xhtml.characters(run.getContent().getText());
    }

    private void extractTable(XWPFTable table, XWPFListManager listManager,
                              XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        xhtml.startElement("table");
        xhtml.startElement("tbody");
        for (XWPFTableRow row : table.getRows()) {
            xhtml.startElement("tr");
            for (ICell cell : row.getTableICells()) {
                xhtml.startElement("td");
                if (cell instanceof XWPFTableCell) {
                    extractIBodyText((XWPFTableCell) cell, listManager, xhtml);
                } else if (cell instanceof XWPFSDTCell) {
                    xhtml.characters(((XWPFSDTCell) cell).getContent().getText());
                }
                xhtml.endElement("td");
            }
            xhtml.endElement("tr");
        }
        xhtml.endElement("tbody");
        xhtml.endElement("table");
    }

    private void extractFooters(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy,
            XWPFListManager listManager)
            throws SAXException, XmlException, IOException {
        // footers
        if (hfPolicy.getFirstPageFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getFirstPageFooter(), listManager);
        }
        if (hfPolicy.getEvenPageFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getEvenPageFooter(), listManager);
        }
        if (hfPolicy.getDefaultFooter() != null) {
            extractHeaderText(xhtml, hfPolicy.getDefaultFooter(), listManager);
        }
    }

    private void extractHeaders(
            XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy, XWPFListManager listManager)
            throws SAXException, XmlException, IOException {
        if (hfPolicy == null) return;

        if (hfPolicy.getFirstPageHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getFirstPageHeader(), listManager);
        }

        if (hfPolicy.getEvenPageHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getEvenPageHeader(), listManager);
        }

        if (hfPolicy.getDefaultHeader() != null) {
            extractHeaderText(xhtml, hfPolicy.getDefaultHeader(), listManager);
        }
    }

    private void extractHeaderText(XHTMLContentHandler xhtml, XWPFHeaderFooter header, XWPFListManager listManager) throws SAXException, XmlException, IOException {

        for (IBodyElement e : header.getBodyElements()) {
            if (e instanceof XWPFParagraph) {
                extractParagraph((XWPFParagraph) e, listManager, xhtml);
            } else if (e instanceof XWPFTable) {
                extractTable((XWPFTable) e, listManager, xhtml);
            } else if (e instanceof XWPFSDT) {
                extractSDT((XWPFSDT) e, xhtml);
            }
        }
    }

    /**
     * Include main body and anything else that can
     * have an attachment/embedded object
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() {
        List<PackagePart> parts = new ArrayList<PackagePart>();
        parts.add(document.getPackagePart());
        addRelatedParts(document.getPackagePart(), parts);
        return parts;
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

    private class TmpFormatting {
        private boolean bold = false;
        private boolean italic = false;
        private boolean underline = false;
        private boolean strikeThrough = false;


        private TmpFormatting(boolean bold, boolean italic, boolean underline,
                              boolean strikeThrough) {
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strikeThrough = strikeThrough;
        }

        public boolean isBold() {
            return bold;
        }

        public void setBold(boolean bold) {
            this.bold = bold;
        }

        public boolean isItalic() {
            return italic;
        }

        public void setItalic(boolean italic) {
            this.italic = italic;
        }
        

        public boolean isUnderline() {
            return underline;
        }

        public void setUnderline(boolean underline) {
            this.underline = underline;
        }

        public boolean isStrikeThrough() {
            return strikeThrough;
        }

        public void setStrikeThrough(boolean strikeThrough) {
            this.strikeThrough = strikeThrough;
        }
    }

}
