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
package org.apache.tika.pipes.fetchiterator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;

/**
 * Abstract class that handles the testing for timeouts/thread safety
 * issues.  Concrete classes implement the blocking {@link #enqueue()}.
 * If there's an exception in the enqueuing thread, this will throw
 * a RuntimeException.  It will throw an IllegalStateException if
 * next() is called after hasNext() has returned false.
 */
public abstract class FetchIterator
        implements Callable<Integer>, Iterable<FetchEmitTuple>, Initializable {

    public static final long DEFAULT_MAX_WAIT_MS = 300_000;
    public static final int DEFAULT_QUEUE_SIZE = 1000;

    public static final FetchEmitTuple COMPLETED_SEMAPHORE =
            new FetchEmitTuple(null,null, null, null, null, null);

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchIterator.class);

    private long maxWaitMs = DEFAULT_MAX_WAIT_MS;
    private ArrayBlockingQueue<FetchEmitTuple> queue = null;
    private int queueSize = DEFAULT_QUEUE_SIZE;
    private String fetcherName;
    private String emitterName;
    private FetchEmitTuple.ON_PARSE_EXCEPTION onParseException =
            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
    private BasicContentHandlerFactory.HANDLER_TYPE handlerType =
            BasicContentHandlerFactory.HANDLER_TYPE.TEXT;
    private int writeLimit = -1;
    private int maxEmbeddedResources = -1;

    private int added = 0;
    private FutureTask<Integer> futureTask;

    public String getFetcherName() {
        return fetcherName;
    }

    @Field
    public void setFetcherName(String fetcherName) {
        this.fetcherName = fetcherName;
    }

    public String getEmitterName() {
        return emitterName;
    }

    @Field
    public void setEmitterName(String emitterName) {
        this.emitterName = emitterName;
    }

    @Field
    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    @Field
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public FetchEmitTuple.ON_PARSE_EXCEPTION getOnParseException() {
        return onParseException;
    }

    @Field
    public void setOnParseException(String onParseException) throws TikaConfigException {
        if ("skip".equalsIgnoreCase(onParseException)) {
            setOnParseException(FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        } else if ("emit".equalsIgnoreCase(onParseException)) {
            setOnParseException(FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        } else {
            throw new TikaConfigException("must be either 'skip' or 'emit': " + onParseException);
        }
    }

    public void setOnParseException(FetchEmitTuple.ON_PARSE_EXCEPTION onParseException) {
        this.onParseException = onParseException;
    }

    @Field
    public void setHandlerType(String handlerType) {
        this.handlerType = BasicContentHandlerFactory
                .parseHandlerType(handlerType, BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
    }

    @Field
    public void setWriteLimit(int writeLimit) {
        this.writeLimit = writeLimit;
    }

    @Field
    void setMaxEmbeddedResources(int maxEmbeddedResources) {
        this.maxEmbeddedResources = maxEmbeddedResources;
    }

    public Integer call() throws Exception {
        enqueue();
        tryToAdd(COMPLETED_SEMAPHORE);
        return added;
    }

    protected HandlerConfig getHandlerConfig() {
        return new HandlerConfig(handlerType, writeLimit, maxEmbeddedResources);
    }

    protected abstract void enqueue() throws IOException, TimeoutException, InterruptedException;

    protected void tryToAdd(FetchEmitTuple p) throws InterruptedException, TimeoutException {
        added++;
        boolean offered = queue.offer(p, maxWaitMs, TimeUnit.MILLISECONDS);
        if (!offered) {
            throw new TimeoutException("timed out while offering");
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        //no-op
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {
        //no-op
    }

    @Override
    public Iterator<FetchEmitTuple> iterator() {
        if (futureTask != null) {
            throw new IllegalStateException("Can't call iterator more than once!");
        }
        futureTask = new FutureTask<>(this);
        queue = new ArrayBlockingQueue<>(queueSize);
        new Thread(futureTask).start();
        return new TupleIterator();
    }

    private class TupleIterator implements Iterator<FetchEmitTuple> {
        FetchEmitTuple next = null;

        @Override
        public boolean hasNext() {
            if (next == null) {
                next = pollNext();
            }
            return next != COMPLETED_SEMAPHORE;
        }

        @Override
        public FetchEmitTuple next() {
            if (next == COMPLETED_SEMAPHORE) {
                throw new IllegalStateException(
                        "don't call next() after hasNext() has returned false!");
            }
            FetchEmitTuple ret = next;
            next = pollNext();
            return ret;
        }

        private FetchEmitTuple pollNext() throws TikaTimeoutException {

            FetchEmitTuple t = null;
            long start = System.currentTimeMillis();
            try {
                long elapsed = System.currentTimeMillis() - start;
                while (t == null && elapsed < maxWaitMs) {
                    checkThreadOk();
                    t = queue.poll(100, TimeUnit.MILLISECONDS);
                    elapsed = System.currentTimeMillis() - start;
                }
            } catch (InterruptedException e) {
                LOGGER.warn("interrupted");
                return COMPLETED_SEMAPHORE;
            }
            if (t == null) {
                throw new TikaTimeoutException(
                        "waited longer than " + maxWaitMs + "ms for the next tuple");
            }
            return t;
        }

        /**
         * this checks to make sure that the thread hasn't terminated early.
         * Will return true if the thread has successfully completed or if
         * it has not completed.  Will return false if there has been a thread
         * interrupt. Will throw a RuntimeException if there's been
         * an execution exception in the thread.
         */
        private void checkThreadOk() throws InterruptedException {
            if (futureTask.isDone()) {
                try {
                    futureTask.get();
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
        }
    }
}
