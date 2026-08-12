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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.parser.ParseContext;

/**
 * These tests spawn the OS {@code sleep} command directly (unavailable on Windows) rather
 * than mocking {@link Process}, because the property under test -- that a checkpoint fires
 * while a real bounded wait is still in progress, not only after it returns -- is exactly
 * the thing a mock would have to fake.
 */
public class ProcessUtilsTest {

    @Test
    public void testHeartbeatFiresWhileProcessIsStillRunning() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        ParseContext context = new ParseContext();
        context.set(TimeoutLimits.class, new TimeoutLimits(60_000, 60_000));
        ParseTimeout parseTimeout = ParseTimeout.getOrCreate(context);

        ProcessBuilder pb = new ProcessBuilder("sleep", "6");
        Thread runner = new Thread(() -> {
            try {
                ProcessUtils.execute(pb, context, 20_000, 1000, 1000);
            } catch (Exception e) {
                // surfaced via the join()+assert below if it prevents progress
            }
        });
        runner.start();

        try {
            // Wait a generous multiple of HEARTBEAT_INTERVAL_MILLIS (~1000ms), well short of
            // the process's 6s completion, for slack against jitter under a loaded test run.
            Thread.sleep(3000);

            // without a mid-wait checkpoint, millisSinceLastProgress() would be >= 3000
            assertTrue(parseTimeout.millisSinceLastProgress() < 3000,
                    "expected a checkpoint to have fired while the process was still running");
        } finally {
            runner.join(10_000);
        }
    }

    @Test
    public void testNullContextDoesNotThrow() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        ProcessBuilder pb = new ProcessBuilder("sleep", "0");
        FileProcessResult result = ProcessUtils.execute(pb, null, 5_000, 1000, 1000);

        assertTrue(!result.isTimeout());
    }

    /**
     * TIKA-4813 follow-up: a null context has no task to clip against, but
     * {@code ParseTimeout.getOrCreate(null)} used to hand back a detached ParseTimeout built
     * from *default* TimeoutLimits (1 hour) -- silently capping any request above that and
     * contradicting the "granted unclipped" contract the javadoc on this overload promises
     * (e.g. LibPstParser's one-off version-check process, run during initialize() with no
     * ParseContext yet).
     */
    @Test
    public void testNullContextGrantsRequestUnclippedEvenAboveDefaultOneHour() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        long requestedTimeoutMillis = TimeoutLimits.DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS + 60_000;
        ProcessBuilder pb = new ProcessBuilder("sleep", "0");
        FileProcessResult result = ProcessUtils.execute(pb, null, requestedTimeoutMillis, 1000, 1000);

        assertFalse(result.isTimeout());
        assertFalse(result.isClippedByRemaining(),
                "a null context must not silently clip the request against a default-TimeoutLimits budget");
        assertTrue(result.getGrantedTimeoutMillis() == requestedTimeoutMillis,
                "granted (" + result.getGrantedTimeoutMillis() + ") must equal requested ("
                        + requestedTimeoutMillis + ") when there is no context to clip against");
    }

    @Test
    public void testTimeoutStillBoundsTheWait() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder("sleep", "5");
        FileProcessResult result = ProcessUtils.execute(pb, null, 800, 1000, 1000);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isTimeout(), "a 5s sleep with an 800ms budget must time out");
        assertTrue(elapsed < 4_000,
                "the polling rewrite must still honor the timeout, not wait for the full sleep; took " + elapsed + "ms");
    }

    @Test
    public void testCheckCommandDefaultTimeoutStillWorksForAFastCommand() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        assertTrue(ProcessUtils.checkCommand(new String[]{"true"}),
                "a fast, well-behaved command must still succeed under the new default");
    }

    @Test
    public void testCheckCommandCustomTimeoutBoundsASlowCommand() {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        long start = System.currentTimeMillis();
        boolean result = ProcessUtils.checkCommandWithTimeout(new String[]{"sleep", "5"}, 500);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result, "a command that outlives its timeout must report failure");
        assertTrue(elapsed < 4_000,
                "checkCommandWithTimeout must honor its own timeout, not the default; took " + elapsed + "ms");
    }
}
