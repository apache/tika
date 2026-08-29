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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.TikaTest;
import org.apache.tika.config.loader.TikaLoader;
import org.apache.tika.io.TikaInputStream;
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

    /**
     * The largest preview: the file's thumbnail, always emitted first.
     */
    private void assertThumbnail(Metadata preview, int width, int height) {
        assertPreview(preview, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL,
                "thumbnail-0.jpg", width, height);
    }

    /**
     * A smaller preview, emitted after the thumbnail as an inline image.
     */
    private void assertInlinePreview(Metadata preview, int index, int width, int height) {
        assertPreview(preview, TikaCoreProperties.EmbeddedResourceType.INLINE,
                "image-" + index + ".jpg", width, height);
    }

    private void assertPreview(Metadata preview, TikaCoreProperties.EmbeddedResourceType type,
                               String name, int width, int height) {
        assertEquals("image/jpeg", preview.get(HttpHeaders.CONTENT_TYPE));
        assertEquals(name, preview.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals(type.toString(), preview.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
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

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    @Test
    public void testDuplicatePreviewOffsetsDeduplicated() throws Exception {
        //IFD0 and IFD1 both point their JPEGInterchangeFormat at the same region;
        //it is extracted once, not per referencing IFD
        List<Metadata> metadataList = parseByName("testNEF_dup.nef");
        assertEquals(2, metadataList.size());
        assertThumbnail(metadataList.get(1), 64, 48);
    }

    @Test
    public void testARW() throws Exception {
        List<Metadata> metadataList = parseByName("testARW.arw");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-sony", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("SONY", container.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("NEX-6", container.get(TIFF.EQUIPMENT_MODEL));

        //full-size preview from IFD0, then the camera thumbnail from IFD1
        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    @Test
    public void testPEF() throws Exception {
        List<Metadata> metadataList = parseByName("testPEF.pef");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-pentax", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("PENTAX K-7", container.get(TIFF.EQUIPMENT_MODEL));

        //the camera thumbnail comes first in the file (IFD1), the full-size
        //preview second (IFD2): the larger one is still emitted first
        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    @Test
    public void testDNG() throws Exception {
        List<Metadata> metadataList = parseByName("testDNG.dng");
        //the raw strip in SubIFD0 also starts with a JPEG SOI marker
        //(lossless JPEG) and must not be extracted
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-adobe", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("PENTAX K-x", container.get(TIFF.EQUIPMENT_MODEL));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    @Test
    public void testCR2() throws Exception {
        List<Metadata> metadataList = parseByName("testCR2.cr2");
        //the lossless-JPEG raw strip in the last IFD must not be extracted
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-canon-cr2", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("Canon EOS 7D", container.get(TIFF.EQUIPMENT_MODEL));

        //full-size preview stored as a strip in IFD0, thumbnail from IFD1
        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    @Test
    public void testParsedByRawTiffParser() throws Exception {
        List<Metadata> metadataList = parseByName("testNEF.nef");
        List<String> parsedBy =
                Arrays.asList(metadataList.get(0).getValues(TikaCoreProperties.TIKA_PARSED_BY));
        assertContains(RawTiffParser.class.getName(), parsedBy);
    }

    @Test
    public void testBigTiffDNG() throws Exception {
        //DNG allows BigTIFF containers since spec version 1.7
        List<Metadata> metadataList = parseByName("testDNG_bigtiff.dng");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-adobe", container.get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    @Test
    public void testEmptyBigTiffWithoutException() throws Exception {
        //a BigTIFF file without any previews must parse quietly,
        //without recording an exception
        byte[] bigTiff = new byte[]{
                'M', 'M', 0, 0x2B, 0, 8, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 16,  //offset of IFD0
                0, 0, 0, 0, 0, 0, 0, 0,   //IFD0: zero entries
                0, 0, 0, 0, 0, 0, 0, 0    //no next IFD
        };
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-raw-adobe");
        try (TikaInputStream tis = TikaInputStream.get(bigTiff)) {
            new RawTiffParser().parse(tis, new DefaultHandler(), metadata, new ParseContext());
        }
        assertNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    public void testMaxPreviewLength() throws Exception {
        Parser parser = TikaLoader
                .load(getConfigPath(RawTiffParserTest.class,
                        "tika-config-raw-preview-max-length.json"))
                .loadParsers();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testNEF.nef");
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-raw-nikon");
        List<Metadata> metadataList =
                getRecursiveMetadata("testNEF.nef", parser, metadata, new ParseContext(), false);

        //the preview is larger than the configured limit and is skipped
        assertEquals(1, metadataList.size());
    }

    @Test
    public void testMaxTotalPreviewBytes() throws Exception {
        //testARW has two previews (676 + 644 bytes); a 700-byte total budget
        //admits only the first, bounding aggregate extraction work
        Parser parser = TikaLoader
                .load(getConfigPath(RawTiffParserTest.class,
                        "tika-config-raw-total-preview-budget.json"))
                .loadParsers();
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testARW.arw");
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/x-raw-sony");
        List<Metadata> metadataList =
                getRecursiveMetadata("testARW.arw", parser, metadata, new ParseContext(), false);

        assertEquals(2, metadataList.size());
        assertThumbnail(metadataList.get(1), 64, 48);
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
