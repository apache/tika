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

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.detect.Detector;
import org.apache.tika.digest.Digester;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.RUnpackExtractorFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.AutoDetectParser;
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
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.plugins.TikaPluginManager;
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

    private final long heartbeatIntervalMs;
    private final String pipesClientId;

    //this has to be some number not close to 0-3
    //it looks like the server crashes with exit value 3 on uncaught OOM, for example
    public static final int TIMEOUT_EXIT_CODE = 17;
    public static final int OOM_EXIT_CODE = 18;
    public static final int UNSPECIFIED_CRASH_EXIT_CODE = 19;


    public enum PROCESSING_STATUS {
        READY, INTERMEDIATE_RESULT, WORKING, FINISHED,
        OOM(OOM_EXIT_CODE), TIMEOUT(TIMEOUT_EXIT_CODE), UNSPECIFIED_CRASH(UNSPECIFIED_CRASH_EXIT_CODE);

        int exitCode = -1;
        public static PROCESSING_STATUS lookup(int b) {
            if (b < 1) {
                throw new IllegalArgumentException("bad result value: " + b);
            }
            int ordinal = b - 1;
            if (ordinal >= PROCESSING_STATUS.values().length) {
                throw new IllegalArgumentException("ordinal > than array length? " + ordinal);
            }
            return PROCESSING_STATUS.values()[ordinal];
        }
        PROCESSING_STATUS() {

        }

        PROCESSING_STATUS(int exitCode) {
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }

        public byte getByte() {
            return (byte) (ordinal() + 1);
        }
    }
    private Digester digester;

    private Detector detector;



    private final Object[] lock = new Object[0];
    private final DataInputStream input;
    private final DataOutputStream output;
    //if an extract is larger than this value, emit it directly;
    //if it is smaller than this value, write it back to the
    //PipesClient so that it can cache the extracts and then batch emit.

    private final TikaLoader tikaLoader;
    private final PipesConfig pipesConfig;
    private final Socket socket;
    private final MetadataFilter defaultMetadataFilter;
    private AutoDetectParser autoDetectParser;
    private RecursiveParserWrapper rMetaParser;
    private FetcherManager fetcherManager;
    private EmitterManager emitterManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ExecutorCompletionService<PipesResult> executorCompletionService = new ExecutorCompletionService<>(executorService);
    private final EmitStrategy emitStrategy;

    public static PipesServer load(int port, Path tikaConfigPath) throws Exception {
            String pipesClientId = System.getProperty("pipesClientId", "unknown");
            LOG.debug("pipesClientId={}: connecting to client on port={}", pipesClientId, port);
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), PipesClient.SOCKET_CONNECT_TIMEOUT_MS);

            DataInputStream dis = new DataInputStream(socket.getInputStream());
            DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        try {
            TikaLoader tikaLoader = TikaLoader.load(tikaConfigPath);
            TikaJsonConfig tikaJsonConfig = tikaLoader.getConfig();
            PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);

            // Set socket timeout from config after loading PipesConfig
            socket.setSoTimeout((int) pipesConfig.getSocketTimeoutMs());

            MetadataFilter metadataFilter = tikaLoader.loadMetadataFilters();
            PipesServer pipesServer = new PipesServer(pipesClientId, tikaLoader, pipesConfig, socket, dis, dos, metadataFilter);
            pipesServer.initializeResources();
            LOG.debug("pipesClientId={}: PipesServer loaded and ready", pipesClientId);
            return pipesServer;
        } catch (Exception e) {
            LOG.error("Failed to start up", e);
            try {
                // Write FINISHED status byte and await ACK
                dos.writeByte(FINISHED.getByte());
                dos.flush();
                int ack = dis.read();
                if (ack != PipesClient.COMMANDS.ACK.getByte()) {
                    LOG.warn("Expected ACK but got: {}", ack);
                }

                // Write error message and await ACK
                String msg = ExceptionUtils.getStackTrace(e);
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                dos.writeInt(bytes.length);
                dos.write(bytes);
                dos.flush();
                ack = dis.read();
                if (ack != PipesClient.COMMANDS.ACK.getByte()) {
                    LOG.warn("Expected ACK but got: {}", ack);
                }
            } catch (IOException ioException) {
                LOG.error("Failed to send startup failure message to client", ioException);
            }
            throw e;
        }
    }

    public PipesServer(String pipesClientId, TikaLoader tikaLoader, PipesConfig pipesConfig, Socket socket, DataInputStream in,
                       DataOutputStream out, MetadataFilter metadataFilter) throws TikaConfigException,
            IOException {

        this.pipesClientId = pipesClientId;
        this.tikaLoader = tikaLoader;
        this.pipesConfig = pipesConfig;
        this.socket = socket;
        this.defaultMetadataFilter = metadataFilter;
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
    }


    public static void main(String[] args) throws Exception {
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
            LOG.info("pipesClientId={}: server shutting down", pipesClientId);
        }
    }

    public void mainLoop() {
        write(PROCESSING_STATUS.READY.getByte());
        LOG.debug("pipesClientId={}: sent READY, entering main loop", pipesClientId);
        ArrayBlockingQueue<Metadata> intermediateResult = new ArrayBlockingQueue<>(1);

        //main loop
        try {
            long start = System.currentTimeMillis();
            while (true) {
                int request = input.read();
                LOG.trace("pipesClientId={}: received command byte={}", pipesClientId, HexFormat.of().formatHex(new byte[]{(byte)request}));
                if (request == -1) {
                    LOG.warn("received -1 from client; shutting down");
                    exit(0);
                }

                // Validate that we received a command byte, not a status/ACK byte
                if (request == PipesClient.COMMANDS.ACK.getByte()) {
                    String msg = String.format(Locale.ROOT,
                            "pipesClientId=%s: PROTOCOL ERROR - Received ACK (byte=0x%02x) when expecting a command. " +
                            "This indicates a protocol synchronization issue where the server missed consuming an ACK. " +
                            "Valid commands are: PING(0x%02x), NEW_REQUEST(0x%02x), SHUT_DOWN(0x%02x). " +
                            "This is likely a bug in the server's message handling - check that all status messages " +
                            "that trigger client ACKs are properly awaiting those ACKs.",
                            pipesClientId, (byte)request,
                            PipesClient.COMMANDS.PING.getByte(),
                            PipesClient.COMMANDS.NEW_REQUEST.getByte(),
                            PipesClient.COMMANDS.SHUT_DOWN.getByte());
                    LOG.error(msg);
                    throw new IllegalStateException(msg);
                }

                if (request == PipesClient.COMMANDS.PING.getByte()) {
                    writeNoAck(PipesClient.COMMANDS.PING.getByte());
                } else if (request == PipesClient.COMMANDS.NEW_REQUEST.getByte()) {
                    intermediateResult.clear();
                    CountDownLatch countDownLatch = new CountDownLatch(1);

                    FetchEmitTuple fetchEmitTuple = readFetchEmitTuple();
                    // Resolve friendly-named configs in ParseContext to actual objects
                    ParseContextUtils.resolveAll(fetchEmitTuple.getParseContext(), getClass().getClassLoader());

                    PipesWorker pipesWorker = getPipesWorker(intermediateResult, fetchEmitTuple, countDownLatch);
                    executorCompletionService.submit(pipesWorker);
                    //set progress counter
                    try {
                        loopUntilDone(fetchEmitTuple, executorCompletionService, intermediateResult, countDownLatch);
                    } catch (Throwable t) {
                        LOG.error("Serious problem: {}", HexFormat.of().formatHex(new byte[]{(byte)request}), t);
                    }
                } else if (request == PipesClient.COMMANDS.SHUT_DOWN.getByte()) {
                    LOG.info("shutting down");
                    try {
                        close();
                    } catch (Exception e) {
                        //swallow
                    }
                    System.exit(0);
                } else {
                    String msg = String.format(Locale.ROOT,
                            "pipesClientId=%s: Unexpected byte 0x%02x in command position. " +
                            "Expected one of: PING(0x%02x), ACK(0x%02x), NEW_REQUEST(0x%02x), SHUT_DOWN(0x%02x)",
                            pipesClientId, (byte)request,
                            PipesClient.COMMANDS.PING.getByte(),
                            PipesClient.COMMANDS.ACK.getByte(),
                            PipesClient.COMMANDS.NEW_REQUEST.getByte(),
                            PipesClient.COMMANDS.SHUT_DOWN.getByte());
                    LOG.error(msg);
                    throw new IllegalStateException(msg);
                }
                output.flush();
            }
        } catch (Throwable t) {
            LOG.error("main loop error (did the forking process shut down?)", t);
            exit(1);
        }
    }

    private PipesWorker getPipesWorker(ArrayBlockingQueue<Metadata> intermediateResult, FetchEmitTuple fetchEmitTuple, CountDownLatch countDownLatch) {
        FetchHandler fetchHandler = new FetchHandler(fetcherManager);
        ParseHandler parseHandler = new ParseHandler(detector, digester, intermediateResult, countDownLatch, autoDetectParser, rMetaParser);
        Long thresholdBytes = pipesConfig.getEmitStrategy().getThresholdBytes();
        long threshold = (thresholdBytes != null) ? thresholdBytes : EmitStrategyConfig.DEFAULT_DIRECT_EMIT_THRESHOLD_BYTES;
        EmitHandler emitHandler = new EmitHandler(defaultMetadataFilter, emitStrategy, emitterManager, threshold);
        PipesWorker pipesWorker = new PipesWorker(fetchEmitTuple, autoDetectParser, emitterManager, fetchHandler, parseHandler, emitHandler);
        return pipesWorker;
    }

    private void loopUntilDone(FetchEmitTuple fetchEmitTuple, ExecutorCompletionService<PipesResult> executorCompletionService,
                               ArrayBlockingQueue<Metadata> intermediateResult, CountDownLatch countDownLatch) throws InterruptedException, IOException {
        Instant start = Instant.now();
        long timeoutMillis = PipesClient.getTimeoutMillis(pipesConfig, fetchEmitTuple.getParseContext());
        long mockProgressCounter = 0;
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
                    handleCrash(OOM, fetchEmitTuple.getId(), e);
                } catch (ExecutionException e) {
                    Throwable t = e.getCause();
                    LOG.error("crash: {}", fetchEmitTuple.getId(), t);
                    if (t instanceof OutOfMemoryError) {
                        handleCrash(OOM, fetchEmitTuple.getId(), t);
                    }
                    handleCrash(PROCESSING_STATUS.UNSPECIFIED_CRASH, fetchEmitTuple.getId(), t);
                }
                LOG.debug("executor completionService finished task: id={} status={}", fetchEmitTuple.getId(), pipesResult.status());
                write(FINISHED, pipesResult);
                return;
            }

            // Send heartbeat if we've waited long enough
            long elapsed = System.currentTimeMillis() - start.toEpochMilli();
            if (elapsed > mockProgressCounter * heartbeatIntervalMs) {
                LOG.debug("still processing: {}", mockProgressCounter);
                write(PROCESSING_STATUS.WORKING.getByte());
                output.writeLong(mockProgressCounter++);
                output.flush();
            }

            checkTimeout(start, timeoutMillis);
        }

    }

    private void checkTimeout(Instant start, long timeoutMillis) throws IOException {

        if (Duration.between(start, Instant.now()).toMillis() > timeoutMillis) {
            write(TIMEOUT.getByte());
            exit(TIMEOUT_EXIT_CODE);
        }
    }

    private void handleCrash(PROCESSING_STATUS processingStatus, String id, Throwable t) {
        LOG.error("{}: {}", processingStatus, id, t);
        String msg = (t != null) ? ExceptionUtils.getStackTrace(t) : "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            write(processingStatus, bos.toByteArray());
            awaitAck();
        } catch (IOException e) {
            //swallow
            LOG.warn("problem writing crash info to client", e);
        }
        exit(processingStatus.getExitCode());
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
            LOG.info("exiting: {}", exitCode);
        }
        System.exit(exitCode);
    }


    private FetchEmitTuple readFetchEmitTuple() {
        try {
            int length = input.readInt();
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            try (ObjectInputStream objectInputStream = new ObjectInputStream(
                    UnsynchronizedByteArrayInputStream.builder().setByteArray(bytes).get())) {
                return (FetchEmitTuple) objectInputStream.readObject();
            }
        } catch (IOException e) {
            LOG.error("problem reading tuple", e);
            exit(1);
        } catch (ClassNotFoundException e) {
            LOG.error("can't find class?!", e);
            exit(1);
        }
        //unreachable, no?!
        return null;
    }

    protected void initializeResources() throws TikaException, IOException, SAXException {

        TikaJsonConfig tikaJsonConfig = tikaLoader.getConfig();
        TikaPluginManager tikaPluginManager = TikaPluginManager.load(tikaJsonConfig);

        // Create ConfigStore using the same configuration as TikaGrpcServerImpl
        // This allows fetchers saved via gRPC to be available to PipesServer
        ConfigStore configStore = createConfigStore(pipesConfig, tikaPluginManager);

        // Load FetcherManager with ConfigStore to enable runtime modifications
        this.fetcherManager = FetcherManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);
        // Always initialize emitters to support runtime overrides via ParseContext  
        this.emitterManager = EmitterManager.load(tikaPluginManager, tikaJsonConfig, true, configStore);
        this.autoDetectParser = (AutoDetectParser) tikaLoader.loadAutoDetectParser();
        // Get the digester for pre-parse digesting of container documents.
        // If user configured skipContainerDocumentDigest=false (the default), PipesServer
        // digests the container document before parsing to ensure we have the digest even
        // if parsing times out. The SkipContainerDocumentDigest marker is then added to
        // ParseContext to prevent AutoDetectParser from re-digesting the container.
        // If user configured skipContainerDocumentDigest=true, we don't digest containers at all.
        boolean skipContainerDigest = autoDetectParser.getAutoDetectParserConfig()
                .isSkipContainerDocumentDigest();
        if (!skipContainerDigest) {
            // User wants container documents digested - we'll do it in ParseHandler before parse
            this.digester = autoDetectParser.getAutoDetectParserConfig().digester();
        } else {
            // User doesn't want container documents digested
            this.digester = null;
        }

        // If the user hasn't configured an embedded document extractor, set up the
        // RUnpackExtractorFactory
        if (autoDetectParser.getAutoDetectParserConfig().getEmbeddedDocumentExtractorFactory() == null) {
                autoDetectParser.getAutoDetectParserConfig().setEmbeddedDocumentExtractorFactory(new RUnpackExtractorFactory());
        }
        this.detector = this.autoDetectParser.getDetector();
        this.rMetaParser = new RecursiveParserWrapper(autoDetectParser);
    }

    private ConfigStore createConfigStore(PipesConfig pipesConfig, TikaPluginManager tikaPluginManager) throws TikaException {
        if (pipesConfig.getConfigStore() != null) {
            ConfigStoreFactory factory = tikaPluginManager.getPluginManager()
                    .getExtensions(ConfigStoreFactory.class).stream()
                    .filter(f -> f.getClass().getName().equals(pipesConfig.getConfigStore().getFactoryClass()))
                    .findFirst()
                    .orElseThrow(() -> new TikaException("Could not find ConfigStoreFactory: " + 
                            pipesConfig.getConfigStore().getFactoryClass()));
            return factory.build(pipesConfig.getConfigStore().getParams());
        }
        return null;
    }

    private void write(PROCESSING_STATUS processingStatus, PipesResult pipesResult) {
        try {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(pipesResult);
            }
            write(processingStatus, bos.toByteArray());
        } catch (IOException e) {
            LOG.error("problem writing emit data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void writeIntermediate(Metadata metadata) {
        try {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(metadata);
            }
            write(INTERMEDIATE_RESULT, bos.toByteArray());
        } catch (IOException e) {
            LOG.error("problem writing intermediate data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void awaitAck() throws IOException {
        int b = input.read();
        if (b == ACK.getByte()) {
            return;
        }
        LOG.error("pipesClientId={}: expected ACK but got byte={}", pipesClientId, HexFormat.of().formatHex(new byte[]{ (byte) b}));
        throw new IOException("Wasn't expecting byte=" + HexFormat.of().formatHex(new byte[]{ (byte) b}));
    }

    private void writeNoAck(byte b) {
        try {
            output.write(b);
            output.flush();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(byte b) {
        try {
            output.write(b);
            output.flush();
            awaitAck();
        } catch (IOException e) {
            LOG.error("pipesClientId={}: problem writing data (forking process shutdown?)", pipesClientId, e);
            exit(1);
        }
    }


    private void write(PROCESSING_STATUS status, byte[] bytes) {
        try {
            write(status.getByte());
            int len = bytes.length;
            output.writeInt(len);
            output.write(bytes);
            output.flush();
            awaitAck();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }

}
