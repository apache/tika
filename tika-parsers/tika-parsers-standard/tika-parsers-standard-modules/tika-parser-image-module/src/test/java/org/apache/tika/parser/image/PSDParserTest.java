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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class PSDParserTest extends TikaTest {

    private final Parser parser = new PSDParser();

    /**
     * Tests a very basic file, without much metadata
     */
    @Test
    public void testPSD() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-psd");
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testPSD.psd")) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }

        assertEquals("537", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals("51", metadata.get(TIFF.IMAGE_LENGTH));
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));
    }

    /**
     * Tests a very basic file, without much metadata,
     * where some of the data lengths are padded to be even
     */
    @Test
    public void testOddPSD() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-psd");
        try (TikaInputStream tis = getResourceAsStream("/test-documents/testPSD2.psd")) {
            parser.parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertEquals("69", metadata.get(TIFF.IMAGE_WIDTH));
        assertEquals("70", metadata.get(TIFF.IMAGE_LENGTH));
        assertEquals("8", metadata.get(TIFF.BITS_PER_SAMPLE));
    }

    @Test
    public void testXMP() throws Exception {
        Metadata metadata = getXML("testPSD_xmp.psd").metadata;
        assertEquals("Adobe Photoshop CC 2014 (Macintosh)",
                metadata.get(XMPMM.HISTORY_SOFTWARE_AGENT));
        assertEquals("xmp.iid:63681182-81a0-4035-b4b2-19bea6201c05",
                metadata.get(XMPMM.HISTORY_EVENT_INSTANCEID));
    }

    @Test
    public void testMaxLength() throws Exception {
        Parser p = TikaLoader.load(
                        getConfigPath(PSDParserTest.class, "tika-config-TIKA-3243.json"))
                .loadParsers();
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-psd");
        assertThrows(TikaException.class, () -> {
            getXML("testPSD_xmp.psd", p, metadata);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"testPSD2.psd:69", "testPSD_xmp.psd:160"})
    public void testThumbnail(String fileAndWidth) throws Exception {
        String[] parts = fileAndWidth.split(":");
        List<Metadata> metadataList = getRecursiveMetadata(parts[0]);
        assertEquals(2, metadataList.size());
        Metadata thumbnail = metadataList.get(1);
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                thumbnail.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("image/jpeg", thumbnail.get(HttpHeaders.CONTENT_TYPE));
        //only a correctly sliced JPEG decodes to the thumbnail's dimensions
        assertEquals(parts[1], thumbnail.get(TIFF.IMAGE_WIDTH));
    }

    @Test
    public void testSyntheticThumbnail() throws Exception {
        byte[] jpeg = jpeg(8, 4);
        List<Metadata> metadataList = parseSynthetic(thumbnailResource(1, jpeg.length, jpeg));
        assertEquals(2, metadataList.size());
        assertEquals("8", metadataList.get(1).get(TIFF.IMAGE_WIDTH));
        assertEquals("4", metadataList.get(1).get(TIFF.IMAGE_LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"notJpegFormat", "sizePastResource", "sizeHuge", "sizeOne", "noSoi"})
    public void testMalformedThumbnailIsIgnored(String variant) throws Exception {
        byte[] jpeg = jpeg(8, 4);
        byte[] resource;
        switch (variant) {
            case "notJpegFormat":
                resource = thumbnailResource(0, jpeg.length, jpeg);
                break;
            case "sizePastResource":
                //+2: an odd resource is padded by one byte that the size may cover
                resource = thumbnailResource(1, jpeg.length + 2L, jpeg);
                break;
            case "sizeHuge":
                resource = thumbnailResource(1, 0xFFFFFFF0L, jpeg);
                break;
            case "sizeOne":
                resource = thumbnailResource(1, 1, jpeg);
                break;
            default:
                jpeg[1] = 0;
                resource = thumbnailResource(1, jpeg.length, jpeg);
                break;
        }
        List<Metadata> metadataList = parseSynthetic(resource);
        assertEquals(1, metadataList.size(), variant);
        assertEquals("1", metadataList.get(0).get(TIFF.IMAGE_WIDTH), variant);
    }

    private List<Metadata> parseSynthetic(byte[] resourceData) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(psdWithThumbnail(resourceData))) {
            return getRecursiveMetadata(tis, new Metadata(), new ParseContext(), false);
        }
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", bos);
        return bos.toByteArray();
    }

    private static byte[] thumbnailResource(int format, long compressedSize, byte[] jpeg)
            throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(format);
        out.writeInt(8); //width
        out.writeInt(4); //height
        out.writeInt(24); //widthbytes
        out.writeInt(96); //total size
        out.writeInt((int) compressedSize);
        out.writeShort(24); //bits per pixel
        out.writeShort(1); //planes
        out.write(jpeg);
        return bos.toByteArray();
    }

    private static byte[] psdWithThumbnail(byte[] resourceData) throws IOException {
        int padded = resourceData.length + (resourceData.length % 2);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.write("8BPS".getBytes(StandardCharsets.US_ASCII));
        out.writeShort(1); //version
        out.write(new byte[6]); //reserved
        out.writeShort(3); //channels
        out.writeInt(1); //height
        out.writeInt(1); //width
        out.writeShort(8); //depth
        out.writeShort(3); //RGB
        out.writeInt(0); //color mode section
        out.writeInt(4 + 2 + 2 + 4 + padded); //image resources section
        out.write("8BIM".getBytes(StandardCharsets.US_ASCII));
        out.writeShort(0x040C);
        out.writeShort(0); //empty name, padded to even
        out.writeInt(resourceData.length);
        out.write(resourceData);
        out.write(new byte[padded - resourceData.length]);
        return bos.toByteArray();
    }

    @Test
    public void testNoThumbnail() throws Exception {
        assertEquals(1, getRecursiveMetadata("testPSD.psd").size());
    }

    @Test
    public void testOversizedThumbnailIsSkippedWithWarning() throws Exception {
        PSDParser.PSDParserConfig config = new PSDParser.PSDParserConfig();
        config.setMaxDataLengthBytes(1000);
        List<Metadata> metadataList = getRecursiveMetadata("testPSD2.psd",
                new PSDParser(config), new Metadata(), new ParseContext(), false);
        assertEquals(1, metadataList.size());
        assertContains("exceeds 1000",
                metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
        assertNotNull(metadataList.get(0).get(TIFF.IMAGE_WIDTH));
    }
}
