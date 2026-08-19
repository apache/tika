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

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for TIFF-based camera raw images: Nikon NEF/NRW, Sony ARW/SRF/SR2,
 * Pentax PEF/PTX, Adobe DNG and Canon CR2.
 * <p>
 * These formats are TIFF containers: metadata extraction is inherited from
 * {@link TiffParser}. In addition, this parser extracts the camera-generated
 * JPEG preview images embedded in the raw file and hands them to the
 * {@link EmbeddedDocumentExtractor}. Previews are referenced from the IFD
 * chain or from SubIFDs, either via the JPEGInterchangeFormat/
 * JPEGInterchangeFormatLength tags or as a single JPEG-compressed strip
 * (DNG, CR2). Strips holding raw sensor data are also JPEG-encoded in some
 * formats (lossless JPEG in CR2 and DNG), so strip candidates are only
 * accepted for displayable images: PhotometricInterpretation RGB or YCbCr,
 * or 8 bits per sample when PhotometricInterpretation is absent (CR2).
 * Both classic TIFF and BigTIFF containers (allowed for DNG since spec
 * version 1.7) are supported for preview extraction; for BigTIFF, EXIF
 * metadata extraction is skipped until metadata-extractor supports it.
 */
@TikaComponent
public class RawTiffParser extends TiffParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 5385105345533384662L;

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    MediaType.image("x-raw-nikon"),
                    MediaType.image("x-raw-sony"),
                    MediaType.image("x-raw-pentax"),
                    MediaType.image("x-raw-adobe"),
                    MediaType.image("x-canon-cr2"))));

    private static final String JPEG_MIME = "image/jpeg";

    private static final int TAG_BITS_PER_SAMPLE = 0x0102;
    private static final int TAG_COMPRESSION = 0x0103;
    private static final int TAG_PHOTOMETRIC_INTERPRETATION = 0x0106;
    private static final int TAG_STRIP_OFFSETS = 0x0111;
    private static final int TAG_STRIP_BYTE_COUNTS = 0x0117;
    private static final int TAG_SUB_IFDS = 0x014A;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;

    private static final int COMPRESSION_OLD_JPEG = 6;
    private static final int COMPRESSION_JPEG = 7;
    private static final int PHOTOMETRIC_RGB = 2;
    private static final int PHOTOMETRIC_YCBCR = 6;

    private static final int MAX_IFDS = 32;
    private static final int MAX_ENTRIES_PER_IFD = 1024;
    // at most MAX_IFDS are ever processed; cap the pending queue so a crafted
    // file packed with SubIFD pointers cannot grow it without bound
    private static final int MAX_PENDING_IFDS = 1024;
    //previews are camera-generated JPEGs, tens of MB is already generous
    private static final long DEFAULT_MAX_PREVIEW_LENGTH_BYTES = 100 * 1024 * 1024;

    private final RawTiffParserConfig defaultConfig;

    public RawTiffParser() {
        this(new RawTiffParserConfig());
    }

    public RawTiffParser(RawTiffParserConfig config) {
        this.defaultConfig = config;
    }

    public RawTiffParser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, RawTiffParserConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        if (isBigTiff(tis)) {
            //metadata-extractor cannot read BigTIFF containers yet:
            //scan for XMP, but skip EXIF metadata extraction
            tis.getFile();
            ImageXmp.scanAndExtract(tis, metadata, context);
        } else {
            extractMetadata(tis, handler, metadata, context);
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        if (defaultConfig.isExtractPreviews()) {
            extractPreviews(tis, xhtml, metadata, context);
        }
        xhtml.endDocument();
    }

    private void extractPreviews(TikaInputStream tis, XHTMLContentHandler xhtml, Metadata metadata,
                                 ParseContext context) throws IOException, SAXException {
        List<Preview> previews;
        try (RandomAccessFile raf = new RandomAccessFile(tis.getFile(), "r")) {
            previews = locateJpegPreviews(raf);
        } catch (TiffStructureException | IOException e) {
            //a file we cannot walk for previews should not fail the parse;
            //the TIFF metadata has already been extracted at this point
            EmbeddedDocumentUtil.recordException(e, metadata);
            return;
        }
        if (previews.isEmpty()) {
            return;
        }
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        int count = 0;
        for (Preview preview : previews) {
            Metadata previewMetadata = Metadata.newInstance(context);
            previewMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
            previewMetadata.set(HttpHeaders.CONTENT_TYPE, JPEG_MIME);
            EmbeddedDocumentUtil.setGeneratedResourceName(previewMetadata,
                    EmbeddedDocumentUtil.EmbeddedResourcePrefix.THUMBNAIL, count, JPEG_MIME);
            count++;
            if (!extractor.shouldParseEmbedded(previewMetadata, context)) {
                continue;
            }
            //stream the preview region instead of loading it onto the heap
            try (InputStream fileStream = Files.newInputStream(tis.getPath())) {
                IOUtils.skipFully(fileStream, preview.offset());
                BoundedInputStream bounded = BoundedInputStream.builder()
                        .setInputStream(fileStream)
                        .setMaxCount(preview.length())
                        .get();
                try (TikaInputStream previewStream = TikaInputStream.get(bounded)) {
                    extractor.parseEmbedded(previewStream, xhtml, previewMetadata, context, true);
                }
            }
        }
    }

    /**
     * Walks the TIFF IFD chain and any SubIFDs (traversal bounded by
     * {@link #MAX_IFDS}) and returns the embedded JPEG previews.
     */
    private List<Preview> locateJpegPreviews(RandomAccessFile raf)
            throws IOException, TiffStructureException {
        long fileLength = raf.length();
        if (fileLength < 8) {
            throw new TiffStructureException("file too short for a TIFF header");
        }
        raf.seek(0);
        int b0 = raf.read();
        int b1 = raf.read();
        boolean bigEndian;
        if (b0 == 'M' && b1 == 'M') {
            bigEndian = true;
        } else if (b0 == 'I' && b1 == 'I') {
            bigEndian = false;
        } else {
            throw new TiffStructureException("not a TIFF byte order marker");
        }
        int magic = readUInt16(raf, bigEndian);
        boolean bigTiff;
        if (magic == 42) {
            bigTiff = false;
        } else if (magic == 43) {
            //BigTIFF: 8-byte offset size, then a constant 0
            bigTiff = true;
            if (readUInt16(raf, bigEndian) != 8 || readUInt16(raf, bigEndian) != 0) {
                throw new TiffStructureException("unsupported BigTIFF header");
            }
        } else {
            throw new TiffStructureException("bad TIFF magic number");
        }
        int countSize = bigTiff ? 8 : 2;
        int entrySize = bigTiff ? 20 : 12;
        int offsetSize = bigTiff ? 8 : 4;

        List<Preview> previews = new ArrayList<>();
        //distinct IFDs can reference the same JPEG region; extract each region once
        Set<Long> previewOffsets = new HashSet<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>();
        toVisit.add(readOffset(raf, bigEndian, bigTiff));

        while (!toVisit.isEmpty() && visited.size() < MAX_IFDS) {
            long ifdOffset = toVisit.poll();
            if (ifdOffset <= 0 || !visited.add(ifdOffset)) {
                continue;
            }
            if (ifdOffset + countSize > fileLength) {
                continue;
            }
            raf.seek(ifdOffset);
            long numEntries = bigTiff ? readUInt64(raf, bigEndian) : readUInt16(raf, bigEndian);
            if (numEntries < 0 || numEntries > MAX_ENTRIES_PER_IFD ||
                    ifdOffset + countSize + numEntries * entrySize + offsetSize > fileLength) {
                continue;
            }
            long jpegOffset = -1;
            long jpegLength = -1;
            long compression = -1;
            long photometric = -1;
            long[] bitsPerSample = new long[0];
            long[] stripOffsets = new long[0];
            long[] stripByteCounts = new long[0];
            for (int i = 0; i < numEntries; i++) {
                raf.seek(ifdOffset + countSize + (long) i * entrySize);
                int tag = readUInt16(raf, bigEndian);
                int type = readUInt16(raf, bigEndian);
                long valueCount = bigTiff ? readUInt64(raf, bigEndian) : readUInt32(raf, bigEndian);
                if (tag == TAG_SUB_IFDS) {
                    for (long subIfdOffset :
                            readLongValues(raf, bigEndian, bigTiff, type, valueCount)) {
                        enqueue(toVisit, subIfdOffset);
                    }
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                    jpegOffset = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                    jpegLength = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_COMPRESSION && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                    compression = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_PHOTOMETRIC_INTERPRETATION && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                    photometric = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_BITS_PER_SAMPLE) {
                    bitsPerSample = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                } else if (tag == TAG_STRIP_OFFSETS) {
                    stripOffsets = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                } else if (tag == TAG_STRIP_BYTE_COUNTS) {
                    stripByteCounts = readLongValues(raf, bigEndian, bigTiff, type, valueCount);
                }
            }
            raf.seek(ifdOffset + countSize + numEntries * entrySize);
            enqueue(toVisit, readOffset(raf, bigEndian, bigTiff));

            if (jpegOffset < 0 && isDisplayableJpegStrip(compression, photometric, bitsPerSample,
                    stripOffsets, stripByteCounts)) {
                jpegOffset = stripOffsets[0];
                jpegLength = stripByteCounts[0];
            }
            if (jpegOffset > 0 && jpegLength > 4 &&
                    jpegLength <= defaultConfig.getMaxPreviewLengthBytes() &&
                    jpegOffset <= fileLength - jpegLength) {
                raf.seek(jpegOffset);
                //require the JPEG SOI marker
                if (raf.read() == 0xFF && raf.read() == 0xD8 && previewOffsets.add(jpegOffset)) {
                    previews.add(new Preview(jpegOffset, jpegLength));
                }
            }
        }
        return previews;
    }

    /**
     * Decides whether an IFD without JPEGInterchangeFormat holds a JPEG
     * preview as a single strip (DNG, CR2). Raw sensor data can also be
     * JPEG-encoded (lossless JPEG), so the image must be displayable:
     * PhotometricInterpretation RGB or YCbCr, or 8 bits per sample when
     * PhotometricInterpretation is absent (CR2's preview IFD).
     */
    private boolean isDisplayableJpegStrip(long compression, long photometric,
                                           long[] bitsPerSample, long[] stripOffsets,
                                           long[] stripByteCounts) {
        if (compression != COMPRESSION_OLD_JPEG && compression != COMPRESSION_JPEG) {
            return false;
        }
        if (stripOffsets.length != 1 || stripByteCounts.length != 1) {
            return false;
        }
        if (photometric == PHOTOMETRIC_RGB || photometric == PHOTOMETRIC_YCBCR) {
            return true;
        }
        if (photometric != -1) {
            return false;
        }
        if (bitsPerSample.length == 0) {
            return false;
        }
        for (long bits : bitsPerSample) {
            if (bits != 8) {
                return false;
            }
        }
        return true;
    }

    private boolean isBigTiff(TikaInputStream tis) throws IOException {
        tis.mark(4);
        try {
            byte[] header = new byte[4];
            if (IOUtils.read(tis, header) < 4) {
                return false;
            }
            if (header[0] == 'I' && header[1] == 'I') {
                return header[2] == 0x2B && header[3] == 0;
            }
            if (header[0] == 'M' && header[1] == 'M') {
                return header[2] == 0 && header[3] == 0x2B;
            }
            return false;
        } finally {
            tis.reset();
        }
    }

    private static void enqueue(Deque<Long> toVisit, long offset) {
        if (toVisit.size() < MAX_PENDING_IFDS) {
            toVisit.add(offset);
        }
    }

    /**
     * Reads the values of an integer-typed entry (SHORT, LONG, IFD and, for
     * BigTIFF, LONG8/IFD8) whose value field starts at the current position;
     * returns an empty array for other types.
     */
    private long[] readLongValues(RandomAccessFile raf, boolean bigEndian, boolean bigTiff,
                                  int type, long valueCount) throws IOException {
        int typeSize;
        if (type == 3) {
            typeSize = 2;
        } else if (type == 4 || type == 13) {
            typeSize = 4;
        } else if (bigTiff && (type == 16 || type == 18)) {
            typeSize = 8;
        } else {
            return new long[0];
        }
        if (valueCount < 1 || valueCount > MAX_ENTRIES_PER_IFD) {
            return new long[0];
        }
        int count = (int) valueCount;
        if (typeSize * count > (bigTiff ? 8 : 4)) {
            long valueOffset = readOffset(raf, bigEndian, bigTiff);
            if (valueOffset < 0 || valueOffset > raf.length() - typeSize * (long) count) {
                return new long[0];
            }
            raf.seek(valueOffset);
        }
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            if (typeSize == 2) {
                values[i] = readUInt16(raf, bigEndian);
            } else if (typeSize == 4) {
                values[i] = readUInt32(raf, bigEndian);
            } else {
                values[i] = readUInt64(raf, bigEndian);
            }
        }
        return values;
    }

    private int readUInt16(RandomAccessFile raf, boolean bigEndian) throws IOException {
        int a = raf.read();
        int b = raf.read();
        if (a < 0 || b < 0) {
            throw new IOException("unexpected end of file");
        }
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private long readUInt32(RandomAccessFile raf, boolean bigEndian) throws IOException {
        long high = readUInt16(raf, bigEndian);
        long low = readUInt16(raf, bigEndian);
        return bigEndian ? (high << 16) | low : (low << 16) | high;
    }

    private long readUInt64(RandomAccessFile raf, boolean bigEndian) throws IOException {
        long high = readUInt32(raf, bigEndian);
        long low = readUInt32(raf, bigEndian);
        return bigEndian ? (high << 32) | low : (low << 32) | high;
    }

    private long readOffset(RandomAccessFile raf, boolean bigEndian, boolean bigTiff)
            throws IOException {
        return bigTiff ? readUInt64(raf, bigEndian) : readUInt32(raf, bigEndian);
    }

    private record Preview(long offset, long length) {
    }

    private static class TiffStructureException extends TikaException {
        TiffStructureException(String msg) {
            super(msg);
        }
    }

    /**
     * Configuration class for RawTiffParser.
     */
    public static class RawTiffParserConfig implements Serializable {

        private static final long serialVersionUID = 1990316744955315312L;

        private boolean extractPreviews = true;
        private long maxPreviewLengthBytes = DEFAULT_MAX_PREVIEW_LENGTH_BYTES;

        public boolean isExtractPreviews() {
            return extractPreviews;
        }

        public void setExtractPreviews(boolean extractPreviews) {
            this.extractPreviews = extractPreviews;
        }

        public long getMaxPreviewLengthBytes() {
            return maxPreviewLengthBytes;
        }

        public void setMaxPreviewLengthBytes(long maxPreviewLengthBytes) {
            this.maxPreviewLengthBytes = maxPreviewLengthBytes;
        }
    }
}
