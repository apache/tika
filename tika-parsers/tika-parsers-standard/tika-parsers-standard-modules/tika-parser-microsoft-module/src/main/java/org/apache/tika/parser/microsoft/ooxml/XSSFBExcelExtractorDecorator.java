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
import java.util.Locale;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipCollection;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.xssf.binary.XSSFBSheetHandler;
import org.apache.poi.xssf.binary.XSSFBStylesTable;
import org.apache.poi.xssf.eventusermodel.XSSFBReader;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.ExceptionUtils;

public class XSSFBExcelExtractorDecorator extends XSSFExcelExtractorDecorator {

    public XSSFBExcelExtractorDecorator(ParseContext context, OPCPackage pkg,
                                        Locale locale) {
        super(context, pkg, locale);
    }

    @Override
    public void getXHTML(ContentHandler handler, Metadata metadata, ParseContext context)
            throws SAXException, IOException, TikaException {

        this.metadata = metadata;
        this.parseContext = context;
        metadata.set(Office.PROTECTED_WORKSHEET, false);

        super.getXHTML(handler, metadata, context);
    }

    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml)
            throws SAXException, IOException {
        OPCPackage container = opcPackage;

        TikaXSSFBSharedStringsTable strings;
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
        } catch (OpenXML4JException e) {
            throw new IOException(e);
        }
        // Shared strings are optional -- a corrupt/truncated sharedStrings.bin degrades to an
        // empty table (getItemAt then returns "") rather than aborting the workbook.
        try {
            strings = new TikaXSSFBSharedStringsTable(container);
        } catch (IOException | RuntimeException e) {
            metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                    ExceptionUtils.getStackTrace(e));
            strings = new TikaXSSFBSharedStringsTable();
        }

        while (true) {
            try {
                if (!iter.hasNext()) {
                    break;
                }
            } catch (RuntimeException e) {
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                break;
            }
            InputStream stream;
            PackagePart sheetPart;
            try {
                stream = iter.next();
                sheetPart = iter.getSheetPart();
            } catch (RuntimeException e) {
                // POI throws POIXMLException when a referenced sheet part is missing; its
                // iterator state may not advance, so break rather than risk an infinite loop.
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                break;
            }
            addDrawingHyperLinks(sheetPart);
            sheetParts.add(sheetPart);

            SheetTextAsHTML sheetExtractor = new SheetTextAsHTML(config, xhtml);

            // Parse comments with our own binary parser that avoids xmlbeans
            TikaXSSFBCommentsTable tikaComments = parseBinaryComments(sheetPart);
            if (tikaComments != null && tikaComments.hasComments()) {
                metadata.set(Office.HAS_COMMENTS, true);
            }

            xhtml.startElement("div");
            xhtml.element("h1", iter.getSheetName());

            xhtml.startElement("table");
            xhtml.startElement("tbody");

            // Pass null for POI's comments table to avoid xmlbeans dependency.
            // Comments are emitted separately after sheet processing.
            try (InputStream sheetStream = stream) {
                XSSFBSheetHandler xssfbSheetHandler =
                        new XSSFBSheetHandler(sheetStream, styles, null, strings,
                                sheetExtractor, formatter, false);
                xssfbSheetHandler.parse();
            } catch (RuntimeException | IOException e) {
                // Truncated/malformed binary sheet -- keep prior sheets and close any open
                // row/cell. A wrapped write-limit signal must still propagate.
                WriteLimitReachedException.throwIfWriteLimitReached(e);
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                        ExceptionUtils.getStackTrace(e));
                sheetExtractor.closeAnyPending();
            }

            xhtml.endElement("tbody");
            xhtml.endElement("table");

            // Emit comments after the table (since we bypass POI's inline
            // comment handling to avoid xmlbeans dependency)
            if (tikaComments != null) {
                tikaComments.emitAllComments(xhtml);
            }

            for (String header : sheetExtractor.headers) {
                extractHeaderFooter(header, xhtml);
            }
            for (String footer : sheetExtractor.footers) {
                extractHeaderFooter(footer, xhtml);
            }
            processDrawings(sheetPart, xhtml);
            extractHyperLinks(sheetPart, xhtml);
            xhtml.endElement("div");
        }
    }

    private static final String RELATION_COMMENTS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments";

    private TikaXSSFBCommentsTable parseBinaryComments(PackagePart sheetPart) {
        try {
            PackageRelationshipCollection rels =
                    sheetPart.getRelationshipsByType(RELATION_COMMENTS);
            if (rels.isEmpty()) {
                return null;
            }
            PackageRelationship rel = rels.getRelationship(0);
            PackagePartName partName =
                    PackagingURIHelper.createPartName(rel.getTargetURI());
            PackagePart commentsPart = rel.getPackage().getPart(partName);
            if (commentsPart == null) {
                return null;
            }
            try (InputStream is = commentsPart.getInputStream()) {
                return new TikaXSSFBCommentsTable(is);
            }
        } catch (InvalidFormatException | IOException | RuntimeException e) {
            //missing/corrupt comments part must not abort the sheet
            return null;
        }
    }

    @Override
    protected void extractHeaderFooter(String hf, XHTMLContentHandler xhtml) throws SAXException {
        if (hf.length() > 0) {
            xhtml.element("p", hf);
        }
    }
}
