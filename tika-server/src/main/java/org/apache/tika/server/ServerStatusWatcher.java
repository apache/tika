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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);
    private final ServerStatus serverStatus;
    private final DataInputStream fromParent;
    private final DataOutputStream toParent;
    private final long maxFiles;
    private final ServerTimeouts serverTimeouts;


    private volatile Instant lastPing = null;

    public ServerStatusWatcher(ServerStatus serverStatus,
                               InputStream inputStream, OutputStream outputStream,
                               long maxFiles,
                               ServerTimeouts serverTimeouts) {
        this.serverStatus = serverStatus;
        this.maxFiles = maxFiles;
        this.serverTimeouts = serverTimeouts;

        this.fromParent = new DataInputStream(inputStream);
        this.toParent = new DataOutputStream(outputStream);
        Thread statusWatcher = new Thread(new StatusWatcher());
        statusWatcher.setDaemon(true);
        statusWatcher.start();
    }

    @Override
    public void run() {
        //let parent know child is alive
        try {
            toParent.writeByte(ServerStatus.STATUS.OPERATING.getByte());
            toParent.flush();
        } catch (Exception e) {
            LOG.warn("Exception writing startup ping to parent", e);
            serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
            shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
        }

        byte directive = (byte)-1;
        while (true) {
            try {
                directive = fromParent.readByte();
                lastPing = Instant.now();
            } catch (Exception e) {
                LOG.warn("Exception reading from parent", e);
                serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
            }
            if (directive == ServerStatus.DIRECTIVES.PING.getByte()) {
                if (serverStatus.getStatus().equals(ServerStatus.STATUS.OPERATING)) {
                    checkForHitMaxFiles();
                    checkForTaskTimeouts();
                }
                try {
                    toParent.writeByte(serverStatus.getStatus().getByte());
                    toParent.flush();
                } catch (Exception e) {
                    LOG.warn("Exception writing to parent", e);
                    serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                    shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
                }
            } else if (directive == ServerStatus.DIRECTIVES.SHUTDOWN.getByte()) {
                LOG.info("Parent requested shutdown");
                serverStatus.setStatus(ServerStatus.STATUS.PARENT_REQUESTED_SHUTDOWN);
                shutdown(ServerStatus.STATUS.PARENT_REQUESTED_SHUTDOWN);
            } else if (directive == ServerStatus.DIRECTIVES.PING_ACTIVE_SERVER_TASKS.getByte()) {              try {
                    toParent.writeInt(serverStatus.getTasks().size());
                    toParent.flush();
                } catch (Exception e) {
                    LOG.warn("Exception writing to parent", e);
                    serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                    shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
                }
            }
        }
    }

    private void checkForHitMaxFiles() {
        if (maxFiles < 0) {
            return;
        }
        long filesProcessed = serverStatus.getFilesProcessed();
        if (filesProcessed >= maxFiles) {
            serverStatus.setStatus(ServerStatus.STATUS.HIT_MAX);
        }
    }

    private void checkForTaskTimeouts() {
        Instant now = Instant.now();
        for (TaskStatus status : serverStatus.getTasks().values()) {
            long millisElapsed = Duration.between(status.started, now).toMillis();
            if (millisElapsed > serverTimeouts.getTaskTimeoutMillis()) {
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

    private void shutdown(ServerStatus.STATUS status) {
        LOG.info("Shutting down child process with status: " +status.name());
        System.exit(status.getShutdownCode());
    }

    //This is an internal thread that pulses every 100MS
    //within the child to see if the child should die.
    private class StatusWatcher implements Runnable {

        @Override
        public void run() {
            while (true) {
                ServerStatus.STATUS currStatus = serverStatus.getStatus();

                if (currStatus != ServerStatus.STATUS.OPERATING) {
                    LOG.warn("child process observed "+currStatus.name()+ " and is shutting down.");
                    shutdown(currStatus);
                }

                if (lastPing != null) {
                    long elapsed = Duration.between(lastPing, Instant.now()).toMillis();
                    if (elapsed > serverTimeouts.getPingTimeoutMillis()) {
                        serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                        shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
                    }
                }
                try {
                    Thread.sleep(serverTimeouts.getPingPulseMillis());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}