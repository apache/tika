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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncEmitter implements Callable<Integer> {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncEmitter.class);


    private final String connectionString;
    private final int emitterId;
    private final Path tikaConfigPath;
    private final Connection connection;
    private final PreparedStatement finished;
    private final PreparedStatement restarting;

    public AsyncEmitter(Connection connection, String connectionString, int emitterId,
                        Path tikaConfigPath) throws SQLException {
        this.connectionString = connectionString;
        this.emitterId = emitterId;
        this.tikaConfigPath = tikaConfigPath;
        this.connection = connection;
        String sql = "update emitters set status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.HAS_SHUTDOWN.ordinal() +
                " where emitter_id = (" + emitterId + ")";
        finished = connection.prepareStatement(sql);

        sql = "update emitters set status=" +
                AsyncWorkerProcess.WORKER_STATUS_CODES.RESTARTING.ordinal() +
                " where emitter_id = (" + emitterId + ")";
        restarting = connection.prepareStatement(sql);
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
                        LOG.debug("forked emitter process finished with exitValue=0");
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

    private Process start() throws IOException {
        String[] args = new String[]{"java", "-Djava.awt.headless=true", "-cp",
                System.getProperty("java.class.path"),
                "org.apache.tika.pipes.async.AsyncEmitterProcess", Integer.toString(emitterId)};
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.environment().put(TIKA_ASYNC_JDBC_KEY, connectionString);
        pb.environment()
                .put(TIKA_ASYNC_CONFIG_FILE_KEY, tikaConfigPath.toAbsolutePath().toString());
        pb.inheritIO();
        return pb.start();
    }

    private void reportCrash(int numRestarts, int exitValue) throws SQLException, IOException {
        LOG.warn("emitter id={} terminated, exitValue={}", emitterId, exitValue);
        restarting.execute();
        //should we unassign emit tasks here?
    }

 /*
    static PreparedStatement prepareInsertErrorLog(Connection connection) throws SQLException {
        //if not, this is called to insert into the error log
        return connection.prepareStatement(
                "insert into error_log (task_id, fetch_key, time_stamp, retry, error_code) " +
                        " values (?,?,CURRENT_TIMESTAMP(),?,?)"
        );
    }

    static PreparedStatement prepareReset(Connection connection) throws SQLException {
        //and this is called to reset the status on error
        return connection.prepareStatement(
                "update task_queue set " +
                        "status=?, " +
                        "time_stamp=CURRENT_TIMESTAMP(), " +
                        "retry=? " +
                        "where id=?");
    }*/
}
