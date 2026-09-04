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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.function.IOSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ReopenableSourceTest {

    private static final int FLOOR = 1024 * 1024; // ReopenableSource.IN_MEMORY_FLOOR

    private final TemporaryResources tmp = new TemporaryResources();

    @AfterEach
    public void tearDown() throws IOException {
        tmp.close();
    }

    private static byte[] data(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        return data;
    }

    private static IOSupplier<InputStream> countingOpener(byte[] data, AtomicInteger opens) {
        return () -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(data);
        };
    }

    private static byte[] readFully(SeekableByteChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        while (buf.hasRemaining() && channel.read(buf) != -1) {
            // keep reading
        }
        return buf.array();
    }

    @Test
    public void testReadAndRewindReopens() throws Exception {
        byte[] data = data(1000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            source.enableRewind(null);
            byte[] first = source.readAllBytes();
            assertArrayEquals(data, first);
            assertEquals(1, opens.get());
            source.seekTo(0);
            byte[] second = source.readAllBytes();
            assertArrayEquals(data, second);
            assertEquals(2, opens.get());
        }
    }

    @Test
    public void testMarkReset() throws Exception {
        byte[] data = data(1000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            byte[] buf = new byte[100];
            source.readNBytes(buf, 0, buf.length);
            source.mark(0);
            source.readNBytes(buf, 0, buf.length);
            source.reset();
            int b = source.read();
            assertEquals(data[100] & 0xFF, b);
        }
    }

    @Test
    public void testResetWithoutMarkThrows() throws Exception {
        try (ReopenableSource source = new ReopenableSource(
                countingOpener(data(10), new AtomicInteger()), tmp, 10, null)) {
            assertThrows(IOException.class, source::reset);
        }
    }

    @Test
    public void testEnableRewindAfterReadThrows() throws Exception {
        try (ReopenableSource source = new ReopenableSource(
                countingOpener(data(10), new AtomicInteger()), tmp, 10, null)) {
            source.enableRewind(null);  // at 0: fine
            source.read();
            assertThrows(IOException.class, () -> source.enableRewind(null));
        }
    }

    @Test
    public void testGetPathSpillsOnceWithSuffix() throws Exception {
        byte[] data = data(1000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, ".zip")) {
            assertFalse(source.hasPath());
            Path p = source.getPath(null);
            assertTrue(source.hasPath());
            assertTrue(p.getFileName().toString().endsWith(".zip"));
            assertEquals(1, opens.get());
            assertEquals(p, source.getPath(null));
            assertEquals(1, opens.get());
            // post-spill reads come from the file, not the opener
            source.seekTo(0);
            assertArrayEquals(data, source.readAllBytes());
            assertEquals(1, opens.get());
        }
    }

    @Test
    public void testChannelSmallContentInMemoryAndRetained() throws Exception {
        byte[] data = data(50_000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(MemorySeekableByteChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(1, opens.get());
            assertFalse(source.hasPath());
            // second channel served from the retained buffer -- no re-read
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(1, opens.get());
        }
    }

    @Test
    public void testChannelDoesNotDisturbReadPosition() throws Exception {
        byte[] data = data(1000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            byte[] buf = new byte[10];
            source.readNBytes(buf, 0, buf.length);
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                readFully(channel);
            }
            int b = source.read();
            assertEquals(data[10] & 0xFF, b);
        }
    }

    @Test
    public void testChannelOverFloorWithoutBudgetSpills() throws Exception {
        byte[] data = data(FLOOR + 1);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(FileChannel.class, channel);
                assertEquals(data.length, channel.size());
            }
            assertTrue(source.hasPath());
            // declared length routed straight to spill: exactly one full read
            assertEquals(1, opens.get());
        }
    }

    @Test
    public void testChannelOverFloorWithBudgetStaysInMemory() throws Exception {
        byte[] data = data(FLOOR + 100);
        AtomicInteger opens = new AtomicInteger();
        CacheMemoryBudget budget = new CacheMemoryBudget(16L * 1024 * 1024);
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            source.enableRewind(budget);
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(MemorySeekableByteChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
            assertFalse(source.hasPath());
            assertTrue(budget.getReservedBytes() > 0, "capacity beyond the floor is reserved");
        }
        assertEquals(0, budget.getReservedBytes(), "close() releases the retained reservation");
    }

    @Test
    public void testChannelOutlivingSourceHoldsReservation() throws Exception {
        byte[] data = data(FLOOR + 100);
        CacheMemoryBudget budget = new CacheMemoryBudget(16L * 1024 * 1024);
        ReopenableSource source = new ReopenableSource(
                countingOpener(data, new AtomicInteger()), tmp, data.length, null);
        source.enableRewind(budget);
        SeekableByteChannel channel = source.getSeekableByteChannel();
        source.close();
        assertTrue(budget.getReservedBytes() > 0,
                "reservation must be held while a channel still pins the retained buffer");
        assertArrayEquals(data, readFully(channel));
        channel.close();
        assertEquals(0, budget.getReservedBytes(), "last channel close releases");
        channel.close(); // idempotent: no double-release
        assertEquals(0, budget.getReservedBytes());
    }

    @Test
    public void testChannelBudgetExhaustedSpills() throws Exception {
        byte[] data = data(FLOOR + 2048);
        AtomicInteger opens = new AtomicInteger();
        CacheMemoryBudget budget = new CacheMemoryBudget(1024);  // too small for beyond-floor
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, data.length, null)) {
            source.enableRewind(budget);
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(FileChannel.class, channel);
                assertEquals(data.length, channel.size());
            }
            assertEquals(0, budget.getReservedBytes(), "failed reservation fully released");
        }
    }

    /**
     * A declared length far above the content, against a budget that could never
     * grant it: the claim must not be what gets reserved, or a 500-byte payload is
     * pushed to disk by a number the file made up.
     */
    @Test
    public void testLyingDeclaredLengthDoesNotReserveOrSpill() throws Exception {
        byte[] data = data(500);
        AtomicInteger opens = new AtomicInteger();
        CacheMemoryBudget budget = new CacheMemoryBudget(1024);
        try (ReopenableSource source = new ReopenableSource(countingOpener(data, opens), tmp,
                50L * 1024 * 1024, null)) {
            source.enableRewind(budget);
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(MemorySeekableByteChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
            assertFalse(source.hasPath());
            assertEquals(0, budget.getReservedBytes(), "nothing reserved for a 500 byte payload");
            assertEquals(500, source.getLength());
        }
    }

    @Test
    public void testLyingDeclaredLengthCorrected() throws Exception {
        byte[] data = data(500);
        AtomicInteger opens = new AtomicInteger();
        // declared length lies high
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, 400_000, null)) {
            assertEquals(400_000, source.getLength());
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertEquals(500, channel.size());
            }
            assertEquals(500, source.getLength(), "full read overrides the lying declared length");
        }
        // declared length lies high, spill path
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, 400_000, null)) {
            source.getPath(null);
            assertEquals(500, source.getLength());
        }
        // declared length lies low: growth still captures everything
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, 100, null)) {
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(500, source.getLength());
        }
    }

    @Test
    public void testUnknownLengthChannel() throws Exception {
        byte[] data = data(100_000);
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(data, opens), tmp, -1, null)) {
            assertEquals(-1, source.getLength());
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertInstanceOf(MemorySeekableByteChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(data.length, source.getLength());
        }
    }

    @Test
    public void testEmptyContent() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        try (ReopenableSource source =
                     new ReopenableSource(countingOpener(new byte[0], opens), tmp, 0, null)) {
            try (SeekableByteChannel channel = source.getSeekableByteChannel()) {
                assertEquals(0, channel.size());
                assertEquals(-1, channel.read(ByteBuffer.allocate(1)));
            }
        }
    }
}
