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
package org.apache.tika.server.core;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);
    private final ServerStatus serverStatus;
    private final TikaServerConfig tikaServerConfig;

    public ServerStatusWatcher(ServerStatus serverStatus, TikaServerConfig tikaServerConfig) throws InterruptedException {
        this.serverStatus = serverStatus;
        this.tikaServerConfig = tikaServerConfig;
        serverStatus.setStatus(ServerStatus.STATUS.OPERATING);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(tikaServerConfig.getTaskPulseMillis());
            } catch (InterruptedException e) {
                return;
            }
            checkForTaskTimeouts();
            if (serverStatus.getStatus() == ServerStatus.STATUS.OOM) {
                LOG.error("hit oom shutting down");
                shutdown();
            } else if (serverStatus.getStatus() == ServerStatus.STATUS.CRASH) {
                LOG.error("other crash");
                shutdown();
            }
        }
    }

    private void checkForTaskTimeouts() {
        Instant now = Instant.now();
        for (TaskStatus status : serverStatus
                .getTasks()
                .values()) {
            long millisElapsed = Duration
                    .between(status.started, now)
                    .toMillis();
            if (millisElapsed > status.timeoutMillis) {
                serverStatus.setStatus(ServerStatus.STATUS.TIMEOUT);
                LOG.error("Timeout task {}, millis elapsed {}; " +
                                "consider increasing the allowable time via: " +
                                "server config (\"taskTimeoutMillis\") or " +
                                "per-request config (\"tika-task-timeout\")",
                        status.task.toString(), millisElapsed);
                shutdown();
            }
        }
    }

    static void shutdown() {
        //if something went wrong with the parent,
        //the forked process should try to delete the tmp file

        System.exit(1);
    }

}
