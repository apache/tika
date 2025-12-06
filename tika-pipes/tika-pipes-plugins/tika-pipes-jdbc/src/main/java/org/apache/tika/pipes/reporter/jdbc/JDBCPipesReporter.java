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
package org.apache.tika.pipes.reporter.jdbc;

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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.reporters.PipesReporterBase;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.utils.StringUtils;

/**
 * This is an initial draft of a JDBCPipesReporter.  This will drop
 * the tika_status table with each run.  If you'd like different behavior,
 * please open a ticket on our JIRA!
 */
public class JDBCPipesReporter extends PipesReporterBase {

    private static final Logger LOG = LoggerFactory.getLogger(JDBCPipesReporter.class);
    static final int DEFAULT_CACHE_SIZE = 100;
    static final long DEFAULT_REPORT_WITHIN_MS = 10000;

    private static final int ARRAY_BLOCKING_QUEUE_SIZE = 1000;

    public static final String TABLE_NAME = "tika_status";

    private static final long MAX_WAIT_MILLIS = 120000;

    public static JDBCPipesReporter build(ExtensionConfig pluginConfig) throws TikaConfigException, IOException {
        JDBCPipesReporterConfig config = JDBCPipesReporterConfig.load(pluginConfig.json());
        return new JDBCPipesReporter(pluginConfig, config);
    }


    private final JDBCPipesReporterConfig config;
    private final List<String> reportVariables = new ArrayList<>();
    private String reportSql = null;

    private final ArrayBlockingQueue<IdStatusPair> queue =
            new ArrayBlockingQueue(ARRAY_BLOCKING_QUEUE_SIZE);

    CompletableFuture<Void> reportWorkerFuture;

    public JDBCPipesReporter(ExtensionConfig pluginConfig, JDBCPipesReporterConfig config) throws TikaConfigException {
        super(pluginConfig, config.includes(), config.excludes());
        this.config = config;
        init();
    }

    private void init() throws TikaConfigException {
        if (StringUtils.isBlank(config.connectionString())) {
            throw new TikaConfigException("Must specify a connectionString");
        }
        if (config.reportVariables() == null || config.reportVariables().isEmpty()) {

            reportVariables.add("id");
            reportVariables.add("status");
            reportVariables.add("timestamp");
        } else {
            reportVariables.addAll(config.reportVariables());
        }
        if (config.reportSql() == null || config.reportSql().isBlank()) {
            reportSql = "insert into " + config.tableName() + " (id, status, timestamp) values (?,?,?)";
        }
        ReportWorker reportWorker = new ReportWorker(config, queue);
        reportWorker.init();
        reportWorkerFuture = CompletableFuture.runAsync(reportWorker);
    }


    @Override
    public void report(FetchEmitTuple t, PipesResult result, long elapsed) {
        if (! accept(result.status())) {
            return;
        }
        try {
            queue.offer(new IdStatusPair(t.getId(), result.status()),
                    MAX_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //swallow
        }

    }

    @Override
    public void report(TotalCountResult totalCountResult) {
        //no-op
    }

    @Override
    public boolean supportsTotalCount() {
        return false;
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
        private final PipesResult.RESULT_STATUS status;

        public IdStatusPair(String id, PipesResult.RESULT_STATUS status) {
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
        private final ArrayBlockingQueue<IdStatusPair> queue;
        private final JDBCPipesReporterConfig config;
        List<IdStatusPair> cache = new ArrayList<>();
        private Connection connection;
        private PreparedStatement insert;


        public ReportWorker(JDBCPipesReporterConfig config,
                            ArrayBlockingQueue<IdStatusPair> queue) {
            this.config = config;
            this.queue = queue;
        }

        public void init() throws TikaConfigException {
            try {
                createConnection();
                if (config.createTable()) {
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
                    p = queue.poll(config.reportWithinMs(), TimeUnit.MILLISECONDS);
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

                if (cache.size() >= config.cacheSize() || elapsed > config.reportWithinMs()) {
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
                    LOG.debug("writing {} ", cache.size());
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
                String sql = "drop table if exists " + config.tableName();
                st.execute(sql);
                sql = "create table " + config.tableName() + " (id varchar(1024), status varchar(32), " +
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
                    LOG.warn("exception closing insert statement {}", insert);
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
            connection = DriverManager.getConnection(config.connectionString());
            if (! StringUtils.isBlank(config.postConnectionSql())) {
                try (Statement st = connection.createStatement()) {
                    st.execute(config.postConnectionSql());
                }
            }
        }

        private void createPreparedStatement() throws SQLException {
            insert = connection.prepareStatement(reportSql);
        }
    }

}
