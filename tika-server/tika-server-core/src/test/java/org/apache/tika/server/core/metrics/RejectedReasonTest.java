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
package org.apache.tika.server.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.server.core.resource.PipesParsingHelper;

/**
 * Ties {@code rejected_total} to the statuses tika-server actually returns, so a new
 * {@link PipesResult.RESULT_STATUS} that maps to 429/503/413 fails here until someone
 * decides which rejection reason it is.
 */
public class RejectedReasonTest {

    private static final Map<PipesResult.RESULT_STATUS, String> EXPECTED = new HashMap<>();

    static {
        EXPECTED.put(PipesResult.RESULT_STATUS.CLIENT_UNAVAILABLE_WITHIN_MS, "busy_429");
        EXPECTED.put(PipesResult.RESULT_STATUS.TIMEOUT, "crash_503");
        EXPECTED.put(PipesResult.RESULT_STATUS.OOM, "crash_503");
        EXPECTED.put(PipesResult.RESULT_STATUS.UNSPECIFIED_CRASH, "crash_503");
        EXPECTED.put(PipesResult.RESULT_STATUS.PAYLOAD_LIMIT_EXCEEDED, "payload_413");
    }

    @Test
    public void testEveryStatusIsClassified() {
        for (PipesResult.RESULT_STATUS status : PipesResult.RESULT_STATUS.values()) {
            int httpStatus = PipesParsingHelper
                    .responseBuilder(status, 1000)
                    .build()
                    .getStatus();
            assertEquals(EXPECTED.get(status), TikaServerMetrics.rejectedReason(httpStatus),
                    status + " maps to HTTP " + httpStatus);
        }
    }

    @Test
    public void testNonRejectionStatusesAreNotCounted() {
        assertNull(TikaServerMetrics.rejectedReason(200));
        assertNull(TikaServerMetrics.rejectedReason(400));
        assertNull(TikaServerMetrics.rejectedReason(500));
    }
}
