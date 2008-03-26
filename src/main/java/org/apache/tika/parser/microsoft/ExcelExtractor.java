/**
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
package org.apache.tika.parser.microsoft;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.CountryRecord;
import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.record.EOFRecord;
import org.apache.poi.hssf.record.ExtendedFormatRecord;
import org.apache.poi.hssf.record.FormatRecord;
import org.apache.poi.hssf.record.FormulaRecord;
//import org.apache.poi.hssf.record.HyperlinkRecord;  // FIXME - requires POI release
import org.apache.poi.hssf.record.LabelRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RKRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Excel parser implementation which uses POI's Event API
 * to handle the contents of a Workbook.
 * <p>
 * The Event API uses a much smaller memory footprint than
 * <code>HSSFWorkbook</code> when processing excel files
 * but at the cost of more complexity.
 * <p>
 * With the Event API a <i>listener</i> is registered for
 * specific record types and those records are created,
 * fired off to the listener and then discarded as the stream
 * is being processed.
 *
 * @see org.apache.poi.hssf.eventusermodel.HSSFListener
 * @see <a href="http://poi.apache.org/hssf/how-to.html#event_api">
 * POI Event API How To</a>
 */
public class ExcelExtractor {

    /** Logging instance */
    private static final Log log = LogFactory.getLog(ExcelExtractor.class);

    /**
     * <code>true</code> if the HSSFListener should be registered
     * to listen for all records or <code>false</code> (the default)
     * if the listener should be configured to only receive specified
     * records.
     */
    private boolean listenForAllRecords = false;

    /**
     * Returns <code>true</code> if this parser is configured to listen
     * for all records instead of just the specified few.
     */
    public boolean isListenForAllRecords() {
        return listenForAllRecords;
    }

    /**
     * Specifies whether this parser should to listen for all
     * records or just for the specified few.
     * <p>
     * <strong>Note:</strong> Under normal operation this setting should
     * be <code>false</code> (the default), but you can experiment with
     * this setting for testing and debugging purposes.
     *
     * @param listenForAllRecords <code>true</code> if the HSSFListener
     * should be registered to listen for all records or <code>false</code>
     * if the listener should be configured to only receive specified records.
     */
    public void setListenForAllRecords(boolean listenForAllRecords) {
        this.listenForAllRecords = listenForAllRecords;
    }

    /**
     * Extracts text from an Excel Workbook writing the extracted content
     * to the specified {@link Appendable}.
     *
     * @param filesystem POI file system
     * @throws IOException if an error occurs processing the workbook
     * or writing the extracted content
     */
    protected void parse(POIFSFileSystem filesystem, XHTMLContentHandler xhtml)
            throws IOException, SAXException {
        log.debug("Starting listenForAllRecords=" + listenForAllRecords);

        // Set up listener and register the records we want to process
        TikaHSSFListener listener = new TikaHSSFListener(xhtml);
        HSSFRequest hssfRequest = new HSSFRequest();
        if (listenForAllRecords) {
            hssfRequest.addListenerForAllRecords(listener);
        } else {
            hssfRequest.addListener(listener, BOFRecord.sid);
            hssfRequest.addListener(listener, EOFRecord.sid);
            hssfRequest.addListener(listener, DateWindow1904Record.sid);
            hssfRequest.addListener(listener, CountryRecord.sid);
            hssfRequest.addListener(listener, BoundSheetRecord.sid);
            hssfRequest.addListener(listener, FormatRecord.sid);
            hssfRequest.addListener(listener, ExtendedFormatRecord.sid);
            hssfRequest.addListener(listener, SSTRecord.sid);
            hssfRequest.addListener(listener, FormulaRecord.sid);
            hssfRequest.addListener(listener, LabelRecord.sid);
            hssfRequest.addListener(listener, LabelSSTRecord.sid);
            hssfRequest.addListener(listener, NumberRecord.sid);
            hssfRequest.addListener(listener, RKRecord.sid);
            //hssfRequest.addListener(listener, HyperlinkRecord.sid); // FIXME - requires POI release
        }

        // Create event factory and process Workbook (fire events)
        DocumentInputStream documentInputStream = filesystem.createDocumentInputStream("Workbook");
        HSSFEventFactory eventFactory = new HSSFEventFactory();

        eventFactory.processEvents(hssfRequest, documentInputStream);
        listener.throwStoredException();
    }

    // ======================================================================

    /**
     * HSSF Listener implementation which processes the HSSF records.
     */
    private static class TikaHSSFListener implements HSSFListener, Serializable {

        private final XHTMLContentHandler handler;

        private SAXException exception;

        private SSTRecord sstRecord;
        private List<String> sheetNames = new ArrayList<String>();
        private short currentSheetIndex;

        private boolean insideWorksheet = false;

        private TikaExcelSheet currentSheet;

        /**
         * Contstruct a new listener instance outputting parsed data to
         * the specified XHTML content handler.
         *
         * @param handler Destination to write the parsed output to
         */
        private TikaHSSFListener(XHTMLContentHandler handler) {
            this.handler = handler;
            this.exception = null;
        }

        /**
         * Process a HSSF record.
         *
         * @param record HSSF Record
         */
        public void processRecord(Record record) {
            try {
                if (log.isDebugEnabled()) {
                    log.debug(record.toString());
                }
                internalProcessRecord(record);
            } catch (SAXException e) {
                if (exception == null) {
                    exception = e;
                }
            }
        }

        public void throwStoredException() throws SAXException {
            if (exception != null) {
                throw exception;
            }
        }

        private void internalProcessRecord(Record record) throws SAXException {
            switch (record.getSid()) {

                /* BOFRecord: indicates start of workbook, worksheet etc. records */
                case BOFRecord.sid:
                    switch (((BOFRecord) record).getType()) {
                        case BOFRecord.TYPE_WORKBOOK:
                            currentSheetIndex = -1;
                            break;
                        case BOFRecord.TYPE_WORKSHEET:
                            currentSheetIndex++;
                            String currentSheetName = "";
                            if (currentSheetIndex < sheetNames.size()) {
                                currentSheetName = sheetNames.get(currentSheetIndex);
                            }
                            currentSheet = new TikaExcelSheet(currentSheetName);
                            insideWorksheet = true;
                            break;
                    }
                    break;

                /* EOFRecord: indicates end of workbook, worksheet etc. records */
                case EOFRecord.sid:
                    if (insideWorksheet) {
                        processSheet();
                        insideWorksheet = false;
                    }
                    break;

                /* SSTRecord: holds all the strings for LabelSSTRecords */
                case SSTRecord.sid:
                    sstRecord = (SSTRecord)record;
                    break;

                /* BoundSheetRecord: Worksheet index record */
                case BoundSheetRecord.sid:
                    BoundSheetRecord boundSheetRecord = (BoundSheetRecord)record;
                    String sheetName = boundSheetRecord.getSheetname();
                    sheetNames.add(sheetName);
                    break;

                // FIXME - requires POI release
                ///* HyperlinkRecord: holds a URL associated with a cell */
                //case HyperlinkRecord.sid:
                //    HyperlinkRecord hyperlinkRecord = (HyperlinkRecord)record;
                //    if (insideWorksheet) {
                //        int row = hyperlinkRecord.getFirstRow();
                //        short column =  hyperlinkRecord.getFirstColumn();
                //        TikaExcelCell cell = currentSheet.findCell(row, column);
                //        if (cell != null) {
                //            cell.setHyperlink(hyperlinkRecord.getAddress());
                //        }
                //    }
                //    break;

                default:
                    if (insideWorksheet
                            && record instanceof CellValueRecordInterface) {
                        processCellValue(
                                record.getSid(),
                                (CellValueRecordInterface)record);
                    }
                    break;
            }
        }

        /**
         * Process a Cell Value record.
         *
         * @param sid record type identifier
         * @param record The cell value record
         */
        private void processCellValue(
                short sid, CellValueRecordInterface record)
                throws SAXException {

            String text = null;
            switch (sid) {
                /* FormulaRecord: Cell value from a formula */
                case FormulaRecord.sid:
                    FormulaRecord formulaRecord = (FormulaRecord)record;
                    double fmlValue = formulaRecord.getValue();
                    text = Double.toString(fmlValue);
                    break;

                /* LabelRecord: strings stored directly in the cell */
                case LabelRecord.sid:
                    text = ((LabelRecord)record).getValue();
                    break;

                /* LabelSSTRecord: Ref. a string in the shared string table */
                case LabelSSTRecord.sid:
                    LabelSSTRecord labelSSTRecord = (LabelSSTRecord) record;
                    int sstIndex = labelSSTRecord.getSSTIndex();
                    text = sstRecord.getString(sstIndex).getString();
                    break;

                /* NumberRecord: Contains a numeric cell value */
                case NumberRecord.sid:
                    double numValue = ((NumberRecord)record).getValue();
                    text = Double.toString(numValue);
                    break;

                /* RKRecord: Excel internal number record */
                case RKRecord.sid:
                    double rkValue = ((RKRecord)record).getRKNumber();
                    text = Double.toString(rkValue);
                    break;
            }
            if (text != null) {
                text = text.trim();
            }
            if (text != null && text.length() > 0) {
                currentSheet.addCell(record.getRow(), record.getColumn(), text);
            }
        }

        /**
         * Process an excel sheet.
         *
         * @throws SAXException if an error occurs
         */
        private void processSheet() throws SAXException {

            // ignore empty sheets
            if (currentSheet.getCellCount() == 0) {
                return;
            }
            
            // Sheet Start
            handler.startElement("div", "class", "page");
            handler.element("h1", currentSheet.getName());
            handler.characters("\n");
            handler.startElement("table");
            handler.startElement("tbody");

            // Process Rows
            int currentRow = 0;
            TikaExcelRow row = currentSheet.getFirstRow();
            while (row != null) {
                while (currentRow < row.getRow()) {
                    handler.startElement("tr");
                    handler.startElement("td");
                    handler.endElement("td");
                    handler.endElement("tr");
                    handler.characters("\n");
                    currentRow++;
                }
                handler.startElement("tr");

                // Process Cells
                short currentColumn = 0;
                TikaExcelCell cell = row.getFirstCell();
                while (cell != null) {
                    while (currentColumn < cell.getColumn()) {
                        handler.startElement("td");
                        handler.endElement("td");
                        handler.characters("\t");
                        currentColumn++;
                    }
                    handler.startElement("td");
                    if (cell.getHyperlink() != null) {
                        handler.startElement("a", "href", cell.getHyperlink());
                        handler.characters(cell.getText());
                        handler.endElement("a");
                    } else {
                        handler.characters(cell.getText());
                    }
                    handler.endElement("td");
                    cell = cell.getNextCell();
                }

                handler.endElement("tr");
                row = row.getNextRow();
            }
            
            // Sheet End
            handler.endElement("tbody");
            handler.endElement("table");
            handler.endElement("div");
            handler.characters("\n");
        }
    }

    // ======================================================================

    /**
     * Tika's excel sheet representation.
     */
    private static class TikaExcelSheet {
        private String name;
        private TikaExcelRow firstRow;
        private TikaExcelRow lastRow;
        private int rowCount;
        private int cellCount;

        /**
         * Construct a new sheet instance.
         *
         * @param name The name of the sheet
         */
        TikaExcelSheet(String name) {
            this.name = name;
        }

        /**
         * Add a cell to the sheet.
         *
         * @param row The cell's row number
         * @param column The cell's column number
         * @param text The cell's text
         */
        void addCell(int row, short column, String text) {

            // Create row if required
            if (lastRow == null || lastRow.row != row) {
                TikaExcelRow newRow = new TikaExcelRow(row);
                rowCount++;
                if (lastRow == null) {
                    firstRow = newRow;
                } else {
                    lastRow.setNextRow(newRow);
                }
                lastRow = newRow;
            }

            cellCount++;

            // Add a cell
            lastRow.addCell(new TikaExcelCell(column, text));
        }

        /**
         * Find a cell in a sheet.
         *
         * @param row The cell's row number
         * @param column The cell's column number
         * @return The cell or null if not found
         */
        TikaExcelCell findCell(int row, short column) {

            TikaExcelRow currentRow = firstRow;
            while (currentRow != null && currentRow.getRow() < row) {
                currentRow = currentRow.getNextRow();
            }
            if (currentRow != null && currentRow.getRow() == row) {
                TikaExcelCell currentCell = currentRow.getFirstCell();
                while (currentCell != null && currentCell.getColumn() < column) {
                    currentCell = currentCell.getNextCell();
                }
                if (currentCell != null && currentCell.getColumn() == column) {
                    return currentCell;
                }
            }
            return null;
        }

        /**
         * Return the number of cells in the sheet.
         *
         * @return the number of cells in the sheet
         */
        int getCellCount() {
            return cellCount;
        }

        /**
         * Return the number of rows in the sheet.
         *
         * @return the number of cells in the sheet
         */
        int getRowCount() {
            return rowCount;
        }

        /**
         * Return the first row.
         *
         * @return the first row
         */
        TikaExcelRow getFirstRow() {
            return firstRow;
        }

        /**
         * Return the name of the sheet.
         *
         * @return the name of the sheet
         */
        String getName() {
            return name;
        }
        
    }

    // ======================================================================

    /**
     * Tika's excel row representation. 
     */
    private static class TikaExcelRow {
        private final int row;
        private TikaExcelRow nextRow;
        private TikaExcelCell firstCell;
        private TikaExcelCell lastCell;

        /**
         * Construct a new Row instance.
         *
         * @param row The row number
         */
        TikaExcelRow(int row) {
            this.row = row;
        }

        /**
         * Add a cell to the row.
         *
         * @param newCell the new cell to add
         */
        void addCell(TikaExcelCell newCell) {
            if (lastCell != null) {
                lastCell.setNextCell(newCell);
            }
            this.lastCell = newCell;
            if (firstCell == null) {
                firstCell = newCell;
            }
        }

        /**
         * Return the first cell in the row.
         *
         * @return the first cell in the row
         */
        TikaExcelCell getFirstCell() {
            return firstCell;
        }

        /**
         * Return the row number.
         *
         * @return the row number
         */
        int getRow() {
            return row;
        }
        
        /**
         * Return the next row in the sheet.
         *
         * @return the next row in the sheet
         */
        TikaExcelRow getNextRow() {
            return nextRow;
        }

        /**
         * Set the next row in the sheet.
         *
         * @param nextRow the next row in the sheet
         */
        void setNextRow(TikaExcelRow nextRow) {
            this.nextRow = nextRow;
        }
        
    }

    // ======================================================================

    /**
     * Tika's excel cell representation. 
     */
    private static class TikaExcelCell {
        private final short column;
        private String text;
        private String hyperlink;
        private TikaExcelCell nextCell;

        /**
         * Construct a new cell.
         *
         * @param column The cell's column number
         * @param text The cell's text
         */
        TikaExcelCell(short column, String text) {
            this.column = column;
            this.text = text;
        }

        /**
         * Return the cell's column number
         *
         * @return the cell's column number
         */
        short getColumn() {
            return column;
        }

        /**
         * Return the next cell in the row.
         *
         * @return the next cell in the row
         */
        TikaExcelCell getNextCell() {
            return nextCell;
        }

        /**
         * Return the cell's text.
         *
         * @return the cell's text
         */
        String getText() {
            return text;
        }

        /**
         * Return hyperlink address, if any
         *
         * @return the hyperlink address
         */
        String getHyperlink() {
            return hyperlink;
        }

        /**
         * Set the hyperlink address
         *
         * @param hyperlink the hyperlink address to set
         */
        void setHyperlink(String hyperlink) {
            this.hyperlink = hyperlink;
        }

        /**
         * Set the next cell in the row.
         *
         * @param nextCell next cell in the row
         */
        void setNextCell(TikaExcelCell nextCell) {
            this.nextCell = nextCell;
        }
        
    }
}
