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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.Comments;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.Styles;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This is a temporary work around for POI 5.1.0: https://bz.apache.org/bugzilla/show_bug.cgi?id=65676
 */
public class TikaXSSFSheetXMLHandler extends DefaultHandler {
    private static final Logger LOG = LogManager.getLogger(TikaXSSFSheetXMLHandler.class);
    private Styles stylesTable;
    private Comments comments;
    private SharedStrings sharedStringsTable;
    private final XSSFSheetXMLHandler.SheetContentsHandler sheetContentsHandler;
    private boolean vIsOpen;
    private boolean fIsOpen;
    private boolean isIsOpen;
    private boolean hfIsOpen;
    private xssfDataType nextDataType;
    private short formatIndex;
    private String formatString;
    private final DataFormatter formatter;
    private int rowNum;
    private int nextRowNum;
    private String cellRef;
    private boolean formulasNotResults;
    private StringBuilder value;
    private StringBuilder formula;
    private StringBuilder headerFooter;
    private Queue<CellAddress> commentCellRefs;

    public TikaXSSFSheetXMLHandler(Styles styles, Comments comments, SharedStrings strings,
                                   XSSFSheetXMLHandler.SheetContentsHandler sheetContentsHandler,
                                   DataFormatter dataFormatter, boolean formulasNotResults) {
        this.value = new StringBuilder(64);
        this.formula = new StringBuilder(64);
        this.headerFooter = new StringBuilder(64);
        this.stylesTable = styles;
        this.comments = comments;
        this.sharedStringsTable = strings;
        this.sheetContentsHandler = sheetContentsHandler;
        this.formulasNotResults = formulasNotResults;
        this.nextDataType = xssfDataType.NUMBER;
        this.formatter = dataFormatter;
        this.init(comments);
    }

    public TikaXSSFSheetXMLHandler(Styles styles, SharedStrings strings,
                                   XSSFSheetXMLHandler.SheetContentsHandler sheetContentsHandler,
                                   DataFormatter dataFormatter, boolean formulasNotResults) {
        this(styles, (Comments) null, strings, sheetContentsHandler, dataFormatter,
                formulasNotResults);
    }

    public TikaXSSFSheetXMLHandler(Styles styles, SharedStrings strings,
                                   XSSFSheetXMLHandler.SheetContentsHandler sheetContentsHandler,
                                   boolean formulasNotResults) {
        this(styles, strings, sheetContentsHandler, new DataFormatter(), formulasNotResults);
    }

    private void init(Comments commentsTable) {
        if (commentsTable != null) {
            this.commentCellRefs = new LinkedList();
            Iterator<CellAddress> iter = commentsTable.getCellAddresses();

            while (iter.hasNext()) {
                this.commentCellRefs.add(iter.next());
            }
        }
    }

    private boolean isTextTag(String name) {
        if ("v".equals(name)) {
            return true;
        } else if ("inlineStr".equals(name)) {
            return true;
        } else {
            return "t".equals(name) && this.isIsOpen;
        }
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (uri == null ||
                uri.equals("http://schemas.openxmlformats.org/spreadsheetml/2006/main")) {
            if (this.isTextTag(localName)) {
                this.vIsOpen = true;
                if (!this.isIsOpen) {
                    this.value.setLength(0);
                }
            } else if ("is".equals(localName)) {
                this.isIsOpen = true;
            } else {
                String cellType;
                String cellStyleStr;
                if ("f".equals(localName)) {
                    this.formula.setLength(0);
                    if (this.nextDataType == xssfDataType.NUMBER) {
                        this.nextDataType = xssfDataType.FORMULA;
                    }

                    cellType = attributes.getValue("t");
                    if (cellType != null && cellType.equals("shared")) {
                        cellStyleStr = attributes.getValue("ref");
                        String si = attributes.getValue("si");
                        if (cellStyleStr != null) {
                            this.fIsOpen = true;
                        } else if (this.formulasNotResults) {
                            LOG.atWarn().log("shared formulas not yet supported!");
                        }
                    } else {
                        this.fIsOpen = true;
                    }
                } else if (!"oddHeader".equals(localName) && !"evenHeader".equals(localName) &&
                        !"firstHeader".equals(localName) && !"firstFooter".equals(localName) &&
                        !"oddFooter".equals(localName) && !"evenFooter".equals(localName)) {
                    if ("row".equals(localName)) {
                        cellType = attributes.getValue("r");
                        if (cellType != null) {
                            this.rowNum = Integer.parseInt(cellType) - 1;
                        } else {
                            this.rowNum = this.nextRowNum;
                        }

                        this.sheetContentsHandler.startRow(this.rowNum);
                    } else if ("c".equals(localName)) {
                        this.nextDataType = xssfDataType.NUMBER;
                        this.formatIndex = -1;
                        this.formatString = null;
                        this.cellRef = attributes.getValue("r");
                        cellType = attributes.getValue("t");
                        cellStyleStr = attributes.getValue("s");
                        if ("b".equals(cellType)) {
                            this.nextDataType = xssfDataType.BOOLEAN;
                        } else if ("e".equals(cellType)) {
                            this.nextDataType = xssfDataType.ERROR;
                        } else if ("inlineStr".equals(cellType)) {
                            this.nextDataType = xssfDataType.INLINE_STRING;
                        } else if ("s".equals(cellType)) {
                            this.nextDataType = xssfDataType.SST_STRING;
                        } else if ("str".equals(cellType)) {
                            this.nextDataType = xssfDataType.FORMULA;
                        } else {
                            XSSFCellStyle style = null;
                            if (this.stylesTable != null) {
                                if (cellStyleStr != null) {
                                    int styleIndex = Integer.parseInt(cellStyleStr);
                                    style = this.stylesTable.getStyleAt(styleIndex);
                                } else if (this.stylesTable.getNumCellStyles() > 0) {
                                    style = this.stylesTable.getStyleAt(0);
                                }
                            }

                            if (style != null) {
                                this.formatIndex = style.getDataFormat();
                                this.formatString = style.getDataFormatString();
                                if (this.formatString == null) {
                                    this.formatString =
                                            BuiltinFormats.getBuiltinFormat(this.formatIndex);
                                }
                            }
                        }
                    }
                } else {
                    this.hfIsOpen = true;
                    this.headerFooter.setLength(0);
                }
            }

        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (uri == null ||
                uri.equals("http://schemas.openxmlformats.org/spreadsheetml/2006/main")) {
            if (this.isTextTag(localName)) {
                this.vIsOpen = false;
                if (!this.isIsOpen) {
                    this.outputCell();
                }
            } else if ("f".equals(localName)) {
                this.fIsOpen = false;
            } else if ("is".equals(localName)) {
                this.isIsOpen = false;
                this.outputCell();
                this.value.setLength(0);
            } else if ("row".equals(localName)) {
                this.checkForEmptyCellComments(EmptyCellCommentsCheckType.END_OF_ROW);
                this.sheetContentsHandler.endRow(this.rowNum);
                this.nextRowNum = this.rowNum + 1;
            } else if ("sheetData".equals(localName)) {
                this.checkForEmptyCellComments(EmptyCellCommentsCheckType.END_OF_SHEET_DATA);
                this.sheetContentsHandler.endSheet();
            } else if (!"oddHeader".equals(localName) && !"evenHeader".equals(localName) &&
                    !"firstHeader".equals(localName)) {
                if ("oddFooter".equals(localName) || "evenFooter".equals(localName) ||
                        "firstFooter".equals(localName)) {
                    this.hfIsOpen = false;
                    this.sheetContentsHandler.headerFooter(this.headerFooter.toString(), false,
                            localName);
                }
            } else {
                this.hfIsOpen = false;
                this.sheetContentsHandler.headerFooter(this.headerFooter.toString(), true,
                        localName);
            }

        }
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        if (this.vIsOpen) {
            this.value.append(ch, start, length);
        }

        if (this.fIsOpen) {
            this.formula.append(ch, start, length);
        }

        if (this.hfIsOpen) {
            this.headerFooter.append(ch, start, length);
        }

    }

    private void outputCell() {
        String thisStr = null;
        switch (this.nextDataType) {
            case BOOLEAN:
                char first = this.value.charAt(0);
                thisStr = first == '0' ? "FALSE" : "TRUE";
                break;
            case ERROR:
                thisStr = "ERROR:" + this.value;
                break;
            case FORMULA:
                if (this.formulasNotResults) {
                    thisStr = this.formula.toString();
                } else {
                    String fv = this.value.toString();
                    if (this.formatString != null) {
                        try {
                            double d = Double.parseDouble(fv);
                            thisStr = this.formatter.formatRawCellContents(d, this.formatIndex,
                                    this.formatString);
                        } catch (NumberFormatException var8) {
                            thisStr = fv;
                        }
                    } else {
                        thisStr = fv;
                    }
                }
                break;
            case INLINE_STRING:
                XSSFRichTextString rtsi = new XSSFRichTextString(this.value.toString());
                thisStr = rtsi.toString();
                break;
            case SST_STRING:
                String sstIndex = this.value.toString();

                try {
                    int idx = Integer.parseInt(sstIndex);
                    RichTextString rtss = this.sharedStringsTable.getItemAt(idx);
                    thisStr = rtss.toString();
                } catch (NumberFormatException var7) {
                    LOG.atError().withThrowable(var7)
                            .log("Failed to parse SST index '{}'", sstIndex);
                }
                break;
            case NUMBER:
                String n = this.value.toString();
                if (this.formatString != null && n.length() > 0) {
                    thisStr = this.formatter.formatRawCellContents(Double.parseDouble(n),
                            this.formatIndex, this.formatString);
                } else {
                    thisStr = n;
                }
                break;
            default:
                thisStr = "(TODO: Unexpected type: " + this.nextDataType + ")";
        }

        this.checkForEmptyCellComments(EmptyCellCommentsCheckType.CELL);
        XSSFComment comment = this.comments != null ?
                this.comments.findCellComment(new CellAddress(this.cellRef)) : null;
        this.sheetContentsHandler.cell(this.cellRef, thisStr, comment);
        this.value.setLength(0);
    }

    private void checkForEmptyCellComments(EmptyCellCommentsCheckType type) {
        if (this.commentCellRefs != null && !this.commentCellRefs.isEmpty()) {
            if (type == EmptyCellCommentsCheckType.END_OF_SHEET_DATA) {
                while (!this.commentCellRefs.isEmpty()) {
                    this.outputEmptyCellComment((CellAddress) this.commentCellRefs.remove());
                }

                return;
            }

            if (this.cellRef == null) {
                if (type == EmptyCellCommentsCheckType.END_OF_ROW) {
                    while (!this.commentCellRefs.isEmpty()) {
                        if (((CellAddress) this.commentCellRefs.peek()).getRow() != this.rowNum) {
                            return;
                        }

                        this.outputEmptyCellComment((CellAddress) this.commentCellRefs.remove());
                    }

                    return;
                }

                throw new IllegalStateException(
                        "Cell ref should be null only if there are only empty cells in the row; rowNum: " +
                                this.rowNum);
            }

            CellAddress nextCommentCellRef;
            do {
                CellAddress cellRef = new CellAddress(this.cellRef);
                CellAddress peekCellRef = (CellAddress) this.commentCellRefs.peek();
                if (type == EmptyCellCommentsCheckType.CELL && cellRef.equals(peekCellRef)) {
                    this.commentCellRefs.remove();
                    return;
                }

                int comparison = peekCellRef.compareTo(cellRef);
                if (comparison > 0 && type == EmptyCellCommentsCheckType.END_OF_ROW &&
                        peekCellRef.getRow() <= this.rowNum) {
                    nextCommentCellRef = (CellAddress) this.commentCellRefs.remove();
                    this.outputEmptyCellComment(nextCommentCellRef);
                } else if (comparison < 0 && type == EmptyCellCommentsCheckType.CELL &&
                        peekCellRef.getRow() <= this.rowNum) {
                    nextCommentCellRef = (CellAddress) this.commentCellRefs.remove();
                    this.outputEmptyCellComment(nextCommentCellRef);
                } else {
                    nextCommentCellRef = null;
                }
            } while (nextCommentCellRef != null && !this.commentCellRefs.isEmpty());
        }

    }

    private void outputEmptyCellComment(CellAddress cellRef) {
        XSSFComment comment = this.comments.findCellComment(cellRef);
        this.sheetContentsHandler.cell(cellRef.formatAsString(), (String) null, comment);
    }

    public interface SheetContentsHandler {
        void startRow(int var1);

        void endRow(int var1);

        void cell(String var1, String var2, XSSFComment var3);

        default void headerFooter(String text, boolean isHeader, String tagName) {
        }

        default void endSheet() {
        }
    }

    private static enum EmptyCellCommentsCheckType {
        CELL, END_OF_ROW, END_OF_SHEET_DATA;

        private EmptyCellCommentsCheckType() {
        }
    }

    static enum xssfDataType {
        BOOLEAN, ERROR, FORMULA, INLINE_STRING, SST_STRING, NUMBER;

        private xssfDataType() {
        }
    }
}

