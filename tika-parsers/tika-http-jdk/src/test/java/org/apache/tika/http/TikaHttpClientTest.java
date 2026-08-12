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
package org.apache.tika.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.exception.TikaTimeoutException;
import org.apache.tika.http.TikaTestHttpServer.MockResponse;
import org.apache.tika.parser.ParseContext;

public class TikaHttpClientTest {

    @Test
    public void testHeartbeatFiresWhileWaitingForResponse() throws Exception {
        try (TikaTestHttpServer server = new TikaTestHttpServer();
             TikaHttpClient client = TikaHttpClient.build(30)) {
            server.enqueue(new MockResponse(200, "{\"ok\":true}", 6000));

            ParseContext context = new ParseContext();
            context.set(TimeoutLimits.class, new TimeoutLimits(60_000, 60_000));
            ParseTimeout parseTimeout = ParseTimeout.getOrCreate(context);

            Thread requester = new Thread(() -> {
                try {
                    client.get(server.url(), Map.of(), 20_000, context);
                } catch (Exception e) {
                    // surfaced via the assertion below if it prevented progress
                }
            });
            requester.start();

            try {
                // Wait a generous multiple of HEARTBEAT_INTERVAL_MILLIS (~1000ms), well short of
                // the server's 6s delay, for slack against jitter under a loaded test run.
                Thread.sleep(3000);

                // without a mid-wait checkpoint, millisSinceLastProgress() would be >= 3000
                assertTrue(parseTimeout.millisSinceLastProgress() < 3000,
                        "expected a checkpoint to have fired while the request was still in flight");
            } finally {
                requester.join(10_000);
            }
        }
    }

    @Test
    public void testNullContextDoesNotThrow() throws Exception {
        try (TikaTestHttpServer server = new TikaTestHttpServer();
             TikaHttpClient client = TikaHttpClient.build(30)) {
            server.enqueue(new MockResponse(200, "{\"ok\":true}"));

            String body = client.get(server.url(), Map.of(), 5_000);

            assertEquals("{\"ok\":true}", body);
        }
    }

    /**
     * A null context must grant the requested timeout unclipped --
     * {@code ParseTimeout.getOrCreate(null)} used to clip it against a detached
     * default-TimeoutLimits (1 hour) budget. Exercises {@code grantedMillis} directly.
     */
    @Test
    public void testNullContextGrantsRequestUnclippedEvenAboveDefaultOneHour() throws Exception {
        try (TikaHttpClient client = TikaHttpClient.build(30)) {
            long requestedTimeoutMillis = TimeoutLimits.DEFAULT_TOTAL_TASK_TIMEOUT_MILLIS + 60_000;

            assertEquals(requestedTimeoutMillis, client.grantedMillis(requestedTimeoutMillis, null),
                    "a null context must not silently clip the request against a default-TimeoutLimits budget");
        }
    }

    @Test
    public void testRequestTimeoutStillBoundsTheWait() throws Exception {
        try (TikaTestHttpServer server = new TikaTestHttpServer();
             TikaHttpClient client = TikaHttpClient.build(30)) {
            server.enqueue(new MockResponse(200, "{\"ok\":true}", 5000));

            long start = System.currentTimeMillis();
            boolean threw = false;
            try {
                client.get(server.url(), Map.of(), 1_000);
            } catch (Exception e) {
                threw = true;
            }
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(threw, "a 5s server delay with a 1s request timeout must fail");
            assertTrue(elapsed < 4_000,
                    "the async polling rewrite must still honor the request timeout; took " + elapsed + "ms");
        }
    }

    @Test
    public void testSlowBodyAfterHeadersIsBoundedByClientDeadline() throws Exception {
        // TIKA-4813 follow-up: headers arrive immediately, but the body then trickles in
        // 5s later -- well past the task's 1s budget. HttpRequest.timeout() is not a
        // guaranteed bound on the full exchange in every JDK/transport scenario once
        // headers have already arrived; without waitWithHeartbeat enforcing its own
        // deadline, a document with many such calls (or one truly stuck) could stall far
        // past its configured budget instead of failing fast.
        try (TikaTestHttpServer server = new TikaTestHttpServer();
             TikaHttpClient client = TikaHttpClient.build(30)) {
            server.enqueue(new MockResponse(200, "{\"ok\":true}", 0, 5000));

            ParseContext context = new ParseContext();
            context.set(TimeoutLimits.class, new TimeoutLimits(1000, 1000));

            long start = System.currentTimeMillis();
            Exception thrown = null;
            try {
                // Request 30s -- far more than the 1s task budget -- so only the task's
                // own remaining-budget deadline (not the request's own timeout) is what
                // can be bounding this call.
                client.get(server.url(), Map.of(), 30_000, context);
            } catch (Exception e) {
                thrown = e;
            }
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(thrown != null, "a 5s-delayed body under a 1s task budget must fail");
            assertInstanceOf(TikaTimeoutException.class, thrown,
                    "must be reported as a TikaTimeoutException with requested/granted info, not a bare " +
                            "IOException/HttpTimeoutException: " + thrown);
            assertTrue(elapsed < 4_000,
                    "client deadline must fire near the ~1s task budget, not wait for the 5s body; took " +
                            elapsed + "ms");
        }
    }

    @Test
    public void testExhaustedBudgetFailsFastWithoutAttemptingRequest() throws Exception {
        // TIKA-4813 follow-up: granted==0 must fail immediately (like ProcessUtils does
        // for external processes), not be floored up to a 1-second HTTP call -- a
        // document with many post-deadline calls would otherwise pay a full extra second
        // per call instead of failing fast.
        try (TikaTestHttpServer server = new TikaTestHttpServer();
             TikaHttpClient client = TikaHttpClient.build(30)) {
            server.enqueue(new MockResponse(200, "{\"ok\":true}"));

            ParseContext context = new ParseContext();
            // total=0 -> remainingMillis() is already 0 by the time anything checks it
            context.set(TimeoutLimits.class, new TimeoutLimits(0, 10_000));

            long start = System.currentTimeMillis();
            TikaTimeoutException thrown = null;
            try {
                client.get(server.url(), Map.of(), 5_000, context);
            } catch (TikaTimeoutException e) {
                thrown = e;
            }
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(thrown != null, "an already-exhausted budget must throw TikaTimeoutException");
            assertEquals(0, thrown.getGrantedMillis());
            assertTrue(elapsed < 500,
                    "an exhausted budget must fail immediately, not floor up to a 1s HTTP call; took " +
                            elapsed + "ms");
            assertEquals(0, server.getRequestCount(),
                    "no HTTP request should have been attempted at all with a 0ms granted budget");
        }
    }
}
