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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/**
 * {@code TikaInputStream.get(stream, tmp, ...)}: tmp owns the cache the call creates and never
 * the caller's stream. A scope that disposes tmp without closing the stream must still release
 * the cache's budget reservation; the caller's stream, or a TikaInputStream passed through
 * get() as-is, must never be closed on their behalf.
 */
public class TikaInputStreamOwnershipTest {

    private static final int BIG = 3 * 1024 * 1024; // past StreamCache's 1 MB threshold and
                                                    // ReopenableSource's in-memory floor

    private static byte[] data(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        return data;
    }

    private static class CloseCountingInputStream extends ByteArrayInputStream {
        int closes = 0;

        CloseCountingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closes++;
            super.close();
        }
    }

    private static CacheMemoryBudget budget() {
        return new CacheMemoryBudget(64L * 1024 * 1024);
    }

    @Test
    public void tmpOwnsTheCacheNotTheCallersStream() throws Exception {
        CacheMemoryBudget budget = budget();
        CloseCountingInputStream raw = new CloseCountingInputStream(data(BIG));
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = TikaInputStream.get(raw, tmp, new Metadata());
        tis.enableRewind(budget);
        assertEquals(BIG, IOUtils.toByteArray(tis).length);
        assertTrue(budget.getReservedBytes() > 0, "a cache past the threshold reserves budget");

        tmp.close(); // the TikaInputStream itself is never closed

        assertEquals(0, budget.getReservedBytes(),
                "disposing tmp must release the cache it created and its reservation");
        assertEquals(0, raw.closes, "the caller's stream is not tmp's to close");
    }

    @Test
    public void callersTikaInputStreamIsNotRegistered() throws Exception {
        byte[] data = data(64);
        TikaInputStream outer = TikaInputStream.get(data);
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream same = TikaInputStream.get(outer, tmp, new Metadata());
        assertSame(outer, same, "an existing TikaInputStream is returned as-is");

        tmp.close();

        assertEquals(data.length, IOUtils.toByteArray(outer).length,
                "closing the scope's tmp must not close a caller-supplied stream");
        outer.close();
    }

    @Test
    public void closingBothWaysReleasesOnce() throws Exception {
        CacheMemoryBudget budget = budget();
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis =
                TikaInputStream.get(new ByteArrayInputStream(data(BIG)), tmp, new Metadata());
        tis.enableRewind(budget);
        IOUtils.toByteArray(tis);
        assertTrue(budget.getReservedBytes() > 0);

        tis.close();
        tmp.close();
        tis.close();

        assertEquals(0, budget.getReservedBytes(), "released exactly once, never negative");
    }

    private static TikaInputStream reopenable(byte[] data, TemporaryResources tmp) {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(data.length));
        return TikaInputStream.get(() -> new ByteArrayInputStream(data), tmp, metadata);
    }

    @Test
    public void tmpOwnsAReopenableSourcesRetention() throws Exception {
        CacheMemoryBudget budget = budget();
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = reopenable(data(BIG), tmp);
        tis.enableRewind(budget);
        try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
            assertEquals(BIG, channel.size());
        }
        assertTrue(budget.getReservedBytes() > 0, "retention past the floor reserves budget");

        tmp.close(); // the TikaInputStream itself is never closed

        assertEquals(0, budget.getReservedBytes(),
                "disposing tmp must release the retained buffer's reservation");
    }

    @Test
    public void aPinnedChannelClosesWithTmp() throws Exception {
        CacheMemoryBudget budget = budget();
        TemporaryResources tmp = new TemporaryResources();
        TikaInputStream tis = reopenable(data(BIG), tmp);
        tis.enableRewind(budget);
        SeekableByteChannel channel = tis.getSeekableByteChannel();
        assertTrue(budget.getReservedBytes() > 0);

        tmp.close();   // the scope ends without the channel being closed

        assertFalse(channel.isOpen(), "the channel is tmp's to close");
        assertEquals(0, budget.getReservedBytes(), "and its pin is released");
        channel.close();
        assertEquals(0, budget.getReservedBytes(), "never released twice");
    }
}
