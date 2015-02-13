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

import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;



/**
 * Concrete class for SQLLite table parsing.  This overrides
 * column type handling from JDBCRowHandler.
 * <p>
 * This class is not designed to be thread safe (because of DateFormat)!
 * Need to call a new instance for each parse, as AbstractDBParser does.
 * <p>
 * For now, this silently skips cells of type CLOB, because xerial's jdbc connector
 * does not currently support them.
 */
class SQLite3TableReader extends JDBCTableReader {


    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);

    public SQLite3TableReader(Connection connection, String tableName, ParseContext context) {
        super(connection, tableName, context);
    }


    /**
     * No-op for now in {@link SQLite3TableReader}.
     *
     * @param tableName
     * @param fieldName
     * @param rowNum
     * @param resultSet
     * @param columnIndex
     * @param handler
     * @param context
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     */
    @Override
    protected void handleClob(String tableName, String fieldName, int rowNum,
                              ResultSet resultSet, int columnIndex,
                              ContentHandler handler, ParseContext context) throws SQLException, IOException, SAXException {
        //no-op for now.
    }

    /**
     * The jdbc connection to Sqlite does not yet implement blob, have to getBytes().
     *
     * @param resultSet   resultSet
     * @param columnIndex columnIndex for blob
     * @return
     * @throws java.sql.SQLException
     */
    @Override
    protected InputStream getInputStreamFromBlob(ResultSet resultSet, int columnIndex, Blob blob, Metadata m) throws SQLException {
        return TikaInputStream.get(resultSet.getBytes(columnIndex), m);
    }

    @Override
    protected void handleInteger(String columnTypeName, ResultSet rs, int columnIndex,
                                 ContentHandler handler) throws SQLException, SAXException {
        //As of this writing, with xerial's sqlite jdbc connector, a timestamp is
        //stored as a column of type Integer, but the columnTypeName is TIMESTAMP, and the
        //value is a string representing a Long.
        if (columnTypeName.equals("TIMESTAMP")) {
            addAllCharacters(parseDateFromLongString(rs.getString(columnIndex)), handler);
        } else {
            addAllCharacters(Integer.toString(rs.getInt(columnIndex)), handler);
        }

    }

    private String parseDateFromLongString(String longString) throws SAXException {
        java.sql.Date d = new java.sql.Date(Long.parseLong(longString));
        return dateFormat.format(d);

    }
}
