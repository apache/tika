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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Tells the TIFF-based raw camera formats (Nikon NEF/NRW, Pentax PEF/PTX,
 * Sony ARW/SRF/SR2, Samsung SRW, Adobe DNG) from a plain TIFF by content,
 * so they are recognized without a file name (TIKA-4861). They share the
 * TIFF magic; what differs is the image directory:
 * <ol>
 *   <li>a {@code DNGVersion} tag (0xC612) makes a DNG, whoever wrote it;</li>
 *   <li>a vendor-specific {@code Compression} value (0x0103) in any IFD
 *       names the format: 34713 Nikon, 32767 Sony, 65535 Pentax,
 *       32770 and 32772 Samsung (this is how ExifTool identifies them);</li>
 *   <li>otherwise an image with {@code PhotometricInterpretation} 32803
 *       (CFA) or 34892 (LinearRaw) in any IFD marks a raw, and the
 *       {@code Make} tag (0x010F) picks the vendor: this catches the
 *       uncompressed variants. A plain TIFF from the same camera has RGB
 *       data and stays {@code image/tiff}.</li>
 * </ol>
 * The detector reads a bounded prefix of the stream into memory and walks
 * the IFD chain and the SubIFDs (0x014A) inside it; raw files keep their
 * directories at the start, ahead of the image data. Anything it cannot
 * decide is left to the other detectors as {@code application/octet-stream}.
 * Canon CR2 has a fixed signature and is matched by the mime magic instead.
 */
@TikaComponent
public class RawTiffDetector implements Detector {

    private static final long serialVersionUID = 1L;

    /**
     * How much of the stream is examined. The directories of a raw file
     * sit at its start; a few hundred KB cover them with room to spare.
     */
    static final int PREFIX_LENGTH = 256 * 1024;

    private static final int TAG_MAKE = 0x010F;
    private static final int TAG_COMPRESSION = 0x0103;
    private static final int TAG_PHOTOMETRIC_INTERPRETATION = 0x0106;
    private static final int TAG_SUB_IFDS = 0x014A;
    private static final int TAG_DNG_VERSION = 0xC612;

    private static final int COMPRESSION_NIKON = 34713;
    private static final int COMPRESSION_SONY = 32767;
    private static final int COMPRESSION_PENTAX = 65535;
    private static final int COMPRESSION_SAMSUNG = 32770;
    private static final int COMPRESSION_SAMSUNG_2 = 32772;

    private static final int PHOTOMETRIC_CFA = 32803;
    private static final int PHOTOMETRIC_LINEAR_RAW = 34892;

    private static final int TYPE_ASCII = 2;
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_IFD = 13;
    private static final int TYPE_LONG8 = 16;
    private static final int TYPE_IFD8 = 18;

    private static final int MAX_IFDS = 32;
    private static final int MAX_ENTRIES_PER_IFD = 1024;
    private static final int MAX_MAKE_LENGTH = 256;

    static final MediaType NIKON = MediaType.image("x-raw-nikon");
    static final MediaType PENTAX = MediaType.image("x-raw-pentax");
    static final MediaType SONY = MediaType.image("x-raw-sony");
    static final MediaType SAMSUNG = MediaType.image("x-raw-samsung");
    static final MediaType ADOBE = MediaType.image("x-raw-adobe");

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }
        //one more than is read: a BufferedInputStream drops the mark once the
        //read limit is reached, and reset() in finally must always succeed
        tis.mark(PREFIX_LENGTH + 1);
        try {
            byte[] header = new byte[8];
            if (tis.readNBytes(header, 0, 8) < 8 || !isTiff(header)) {
                return MediaType.OCTET_STREAM;
            }
            byte[] prefix = new byte[PREFIX_LENGTH];
            System.arraycopy(header, 0, prefix, 0, 8);
            int length = 8 + tis.readNBytes(prefix, 8, PREFIX_LENGTH - 8);
            return detect(prefix, length);
        } finally {
            tis.reset();
        }
    }

    private static boolean isTiff(byte[] h) {
        boolean bigEndian = h[0] == 'M' && h[1] == 'M';
        boolean littleEndian = h[0] == 'I' && h[1] == 'I';
        if (!bigEndian && !littleEndian) {
            return false;
        }
        int magic = getUInt16(h, 2, bigEndian);
        return magic == 42 || magic == 43;
    }

    /**
     * @param buf    the start of the file
     * @param length how many bytes of it are valid
     * @return the raw type, or {@link MediaType#OCTET_STREAM} if the prefix
     * does not show one
     */
    static MediaType detect(byte[] buf, int length) {
        if (length < 8 || !isTiff(buf)) {
            return MediaType.OCTET_STREAM;
        }
        boolean bigEndian = buf[0] == 'M';
        boolean bigTiff = getUInt16(buf, 2, bigEndian) == 43;
        int countSize = bigTiff ? 8 : 2;
        int entrySize = bigTiff ? 20 : 12;
        int offsetSize = bigTiff ? 8 : 4;
        int inlineSize = bigTiff ? 8 : 4;
        long firstIfd;
        if (bigTiff) {
            if (length < 16 || getUInt16(buf, 4, bigEndian) != 8) {
                return MediaType.OCTET_STREAM;
            }
            firstIfd = getUInt64(buf, 8, bigEndian);
        } else {
            firstIfd = getUInt32(buf, 4, bigEndian);
        }

        boolean dng = false;
        MediaType byCompression = null;
        boolean rawImage = false;
        String make = null;

        Set<Long> visited = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>();
        toVisit.add(firstIfd);
        while (!toVisit.isEmpty() && visited.size() < MAX_IFDS) {
            long ifdOffset = toVisit.poll();
            if (ifdOffset <= 0 || ifdOffset > length - countSize || !visited.add(ifdOffset)) {
                continue;
            }
            int ifd = (int) ifdOffset;
            long numEntries = bigTiff ? getUInt64(buf, ifd, bigEndian) : getUInt16(buf, ifd, bigEndian);
            if (numEntries < 0 || numEntries > MAX_ENTRIES_PER_IFD
                    || ifd + countSize + numEntries * entrySize + offsetSize > length) {
                continue;
            }
            int entries = ifd + countSize;
            for (int i = 0; i < numEntries; i++) {
                int e = entries + i * entrySize;
                int tag = getUInt16(buf, e, bigEndian);
                int type = getUInt16(buf, e + 2, bigEndian);
                long count = bigTiff ? getUInt64(buf, e + 4, bigEndian) : getUInt32(buf, e + 4, bigEndian);
                int valueField = e + 4 + inlineSize;
                switch (tag) {
                    case TAG_DNG_VERSION:
                        dng = true;
                        break;
                    case TAG_SUB_IFDS:
                        //only MAX_IFDS can ever be visited: do not read more pointers
                        for (long sub : longValues(buf, length, valueField, bigEndian, bigTiff, type,
                                Math.min(count, MAX_IFDS))) {
                            if (toVisit.size() < MAX_IFDS) {
                                toVisit.add(sub);
                            }
                        }
                        break;
                    case TAG_COMPRESSION:
                        if (byCompression == null && count == 1) {
                            long[] v = longValues(buf, length, valueField, bigEndian, bigTiff, type, count);
                            byCompression = v.length == 1 ? vendorByCompression(v[0]) : null;
                        }
                        break;
                    case TAG_PHOTOMETRIC_INTERPRETATION:
                        if (count == 1) {
                            long[] v = longValues(buf, length, valueField, bigEndian, bigTiff, type, count);
                            if (v.length == 1
                                    && (v[0] == PHOTOMETRIC_CFA || v[0] == PHOTOMETRIC_LINEAR_RAW)) {
                                rawImage = true;
                            }
                        }
                        break;
                    case TAG_MAKE:
                        if (make == null && type == TYPE_ASCII) {
                            make = asciiValue(buf, length, valueField, bigEndian, bigTiff, count);
                        }
                        break;
                    default:
                        break;
                }
            }
            int follower = (int) (entries + numEntries * entrySize);
            long next = bigTiff ? getUInt64(buf, follower, bigEndian) : getUInt32(buf, follower, bigEndian);
            if (toVisit.size() < MAX_IFDS) {
                toVisit.add(next);
            }
        }

        if (dng) {
            return ADOBE;
        }
        if (byCompression != null) {
            return byCompression;
        }
        if (rawImage && make != null) {
            MediaType byMake = vendorByMake(make);
            if (byMake != null) {
                return byMake;
            }
        }
        return MediaType.OCTET_STREAM;
    }

    private static MediaType vendorByCompression(long compression) {
        if (compression == COMPRESSION_NIKON) {
            return NIKON;
        } else if (compression == COMPRESSION_SONY) {
            return SONY;
        } else if (compression == COMPRESSION_PENTAX) {
            return PENTAX;
        } else if (compression == COMPRESSION_SAMSUNG || compression == COMPRESSION_SAMSUNG_2) {
            return SAMSUNG;
        }
        return null;
    }

    private static MediaType vendorByMake(String make) {
        String m = make.trim().toUpperCase(Locale.ROOT);
        if (m.startsWith("NIKON")) {
            return NIKON;
        } else if (m.startsWith("PENTAX") || m.startsWith("RICOH")) {
            return PENTAX;
        } else if (m.startsWith("SONY")) {
            return SONY;
        } else if (m.startsWith("SAMSUNG")) {
            return SAMSUNG;
        }
        return null;
    }

    /**
     * The values of a SHORT, LONG, IFD (and for BigTIFF LONG8, IFD8) entry,
     * inline or at their offset; empty if the entry is another type or
     * points outside the prefix.
     */
    private static long[] longValues(byte[] buf, int length, int valueField, boolean bigEndian,
                                     boolean bigTiff, int type, long count) {
        int typeSize;
        if (type == TYPE_SHORT) {
            typeSize = 2;
        } else if (type == TYPE_LONG || type == TYPE_IFD) {
            typeSize = 4;
        } else if (bigTiff && (type == TYPE_LONG8 || type == TYPE_IFD8)) {
            typeSize = 8;
        } else {
            return new long[0];
        }
        if (count < 1 || count > MAX_ENTRIES_PER_IFD) {
            return new long[0];
        }
        int n = (int) count;
        long totalBytes = (long) typeSize * n;
        int off;
        if (totalBytes <= (bigTiff ? 8 : 4)) {
            off = valueField;
        } else {
            long valueOffset = bigTiff
                    ? getUInt64(buf, valueField, bigEndian) : getUInt32(buf, valueField, bigEndian);
            if (valueOffset < 0 || valueOffset > length - totalBytes) {
                return new long[0];
            }
            off = (int) valueOffset;
        }
        long[] values = new long[n];
        for (int i = 0; i < n; i++) {
            int p = off + i * typeSize;
            if (typeSize == 2) {
                values[i] = getUInt16(buf, p, bigEndian);
            } else if (typeSize == 4) {
                values[i] = getUInt32(buf, p, bigEndian);
            } else {
                values[i] = getUInt64(buf, p, bigEndian);
            }
        }
        return values;
    }

    private static String asciiValue(byte[] buf, int length, int valueField, boolean bigEndian,
                                     boolean bigTiff, long count) {
        if (count < 1 || count > MAX_MAKE_LENGTH) {
            return null;
        }
        int n = (int) count;
        int off;
        if (n <= (bigTiff ? 8 : 4)) {
            off = valueField;
        } else {
            long valueOffset = bigTiff
                    ? getUInt64(buf, valueField, bigEndian) : getUInt32(buf, valueField, bigEndian);
            if (valueOffset < 0 || valueOffset > length - n) {
                return null;
            }
            off = (int) valueOffset;
        }
        int end = off;
        while (end < off + n && buf[end] != 0) {
            end++;
        }
        return new String(buf, off, end - off, StandardCharsets.US_ASCII);
    }

    private static int getUInt16(byte[] b, int off, boolean bigEndian) {
        int a = b[off] & 0xFF;
        int c = b[off + 1] & 0xFF;
        return bigEndian ? (a << 8) | c : (c << 8) | a;
    }

    private static long getUInt32(byte[] b, int off, boolean bigEndian) {
        long high = getUInt16(b, off, bigEndian);
        long low = getUInt16(b, off + 2, bigEndian);
        return bigEndian ? (high << 16) | low : (low << 16) | high;
    }

    private static long getUInt64(byte[] b, int off, boolean bigEndian) {
        long high = getUInt32(b, off, bigEndian);
        long low = getUInt32(b, off + 4, bigEndian);
        return bigEndian ? (high << 32) | low : (low << 32) | high;
    }
}
