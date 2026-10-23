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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

/** The accounting contract: the total changes only through reservations, each released once. */
public class RequestBodyBudgetTest {

    @Test
    public void testChargeGrowsToBytesReadAndReleaseRefundsAll() {
        RequestBodyBudget budget = new RequestBodyBudget(100);
        RequestBodyBudget.Reservation r = budget.newReservation();
        assertTrue(r.ensure(30));
        assertTrue(r.ensure(20));
        assertEquals(30, budget.getInFlightBytes());
        assertTrue(r.ensure(70));
        assertEquals(70, budget.getInFlightBytes());
        r.release();
        assertEquals(0, budget.getInFlightBytes());
    }

    @Test
    public void testOverBudgetChargesNothing() {
        RequestBodyBudget budget = new RequestBodyBudget(100);
        RequestBodyBudget.Reservation a = budget.newReservation();
        RequestBodyBudget.Reservation b = budget.newReservation();
        assertTrue(a.ensure(60));
        assertTrue(b.ensure(10));
        assertFalse(b.ensure(50));
        assertEquals(70, budget.getInFlightBytes());
        a.release();
        assertTrue(b.ensure(50));
        assertEquals(50, budget.getInFlightBytes());
        b.release();
        assertEquals(0, budget.getInFlightBytes());
    }

    @Test
    public void testReleaseIsIdempotentAndCloses() {
        RequestBodyBudget budget = new RequestBodyBudget(100);
        RequestBodyBudget.Reservation a = budget.newReservation();
        RequestBodyBudget.Reservation b = budget.newReservation();
        assertTrue(a.ensure(40));
        assertTrue(b.ensure(40));
        a.release();
        a.release();
        assertEquals(40, budget.getInFlightBytes());
        // a late read after completion must not charge again
        assertTrue(a.ensure(90));
        assertEquals(40, budget.getInFlightBytes());
        b.release();
        assertEquals(0, budget.getInFlightBytes());
    }

    @Test
    public void testNonPositiveBudgetRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RequestBodyBudget(0));
        assertThrows(IllegalArgumentException.class, () -> new RequestBodyBudget(-1));
    }

    /** Concurrent charges never exceed the budget, and everything drains to zero. */
    @Test
    public void testConcurrentChargesNeverExceedAndDrain() throws Exception {
        long max = 10_000;
        RequestBodyBudget budget = new RequestBodyBudget(max);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                long seed = t;
                futures.add(pool.submit(() -> {
                    Random random = new Random(seed);
                    for (int i = 0; i < 5_000; i++) {
                        RequestBodyBudget.Reservation r = budget.newReservation();
                        long read = 0;
                        for (int k = 0; k < 4; k++) {
                            read += random.nextInt(1_000);
                            if (!r.ensure(read)) {
                                break;
                            }
                            if (budget.getInFlightBytes() > max) {
                                throw new AssertionError("over budget: " + budget.getInFlightBytes());
                            }
                        }
                        r.release();
                        if (random.nextBoolean()) {
                            r.release();
                        }
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, budget.getInFlightBytes());
    }
}
