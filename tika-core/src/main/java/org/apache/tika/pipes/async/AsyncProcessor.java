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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.PipesClient;
import org.apache.tika.pipes.PipesException;
import org.apache.tika.pipes.PipesResult;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

/**
 * This is the main class for handling async requests. This manages
 * AsyncClients and AsyncEmitters.
 *
 */
public class AsyncProcessor implements Closeable {

    static final int PARSER_FUTURE_CODE = 1;
    static final int WATCHER_FUTURE_CODE = 3;

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessor.class);

    private final ArrayBlockingQueue<FetchEmitTuple> fetchEmitTuples;
    private final ArrayBlockingQueue<EmitData> emitData;
    private final ExecutorCompletionService<Integer> executorCompletionService;
    private final ExecutorService executorService;
    private final AsyncConfig asyncConfig;
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private static long MAX_OFFER_WAIT_MS = 120000;
    private volatile int numParserThreadsFinished = 0;
    private volatile int numEmitterThreadsFinished = 0;
    private boolean addedEmitterSemaphores = false;
    boolean isShuttingDown = false;

    public AsyncProcessor(Path tikaConfigPath) throws TikaException, IOException {
        this.asyncConfig = AsyncConfig.load(tikaConfigPath);
        this.fetchEmitTuples = new ArrayBlockingQueue<>(asyncConfig.getQueueSize());
        this.emitData = new ArrayBlockingQueue<>(100);
        this.executorService = Executors.newFixedThreadPool(
                asyncConfig.getNumClients() + asyncConfig.getNumEmitters() + 1);
        this.executorCompletionService =
                new ExecutorCompletionService<>(executorService);
        if (!tikaConfigPath.toAbsolutePath()
                .equals(asyncConfig.getTikaConfig().toAbsolutePath())) {
            LOG.warn("TikaConfig for AsyncProcessor ({}) is different " +
                    "from TikaConfig for workers ({}). If this is intended," +
                    " please ignore this warning.",
                    tikaConfigPath.toAbsolutePath(),
                    asyncConfig.getTikaConfig().toAbsolutePath()
            );
        }
        this.executorCompletionService.submit(() -> {
            while (true) {
                try {
                    Thread.sleep(500);
                    checkActive();
                } catch (InterruptedException e) {
                    return WATCHER_FUTURE_CODE;
                }
            }
        });

        for (int i = 0; i < asyncConfig.getNumClients(); i++) {
            executorCompletionService.submit(new FetchEmitWorker(asyncConfig, fetchEmitTuples,
                    emitData));
        }

        EmitterManager emitterManager = EmitterManager.load(asyncConfig.getTikaConfig());
        for (int i = 0; i < asyncConfig.getNumEmitters(); i++) {
            executorCompletionService.submit(new AsyncEmitter(asyncConfig, emitData,
                    emitterManager));
        }
    }

    public synchronized boolean offer(List<FetchEmitTuple> newFetchEmitTuples, long offerMs)
            throws PipesException, InterruptedException {
        if (isShuttingDown) {
            throw new IllegalStateException(
                    "Can't call offer after calling close() or " + "shutdownNow()");
        }
        if (newFetchEmitTuples.size() > asyncConfig.getQueueSize()) {
            throw new OfferLargerThanQueueSize(newFetchEmitTuples.size(),
                    asyncConfig.getQueueSize());
        }
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        while (elapsed < offerMs) {
            if (fetchEmitTuples.remainingCapacity() > newFetchEmitTuples.size()) {
                try {
                    fetchEmitTuples.addAll(newFetchEmitTuples);
                    return true;
                } catch (IllegalStateException e) {
                    //this means that the add all failed because the queue couldn't
                    //take the full list
                    LOG.debug("couldn't add full list", e);
                }
            }
            Thread.sleep(100);
            elapsed = System.currentTimeMillis() - start;
        }
        return false;
    }

    public int getCapacity() {
        return fetchEmitTuples.remainingCapacity();
    }

    public synchronized boolean offer(FetchEmitTuple t, long offerMs)
            throws PipesException, InterruptedException {
        if (fetchEmitTuples == null) {
            throw new IllegalStateException("queue hasn't been initialized yet.");
        } else if (isShuttingDown) {
            throw new IllegalStateException(
                    "Can't call offer after calling close() or " + "shutdownNow()");
        }
        checkActive();
        return fetchEmitTuples.offer(t, offerMs, TimeUnit.MILLISECONDS);
    }

    public void finished() throws InterruptedException {
        for (int i = 0; i < asyncConfig.getNumClients(); i++) {
            boolean offered = fetchEmitTuples.offer(PipesIterator.COMPLETED_SEMAPHORE,
                    MAX_OFFER_WAIT_MS, TimeUnit.MILLISECONDS);
            if (! offered) {
                throw new RuntimeException("Couldn't offer completed semaphore within " +
                        MAX_OFFER_WAIT_MS + " ms");
            }
        }
    }

    public synchronized boolean checkActive() throws InterruptedException {

        Future<Integer> future = executorCompletionService.poll();
        if (future != null) {
            try {
                Integer i = future.get();
                switch (i) {
                    case PARSER_FUTURE_CODE :
                        numParserThreadsFinished++;
                        LOG.debug("fetchEmitWorker finished, total {}", numParserThreadsFinished);
                        break;
                    case AsyncEmitter.EMITTER_FUTURE_CODE :
                        numEmitterThreadsFinished++;
                        LOG.debug("emitter thread finished, total {}", numEmitterThreadsFinished);
                        break;
                    case WATCHER_FUTURE_CODE :
                        LOG.debug("watcher thread finished");
                        break;
                    default :
                        throw new IllegalArgumentException("Don't recognize this future code: " + i);
                }
            } catch (ExecutionException e) {
                LOG.error("execution exception", e);
                throw new RuntimeException(e);
            }
        }
        if (numParserThreadsFinished == asyncConfig.getNumClients() && ! addedEmitterSemaphores) {
            for (int i = 0; i < asyncConfig.getNumEmitters(); i++) {
                try {
                    boolean offered = emitData.offer(AsyncEmitter.EMIT_DATA_STOP_SEMAPHORE,
                            MAX_OFFER_WAIT_MS,
                            TimeUnit.MILLISECONDS);
                    if (! offered) {
                        throw new RuntimeException("Couldn't offer emit data stop semaphore " +
                                "within " + MAX_OFFER_WAIT_MS + " ms");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            addedEmitterSemaphores = true;
        }
        return !(numParserThreadsFinished == asyncConfig.getNumClients() &&
                numEmitterThreadsFinished == asyncConfig.getNumEmitters());
    }

    @Override
    public void close() throws IOException {
        executorService.shutdownNow();
        asyncConfig.getPipesReporter().close();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    private class FetchEmitWorker implements Callable<Integer> {

        private final AsyncConfig asyncConfig;
        private final ArrayBlockingQueue<FetchEmitTuple> fetchEmitTuples;
        private final ArrayBlockingQueue<EmitData> emitDataQueue;

        private FetchEmitWorker(AsyncConfig asyncConfig,
                                ArrayBlockingQueue<FetchEmitTuple> fetchEmitTuples,
                                ArrayBlockingQueue<EmitData> emitDataQueue) {
            this.asyncConfig = asyncConfig;
            this.fetchEmitTuples = fetchEmitTuples;
            this.emitDataQueue = emitDataQueue;
        }

        @Override
        public Integer call() throws Exception {

            try (PipesClient pipesClient = new PipesClient(asyncConfig)) {
                while (true) {
                    FetchEmitTuple t = fetchEmitTuples.poll(1, TimeUnit.SECONDS);
                    if (t == null) {
                        //skip
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("null fetch emit tuple");
                        }
                    } else if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("hit completed semaphore");
                        }
                        return PARSER_FUTURE_CODE;
                    } else {
                        PipesResult result = null;
                        long start = System.currentTimeMillis();
                        try {
                            result = pipesClient.process(t);
                        } catch (IOException e) {
                            result = PipesResult.UNSPECIFIED_CRASH;
                        }
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("timer -- pipes client process: {} ms",
                                    System.currentTimeMillis() - start);
                        }
                        long offerStart = System.currentTimeMillis();
                        if (result.getStatus() == PipesResult.STATUS.PARSE_SUCCESS ||
                                result.getStatus() == PipesResult.STATUS.PARSE_SUCCESS_WITH_EXCEPTION) {
                            boolean offered = emitDataQueue.offer(result.getEmitData(),
                                    MAX_OFFER_WAIT_MS,
                                    TimeUnit.MILLISECONDS);
                            if (! offered) {
                                throw new RuntimeException("Couldn't offer emit data to queue " +
                                        "within " + MAX_OFFER_WAIT_MS + " ms");
                            }
                        }
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("timer -- offered: {} ms",
                                    System.currentTimeMillis() - offerStart);
                        }
                        long elapsed = System.currentTimeMillis() - start;
                        asyncConfig.getPipesReporter().report(t, result, elapsed);
                        totalProcessed.incrementAndGet();
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path tikaConfigPath = Paths.get(args[0]);
        PipesIterator pipesIterator = PipesIterator.build(tikaConfigPath);
        long start = System.currentTimeMillis();
        try (AsyncProcessor processor = new AsyncProcessor(tikaConfigPath)) {
            for (FetchEmitTuple t : pipesIterator) {
                processor.offer(t, 2000);
            }
            processor.finished();
            while (true) {
                if (processor.checkActive()) {
                    Thread.sleep(500);
                } else {
                    break;
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            LOG.info("Successfully finished processing {} files in {} ms",
                    processor.getTotalProcessed(), elapsed);
        }
    }
}
