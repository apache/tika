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
import java.util.Locale;

import org.apache.poi.POIXMLTextExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xssf.binary.XSSFBCommentsTable;
import org.apache.poi.xssf.binary.XSSFBSharedStringsTable;
import org.apache.poi.xssf.binary.XSSFBSheetHandler;
import org.apache.poi.xssf.binary.XSSFBStylesTable;
import org.apache.poi.xssf.eventusermodel.XSSFBReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.extractor.XSSFBEventBasedExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSimpleShape;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.drawingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTShape;
import org.openxmlformats.schemas.drawingml.x2006.spreadsheetDrawing.CTShapeNonVisual;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class XSSFBExcelExtractorDecorator extends XSSFExcelExtractorDecorator {

    public XSSFBExcelExtractorDecorator(
            ParseContext context, POIXMLTextExtractor extractor, Locale locale) {
        super(context, extractor, locale);
    }

    @Override
    protected void configureExtractor(POIXMLTextExtractor extractor, Locale locale) {
        //need to override this because setFormulasNotResults is not yet available
        //for xlsb
       //((XSSFBEventBasedExcelExtractor)extractor).setFormulasNotResults(false);
        ((XSSFBEventBasedExcelExtractor)extractor).setLocale(locale);
    }

    @Override
    public void getXHTML(
            ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, XmlException, IOException, TikaException {

        this.metadata = metadata;
        this.parseContext = context;
        metadata.set(TikaMetadataKeys.PROTECTED, "false");

        super.getXHTML(handler, metadata, context);
    }

    /**
     * @see org.apache.poi.xssf.extractor.XSSFBEventBasedExcelExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        OPCPackage container = extractor.getPackage();

        XSSFBSharedStringsTable strings;
        XSSFBReader.SheetIterator iter;
        XSSFBReader xssfReader;
        XSSFBStylesTable styles;
        try {
            xssfReader = new XSSFBReader(container);
            String originalPath = xssfReader.getAbsPathMetadata();
            if (originalPath != null) {
                metadata.set(TikaCoreProperties.ORIGINAL_RESOURCE_NAME, originalPath);
            }
            styles = xssfReader.getXSSFBStylesTable();
            iter = (XSSFBReader.SheetIterator) xssfReader.getSheetsData();
            strings = new XSSFBSharedStringsTable(container);
        } catch (InvalidFormatException e) {
            throw new XmlException(e);
        } catch (OpenXML4JException oe) {
            throw new XmlException(oe);
        }

        while (iter.hasNext()) {
            InputStream stream = iter.next();
            PackagePart sheetPart = iter.getSheetPart();
            addDrawingHyperLinks(sheetPart);
            sheetParts.add(sheetPart);

            SheetTextAsHTML sheetExtractor = new SheetTextAsHTML(config.getIncludeHeadersAndFooters(), xhtml);
            XSSFBCommentsTable comments = iter.getXSSFBSheetComments();

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
            List<XSSFShape> shapes = iter.getShapes();

            processShapes(shapes, xhtml);

            //for now dump sheet hyperlinks at bottom of page
            //consider a double-pass of the inputstream to reunite hyperlinks with cells/textboxes
            //step 1: extract hyperlink info from bottom of page
            //step 2: process as we do now, but with cached hyperlink relationship info
            extractHyperLinks(sheetPart, xhtml);
            // All done with this sheet
            xhtml.endElement("div");
        }
    }


    @Override
    protected void extractHeaderFooter(String hf, XHTMLContentHandler xhtml)
            throws SAXException {
        if (hf.length() > 0) {
            xhtml.element("p", hf);
        }
    }

    private void extractHyperLinks(PackagePart sheetPart, XHTMLContentHandler xhtml) throws SAXException {
        try {
            for (PackageRelationship rel : sheetPart.getRelationshipsByType(XSSFRelation.SHEET_HYPERLINKS.getRelation())) {
                xhtml.startElement("a", "href", rel.getTargetURI().toString());
                xhtml.characters(rel.getTargetURI().toString());
                xhtml.endElement("a");
            }
        } catch (InvalidFormatException e) {
            //swallow
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
                extractHyperLinksFromShape(((XSSFSimpleShape)shape).getCTShape(), xhtml);
            }
            XSSFDrawing drawing = shape.getDrawing();
            if (drawing != null) {
                //dump diagram data
                handleGeneralTextContainingPart(
                        AbstractOOXMLExtractor.RELATION_DIAGRAM_DATA,
                        "diagram-data",
                        drawing.getPackagePart(),
                        metadata,
                        new OOXMLWordAndPowerPointTextHandler(
                                new OOXMLTikaBodyPartHandler(xhtml),
                                new HashMap<String, String>()//empty
                        )
                );
                //dump chart data
                handleGeneralTextContainingPart(
                        XSSFRelation.CHART.getRelation(),
                        "chart",
                        drawing.getPackagePart(),
                        metadata,
                        new OOXMLWordAndPowerPointTextHandler(
                                new OOXMLTikaBodyPartHandler(xhtml),
                                new HashMap<String, String>()//empty
                        )
                );
            }
        }
    }

    private void extractHyperLinksFromShape(CTShape ctShape, XHTMLContentHandler xhtml) throws SAXException {

        if (ctShape == null)
            return;

        CTShapeNonVisual nvSpPR = ctShape.getNvSpPr();
        if (nvSpPR == null)
            return;

        CTNonVisualDrawingProps cNvPr = nvSpPR.getCNvPr();
        if (cNvPr == null)
            return;

        CTHyperlink ctHyperlink = cNvPr.getHlinkClick();
        if (ctHyperlink == null)
            return;

        String url = drawingHyperlinks.get(ctHyperlink.getId());
        if (url != null) {
            xhtml.startElement("a", "href", url);
            xhtml.characters(url);
            xhtml.endElement("a");
        }

        CTHyperlink ctHoverHyperlink = cNvPr.getHlinkHover();
        if (ctHoverHyperlink == null)
            return;

        url = drawingHyperlinks.get(ctHoverHyperlink.getId());
        if (url != null) {
            xhtml.startElement("a", "href", url);
            xhtml.characters(url);
            xhtml.endElement("a");
        }

    }

    private void processSheet(
            SheetContentsHandler sheetContentsExtractor,
            XSSFBCommentsTable comments,
            XSSFBStylesTable styles,
            XSSFBSharedStringsTable strings,
            InputStream sheetInputStream)
            throws IOException, SAXException {

        XSSFBSheetHandler xssfbSheetHandler = new XSSFBSheetHandler(
                sheetInputStream,
                styles,
                comments,
                strings,
                sheetContentsExtractor,
                formatter,
                false
        );
        xssfbSheetHandler.parse();
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

        //add main document so that macros can be extracted
        //by AbstractOOXMLExtractor
        for (PackagePart part : extractor.getPackage().
                getPartsByRelationshipType(PackageRelationshipTypes.CORE_DOCUMENT)) {
            parts.add(part);
        }

        return parts;
    }
}