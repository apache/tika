package org.apache.tika.parser.jdbc;
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


import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOExceptionWithCause;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * General base class to iterate through rows of a JDBC table
 */
class JDBCTableReader {

    private final static Attributes EMPTY_ATTRIBUTES = new AttributesImpl();
    private final Connection connection;
    private final String tableName;
    int maxClobLength = 1000000;
    ResultSet results = null;
    int rows = 0;
    private final EmbeddedDocumentUtil embeddedDocumentUtil;
    public JDBCTableReader(Connection connection, String tableName, EmbeddedDocumentUtil embeddedDocumentUtil) {
        System.out.println("new table: "+tableName);
        this.connection = connection;
        this.tableName = tableName;
        this.embeddedDocumentUtil = embeddedDocumentUtil;
    }

    public boolean nextRow(ContentHandler handler, ParseContext context) throws IOException, SAXException {
        //lazy initialization
        if (results == null) {
            reset();
        }
        try {
            if (!results.next()) {
                return false;
            }
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        try {
            ResultSetMetaData meta = results.getMetaData();
            handler.startElement(XHTMLContentHandler.XHTML, "tr", "tr", EMPTY_ATTRIBUTES);
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                handler.startElement(XHTMLContentHandler.XHTML, "td", "td", EMPTY_ATTRIBUTES);
                handleCell(meta, i, handler, context);
                handler.endElement(XHTMLContentHandler.XHTML, "td", "td");
            }
            handler.endElement(XHTMLContentHandler.XHTML, "tr", "tr");
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        rows++;
        return true;
    }

    private void handleCell(ResultSetMetaData rsmd, int i, ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        switch (rsmd.getColumnType(i)) {
            case Types.BLOB:
                handleBlob(tableName, rsmd.getColumnName(i), rows, results, i, handler, context);
                break;
            case Types.CLOB:
                handleClob(tableName, rsmd.getColumnName(i), rows, results, i, handler, context);
                break;
            case Types.BOOLEAN:
                handleBoolean(results, i, handler);
                break;
            case Types.DATE:
                handleDate(results, i, handler);
                break;
            case Types.TIMESTAMP:
                handleTimeStamp(results, i, handler);
                break;
            case Types.INTEGER:
                handleInteger(results, i, handler);
                break;
            case Types.FLOAT:
                //this is necessary to handle rounding issues in presentation
                //Should we just use getString(i)?
                float f = results.getFloat(i);
                if (! results.wasNull()) {
                    addAllCharacters(Float.toString(f), handler);
                }
                break;
            case Types.DOUBLE:
                double d = results.getDouble(i);
                if (! results.wasNull()) {
                    addAllCharacters(Double.toString(d), handler);
                }
                break;
            default:
                String s = results.getString(i);
                if (!results.wasNull()) {
                    addAllCharacters(s, handler);
                }
                break;
        }
    }

    public List<String> getHeaders() throws IOException {
        List<String> headers = new LinkedList<String>();
        //lazy initialization
        if (results == null) {
            reset();
        }
        try {
            ResultSetMetaData meta = results.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                headers.add(meta.getColumnName(i));
            }
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        return headers;
    }

    protected void handleInteger(ResultSet rs, int columnIndex, ContentHandler handler) throws SQLException, SAXException {
        int i = rs.getInt(columnIndex);
        if (! rs.wasNull()) {
            addAllCharacters(Integer.toString(i), handler);
        }
    }

    private void handleBoolean(ResultSet rs, int columnIndex, ContentHandler handler) throws SAXException, SQLException {
        boolean b = rs.getBoolean(columnIndex);
        if (! rs.wasNull()) {
            addAllCharacters(Boolean.toString(b), handler);
        }
    }


    protected void handleClob(String tableName, String columnName, int rowNum,
                              ResultSet resultSet, int columnIndex,
                              ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        Clob clob = resultSet.getClob(columnIndex);
        if (resultSet.wasNull()) {
            return;
        }
        boolean truncated = clob.length() > Integer.MAX_VALUE || clob.length() > maxClobLength;

        int readSize = (clob.length() < maxClobLength ? (int) clob.length() : maxClobLength);
        Metadata m = new Metadata();
        m.set(Database.TABLE_NAME, tableName);
        m.set(Database.COLUMN_NAME, columnName);
        m.set(Database.PREFIX + "ROW_NUM", Integer.toString(rowNum));
        m.set(Database.PREFIX + "IS_CLOB", "true");
        m.set(Database.PREFIX + "CLOB_LENGTH", Long.toString(clob.length()));
        m.set(Database.PREFIX + "IS_CLOB_TRUNCATED", Boolean.toString(truncated));
        m.set(Metadata.CONTENT_TYPE, "text/plain; charset=UTF-8");
        m.set(Metadata.CONTENT_LENGTH, Integer.toString(readSize));
        m.set(TikaMetadataKeys.RESOURCE_NAME_KEY,
                //just in case something screwy is going on with the column name
                FilenameUtils.normalize(FilenameUtils.getName(columnName + "_" + rowNum + ".txt")));


        //is there a more efficient way to go from a Reader to an InputStream?
        String s = clob.getSubString(0, readSize);
        if (embeddedDocumentUtil.shouldParseEmbedded(m)) {
            embeddedDocumentUtil.parseEmbedded(new ByteArrayInputStream(s.getBytes(UTF_8)), handler, m, true);
        }
    }

    protected void handleBlob(String tableName, String columnName, int rowNum, ResultSet resultSet, int columnIndex,
                              ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        Metadata m = new Metadata();
        m.set(Database.TABLE_NAME, tableName);
        m.set(Database.COLUMN_NAME, columnName);
        m.set(Database.PREFIX + "ROW_NUM", Integer.toString(rowNum));
        m.set(Database.PREFIX + "IS_BLOB", "true");
        Blob blob = null;
        TikaInputStream is = null;
        try {
            blob = getBlob(resultSet, columnIndex, m);
            if (blob == null) {
                return;
            }
            is = TikaInputStream.get(blob, m);
            Attributes attrs = new AttributesImpl();
            ((AttributesImpl) attrs).addAttribute("", "type", "type", "CDATA", "blob");
            ((AttributesImpl) attrs).addAttribute("", "column_name", "column_name", "CDATA", columnName);
            ((AttributesImpl) attrs).addAttribute("", "row_number", "row_number", "CDATA", Integer.toString(rowNum));
            handler.startElement("", "span", "span", attrs);
            String extension = embeddedDocumentUtil.getExtension(is, m);

            m.set(TikaMetadataKeys.RESOURCE_NAME_KEY,
                    //just in case something screwy is going on with the column name
                    FilenameUtils.normalize(FilenameUtils.getName(columnName + "_" + rowNum + extension)));
            if (embeddedDocumentUtil.shouldParseEmbedded(m)) {
                embeddedDocumentUtil.parseEmbedded(is, handler, m, true);
            }

        } finally {
            if (blob != null) {
                try {
                    blob.free();
                } catch (SQLException|UnsupportedOperationException e) {
                    //swallow
                }
            }
            IOUtils.closeQuietly(is);
        }
        handler.endElement("", "span", "span");
    }

    /**
     *
     * @param resultSet result set to grab value from
     * @param columnIndex index in result set
     * @param metadata metadata to populate or use for each implementation
     * @return the blob or <code>null</code> if the value was null
     * @throws SQLException
     */
    protected Blob getBlob(ResultSet resultSet, int columnIndex, Metadata metadata) throws SQLException {
        Blob blob = resultSet.getBlob(columnIndex);
        if (! resultSet.wasNull()) {
            return blob;
        }
        return null;
    }

    protected void handleDate(ResultSet resultSet, int columnIndex, ContentHandler handler) throws SAXException, SQLException {
        addAllCharacters(resultSet.getString(columnIndex), handler);
    }

    protected void handleTimeStamp(ResultSet resultSet, int columnIndex, ContentHandler handler) throws SAXException, SQLException {
        addAllCharacters(resultSet.getString(columnIndex), handler);
    }

    protected void addAllCharacters(String s, ContentHandler handler) throws SAXException {
        if (s == null) {
            return;
        }
        char[] chars = s.toCharArray();
        handler.characters(chars, 0, chars.length);
    }

    void reset() throws IOException {

        if (results != null) {
            try {
                results.close();
            } catch (SQLException e) {
                //swallow
            }
        }

        String sql = "SELECT * from " + tableName;
        try {
            Statement st = connection.createStatement();
            results = st.executeQuery(sql);
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        rows = 0;
    }

    public String getTableName() {
        return tableName;
    }


    protected TikaConfig getTikaConfig() {
        return embeddedDocumentUtil.getTikaConfig();
    }

    protected Detector getDetector() {
        return embeddedDocumentUtil.getDetector();
    }

    protected MimeTypes getMimeTypes() {
        return embeddedDocumentUtil.getMimeTypes();
    }

}
