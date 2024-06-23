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

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.tika.server.core.TikaServerCli.TIKA_SERVER_ID_ENV;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.BindException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.ProcessUtils;

public class TikaServerWatchDog implements Callable<WatchDogResult> {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerWatchDog.class);
    private static Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();
    private static Set<ForkedProcess> FORKED_PROCESSES = ConcurrentHashMap.newKeySet();

    static {
        Thread shutdownHook = new Thread(() -> {
            //prioritize destroying processes
            for (Process process : PROCESSES) {
                process.destroyForcibly();
            }
            //once that's done, try to clean up tmp files too
            for (ForkedProcess forkedProcess : FORKED_PROCESSES) {
                try {
                    forkedProcess.close();
                } catch (DoNotRestartException | InterruptedException e) {
                    //swallow
                }
            }
        });
        Runtime
                .getRuntime()
                .addShutdownHook(shutdownHook);
    }

    private final int port;
    private final String id;
    private final TikaServerConfig tikaServerConfig;
    private final Object[] forkedStatusLock = new Object[0];
    private volatile FORKED_STATUS forkedStatus = FORKED_STATUS.INITIALIZING;
    private volatile Instant lastPing = null;
    private ForkedProcess forkedProcess = null;
    private int restarts = 0;
    private volatile boolean shutDown = false;

    TikaServerWatchDog(int port, String id, TikaServerConfig tikaServerConfig) {
        this.port = port;
        this.id = id;
        this.tikaServerConfig = tikaServerConfig;
    }

    private static void redirectIO(final InputStream src, final PrintStream targ) {
        Thread gobbler = new Thread(() -> {
            BufferedReader reader = new BufferedReader(new InputStreamReader(src, StandardCharsets.UTF_8));
            String line;
            try {
                line = reader.readLine();
                while (line != null) {
                    targ.println(line);
                    line = reader.readLine();
                }
            } catch (IOException e) {
                //swallow
            }
        });
        gobbler.setDaemon(true);
        gobbler.start();
    }

    private static synchronized void destroyForkedForcibly(Process process) throws InterruptedException {

        process = process.destroyForcibly();
        try {
            boolean destroyed = process.waitFor(60, TimeUnit.SECONDS);

            if (!destroyed) {
                LOG.error("Forked process still alive after 60 seconds. " + "Shutting down the forking process.");
                System.exit(1);
            }
        } finally {
            PROCESSES.remove(process);
        }
    }

    private static void closeForkedProcess(ForkedProcess forkedProcess) throws DoNotRestartException, InterruptedException {
        try {
            forkedProcess.close();
        } finally {
            FORKED_PROCESSES.remove(forkedProcess);
        }
    }

    @Override
    public WatchDogResult call() throws Exception {
        boolean mustRestart = true;
        try {
            while (true) {
                if (tikaServerConfig.getMaxRestarts() > 0 && restarts >= tikaServerConfig.getMaxRestarts()) {
                    LOG.warn("hit max restarts ({}). Ending processing for {} {}", restarts, id, port);
                    return new WatchDogResult(port, id, restarts);
                }

                try {
                    if (mustRestart) {
                        forkedProcess = startForkedProcess(restarts++);
                        if (forkedProcess == null) {
                            if (!shutDown) {
                                throw new IllegalArgumentException("forked process should not be " + "null when not in shutdown mode");
                            } else {
                                return new WatchDogResult(port, id, restarts);
                            }
                        }
                        setForkedStatus(FORKED_STATUS.RUNNING);
                        mustRestart = false;
                    }
                    boolean exited = forkedProcess.process.waitFor(tikaServerConfig.getTaskPulseMillis(), TimeUnit.MILLISECONDS);
                    if (exited) {
                        LOG.info("forked process exited with exit value {}", forkedProcess.process.exitValue());
                        closeForkedProcess(forkedProcess);
                        mustRestart = true;
                    } else {
                        ForkedStatus status = forkedProcess.readStatus();
                        if (status.status == FORKED_STATUS.FAILED_COMMUNICATION.ordinal()) {
                            LOG.info("failed to read from status file. Restarting now.");
                            closeForkedProcess(forkedProcess);
                            mustRestart = true;
                        } else if (status.status == FORKED_STATUS.SHUTTING_DOWN.ordinal()) {
                            LOG.info("Forked process is in shutting down mode.  Will wait a bit");
                            forkedProcess.process.waitFor(tikaServerConfig.getTaskTimeoutMillis(), TimeUnit.MILLISECONDS);
                            closeForkedProcess(forkedProcess);
                            mustRestart = true;
                        } else {
                            long elapsed = Duration
                                    .between(Instant.ofEpochMilli(status.timestamp), Instant.now())
                                    .toMillis();
                            if (elapsed > tikaServerConfig.getTaskTimeoutMillis()) {
                                LOG.info("{} ms have elapsed since forked process " + "last updated status. " + "Shutting down and restarting.", elapsed);
                                closeForkedProcess(forkedProcess);
                                mustRestart = true;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    return new WatchDogResult(port, id, restarts);
                }
            }
        } finally {
            setForkedStatus(FORKED_STATUS.SHUTTING_DOWN);
            LOG.debug("about to shutdown");
            if (forkedProcess != null) {
                LOG.info("about to shutdown process");
                closeForkedProcess(forkedProcess);
            }
        }
    }

    public synchronized void close() throws DoNotRestartException, InterruptedException {
        setForkedStatus(FORKED_STATUS.SHUTTING_DOWN);
        LOG.debug("received 'close()'; about to shutdown");
        shutDown();
        closeForkedProcess(forkedProcess);
    }

    private synchronized ForkedProcess startForkedProcess(int restarts) throws Exception {
        LOG.debug("attempting to start forked process on {} restarts", restarts);
        int consecutiveRestarts = 0;
        //if there's a bind exception, retry for 30 seconds to give the OS
        //a chance to release the port
        int maxBind = 30;
        while (consecutiveRestarts < maxBind && !shutDown) {
            try {
                ForkedProcess forkedProcess = new ForkedProcess(restarts);
                FORKED_PROCESSES.add(forkedProcess);
                return forkedProcess;
            } catch (BindException e) {
                LOG.warn("WatchDog observes bind exception on retry {}. " + "Will retry {} times.", consecutiveRestarts, maxBind);
                consecutiveRestarts++;
                Thread.sleep(1000);
                if (consecutiveRestarts >= maxBind) {
                    throw e;
                }
            }
        }
        if (shutDown) {
            return null;
        }
        throw new RuntimeException("Couldn't start forked process");
    }


    public void shutDown() {
        shutDown = true;
    }

    private void setForkedStatus(FORKED_STATUS status) {
        synchronized (forkedStatusLock) {
            forkedStatus = status;
        }
    }

    private enum FORKED_STATUS {
        INITIALIZING, RUNNING, SHUTTING_DOWN, FAILED_COMMUNICATION
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
            return "ForkedStatus{" + "timestamp=" + timestamp + ", status=" + status + ", numTasks=" + numTasks + '}';
        }
    }

    private static class DoNotRestartException extends TikaException {

        public DoNotRestartException(String msg) {
            super(msg);
        }

        public DoNotRestartException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private class ForkedProcess {
        private final Process process;
        //        private final DataOutputStream toForked;
        private final Path forkedStatusFile;
        private final ByteBuffer statusBuffer = ByteBuffer.allocate(16);

        private ForkedProcess(int numRestarts) throws Exception {
            String prefix = tikaServerConfig.getTempFilePrefix();

            this.forkedStatusFile = Files.createTempFile(prefix, "");
            this.process = startProcess(numRestarts, forkedStatusFile);

            //wait for file to be written/initialized by forked process
            Instant start = Instant.now();
            long elapsed = Duration
                    .between(start, Instant.now())
                    .toMillis();
            try {
                while (process.isAlive() && Files.size(forkedStatusFile) < 12 && elapsed < tikaServerConfig.getMaxForkedStartupMillis()) {
                    Thread.sleep(50);
                    elapsed = Duration
                            .between(start, Instant.now())
                            .toMillis();
                }
            } catch (IOException e) {
                //the forkedStatusFile can be deleted by the
                //forked process if it closes...this can lead to a NoSuchFileException
                LOG.warn("failed to start forked process", e);
            }

            if (elapsed > tikaServerConfig.getMaxForkedStartupMillis()) {
                close();
                throw new RuntimeException("Forked process failed to start after " + elapsed + " (ms)");
            }
            if (!process.isAlive()) {
                close();
                if (process.exitValue() == TikaServerProcess.BIND_EXCEPTION) {
                    throw new BindException("couldn't bind");
                }
                throw new RuntimeException("Failed to start forked process -- forked is not alive");
            }
            if (!Files.exists(forkedStatusFile)) {
                close();
                throw new RuntimeException("Failed to start forked process -- forked status file does not exist");
            }

            lastPing = Instant.now();
        }

        private ForkedStatus readStatus() throws Exception {
            Instant started = Instant.now();
            long elapsed = Duration
                    .between(started, Instant.now())
                    .toMillis();
            //only reading, but need to include write to allow for locking
            try (FileChannel fc = FileChannel.open(forkedStatusFile, READ, WRITE)) {

                while (elapsed < tikaServerConfig.getTaskTimeoutMillis()) {
                    try (FileLock lock = fc.tryLock(0, 16, true)) {
                        if (lock != null) {
                            ((Buffer) statusBuffer).position(0);
                            fc.read(statusBuffer);
                            long timestamp = statusBuffer.getLong(0);
                            int status = statusBuffer.getInt(8);
                            int numTasks = statusBuffer.getInt(12);
                            return new ForkedStatus(timestamp, status, numTasks);
                        }
                    } catch (OverlappingFileLockException e) {
                        //swallow
                    }
                    Thread.sleep(100);
                    elapsed = Duration
                            .between(started, Instant.now())
                            .toMillis();
                }
            }
            return new ForkedStatus(-1, FORKED_STATUS.FAILED_COMMUNICATION.ordinal(), -1);
        }

        private void close() throws DoNotRestartException, InterruptedException {

            try {
                if (!process.isAlive()) {
                    try {
                        int exit = process.exitValue();
                        if (exit == TikaServerProcess.DO_NOT_RESTART_EXIT_VALUE) {
                            throw new DoNotRestartException("Forked exited with: " + exit);
                        }
                    } catch (IllegalThreadStateException e) {
                        //swallow
                    }
                }
                destroyForkedForcibly(process);
            } finally {
                if (forkedStatusFile != null) {
                    try {
                        if (Files.isRegularFile(forkedStatusFile)) {
                            Files.delete(forkedStatusFile);
                        }
                        LOG.debug("deleted " + forkedStatusFile);
                    } catch (IOException e) {
                        LOG.warn("problem deleting forked process status file", e);
                    }
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
            forkedArgs.add(ProcessUtils.escapeCommandLine(forkedStatusFile
                    .toAbsolutePath()
                    .toString()));

            argList.add(javaPath);
            if (!jvmArgs.contains("-cp") && !jvmArgs.contains("--classpath")) {
                String cp = System.getProperty("java.class.path");
                jvmArgs.add("-cp");
                jvmArgs.add(cp);
            }
            //this is mostly for log4j 1.x so that different processes
            //can log to different log files
            jvmArgs.add("-Dtika.server.id=" + tikaServerConfig.getId());
            argList.addAll(jvmArgs);

            argList.add("org.apache.tika.server.core.TikaServerProcess");
            argList.addAll(forkedArgs);

            argList.add("-numRestarts");
            argList.add(Integer.toString(numRestarts));
            LOG.debug("forked process commandline: " + argList.toString());
            builder.command(argList);
            //now overwrite with the specific server id
            //this is mostly for log4j 2.x so that different processes
            //can log to different log files via {env:tika.server.id}
            builder
                    .environment()
                    .put(TIKA_SERVER_ID_ENV, id);
            Process process = builder.start();
            PROCESSES.add(process);
            //redirect stdout to parent stderr to avoid error msgs
            //from maven during build: Corrupted STDOUT by directly writing to
            // native stream in forked
            redirectIO(process.getInputStream(), System.err);
            redirectIO(process.getErrorStream(), System.err);
            return process;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ForkedProcess that = (ForkedProcess) o;

            if (!process.equals(that.process)) {
                return false;
            }
            if (!forkedStatusFile.equals(that.forkedStatusFile)) {
                return false;
            }
            return statusBuffer.equals(that.statusBuffer);
        }

        @Override
        public int hashCode() {
            int result = process.hashCode();
            result = 31 * result + forkedStatusFile.hashCode();
            result = 31 * result + statusBuffer.hashCode();
            return result;
        }
    }

}
