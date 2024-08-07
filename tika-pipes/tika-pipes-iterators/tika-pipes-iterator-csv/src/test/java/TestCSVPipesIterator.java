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

import static org.apache.tika.pipes.pipesiterator.PipesIterator.COMPLETED_SEMAPHORE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.csv.CSVPipesIterator;

public class TestCSVPipesIterator {


    @Test
    public void testSimple() throws Exception {
        Path p = get("test-simple.csv");
        CSVPipesIterator it = new CSVPipesIterator();
        it.setFetcherName("fsf");
        it.setEmitterName("fse");
        it.setCsvPath(p);
        it.setFetchKeyColumn("fetchKey");
        int numConsumers = 2;
        ExecutorService es = Executors.newFixedThreadPool(numConsumers);
        ExecutorCompletionService c = new ExecutorCompletionService(es);
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(100);
        List<MockFetcher> fetchers = new ArrayList<>();
        for (int i = 0; i < numConsumers; i++) {
            MockFetcher f = new MockFetcher(queue);
            fetchers.add(f);
            c.submit(f);
        }
        for (FetchEmitTuple t : it) {
            queue.offer(t);
        }
        for (int i = 0; i < numConsumers; i++) {
            queue.offer(COMPLETED_SEMAPHORE);
        }
        int finished = 0;
        int completed = 0;
        try {
            while (finished++ < numConsumers) {
                Future<Integer> f = c.take();
                completed += f.get();
            }
        } finally {
            es.shutdownNow();
        }
        assertEquals(5, completed);
        for (MockFetcher f : fetchers) {
            for (FetchEmitTuple t : f.pairs) {
                String id = t
                        .getMetadata()
                        .get("id");
                assertEquals("path/to/my/file" + id, t
                        .getFetchKey()
                        .getFetchKey());
                assertEquals("project" + (Integer.parseInt(id) % 2 == 1 ? "a" : "b"), t
                        .getMetadata()
                        .get("project"));
            }
        }
    }

    @Test
    public void testBadFetchKeyCol() throws Exception {
        Path p = get("test-simple.csv");
        CSVPipesIterator it = new CSVPipesIterator();
        it.setFetcherName("fs");
        it.setCsvPath(p);
        assertThrows(RuntimeException.class, () -> {
            it.setFetchKeyColumn("fetchKeyDoesntExist");
            for (FetchEmitTuple t : it) {

            }
        });
    }

    private Path get(String testFileName) throws Exception {
        return Paths.get(TestCSVPipesIterator.class
                .getResource("/" + testFileName)
                .toURI());
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
                if (t == COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(t);
            }
        }
    }
}
