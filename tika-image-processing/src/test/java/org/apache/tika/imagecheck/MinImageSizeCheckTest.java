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
package org.apache.tika.imagecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;

public class MinImageSizeCheckTest {

    private static ImageCheck.Verdict check(MinImageSizeCheck check, byte[] image)
            throws Exception {
        return check.check(ImageFixtures.probe(image), new Metadata(), new ParseContext());
    }

    @Test
    public void testDefaultsRefuseATinyImage() throws Exception {
        ImageCheck.Verdict v = check(new MinImageSizeCheck(), ImageFixtures.png(20, 20, 0.5));
        assertEquals(false, v.admitted());
        assertEquals("image-too-small", v.reason());
    }

    @Test
    public void testDefaultsAdmitA100x100Image() throws Exception {
        assertTrue(check(new MinImageSizeCheck(), ImageFixtures.png(100, 100, 0.5)).admitted());
    }

    @Test
    public void testAThinStripPassesTheAreaTestButNotTheSideMinimum() throws Exception {
        byte[] strip = ImageFixtures.png(1000, 11, 0.5);
        assertEquals(false, check(new MinImageSizeCheck(), strip).admitted());
        MinImageSizeCheck noSides = new MinImageSizeCheck();
        noSides.setMinShortSidePixels(0);
        noSides.setMinTotalPixels(10_000);
        assertTrue(check(noSides, strip).admitted());
    }

    @Test
    public void testMinWidthPixelsAndMinHeightPixelsAddToMinShortSidePixels() throws Exception {
        MinImageSizeCheck banner = new MinImageSizeCheck();
        banner.setMinTotalPixels(0);
        banner.setMinShortSidePixels(10);
        banner.setMinWidthPixels(300);
        // 200 x 40 clears the side minimum but not the width asked for
        assertEquals(false, check(banner, ImageFixtures.png(200, 40, 0.5)).admitted());
        assertTrue(check(banner, ImageFixtures.png(400, 40, 0.5)).admitted());
    }

    @Test
    public void testZeroTurnsATestOff() throws Exception {
        MinImageSizeCheck off = new MinImageSizeCheck();
        off.setMinTotalPixels(0);
        off.setMinShortSidePixels(0);
        assertTrue(check(off, ImageFixtures.png(2, 2, 0.5)).admitted());
    }

    /** A grayscale TIFF whose directory, with the dimensions, follows the pixel data. */
    private static byte[] tiffWithDirectoryAtTheEnd(int width, int height) {
        int data = width * height;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(8 + data + 2 + 9 * 12 + 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8 + data);
        b.position(8 + data);
        b.putShort((short) 9);
        // tag, type (3 short, 4 long), value: width, length, bits, no compression, black is
        // zero, strip offset, samples, rows per strip, strip byte count
        int[][] entries = {{256, 3, width}, {257, 3, height}, {258, 3, 8}, {259, 3, 1},
                {262, 3, 1}, {273, 4, 8}, {277, 3, 1}, {278, 3, height}, {279, 4, data}};
        for (int[] e : entries) {
            b.putShort((short) e[0]).putShort((short) e[1]).putInt(1).putInt(e[2]);
        }
        b.putInt(0);
        return b.array();
    }

    @Test
    public void testADimensionAtTheEndOfALargeFileIsStillRead() throws Exception {
        // past the first megabyte the header read cannot see the directory; the check then
        // reads the whole image
        byte[] tiff = tiffWithDirectoryAtTheEnd(1500, 1500);
        MinImageSizeCheck check = new MinImageSizeCheck();
        check.setMinTotalPixels(10_000_000);
        // refused only if the 1500 x 1500 size was found
        assertEquals(false, check(check, tiff).admitted());
        MinImageSizeCheck small = new MinImageSizeCheck();
        small.setMinTotalPixels(1_000_000);
        assertTrue(check(small, tiff).admitted());
    }

    @Test
    public void testAnUnreadableImageIsAdmitted() throws Exception {
        assertTrue(check(new MinImageSizeCheck(), new byte[] {1, 2, 3}).admitted());
    }

    @Test
    public void testBadSettingsAreRefused() {
        MinImageSizeCheck check = new MinImageSizeCheck();
        assertThrows(IllegalArgumentException.class, () -> check.setMinTotalPixels(-1));
        assertThrows(IllegalArgumentException.class, () -> check.setMinWidthPixels(-1));
    }
}
