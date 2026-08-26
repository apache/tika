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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
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
 * must reject those on the declared size, not on the real one.
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
    public void testDeclaredSizeFollowsHeaderNotLength() throws Exception {
        // (1 + 3813 * 128) * 512 == 249_889_280, just under POI's 250MB allocation ceiling
        try (SeekableByteChannel channel = channelFor(header(3813), "hostile.ole")) {
            long declared = POIFSContainerDetector.declaredInMemorySize(channel);
            assertEquals(249_889_280L, declared,
                    "a 512-byte object declares a ~238MB in-memory buffer");
            assertEquals(0, channel.position(), "the size probe must not move the channel");
        }
    }

    @Test
    public void testModestHeaderIsNotRejected() throws Exception {
        try (SeekableByteChannel channel = channelFor(header(1), "modest.ole")) {
            assertEquals((1 + 128) * 512L, POIFSContainerDetector.declaredInMemorySize(channel));
        }
    }

    @Test
    public void testTooShortForAHeaderReportsZero() throws Exception {
        try (SeekableByteChannel channel = channelFor(new byte[16], "short.bin")) {
            assertEquals(0, POIFSContainerDetector.declaredInMemorySize(channel),
                    "no header to read; leave the rejection to POI");
        }
    }

    /**
     * End to end: the crafted object must not be opened in memory, so no POIFSFileSystem is
     * retained. Before the declared-size check this allocated ~238MB first.
     */
    @Test
    public void testHostileHeaderDoesNotBecomeAnOpenContainer() throws Exception {
        ParseContext context = new ParseContext();
        context.set(CacheMemoryBudget.class, new CacheMemoryBudget(64L * 1024 * 1024));
        Metadata metadata = new Metadata();
        try (TemporaryResources tmp = new TemporaryResources()) {
            TikaInputStream tis = TikaInputStream.get(
                    new ByteArrayInputStream(header(3813)), tmp, metadata);
            POIFSContainerDetector detector = new POIFSContainerDetector();
            assertTrue(detector.detect(tis, metadata, context) != null);
            assertNull(tis.getOpenContainer(),
                    "a header-only object must not be opened from memory");
        }
    }
}
