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

import org.apache.tika.TikaTest;
import org.apache.tika.io.CacheMemoryBudget;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * POI sizes its in-memory OLE2 buffer from the header's declared BAT count rather than the
 * actual length, so a 512-byte object can demand hundreds of MB. The in-memory detection path
 * only believes a header the bytes in hand can account for, and reserves that from the
 * budget before POI allocates it.
 */
public class POIFSDeclaredSizeTest extends TikaTest {

    private static final int BAT_COUNT_OFFSET = 0x2C;
    private static final int SECTOR_SHIFT_OFFSET = 0x1E;

    @TempDir
    Path tempDir;

    /** A bare 512-byte OLE2 header declaring {@code batCount} BAT blocks and nothing else. */
    private static byte[] header(int batCount) {
        byte[] data = new byte[512];
        byte[] magic = {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0,
                (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1};
        System.arraycopy(magic, 0, data, 0, magic.length);
        data[SECTOR_SHIFT_OFFSET] = 9;   // 2^9 = 512-byte blocks
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(BAT_COUNT_OFFSET, batCount);
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

    @Test
    public void testHonestHeaderIsBelievedWithinOneBatBlock() throws Exception {
        // header only, declaring one BAT block: 129 sectors, 512 bytes present -- within slack
        try (SeekableByteChannel channel = channelFor(header(1), "modest.ole")) {
            assertEquals((1 + 128) * 512L, POIFSContainerDetector.honestDeclaredSize(channel));
        }
        // two BAT blocks declared by 512 bytes: one block past what the content covers
        try (SeekableByteChannel channel = channelFor(header(2), "twoblocks.ole")) {
            assertEquals(-1, POIFSContainerDetector.honestDeclaredSize(channel));
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
            assertTrue(declared >= bytes.length && declared <= bytes.length + 128 * 512L,
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
