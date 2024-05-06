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
package org.apache.tika.pipes.pipesiterator;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.tika.pipes.FetchEmitTuple;

/**
 * This is a simple wrapper around {@link PipesIterator} that allows it to be called in its own
 * thread.
 */
public class CallablePipesIterator implements Callable<Long> {

    private final PipesIterator pipesIterator;
    private final ArrayBlockingQueue<FetchEmitTuple> queue;

    private final long timeoutMillis;

    private final int numConsumers;

    /**
     * This sets timeoutMillis to -1, meaning that this will block forever trying to add
     * fetchemittuples to the queue. This sets the number of {@link
     * PipesIterator#COMPLETED_SEMAPHORE} to 1. This means that your consumers must put the
     * semaphore back in the queue after they finish.
     *
     * @param pipesIterator
     * @param queue
     */
    public CallablePipesIterator(
            PipesIterator pipesIterator, ArrayBlockingQueue<FetchEmitTuple> queue) {
        this(pipesIterator, queue, -1);
    }

    /**
     * This sets the number of {@link PipesIterator#COMPLETED_SEMAPHORE} to 1. This means that your
     * consumers must put the semaphore back in the queue after they finish.
     *
     * @param pipesIterator underlying pipes iterator to use
     * @param queue queue to add the fetch emit tuples to
     * @param timeoutMillis how long to try to offer the fetch emit tuples to the queue. If -1, this
     *     will block with {@link ArrayBlockingQueue#put(Object)} forever.
     */
    public CallablePipesIterator(
            PipesIterator pipesIterator,
            ArrayBlockingQueue<FetchEmitTuple> queue,
            long timeoutMillis) {
        this(pipesIterator, queue, timeoutMillis, 1);
    }

    /**
     * @param pipesIterator underlying pipes iterator to use
     * @param queue queue to add the fetch emit tuples to
     * @param timeoutMillis how long to try to offer the fetch emit tuples to the queue. If -1, this
     *     will block with {@link ArrayBlockingQueue#put(Object)} forever.
     * @param numConsumers how many {@link PipesIterator#COMPLETED_SEMAPHORE} to add to the queue.
     *     If the consumers are adding this back to the queue when they find it, then this should be
     *     set to 1, otherwise, for a single semaphore for each consumer, set this to the number of
     *     consumers
     */
    public CallablePipesIterator(
            PipesIterator pipesIterator,
            ArrayBlockingQueue<FetchEmitTuple> queue,
            long timeoutMillis,
            int numConsumers) {
        this.pipesIterator = pipesIterator;
        this.queue = queue;
        this.timeoutMillis = timeoutMillis;
        this.numConsumers = numConsumers;
    }

    @Override
    public Long call() throws Exception {
        long added = 0;
        if (timeoutMillis > 0) {
            for (FetchEmitTuple t : pipesIterator) {
                boolean offered = queue.offer(t, timeoutMillis, TimeUnit.MILLISECONDS);
                if (!offered) {
                    throw new TimeoutException("timed out trying to offer tuple");
                }
                added++;
            }
            for (int i = 0; i < numConsumers; i++) {
                boolean offered =
                        queue.offer(
                                PipesIterator.COMPLETED_SEMAPHORE,
                                timeoutMillis,
                                TimeUnit.MILLISECONDS);
                if (!offered) {
                    throw new TimeoutException(
                            "timed out trying to offer the completed " + "semaphore");
                }
            }
        } else {
            // blocking!
            for (FetchEmitTuple t : pipesIterator) {
                queue.put(t);
                added++;
            }
            for (int i = 0; i < numConsumers; i++) {
                queue.put(PipesIterator.COMPLETED_SEMAPHORE);
            }
        }
        return added;
    }
}
