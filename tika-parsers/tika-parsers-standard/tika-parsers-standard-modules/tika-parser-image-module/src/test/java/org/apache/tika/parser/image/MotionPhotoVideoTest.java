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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * The video a motion photo carries after the image becomes an embedded
 * document (TIKA-4869).
 */
public class MotionPhotoVideoTest extends TikaTest {

    /**
     * A Motion Photo describes its video in Container:Directory.
     */
    @Test
    public void testMotionPhotoVideo() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJPEG_MotionPhoto.jpg");
        assertEquals(2, metadataList.size());
        Metadata video = metadataList.get(1);
        //this module's classpath types the video by mime magic alone, which
        //gets it as far as quicktime; the integration test pins video/mp4
        assertTrue(video.get(HttpHeaders.CONTENT_TYPE).startsWith("video/"),
                video.get(HttpHeaders.CONTENT_TYPE));
        //the Motion Photo format declares the type of its video
        assertEquals("motion-photo.mp4", video.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name(),
                video.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        //the length is known from the declaration, as it is for a zip entry
        assertEquals(String.valueOf(declaredLength()), video.get(HttpHeaders.CONTENT_LENGTH));
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * The older MicroVideo gives the length as an offset from the end.
     */
    @Test
    public void testMicroVideo() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJPEG_MicroVideo.jpg");
        assertEquals(2, metadataList.size());
        Metadata video = metadataList.get(1);
        assertTrue(video.get(HttpHeaders.CONTENT_TYPE).startsWith("video/"),
                video.get(HttpHeaders.CONTENT_TYPE));
        //the MicroVideo format declares no type, so the name carries no extension
        assertEquals("motion-photo", video.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.ATTACHMENT.name(),
                video.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * A JPEG without the XMP of a motion photo keeps to itself.
     */
    @Test
    public void testPlainJpegHasNoVideo() throws Exception {
        assertEquals(1, getRecursiveMetadata("testJPEG.jpg").size());
    }

    /**
     * Sharing a motion photo out of the Android gallery leaves the image with
     * the Camera:MotionPhoto flag but without the video and without the
     * directory that would locate it: nothing to emit, and nothing to
     * complain about.
     */
    @Test
    public void testMotionPhotoFlagWithoutAVideo() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testJPEG_MotionPhoto_noVideo.jpg");
        assertEquals(1, metadataList.size());
        assertEquals("1", metadataList.get(0).get("Camera:MotionPhoto"));
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * The length fits the file, but what is there is not a video: no embedded
     * document, and no complaint either.
     */
    @Test
    public void testDeclaredVideoIsNotOne(@TempDir Path tmp) throws Exception {
        byte[] file = fixture();
        //keep the length, replace the video with something unrecognizable
        java.util.Arrays.fill(file, file.length - declaredLength(), file.length, (byte) 0);
        Path overwritten = tmp.resolve("no-video.jpg");
        Files.write(overwritten, file);

        List<Metadata> metadataList = parse(overwritten);
        assertEquals(1, metadataList.size());
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * A declared length the file cannot hold, which is what stripping the
     * video out of a motion photo leaves: no embedded document, and no
     * complaint about a file that is otherwise fine.
     */
    @Test
    public void testDeclaredLengthBeyondTheFile(@TempDir Path tmp) throws Exception {
        byte[] file = fixture();
        //cut the video the XMP still declares
        Path truncated = tmp.resolve("truncated.jpg");
        Files.write(truncated, java.util.Arrays.copyOf(file, file.length - declaredLength()));

        List<Metadata> metadataList = parse(truncated);
        assertEquals(1, metadataList.size());
        assertNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * The video is the last thing in the file, so its own Item:Length is the
     * whole distance to the end: an item behind it is not added on, and neither
     * is a Padding, which the format only allows on the primary image. Lengths
     * are read from the file, so summing them could be made to overflow.
     */
    @Test
    public void testTheDeclarationIsTheVideosOwnLength() {
        Metadata metadata = new Metadata();
        item(metadata, 1, "Primary", "3000", null);
        item(metadata, 2, "MotionPhoto", "1583", String.valueOf(Long.MAX_VALUE));
        //a shared resource is declared with a length of 0, and may follow the video
        item(metadata, 3, "Segment", "0", null);
        assertEquals(1583, MotionPhoto.declaration(metadata).length);
    }

    /**
     * A length no file could hold ends it, rather than an offset that wraps
     * around into the file and reads somewhere else.
     */
    @Test
    public void testAbsurdDeclaredLength() throws Exception {
        Metadata metadata = new Metadata();
        item(metadata, 1, "Primary", "3000", null);
        item(metadata, 2, "MotionPhoto", String.valueOf(Long.MAX_VALUE), null);
        assertEquals(Long.MAX_VALUE, MotionPhoto.declaration(metadata).length);

        try (TikaInputStream tis = TikaInputStream.get(
                getResourceAsStream("/test-documents/testJPEG_MotionPhoto.jpg"))) {
            XHTMLContentHandler xhtml =
                    new XHTMLContentHandler(new DefaultHandler(), metadata, new ParseContext());
            MotionPhoto.extract(tis, metadata, xhtml, new ParseContext());
        }
    }

    private static void item(Metadata metadata, int index, String semantic, String length,
                             String padding) {
        String item = "xmp-raw:Container:Directory[" + index + "]/Container:Item/";
        metadata.set(item + "Item:Semantic", semantic);
        metadata.set(item + "Item:Length", length);
        if (padding != null) {
            metadata.set(item + "Item:Padding", padding);
        }
    }

    /**
     * What is appended is typed by content, and what is there is emitted even
     * where it is not the video the XMP promised. The name follows the bytes
     * then, not the declaration.
     */
    @Test
    public void testTrailerThatIsNotAVideo(@TempDir Path tmp) throws Exception {
        byte[] file = fixture();
        byte[] pdf = new byte[declaredLength()];
        java.util.Arrays.fill(pdf, (byte) ' ');
        System.arraycopy("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII), 0, pdf, 0, 9);
        System.arraycopy(pdf, 0, file, file.length - pdf.length, pdf.length);
        Path swapped = tmp.resolve("pdf-trailer.jpg");
        Files.write(swapped, file);

        List<Metadata> metadataList = parse(swapped);
        assertEquals(2, metadataList.size());
        Metadata trailer = metadataList.get(1);
        assertEquals("application/pdf", trailer.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("motion-photo.pdf", trailer.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("true",
                trailer.get(TikaCoreProperties.RESOURCE_NAME_EXTENSION_INFERRED));
    }

    /**
     * An image appended to an image is emitted, but is not searched for a
     * trailer of its own: a file that nests itself would otherwise chain as
     * deep as it cares to.
     */
    @Test
    public void testANestedMotionPhotoDoesNotChain(@TempDir Path tmp) throws Exception {
        byte[] inner = fixture();
        //the fixture is as long as the length it declares has digits, so the
        //outer file can declare the whole inner one without moving anything
        byte[] outer = new String(fixture(), StandardCharsets.ISO_8859_1)
                .replace("Item:Length=\"" + declaredLength() + "\"",
                        "Item:Length=\"" + inner.length + "\"")
                .getBytes(StandardCharsets.ISO_8859_1);
        Path nested = tmp.resolve("nested.jpg");
        try (OutputStream out = Files.newOutputStream(nested)) {
            out.write(outer);
            out.write(inner);
        }

        List<Metadata> metadataList = parse(nested);
        assertEquals(2, metadataList.size());
        assertEquals("image/jpeg", metadataList.get(1).get(HttpHeaders.CONTENT_TYPE));
    }

    private byte[] fixture() throws Exception {
        try (InputStream is = getResourceAsStream("/test-documents/testJPEG_MotionPhoto.jpg")) {
            return is.readAllBytes();
        }
    }

    /**
     * The length the fixture's XMP declares, rather than that number spelled
     * out in every test that needs it.
     */
    private int declaredLength() throws Exception {
        return (int) MotionPhoto.declaration(
                getRecursiveMetadata("testJPEG_MotionPhoto.jpg").get(0)).length;
    }

    private List<Metadata> parse(Path file) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(file)) {
            return getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    new ParseContext(), false);
        }
    }
}
