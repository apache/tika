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
package org.apache.tika.parser.microsoft.chm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.apache.tika.exception.TikaException;

/**
 * Characterization harness for CHM parsing on malformed / hostile input.
 * <p>
 * CHM is a binary format read from untrusted bytes. The contract for any Tika parser is that
 * bad input surfaces as a {@link TikaException} (here {@link ChmParsingException}), never as a
 * raw {@link RuntimeException} ({@code IllegalArgumentException}, {@code NegativeArraySizeException},
 * {@code ArrayIndexOutOfBoundsException}, {@code NullPointerException}) and never as an
 * {@code OutOfMemoryError}. These tests assert that target. A failing test here documents a spot
 * where malformed input currently escapes as the wrong throwable; the hardening turns it green,
 * while {@code TestChmExtraction} guards that real CHM files still parse.
 */
public class TestChmMalformed {

    // ------------------------------------------------------------------
    // ChmCommons.copyOfRange: the shared offset chokepoint. Malformed CHM
    // offsets/lengths reach it as out-of-range (from, to) after being cast
    // from parsed uint32/uint64 fields. It already declares throws TikaException.
    // ------------------------------------------------------------------

    @Test
    public void testCopyOfRangeToBeyondLength() {
        byte[] data = new byte[16];
        assertThrows(TikaException.class, () -> ChmCommons.copyOfRange(data, 0, 32));
    }

    @Test
    public void testCopyOfRangeFromGreaterThanTo() {
        byte[] data = new byte[16];
        assertThrows(TikaException.class, () -> ChmCommons.copyOfRange(data, 12, 4));
    }

    @Test
    public void testCopyOfRangeNegativeFrom() {
        // a wrapped long->int offset can land negative
        byte[] data = new byte[16];
        assertThrows(TikaException.class, () -> ChmCommons.copyOfRange(data, -1, 4));
    }

    @Test
    public void testCopyOfRangeFromBeyondLength() {
        byte[] data = new byte[16];
        assertThrows(TikaException.class, () -> ChmCommons.copyOfRange(data, 32, 40));
    }

    // ------------------------------------------------------------------
    // ChmExtractor end-to-end: the ctor parses ITSF/ITSP/directory eagerly.
    // ------------------------------------------------------------------

    @Test
    public void testTruncatedShorterThanItsfHeader() {
        // shorter than CHM_ITSF_V3_LEN, which the ctor immediately copyOfRange's out
        byte[] tiny = new byte[10];
        assertThrows(TikaException.class,
                () -> new ChmExtractor(new ByteArrayInputStream(tiny)));
    }

    @Test
    public void testEmptyInput() {
        assertThrows(TikaException.class,
                () -> new ChmExtractor(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void testItsfLengthButAllZero() {
        // long enough for the first header copy, but not a valid ITSF signature
        byte[] zeros = new byte[512];
        assertThrows(TikaException.class,
                () -> new ChmExtractor(new ByteArrayInputStream(zeros)));
    }

    @Test
    public void testIOExceptionNotSwallowed() {
        // a stream that fails mid-read must surface, not leave a half-built extractor
        // that NPEs later in ChmParser
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("boom");
            }
        };
        assertThrows(IOException.class, () -> new ChmExtractor(broken));
    }

    // ------------------------------------------------------------------
    // ChmLzxcResetTable.enumerateBlockAddresses allocates new long[blockCount] and
    // reads dataRemained/8 addresses. A block count that is huge (uint32 high bit)
    // or smaller than the trailing data must not become a huge/negative allocation
    // or write past the array.
    // ------------------------------------------------------------------

    @Test
    public void testResetTableHugeBlockCountIsCapped() {
        // 40-byte header, no address data; blockCount 0x80000000 is a large unsigned
        // value that must be capped rather than allocated in full or read as negative
        byte[] data = new byte[40];
        data[0] = 0x02;             // version = 2 (little-endian)
        data[7] = (byte) 0x80;      // blockCount = 0x80000000 = 2147483648 unsigned
        ChmLzxcResetTable table = new ChmLzxcResetTable();
        assertDoesNotThrow(() -> table.parse(data, table));
        assertTrue(table.getBlockAddress().length <= 5000);
    }

    @Test
    public void testResetTableBlockCountSmallerThanData() {
        // block count smaller than the trailing address data must not write past
        // the addresses array (previously an AIOOBE)
        byte[] data = new byte[56];  // 40-byte header + room for 2 addresses
        data[0] = 0x02;             // version = 2
        data[4] = 0x01;             // blockCount = 1
        ChmLzxcResetTable table = new ChmLzxcResetTable();
        assertDoesNotThrow(() -> table.parse(data, table));
        assertEquals(1, table.getBlockAddress().length);
    }

    // ------------------------------------------------------------------
    // ChmLzxBlock allocates content of (int) blockLength. A blockLength coming
    // from a crafted reset table that is >= Integer.MAX_VALUE overflows the int
    // (negative content length) and, before that, null-derefs in checkLzxBlock.
    // ------------------------------------------------------------------

    @Test
    public void testLzxBlockOversizeBlockLength() {
        // blockLength >= Integer.MAX_VALUE (also > MAX_CONTENT_SIZE)
        assertThrows(TikaException.class,
                () -> new ChmLzxBlock(0, new byte[16], 0x80000000L, null));
    }

    // ------------------------------------------------------------------
    // Entry name lengths are variable-length ENCINTs. A signed-byte comparison used
    // to cap the decoded value at 127 (corrupting longer names), and BigInteger-based
    // decoders grew without bound on a run of continuation bytes (an O(n^2) DoS).
    // ------------------------------------------------------------------

    @Test
    public void testDecodeEncintSingleByte() {
        assertEquals(0, ChmDirectoryListingSet.decodeEncint(new byte[] {0x00}, 0));
        assertEquals(5, ChmDirectoryListingSet.decodeEncint(new byte[] {0x05}, 0));
        assertEquals(127, ChmDirectoryListingSet.decodeEncint(new byte[] {0x7f}, 0));
    }

    @Test
    public void testDecodeEncintMultiByte() {
        // the high bit signals a continuation byte: 0x81 0x00 = 128, 0x81 0x48 = 200
        assertEquals(128, ChmDirectoryListingSet.decodeEncint(new byte[] {(byte) 0x81, 0x00}, 0));
        assertEquals(200, ChmDirectoryListingSet.decodeEncint(new byte[] {(byte) 0x81, 0x48}, 0));
        assertEquals(16384,
                ChmDirectoryListingSet.decodeEncint(new byte[] {(byte) 0x81, (byte) 0x80, 0x00}, 0));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testChmSectionGetEncintNotUnboundedBigInteger() throws Exception {
        // a section that is all continuation bytes must not accumulate an unbounded
        // BigInteger (previously O(n^2)); the bounded long version returns promptly
        byte[] hostile = new byte[1_000_000];
        Arrays.fill(hostile, (byte) 0xff);
        ChmSection section = new ChmSection(hostile);
        assertDoesNotThrow(section::getEncint);
    }
}
