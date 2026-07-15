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
package org.apache.tika.parser.image;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class ExtendedXmpTest {

    private static final String STD = "http://ns.adobe.com/xap/1.0/\u0000";
    private static final String EXT = "http://ns.adobe.com/xmp/extension/\u0000";
    private static final String GUID = "0123456789ABCDEF0123456789ABCDEF";

    private static byte[] concat(byte[]... parts) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            b.write(p);
        }
        return b.toByteArray();
    }

    private static byte[] be(int v) {
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    /** Standard packet + two out-of-order extension chunks reassemble to the full extended packet. */
    @Test
    public void testReassemblesExtended() throws Exception {
        byte[] std = ("<x xmpNote:HasExtendedXMP=\"" + GUID + "\"/>").getBytes(US_ASCII);
        byte[] full = "EXTENDED-XMP-PAYLOAD-DATA-1234567890".getBytes(US_ASCII);
        int split = 20;
        byte[] c1 = Arrays.copyOfRange(full, 0, split);
        byte[] c2 = Arrays.copyOfRange(full, split, full.length);

        byte[] stdSeg = concat(STD.getBytes(US_ASCII), std);
        byte[] ext1 = concat(EXT.getBytes(US_ASCII), GUID.getBytes(US_ASCII), be(full.length), be(0), c1);
        byte[] ext2 =
                concat(EXT.getBytes(US_ASCII), GUID.getBytes(US_ASCII), be(full.length), be(split), c2);

        // feed the extension chunks out of order to prove offset-based placement
        List<byte[]> packets = ExtendedXmp.assemble(Arrays.asList(stdSeg, ext2, ext1));
        assertEquals(2, packets.size());
        assertArrayEquals(std, packets.get(0));
        assertArrayEquals(full, packets.get(1));
    }

    /** No HasExtendedXMP GUID -> just the standard packet, no extended part. */
    @Test
    public void testNoExtended() throws Exception {
        byte[] std = "<x>plain</x>".getBytes(US_ASCII);
        List<byte[]> packets = ExtendedXmp.assemble(Arrays.asList(concat(STD.getBytes(US_ASCII), std)));
        assertEquals(1, packets.size());
        assertArrayEquals(std, packets.get(0));
    }
}
