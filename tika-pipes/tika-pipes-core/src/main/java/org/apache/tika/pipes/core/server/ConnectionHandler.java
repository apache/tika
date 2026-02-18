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

import static org.apache.tika.pipes.core.PipesClient.COMMANDS.ACK;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.FINISHED;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.INTERMEDIATE_RESULT;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.OOM;
import static org.apache.tika.pipes.core.server.PipesServer.PROCESSING_STATUS.TIMEOUT;

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
import java.util.HexFormat;
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
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

/**
 * Handles a single client connection in shared server mode.
 * <p>
 * Each ConnectionHandler runs in its own thread and processes requests from
 * one PipesClient. It shares resources (parser, fetcher manager, etc.) with
 * other handlers but has its own socket, streams, and executor.
 * <p>
 * Unlike the per-client PipesServer, a ConnectionHandler does NOT call
 * System.exit() on errors - it just closes the connection and terminates
 * its thread. The shared server continues running for other clients.
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
    }

    @Override
    public void run() {
        LOG.debug("handlerId={}: starting connection handler", handlerId);
        try {
            // Send READY signal
            write(PipesServer.PROCESSING_STATUS.READY.getByte());
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
                int request = input.read();
                LOG.trace("handlerId={}: received command byte={}", handlerId,
                        HexFormat.of().formatHex(new byte[]{(byte) request}));

                if (request == -1) {
                    LOG.debug("handlerId={}: received -1 from client; closing connection", handlerId);
                    return;
                }

                // Validate command byte
                if (request == PipesClient.COMMANDS.ACK.getByte()) {
                    String msg = String.format(Locale.ROOT,
                            "handlerId=%d: PROTOCOL ERROR - Received ACK when expecting command", handlerId);
                    LOG.error(msg);
                    throw new IllegalStateException(msg);
                }

                if (request == PipesClient.COMMANDS.PING.getByte()) {
                    writeNoAck(PipesClient.COMMANDS.PING.getByte());
                } else if (request == PipesClient.COMMANDS.NEW_REQUEST.getByte()) {
                    intermediateResult.clear();
                    CountDownLatch countDownLatch = new CountDownLatch(1);

                    FetchEmitTuple fetchEmitTuple = readFetchEmitTuple();
                    try {
                        validateFetchEmitTuple(fetchEmitTuple);
                        ParseContext mergedContext = resources.createMergedParseContext(fetchEmitTuple.getParseContext());
                        ParseContextUtils.resolveAll(mergedContext, getClass().getClassLoader());

                        PipesWorker pipesWorker = createPipesWorker(intermediateResult, fetchEmitTuple,
                                mergedContext, countDownLatch);
                        executorCompletionService.submit(pipesWorker);

                        loopUntilDone(fetchEmitTuple, mergedContext, intermediateResult, countDownLatch);
                    } catch (TikaConfigException e) {
                        LOG.error("handlerId={}: config error processing request", handlerId, e);
                        handleCrash(PipesServer.PROCESSING_STATUS.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), e);
                    } catch (Throwable t) {
                        LOG.error("handlerId={}: error processing request", handlerId, t);
                    }
                } else if (request == PipesClient.COMMANDS.SHUT_DOWN.getByte()) {
                    LOG.info("handlerId={}: received SHUT_DOWN, closing connection", handlerId);
                    return;
                } else {
                    String msg = String.format(Locale.ROOT,
                            "handlerId=%d: Unexpected byte 0x%02x in command position", handlerId, (byte) request);
                    LOG.error(msg);
                    throw new IllegalStateException(msg);
                }
                output.flush();
            } catch (SocketException e) {
                // Client disconnected
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
        long mockProgressCounter = 0;
        boolean wroteIntermediateResult = false;

        while (running) {
            // Check for intermediate result
            if (!wroteIntermediateResult) {
                Metadata intermediate = intermediateResult.poll(100, TimeUnit.MILLISECONDS);
                if (intermediate != null) {
                    writeIntermediate(intermediate);
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
                    handleCrash(OOM, fetchEmitTuple.getId(), e);
                    LOG.error("handlerId={}: exiting server due to OOM", handlerId);
                    System.exit(PipesServer.OOM_EXIT_CODE);
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    LOG.error("handlerId={}: crash processing {}", handlerId, fetchEmitTuple.getId(), t);
                    if (t instanceof OutOfMemoryError) {
                        handleCrash(OOM, fetchEmitTuple.getId(), t);
                        LOG.error("handlerId={}: exiting server due to OOM", handlerId);
                        System.exit(PipesServer.OOM_EXIT_CODE);
                    }
                    handleCrash(PipesServer.PROCESSING_STATUS.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), t);
                    return;
                }
                LOG.debug("handlerId={}: finished task id={} status={}", handlerId,
                        fetchEmitTuple.getId(), pipesResult.status());
                write(FINISHED, pipesResult);
                return;
            }

            // Send heartbeat
            long elapsed = System.currentTimeMillis() - start.toEpochMilli();
            if (elapsed > mockProgressCounter * heartbeatIntervalMs) {
                LOG.trace("handlerId={}: still processing, counter={}", handlerId, mockProgressCounter);
                write(PipesServer.PROCESSING_STATUS.WORKING.getByte());
                output.writeLong(mockProgressCounter++);
                output.flush();
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
        write(TIMEOUT.getByte());
        // Timeout means a parsing thread is stuck - the JVM must be restarted
        LOG.error("handlerId={}: exiting server due to timeout", handlerId);
        System.exit(PipesServer.TIMEOUT_EXIT_CODE);
    }

    private void handleCrash(PipesServer.PROCESSING_STATUS processingStatus, String id, Throwable t) {
        LOG.error("handlerId={}: {} processing id={}", handlerId, processingStatus, id, t);
        String msg = (t != null) ? ExceptionUtils.getStackTrace(t) : "";
        try {
            byte[] bytes = JsonPipesIpc.toBytes(msg);
            write(processingStatus, bytes);
            // Note: write() already awaits ACKs internally, don't call awaitAck() again
        } catch (IOException e) {
            LOG.warn("handlerId={}: problem writing crash info to client", handlerId, e);
        }
        // Note: For OOM/timeout, caller is responsible for calling System.exit()
        // For other crashes (UNSPECIFIED_CRASH), we just close this connection
    }

    private FetchEmitTuple readFetchEmitTuple() throws IOException {
        int length = input.readInt();
        if (length < 0 || length > PipesServer.MAX_FETCH_EMIT_TUPLE_BYTES) {
            throw new IOException("FetchEmitTuple length " + length +
                    " exceeds maximum allowed size of " + PipesServer.MAX_FETCH_EMIT_TUPLE_BYTES + " bytes");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return JsonPipesIpc.fromBytes(bytes, FetchEmitTuple.class);
    }

    private void validateFetchEmitTuple(FetchEmitTuple fetchEmitTuple) throws TikaConfigException {
        ParseContext requestContext = fetchEmitTuple.getParseContext();
        if (requestContext == null) {
            return;
        }
        org.apache.tika.pipes.core.extractor.UnpackConfig unpackConfig =
                requestContext.get(org.apache.tika.pipes.core.extractor.UnpackConfig.class);
        org.apache.tika.pipes.api.ParseMode parseMode =
                requestContext.get(org.apache.tika.pipes.api.ParseMode.class);

        if (unpackConfig != null && !StringUtils.isBlank(unpackConfig.getEmitter())
                && parseMode != org.apache.tika.pipes.api.ParseMode.UNPACK) {
            throw new TikaConfigException(
                    "FetchEmitTuple has UnpackConfig with emitter '" + unpackConfig.getEmitter() +
                            "' but ParseMode is " + parseMode + ". " +
                            "To extract embedded bytes, set ParseMode.UNPACK in the ParseContext.");
        }
    }

    private void write(PipesServer.PROCESSING_STATUS processingStatus, PipesResult pipesResult) {
        try {
            byte[] bytes = JsonPipesIpc.toBytes(pipesResult);
            write(processingStatus, bytes);
        } catch (IOException e) {
            LOG.error("handlerId={}: problem writing emit data", handlerId, e);
        }
    }

    private void writeIntermediate(Metadata metadata) {
        try {
            byte[] bytes = JsonPipesIpc.toBytes(metadata);
            write(INTERMEDIATE_RESULT, bytes);
        } catch (IOException e) {
            LOG.error("handlerId={}: problem writing intermediate data", handlerId, e);
        }
    }

    private void awaitAck() throws IOException {
        int b = input.read();
        if (b == ACK.getByte()) {
            return;
        }
        LOG.error("handlerId={}: expected ACK but got byte={}", handlerId,
                HexFormat.of().formatHex(new byte[]{(byte) b}));
        throw new IOException("Expected ACK but got byte=" + HexFormat.of().formatHex(new byte[]{(byte) b}));
    }

    private void writeNoAck(byte b) {
        try {
            output.write(b);
            output.flush();
        } catch (IOException e) {
            LOG.error("handlerId={}: problem writing data", handlerId, e);
        }
    }

    private void write(byte b) {
        try {
            output.write(b);
            output.flush();
            awaitAck();
        } catch (IOException e) {
            LOG.error("handlerId={}: problem writing data", handlerId, e);
        }
    }

    private void write(PipesServer.PROCESSING_STATUS status, byte[] bytes) {
        try {
            write(status.getByte());
            int len = bytes.length;
            output.writeInt(len);
            output.write(bytes);
            output.flush();
            awaitAck();
        } catch (IOException e) {
            LOG.error("handlerId={}: problem writing data", handlerId, e);
        }
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
