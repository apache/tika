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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

public class TikaClientCLI {

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaClientCLI.class);
    private static final int QUEUE_SIZE = 10000;

    private final long maxWaitMs = 300000;

    public static void main(String[] args) throws Exception {
        //TODO -- add an actual commandline,
        Path tikaConfigPath = Paths.get(args[0]);
        int numThreads = Integer.parseInt(args[1]);
        List<String> tikaServerUrls = Arrays.asList(args[2].split(","));
        TikaClientCLI cli = new TikaClientCLI();
        cli.execute(tikaConfigPath, tikaServerUrls, numThreads);
    }

    private void execute(Path tikaConfigPath, List<String> tikaServerUrls, int numThreads)
            throws TikaException, IOException, SAXException {
        TikaConfig config = new TikaConfig(tikaConfigPath);

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads + 1);
        ExecutorCompletionService<Integer> completionService =
                new ExecutorCompletionService<>(executorService);
        final PipesIterator pipesIterator =
                PipesIterator.build(tikaConfigPath);
        final ArrayBlockingQueue<FetchEmitTuple> queue =
                new ArrayBlockingQueue<>(QUEUE_SIZE);

        completionService.submit(new PipesIteratorWrapper(pipesIterator, queue, numThreads));
        if (tikaServerUrls.size() == numThreads) {
            logDiffSizes(tikaServerUrls.size(), numThreads);
            for (int i = 0; i < numThreads; i++) {
                TikaClient client =
                        TikaClient.get(config, Collections.singletonList(tikaServerUrls.get(i)));
                completionService.submit(new FetchWorker(queue, client));
            }
        } else {
            for (int i = 0; i < numThreads; i++) {
                TikaClient client = TikaClient.get(config, tikaServerUrls);
                completionService.submit(new FetchWorker(queue, client));
            }
        }

        int finished = 0;
        while (finished < numThreads + 1) {
            Future<Integer> future = null;
            try {
                future = completionService.poll(maxWaitMs, TimeUnit.MILLISECONDS);
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
                    LOGGER.error("", e);
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void logDiffSizes(int servers, int numThreads) {
        LOGGER.info("tika server count ({}) != numThreads ({}). " +
                "Each client will randomly select a server from this list", servers, numThreads);
    }

    private class AsyncFetchWorker implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final TikaClient client;

        public AsyncFetchWorker(ArrayBlockingQueue<FetchEmitTuple> queue, TikaClient client) {
            this.queue = queue;
            this.client = client;
        }

        @Override
        public Integer call() throws Exception {
            List<FetchEmitTuple> localCache = new ArrayList<>();
            while (true) {

                FetchEmitTuple t = queue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (t == null) {
                    send(localCache);
                    throw new TimeoutException("exceeded maxWaitMs");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    send(localCache);
                    return 1;
                }
                if (localCache.size() > 20) {
                    LOGGER.debug("about to send: {}", localCache.size());
                    send(localCache);
                    localCache.clear();
                }
                localCache.add(t);
            }
        }

        private void send(List<FetchEmitTuple> localCache) {

        }
    }

    private class FetchWorker implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final TikaClient client;

        public FetchWorker(ArrayBlockingQueue<FetchEmitTuple> queue, TikaClient client) {
            this.queue = queue;
            this.client = client;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {

                FetchEmitTuple t = queue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (t == null) {
                    throw new TimeoutException("exceeded maxWaitMs");
                }
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return 1;
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

    private class PipesIteratorWrapper implements Callable<Integer> {
        private final PipesIterator pipesIterator;
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final int numThreads;

        public PipesIteratorWrapper(PipesIterator pipesIterator,
                                    ArrayBlockingQueue<FetchEmitTuple> queue,
                                    int numThreads) {
            this.pipesIterator = pipesIterator;
            this.queue = queue;
            this.numThreads = numThreads;

        }

        @Override
        public Integer call() throws Exception {
            for (FetchEmitTuple t : pipesIterator) {
                //potentially blocks forever
                queue.put(t);
            }
            for (int i = 0; i < numThreads; i ++) {
                queue.put(PipesIterator.COMPLETED_SEMAPHORE);
            }
            return 1;
        }
    }
}
