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
package org.apache.tika.server.client;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.CallablePipesIterator;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TikaClientCLI {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaClientCLI.class);
    private static final int QUEUE_SIZE = 10000;

    public static void main(String[] args) throws Exception {
        Path tikaConfigPath = Paths.get(args[0]);
        TikaClientCLI cli = new TikaClientCLI();
        cli.execute(tikaConfigPath);
    }

    private void execute(Path tikaConfigPath)
            throws TikaException, IOException, SAXException {
        TikaServerClientConfig clientConfig = TikaServerClientConfig.build(tikaConfigPath);

        ExecutorService executorService =
                Executors.newFixedThreadPool(clientConfig.getNumThreads() + 1);

        ExecutorCompletionService<Long> completionService =
                new ExecutorCompletionService<>(executorService);

        final PipesIterator pipesIterator = PipesIterator.build(tikaConfigPath);

        final ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);

        completionService.submit(new CallablePipesIterator(pipesIterator, queue));

        if (clientConfig.getTikaEndpoints().size() == clientConfig.getNumThreads()) {
            logDiffSizes(clientConfig.getTikaEndpoints().size(), clientConfig.getNumThreads());
            for (int i = 0; i < clientConfig.getNumThreads(); i++) {
                TikaClient client =
                        TikaClient.get(clientConfig.getHttpClientFactory(),
                                Collections.singletonList(clientConfig.getTikaEndpoints().get(i)));
                completionService.submit(new FetchWorker(queue, client,
                        clientConfig.getMaxWaitMillis()));
            }
        } else {
            for (int i = 0; i < clientConfig.getNumThreads(); i++) {
                TikaClient client = TikaClient.get(clientConfig.getHttpClientFactory(),
                        clientConfig.getTikaEndpoints());
                completionService.submit(new FetchWorker(queue, client,
                        clientConfig.getMaxWaitMillis()));
            }
        }

        int finished = 0;
        while (finished < clientConfig.getNumThreads() + 1) {
            Future<Long> future = null;
            try {
                future = completionService.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                //stop the world
                LOGGER.error("", e);
                throw new RuntimeException(e);
            }
            if (future != null) {
                finished++;
                try {
                    future.get();
                } catch (InterruptedException | ExecutionException e) {
                    //stop the world
                    LOGGER.error("critical main loop failure", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void logDiffSizes(int servers, int numThreads) {
        LOGGER.info("tika server count ({}) != numThreads ({}). " +
                "Each client will randomly select a server from this list", servers, numThreads);
    }

    private class FetchWorker implements Callable<Long> {
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final TikaClient client;

        private final long maxWaitMs;

        public FetchWorker(ArrayBlockingQueue<FetchEmitTuple> queue,
                           TikaClient client, long maxWaitMs) {
            this.queue = queue;
            this.client = client;
            this.maxWaitMs = maxWaitMs;
        }

        @Override
        public Long call() throws Exception {
            while (true) {

                FetchEmitTuple t = queue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (t == null) {
                    throw new TimeoutException("exceeded maxWaitMs");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    //potentially blocks forever
                    queue.put(PipesIterator.COMPLETED_SEMAPHORE);
                    return 1l;
                }
                try {
                    LOGGER.debug("about to parse: {}", t.getFetchKey());
                    client.parse(t);
                } catch (IOException | TikaException e) {
                    LOGGER.warn(t.getFetchKey().toString(), e);
                }
            }
        }
    }
}
