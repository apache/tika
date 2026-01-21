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



import static org.apache.tika.pipes.api.PipesResult.RESULT_STATUS.TIMEOUT;
import static org.apache.tika.pipes.api.PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH;
import static org.apache.tika.pipes.core.PipesClient.COMMANDS.ACK;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.FINISHED;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.READY;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;
import org.apache.tika.pipes.core.server.IntermediateResult;
import org.apache.tika.pipes.core.server.PipesServer;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.ProcessUtils;
import org.apache.tika.utils.StringUtils;

/**
 * The PipesClient is designed to be single-threaded. It only allots
 * a single thread for {@link #process(FetchEmitTuple)} processing.
 * See {@link org.apache.tika.pipes.core.async.AsyncProcessor} for handling
 * multiple PipesClients.
 */
public class PipesClient implements Closeable {

    public enum COMMANDS {
        PING, ACK, NEW_REQUEST, SHUT_DOWN;

        public byte getByte() {
            return (byte) (ordinal() + 1);
        }
    }


    private static final Logger LOG = LoggerFactory.getLogger(PipesClient.class);
    private static final AtomicInteger CLIENT_COUNTER = new AtomicInteger(0);
    private static final long WAIT_ON_DESTROY_MS = 10000;
    public static final int SOCKET_CONNECT_TIMEOUT_MS = 60000;
    public static final int SOCKET_TIMEOUT_MS = 60000;


    private final PipesConfig pipesConfig;
    private final Path tikaConfigPath;
    private final int pipesClientId;
    private final Thread shutdownHook;
    private ServerTuple serverTuple;
    private int filesProcessed = 0;

    public PipesClient(PipesConfig pipesConfig, Path tikaConfigPath) {
        this.pipesConfig = pipesConfig;
        this.tikaConfigPath = tikaConfigPath;
        this.pipesClientId = CLIENT_COUNTER.getAndIncrement();
        this.shutdownHook = new Thread(this::cleanupOnShutdown, "PipesClient-shutdown-" + pipesClientId);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void cleanupOnShutdown() {
        if (serverTuple != null && serverTuple.tmpDir != null) {
            deleteDir(serverTuple.tmpDir);
        }
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    private boolean ping() {
        if (serverTuple == null) {
            return false;
        }
        if (serverTuple.process == null || !serverTuple.process.isAlive()) {
            return false;
        }
        try {
            serverTuple.output.write(COMMANDS.PING.getByte());
            serverTuple.output.flush();
            int ping = serverTuple.input.read();
            if (ping == COMMANDS.PING.getByte()) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
            // JVM is already shutting down, ignore
        }
        try {
            shutItAllDown();
        } catch (InterruptedException e) {
            //swallow
        }
    }

    public int getPipesClientId() {
        return pipesClientId;
    }

    private void shutItAllDown() throws InterruptedException {
        if (serverTuple == null) {
            return;
        }
        LOG.debug("pipesClientId={}: shutting down server", pipesClientId);
        try {
            serverTuple.output.write(COMMANDS.SHUT_DOWN.getByte());
            serverTuple.output.flush();
        } catch (IOException e) {
            //swallow
        }
        List<IOException> exceptions = new ArrayList<>();
        tryToClose(serverTuple.input, exceptions);
        tryToClose(serverTuple.output, exceptions);
        tryToClose(serverTuple.socket, exceptions);
        tryToClose(serverTuple.serverSocket, exceptions);
        destroyForcibly();

        deleteDir(serverTuple.tmpDir);
        serverTuple = null;

    }

    private void tryToClose(Closeable closeable, List<IOException> exceptions) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            exceptions.add(e);
        }
    }

    public PipesResult process(FetchEmitTuple t) throws IOException, InterruptedException {
        //container object to hold latest intermediate result if the parser is doing that
        IntermediateResult intermediateResult = new IntermediateResult();
        PipesResult result = null;
        try {
            maybeInit();
        } catch (ServerInitializationException e) {
            LOG.error("server initialization failed: {} ", t.getId(), e);
            shutItAllDown();
            return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, intermediateResult.get(), e.getMessage());
        } catch (SecurityException e) {
            LOG.error("security exception during initialization: {} ", t.getId());
            shutItAllDown();
            return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE, intermediateResult.get());
        }

        try {
            writeTask(t);
            result = waitForServer(t, intermediateResult);
            filesProcessed++;
        } catch (InterruptedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("exception waiting for server to complete task: {} ", t.getId(), e);
            shutItAllDown();
            return buildFatalResult(t.getId(), t.getEmitKey(), UNSPECIFIED_CRASH, intermediateResult.get());
        }
        return result;
    }

    private void maybeInit() throws InterruptedException, ServerInitializationException {
        boolean restart = false;
        if (!ping()) {
            restart = true;
        } else if (pipesConfig.getMaxFilesProcessedPerProcess() > 0 && filesProcessed >= pipesConfig.getMaxFilesProcessedPerProcess()) {
            LOG.info("pipesClientId={}: restarting server after hitting max files: {}", pipesClientId, filesProcessed);
            restart = true;
        }
        int maxRestartAttempts = 3;
        int restartAttempts = 0;
        if (restart) {
            while (true) {
                try {
                    restart();
                    return;
                } catch (ServerInitializationException e) {
                    // Server initialization failed - don't retry, rethrow immediately with original message
                    throw e;
                } catch (TimeoutException e) {
                    LOG.warn("pipesClientId={}: couldn't restart within {} ms (startupTimeoutMillis)", pipesClientId, pipesConfig.getStartupTimeoutMillis());
                    if (++restartAttempts >= maxRestartAttempts) {
                        throw new ServerInitializationException("couldn't start server", e);
                    }
                } catch (IOException e) {
                    LOG.warn("pipesClientId={}: couldn't restart", pipesClientId, e);
                    if (++restartAttempts > 0) {
                        throw new ServerInitializationException("couldn't start server", e);
                    }
                }
            }
        }
    }

    private void writeTask(FetchEmitTuple t) throws IOException {
        LOG.debug("pipesClientId={}: sending NEW_REQUEST for id={}", pipesClientId, t.getId());
        UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream
                .builder()
                .get();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
            objectOutputStream.writeObject(t);
        }

        byte[] bytes = bos.toByteArray();
        serverTuple.output.write(COMMANDS.NEW_REQUEST.getByte());
        serverTuple.output.writeInt(bytes.length);
        serverTuple.output.write(bytes);
        serverTuple.output.flush();
    }

    private PipesResult waitForServer(FetchEmitTuple t, IntermediateResult intermediateResult) throws InterruptedException {
        long timeoutMillis = getTimeoutMillis(pipesConfig, t.getParseContext());
        Instant start = Instant.now();
        //TODO - implement progress updates
        Instant lastUpdate = start;
        long lastProgressCounter = 0;
        while (true) {
            if (Thread
                    .currentThread()
                    .isInterrupted()) {
                throw new InterruptedException("thread interrupt");
            }
            long totalElapsed = Duration.between(start, Instant.now()).toMillis();
            if ( totalElapsed > timeoutMillis) {
                LOG.warn("clientId={}: timeout on client side: id={} elapsed={} timeoutMillis={}", pipesClientId, t.getId(), totalElapsed, timeoutMillis);
                return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.TIMEOUT, intermediateResult.get());
            }
            try {
                //read blocks on the socket
                PipesServer.PROCESSING_STATUS status = readServerStatus();
                LOG.trace("clientId={}: switch status id={} status={}", pipesClientId, t.getId(), status);
                String msg = null;
                switch (status) {
                    case OOM:
                        msg = readResult(String.class);
                        shutItAllDown();
                        return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.OOM, intermediateResult.get(), msg);
                    case TIMEOUT:
                        msg = readResult(String.class);
                        shutItAllDown();
                        return buildFatalResult(t.getId(), t.getEmitKey(), TIMEOUT, intermediateResult.get(), msg);
                    case UNSPECIFIED_CRASH:
                        msg = readResult(String.class);
                        shutItAllDown();
                        return buildFatalResult(t.getId(), t.getEmitKey(), UNSPECIFIED_CRASH, intermediateResult.get(), msg);
                    case INTERMEDIATE_RESULT:
                        intermediateResult.set(readResult(Metadata.class));
                        lastUpdate = Instant.now();
                        break;
                    case WORKING:
                        lastProgressCounter = readProgressCounter();
                        lastUpdate = Instant.now();
                        break;
                    case FINISHED:
                        return readResult(PipesResult.class);
                }
            } catch (SocketTimeoutException e) {
                LOG.warn("clientId={}: Socket timeout exception while waiting for server", pipesClientId, e);
                shutItAllDown();
                return buildFatalResult(t.getId(), t.getEmitKey(), TIMEOUT, intermediateResult.get(), ExceptionUtils.getStackTrace(e));
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                //TODO -- catch socket timeout separately
                LOG.warn("clientId={} - crash while waiting for server", pipesClientId, e);
                serverTuple.process.waitFor(1, TimeUnit.SECONDS);
                PipesResult.RESULT_STATUS status = UNSPECIFIED_CRASH;
                if (! serverTuple.process.isAlive()) {
                    int exitValue = serverTuple.process.exitValue();
                    if (exitValue == PipesServer.OOM_EXIT_CODE) {
                        status = PipesResult.RESULT_STATUS.OOM;
                    } else if (exitValue == PipesServer.TIMEOUT_EXIT_CODE) {
                        status = PipesResult.RESULT_STATUS.TIMEOUT;
                    }
                    LOG.warn("clientId={}: exit value{}", pipesClientId, serverTuple.process.exitValue());
                } else {
                    LOG.warn("clientId={}: process still alive ?!", pipesClientId);
                }
                shutItAllDown();
                return buildFatalResult(t.getId(), t.getEmitKey(), status, intermediateResult.get(), ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private long readProgressCounter() throws IOException {
        return serverTuple.input.readLong();
    }


    private PipesResult buildFatalResult(String id, EmitKey emitKey, PipesResult.RESULT_STATUS status, Optional<Metadata> intermediateResultOpt) {
        return buildFatalResult(id, emitKey, status, intermediateResultOpt, null);
    }

    private PipesResult buildFatalResult(String id, EmitKey emitKey, PipesResult.RESULT_STATUS status, Optional<Metadata> intermediateResultOpt, String msg) {
        LOG.warn("clientId={}: crash id={} status={}", pipesClientId, id, status);
        Metadata intermediateResult = intermediateResultOpt.orElse(new Metadata());

        if (LOG.isTraceEnabled()) {
            LOG.trace("clientId={}: intermediate result: id={}", pipesClientId, intermediateResult);
        }
        intermediateResult.set(TikaCoreProperties.PIPES_RESULT, status.toString());
        if (StringUtils.isBlank(msg)) {
            return new PipesResult(status, new EmitDataImpl(emitKey.getEmitKey(), List.of(intermediateResult)));
        } else {
            return new PipesResult(status, new EmitDataImpl(emitKey.getEmitKey(), List.of(intermediateResult)), msg);
        }

    }

    private void pauseThenDestroy() throws InterruptedException {
        //wait just a little bit to let process end to get exit value
        //if there's a timeout on the server side
        try {
            serverTuple.process.waitFor(200, TimeUnit.MILLISECONDS);
        } finally {
            destroyForcibly();
        }
    }

    private void destroyForcibly() throws InterruptedException {
        serverTuple.process.destroyForcibly();
        serverTuple.process.waitFor(WAIT_ON_DESTROY_MS, TimeUnit.MILLISECONDS);

        if (serverTuple.process.isAlive()) {
            LOG.error("clientId={}: process still alive after {}ms", pipesClientId, WAIT_ON_DESTROY_MS);
        }
    }


    private PipesServer.PROCESSING_STATUS readServerStatus() throws IOException {
        int statusByte = serverTuple.input.read();
        writeAck();
        PipesServer.PROCESSING_STATUS status = null;
        try {
            status = PipesServer.PROCESSING_STATUS.lookup(statusByte);
        } catch (IllegalArgumentException e) {
            String byteString = "-1";
            if (statusByte > -1) {
                byteString = String.format(Locale.US, "%02x", (byte) statusByte);
            }
            throw new IOException("problem reading response from server: " + byteString, e);
        }
        return status;
    }


    private PipesResult.RESULT_STATUS readResultStatus() throws IOException {
        int statusByte = serverTuple.input.read();
        PipesResult.RESULT_STATUS status = null;
        try {
            status = PipesResult.RESULT_STATUS.lookup(statusByte);
        } catch (IllegalArgumentException e) {
            String byteString = "-1";
            if (statusByte > -1) {
                byteString = String.format(Locale.US, "%02x", (byte) statusByte);
            }
            throw new IOException("problem reading response from server: " + byteString, e);
        }
        return status;
    }

    private <T> T readResult(Class<? extends T> clazz) throws IOException {
        int len = serverTuple.input.readInt();
        byte[] bytes = new byte[len];
        serverTuple.input.readFully(bytes);

        writeAck();

        try (ObjectInputStream objectInputStream = new ObjectInputStream(UnsynchronizedByteArrayInputStream
                .builder()
                .setByteArray(bytes)
                .get())) {
            return clazz.cast(objectInputStream.readObject());
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
    }

    private void writeAck() throws IOException {
        serverTuple.output.write(ACK.getByte());
        serverTuple.output.flush();
    }


    private void restart() throws InterruptedException, IOException, TimeoutException {
        ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        int port = serverSocket.getLocalPort();
        if (serverTuple != null && serverTuple.process != null) {
            int oldPort = serverTuple.serverSocket.getLocalPort();
            shutItAllDown();
            LOG.info("clientId={}: restarting process on port={} (old port was {})", pipesClientId, port, oldPort);
        } else {
            LOG.info("clientId={}: starting process on port={}", pipesClientId, port);
        }
        Path tmpDir = Files.createTempDirectory("pipes-server-" + pipesClientId + "-");
        ProcessBuilder pb = new ProcessBuilder(getCommandline(port, tmpDir));
        pb.inheritIO();
        Process process = null;
        try {
            process = pb.start();
        } catch (Exception e) {
            deleteDir(tmpDir);
            //Do we ever want this to be not fatal?!
            LOG.error("clientId={}: failed to start server", pipesClientId, e);
            String msg = "Failed to start server process";
            if (e.getMessage() != null) {
                msg += ": " + e.getMessage();
            }
            throw new ServerInitializationException(msg, e);
        }
        // Poll for connection with periodic checks if process is still alive
        serverSocket.setSoTimeout(1000); // 1 second timeout for each poll
        Socket socket = null;
        long startTime = System.currentTimeMillis();
        while (socket == null) {
            try {
                socket = serverSocket.accept();
            } catch (SocketTimeoutException e) {
                // Check if the process died before connecting
                if (!process.isAlive()) {
                    int exitValue = process.exitValue();
                    LOG.error("clientId={}: Process exited with code {} before connecting to socket", pipesClientId, exitValue);
                    deleteDir(tmpDir);
                    throw new ServerInitializationException("Process failed to start (exit code " + exitValue + "). Check JVM arguments and classpath.");
                }
                // Check if we've exceeded the overall timeout
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > SOCKET_CONNECT_TIMEOUT_MS) {
                    LOG.error("clientId={}: Timed out waiting for server to connect after {}ms", pipesClientId, elapsed);
                    deleteDir(tmpDir);
                    throw new ServerInitializationException("Server did not connect within " + SOCKET_CONNECT_TIMEOUT_MS + "ms");
                }
                // Continue polling
            }
        }
        socket.setSoTimeout((int) pipesConfig.getSocketTimeoutMs());
        socket.setTcpNoDelay(true); // Disable Nagle's algorithm to avoid ~40ms delays on small writes
        serverTuple = new ServerTuple(process, serverSocket, socket, new DataInputStream(socket.getInputStream()),
                new DataOutputStream(socket.getOutputStream()), tmpDir);
        waitForStartup();
    }

    private void waitForStartup() throws IOException {
        //wait for ready byte
        int b = serverTuple.input.read();
        writeAck();
        if (b == READY.getByte()) {
            LOG.debug("clientId={}: server ready", pipesClientId);
        } else if (b == FINISHED.getByte()) {
            int len = serverTuple.input.readInt();
            byte[] bytes = new byte[len];
            serverTuple.input.readFully(bytes);
            writeAck();
            String msg = new String(bytes, StandardCharsets.UTF_8);
            LOG.error("clientId={}: Server failed to start: {}", pipesClientId, msg);
            throw new ServerInitializationException(msg);
        } else {
            LOG.error("clientId={}: Unexpected first byte: {}", pipesClientId, HexFormat.of().formatHex(new byte[]{ (byte) b }));
            throw new IOException("Unexpected first byte from server: " + HexFormat.of().formatHex(new byte[]{ (byte) b }));
        }

    }

    private void deleteDir(Path tmpDir) {
        try {
            FileUtils.deleteDirectory(tmpDir.toFile());
        } catch (IOException e) {
            //swallow
            LOG.warn("couldn't delete tmp dir {}", tmpDir);
        }
    }

    private static String getMsg(String msg, UnsynchronizedByteArrayOutputStream bos) {
        String readSoFar = bos.toString(StandardCharsets.UTF_8);
        if (StringUtils.isBlank(readSoFar)) {
            return msg;
        } else {
            return msg + "So far, I've read: >" + readSoFar + "<";
        }
    }

    private String[] getCommandline(int port, Path tmpDir) {
        List<String> configArgs = pipesConfig.getForkedJvmArgs();
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
                newGCLogString = arg.replace("${pipesClientId}", "id-" + pipesClientId);
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
            commandLine.add("-cp");
            commandLine.add(System.getProperty("java.class.path"));
        }
        if (!hasHeadless) {
            commandLine.add("-Djava.awt.headless=true");
        }
        if (hasExitOnOOM) {
            LOG.warn("I notice that you have a jdk setting to exit/crash on OOM. If you run heavy external processes " +
                    "like tesseract, this setting may result in orphaned processes which could be disastrous" + " for performance.");
        }
        if (!hasLog4j) {
            commandLine.add("-Dlog4j.configurationFile=classpath:pipes-fork-server-default-log4j2.xml");
        }
        commandLine.add("-DpipesClientId=" + pipesClientId);
        commandLine.addAll(configArgs);
        commandLine.add("-Djava.io.tmpdir=" + tmpDir.toAbsolutePath());
        commandLine.add("org.apache.tika.pipes.core.server.PipesServer");

        commandLine.add(Integer.toString(port));
        commandLine.add(tikaConfigPath.toAbsolutePath().toString());
        LOG.debug("pipesClientId={}: commandline: {}", pipesClientId, commandLine);
        return commandLine.toArray(new String[0]);
    }

    private record ServerTuple(Process process, ServerSocket serverSocket, Socket socket,
                               DataInputStream input, DataOutputStream output, Path tmpDir) {

    }

    public static long getTimeoutMillis(PipesConfig pipesConfig, ParseContext parseContext) {
        long defaultTimeoutMillis = pipesConfig.getTimeoutMillis();

        if (parseContext == null) {
            return defaultTimeoutMillis;
        }

        TikaTaskTimeout tikaTaskTimeout = parseContext.get(TikaTaskTimeout.class);
        if (tikaTaskTimeout == null) {
            return defaultTimeoutMillis;
        }
        LOG.debug("applying timeout from parseContext: {}ms", tikaTaskTimeout.getTimeoutMillis());
        return tikaTaskTimeout.getTimeoutMillis();
    }
}
