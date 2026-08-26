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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.parser.ParseContext;

public class CacheMemoryBudgetSeedingTest {

    @Test
    public void testDefaultBudgetClampedToHeap() {
        // No -Dtika.pipes.cacheMemoryBudgetBytes in the surefire JVM -> the 256MB default,
        // clamped to a quarter of max heap
        assertNotNull(PipesServer.CACHE_MEMORY_BUDGET);
        long expected = Math.min(256L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4);
        assertEquals(expected, PipesServer.CACHE_MEMORY_BUDGET.getMaxBytes());
    }

    @Test
    public void testSeeding() {
        ParseContext context = new ParseContext();
        PipesServer.seedCacheMemoryBudget(context);
        assertSame(PipesServer.CACHE_MEMORY_BUDGET, context.get(CacheMemoryBudget.class));
    }
}
