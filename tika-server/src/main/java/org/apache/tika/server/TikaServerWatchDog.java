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

import org.apache.tika.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

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
        args = addIdIfMissing(args);
        LOG.info("server watch dog is starting up");
        startPingTimer(serverTimeouts);
        try {
            int restarts = 0;
            childProcess = startChildProcess(args, restarts, serverTimeouts);
            setChildStatus(CHILD_STATUS.RUNNING);
            while (true) {
                if (!childProcess.ping()) {
                    LOG.info("bad ping. restarting.");
                    restarts++;
                    if (serverTimeouts.getMaxRestarts() > -1 && restarts >= serverTimeouts.getMaxRestarts()) {
                        LOG.warn("hit max restarts: "+restarts+". Stopping now");
                        break;
                    }
                    setChildStatus(CHILD_STATUS.INITIALIZING);
                    lastPing = null;
                    childProcess.close();
                    LOG.info("About to restart the child process");
                    childProcess = startChildProcess(args, restarts, serverTimeouts);
                    LOG.info("Successfully restarted child process -- {} restarts so far)", restarts);
                    setChildStatus(CHILD_STATUS.RUNNING);
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

    private ChildProcess startChildProcess(String[] args, int restarts,
                                           ServerTimeouts serverTimeouts) throws Exception {
        int consecutiveRestarts = 0;
        //if there's a bind exception, retry for 5 seconds to give the OS
        //a chance to release the port
        int maxBind = 5;
        while (consecutiveRestarts < maxBind) {
            try {
                return new ChildProcess(args, restarts, serverTimeouts);
            } catch (BindException e) {
                LOG.warn("WatchDog observes bind exception on retry {}. " +
                        "Will retry {} times.", consecutiveRestarts, maxBind);
                consecutiveRestarts++;
                Thread.sleep(1000);
                if (consecutiveRestarts > maxBind) {
                    throw e;
                }
            }
        }
        throw new RuntimeException("Couldn't start child process");
    }

    private String[] addIdIfMissing(String[] args) {
        for (String arg : args) {
            //id is already specified, leave the array as is
            if (arg.equals("-i") || arg.equals("--id")) {
                return args;
            }
        }

        String[] newArgs = new String[args.length+2];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = "--id";
        newArgs[args.length+1] = UUID.randomUUID().toString();
        return newArgs;
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
        private final DataOutputStream toChild;
        private final ServerTimeouts serverTimeouts;
        private final Path childStatusFile;
        private final ByteBuffer statusBuffer = ByteBuffer.allocate(16);

        private ChildProcess(String[] args, int numRestarts, ServerTimeouts serverTimeouts) throws Exception {
            String prefix = DEFAULT_CHILD_STATUS_FILE_PREFIX;
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-tmpFilePrefix")) {
                    prefix = args[i+1];
                }
            }

            this.childStatusFile = Files.createTempFile(prefix, "");
            this.serverTimeouts = serverTimeouts;
            this.process = startProcess(args, numRestarts, childStatusFile);

            //wait for file to be written/initialized by child process
            Instant start = Instant.now();
            long elapsed = Duration.between(start, Instant.now()).toMillis();
            try {
                while (process.isAlive() && Files.size(childStatusFile) < 12
                        && elapsed < serverTimeouts.getMaxChildStartupMillis()) {
                    Thread.sleep(50);
                    elapsed = Duration.between(start, Instant.now()).toMillis();
                }
            } catch (IOException e) {
                //the childStatusFile can be deleted by the
                //child process if it closes...this can lead to a NoSuchFileException
                LOG.warn("failed to start child process", e);
            }

            if (elapsed > serverTimeouts.getMaxChildStartupMillis()) {
                close();
                throw new RuntimeException("Child process failed to start after "+elapsed + " (ms)");
            }
            if (!process.isAlive()) {
                close();
                if (process.exitValue() == TikaServerCli.BIND_EXCEPTION) {
                    throw new BindException("couldn't bind");
                }
                throw new RuntimeException("Failed to start child process -- child is not alive");
            }
            if (!Files.exists(childStatusFile)) {
                close();
                throw new RuntimeException("Failed to start child process -- child status file does not exist");
            }

            this.toChild = new DataOutputStream(process.getOutputStream());
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
            ChildStatus childStatus = null;
            try {
                childStatus = readStatus();
            } catch (Exception e) {
                LOG.warn("Exception reading status from child", e);
                return false;
            }

            if (childStatus.status != ServerStatus.STATUS.OPERATING.getInt()) {
                LOG.warn("Received non-operating status from child: {}",
                        ServerStatus.STATUS.lookup(childStatus.status));
                return false;
            }

            long elapsedSinceLastUpdate =
                    Duration.between(Instant.ofEpochMilli(childStatus.timestamp), Instant.now()).toMillis();
            LOG.debug("last update: {}, elapsed:{}, status:{}", childStatus.timestamp, elapsedSinceLastUpdate,
                    childStatus.status);

            if (elapsedSinceLastUpdate >
                    serverTimeouts.getPingTimeoutMillis()) {
                //child hasn't written a status update in a longer time than allowed
                LOG.warn("Child's last update exceeded ping timeout: {} (ms) with status {}",
                        elapsedSinceLastUpdate, childStatus.status);
                return false;
            }

            lastPing = Instant.now();
            return true;
        }

        private ChildStatus readStatus() throws Exception {
            Instant started = Instant.now();
            Long elapsed = Duration.between(started, Instant.now()).toMillis();
            //only reading, but need to include write to allow for locking
            try (FileChannel fc = FileChannel.open(childStatusFile, READ, WRITE)) {

                while (elapsed < serverTimeouts.getPingTimeoutMillis()) {
                    try (FileLock lock = fc.tryLock(0, 16, true)) {
                        if (lock != null) {
                            ((Buffer)statusBuffer).position(0);
                            fc.read(statusBuffer);
                            long timestamp = statusBuffer.getLong(0);
                            int status = statusBuffer.getInt(8);
                            int numTasks = statusBuffer.getInt(12);
                            return new ChildStatus(timestamp, status, numTasks);
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

            if (childStatusFile != null) {
                try {
                    if (Files.isRegularFile(childStatusFile)) {
                        Files.delete(childStatusFile);
                    }
                    LOG.debug("deleted "+childStatusFile);
                } catch (IOException e) {
                    LOG.warn("problem deleting child status file", e);
                }
            }

        }

        private Process startProcess(String[] args, int numRestarts, Path childStatusFile) throws IOException {

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
            argList.add("--numRestarts");
            argList.add(Integer.toString(numRestarts));
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
            try {
                int exitValue = process.exitValue();
                LOG.info("Forked (child) process shut down with exit value: {}", exitValue);
            } catch (IllegalThreadStateException e) {
                LOG.error("Child process still alive when trying to read exit value. " +
                        "Shutting down the parent.");
                System.exit(1);
            }
        } catch (InterruptedException e) {
            LOG.warn("interrupted exception while trying to destroy the forked process");
            //swallow
        }
    }

    private static class ChildStatus {
        private final long timestamp;
        private final int status;
        private final int numTasks;

        public ChildStatus(long timestamp, int status, int numTasks) {
            this.timestamp = timestamp;
            this.status = status;
            this.numTasks = numTasks;
        }

        @Override
        public String toString() {
            return "ChildStatus{" +
                    "timestamp=" + timestamp +
                    ", status=" + status +
                    ", numTasks=" + numTasks +
                    '}';
        }
    }

}
