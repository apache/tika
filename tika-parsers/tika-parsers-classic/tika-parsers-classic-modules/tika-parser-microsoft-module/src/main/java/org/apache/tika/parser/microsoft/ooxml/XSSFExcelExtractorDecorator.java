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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.ooxml.extractor.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HeaderFooter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.poi.xssf.model.CommentsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.poi.xssf.usermodel.helpers.HeaderFooterHelper;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.drawingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTShape;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTShapeNonVisual;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.TikaExcelDataFormatter;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

public class XSSFExcelExtractorDecorator extends AbstractOOXMLExtractor {
    /**
     * Allows access to headers/footers from raw xml strings
     */
    protected static HeaderFooterHelper hfHelper = new HeaderFooterHelper();
    protected final DataFormatter formatter;
    protected final List<PackagePart> sheetParts = new ArrayList<>();
    protected final Map<String, String> drawingHyperlinks = new HashMap<>();
    protected Metadata metadata;
    protected ParseContext parseContext;

    public XSSFExcelExtractorDecorator(ParseContext context, POIXMLTextExtractor extractor,
                                       Locale locale) {
        super(context, extractor);

        this.parseContext = context;
        this.extractor = (XSSFEventBasedExcelExtractor) extractor;
        configureExtractor(this.extractor, locale);

        if (locale == null) {
            formatter = new TikaExcelDataFormatter();
        } else {
            formatter = new TikaExcelDataFormatter(locale);
        }
        OfficeParserConfig officeParserConfig = context.get(OfficeParserConfig.class);
        if (officeParserConfig != null) {
            ((TikaExcelDataFormatter) formatter)
                    .setDateFormatOverride(officeParserConfig.getDateFormatOverride());
        }
    }

    protected void configureExtractor(POIXMLTextExtractor extractor, Locale locale) {
        ((XSSFEventBasedExcelExtractor) extractor)
                .setIncludeTextBoxes(config.isIncludeShapeBasedContent());
        ((XSSFEventBasedExcelExtractor) extractor).setFormulasNotResults(false);
        ((XSSFEventBasedExcelExtractor) extractor).setLocale(locale);
        //given that we load our own shared strings table, setting:
        //((XSSFEventBasedExcelExtractor)extractor).setConcatenatePhoneticRuns();
        //does no good here.
    }

    @Override
    public void getXHTML(ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {

        this.metadata = metadata;
        this.parseContext = context;
        metadata.set(TikaCoreProperties.PROTECTED, "false");

        super.getXHTML(handler, metadata, context);
    }

    /**
     * @see org.apache.poi.xssf.extractor.XSSFExcelExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, XmlException, IOException {
        OPCPackage container = extractor.getPackage();

        ReadOnlySharedStringsTable strings;
        XSSFReader.SheetIterator iter;
        XSSFReader xssfReader;
        StylesTable styles;
        try {
            xssfReader = new XSSFReader(container);
            styles = xssfReader.getStylesTable();

            iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            strings = new ReadOnlySharedStringsTable(container, config.isConcatenatePhoneticRuns());
        } catch (OpenXML4JException e) {
            throw new XmlException(e);
        }

        while (iter.hasNext()) {
            SheetTextAsHTML sheetExtractor = new SheetTextAsHTML(config, xhtml);
            PackagePart sheetPart = null;
            try (InputStream stream = iter.next()) {
                sheetPart = iter.getSheetPart();

                addDrawingHyperLinks(sheetPart);
                sheetParts.add(sheetPart);

                CommentsTable comments = iter.getSheetComments();

                // Start, and output the sheet name
                xhtml.startElement("div");
                xhtml.element("h1", iter.getSheetName());

                // Extract the main sheet contents
                xhtml.startElement("table");
                xhtml.startElement("tbody");

                processSheet(sheetExtractor, comments, styles, strings, stream);
            }
            xhtml.endElement("tbody");
            xhtml.endElement("table");

            // Output any headers and footers
            // (Need to process the sheet to get them, so we can't
            //  do the headers before the contents)
            for (String header : sheetExtractor.headers) {
                extractHeaderFooter(header, xhtml);
            }
            for (String footer : sheetExtractor.footers) {
                extractHeaderFooter(footer, xhtml);
            }

            // Do text held in shapes, if required
            if (config.isIncludeShapeBasedContent()) {
                List<XSSFShape> shapes = iter.getShapes();
                processShapes(shapes, xhtml);
            }

            //for now dump sheet hyperlinks at bottom of page
            //consider a double-pass of the inputstream to reunite hyperlinks with cells/textboxes
            //step 1: extract hyperlink info from bottom of page
            //step 2: process as we do now, but with cached hyperlink relationship info
            extractHyperLinks(sheetPart, xhtml);
            // All done with this sheet
            xhtml.endElement("div");
        }

        //consider adding this back to POI
        try (InputStream wbData = xssfReader.getWorkbookData()) {
            XMLReaderUtils
                    .parseSAX(wbData, new OfflineContentHandler(new AbsPathExtractorHandler()),
                            parseContext);
        } catch (InvalidFormatException | TikaException e) {
            //swallow
        }
    }


    protected void addDrawingHyperLinks(PackagePart sheetPart) {
        try {
            for (PackageRelationship rel : sheetPart
                    .getRelationshipsByType(XSSFRelation.DRAWINGS.getRelation())) {
                if (rel.getTargetMode() == TargetMode.INTERNAL) {
                    PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                    PackagePart part = rel.getPackage().getPart(relName);
                    //parts can go missing, and Excel quietly ignores missing images -- TIKA-2134
                    if (part == null) {
                        continue;
                    }
                    for (PackageRelationship drawRel : part
                            .getRelationshipsByType(XSSFRelation.SHEET_HYPERLINKS.getRelation())) {
                        drawingHyperlinks.put(drawRel.getId(), drawRel.getTargetURI().toString());
                    }
                }
            }
        } catch (InvalidFormatException e) {
            //swallow
            //an exception trying to extract
            //hyperlinks on drawings should not cause a parse failure
        }

    }


    protected void extractHyperLinks(PackagePart sheetPart, XHTMLContentHandler xhtml)
            throws SAXException {
        try {
            for (PackageRelationship rel : sheetPart
                    .getRelationshipsByType(XSSFRelation.SHEET_HYPERLINKS.getRelation())) {
                xhtml.startElement("a", "href", rel.getTargetURI().toString());
                xhtml.characters(rel.getTargetURI().toString());
                xhtml.endElement("a");
            }
        } catch (InvalidFormatException e) {
            //swallow
        }
    }

    protected void extractHeaderFooter(String hf, XHTMLContentHandler xhtml) throws SAXException {
        String content = ExcelExtractor._extractHeaderFooter(new HeaderFooterFromString(hf));
        if (content.length() > 0) {
            xhtml.element("p", content);
        }
    }

    protected void processShapes(List<XSSFShape> shapes, XHTMLContentHandler xhtml)
            throws SAXException {
        if (shapes == null) {
            return;
        }
        //We don't currently have an obvious way to get drawings
        //directly from sheetIter. Therefore, we grab the shapes and process those.
        //To get the diagrams and charts, we need to get the parent drawing for each
        //shape, and we need to make sure that we only process each parent shape once!
        //SEE TIKA-2703 TODO: add unit test
        Set<String> seenParentDrawings = new HashSet<>();
        for (XSSFShape shape : shapes) {
            if (shape instanceof XSSFSimpleShape) {
                String sText = ((XSSFSimpleShape) shape).getText();
                if (sText != null && sText.length() > 0) {
                    xhtml.element("p", sText);
                }
                extractHyperLinksFromShape(((XSSFSimpleShape) shape).getCTShape(), xhtml);
            }

            XSSFDrawing parentDrawing = shape.getDrawing();
            if (parentDrawing != null) {
                if (!seenParentDrawings
                        .contains(parentDrawing.getPackagePart().getPartName().toString())) {
                    //dump diagram data
                    handleGeneralTextContainingPart(AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                            "diagram-data", parentDrawing.getPackagePart(), metadata,
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml),
                                    new HashMap<>()//empty
                            ));
                    //dump chart data
                    handleGeneralTextContainingPart(XSSFRelation.CHART.getRelation(), "chart",
                            parentDrawing.getPackagePart(), metadata,
                            new OOXMLWordAndPowerPointTextHandler(
                                    new OOXMLTikaBodyPartHandler(xhtml),
                                    new HashMap<>()//empty
                            ));
                }
                seenParentDrawings.add(parentDrawing.getPackagePart().getPartName().toString());
            }
        }
    }

    private void extractHyperLinksFromShape(CTShape ctShape, XHTMLContentHandler xhtml)
            throws SAXException {

        if (ctShape == null) {
            return;
        }

        CTShapeNonVisual nvSpPR = ctShape.getNvSpPr();
        if (nvSpPR == null) {
            return;
        }

        CTNonVisualDrawingProps cNvPr = nvSpPR.getCNvPr();
        if (cNvPr == null) {
            return;
        }

        CTHyperlink ctHyperlink = cNvPr.getHlinkClick();
        if (ctHyperlink == null) {
            return;
        }

        String url = drawingHyperlinks.get(ctHyperlink.getId());
        if (url != null) {
            xhtml.startElement("a", "href", url);
            xhtml.characters(url);
            xhtml.endElement("a");
        }

        CTHyperlink ctHoverHyperlink = cNvPr.getHlinkHover();
        if (ctHoverHyperlink == null) {
            return;
        }

        url = drawingHyperlinks.get(ctHoverHyperlink.getId());
        if (url != null) {
            xhtml.startElement("a", "href", url);
            xhtml.characters(url);
            xhtml.endElement("a");
        }

    }

    public void processSheet(SheetContentsHandler sheetContentsExtractor, CommentsTable comments,
                             StylesTable styles, ReadOnlySharedStringsTable strings,
                             InputStream sheetInputStream) throws IOException, SAXException {
        try {

            XSSFSheetInterestingPartsCapturer handler = new XSSFSheetInterestingPartsCapturer(
                    new XSSFSheetXMLHandler(styles, comments, strings, sheetContentsExtractor,
                            formatter, false));
            XMLReaderUtils.parseSAX(sheetInputStream, handler, parseContext);
            sheetInputStream.close();

            if (handler.hasProtection) {
                metadata.set(TikaCoreProperties.PROTECTED, "true");
            }
        } catch (TikaException e) {
            throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
        }
    }

    /**
     * In Excel files, sheets have things embedded in them,
     * and sheet drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
        List<PackagePart> parts = new ArrayList<>();
        for (PackagePart part : sheetParts) {
            // Add the sheet
            parts.add(part);

            // If it has drawings, return those too
            try {
                for (PackageRelationship rel : part
                        .getRelationshipsByType(XSSFRelation.DRAWINGS.getRelation())) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName =
                                PackagingURIHelper.createPartName(rel.getTargetURI());
                        parts.add(rel.getPackage().getPart(relName));
                    }
                }
                for (PackageRelationship rel : part
                        .getRelationshipsByType(XSSFRelation.VML_DRAWINGS.getRelation())) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName =
                                PackagingURIHelper.createPartName(rel.getTargetURI());
                        parts.add(rel.getPackage().getPart(relName));
                    }
                }
            } catch (InvalidFormatException e) {
                throw new TikaException("Broken OOXML file", e);
            }
        }

        //add main document so that macros can be extracted
        //by AbstractOOXMLExtractor
        parts.addAll(extractor.getPackage()
                .getPartsByRelationshipType(PackageRelationshipTypes.CORE_DOCUMENT));

        return parts;
    }

    /**
     * Turns formatted sheet events into HTML
     */
    protected static class SheetTextAsHTML implements SheetContentsHandler {
        private final boolean includeHeadersFooters;
        private final boolean includeMissingRows;
        protected List<String> headers;
        protected List<String> footers;
        private XHTMLContentHandler xhtml;
        private int lastSeenRow = -1;
        private int lastSeenCol = -1;

        protected SheetTextAsHTML(OfficeParserConfig config, XHTMLContentHandler xhtml) {
            this.includeHeadersFooters = config.isIncludeHeadersAndFooters();
            this.includeMissingRows = config.isIncludeMissingRows();
            this.xhtml = xhtml;
            headers = new ArrayList<>();
            footers = new ArrayList<>();
        }

        public void startRow(int rowNum) {
            try {
                // Missing rows, if desired, with a single empty row
                if (includeMissingRows && rowNum > (lastSeenRow + 1)) {
                    for (int rn = lastSeenRow + 1; rn < rowNum; rn++) {
                        xhtml.startElement("tr");
                        xhtml.startElement("td");
                        xhtml.endElement("td");
                        xhtml.endElement("tr");
                    }
                }

                // Start the new row
                xhtml.startElement("tr");
                lastSeenCol = -1;
            } catch (SAXException e) {
                //swallow
                throw new RuntimeSAXException(e);
            }

        }

        public void endRow(int rowNum) {
            try {
                xhtml.endElement("tr");
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
            try {
                // Handle any missing cells
                int colNum =
                        (cellRef == null) ? lastSeenCol + 1 : (new CellReference(cellRef)).getCol();
                for (int cn = lastSeenCol + 1; cn < colNum; cn++) {
                    xhtml.startElement("td");
                    xhtml.endElement("td");
                }
                lastSeenCol = colNum;

                // Start this cell
                xhtml.startElement("td");

                // Main cell contents
                if (formattedValue != null) {
                    xhtml.characters(formattedValue);
                }

                // Comments
                if (comment != null) {
                    xhtml.startElement("br");
                    xhtml.endElement("br");
                    xhtml.characters(comment.getAuthor());
                    xhtml.characters(": ");
                    xhtml.characters(comment.getString().getString());
                }

                xhtml.endElement("td");
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        public void headerFooter(String text, boolean isHeader, String tagName) {
            if (!includeHeadersFooters) {
                return;
            }
            if (isHeader) {
                headers.add(text);
            } else {
                footers.add(text);
            }
        }
    }

    protected static class HeaderFooterFromString implements HeaderFooter {
        private String text;

        protected HeaderFooterFromString(String text) {
            this.text = text;
        }

        public String getCenter() {
            return hfHelper.getCenterSection(text);
        }

        public void setCenter(String paramString) {
        }

        public String getLeft() {
            return hfHelper.getLeftSection(text);
        }

        public void setLeft(String paramString) {
        }

        public String getRight() {
            return hfHelper.getRightSection(text);
        }

        public void setRight(String paramString) {
        }
    }

    /**
     * Captures information on interesting tags, whilst
     * delegating the main work to the formatting handler
     */
    protected static class XSSFSheetInterestingPartsCapturer extends DefaultHandler {
        private ContentHandler delegate;
        private boolean hasProtection = false;

        protected XSSFSheetInterestingPartsCapturer(ContentHandler delegate) {
            this.delegate = delegate;
        }

        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if ("sheetProtection".equals(qName)) {
                hasProtection = true;
            }
            delegate.startElement(uri, localName, qName, atts);
        }

        public void characters(char[] ch, int start, int length) throws SAXException {
            delegate.characters(ch, start, length);
        }

        public void endDocument() throws SAXException {
            delegate.endDocument();
        }

        public void endElement(String uri, String localName, String qName) throws SAXException {
            delegate.endElement(uri, localName, qName);
        }

        public void endPrefixMapping(String prefix) throws SAXException {
            delegate.endPrefixMapping(prefix);
        }

        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            delegate.ignorableWhitespace(ch, start, length);
        }

        public void processingInstruction(String target, String data) throws SAXException {
            delegate.processingInstruction(target, data);
        }

        public void setDocumentLocator(Locator locator) {
            delegate.setDocumentLocator(locator);
        }

        public void skippedEntity(String name) throws SAXException {
            delegate.skippedEntity(name);
        }

        public void startDocument() throws SAXException {
            delegate.startDocument();
        }

        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            delegate.startPrefixMapping(prefix, uri);
        }
    }

    private class AbsPathExtractorHandler extends DefaultHandler {
        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            //require x15ac //http://schemas.microsoft.com/office/spreadsheetml/2010/11/ac ???
            if ("absPath".equals(localName)) {
                for (int i = 0; i < atts.getLength(); i++) {
                    String n = atts.getLocalName(i);
                    if ("url".equals(n)) {
                        String url = atts.getValue(i);
                        metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, url);
                        return;
                    }
                }
            }
        }
    }
}
