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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ParseTimeout;
import org.apache.tika.config.TimeoutLimits;
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
            long initialProgress = parseTimeout.getLastProgressMillis();

            Thread requester = new Thread(() -> {
                try {
                    client.get(server.url(), Map.of(), 20, context);
                } catch (Exception e) {
                    // surfaced via the assertion below if it prevented progress
                }
            });
            requester.start();

            try {
                // Wait a generous multiple of HEARTBEAT_INTERVAL_MILLIS (~1000ms), well short of
                // the server's 6s delay, for slack against jitter under a loaded test run.
                Thread.sleep(3000);
                long midProgress = parseTimeout.getLastProgressMillis();

                assertTrue(midProgress > initialProgress,
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

            String body = client.get(server.url(), Map.of(), 5);

            assertEquals("{\"ok\":true}", body);
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
                client.get(server.url(), Map.of(), 1);
            } catch (Exception e) {
                threw = true;
            }
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(threw, "a 5s server delay with a 1s request timeout must fail");
            assertTrue(elapsed < 4_000,
                    "the async polling rewrite must still honor the request timeout; took " + elapsed + "ms");
        }
    }
}
