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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);
    private final ServerStatus serverStatus;
    private final DataInputStream fromParent;
    private final long maxFiles;
    private final ServerTimeoutConfig serverTimeouts;
    private final Path forkedStatusPath;
    private final ByteBuffer statusBuffer = ByteBuffer.allocate(16);



    private volatile Instant lastPing = null;

    public ServerStatusWatcher(ServerStatus serverStatus,
                               InputStream inputStream, Path forkedStatusPath,
                               long maxFiles,
                               ServerTimeoutConfig serverTimeouts) throws IOException {
        this.serverStatus = serverStatus;
        this.maxFiles = maxFiles;
        this.serverTimeouts = serverTimeouts;
        this.forkedStatusPath = forkedStatusPath;
        serverStatus.setStatus(ServerStatus.STATUS.OPERATING);
        this.fromParent = new DataInputStream(inputStream);
        Thread statusWatcher = new Thread(new StatusWatcher());
        statusWatcher.setDaemon(true);
        statusWatcher.start();
        writeStatus();

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

    private void writeStatus() throws IOException {
        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        try (FileChannel channel = FileChannel.open(forkedStatusPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE)) {
            while (elapsed < serverTimeouts.getPingTimeoutMillis()) {
                try (FileLock lock = channel.tryLock()) {
                    if (lock != null) {
                        ((Buffer) statusBuffer).position(0);
                        statusBuffer.putLong(0, Instant.now().toEpochMilli());
                        statusBuffer.putInt(8, serverStatus.getStatus().getInt());
                        statusBuffer.putInt(12, serverStatus.getTasks().size());
                        channel.write(statusBuffer);
                        channel.force(true);
                        return;
                    }
                }
                elapsed = Duration.between(started, Instant.now()).toMillis();
            }
        }
        throw new FatalException("Couldn't write to status file after trying for " + elapsed + " millis.");
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
                    LOG.error("Timeout task {}, millis elapsed {}, file {}" +
                                    "consider increasing the allowable time with the " +
                                    "-taskTimeoutMillis flag",
                            status.task.toString(), Long.toString(millisElapsed), status.fileName.get());
                } else {
                    LOG.error("Timeout task {}, millis elapsed {}; " +
                                    "consider increasing the allowable time with the " +
                                    "-taskTimeoutMillis flag",
                            status.task.toString(), Long.toString(millisElapsed));
                }
            }
        }
    }

    private void shutdown(ServerStatus.STATUS status) {

        try {
            writeStatus();
        } catch (Exception e) {
            LOG.debug("problem writing status before shutdown", e);
        }

        //if something went wrong with the parent,
        //the forked process should try to delete the tmp file
        if (status == ServerStatus.STATUS.PARENT_EXCEPTION) {
            try {
                Files.delete(forkedStatusPath);
            } catch (IOException e) {
                //swallow
            }
        }
        LOG.info("Shutting down forked process with status: {}", status.name());
        System.exit(status.getShutdownCode());
    }


    //This is an internal thread that pulses every ServerTimeouts#pingPulseMillis
    //within the forked process to see if the forked process should terminate.
    private class StatusWatcher implements Runnable {

        @Override
        public void run() {
            while (true) {
                ServerStatus.STATUS currStatus = serverStatus.getStatus();

                if (currStatus != ServerStatus.STATUS.OPERATING) {
                    LOG.warn("forked process observed "+currStatus.name()+ " and is shutting down.");
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

    private static class FatalException extends RuntimeException {
        public FatalException() {
            super();
        }

        public FatalException(String msg) {
            super(msg);
        }
    }
}