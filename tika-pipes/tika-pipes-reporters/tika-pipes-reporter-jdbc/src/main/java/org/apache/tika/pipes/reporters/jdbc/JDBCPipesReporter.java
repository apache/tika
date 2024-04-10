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
package org.apache.tika.pipes.reporters.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesReporterBase;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.utils.StringUtils;

/**
 * This is an initial draft of a JDBCPipesReporter.  This will drop
 * the tika_status table with each run.  If you'd like different behavior,
 * please open a ticket on our JIRA!
 */
public class JDBCPipesReporter extends PipesReporterBase implements Initializable {

    private static final Logger LOG = LoggerFactory.getLogger(JDBCPipesReporter.class);
    private static final int DEFAULT_CACHE_SIZE = 100;
    private static final long DEFAULT_REPORT_WITHIN_MS = 10000;
    private static final int ARRAY_BLOCKING_QUEUE_SIZE = 1000;

    public static final String TABLE_NAME = "tika_status";

    private static final long MAX_WAIT_MILLIS = 120000;

    private long reportWithinMs = DEFAULT_REPORT_WITHIN_MS;

    private int cacheSize = DEFAULT_CACHE_SIZE;

    private String connectionString;

    private boolean createTable = true;

    private String tableName = TABLE_NAME;

    private String reportSql;

    private List<String> reportVariables;

    private Optional<String> postConnectionString = Optional.empty();
    private final ArrayBlockingQueue<IdStatusPair> queue =
            new ArrayBlockingQueue(ARRAY_BLOCKING_QUEUE_SIZE);
    CompletableFuture<Void> reportWorkerFuture;

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        super.initialize(params);
        if (StringUtils.isBlank(connectionString)) {
            throw new TikaConfigException("Must specify a connectionString");
        }
        if (reportVariables == null) {
            reportVariables = new ArrayList<>();
            reportVariables.add("id");
            reportVariables.add("status");
            reportVariables.add("timestamp");
        }
        if (reportSql == null) {
            reportSql = "insert into " + getTableName() + " (id, status, timestamp) values (?,?,?)";
        }
        ReportWorker reportWorker = new ReportWorker(connectionString, postConnectionString,
                queue, cacheSize, reportWithinMs);
        reportWorker.init();
        reportWorkerFuture = CompletableFuture.runAsync(reportWorker);
    }


    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }

    @Field
    public void setConnection(String connection) {
        this.connectionString = connection;
    }

    /**
     * Commit the reports if the cache is greater than or equal to this size.
     * <p/>
     * Default is {@link JDBCPipesReporter#DEFAULT_CACHE_SIZE}.
     * <p/>
     * The reports will be committed if the cache size
     * triggers reporting or if the amount of time since
     * last reported ({@link JDBCPipesReporter#reportWithinMs}) triggers reporting.
     * @param cacheSize
     */
    @Field
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    /**
     * The default is true. In a distributed setting with multiple
     * servers, this should be set to false, and you'll need to set up
     * the table on your own.
     * <p/>
     * <b>NOTE</b> The default behavior is to drop the table if it exists and
     * then create it. Make sure to set this to false if you do not want
     * to drop the table.
     * @param createTable
     */
    @Field
    public void setCreateTable(boolean createTable) {
        this.createTable = createTable;
    }

    /**
     * The default is {@link JDBCPipesReporter#TABLE_NAME}
     * @param tableName
     */
    @Field
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * This is the sql for the prepared statement to execute
     * to store the report record. the default is:
     * <code>insert into tika_status (id, status, timestamp) values (?,?,?)</code>
     *
     * This can be modified for specific dialects of SQL or to run an upsert, merge or update
     * instead of the default insert.
     *
     * Users need to coordinate this with {@link #setReportVariables(List)}
     * @param reportSql
     */
    @Field
    public void setReportSql(String reportSql) {
        this.reportSql = reportSql;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getReportVariables() {
        return reportVariables;
    }

    public String getReportSql() {
        return reportSql;
    }

    public boolean isCreateTable() {
        return createTable;
    }
    /**
     * ADVANCED: This is used to set the variables in the prepared statement for
     * the report. This needs to be coordinated with {@link #setReportSql(String)}.
     * The available variables are "id, status, timestamp". If you're modifying to an update
     * statement like "update table tika_status set status=?, timestamp=? where id = ?"
     * then the values for this would be ["status", "timestamp", "id"].
     * <p/>
     * The default for the insert is ["id", "status", "timestamp"]
     * @param variables
     */

    @Field
    public void setReportVariables(List<String> variables) {
        reportVariables = variables;
    }

    /**
     * Commit the reports if the amount of time elapsed since the last report commit
     * exceeds this value.
     * <p/>
     * Default is {@link JDBCPipesReporter#DEFAULT_REPORT_WITHIN_MS}.
     * <p/>
     * The reports will be committed if the cache size triggers reporting or if the amount of
     * time since last reported triggers reporting.
     * @param reportWithinMs
     */
    @Field
    public void setReportWithinMs(long reportWithinMs) {
        this.reportWithinMs = reportWithinMs;
    }

    /**
     * This sql will be called immediately after the connection is made. This was
     * initially added for setting pragmas on sqlite3, but may be used for other
     * connection configuration in other dbs. Note: This is called before the table is
     * created if it needs to be created.
     *
     * @param postConnection
     */
    @Field
    public void setPostConnection(String postConnection) {
        this.postConnectionString = Optional.of(postConnection);
    }

    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (! accept(result.getStatus())) {
            return;
        }
        try {
            queue.offer(new IdStatusPair(t.getId(), result.getStatus()),
                    MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //swallow
        }

    }

    @Override
    public void error(Throwable t) {
        LOG.error("reported error; all bets are off", t);
    }

    @Override
    public void error(String msg) {
        LOG.error("reported error; all bets are off: {}", msg);
    }

    @Override
    public void close() throws IOException {
        try {
            queue.offer(IdStatusPair.END_SEMAPHORE, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return;
        }

        try {
            reportWorkerFuture.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            LOG.error("problem closing", e);
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            LOG.error("timeout closing", e);
        } catch (InterruptedException e) {
            //
        } finally {
            reportWorkerFuture.cancel(true);
        }
    }

    private static class IdStatusPair {

        static IdStatusPair END_SEMAPHORE = new IdStatusPair(null, null);
        private final String id;
        private final PipesResult.STATUS status;

        public IdStatusPair(String id, PipesResult.STATUS status) {
            this.id = id;
            this.status = status;
        }

        @Override
        public String toString() {
            return "KeyStatusPair{" + "id='" + id + '\'' + ", status=" + status + '}';
        }
    }

    private class ReportWorker implements Runnable {

        private static final int MAX_TRIES = 3;
        private final String connectionString;
        private final Optional<String> postConnectionString;
        private final ArrayBlockingQueue<IdStatusPair> queue;
        private final int cacheSize;
        private final long reportWithinMs;

        List<IdStatusPair> cache = new ArrayList<>();
        private Connection connection;
        private PreparedStatement insert;


        public ReportWorker(String connectionString,
                            Optional<String> postConnectionString,
                            ArrayBlockingQueue<IdStatusPair> queue, int cacheSize,
                            long reportWithinMs) {
            this.connectionString = connectionString;
            this.postConnectionString = postConnectionString;
            this.queue = queue;
            this.cacheSize = cacheSize;
            this.reportWithinMs = reportWithinMs;
        }

        public void init() throws TikaConfigException {
            try {
                createConnection();
                if (isCreateTable()) {
                    createTable();
                }
                //table must exist for this to work
                createPreparedStatement();
            } catch (SQLException e) {
                throw new TikaConfigException("Problem creating connection, etc", e);
            }
        }

        @Override
        public void run() {
            long lastReported = System.currentTimeMillis();
            while (true) {
                IdStatusPair p = null;
                try {
                    p = queue.poll(reportWithinMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                if (p != null) {
                    if (p == IdStatusPair.END_SEMAPHORE) {
                        shutdownNow();
                        return;
                    }
                    cache.add(p);
                }
                long elapsed = System.currentTimeMillis() - lastReported;

                if (cache.size() >= cacheSize || elapsed > reportWithinMs) {
                    try {
                        reportNow();
                        lastReported = System.currentTimeMillis();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }

        private void shutdownNow() {
            LOG.trace("received end semaphore");
            try {
                reportNow();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                return;
            }
            LOG.trace("about to close");
            try {
                insert.close();
            } catch (SQLException e) {
                LOG.warn("problem shutting down insert statement in reporter", e);
            }
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.warn("problem shutting down connection in reporter", e);
            }
            LOG.trace("successfully closed resources");
        }

        private void reportNow() throws SQLException, InterruptedException {
            int attempt = 0;
            while (++attempt < MAX_TRIES) {
                try {
                    for (IdStatusPair p : cache) {
                        insert.clearParameters();
                        updateInsert(insert, p.id, p.status.name(), Timestamp.from(Instant.now()));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                    LOG.debug("writing {} " + cache.size());
                    cache.clear();
                    return;
                } catch (SQLException e) {
                    LOG.warn("problem writing to the db. Will try to reconnect", e);
                    reconnect();
                }
            }
        }

        private void updateInsert(PreparedStatement insert, String id,
                                  String status,
                                  Timestamp timestamp) throws SQLException {
            //there has to be a more efficient way than this
            for (int i = 0; i < reportVariables.size(); i++) {
                String name = reportVariables.get(i);
                if (name.equals("timestamp")) {
                    insert.setTimestamp(i + 1, timestamp);
                } else if (name.equals("id")) {
                    insert.setString(i + 1, id);
                } else if (name.equals("status")) {
                    insert.setString(i + 1, status);
                } else {
                    throw new IllegalArgumentException("I expected one of (id, status, timestamp)" +
                            ", but I got: " + name);
                }
            }

        }

        private void createTable() throws SQLException {
            try (Statement st = connection.createStatement()) {
                String sql = "drop table if exists " + getTableName();
                st.execute(sql);
                sql = "create table " + getTableName() + " (id varchar(1024), status varchar(32), " +
                        "timestamp timestamp with time zone)";
                st.execute(sql);
            }
        }

        private void reconnect() throws SQLException, InterruptedException {
            int attempts = 0;
            SQLException ex = null;
            while (++attempts < 3) {
                try {
                    tryClose();
                    createConnection();
                    createPreparedStatement();
                    LOG.debug("success reconnecting after {} attempts", attempts);
                    return;
                } catch (SQLException e) {
                    LOG.warn("problem reconnecting", e);
                    //if there's a failure, wait 30 seconds
                    //and hope the db is back up.
                    Thread.sleep(30000);
                    ex = e;
                }
            }
            throw ex;
        }

        private void tryClose() {
            if (insert != null) {
                try {
                    insert.close();
                } catch (SQLException e) {
                    LOG.warn("exception closing insert statement", insert);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    LOG.warn("exception closing connection", e);
                }
            }
        }

        private void createConnection() throws SQLException {
            connection = DriverManager.getConnection(connectionString);
            if (postConnectionString.isPresent()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(postConnectionString.get());
                }
            }
        }

        private void createPreparedStatement() throws SQLException {
            insert = connection.prepareStatement(getReportSql());
        }
    }

}
