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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class PipesParser implements Closeable {


    private final PipesConfig pipesConfig;
    private final List<PipesClient> clients = new ArrayList<>();
    private final ArrayBlockingQueue<PipesClient> clientQueue ;


    public PipesParser(PipesConfig pipesConfig) {
        this.pipesConfig = pipesConfig;
        this.clientQueue = new ArrayBlockingQueue<>(pipesConfig.getNumClients());
        for (int i = 0; i < pipesConfig.getNumClients(); i++) {
            PipesClient client = new PipesClient(pipesConfig);
            clientQueue.offer(client);
            clients.add(client);
        }
    }

    public PipesResult parse(FetchEmitTuple t) throws InterruptedException,
            PipesException, IOException {
        PipesClient client = null;
        try {
            client = clientQueue.poll(pipesConfig.getMaxWaitForClientMillis(),
                    TimeUnit.MILLISECONDS);
            if (client == null) {
                return PipesResult.CLIENT_UNAVAILABLE_WITHIN_MS;
            }
            return client.process(t);
        } finally {
            if (client != null) {
                clientQueue.offer(client);
            }
        }
    }

    @Override
    public void close() throws IOException {
        List<IOException> exceptions = new ArrayList<>();
        for (PipesClient pipesClient : clients) {
            try {
                pipesClient.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
    }
}
