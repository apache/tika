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

import static org.apache.tika.config.TikaConfig.mustNotBeEmpty;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.apache.tika.utils.StringUtils;

/**
 * Iterates through a the results from a sql call via jdbc. This adds all columns
 * (except for the 'fetchKeyColumn' and 'emitKeyColumn', if specified)
 * to the metadata object.
 * <p>
 *  <ul>
 *      <li>If a 'fetchKeyColumn' is specified, this will use that
 *      column's value as the fetchKey.</li>
 *      <li>If no 'fetchKeyColumn' is specified, this will send the
 *      metadata from the other columns.</li>
 *      <li>The 'fetchKeyColumn' value is not added to the metadata.</li>
 *  </ul>
 * <p>
 *  <ul>
 *      <li>An 'emitKeyColumn' must be specified</li>
 *      <li>The 'emitKeyColumn' value is not added to the metadata.</li>
 *  </ul>
 */
public class JDBCFetchIterator extends FetchIterator implements Initializable {


    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCFetchIterator.class);

    private String fetchKeyColumn;
    private String emitKeyColumn;
    private String connection;
    private String select;

    private Connection db;

    @Field
    public void setFetchKeyColumn(String fetchKeyColumn) {
        this.fetchKeyColumn = fetchKeyColumn;
    }

    @Field
    public void setEmitKeyColumn(String fetchKeyColumn) {
        this.emitKeyColumn = fetchKeyColumn;
    }

    @Field
    public void setConnection(String connection) {
        this.connection = connection;
    }

    public String getSelect() {
        return select;
    }

    @Field
    public void setSelect(String select) {
        this.select = select;
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        String fetcherName = getFetcherName();
        String emitterName = getEmitterName();
        FetchEmitKeyIndices fetchEmitKeyIndices = null;
        List<String> headers = new ArrayList<>();
        int rowCount = 0;
        LOGGER.debug("select: {}", select);
        try (Statement st = db.createStatement()) {
            try (ResultSet rs = st.executeQuery(select)) {
                while (rs.next()) {
                    if (headers.size() == 0) {
                        fetchEmitKeyIndices = loadHeaders(rs.getMetaData(), headers);
                        checkFetchEmitValidity(fetcherName, emitterName, fetchEmitKeyIndices,
                                headers);
                    }
                    try {
                        processRow(fetcherName, emitterName, headers, fetchEmitKeyIndices, rs);
                    } catch (SQLException e) {
                        LOGGER.warn("Failed to insert: " + rs, e);
                    }
                    rowCount++;
                    if (rowCount % 1000 == 0) {
                        LOGGER.info("added " + rowCount + " rows to the queue");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("problem initializing connection and selecting", e);
            throw new IOException(e);
        } finally {
            try {
                db.close();
            } catch (SQLException e) {
                LOGGER.warn("failed to close connection", e);
            }
        }
    }

    private void checkFetchEmitValidity(String fetcherName, String emitterName,
                                        FetchEmitKeyIndices fetchEmitKeyIndices,
                                        List<String> headers) throws IOException {

        if (!StringUtils.isBlank(fetchKeyColumn) && fetchEmitKeyIndices.fetchKeyIndex < 0) {
            throw new IOException(
                    new TikaConfigException("Couldn't find column: " + fetchKeyColumn));
        }
        if (!StringUtils.isBlank(emitKeyColumn) && fetchEmitKeyIndices.emitKeyIndex < 0) {
            throw new IOException(
                    new TikaConfigException("Couldn't find column: " + emitKeyColumn));
        }
    }

    private void processRow(String fetcherName, String emitterName, List<String> headers,
                            FetchEmitKeyIndices fetchEmitKeyIndices, ResultSet rs)
            throws SQLException, TimeoutException, InterruptedException {
        Metadata metadata = new Metadata();
        String fetchKey = "";
        String emitKey = "";
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            if (i == fetchEmitKeyIndices.fetchKeyIndex) {
                fetchKey = getString(i, rs);
                if (fetchKey == null) {
                    LOGGER.debug("fetchKey is empty for record " + toString(rs));
                }
                fetchKey = (fetchKey == null) ? "" : fetchKey;
                continue;
            }
            if (i == fetchEmitKeyIndices.emitKeyIndex) {
                emitKey = getString(i, rs);
                if (emitKey == null) {
                    LOGGER.debug("emitKey is empty for record " + toString(rs));
                }
                emitKey = (emitKey == null) ? "" : emitKey;
                continue;
            }
            String val = getString(i, rs);
            if (val != null) {
                metadata.set(headers.get(i - 1), val);
            }
        }

        tryToAdd(new FetchEmitTuple(new FetchKey(fetcherName, fetchKey),
                new EmitKey(emitterName, emitKey), metadata, getOnParseException()));
    }

    private String toString(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            String val = rs.getString(i);
            val = (val == null) ? "" : val;
            val = (val.length() > 100) ? val.substring(0, 100) : val;
            sb.append(rs.getMetaData().getColumnLabel(i) + ":" + val + "\n");
        }
        return sb.toString();
    }

    private String getString(int i, ResultSet rs) throws SQLException {
        //TODO: improve this later with special handling for numerals/dates/timestamps, etc
        String val = rs.getString(i);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }


    private FetchEmitKeyIndices loadHeaders(ResultSetMetaData metaData, List<String> headers)
            throws SQLException {
        int fetchKeyIndex = -1;
        int emitKeyIndex = -1;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            if (metaData.getColumnLabel(i).equalsIgnoreCase(fetchKeyColumn)) {
                fetchKeyIndex = i;
            }
            if (metaData.getColumnLabel(i).equalsIgnoreCase(emitKeyColumn)) {
                emitKeyIndex = i;
            }
            headers.add(metaData.getColumnLabel(i));
        }
        return new FetchEmitKeyIndices(fetchKeyIndex, emitKeyIndex);
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
        mustNotBeEmpty("emitterName", this.getEmitterName());
        mustNotBeEmpty("emitKeyColumn", this.emitKeyColumn);

        if (StringUtils.isBlank(getFetcherName()) && !StringUtils.isBlank(fetchKeyColumn)) {
            throw new TikaConfigException(
                    "If you specify a 'fetchKeyColumn', you must specify a 'fetcherName'");
        }

        if (StringUtils.isEmpty(fetchKeyColumn)) {
            LOGGER.info("no fetch key column has been specified");
        }

    }

    private static class FetchEmitKeyIndices {
        private final int fetchKeyIndex;
        private final int emitKeyIndex;

        public FetchEmitKeyIndices(int fetchKeyIndex, int emitKeyIndex) {
            this.fetchKeyIndex = fetchKeyIndex;
            this.emitKeyIndex = emitKeyIndex;
        }

        public boolean shouldSkip(int index) {
            return fetchKeyIndex == index || emitKeyIndex == index;
        }
    }
}
