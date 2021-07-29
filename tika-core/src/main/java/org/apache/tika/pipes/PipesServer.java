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
package org.apache.tika.pipes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.ExceptionUtils;
import org.apache.tika.utils.StringUtils;

/**
 * This server is forked from the PipesClient.  This class isolates
 * parsing from the client to protect the primary JVM.
 *
 * When configuring logging for this class, make absolutely certain
 * not to write to STDOUT.  This class uses STDOUT to communicate with
 * the PipesClient.
 */
public class PipesServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesServer.class);

    //this has to be some number not close to 0-3
    //it looks like the server crashes with exit value 3 on OOM, for example
    public static final int TIMEOUT_EXIT_CODE = 17;

    public enum STATUS {
        READY,
        CALL,
        PING,
        FAILED_TO_START,
        FETCHER_NOT_FOUND,
        EMITTER_NOT_FOUND,
        FETCHER_INITIALIZATION_EXCEPTION,
        FETCH_EXCEPTION,
        PARSE_SUCCESS,
        PARSE_EXCEPTION_NO_EMIT,
        EMIT_SUCCESS,
        EMIT_SUCCESS_PARSE_EXCEPTION,
        EMIT_EXCEPTION,
        OOM,
        TIMEOUT,
        EMPTY_OUTPUT;

        byte getByte() {
            return (byte) (ordinal() + 1);
        }

        public static STATUS lookup(int val) {
            int i = val - 1;
            if (i < 0) {
                throw new IllegalArgumentException("byte must be > 0");
            }
            STATUS[] statuses = STATUS.values();

            if (i >= statuses.length) {
                throw new IllegalArgumentException("byte with index " +
                        i + " must be < " + statuses.length);
            }
            return statuses[i];
        }
    }

    private final Object[] lock = new Object[0];
    private final Path tikaConfigPath;
    private final DataInputStream input;
    private final DataOutputStream output;
    //if an extract is larger than this value, emit it directly;
    //if it is smaller than this value, write it back to the
    //PipesClient so that it can cache the extracts and then batch emit.
    private final long maxForEmitBatchBytes;
    private final long serverParseTimeoutMillis;
    private final long serverWaitTimeoutMillis;
    private Parser autoDetectParser;
    private Parser rMetaParser;
    private TikaConfig tikaConfig;
    private FetcherManager fetcherManager;
    private EmitterManager emitterManager;
    private volatile boolean parsing;
    private volatile long since;


    public PipesServer(Path tikaConfigPath, InputStream in, PrintStream out,
                       long maxForEmitBatchBytes,
                       long serverParseTimeoutMillis, long serverWaitTimeoutMillis)
            throws IOException, TikaException, SAXException {
        this.tikaConfigPath = tikaConfigPath;
        this.input = new DataInputStream(in);
        this.output = new DataOutputStream(out);
        this.maxForEmitBatchBytes = maxForEmitBatchBytes;
        this.serverParseTimeoutMillis = serverParseTimeoutMillis;
        this.serverWaitTimeoutMillis = serverWaitTimeoutMillis;
        this.parsing = false;
        this.since = System.currentTimeMillis();
    }


    public static void main(String[] args) throws Exception {
        Path tikaConfig = Paths.get(args[0]);
        long maxForEmitBatchBytes = Long.parseLong(args[1]);
        long serverParseTimeoutMillis = Long.parseLong(args[2]);
        long serverWaitTimeoutMillis = Long.parseLong(args[3]);

        PipesServer server =
                new PipesServer(tikaConfig, System.in, System.out,
                        maxForEmitBatchBytes, serverParseTimeoutMillis,
                serverWaitTimeoutMillis);
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);
        Thread watchdog = new Thread(server, "Tika Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        server.processRequests();
    }

    public void run() {
        try {
            while (true) {
                synchronized (lock) {
                    long elapsed = System.currentTimeMillis() - since;
                    if (parsing && elapsed > serverParseTimeoutMillis) {
                        LOG.warn("timeout server; elapsed {}  with {}", elapsed, serverParseTimeoutMillis);
                        exit(TIMEOUT_EXIT_CODE);
                    } else if (!parsing && serverWaitTimeoutMillis > 0 &&
                            elapsed > serverWaitTimeoutMillis) {
                        LOG.debug("closing down from inactivity");
                        exit(0);
                    }
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            //swallow
        }
    }

    public void processRequests() {
        //initialize
        try {
            initializeParser();
        } catch (Throwable t) {
            t.printStackTrace();
            LOG.error("couldn't initialize parser", t);
            try {
                output.writeByte(STATUS.FAILED_TO_START.getByte());
                output.flush();
            } catch (IOException e) {
                LOG.warn("couldn't notify of failure to start", e);
            }
            return;
        }
        //main loop
        try {
            write(STATUS.READY);
            while (true) {
                int request = input.read();
                if (request == -1) {
                    exit(1);
                } else if (request == STATUS.PING.getByte()) {
                    write(STATUS.PING);
                } else if (request == STATUS.CALL.getByte()) {
                    parseOne();
                } else {
                    throw new IllegalStateException("Unexpected request");
                }
                output.flush();
            }
        } catch (Throwable t) {
            LOG.error("main loop error (did the forking process shut down?)", t);
            exit(1);
        }
        System.err.flush();
    }

    private boolean metadataIsEmpty(List<Metadata> metadataList) {
        return metadataList == null || metadataList.size() == 0;
    }

    /**
     * returns stack trace if there was a container exception or empty string
     * if there was no stacktrace
     * @param t
     * @param metadataList
     * @return
     */
    private String getContainerStacktrace(FetchEmitTuple t, List<Metadata> metadataList) {
        if (metadataList == null || metadataList.size() < 1) {
            return "";
        }
        String stack = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        return (stack != null) ? stack : "";
    }


    private void emit(String taskId, EmitData emitData, String parseExceptionStack) {
        Emitter emitter = null;

        try {
            emitter = emitterManager.getEmitter(emitData.getEmitKey().getEmitterName());
        } catch (IllegalArgumentException e) {
            String noEmitterMsg = getNoEmitterMsg(taskId);
            LOG.warn(noEmitterMsg);
            write(STATUS.EMITTER_NOT_FOUND, noEmitterMsg);
            return;
        }
        try {
            emitter.emit(emitData.getEmitKey().getEmitKey(), emitData.getMetadataList());
        } catch (IOException | TikaEmitterException e) {
            LOG.warn("emit exception", e);
            String msg = ExceptionUtils.getStackTrace(e);
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            //for now, we're hiding the parse exception if there was also an emit exception
            write(STATUS.EMIT_EXCEPTION, bytes);
            return;
        }
        if (StringUtils.isBlank(parseExceptionStack)) {
            write(STATUS.EMIT_SUCCESS);
        } else {
            write(STATUS.EMIT_SUCCESS_PARSE_EXCEPTION,
                    parseExceptionStack.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void parseOne() {
        synchronized (lock) {
            parsing = true;
            since = System.currentTimeMillis();
        }
        FetchEmitTuple t = null;
        try {
            t = readFetchEmitTuple();
            actuallyParse(t);
        } catch (OutOfMemoryError e) {
            handleOOM(t.getId(), e);
        } finally {
            synchronized (lock) {
                parsing = false;
                since = System.currentTimeMillis();
            }
        }
    }

    private void actuallyParse(FetchEmitTuple t) {
        List<Metadata> metadataList = null;

        Fetcher fetcher = null;
        try {
            fetcher = fetcherManager.getFetcher(t.getFetchKey().getFetcherName());
        } catch (IllegalArgumentException e) {
            String noFetcherMsg = getNoFetcherMsg(t.getFetchKey().getFetcherName());
            LOG.warn(noFetcherMsg);
            write(STATUS.FETCHER_NOT_FOUND, noFetcherMsg);
            return;
        } catch (IOException | TikaException e) {
            LOG.warn("Couldn't initialize fetcher for fetch id '" +
                    t.getId() + "'", e);
            write(STATUS.FETCHER_INITIALIZATION_EXCEPTION,
                    ExceptionUtils.getStackTrace(e));
            return;
        }

        Metadata metadata = new Metadata();
        try (InputStream stream = fetcher.fetch(t.getFetchKey().getFetchKey(), metadata)) {
            metadataList = parse(t, stream, metadata);
        } catch (SecurityException e) {
            LOG.error("security exception " + t.getId(), e);
            throw e;
        } catch (TikaException | IOException e) {
            LOG.warn("fetch exception " + t.getId(), e);
            write(STATUS.FETCH_EXCEPTION, ExceptionUtils.getStackTrace(e));
        }
        if (metadataIsEmpty(metadataList)) {
            write(STATUS.EMPTY_OUTPUT);
            return;
        }

        String stack = getContainerStacktrace(t, metadataList);
        if (StringUtils.isBlank(stack) || t.getOnParseException() == FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT) {
            injectUserMetadata(t.getMetadata(), metadataList);
            EmitKey emitKey = t.getEmitKey();
            if (StringUtils.isBlank(emitKey.getEmitKey())) {
                emitKey = new EmitKey(emitKey.getEmitterName(), t.getFetchKey().getFetchKey());
                t.setEmitKey(emitKey);
            }
            EmitData emitData = new EmitData(t.getEmitKey(), metadataList);
            if (maxForEmitBatchBytes >= 0 && emitData.getEstimatedSizeBytes() >= maxForEmitBatchBytes) {
                emit(t.getId(), emitData, stack);
            } else {
                //ignore the stack, it is stored in the emit data
                write(emitData);
            }
        } else {
            write(STATUS.PARSE_EXCEPTION_NO_EMIT, stack);
        }

    }

    private String getNoFetcherMsg(String fetcherName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fetcher '").append(fetcherName).append("'");
        sb.append(" not found.");
        sb.append("\nThe configured FetcherManager supports:");
        int i = 0;
        for (String f : fetcherManager.getSupported()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(f);
        }
        return sb.toString();
    }

    private String getNoEmitterMsg(String emitterName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Emitter '").append(emitterName).append("'");
        sb.append(" not found.");
        sb.append("\nThe configured emitterManager supports:");
        int i = 0;
        for (String e : emitterManager.getSupported()) {
            if (i++ > 0) {
                sb.append(", ");
            }
            sb.append(e);
        }
        return sb.toString();
    }


    private void handleOOM(String taskId, OutOfMemoryError oom) {
        write(STATUS.OOM);
        LOG.error("oom: " + taskId, oom);
        exit(1);
    }

    private List<Metadata> parse(FetchEmitTuple fetchEmitTuple, InputStream stream,
                                 Metadata metadata) {
        HandlerConfig handlerConfig = fetchEmitTuple.getHandlerConfig();
        if (handlerConfig.getParseMode() == HandlerConfig.PARSE_MODE.RMETA) {
            return parseRecursive(fetchEmitTuple, handlerConfig, stream, metadata);
        } else {
            return parseConcatenated(fetchEmitTuple, handlerConfig, stream, metadata);
        }
    }

    private List<Metadata> parseConcatenated(FetchEmitTuple fetchEmitTuple,
                                             HandlerConfig handlerConfig, InputStream stream,
                                             Metadata metadata) {
        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(handlerConfig.getType(), handlerConfig.getWriteLimit());
        ContentHandler handler = contentHandlerFactory.getNewContentHandler();
        ParseContext parseContext = new ParseContext();
        parseContext.set(DocumentSelector.class, new DocumentSelector() {
            final int maxEmbedded = handlerConfig.maxEmbeddedResources;
            int embedded = 0;
            @Override
            public boolean select(Metadata metadata) {
                if (maxEmbedded < 0) {
                    return true;
                }
                return embedded++ > maxEmbedded;
            }
        });

        String containerException = null;
        try {
            autoDetectParser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
        } catch (EncryptedDocumentException e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("encrypted document:" + fetchEmitTuple.getId(), e);
        } catch (SecurityException e) {
            LOG.warn("security exception:" + fetchEmitTuple.getId(), e);
            throw e;
        } catch (Exception e) {
            containerException = ExceptionUtils.getStackTrace(e);
            LOG.warn("exception: " + fetchEmitTuple.getId(), e);
        } finally {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, handler.toString());
            if (containerException != null) {
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, containerException);
            }
            try {
                tikaConfig.getMetadataFilter().filter(metadata);
            } catch (TikaException e) {
                LOG.warn("exception mapping metadata", e);
            }
        }
        return Collections.singletonList(metadata);
    }

    private List<Metadata> parseRecursive(FetchEmitTuple fetchEmitTuple,
                                          HandlerConfig handlerConfig, InputStream stream,
                                          Metadata metadata) {
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerConfig.getType(), handlerConfig.getWriteLimit()),
                handlerConfig.getMaxEmbeddedResources(), tikaConfig.getMetadataFilter());
        ParseContext parseContext = new ParseContext();
        try {
            rMetaParser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
        } catch (EncryptedDocumentException e) {
            LOG.warn("encrypted document:" + fetchEmitTuple.getId(), e);
        } catch (SecurityException e) {
            LOG.warn("security exception:" + fetchEmitTuple.getId(), e);
            throw e;
        } catch (Exception e) {
            LOG.warn("exception: " + fetchEmitTuple.getId(), e);
        }
        return handler.getMetadataList();
    }

    private void injectUserMetadata(Metadata userMetadata, List<Metadata> metadataList) {
        for (String n : userMetadata.names()) {
            //overwrite whatever was there
            metadataList.get(0).set(n, null);
            for (String val : userMetadata.getValues(n)) {
                metadataList.get(0).add(n, val);
            }
        }
    }

    private void exit(int exitCode) {
        LOG.error("exiting: {}", exitCode);
        System.exit(exitCode);
    }


    private FetchEmitTuple readFetchEmitTuple() {
        try {
            int length = input.readInt();
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            try (ObjectInputStream objectInputStream = new ObjectInputStream(
                    new ByteArrayInputStream(bytes))) {
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

    private void initializeParser() throws TikaException, IOException, SAXException {
        //TODO allowed named configurations in tika config
        this.tikaConfig = new TikaConfig(tikaConfigPath);
        this.fetcherManager = FetcherManager.load(tikaConfigPath);
        this.emitterManager = EmitterManager.load(tikaConfigPath);
        this.autoDetectParser = new AutoDetectParser(this.tikaConfig);
        this.rMetaParser = new RecursiveParserWrapper(autoDetectParser);
    }


    private void write(EmitData emitData) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(emitData);
            }
            write(STATUS.PARSE_SUCCESS, bos.toByteArray());
        } catch (IOException e) {
            LOG.error("problem writing emit data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(STATUS status, String msg) {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        write(status, bytes);
    }

    private void write(STATUS status, byte[] bytes) {
        try {
            int len = bytes.length;
            output.write(status.getByte());
            output.writeInt(len);
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(STATUS status) {
        try {
            output.write(status.getByte());
            output.flush();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }
}
