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
package org.apache.tika.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class StreamCacheBudgetTest {

    private static final int THRESHOLD = 1024;

    private final TemporaryResources tmp = new TemporaryResources();

    @AfterEach
    public void tearDown() throws IOException {
        tmp.close();
    }

    private static void append(StreamCache cache, int n) throws IOException {
        byte[] data = new byte[n];
        cache.append(data, 0, n);
    }

    @Test
    public void testNoBudgetSpillsAtThreshold() throws Exception {
        try (StreamCache cache = new StreamCache(tmp, null, THRESHOLD, null)) {
            append(cache, THRESHOLD);
            assertFalse(cache.isFileBacked());
            append(cache, 1);
            assertTrue(cache.isFileBacked());
        }
    }

    @Test
    public void testBudgetAllowsGrowthPastThreshold() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(1024 * 1024);
        try (StreamCache cache = new StreamCache(tmp, null, THRESHOLD, budget)) {
            append(cache, 10 * THRESHOLD);
            assertFalse(cache.isFileBacked());
            // channel must be closed: an open channel pins the reservation past cache close
            try (java.nio.channels.SeekableByteChannel channel =
                    cache.getInMemorySeekableByteChannel()) {
                assertNotNull(channel);
            }
            assertTrue(budget.getReservedBytes() >= 9 * THRESHOLD,
                    "capacity beyond the threshold is reserved");
        }
        assertEquals(0, budget.getReservedBytes(), "close() releases the reservation");
    }

    @Test
    public void testUpToThresholdNeverTouchesBudget() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(1);
        try (StreamCache cache = new StreamCache(tmp, null, THRESHOLD, budget)) {
            // an exhausted/tiny budget must not force sub-threshold content to disk
            append(cache, THRESHOLD);
            assertFalse(cache.isFileBacked());
            assertEquals(0, budget.getReservedBytes());
        }
    }

    @Test
    public void testBudgetExhaustedSpillsAndReleases() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(2048);
        try (StreamCache cache = new StreamCache(tmp, null, THRESHOLD, budget)) {
            append(cache, 100 * THRESHOLD);
            assertTrue(cache.isFileBacked());
            assertEquals(0, budget.getReservedBytes(), "spill returns the reservation");
            assertNull(cache.getInMemorySeekableByteChannel());
            assertEquals(100 * THRESHOLD, cache.size());
        }
    }

    @Test
    public void testTwoCachesShareBudget() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(8 * THRESHOLD);
        try (StreamCache first = new StreamCache(tmp, null, THRESHOLD, budget);
                StreamCache second = new StreamCache(tmp, null, THRESHOLD, budget)) {
            append(first, 8 * THRESHOLD);
            assertFalse(first.isFileBacked());
            // pool is (mostly) consumed; the second cache must spill past its threshold
            append(second, 100 * THRESHOLD);
            assertTrue(second.isFileBacked());
            first.close();
            // after the first cache releases, a third can grow again
            try (StreamCache third = new StreamCache(tmp, null, THRESHOLD, budget)) {
                append(third, 4 * THRESHOLD);
                assertFalse(third.isFileBacked());
            }
        }
    }

    @Test
    public void testReadBackIntact() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(1024 * 1024);
        try (StreamCache cache = new StreamCache(tmp, null, THRESHOLD, budget)) {
            byte[] data = new byte[5000];
            for (int i = 0; i < data.length; i++) {
                data[i] = (byte) i;
            }
            cache.append(data, 0, data.length);
            byte[] out = new byte[data.length];
            assertEquals(data.length, cache.readAt(0, out, 0, out.length));
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], out[i]);
            }
        }
    }

    @Test
    public void testChannelOutlivingCacheHoldsReservation() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(1024 * 1024);
        StreamCache cache = new StreamCache(tmp, null, THRESHOLD, budget);
        append(cache, 10 * THRESHOLD);
        java.nio.channels.SeekableByteChannel channel = cache.getInMemorySeekableByteChannel();
        assertNotNull(channel);
        cache.close();
        assertTrue(budget.getReservedBytes() > 0,
                "reservation must be held while a channel still pins the buffer");
        channel.close();
        assertEquals(0, budget.getReservedBytes(), "last channel close releases");
        channel.close(); // idempotent: no double-release
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testUseAfterCloseThrows() throws Exception {
        StreamCache cache = new StreamCache(tmp, null, THRESHOLD, null);
        append(cache, 10);
        cache.close();
        assertThrows(IOException.class, () -> cache.append(new byte[1], 0, 1));
        assertThrows(IOException.class, cache::toFile);
        assertNull(cache.getInMemorySeekableByteChannel());
    }
}
