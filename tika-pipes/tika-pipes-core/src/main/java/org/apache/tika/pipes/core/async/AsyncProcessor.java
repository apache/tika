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
package org.apache.tika.pipes.core.async;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
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
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.pipesiterator.PipesIterator;
import org.apache.tika.pipes.api.pipesiterator.TotalCountResult;
import org.apache.tika.pipes.api.pipesiterator.TotalCounter;
import org.apache.tika.pipes.api.reporter.PipesReporter;
import org.apache.tika.pipes.core.PipesClient;
import org.apache.tika.pipes.core.PipesException;
import org.apache.tika.pipes.core.PipesResults;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.core.reporter.ReporterManager;

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
    private final ArrayBlockingQueue<EmitDataPair> emitDatumTuples;
    private final ExecutorCompletionService<Integer> executorCompletionService;
    private final ExecutorService executorService;
    private final AsyncConfig asyncConfig;
    private final PipesReporter pipesReporter;
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private static long MAX_OFFER_WAIT_MS = 120000;
    private volatile int numParserThreadsFinished = 0;
    private volatile int numEmitterThreadsFinished = 0;
    private boolean addedEmitterSemaphores = false;
    boolean isShuttingDown = false;

    public AsyncProcessor(Path tikaConfigPath, Path pluginsConfigPath) throws TikaException, IOException {
        this(tikaConfigPath, pluginsConfigPath, null);
    }

    public AsyncProcessor(Path tikaConfigPath, Path pluginsConfigPath, PipesIterator pipesIterator) throws TikaException, IOException {
        this.asyncConfig = AsyncConfig.load(tikaConfigPath, pluginsConfigPath);
        this.pipesReporter = ReporterManager.load(pluginsConfigPath);
        LOG.debug("loaded reporter {}", pipesReporter.getClass());
        this.fetchEmitTuples = new ArrayBlockingQueue<>(asyncConfig.getQueueSize());
        this.emitDatumTuples = new ArrayBlockingQueue<>(100);
        //+1 is the watcher thread
        this.executorService = Executors.newFixedThreadPool(
                asyncConfig.getNumClients() + asyncConfig.getNumEmitters() + 1);
        this.executorCompletionService =
                new ExecutorCompletionService<>(executorService);
        try {
            if (asyncConfig.getTikaConfig() != null && !tikaConfigPath.toAbsolutePath().equals(asyncConfig.getTikaConfig().toAbsolutePath())) {
                LOG.warn("TikaConfig for AsyncProcessor ({}) is different " +
                                "from TikaConfig for workers ({}). If this is intended," +
                                " please ignore this warning.", tikaConfigPath.toAbsolutePath(),
                        asyncConfig.getTikaConfig().toAbsolutePath());
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
            //this is run in a daemon thread
            if (pipesIterator != null && (pipesIterator instanceof TotalCounter)) {
                LOG.debug("going to total counts");
                startCounter((TotalCounter) pipesIterator);
            }

            for (int i = 0; i < asyncConfig.getNumClients(); i++) {
                executorCompletionService.submit(
                        new FetchEmitWorker(asyncConfig, fetchEmitTuples, emitDatumTuples));
            }

            EmitterManager emitterManager = EmitterManager.load(asyncConfig.getPipesPluginsConfig());
            for (int i = 0; i < asyncConfig.getNumEmitters(); i++) {
                executorCompletionService.submit(
                        new AsyncEmitter(asyncConfig, emitDatumTuples, emitterManager));
            }
        } catch (Exception e) {
            LOG.error("problem initializing AsyncProcessor", e);
            executorService.shutdownNow();
            this.pipesReporter.error(e);
            throw e;
        }
    }

    private void startCounter(TotalCounter totalCounter) {
        Thread counterThread = new Thread(() -> {
            totalCounter.startTotalCount();
            TotalCountResult.STATUS status = totalCounter.getTotalCount().getStatus();
            while (status == TotalCountResult.STATUS.NOT_COMPLETED) {
                try {
                    Thread.sleep(500);
                    TotalCountResult result = totalCounter.getTotalCount();
                    LOG.trace("counter total  {} {} ", result.getStatus(), result.getTotalCount());
                    pipesReporter.report(result);
                    status = result.getStatus();
                } catch (InterruptedException e) {
                    return;
                }
            }

        });
        counterThread.setDaemon(true);
        counterThread.start();
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
                this.pipesReporter.error(e);
                throw new RuntimeException(e);
            }
        }
        if (numParserThreadsFinished == asyncConfig.getNumClients() && ! addedEmitterSemaphores) {
            for (int i = 0; i < asyncConfig.getNumEmitters(); i++) {
                try {
                    boolean offered = emitDatumTuples.offer(AsyncEmitter.EMIT_DATA_STOP_SEMAPHORE,
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
        this.pipesReporter.close();
    }

    public long getTotalProcessed() {
        return totalProcessed.get();
    }

    private class FetchEmitWorker implements Callable<Integer> {

        private final AsyncConfig asyncConfig;
        private final ArrayBlockingQueue<FetchEmitTuple> fetchEmitTuples;
        private final ArrayBlockingQueue<EmitDataPair> emitDataTupleQueue;

        private FetchEmitWorker(AsyncConfig asyncConfig,
                                ArrayBlockingQueue<FetchEmitTuple> fetchEmitTuples,
                                ArrayBlockingQueue<EmitDataPair> emitDataTupleQueue) {
            this.asyncConfig = asyncConfig;
            this.fetchEmitTuples = fetchEmitTuples;
            this.emitDataTupleQueue = emitDataTupleQueue;
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
                            LOG.warn("pipesClient crash", e);
                            result = PipesResults.UNSPECIFIED_CRASH;
                        }
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("timer -- pipes client process: {} ms",
                                    System.currentTimeMillis() - start);
                        }
                        long offerStart = System.currentTimeMillis();

                        if (shouldEmit(result)) {
                            LOG.trace("adding result to emitter queue: " + result.emitData());
                            boolean offered = emitDataTupleQueue.offer(
                                    new EmitDataPair(t.getEmitKey().getEmitterId(), result.emitData()), MAX_OFFER_WAIT_MS,
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
                        pipesReporter.report(t, result, elapsed);
                        totalProcessed.incrementAndGet();
                    }
                }
            }
        }

        private boolean shouldEmit(PipesResult result) {

            if (result.status() == PipesResult.STATUS.PARSE_SUCCESS ||
                    result.status() == PipesResult.STATUS.PARSE_SUCCESS_WITH_EXCEPTION) {
                return true;
            }
            return result.intermediate() && asyncConfig.isEmitIntermediateResults();
        }
    }
}
