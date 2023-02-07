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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import com.microsoft.schemas.vml.impl.CTShapeImpl;
import org.apache.poi.ooxml.POIXMLDocumentPart;
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
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.EMFParser;
import org.apache.tika.parser.microsoft.FormattingUtils;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.parser.microsoft.WordExtractor.TagAndStyle;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.StringUtils;

public class XWPFWordExtractorDecorator extends AbstractOOXMLExtractor {

    // could be improved by using the real delimiter in xchFollow [MS-DOC], v20140721, 2.4.6.3,
    // Part 3, Step 3
    private static final String LIST_DELIMITER = " ";


    //include all parts that might have embedded objects
    private final static String[] MAIN_PART_RELATIONS =
            new String[]{XWPFRelation.HEADER.getRelation(), XWPFRelation.FOOTER.getRelation(),
                    XWPFRelation.FOOTNOTE.getRelation(),
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/endnotes",
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments",
                    AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA};


    private XWPFDocument document;
    private XWPFStyles styles;
    private Metadata metadata;

    private Map<String, EmbeddedPartMetadata> embeddedPartMetadataMap = new HashMap<>();

    public XWPFWordExtractorDecorator(Metadata metadata, ParseContext context,
                                      XWPFWordExtractor extractor) {
        super(context, extractor);
        this.metadata = metadata;
        document = (XWPFDocument) extractor.getDocument();
        styles = document.getStyles();
    }

    /**
     * @param context
     * @param extractor
     * @deprecated use {@link XWPFWordExtractorDecorator#XWPFWordExtractorDecorator(Metadata,
     * ParseContext, XWPFWordExtractor)}
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
        if (hfPolicy != null && config.isIncludeHeadersAndFooters()) {
            extractHeaders(xhtml, hfPolicy, listManager);
        }

        // process text in the order that it occurs in
        extractIBodyText(document, listManager, xhtml);

        //handle the diagram data
        handleGeneralTextContainingPart(RELATION_DIAGRAM_DATA, "diagram-data",
                document.getPackagePart(), metadata,
                new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                        new HashMap<>()//empty
                ));
        //handle chart data
        handleGeneralTextContainingPart(XSSFRelation.CHART.getRelation(), "chart",
                document.getPackagePart(), metadata,
                new OOXMLWordAndPowerPointTextHandler(new OOXMLTikaBodyPartHandler(xhtml),
                        new HashMap<>()//empty
                ));

        // then all document footers
        if (hfPolicy != null && config.isIncludeHeadersAndFooters()) {
            extractFooters(xhtml, hfPolicy, listManager);
        }
    }

    @Override
    protected Map<String, EmbeddedPartMetadata> getEmbeddedPartMetadataMap() {
        return embeddedPartMetadataMap;
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

    private void extractSDT(XWPFSDT element, XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
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
            if (ctSectPr != null && config.isIncludeHeadersAndFooters()) {
                headerFooterPolicy = new XWPFHeaderFooterPolicy(document, ctSectPr);
                extractHeaders(xhtml, headerFooterPolicy, listManager);
            }
        }

        // Is this a paragraph, or a heading?
        String tag = "p";
        String styleClass = null;
        //TIKA-2144 check that styles is not null
        if (paragraph.getStyleID() != null && styles != null) {
            XWPFStyle style = styles.getStyle(paragraph.getStyleID());

            if (style != null && style.getName() != null) {
                TagAndStyle tas = WordExtractor.buildParagraphTagAndStyle(style.getName(),
                        paragraph.getPartType() == BodyType.TABLECELL);
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
        processEmbeddedObjects(paragraph.getRuns(), xhtml);

        // Attach bookmarks for the paragraph
        // (In future, we might put them in the right place, for now
        //  we just put them in the correct paragraph)
        for (int i = 0; i < paragraph.getCTP().sizeOfBookmarkStartArray(); i++) {
            CTBookmark bookmark = paragraph.getCTP().getBookmarkStartArray(i);
            xhtml.startElement("a", "name", bookmark.getName());
            xhtml.endElement("a");
        }

        Deque<FormattingUtils.Tag> formattingState = new ArrayDeque<>();

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
                        FormattingUtils.closeStyleTags(xhtml, formattingState);
                        xhtml.endElement("a");
                        inHyperlink = false;
                    }
                    lastHyperlinkId = hyperlinkRun.getHyperlinkId();
                    FormattingUtils.closeStyleTags(xhtml, formattingState);
                    XWPFHyperlink link = hyperlinkRun.getHyperlink(document);
                    if (link != null && link.getURL() != null) {
                        xhtml.startElement("a", "href", link.getURL());
                        inHyperlink = true;
                    } else if (hyperlinkRun.getAnchor() != null &&
                            hyperlinkRun.getAnchor().length() > 0) {
                        xhtml.startElement("a", "href", "#" + hyperlinkRun.getAnchor());
                        inHyperlink = true;
                    }
                }
            } else if (inHyperlink) {
                //if this isn't a hyperlink, but the last one was
                FormattingUtils.closeStyleTags(xhtml, formattingState);
                xhtml.endElement("a");
                lastHyperlinkId = null;
                inHyperlink = false;
            }

            if (run instanceof XWPFSDT) {
                FormattingUtils.closeStyleTags(xhtml, formattingState);
                processSDTRun((XWPFSDT) run, xhtml);
                //for now, we're ignoring formatting in sdt
                //if you hit an sdt reset to false
            } else {
                processRun((XWPFRun) run, paragraph, xhtml, formattingState);
            }
        }
        FormattingUtils.closeStyleTags(xhtml, formattingState);
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

        // Also extract any paragraphs embedded in text boxes
        //Note "w:txbxContent//"...must look for all descendant paragraphs
        //not just the immediate children of txbxContent -- TIKA-2807
        if (config.isIncludeShapeBasedContent()) {
            for (XmlObject embeddedParagraph : paragraph.getCTP().selectPath(
                    "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' declare namespace wps='http://schemas.microsoft.com/office/word/2010/wordprocessingShape' .//*/wps:txbx/w:txbxContent//w:p")) {
                extractParagraph(new XWPFParagraph(CTP.Factory.parse(embeddedParagraph.xmlText()),
                        paragraph.getBody()), listManager, xhtml);
            }
        }

        // Finish this paragraph
        xhtml.endElement(tag);

        if (headerFooterPolicy != null && config.isIncludeHeadersAndFooters()) {
            extractFooters(xhtml, headerFooterPolicy, listManager);
        }
    }

    private void processEmbeddedObjects(List<XWPFRun> runs, XHTMLContentHandler xhtml)
            throws SAXException {
        // TODO: replace w/ XPath/XQuery:
        for (XWPFRun run : runs) {
            try (XmlCursor c = run.getCTR().newCursor()) {
                c.selectPath("./*");
                while (c.toNextSelection()) {
                    XmlObject o = c.getObject();
                    if (o instanceof CTObject) {
                        try (XmlCursor objectCursor = o.newCursor()) {
                            processObject(objectCursor, xhtml);
                        }
                    }
                }
            }
        }
    }

    private void processObject(XmlCursor cursor, XHTMLContentHandler xhtml) throws SAXException {

        cursor.selectPath("./*");
        String objectRelId = null;
        String progId = null;
        EmbeddedPartMetadata embeddedPartMetadata = null;
        while (cursor.toNextSelection()) {
            XmlObject o2 = cursor.getObject();
            XmlObject embedAtt = o2.selectAttribute(new QName("Type"));
            if (embedAtt != null &&
                    embedAtt.getDomNode().getNodeValue().equals("Embed")) {
                //TODO: get ProgID, while we're here?
                // Type is "Embed"
                XmlObject relIDAtt = o2.selectAttribute(new QName(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id"));
                if (relIDAtt != null) {
                    objectRelId = relIDAtt.getDomNode().getNodeValue();
                }

                XmlObject progIDAtt = o2.selectAttribute(new QName("ProgID"));
                if (progIDAtt != null) {
                    progId = progIDAtt.getDomNode().getNodeValue();
                }
            } else if (o2 instanceof CTShapeImpl) {
                XmlObject[] imagedata = o2.selectChildren(
                        new QName("urn:schemas" +
                                "-microsoft-com:vml","imagedata"));
                if (imagedata.length > 0) {
                    XmlObject relIDAtt = imagedata[0].selectAttribute(new QName(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                            "id"));
                    if (relIDAtt != null) {
                        String rid = relIDAtt.getDomNode().getNodeValue();
                        embeddedPartMetadata = new EmbeddedPartMetadata(rid);
                        tryToParseEmbeddedName(rid, embeddedPartMetadata);
                    }
                }
            }
        }
        if (objectRelId == null) {
            return;
        }
        if (! StringUtils.isBlank(progId)) {
            embeddedPartMetadata.setProgId(progId);
        }

        if (embeddedPartMetadata != null) {
            embeddedPartMetadataMap.put(objectRelId, embeddedPartMetadata);
        }
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "embedded");
        attributes.addAttribute("", "id", "id", "CDATA", objectRelId);
        if (!StringUtils.isBlank(embeddedPartMetadata.getFullName())) {
            attributes.addAttribute("", "name", "name", "CDATA",
                    embeddedPartMetadata.getFullName());
        }
        xhtml.startElement("div", attributes);
        xhtml.endElement("div");
    }

    private String tryToParseEmbeddedName(String rid, EmbeddedPartMetadata embeddedPartMetadata) {
        //This tries to parse the embedded name out of a comment
        //field in an emf
        POIXMLDocumentPart part = document.getRelationById(rid);
        if (part == null || part.getPackagePart() == null
                || part.getPackagePart().getContentType() == null) {
            return null;
        }
        PackagePart packagePart = part.getPackagePart();
        if ("image/x-emf".equals(packagePart.getContentType())) {
            try (InputStream is = packagePart.getInputStream()) {
                EMFParser p = new EMFParser();
                Metadata m = new Metadata();
                ParseContext pc = new ParseContext();
                ToTextContentHandler toTextContentHandler = new ToTextContentHandler();
                p.parse(is, toTextContentHandler, m, pc);
                embeddedPartMetadata.setRenderedName(toTextContentHandler.toString().trim());
                embeddedPartMetadata.setFullName(m.get(EMFParser.EMF_ICON_STRING));
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                //we tried
            }
        }
        return null;
    }

    private void writeParagraphNumber(XWPFParagraph paragraph, XWPFListManager listManager,
                                      XHTMLContentHandler xhtml) throws SAXException {
        if (paragraph.getNumIlvl() == null) {
            return;
        }
        String number = listManager.getFormattedNumber(paragraph);
        if (number != null) {
            xhtml.characters(number);
        }

    }

    private void processRun(XWPFRun run, XWPFParagraph paragraph, XHTMLContentHandler xhtml,
                            Deque<FormattingUtils.Tag> formattingState)
            throws SAXException, XmlException, IOException {
        // open/close required tags if run changes formatting
        FormattingUtils.ensureFormattingState(xhtml, FormattingUtils.toTags(run), formattingState);

        if (config.isConcatenatePhoneticRuns()) {
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

    private void extractFooters(XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy,
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

    private void extractHeaders(XHTMLContentHandler xhtml, XWPFHeaderFooterPolicy hfPolicy,
                                XWPFListManager listManager)
            throws SAXException, XmlException, IOException {
        if (hfPolicy == null) {
            return;
        }

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

    private void extractHeaderText(XHTMLContentHandler xhtml, XWPFHeaderFooter header,
                                   XWPFListManager listManager)
            throws SAXException, XmlException, IOException {

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
        List<PackagePart> parts = new ArrayList<>();
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
                        PackagePart packagePart =
                                documentPart.getRelatedPart(prc.getRelationship(i));
                        relatedParts.add(packagePart);
                    }
                }
            } catch (InvalidFormatException e) {
                //swallow
            }
        }

    }

}
