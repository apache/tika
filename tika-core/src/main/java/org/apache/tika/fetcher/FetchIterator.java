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
package org.apache.tika.fetcher;

import org.apache.tika.config.Field;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract class that handles the testing for timeouts/thread safety
 * issues.  Concrete classes implement {@link #enqueue()}.
 *
 * This must be "called" and managed with an ExecutorService, etc.
 * for the iterable to work.
 */
public abstract class FetchIterator implements Callable<Integer>,
        Iterable<FetchMetadataPair> {

    public static final long DEFAULT_MAX_WAIT_MS = 300_000;

    static final FetchMetadataPair POISON =
            new FetchMetadataPair(null, null);

    private final int queueSize = 1000;
    private long maxWaitMs = DEFAULT_MAX_WAIT_MS;
    private final ArrayBlockingQueue<FetchMetadataPair> queue = new ArrayBlockingQueue<>(queueSize);
    private String name;

    public FetchIterator(String name) {
        this.name = name;
    }

    @Field
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public Integer call() throws Exception {
        enqueue();
        System.out.println("finished");
        return 1;
    }

    protected abstract void enqueue() throws IOException, TimeoutException;

    void tryToAdd(FetchMetadataPair p) throws InterruptedException, TimeoutException {
        System.out.println("trying to add: "+p + " "+ queue.size());
        boolean offered = queue.offer(p, maxWaitMs, TimeUnit.MILLISECONDS);
        System.out.println("added: "+p + " "+ queue.size());
        if (! offered) {
            throw new TimeoutException("timed out while offering");
        }
    }

    @Override
    public Iterator<FetchMetadataPair> iterator() {
        return new InternalIterator();
    }

    private class InternalIterator implements Iterator<FetchMetadataPair> {
        //Object[] is recommended by FindBugs as a lock object
        private Object[] lock = new Object[0];
        private FetchMetadataPair next = null;
        volatile boolean initialized = false;
        InternalIterator() {

        }

        @Override
        public boolean hasNext() {
            System.out.println("hasNExt");
                if (!initialized) {
                    next = getNext();
                    initialized = true;
                }

            return next != POISON;
        }

        /**
         *
         * @return next FetcherStringMetadataPair; if {@link #hasNext()} returns
         * false, this will return null.
         */
        @Override
        public FetchMetadataPair next() {

            System.out.println("in next");

                if (next == POISON) {
                    return null;
                }
                FetchMetadataPair ret = next;
                next = getNext();
                return ret;
        }

        private FetchMetadataPair getNext() {
            FetchMetadataPair p = null;
            System.out.println("in get next: " + queue.size());
            try {
                //System.out.println("peek: " + queue.peek());
                p = queue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                //System.out.println("peek2: " + queue.peek() + " :: "+p);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (p == null) {
                throw new RuntimeException(new TimeoutException(""));
            }
            return p;
        }
    }
}
