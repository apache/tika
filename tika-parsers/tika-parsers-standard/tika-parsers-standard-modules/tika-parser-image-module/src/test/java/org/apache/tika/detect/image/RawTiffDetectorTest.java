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
package org.apache.tika.detect.image;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;

public class RawTiffDetectorTest {

    /**
     * The default detector, with this module's detectors loaded through SPI.
     */
    private final Detector detector = new DefaultDetector(MimeTypes.getDefaultMimeTypes());

    /**
     * testNEF_dup.nef is a synthetic file with nothing but JPEG previews in
     * it (no Make, no vendor compression, no raw image): rightly a TIFF.
     */
    @ParameterizedTest
    @CsvSource({
            "testNEF.nef, image/x-raw-nikon",
            "testNEF_dup.nef, image/tiff",
            "testARW.arw, image/x-raw-sony",
            "testPEF.pef, image/x-raw-pentax",
            "testDNG.dng, image/x-raw-adobe",
            "testDNG_bigtiff.dng, image/x-raw-adobe",
            "testCR2.cr2, image/x-canon-cr2",
            "testTIFF.tif, image/tiff",
            "testJPEG.jpg, image/jpeg"})
    public void testDetectionWithoutName(String file, String expected) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/test-documents/" + file);
             TikaInputStream tis = TikaInputStream.get(is)) {
            assertEquals(expected, detector.detect(tis, new Metadata(), new ParseContext()).toString());
        }
    }

    /**
     * The stream is left where it was: the parsers that follow read it from
     * the start.
     */
    @Test
    public void testStreamIsReset() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/test-documents/testNEF.nef");
             TikaInputStream tis = TikaInputStream.get(is)) {
            new RawTiffDetector().detect(tis, new Metadata(), new ParseContext());
            assertEquals(0, tis.getPosition());
            assertEquals('M', tis.read());
        }
    }

    /**
     * A TIFF a Nikon camera wrote is not a NEF: RGB data, no vendor
     * compression, no DNGVersion.
     */
    @Test
    public void testCameraTiffStaysTiff() {
        byte[] tiff = tiff("NIKON CORPORATION", 1, 2, false);
        assertEquals(MediaType.OCTET_STREAM, RawTiffDetector.detect(tiff, tiff.length));
    }

    /**
     * An uncompressed raw: no vendor compression code, but a CFA image and
     * the maker's name.
     */
    @Test
    public void testUncompressedRawByMakeAndCfa() {
        byte[] tiff = tiff("PENTAX Corporation", 1, 32803, false);
        assertEquals(RawTiffDetector.PENTAX, RawTiffDetector.detect(tiff, tiff.length));
        tiff = tiff("Unknown Maker", 1, 32803, false);
        assertEquals(MediaType.OCTET_STREAM, RawTiffDetector.detect(tiff, tiff.length));
    }

    @Test
    public void testVendorCompressionWins() {
        byte[] tiff = tiff("NIKON CORPORATION", 34713, 2, false);
        assertEquals(RawTiffDetector.NIKON, RawTiffDetector.detect(tiff, tiff.length));
    }

    /**
     * DNGVersion decides before anything else, also for a DNG a camera
     * maker wrote with its own name in Make.
     */
    @Test
    public void testDngVersionFirst() {
        byte[] tiff = tiff("PENTAX", 65535, 32803, true);
        assertEquals(RawTiffDetector.ADOBE, RawTiffDetector.detect(tiff, tiff.length));
    }

    @Test
    public void testTruncatedPrefixIsHarmless() {
        byte[] tiff = tiff("NIKON CORPORATION", 34713, 32803, false);
        for (int length = 0; length < tiff.length; length++) {
            RawTiffDetector.detect(tiff, length);
        }
    }

    /**
     * A minimal little-endian TIFF: one IFD with Make, Compression,
     * PhotometricInterpretation and, optionally, DNGVersion.
     */
    private static byte[] tiff(String make, int compression, int photometric, boolean dngVersion) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] makeBytes = (make + "\0").getBytes(StandardCharsets.US_ASCII);
        int entries = dngVersion ? 4 : 3;
        int ifdOffset = 8;
        int makeOffset = ifdOffset + 2 + entries * 12 + 4;
        out.writeBytes(new byte[]{'I', 'I', 42, 0});
        le32(out, ifdOffset);
        le16(out, entries);
        entry(out, 0x0103, 3, 1, compression);
        entry(out, 0x0106, 3, 1, photometric);
        entry(out, 0x010F, 2, makeBytes.length, makeOffset);
        if (dngVersion) {
            entry(out, 0xC612, 1, 4, 0x00000401);
        }
        le32(out, 0);
        out.writeBytes(makeBytes);
        return out.toByteArray();
    }

    private static void entry(ByteArrayOutputStream out, int tag, int type, int count, int value) {
        le16(out, tag);
        le16(out, type);
        le32(out, count);
        if (type == 3 && count == 1) {
            le16(out, value);
            le16(out, 0);
        } else {
            le32(out, value);
        }
    }

    private static void le16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void le32(ByteArrayOutputStream out, int v) {
        le16(out, v & 0xFFFF);
        le16(out, (v >> 16) & 0xFFFF);
    }
}
