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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

public class CacheMemoryBudgetTest {

    @Test
    public void testCtorRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new CacheMemoryBudget(0));
        assertThrows(IllegalArgumentException.class, () -> new CacheMemoryBudget(-1));
    }

    @Test
    public void testAllOrNothing() {
        CacheMemoryBudget budget = new CacheMemoryBudget(100);
        assertEquals(60, budget.tryReserve(60));
        // only 40 left; a 60-byte request must reserve nothing, not 40
        assertEquals(0, budget.tryReserve(60));
        assertEquals(60, budget.getReservedBytes());
        assertEquals(40, budget.tryReserve(40));
        assertEquals(0, budget.tryReserve(1));
    }

    @Test
    public void testZeroAndNegativeRequests() {
        CacheMemoryBudget budget = new CacheMemoryBudget(100);
        assertEquals(0, budget.tryReserve(0));
        assertEquals(0, budget.tryReserve(-5));
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testReleaseRestoresCapacity() {
        CacheMemoryBudget budget = new CacheMemoryBudget(100);
        assertEquals(100, budget.tryReserve(100));
        assertEquals(0, budget.tryReserve(1));
        budget.release(30);
        assertEquals(30, budget.tryReserve(30));
        budget.release(100);
        assertEquals(0, budget.getReservedBytes());
        assertEquals(100, budget.tryReserve(100));
    }

    @Test
    public void testConcurrency() throws Exception {
        final CacheMemoryBudget budget = new CacheMemoryBudget(1000);
        final AtomicLong netReserved = new AtomicLong();
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final long seed = 42L + t;
                tasks.add(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 5000; i++) {
                        long n = 1 + random.nextInt(100);
                        long got = budget.tryReserve(n);
                        if (got > 0) {
                            netReserved.addAndGet(got);
                            long reserved = budget.getReservedBytes();
                            assertTrue(reserved <= 1000, "over-reserved: " + reserved);
                            budget.release(got);
                            netReserved.addAndGet(-got);
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> f : executor.invokeAll(tasks)) {
                f.get();
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(0, netReserved.get());
        assertEquals(0, budget.getReservedBytes());
    }
}
