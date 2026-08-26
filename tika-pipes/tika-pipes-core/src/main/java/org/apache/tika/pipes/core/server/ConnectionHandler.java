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
package org.apache.tika.pipes.core.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.pipes.core.serialization.PipesRequest;
import org.apache.tika.serialization.ParseContextUtils;

/**
 * Handles a single client connection in shared server mode.
 * <p>
 * Each ConnectionHandler runs in its own thread and processes requests from
 * one PipesClient. It shares resources (parser, fetcher manager, etc.) with
 * other handlers but has its own socket, streams, and executor.
 * <p>
 * Unlike the per-client PipesServer, a ConnectionHandler does not call
 * System.exit() for most errors — it just closes the connection and
 * terminates its thread. However, OOM and TIMEOUT require a JVM restart,
 * so those still call System.exit(). For all other crashes the shared
 * server continues running for other clients.
 */
public class ConnectionHandler implements Runnable, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(ConnectionHandler.class);
    private static final AtomicInteger HANDLER_COUNTER = new AtomicInteger(0);

    private final int handlerId;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final SharedServerResources resources;
    private final PipesConfig pipesConfig;
    private final long heartbeatIntervalMillis;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ExecutorCompletionService<PipesResult> executorCompletionService =
            new ExecutorCompletionService<>(executorService);

    private final ServerProtocolIO protocolIO;
    private volatile boolean running = true;

    /**
     * Creates a new ConnectionHandler.
     *
     * @param socket the connected client socket
     * @param resources shared server resources (parser, managers, etc.)
     * @param pipesConfig the pipes configuration
     * @throws IOException if streams cannot be created
     */
    public ConnectionHandler(Socket socket, SharedServerResources resources, PipesConfig pipesConfig)
            throws IOException {
        this.handlerId = HANDLER_COUNTER.getAndIncrement();
        this.socket = socket;
        this.input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.resources = resources;
        this.pipesConfig = pipesConfig;
        this.heartbeatIntervalMillis = pipesConfig.getHeartbeatIntervalMillis();
        this.protocolIO = new ServerProtocolIO(input, output, pipesConfig.getMaxIpcPayloadBytes());
    }

    @Override
    public void run() {
        LOG.debug("handlerId={}: starting connection handler", handlerId);
        try {
            // Send READY signal (fire-and-forget, no ACK)
            PipesMessage.ready().write(output);
            LOG.debug("handlerId={}: sent READY, entering main loop", handlerId);

            mainLoop();
        } catch (Exception e) {
            if (running) {
                LOG.error("handlerId={}: error in connection handler", handlerId, e);
            } else {
                LOG.debug("handlerId={}: connection handler stopped", handlerId);
            }
        } finally {
            cleanup();
        }
    }

    private void mainLoop() {
        ArrayBlockingQueue<Metadata> intermediateResult = new ArrayBlockingQueue<>(1);

        while (running) {
            try {
                PipesMessage msg;
                try {
                    msg = PipesMessage.read(input, pipesConfig.getMaxIpcPayloadBytes());
                } catch (SocketTimeoutException e) {
                    // Socket timeout while idle is the normal inactivity shutdown path.
                    LOG.info("handlerId={}: socket timeout while waiting for task, closing connection",
                            handlerId);
                    return;
                }
                LOG.trace("handlerId={}: received message type={}", handlerId, msg.type());

                switch (msg.type()) {
                    case PING:
                        PipesMessage.ping().write(output);
                        break;
                    case NEW_REQUEST:
                        intermediateResult.clear();
                        CountDownLatch countDownLatch = new CountDownLatch(1);

                        PipesRequest pipesRequest;
                        FetchEmitTuple fetchEmitTuple;
                        try {
                            pipesRequest = JsonPipesIpc.fromBytes(msg.payload(), PipesRequest.class);
                            fetchEmitTuple = pipesRequest.getTuple();
                        } catch (IOException e) {
                            LOG.error("handlerId={}: problem deserializing PipesRequest", handlerId, e);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, "unknown", e);
                            return; // connection is unsalvageable after deserialization failure
                        }
                        ParseContext mergedContext = null;
                        try {
                            mergedContext = resources.createMergedParseContext(fetchEmitTuple.getParseContext());
                            ParseContextUtils.resolveAll(mergedContext, getClass().getClassLoader());
                            ServerProtocolIO.validateParseContext(mergedContext);
                            ServerProtocolIO.clampRequestTimeoutLimits(
                                    fetchEmitTuple.getParseContext(), mergedContext,
                                    pipesConfig.getMaxTotalTaskTimeoutMillis());
                            // After resolveAll: the payload is typed runtime state for
                            // BytesFetcher, never a resolvable config entry.
                            pipesRequest.applyTo(mergedContext);
                            // Installed here, before submit, so the worker thread's own
                            // ParseTimeout.getOrCreate(mergedContext) call (inside CompositeParser)
                            // sees this instance rather than racing to install its own.
                            ParseTimeout parseTimeout = ParseTimeout.getOrCreate(mergedContext);

                            PipesWorker pipesWorker = createPipesWorker(intermediateResult, fetchEmitTuple,
                                    mergedContext, countDownLatch);
                            executorCompletionService.submit(pipesWorker);

                            loopUntilDone(fetchEmitTuple, mergedContext, intermediateResult, countDownLatch, parseTimeout);
                        } catch (TikaConfigException e) {
                            LOG.error("handlerId={}: config error processing request", handlerId, e);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), e);
                        } catch (Throwable t) {
                            if (t instanceof Error) {
                                // OOM or other JVM-level error: don't trust the heap; exit
                                // immediately. Everything before the exit is best-effort and
                                // inside the try -- a secondary OOM in logging or writeCrash
                                // must not escape and leave this shared JVM alive post-Error.
                                try {
                                    LOG.error("handlerId={}: fatal JVM error; exiting", handlerId, t);
                                    protocolIO.writeCrash(PipesMessageType.OOM, t);
                                } catch (Throwable ignored) {
                                    //swallow
                                } finally {
                                    System.exit(PipesMessageType.OOM.getExitCode().orElse(18));
                                }
                            }
                            // respond, or the client blocks until socket timeout and
                            // restarts a healthy server
                            LOG.error("handlerId={}: error processing request", handlerId, t);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), t);
                        } finally {
                            if (mergedContext != null) {
                                MetadataFilter requestFilter = mergedContext.get(MetadataFilter.class);
                                if (requestFilter != null) {
                                    try {
                                        requestFilter.close();
                                    } catch (IOException e) {
                                        LOG.warn("handlerId={}: failed to close per-request MetadataFilter", handlerId, e);
                                    }
                                }
                            }
                        }
                        break;
                    case SHUT_DOWN:
                        LOG.info("handlerId={}: received SHUT_DOWN, closing connection", handlerId);
                        return;
                    default:
                        String errorMsg = String.format(Locale.ROOT,
                                "handlerId=%d: Unexpected message type %s in command position",
                                handlerId, msg.type());
                        LOG.error(errorMsg);
                        throw new IllegalStateException(errorMsg);
                }
            } catch (java.io.EOFException e) {
                // Client disconnected (stream closed)
                LOG.debug("handlerId={}: client disconnected (EOF)", handlerId);
                return;
            } catch (SocketException e) {
                // Client disconnected (socket closed)
                LOG.debug("handlerId={}: client disconnected", handlerId);
                return;
            } catch (IOException e) {
                LOG.error("handlerId={}: I/O error in main loop", handlerId, e);
                return;
            }
        }
    }

    private PipesWorker createPipesWorker(ArrayBlockingQueue<Metadata> intermediateResult,
                                          FetchEmitTuple fetchEmitTuple, ParseContext mergedContext,
                                          CountDownLatch countDownLatch) {
        FetchHandler fetchHandler = new FetchHandler(resources.getFetcherManager());
        ParseHandler parseHandler = new ParseHandler(resources.getDetector(), intermediateResult,
                countDownLatch, resources.getAutoDetectParser(), resources.getRMetaParser(),
                resources.getDefaultContentHandlerFactory(), pipesConfig.getParseMode());
        Long thresholdBytes = pipesConfig.getEmitStrategy().getThresholdBytes();
        long threshold = (thresholdBytes != null) ? thresholdBytes : EmitStrategyConfig.DEFAULT_DIRECT_EMIT_THRESHOLD_BYTES;
        EmitHandler emitHandler = new EmitHandler(resources.getDefaultMetadataFilter(),
                resources.getEmitStrategy(), resources.getEmitterManager(), threshold);
        return new PipesWorker(fetchEmitTuple, mergedContext, resources.getAutoDetectParser(),
                resources.getEmitterManager(), fetchHandler, parseHandler, emitHandler,
                resources.getDefaultMetadataWriteLimiterFactory(), pipesConfig.getParseMode());
    }

    private void loopUntilDone(FetchEmitTuple fetchEmitTuple, ParseContext mergedContext,
                               ArrayBlockingQueue<Metadata> intermediateResult,
                               CountDownLatch countDownLatch,
                               ParseTimeout parseTimeout) throws InterruptedException, IOException {
        // nanoTime: watchdog deadlines and heartbeat pacing must be immune to wall-clock steps
        long startNanos = System.nanoTime();
        TimeoutLimits limits = TimeoutLimits.get(mergedContext);
        long progressTimeoutMillis = limits.getProgressTimeoutMillis();
        long totalTaskTimeoutMillis = limits.getTotalTaskTimeoutMillis();
        long heartbeatCounter = 1;
        boolean wroteIntermediateResult = false;
        // If the client disconnects mid-parse we stop writing to the dead socket but keep
        // polling and enforcing the timeouts below, so an abandoned worker that will not stop
        // still trips checkTotalTimeout/checkProgressTimeout -> System.exit -> the shared JVM
        // recycles it. Otherwise (per-JVM shared mode has no per-request process to kill) a
        // runaway parse would spin forever with its heap pinned.
        boolean clientGone = false;

        while (running) {
            // Check for intermediate result
            if (!wroteIntermediateResult) {
                Metadata intermediate = intermediateResult.poll(100, TimeUnit.MILLISECONDS);
                if (intermediate != null) {
                    if (!clientGone) {
                        try {
                            protocolIO.writeIntermediate(intermediate);
                        } catch (IOException e) {
                            clientGone = true;
                            LOG.debug("handlerId={}: client gone (writing intermediate); keeping the "
                                    + "worker under its timeout so a runaway parse is reclaimed", handlerId);
                        }
                    }
                    countDownLatch.countDown();
                    wroteIntermediateResult = true;
                }
            }

            // Check for task completion
            Future<PipesResult> future = executorCompletionService.poll(100, TimeUnit.MILLISECONDS);
            if (future != null) {
                PipesResult pipesResult = null;
                try {
                    pipesResult = future.get();
                } catch (OutOfMemoryError e) {
                    handleCrash(PipesMessageType.OOM, fetchEmitTuple.getId(), e);
                    LOG.error("handlerId={}: exiting server due to OOM", handlerId);
                    System.exit(PipesMessageType.OOM.getExitCode().orElse(18));
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    LOG.error("handlerId={}: crash processing {}", handlerId, fetchEmitTuple.getId(), t);
                    if (t instanceof OutOfMemoryError) {
                        handleCrash(PipesMessageType.OOM, fetchEmitTuple.getId(), t);
                        LOG.error("handlerId={}: exiting server due to OOM", handlerId);
                        System.exit(PipesMessageType.OOM.getExitCode().orElse(18));
                    }
                    handleCrash(PipesMessageType.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), t);
                    return;
                }
                LOG.debug("handlerId={}: finished task id={} status={}", handlerId,
                        fetchEmitTuple.getId(), pipesResult.status());
                if (!clientGone) {
                    try {
                        protocolIO.writeFinished(pipesResult);
                    } catch (IOException e) {
                        LOG.debug("handlerId={}: client gone before final result could be sent", handlerId);
                    }
                }
                return;
            }

            // Send fire-and-forget heartbeat
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
            if (!clientGone && elapsed > heartbeatCounter * heartbeatIntervalMillis) {
                LOG.trace("handlerId={}: still processing, counter={}", handlerId, heartbeatCounter);
                try {
                    PipesMessage.working().write(output);
                } catch (IOException e) {
                    clientGone = true;
                    LOG.debug("handlerId={}: client gone (heartbeat); keeping the worker under its "
                            + "timeout so a runaway parse is reclaimed", handlerId);
                }
                heartbeatCounter++;
            }

            // Check timeouts
            if (checkTotalTimeout(startNanos, totalTaskTimeoutMillis, progressTimeoutMillis, fetchEmitTuple.getId())) {
                return;
            }
            if (checkProgressTimeout(parseTimeout, progressTimeoutMillis, fetchEmitTuple.getId())) {
                return;
            }
        }
    }

    /**
     * Fires at {@code totalTaskTimeoutMillis + progressTimeoutMillis}, not at the deadline
     * itself: the cooperative deadline path (skip remaining embedded docs, emit a
     * PARTIAL_TIMEOUT result) only starts once ParseTimeout's identically-anchored deadline
     * is reached, so killing the JVM at that instant would leave the wind-down no time to
     * run. The grace window stays bounded: {@link #checkProgressTimeout} still fires
     * independently, so a wind-down that hangs is caught via the stall path instead.
     */
    private boolean checkTotalTimeout(long startNanos, long totalTaskTimeoutMillis, long progressTimeoutMillis, String id) {
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
        long graceDeadline = (totalTaskTimeoutMillis >= Long.MAX_VALUE - progressTimeoutMillis)
                ? Long.MAX_VALUE : totalTaskTimeoutMillis + progressTimeoutMillis;
        if (elapsed > graceDeadline) {
            handleCrash(PipesMessageType.TIMEOUT, id,
                    new RuntimeException("Server-side total task timeout after " + elapsed + "ms (limit: " + totalTaskTimeoutMillis + "ms)"));
            // Timeout means a parsing thread is stuck - the JVM must be restarted
            LOG.error("handlerId={}: exiting server due to total task timeout", handlerId);
            System.exit(PipesMessageType.TIMEOUT.getExitCode().orElse(17));
            return true;
        }
        return false;
    }

    private boolean checkProgressTimeout(ParseTimeout parseTimeout, long progressTimeoutMillis, String id) {
        long timeSinceProgress = parseTimeout.millisSinceLastProgress();
        if (timeSinceProgress > progressTimeoutMillis) {
            handleCrash(PipesMessageType.TIMEOUT, id,
                    new RuntimeException("Server-side progress timeout: no progress for " + timeSinceProgress + "ms (limit: " + progressTimeoutMillis + "ms)"));
            // Timeout means a parsing thread is stuck - the JVM must be restarted
            LOG.error("handlerId={}: exiting server due to progress timeout", handlerId);
            System.exit(PipesMessageType.TIMEOUT.getExitCode().orElse(17));
            return true;
        }
        return false;
    }

    private void handleCrash(PipesMessageType crashType, String id, Throwable t) {
        LOG.error("handlerId={}: {} processing id={}", handlerId, crashType, id, t);
        try {
            protocolIO.writeCrash(crashType, t);
        } catch (IOException e) {
            LOG.warn("handlerId={}: problem writing crash info to client", handlerId, e);
        }
        // Note: For OOM/timeout, caller is responsible for calling System.exit()
        // For other crashes (UNSPECIFIED_CRASH), we just close this connection
    }

    @Override
    public void close() {
        running = false;
        cleanup();
    }

    private void cleanup() {
        executorService.shutdownNow();
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("handlerId={}: error closing socket", handlerId, e);
        }
        LOG.debug("handlerId={}: connection handler closed", handlerId);
    }
}
