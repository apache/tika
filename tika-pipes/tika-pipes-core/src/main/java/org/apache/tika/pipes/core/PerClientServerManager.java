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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.utils.ProcessUtils;

/**
 * Manages a dedicated PipesServer process for a single PipesClient.
 * <p>
 * This is the default mode where each PipesClient has its own server process,
 * providing isolation at the cost of memory overhead.
 * <p>
 * Connection model: The client creates a ServerSocket and the server connects TO it.
 * This is the reverse of typical client-server patterns but allows the client to
 * control the port assignment.
 */
public class PerClientServerManager implements ServerManager {

    private static final Logger LOG = LoggerFactory.getLogger(PerClientServerManager.class);
    private static final long WAIT_ON_DESTROY_MS = 10000;
    public static final int SOCKET_CONNECT_TIMEOUT_MS = 60000;

    private final PipesConfig pipesConfig;
    private final Path tikaConfigPath;
    private final int clientId;

    private Process process;
    private ServerSocket serverSocket;
    private Path tmpDir;
    private int port = -1;
    private long filesProcessed = 0;
    private boolean pendingRestart = false;

    public PerClientServerManager(PipesConfig pipesConfig, Path tikaConfigPath, int clientId) {
        this.pipesConfig = pipesConfig;
        this.tikaConfigPath = tikaConfigPath;
        this.clientId = clientId;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Path getTempDirectory() {
        return tmpDir;
    }

    @Override
    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    @Override
    public void incrementFilesProcessed(long maxFilesPerProcess) {
        if (maxFilesPerProcess <= 0) {
            return;
        }
        filesProcessed++;
        if (filesProcessed >= maxFilesPerProcess) {
            LOG.info("clientId={}: reached max files limit ({}/{}), marking for restart",
                    clientId, filesProcessed, maxFilesPerProcess);
            pendingRestart = true;
        }
    }

    @Override
    public boolean needsRestart() {
        return pendingRestart;
    }

    @Override
    public void markServerForRestart() {
        LOG.info("clientId={}: marking server for restart", clientId);
        pendingRestart = true;
    }

    @Override
    public int handleCrashAndGetExitCode() {
        pendingRestart = true;
        if (process != null) {
            try {
                process.waitFor(1, TimeUnit.SECONDS);
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    LOG.warn("clientId={}: process exited with code {}", clientId, exitValue);
                    return exitValue;
                } else {
                    LOG.warn("clientId={}: process still running after crash", clientId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return -1;
    }

    @Override
    public void ensureRunning() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        // Check if server is running AND not marked for restart
        if (isRunning() && !pendingRestart) {
            return;
        }
        startServer();
    }

    @Override
    public Socket connect(int socketTimeoutMs) throws IOException, ServerInitializationException {
        if (serverSocket == null) {
            throw new IllegalStateException("Server not started. Call ensureRunning() first.");
        }

        // Accept incoming connection from the server process
        serverSocket.setSoTimeout(1000); // 1 second timeout for each poll
        long startTime = System.currentTimeMillis();

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(socketTimeoutMs);
                socket.setTcpNoDelay(true);
                LOG.debug("clientId={}: accepted connection from server", clientId);
                return socket;
            } catch (SocketTimeoutException e) {
                // Check if the process died before connecting
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    LOG.error("clientId={}: Process exited with code {} before connecting to socket", clientId, exitValue);
                    throw new ServerInitializationException(
                            "Process failed to start (exit code " + exitValue + "). Check JVM arguments and classpath.");
                }
                // Check if we've exceeded the overall timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > SOCKET_CONNECT_TIMEOUT_MS) {
                    LOG.error("clientId={}: Timed out waiting for server to connect after {}ms", clientId, elapsed);
                    throw new ServerInitializationException(
                            "Server did not connect within " + SOCKET_CONNECT_TIMEOUT_MS + "ms");
                }
                // Continue polling
            }
        }
    }

    private void startServer() throws IOException, InterruptedException, TimeoutException, ServerInitializationException {
        // Clean up any previous server
        if (process != null || serverSocket != null || tmpDir != null) {
            shutdown();
        }

        // Create new server socket to get a free port
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 50);
        port = serverSocket.getLocalPort();

        LOG.info("clientId={}: starting server on port={}", clientId, port);

        tmpDir = Files.createTempDirectory("pipes-server-" + clientId + "-");
        ProcessBuilder pb = new ProcessBuilder(getCommandline());
        pb.inheritIO();

        try {
            process = pb.start();
        } catch (Exception e) {
            deleteDir(tmpDir);
            tmpDir = null;
            LOG.error("clientId={}: failed to start server", clientId, e);
            String msg = "Failed to start server process";
            if (e.getMessage() != null) {
                msg += ": " + e.getMessage();
            }
            throw new ServerInitializationException(msg, e);
        }

        // Server is started, but we don't wait for connection here.
        // The connection is established in connect() method.
        LOG.debug("clientId={}: server process started, waiting for connection in connect()", clientId);

        // Reset counters after successful start
        filesProcessed = 0;
        pendingRestart = false;
    }

    @Override
    public void shutdown() throws InterruptedException {
        LOG.debug("clientId={}: shutting down server", clientId);

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.debug("Error closing server socket", e);
            }
            serverSocket = null;
        }

        destroyProcess();

        if (tmpDir != null) {
            deleteDir(tmpDir);
            tmpDir = null;
        }

        port = -1;
    }

    private void destroyProcess() throws InterruptedException {
        if (process != null) {
            process.destroyForcibly();
            process.waitFor(WAIT_ON_DESTROY_MS, TimeUnit.MILLISECONDS);
            if (process.isAlive()) {
                LOG.error("clientId={}: process still alive after {}ms", clientId, WAIT_ON_DESTROY_MS);
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

    /**
     * Returns the ServerSocket for accepting client connections.
     * Used by PipesClient to accept the server's incoming connection.
     *
     * @return the server socket
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Returns the server process.
     *
     * @return the process, or null if not started
     */
    public Process getProcess() {
        return process;
    }

    private void deleteDir(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            FileUtils.deleteDirectory(dir.toFile());
        } catch (IOException e) {
            LOG.warn("couldn't delete tmp dir {}", dir);
        }
    }

    private String[] getCommandline() throws IOException {
        List<String> configArgs = new ArrayList<>(pipesConfig.getForkedJvmArgs());
        boolean hasClassPath = false;
        boolean hasHeadless = false;
        boolean hasExitOnOOM = false;
        boolean hasLog4j = false;
        String origGCString = null;
        String newGCLogString = null;

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
            if (arg.startsWith("-Xloggc:")) {
                origGCString = arg;
                newGCLogString = arg.replace("${pipesClientId}", "id-" + clientId);
            }
        }

        if (origGCString != null && newGCLogString != null) {
            configArgs.remove(origGCString);
            configArgs.add(newGCLogString);
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
            LOG.warn("I notice that you have a jdk setting to exit/crash on OOM. If you run heavy external processes " +
                    "like tesseract, this setting may result in orphaned processes which could be disastrous for performance.");
        }
        if (!hasLog4j) {
            commandLine.add("-Dlog4j.configurationFile=classpath:pipes-fork-server-default-log4j2.xml");
        }
        commandLine.add("-DpipesClientId=" + clientId);
        commandLine.addAll(configArgs);
        commandLine.add("-Djava.io.tmpdir=" + tmpDir.toAbsolutePath());
        commandLine.add("org.apache.tika.pipes.core.server.PipesServer");

        commandLine.add(Integer.toString(port));
        commandLine.add(tikaConfigPath.toAbsolutePath().toString());

        LOG.debug("clientId={}: commandline: {}", clientId, commandLine);
        return commandLine.toArray(new String[0]);
    }

    private Path writeArgFile() throws IOException {
        Path argFile = tmpDir.resolve("jvm-args.txt");
        String classpath = System.getProperty("java.class.path");
        String normalizedClasspath = classpath.replace("\\", "/");
        String content = "-cp\n\"" + normalizedClasspath + "\"\n";
        Files.writeString(argFile, content, StandardCharsets.UTF_8);
        LOG.debug("clientId={}: wrote argfile with classpath ({} chars) to {}",
                clientId, classpath.length(), argFile);
        return argFile;
    }
}
