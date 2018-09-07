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

package org.apache.tika.server;

import org.apache.tika.server.resource.TranslateResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);

    private final ServerStatus serverStatus;
    private final long timeoutMillis;
    private final long pulseMillis;

    public ServerStatusWatcher(ServerStatus serverStatus, long timeoutMillis, long pulseMillis) {
        this.serverStatus = serverStatus;
        this.timeoutMillis = timeoutMillis;
        this.pulseMillis = pulseMillis;
    }

    @Override
    public void run() {
        ServerStatus.STATUS status = serverStatus.getStatus();
        while (status.equals(ServerStatus.STATUS.OPEN)) {
            try {
                Thread.sleep(pulseMillis);
            } catch (InterruptedException e) {
            }
            checkForTimeouts();
            status = serverStatus.getStatus();
        }
        if (! status.equals(ServerStatus.STATUS.OPEN)) {
            LOG.warn("child process shutting down with status: {}", status);
            System.exit(status.getShutdownCode());
        }
    }

    private void checkForTimeouts() {
        Instant now = Instant.now();
        for (TaskStatus status : serverStatus.getTasks().values()) {
            long millisElapsed = Duration.between(now, status.started).toMillis();
            if (millisElapsed > timeoutMillis) {
                serverStatus.setStatus(ServerStatus.STATUS.TIMEOUT);
                if (status.fileName.isPresent()) {
                    LOG.error("Timeout task {}, millis elapsed {}, file {}",
                            status.task.toString(), Long.toString(millisElapsed), status.fileName.get());
                } else {
                    LOG.error("Timeout task {}, millis elapsed {}",
                            status.task.toString(), Long.toString(millisElapsed));
                }
            }
        }
    }
}