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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class BitReaderTest {

    /**
     * Every read must match a reference reader stepping moveNext()/getCurrent() one bit at a
     * time over the same bytes.
     */
    @Test
    public void testReadsMatchBitByBitReference() throws IOException {
        Random random = new Random(20261104L);
        for (int trial = 0; trial < 200; trial++) {
            byte[] data = new byte[1 + random.nextInt(64)];
            random.nextBytes(data);
            int startByte = random.nextInt(data.length);
            BitReader reader = new BitReader(data, startByte);
            BitReader reference = new BitReader(data, startByte);
            long bitsLeft = (long) (data.length - startByte) * 8;
            while (bitsLeft > 0) {
                int kind = random.nextInt(3);
                if (kind == 0 && bitsLeft >= 128) {
                    assertEquals(GuidUtil.fromMicrosoftBytes(referenceBytes(reference, 128, 16), 0),
                            reader.readGuid());
                    bitsLeft -= 128;
                } else if (kind == 1 && bitsLeft >= 8) {
                    int bytes = 1 + random.nextInt((int) Math.min(bitsLeft / 8, 20));
                    assertArrayEquals(referenceBytes(reference, bytes * 8, bytes),
                            reader.readBytes(bytes));
                    bitsLeft -= bytes * 8L;
                } else {
                    int bits = 1 + random.nextInt((int) Math.min(bitsLeft, 64));
                    assertEquals(LittleEndianBitConverter.toUInt64(
                                    referenceBytes(reference, bits, 8), 0),
                            reader.readUInt64(bits), "trial " + trial + ", width " + bits);
                    bitsLeft -= bits;
                }
            }
            assertThrows(IOException.class, () -> reader.readUInt32(1));
        }
    }

    /**
     * The GUID cache slot depends on high ^ low, so XORing both 8-byte halves of a GUID with the
     * same mask gives a different GUID in the same slot. Reads alternating between the two must
     * each return their own UUID; a repeat read of the resident one is a cache hit.
     */
    /** GUIDs are stored in Microsoft byte order, as in the encryption key root GUID. */
    @Test
    public void testGuidByteOrder() throws IOException {
        byte[] data = {(byte) 0xF8, 0x17, 0x37, 0x4A, 0x14, 0x1C, (byte) 0xE7, 0x49,
                (byte) 0x95, 0x26, (byte) 0x81, (byte) 0xD9, 0x42, (byte) 0xDE, 0x17, 0x41};
        assertEquals(UUID.fromString("4A3717F8-1C14-49E7-9526-81D942DE1741"),
                new BitReader(data, 0).readGuid());
    }

    @Test
    public void testGuidCacheCollision() throws IOException {
        byte[] first = new byte[16];
        new Random(20261106L).nextBytes(first);
        byte[] second = first.clone();
        second[0] ^= (byte) 0x5A;
        second[8] ^= (byte) 0x5A;
        UUID firstUuid = GuidUtil.fromMicrosoftBytes(first, 0);
        UUID secondUuid = GuidUtil.fromMicrosoftBytes(second, 0);

        byte[] data = new byte[16 * 5];
        for (int i = 0; i < 5; i++) {
            System.arraycopy(i % 2 == 0 ? first : second, 0, data, i * 16, 16);
        }
        BitReader reader = new BitReader(data, 0);
        assertEquals(firstUuid, reader.readGuid());
        assertEquals(secondUuid, reader.readGuid());
        assertEquals(firstUuid, reader.readGuid());
        assertEquals(secondUuid, reader.readGuid());

        BitReader repeat = new BitReader(data, 64);
        UUID resident = repeat.readGuid();
        assertEquals(firstUuid, resident);
        assertSame(resident, new BitReader(data, 64).readGuid());
    }

    @Test
    public void testReadPastEndThrows() {
        BitReader reader = new BitReader(new byte[]{1, 2, 3}, 1);
        assertThrows(IOException.class, () -> reader.readUInt32(17));
    }

    private static byte[] referenceBytes(BitReader reference, int bits, int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < bits; i++) {
            if (!reference.moveNext()) {
                throw new IllegalStateException("reference reader ran out of bits");
            }
            if (reference.getCurrent()) {
                result[i >> 3] |= (byte) (1 << (i & 7));
            }
        }
        return result;
    }
}
