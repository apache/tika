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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Conformance test: MemorySeekableByteChannel must behave like a read-only FileChannel over
 * the same bytes -- external code (commons-compress ZipFile/SevenZFile) is handed either and
 * is entitled to spec behavior for both. Each test runs against both kinds.
 */
public class MemorySeekableByteChannelTest {

    private static final byte[] DATA =
            "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);

    private static final List<String> KINDS = Arrays.asList("memory", "file");

    @TempDir
    Path tmp;

    private SeekableByteChannel open(String kind) throws IOException {
        if ("memory".equals(kind)) {
            return new MemorySeekableByteChannel(DATA, DATA.length);
        }
        Path p = Files.createTempFile(tmp, "msbc", ".bin");
        Files.write(p, DATA);
        return FileChannel.open(p, StandardOpenOption.READ);
    }

    @Test
    public void testSequentialRead() throws Exception {
        for (String kind : KINDS) {
            try (SeekableByteChannel channel = open(kind)) {
                assertEquals(DATA.length, channel.size(), kind);
                ByteBuffer buf = ByteBuffer.allocate(10);
                assertEquals(10, channel.read(buf), kind);
                assertArrayEquals(Arrays.copyOfRange(DATA, 0, 10), buf.array(), kind);
                assertEquals(10, channel.position(), kind);
                buf.clear();
                int total = 10;
                int n;
                while ((n = channel.read(buf)) != -1) {
                    total += n;
                    buf.clear();
                }
                assertEquals(DATA.length, total, kind);
            }
        }
    }

    @Test
    public void testPositionRoundTripIncludingBeyondSize() throws Exception {
        for (String kind : KINDS) {
            try (SeekableByteChannel channel = open(kind)) {
                channel.position(5);
                assertEquals(5, channel.position(), kind);
                ByteBuffer one = ByteBuffer.allocate(1);
                channel.read(one);
                assertEquals(DATA[5], one.get(0), kind);

                // Setting a position beyond size is legal; position() must echo it and
                // reads must return EOF
                long beyond = DATA.length + 100L;
                channel.position(beyond);
                assertEquals(beyond, channel.position(), kind);
                one.clear();
                assertEquals(-1, channel.read(one), kind);
                assertEquals(beyond, channel.position(), kind);
                assertEquals(DATA.length, channel.size(), kind);

                channel.position(DATA.length);
                one.clear();
                assertEquals(-1, channel.read(one), kind);
            }
        }
    }

    @Test
    public void testNegativePositionRejected() throws Exception {
        for (String kind : KINDS) {
            try (SeekableByteChannel channel = open(kind)) {
                assertThrows(IllegalArgumentException.class, () -> channel.position(-1), kind);
            }
        }
    }

    @Test
    public void testZeroRemainingBufferReadsZero() throws Exception {
        for (String kind : KINDS) {
            try (SeekableByteChannel channel = open(kind)) {
                assertEquals(0, channel.read(ByteBuffer.allocate(0)), kind);
            }
        }
    }

    @Test
    public void testReadOnly() throws Exception {
        for (String kind : KINDS) {
            try (SeekableByteChannel channel = open(kind)) {
                assertThrows(NonWritableChannelException.class,
                        () -> channel.write(ByteBuffer.allocate(1)), kind);
                assertThrows(NonWritableChannelException.class, () -> channel.truncate(1), kind);
            }
        }
    }

    @Test
    public void testClosedChannel() throws Exception {
        for (String kind : KINDS) {
            SeekableByteChannel channel = open(kind);
            channel.close();
            assertFalse(channel.isOpen(), kind);
            assertThrows(ClosedChannelException.class,
                    () -> channel.read(ByteBuffer.allocate(1)), kind);
            assertThrows(ClosedChannelException.class, channel::position, kind);
            assertThrows(ClosedChannelException.class, () -> channel.position(0), kind);
            assertThrows(ClosedChannelException.class, channel::size, kind);
            channel.close(); // idempotent
        }
    }

    @Test
    public void testPartialLengthView() throws Exception {
        // MemorySeekableByteChannel may expose a prefix of a larger array
        try (SeekableByteChannel channel = new MemorySeekableByteChannel(DATA, 10)) {
            assertEquals(10, channel.size());
            ByteBuffer buf = ByteBuffer.allocate(100);
            assertEquals(10, channel.read(buf));
            assertEquals(-1, channel.read(buf));
        }
    }
}
