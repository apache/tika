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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Icon;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;

/**
 * testICO.ico holds 16 and 32 px BMP-encoded images plus a 256 px PNG-encoded
 * one, all 32 bpp; testICO_bmpOnly.ico holds 16 and 48 px BMP images;
 * testCUR.cur holds 16 px (hotspot 3,2) and 32 px (hotspot 7,5) cursors.
 * All were generated, not taken from a product.
 */
public class ICOParserTest extends TikaTest {

    @Test
    public void testIconWithPngEntry() throws Exception {
        Metadata metadata = parse("testICO.ico");
        assertEquals("image/vnd.microsoft.icon", metadata.get(HttpHeaders.CONTENT_TYPE));
        // the 256 px entry is stored as 0x0 in the directory; the size comes from the PNG header
        assertEquals(256, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertEquals(256, metadata.getInt(TIFF.IMAGE_LENGTH));
        // 32 bpp RGBA: 8 bits per sample, 4 samples
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));
        assertEquals(4, metadata.getInt(TIFF.SAMPLES_PER_PIXEL));
        assertEquals(3, metadata.getInt(Icon.IMAGE_COUNT));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp", "256x256@32bpp png"},
                metadata.getValues(Icon.IMAGES));
        assertNull(metadata.get(Icon.HOTSPOT_X));
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testIconBmpOnly() throws Exception {
        Metadata metadata = parse("testICO_bmpOnly.ico");
        assertEquals(48, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertEquals(48, metadata.getInt(TIFF.IMAGE_LENGTH));
        assertEquals(2, metadata.getInt(Icon.IMAGE_COUNT));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "48x48@32bpp bmp"},
                metadata.getValues(Icon.IMAGES));
    }

    @Test
    public void testCursor() throws Exception {
        Metadata metadata = parse("testCUR.cur");
        assertEquals("image/x-win-bitmap", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertEquals(32, metadata.getInt(TIFF.IMAGE_LENGTH));
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));
        assertEquals(4, metadata.getInt(TIFF.SAMPLES_PER_PIXEL));
        assertEquals(7, metadata.getInt(Icon.HOTSPOT_X));
        assertEquals(5, metadata.getInt(Icon.HOTSPOT_Y));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp"},
                metadata.getValues(Icon.IMAGES));
    }

    /**
     * A cursor's directory entry carries the hotspot where an icon's carries
     * the bit count, so a cursor whose image header is cut has a hotspot but
     * no colour depth.
     */
    @Test
    public void testCursorWithUnreadableImage() throws Exception {
        byte[] cur = readTestResource("testCUR.cur");
        // header + 2 entries = 38 bytes, then the 1128 byte 16 px image; cut into the 32 px one
        Metadata metadata = parse(Arrays.copyOf(cur, 38 + 1128 + 10));
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertNull(metadata.get(TIFF.BITS_PER_SAMPLE));
        assertEquals(7, metadata.getInt(Icon.HOTSPOT_X));
        assertEquals(5, metadata.getInt(Icon.HOTSPOT_Y));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32 unknown"},
                metadata.getValues(Icon.IMAGES));
        assertContains("1 of 2 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testAliasRoutesHere() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-icon");
        try (TikaInputStream tis = TikaInputStream.get(readTestResource("testICO.ico"))) {
            new DefaultParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertEquals(3, metadata.getInt(Icon.IMAGE_COUNT));
        assertEquals(256, metadata.getInt(TIFF.IMAGE_WIDTH));
    }

    @Test
    public void testAutoDetect() throws Exception {
        Metadata ico = getXML("testICO.ico").metadata;
        assertEquals("image/vnd.microsoft.icon", ico.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(256, ico.getInt(TIFF.IMAGE_WIDTH));
        Metadata cur = getXML("testCUR.cur").metadata;
        assertEquals("image/x-win-bitmap", cur.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(7, cur.getInt(Icon.HOTSPOT_X));
    }

    /**
     * Only two of three images survive the cut; they are reported, the third
     * is a warning, not a failure.
     */
    @Test
    public void testTruncated() throws Exception {
        byte[] file = readTestResource("testICO.ico");
        // header + 3 entries = 54 bytes, then 1128 + 4264 bytes of BMP images; the PNG image
        // is cut but its 29 byte header survives, and headers are all this parser reads
        Metadata metadata = parse(Arrays.copyOf(file, 54 + 1128 + 4264 + 100));
        assertEquals(256, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertEquals(3, metadata.getInt(Icon.IMAGE_COUNT));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp", "256x256@32bpp png"},
                metadata.getValues(Icon.IMAGES));
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        // the second image's header is cut: its directory entry still says 32x32, and the
        // third image lies beyond the end
        metadata = parse(Arrays.copyOf(file, 54 + 1128 + 10));
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp unknown"},
                metadata.getValues(Icon.IMAGES));
        // the second image's depth comes from the directory, so it is still 32 bpp
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));
        // one image cut, one beyond the end
        assertContains("2 of 3 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        // cut inside the directory itself
        metadata = parse(Arrays.copyOf(file, 30));
        assertNull(metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals(3, metadata.getInt(Icon.IMAGE_COUNT));
        assertContains("3 of 3 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * Directory values are the fallback when the image header is unreadable:
     * a 256 px entry then reads as 256, not 0.
     */
    @Test
    public void testDirectoryFallback() throws Exception {
        byte[] file = readTestResource("testICO.ico");
        // the third entry's image offset (6 + 2 * 16 + 12) is sent past the end of the file
        Metadata metadata = parse(patch(file, 6 + 2 * 16 + 12, 0xff, 0xff, 0, 0));
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp"},
                metadata.getValues(Icon.IMAGES));
        assertContains("1 of 3 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * OS/2 bitmap arrays share the icon type's magic. They are not read, but
     * they are not a failure either.
     */
    @Test
    public void testOs2BitmapArray() throws Exception {
        byte[] bitmapArray = new byte[64];
        bitmapArray[0] = 'B';
        bitmapArray[1] = 'A';
        bitmapArray[2] = 0x28;
        bitmapArray[6] = 0x2e;

        Metadata metadata = parse(bitmapArray);
        assertEquals("image/vnd.microsoft.icon", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertNull(metadata.get(Icon.IMAGE_COUNT));
        assertNull(metadata.get(TIFF.IMAGE_WIDTH));

        Metadata detected = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(bitmapArray)) {
            getXML(tis, AUTO_DETECT_PARSER, detected);
        }
        assertEquals("image/vnd.microsoft.icon", detected.get(HttpHeaders.CONTENT_TYPE));
        assertContains(ICOParser.class.getName(),
                Arrays.asList(detected.getValues(TikaCoreProperties.TIKA_PARSED_BY)));
        assertNull(detected.get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    /**
     * A header whose width or height is zero, negative or beyond any real icon
     * is unreadable: the directory values stand in.
     */
    @Test
    public void testHostileDimensions() throws Exception {
        byte[] file = readTestResource("testICO.ico");
        // the 16 px BMP image starts at 54, the 256 px PNG image at 5446
        int bmpWidth = 54 + 4;
        int bmpHeight = 54 + 8;
        int pngWidth = 5446 + 16;
        int pngHeight = 5446 + 20;

        // little endian -1 and Integer.MIN_VALUE
        assertFallsBackToDirectory(patch(file, bmpWidth, 0xff, 0xff, 0xff, 0xff),
                "16x16@32bpp unknown");
        assertFallsBackToDirectory(patch(file, bmpWidth, 0, 0, 0, 0x80), "16x16@32bpp unknown");
        assertFallsBackToDirectory(patch(file, bmpHeight, 0, 0, 0, 0), "16x16@32bpp unknown");
        assertFallsBackToDirectory(patch(file, bmpHeight, 0, 0, 0, 0x80), "16x16@32bpp unknown");
        // big endian Integer.MIN_VALUE, Integer.MAX_VALUE and one past the largest size believed
        assertFallsBackToDirectory(patch(file, pngWidth, 0x80, 0, 0, 0), "256x256@32bpp unknown");
        assertFallsBackToDirectory(patch(file, pngHeight, 0x80, 0, 0, 0), "256x256@32bpp unknown");
        assertFallsBackToDirectory(patch(file, pngHeight, 0, 0, 0, 0), "256x256@32bpp unknown");
        assertFallsBackToDirectory(patch(file, pngWidth, 0x7f, 0xff, 0xff, 0xff),
                "256x256@32bpp unknown");
        assertFallsBackToDirectory(patch(file, pngWidth, 0, 1, 0, 0), "256x256@32bpp unknown");
        // two negative dimensions have a positive product, which must not win
        assertFallsBackToDirectory(
                patch(patch(file, pngWidth, 0x80, 0, 0, 0), pngHeight, 0x80, 0, 0, 0),
                "256x256@32bpp unknown");
    }

    /**
     * The fixtures are all 32 bpp; the other depths are patched into their headers.
     */
    @Test
    public void testColourDepths() throws Exception {
        byte[] bmpOnly = readTestResource("testICO_bmpOnly.ico");
        // the 48 px image is the second entry; a BITMAPINFOHEADER holds the bit count at 14
        int bitCount = (int) EndianUtils.getUIntLE(bmpOnly, 6 + 16 + 12) + 14;
        assertDepth(patch(bmpOnly, bitCount, 24, 0), "48x48@24bpp bmp", "8", 3);
        assertDepth(patch(bmpOnly, bitCount, 16, 0), "48x48@16bpp bmp", "5", 3);
        assertDepth(patch(bmpOnly, bitCount, 8, 0), "48x48@8bpp bmp", "8", 1);
        assertDepth(patch(bmpOnly, bitCount, 1, 0), "48x48@1bpp bmp", "1", 1);

        byte[] withPng = readTestResource("testICO.ico");
        // an IHDR holds the bit depth at 24 and the colour type at 25
        int bitDepth = 5446 + 24;
        assertDepth(patch(withPng, bitDepth, 8, 2), "256x256@24bpp png", "8", 3);
        assertDepth(patch(withPng, bitDepth, 8, 4), "256x256@16bpp png", "8", 2);
        assertDepth(patch(withPng, bitDepth, 8, 3), "256x256@8bpp png", "8", 1);
        assertDepth(patch(withPng, bitDepth, 16, 6), "256x256@64bpp png", "16", 4);
    }

    /**
     * A depth no DIB or PNG can have is ignored; the directory's 32 bpp stands in.
     */
    @Test
    public void testImplausibleColourDepths() throws Exception {
        byte[] bmpOnly = readTestResource("testICO_bmpOnly.ico");
        int bitCount = (int) EndianUtils.getUIntLE(bmpOnly, 6 + 16 + 12) + 14;
        assertDepth(patch(bmpOnly, bitCount, 0xff, 0xff), "48x48@32bpp bmp", "8", 4);
        assertDepth(patch(bmpOnly, bitCount, 7, 0), "48x48@32bpp bmp", "8", 4);

        byte[] withPng = readTestResource("testICO.ico");
        int bitDepth = 5446 + 24;
        assertDepth(patch(withPng, bitDepth, 0xff, 6), "256x256@32bpp png", "8", 4);
        assertDepth(patch(withPng, bitDepth, 8, 5), "256x256@32bpp png", "8", 4);

        // the directory's own bit count is no more trusted: the 32 px image is cut and has no other
        byte[] cut = Arrays.copyOf(patch(withPng, 6 + 16 + 6, 0xff, 0xff), 54 + 1128 + 10);
        assertContains("32x32 unknown", Arrays.asList(parse(cut).getValues(Icon.IMAGES)));
    }

    private static void assertDepth(byte[] file, String expectedImage, String bitsPerSample,
                                    int samplesPerPixel) throws Exception {
        Metadata metadata = parse(file);
        assertContains(expectedImage, Arrays.asList(metadata.getValues(Icon.IMAGES)));
        assertEquals(bitsPerSample, metadata.get(TIFF.BITS_PER_SAMPLE));
        assertEquals(samplesPerPixel, metadata.getInt(TIFF.SAMPLES_PER_PIXEL));
    }

    @Test
    public void testEmptyDirectory() throws Exception {
        Metadata metadata = parse(new byte[]{0, 0, 1, 0, 0, 0});
        assertEquals("image/vnd.microsoft.icon", metadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(0, metadata.getInt(Icon.IMAGE_COUNT));
        assertContains("no images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testHeaderCutBeforeImageCount() throws Exception {
        for (byte[] header : new byte[][]{{0, 0, 2, 0}, {0, 0, 2, 0, 1}}) {
            Metadata metadata = parse(header);
            assertEquals("image/x-win-bitmap", metadata.get(HttpHeaders.CONTENT_TYPE));
            assertNull(metadata.get(Icon.IMAGE_COUNT));
            assertContains("image count",
                    metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        }
    }

    /**
     * An image whose header lies beyond the size limit is as unreadable as one
     * beyond the end of the file.
     */
    @Test
    public void testSizeLimit() throws Exception {
        byte[] file = readTestResource("testICO.ico");
        int limit = ICOParser.MAX_FILE_SIZE;
        byte[] large = Arrays.copyOf(file, limit + 64);
        // the 256 px image's PNG header, copied to just beyond the limit, and its entry's offset
        System.arraycopy(file, 5446, large, limit, 29);
        int offset = 6 + 2 * 16 + 12;
        large[offset] = (byte) limit;
        large[offset + 1] = (byte) (limit >> 8);
        large[offset + 2] = (byte) (limit >> 16);
        large[offset + 3] = (byte) (limit >> 24);

        Metadata metadata = parse(large);
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp"},
                metadata.getValues(Icon.IMAGES));
        assertContains("1 of 3 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    private static byte[] patch(byte[] file, int offset, int... bytes) {
        byte[] patched = file.clone();
        for (int i = 0; i < bytes.length; i++) {
            patched[offset + i] = (byte) bytes[i];
        }
        return patched;
    }

    private static void assertFallsBackToDirectory(byte[] file, String expected) throws Exception {
        Metadata metadata = parse(file);
        assertContains(expected, Arrays.asList(metadata.getValues(Icon.IMAGES)));
        assertEquals(256, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertEquals(256, metadata.getInt(TIFF.IMAGE_LENGTH));
        assertContains("1 of 3 images", metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testNotAnIcon() {
        assertThrows(TikaException.class, () -> parse(new byte[]{0, 0, 3, 0, 1, 0}));
        assertThrows(TikaException.class, () -> parse(new byte[]{1, 2, 3}));
    }

    private Metadata parse(String name) throws Exception {
        return parse(readTestResource(name));
    }

    private static Metadata parse(byte[] file) throws Exception {
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new ICOParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        return metadata;
    }

    private byte[] readTestResource(String name) throws Exception {
        try (TikaInputStream tis = getResourceAsStream("/test-documents/" + name)) {
            return tis.readAllBytes();
        }
    }
}
