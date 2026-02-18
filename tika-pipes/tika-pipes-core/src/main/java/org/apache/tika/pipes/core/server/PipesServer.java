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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.writefilter.MetadataWriteLimiterFactory;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesClient;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.config.ConfigStore;
import org.apache.tika.pipes.core.config.ConfigStoreFactory;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.extractor.UnpackExtractorFactory;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.pipes.core.protocol.PipesMessage;
import org.apache.tika.pipes.core.protocol.PipesMessageType;
import org.apache.tika.pipes.core.protocol.ShutDownReceivedException;
import org.apache.tika.pipes.core.serialization.JsonPipesIpc;
import org.apache.tika.plugins.ExtensionConfig;
import org.apache.tika.plugins.TikaPluginManager;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.ParseContextUtils;
import org.apache.tika.utils.ExceptionUtils;

/**
 * This server is forked from the PipesClient.  This class isolates
 * parsing from the client to protect the primary JVM.
 * <p>
 * When configuring logging for this class, make absolutely certain
 * not to write to STDOUT.  This class uses STDOUT to communicate with
 * the PipesClient.
 */
public class PipesServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesServer.class);

    public static final int AUTH_TOKEN_LENGTH_BYTES = 32;

    private final long heartbeatIntervalMs;
    private final String pipesClientId;

    private Detector detector;

    private final DataInputStream input;
    private final DataOutputStream output;

    private final TikaLoader tikaLoader;
    private final PipesConfig pipesConfig;
    private final Socket socket;
    private final MetadataFilter defaultMetadataFilter;
    private final ContentHandlerFactory defaultContentHandlerFactory;
    private final MetadataWriteLimiterFactory defaultMetadataWriteLimiterFactory;
    private AutoDetectParser autoDetectParser;
    private RecursiveParserWrapper rMetaParser;
    private FetcherManager fetcherManager;
    private EmitterManager emitterManager;
    private ConfigStore configStore;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ExecutorCompletionService<PipesResult> executorCompletionService = new ExecutorCompletionService<>(executorService);
    private final EmitStrategy emitStrategy;
    private final ServerProtocolIO protocolIO;

    public static PipesServer load(int port, Path tikaConfigPath) throws Exception {
            String pipesClientId = System.getProperty("pipesClientId", "unknown");
            LOG.debug("pipesClientId={}: connecting to client on port={}", pipesClientId, port);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), PipesClient.SOCKET_CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm to avoid ~40ms delays on small writes

            DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            TikaLoader tikaLoader = TikaLoader.load(tikaConfigPath);
            TikaJsonConfig tikaJsonConfig = tikaLoader.getConfig();
            PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

            // Set socket timeout from config after loading PipesConfig
            socket.setSoTimeout((int) pipesConfig.getSocketTimeoutMs());
            socket.setTcpNoDelay(true);

            MetadataFilter metadataFilter = tikaLoader.loadMetadataFilters();
            ContentHandlerFactory contentHandlerFactory = tikaLoader.loadContentHandlerFactory();
            MetadataWriteLimiterFactory metadataWriteLimiterFactory = tikaLoader.loadParseContext().get(MetadataWriteLimiterFactory.class);
            PipesServer pipesServer = new PipesServer(pipesClientId, tikaLoader, pipesConfig, socket, dis, dos, metadataFilter, contentHandlerFactory, metadataWriteLimiterFactory);
            pipesServer.initializeResources();
            LOG.debug("pipesClientId={}: PipesServer loaded and ready", pipesClientId);
            return pipesServer;
        } catch (Exception e) {
            LOG.error("Failed to start up", e);
            try {
                String msg = ExceptionUtils.getStackTrace(e);
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                PipesMessage.startupFailed(bytes).write(dos);
                PipesMessage ackMsg = PipesMessage.read(dis);
                if (ackMsg.type() != PipesMessageType.ACK) {
                    LOG.warn("Expected ACK but got: {}", ackMsg.type());
                }
            } catch (IOException ioException) {
                LOG.error("Failed to send startup failure message to client", ioException);
            }
            throw e;
        }
    }

    public PipesServer(String pipesClientId, TikaLoader tikaLoader, PipesConfig pipesConfig, Socket socket, DataInputStream in,
                       DataOutputStream out, MetadataFilter metadataFilter, ContentHandlerFactory contentHandlerFactory,
                       MetadataWriteLimiterFactory metadataWriteLimiterFactory) throws TikaConfigException,
            IOException {

        this.pipesClientId = pipesClientId;
        this.tikaLoader = tikaLoader;
        this.pipesConfig = pipesConfig;
        this.socket = socket;
        this.defaultMetadataFilter = metadataFilter;
        this.defaultContentHandlerFactory = contentHandlerFactory;
        this.defaultMetadataWriteLimiterFactory = metadataWriteLimiterFactory;
        this.input = new DataInputStream(in);
        this.output = new DataOutputStream(out);
        this.heartbeatIntervalMs = pipesConfig.getHeartbeatIntervalMs();

        // Validate heartbeat interval is less than socket timeout
        if (heartbeatIntervalMs >= pipesConfig.getSocketTimeoutMs()) {
            String msg = String.format(Locale.ROOT, "Heartbeat interval (%dms) must be less than socket timeout (%dms). " +
                    "This configuration will cause socket timeouts during normal processing.",
                    heartbeatIntervalMs, pipesConfig.getSocketTimeoutMs());

            // Allow override for testing only
            if (!"true".equals(System.getProperty("tika.pipes.allowInvalidHeartbeat"))) {
                throw new TikaConfigException(msg);
            }
            LOG.error(msg + " Proceeding because tika.pipes.allowInvalidHeartbeat=true");
        }

        emitStrategy = pipesConfig.getEmitStrategy().getType();
        this.protocolIO = new ServerProtocolIO(input, output);
    }


    public static void main(String[] args) throws Exception {
        // Check for shared mode: --shared <numConnections> <tikaConfigPath>
        if (args.length > 0 && "--shared".equals(args[0])) {
            String portEnv = System.getenv("TIKA_PIPES_PORT");
            if (portEnv == null || portEnv.isEmpty()) {
                throw new IllegalStateException("TIKA_PIPES_PORT environment variable is not set");
            }
            int port = Integer.parseInt(portEnv);
            String tokenHex = System.getenv("TIKA_PIPES_AUTH_TOKEN");
            if (tokenHex == null || tokenHex.isEmpty()) {
                throw new IllegalStateException("TIKA_PIPES_AUTH_TOKEN environment variable is not set");
            }
            byte[] expectedToken = HexFormat.of().parseHex(tokenHex);
            int numConnections = Integer.parseInt(args[1]);
            Path tikaConfig = Paths.get(args[2]);
            LOG.info("Starting shared PipesServer with {} connections", numConnections);
            runSharedMode(port, numConnections, tikaConfig, expectedToken);
        } else {
            // Per-client mode: <port> <tikaConfigPath>
            int port = Integer.parseInt(args[0]);
            Path tikaConfig = Paths.get(args[1]);
            String pipesClientId = System.getProperty("pipesClientId", "unknown");
            LOG.debug("pipesClientId={}: starting pipes server on port={}", pipesClientId, port);
            try (PipesServer server = PipesServer.load(port, tikaConfig)) {
                server.mainLoop();
            } catch (Throwable t) {
                LOG.error("pipesClientId={}: crashed", pipesClientId, t);
                throw t;
            } finally {
                LOG.debug("pipesClientId={}: server shutting down", pipesClientId);
            }
        }
    }

    /**
     * Runs the server in shared mode, accepting multiple client connections.
     * <p>
     * Each incoming connection must present a valid auth token (32 bytes) before
     * being accepted. This prevents unauthorized local processes from connecting.
     * Note: if a malicious actor has access to your localhost and can read
     * /proc/&lt;pid&gt;/environ, that is beyond Tika's security model. This auth
     * token exists to prevent CVE-style abuse from untrusted local processes that
     * cannot read the server process's environment.
     */
    private static void runSharedMode(int port, int numConnections, Path tikaConfigPath,
                                      byte[] expectedToken) throws Exception {
        TikaLoader tikaLoader = TikaLoader.load(tikaConfigPath);
        PipesConfig pipesConfig = PipesConfig.load(tikaLoader.getConfig());

        // Load shared resources
        SharedServerResources resources = SharedServerResources.load(tikaLoader, pipesConfig);

        // Create thread pool for connection handlers
        ExecutorService connectionPool = Executors.newFixedThreadPool(numConnections);

        // Create server socket and accept connections
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), numConnections);

            // Signal readiness to the parent process via stdout
            System.out.println("READY:" + port);
            System.out.flush();

            LOG.info("Shared server ready, accepting connections");

            // Accept connections until shutdown
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    java.net.Socket clientSocket = serverSocket.accept();
                    clientSocket.setSoTimeout((int) pipesConfig.getSocketTimeoutMs());
                    clientSocket.setTcpNoDelay(true);

                    // Validate auth token before creating handler
                    byte[] clientToken = new byte[AUTH_TOKEN_LENGTH_BYTES];
                    int bytesRead = 0;
                    while (bytesRead < AUTH_TOKEN_LENGTH_BYTES) {
                        int r = clientSocket.getInputStream().read(
                                clientToken, bytesRead, AUTH_TOKEN_LENGTH_BYTES - bytesRead);
                        if (r == -1) {
                            break;
                        }
                        bytesRead += r;
                    }
                    if (bytesRead < AUTH_TOKEN_LENGTH_BYTES ||
                            !MessageDigest.isEqual(expectedToken, clientToken)) {
                        LOG.warn("Rejected connection with invalid auth token");
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {
                        }
                        continue;
                    }

                    LOG.debug("Accepted authenticated connection from client");

                    ConnectionHandler handler = new ConnectionHandler(clientSocket, resources, pipesConfig);
                    connectionPool.submit(handler);
                } catch (java.net.SocketException e) {
                    // Server socket closed, shutdown
                    LOG.debug("Server socket closed, shutting down");
                    break;
                }
            }
        } finally {
            connectionPool.shutdownNow();
            connectionPool.awaitTermination(10, TimeUnit.SECONDS);
            LOG.debug("Shared server shutdown complete");
        }
    }

    public void mainLoop() {
        try {
            PipesMessage.ready().write(output);
        } catch (IOException e) {
            LOG.error("pipesClientId={}: failed to send READY", pipesClientId, e);
            exit(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().orElse(19));
            return;
        }
        LOG.debug("pipesClientId={}: sent READY, entering main loop", pipesClientId);
        ArrayBlockingQueue<Metadata> intermediateResult = new ArrayBlockingQueue<>(1);

        //main loop
        try {
            while (true) {
                PipesMessage msg = PipesMessage.read(input);
                LOG.trace("pipesClientId={}: received message type={}", pipesClientId, msg.type());

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
                            LOG.error("problem deserializing FetchEmitTuple", e);
                            handleCrash(PipesMessageType.UNSPECIFIED_CRASH, "unknown", e);
                            break; // unreachable after handleCrash/exit, but needed for compilation
                        }
                        // Validate before merging with global config
                        ServerProtocolIO.validateFetchEmitTuple(fetchEmitTuple);
                        // Create merged ParseContext: defaults from tika-config + request overrides
                        ParseContext mergedContext = createMergedParseContext(fetchEmitTuple.getParseContext());
                        // Resolve friendly-named configs in ParseContext to actual objects
                        ParseContextUtils.resolveAll(mergedContext, getClass().getClassLoader());

                        PipesWorker pipesWorker = getPipesWorker(intermediateResult, fetchEmitTuple, mergedContext, countDownLatch);
                        executorCompletionService.submit(pipesWorker);
                        try {
                            loopUntilDone(fetchEmitTuple, mergedContext, executorCompletionService, intermediateResult, countDownLatch);
                        } catch (Throwable t) {
                            LOG.error("Serious problem processing request", t);
                        }
                        break;
                    case SHUT_DOWN:
                        LOG.debug("shutting down");
                        try {
                            close();
                        } catch (Exception e) {
                            //swallow
                        }
                        System.exit(0);
                        break;
                    default:
                        String errorMsg = String.format(Locale.ROOT,
                                "pipesClientId=%s: Unexpected message type %s in command position",
                                pipesClientId, msg.type());
                        LOG.error(errorMsg);
                        throw new IllegalStateException(errorMsg);
                }
            }
        } catch (Throwable t) {
            LOG.error("main loop error (did the forking process shut down?)", t);
            exit(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().orElse(19));
        }
    }

    private PipesWorker getPipesWorker(ArrayBlockingQueue<Metadata> intermediateResult, FetchEmitTuple fetchEmitTuple,
                                        ParseContext mergedContext, CountDownLatch countDownLatch) {
        FetchHandler fetchHandler = new FetchHandler(fetcherManager);
        ParseHandler parseHandler = new ParseHandler(detector, intermediateResult, countDownLatch, autoDetectParser,
                rMetaParser, defaultContentHandlerFactory, pipesConfig.getParseMode());
        Long thresholdBytes = pipesConfig.getEmitStrategy().getThresholdBytes();
        long threshold = (thresholdBytes != null) ? thresholdBytes : EmitStrategyConfig.DEFAULT_DIRECT_EMIT_THRESHOLD_BYTES;
        EmitHandler emitHandler = new EmitHandler(defaultMetadataFilter, emitStrategy, emitterManager, threshold);
        return new PipesWorker(fetchEmitTuple, mergedContext, autoDetectParser, emitterManager,
                fetchHandler, parseHandler, emitHandler, defaultMetadataWriteLimiterFactory);
    }

    private void loopUntilDone(FetchEmitTuple fetchEmitTuple, ParseContext mergedContext,
                               ExecutorCompletionService<PipesResult> executorCompletionService,
                               ArrayBlockingQueue<Metadata> intermediateResult, CountDownLatch countDownLatch) throws InterruptedException, IOException {
        Instant start = Instant.now();
        long timeoutMillis = PipesClient.getTimeoutMillis(pipesConfig, mergedContext);
        long progressCounter = 1;
        boolean wroteIntermediateResult = false;

        while (true) {
            // Check for intermediate result (pre-parse metadata)
            if (!wroteIntermediateResult) {
                Metadata intermediate = intermediateResult.poll(100, TimeUnit.MILLISECONDS);
                if (intermediate != null) {
                    writeIntermediate(intermediate);
                    countDownLatch.countDown();
                    wroteIntermediateResult = true;
                }
            }

            // Check for task completion (can happen even without intermediate result if crash occurs early)
            Future<PipesResult> future = executorCompletionService.poll(100, TimeUnit.MILLISECONDS);
            if (future != null) {
                PipesResult pipesResult = null;
                try {
                    pipesResult = future.get();
                } catch (OutOfMemoryError e) {
                    handleCrash(PipesMessageType.OOM, fetchEmitTuple.getId(), e);
                    return; // handleCrash calls exit(), but guard against unexpected return
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    LOG.error("crash: {}", fetchEmitTuple.getId(), t);
                    if (t instanceof OutOfMemoryError) {
                        handleCrash(PipesMessageType.OOM, fetchEmitTuple.getId(), t);
                        return;
                    }
                    handleCrash(PipesMessageType.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), t);
                    return;
                }
                LOG.debug("executor completionService finished task: id={} status={}", fetchEmitTuple.getId(), pipesResult.status());
                writeFinished(pipesResult);
                return;
            }

            // Send fire-and-forget heartbeat if we've waited long enough
            long elapsed = System.currentTimeMillis() - start.toEpochMilli();
            if (elapsed > progressCounter * heartbeatIntervalMs) {
                LOG.debug("still processing: {}", progressCounter);
                PipesMessage.working(progressCounter++).write(output);
            }

            if (checkTimeout(start, timeoutMillis, fetchEmitTuple.getId())) {
                return; // handleCrash calls exit(), but guard against unexpected return
            }
        }

    }

    private boolean checkTimeout(Instant start, long timeoutMillis, String id) {
        if (Duration.between(start, Instant.now()).toMillis() > timeoutMillis) {
            handleCrash(PipesMessageType.TIMEOUT, id,
                    new RuntimeException("Server-side timeout after " + timeoutMillis + "ms"));
            return true;
        }
        return false;
    }

    private void handleCrash(PipesMessageType crashType, String id, Throwable t) {
        LOG.error("{}: {}", crashType, id, t);
        try {
            protocolIO.writeCrash(crashType, t);
        } catch (IOException e) {
            LOG.warn("problem writing crash info to client", e);
        }
        exit(crashType.getExitCode().orElse(19));
    }


    @Override
    public void close() throws Exception {
        executorService.shutdownNow();
        socket.close();
    }

    private void exit(int exitCode) {
        if (exitCode != 0) {
            LOG.error("exiting: {}", exitCode);
        } else {
            LOG.debug("exiting: {}", exitCode);
        }
        System.exit(exitCode);
    }

    protected void initializeResources() throws TikaException, IOException, SAXException {

        TikaJsonConfig tikaJsonConfig = tikaLoader.getConfig();
        TikaPluginManager tikaPluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Create ConfigStore if specified in pipesConfig
        this.configStore = createConfigStore(pipesConfig, tikaPluginManager);

        // Load managers with ConfigStore to enable runtime modifications
        this.fetcherManager = FetcherManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);
        this.emitterManager = EmitterManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);
        this.autoDetectParser = (AutoDetectParser) tikaLoader.loadAutoDetectParser();
        this.detector = this.autoDetectParser.getDetector();
        this.rMetaParser = new RecursiveParserWrapper(autoDetectParser);

    }

    /**
     * Creates a merged ParseContext with defaults from tika-config overlaid with request values.
     * Request values take precedence over defaults.
     * <p>
     * Creates a fresh context each time to avoid shared state between requests.
     *
     * @param requestContext the ParseContext from FetchEmitTuple
     * @return a new ParseContext with defaults + request overrides
     */
    private ParseContext createMergedParseContext(ParseContext requestContext) throws TikaConfigException {
        // Create fresh context with defaults from tika-config (e.g., DigesterFactory)
        ParseContext mergedContext = tikaLoader.loadParseContext();
        // If no embedded document extractor factory is configured, use UnpackExtractorFactory
        // as the default for pipes scenarios (supports embedded byte extraction)
        if (mergedContext.get(EmbeddedDocumentExtractorFactory.class) == null) {
            mergedContext.set(EmbeddedDocumentExtractorFactory.class, new UnpackExtractorFactory());
        }
        // Overlay request's values (request takes precedence)
        mergedContext.copyFrom(requestContext);
        return mergedContext;
    }

    private ConfigStore createConfigStore(PipesConfig pipesConfig, TikaPluginManager tikaPluginManager) throws TikaException {
        String configStoreType = pipesConfig.getConfigStoreType();
        String configStoreParams = pipesConfig.getConfigStoreParams();

        if (configStoreType == null || "memory".equals(configStoreType)) {
            // Use default in-memory store (no persistence)
            return null;
        }

        ExtensionConfig storeConfig = new ExtensionConfig(
            configStoreType, configStoreType, configStoreParams);

        return ConfigStoreFactory.createConfigStore(
                tikaPluginManager,
                configStoreType,
                storeConfig);
    }

    private void writeFinished(PipesResult pipesResult) {
        try {
            protocolIO.writeFinished(pipesResult);
        } catch (ShutDownReceivedException e) {
            handleShutDown();
        } catch (IOException e) {
            LOG.error("problem writing emit data (forking process shutdown?)", e);
            exit(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().orElse(19));
        }
    }

    private void writeIntermediate(Metadata metadata) {
        try {
            protocolIO.writeIntermediate(metadata);
        } catch (ShutDownReceivedException e) {
            handleShutDown();
        } catch (IOException e) {
            LOG.error("problem writing intermediate data (forking process shutdown?)", e);
            exit(PipesMessageType.UNSPECIFIED_CRASH.getExitCode().orElse(19));
        }
    }

    private void handleShutDown() {
        LOG.info("pipesClientId={}: received SHUT_DOWN, shutting down gracefully", pipesClientId);
        try {
            close();
        } catch (Exception e) {
            //swallow
        }
        exit(0);
    }
}
