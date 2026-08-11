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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.ParseContext;

public class ParseTimeoutTest {

    @Test
    public void testInitialTimestamp() {
        long before = System.currentTimeMillis();
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits());
        long after = System.currentTimeMillis();

        assertTrue(timeout.getLastProgressMillis() >= before);
        assertTrue(timeout.getLastProgressMillis() <= after);
        assertTrue(timeout.getStartMillis() >= before);
        assertTrue(timeout.getStartMillis() <= after);
    }

    @Test
    public void testCheckpointAdvancesTimestamp() throws Exception {
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits());
        long initial = timeout.getLastProgressMillis();

        Thread.sleep(20);
        timeout.checkpoint();

        assertTrue(timeout.getLastProgressMillis() > initial);
    }

    @Test
    public void testConcurrentCheckpoints() throws Exception {
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits());
        int numThreads = 4;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < 100; j++) {
                        timeout.checkpoint();
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
        assertTrue(timeout.getLastProgressMillis() > 0);
    }

    @Test
    public void testBudgetForNeverExceedsRequested() {
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(3_600_000L, 60_000L));
        assertEquals(5_000L, timeout.budgetFor(5_000L));
    }

    @Test
    public void testBudgetForClipsToRemaining() {
        // total task budget of 1000ms; request far more than that -- must clip to ~1000ms
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(1_000L, 1_000L));
        long budget = timeout.budgetFor(600_000L);
        assertTrue(budget <= 1_000L, "budget should be clipped to remaining, was " + budget);
        assertTrue(budget >= 0L);
    }

    @Test
    public void testBudgetForNeverGrowsAcrossACompositionChain() {
        // A tight parser-level request must not be "rescued" back up to a looser value
        // by an outer budget, regardless of order -- min() composes correctly either way.
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(3_600_000L, 60_000L));
        long innerRequested = 500L;
        assertEquals(innerRequested, timeout.budgetFor(innerRequested));
    }

    @Test
    public void testRemainingMillisIsNeverNegative() throws Exception {
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(1L, 1L));
        Thread.sleep(20);
        assertEquals(0L, timeout.remainingMillis());
        assertEquals(0L, timeout.budgetFor(60_000L));
    }

    @Test
    public void testHardDeadlineOverflowGuard() {
        // A huge or MAX_VALUE total must not wrap the deadline negative and expire
        // the task immediately.
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(Long.MAX_VALUE, 60_000L));
        assertEquals(Long.MAX_VALUE, timeout.getHardDeadlineMillis());
        assertEquals(Long.MAX_VALUE, timeout.remainingMillis());
        assertTrue(timeout.budgetFor(60_000L) == 60_000L);
    }

    @Test
    public void testGetOrCreateInstallsAndReusesSameInstance() {
        ParseContext context = new ParseContext();
        ParseTimeout first = ParseTimeout.getOrCreate(context);
        ParseTimeout second = ParseTimeout.getOrCreate(context);

        assertNotNull(first);
        assertSame(first, second, "getOrCreate must be idempotent per context");
        assertSame(first, context.get(ParseTimeout.class));
    }

    @Test
    public void testGetOrCreateUsesTimeoutLimitsFromContext() {
        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(7_200_000L, 300_000L));

        ParseTimeout timeout = ParseTimeout.getOrCreate(context);

        assertEquals(300_000L, timeout.getProgressTimeoutMillis());
    }

    @Test
    public void testCheckpointStaticWithNullContext() {
        // Should not throw
        ParseTimeout.checkpoint(null);
    }

    @Test
    public void testCheckpointStaticWithNoTimeoutInstalled() {
        // Should not throw, and should not install one as a side effect
        ParseContext context = new ParseContext();
        ParseTimeout.checkpoint(context);
        assertEquals(null, context.get(ParseTimeout.class));
    }

    @Test
    public void testCheckpointStaticUpdatesInstalledTimeout() throws Exception {
        ParseContext context = new ParseContext();
        ParseTimeout timeout = ParseTimeout.getOrCreate(context);
        long initial = timeout.getLastProgressMillis();

        Thread.sleep(20);
        ParseTimeout.checkpoint(context);

        assertTrue(timeout.getLastProgressMillis() > initial);
    }

    // ---- misconfiguration validation (design doc §9) ----------------------------------

    @Test
    public void testStartRejectsNegativeTotal() {
        assertThrows(IllegalArgumentException.class,
                () -> ParseTimeout.start(new TimeoutLimits(-1L, 1000L)));
    }

    @Test
    public void testStartRejectsNegativeProgress() {
        assertThrows(IllegalArgumentException.class,
                () -> ParseTimeout.start(new TimeoutLimits(1000L, -1L)));
    }

    @Test
    public void testStartAllowsZeroAsAnAlreadyExhaustedBudget() {
        // Zero is a coherent state (no time left), unlike negative -- must not throw,
        // and must behave as immediately exhausted.
        ParseTimeout timeout = assertDoesNotThrow(() -> ParseTimeout.start(new TimeoutLimits(0L, 0L)));
        assertEquals(0L, timeout.remainingMillis());
    }

    @Test
    public void testBudgetForNonPositiveRequestFallsBackToRemainingInsteadOfZero() {
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(60_000L, 60_000L));

        // A misconfigured 0 or negative per-parser timeout must not silently grant a
        // budget of zero (which would make every call fail instantly with no useful
        // diagnostic) -- it falls back to whatever remains of the task instead.
        assertTrue(timeout.budgetFor(0L) > 0L);
        assertTrue(timeout.budgetFor(-100L) > 0L);
    }

    @Test
    public void testBudgetForSubSecondRequestIsStillHonored() {
        // The sub-second warning is diagnostic only -- it must not alter the granted budget.
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(60_000L, 60_000L));
        assertEquals(500L, timeout.budgetFor(500L));
    }

    @Test
    public void testBudgetForExceedingTotalStillClipsToRemaining() {
        // The "exceeds total" warning is diagnostic only -- min(requested, remaining)
        // still applies exactly as it would without the warning.
        ParseTimeout timeout = ParseTimeout.start(new TimeoutLimits(1_000L, 1_000L));
        long budget = timeout.budgetFor(600_000L);
        assertTrue(budget <= 1_000L, "budget should still be clipped to remaining, was " + budget);
    }
}
