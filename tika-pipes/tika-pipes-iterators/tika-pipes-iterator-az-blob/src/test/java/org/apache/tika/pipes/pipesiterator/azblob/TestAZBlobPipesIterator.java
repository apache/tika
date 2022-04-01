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
package org.apache.tika.pipes.pipesiterator.azblob;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIterator;

@Disabled("turn into an actual unit test")
public class TestAZBlobPipesIterator {

    @Test
    public void testSimple() throws Exception {
        AZBlobPipesIterator it = new AZBlobPipesIterator();
        it.setContainer("");
        it.setEndpoint("");
        it.setSasToken("");
        it.initialize(Collections.EMPTY_MAP);
        int numConsumers = 2;
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(10);

        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService c = new ExecutorCompletionService(es);
        List<MockFetcher> fetchers = new ArrayList<>();
        for (int i = 0; i < numConsumers; i++) {
            MockFetcher fetcher = new MockFetcher(queue);
            fetchers.add(fetcher);
            c.submit(fetcher);
        }
        for (FetchEmitTuple t : it) {
            queue.offer(t);
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(PipesIterator.COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        int completed = 0;
        try {
            while (finished < numConsumers) {
                Future<Integer> f = c.take();
                completed += f.get();
                finished++;
            }
        } finally {
            es.shutdownNow();
        }
        assertEquals(1, completed);

    }

    private static class MockFetcher implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchEmitTuple> queue;
        private final List<FetchEmitTuple> pairs = new ArrayList<>();

        private MockFetcher(ArrayBlockingQueue<FetchEmitTuple> queue) {
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                FetchEmitTuple t = queue.poll(1, TimeUnit.HOURS);
                if (t == PipesIterator.COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(t);
            }
        }
    }
}
