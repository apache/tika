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
        byte[] file;
        try (InputStream is = getResourceAsStream("/test-documents/testJPEG_MotionPhoto.jpg")) {
            file = is.readAllBytes();
        }
        //keep the length, replace the video with something unrecognizable
        java.util.Arrays.fill(file, file.length - 1583, file.length, (byte) 0);
        Path overwritten = tmp.resolve("no-video.jpg");
        Files.write(overwritten, file);

        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(overwritten)) {
            metadataList = getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    new ParseContext(), false);
        }
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
        byte[] file;
        try (InputStream is = getResourceAsStream("/test-documents/testJPEG_MotionPhoto.jpg")) {
            file = is.readAllBytes();
        }
        //cut the video the XMP still declares
        Path truncated = tmp.resolve("truncated.jpg");
        Files.write(truncated, java.util.Arrays.copyOf(file, file.length - 1583));

        List<Metadata> metadataList;
        try (TikaInputStream tis = TikaInputStream.get(truncated)) {
            metadataList = getRecursiveMetadata(tis, AUTO_DETECT_PARSER, new Metadata(),
                    new ParseContext(), false);
        }
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
}
