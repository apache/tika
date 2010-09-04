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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ForkParser extends DelegatingParser {

    private final ClassLoader loader;

    private final Queue<ForkClient> pool =
        new LinkedList<ForkClient>();

    private int poolSize = 5;

    public static void main(String[] args) throws Exception {
        ForkParser parser = new ForkParser(
                Thread.currentThread().getContextClassLoader());
        try {
            ParseContext context = new ParseContext();
            context.set(Parser.class, new AutoDetectParser());
            parser.parse(null, null, null, context);
        } finally {
            parser.close();
        }
    }

    public ForkParser(ClassLoader loader) {
        this.loader = loader;
    }

    /**
     * 
     */
    @Override
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        ForkClient client = acquireClient();
        try {
            System.out.println(client.echo(getDelegateParser(context)));
        } finally {
            releaseClient(client);
        }
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
            client = new ForkClient(loader);
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
