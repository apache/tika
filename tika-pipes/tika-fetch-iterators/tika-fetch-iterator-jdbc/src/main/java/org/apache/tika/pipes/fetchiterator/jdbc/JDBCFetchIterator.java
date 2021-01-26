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
package org.apache.tika.pipes.fetchiterator.jdbc;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.FetchId;
import org.apache.tika.pipes.fetcher.FetchIdMetadataPair;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class JDBCFetchIterator extends FetchIterator implements Initializable {


    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCFetchIterator.class);

    private String fetchKeyColumn;
    private String connection;
    private String select;

    private Connection db;

    @Field
    public void setFetchKeyColumn(String fetchKeyColumn) {
        this.fetchKeyColumn = fetchKeyColumn;
    }

    @Field
    public void setConnection(String connection) {
        this.connection = connection;
    }

    @Field
    public void setSelect(String select) {
        this.select = select;
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        int fetchKeyIndex = -1;
        List<String> headers = new ArrayList<>();
        int rowCount = 0;
        LOGGER.debug("select: {}", select);
        try (Statement st = db.createStatement()) {
            try (ResultSet rs = st.executeQuery(select)) {
                while (rs.next()) {
                    if (headers.size() == 0) {
                        fetchKeyIndex = loadHeaders(rs.getMetaData(), headers);
                    }
                    try {
                        processRow(fetcherName, headers, fetchKeyIndex, rs);
                    } catch (SQLException e) {
                        LOGGER.warn("Failed to insert: "+rs, e);
                    }
                    rowCount++;
                    if (rowCount % 1000 == 0) {
                        LOGGER.info("added "+rowCount + " rows to the queue");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("problem initializing connection and selecting", e);
            throw new IOException(e);
        } finally {
            try {
                db.close();
            } catch (SQLException e){
                LOGGER.warn("failed to close connection", e);
            }
        }
    }

    private void processRow(String fetcherName, List<String> headers,
                            int fetchKeyIndex, ResultSet rs)
            throws SQLException, TimeoutException, InterruptedException {
        Metadata metadata = new Metadata();
        String fetchKey = null;
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            if (i == fetchKeyIndex) {
                fetchKey = getString(i, rs);
                fetchKey = (fetchKey == null) ? "" : fetchKey;
                continue;
            }
            String val = getString(i, rs);
            if (val != null) {
                metadata.set(headers.get(i - 1), val);
            }
        }

        tryToAdd(new FetchIdMetadataPair(new FetchId(fetcherName, fetchKey), metadata));
    }

    private String getString(int i, ResultSet rs) throws SQLException {
        //TODO: improve this later with special handling for numerals/dates/timestamps, etc
        String val = rs.getString(i);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }


    private int loadHeaders(ResultSetMetaData metaData, List<String> headers) throws SQLException {
        int fetchKeyIndex = -1;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnLabel(i).equalsIgnoreCase(fetchKeyColumn)) {
                fetchKeyIndex = i;
            }
            headers.add(metaData.getColumnLabel(i));
        }
        return fetchKeyIndex;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            db = DriverManager.getConnection(connection);
        } catch (SQLException e) {
            throw new TikaConfigException("couldn't connect to db", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        super.checkInitialization(problemHandler);
        mustNotBeEmpty("connection", this.connection);
        mustNotBeEmpty("select", this.select);
        if (StringUtils.isEmpty(fetchKeyColumn)) {
            LOGGER.info("no fetch key column has been specified");
        }
    }
}
