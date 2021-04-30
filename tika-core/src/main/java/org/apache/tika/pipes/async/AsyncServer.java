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
package org.apache.tika.pipes.async;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.xml.sax.SAXException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.Fetcher;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.utils.StringUtils;

public class AsyncServer implements Runnable {

    public static final byte ERROR = -1;

    public static final byte DONE = 0;

    public static final byte CALL = 1;

    public static final byte PING = 2;

    public static final byte RESOURCE = 3;

    public static final byte READY = 4;

    public static final byte FAILED_TO_START = 5;

    public static final byte OOM = 6;

    public static final byte TIMEOUT = 7;

    private final Object[] lock = new Object[0];
    private final Path tikaConfigPath;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final long serverParseTimeoutMillis;
    private final long serverWaitTimeoutMillis;
    private Parser parser;
    private TikaConfig tikaConfig;
    private FetcherManager fetcherManager;
    private volatile boolean parsing;
    private volatile long since;

    //logging is fussy...the logging frameworks grab stderr/stdout
    //before we can redirect.  slf4j complains on stderr, log4j2 unconfigured writes to stdout
    //We can add logging later but it has to be done carefully...
    public AsyncServer(Path tikaConfigPath, InputStream in, PrintStream out,
                       long serverParseTimeoutMillis, long serverWaitTimeoutMillis)
            throws IOException, TikaException, SAXException {
        this.tikaConfigPath = tikaConfigPath;
        this.input = new DataInputStream(in);
        this.output = new DataOutputStream(out);
        this.serverParseTimeoutMillis = serverParseTimeoutMillis;
        this.serverWaitTimeoutMillis = serverWaitTimeoutMillis;
        this.parsing = false;
        this.since = System.currentTimeMillis();
    }


    public static void main(String[] args) throws Exception {
        Path tikaConfig = Paths.get(args[0]);
        long serverParseTimeoutMillis = Long.parseLong(args[1]);
        long serverWaitTimeoutMillis = Long.parseLong(args[2]);

        AsyncServer server =
                new AsyncServer(tikaConfig, System.in, System.out, serverParseTimeoutMillis,
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
                        err("server timeout");
                        System.exit(1);
                    } else if (!parsing && serverWaitTimeoutMillis > 0 &&
                            elapsed > serverWaitTimeoutMillis) {
                        err("closing down from inactivity");
                        System.exit(0);
                    }
                }
                Thread.sleep(250);
            }
        } catch (InterruptedException e) {
            //swallow
        }
    }

    private void err(String msg) {
        System.err.println(msg);
        System.err.flush();
    }

    private void err(Throwable t) {
        t.printStackTrace();
        System.err.flush();
    }

    public void processRequests() {
        //initialize
        try {
            initializeParser();
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.flush();
            try {
                output.writeByte(FAILED_TO_START);
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.err.flush();
            }
            return;
        }
        //main loop
        try {
            while (true) {
                int request = input.read();
                if (request == -1) {
                    break;
                } else if (request == PING) {
                    output.writeByte(PING);
                    output.flush();
                } else if (request == CALL) {
                    EmitData emitData = parseOne();
                    write(emitData);
                } else {
                    throw new IllegalStateException("Unexpected request");
                }
                output.flush();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.err.flush();
    }

    private void write(EmitData emitData) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bos)) {
                objectOutputStream.writeObject(emitData);
            }
            byte[] bytes = bos.toByteArray();
            int len = bytes.length;
            output.write(READY);
            output.writeInt(len);
            output.write(bytes);
            output.flush();
        } catch (IOException e) {
            err(e);
            //LOG.error("problem writing emit data", e);
            exit(1);
        }
    }

    private EmitData parseOne() throws FetchException {
        synchronized (lock) {
            parsing = true;
            since = System.currentTimeMillis();
        }
        try {
            FetchEmitTuple t = readFetchEmitTuple();
            Metadata userMetadata = t.getMetadata();
            Metadata metadata = new Metadata();
            String fetcherName = t.getFetchKey().getFetcherName();
            String fetchKey = t.getFetchKey().getFetchKey();
            List<Metadata> metadataList = null;
            Fetcher fetcher = null;
            try {
                fetcher = fetcherManager.getFetcher(fetcherName);
            } catch (TikaException | IOException e) {
                err(e);
                //LOG.error("can't get fetcher", e);
                throw new FetchException(e);
            }

            try (InputStream stream = fetcher.fetch(fetchKey, metadata)) {
                metadataList = parseMetadata(t, stream, metadata);
            } catch (SecurityException e) {
                throw e;
            } catch (TikaException | IOException e) {
                err(e);
                //LOG.error("problem reading from fetcher", e);
                throw new FetchException(e);
            } catch (OutOfMemoryError e) {
                //LOG.error("oom", e);
                handleOOM(e);
            }

            injectUserMetadata(userMetadata, metadataList);
            EmitKey emitKey = t.getEmitKey();
            if (StringUtils.isBlank(emitKey.getEmitKey())) {
                emitKey = new EmitKey(emitKey.getEmitterName(), fetchKey);
                t.setEmitKey(emitKey);
            }
            return new EmitData(t.getEmitKey(), metadataList);
        } finally {
            synchronized (lock) {
                parsing = false;
                since = System.currentTimeMillis();
            }
        }
    }

    private void handleOOM(OutOfMemoryError oom) {
        try {
            output.writeByte(OOM);
            output.flush();
        } catch (IOException e) {
            //swallow at this point
        }
        err(oom);
        exit(1);
    }

    private List<Metadata> parseMetadata(FetchEmitTuple fetchEmitTuple, InputStream stream,
                                         Metadata metadata) {
        //make these configurable
        BasicContentHandlerFactory.HANDLER_TYPE type = BasicContentHandlerFactory.HANDLER_TYPE.TEXT;


        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(type,
                    fetchEmitTuple.getHandlerConfig().getWriteLimit()),
                fetchEmitTuple.getHandlerConfig().getMaxEmbeddedResources(),
                tikaConfig.getMetadataFilter());
        ParseContext parseContext = new ParseContext();
        FetchKey fetchKey = fetchEmitTuple.getFetchKey();
        try {
            parser.parse(stream, handler, metadata, parseContext);
        } catch (SAXException e) {
            err(e);
            //LOG.warn("problem:" + fetchKey.getFetchKey(), e);
        } catch (EncryptedDocumentException e) {
            err(e);
            //LOG.warn("encrypted:" + fetchKey.getFetchKey(), e);
        } catch (SecurityException e) {
            err(e);
            //LOG.warn("security exception: " + fetchKey.getFetchKey());
            throw e;
        } catch (Exception e) {
            err(e);
            //LOG.warn("exception: " + fetchKey.getFetchKey());
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
            err(e);
            //LOG.error("problem reading tuple", e);
            System.exit(1);
        } catch (ClassNotFoundException e) {
            err(e);
            //LOG.error("can't find class?!", e);
            System.exit(1);
        }
        //unreachable, no?!
        return null;
    }

    private void initializeParser() throws TikaException, IOException, SAXException {
        //TODO allowed named configurations in tika config
        this.tikaConfig = new TikaConfig(tikaConfigPath);
        this.fetcherManager = FetcherManager.load(tikaConfigPath);
        Parser autoDetectParser = new AutoDetectParser(this.tikaConfig);
        this.parser = new RecursiveParserWrapper(autoDetectParser);

    }

    private static class FetchException extends IOException {
        FetchException(Throwable t) {
            super(t);
        }
    }
}
