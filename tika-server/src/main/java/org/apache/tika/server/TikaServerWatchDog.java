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

import org.apache.tika.io.MappedBufferCleaner;
import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TikaServerWatchDog {

    private enum CHILD_STATUS {
        INITIALIZING,
        RUNNING,
        SHUTTING_DOWN
    }

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerWatchDog.class);
    private static final String DEFAULT_CHILD_STATUS_FILE_PREFIX = "tika-server-child-process-mmap-";

    private Object[] childStatusLock = new Object[0];
    private volatile CHILD_STATUS childStatus = CHILD_STATUS.INITIALIZING;
    private volatile Instant lastPing = null;
    private ChildProcess childProcess = null;


    public void execute(String[] args, ServerTimeouts serverTimeouts) throws Exception {
        LOG.info("server watch dog is starting up");
        startPingTimer(serverTimeouts);

        try {
            childProcess = new ChildProcess(args, serverTimeouts);
            setChildStatus(CHILD_STATUS.RUNNING);
            int restarts = 0;
            while (true) {
                if (!childProcess.ping()) {
                    LOG.debug("bad ping, initializing");
                    setChildStatus(CHILD_STATUS.INITIALIZING);
                    lastPing = null;
                    childProcess.close();
                    LOG.debug("About to restart the child process");
                    childProcess = new ChildProcess(args, serverTimeouts);
                    LOG.info("Successfully restarted child process -- {} restarts so far)", restarts);
                    setChildStatus(CHILD_STATUS.RUNNING);
                    restarts++;
                    if (serverTimeouts.getMaxRestarts() > -1 && restarts >= serverTimeouts.getMaxRestarts()) {
                        LOG.warn("hit max restarts: "+restarts+". Stopping now");
                        break;
                    }
                }
                Thread.sleep(serverTimeouts.getPingPulseMillis());
            }
        } catch (InterruptedException e) {
            //interrupted...shutting down
        } finally {
            setChildStatus(CHILD_STATUS.SHUTTING_DOWN);
            LOG.debug("about to shutdown");
            if (childProcess != null) {
                LOG.info("about to shutdown process");
                childProcess.close();
            }
        }
    }

    private void startPingTimer(ServerTimeouts serverTimeouts) {
        //if the child thread is in stop-the-world mode, and isn't
        //reading the ping, this thread checks to make sure
        //that the parent ping is sent often enough.
        //The write() in ping() could block.
        //If there isn't a successful ping often enough,
        //this force destroys the child process.
        Thread pingTimer = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    long tmpLastPing = -1L;
                    synchronized (childStatusLock) {
                        if (childStatus == CHILD_STATUS.RUNNING) {
                            tmpLastPing = lastPing.toEpochMilli();
                        }
                    }
                    if (tmpLastPing > 0) {
                        long elapsed = Duration.between(Instant.ofEpochMilli(tmpLastPing), Instant.now()).toMillis();
                        if (elapsed > serverTimeouts.getPingTimeoutMillis()) {
                            Process processToDestroy = null;
                            try {
                                processToDestroy = childProcess.process;
                                LOG.warn("{} ms have elapsed since last successful ping. Destroying child now",
                                        elapsed);
                                destroyChildForcibly(processToDestroy);
                                childProcess.close();
                            } catch (NullPointerException e) {
                                //ignore
                            }
                        }
                    }
                    try {
                        Thread.sleep(serverTimeouts.getPingPulseMillis());
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

    private void setChildStatus(CHILD_STATUS status) {
        synchronized (childStatusLock) {
            childStatus = status;
        }
    }

    private static List<String> extractArgs(String[] args) {
        List<String> argList = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-J") || args[i].equals("-spawnChild") || args[i].equals("--spawnChild")) {
                continue;
            }
            if (args[i].equals("-javaHome")) {
                if (i == args.length-1) {
                    throw new IllegalArgumentException("must specify a value for -javaHome");
                }
                i++;//skip argument value
                continue;
            }

            argList.add(args[i]);
        }
        return argList;
    }

    private static String extractJavaPath(String[] args) {
        String javaHome = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-javaHome")) {
                if (i == args.length-1) {
                    throw new IllegalArgumentException("must specify a value for -javaHome");
                }
                javaHome = args[i+1];
                break;
            }
        }
        if (javaHome == null) {
            javaHome = System.getenv("JAVA_HOME");
        }
        if (javaHome != null) {
            Path jPath = Paths.get(javaHome).resolve("bin").resolve("java");
            return ProcessUtils.escapeCommandLine(
                    jPath.toAbsolutePath().toString());
        }
        return "java";
    }
    private static List<String> extractJVMArgs(String[] args) {
        List<String> jvmArgs = new ArrayList<>();
        boolean foundHeadlessOption = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-J")) {
                jvmArgs.add("-"+args[i].substring(2));
            }
            if (args[i].contains("java.awt.headless")) {
                foundHeadlessOption = true;
            }
        }
        //if user has already specified headless...don't modify
        if (! foundHeadlessOption) {
            jvmArgs.add("-Djava.awt.headless=true");
        }

        return jvmArgs;
    }

    private class ChildProcess {
        private Thread SHUTDOWN_HOOK = null;

        private final Process process;
        private final FileChannel fromChildChannel;
        private final MappedByteBuffer fromChild;
        private final DataOutputStream toChild;
        private final ServerTimeouts serverTimeouts;
        private final Path childStatusFile;
        private ChildProcess(String[] args, ServerTimeouts serverTimeouts) throws Exception {
            String prefix = DEFAULT_CHILD_STATUS_FILE_PREFIX;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-tmpFilePrefix")) {
                    prefix = args[i+1];
                }
            }

            this.childStatusFile = Files.createTempFile(prefix, "");
            this.serverTimeouts = serverTimeouts;
            this.process = startProcess(args, childStatusFile);

            //wait for file to be written/initialized by child process
            Instant start = Instant.now();
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            while (Files.size(childStatusFile) < 12
                    && elapsed < serverTimeouts.getMaxChildStartupMillis()) {
                if (!process.isAlive()) {
                    close();
                    throw new RuntimeException("Failed to start child process");
                }
                Thread.sleep(50);
                elapsed = Duration.between(start, Instant.now()).toMillis();
            }

            if (elapsed > serverTimeouts.getMaxChildStartupMillis()) {
                close();
                throw new RuntimeException("Child process failed to start after "+elapsed + " (ms)");
            }
            this.fromChildChannel = FileChannel.open(childStatusFile,
                    StandardOpenOption.READ,
                    StandardOpenOption.DELETE_ON_CLOSE);
            this.fromChild = fromChildChannel.map(
                    FileChannel.MapMode.READ_ONLY, 0, 12);

            this.toChild = new DataOutputStream(process.getOutputStream());
            elapsed = Duration.between(start, Instant.now()).toMillis();
            //wait for child process to write something to the file
            while (elapsed < serverTimeouts.getMaxChildStartupMillis()) {
                int status = fromChild.getInt(8);
                if (status == ServerStatus.STATUS.OPERATING.getInt()) {
                    break;
                }
                Thread.sleep(50);
                elapsed = Duration.between(start, Instant.now()).toMillis();
            }
            if (elapsed > serverTimeouts.getMaxChildStartupMillis()) {
                close();
                throw new RuntimeException("Child process failed to start after "+elapsed + " (ms)");
            }
            lastPing = Instant.now();
        }

        public boolean ping() {
            if (!process.isAlive()) {
                LOG.debug("process is not alive");
                return false;
            }
            try {
                toChild.writeByte(ServerStatus.DIRECTIVES.PING.getByte());
                toChild.flush();
            } catch (Exception e) {
                LOG.warn("Exception pinging child process", e);
                return false;
            }
            long lastUpdate = -1;
            int status = -1;
            try {
                lastUpdate = fromChild.getLong(0);
                status = fromChild.getInt(8);
            } catch (IndexOutOfBoundsException e) {
                //something went wrong with the tmp file
                LOG.warn("Exception receiving status from child", e);
                return false;
            }

            if (status != ServerStatus.STATUS.OPERATING.getInt()) {
                LOG.warn("Received non-operating status from child: {}",
                        ServerStatus.STATUS.lookup(status));
                return false;
            }

            long elapsedSinceLastUpdate =
                    Duration.between(Instant.ofEpochMilli(lastUpdate), Instant.now()).toMillis();
            LOG.trace("last update: {}, elapsed:{}, status:{}", lastUpdate, elapsedSinceLastUpdate, status);

            if (elapsedSinceLastUpdate >
                    serverTimeouts.getPingTimeoutMillis()) {
                //child hasn't written a status update in a longer time than allowed
                LOG.warn("Child's last update exceeded ping timeout: {} (ms) with status {}",
                        elapsedSinceLastUpdate, status);
                return false;
            }

            lastPing = Instant.now();
            return true;
        }

        private void close() {

            try {
                if (toChild != null) {
                    toChild.writeByte(ServerStatus.DIRECTIVES.SHUTDOWN.getByte());
                    toChild.flush();
                }
            } catch (IOException e) {
                LOG.debug("Exception asking child to shutdown", e);
            }

            try {
                if (toChild != null) {
                    toChild.close();
                }
            } catch (IOException e) {
                LOG.debug("Problem shutting down writer to child", e);
            }
            destroyChildForcibly(process);
            try {
                MappedBufferCleaner.freeBuffer(fromChild);
            } catch (IOException e) {
                LOG.warn("problem freeing buffer");
            }
            try {
                if (fromChildChannel != null) {
                    fromChildChannel.close();
                }
            } catch (IOException e) {
                LOG.debug("Problem closing child channel", e);
            }
            if (childStatusFile != null) {
                try {
                    if (Files.isRegularFile(childStatusFile)) {
                        Files.delete(childStatusFile);
                    }
                } catch (IOException e) {
                    LOG.warn("problem deleting child status file", e);
                }
            }

        }

        private Process startProcess(String[] args, Path childStatusFile) throws IOException {

            ProcessBuilder builder = new ProcessBuilder();
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);

            List<String> argList = new ArrayList<>();
            String javaPath = extractJavaPath(args);
            List<String> jvmArgs = extractJVMArgs(args);
            List<String> childArgs = extractArgs(args);

            childArgs.add("-childStatusFile");
            childArgs.add(ProcessUtils.escapeCommandLine(childStatusFile.toAbsolutePath().toString()));

            argList.add(javaPath);
            if (! jvmArgs.contains("-cp") && ! jvmArgs.contains("--classpath")) {
                String cp = System.getProperty("java.class.path");
                jvmArgs.add("-cp");
                jvmArgs.add(cp);
            }
            argList.addAll(jvmArgs);
            argList.add("org.apache.tika.server.TikaServerCli");
            argList.addAll(childArgs);
            argList.add("-child");
            LOG.debug("child process commandline: " +argList.toString());
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

    private static synchronized void destroyChildForcibly(Process process) {
        process = process.destroyForcibly();
        try {
            boolean destroyed = process.waitFor(60, TimeUnit.SECONDS);
            if (! destroyed) {
                LOG.error("Child process still alive after 60 seconds. " +
                        "Shutting down the parent.");
                System.exit(1);
            }
        } catch (InterruptedException e) {
            //swallow
        }
    }

}
