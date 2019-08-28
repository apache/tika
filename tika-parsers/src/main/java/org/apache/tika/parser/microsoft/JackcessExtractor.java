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

package org.apache.tika.parser.microsoft;


import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.PropertyMap;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;
import com.healthmarketscience.jackcess.query.Query;
import com.healthmarketscience.jackcess.util.OleBlob;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.OfficeOpenXMLExtended;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;

/**
 * Internal class.  Needs to be instantiated for each parse because of
 * the lack of thread safety with the dateTimeFormatter
 */
class JackcessExtractor extends AbstractPOIFSExtractor {

    final static String TITLE_PROP_KEY = "Title";
    final static String AUTHOR_PROP_KEY = "Author";
    final static String COMPANY_PROP_KEY = "Company";

    final static String TEXT_FORMAT_KEY = "TextFormat";
    final static String CURRENCY_FORMAT_KEY = "Format";
    final static byte TEXT_FORMAT = 0;
    final static byte RICH_TEXT_FORMAT = 1;

    final NumberFormat currencyFormatter;
    final DateFormat shortDateTimeFormatter;

    final Parser htmlParser;
    final ParseContext parseContext;

    protected JackcessExtractor(Metadata metadata, ParseContext context, Locale locale) {
        super(context, metadata);
        currencyFormatter = NumberFormat.getCurrencyInstance(locale);
        shortDateTimeFormatter = DateFormat.getDateInstance(DateFormat.SHORT, locale);
        this.parseContext = context;
        Parser tmpHtmlParser =
                EmbeddedDocumentUtil.tryToFindExistingLeafParser(HtmlParser.class, context);
        if (tmpHtmlParser == null) {
            htmlParser = new HtmlParser();
        } else {
            htmlParser = tmpHtmlParser;
        }
    }

    public void parse(Database db, XHTMLContentHandler xhtml) throws IOException, SAXException, TikaException {


        String pw = db.getDatabasePassword();
        if (pw != null) {
            parentMetadata.set(JackcessParser.MDB_PW, pw);
        }

        PropertyMap dbp = db.getDatabaseProperties();
        for (PropertyMap.Property p : dbp) {
            parentMetadata.add(JackcessParser.MDB_PROPERTY_PREFIX + p.getName(),
                    toString(p.getValue(), p.getType()));
        }

        PropertyMap up = db.getUserDefinedProperties();
        for (PropertyMap.Property p : up) {
            parentMetadata.add(JackcessParser.USER_DEFINED_PROPERTY_PREFIX+ p.getName(),
                    toString(p.getValue(), p.getType()));
        }

        Set<String> found = new HashSet<>();
        PropertyMap summaryProperties = db.getSummaryProperties();
        if (summaryProperties != null) {
            //try to get core properties
            PropertyMap.Property title = summaryProperties.get(TITLE_PROP_KEY);
            if (title != null) {
                parentMetadata.set(TikaCoreProperties.TITLE, toString(title.getValue(), title.getType()));
                found.add(title.getName());
            }
            PropertyMap.Property author = summaryProperties.get(AUTHOR_PROP_KEY);
            if (author != null && author.getValue() != null) {
                String authorString = toString(author.getValue(), author.getType());
                SummaryExtractor.addMulti(parentMetadata, TikaCoreProperties.CREATOR, authorString);
                found.add(author.getName());
            }
            PropertyMap.Property company = summaryProperties.get(COMPANY_PROP_KEY);
            if (company != null) {
                parentMetadata.set(OfficeOpenXMLExtended.COMPANY, toString(company.getValue(), company.getType()));
                found.add(company.getName());
            }

            for (PropertyMap.Property p : db.getSummaryProperties()) {
                if (! found.contains(p.getName())) {
                    parentMetadata.add(JackcessParser.SUMMARY_PROPERTY_PREFIX + p.getName(),
                            toString(p.getValue(), p.getType()));
                }
            }

        }

        Iterator<Table> it = db.newIterable().
                setIncludeLinkedTables(false).
                setIncludeSystemTables(false).iterator();

        while (it.hasNext()) {
            Table table = it.next();
            String tableName = table.getName();
            List<? extends Column> columns = table.getColumns();
            xhtml.startElement("table", "name", tableName);
            addHeaders(columns, xhtml);
            xhtml.startElement("tbody");

            Row r = table.getNextRow();

            while (r != null) {
                xhtml.startElement("tr");
                for (Column c : columns) {
                    handleCell(r, c, xhtml);
                }
                xhtml.endElement("tr");
                r = table.getNextRow();
            }
            xhtml.endElement("tbody");
            xhtml.endElement("table");
        }

        for (Query q : db.getQueries()) {
            xhtml.startElement("div", "type", "sqlQuery");
            String sqlString = "unsupported query type";
            //unknownqueryimpl can throw an UnsupportedOperationException
            try {
                sqlString = q.toSQLString();
            } catch (UnsupportedOperationException e) {
                //swallow
            }
            xhtml.characters(sqlString);
            xhtml.endElement("div");
        }
    }

    private void addHeaders(List<? extends Column> columns, XHTMLContentHandler xhtml) throws SAXException {
        xhtml.startElement("thead");
        xhtml.startElement("tr");
        for (Column c : columns) {
            xhtml.startElement("th");
            xhtml.characters(c.getName());
            xhtml.endElement("th");
        }
        xhtml.endElement("tr");
        xhtml.endElement("thead");

    }

    private void handleCell(Row r, Column c, XHTMLContentHandler handler)
            throws SAXException, IOException, TikaException {

        handler.startElement("td");
        if (c.getType().equals(DataType.OLE)) {
            handleOLE(r, c.getName(), handler);
        } else if (c.getType().equals(DataType.BINARY)) {
            Object obj = r.get(c.getName());
            if (obj != null) {
                byte[] bytes = (byte[])obj;
                handleEmbeddedResource(
                        TikaInputStream.get(bytes),
                        null,//filename
                        null,//relationshipId
                        null,//mediatype
                        handler, false);
            }
        } else {
            Object obj = r.get(c.getName());
            String v = toString(obj, c.getType());
            if (isRichText(c)) {
                BodyContentHandler h = new BodyContentHandler();
                Metadata m = new Metadata();
                m.set(Metadata.CONTENT_TYPE, "text/html; charset=UTF-8");
                try {
                    htmlParser.parse(new ByteArrayInputStream(v.getBytes(UTF_8)),
                            h,
                           m, parseContext);
                    handler.characters(h.toString());
                } catch (SAXException e) {
                    //if something went wrong in htmlparser, just append the characters
                    handler.characters(v);
                }
            } else {
                handler.characters(v);
            }
        }
        handler.endElement("td");
    }

    private boolean isRichText(Column c) throws IOException {

        if (c == null) {
            return false;
        }

        PropertyMap m = c.getProperties();
        if (m == null) {
            return false;
        }
        if (c.getType() == null || ! c.getType().equals(DataType.MEMO)) {
            return false;
        }
        Object b = m.getValue(TEXT_FORMAT_KEY);
        if (b instanceof Byte) {
            if (((Byte)b).byteValue() == RICH_TEXT_FORMAT) {
                return true;
            }
        }
        return false;
    }

    private String toString(Object value, DataType type) {
        if (value == null) {
            return "";
        }
        if (type == null) {
            //this shouldn't happen
            return value.toString();
        }
        switch (type) {
            case LONG:
                return Integer.toString((Integer)value);
            case TEXT:
                return (String)value;
            case MONEY:
                //TODO: consider getting parsing "Format" field from
                //field properties.
                return formatCurrency(((BigDecimal)value).doubleValue(), type);
            case SHORT_DATE_TIME:
                return formatShortDateTime((Date)value);
            case BOOLEAN:
                return Boolean.toString((Boolean) value);
            case MEMO:
                return (String)value;
            case INT:
                return Short.toString((Short)value);
            case DOUBLE:
                return Double.toString((Double)value);
            case FLOAT:
                return Float.toString((Float)value);
            case NUMERIC:
                return value.toString();
            case BYTE:
                return Byte.toString((Byte)value);
            case GUID:
                return value.toString();
            case COMPLEX_TYPE: //skip all these
            case UNKNOWN_0D:
            case UNKNOWN_11:
            case UNSUPPORTED_FIXEDLEN:
            case UNSUPPORTED_VARLEN:
            default:
                return "";

        }
    }


    private void handleOLE(Row row, String cName, XHTMLContentHandler xhtml) throws IOException, SAXException, TikaException {
        OleBlob blob = row.getBlob(cName);
        //lifted shamelessly from Jackcess's OleBlobTest
        if (blob == null)
            return;

        OleBlob.Content content = blob.getContent();
        if (content == null)
            return;

        switch (content.getType()) {
            case LINK:
                xhtml.characters(((OleBlob.LinkContent) content).getLinkPath());
                break;
            case SIMPLE_PACKAGE:
                OleBlob.SimplePackageContent spc = (OleBlob.SimplePackageContent) content;
                //TODO: find test file that has this kind of attachment
                //and see if getFilePath or getLocalFilePath is meaningful
                //for TikaCoreProperties.ORIGINAL_RESOURCE_NAME
                TikaInputStream tis = null;
                try {
                    tis = TikaInputStream.get(spc.getStream());
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                    break;
                }
                if (tis != null) {
                    try {
                        handleEmbeddedResource(
                                tis,
                                spc.getFileName(),//filename
                                null,//relationshipId
                                spc.getTypeName(),//mediatype
                                xhtml, false);
                    } finally {
                        IOUtils.closeQuietly(tis);
                    }
                }
                break;
            case OTHER:
                OleBlob.OtherContent oc = (OleBlob.OtherContent) content;
                TikaInputStream ocStream = null;
                try {
                    ocStream = TikaInputStream.get(oc.getStream());
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordException(e, parentMetadata);
                }
                try {
                    handleEmbeddedResource(
                            ocStream,
                            null,//filename
                            null,//relationshipId
                            oc.getTypeName(),//mediatype
                            xhtml, false);
                } finally {
                    IOUtils.closeQuietly(ocStream);
                }
                break;
            case COMPOUND_STORAGE:
                OleBlob.CompoundContent cc = (OleBlob.CompoundContent) content;
                handleCompoundContent(cc, xhtml);
                break;
        }
    }

    private void handleCompoundContent(OleBlob.CompoundContent cc, XHTMLContentHandler xhtml) throws IOException, SAXException, TikaException {
        InputStream is = null;
        POIFSFileSystem fileSystem = null;
        try {
            try {
                is = cc.getStream();
            } catch (IOException e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                return;
            }

            try {
                fileSystem = new POIFSFileSystem(is);
            } catch (Exception e) {
                EmbeddedDocumentUtil.recordEmbeddedStreamException(e, parentMetadata);
                return;
            }

            handleEmbeddedOfficeDoc(fileSystem.getRoot(), xhtml);

        } finally {
            if (fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException e) {
                    //swallow
                }
            }
            if (is != null) {
                IOUtils.closeQuietly(is);
            }
        }
    }

    String formatCurrency(Double d, DataType type) {
        if (d == null) {
            return "";
        }
        return currencyFormatter.format(d);
    }

    String formatShortDateTime(Date d) {
        if (d == null) {
            return "";
        }
        return shortDateTimeFormatter.format(d);
    }

}

