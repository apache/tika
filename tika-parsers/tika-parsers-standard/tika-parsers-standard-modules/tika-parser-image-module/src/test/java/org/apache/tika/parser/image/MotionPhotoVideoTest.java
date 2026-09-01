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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;

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
        assertTrue(video.get(TikaCoreProperties.RESOURCE_NAME_KEY).startsWith("motion-photo."),
                video.get(TikaCoreProperties.RESOURCE_NAME_KEY));
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
     * A declared length the file cannot hold: no embedded document, and the
     * image says why.
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
        assertNotNull(metadataList.get(0).get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }
}
