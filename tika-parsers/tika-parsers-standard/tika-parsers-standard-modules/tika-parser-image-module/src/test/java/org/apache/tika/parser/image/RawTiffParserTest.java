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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

    @Test
    public void testSharedImageMetadataConfigApplies() throws Exception {
        ImageMetadataConfig shared = new ImageMetadataConfig();
        shared.setIncludeIccCurvesAndLuts(true);
        ParseContext context = new ParseContext();
        context.set(ImageMetadataConfig.class, shared);
        assertTrue(new RawTiffParser().getImageMetadataConfig(context).isIncludeIccCurvesAndLuts());
        assertFalse(new RawTiffParser().getImageMetadataConfig(new ParseContext())
                .isIncludeIccCurvesAndLuts());
    }

    private List<Metadata> parseByName(String fileName) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        return getRecursiveMetadata(fileName, metadata);
    }

    /**
     * The largest extracted preview: the file's thumbnail, which the parser
     * emits before the smaller ones.
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
        //the IFD knows how long the preview is, so the metadata says so
        assertTrue(Long.parseLong(preview.get(HttpHeaders.CONTENT_LENGTH)) > 0,
                preview.get(HttpHeaders.CONTENT_LENGTH));
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

    /**
     * An ERF reaches this parser because the detector tells it by its Make
     * (Epson's compression code is a published TIFF value other writers use)
     * and the registry makes image/x-raw-epson a TIFF. Its
     * preview hangs in the MakerNote under 0x0280, at an offset counted from
     * the start of the file, and begins with a byte the camera wrote over
     * the SOI: it only parses as a JPEG once that byte is put back.
     */
    @Test
    public void testERF() throws Exception {
        List<Metadata> metadataList = parseByName("testERF.erf");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-epson", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("SEIKO EPSON CORP.", container.get(TIFF.EQUIPMENT_MAKE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    /**
     * ORF keeps its previews in the MakerNote: the preview the CameraSettings
     * sub-IFD points at, and the camera's own thumbnail.
     */
    @Test
    public void testORF() throws Exception {
        List<Metadata> metadataList = parseByName("testORF.orf");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-olympus", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("OLYMPUS CORPORATION", container.get(TIFF.EQUIPMENT_MAKE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    /**
     * A MakerNote carries its own byte order marker and does not have to
     * share the file's: this one is a big endian "MMOR" whose note says
     * "OLYMPUS\0II".
     */
    @Test
    public void testORFBigEndian() throws Exception {
        List<Metadata> metadataList = parseByName("testORF_bigendian.orf");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-olympus", container.get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    /**
     * OM Digital Solutions bodies keep the Olympus layout behind a longer
     * name: "OM SYSTEM\0\0\0", then the byte order marker and the version,
     * and the IFD at 16, with every offset still counted from the MakerNote.
     */
    @Test
    public void testORFOMSystem() throws Exception {
        List<Metadata> metadataList = parseByName("testORF_omsystem.orf");
        assertEquals(3, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-olympus", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("OM Digital Solutions", container.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("OM-3", container.get(TIFF.EQUIPMENT_MODEL));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
        assertInlinePreview(metadataList.get(2), 0, 32, 24);
    }

    /**
     * RW2 keeps its preview in the private JpgFromRaw tag and writes
     * placeholders into the strip tags, which otherwise read as a JPEG strip
     * of no length: the private tag has to answer first.
     */
    @Test
    public void testRW2() throws Exception {
        List<Metadata> metadataList = parseByName("testRW2.rw2");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-panasonic", container.get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    /**
     * A KDC points at its preview with the standard tags, so it only had to
     * reach this parser. Its name is what types it: nothing in the file says
     * raw to the detector.
     */
    @Test
    public void testKDC() throws Exception {
        List<Metadata> metadataList = parseByName("testKDC.kdc");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-kodak", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("EASTMAN KODAK COMPANY", container.get(TIFF.EQUIPMENT_MAKE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    /**
     * A MOS keeps its preview in the Leaf data block, in the section named
     * JPEG_preview_data. The tag is typed ASCII, which the block is not.
     */
    @Test
    public void testMOS() throws Exception {
        List<Metadata> metadataList = parseByName("testMOS.mos");
        assertEquals(2, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-leaf", container.get(HttpHeaders.CONTENT_TYPE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));

        assertThumbnail(metadataList.get(1), 64, 48);
    }

    /**
     * A raw with nothing to hand on is typed and read all the same: this one
     * carries a MakerNote that names no preview, and no thumbnail is an
     * answer, not a failure.
     */
    @Test
    public void testERFWithoutPreview() throws Exception {
        List<Metadata> metadataList = parseByName("testERF_nopreview.erf");
        assertEquals(1, metadataList.size());

        Metadata container = metadataList.get(0);
        assertEquals("image/x-raw-epson", container.get(HttpHeaders.CONTENT_TYPE));
        assertEquals("SEIKO EPSON CORP.", container.get(TIFF.EQUIPMENT_MAKE));
        assertNull(container.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    /**
     * *.raw is also a disk image, PCM audio and more: when only the name
     * says Panasonic, the parser reads nothing instead of failing.
     */
    @Test
    public void testNonTiffByNameOnly() throws Exception {
        byte[] bytes = new byte[4096];
        new Random(42).nextBytes(bytes);
        bytes[0] = 0;
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "disk.raw");
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<Metadata> metadataList =
                    getRecursiveMetadata(tis, metadata, new ParseContext(), false);
            assertEquals(1, metadataList.size());
            assertEquals("image/x-raw-panasonic",
                    metadataList.get(0).get(HttpHeaders.CONTENT_TYPE));
            assertNull(metadataList.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION));
        }
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
        assertEquals("PENTAX", container.get(TIFF.EQUIPMENT_MAKE));
        assertEquals("PENTAX K-x", container.get(TIFF.EQUIPMENT_MODEL));
        assertEquals("0.004", container.get(TIFF.EXPOSURE_TIME));
        assertEquals("5.6", container.get(TIFF.F_NUMBER));
        assertEquals("200", container.get(TIFF.ISO_SPEED_RATINGS));
        assertEquals("50.0", container.get(TIFF.FOCAL_LENGTH));
        assertEquals("2010-01-01T12:00:00", container.get(TIFF.ORIGINAL_DATE));

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
