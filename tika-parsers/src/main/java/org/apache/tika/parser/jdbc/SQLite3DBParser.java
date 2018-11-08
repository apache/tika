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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.sqlite.SQLiteConfig;

/**
 * This is the implementation of the db parser for SQLite.
 * <p/>
 * This parser is internal only; it should not be registered in the services
 * file or configured in the TikaConfig xml file.
 */
class SQLite3DBParser extends AbstractDBParser {

    protected static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC";
    //If the InputStream wasn't a TikaInputStream, copy to this tmp file
    Path tmpFile = null;

    /**
     * @param context context
     * @return null (always)
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return null;
    }

    @Override
    protected Connection getConnection(InputStream stream, Metadata metadata, ParseContext context) throws IOException {
        String connectionString = getConnectionString(stream, metadata, context);

        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new IOExceptionWithCause(e);
        }
        try {
            SQLiteConfig config = new SQLiteConfig();

            //good habit, but effectively meaningless here
            config.setReadOnly(true);
            connection = config.createConnection(connectionString);

        } catch (SQLException e) {
            throw new IOException(e.getMessage());
        }
        return connection;
    }

    @Override
    protected String getConnectionString(InputStream is, Metadata metadata, ParseContext context) throws IOException {
        TikaInputStream tis = TikaInputStream.cast(is);
        //if this is a TikaInputStream, use that to spool is to disk or
        //use original underlying file.
        if (tis != null) {
            Path dbFile = tis.getPath();
            return "jdbc:sqlite:" + dbFile.toAbsolutePath().toString();
        } else {
            //if not TikaInputStream, create own tmpResources.
            tmpFile = Files.createTempFile("tika-sqlite-tmp", "");
            Files.copy(is, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            return "jdbc:sqlite:"+ tmpFile.toAbsolutePath().toString();
        }
    }

    @Override
    public void close() throws SQLException, IOException {
        try {
            super.close();
        } finally {
            if (tmpFile != null) {
                Files.delete(tmpFile);
            }
        }
    }

    @Override
    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }

    @Override
    protected List<String> getTableNames(Connection connection, Metadata metadata,
                                         ParseContext context) throws SQLException {
        List<String> tableNames = new LinkedList<String>();

        try (Statement st = connection.createStatement()) {
            String sql = "SELECT name FROM sqlite_master WHERE type='table'";
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                tableNames.add(rs.getString(1));
            }
        }
        return tableNames;
    }

    @Override
    public JDBCTableReader getTableReader(Connection connection, String tableName, ParseContext context) {
        return new SQLite3TableReader(connection, tableName, new EmbeddedDocumentUtil(context));
    }

    @Override
    protected JDBCTableReader getTableReader(Connection connection, String tableName, EmbeddedDocumentUtil embeddedDocumentUtil) {
        return new SQLite3TableReader(connection, tableName, embeddedDocumentUtil);
    }
}
