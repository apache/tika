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
package org.apache.tika.pipes.core.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.parser.ParseContext;

public class ServerProtocolIOTest {

    private static final long MAX = 3_600_000L;

    @Test
    public void testRequestLimitsOverCapAreClamped() {
        ParseContext request = new ParseContext();
        request.set(TimeoutLimits.class, new TimeoutLimits(Long.MAX_VALUE, Long.MAX_VALUE));
        ParseContext merged = new ParseContext();
        merged.copyFrom(request);

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        TimeoutLimits limits = merged.get(TimeoutLimits.class);
        assertEquals(MAX, limits.getTotalTaskTimeoutMillis());
        assertEquals(MAX, limits.getProgressTimeoutMillis());
    }

    @Test
    public void testUnresolvedJsonRequestLimitsAlsoTriggerClamp() {
        // A request can carry timeout-limits as an unresolved JSON config; after
        // resolveAll the value lives in the merged context's typed slot -- the clamp
        // must key off the request's json entry, not only its typed one.
        ParseContext request = new ParseContext();
        request.setJsonConfig("timeout-limits", "{\"totalTaskTimeoutMillis\": 9999999999}");
        ParseContext merged = new ParseContext();
        // simulate post-resolveAll state
        merged.set(TimeoutLimits.class, new TimeoutLimits(9_999_999_999L, 120_000));

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        assertEquals(MAX, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }

    @Test
    public void testServerConfigLimitsAreTrustedAndNeverClamped() {
        // Operator raised the total in the server's own tika-config; the request carries
        // no limits, so the cap must not apply.
        ParseContext request = new ParseContext();
        ParseContext merged = new ParseContext();
        merged.set(TimeoutLimits.class, new TimeoutLimits(7_200_000L, 120_000));

        ServerProtocolIO.clampRequestTimeoutLimits(request, merged, MAX);

        assertEquals(7_200_000L, merged.get(TimeoutLimits.class).getTotalTaskTimeoutMillis());
    }
}
