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

import java.io.ByteArrayInputStream;
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
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ForkParser implements Parser {

    private final ClassLoader loader;

    private final Parser parser;

    private final Queue<ForkClient> pool =
        new LinkedList<ForkClient>();

    private int poolSize = 5;

    public static void main(String[] args) throws Exception {
        ForkParser parser = new ForkParser(
                Thread.currentThread().getContextClassLoader(),
                new AutoDetectParser());
        try {
            InputStream stream =
                new ByteArrayInputStream("Hello, World!".getBytes());
            ParseContext context = new ParseContext();
            parser.parse(
                    stream, new WriteOutContentHandler(System.out),
                    new Metadata(), context);
        } finally {
            parser.close();
        }
    }

    public ForkParser(ClassLoader loader, Parser parser) {
        this.loader = loader;
        this.parser = parser;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ForkClient client = acquireClient();
        try {
            client.call("parse", stream, handler, metadata, context);
        } finally {
            releaseClient(client);
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
        if (client == null) {
            client = new ForkClient(loader, parser);
        }
        return client;
    }

    private synchronized void releaseClient(ForkClient client) {
        if (pool.size() < poolSize) {
            pool.offer(client);
        } else {
            client.close();
        }
    }

}
