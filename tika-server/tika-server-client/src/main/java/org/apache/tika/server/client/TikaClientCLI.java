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

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.fetcher.FetchIterator;
import org.apache.tika.fetcher.FetchMetadataPair;
import org.apache.tika.fetcher.FileSystemFetchIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public class TikaClientCLI {

    private static final FetchMetadataPair POISON =
            new FetchMetadataPair(null, null);

    private static final Logger LOGGER = LoggerFactory.getLogger(TikaClientCLI.class);

    //make these configurable
    private int numThreads = 1;
    private long maxWaitMs = 300000;

    public static void main(String[] args) throws Exception {
        //TODO -- add an actual commandline
        Path tikaConfigPath = Paths.get(args[0]);
        List<String> tikaServerUrls = Arrays.asList(args[1].split(","));
        String fetcherString = args[2];

        TikaClientCLI cli = new TikaClientCLI();
        cli.execute(tikaConfigPath, tikaServerUrls, fetcherString);
    }

    private void execute(Path tikaConfigPath, List<String> tikaServerUrls, String fetcherString)
            throws TikaException, IOException, SAXException {
        TikaConfig config = new TikaConfig(tikaConfigPath);

        ArrayBlockingQueue<FetchMetadataPair> queue = new ArrayBlockingQueue<>(1000);
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads+2);
        ExecutorCompletionService<Integer> completionService = new ExecutorCompletionService<>(executorService);
        //TODO: fix this!
        final FetchIterator fetchIterator = new FileSystemFetchIterator(
                "fs", Paths.get("."));
        completionService.submit(fetchIterator);
        completionService.submit(new Enqueuer(queue, fetchIterator, numThreads));
        if (tikaServerUrls.size() == numThreads) {
            logDiffSizes(tikaServerUrls.size(), numThreads);
            for (int i = 0; i < numThreads; i++) {
                TikaClient client = TikaClient.get(config,
                        Collections.singletonList(tikaServerUrls.get(i)));
                completionService.submit(new FetchWorker(queue, client, fetcherString));
            }
        } else {
            TikaClient client = TikaClient.get(config,tikaServerUrls);
            completionService.submit(new FetchWorker(queue, client, fetcherString));
        }

        int finished = 0;
        while (finished < numThreads+2) {
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
                } catch (InterruptedException|ExecutionException e) {
                    //stop the world
                    LOGGER.error("", e);
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private void logDiffSizes(int servers, int numThreads) {
        LOGGER.info("tika server count ({}) != numThreads ({}). " +
                        "Each client will randomly select a server from this list",
                servers, numThreads);
    }

    private class Enqueuer implements Callable<Integer> {
        //simple class that pulls fetchmetadata pairs from the fetchiterator
        //and enqueues them for the worker threads.
        private final ArrayBlockingQueue<FetchMetadataPair> queue;
        private final FetchIterator fetchIterator;
        private final int numThreads;

        public Enqueuer(ArrayBlockingQueue<FetchMetadataPair> queue, FetchIterator fetchIterator, int numThreads) {
            this.queue = queue;
            this.fetchIterator = fetchIterator;
            this.numThreads = numThreads;
        }

        @Override
        public Integer call() throws Exception {
            System.out.println("enqueing");
            for (FetchMetadataPair p : fetchIterator) {
                System.out.println("offering "+p);
                boolean offered = queue.offer(p, maxWaitMs, TimeUnit.MILLISECONDS);
                if (! offered) {
                    throw new TimeoutException("exceeded max wait");
                }
            }
            for (int i = 0; i < numThreads; i++) {
                boolean offered = queue.offer(POISON, maxWaitMs, TimeUnit.MILLISECONDS);
                if (! offered) {
                    throw new TimeoutException("exceeded max wait");
                }
            }
            return 1;
        }
    }

    private class FetchWorker implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchMetadataPair> queue;
        private final TikaClient client;
        private final String emitterString;
        public FetchWorker(ArrayBlockingQueue<FetchMetadataPair> queue, TikaClient client,
                           String emitterString) {
            this.queue = queue;
            this.client = client;
            this.emitterString = emitterString;
        }

        @Override
        public Integer call() throws Exception {

            while (true) {
                System.out.println("about to work");
                FetchMetadataPair p = queue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (p == null) {
                    throw new TimeoutException("exceeded maxWaitMs");
                }
                if (p == POISON) {
                    return 1;
                }
                try {
                    System.out.println("parsing; "+p.getFetcherString());
                    System.out.println(client.parse(p.getFetcherString(), p.getMetadata(), emitterString));
                } catch (IOException e) {
                    LOGGER.warn(p.getFetcherString(), e);
                } catch (TikaException e) {
                    LOGGER.warn(p.getFetcherString(), e);
                }
            }
        }
    }
}
