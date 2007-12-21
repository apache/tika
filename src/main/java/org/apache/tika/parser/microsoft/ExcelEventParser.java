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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.apache.poi.hssf.record.LabelRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RKRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.hssf.record.UnicodeString;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Excel parser implementation which uses POI's Event API
 * to handle the contents of a Workbook.
 * <p>
 * This is an alternative implementation to Tika's
 * {@link ExcelParser} implementation which uses POI's
 * <code>HSSFWorkbook</code> to parse excel files.
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
public class ExcelEventParser extends OfficeParser implements Serializable {

    /** Logging instance */
    private static Log log = LogFactory.getLog(ExcelEventParser.class);

    /**
     * <code>true</code> if the HSSFListener should be registered
     * to listen for all records or <code>false</code> if the listener
     * should be configured to only receive specified records.
     */
    private final boolean listenForAllRecords;

    /**
     * Create an instance which only listens for the specified
     * records (i.e. <code>listenForAllRecords</code> is
     * <code>false</code>).
     */
    public ExcelEventParser() {
        this(false);
    }

    /**
     * Create an instance specifying whether to listen for all
     * records or just for the specified few.
     * <p>
     * <strong>Note</strong> This constructor is intended primarily
     * for testing and debugging - under normal operation
     * <code>listenForAllRecords</code> should be <code>false</code>.
     *
     * @param listenForAllRecords <code>true</code> if the HSSFListener
     * should be registered to listen for all records or <code>false</code>
     * if the listener should be configured to only receive specified records.
     */
    public ExcelEventParser(boolean listenForAllRecords) {
        this.listenForAllRecords = listenForAllRecords;
    }

    /**
     * Return the content type handled by this parser.
     *
     * @return The content type handled
     */
    protected String getContentType() {
        return "application/vnd.ms-excel";
    }

    /**
     * Extracts text from an Excel Workbook writing the extracted content
     * to the specified {@link Appendable}.
     *
     * @param filesystem POI file system
     * @param appendable Where to output the parsed contents
     * @throws IOException if an error occurs processing the workbook
     * or writing the extracted content
     */
    protected void extractText(final POIFSFileSystem filesystem,
            final Appendable appendable) throws IOException {

        if (log.isInfoEnabled()) {
            log.info("Starting listenForAllRecords=" + listenForAllRecords);
        }

        // Set up listener and register the records we want to process
        TikaHSSFListener listener = new TikaHSSFListener(appendable);
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
        }

        // Create event factory and process Workbook (fire events)
        DocumentInputStream documentInputStream = filesystem.createDocumentInputStream("Workbook");
        HSSFEventFactory eventFactory = new HSSFEventFactory();
        eventFactory.processEvents(hssfRequest, documentInputStream);

        if (log.isInfoEnabled()) {
            log.info("Processed " + listener.getRecordCount() + " records");
        }
    }

    // ======================================================================

    /**
     * HSSF Listener implementation which processes the HSSF records.
     */
    private static class TikaHSSFListener implements HSSFListener, Serializable {

        /** Logging instance */
        private static Log log = LogFactory.getLog(ExcelEventParser.class);

        private final Appendable appendable;
        private int recordCount;
        private SSTRecord sstRecord;
        private Map<Short, String> formats        = new HashMap<Short, String>();
        private Map<Short, Short> extendedFormats = new HashMap<Short, Short>();
        private List<String> sheetNames = new ArrayList<String>();
        private short bofRecordType;
        private short defualtCountry;
        private short currentCountry;
        private short currentXFormatIdx;
        private short currentSheetIndex;
        private String currentSheetName;
        private boolean firstElement = true;
        private boolean use1904windowing = false;

        /**
         * Contstruct a new listener instance outputting parsed data to
         * the specified Appendable.
         *
         * @param appendable Destination to write the parsed output to
         */
        private TikaHSSFListener(final Appendable appendable) {
            this.appendable = appendable;
        }

        /**
         * Return a count of the number of records processed.
         *
         * @return The number of records processed by this listener
         */
        private int getRecordCount() {
            return recordCount;
        }

        /**
         * Process a HSSF record.
         *
         * @param record HSSF Record
         */
        public void processRecord(final Record record) {
            recordCount++;
            final short sid = record.getSid();
            switch (sid) {

                /* BOFRecord: indicates start of workbook, worksheet etc. records */
                case BOFRecord.sid:
                    BOFRecord bofRecord = (BOFRecord)record;
                    bofRecordType = bofRecord.getType();
                    switch (bofRecordType) {
                        case BOFRecord.TYPE_WORKBOOK:
                            currentSheetIndex = -1;
                            debug(record, ".Workbook");
                            break;
                        case BOFRecord.TYPE_WORKSHEET:
                            currentSheetIndex++;
                            currentSheetName = null;
                            if (currentSheetIndex < sheetNames.size()) {
                                currentSheetName = sheetNames.get(currentSheetIndex);
                            }
                            if (log.isDebugEnabled()) {
                                debug(record, ".Worksheet[" + currentSheetIndex
                                        + "], Name=[" + currentSheetName + "]");
                            }
                            addText(currentSheetName);
                            break;
                        default:
                            if (log.isDebugEnabled()) {
                                debug(record, "[" + bofRecordType + "]");
                            }
                            break;
                    }
                    break;

                /* BOFRecord: indicates end of workbook, worksheet etc. records */
                case EOFRecord.sid:
                    debug(record);
                    bofRecordType = 0;
                    break;

                /* Indicates whether to use 1904 Date Windowing or not */
                case DateWindow1904Record.sid:
                    DateWindow1904Record dw1904Rec = (DateWindow1904Record)record;
                    use1904windowing = (dw1904Rec.getWindowing() == 1);
                    if (log.isDebugEnabled()) {
                        debug(record, "[" + use1904windowing + "]");
                    }
                    break;

                /* CountryRecord: holds all the strings for LabelSSTRecords */
                case CountryRecord.sid:
                    CountryRecord countryRecord = (CountryRecord)record;
                    defualtCountry = countryRecord.getDefaultCountry();
                    currentCountry = countryRecord.getCurrentCountry();
                    if (log.isDebugEnabled()) {
                        debug(record, " default=[" + defualtCountry
                                + "], current=[" + currentCountry + "]");
                    }
                    break;

                /* SSTRecord: holds all the strings for LabelSSTRecords */
                case SSTRecord.sid:
                    sstRecord = (SSTRecord)record;
                    debug(record);
                    break;

                /* BoundSheetRecord: Worksheet index record */
                case BoundSheetRecord.sid:
                    BoundSheetRecord boundSheetRecord = (BoundSheetRecord)record;
                    String sheetName = boundSheetRecord.getSheetname();
                    sheetNames.add(sheetName);
                    if (log.isDebugEnabled()) {
                        debug(record, "[" + sheetNames.size()
                                + "], Name=[" + sheetName + "]");
                    }
                    break;

                /* FormatRecord */
                case FormatRecord.sid:
                    FormatRecord formatRecord = (FormatRecord)record;
                    String dataFormat = formatRecord.getFormatString();
                    short formatIdx = formatRecord.getIndexCode();
                    formats.put(formatIdx, dataFormat);
                    if (log.isDebugEnabled()) {
                        debug(record, "[" + formatIdx + "]=[" + dataFormat + "]");
                    }
                    break;

                /* ExtendedFormatRecord */
                case ExtendedFormatRecord.sid:
                    ExtendedFormatRecord xFormatRecord = (ExtendedFormatRecord)record;
                    if (xFormatRecord.getXFType() == ExtendedFormatRecord.XF_CELL) {
                        short dataFormatIdx = xFormatRecord.getFormatIndex();
                        if (dataFormatIdx > 0) {
                            extendedFormats.put(currentXFormatIdx, dataFormatIdx);
                            if (log.isDebugEnabled()) {
                                debug(record, "[" + currentXFormatIdx
                                        + "]=FormatRecord[" + dataFormatIdx + "]");
                            }
                        }
                    }
                    currentXFormatIdx++;
                    break;

                default:
                    if (bofRecordType == BOFRecord.TYPE_WORKSHEET
                            && record instanceof CellValueRecordInterface) {
                        processCellValue(sid, (CellValueRecordInterface)record);
                    } else {
                        debug(record);
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
        private void processCellValue(final short sid,
                final CellValueRecordInterface record) {

            short xfIdx = record.getXFIndex();
            Short dfIdx = extendedFormats.get(xfIdx);
            String dataFormat = dfIdx != null ? formats.get(dfIdx) : null;
            String str = null;
            switch (sid) {

                /* FormulaRecord: Cell value from a formula */
                case FormulaRecord.sid:
                    FormulaRecord formulaRecord = (FormulaRecord)record;
                    double fmlValue = formulaRecord.getValue();
                    str = toString(fmlValue, dfIdx, dataFormat);
                    str = addText(str);
                    break;

                /* LabelRecord: strings stored directly in the cell */
                case LabelRecord.sid:
                    LabelRecord labelRecord = (LabelRecord)record;
                    str = addText(labelRecord.getValue());
                    break;

                /* LabelSSTRecord: Ref. a string in the shared string table */
                case LabelSSTRecord.sid:
                    LabelSSTRecord labelSSTRecord = (LabelSSTRecord)record;
                    int sstIndex = labelSSTRecord.getSSTIndex();
                    UnicodeString unicodeStr = sstRecord.getString(sstIndex);
                    str = addText(unicodeStr.getString());
                    break;

                /* NumberRecord: Contains a numeric cell value */
                case NumberRecord.sid:
                    double numValue = ((NumberRecord)record).getValue();
                    if (!Double.isNaN(numValue)) {
                        str = Double.toString(numValue);
                    }
                    str = toString(numValue, dfIdx, dataFormat);
                    str = addText(str);
                    break;

                /* RKRecord: Excel internal number record */
                case RKRecord.sid:
                    double rkValue = ((RKRecord)record).getRKNumber();
                    str = toString(rkValue, dfIdx, dataFormat);
                    str = addText(str);
                    break;
            }

            // =========== Debug Mess: START ===========
            if (log.isDebugEnabled()) {
                StringBuilder builder = new StringBuilder();
                builder.append('[');
                // builder.append(ExcelUtils.columnIndexToLabel(record.getColumn()));
                builder.append(record.getColumn());
                builder.append(":");
                builder.append((record.getRow() + 1));
                builder.append(']');
                if (dfIdx != null) {
                    builder.append(" xfIdx[");
                    builder.append(xfIdx).append(']');
                    builder.append("=dfIdx[");
                    builder.append(dfIdx);
                    builder.append(']');
                    if (dataFormat != null) {
                        builder.append("=[");
                        builder.append(dataFormat);
                        builder.append(']');
                    }
                }
                builder.append(", value=[");
                if (str != null && str.length() > 0) {
                    builder.append(str);
                }
                builder.append(']');
                debug((Record)record, builder.toString());
            }
            // =========== Debug Mess: END =============
        }

        /**
         * Converts a numeric excel cell value to a String.
         *
         * @param value The cell value
         * @param dfIdx The data format index
         * @param dataFormat The data format
         * @return Formatted string value
         */
        private String toString(double value, Short dfIdx, String dataFormat) {
            if (Double.isNaN(value)) {
                return null;
            }

            // **** TODO: Data Format parsing ****
            // return ExcelUtils.format(value, dfIdx, dataFormat, use1904windowing);
            return Double.toString(value);
        }

        /**
         * Add a parsed text value to this listners appendable.
         * <p>
         * Null and zero length values are ignored.
         *
         * @param text The text value
         * @return the added text
         */
        private String addText(String text) {
            if (text != null) {
                text = text.trim();
                if (text.length() > 0) {
                    try {
                        if (!firstElement) {
                            appendable.append(" ");
                        }
                        appendable.append(text);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    firstElement = false;
                }
            }
            return text;
        }

        /**
         * Record debugging.
         *
         * @param record The Record
         */
        private void debug(Record record) {
            debug(record, "");
        }

        /**
         * Record debugging.
         *
         * @param record The Record
         * @param msg Debug Message
         */
        private void debug(Record record, String msg) {
            if (log.isDebugEnabled()) {
                String className = record.getClass().getSimpleName();
                String text = (msg == null ? className :  className + msg);
                if (record.getSid() == BOFRecord.sid ||
                    record.getSid() == EOFRecord.sid) {
                    log.debug(text);
                } else {
                    log.debug("    " + text);
                }
            }
        }
    }
}
