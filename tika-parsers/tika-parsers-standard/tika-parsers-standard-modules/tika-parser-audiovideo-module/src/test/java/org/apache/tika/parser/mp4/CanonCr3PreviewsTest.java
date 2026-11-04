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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Canon CR3 previews, on a file built here with the box layout of a real CR3: a
 * {@code THMB} record in the Canon {@code uuid} box inside {@code moov}, a
 * {@code PRVW} record in the Canon {@code uuid} box at the top level.
 */
public class CanonCr3PreviewsTest extends TikaTest {

    private static final String METADATA_UUID = "85c0b687820f11e08111f4ce462b6a48";
    private static final String PREVIEW_UUID = "eaf42b5e1c984b88b9fbb7dc406e4d16";

    @Test
    public void testPreviewIsThumbnailAndThmbIsInline() throws Exception {
        byte[] thmb = jpeg(16, 12);
        byte[] prvw = jpeg(48, 32);
        List<Metadata> embedded = embedded(cr3(thmb, prvw));
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
        parse(cr3(jpeg(16, 12), prvw), new ArrayList<>(), bytes);
        assertArrayEquals(prvw, bytes.get(1));
    }

    @Test
    public void testLengthBeyondTheBoxIsIgnored() throws Exception {
        byte[] file = cr3(jpeg(16, 12), jpeg(48, 32));
        // claim a longer JPEG than the PRVW box holds
        int prvw = indexOf(file, "PRVW".getBytes(StandardCharsets.US_ASCII));
        ByteBuffer.wrap(file).putInt(prvw + 16, Integer.MAX_VALUE);
        List<Metadata> embedded = embedded(file);
        assertEquals(1, embedded.size());
        assertEquals("INLINE", embedded.get(0).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    private List<Metadata> embedded(byte[] file) throws Exception {
        List<Metadata> metadata = new ArrayList<>();
        parse(file, metadata, new ArrayList<>());
        return metadata;
    }

    private void parse(byte[] file, List<Metadata> metadata, List<byte[]> bytes)
            throws Exception {
        ParseContext context = new ParseContext();
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata m, ParseContext ctx) {
                return true;
            }

            @Override
            public void parseEmbedded(TikaInputStream stream, org.xml.sax.ContentHandler handler,
                                      Metadata m, ParseContext ctx, boolean outputHtml)
                    throws IOException {
                metadata.add(m);
                bytes.add(stream.readAllBytes());
            }
        });
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            new MP4Parser().parse(tis, new BodyContentHandler(), new Metadata(), context);
        }
    }

    private static byte[] cr3(byte[] thmb, byte[] prvw) throws IOException {
        ByteArrayOutputStream ftyp = new ByteArrayOutputStream();
        ftyp.write(ascii("crx "));
        ftyp.write(new byte[]{0, 0, 0, 1});
        ftyp.write(ascii("crx isom"));

        ByteArrayOutputStream thmbBox = new ByteArrayOutputStream();
        thmbBox.write(new byte[4]);
        thmbBox.write(shorts(16, 12));
        thmbBox.write(ints(thmb.length));
        thmbBox.write(new byte[]{0, 1, 0, 0});
        thmbBox.write(thmb);
        ByteArrayOutputStream metadataUuid = new ByteArrayOutputStream();
        metadataUuid.write(hex(METADATA_UUID));
        metadataUuid.write(box("CNCV", ascii("CanonCR3_001/01.09.00/00.00.00")));
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
        file.write(box("ftyp", ftyp.toByteArray()));
        file.write(box("moov", box("uuid", metadataUuid.toByteArray())));
        file.write(box("uuid", previewUuid.toByteArray()));
        file.write(box("mdat", new byte[16]));
        return file.toByteArray();
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
