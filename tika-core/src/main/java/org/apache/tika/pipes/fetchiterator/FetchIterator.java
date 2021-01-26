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

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.fetcher.FetchIdMetadataPair;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract class that handles the testing for timeouts/thread safety
 * issues.  Concrete classes implement the blocking {@link #enqueue()}.
 * <p>
 * This must be "called", obviously...
 */
public abstract class FetchIterator implements Callable<Integer>, Initializable {

    public static final long DEFAULT_MAX_WAIT_MS = 300_000;
    public static final int DEFAULT_QUEUE_SIZE = 1000;
    public static final FetchIdMetadataPair COMPLETED_SEMAPHORE =
            new FetchIdMetadataPair(null, null);

    private long maxWaitMs = DEFAULT_MAX_WAIT_MS;
    private int numConsumers = -1;
    private ArrayBlockingQueue<FetchIdMetadataPair> queue = null;
    private int queueSize = DEFAULT_QUEUE_SIZE;
    private String fetcherName;
    private int added = 0;
    public FetchIterator() {

    }

    public FetchIterator(String fetcherName) {
        this.fetcherName = fetcherName;
    }

    /**
     * This must be called before 'calling' this object.
     * @param numConsumers
     */
    public ArrayBlockingQueue<FetchIdMetadataPair> init(int numConsumers) {
        this.queue = new ArrayBlockingQueue<>(queueSize);
        this.numConsumers = numConsumers;
        return queue;
    }

    @Field
    public void setFetcherName(String fetcherName) {
        this.fetcherName = fetcherName;
    }

    public String getFetcherName() {
        return fetcherName;
    }


    @Field
    public void setMaxWaitMs(long maxWaitMs) {
        this.maxWaitMs = maxWaitMs;
    }

    @Field
    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
    @Override
    public Integer call() throws Exception {
        if (queue == null || numConsumers < 0) {
            throw new IllegalStateException("Must call 'init' before calling this object");
        }

        enqueue();
        for (int i = 0; i < numConsumers; i++) {
            try {
                tryToAdd(COMPLETED_SEMAPHORE);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return added;
    }

    protected abstract void enqueue() throws IOException, TimeoutException, InterruptedException;

    protected void tryToAdd(FetchIdMetadataPair p) throws InterruptedException, TimeoutException {
        if (p != COMPLETED_SEMAPHORE) {
            added++;
        }
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
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        //no-op
    }

    protected static void mustNotBeEmpty(String paramName, String paramValue) {
        if (paramValue == null || paramValue.trim().equals("")) {
            throw new IllegalArgumentException("parameter '"+
                    paramName + "' must be set in the config file");
        }
    }

}
