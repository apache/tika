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
package org.apache.tika.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

public class TikaProgressTrackerTest {

    @Test
    public void testInitialTimestamp() {
        long before = System.currentTimeMillis();
        TikaProgressTracker tracker = new TikaProgressTracker();
        long after = System.currentTimeMillis();

        assertTrue(tracker.getLastProgressMillis() >= before);
        assertTrue(tracker.getLastProgressMillis() <= after);
    }

    @Test
    public void testUpdateAdvancesTimestamp() throws Exception {
        TikaProgressTracker tracker = new TikaProgressTracker();
        long initial = tracker.getLastProgressMillis();

        Thread.sleep(50);
        tracker.update();

        assertTrue(tracker.getLastProgressMillis() > initial);
    }

    @Test
    public void testConcurrentUpdates() throws Exception {
        TikaProgressTracker tracker = new TikaProgressTracker();
        int numThreads = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 100; j++) {
                        tracker.update();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        assertTrue(tracker.getLastProgressMillis() > 0);
    }

    @Test
    public void testStaticUpdateWithParseContext() throws Exception {
        TikaProgressTracker tracker = new TikaProgressTracker();
        long initial = tracker.getLastProgressMillis();

        ParseContext context = new ParseContext();
        context.set(TikaProgressTracker.class, tracker);

        Thread.sleep(50);
        TikaProgressTracker.update(context);

        assertTrue(tracker.getLastProgressMillis() > initial);
    }

    @Test
    public void testStaticUpdateWithNullContext() {
        // Should not throw
        TikaProgressTracker.update(null);
    }

    @Test
    public void testStaticUpdateWithNoTracker() {
        // Should not throw
        TikaProgressTracker.update(new ParseContext());
    }
}
