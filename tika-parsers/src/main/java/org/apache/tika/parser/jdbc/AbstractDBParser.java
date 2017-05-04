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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOExceptionWithCause;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract class that handles iterating through tables within a database.
 */
abstract class AbstractDBParser extends AbstractParser {

    private final static byte[] EMPTY_BYTE_ARR = new byte[0];

    private Connection connection;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return null;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        connection = getConnection(stream, metadata, context);
        XHTMLContentHandler xHandler = null;
        List<String> tableNames = null;
        try {
            tableNames = getTableNames(connection, metadata, context);
        } catch (SQLException e) {
            try {
                close();
            } catch (SQLException sqlE) {
                //swallow
            }
            throw new IOExceptionWithCause(e);
        }
        for (String tableName : tableNames) {
            //add table names to parent metadata
            metadata.add(Database.TABLE_NAME, tableName);
        }
        xHandler = new XHTMLContentHandler(handler, metadata);
        xHandler.startDocument();

        try {
            for (String tableName : tableNames) {
                JDBCTableReader tableReader = getTableReader(connection, tableName, context);
                xHandler.startElement("table", "name", tableReader.getTableName());
                xHandler.startElement("thead");
                xHandler.startElement("tr");
                for (String header : tableReader.getHeaders()) {
                    xHandler.startElement("th");
                    xHandler.characters(header);
                    xHandler.endElement("th");
                }
                xHandler.endElement("tr");
                xHandler.endElement("thead");
                xHandler.startElement("tbody");
                while (tableReader.nextRow(xHandler, context)) {
                    //no-op
                }
                xHandler.endElement("tbody");
                xHandler.endElement("table");
            }
        } finally {
            try {
                close();
            } catch (IOException|SQLException e) {
                //swallow
            }
            if (xHandler != null) {
                xHandler.endDocument();
            }
        }
    }

    /**
     * Override this for any special handling of closing the connection.
     *
     * @throws java.sql.SQLException
     * @throws java.io.IOException
     */
    protected void close() throws SQLException, IOException {
        connection.close();
    }

    /**
     * Override this for special configuration of the connection, such as limiting
     * the number of rows to be held in memory.
     *
     * @param stream   stream to use
     * @param metadata metadata that could be used in parameterizing the connection
     * @param context  parsecontext that could be used in parameterizing the connection
     * @return connection
     * @throws java.io.IOException
     * @throws org.apache.tika.exception.TikaException
     */
    protected Connection getConnection(InputStream stream, Metadata metadata, ParseContext context) throws IOException, TikaException {
        String connectionString = getConnectionString(stream, metadata, context);

        Connection connection = null;
        try {
            Class.forName(getJDBCClassName());
        } catch (ClassNotFoundException e) {
            throw new TikaException(e.getMessage());
        }
        try {
            connection = DriverManager.getConnection(connectionString);
        } catch (SQLException e) {
            throw new IOExceptionWithCause(e);
        }
        return connection;
    }

    /**
     * Implement for db specific connection information, e.g. "jdbc:sqlite:/docs/mydb.db"
     * <p/>
     * Include any optimization settings, user name, password, etc.
     * <p/>
     *
     * @param stream       stream for processing
     * @param metadata     metadata might be useful in determining connection info
     * @param parseContext context to use to help create connectionString
     * @return connection string to be used by {@link #getConnection}.
     * @throws java.io.IOException
     */
    abstract protected String getConnectionString(InputStream stream,
                                                  Metadata metadata, ParseContext parseContext) throws IOException;

    /**
     * JDBC class name, e.g. org.sqlite.JDBC
     *
     * @return jdbc class name
     */
    abstract protected String getJDBCClassName();

    /**
     * Returns the names of the tables to process
     *
     * @param connection Connection to use to make the sql call(s) to get the names of the tables
     * @param metadata   Metadata to use (potentially) in decision about which tables to extract
     * @param context    ParseContext to use (potentially) in decision about which tables to extract
     * @return
     * @throws java.sql.SQLException
     */
    abstract protected List<String> getTableNames(Connection connection, Metadata metadata,
                                                  ParseContext context) throws SQLException;

    /**
     * Given a connection and a table name, return the JDBCTableReader for this db.
     *
     * @param connection
     * @param tableName
     * @return
     */
    abstract protected JDBCTableReader getTableReader(Connection connection, String tableName, ParseContext parseContext);

}
