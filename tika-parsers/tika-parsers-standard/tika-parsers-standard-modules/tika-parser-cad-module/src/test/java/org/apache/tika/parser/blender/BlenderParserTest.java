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
package org.apache.tika.parser.blender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

import com.github.luben.zstd.Zstd;
import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

public class BlenderParserTest extends TikaTest {

    /** A 3x2 preview: the rows as Blender stores them, the bottom one first. */
    private static final int[][] BOTTOM_UP = {
            {0xff0000ff, 0x00ff00ff, 0x0000ff80},
            {0xffffffff, 0x000000ff, 0x11223344}};

    private enum Header { POINTER4_BIG_ENDIAN, POINTER8, LARGE_BLOCKS }

    @Test
    public void testPreviewOfEveryHeader() throws Exception {
        for (Header header : Header.values()) {
            List<Metadata> list = parse(blend(header, true));
            assertEquals(2, list.size(), header.toString());
            assertEquals("application/x-blender", list.get(0).get(HttpHeaders.CONTENT_TYPE));
            assertPreview(list.get(1), header.toString());
        }
    }

    @Test
    public void testCompressedFiles() throws Exception {
        byte[] blend = blend(Header.POINTER8, true);
        for (byte[] file : new byte[][]{gzip(blend), Zstd.compress(blend)}) {
            List<Metadata> list = parse(file);
            assertEquals("application/x-blender", list.get(0).get(HttpHeaders.CONTENT_TYPE));
            assertEquals(2, list.size());
            assertPreview(list.get(1), "compressed");
        }
    }

    @Test
    public void testDetection() throws Exception {
        byte[] blend = blend(Header.LARGE_BLOCKS, true);
        assertEquals("application/x-blender", detect(blend, null));
        assertEquals("application/x-blender", detect(gzip(blend), null));
        assertEquals("application/x-blender", detect(Zstd.compress(blend), null));
        assertEquals("application/x-blender", detect(gzip(blend), "scene.blend"));
        assertEquals("application/x-blender", detect(new byte[16], "scene.blend"));
        assertEquals("application/x-blender", detect(new byte[16], "scene.blender"));
        byte[] other = "BLENDEX and more".getBytes(StandardCharsets.US_ASCII);
        assertEquals("application/gzip", detect(gzip(other), "scene.blend"));
        assertEquals("application/zstd", detect(Zstd.compress(other), "scene.blend"));
    }

    @Test
    public void testNoPreview() throws Exception {
        List<Metadata> list = parse(blend(Header.POINTER8, false));
        assertEquals(1, list.size());
        assertEquals("application/x-blender", list.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testBadPreviewSize() throws Exception {
        byte[] file = blend(Header.POINTER8, true);
        int test = indexOf(file, "TEST") + 4 + 4 + 8 + 4 + 4;
        ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN).putInt(test, 100000);
        assertThrows(TikaException.class, () -> parseDirectly(file));
    }

    @Test
    public void testTruncatedFiles() throws Exception {
        for (Header header : Header.values()) {
            byte[] file = blend(header, true);
            for (int length = 0; length < file.length; length++) {
                byte[] cut = Arrays.copyOf(file, length);
                try {
                    parseDirectly(cut);
                } catch (TikaException | IOException e) {
                    // the ways out
                }
            }
        }
    }

    @Test
    public void testNotABlenderFile() throws Exception {
        assertThrows(TikaException.class,
                () -> parseDirectly("BLENDEX-v279".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(TikaException.class,
                () -> parseDirectly("BLENDERxx-01v0500".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(TikaException.class,
                () -> parseDirectly("BLENDER17-02v0600".getBytes(StandardCharsets.US_ASCII)));
    }

    private static void assertPreview(Metadata thumbnail, String message) throws IOException {
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE), message);
        assertEquals("thumbnail.png", thumbnail.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testPreviewPixels() throws Exception {
        for (Header header : Header.values()) {
            byte[] png = BlenderParser.readThumbnail(
                    new ByteArrayInputStream(blend(header, true)));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            assertEquals(3, image.getWidth(), header.toString());
            assertEquals(2, image.getHeight());
            // the top row is the one stored second, RGBA becomes ARGB
            assertEquals(0x44112233, image.getRGB(2, 0));
            assertEquals(0xff000000, image.getRGB(1, 0));
            assertEquals(0xffff0000, image.getRGB(0, 1));
            assertEquals(0x800000ff, image.getRGB(2, 1));
        }
    }

    private List<Metadata> parse(byte[] file) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            return getRecursiveMetadata(tis, true);
        }
    }

    private static void parseDirectly(byte[] file) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new BlenderParser().parse(tis, new DefaultHandler(), new Metadata(),
                    new ParseContext());
        }
    }

    private static String detect(byte[] file, String name) throws IOException {
        Metadata metadata = new Metadata();
        if (name != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        }
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            return new DefaultDetector().detect(tis, metadata, new ParseContext()).toString();
        }
    }

    /** A blend file with a REND block, the preview as a TEST block if asked for, and ENDB. */
    private static byte[] blend(Header header, boolean preview) {
        boolean little = header != Header.POINTER4_BIG_ENDIAN;
        ByteBuffer out = ByteBuffer.allocate(1024)
                .order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        String magic = header == Header.POINTER4_BIG_ENDIAN ? "BLENDER_V279"
                : header == Header.POINTER8 ? "BLENDER-v402" : "BLENDER17-01v0500";
        out.put(magic.getBytes(StandardCharsets.US_ASCII));
        block(out, header, "REND", new byte[72]);
        if (preview) {
            ByteBuffer data = ByteBuffer.allocate(8 + 4 * 6).order(out.order());
            data.putInt(3).putInt(2);
            for (int[] row : BOTTOM_UP) {
                for (int rgba : row) {
                    data.order(ByteOrder.BIG_ENDIAN).putInt(rgba).order(out.order());
                }
            }
            block(out, header, "TEST", data.array());
        }
        block(out, header, "GLOB", new byte[16]);
        block(out, header, "ENDB", new byte[0]);
        return Arrays.copyOf(out.array(), out.position());
    }

    private static void block(ByteBuffer out, Header header, String code, byte[] data) {
        out.put(code.getBytes(StandardCharsets.US_ASCII));
        switch (header) {
            case POINTER4_BIG_ENDIAN:
                out.putInt(data.length).putInt(0x1000).putInt(0).putInt(1);
                break;
            case POINTER8:
                out.putInt(data.length).putLong(0x7fff0000L).putInt(0).putInt(1);
                break;
            default:
                out.putInt(0).putLong(0x7fff0000L).putLong(data.length).putLong(1);
                break;
        }
        out.put(data);
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(data);
        }
        return bytes.toByteArray();
    }

    private static int indexOf(byte[] haystack, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i + n.length <= haystack.length; i++) {
            if (Arrays.equals(haystack, i, i + n.length, n, 0, n.length)) {
                return i;
            }
        }
        throw new AssertionError(needle);
    }
}
