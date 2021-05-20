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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
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
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
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

    public static final byte READY = 1;

    public static final byte CALL = 2;

    public static final byte PING = 3;

    public static final byte FAILED_TO_START = 4;

    public static final byte PARSE_SUCCESS = 5;

    /**
     * This will return the parse exception stack trace
     */
    public static final byte PARSE_EXCEPTION_NO_EMIT = 6;

    /**
     * This will return the metadata list
     */
    public static final byte PARSE_EXCEPTION_EMIT = 7;

    public static final byte EMIT_SUCCESS = 8;

    public static final byte EMIT_SUCCESS_PARSE_EXCEPTION = 9;

    public static final byte EMIT_EXCEPTION = 10;

    public static final byte NO_EMITTER_FOUND = 11;

    public static final byte OOM = 12;

    public static final byte TIMEOUT = 13;

    public static final byte EMPTY_OUTPUT = 14;


    private final Object[] lock = new Object[0];
    private final Path tikaConfigPath;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final long maxExtractSizeToReturn;
    private final long serverParseTimeoutMillis;
    private final long serverWaitTimeoutMillis;
    private Parser parser;
    private TikaConfig tikaConfig;
    private FetcherManager fetcherManager;
    private EmitterManager emitterManager;
    private volatile boolean parsing;
    private volatile long since;

    //logging is fussy...the logging frameworks grab stderr and stdout
    //before we can redirect.  slf4j complains on stderr, log4j2 unconfigured writes to stdout
    //We can add logging later but it has to be done carefully...
    public PipesServer(Path tikaConfigPath, InputStream in, PrintStream out,
                       long maxExtractSizeToReturn,
                       long serverParseTimeoutMillis, long serverWaitTimeoutMillis)
            throws IOException, TikaException, SAXException {
        this.tikaConfigPath = tikaConfigPath;
        this.input = new DataInputStream(in);
        this.output = new DataOutputStream(out);
        this.maxExtractSizeToReturn = maxExtractSizeToReturn;
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
            LOG.error("couldn't initialize parser", t);
            try {
                output.writeByte(FAILED_TO_START);
                output.flush();
            } catch (IOException e) {
                LOG.warn("couldn't notify of failure to start", e);
            }
            return;
        }
        //main loop
        try {
            output.write(READY);
            output.flush();
            while (true) {
                int request = input.read();
                if (request == -1) {
                    exit(1);
                } else if (request == PING) {
                    output.writeByte(PING);
                    output.flush();
                } else if (request == CALL) {
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


    private void emit(EmitData emitData, String parseExceptionStack) {
        Emitter emitter = emitterManager.getEmitter(emitData.getEmitKey().getEmitterName());
        if (emitter == null) {
            write(NO_EMITTER_FOUND, new byte[0]);
            return;
        }
        try {
            emitter.emit(emitData.getEmitKey().getEmitKey(), emitData.getMetadataList());
        } catch (IOException | TikaEmitterException e) {
            String msg = ExceptionUtils.getStackTrace(e);
            byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
            //for now, we're hiding the parse exception if there was also an emit exception
            write(EMIT_EXCEPTION, bytes);
            return;
        }
        if (StringUtils.isBlank(parseExceptionStack)) {
            write(EMIT_SUCCESS);
        } else {
            write(EMIT_SUCCESS_PARSE_EXCEPTION, parseExceptionStack.getBytes(StandardCharsets.UTF_8));
        }
    }


    private void parseOne() throws FetchException {
        synchronized (lock) {
            parsing = true;
            since = System.currentTimeMillis();
        }
        try {
            actuallyParse();
        } finally {
            synchronized (lock) {
                parsing = false;
                since = System.currentTimeMillis();
            }
        }
    }

    public void actuallyParse() throws FetchException {
        FetchEmitTuple t = readFetchEmitTuple();
        List<Metadata> metadataList = null;

        Fetcher fetcher = getFetcher(t.getFetchKey().getFetcherName());

        Metadata metadata = new Metadata();
        try (InputStream stream = fetcher.fetch(t.getFetchKey().getFetchKey(), metadata)) {
            metadataList = parseMetadata(t, stream, metadata);
        } catch (SecurityException e) {
            throw e;
        } catch (TikaException | IOException e) {
            LOG.warn("fetch exception", e);
            throw new FetchException(e);
        } catch (OutOfMemoryError e) {
            handleOOM(e);
        }
        if (metadataIsEmpty(metadataList)) {
            write(EMPTY_OUTPUT);
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
            if (emitData.getEstimatedSizeBytes() >= maxExtractSizeToReturn) {
                emit(emitData, stack);
            } else {
                write(emitData, stack);
            }
        } else {
            write(PARSE_EXCEPTION_NO_EMIT, stack.getBytes(StandardCharsets.UTF_8));
        }

    }

    private Fetcher getFetcher(String fetcherName) throws FetchException {
        try {
            return fetcherManager.getFetcher(fetcherName);
        } catch (TikaException | IOException e) {
            LOG.error("can't load fetcher", e);
            throw new FetchException(e);
        }
    }

    private void handleOOM(OutOfMemoryError oom) {
        try {
            output.writeByte(OOM);
            output.flush();
        } catch (IOException e) {
            //swallow at this point
        }
        LOG.error("oom", oom);
        exit(1);
    }

    private List<Metadata> parseMetadata(FetchEmitTuple fetchEmitTuple, InputStream stream,
                                         Metadata metadata) {
        HandlerConfig handlerConfig = fetchEmitTuple.getHandlerConfig();
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(handlerConfig.getType(),
                    handlerConfig.getWriteLimit()),
                handlerConfig.getMaxEmbeddedResources(),
                tikaConfig.getMetadataFilter());
        ParseContext parseContext = new ParseContext();
        FetchKey fetchKey = fetchEmitTuple.getFetchKey();
        try {
            parser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            LOG.warn("sax problem:" + fetchEmitTuple.getId(), e);
        } catch (EncryptedDocumentException e) {
            LOG.warn("encrypted document:" + fetchEmitTuple.getId(), e);
        } catch (SecurityException e) {
            LOG.warn("security exception:" + fetchEmitTuple.getId(), e);
            throw e;
        } catch (Exception e) {
            LOG.warn("exception: " + fetchEmitTuple.getId(), e);
        } catch (OutOfMemoryError e) {
            //TODO, maybe return file type gathered so far and then crash?
            //LOG.error("oom: " + fetchKey.getFetchKey());
            throw e;
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
        LOG.warn("exiting: {}", exitCode);
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
        Parser autoDetectParser = new AutoDetectParser(this.tikaConfig);
        this.parser = new RecursiveParserWrapper(autoDetectParser);

    }

    private static class FetchException extends IOException {
        FetchException(Throwable t) {
            super(t);
        }
    }

    private void write(EmitData emitData, String stack) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(emitData);
            }
            write(PARSE_SUCCESS, bos.toByteArray());
        } catch (IOException e) {
            LOG.error("problem writing emit data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(byte status, byte[] bytes) {
        try {
            int len = bytes.length;
            output.write(status);
            output.writeInt(len);
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }

    private void write(byte status) {
        try {
            output.write(status);
            output.flush();
        } catch (IOException e) {
            LOG.error("problem writing data (forking process shutdown?)", e);
            exit(1);
        }
    }

}
