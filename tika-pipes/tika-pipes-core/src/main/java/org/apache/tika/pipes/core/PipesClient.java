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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.pipes.core.server.IntermediateResult;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

/**
 * The PipesClient is designed to be single-threaded. It only allots
 * a single thread for {@link #process(FetchEmitTuple)} processing.
 * See {@link org.apache.tika.pipes.core.async.AsyncProcessor} for handling
 * multiple PipesClients.
 * <p>
 * PipesClient delegates server lifecycle management to a {@link ServerManager}.
 * In per-client mode, each client has its own {@link PerClientServerManager}.
 * In shared mode, all clients share a single {@link SharedServerManager}.
 */
public class PipesClient implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesClient.class);
    private static final AtomicInteger CLIENT_COUNTER = new AtomicInteger(0);
    public static final int SOCKET_CONNECT_TIMEOUT_MS = 60000;
    public static final int SOCKET_TIMEOUT_MS = 60000;

    private final PipesConfig pipesConfig;
    private final ServerManager serverManager;
    private final boolean ownsServerManager;
    private final int pipesClientId;

    private ConnectionTuple connectionTuple;
    private int filesProcessed = 0;

    /**
     * Creates a PipesClient with the given server manager.
     * <p>
     * The caller retains ownership of the server manager and is responsible
     * for closing it. This is used in shared mode where multiple clients
     * share a single server manager.
     *
     * @param pipesConfig the pipes configuration
     * @param serverManager the server manager (per-client or shared)
     */
    public PipesClient(PipesConfig pipesConfig, ServerManager serverManager) {
        this.pipesConfig = pipesConfig;
        this.serverManager = serverManager;
        this.ownsServerManager = false;
        this.pipesClientId = CLIENT_COUNTER.getAndIncrement();
    }

    /**
     * Creates a PipesClient with its own dedicated server process.
     * <p>
     * This is a convenience constructor for per-client mode that creates
     * a {@link PerClientServerManager} internally. The server will be started
     * lazily on first use and shut down when this client is closed.
     *
     * @param pipesConfig the pipes configuration
     * @param tikaConfigPath path to the tika config file
     */
    public PipesClient(PipesConfig pipesConfig, java.nio.file.Path tikaConfigPath) {
        this.pipesConfig = pipesConfig;
        this.pipesClientId = CLIENT_COUNTER.getAndIncrement();
        this.serverManager = new PerClientServerManager(pipesConfig, tikaConfigPath, pipesClientId);
        this.ownsServerManager = true;
    }

    public int getFilesProcessed() {
        return filesProcessed;
    }

    private boolean ping() {
        if (connectionTuple == null) {
            return false;
        }
        // Check if server process is still running
        if (!serverManager.isRunning()) {
            return false;
        }
        try {
            PipesMessage.ping().write(connectionTuple.output);
            PipesMessage response = PipesMessage.read(connectionTuple.input);
            if (response.type() == PipesMessageType.PING) {
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
            closeConnection();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (ownsServerManager) {
            serverManager.close();
        }
    }

    public int getPipesClientId() {
        return pipesClientId;
    }

    /**
     * Closes just this client's connection, not the server.
     * Server lifecycle is managed by PipesParser.
     */
    private void closeConnection() throws InterruptedException {
        if (connectionTuple == null) {
            return;
        }
        LOG.debug("pipesClientId={}: closing connection", pipesClientId);
        try {
            PipesMessage.shutDown().write(connectionTuple.output);
        } catch (IOException e) {
            // swallow
        }
        List<IOException> exceptions = new ArrayList<>();
        tryToClose(connectionTuple.input, exceptions);
        tryToClose(connectionTuple.output, exceptions);
        tryToClose(connectionTuple.socket, exceptions);
        connectionTuple = null;
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
        // Container object to hold latest intermediate result if the parser is doing that
        IntermediateResult intermediateResult = new IntermediateResult();
        PipesResult result = null;
        try {
            maybeInit();
        } catch (ServerInitializationException e) {
            LOG.error("server initialization failed: {} ", t.getId(), e);
            closeConnection();
            return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE,
                    intermediateResult.get(), e.getMessage());
        } catch (SecurityException e) {
            LOG.error("security exception during initialization: {} ", t.getId());
            closeConnection();
            return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.FAILED_TO_INITIALIZE,
                    intermediateResult.get());
        }

        try {
            writeTask(t);
            result = waitForServer(t, intermediateResult);
            filesProcessed++;
            // Update server manager's file counter for maxFilesProcessedPerProcess tracking
            serverManager.incrementFilesProcessed(pipesConfig.getMaxFilesProcessedPerProcess());
        } catch (InterruptedException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("exception waiting for server to complete task: {} ", t.getId(), e);
            closeConnection();
            return buildFatalResult(t.getId(), t.getEmitKey(), UNSPECIFIED_CRASH, intermediateResult.get());
        }
        return result;
    }

    private void maybeInit() throws InterruptedException, ServerInitializationException {
        boolean reconnect = false;

        // Check if server needs restart (marked for restart after crash or reaching file limit)
        if (serverManager.needsRestart()) {
            LOG.debug("pipesClientId={}: server marked for restart - reconnecting", pipesClientId);
            closeConnection();
            reconnect = true;
        }

        // Check if connection is alive
        if (!reconnect && !ping()) {
            reconnect = true;
        }

        if (reconnect) {
            int maxRestartAttempts = 5;
            int restartAttempts = 0;
            long baseDelayMs = 100;

            while (true) {
                try {
                    reconnect();
                    filesProcessed = 0;
                    return;
                } catch (ServerInitializationException e) {
                    // Server initialization failed - don't retry, rethrow immediately
                    throw e;
                } catch (TimeoutException e) {
                    LOG.warn("pipesClientId={}: couldn't reconnect within timeout (attempt {}/{})",
                            pipesClientId, restartAttempts + 1, maxRestartAttempts);
                    if (++restartAttempts >= maxRestartAttempts) {
                        throw new ServerInitializationException("couldn't connect to server after " +
                                restartAttempts + " attempts", e);
                    }
                    // Wait with exponential backoff before retry
                    long delay = baseDelayMs * (1L << Math.min(restartAttempts, 6));
                    Thread.sleep(delay);
                } catch (IOException e) {
                    LOG.warn("pipesClientId={}: couldn't reconnect (attempt {}/{})",
                            pipesClientId, restartAttempts + 1, maxRestartAttempts, e);
                    if (++restartAttempts >= maxRestartAttempts) {
                        throw new ServerInitializationException("couldn't connect to server after " +
                                restartAttempts + " attempts", e);
                    }
                    // Wait with exponential backoff before retry
                    long delay = baseDelayMs * (1L << Math.min(restartAttempts, 6));
                    Thread.sleep(delay);
                }
            }
        }
    }

    /**
     * Establishes or re-establishes connection to the server.
     */
    private void reconnect() throws InterruptedException, IOException, TimeoutException, ServerInitializationException {
        // Close existing connection if any
        if (connectionTuple != null) {
            LOG.debug("pipesClientId={}: closing existing connection before reconnect", pipesClientId);
            closeConnection();
        }

        // Ensure server is running (blocks if restart in progress)
        serverManager.ensureRunning();

        // Get port after ensureRunning - this is the port we'll connect to
        int port = serverManager.getPort();
        LOG.debug("pipesClientId={}: connecting to server", pipesClientId);

        // Connect to server
        Socket socket = serverManager.connect((int) pipesConfig.getSocketTimeoutMs());

        connectionTuple = new ConnectionTuple(socket,
                new DataInputStream(new BufferedInputStream(socket.getInputStream())),
                new DataOutputStream(new BufferedOutputStream(socket.getOutputStream())));

        waitForStartup();
    }

    private void writeTask(FetchEmitTuple t) throws IOException {
        LOG.debug("pipesClientId={}: sending NEW_REQUEST for id={}", pipesClientId, t.getId());
        byte[] bytes = JsonPipesIpc.toBytes(t);
        PipesMessage.newRequest(bytes).write(connectionTuple.output);
    }

    private PipesResult waitForServer(FetchEmitTuple t, IntermediateResult intermediateResult) throws InterruptedException {
        long timeoutMillis = getTimeoutMillis(pipesConfig, t.getParseContext());
        Instant start = Instant.now();
        Instant lastUpdate = start;

        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("thread interrupt");
            }
            long totalElapsed = Duration.between(start, Instant.now()).toMillis();
            if (totalElapsed > timeoutMillis) {
                LOG.warn("clientId={}: timeout on client side: id={} elapsed={} timeoutMillis={}",
                        pipesClientId, t.getId(), totalElapsed, timeoutMillis);
                // Mark for restart - server is stuck on current request and needs to be restarted
                serverManager.markServerForRestart();
                closeConnection();
                return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.TIMEOUT,
                        intermediateResult.get());
            }
            try {
                PipesMessage msg = PipesMessage.read(connectionTuple.input);
                LOG.trace("clientId={}: received message type={} id={}", pipesClientId, msg.type(), t.getId());

                // Send ACK only for messages that require it
                if (msg.type().requiresAck()) {
                    PipesMessage.ack().write(connectionTuple.output);
                }

                switch (msg.type()) {
                    case OOM:
                        String oomMsg = JsonPipesIpc.fromBytes(msg.payload(), String.class);
                        serverManager.markServerForRestart();
                        closeConnection();
                        return buildFatalResult(t.getId(), t.getEmitKey(), PipesResult.RESULT_STATUS.OOM,
                                intermediateResult.get(), oomMsg);
                    case TIMEOUT:
                        String timeoutMsg = JsonPipesIpc.fromBytes(msg.payload(), String.class);
                        serverManager.markServerForRestart();
                        closeConnection();
                        return buildFatalResult(t.getId(), t.getEmitKey(), TIMEOUT,
                                intermediateResult.get(), timeoutMsg);
                    case UNSPECIFIED_CRASH:
                        String crashMsg = JsonPipesIpc.fromBytes(msg.payload(), String.class);
                        serverManager.markServerForRestart();
                        closeConnection();
                        return buildFatalResult(t.getId(), t.getEmitKey(), UNSPECIFIED_CRASH,
                                intermediateResult.get(), crashMsg);
                    case INTERMEDIATE_RESULT:
                        intermediateResult.set(JsonPipesIpc.fromBytes(msg.payload(), Metadata.class));
                        lastUpdate = Instant.now();
                        break;
                    case WORKING:
                        lastUpdate = Instant.now();
                        break;
                    case FINISHED:
                        PipesResult result = JsonPipesIpc.fromBytes(msg.payload(), PipesResult.class);
                        // Restore ParseContext from original FetchEmitTuple (not serialized back from server)
                        if (result.emitData() instanceof EmitDataImpl emitDataImpl) {
                            emitDataImpl.setParseContext(t.getParseContext());
                        }
                        return result;
                    default:
                        throw new IOException("Unexpected message type from server: " + msg.type());
                }
            } catch (SocketTimeoutException e) {
                LOG.warn("clientId={}: Socket timeout exception while waiting for server", pipesClientId, e);
                // Mark for restart - server is stuck on current request and needs to be restarted
                serverManager.markServerForRestart();
                closeConnection();
                return buildFatalResult(t.getId(), t.getEmitKey(), TIMEOUT, intermediateResult.get(),
                        ExceptionUtils.getStackTrace(e));
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn("clientId={} - crash while waiting for server", pipesClientId, e);
                // Handle crash and determine status based on exit code
                int exitCode = serverManager.handleCrashAndGetExitCode();
                PipesResult.RESULT_STATUS status = UNSPECIFIED_CRASH;
                if (exitCode == PipesMessageType.OOM.getExitCode().orElse(-1)) {
                    status = PipesResult.RESULT_STATUS.OOM;
                } else if (exitCode == PipesMessageType.TIMEOUT.getExitCode().orElse(-1)) {
                    status = PipesResult.RESULT_STATUS.TIMEOUT;
                }
                closeConnection();
                return buildFatalResult(t.getId(), t.getEmitKey(), status, intermediateResult.get(),
                        ExceptionUtils.getStackTrace(e));
            }
        }
    }

    private PipesResult buildFatalResult(String id, EmitKey emitKey, PipesResult.RESULT_STATUS status,
                                         Optional<Metadata> intermediateResultOpt) {
        return buildFatalResult(id, emitKey, status, intermediateResultOpt, null);
    }

    private PipesResult buildFatalResult(String id, EmitKey emitKey, PipesResult.RESULT_STATUS status,
                                         Optional<Metadata> intermediateResultOpt, String msg) {
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

    private void waitForStartup() throws IOException {
        PipesMessage msg = PipesMessage.read(connectionTuple.input);
        if (msg.type() == PipesMessageType.READY) {
            LOG.debug("clientId={}: server ready", pipesClientId);
        } else if (msg.type() == PipesMessageType.STARTUP_FAILED) {
            // Send ACK for startup failure
            PipesMessage.ack().write(connectionTuple.output);
            String errorMsg = new String(msg.payload(), StandardCharsets.UTF_8);
            LOG.error("clientId={}: Server failed to start: {}", pipesClientId, errorMsg);
            throw new ServerInitializationException(errorMsg);
        } else {
            LOG.error("clientId={}: Unexpected first message type: {}", pipesClientId, msg.type());
            throw new IOException("Unexpected first message type from server: " + msg.type());
        }
    }

    /**
     * Connection state: socket and streams for communicating with the server.
     * Unlike the old ServerTuple, this doesn't include the process or server socket
     * since those are now managed by ServerManager.
     */
    private record ConnectionTuple(Socket socket, DataInputStream input, DataOutputStream output) {
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
