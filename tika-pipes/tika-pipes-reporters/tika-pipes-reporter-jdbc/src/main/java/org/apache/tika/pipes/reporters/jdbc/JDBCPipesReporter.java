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
    private static final int CACHE_SIZE = 100;
    private static final int ARRAY_BLOCKING_QUEUE_SIZE = 1000;

    public static final String TABLE_NAME = "tika_status";

    private static final long MAX_WAIT_MILLIS = 120000;

    private String connectionString;

    private Optional<String> postConnectionString = Optional.empty();
    private final ArrayBlockingQueue<KeyStatusPair> queue =
            new ArrayBlockingQueue(ARRAY_BLOCKING_QUEUE_SIZE);
    CompletableFuture<Void> reportWorkerFuture;

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        super.initialize(params);
        if (StringUtils.isBlank(connectionString)) {
            throw new TikaConfigException("Must specify a connectionString");
        }
        ReportWorker reportWorker = new ReportWorker(connectionString, postConnectionString, queue);
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
            queue.offer(new KeyStatusPair(t.getEmitKey().getEmitKey(), result.getStatus()),
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
            queue.offer(KeyStatusPair.END_SEMAPHORE, 60, TimeUnit.SECONDS);
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

    private static class KeyStatusPair {

        static KeyStatusPair END_SEMAPHORE = new KeyStatusPair(null, null);
        private final String emitKey;
        private final PipesResult.STATUS status;

        public KeyStatusPair(String emitKey, PipesResult.STATUS status) {
            this.emitKey = emitKey;
            this.status = status;
        }

        @Override
        public String toString() {
            return "KeyStatusPair{" + "emitKey='" + emitKey + '\'' + ", status=" + status + '}';
        }
    }

    private static class ReportWorker implements Runnable {

        private static final int MAX_TRIES = 3;
        private final String connectionString;
        private final Optional<String> postConnectionString;
        private final ArrayBlockingQueue<KeyStatusPair> queue;
        List<KeyStatusPair> cache = new ArrayList<>();
        private Connection connection;
        private PreparedStatement insert;

        public ReportWorker(String connectionString,
                            Optional<String> postConnectionString,
                            ArrayBlockingQueue<KeyStatusPair> queue) {
            this.connectionString = connectionString;
            this.postConnectionString = postConnectionString;
            this.queue = queue;
        }

        public void init() throws TikaConfigException {
            try {
                createConnection();
                createTable();
                createPreparedStatement();
            } catch (SQLException e) {
                throw new TikaConfigException("Problem creating connection, etc", e);
            }
        }

        @Override
        public void run() {
            while (true) {
                //blocking
                KeyStatusPair p = null;
                try {
                    p = queue.take();
                } catch (InterruptedException e) {
                    return;
                }
                if (p == KeyStatusPair.END_SEMAPHORE) {
                    shutdownNow();
                    return;
                }
                cache.add(p);
                if (cache.size() >= CACHE_SIZE) {
                    try {
                        reportNow();
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
                    for (KeyStatusPair p : cache) {
                        insert.clearParameters();
                        insert.setString(1, p.emitKey);
                        insert.setString(2, p.status.name());
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

        private void createTable() throws SQLException {
            try (Statement st = connection.createStatement()) {
                String sql = "drop table if exists " + TABLE_NAME;
                st.execute(sql);
                sql = "create table " + TABLE_NAME + " (path varchar(1024), status varchar(32))";
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
            String sql = "insert into " + TABLE_NAME + " (path, status) values (?,?)";
            insert = connection.prepareStatement(sql);
        }
    }

}
