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
package org.apache.tika.parser;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class OutOfProcessParser {

    private final ClassLoader loader;

    private final Queue<OutOfProcessClient> pool =
        new LinkedList<OutOfProcessClient>();

    private int poolSize = 5;

    public static void main(String[] args) throws Exception {
        OutOfProcessParser parser = new OutOfProcessParser(
                Thread.currentThread().getContextClassLoader());
        try {
            OutOfProcessClient client = parser.acquireClient();
            System.out.println(client.echo(EmptyParser.INSTANCE));
            parser.releaseClient(client);
        } finally {
            parser.close();
        }
    }

    public OutOfProcessParser(ClassLoader loader) {
        this.loader = loader;
    }

    public synchronized void close() {
        for (OutOfProcessClient client : pool) {
            client.close();
        }
        pool.clear();
    }

    private OutOfProcessClient acquireClient() throws IOException {
        OutOfProcessClient client = pool.poll();
        if (client == null) {
            client = new OutOfProcessClient(loader);
        }
        return client;
    }

    private synchronized void releaseClient(OutOfProcessClient client) {
        if (pool.size() < poolSize) {
            pool.offer(client);
        } else {
            client.close();
        }
    }

}
