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
package org.apache.tika.parser.ogg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Tests the parsing of native FLAC files.
 */
public class FlacParserTest extends TikaTest {

    /**
     * Cover art in a native PICTURE metadata block becomes an embedded
     * document, with no extra metadata on the audio document itself.
     */
    @Test
    public void testCoverArt() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testFLAC_coverArt.flac");

        assertEquals(2, metadataList.size());
        assertEquals("audio/x-flac", metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));

        Metadata pictureMetadata = metadataList.get(1);
        assertEquals("image/png", pictureMetadata.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                pictureMetadata.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals("Test Cover", pictureMetadata.get(TikaCoreProperties.TITLE));
        assertEquals("Cover (front)", pictureMetadata.get(TikaCoreProperties.DESCRIPTION));
    }

    /**
     * A file with several PICTURE blocks yields one embedded document
     * per picture, in file order.
     */
    @Test
    public void testMultipleCovers() throws Exception {
        List<Metadata> metadataList = getRecursiveMetadata("testFLAC_twoCovers.flac");

        assertEquals(3, metadataList.size());

        Metadata front = metadataList.get(1);
        assertEquals("image/png", front.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Front Cover", front.get(TikaCoreProperties.TITLE));
        assertEquals("Cover (front)", front.get(TikaCoreProperties.DESCRIPTION));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                front.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));

        Metadata back = metadataList.get(2);
        assertEquals("image/png", back.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Back Cover", back.get(TikaCoreProperties.TITLE));
        assertEquals("Cover (back)", back.get(TikaCoreProperties.DESCRIPTION));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                back.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    /**
     * A PICTURE block that declares more data than the file has left ends
     * the walk, but the pictures before it survive: the walk breaks instead
     * of returning or throwing (regression guard for the return-vs-break in
     * readNativePictures).
     */
    @Test
    public void testTruncatedPictureBlockKeepsEarlierPictures(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("fLaC".getBytes(StandardCharsets.US_ASCII));
        byte[] picture = pictureBlock(3, "image/png", "front");
        out.write(blockHeader(false, 6, picture.length));
        out.write(picture);
        //a second PICTURE block declaring far more data than follows
        out.write(blockHeader(true, 6, 0x00FFFF));
        out.write(new byte[]{1, 2, 3});
        Path flac = tmp.resolve("truncated.flac");
        Files.write(flac, out.toByteArray());

        List<OggAudioParser.PictureBlock> pictures = FlacParser.readNativePictures(flac);
        assertEquals(1, pictures.size());
    }

    private static byte[] blockHeader(boolean last, int type, int length) {
        return new byte[]{(byte) ((last ? 0x80 : 0) | type),
                (byte) (length >>> 16), (byte) (length >>> 8), (byte) length};
    }

    private static byte[] pictureBlock(int pictureType, String mimeType, String description)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(pictureType);
        data.writeInt(mimeType.length());
        data.writeBytes(mimeType);
        data.writeInt(description.length());
        data.writeBytes(description);
        data.write(new byte[16]); //geometry
        byte[] image = {(byte) 0x89, 'P', 'N', 'G'};
        data.writeInt(image.length);
        data.write(image);
        return out.toByteArray();
    }
}
