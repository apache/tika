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
package org.apache.tika.pipes.fetchiterator.s3;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.FetchId;
import org.apache.tika.pipes.fetcher.FetchIdMetadataPair;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

import static org.junit.Assert.assertEquals;

@Ignore("turn into an actual unit test")
public class TestS3FetchIterator {


    @Test
    public void testSimple() throws Exception {
        S3FetchIterator it = new S3FetchIterator();
        it.setFetcherName("s3");
        it.setBucket("");//find one
        it.setProfile("");//use one
        it.setRegion("");//select one
        it.initialize(Collections.EMPTY_MAP);
        int numConsumers = 6;
        ArrayBlockingQueue<FetchIdMetadataPair> queue = it.init(numConsumers);

        ExecutorService es = Executors.newFixedThreadPool(numConsumers+1);
        ExecutorCompletionService c = new ExecutorCompletionService(es);
        c.submit(it);
        List<MockFetcher> fetchers = new ArrayList<>();
        for (int i = 0; i < numConsumers; i++) {
            MockFetcher fetcher = new MockFetcher(queue);
            fetchers.add(fetcher);
            c.submit(fetcher);
        }
        int finished = 0;
        int completed = 0;
        try {
            while (finished++ < numConsumers+1) {
                Future<Integer> f = c.take();
                completed += f.get();
            }
        } finally {
            es.shutdownNow();
        }
        assertEquals(20000, completed);

    }

    private static class MockFetcher implements Callable<Integer> {
        private final ArrayBlockingQueue<FetchIdMetadataPair> queue;
        private final List<FetchIdMetadataPair> pairs = new ArrayList<>();
        private MockFetcher(ArrayBlockingQueue<FetchIdMetadataPair> queue) {
            this.queue = queue;
        }

        @Override
        public Integer call() throws Exception {
            while (true) {
                FetchIdMetadataPair p = queue.poll(1, TimeUnit.HOURS);
                if (p == FetchIterator.COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(p);
            }
        }
    }
}
