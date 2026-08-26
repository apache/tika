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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;

/**
 * {@link TikaInputStream#inMemoryContent} hands a library the cache's own array. These pin
 * the contract: the view is exact, read-only, and stays valid for the life of the channel
 * even when the stream is spilled or advanced underneath it.
 */
public class InMemoryContentViewTest {

    @TempDir
    Path tempDir;

    private static byte[] data(int len) {
        byte[] d = new byte[len];
        for (int i = 0; i < len; i++) {
            d[i] = (byte) (i * 31 + 7);
        }
        return d;
    }

    private static byte[] contents(ByteBuffer view) {
        byte[] out = new byte[view.remaining()];
        view.duplicate().get(out);
        return out;
    }

    @Test
    public void testViewIsExactAndReadOnly() throws Exception {
        byte[] data = data(10_000);
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(null);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                ByteBuffer view = TikaInputStream.inMemoryContent(channel);
                assertNotNull(view, "small stream-backed content must be served from memory");
                // exact bounds: the cache array is over-allocated, the view must not be
                assertEquals(data.length, view.remaining());
                assertArrayEquals(data, contents(view));
                assertTrue(view.isReadOnly());
                assertThrows(ReadOnlyBufferException.class, () -> view.put(0, (byte) 1));
            }
        }
    }

    @Test
    public void testViewIsIndependentOfChannelPosition() throws Exception {
        byte[] data = data(5_000);
        try (TikaInputStream tis = TikaInputStream.get(data);
             SeekableByteChannel channel = tis.getSeekableByteChannel()) {
            channel.position(4_000);
            ByteBuffer view = TikaInputStream.inMemoryContent(channel);
            assertEquals(0, view.position());
            assertEquals(data.length, view.limit());
            // reading through the view does not move the channel either
            view.get(new byte[100]);
            assertEquals(4_000, channel.position());
        }
    }

    /** The whole point: a library may hold the view while something else spills the stream. */
    @Test
    public void testViewSurvivesSpillWhileChannelOpen() throws Exception {
        byte[] data = data(20_000);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(null);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                ByteBuffer view = TikaInputStream.inMemoryContent(channel);
                assertNotNull(view);
                // spill + close the memory cache underneath the live view
                Path spilled = tis.getPath();
                assertTrue(Files.exists(spilled));
                assertEquals(data.length, Files.size(spilled));
                assertArrayEquals(data, contents(view), "view must still read the content");
                // and the stream itself is still usable from the file
                tis.rewind();
                assertArrayEquals(data, tis.readAllBytes());
            }
        }
    }

    @Test
    public void testNullOnceContentIsOnDisk() throws Exception {
        // no budget => the cache spills past its 1MB per-object threshold
        byte[] data = data(3 * 1024 * 1024);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(null);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertNull(TikaInputStream.inMemoryContent(channel), "spilled content has no view");
                assertTrue(tis.hasFile());
            }
        }
    }

    @Test
    public void testNullForFileBackedInput() throws Exception {
        Path file = tempDir.resolve("in.bin");
        Files.write(file, data(100));
        try (TikaInputStream tis = TikaInputStream.get(file);
             SeekableByteChannel channel = tis.getSeekableByteChannel()) {
            assertNull(TikaInputStream.inMemoryContent(channel));
        }
    }

    @Test
    public void testNoViewFromClosedChannel() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(data(100))) {
            SeekableByteChannel channel = tis.getSeekableByteChannel();
            channel.close();
            assertThrows(ClosedChannelException.class, () -> TikaInputStream.inMemoryContent(channel));
        }
    }

    /** Budget accounting: the view's pin holds the reservation, closing the channel releases it. */
    @Test
    public void testPinHoldsBudgetUntilChannelCloses() throws Exception {
        byte[] data = data(2 * 1024 * 1024);
        CacheMemoryBudget budget = new CacheMemoryBudget(64L * 1024 * 1024);
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(budget);
            SeekableByteChannel channel = tis.getSeekableByteChannel();
            assertNotNull(TikaInputStream.inMemoryContent(channel), "2MB under a 64MB budget stays in memory");
            assertTrue(budget.getReservedBytes() > 0);
            tis.close();
            assertTrue(budget.getReservedBytes() > 0, "open channel still pins the reservation");
            channel.close();
            assertEquals(0, budget.getReservedBytes(), "last close releases");
        }
    }
}
