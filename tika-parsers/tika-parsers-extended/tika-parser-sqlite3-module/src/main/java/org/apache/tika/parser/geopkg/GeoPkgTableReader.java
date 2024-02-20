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
package org.apache.tika.parser.geopkg;


import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.sqlite3.SQLite3TableReader;


/**
 * Concrete class for GeoPkg parsing.  This overrides blob handling to skip "geom" and "data"
 * columns
 * <p/>
 * For now, this silently skips cells of type CLOB, because xerial's jdbc connector
 * does not currently support them.
 */
class GeoPkgTableReader extends SQLite3TableReader {

    private final Set<String> ignoreBlobColumns;

    public GeoPkgTableReader(Connection connection, String tableName,
                             EmbeddedDocumentUtil embeddedDocumentUtil, Set<String> ignoreBlobColumns) {
        super(connection, tableName, embeddedDocumentUtil);
        this.ignoreBlobColumns = ignoreBlobColumns;
    }



    @Override
    protected void handleBlob(String tableName, String columnName, int rowNum, ResultSet resultSet,
                              int columnIndex, ContentHandler handler, ParseContext context)
            throws SQLException, IOException, SAXException {
        if (ignoreBlobColumns.contains(columnName)) {
            Attributes attrs = new AttributesImpl();
            ((AttributesImpl) attrs).addAttribute("", "type", "type", "CDATA", "blob");
            ((AttributesImpl) attrs)
                    .addAttribute("", "column_name", "column_name", "CDATA", columnName);
            ((AttributesImpl) attrs).addAttribute("", "row_number", "row_number", "CDATA",
                    Integer.toString(rowNum));
            handler.startElement("", "span", "span", attrs);
            handler.endElement("", "span", "span");
            return;
        }
        super.handleBlob(tableName, columnName, rowNum, resultSet, columnIndex, handler, context);
    }
}
