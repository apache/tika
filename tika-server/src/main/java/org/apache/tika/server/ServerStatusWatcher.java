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
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);
    private final ServerStatus serverStatus;
    private final DataInputStream fromParent;
    private final long maxFiles;
    private final ServerTimeouts serverTimeouts;
    private final FileChannel childStatusChannel;
    private final MappedByteBuffer toParent;


    private volatile Instant lastPing = null;

    public ServerStatusWatcher(ServerStatus serverStatus,
                               InputStream inputStream, Path childStatusFile,
                               long maxFiles,
                               ServerTimeouts serverTimeouts) throws IOException {
        this.serverStatus = serverStatus;
        this.maxFiles = maxFiles;
        this.serverTimeouts = serverTimeouts;
        this.childStatusChannel = FileChannel.open(childStatusFile,
                StandardOpenOption.DSYNC, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.READ,
                StandardOpenOption.DELETE_ON_CLOSE);
        this.toParent= childStatusChannel.map(FileChannel.MapMode.READ_WRITE,
                0, 16);//8 for timestamp long, 4 for status int, 4 for numactivetasks int
        serverStatus.setStatus(ServerStatus.STATUS.OPERATING);
        writeStatus();
        this.fromParent = new DataInputStream(inputStream);
        Thread statusWatcher = new Thread(new StatusWatcher());
        statusWatcher.setDaemon(true);
        statusWatcher.start();

    }

    @Override
    public void run() {

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
                    writeStatus();
                } catch (Exception e) {
                    LOG.warn("Exception writing to parent", e);
                    serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                    shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
                }
            } else if (directive == ServerStatus.DIRECTIVES.SHUTDOWN.getByte()) {
                LOG.info("Parent requested shutdown");
                serverStatus.setStatus(ServerStatus.STATUS.PARENT_REQUESTED_SHUTDOWN);
                shutdown(ServerStatus.STATUS.PARENT_REQUESTED_SHUTDOWN);
            } else if (directive == ServerStatus.DIRECTIVES.PING_ACTIVE_SERVER_TASKS.getByte()) {
                try {
                    writeStatus();
                } catch (Exception e) {
                    LOG.warn("Exception writing to parent", e);
                    serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
                    shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
                }
            }
        }
    }

    private void writeStatus() throws IllegalArgumentException {
        toParent.putLong(0, Instant.now().toEpochMilli());
        toParent.putInt(8, serverStatus.getStatus().getInt());
        toParent.putInt(12, serverStatus.getTasks().size());
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

        toParent.putLong(0, Instant.now().toEpochMilli());
        toParent.putInt(8, serverStatus.getStatus().getInt());
        toParent.putInt(12, 0);
        toParent.force();
        try {
            childStatusChannel.close();
        } catch (IOException e) {
            LOG.warn("problem closing status channel", e);
        }
        LOG.info("Shutting down child process with status: {}", status.name());
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