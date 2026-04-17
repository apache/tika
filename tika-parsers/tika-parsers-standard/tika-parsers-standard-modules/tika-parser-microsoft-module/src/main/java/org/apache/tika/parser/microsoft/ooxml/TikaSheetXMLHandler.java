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

import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Sheet XML handler for XLSX event-based parsing that uses {@link XSSFStylesShim}
 * and {@link XSSFCommentsShim} instead of POI's XMLBeans-dependent
 * {@code StylesTable} and {@code CommentsTable}.
 * <p>
 * Adapted from Apache POI's {@code XSSFSheetXMLHandler} (Apache 2.0 license).
 */
class TikaSheetXMLHandler extends DefaultHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TikaSheetXMLHandler.class);

    private static final String NS_SPREADSHEETML =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    enum XssfDataType {
        BOOLEAN,
        ERROR,
        FORMULA,
        INLINE_STRING,
        SST_STRING,
        NUMBER,
    }

    private final XSSFStylesShim stylesShim;
    private final XSSFCommentsShim commentsShim;
    private final XSSFSharedStringsShim sharedStringsShim;
    private final TikaSheetContentsHandler output;
    private final DataFormatter formatter;
    private final boolean formulasNotResults;

    private boolean vIsOpen;
    private boolean fIsOpen;
    private boolean isIsOpen;
    private boolean hfIsOpen;

    private XssfDataType nextDataType;
    private short formatIndex;
    private String formatString;

    private int rowNum;
    private int nextRowNum;
    private String cellRef;

    private final StringBuilder value = new StringBuilder(64);
    private final StringBuilder formula = new StringBuilder(64);
    private final StringBuilder headerFooter = new StringBuilder(64);

    private Queue<CellAddress> commentCellRefs;

    TikaSheetXMLHandler(XSSFStylesShim stylesShim,
                         XSSFCommentsShim commentsShim,
                         XSSFSharedStringsShim sharedStringsShim,
                         TikaSheetContentsHandler sheetContentsHandler,
                         DataFormatter dataFormatter,
                         boolean formulasNotResults) {
        this.stylesShim = stylesShim;
        this.commentsShim = commentsShim;
        this.sharedStringsShim = sharedStringsShim;
        this.output = sheetContentsHandler;
        this.formatter = dataFormatter;
        this.formulasNotResults = formulasNotResults;
        this.nextDataType = XssfDataType.NUMBER;
        initComments(commentsShim);
    }

    TikaSheetXMLHandler(XSSFStylesShim stylesShim,
                         XSSFSharedStringsShim sharedStringsShim,
                         TikaSheetContentsHandler sheetContentsHandler,
                         DataFormatter dataFormatter,
                         boolean formulasNotResults) {
        this(stylesShim, null, sharedStringsShim, sheetContentsHandler, dataFormatter,
                formulasNotResults);
    }

    private void initComments(XSSFCommentsShim commentsShim) {
        if (commentsShim != null) {
            commentCellRefs = new LinkedList<>();
            for (Iterator<CellAddress> iter = commentsShim.getCellAddresses();
                 iter.hasNext(); ) {
                commentCellRefs.add(iter.next());
            }
        }
    }

    private boolean isTextTag(String name) {
        if ("v".equals(name)) {
            return true;
        }
        if ("inlineStr".equals(name)) {
            return true;
        }
        return "t".equals(name) && isIsOpen;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if (uri != null && !uri.equals(NS_SPREADSHEETML)) {
            return;
        }

        if (isTextTag(localName)) {
            vIsOpen = true;
            if (!isIsOpen) {
                value.setLength(0);
            }
        } else if ("is".equals(localName)) {
            isIsOpen = true;
        } else if ("f".equals(localName)) {
            formula.setLength(0);
            if (this.nextDataType == XssfDataType.NUMBER) {
                this.nextDataType = XssfDataType.FORMULA;
            }
            String type = attributes.getValue("t");
            if (type != null && type.equals("shared")) {
                String ref = attributes.getValue("ref");
                if (ref != null) {
                    fIsOpen = true;
                } else {
                    if (formulasNotResults) {
                        LOG.warn("shared formulas not yet supported!");
                    }
                }
            } else {
                fIsOpen = true;
            }
        } else if ("oddHeader".equals(localName) || "evenHeader".equals(localName) ||
                "firstHeader".equals(localName) || "firstFooter".equals(localName) ||
                "oddFooter".equals(localName) || "evenFooter".equals(localName)) {
            hfIsOpen = true;
            headerFooter.setLength(0);
        } else if ("row".equals(localName)) {
            String rowNumStr = attributes.getValue("r");
            if (rowNumStr != null) {
                rowNum = Integer.parseInt(rowNumStr.trim()) - 1;
            } else {
                rowNum = nextRowNum;
            }
            output.startRow(rowNum);
        } else if ("c".equals(localName)) {
            // Cell element — resolve style to format index/string
            this.formula.setLength(0);
            this.nextDataType = XssfDataType.NUMBER;
            this.formatIndex = -1;
            this.formatString = null;
            cellRef = attributes.getValue("r");
            String cellType = attributes.getValue("t");
            String cellStyleStr = attributes.getValue("s");

            if ("b".equals(cellType)) {
                nextDataType = XssfDataType.BOOLEAN;
            } else if ("e".equals(cellType)) {
                nextDataType = XssfDataType.ERROR;
            } else if ("inlineStr".equals(cellType)) {
                nextDataType = XssfDataType.INLINE_STRING;
            } else if ("s".equals(cellType)) {
                nextDataType = XssfDataType.SST_STRING;
            } else if ("str".equals(cellType)) {
                nextDataType = XssfDataType.FORMULA;
            } else {
                // Number — resolve format via our styles shim
                if (stylesShim != null) {
                    int styleIndex;
                    if (cellStyleStr != null) {
                        styleIndex = Integer.parseInt(cellStyleStr.trim());
                    } else if (stylesShim.getNumCellStyles() > 0) {
                        styleIndex = 0;
                    } else {
                        styleIndex = -1;
                    }
                    if (styleIndex >= 0) {
                        this.formatIndex = stylesShim.getFormatIndex(styleIndex);
                        this.formatString = stylesShim.getFormatString(styleIndex);
                        if (this.formatString == null) {
                            this.formatString =
                                    BuiltinFormats.getBuiltinFormat(this.formatIndex);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (uri != null && !uri.equals(NS_SPREADSHEETML)) {
            return;
        }

        if (isTextTag(localName)) {
            vIsOpen = false;
            if (!isIsOpen) {
                outputCell();
                value.setLength(0);
            }
        } else if ("f".equals(localName)) {
            fIsOpen = false;
        } else if ("is".equals(localName)) {
            isIsOpen = false;
            outputCell();
            value.setLength(0);
        } else if ("row".equals(localName)) {
            checkForEmptyCellComments(EmptyCellCommentsCheckType.END_OF_ROW);
            output.endRow(rowNum);
            nextRowNum = rowNum + 1;
        } else if ("sheetData".equals(localName)) {
            checkForEmptyCellComments(EmptyCellCommentsCheckType.END_OF_SHEET_DATA);
            output.endSheet();
        } else if ("oddHeader".equals(localName) || "evenHeader".equals(localName) ||
                "firstHeader".equals(localName)) {
            hfIsOpen = false;
            output.headerFooter(headerFooter.toString(), true, localName);
        } else if ("oddFooter".equals(localName) || "evenFooter".equals(localName) ||
                "firstFooter".equals(localName)) {
            hfIsOpen = false;
            output.headerFooter(headerFooter.toString(), false, localName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (vIsOpen) {
            value.append(ch, start, length);
        }
        if (fIsOpen) {
            formula.append(ch, start, length);
        }
        if (hfIsOpen) {
            headerFooter.append(ch, start, length);
        }
    }

    private void outputCell() {
        String thisStr = null;

        if (formulasNotResults && formula.length() > 0) {
            thisStr = formula.toString();
        } else {
            switch (nextDataType) {
                case BOOLEAN:
                    char first = value.charAt(0);
                    thisStr = first == '0' ? "FALSE" : "TRUE";
                    break;
                case ERROR:
                    thisStr = "ERROR:" + value;
                    break;
                case FORMULA:
                    if (formulasNotResults) {
                        thisStr = formula.toString();
                    } else {
                        String fv = value.toString();
                        if (this.formatString != null) {
                            try {
                                double d = Double.parseDouble(fv.trim());
                                thisStr = formatter.formatRawCellContents(
                                        d, this.formatIndex, this.formatString);
                            } catch (Exception e) {
                                thisStr = fv;
                            }
                        } else {
                            thisStr = fv;
                        }
                    }
                    break;
                case INLINE_STRING:
                    thisStr = value.toString();
                    break;
                case SST_STRING:
                    String sstIndex = value.toString().trim();
                    if (!sstIndex.isEmpty() && sharedStringsShim != null) {
                        try {
                            int idx = Integer.parseInt(sstIndex);
                            thisStr = sharedStringsShim.getItemAt(idx);
                        } catch (NumberFormatException ex) {
                            LOG.error("Failed to parse SST index '{}'", sstIndex, ex);
                        }
                    }
                    break;
                case NUMBER:
                    String n = value.toString();
                    if (this.formatString != null && !n.isEmpty()) {
                        try {
                            thisStr = formatter.formatRawCellContents(
                                    Double.parseDouble(n.trim()),
                                    this.formatIndex, this.formatString);
                        } catch (Exception e) {
                            thisStr = n;
                        }
                    } else {
                        thisStr = n;
                    }
                    break;
                default:
                    thisStr = "(TODO: Unexpected type: " + nextDataType + ")";
                    break;
            }
        }

        checkForEmptyCellComments(EmptyCellCommentsCheckType.CELL);
        XSSFCommentsShim.CommentData comment = commentsShim != null ?
                commentsShim.findCellComment(new CellAddress(cellRef)) : null;
        output.cell(cellRef, thisStr, comment);
    }

    private void checkForEmptyCellComments(EmptyCellCommentsCheckType type) {
        if (commentCellRefs != null && !commentCellRefs.isEmpty()) {
            if (type == EmptyCellCommentsCheckType.END_OF_SHEET_DATA) {
                while (!commentCellRefs.isEmpty()) {
                    outputEmptyCellComment(commentCellRefs.remove());
                }
                return;
            }

            if (this.cellRef == null) {
                if (type == EmptyCellCommentsCheckType.END_OF_ROW) {
                    while (!commentCellRefs.isEmpty()) {
                        if (commentCellRefs.peek().getRow() == rowNum) {
                            outputEmptyCellComment(commentCellRefs.remove());
                        } else {
                            return;
                        }
                    }
                    return;
                } else {
                    throw new IllegalStateException(
                            "Cell ref should be null only if there are only empty " +
                                    "cells in the row; rowNum: " + rowNum);
                }
            }

            CellAddress nextCommentCellRef;
            do {
                CellAddress cellAddr = new CellAddress(this.cellRef);
                CellAddress peekCellRef = commentCellRefs.peek();
                if (type == EmptyCellCommentsCheckType.CELL &&
                        cellAddr.equals(peekCellRef)) {
                    commentCellRefs.remove();
                    return;
                } else {
                    int comparison = peekCellRef.compareTo(cellAddr);
                    if (comparison > 0 &&
                            type == EmptyCellCommentsCheckType.END_OF_ROW &&
                            peekCellRef.getRow() <= rowNum) {
                        nextCommentCellRef = commentCellRefs.remove();
                        outputEmptyCellComment(nextCommentCellRef);
                    } else if (comparison < 0 &&
                            type == EmptyCellCommentsCheckType.CELL &&
                            peekCellRef.getRow() <= rowNum) {
                        nextCommentCellRef = commentCellRefs.remove();
                        outputEmptyCellComment(nextCommentCellRef);
                    } else {
                        nextCommentCellRef = null;
                    }
                }
            } while (nextCommentCellRef != null && !commentCellRefs.isEmpty());
        }
    }

    private void outputEmptyCellComment(CellAddress cellRef) {
        XSSFCommentsShim.CommentData comment = commentsShim.findCellComment(cellRef);
        output.cell(cellRef.formatAsString(), null, comment);
    }

    private enum EmptyCellCommentsCheckType {
        CELL,
        END_OF_ROW,
        END_OF_SHEET_DATA
    }
}
