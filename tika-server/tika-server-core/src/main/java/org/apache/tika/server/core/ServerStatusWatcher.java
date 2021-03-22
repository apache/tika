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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerStatusWatcher implements Runnable {


    private static final Logger LOG = LoggerFactory.getLogger(ServerStatusWatcher.class);
    private final ServerStatus serverStatus;
    private final InputStream fromParent;
    private final TikaServerConfig tikaServerConfig;
    private final Path forkedStatusPath;
    private final ByteBuffer statusBuffer = ByteBuffer.allocate(16);
    private volatile boolean shuttingDown = false;

    public ServerStatusWatcher(ServerStatus serverStatus, InputStream inputStream,
                               Path forkedStatusPath, TikaServerConfig tikaServerConfig)
            throws InterruptedException {
        this.serverStatus = serverStatus;
        this.tikaServerConfig = tikaServerConfig;
        this.forkedStatusPath = forkedStatusPath;
        serverStatus.setStatus(ServerStatus.STATUS.OPERATING);
        this.fromParent = inputStream;
        Thread statusWatcher = new Thread(new StatusWatcher());
        statusWatcher.setDaemon(true);
        statusWatcher.start();
        writeStatus(false);
    }

    @Override
    public void run() {


        try {
            //this should block forever until the parent dies
            int directive = fromParent.read();
            if (directive != -1) {
                LOG.debug("Read byte ({}) from forking process. Shouldn't have received anything",
                        directive);
            }
        } catch (Exception e) {
            LOG.debug("Exception reading from parent", e);
        } finally {
            LOG.info("Forking process signalled I should shutdown.");
            serverStatus.setStatus(ServerStatus.STATUS.PARENT_EXCEPTION);
            shutdown(ServerStatus.STATUS.PARENT_EXCEPTION);
        }

    }

    private synchronized void writeStatus(boolean shuttingDown) throws InterruptedException {
        if (this.shuttingDown == true) {
            return;
        }
        if (shuttingDown == true) {
            this.shuttingDown = true;
        }

        Instant started = Instant.now();
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        try (FileChannel channel = FileChannel
                .open(forkedStatusPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            while (elapsed < tikaServerConfig.getTaskTimeoutMillis()) {
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
                } catch (IOException e) {
                    LOG.warn("Problem writing to status file", e);
                }
                Thread.sleep(100);
                elapsed = Duration.between(started, Instant.now()).toMillis();
            }
        } catch (IOException e) {
            LOG.warn("Couldn't open forked status file for writing", e);
        }
        throw new FatalException(
                "Couldn't write to status file after trying for " + elapsed + " millis.");
    }

    private void checkForHitMaxFiles() {
        if (tikaServerConfig.getMaxFiles() < 0) {
            return;
        }
        long filesProcessed = serverStatus.getFilesProcessed();
        if (filesProcessed >= tikaServerConfig.getMaxFiles()) {
            serverStatus.setStatus(ServerStatus.STATUS.HIT_MAX_FILES);
        }
    }

    private void checkForTaskTimeouts() {
        Instant now = Instant.now();
        for (TaskStatus status : serverStatus.getTasks().values()) {
            long millisElapsed = Duration.between(status.started, now).toMillis();
            if (millisElapsed > tikaServerConfig.getTaskTimeoutMillis()) {
                serverStatus.setStatus(ServerStatus.STATUS.TIMEOUT);
                if (status.fileName.isPresent()) {
                    LOG.error("Timeout task {}, millis elapsed {}, file {}" +
                                    "consider increasing the allowable time with the " +
                                    "<taskTimeoutMillis/> parameter", status.task.toString(),
                            millisElapsed,
                            status.fileName.get());
                } else {
                    LOG.error("Timeout task {}, millis elapsed {}; " +
                                    "consider increasing the allowable time with the " +
                                    "<taskTimeoutMillis/> parameter", status.task.toString(),
                            millisElapsed);
                }
            }
        }
    }

    private void shutdown(ServerStatus.STATUS status) {
        //if something went wrong with the parent,
        //the forked process should try to delete the tmp file
        if (status == ServerStatus.STATUS.PARENT_EXCEPTION) {
            try {
                Files.delete(forkedStatusPath);
            } catch (IOException e) {
                //swallow
            }
        } else {
            try {
                writeStatus(true);
            } catch (Exception e) {
                LOG.debug("problem writing status before shutdown", e);
            }
        }

        LOG.info("Shutting down forked process with status: {}", status.name());
        System.exit(status.getShutdownCode());
    }

    private static class FatalException extends RuntimeException {
        public FatalException() {
            super();
        }

        public FatalException(String msg) {
            super(msg);
        }
    }

    //This is an internal thread that pulses every ServerTimeouts#pingPulseMillis
    //within the forked process to see if the forked process should terminate.
    private class StatusWatcher implements Runnable {

        @Override
        public void run() {
            Instant lastWrite = Instant.now();
            while (true) {
                checkForHitMaxFiles();
                checkForTaskTimeouts();
                ServerStatus.STATUS currStatus = serverStatus.getStatus();
                if (currStatus != ServerStatus.STATUS.OPERATING) {
                    LOG.warn("forked process observed " + currStatus.name() +
                            " and is shutting down.");
                    shutdown(currStatus);
                } else {
                    long elapsed = Duration.between(lastWrite, Instant.now()).toMillis();
                    if (elapsed > tikaServerConfig.getTaskPulseMillis()) {
                        try {
                            writeStatus(false);
                            lastWrite = Instant.now();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    LOG.warn("status watcher sees interrupted exception");
                    return;
                }
            }
        }
    }
}
