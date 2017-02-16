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

package org.apache.tika.eval.db;


import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class AbstractBufferTest {


    @Test(timeout = 30000)
    public void runTest() throws InterruptedException, ExecutionException {
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, new String[]{
                "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"});

        int numGets = 100;
        int numTesters = 20;
        AbstractDBBuffer b = new TestBuffer();


        ExecutorService ex = Executors.newFixedThreadPool(numTesters);
        CompletionService<MyTestResult> completionService =
                new ExecutorCompletionService<>(
                        ex);
        for (int i = 0; i < numTesters; i++) {
            completionService.submit(new Tester(keys, b, numGets));
        }

        int results = 0;
        Map<String, Integer> combined = new HashMap<>();
        while (results < numTesters) {
            Future<MyTestResult> futureResult =
                    completionService.poll(1, TimeUnit.SECONDS);
            if (futureResult != null) {
                results++;
                assertEquals(keys.size(), futureResult.get().getMap().keySet().size());
                for (Map.Entry<String, Integer> e : futureResult.get().getMap().entrySet()) {
                    if (!combined.containsKey(e.getKey())) {
                        combined.put(e.getKey(), e.getValue());
                    } else {
                        assertEquals(combined.get(e.getKey()), e.getValue());
                    }
                }
            }
        }
        assertEquals(keys.size(), b.getNumWrites());
    }

    private class Tester implements Callable<MyTestResult> {

        private Random r = new Random();
        private Map<String, Integer> m = new HashMap<>();
        List<String> keys = new ArrayList<>();
        private final AbstractDBBuffer dbBuffer;
        private final int numGets;

        private Tester(List<String> inputKeys, AbstractDBBuffer buffer, int numGets) {
            keys.addAll(inputKeys);
            dbBuffer = buffer;
            this.numGets = numGets;
        }

        @Override
        public MyTestResult call() throws Exception {


            for (int i = 0; i < numGets; i++) {
                int index = r.nextInt(keys.size());
                String k = keys.get(index);
                if (k == null) {
                    throw new RuntimeException("keys can't be null");
                }
                Integer expected = m.get(k);
                Integer val = dbBuffer.getId(k);
                if (val == null) {
                    throw new RuntimeException("Val can't be null!");
                }
                if (expected != null) {
                    assertEquals(expected, val);
                }
                m.put(k, val);
            }

            //now add the val for every key
            //just in case the rand() process didn't hit
            //all indices
            for (String k : keys) {
                Integer val = dbBuffer.getId(k);
                m.put(k, val);
            }
            MyTestResult r = new MyTestResult(m);
            return r;
        }
    }

    private class MyTestResult {
        Map<String, Integer> m;
        private MyTestResult(Map<String, Integer> m) {
            this.m = m;
        }
        private Map<String, Integer> getMap() {
            return m;
        }

        @Override
        public String toString() {
            return "MyTester: "+m.size();
        }
    }

    private class TestBuffer extends AbstractDBBuffer {
        @Override
        public void write(int id, String value) throws RuntimeException {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws SQLException {
            //no-op
        }
    }
}
