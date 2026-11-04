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
package org.apache.tika.parser.sketchup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

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

public class SketchUpParserTest extends TikaTest {

    private static final byte[] MODEL = png(0xff0000);

    private static final byte[] VIEW = png(0x00ff00);

    @Test
    public void testCDibPreview() throws Exception {
        byte[] file = cdib("17.0.18899", MODEL);
        assertArrayEquals(MODEL, SketchUpParser.readThumbnail(new ByteArrayInputStream(file)));
        assertThumbnail(file);
    }

    @Test
    public void testZipModelThumbnail() throws Exception {
        byte[] file = zipped("26.1.194", VIEW, MODEL);
        assertArrayEquals(MODEL, SketchUpParser.readThumbnail(new ByteArrayInputStream(file)),
                "the model thumbnail wins wherever it is");
        assertThumbnail(file);
    }

    @Test
    public void testZipViewThumbnailWithoutModel() throws Exception {
        byte[] file = zipped("21.0.391", VIEW, null);
        assertArrayEquals(VIEW, SketchUpParser.readThumbnail(new ByteArrayInputStream(file)));
    }

    @Test
    public void testNoPreview() throws Exception {
        assertNull(SketchUpParser.readThumbnail(new ByteArrayInputStream(zipped("26.0.429", null,
                null))));
        byte[] old = Arrays.copyOf(header("8.0.4811"), 4096);
        assertNull(SketchUpParser.readThumbnail(new ByteArrayInputStream(old)));
        List<Metadata> list = parse(old);
        assertEquals(1, list.size());
        assertEquals("application/vnd.sketchup.skp", list.get(0).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testDetection() throws Exception {
        assertEquals("application/vnd.sketchup.skp", detect(cdib("15.3.331", MODEL), null));
        assertEquals("application/vnd.sketchup.skp", detect(cdib("15.3.331", MODEL), "a.skp"));
        assertEquals("application/vnd.sketchup.skp", detect(zipped("22.0.354", VIEW, MODEL),
                "house.skp"));
        assertEquals("application/vnd.koan", detect(new byte[64], "song.skp"),
                "without the header the name is all there is");
    }

    @Test
    public void testNotASketchUpFile() {
        byte[] text = "SketchUp Model".getBytes(StandardCharsets.UTF_16);
        assertThrows(TikaException.class,
                () -> SketchUpParser.readThumbnail(new ByteArrayInputStream(text)));
        byte[] jpeg = cdib("17.0.18899", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});
        assertThrows(TikaException.class,
                () -> SketchUpParser.readThumbnail(new ByteArrayInputStream(jpeg)));
    }

    @Test
    public void testOversizedPreview() throws Exception {
        byte[] file = cdib("17.0.18899", MODEL);
        int length = indexOf(file, "CDib".getBytes(StandardCharsets.US_ASCII)) + 8;
        ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(length, SketchUpParser.MAX_THUMBNAIL_LENGTH + 1);
        assertThrows(TikaException.class,
                () -> SketchUpParser.readThumbnail(new ByteArrayInputStream(file)));
    }

    @Test
    public void testTruncatedFiles() throws Exception {
        for (byte[] file : new byte[][]{cdib("17.0.18899", MODEL),
                zipped("26.1.194", VIEW, MODEL)}) {
            for (int length = 0; length < file.length; length++) {
                try (TikaInputStream tis = TikaInputStream.get(Arrays.copyOf(file, length))) {
                    new SketchUpParser().parse(tis, new DefaultHandler(), new Metadata(),
                            new ParseContext());
                } catch (TikaException | IOException e) {
                    // the ways out
                }
            }
        }
    }

    private void assertThumbnail(byte[] file) throws Exception {
        List<Metadata> list = parse(file);
        assertEquals(2, list.size());
        assertEquals("application/vnd.sketchup.skp", list.get(0).get(HttpHeaders.CONTENT_TYPE));
        Metadata thumbnail = list.get(1);
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("thumbnail.png", thumbnail.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("image/png", thumbnail.get(HttpHeaders.CONTENT_TYPE));
    }

    private List<Metadata> parse(byte[] file) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            return getRecursiveMetadata(tis, true);
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

    /** "SketchUp Model" and the version, as SketchUp writes them. */
    private static byte[] header(String version) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String s : new String[]{"SketchUp Model", "{" + version + "}"}) {
            out.write(0xff);
            out.write(0xfe);
            out.write(0xff);
            out.write(s.length());
            out.writeBytes(s.getBytes(StandardCharsets.UTF_16LE));
        }
        return out.toByteArray();
    }

    /** A file of SketchUp 2020 and before: the header, other records, the CDib record. */
    private static byte[] cdib(String version, byte[] png) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header(version));
        out.writeBytes(new byte[1500]);
        out.writeBytes(new byte[]{(byte) 0xff, (byte) 0xff, 3, 0, 4, 0, 'C', 'D', 'i', 'b'});
        out.writeBytes(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(4)
                .putInt(png.length).array());
        out.writeBytes(png);
        out.writeBytes(new byte[300]);
        return out.toByteArray();
    }

    /** A file of SketchUp 2021 and later: the header, "VFF" and its fields, then a zip. */
    private static byte[] zipped(String version, byte[] view, byte[] model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(header(version));
        out.writeBytes(new byte[]{'V', 'F', 'F', 8, 0, 1, 0, 0x11, 0, 1, 2, 3, 4});
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (view != null) {
                zip.putNextEntry(new ZipEntry("meta/preview_thumbnail.png"));
                zip.write(view);
            }
            if (model != null) {
                zip.putNextEntry(new ZipEntry("meta/model_thumbnail.png"));
                zip.write(model);
            }
            zip.putNextEntry(new ZipEntry("meta/meta.dat"));
            zip.write(new byte[16]);
            zip.putNextEntry(new ZipEntry("model.dat"));
            zip.write(new byte[64]);
        }
        return out.toByteArray();
    }

    private static byte[] png(int rgb) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        throw new AssertionError();
    }
}
