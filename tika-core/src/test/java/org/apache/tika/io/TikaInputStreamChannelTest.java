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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/**
 * TikaInputStream.getSeekableByteChannel() across the source types.
 */
public class TikaInputStreamChannelTest {

    @TempDir
    Path tmp;

    private static byte[] data(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 17 + 3);
        }
        return data;
    }

    private static byte[] readFully(SeekableByteChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) channel.size());
        while (buf.hasRemaining() && channel.read(buf) != -1) {
            // keep reading
        }
        return buf.array();
    }

    @Test
    public void testByteArrayStaysInMemory() throws Exception {
        byte[] data = data(10_000);
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertArrayEquals(data, readFully(channel));
            }
            assertFalse(tis.hasFile(), "in-memory content must not be forced to disk");
        }
    }

    @Test
    public void testFileBacked() throws Exception {
        byte[] data = data(10_000);
        Path p = Files.createTempFile(tmp, "chan", ".bin");
        Files.write(p, data);
        try (TikaInputStream tis = TikaInputStream.get(p)) {
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertInstanceOf(FileChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
        }
    }

    @Test
    public void testUnreadStreamDrainsAndSetsContentLength() throws Exception {
        byte[] data = data(10_000);
        Metadata metadata = new Metadata();
        try (TikaInputStream tis =
                     TikaInputStream.get(new ByteArrayInputStream(data), metadata)) {
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(Long.toString(data.length), metadata.get(HttpHeaders.CONTENT_LENGTH));
            assertEquals(data.length, tis.getLength());
            // read position was not disturbed by the drain
            assertEquals(data[0] & 0xFF, tis.read());
        }
    }

    @Test
    public void testPartiallyReadStreamThrows() throws Exception {
        byte[] data = data(100);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.read();
            assertThrows(IOException.class, tis::getSeekableByteChannel);
        }
    }

    @Test
    public void testPartiallyReadWithRewindEnabledWorks() throws Exception {
        byte[] data = data(100);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind();
            tis.read();
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertArrayEquals(data, readFully(channel));
            }
            assertEquals(data[1] & 0xFF, tis.read());
        }
    }

    @Test
    public void testAfterGetPathServesFileChannel() throws Exception {
        byte[] data = data(100);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.getPath();
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertInstanceOf(FileChannel.class, channel);
                assertArrayEquals(data, readFully(channel));
            }
        }
    }

    @Test
    public void testStreamWithBudgetPastPerObjectThreshold() throws Exception {
        // over the 1MB per-object threshold: without a budget this drains to a spill file,
        // with a budget it stays in memory
        byte[] data = data(2 * 1024 * 1024);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertInstanceOf(FileChannel.class, channel);
            }
        }
        CacheMemoryBudget budget = new CacheMemoryBudget(16L * 1024 * 1024);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind(budget);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertFalse(channel instanceof FileChannel);
                assertArrayEquals(data, readFully(channel));
            }
            assertTrue(budget.getReservedBytes() > 0);
        }
        assertEquals(0, budget.getReservedBytes(), "closing the stream releases the reservation");
    }
}
