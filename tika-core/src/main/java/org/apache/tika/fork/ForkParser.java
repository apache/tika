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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ForkParser implements Parser {

    /** Serial version UID */
    private static final long serialVersionUID = -4962742892274663950L;

    private final ClassLoader loader;

    private final Parser parser;

    /** Java command line */
    private String java = "java -Xmx32m";

    /** Process pool size */
    private int poolSize = 5;

    private final Queue<ForkClient> pool =
        new LinkedList<ForkClient>();

    public ForkParser(ClassLoader loader, Parser parser) {
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
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the size of the process pool.
     *
     * @param poolSize process pool size
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /**
     * Returns the command used to start the forked server process.
     *
     * @return java command line
     */
    public String getJavaCommand() {
        return java;
    }

    /**
     * Sets the command used to start the forked server process.
     * The given command line is split on whitespace and the arguments
     * "-jar" and "/path/to/bootstrap.jar" are appended to it when starting
     * the process. The default setting is "java -Xmx32m".
     *
     * @param java java command line
     */
    public void setJavaCommand(String java) {
        this.java = java;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Throwable t;

        boolean alive = false;
        ForkClient client = acquireClient();
        try {
            t = client.call("parse", stream, handler, metadata, context);
            alive = true;
        } catch (IOException e) {
            throw new TikaException(
                    "Failed to communicate with a forked parser process."
                    + " The process has most likely crashed due to some error"
                    + " like running out of memory. A new process will be"
                    + " started for the next parsing request.", e);
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
            throw new TikaException(
                    "Unexpected error in forked server process", t);
        }
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    public synchronized void close() {
        for (ForkClient client : pool) {
            client.close();
        }
        pool.clear();
        poolSize = 0;
    }

    private synchronized ForkClient acquireClient()
            throws IOException {
        ForkClient client = pool.poll();
        if (client == null || !client.ping()) {
            client = new ForkClient(loader, parser, java);
        }
        return client;
    }

    private synchronized void releaseClient(ForkClient client, boolean alive) {
        if (pool.size() < poolSize && alive) {
            pool.offer(client);
        } else {
            client.close();
        }
    }

}
