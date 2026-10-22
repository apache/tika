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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class LimitsTightenedToTest {

    @Test
    public void testOutputLimitsTighterRequestPassesThrough() {
        OutputLimits request = new OutputLimits(1000, false, 50, 500_000, 10);
        assertSame(request, request.tightenedTo(new OutputLimits()));
    }

    @Test
    public void testOutputLimitsLooserRequestIsClamped() {
        OutputLimits ceiling = new OutputLimits(1000, false, 50, 500_000, 10);
        OutputLimits request = new OutputLimits(OutputLimits.UNLIMITED, true, 10_000,
                Long.MAX_VALUE, 5);

        OutputLimits clamped = request.tightenedTo(ceiling);

        assertEquals(1000, clamped.getWriteLimit());
        assertEquals(50, clamped.getMaxXmlDepth());
        assertEquals(500_000, clamped.getZipBombThreshold());
        // already tighter than the ceiling
        assertEquals(5, clamped.getZipBombRatio());
        // not a ceiling: the request's choice stands
        assertTrue(clamped.isThrowOnWriteLimit());
    }

    @Test
    public void testUnlimitedCeilingAllowsAnyWriteLimit() {
        OutputLimits request = new OutputLimits();
        request.setWriteLimit(OutputLimits.UNLIMITED);
        assertSame(request, request.tightenedTo(new OutputLimits()));
    }

    @Test
    public void testEmbeddedLimits() {
        EmbeddedLimits ceiling = new EmbeddedLimits(5, false, 100, false);

        EmbeddedLimits looser = new EmbeddedLimits(EmbeddedLimits.UNLIMITED, true, 1000, true);
        EmbeddedLimits clamped = looser.tightenedTo(ceiling);
        assertEquals(5, clamped.getMaxDepth());
        assertEquals(100, clamped.getMaxCount());
        assertTrue(clamped.isThrowOnMaxDepth());
        assertTrue(clamped.isThrowOnMaxCount());

        EmbeddedLimits tighter = new EmbeddedLimits(2, false, 10, false);
        assertSame(tighter, tighter.tightenedTo(ceiling));

        // default maxCount is unlimited, so any request count is allowed
        EmbeddedLimits anyCount = new EmbeddedLimits(5, false, 1_000_000, false);
        assertSame(anyCount, anyCount.tightenedTo(new EmbeddedLimits(20, false,
                EmbeddedLimits.UNLIMITED, false)));
    }
}
