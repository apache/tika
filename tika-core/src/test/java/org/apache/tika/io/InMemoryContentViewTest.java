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

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

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
                assertEquals(data.length, view.capacity(), "clear() must not expose the array's slack");
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
            view.get(new byte[100]);
            assertEquals(4_000, channel.position());
        }
    }

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
                tis.rewind();
                assertArrayEquals(data, tis.readAllBytes());
                assertArrayEquals(data, contents(view), "view unaffected by the stream moving");
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
    public void testNullWhenBudgetIsExhausted() throws Exception {
        CacheMemoryBudget budget = new CacheMemoryBudget(4 * 1024 * 1024);
        assertEquals(budget.getMaxBytes(), budget.tryReserve(budget.getMaxBytes()), "precondition");
        byte[] data = data(2 * 1024 * 1024);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(budget);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertNull(TikaInputStream.inMemoryContent(channel), "over threshold with no room => on disk");
                assertTrue(tis.hasFile());
            }
        }
    }

    @Test
    public void testViewOverReopenableSource() throws Exception {
        byte[] data = data(10_000);
        try (TemporaryResources tmp = new TemporaryResources();
             TikaInputStream tis = TikaInputStream.get(() -> new ByteArrayInputStream(data), tmp, null);
             SeekableByteChannel channel = tis.getSeekableByteChannel()) {
            ByteBuffer view = TikaInputStream.inMemoryContent(channel);
            assertNotNull(view, "a re-openable source buffers small content in memory");
            assertEquals(data.length, view.capacity());
            assertArrayEquals(data, contents(view));
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

    /**
     * toString() must never force a spill. The at-risk state is a cache that spilled
     * mid-stream: hasFile() is true but no getPath() has run, so getPath() would drain the
     * rest of the source, flip the source to file mode and write Content-Length.
     */
    @Test
    public void testToStringDoesNotMaterializeContent() throws Exception {
        byte[] data = data(5 * 1024 * 1024);
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, metadata);
            tis.enableRewind(null);
            // read past the 1MB cache threshold but nowhere near EOF
            assertEquals(2 * 1024 * 1024, tis.readNBytes(2 * 1024 * 1024).length);
            assertTrue(tis.hasFile(), "the cache spilled on its own");

            String rendered = tis.toString();
            assertNull(metadata.get(HttpHeaders.CONTENT_LENGTH),
                    "toString must not drain the source into the spill file");
            assertFalse(rendered.contains(tempDir.toString()),
                    "no getPath() has run, so there is no path to name yet");

            // the rest of the stream is still there to read
            assertEquals(3 * 1024 * 1024, tis.readAllBytes().length);
        }
    }

    /** Once a path really exists, toString() names it so an operator can find the file. */
    @Test
    public void testToStringNamesTheSpillFileOnceItExists() throws Exception {
        byte[] data = data(3 * 1024 * 1024);
        try (TemporaryResources tmp = new TemporaryResources()) {
            tmp.setTemporaryFileDirectory(tempDir);
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data), tmp, new Metadata());
            tis.enableRewind(null);
            Path spilled = tis.getPath();
            assertTrue(tis.toString().contains(spilled.toString()));
        }
    }

    /** A file-backed stream reports its path without any spill machinery. */
    @Test
    public void testToStringNamesARealFile() throws Exception {
        Path file = tempDir.resolve("named.bin");
        Files.write(file, data(50));
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            assertTrue(tis.toString().contains(file.toString()));
        }
    }
}
