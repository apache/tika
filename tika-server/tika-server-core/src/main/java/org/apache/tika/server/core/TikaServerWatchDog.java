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

import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public class TikaServerWatchDog implements Callable<WatchDogResult> {

    private enum FORKED_STATUS {
        INITIALIZING,
        RUNNING,
        SHUTTING_DOWN
    }

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerWatchDog.class);
    private static final String DEFAULT_FORKED_STATUS_FILE_PREFIX = "tika-server-forked-process-mmap-";

    private Object[] forkedStatusLock = new Object[0];
    private volatile FORKED_STATUS forkedStatus = FORKED_STATUS.INITIALIZING;
    private volatile Instant lastPing = null;
    private ForkedProcess forkedProcess = null;

    private final int port;
    private final String id;
    private int restarts = 0;
    private final TikaServerConfig tikaServerConfig;
    private volatile boolean shutDown = false;

    TikaServerWatchDog(int port, String id,
                       TikaServerConfig tikaServerConfig) {
        this.port = port;
        this.id  = id;
        this.tikaServerConfig = tikaServerConfig;
    }


    @Override
    public WatchDogResult call() throws Exception {
        while (true) {
            if (tikaServerConfig.getMaxRestarts() > 0 && restarts >=
                    tikaServerConfig.getMaxRestarts()) {
                LOG.warn("hit max restarts ({}). Ending processing for {} {}",
                        restarts, id, port);
                return new WatchDogResult(port, id, restarts);
            }

            try {
                forkedProcess = new ForkedProcess(restarts++);
                setForkedStatus(FORKED_STATUS.RUNNING);
                startPingTimer();
                while (forkedProcess.ping()) {
                    Thread.sleep(tikaServerConfig.getPingPulseMillis());
                }
            } catch (InterruptedException e) {
                return new WatchDogResult(port, id,restarts);
            } finally {
                setForkedStatus(FORKED_STATUS.SHUTTING_DOWN);
                LOG.debug("about to shutdown");
                if (forkedProcess != null) {
                    LOG.info("about to shutdown process");
                    forkedProcess.close();
                }
            }
        }
    }

    public void shutDown() {
        shutDown = true;
    }

    private void startPingTimer() {
        //if the forked thread is in stop-the-world mode, and isn't
        //reading the ping, this thread checks to make sure
        //that the parent ping is sent often enough.
        //The write() in ping() could block.
        //If there isn't a successful ping often enough,
        //this force destroys the forked process.
        Thread pingTimer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long tmpLastPing = -1L;
                    synchronized (forkedStatusLock) {
                        if (forkedStatus == FORKED_STATUS.RUNNING) {
                            tmpLastPing = lastPing.toEpochMilli();
                        }
                    }
                    if (tmpLastPing > 0) {
                        long elapsed = Duration.between(Instant.ofEpochMilli(tmpLastPing), Instant.now()).toMillis();
                        if (elapsed > tikaServerConfig.getPingTimeoutMillis()) {
                            Process processToDestroy = null;
                            try {
                                processToDestroy = forkedProcess.process;
                                LOG.warn("{} ms have elapsed since last successful ping. Destroying forked now",
                                        elapsed);
                                destroyForkedForcibly(processToDestroy);
                                forkedProcess.close();
                            } catch (NullPointerException e) {
                                //ignore
                            }
                        }
                    }
                    try {
                        Thread.sleep(tikaServerConfig.getPingPulseMillis());
                    } catch (InterruptedException e) {
                        //swallow
                    }
                }
            }
        }
        );
        pingTimer.setDaemon(true);
        pingTimer.start();

    }

    private void setForkedStatus(FORKED_STATUS status) {
        synchronized (forkedStatusLock) {
            forkedStatus = status;
        }
    }

    private class ForkedProcess {
        private Thread SHUTDOWN_HOOK = null;

        private final Process process;
        private final DataOutputStream toForked;
        private final Path forkedStatusFile;
        private final ByteBuffer statusBuffer = ByteBuffer.allocate(16);

        private ForkedProcess(int numRestarts) throws Exception {
            String prefix = tikaServerConfig.getTempFilePrefix();

            this.forkedStatusFile = Files.createTempFile(prefix, "");
            this.process = startProcess(numRestarts, forkedStatusFile);

            //wait for file to be written/initialized by forked process
            Instant start = Instant.now();
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            try {
                while (process.isAlive() && Files.size(forkedStatusFile) < 12
                        && elapsed < tikaServerConfig.getMaxForkedStartupMillis()) {
                    Thread.sleep(50);
                    elapsed = Duration.between(start, Instant.now()).toMillis();
                }
            } catch (IOException e) {
                //the forkedStatusFile can be deleted by the
                //forked process if it closes...this can lead to a NoSuchFileException
                LOG.warn("failed to start forked process", e);
            }

            if (elapsed > tikaServerConfig.getMaxForkedStartupMillis()) {
                close();
                throw new RuntimeException("Forked process failed to start after "+elapsed + " (ms)");
            }
            if (!process.isAlive()) {
                close();
                throw new RuntimeException("Failed to start forked process -- forked is not alive");
            }
            if (!Files.exists(forkedStatusFile)) {
                close();
                throw new RuntimeException("Failed to start forked process -- forked status file does not exist");
            }

            this.toForked = new DataOutputStream(process.getOutputStream());
            lastPing = Instant.now();
        }

        public boolean ping() {
            if (!process.isAlive()) {
                LOG.debug("process is not alive");
                return false;
            }
            try {
                toForked.writeByte(ServerStatus.DIRECTIVES.PING.getByte());
                toForked.flush();
            } catch (Exception e) {
                LOG.warn("Exception pinging forked process", e);
                return false;
            }
            ForkedStatus forkedStatus = null;
            try {
                forkedStatus = readStatus();
            } catch (Exception e) {
                LOG.warn("Exception reading status from forked", e);
                return false;
            }

            if (forkedStatus.status != ServerStatus.STATUS.OPERATING.getInt()) {
                LOG.warn("Received non-operating status from forked: {}",
                        ServerStatus.STATUS.lookup(forkedStatus.status));
                return false;
            }

            long elapsedSinceLastUpdate =
                    Duration.between(Instant.ofEpochMilli(forkedStatus.timestamp), Instant.now()).toMillis();
            LOG.debug("last update: {}, elapsed:{}, status:{}", forkedStatus.timestamp, elapsedSinceLastUpdate,
                    forkedStatus.status);

            if (elapsedSinceLastUpdate >
                    tikaServerConfig.getPingTimeoutMillis()) {
                //forked hasn't written a status update in a longer time than allowed
                LOG.warn("Forked's last update exceeded ping timeout: {} (ms) with status {}",
                        elapsedSinceLastUpdate, forkedStatus.status);
                return false;
            }

            lastPing = Instant.now();
            return true;
        }

        private ForkedStatus readStatus() throws Exception {
            Instant started = Instant.now();
            Long elapsed = Duration.between(started, Instant.now()).toMillis();
            //only reading, but need to include write to allow for locking
            try (FileChannel fc = FileChannel.open(forkedStatusFile, READ, WRITE)) {

                while (elapsed < tikaServerConfig.getPingTimeoutMillis()) {
                    try (FileLock lock = fc.tryLock(0, 16, true)) {
                        if (lock != null) {
                            ((Buffer)statusBuffer).position(0);
                            fc.read(statusBuffer);
                            long timestamp = statusBuffer.getLong(0);
                            int status = statusBuffer.getInt(8);
                            int numTasks = statusBuffer.getInt(12);
                            return new ForkedStatus(timestamp, status, numTasks);
                        }
                    } catch (OverlappingFileLockException e) {
                        //swallow
                    }
                    elapsed = Duration.between(started, Instant.now()).toMillis();
                }
            }
            throw new RuntimeException("couldn't read from status file after "+elapsed +" millis");
        }

        private void close() {
            try {
                if (toForked != null) {
                    toForked.writeByte(ServerStatus.DIRECTIVES.SHUTDOWN.getByte());
                    toForked.flush();
                }
            } catch (IOException e) {
                LOG.debug("Exception asking forked process to shutdown", e);
            }

            try {
                if (toForked != null) {
                    toForked.close();
                }
            } catch (IOException e) {
                LOG.debug("Problem shutting down writer to forked process", e);
            }
            destroyForkedForcibly(process);

            if (forkedStatusFile != null) {
                try {
                    if (Files.isRegularFile(forkedStatusFile)) {
                        Files.delete(forkedStatusFile);
                    }
                    LOG.debug("deleted "+forkedStatusFile);
                } catch (IOException e) {
                    LOG.warn("problem deleting forked process status file", e);
                }
            }
        }

        private Process startProcess(int numRestarts, Path forkedStatusFile) throws IOException {

            ProcessBuilder builder = new ProcessBuilder();
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            List<String> argList = new ArrayList<>();
            String javaPath = tikaServerConfig.getJavaPath();
            List<String> jvmArgs = tikaServerConfig.getForkedJvmArgs();
            List<String> forkedArgs = tikaServerConfig.getForkedProcessArgs(port, id);

            forkedArgs.add("-forkedStatusFile");
            forkedArgs.add(ProcessUtils.escapeCommandLine(forkedStatusFile.toAbsolutePath().toString()));

            argList.add(javaPath);
            if (! jvmArgs.contains("-cp") && ! jvmArgs.contains("--classpath")) {
                String cp = System.getProperty("java.class.path");
                jvmArgs.add("-cp");
                jvmArgs.add(cp);
            }
            argList.addAll(jvmArgs);
            argList.add("org.apache.tika.server.core.TikaServerProcess");
            argList.addAll(forkedArgs);
            argList.add("-numRestarts");
            argList.add(Integer.toString(numRestarts));
            LOG.debug("forked process commandline: " +argList.toString());
            builder.command(argList);
            Process process = builder.start();
            //redirect stdout to parent stderr to avoid error msgs
            //from maven during build: Corrupted STDOUT by directly writing to native stream in forked
            redirectIO(process.getInputStream(), System.err);
            if (SHUTDOWN_HOOK != null) {
                Runtime.getRuntime().removeShutdownHook(SHUTDOWN_HOOK);
            }
            SHUTDOWN_HOOK = new Thread(() -> this.close());
            Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);

            return process;
        }
    }

    private static void redirectIO(final InputStream src, final PrintStream targ) {
        Thread gobbler = new Thread(new Runnable() {
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(src, StandardCharsets.UTF_8));
                String line = null;
                try {
                    line = reader.readLine();
                    while (line != null) {
                        targ.println(line);
                        line = reader.readLine();
                    }
                } catch (IOException e) {
                    //swallow
                }
            }
        });
        gobbler.setDaemon(true);
        gobbler.start();
    }

    private static synchronized void destroyForkedForcibly(Process process) {
        process = process.destroyForcibly();
        try {
            boolean destroyed = process.waitFor(60, TimeUnit.SECONDS);
            if (! destroyed) {
                LOG.error("Forked process still alive after 60 seconds. " +
                        "Shutting down the parent.");
                System.exit(1);
            }
        } catch (InterruptedException e) {
            //swallow
        }
    }

    private static class ForkedStatus {
        private final long timestamp;
        private final int status;
        private final int numTasks;

        public ForkedStatus(long timestamp, int status, int numTasks) {
            this.timestamp = timestamp;
            this.status = status;
            this.numTasks = numTasks;
        }

        @Override
        public String toString() {
            return "ForkedStatus{" +
                    "timestamp=" + timestamp +
                    ", status=" + status +
                    ", numTasks=" + numTasks +
                    '}';
        }
    }

}
