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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class TikaInputStreamTest {

    private static final Logger LOG = LoggerFactory.getLogger(TikaInputStreamTest.class);

    @TempDir
    Path tempDir;

    @Test
    public void testFileBased() throws IOException {
        Path path = createTempFile("Hello, World!");
        TikaInputStream tis = TikaInputStream.get(path);
        assertTrue(tis.hasFile());
        assertNull(tis.getOpenContainer());

        assertEquals(path, TikaInputStream.get(tis).getPath(),
                "The file returned by the getFile() method should" +
                                " be the file used to instantiate a TikaInputStream");

        assertEquals("Hello, World!", readStream(tis),
                "The contents of the TikaInputStream should equal the" +
                        " contents of the underlying file");

        tis.close();
        assertTrue(Files.exists(path),
                "The close() method must not remove the file used to" +
                " instantiate a TikaInputStream");

    }

    @Test
    public void testStreamBased() throws IOException {
        InputStream input = IOUtils.toInputStream("Hello, World!", UTF_8);
        TikaInputStream tis = TikaInputStream.get(input);
        assertFalse(tis.hasFile());
        assertNull(tis.getOpenContainer());

        Path file = TikaInputStream.get(tis).getPath();
        assertTrue(file != null && Files.isRegularFile(file));
        assertTrue(tis.hasFile());
        assertNull(tis.getOpenContainer());

        assertEquals("Hello, World!", readFile(file),
                "The contents of the file returned by the getFile method" +
                        " should equal the contents of the TikaInputStream");

        assertEquals("Hello, World!", readStream(tis),
                "The contents of the TikaInputStream should not get modified" +
                        " by reading the file first");

        tis.close();
        assertFalse(Files.exists(file),
                "The close() method must remove the temporary file created by a TikaInputStream");
    }

    private Path createTempFile(String data) throws IOException {
        Path file = Files.createTempFile(tempDir, "tika-", ".tmp");
        Files.write(file, data.getBytes(UTF_8));
        return file;
    }

    private String readFile(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    private String readStream(InputStream stream) throws IOException {
        return IOUtils.toString(stream, UTF_8);
    }

    @Test
    public void testGetMetadata() throws Exception {
        URL url = TikaInputStreamTest.class.getResource("test.txt");
        Metadata metadata = new Metadata();
        TikaInputStream.get(url, metadata).close();
        assertEquals("test.txt", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(Long.toString(Files.size(Paths.get(url.toURI()))),
                metadata.get(Metadata.CONTENT_LENGTH));
    }

    // ========== New Caching Tests ==========

    @Test
    public void testMarkReset() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            byte[] first = new byte[5];
            tis.read(first);
            assertEquals("Hello", str(first));

            tis.mark(100);

            byte[] next = new byte[2];
            tis.read(next);
            assertEquals(", ", str(next));

            tis.reset();

            byte[] again = new byte[2];
            tis.read(again);
            assertEquals(", ", str(again));
        }
    }

    @Test
    public void testMarkResetAtZero() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.mark(100);

            byte[] buffer = new byte[data.length];
            tis.read(buffer);

            tis.reset();
            assertEquals(0, tis.getPosition());

            byte[] again = new byte[data.length];
            tis.read(again);
            assertArrayEquals(data, again);
        }
    }

    @Test
    public void testMultipleMarkReset() throws IOException {
        byte[] data = bytes("ABCDEFGHIJ");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.mark(100);
            byte[] buf = new byte[3];
            tis.read(buf);
            assertEquals("ABC", str(buf));

            tis.reset();
            tis.mark(100);
            buf = new byte[5];
            tis.read(buf);
            assertEquals("ABCDE", str(buf));

            tis.mark(100);
            buf = new byte[3];
            tis.read(buf);
            assertEquals("FGH", str(buf));

            tis.reset();
            buf = new byte[3];
            tis.read(buf);
            assertEquals("FGH", str(buf));
        }
    }

    @Test
    public void testRewind() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            byte[] buffer = new byte[data.length];
            tis.read(buffer);
            assertEquals(data.length, tis.getPosition());

            tis.rewind();
            assertEquals(0, tis.getPosition());

            byte[] again = new byte[data.length];
            tis.read(again);
            assertArrayEquals(data, again);
        }
    }

    @Test
    public void testGetPathPreservesPosition() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind(); // Enable caching for getPath() support after reading

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals(5, tis.getPosition());

            Path path = tis.getPath();
            assertNotNull(path);
            assertEquals(5, tis.getPosition());

            buf = new byte[2];
            tis.read(buf);
            assertEquals(", ", str(buf));
        }
    }

    @Test
    public void testFileBackedMarkReset() throws IOException {
        Path tempFile = createTempFile("ABCDEFGHIJ");

        try (TikaInputStream tis = TikaInputStream.get(tempFile)) {
            byte[] buf = new byte[3];
            tis.read(buf);
            assertEquals("ABC", str(buf));

            tis.mark(100);

            buf = new byte[4];
            tis.read(buf);
            assertEquals("DEFG", str(buf));

            tis.reset();
            assertEquals(3, tis.getPosition());

            buf = new byte[4];
            tis.read(buf);
            assertEquals("DEFG", str(buf));
        }
    }

    @Test
    public void testSkip() throws IOException {
        byte[] data = bytes("ABCDEFGHIJ");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.skip(3);
            assertEquals(3, tis.getPosition());

            byte[] buf = new byte[4];
            tis.read(buf);
            assertEquals("DEFG", str(buf));
        }
    }

    @Test
    public void testLargeStreamSpillsToFile() throws IOException {
        byte[] data = new byte[2 * 1024 * 1024]; // 2MB
        new Random(42).nextBytes(data);

        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind(); // Enable caching for rewind support

            byte[] buffer = new byte[data.length];
            int totalRead = 0;
            int n;
            while ((n = tis.read(buffer, totalRead, buffer.length - totalRead)) != -1) {
                totalRead += n;
                if (totalRead >= buffer.length) {
                    break;
                }
            }
            assertEquals(data.length, totalRead);

            tis.rewind();
            assertEquals(0, tis.getPosition());

            byte[] again = new byte[data.length];
            totalRead = 0;
            while ((n = tis.read(again, totalRead, again.length - totalRead)) != -1) {
                totalRead += n;
                if (totalRead >= again.length) {
                    break;
                }
            }
            assertArrayEquals(data, again);
        }
    }

    @Test
    public void testResetWithoutMark() throws IOException {
        byte[] data = bytes("Hello");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.read();
            assertThrows(IOException.class, tis::reset);
        }
    }

    @Test
    public void testPeek() throws IOException {
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            byte[] peekBuffer = new byte[5];
            int peeked = tis.peek(peekBuffer);
            assertEquals(5, peeked);
            assertEquals("Hello", str(peekBuffer));

            assertEquals(0, tis.getPosition());

            byte[] readBuffer = new byte[5];
            tis.read(readBuffer);
            assertEquals("Hello", str(readBuffer));
            assertEquals(5, tis.getPosition());
        }
    }

    @Test
    public void testPosition() throws IOException {
        byte[] data = bytes("ABCDEFGHIJ");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
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
    public void testLength() throws IOException {
        byte[] data = bytes("Hello");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            assertTrue(tis.hasLength());
            assertEquals(5, tis.getLength());
        }
    }

    @Test
    public void testCloseShield() throws IOException {
        byte[] data = bytes("Hello");
        TikaInputStream tis = TikaInputStream.get(data);

        assertFalse(tis.isCloseShield());
        tis.setCloseShield();
        assertTrue(tis.isCloseShield());

        tis.close();

        byte[] buf = new byte[5];
        tis.read(buf);
        assertEquals("Hello", str(buf));

        tis.removeCloseShield();
        assertFalse(tis.isCloseShield());
        tis.close();
    }

    // ========== Randomized Tests ==========

    // --- ByteArray Backed Tests ---

    @RepeatedTest(50)
    public void testRandomizedSizeAndTypes() throws Exception {
        int minSz = 0;
        int maxSz = 2 * 1024 * 1024;

        long seed = System.currentTimeMillis();
        Random random = new Random(seed);
        int sz = minSz + random.nextInt(maxSz - minSz + 1);
        BackingType type = BackingType.values()[random.nextInt(BackingType.values().length)];
        runRandomizedTest(sz, type, seed);
    }

    @RepeatedTest(10)
    public void testRandomizedSizeStepsAndTypes() throws Exception {
        for (int sz : new int[]{ 0, 100, 8191, 8192, 8193, 100_000, 2 * 1024 * 1024 }) {
             for (BackingType type : BackingType.values()) {
               runRandomizedTest(sz, type);
             }
        }
    }

    @RepeatedTest(10)
    public void testRandomizedOperationsTest() throws Exception {
        long seed = Instant.now().getEpochSecond();
        for (int sz : new int[]{ 0, 100, 8191, 8192, 8193, 100_000, 2 * 1024 * 1024 }) {
            for (BackingType type : BackingType.values()) {
                runRandomizedOperationsTest(sz, type, seed);
            }
        }
    }




    /**
     * Backing strategy types for TikaInputStream.
     */
    private enum BackingType {
        BYTE_ARRAY,  // TikaInputStream.get(byte[]) - ByteArrayBackedStrategy
        FILE,        // TikaInputStream.get(Path) - FileBackedStrategy
        STREAM       // TikaInputStream.get(InputStream) - StreamBackedStrategy
    }

    /**
     * Reproduce a failing randomized test by specifying the backingType and seed.
     * Enable this test and set the parameters from a failing test's log output.
     * The size is derived from the seed using the same logic as the original test.
     */
    @Disabled("Enable this test to reproduce a specific failure using seed from logs")
    @Test
    public void reproduceRandomizedTestFailure() throws Exception {
        // Set these values from the failing test's log output:
        BackingType backingType = BackingType.STREAM;
        long seed = 1768257360409L;
        int size = 1;
        runRandomizedOperationsTest(size, backingType, seed);
    }

    private void runRandomizedTest(int size, BackingType backingType) throws Exception {
        runRandomizedTest(size, backingType, System.currentTimeMillis());
    }

    private void runRandomizedTest(int size, BackingType backingType, long seed) throws Exception {
        LOG.debug("runRandomizedTest: size={}, backingType={}, seed={}", size, backingType, seed);
        Random random = new Random(seed);

        byte[] data = new byte[size];
        if (size > 0) {
            random.nextBytes(data);
        }
        String expectedDigest = computeDigest(data);

        try (TikaInputStream tis = createTikaInputStream(data, backingType)) {
            byte[] readData = readAllBytes(tis);
            String actualDigest = computeDigest(readData);
            assertEquals(expectedDigest, actualDigest,
                    "Digest mismatch for size=" + size + ", backingType=" + backingType + ", seed=" + seed);
            assertEquals(size, readData.length);
        }

        try (TikaInputStream tis = createTikaInputStream(data, backingType)) {
            byte[] readData = readAllBytes(tis);
            tis.rewind();
            byte[] rereadData = readAllBytes(tis);
            assertArrayEquals(readData, rereadData,
                    "Data mismatch after rewind for size=" + size + ", backingType=" + backingType + ", seed=" + seed);
        }
    }

    private void runRandomizedOperationsTest(int size, BackingType backingType, long seed) throws Exception {
        LOG.debug("runRandomizedOperationsTest: size={}, backingType={}, seed={}", size, backingType, seed);
        String ctx = "size=" + size + ", backingType=" + backingType + ", seed=" + seed;
        Random random = new Random(seed);
        // Skip the first random value (used for size selection in the calling test)
        random.nextInt();

        byte[] data = new byte[size];
        if (size > 0) {
            random.nextBytes(data);
        }

        try (TikaInputStream tis = createTikaInputStream(data, backingType)) {
            int position = 0;
            int markPosition = -1;
            int numOps = random.nextInt(50) + 10;

            for (int op = 0; op < numOps; op++) {
                int operation = random.nextInt(9);

                switch (operation) {
                    case 0: // single byte read
                        if (position < size) {
                            int expectedByte = data[position] & 0xFF;
                            int actualByte = tis.read();
                            assertEquals(expectedByte, actualByte,
                                    "Single byte read mismatch at position " + position + ", " + ctx);
                            position++;
                        } else {
                            assertEquals(-1, tis.read(),
                                    "Expected EOF at position " + position + ", " + ctx);
                        }
                        break;

                    case 1: // bulk read
                        // Ensure readLen is at least 1 to avoid zero-length buffer reads
                        int readLen = random.nextInt(Math.min(1000, size + 100)) + 1;
                        byte[] buffer = new byte[readLen];
                        int bytesRead = tis.read(buffer);
                        if (position >= size) {
                            assertTrue(bytesRead <= 0,
                                    "Expected EOF, got " + bytesRead + " bytes, " + ctx);
                        } else {
                            assertTrue(bytesRead > 0,
                                    "Expected data, got " + bytesRead + ", " + ctx);
                            for (int i = 0; i < bytesRead; i++) {
                                assertEquals(data[position + i], buffer[i],
                                        "Bulk read mismatch at offset " + i + ", " + ctx);
                            }
                            position += bytesRead;
                        }
                        break;

                    case 2: // skip
                        long skipAmount = random.nextInt(size + 100);
                        long skipped = tis.skip(skipAmount);
                        assertTrue(skipped >= 0 && skipped <= skipAmount,
                                "Skip returned invalid value " + skipped + ", " + ctx);
                        position += (int) skipped;
                        if (position > size) {
                            position = size;
                        }
                        break;

                    case 3: // mark
                        int readLimit = random.nextInt(size + 100) + 1;
                        tis.mark(readLimit);
                        markPosition = position;
                        break;

                    case 4: // reset
                        if (markPosition >= 0) {
                            tis.reset();
                            position = markPosition;
                            markPosition = -1;
                        }
                        break;

                    case 5: // rewind
                        tis.rewind();
                        position = 0;
                        markPosition = -1;
                        break;

                    case 6: // getPath - forces spill to file for stream-backed
                        Path path = tis.getPath();
                        assertNotNull(path, "getPath() returned null, " + ctx);
                        assertTrue(Files.exists(path), "Path doesn't exist, " + ctx);
                        assertEquals(size, Files.size(path), "File size mismatch, " + ctx);
                        break;

                    case 7: // peek
                        int peekLen = random.nextInt(Math.min(100, size + 10)) + 1;
                        byte[] peekBuf = new byte[peekLen];
                        int peeked = tis.peek(peekBuf);
                        if (position >= size) {
                            assertTrue(peeked <= 0, "Expected EOF on peek, " + ctx);
                        } else {
                            assertTrue(peeked > 0, "Expected data on peek, " + ctx);
                            for (int i = 0; i < peeked; i++) {
                                assertEquals(data[position + i], peekBuf[i],
                                        "Peek mismatch at offset " + i + ", " + ctx);
                            }
                        }
                        // position doesn't change after peek, but peek() uses mark/reset
                        // internally which overwrites any existing mark
                        markPosition = -1;
                        break;

                    case 8: // available
                        int avail = tis.available();
                        assertTrue(avail >= 0, "available() returned negative, " + ctx);
                        break;

                    default:
                        break;
                }
                assertEquals(position, tis.getPosition(),
                        "Position mismatch after operation " + operation + ", " + ctx);
            }

            tis.rewind();
            assertEquals(0, tis.getPosition(), "Position should be 0 after rewind, " + ctx);
            byte[] finalRead = readAllBytes(tis);
            String expectedDigest = computeDigest(data);
            String actualDigest = computeDigest(finalRead);
            assertEquals(expectedDigest, actualDigest, "Final digest mismatch, " + ctx);
        }
    }

    @Test
    public void testMarkBeyondStreamLength() throws Exception {
        byte[] data = bytes("Short");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.mark(1000);
            byte[] buf = readAllBytes(tis);
            assertEquals("Short", str(buf));
            tis.reset();
            assertEquals(0, tis.getPosition());
            buf = readAllBytes(tis);
            assertEquals("Short", str(buf));
        }
    }

    @Test
    public void testSkipBeyondStreamLength() throws Exception {
        byte[] data = bytes("Short");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            long skipped = tis.skip(1000);
            assertEquals(5, skipped);
            assertEquals(-1, tis.read());
        }
    }

    @Test
    public void testMarkResetSkipCombination() throws Exception {
        byte[] data = bytes("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.mark(100);
            tis.skip(10);
            assertEquals(10, tis.getPosition());

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("KLMNO", str(buf));

            tis.reset();
            assertEquals(0, tis.getPosition());

            buf = new byte[5];
            tis.read(buf);
            assertEquals("ABCDE", str(buf));
        }
    }

    @Test
    public void testFileBackedMarkResetSkip() throws Exception {
        byte[] data = bytes("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        Path tempFile = createTempFile("ABCDEFGHIJKLMNOPQRSTUVWXYZ");

        try (TikaInputStream tis = TikaInputStream.get(tempFile)) {
            tis.skip(5);
            assertEquals(5, tis.getPosition());

            tis.mark(100);

            tis.skip(10);
            assertEquals(15, tis.getPosition());

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("PQRST", str(buf));

            tis.reset();
            assertEquals(5, tis.getPosition());

            buf = new byte[5];
            tis.read(buf);
            assertEquals("FGHIJ", str(buf));
        }
    }

    // ========== enableRewind() Tests ==========

    @Test
    public void testEnableRewindByteArrayNoOp() throws Exception {
        // ByteArraySource is always rewindable - enableRewind() is no-op
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(data)) {
            tis.enableRewind(); // Should be no-op

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));

            tis.rewind();
            assertEquals(0, tis.getPosition());

            buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));
        }
    }

    @Test
    public void testEnableRewindFileNoOp() throws Exception {
        // FileSource is always rewindable - enableRewind() is no-op
        Path tempFile = createTempFile("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(tempFile)) {
            tis.enableRewind(); // Should be no-op

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));

            tis.rewind();
            assertEquals(0, tis.getPosition());

            buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));
        }
    }

    @Test
    public void testEnableRewindStreamEnablesCaching() throws Exception {
        // CachingSource starts in passthrough mode, enableRewind() enables caching
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind(); // Enable caching mode

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));

            tis.rewind();
            assertEquals(0, tis.getPosition());

            buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));
        }
    }

    @Test
    public void testEnableRewindAfterReadThrows() throws Exception {
        // enableRewind() must be called at position 0
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.read(); // Read one byte, position is now 1
            assertEquals(1, tis.getPosition());

            assertThrows(IllegalStateException.class, tis::enableRewind,
                    "enableRewind() should throw when position != 0");
        }
    }

    @Test
    public void testEnableRewindMultipleCallsNoOp() throws Exception {
        // Multiple enableRewind() calls should be safe (no-op after first)
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            tis.enableRewind();
            tis.enableRewind(); // Should be no-op
            tis.enableRewind(); // Should be no-op

            byte[] buf = readAllBytes(tis);
            assertEquals("Hello, World!", str(buf));

            tis.rewind();
            buf = readAllBytes(tis);
            assertEquals("Hello, World!", str(buf));
        }
    }

    @Test
    public void testStreamWithoutEnableRewindCannotRewind() throws Exception {
        // Without enableRewind(), CachingSource is in passthrough mode
        // rewind() should fail after reading in passthrough mode
        byte[] data = bytes("Hello, World!");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            // Don't call enableRewind()

            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("Hello", str(buf));

            // rewind() internally calls reset() which calls seekTo()
            // In passthrough mode, seekTo() fails if not at current position
            assertThrows(IOException.class, tis::rewind,
                    "rewind() should fail in passthrough mode after reading");
        }
    }

    @Test
    public void testMarkResetThenEnableRewind() throws Exception {
        // Test transitioning from passthrough mode (using BufferedInputStream's mark/reset)
        // to caching mode via enableRewind()
        byte[] data = bytes("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data))) {
            // Passthrough mode - use BufferedInputStream's mark/reset
            tis.mark(100);
            byte[] buf = new byte[5];
            tis.read(buf);
            assertEquals("ABCDE", str(buf));

            tis.reset();  // Back to 0
            assertEquals(0, tis.getPosition());

            // Another mark/reset cycle in passthrough mode
            tis.mark(100);
            buf = new byte[10];
            tis.read(buf);
            assertEquals("ABCDEFGHIJ", str(buf));

            tis.reset();  // Back to 0 again
            assertEquals(0, tis.getPosition());

            // Now enable rewind (switches to caching mode)
            tis.enableRewind();

            // Should still work with caching mode
            buf = new byte[5];
            tis.read(buf);
            assertEquals("ABCDE", str(buf));

            tis.rewind();  // Full rewind now works
            assertEquals(0, tis.getPosition());

            buf = readAllBytes(tis);
            assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", str(buf));
        }
    }

    // ========== Helper Methods ==========

    private TikaInputStream createTikaInputStream(byte[] data, boolean fileBacked) throws IOException {
        return createTikaInputStream(data, fileBacked ? BackingType.FILE : BackingType.STREAM);
    }

    private TikaInputStream createTikaInputStream(byte[] data, BackingType backingType) throws IOException {
        switch (backingType) {
            case BYTE_ARRAY:
                return TikaInputStream.get(data);
            case FILE:
                Path file = Files.createTempFile(tempDir, "test_", ".bin");
                Files.write(file, data);
                return TikaInputStream.get(file);
            case STREAM:
                TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(data));
                tis.enableRewind(); // Enable caching for rewind support in tests
                return tis;
            default:
                throw new IllegalArgumentException("Unknown backing type: " + backingType);
        }
    }

    private byte[] readAllBytes(TikaInputStream tis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = tis.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    private String computeDigest(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(UTF_8);
    }

    private static String str(byte[] b) {
        return new String(b, UTF_8);
    }
}
