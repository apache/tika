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
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.fetcher.Fetcher;
import org.apache.tika.pipes.core.TrackingFetcherManager.TrackedFetcher;

public class ComponentLeaseTest {

    @Test
    public void testIdleInstanceClosedOnDelete() throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager();
        manager.saveComponent(TrackingFetcherManager.config("f"));
        manager.acquire("f").close();
        TrackedFetcher fetcher = manager.getBuilt().get(0);
        assertEquals(0, fetcher.getCloses(), "a released instance stays open while it is current");

        manager.deleteComponent("f");
        assertEquals(1, fetcher.getCloses());
    }

    @Test
    public void testLeasedInstanceClosedOnLastRelease() throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager();
        manager.saveComponent(TrackingFetcherManager.config("f"));
        Lease<Fetcher> first = manager.acquire("f");
        Lease<Fetcher> second = manager.acquire("f");
        assertSame(first.get(), second.get());
        TrackedFetcher old = (TrackedFetcher) first.get();

        manager.saveComponent(TrackingFetcherManager.config("f"));
        try (Lease<Fetcher> replacement = manager.acquire("f")) {
            assertNotSame(old, replacement.get(), "an update must not hand out the retired instance");
        }
        assertEquals(0, old.getCloses());

        first.close();
        // a double close must not release the other holder's lease
        first.close();
        assertEquals(0, old.getCloses());

        second.close();
        assertEquals(1, old.getCloses());
        assertEquals(0, ((TrackedFetcher) manager.getBuilt().get(1)).getCloses());
    }

    @Test
    public void testCloseRetiresEverything() throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager("configured");
        manager.saveComponent(TrackingFetcherManager.config("runtime"));
        manager.acquire("configured").close();
        Lease<Fetcher> held = manager.acquire("runtime");
        TrackedFetcher configured = manager.getBuilt().get(0);
        TrackedFetcher runtime = (TrackedFetcher) held.get();

        manager.close();
        assertEquals(1, configured.getCloses());
        assertEquals(0, runtime.getCloses(), "closed under an in-flight task");
        assertThrows(IllegalStateException.class, () -> manager.acquire("configured"));

        held.close();
        assertEquals(1, runtime.getCloses());
    }

    @Test
    public void testConcurrentAcquireBuildsOnce() throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager("f");
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Lease<Fetcher>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return manager.acquire("f");
                }));
            }
            start.countDown();
            List<Lease<Fetcher>> leases = new ArrayList<>();
            for (Future<Lease<Fetcher>> f : futures) {
                leases.add(f.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, manager.getBuilt().size());
            manager.close();
            for (int i = 0; i < leases.size(); i++) {
                assertEquals(0, manager.getBuilt().get(0).getCloses());
                leases.get(i).close();
            }
            assertEquals(1, manager.getBuilt().get(0).getCloses());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testRetireRacingAcquireClosesEachInstanceOnce() throws Exception {
        TrackingFetcherManager manager = new TrackingFetcherManager();
        manager.saveComponent(TrackingFetcherManager.config("f"));
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 2000; j++) {
                        try (Lease<Fetcher> lease = manager.acquire("f")) {
                            // throws if handed an instance that was already closed
                            lease.get().fetch("k", null, null);
                        }
                    }
                    return null;
                }));
            }
            futures.add(executor.submit(() -> {
                start.await();
                for (int j = 0; j < 500; j++) {
                    manager.saveComponent(TrackingFetcherManager.config("f"));
                }
                return null;
            }));
            start.countDown();
            for (Future<?> f : futures) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        manager.close();
        for (TrackedFetcher fetcher : manager.getBuilt()) {
            assertEquals(1, fetcher.getCloses());
        }
    }
}
