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
import java.time.Duration;
import java.time.Instant;
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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesClient;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
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
    private final long heartbeatIntervalMs;

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
        this.heartbeatIntervalMs = pipesConfig.getHeartbeatIntervalMs();
        this.protocolIO = new ServerProtocolIO(input, output);
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
                PipesMessage msg = PipesMessage.read(input);
                LOG.trace("handlerId={}: received message type={}", handlerId, msg.type());

                switch (msg.type()) {
                    case PING:
                        PipesMessage.ping().write(output);
                        break;
                    case NEW_REQUEST:
                        intermediateResult.clear();
                        CountDownLatch countDownLatch = new CountDownLatch(1);

                        FetchEmitTuple fetchEmitTuple;
                        try {
                            fetchEmitTuple = JsonPipesIpc.fromBytes(msg.payload(), FetchEmitTuple.class);
                        } catch (IOException e) {
                            LOG.error("handlerId={}: problem deserializing FetchEmitTuple", handlerId, e);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, "unknown", e);
                            return; // connection is unsalvageable after deserialization failure
                        }
                        try {
                            ServerProtocolIO.validateFetchEmitTuple(fetchEmitTuple);
                            ParseContext mergedContext = resources.createMergedParseContext(fetchEmitTuple.getParseContext());
                            ParseContextUtils.resolveAll(mergedContext, getClass().getClassLoader());

                            PipesWorker pipesWorker = createPipesWorker(intermediateResult, fetchEmitTuple,
                                    mergedContext, countDownLatch);
                            executorCompletionService.submit(pipesWorker);

                            loopUntilDone(fetchEmitTuple, mergedContext, intermediateResult, countDownLatch);
                        } catch (TikaConfigException e) {
                            LOG.error("handlerId={}: config error processing request", handlerId, e);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), e);
                        } catch (Throwable t) {
                            LOG.error("handlerId={}: error processing request", handlerId, t);
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
                resources.getDefaultMetadataWriteLimiterFactory());
    }

    private void loopUntilDone(FetchEmitTuple fetchEmitTuple, ParseContext mergedContext,
                               ArrayBlockingQueue<Metadata> intermediateResult,
                               CountDownLatch countDownLatch) throws InterruptedException, IOException {
        Instant start = Instant.now();
        long timeoutMillis = PipesClient.getTimeoutMillis(pipesConfig, mergedContext);
        long progressCounter = 1;
        boolean wroteIntermediateResult = false;

        while (running) {
            // Check for intermediate result
            if (!wroteIntermediateResult) {
                Metadata intermediate = intermediateResult.poll(100, TimeUnit.MILLISECONDS);
                if (intermediate != null) {
                    protocolIO.writeIntermediate(intermediate);
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
                protocolIO.writeFinished(pipesResult);
                return;
            }

            // Send fire-and-forget heartbeat
            long elapsed = System.currentTimeMillis() - start.toEpochMilli();
            if (elapsed > progressCounter * heartbeatIntervalMs) {
                LOG.trace("handlerId={}: still processing, counter={}", handlerId, progressCounter);
                PipesMessage.working(progressCounter++).write(output);
            }

            // Check timeout
            if (Duration.between(start, Instant.now()).toMillis() > timeoutMillis) {
                handleTimeout(fetchEmitTuple.getId());
                return;
            }
        }
    }

    private void handleTimeout(String id) throws IOException {
        LOG.warn("handlerId={}: timeout processing id={}", handlerId, id);
        handleCrash(PipesMessageType.TIMEOUT, id,
                new RuntimeException("Server-side timeout processing " + id));
        // Timeout means a parsing thread is stuck - the JVM must be restarted
        LOG.error("handlerId={}: exiting server due to timeout", handlerId);
        System.exit(PipesMessageType.TIMEOUT.getExitCode().orElse(17));
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
