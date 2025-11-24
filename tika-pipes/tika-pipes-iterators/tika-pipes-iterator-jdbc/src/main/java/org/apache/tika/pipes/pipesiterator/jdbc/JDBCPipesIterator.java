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
package org.apache.tika.pipes.pipesiterator.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.HandlerConfig;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;
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
public class JDBCPipesIterator extends PipesIteratorBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(JDBCPipesIterator.class);

    private final JDBCPipesIteratorConfig config;
    private final Connection db;

    private JDBCPipesIterator(JDBCPipesIteratorConfig config, ExtensionConfig extensionConfig) throws TikaConfigException {
        super(extensionConfig);
        this.config = config;

        if (StringUtils.isBlank(config.getConnection())) {
            throw new TikaConfigException("connection must not be empty");
        }
        if (StringUtils.isBlank(config.getSelect())) {
            throw new TikaConfigException("select must not be empty");
        }

        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherName = baseConfig.fetcherId();
        String emitterName = baseConfig.emitterId();

        if (StringUtils.isBlank(fetcherName) && !StringUtils.isBlank(config.getFetchKeyColumn())) {
            throw new TikaConfigException("If you specify a 'fetchKeyColumn', you must specify a 'fetcherPluginId'");
        }

        if (StringUtils.isBlank(emitterName) && !StringUtils.isBlank(config.getEmitKeyColumn())) {
            throw new TikaConfigException("If you specify an 'emitKeyColumn', you must specify an 'emitterPluginId'");
        }

        if (StringUtils.isBlank(emitterName) && StringUtils.isBlank(fetcherName)) {
            LOGGER.warn("no fetcher or emitter specified?!");
        }

        if (StringUtils.isEmpty(config.getFetchKeyColumn())) {
            LOGGER.warn("no fetch key column has been specified");
        }

        if (config.getFetchSize() == 0) {
            throw new TikaConfigException("Can't set fetch size == 0");
        }
        if (config.getFetchSize() < 0) {
            LOGGER.info("fetch size < 0; no fetch size will be set");
        }

        // Initialize DB connection
        try {
            this.db = DriverManager.getConnection(config.getConnection());
        } catch (SQLException e) {
            throw new TikaConfigException("couldn't connect to db", e);
        }
    }

    public static JDBCPipesIterator build(ExtensionConfig extensionConfig) throws IOException, TikaConfigException {
        JDBCPipesIteratorConfig config = JDBCPipesIteratorConfig.load(extensionConfig.jsonConfig());
        return new JDBCPipesIterator(config, extensionConfig);
    }

    @Override
    protected void enqueue() throws InterruptedException, IOException, TimeoutException {
        PipesIteratorBaseConfig baseConfig = config.getBaseConfig();
        String fetcherPluginId = baseConfig.fetcherId();
        String emitterName = baseConfig.emitterId();
        FetchEmitKeyIndices fetchEmitKeyIndices = null;
        List<String> headers = new ArrayList<>();
        int rowCount = 0;
        HandlerConfig handlerConfig = baseConfig.handlerConfig();
        LOGGER.debug("select: {}", config.getSelect());
        try (Statement st = db.createStatement()) {
            if (config.getFetchSize() > 0) {
                st.setFetchSize(config.getFetchSize());
            }
            if (config.getQueryTimeoutSeconds() > 0) {
                st.setQueryTimeout(config.getQueryTimeoutSeconds());
            }
            try (ResultSet rs = st.executeQuery(config.getSelect())) {
                while (rs.next()) {
                    if (headers.size() == 0) {
                        fetchEmitKeyIndices = loadHeaders(rs.getMetaData(), headers);
                        checkFetchEmitValidity(fetcherPluginId, emitterName, fetchEmitKeyIndices, headers);
                    }
                    try {
                        processRow(fetcherPluginId, emitterName, headers, fetchEmitKeyIndices, rs, handlerConfig, baseConfig);
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

    private void checkFetchEmitValidity(String fetcherPluginId, String emitterName, FetchEmitKeyIndices fetchEmitKeyIndices, List<String> headers) throws IOException {
        if (!StringUtils.isBlank(config.getFetchKeyColumn()) && fetchEmitKeyIndices.fetchKeyIndex < 0) {
            throw new IOException(new TikaConfigException("Couldn't find fetchkey column: " + config.getFetchKeyColumn()));
        }
        if (!StringUtils.isBlank(config.getEmitKeyColumn()) && fetchEmitKeyIndices.emitKeyIndex < 0) {
            throw new IOException(new TikaConfigException("Couldn't find emitKey column: " + config.getEmitKeyColumn()));
        }
        if (!StringUtils.isBlank(config.getIdColumn()) && fetchEmitKeyIndices.idIndex < 0) {
            throw new IOException(new TikaConfigException("Couldn't find id column: " + config.getIdColumn()));
        }
        if (StringUtils.isBlank(config.getIdColumn())) {
            LOGGER.warn("id column is blank, using fetchkey column as the id column");
            fetchEmitKeyIndices.idIndex = fetchEmitKeyIndices.fetchKeyIndex;
        }
    }

    private void processRow(String fetcherPluginId, String emitterName, List<String> headers,
                            FetchEmitKeyIndices fetchEmitKeyIndices, ResultSet rs,
                            HandlerConfig handlerConfig, PipesIteratorBaseConfig baseConfig)
            throws SQLException, TimeoutException, InterruptedException {
        Metadata metadata = new Metadata();
        String fetchKey = "";
        long fetchStartRange = -1l;
        long fetchEndRange = -1l;
        String emitKey = "";
        String id = "";
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            //a single column can be the fetch key and the emit key, etc.
            boolean isUsed = false;
            if (i == fetchEmitKeyIndices.fetchKeyIndex) {
                fetchKey = getString(i, rs);
                if (StringUtils.isBlank(fetchKey)) {
                    LOGGER.debug("fetchKey is empty for record " + toString(rs));
                }
                fetchKey = (fetchKey == null) ? "" : fetchKey;
                isUsed = true;
            }
            if (i == fetchEmitKeyIndices.emitKeyIndex) {
                emitKey = getString(i, rs);
                if (StringUtils.isBlank(emitKey)) {
                    LOGGER.debug("emitKey is empty for record " + toString(rs));
                }
                emitKey = (emitKey == null) ? "" : emitKey;
                isUsed = true;
            }
            if (i == fetchEmitKeyIndices.idIndex) {
                id = getString(i, rs);
                if (StringUtils.isBlank(id)) {
                    LOGGER.warn("id is empty for record " + toString(rs));
                }
                id = (id == null) ? "" : id;
                isUsed = true;
            }
            if (i == fetchEmitKeyIndices.fetchStartRangeIndex) {
                fetchStartRange = getLong(i, rs);
                isUsed = true;
            }
            if (i == fetchEmitKeyIndices.fetchEndRangeIndex) {
                fetchEndRange = getLong(i, rs);
                isUsed = true;
            }
            if (!isUsed) {
                String val = getString(i, rs);
                if (!StringUtils.isBlank(val)) {
                    metadata.set(headers.get(i - 1), val);
                }
            }
        }
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, handlerConfig);
        tryToAdd(new FetchEmitTuple(id, new FetchKey(fetcherPluginId, fetchKey, fetchStartRange, fetchEndRange), new EmitKey(emitterName, emitKey), metadata, parseContext,
                baseConfig.onParseException()));
    }

    private String toString(ResultSet rs) throws SQLException {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
            String val = rs.getString(i);
            val = (val == null) ? "" : val;
            val = (val.length() > 100) ? val.substring(0, 100) : val;
            sb.append(rs.getMetaData().getColumnLabel(i)).append(":").append(val).append("\n");
        }
        return sb.toString();
    }

    private String getString(int i, ResultSet rs) throws SQLException {
        String val = rs.getString(i);
        if (rs.wasNull()) {
            return null;
        }
        return val;
    }

    private long getLong(int i, ResultSet rs) throws SQLException {
        long val = rs.getLong(i);
        if (rs.wasNull()) {
            return -1l;
        }
        return val;
    }

    private FetchEmitKeyIndices loadHeaders(ResultSetMetaData metaData, List<String> headers) throws SQLException {
        int idIndex = -1;
        int fetchKeyIndex = -1;
        int fetchKeyStartRangeIndex = -1;
        int fetchKeyEndRangeIndex = -1;
        int emitKeyIndex = -1;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String colLabel = metaData.getColumnLabel(i);
            if (colLabel.equalsIgnoreCase(config.getFetchKeyColumn())) {
                fetchKeyIndex = i;
            }
            if (colLabel.equalsIgnoreCase(config.getFetchKeyRangeStartColumn())) {
                fetchKeyStartRangeIndex = i;
            }
            if (colLabel.equalsIgnoreCase(config.getFetchKeyRangeEndColumn())) {
                fetchKeyEndRangeIndex = i;
            }
            if (colLabel.equalsIgnoreCase(config.getEmitKeyColumn())) {
                emitKeyIndex = i;
            }
            if (colLabel.equalsIgnoreCase(config.getIdColumn())) {
                idIndex = i;
            }
            headers.add(metaData.getColumnLabel(i));
        }
        return new FetchEmitKeyIndices(idIndex, fetchKeyIndex, fetchKeyStartRangeIndex, fetchKeyEndRangeIndex, emitKeyIndex);
    }

    private static class FetchEmitKeyIndices {
        private final int fetchKeyIndex;
        private final int fetchStartRangeIndex;
        private final int fetchEndRangeIndex;
        private final int emitKeyIndex;
        private int idIndex;

        public FetchEmitKeyIndices(int idIndex, int fetchKeyIndex, int fetchStartRangeIndex, int fetchEndRangeIndex, int emitKeyIndex) {
            this.idIndex = idIndex;
            this.fetchKeyIndex = fetchKeyIndex;
            this.fetchStartRangeIndex = fetchStartRangeIndex;
            this.fetchEndRangeIndex = fetchEndRangeIndex;
            this.emitKeyIndex = emitKeyIndex;
        }

        public boolean shouldSkip(int index) {
            return idIndex == index || fetchKeyIndex == index || fetchStartRangeIndex == index || fetchEndRangeIndex == index || emitKeyIndex == index;
        }
    }
}
