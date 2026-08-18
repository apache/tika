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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class RawTiffParserTest extends TikaTest {

    private List<Metadata> parseByName(String fileName) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        return getRecursiveMetadata(fileName, metadata);
    }

    private void assertPreview(Metadata preview, int index, int width, int height) {
        assertEquals("image/jpeg", preview.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("thumbnail-" + index + ".jpg",
                preview.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString(),
                preview.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertEquals(Integer.toString(width), preview.get(TIFF.IMAGE_WIDTH));
        assertEquals(Integer.toString(height), preview.get(TIFF.IMAGE_LENGTH));
    }

    @Test
    public void testNEF() throws Exception {
        List<Metadata> metadataList = parseByName("testNEF.nef");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-nikon", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("NIKON CORPORATION", container.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("NIKON D3000", container.get(TIFF.EQUIPMENT_MODEL));

        assertPreview(metadataList.get(1), 0, 64, 48);
    }

    @Test
    public void testARW() throws Exception {
        List<Metadata> metadataList = parseByName("testARW.arw");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-sony", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("SONY", container.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("NEX-6", container.get(TIFF.EQUIPMENT_MODEL));

        //full-size preview from IFD0, then the thumbnail from IFD1
        assertPreview(metadataList.get(1), 0, 64, 48);
        assertPreview(metadataList.get(2), 1, 32, 24);
    }

    @Test
    public void testPEF() throws Exception {
        List<Metadata> metadataList = parseByName("testPEF.pef");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-pentax", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("PENTAX K-7", container.get(TIFF.EQUIPMENT_MODEL));

        //thumbnail from IFD1, then the full-size preview from IFD2
        assertPreview(metadataList.get(1), 0, 32, 24);
        assertPreview(metadataList.get(2), 1, 64, 48);
    }

    @Test
    public void testParsedByRawTiffParser() throws Exception {
        List<Metadata> metadataList = parseByName("testNEF.nef");
        List<String> parsedBy =
                Arrays.asList(metadataList.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY));
        assertContains(RawTiffParser.class.getName(), parsedBy);
    }

    @Test
    public void testExtractPreviewsDisabled() throws Exception {
        Parser parser = TikaLoader
                .load(getConfigPath(RawTiffParserTest.class, "tika-config-raw-previews-off.json"))
                .loadParsers();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testNEF.nef");
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-raw-nikon");
        List<Metadata> metadataList =
                getRecursiveMetadata("testNEF.nef", parser, metadata, new ParseContext(), false);

        assertEquals(1, metadataList.size());
        assertEquals("NIKON CORPORATION", metadataList.get(0).get(TIFF.EQUIPMENT_MAKE));
    }
}
