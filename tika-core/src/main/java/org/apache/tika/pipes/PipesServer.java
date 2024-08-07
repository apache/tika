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

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.BasicEmbeddedDocumentBytesHandler;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.EmbeddedDocumentByteStoreExtractorFactory;
import org.apache.tika.extractor.EmbeddedDocumentBytesHandler;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.RUnpackExtractor;
import org.apache.tika.extractor.RUnpackExtractorFactory;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DigestingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.StreamEmitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.extractor.EmittingEmbeddedDocumentBytesHandler;
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
 * <p>
 * When configuring logging for this class, make absolutely certain
 * not to write to STDOUT.  This class uses STDOUT to communicate with
 * the PipesClient.
 */
public class PipesServer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(PipesServer.class);

    //this has to be some number not close to 0-3
    //it looks like the server crashes with exit value 3 on OOM, for example
    public static final int TIMEOUT_EXIT_CODE = 17;
    private DigestingParser.Digester digester;

    private Detector detector;

    public enum STATUS {
        READY, CALL, PING, FAILED_TO_START, FETCHER_NOT_FOUND, EMITTER_NOT_FOUND,
        FETCHER_INITIALIZATION_EXCEPTION, FETCH_EXCEPTION, PARSE_SUCCESS, PARSE_EXCEPTION_NO_EMIT,
        EMIT_SUCCESS, EMIT_SUCCESS_PARSE_EXCEPTION, EMIT_EXCEPTION, OOM, TIMEOUT, EMPTY_OUTPUT,
        INTERMEDIATE_RESULT;

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
                throw new IllegalArgumentException(
                        "byte with index " + i + " must be < " + statuses.length);
            }
            return statuses[i];
        }
    }

    private final Object[] lock = new Object[0];
    private long checkForTimeoutMs = 1000;
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
                       long maxForEmitBatchBytes, long serverParseTimeoutMillis,
                       long serverWaitTimeoutMillis)
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
        try {
            Path tikaConfig = Paths.get(args[0]);
            long maxForEmitBatchBytes = Long.parseLong(args[1]);
            long serverParseTimeoutMillis = Long.parseLong(args[2]);
            long serverWaitTimeoutMillis = Long.parseLong(args[3]);

            PipesServer server =
                    new PipesServer(tikaConfig, System.in, System.out, maxForEmitBatchBytes,
                            serverParseTimeoutMillis, serverWaitTimeoutMillis);
            System.setIn(new UnsynchronizedByteArrayInputStream(new byte[0]));
            System.setOut(System.err);
            Thread watchdog = new Thread(server, "Tika Watchdog");
            watchdog.setDaemon(true);
            watchdog.start();

            server.processRequests();
        } finally {
            LOG.info("server shutting down");
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                synchronized (lock) {
                    long elapsed = System.currentTimeMillis() - since;
                    if (parsing && elapsed > serverParseTimeoutMillis) {
                        LOG.warn("timeout server; elapsed {}  with {}", elapsed,
                                serverParseTimeoutMillis);
                        exit(TIMEOUT_EXIT_CODE);
                    } else if (!parsing && serverWaitTimeoutMillis > 0 &&
                            elapsed > serverWaitTimeoutMillis) {
                        LOG.info("closing down from inactivity");
                        exit(0);
                    }
                }
                Thread.sleep(checkForTimeoutMs);
            }
        } catch (InterruptedException e) {
            LOG.debug("interrupted");
        }
    }

    public void processRequests() {
        LOG.debug("processing requests {}");
        //initialize
        try {
            long start = System.currentTimeMillis();
            initializeResources();
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- initialize parser and other resources: {} ms",
                        System.currentTimeMillis() - start);
            }
            LOG.debug("pipes server initialized");
        } catch (Throwable t) {
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
            long start = System.currentTimeMillis();
            while (true) {
                int request = input.read();
                if (request == -1) {
                    LOG.warn("received -1 from client; shutting down");
                    exit(1);
                } else if (request == STATUS.PING.getByte()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("timer -- ping: {} ms", System.currentTimeMillis() - start);
                    }
                    write(STATUS.PING);
                    start = System.currentTimeMillis();
                } else if (request == STATUS.CALL.getByte()) {
                    parseOne();
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("timer -- parse one: {} ms", System.currentTimeMillis() - start);
                    }
                    start = System.currentTimeMillis();
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
     *
     * @param t
     * @param metadataList
     * @return
     */
    private String getContainerStacktrace(FetchEmitTuple t, List<Metadata> metadataList) {
        if (metadataIsEmpty(metadataList)) {
            return StringUtils.EMPTY;
        }
        String stack = metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION);
        return (stack != null) ? stack : StringUtils.EMPTY;
    }


    private void emit(String taskId, EmitKey emitKey,
                      boolean isExtractEmbeddedBytes, MetadataListAndEmbeddedBytes parseData,
                      String parseExceptionStack, ParseContext parseContext) {
        Emitter emitter = null;

        try {
            emitter = emitterManager.getEmitter(emitKey.getEmitterName());
        } catch (IllegalArgumentException e) {
            String noEmitterMsg = getNoEmitterMsg(taskId);
            LOG.warn(noEmitterMsg);
            write(STATUS.EMITTER_NOT_FOUND, noEmitterMsg);
            return;
        }
        try {
            if (isExtractEmbeddedBytes &&
                    parseData.toBePackagedForStreamEmitter()) {
                emitContentsAndBytes(emitter, emitKey, parseData);
            } else {
                emitter.emit(emitKey.getEmitKey(), parseData.getMetadataList(), parseContext);
            }
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

    private void emitContentsAndBytes(Emitter emitter, EmitKey emitKey,
                                      MetadataListAndEmbeddedBytes parseData) {
        if (!(emitter instanceof StreamEmitter)) {
            throw new IllegalArgumentException("The emitter for embedded document byte store must" +
                    " be a StreamEmitter. I see: " + emitter.getClass());
        }
        //TODO: implement this
        throw new UnsupportedOperationException("this is not yet implemented");
    }

    private void parseOne() {
        synchronized (lock) {
            parsing = true;
            since = System.currentTimeMillis();
        }
        FetchEmitTuple t = null;
        try {
            long start = System.currentTimeMillis();
            t = readFetchEmitTuple();
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- read fetchEmitTuple: {} ms",
                        System.currentTimeMillis() - start);
            }
            start = System.currentTimeMillis();
            actuallyParse(t);
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- actually parsed: {} ms", System.currentTimeMillis() - start);
            }
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

        long start = System.currentTimeMillis();
        Fetcher fetcher = getFetcher(t);
        if (fetcher == null) {
            //rely on proper logging/exception handling in getFetcher
            return;
        }

        if (LOG.isTraceEnabled()) {
            long elapsed = System.currentTimeMillis() - start;
            LOG.trace("timer -- got fetcher: {}ms", elapsed);
        }

        start = System.currentTimeMillis();
        MetadataListAndEmbeddedBytes parseData = null;

        try {
            //this can be null if there is a fetch exception
            parseData = parseFromTuple(t, fetcher);

            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- to parse: {} ms", System.currentTimeMillis() - start);
            }

            if (parseData == null || metadataIsEmpty(parseData.getMetadataList())) {
                write(STATUS.EMPTY_OUTPUT);
                return;
            }

            emitParseData(t, parseData);
        } finally {
            if (parseData != null && parseData.hasEmbeddedDocumentByteStore() &&
                    parseData.getEmbeddedDocumentBytesHandler() instanceof Closeable) {
                try {
                    ((Closeable) parseData.getEmbeddedDocumentBytesHandler()).close();
                } catch (IOException e) {
                    LOG.warn("problem closing embedded document byte store", e);
                }
            }
        }
    }

    private void emitParseData(FetchEmitTuple t, MetadataListAndEmbeddedBytes parseData) {
        long start = System.currentTimeMillis();
        String stack = getContainerStacktrace(t, parseData.getMetadataList());
        //we need to apply the metadata filter after we pull out the stacktrace
        MetadataFilter filter = t.getParseContext().get(MetadataFilter.class);
        if (filter == null) {
            filter = tikaConfig.getMetadataFilter();
        }
        filterMetadata(filter, parseData.getMetadataList());
        ParseContext parseContext = t.getParseContext();
        FetchEmitTuple.ON_PARSE_EXCEPTION onParseException = t.getOnParseException();
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = parseContext.get(EmbeddedDocumentBytesConfig.class);
        if (StringUtils.isBlank(stack) ||
                onParseException == FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT) {
            injectUserMetadata(t.getMetadata(), parseData.getMetadataList());
            EmitKey emitKey = t.getEmitKey();
            if (StringUtils.isBlank(emitKey.getEmitKey())) {
                emitKey = new EmitKey(emitKey.getEmitterName(), t.getFetchKey().getFetchKey());
                t.setEmitKey(emitKey);
            }
            EmitData emitData = new EmitData(t.getEmitKey(), parseData.getMetadataList(), stack);
            if (embeddedDocumentBytesConfig.isExtractEmbeddedDocumentBytes() &&
                    parseData.toBePackagedForStreamEmitter()) {
                emit(t.getId(), emitKey, embeddedDocumentBytesConfig.isExtractEmbeddedDocumentBytes(),
                        parseData, stack, parseContext);
            } else if (maxForEmitBatchBytes >= 0 &&
                    emitData.getEstimatedSizeBytes() >= maxForEmitBatchBytes) {
                emit(t.getId(), emitKey, embeddedDocumentBytesConfig.isExtractEmbeddedDocumentBytes(),
                        parseData, stack, parseContext);
            } else {
                //send back to the client
                write(emitData);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- emitted: {} ms", System.currentTimeMillis() - start);
            }
        } else {
            write(STATUS.PARSE_EXCEPTION_NO_EMIT, stack);
        }
    }

    private void filterMetadata(MetadataFilter metadataFilter, List<Metadata> metadataList) {
        for (Metadata m : metadataList) {
            try {
                metadataFilter.filter(m);
            } catch (TikaException e) {
                LOG.warn("failed to filter metadata", e);
            }
        }
    }

    private Fetcher getFetcher(FetchEmitTuple t) {
        try {
            return fetcherManager.getFetcher(t.getFetchKey().getFetcherName());
        } catch (IllegalArgumentException e) {
            String noFetcherMsg = getNoFetcherMsg(t.getFetchKey().getFetcherName());
            LOG.warn(noFetcherMsg);
            write(STATUS.FETCHER_NOT_FOUND, noFetcherMsg);
            return null;
        } catch (IOException | TikaException e) {
            LOG.warn("Couldn't initialize fetcher for fetch id '" + t.getId() + "'", e);
            write(STATUS.FETCHER_INITIALIZATION_EXCEPTION, ExceptionUtils.getStackTrace(e));
            return null;
        }
    }

    protected MetadataListAndEmbeddedBytes parseFromTuple(FetchEmitTuple t, Fetcher fetcher) {

        Metadata metadata = new Metadata();
        try (InputStream stream = fetcher.fetch(t.getFetchKey().getFetchKey(), metadata, t.getParseContext())) {
            return parseWithStream(t, stream, metadata);
        } catch (SecurityException e) {
            LOG.error("security exception " + t.getId(), e);
            throw e;
        } catch (TikaException | IOException e) {
            LOG.warn("fetch exception " + t.getId(), e);
            write(STATUS.FETCH_EXCEPTION, ExceptionUtils.getStackTrace(e));
        }

        return null;
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

    private MetadataListAndEmbeddedBytes parseWithStream(FetchEmitTuple fetchEmitTuple,
                                                         InputStream stream, Metadata metadata)
            throws TikaConfigException {

        List<Metadata> metadataList;
        //this adds the EmbeddedDocumentByteStore to the parsecontext
        ParseContext parseContext = setupParseContext(fetchEmitTuple);
        HandlerConfig handlerConfig = parseContext.get(HandlerConfig.class);
        if (handlerConfig.getParseMode() == HandlerConfig.PARSE_MODE.RMETA) {
            metadataList =
                    parseRecursive(fetchEmitTuple, handlerConfig, stream, metadata, parseContext);
        } else {
            metadataList = parseConcatenated(fetchEmitTuple, handlerConfig, stream, metadata,
                    parseContext);
        }

        return new MetadataListAndEmbeddedBytes(metadataList,
                parseContext.get(EmbeddedDocumentBytesHandler.class));
    }

    private ParseContext setupParseContext(FetchEmitTuple fetchEmitTuple)
            throws TikaConfigException {
        ParseContext parseContext = fetchEmitTuple.getParseContext();
        if (parseContext.get(HandlerConfig.class) == null) {
            parseContext.set(HandlerConfig.class, HandlerConfig.DEFAULT_HANDLER_CONFIG);
        }
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = parseContext.get(EmbeddedDocumentBytesConfig.class);
        if (embeddedDocumentBytesConfig == null) {
            //make sure there's one here -- or do we make this default in fetchemit tuple?
            parseContext.set(EmbeddedDocumentBytesConfig.class, EmbeddedDocumentBytesConfig.SKIP);
            return parseContext;
        }
        EmbeddedDocumentExtractorFactory factory = ((AutoDetectParser)autoDetectParser)
                .getAutoDetectParserConfig().getEmbeddedDocumentExtractorFactory();
        if (factory == null) {
            parseContext.set(EmbeddedDocumentExtractor.class, new RUnpackExtractor(parseContext,
                    RUnpackExtractorFactory.DEFAULT_MAX_EMBEDDED_BYTES_FOR_EXTRACTION));
        } else {
            if (! (factory instanceof EmbeddedDocumentByteStoreExtractorFactory)) {
                throw new TikaConfigException("EmbeddedDocumentExtractorFactory must be an " +
                        "instance of EmbeddedDocumentByteStoreExtractorFactory if you want" +
                        "to extract embedded bytes! I see this embedded doc factory: " +
                        factory.getClass() + "and a request: " +
                        embeddedDocumentBytesConfig);
            }
        }
        //TODO: especially clean this up.
        if (!StringUtils.isBlank(embeddedDocumentBytesConfig.getEmitter())) {
            parseContext.set(EmbeddedDocumentBytesHandler.class,
                    new EmittingEmbeddedDocumentBytesHandler(fetchEmitTuple, emitterManager));
        } else {
            parseContext.set(EmbeddedDocumentBytesHandler.class,
                    new BasicEmbeddedDocumentBytesHandler(
                    embeddedDocumentBytesConfig));
        }
        return parseContext;
    }

    private List<Metadata> parseConcatenated(FetchEmitTuple fetchEmitTuple,
                                             HandlerConfig handlerConfig, InputStream stream,
                                             Metadata metadata, ParseContext parseContext) {

        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(handlerConfig.getType(),
                        handlerConfig.getWriteLimit(), handlerConfig.isThrowOnWriteLimitReached(),
                        parseContext);

        ContentHandler handler = contentHandlerFactory.getNewContentHandler();
        parseContext.set(DocumentSelector.class, new DocumentSelector() {
            final int maxEmbedded = handlerConfig.maxEmbeddedResources;
            int embedded = 0;

            @Override
            public boolean select(Metadata metadata) {
                if (maxEmbedded < 0) {
                    return true;
                }
                return embedded++ < maxEmbedded;
            }
        });

        String containerException = null;
        long start = System.currentTimeMillis();
        preParse(fetchEmitTuple, stream, metadata, parseContext);
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
            LOG.warn("parse exception: " + fetchEmitTuple.getId(), e);
        } finally {
            metadata.add(TikaCoreProperties.TIKA_CONTENT, handler.toString());
            if (containerException != null) {
                metadata.add(TikaCoreProperties.CONTAINER_EXCEPTION, containerException);
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- parse only time: {} ms", System.currentTimeMillis() - start);
            }
        }
        return Collections.singletonList(metadata);
    }

    private List<Metadata> parseRecursive(FetchEmitTuple fetchEmitTuple,
                                          HandlerConfig handlerConfig, InputStream stream,
                                          Metadata metadata, ParseContext parseContext) {
        //Intentionally do not add the metadata filter here!
        //We need to let stacktraces percolate
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerConfig.getType(),
                        handlerConfig.getWriteLimit(), handlerConfig.isThrowOnWriteLimitReached(),
                        parseContext), handlerConfig.getMaxEmbeddedResources());

        long start = System.currentTimeMillis();

        preParse(fetchEmitTuple, stream, metadata, parseContext);
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
            LOG.warn("parse exception: " + fetchEmitTuple.getId(), e);
        } finally {
            if (LOG.isTraceEnabled()) {
                LOG.trace("timer -- parse only time: {} ms", System.currentTimeMillis() - start);
            }
        }
        return handler.getMetadataList();
    }

    private void preParse(FetchEmitTuple t, InputStream stream, Metadata metadata,
                          ParseContext parseContext) {
        TemporaryResources tmp = null;
        try {
            TikaInputStream tis = TikaInputStream.cast(stream);
            if (tis == null) {
                tis = TikaInputStream.get(stream, tmp, metadata);
            }
            _preParse(t, tis, metadata, parseContext);
        } finally {
            IOUtils.closeQuietly(tmp);
        }
        //do we want to filter the metadata to digest, length, content-type?
        writeIntermediate(t.getEmitKey(), metadata);
    }

    private void _preParse(FetchEmitTuple t, TikaInputStream tis, Metadata metadata,
                           ParseContext parseContext) {
        if (digester != null) {
            try {
                digester.digest(tis, metadata, parseContext);
            } catch (IOException e) {
                LOG.warn("problem digesting: " + t.getId(), e);
            }
        }
        try {
            MediaType mt = detector.detect(tis, metadata);
            metadata.set(Metadata.CONTENT_TYPE, mt.toString());
            metadata.set(TikaCoreProperties.CONTENT_TYPE_PARSER_OVERRIDE, mt.toString());
        } catch (IOException e) {
            LOG.warn("problem detecting: " + t.getId(), e);
        }
        EmbeddedDocumentBytesConfig embeddedDocumentBytesConfig = parseContext.get(EmbeddedDocumentBytesConfig.class);
        if (embeddedDocumentBytesConfig != null &&
                embeddedDocumentBytesConfig.isIncludeOriginal()) {
            EmbeddedDocumentBytesHandler embeddedDocumentByteStore = parseContext.get(EmbeddedDocumentBytesHandler.class);
            try (InputStream is = Files.newInputStream(tis.getPath())) {
                embeddedDocumentByteStore.add(0, metadata, is);
            } catch (IOException e) {
                LOG.warn("problem reading source file into embedded document byte store", e);
            }
        }
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
                    new UnsynchronizedByteArrayInputStream(bytes))) {
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
        //TODO allowed named configurations in tika config
        this.tikaConfig = new TikaConfig(tikaConfigPath);
        this.fetcherManager = FetcherManager.load(tikaConfigPath);
        //skip initialization of the emitters if emitting
        //from the pipesserver is turned off.
        if (maxForEmitBatchBytes > -1) {
            this.emitterManager = EmitterManager.load(tikaConfigPath);
        } else {
            LOG.debug("'maxForEmitBatchBytes' < 0. Not initializing emitters in PipesServer");
            this.emitterManager = null;
        }
        this.autoDetectParser = new AutoDetectParser(this.tikaConfig);
        if (((AutoDetectParser) autoDetectParser).getAutoDetectParserConfig()
                .getDigesterFactory() != null) {
            this.digester = ((AutoDetectParser) autoDetectParser).getAutoDetectParserConfig()
                    .getDigesterFactory().build();
            //override this value because we'll be digesting before parse
            ((AutoDetectParser) autoDetectParser).getAutoDetectParserConfig().getDigesterFactory()
                    .setSkipContainerDocument(true);
            //if the user hasn't configured an embedded document extractor, set up the
            // RUnpackExtractorFactory
            if (((AutoDetectParser) autoDetectParser).getAutoDetectParserConfig()
                    .getEmbeddedDocumentExtractorFactory() == null) {
                ((AutoDetectParser) autoDetectParser)
                        .getAutoDetectParserConfig().setEmbeddedDocumentExtractorFactory(
                                new RUnpackExtractorFactory());
            }
        }
        this.detector = ((AutoDetectParser) this.autoDetectParser).getDetector();
        this.rMetaParser = new RecursiveParserWrapper(autoDetectParser);
    }


    private void writeIntermediate(EmitKey emitKey, Metadata metadata) {
        try {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(metadata);
            }
            write(STATUS.INTERMEDIATE_RESULT, bos.toByteArray());
        } catch (IOException e) {
            LOG.error("problem writing intermediate data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(EmitData emitData) {
        try {
            UnsynchronizedByteArrayOutputStream bos = UnsynchronizedByteArrayOutputStream.builder().get();
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

    class MetadataListAndEmbeddedBytes {
        final List<Metadata> metadataList;
        final Optional<EmbeddedDocumentBytesHandler> embeddedDocumentBytesHandler;

        public MetadataListAndEmbeddedBytes(List<Metadata> metadataList,
                                            EmbeddedDocumentBytesHandler embeddedDocumentBytesHandler) {
            this.metadataList = metadataList;
            this.embeddedDocumentBytesHandler = Optional.ofNullable(embeddedDocumentBytesHandler);
        }

        public List<Metadata> getMetadataList() {
            return metadataList;
        }

        public EmbeddedDocumentBytesHandler getEmbeddedDocumentBytesHandler() {
            return embeddedDocumentBytesHandler.get();
        }

        /**
         * This tests whether there's any type of embedded document store
         * ...that, for example, may require closing at the end of the parse.
         *
         * @return
         */
        public boolean hasEmbeddedDocumentByteStore() {
            return embeddedDocumentBytesHandler.isPresent();
        }

        /**
         * If the intent is that the metadata and byte store be packaged in a zip
         * or similar and emitted via a single stream emitter.
         * <p>
         * This is basically a test that this is not an EmbeddedDocumentEmitterStore.
         *
         * @return
         */
        public boolean toBePackagedForStreamEmitter() {
            return !(embeddedDocumentBytesHandler.get() instanceof EmittingEmbeddedDocumentBytesHandler);
        }
    }
}
