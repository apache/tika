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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.poi.hssf.extractor.ExcelExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HeaderFooter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.xmlbeans.XmlException;
import org.xml.sax.SAXException;

public class XSSFExcelExtractorDecorator extends AbstractOOXMLExtractor {

    /**
     * Internal <code>DataFormatter</code> for formatting Numbers.
     */
    private final DataFormatter formatter;

    private final XSSFExcelExtractor extractor;
    private static final String TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public XSSFExcelExtractorDecorator(
            ParseContext context, XSSFExcelExtractor extractor, Locale locale) {
        super(context, extractor, TYPE);

        this.extractor = extractor;
        formatter = new DataFormatter(locale);
    }

    /**
     * @see org.apache.poi.xssf.extractor.XSSFExcelExtractor#getText()
     */
    @Override
    protected void buildXHTML(XHTMLContentHandler xhtml) throws SAXException,
            XmlException, IOException {
        XSSFWorkbook document = (XSSFWorkbook) extractor.getDocument();

        for (int i = 0; i < document.getNumberOfSheets(); i++) {
            xhtml.startElement("div");
            XSSFSheet sheet = (XSSFSheet) document.getSheetAt(i);
            xhtml.element("h1", document.getSheetName(i));

            // Header(s), if present
            extractHeaderFooter(sheet.getFirstHeader(), xhtml);
            extractHeaderFooter(sheet.getOddHeader(), xhtml);
            extractHeaderFooter(sheet.getEvenHeader(), xhtml);

            xhtml.startElement("table");
            xhtml.startElement("tbody");

            // Rows and cells
            for (Object rawR : sheet) {
                xhtml.startElement("tr");
                Row row = (Row) rawR;
                for (Iterator<Cell> ri = row.cellIterator(); ri.hasNext();) {
                    xhtml.startElement("td");
                    Cell cell = ri.next();

                    int type = cell.getCellType();
                    if (type == Cell.CELL_TYPE_FORMULA) {
                        type = cell.getCachedFormulaResultType();
                    }
                    if (type == Cell.CELL_TYPE_STRING) {
                        xhtml.characters(cell.getRichStringCellValue()
                                .getString());
                    } else if (type == Cell.CELL_TYPE_NUMERIC) {
                        CellStyle style = cell.getCellStyle();
                        xhtml.characters(
                            formatter.formatRawCellContents(cell.getNumericCellValue(),
                                                            style.getDataFormat(),
                                                            style.getDataFormatString()));
                    } else {
                        XSSFCell xc = (XSSFCell) cell;
                        String rawValue = xc.getRawValue();
                        if (rawValue != null) {
                            xhtml.characters(rawValue);
                        }

                    }

                    // Output the comment in the same cell as the content
                    Comment comment = cell.getCellComment();
                    if (comment != null) {
                        xhtml.characters(comment.getString().getString());
                    }

                    xhtml.endElement("td");
                }
                xhtml.endElement("tr");
            }

            xhtml.endElement("tbody");
            xhtml.endElement("table");

            // Finally footer(s), if present
            extractHeaderFooter(sheet.getFirstFooter(), xhtml);
            extractHeaderFooter(sheet.getOddFooter(), xhtml);
            extractHeaderFooter(sheet.getEvenFooter(), xhtml);

            xhtml.endElement("div");
        }
    }

    private void extractHeaderFooter(HeaderFooter hf, XHTMLContentHandler xhtml)
            throws SAXException {
        String content = ExcelExtractor._extractHeaderFooter(hf);
        if (content.length() > 0) {
            xhtml.element("p", content);
        }
    }
    
    /**
     * In Excel files, sheets have things embedded in them,
     *  and sheet drawings which have the images
     */
    @Override
    protected List<PackagePart> getMainDocumentParts() throws TikaException {
       List<PackagePart> parts = new ArrayList<PackagePart>();
       XSSFWorkbook document = (XSSFWorkbook) extractor.getDocument();
       for(XSSFSheet sheet : document) {
          PackagePart part = sheet.getPackagePart();
          
          // Add the sheet
          parts.add(part);
          
          // If it has drawings, return those too
          try {
             for(PackageRelationship rel : part.getRelationshipsByType(XSSFRelation.DRAWINGS.getRelation())) {
                if(rel.getTargetMode() == TargetMode.INTERNAL) {
                   PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                   parts.add( rel.getPackage().getPart(relName) );
                }
             }
             for(PackageRelationship rel : part.getRelationshipsByType(XSSFRelation.VML_DRAWINGS.getRelation())) {
                if(rel.getTargetMode() == TargetMode.INTERNAL) {
                   PackagePartName relName = PackagingURIHelper.createPartName(rel.getTargetURI());
                   parts.add( rel.getPackage().getPart(relName) );
                }
             }
          } catch(InvalidFormatException e) {
             throw new TikaException("Broken OOXML file", e);
          }
       }

       return parts;
    }

    @Override
    public MetadataExtractor getMetadataExtractor() {
        return new MetadataExtractor(extractor, TYPE) {
            @Override
            public void extract(Metadata metadata) throws TikaException {
                super.extract(metadata);

                metadata.set(TikaMetadataKeys.PROTECTED, "false");

                XSSFWorkbook document = (XSSFWorkbook) extractor.getDocument();

                for (int i = 0; i < document.getNumberOfSheets(); i++) {
                    XSSFSheet sheet = document.getSheetAt(i);

                    if (sheet.getProtect()) {
                        metadata.set(TikaMetadataKeys.PROTECTED, "true");
                    }
                }
            }
        };
    }
}
