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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Canon CR3, on a file built here with the box layout of a real CR3: the Exif blocks
 * and a {@code THMB} record in the Canon {@code uuid} box inside {@code moov}, a
 * {@code PRVW} record in the Canon {@code uuid} box at the top level.
 */
public class Cr3ParserTest extends TikaTest {

    private static final String METADATA_UUID = "85c0b687820f11e08111f4ce462b6a48";
    private static final String PREVIEW_UUID = "eaf42b5e1c984b88b9fbb7dc406e4d16";

    private static final short ASCII = 2;
    private static final short SHORT = 3;
    private static final short RATIONAL = 5;

    @Test
    public void testExif() throws Exception {
        Metadata metadata = new Metadata();
        parse(cr3(jpeg(16, 12), jpeg(48, 32)), metadata, new ArrayList<>(), new ArrayList<>());
        assertEquals("Canon", metadata.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("Canon EOS R7", metadata.get(TIFF.EQUIPMENT_MODEL));
        assertEquals("0.004", metadata.get(TIFF.EXPOSURE_TIME));
        assertEquals("100", metadata.get(TIFF.ISO_SPEED_RATINGS));
        assertEquals("48.5", metadata.get(Geographic.LATITUDE));
        assertEquals("11.25", metadata.get(Geographic.LONGITUDE));
    }

    @Test
    public void testPreviewIsThumbnailAndThmbIsInline() throws Exception {
        List<Metadata> embedded = new ArrayList<>();
        parse(cr3(jpeg(16, 12), jpeg(48, 32)), new Metadata(), embedded, new ArrayList<>());
        assertEquals(2, embedded.size());
        assertEquals("INLINE", embedded.get(0).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("16", embedded.get(0).get(TIFF.IMAGE_WIDTH));
        assertEquals("THUMBNAIL", embedded.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("48", embedded.get(1).get(TIFF.IMAGE_WIDTH));
        assertEquals("32", embedded.get(1).get(TIFF.IMAGE_LENGTH));
        assertEquals("image/jpeg", embedded.get(1).get(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    public void testJpegBytesAreExact() throws Exception {
        byte[] prvw = jpeg(48, 32);
        List<byte[]> bytes = new ArrayList<>();
        parse(cr3(jpeg(16, 12), prvw), new Metadata(), new ArrayList<>(), bytes);
        assertArrayEquals(prvw, bytes.get(1));
    }

    @Test
    public void testLengthBeyondTheBoxIsIgnored() throws Exception {
        byte[] file = cr3(jpeg(16, 12), jpeg(48, 32));
        int prvw = indexOf(file, "PRVW".getBytes(StandardCharsets.US_ASCII));
        ByteBuffer.wrap(file).putInt(prvw + 16, Integer.MAX_VALUE);
        List<Metadata> embedded = new ArrayList<>();
        Metadata metadata = new Metadata();
        parse(file, metadata, embedded, new ArrayList<>());
        assertEquals(1, embedded.size());
        assertEquals("INLINE", embedded.get(0).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("Canon EOS R7", metadata.get(TIFF.EQUIPMENT_MODEL));
    }

    @Test
    public void testNoCanonBoxes() throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(box("ftyp", ascii("crx \0\0\0\1crx isom")));
        file.write(box("moov", box("mvhd", new byte[100])));
        List<Metadata> embedded = new ArrayList<>();
        Metadata metadata = new Metadata();
        parse(file.toByteArray(), metadata, embedded, new ArrayList<>());
        assertEquals(0, embedded.size());
        assertNull(metadata.get(TIFF.EQUIPMENT_MAKE));
    }

    private void parse(byte[] file, Metadata metadata, List<Metadata> embedded, List<byte[]> bytes)
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata m, ParseContext ctx) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, ContentHandler handler, Metadata m,
                                      ParseContext ctx, boolean outputHtml) throws IOException {
                embedded.add(m);
                bytes.add(stream.readAllBytes());
            }
        });
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new Cr3Parser().parse(tis, new BodyContentHandler(), metadata, context);
        }
    }

    private static byte[] cr3(byte[] thmb, byte[] prvw) throws IOException {
        ByteArrayOutputStream thmbBox = new ByteArrayOutputStream();
        thmbBox.write(new byte[4]);
        thmbBox.write(shorts(16, 12));
        thmbBox.write(ints(thmb.length));
        thmbBox.write(new byte[]{0, 1, 0, 0});
        thmbBox.write(thmb);
        ByteArrayOutputStream metadataUuid = new ByteArrayOutputStream();
        metadataUuid.write(hex(METADATA_UUID));
        metadataUuid.write(box("CNCV", ascii("CanonCR3_001/01.09.00/00.00.00")));
        metadataUuid.write(box("CMT1", tiff(new Entry(0x010F, ASCII, ascii("Canon\0")),
                new Entry(0x0110, ASCII, ascii("Canon EOS R7\0")))));
        metadataUuid.write(box("CMT2", tiff(new Entry(0x829A, RATIONAL, rationals(1, 250)),
                new Entry(0x8827, SHORT, le(ByteBuffer.allocate(2).putShort((short) 100))))));
        metadataUuid.write(box("CMT4", tiff(new Entry(0x0001, ASCII, ascii("N\0")),
                new Entry(0x0002, RATIONAL, rationals(48, 1, 30, 1, 0, 1)),
                new Entry(0x0003, ASCII, ascii("E\0")),
                new Entry(0x0004, RATIONAL, rationals(11, 1, 15, 1, 0, 1)))));
        metadataUuid.write(box("THMB", thmbBox.toByteArray()));

        ByteArrayOutputStream prvwBox = new ByteArrayOutputStream();
        prvwBox.write(new byte[4]);
        prvwBox.write(shorts(1, 48, 32, 1));
        prvwBox.write(ints(prvw.length));
        prvwBox.write(prvw);
        ByteArrayOutputStream previewUuid = new ByteArrayOutputStream();
        previewUuid.write(hex(PREVIEW_UUID));
        previewUuid.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 1});
        previewUuid.write(box("PRVW", prvwBox.toByteArray()));

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(box("ftyp", ascii("crx \0\0\0\1crx isom")));
        file.write(box("moov", box("uuid", metadataUuid.toByteArray())));
        file.write(box("uuid", previewUuid.toByteArray()));
        file.write(box("mdat", new byte[16]));
        return file.toByteArray();
    }

    private record Entry(int tag, short type, byte[] value) {
        int count() {
            return type == ASCII ? value.length : type == SHORT ? value.length / 2 : value.length / 8;
        }
    }

    /** A little-endian TIFF block with one IFD; values that do not fit four bytes follow it. */
    private static byte[] tiff(Entry... entries) {
        int ifdSize = 2 + 12 * entries.length + 4;
        int dataOffset = 8 + ifdSize;
        ByteBuffer out = ByteBuffer.allocate(dataOffset + 256).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[]{'I', 'I', 42, 0}).putInt(8).putShort((short) entries.length);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (Entry e : entries) {
            out.putShort((short) e.tag()).putShort(e.type()).putInt(e.count());
            if (e.value().length <= 4) {
                out.put(Arrays.copyOf(e.value(), 4));
            } else {
                out.putInt(dataOffset + data.size());
                data.writeBytes(e.value());
            }
        }
        out.putInt(0).put(data.toByteArray());
        return Arrays.copyOf(out.array(), out.position());
    }

    private static byte[] rationals(int... values) {
        ByteBuffer b = ByteBuffer.allocate(4 * values.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) {
            b.putInt(v);
        }
        return b.array();
    }

    private static byte[] le(ByteBuffer b) {
        byte[] a = b.array();
        return new byte[]{a[1], a[0]};
    }

    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length).putInt(8 + payload.length)
                .put(ascii(type)).put(payload).array();
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }

    private static byte[] shorts(int... values) {
        ByteBuffer b = ByteBuffer.allocate(2 * values.length);
        for (int v : values) {
            b.putShort((short) v);
        }
        return b.array();
    }

    private static byte[] ints(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] hex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    private static int indexOf(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
