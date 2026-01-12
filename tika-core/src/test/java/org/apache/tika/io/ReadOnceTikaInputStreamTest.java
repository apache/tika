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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

public class ReadOnceTikaInputStreamTest {

    private static byte[] bytes(String s) {
        return s.getBytes(UTF_8);
    }

    private static String str(byte[] b) {
        return new String(b, UTF_8);
    }

    @Test
    public void testBasicRead() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            byte[] buffer = new byte[data.length];
            int read = tis.read(buffer);
            assertEquals(data.length, read);
            assertArrayEquals(data, buffer);
        }
    }

    @Test
    public void testPosition() throws IOException {
        byte[] data = bytes("ABCDEFGHIJ");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertEquals(0, tis.getPosition());

            tis.read();
            assertEquals(1, tis.getPosition());

            tis.read(new byte[3]);
            assertEquals(4, tis.getPosition());

            tis.skip(2);
            assertEquals(6, tis.getPosition());
        }
    }

    @Test
    public void testMarkResetWithinBuffer() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            // Mark at position 0
            tis.mark(100);

            // Read 5 bytes
            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));
            assertEquals(5, tis.getPosition());

            // Reset to 0
            tis.reset();
            assertEquals(0, tis.getPosition());

            // Read again
            buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));
        }
    }

    @Test
    public void testMarkResetAtNonZeroPosition() throws IOException {
        byte[] data = bytes("ABCDEFGHIJ");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            // Read 3 bytes
            byte[] buf = new byte[3];
            tis.read(buf);
            assertEquals("ABC", str(buf));

            // Mark at position 3
            tis.mark(100);

            // Read 4 more bytes
            buf = new byte[4];
            tis.read(buf);
            assertEquals("DEFG", str(buf));

            // Reset to position 3
            tis.reset();
            assertEquals(3, tis.getPosition());

            // Read again from position 3
            buf = new byte[4];
            tis.read(buf);
            assertEquals("DEFG", str(buf));
        }
    }

    @Test
    public void testPeek() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            byte[] peekBuffer = new byte[5];
            int peeked = tis.peek(peekBuffer);
            assertEquals(5, peeked);
            assertEquals("Hello", str(peekBuffer));

            // Position should still be 0
            assertEquals(0, tis.getPosition());

            // Now read normally
            byte[] readBuffer = new byte[5];
            tis.read(readBuffer);
            assertEquals("Hello", str(readBuffer));
            assertEquals(5, tis.getPosition());
        }
    }

    @Test
    public void testPeekExceedsBuffer() throws IOException {
        byte[] data = new byte[100];
        // Use small buffer size
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(
                new ByteArrayInputStream(data), 50)) {
            byte[] peekBuffer = new byte[100];
            // peek() calls mark() which throws IllegalArgumentException if readlimit > bufferSize
            assertThrows(IllegalArgumentException.class, () -> tis.peek(peekBuffer));
        }
    }

    @Test
    public void testMarkExceedsBufferThrows() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(
                new ByteArrayInputStream(data), 50)) {
            // mark with readlimit > bufferSize should throw
            assertThrows(IllegalArgumentException.class, () -> tis.mark(100));
        }
    }

    @Test
    public void testAddCloseableResourceThrows() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertThrows(UnsupportedOperationException.class,
                    () -> tis.addCloseableResource(new ByteArrayInputStream(new byte[0])));
        }
    }

    @Test
    public void testHasFileReturnsFalse() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertFalse(tis.hasFile());
        }
    }

    @Test
    public void testGetPathReturnsNull() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertNull(tis.getPath());
        }
    }

    @Test
    public void testGetFileReturnsNull() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertNull(tis.getFile());
        }
    }

    @Test
    public void testRewindThrows() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertThrows(UnsupportedOperationException.class, tis::rewind);
        }
    }

    @Test
    public void testCloseShield() throws IOException {
        byte[] data = bytes("Hello");
        ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data);

        assertFalse(tis.isCloseShield());
        tis.setCloseShield();
        assertTrue(tis.isCloseShield());

        // Close should be ignored
        tis.close();

        // Stream should still be readable
        byte[] buf = new byte[5];
        tis.read(buf);
        assertEquals("Hello", str(buf));

        tis.removeCloseShield();
        assertFalse(tis.isCloseShield());
        tis.close();
    }

    @Test
    public void testLength() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertTrue(tis.hasLength());
            assertEquals(5, tis.getLength());
        }
    }

    @Test
    public void testLengthUnknown() throws IOException {
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(
                new ByteArrayInputStream(bytes("Hello")))) {
            assertFalse(tis.hasLength());
            assertEquals(-1, tis.getLength());
        }
    }

    @Test
    public void testOpenContainer() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertEquals(null, tis.getOpenContainer());

            Object container = new Object();
            tis.setOpenContainer(container);
            assertEquals(container, tis.getOpenContainer());
        }
    }

    @Test
    public void testBufferSize() throws IOException {
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(
                new ByteArrayInputStream(bytes("Hello")), 1024)) {
            assertEquals(1024, tis.getBufferSize());
        }
    }

    @Test
    public void testMarkSupported() throws IOException {
        byte[] data = bytes("Hello");
        try (ReadOnceTikaInputStream tis = ReadOnceTikaInputStream.get(data)) {
            assertTrue(tis.markSupported());
        }
    }
}
