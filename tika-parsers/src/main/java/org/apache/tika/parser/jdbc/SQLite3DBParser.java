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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.tika.io.IOExceptionWithCause;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.sqlite.SQLiteConfig;

/**
 * This is the implementation of the db parser for SQLite.
 * <p>
 * This parser is internal only; it should not be registered in the services
 * file or configured in the TikaConfig xml file.
 */
class SQLite3DBParser extends AbstractDBParser {

    protected static final String SQLITE_CLASS_NAME = "org.sqlite.JDBC";

    /**
     *
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
        try{
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
        File dbFile = TikaInputStream.get(is).getFile();
        return "jdbc:sqlite:"+dbFile.getAbsolutePath();
    }

    @Override
    protected String getJDBCClassName() {
        return SQLITE_CLASS_NAME;
    }

    @Override
    protected List<String> getTableNames(Connection connection, Metadata metadata,
                                         ParseContext context) throws SQLException {
        List<String> tableNames = new LinkedList<String>();

        Statement st = null;
        try {
            st = connection.createStatement();
            String sql = "SELECT name FROM sqlite_master WHERE type='table'";
            ResultSet rs = st.executeQuery(sql);

            while (rs.next()) {
                tableNames.add(rs.getString(1));
            }
        } finally {
            if (st != null)
                st.close();
        }
        return tableNames;
    }

    @Override
    public JDBCTableReader getTableReader(Connection connection, String tableName, ParseContext context) {
        return new SQLite3TableReader(connection, tableName, context);
    }
}
