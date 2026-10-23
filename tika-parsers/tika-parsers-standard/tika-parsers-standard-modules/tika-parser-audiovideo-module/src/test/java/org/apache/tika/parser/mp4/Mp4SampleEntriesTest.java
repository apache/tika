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
package org.apache.tika.parser.mp4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class Mp4SampleEntriesTest {

    @Test
    public void testClassicEntries() {
        byte[] stsd = stsd(entry(24, "mp4a", 16), entry(24, "ac-3", 16));
        List<String> seen = walk(stsd);
        assertEquals(List.of("mp4a:16:32", "ac-3:40:56"), seen);
    }

    @Test
    public void testLargesizeEntry() {
        //size 1 announces a 64-bit size after the FourCC; the visitor's start
        //must skip the 16 byte header
        byte[] stsd = stsd(largeEntry(32, "avc1", 16), entry(24, "mp4a", 16));
        assertEquals(List.of("avc1:24:40", "mp4a:48:64"), walk(stsd));
    }

    @Test
    public void testZeroSizeEntryExtendsToEnd() {
        byte[] stsd = stsd(entry(0, "hvc1", 16));
        assertEquals(List.of("hvc1:16:32"), walk(stsd));
    }

    @Test
    public void testTruncatedAndUndersizedEntriesStopTheWalk() {
        //an entry claiming more bytes than the payload has
        assertEquals(List.of(), walk(stsd(entry(40, "mp4a", 16))));
        //an entry too small to hold the SampleEntry fields
        assertEquals(List.of(), walk(stsd(entry(12, "mp4a", 4))));
        //a largesize header cut off before the 64-bit size
        byte[] cut = stsd(largeEntry(32, "avc1", 16));
        byte[] truncated = new byte[8 + 12];
        System.arraycopy(cut, 0, truncated, 0, truncated.length);
        assertEquals(List.of(), walk(truncated));
        //a largesize beyond 63 bits
        byte[] huge = stsd(largeEntry(32, "avc1", 16));
        huge[8 + 8] = (byte) 0xFF;
        assertEquals(List.of(), walk(huge));
    }

    @Test
    public void testPrintableFourCC() {
        assertEquals("mp4a", Mp4SampleEntries.printableFourCC(ascii("mp4a"), 0));
        //QuickTime pads short codes with spaces
        assertEquals("raw", Mp4SampleEntries.printableFourCC(ascii("raw "), 0));
        assertEquals("rle", Mp4SampleEntries.printableFourCC(ascii("rle "), 0));
        assertNull(Mp4SampleEntries.printableFourCC(ascii("    "), 0));
        assertNull(Mp4SampleEntries.printableFourCC(new byte[]{0, 1, 2, 3}, 0));
        assertNull(Mp4SampleEntries.printableFourCC(new byte[]{'a', 'v', 'c', 0x7F}, 0));
        assertNull(Mp4SampleEntries.printableFourCC(new byte[]{(byte) 0xE4, 'v', 'c', '1'}, 0));
        //an unprintable FourCC reaches the visitor as null but does not stop the walk
        byte[] stsd = stsd(entry(24, "\u0001vc1", 16), entry(24, "mp4a", 16));
        assertEquals(List.of("null:16:32", "mp4a:40:56"), walk(stsd));
    }

    @Test
    public void testOriginalFormat() {
        byte[] sinf = boxOf("sinf", boxOf("frma", ascii("mp4a")),
                boxOf("schm", new byte[]{0, 0, 0, 0, 'i', 't', 'u', 'n', 0, 1, 0, 0}));
        byte[] children = concat(boxOf("esds", new byte[4]), sinf, boxOf("btrt", new byte[12]));
        assertEquals("mp4a", Mp4SampleEntries.originalFormat(children, 0, children.length));
        //no sinf
        byte[] plain = boxOf("esds", new byte[4]);
        assertNull(Mp4SampleEntries.originalFormat(plain, 0, plain.length));
        //sinf without frma
        byte[] noFrma = boxOf("sinf", boxOf("schi", new byte[0]));
        assertNull(Mp4SampleEntries.originalFormat(noFrma, 0, noFrma.length));
        //frma with an unprintable format
        byte[] bad = boxOf("sinf", boxOf("frma", new byte[]{0, 0, 0, 0}));
        assertNull(Mp4SampleEntries.originalFormat(bad, 0, bad.length));
        //a child box claiming to run past the entry stops the scan
        byte[] truncated = concat(boxOf("esds", new byte[4]), sinf);
        putInt(truncated, 0, 1000);
        assertNull(Mp4SampleEntries.originalFormat(truncated, 0, truncated.length));
        //frma cut off before its payload
        byte[] cut = new byte[16];
        System.arraycopy(sinf, 0, cut, 0, 16);
        putInt(cut, 0, 16);
        assertNull(Mp4SampleEntries.originalFormat(cut, 0, cut.length));
        //a header-only frma (size 8) must not read its format from the next box
        byte[] shortFrma = boxOf("sinf", boxOf("frma"), boxOf("mp4a", new byte[0]));
        assertNull(Mp4SampleEntries.originalFormat(shortFrma, 0, shortFrma.length));
    }

    private static byte[] boxOf(String type, byte[]... payloads) {
        byte[] payload = concat(payloads);
        byte[] b = new byte[8 + payload.length];
        putInt(b, 0, b.length);
        System.arraycopy(ascii(type), 0, b, 4, 4);
        System.arraycopy(payload, 0, b, 8, payload.length);
        return b;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    private static List<String> walk(byte[] stsd) {
        List<String> seen = new ArrayList<>();
        Mp4SampleEntries.walk(stsd, (fourCC, b, start, end) ->
                seen.add(fourCC + ":" + start + ":" + end));
        return seen;
    }

    private static byte[] stsd(byte[]... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0, 0, 0, 0, 0, 0, 0, (byte) entries.length}, 0, 8);
        for (byte[] entry : entries) {
            out.write(entry, 0, entry.length);
        }
        return out.toByteArray();
    }

    private static byte[] entry(int size, String fourCC, int bodyLength) {
        byte[] b = new byte[8 + bodyLength];
        putInt(b, 0, size);
        System.arraycopy(ascii(fourCC), 0, b, 4, 4);
        return b;
    }

    private static byte[] largeEntry(long size, String fourCC, int bodyLength) {
        byte[] b = new byte[16 + bodyLength];
        putInt(b, 0, 1);
        System.arraycopy(ascii(fourCC), 0, b, 4, 4);
        putInt(b, 8, (int) (size >>> 32));
        putInt(b, 12, (int) size);
        return b;
    }

    private static void putInt(byte[] b, int pos, int v) {
        b[pos] = (byte) (v >>> 24);
        b[pos + 1] = (byte) (v >>> 16);
        b[pos + 2] = (byte) (v >>> 8);
        b[pos + 3] = (byte) v;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
