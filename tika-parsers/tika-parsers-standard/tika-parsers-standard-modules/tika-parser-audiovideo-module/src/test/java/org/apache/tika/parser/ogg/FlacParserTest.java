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

import java.util.List;

import org.junit.jupiter.api.Test;

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
}
