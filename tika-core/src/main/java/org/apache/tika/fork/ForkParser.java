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
package org.apache.tika.fork;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.TeeContentHandler;

public class ForkParser implements Parser, Closeable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -4962742892274663950L;

    //these are used by the legacy usage
    private final ClassLoader loader;
    private final Parser parser;

    //these are used when the server builds a parser via a directory
    //of jars, not via legacy bootstrap etc.
    private final Path tikaBin;
    private final ParserFactoryFactory parserFactoryFactory;
    private final Queue<ForkClient> pool = new LinkedList<>();
    /**
     * Java command line
     */
    private List<String> java = Arrays.asList("java", "-Xmx32m", "-Djava.awt.headless=true");
    /**
     * Process pool size
     */
    @Field
    private int poolSize = 5;
    private int currentlyInUse = 0;
    @Field
    private long serverPulseMillis = 1000;

    @Field
    private long serverParseTimeoutMillis = 60000;

    @Field
    private long serverWaitTimeoutMillis = 60000;

    @Field
    private int maxFilesProcessedPerClient = -1;

    /**
     * If you have a directory with, say, tike-app.jar and you want the
     * forked process/server to build a parser
     * and run it from that -- so that you can keep all of those dependencies out of
     * your client code, use this initializer.
     *
     * @param tikaBin        directory containing the tika-app.jar or similar --
     *                       full jar including tika-core and all
     *                       desired parsers and dependencies
     * @param factoryFactory
     */
    public ForkParser(Path tikaBin, ParserFactoryFactory factoryFactory) {
        loader = null;
        parser = null;
        this.tikaBin = tikaBin;
        this.parserFactoryFactory = factoryFactory;
    }

    /**
     * <b>EXPERT</b>
     *
     * @param tikaBin              directory containing the tika-app.jar or similar
     *                             -- full jar including tika-core and all
     *                             desired parsers and dependencies
     * @param parserFactoryFactory -- the factory to use to generate the parser factory
     *                             in the forked process/server
     * @param classLoader          to use for all classes besides the parser in the
     *                             forked process/server
     */
    public ForkParser(Path tikaBin, ParserFactoryFactory parserFactoryFactory,
                      ClassLoader classLoader) {
        parser = null;
        loader = classLoader;
        this.tikaBin = tikaBin;
        this.parserFactoryFactory = parserFactoryFactory;
    }

    /**
     * @param loader The ClassLoader to use
     * @param parser the parser to delegate to. This one cannot be another ForkParser
     */
    public ForkParser(ClassLoader loader, Parser parser) {
        if (parser instanceof ForkParser) {
            throw new IllegalArgumentException(
                    "The underlying parser of a ForkParser should not be a ForkParser, " +
                            "but a specific implementation.");
        }
        this.tikaBin = null;
        this.parserFactoryFactory = null;
        this.loader = loader;
        this.parser = parser;
    }

    public ForkParser(ClassLoader loader) {
        this(loader, new AutoDetectParser());
    }

    public ForkParser() {
        this(ForkParser.class.getClassLoader());
    }

    /**
     * Returns the size of the process pool.
     *
     * @return process pool size
     */
    public synchronized int getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the size of the process pool.
     *
     * @param poolSize process pool size
     */
    public synchronized void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /**
     * Sets the command used to start the forked server process.
     * The arguments "-jar" and "/path/to/bootstrap.jar"
     * or "-cp" and "/path/to/tika_bin" are
     * appended to the given command when starting the process.
     * The default setting is {"java", "-Xmx32m"}.
     * <p/>
     * Creates a defensive copy.
     *
     * @param java java command line
     */
    public void setJavaCommand(List<String> java) {
        this.java = new ArrayList<>(java);
    }

    /**
     * Returns the command used to start the forked server process.
     * <p/>
     * Returned list is unmodifiable.
     *
     * @return java command line args
     */
    public List<String> getJavaCommandAsList() {
        return Collections.unmodifiableList(java);
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    /**
     * This sends the objects to the server for parsing, and the server via
     * the proxies acts on the handler as if it were updating it directly.
     * <p>
     * If using a {@link org.apache.tika.parser.RecursiveParserWrapper}, there are two options:
     * </p>
     * <p>
     *     <ol>
     *         <li>Send in a class that extends
     *              {@link org.apache.tika.sax.RecursiveParserWrapperHandler},
     *              and the server will proxy back the data as best it can[0].</li>
     *         <li>Send in a class that extends {@link AbstractRecursiveParserWrapperHandler}
     *              and the server will act on the class but not proxy back the data.  This
     *              can be used, for example, if all you want to do is write to disc, extend
     *              {@link AbstractRecursiveParserWrapperHandler} to write to disc when
     *             {@link AbstractRecursiveParserWrapperHandler#endDocument(ContentHandler,
     *             Metadata)}
     *              is called, and the server will take care of the writing via the handler.</li>
     *     </ol>
     * </p>
     * <p>
     *     <b>NOTE:</b>[0] &quot;the server will proxy back the data as best it can&quot;.
     *     If the handler implements Serializable and is actually serializable, the
     *     server will send it and the
     *     {@link Metadata} back upon
     *     {@link org.apache.tika.sax.RecursiveParserWrapperHandler#
     *     endEmbeddedDocument(ContentHandler, Metadata)}
     *     or {@link org.apache.tika.sax.RecursiveParserWrapperHandler#
     *     endEmbeddedDocument(ContentHandler, Metadata)}.
     *     If the handler does not implement {@link java.io.Serializable} or if there is a
     *     {@link java.io.NotSerializableException} thrown during serialization, the server will
     *     call {@link ContentHandler#toString()} on the ContentHandler and set that value with the
     *     {@link TikaCoreProperties#TIKA_CONTENT} key and then
     *     serialize and proxy that data back.
     * </p>
     *
     * @param stream   the document stream (input)
     * @param handler  handler for the XHTML SAX events (output)
     * @param metadata document metadata (input and output)
     * @param context  parse context
     * @throws IOException
     * @throws SAXException
     * @throws TikaException
     */
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        if (stream == null) {
            throw new NullPointerException("null stream");
        }

        Throwable t;

        boolean alive = false;
        ForkClient client = acquireClient();
        try {
            ContentHandler tee =
                    (handler instanceof AbstractRecursiveParserWrapperHandler) ? handler :
                            new TeeContentHandler(handler, new MetadataContentHandler(metadata));

            t = client.call("parse", stream, tee, metadata, context);
            alive = true;
        } catch (TikaException te) {
            // Problem occurred on our side
            alive = true;
            throw te;
        } catch (IOException e) {
            // Problem occurred on the other side
            throw new TikaException("Failed to communicate with a forked parser process." +
                    " The process has most likely crashed due to some error" +
                    " like running out of memory. A new process will be" +
                    " started for the next parsing request.", e);
        } finally {
            releaseClient(client, alive);
        }

        if (t instanceof IOException) {
            throw (IOException) t;
        } else if (t instanceof SAXException) {
            throw (SAXException) t;
        } else if (t instanceof TikaException) {
            throw (TikaException) t;
        } else if (t != null) {
            throw new TikaException("Unexpected error in forked server process", t);
        }
    }

    public synchronized void close() {
        for (ForkClient client : pool) {
            client.close();
        }
        pool.clear();
        poolSize = 0;
    }

    private synchronized ForkClient acquireClient() throws IOException, TikaException {
        while (true) {
            ForkClient client = pool.poll();

            // Create a new process if there's room in the pool
            if (client == null && currentlyInUse < poolSize) {
                client = newClient();
            }

            // Ping the process, and get rid of it if it's inactive
            if (client != null && !client.ping()) {
                client.close();
                client = null;
            }

            if (client != null) {
                currentlyInUse++;
                return client;
            } else if (currentlyInUse >= poolSize) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new TikaException("Interrupted while waiting for a fork parser", e);
                }
            }
        }
    }

    private ForkClient newClient() throws IOException, TikaException {
        TimeoutLimits timeoutLimits = new TimeoutLimits(serverPulseMillis, serverParseTimeoutMillis,
                serverWaitTimeoutMillis);
        if (loader == null && parser == null && tikaBin != null && parserFactoryFactory != null) {
            return new ForkClient(tikaBin, parserFactoryFactory, java, timeoutLimits);
        } else if (loader != null && parser != null && tikaBin == null &&
                parserFactoryFactory == null) {
            return new ForkClient(loader, parser, java, timeoutLimits);
        } else if (loader != null && parser == null && tikaBin != null &&
                parserFactoryFactory != null) {
            return new ForkClient(tikaBin, parserFactoryFactory, loader, java, timeoutLimits);
        } else {
            //TODO: make this more useful
            throw new IllegalStateException("Unexpected combination of state items");
        }
    }

    private synchronized void releaseClient(ForkClient client, boolean alive) {
        currentlyInUse--;
        if (currentlyInUse + pool.size() < poolSize && alive) {
            if (maxFilesProcessedPerClient > 0 &&
                    client.getFilesProcessed() >= maxFilesProcessedPerClient) {
                client.close();
            } else {
                pool.offer(client);
            }
            notifyAll();
        } else {
            client.close();
        }
    }

    /**
     * The amount of time in milliseconds that the server
     * should wait before checking to see if the parse has timed out
     * or if the wait has timed out
     * The default is 5 seconds.
     *
     * @param serverPulseMillis milliseconds to sleep before checking if there has been any activity
     */
    public void setServerPulseMillis(long serverPulseMillis) {
        this.serverPulseMillis = serverPulseMillis;
    }

    /**
     * The maximum amount of time allowed for the server to try to parse a file.
     * If more than this time elapses, the server shuts down, and the ForkParser
     * throws an exception.
     *
     * @param serverParseTimeoutMillis
     */
    public void setServerParseTimeoutMillis(long serverParseTimeoutMillis) {
        this.serverParseTimeoutMillis = serverParseTimeoutMillis;
    }

    /**
     * The maximum amount of time allowed for the server to wait for a new request to parse
     * a file.  The server will shutdown after this amount of time, and a new server will have
     * to be started by a new client.
     *
     * @param serverWaitTimeoutMillis
     */
    public void setServerWaitTimeoutMillis(long serverWaitTimeoutMillis) {
        this.serverWaitTimeoutMillis = serverWaitTimeoutMillis;
    }

    /**
     * If there is a slowly building memory leak in one of the parsers,
     * it is useful to set a limit on the number of files processed
     * by a server before it is shutdown and restarted. Default value is -1.
     *
     * @param maxFilesProcessedPerClient maximum number of files that a server can handle
     *                                   before the parser shuts down a client and creates
     *                                   a new process. If set to -1, the server is never restarted
     *                                   because of the number of files handled.
     */
    public void setMaxFilesProcessedPerServer(int maxFilesProcessedPerClient) {
        this.maxFilesProcessedPerClient = maxFilesProcessedPerClient;
    }

}
