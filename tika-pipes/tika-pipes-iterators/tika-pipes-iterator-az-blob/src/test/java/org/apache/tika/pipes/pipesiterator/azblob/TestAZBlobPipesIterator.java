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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.pipesiterator.PipesIteratorBase;
import org.apache.tika.plugins.ExtensionConfig;

@Disabled("turn into an actual unit test")
public class TestAZBlobPipesIterator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testSimple() throws Exception {
        ObjectNode configNode = MAPPER.createObjectNode();
        configNode.put("container", ""); // select one
        configNode.put("endpoint", ""); // use one
        configNode.put("sasToken", ""); // find one

        ObjectNode baseConfigNode = MAPPER.createObjectNode();
        baseConfigNode.put("fetcherId", "az-blob");
        baseConfigNode.put("emitterId", "test-emitter");
        configNode.set("baseConfig", baseConfigNode);

        ExtensionConfig extensionConfig = new ExtensionConfig("test-az-blob", "az-blob-pipes-iterator",
                MAPPER.writeValueAsString(configNode));
        AZBlobPipesIterator it = AZBlobPipesIterator.build(extensionConfig);

        int numConsumers = 2;
        ArrayBlockingQueue<FetchEmitTuple> queue = new ArrayBlockingQueue<>(10);

        ExecutorService es = Executors.newFixedThreadPool(numConsumers + 1);
        ExecutorCompletionService<Integer> c = new ExecutorCompletionService<>(es);
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
            queue.offer(PipesIteratorBase.COMPLETED_SEMAPHORE);
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
                if (t == PipesIteratorBase.COMPLETED_SEMAPHORE) {
                    return pairs.size();
                }
                pairs.add(t);
            }
        }
    }
}
