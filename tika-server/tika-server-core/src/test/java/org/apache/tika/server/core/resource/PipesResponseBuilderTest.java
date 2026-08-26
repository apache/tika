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
package org.apache.tika.server.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.PipesResult;

/**
 * Pins the documented status-code contract (migration guide, index.adoc) without
 * needing to saturate a real worker pool over HTTP.
 */
public class PipesResponseBuilderTest {

    private static Response build(PipesResult.RESULT_STATUS status, long maxWaitMillis) {
        return PipesParsingHelper.responseBuilder(status, maxWaitMillis).build();
    }

    private static String retryAfter(Response response) {
        return response.getHeaderString(HttpHeaders.RETRY_AFTER);
    }

    @Test
    public void testPoolSaturationIs429WithWaitBasedRetryAfter() {
        Response response = build(PipesResult.RESULT_STATUS.CLIENT_UNAVAILABLE_WITHIN_MS, 30_000);
        assertEquals(429, response.getStatus());
        assertEquals("30", retryAfter(response));
    }

    /** Sub-second waits must clamp to 1, not round down to a meaningless 0. */
    @Test
    public void testRetryAfterClampsToOneSecond() {
        Response response = build(PipesResult.RESULT_STATUS.CLIENT_UNAVAILABLE_WITHIN_MS, 100);
        assertEquals("1", retryAfter(response));
    }

    @Test
    public void testCrashFamilyIs503WithShortRetryAfter() {
        for (PipesResult.RESULT_STATUS status : new PipesResult.RESULT_STATUS[]{
                PipesResult.RESULT_STATUS.TIMEOUT,
                PipesResult.RESULT_STATUS.OOM,
                PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH}) {
            Response response = build(status, 30_000);
            assertEquals(503, response.getStatus(), status.name());
            assertEquals("5", retryAfter(response), status.name());
        }
    }

    @Test
    public void testCallerErrorsAre400WithoutRetryAfter() {
        for (PipesResult.RESULT_STATUS status : new PipesResult.RESULT_STATUS[]{
                PipesResult.RESULT_STATUS.FETCHER_NOT_FOUND,
                PipesResult.RESULT_STATUS.EMITTER_NOT_FOUND}) {
            Response response = build(status, 30_000);
            assertEquals(400, response.getStatus(), status.name());
            assertNull(retryAfter(response), status.name());
        }
    }

    @Test
    public void testIpcPayloadOverflowIs413() {
        assertEquals(413, build(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, 30_000).getStatus());
    }

    @Test
    public void testSuccessIs200WithoutRetryAfter() {
        Response response = build(PipesResult.RESULT_STATUS.PARSE_SUCCESS, 30_000);
        assertEquals(200, response.getStatus());
        assertNull(retryAfter(response));
    }
}
