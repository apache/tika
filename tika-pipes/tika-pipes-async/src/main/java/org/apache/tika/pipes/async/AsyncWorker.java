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
package org.apache.tika.pipes.async;

import static org.apache.tika.pipes.async.AsyncProcessor.TIKA_ASYNC_CONFIG_FILE_KEY;
import static org.apache.tika.pipes.async.AsyncProcessor.TIKA_ASYNC_JDBC_KEY;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.pipes.FetchEmitTuple;

/**
 * This controls monitoring of the AsyncWorkerProcess
 * and updates to the db on crashes etc.
 */
public class AsyncWorker implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncWorker.class);


    private final String connectionString;
    private final int workerId;
    private final Path tikaConfigPath;
    private final Connection connection;
    private final PreparedStatement finished;
    private final PreparedStatement restarting;
    private final PreparedStatement selectActiveTasks;
    private final PreparedStatement insertErrorLog;
    private final PreparedStatement resetStatus;
    //TODO: make this configurable
    private final int maxRetries = 2;

    public AsyncWorker(Connection connection, String connectionString, int workerId,
                       Path tikaConfigPath) throws SQLException {
        this.connectionString = connectionString;
        this.workerId = workerId;
        this.tikaConfigPath = tikaConfigPath;
        this.connection = connection;
        String sql = "update workers set status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.HAS_SHUTDOWN.ordinal() +
                " where worker_id = (" + workerId + ")";
        finished = connection.prepareStatement(sql);

        sql = "update workers set status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.RESTARTING.ordinal() +
                " where worker_id = (" + workerId + ")";
        restarting = connection.prepareStatement(sql);
        //this checks if the process was able to reset the status
        sql = "select id, retry, json from task_queue where worker_id=" + workerId +
                " and status=" + AsyncWorkerProcess.TASK_STATUS_CODES.IN_PROCESS.ordinal();
        selectActiveTasks = connection.prepareStatement(sql);

        //if not, this is called to insert into the error log
        insertErrorLog = prepareInsertErrorLog(connection);

        //and this is called to reset the status on error
        resetStatus = prepareReset(connection);
    }

    static void reportAndReset(AsyncTask task, AsyncWorkerProcess.ERROR_CODES errorCode,
                               PreparedStatement insertErrorLog, PreparedStatement resetStatus,
                               Logger logger) {
        try {
            insertErrorLog.clearParameters();
            insertErrorLog.setLong(1, task.getTaskId());
            insertErrorLog.setString(2, task.getFetchKey().getFetchKey());
            insertErrorLog.setInt(3, task.getRetry());
            insertErrorLog.setByte(4, (byte) errorCode.ordinal());
            insertErrorLog.execute();
        } catch (SQLException e) {
            logger.error("Can't update error log", e);
        }

        try {
            resetStatus.clearParameters();
            resetStatus.setByte(1, (byte) AsyncWorkerProcess.TASK_STATUS_CODES.AVAILABLE.ordinal());
            resetStatus.setShort(2, (short) (task.getRetry() + 1));
            resetStatus.setLong(3, task.getTaskId());
            resetStatus.execute();
        } catch (SQLException e) {
            logger.error("Can't reset try status", e);
        }
    }

    static PreparedStatement prepareInsertErrorLog(Connection connection) throws SQLException {
        //if not, this is called to insert into the error log
        return connection.prepareStatement(
                "insert into error_log (task_id, fetch_key, time_stamp, retry, error_code) " +
                        " values (?,?,CURRENT_TIMESTAMP(),?,?)");
    }

    static PreparedStatement prepareReset(Connection connection) throws SQLException {
        //and this is called to reset the status on error
        return connection.prepareStatement(
                "update task_queue set " + "status=?, " + "time_stamp=CURRENT_TIMESTAMP(), " +
                        "retry=? " + "where id=?");
    }

    @Override
    public Integer call() throws Exception {
        Process p = null;
        try {
            p = start();
            int restarts = 0;
            while (true) {
                boolean finished = p.waitFor(60, TimeUnit.SECONDS);
                if (finished) {
                    int exitValue = p.exitValue();
                    if (exitValue == 0) {
                        LOG.debug("forked worker process finished with exitValue=0");
                        return 1;
                    }
                    reportCrash(++restarts, exitValue);
                    p = start();
                }
            }
        } finally {
            if (p != null) {
                p.destroyForcibly();
            }
            finished.execute();
        }
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    private Process start() throws IOException {
        String[] args = new String[]{"java", "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"),
                "org.apache.tika.pipes.async.AsyncWorkerProcess", Integer.toString(workerId)};
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().put(TIKA_ASYNC_JDBC_KEY, connectionString);
        pb.environment()
                .put(TIKA_ASYNC_CONFIG_FILE_KEY, tikaConfigPath.toAbsolutePath().toString());
        pb.inheritIO();
        return pb.start();
    }

    private void reportCrash(int numRestarts, int exitValue) throws SQLException, IOException {
        LOG.warn("worker id={} terminated, exitValue={}", workerId, exitValue);
        restarting.execute();
        List<AsyncTask> activeTasks = new ArrayList<>();
        try (ResultSet rs = selectActiveTasks.executeQuery()) {
            long taskId = rs.getLong(1);
            short retry = rs.getShort(2);
            String json = rs.getString(3);
            FetchEmitTuple tuple = JsonFetchEmitTuple.fromJson(new StringReader(json));
            activeTasks.add(new AsyncTask(taskId, retry, tuple));
        }
        if (activeTasks.size() == 0) {
            LOG.debug("worker reset active tasks, nothing extra to report");
            return;
        }

        if (activeTasks.size() > 1) {
            LOG.warn("more than one active task? this should never happen!");
        }

        for (AsyncTask t : activeTasks) {
            reportAndReset(t, AsyncWorkerProcess.ERROR_CODES.UNKNOWN_PARSE, insertErrorLog,
                    resetStatus, LOG);
        }

    }
}
