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
package org.apache.tika.parser.microsoft.onenote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OneNoteDirectFileResourceTest {

    // more than two 64 KB windows
    private static final int SIZE = 150_000;

    @TempDir
    Path tmp;

    @Test
    public void testReadsMatchTheFileAcrossWindows() throws IOException {
        byte[] data = data();
        try (OneNoteDirectFileResource dif = resource(data)) {
            assertEquals(SIZE, dif.size());
            // single bytes from a window's end into the next one
            dif.position(65_534);
            for (int i = 65_534; i < 65_540; i++) {
                assertEquals(data[i] & 0xFF, dif.read());
            }
            assertEquals(65_540, dif.position());
            // a short read that straddles a window boundary
            dif.position(131_070);
            assertArrayEquals(Arrays.copyOfRange(data, 131_070, 131_080), read(dif, 10));
            // seeking back into an earlier window
            dif.position(3);
            assertArrayEquals(Arrays.copyOfRange(data, 3, 11), read(dif, 8));
            // a read larger than the window
            dif.position(1);
            assertArrayEquals(Arrays.copyOfRange(data, 1, 100_001), read(dif, 100_000));
            assertEquals(100_001, dif.position());
            // a large read into a buffer with no array goes through the window
            dif.position(2);
            ByteBuffer direct = ByteBuffer.allocateDirect(100_000);
            assertEquals(100_000, dif.read(direct));
            byte[] fromDirect = new byte[100_000];
            direct.flip().get(fromDirect);
            assertArrayEquals(Arrays.copyOfRange(data, 2, 100_002), fromDirect);
        }
    }

    @Test
    public void testLargeReadsDoNotCacheDirectBuffers() throws Exception {
        byte[] data = data();
        BufferPoolMXBean pool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream().filter(p -> "direct".equals(p.getName())).findFirst().orElseThrow();
        try (OneNoteDirectFileResource dif = resource(data)) {
            long[] grown = new long[1];
            Exception[] failure = new Exception[1];
            // a fresh thread, so no earlier read has already cached a direct buffer
            Thread reader = new Thread(() -> {
                try {
                    long before = pool.getMemoryUsed();
                    read(dif, 140_000);
                    grown[0] = pool.getMemoryUsed() - before;
                } catch (Exception e) {
                    failure[0] = e;
                }
            });
            reader.start();
            reader.join();
            if (failure[0] != null) {
                throw failure[0];
            }
            assertTrue(grown[0] < 140_000, "direct memory grew by " + grown[0]);
        }
    }

    @Test
    public void testAlternatingRegionsAreReadOnce() throws IOException {
        byte[] data = data();
        try (OneNoteDirectFileResource dif = resource(data)) {
            // a file node list and the property sets it declares, read in turn
            for (int i = 0; i < 100; i++) {
                for (int offset : new int[]{10, 70_000, 140_000}) {
                    dif.position(offset + i);
                    assertEquals(data[offset + i] & 0xFF, dif.read());
                }
            }
            assertEquals(SIZE, dif.bytesFilled());
        }
    }

    @Test
    public void testLittleEndianAcrossAPageBoundary() throws IOException {
        byte[] data = data();
        try (OneNoteDirectFileResource dif = resource(data)) {
            dif.position(65_532);
            long expected = 0;
            for (int i = 7; i >= 0; i--) {
                expected = (expected << 8) | (data[65_532 + i] & 0xFF);
            }
            assertEquals(expected, dif.readLittleEndian(8));
            assertEquals(65_540, dif.position());
        }
    }

    @Test
    public void testEndOfFile() throws IOException {
        try (OneNoteDirectFileResource dif = resource(data())) {
            dif.position(SIZE);
            assertEquals(-1, dif.read());
            dif.position(SIZE - 4);
            assertThrows(IOException.class, () -> read(dif, 8));
            assertThrows(IOException.class, () -> dif.position(SIZE + 1));
            assertThrows(IOException.class, () -> dif.position(-1));
        }
    }

    @Test
    public void testFileShrinkingDuringParseThrows() throws IOException {
        try (OneNoteDirectFileResource dif = resource(data());
                RandomAccessFile raf = new RandomAccessFile(tmp.resolve("data.bin").toFile(),
                        "rw")) {
            raf.setLength(1000);
            dif.position(2000);
            assertThrows(IOException.class, () -> read(dif, 8));
            assertThrows(IOException.class, () -> read(dif, 100_000));
            assertThrows(IOException.class, dif::read);
        }
    }

    private OneNoteDirectFileResource resource(byte[] data) throws IOException {
        Path file = tmp.resolve("data.bin");
        Files.write(file, data);
        return new OneNoteDirectFileResource(file.toFile());
    }

    private static byte[] data() {
        byte[] data = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            data[i] = (byte) (i * 31 + (i >> 8));
        }
        return data;
    }

    private static byte[] read(OneNoteDirectFileResource dif, int len) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(len);
        assertEquals(len, dif.read(buf));
        return buf.array();
    }
}
