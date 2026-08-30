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

    /**
     * The NRW layout: a JPEG-compressed thumbnail in IFD0, the raw image with
     * its CFA data in a SubIFD. The non-vendor Compression of IFD0 does not
     * end the search.
     */
    @Test
    public void testRawInSubIfd() {
        byte[] tiff = new TiffBuilder(false)
                .ifd(entry(0x0103, 3, 6), entry(0x010F, "NIKON CORPORATION"), subIfds(1))
                .ifd(entry(0x0103, 3, 1), entry(0x0106, 3, 32803))
                .build();
        assertEquals(RawTiffDetector.NIKON, RawTiffDetector.detect(tiff, tiff.length));
    }

    /**
     * BigTIFF: 8-byte counts, offsets and inline values, a LONG8 SubIFDs
     * entry, and the vendor code inside the SubIFD.
     */
    @Test
    public void testBigTiffWithLong8SubIfd() {
        byte[] tiff = new TiffBuilder(true)
                .ifd(entry(0x010F, "SONY"), subIfds(1))
                .ifd(entry(0x0103, 3, 32767))
                .build();
        assertEquals(RawTiffDetector.SONY, RawTiffDetector.detect(tiff, tiff.length));
    }

    @Test
    public void testSamsungCompressionCodes() {
        for (int code : new int[]{32770, 32772}) {
            byte[] tiff = tiff("SAMSUNG", code, 2, false);
            assertEquals(RawTiffDetector.SAMSUNG, RawTiffDetector.detect(tiff, tiff.length));
        }
    }

    /**
     * An IFD chain that points back at itself, and a SubIFDs array with many
     * pointers to the same IFD: the walk ends.
     */
    @Test
    public void testCyclesEnd() {
        byte[] tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "NIKON"), entry(0x0106, 3, 32803))
                .nextPointsToSelf()
                .build();
        assertEquals(RawTiffDetector.NIKON, RawTiffDetector.detect(tiff, tiff.length));

        tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "Unknown"), subIfds(64))
                .ifd(entry(0x0103, 3, 1))
                .build();
        assertEquals(MediaType.OCTET_STREAM, RawTiffDetector.detect(tiff, tiff.length));
    }

    /**
     * The Samsung NX1 layout: the raw SubIFD has no PhotometricInterpretation
     * and a compression code that is also PackBits, so only the shape of the
     * image (full resolution, one 14 bit sample) and the Make say raw.
     */
    @Test
    public void testDeepSingleSampleWithoutPhotometric() {
        byte[] tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "SAMSUNG"), subIfds(1))
                .ifd(entry(0x00FE, 4, 0), entry(0x0102, 3, 14), entry(0x0103, 4, 32773))
                .build();
        assertEquals(RawTiffDetector.SAMSUNG, RawTiffDetector.detect(tiff, tiff.length));
        //the same image with RGB data is a TIFF
        tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "SAMSUNG"), subIfds(1))
                .ifd(entry(0x00FE, 4, 0), entry(0x0102, 3, 8), entry(0x0106, 3, 2))
                .build();
        assertEquals(MediaType.OCTET_STREAM, RawTiffDetector.detect(tiff, tiff.length));
    }

    /**
     * Directories behind a few hundred KB of preview data are read on demand;
     * beyond the limit they are not, and the file stays a TIFF.
     */
    @Test
    public void testDirectoriesBehindPreviewData() throws Exception {
        byte[] tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "NIKON CORPORATION"), subIfds(1))
                .gap(600 * 1024)
                .ifd(entry(0x0103, 3, 34713))
                .build();
        try (TikaInputStream tis = TikaInputStream.get(tiff)) {
            assertEquals(RawTiffDetector.NIKON,
                    new RawTiffDetector().detect(tis, new Metadata(), new ParseContext()));
            assertEquals(0, tis.getPosition());
        }
        tiff = new TiffBuilder(false)
                .ifd(entry(0x010F, "NIKON CORPORATION"), subIfds(1))
                .gap(RawTiffDetector.MAX_PREFIX_LENGTH + 1024)
                .ifd(entry(0x0103, 3, 34713))
                .build();
        try (TikaInputStream tis = TikaInputStream.get(tiff)) {
            assertEquals(MediaType.OCTET_STREAM,
                    new RawTiffDetector().detect(tis, new Metadata(), new ParseContext()));
        }
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

    private static Entry entry(int tag, int type, long value) {
        return new Entry(tag, type, 1, null, value);
    }

    private static Entry entry(int tag, String ascii) {
        byte[] bytes = (ascii + "\0").getBytes(StandardCharsets.US_ASCII);
        return new Entry(tag, 2, bytes.length, bytes, 0);
    }

    /**
     * A SubIFDs entry with {@code count} pointers, all to the IFD that
     * follows the current one.
     */
    private static Entry subIfds(int count) {
        return new Entry(0x014A, -1, count, null, 0);
    }

    private record Entry(int tag, int type, long count, byte[] data, long value) {
    }

    /**
     * Lays out IFDs one after the other, each followed by its out-of-line
     * data; classic or BigTIFF, little endian.
     */
    private static final class TiffBuilder {
        private final boolean bigTiff;
        private final java.util.List<Entry[]> ifds = new java.util.ArrayList<>();
        private boolean nextPointsToSelf;
        private final java.util.Map<Integer, Integer> gaps = new java.util.HashMap<>();

        /**
         * Filler bytes between the last added IFD and the next one.
         */
        TiffBuilder gap(int bytes) {
            gaps.put(ifds.size(), bytes);
            return this;
        }

        TiffBuilder(boolean bigTiff) {
            this.bigTiff = bigTiff;
        }

        TiffBuilder ifd(Entry... entries) {
            ifds.add(entries);
            return this;
        }

        TiffBuilder nextPointsToSelf() {
            nextPointsToSelf = true;
            return this;
        }

        byte[] build() {
            int headerSize = bigTiff ? 16 : 8;
            int countSize = bigTiff ? 8 : 2;
            int entrySize = bigTiff ? 20 : 12;
            int offsetSize = bigTiff ? 8 : 4;
            int inline = bigTiff ? 8 : 4;
            //first pass: where does each IFD start
            long[] starts = new long[ifds.size()];
            long pos = headerSize;
            for (int i = 0; i < ifds.size(); i++) {
                pos += gaps.getOrDefault(i, 0);
                starts[i] = pos;
                pos += countSize + (long) ifds.get(i).length * entrySize + offsetSize;
                for (Entry e : ifds.get(i)) {
                    pos += outOfLineSize(e, inline);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(new byte[]{'I', 'I', (byte) (bigTiff ? 43 : 42), 0});
            if (bigTiff) {
                le16(out, 8);
                le16(out, 0);
                le64(out, starts[0]);
            } else {
                le32(out, (int) starts[0]);
            }
            for (int i = 0; i < ifds.size(); i++) {
                pad(out, gaps.getOrDefault(i, 0));
                Entry[] entries = ifds.get(i);
                long dataPos = starts[i] + countSize + (long) entries.length * entrySize + offsetSize;
                if (bigTiff) {
                    le64(out, entries.length);
                } else {
                    le16(out, entries.length);
                }
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                for (Entry e : entries) {
                    le16(out, e.tag());
                    if (e.tag() == 0x014A) {
                        int type = bigTiff ? 16 : 4;
                        int size = bigTiff ? 8 : 4;
                        le16(out, type);
                        count(out, e.count());
                        long target = i + 1 < starts.length ? starts[i + 1] : 0;
                        if (e.count() * size <= inline) {
                            for (int k = 0; k < e.count(); k++) {
                                offset(out, target, size);
                            }
                            pad(out, (int) (inline - e.count() * size));
                        } else {
                            offset(out, dataPos + data.size(), inline);
                            for (int k = 0; k < e.count(); k++) {
                                offset(data, target, size);
                            }
                        }
                    } else if (e.data() != null) {
                        le16(out, e.type());
                        count(out, e.count());
                        if (e.data().length <= inline) {
                            out.writeBytes(e.data());
                            pad(out, inline - e.data().length);
                        } else {
                            offset(out, dataPos + data.size(), inline);
                            data.writeBytes(e.data());
                        }
                    } else {
                        le16(out, e.type());
                        count(out, 1);
                        if (e.type() == 3) {
                            le16(out, (int) e.value());
                            pad(out, inline - 2);
                        } else {
                            le32(out, (int) e.value());
                            pad(out, inline - 4);
                        }
                    }
                }
                long next = nextPointsToSelf ? starts[i] : 0;
                offset(out, next, offsetSize);
                out.writeBytes(data.toByteArray());
            }
            return out.toByteArray();
        }

        private long outOfLineSize(Entry e, int inline) {
            if (e.tag() == 0x014A) {
                int size = bigTiff ? 8 : 4;
                return e.count() * size <= inline ? 0 : e.count() * size;
            }
            if (e.data() != null) {
                return e.data().length <= inline ? 0 : e.data().length;
            }
            return 0;
        }

        private void count(ByteArrayOutputStream out, long count) {
            if (bigTiff) {
                le64(out, count);
            } else {
                le32(out, (int) count);
            }
        }

        private static void offset(ByteArrayOutputStream out, long value, int size) {
            if (size == 8) {
                le64(out, value);
            } else {
                le32(out, (int) value);
            }
        }

        private static void pad(ByteArrayOutputStream out, int n) {
            for (int i = 0; i < n; i++) {
                out.write(0);
            }
        }
    }

    private static void le64(ByteArrayOutputStream out, long v) {
        le32(out, (int) (v & 0xFFFFFFFFL));
        le32(out, (int) ((v >>> 32) & 0xFFFFFFFFL));
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
