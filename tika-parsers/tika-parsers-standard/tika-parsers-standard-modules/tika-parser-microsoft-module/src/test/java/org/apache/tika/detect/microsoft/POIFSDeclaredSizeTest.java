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
package org.apache.tika.detect.microsoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.TikaTest;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * POI sizes its in-memory OLE2 buffer from the header's declared BAT count rather than the
 * actual length, so a 512-byte object can demand hundreds of MB. The in-memory detection path
 * refuses a header that declares an implausible multiple of the bytes in hand, and reserves
 * what it does believe from the budget before POI allocates it.
 */
public class POIFSDeclaredSizeTest extends TikaTest {

    private static final int BAT_COUNT_OFFSET = 0x2C;
    private static final int SECTOR_SHIFT_OFFSET = 0x1E;

    @TempDir
    Path tempDir;

    /** A bare 512-byte OLE2 header declaring {@code batCount} BAT blocks and nothing else. */
    private static byte[] header(int batCount) {
        return header(batCount, 9);
    }

    /** As above, with an explicit sector shift: 9 for 512-byte sectors, 12 for 4K. */
    private static byte[] header(int batCount, int sectorShift) {
        byte[] data = new byte[512];
        byte[] magic = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
        System.arraycopy(magic, 0, data, 0, magic.length);
        data[SECTOR_SHIFT_OFFSET] = (byte) sectorShift;
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(BAT_COUNT_OFFSET, batCount);
        return data;
    }

    /** The same header followed by zeros out to {@code totalLen}. */
    private static byte[] headerPaddedTo(int batCount, int totalLen) {
        byte[] data = new byte[totalLen];
        System.arraycopy(header(batCount), 0, data, 0, 512);
        return data;
    }

    private SeekableByteChannel channelFor(byte[] bytes, String name) throws Exception {
        Path p = tempDir.resolve(name);
        Files.write(p, bytes);
        return Files.newByteChannel(p, StandardOpenOption.READ);
    }

    @Test
    public void testLyingHeaderIsRejected() throws Exception {
        // (1 + 3813 * 128) * 512 == 249_889_280, just under POI's 250MB allocation ceiling,
        // declared by 512 bytes of content
        try (SeekableByteChannel channel = channelFor(header(3813), "hostile.ole")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel),
                    "the content cannot account for what the header declares");
            assertEquals(0, channel.position(), "the size probe must not move the channel");
        }
    }

    /**
     * Small objects may reserve up to four BAT blocks regardless of length: at that size the
     * ratio to a few hundred bytes of header says nothing about whether the header is honest.
     */
    @Test
    public void testSmallDeclarationsAreBelievedUpToTheBatFloor() throws Exception {
        // 4 BAT blocks == 262_656 bytes declared by 512 bytes of content: exactly the floor
        try (SeekableByteChannel channel = channelFor(header(4), "at-floor.ole")) {
            assertEquals((1 + 4 * 128) * 512L,
                    POIFSContainerDetector.honestDeclaredSize(channel));
        }
        // 5 BAT blocks == 328_192: the first value past it
        try (SeekableByteChannel channel = channelFor(header(5), "over-floor.ole")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
        }
    }

    /**
     * The floor is counted in sectors, not bytes. A 4K-sector object cannot declare less than
     * one 4MB BAT block however empty it is, so a byte-valued floor tuned for 512-byte sectors
     * would reject every small one of them -- a case no file in the test corpus exercises.
     */
    @Test
    public void testTheBatFloorScalesWithSectorSize() throws Exception {
        // 4 BAT blocks of 4K sectors == 16_781_312 bytes: the floor, not a byte constant
        try (SeekableByteChannel channel = channelFor(header(4, 12), "4k-at-floor.ole")) {
            assertEquals((1 + 4 * 1024) * 4096L,
                    POIFSContainerDetector.honestDeclaredSize(channel));
        }
        try (SeekableByteChannel channel = channelFor(header(5, 12), "4k-over-floor.ole")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
        }
    }

    /** Past the floor the bound is a multiple of the bytes actually in hand. */
    @Test
    public void testLargeDeclarationsAreBoundedByAmplification() throws Exception {
        int actual = 128 * 1024;   // ceiling is 16x this == 2_097_152
        // 31 BAT blocks == 2_032_128 declared: inside the ceiling
        try (SeekableByteChannel channel =
                     channelFor(headerPaddedTo(31, actual), "under-ceiling.ole")) {
            assertEquals((1 + 31 * 128) * 512L,
                    POIFSContainerDetector.honestDeclaredSize(channel));
        }
        // 32 BAT blocks == 2_097_664 declared: the first value past it
        try (SeekableByteChannel channel =
                     channelFor(headerPaddedTo(32, actual), "over-ceiling.ole")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
        }
    }

    /**
     * The regression this bound exists to avoid: real writers reserve BAT capacity ahead of
     * use, so these all declare well past their own length -- SolidWorks by 26 BAT blocks
     * where 16 would do, the encrypted workbook by 12x. Under a one-BAT-block rule every one
     * of them fell back to spooling the object to a temp file.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "testEXCEL_protected_passtika_2.xlsx",
            "testPPT_comment.ppt",
            "testPPT_macros.ppt",
            "testPPT_oleWorkbook.ppt",
            "testsolidworksAssembly2014SP0.SLDASM",
            "testsolidworksDrawing2014SP0.SLDDRW",
            "testsolidworksPart2013SP2.SLDPRT",
            "testsolidworksPart2014SP0.SLDPRT"})
    public void testOverDeclaringRealFilesAreBelieved(String name) throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/" + name)) {
            bytes = is.readAllBytes();
        }
        try (SeekableByteChannel channel = channelFor(bytes, name)) {
            long declared = POIFSContainerDetector.honestDeclaredSize(channel);
            assertTrue(declared > 0, name + " must not be rejected: declared " + declared +
                    " against " + bytes.length + " bytes");
            assertTrue(declared > bytes.length,
                    name + " is only a regression fixture while it over-declares");
        }
    }

    @Test
    public void testRealDocumentHeaderIsHonest() throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/testWORD.doc")) {
            bytes = is.readAllBytes();
        }
        try (SeekableByteChannel channel = channelFor(bytes, "real.doc")) {
            long declared = POIFSContainerDetector.honestDeclaredSize(channel);
            assertTrue(declared >= bytes.length && declared <= bytes.length * 16L,
                    "a real header declares about its own size: " + declared + " vs " + bytes.length);
        }
    }

    @Test
    public void testTooShortForAHeaderIsRejected() throws Exception {
        try (SeekableByteChannel channel = channelFor(new byte[16], "short.bin")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
        }
    }

    /**
     * End to end, and the point of the whole change: an over-declaring file detected from
     * memory is opened in memory and never touches disk. Before the amplification bound these
     * fell to the file path, which materialised a temp file for every one of them.
     */
    @ParameterizedTest
    @ValueSource(strings = {"testPPT_comment.ppt", "testsolidworksPart2013SP2.SLDPRT"})
    public void testOverDeclaringFileIsOpenedFromMemory(String name) throws Exception {
        byte[] bytes;
        try (InputStream is = getResourceAsStream("/test-documents/" + name)) {
            bytes = is.readAllBytes();
        }
        ParseContext context = new ParseContext();
        CacheMemoryBudget budget = new CacheMemoryBudget(1024L * 1024 * 1024);
        context.set(CacheMemoryBudget.class, budget);
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis =
                    TikaInputStream.get(new ByteArrayInputStream(bytes), tmp, metadata);
            new POIFSContainerDetector().detect(tis, metadata, context);
            assertNotNull(tis.getOpenContainer(), name + " must be opened from memory");
            assertFalse(tis.hasFile(), name + " must not have been spooled to disk");
            assertTrue(budget.getReservedBytes() > 0, "the copy must be charged while open");
        }
        assertEquals(0, budget.getReservedBytes(), "released when the stream closes");
    }

    /**
     * End to end. NOTE: this asserts only that the crafted object does not become an open
     * container and leaves nothing charged -- both of which also hold if the declared-size
     * guard is deleted, because POI throws on the truncated read either way. The guard's
     * real effect is the ~238MB POI would allocate first, and measuring that needs
     * com.sun.management, which forbidden-apis bans. The guard's arithmetic and its input
     * channel type are pinned by the unit tests above instead.
     */
    @Test
    public void testHostileHeaderDoesNotBecomeAnOpenContainer() throws Exception {
        ParseContext context = new ParseContext();
        CacheMemoryBudget budget = new CacheMemoryBudget(1024L * 1024 * 1024);
        context.set(CacheMemoryBudget.class, budget);
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(
                    new ByteArrayInputStream(header(3813)), tmp, metadata);
            new POIFSContainerDetector().detect(tis, metadata, context);
            assertNull(tis.getOpenContainer(),
                    "a header-only object must not be opened from memory");
        }
        assertEquals(0, budget.getReservedBytes(), "nothing left charged");
    }

    /** The channel type production actually uses is in-memory, not a file. */
    @Test
    public void testLyingHeaderIsRejectedOverAnInMemoryChannel() throws Exception {
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(header(3813)), tmp,
                    new Metadata());
            tis.enableRewind(null);
            try (SeekableByteChannel channel = tis.getSeekableByteChannel()) {
                assertNotNull(TikaInputStream.inMemoryContent(channel), "precondition: in memory");
                assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
            }
        }
    }
}
