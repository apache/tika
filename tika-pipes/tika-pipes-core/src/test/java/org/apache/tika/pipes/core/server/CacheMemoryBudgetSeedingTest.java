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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.parser.ParseContext;

public class CacheMemoryBudgetSeedingTest {

    @Test
    public void testDefaultBudgetClampedToHeap() {
        // No -Dtika.pipes.cacheMemoryBudgetBytes in the surefire JVM -> a quarter of max heap
        assertNotNull(PipesServer.CACHE_MEMORY_BUDGET);
        assertEquals(Runtime.getRuntime().maxMemory() / 4, PipesServer.CACHE_MEMORY_BUDGET.getMaxBytes());
    }

    @Test
    public void testInitBranches() {
        long heap = 4L * 1024 * 1024 * 1024;
        assertEquals(heap / 4, PipesServer.initCacheMemoryBudget(null, heap).getMaxBytes());
        assertEquals(512L * 1024 * 1024,
                PipesServer.initCacheMemoryBudget("536870912", heap).getMaxBytes(), "property honoured");
        assertEquals(heap / 4,
                PipesServer.initCacheMemoryBudget("9999999999", heap).getMaxBytes(), "clamped");
        assertEquals(heap / 4,
                PipesServer.initCacheMemoryBudget("not-a-number", heap).getMaxBytes(), "malformed => default");
        assertNull(PipesServer.initCacheMemoryBudget("0", heap), "0 disables");
        assertNull(PipesServer.initCacheMemoryBudget("-1", heap), "negative disables");
        assertEquals(256L * 1024 * 1024,
                PipesServer.initCacheMemoryBudget(null, Long.MAX_VALUE).getMaxBytes(),
                "no heap limit reported => bounded fallback, not an unbounded budget");
    }

    @Test
    public void testSeeding() {
        ParseContext context = new ParseContext();
        PipesServer.seedCacheMemoryBudget(context);
        assertSame(PipesServer.CACHE_MEMORY_BUDGET, context.get(CacheMemoryBudget.class));
    }
}
