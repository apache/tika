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

    /**
     * The legacy alias reaches this parser too, whichever type the caller names.
     */
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

    /**
     * Auto-detection routes both types here.
     */
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
        byte[] broken = file.clone();
        broken[6 + 2 * 16 + 12] = (byte) 0xff;
        broken[6 + 2 * 16 + 13] = (byte) 0xff;
        broken[6 + 2 * 16 + 14] = 0;
        broken[6 + 2 * 16 + 15] = 0;
        Metadata metadata = parse(broken);
        assertEquals(32, metadata.getInt(TIFF.IMAGE_WIDTH));
        assertArrayEquals(new String[]{"16x16@32bpp bmp", "32x32@32bpp bmp"},
                metadata.getValues(Icon.IMAGES));
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
