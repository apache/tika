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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sqlite.SQLiteConfig;

import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.jdbc.AbstractDBParser;
import org.apache.tika.parser.jdbc.JDBCTableReader;
import org.apache.tika.parser.sqlite3.SQLite3DBParser;

/**
 * This is the implementation of the db parser for SQLite.
 * <p/>
 * This parser is internal only; it should not be registered in the services
 * file or configured in the TikaConfig xml file.
 */
class GeoPkgDBParser extends SQLite3DBParser {

    @Override
    public JDBCTableReader getTableReader(Connection connection, String tableName,
                                          ParseContext context) {
        return new GeoPkgTableReader(connection, tableName, new EmbeddedDocumentUtil(context));
    }

    @Override
    protected JDBCTableReader getTableReader(Connection connection, String tableName,
                                             EmbeddedDocumentUtil embeddedDocumentUtil) {
        return new GeoPkgTableReader(connection, tableName, embeddedDocumentUtil);
    }
}
