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

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HeaderFooter;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.extractor.XSSFEventBasedExcelExtractor;
import org.apache.poi.xssf.model.CommentsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.poi.xssf.usermodel.helpers.HeaderFooterHelper;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class XSSFExcelExtractorDecorator extends AbstractOOXMLExtractor {
    /**
     * Allows access to headers/footers from raw xml strings
     */
    private static HeaderFooterHelper hfHelper = new HeaderFooterHelper();
    private final XSSFEventBasedExcelExtractor extractor;
    private final DataFormatter formatter;
    private final List<PackagePart> sheetParts = new ArrayList<PackagePart>();
    private Metadata metadata;

    public XSSFExcelExtractorDecorator(
            ParseContext context, XSSFEventBasedExcelExtractor extractor, Locale locale) {
        super(context, extractor);

        this.extractor = extractor;
        extractor.setFormulasNotResults(false);
        extractor.setLocale(locale);

        if (locale == null) {
            formatter = new DataFormatter();
        } else {
            formatter = new DataFormatter(locale);
        }
    }

    @Override
    public void getXHTML(
            ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {

        this.metadata = metadata;
        metadata.set(TikaMetadataKeys.PROTECTED, "false");

        super.getXHTML(handler, metadata, context);
    }

    /**
     * @see org.apache.poi.xssf.extractor.XSSFExcelExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        OPCPackage container = extractor.getPackage();

        ReadOnlySharedStringsTable strings;
        XSSFReader.SheetIterator iter;
        XSSFReader xssfReader;
        StylesTable styles;
        try {
            xssfReader = new XSSFReader(container);
            styles = xssfReader.getStylesTable();
            iter = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            strings = new ReadOnlySharedStringsTable(container);
        } catch (InvalidFormatException e) {
            throw new XmlException(e);
        } catch (OpenXML4JException oe) {
            throw new XmlException(oe);
        }

        while (iter.hasNext()) {
            InputStream stream = iter.next();
            sheetParts.add(iter.getSheetPart());

            SheetTextAsHTML sheetExtractor = new SheetTextAsHTML(xhtml);
            CommentsTable comments = iter.getSheetComments();

            // Start, and output the sheet name
            xhtml.startElement("div");
            xhtml.element("h1", iter.getSheetName());

            // Extract the main sheet contents
            xhtml.startElement("table");
            xhtml.startElement("tbody");

            processSheet(sheetExtractor, comments, styles, strings, stream);

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
            processShapes(iter.getShapes(), xhtml);
            // All done with this sheet
            xhtml.endElement("div");
        }
    }

    private void extractHeaderFooter(String hf, XHTMLContentHandler xhtml)
            throws SAXException {
        String content = ExcelExtractor._extractHeaderFooter(
                new HeaderFooterFromString(hf));
        if (content.length() > 0) {
            xhtml.element("p", content);
        }
    }

    private void processShapes(List<XSSFShape> shapes, XHTMLContentHandler xhtml) throws SAXException {
        if (shapes == null) {
            return;
        }
        for (XSSFShape shape : shapes) {
            if (shape instanceof XSSFSimpleShape) {
                String sText = ((XSSFSimpleShape) shape).getText();
                if (sText != null && sText.length() > 0) {
                    xhtml.element("p", sText);
                }
            }
        }
    }

    public void processSheet(
            SheetContentsHandler sheetContentsExtractor,
            CommentsTable comments,
            StylesTable styles,
            ReadOnlySharedStringsTable strings,
            InputStream sheetInputStream)
            throws IOException, SAXException {
        InputSource sheetSource = new InputSource(sheetInputStream);
        SAXParserFactory saxFactory = SAXParserFactory.newInstance();
        try {
            SAXParser saxParser = saxFactory.newSAXParser();
            XMLReader sheetParser = saxParser.getXMLReader();
            XSSFSheetInterestingPartsCapturer handler =
                    new XSSFSheetInterestingPartsCapturer(new XSSFSheetXMLHandler(
                            styles, comments, strings, sheetContentsExtractor, formatter, false));
            sheetParser.setContentHandler(handler);
            sheetParser.parse(sheetSource);
            sheetInputStream.close();

            if (handler.hasProtection) {
                metadata.set(TikaMetadataKeys.PROTECTED, "true");
            }
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("SAX parser appears to be broken - " + e.getMessage());
        }
    }

    /**
     * In Excel files, sheets have things embedded in them,
     * and sheet drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
        List<PackagePart> parts = new ArrayList<PackagePart>();
        for (PackagePart part : sheetParts) {
            // Add the sheet
            parts.add(part);

            // If it has drawings, return those too
            try {
                for (PackageRelationship rel : part.getRelationshipsByType(XSSFRelation.DRAWINGS.getRelation())) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                        parts.add(rel.getPackage().getPart(relName));
                    }
                }
                for (PackageRelationship rel : part.getRelationshipsByType(XSSFRelation.VML_DRAWINGS.getRelation())) {
                    if (rel.getTargetMode() == TargetMode.INTERNAL) {
                        PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                        parts.add(rel.getPackage().getPart(relName));
                    }
                }
            } catch (InvalidFormatException e) {
                throw new TikaException("Broken OOXML file", e);
            }
        }

        return parts;
    }

    /**
     * Turns formatted sheet events into HTML
     */
    protected static class SheetTextAsHTML implements SheetContentsHandler {
        private XHTMLContentHandler xhtml;
        private List<String> headers;
        private List<String> footers;

        protected SheetTextAsHTML(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
            headers = new ArrayList<String>();
            footers = new ArrayList<String>();
        }

        public void startRow(int rowNum) {
            try {
                xhtml.startElement("tr");
            } catch (SAXException e) {
            }
        }

        public void endRow(int rowNum) {
            try {
                xhtml.endElement("tr");
            } catch (SAXException e) {
            }
        }

        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
            try {
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
            }
        }

        public void headerFooter(String text, boolean isHeader, String tagName) {
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
    protected static class XSSFSheetInterestingPartsCapturer implements ContentHandler {
        private ContentHandler delegate;
        private boolean hasProtection = false;

        protected XSSFSheetInterestingPartsCapturer(ContentHandler delegate) {
            this.delegate = delegate;
        }

        public void startElement(String uri, String localName, String qName,
                                 Attributes atts) throws SAXException {
            if ("sheetProtection".equals(qName)) {
                hasProtection = true;
            }
            delegate.startElement(uri, localName, qName, atts);
        }

        public void characters(char[] ch, int start, int length)
                throws SAXException {
            delegate.characters(ch, start, length);
        }

        public void endDocument() throws SAXException {
            delegate.endDocument();
        }

        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            delegate.endElement(uri, localName, qName);
        }

        public void endPrefixMapping(String prefix) throws SAXException {
            delegate.endPrefixMapping(prefix);
        }

        public void ignorableWhitespace(char[] ch, int start, int length)
                throws SAXException {
            delegate.ignorableWhitespace(ch, start, length);
        }

        public void processingInstruction(String target, String data)
                throws SAXException {
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

        public void startPrefixMapping(String prefix, String uri)
                throws SAXException {
            delegate.startPrefixMapping(prefix, uri);
        }
    }
}
