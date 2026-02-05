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
package org.apache.tika.pipes.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.utils.ProcessUtils;

/**
 * Manages a single shared PipesServer process for multiple PipesClients.
 * <p>
 * This mode reduces memory overhead by running one JVM instead of N JVMs,
 * at the cost of reduced isolation - one crash affects all in-flight requests.
 * <p>
 * Connection model: The shared server creates a ServerSocket and accepts
 * incoming connections from clients. Each client connects TO the server.
 * <p>
 * Thread safety: {@link #ensureRunning()} is synchronized to prevent multiple
 * clients from attempting to restart the server simultaneously.
 */
public class SharedServerManager implements ServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(SharedServerManager.class);
    private static final long WAIT_ON_DESTROY_MS = 10000;
    private static final int STARTUP_TIMEOUT_MS = 60000;
    public static final int SOCKET_CONNECT_TIMEOUT_MS = 60000;

    private final PipesConfig pipesConfig;
    private final Path tikaConfigPath;
    private final int numConnections;

    private final Object lock = new Object();
    private final AtomicLong filesProcessed = new AtomicLong(0);
    private volatile boolean restarting = false;
    private volatile boolean pendingRestart = false; // Set when a client reports fatal error or max files reached
    private Process process;
    private Path tmpDir;
    private int serverPort = -1;

    /**
     * Creates a SharedServerManager.
     *
     * @param pipesConfig the pipes configuration
     * @param tikaConfigPath path to the tika config file
     * @param numConnections number of concurrent connections the server should support
     */
    public SharedServerManager(PipesConfig pipesConfig, Path tikaConfigPath, int numConnections) {
        this.pipesConfig = pipesConfig;
        this.tikaConfigPath = tikaConfigPath;
        this.numConnections = numConnections;
    }

    /**
     * Returns the current server port, blocking if a restart is in progress.
     * This ensures clients always see a consistent port after restart completes.
     */
    @Override
    public int getPort() {
        synchronized (lock) {
            while (restarting) {
                try {
                    LOG.debug("getPort() waiting for restart to complete");
                    lock.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return serverPort;
                }
            }
            return serverPort;
        }
    }

    @Override
    public Path getTempDirectory() {
        return tmpDir;
    }

    @Override
    public boolean isRunning() {
        synchronized (lock) {
            return process != null && process.isAlive();
        }
    }

    /**
     * Ensures the shared server is running, starting it if necessary.
     * <p>
     * This method is synchronized - only one client will start/restart the server
     * while others wait. The restarting flag is set during restart so that
     * getPort() blocks until the new server is ready.
     */
    @Override
    public void ensureRunning() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        synchronized (lock) {
            // Check if server is alive AND hasn't been marked for restart by a client
            if (isProcessAlive() && !pendingRestart) {
                return;
            }
            restarting = true;
            try {
                startServer();
                pendingRestart = false; // Clear the flag after successful restart
                filesProcessed.set(0); // Reset file counter on restart
            } finally {
                restarting = false;
                lock.notifyAll(); // Wake up any threads waiting in getPort()
            }
        }
    }

    /**
     * Marks the server for restart due to a fatal error (OOM, timeout).
     * <p>
     * This is called by clients when they receive OOM or TIMEOUT status.
     * It signals that the server process is stopping (System.exit was called),
     * even if isRunning() might still return true briefly.
     * <p>
     * The next call to ensureRunning() will wait for the process to fully
     * exit and then restart the server.
     */
    @Override
    public void markServerForRestart() {
        synchronized (lock) {
            LOG.info("Server marked for restart - will restart on next ensureRunning()");
            pendingRestart = true;
        }
    }

    /**
     * Increments the count of files processed and marks for restart if limit reached.
     */
    @Override
    public void incrementFilesProcessed(long maxFilesPerProcess) {
        if (maxFilesPerProcess <= 0) {
            return; // No limit
        }
        long count = filesProcessed.incrementAndGet();
        if (count >= maxFilesPerProcess) {
            synchronized (lock) {
                LOG.info("Shared server reached max files limit ({}/{}), marking for restart",
                        count, maxFilesPerProcess);
                pendingRestart = true;
            }
        }
    }

    /**
     * Checks if the server has been marked for restart.
     */
    @Override
    public boolean needsRestart() {
        synchronized (lock) {
            return pendingRestart;
        }
    }

    private boolean isProcessAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public Socket connect(int socketTimeoutMs) throws IOException, ServerInitializationException {
        int port;
        synchronized (lock) {
            if (!isProcessAlive()) {
                throw new ServerInitializationException("Shared server is not running. Call ensureRunning() first.");
            }
            port = serverPort;
        }

        // Connect to the shared server
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), SOCKET_CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(socketTimeoutMs);
            socket.setTcpNoDelay(true);
            LOG.debug("Connected to shared server on port {}", port);
            return socket;
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException closeEx) {
                e.addSuppressed(closeEx);
            }
            throw e;
        }
    }

    private void startServer() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        // Clean up any previous server
        if (process != null || tmpDir != null) {
            shutdownUnsafe();
        }

        // Find a free port for the server to listen on
        int port;
        try (ServerSocket tempSocket = new ServerSocket()) {
            tempSocket.setReuseAddress(true);
            tempSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            port = tempSocket.getLocalPort();
        }

        LOG.info("Starting shared server on port={} with {} connections", port, numConnections);

        tmpDir = Files.createTempDirectory("pipes-shared-server-");
        ProcessBuilder pb = new ProcessBuilder(getCommandline(port));
        // Redirect stderr to inherit, capture stdout to read the READY signal
        pb.redirectErrorStream(false);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        try {
            process = pb.start();
        } catch (Exception e) {
            deleteDir(tmpDir);
            tmpDir = null;
            LOG.error("Failed to start shared server", e);
            String msg = "Failed to start shared server process";
            if (e.getMessage() != null) {
                msg += ": " + e.getMessage();
            }
            throw new ServerInitializationException(msg, e);
        }

        // Wait for the server to signal it's ready by printing the port
        waitForServerReady(port);
        serverPort = port;
        LOG.info("Shared server started successfully on port {}", serverPort);
    }

    private void waitForServerReady(int expectedPort) throws IOException, ServerInitializationException {
        long startTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            while (true) {
                // Check if process died
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    LOG.error("Shared server process exited with code {} before becoming ready", exitValue);
                    throw new ServerInitializationException(
                            "Shared server failed to start (exit code " + exitValue + "). Check JVM arguments and classpath.");
                }

                // Check timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > STARTUP_TIMEOUT_MS) {
                    LOG.error("Timed out waiting for shared server to start after {}ms", elapsed);
                    destroyProcessUnsafe();
                    throw new ServerInitializationException(
                            "Shared server did not start within " + STARTUP_TIMEOUT_MS + "ms");
                }

                // Try to read a line (with short timeout via available check)
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line != null && line.startsWith("READY:")) {
                        // Server is ready, parse the port
                        String portStr = line.substring("READY:".length()).trim();
                        int actualPort = Integer.parseInt(portStr);
                        if (actualPort != expectedPort) {
                            LOG.warn("Server reported different port {} than expected {}", actualPort, expectedPort);
                        }
                        return;
                    }
                } else {
                    // No data available, sleep briefly
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for server startup", e);
        }
    }

    @Override
    public void shutdown() throws InterruptedException {
        synchronized (lock) {
            shutdownUnsafe();
        }
    }

    private void shutdownUnsafe() throws InterruptedException {
        LOG.debug("Shutting down shared server");

        destroyProcessUnsafe();

        if (tmpDir != null) {
            deleteDir(tmpDir);
            tmpDir = null;
        }

        serverPort = -1;
    }

    private void destroyProcessUnsafe() throws InterruptedException {
        if (process != null) {
            process.destroyForcibly();
            process.waitFor(WAIT_ON_DESTROY_MS, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                LOG.error("Shared server process still alive after {}ms", WAIT_ON_DESTROY_MS);
            }
            process = null;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during shutdown", e);
        }
    }

    private void deleteDir(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(dir.toFile());
        } catch (IOException e) {
            LOG.warn("Couldn't delete tmp dir {}", dir);
        }
    }

    private String[] getCommandline(int port) throws IOException {
        List<String> configArgs = new ArrayList<>(pipesConfig.getForkedJvmArgs());
        boolean hasClassPath = false;
        boolean hasHeadless = false;
        boolean hasExitOnOOM = false;
        boolean hasLog4j = false;

        for (String arg : configArgs) {
            if (arg.startsWith("-Djava.awt.headless")) {
                hasHeadless = true;
            }
            if (arg.equals("-cp") || arg.equals("--classpath")) {
                hasClassPath = true;
            }
            if (arg.equals("-XX:+ExitOnOutOfMemoryError") || arg.equals("-XX:+CrashOnOutOfMemoryError")) {
                hasExitOnOOM = true;
            }
            if (arg.startsWith("-Dlog4j.configuration") || arg.startsWith("-Dlog4j2.configuration")) {
                hasLog4j = true;
            }
        }

        List<String> commandLine = new ArrayList<>();
        String javaPath = pipesConfig.getJavaPath();
        commandLine.add(ProcessUtils.escapeCommandLine(javaPath));

        if (!hasClassPath) {
            Path argFile = writeArgFile();
            commandLine.add("@" + argFile.toAbsolutePath());
        }

        if (!hasHeadless) {
            commandLine.add("-Djava.awt.headless=true");
        }
        if (hasExitOnOOM) {
            LOG.warn("ExitOnOutOfMemoryError/CrashOnOutOfMemoryError is set. In shared mode, " +
                    "an OOM will kill the shared server, affecting all clients.");
        }
        if (!hasLog4j) {
            commandLine.add("-Dlog4j.configurationFile=classpath:pipes-fork-server-default-log4j2.xml");
        }
        commandLine.add("-DpipesClientId=shared");
        commandLine.addAll(configArgs);
        commandLine.add("-Djava.io.tmpdir=" + tmpDir.toAbsolutePath());
        commandLine.add("org.apache.tika.pipes.core.server.PipesServer");

        // Shared mode arguments
        commandLine.add("--shared");
        commandLine.add(Integer.toString(port));
        commandLine.add(Integer.toString(numConnections));
        commandLine.add(tikaConfigPath.toAbsolutePath().toString());

        LOG.debug("Shared server commandline: {}", commandLine);
        return commandLine.toArray(new String[0]);
    }

    private Path writeArgFile() throws IOException {
        Path argFile = tmpDir.resolve("jvm-args.txt");
        String classpath = System.getProperty("java.class.path");
        String normalizedClasspath = classpath.replace("\\", "/");
        String content = "-cp\n\"" + normalizedClasspath + "\"\n";
        Files.writeString(argFile, content, StandardCharsets.UTF_8);
        LOG.debug("Wrote argfile with classpath ({} chars) to {}", classpath.length(), argFile);
        return argFile;
    }
}
