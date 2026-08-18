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
        extractMetadata(tis, handler, metadata, context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        if (defaultConfig.isExtractPreviews()) {
            extractPreviews(tis, xhtml, metadata, context);
        }
        xhtml.endDocument();
    }

    private void extractPreviews(TikaInputStream tis, XHTMLContentHandler xhtml, Metadata metadata,
                                 ParseContext context) throws IOException, SAXException {
        List<long[]> previews;
        try (RandomAccessFile raf = new RandomAccessFile(tis.getFile(), "r")) {
            previews = locateJpegPreviews(raf);
        } catch (TiffStructureException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
            return;
        }
        if (previews.isEmpty()) {
            return;
        }
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        int count = 0;
        for (long[] preview : previews) {
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
                IOUtils.skipFully(fileStream, preview[0]);
                BoundedInputStream bounded = BoundedInputStream.builder()
                        .setInputStream(fileStream)
                        .setMaxCount(preview[1])
                        .get();
                try (TikaInputStream previewStream = TikaInputStream.get(bounded)) {
                    extractor.parseEmbedded(previewStream, xhtml, previewMetadata, context, true);
                }
            }
        }
    }

    /**
     * Walks the TIFF IFD chain plus one level of SubIFDs and returns
     * {offset, length} pairs of embedded JPEG previews.
     */
    private List<long[]> locateJpegPreviews(RandomAccessFile raf)
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
        if (readUInt16(raf, bigEndian) != 42) {
            throw new TiffStructureException("bad TIFF magic number");
        }

        List<long[]> previews = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>();
        toVisit.add(readUInt32(raf, bigEndian));

        while (!toVisit.isEmpty() && visited.size() < MAX_IFDS) {
            long ifdOffset = toVisit.poll();
            if (ifdOffset == 0 || !visited.add(ifdOffset)) {
                continue;
            }
            if (ifdOffset + 2 > fileLength) {
                continue;
            }
            raf.seek(ifdOffset);
            int numEntries = readUInt16(raf, bigEndian);
            if (numEntries > MAX_ENTRIES_PER_IFD ||
                    ifdOffset + 2 + numEntries * 12L + 4 > fileLength) {
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
                raf.seek(ifdOffset + 2 + i * 12L);
                int tag = readUInt16(raf, bigEndian);
                int type = readUInt16(raf, bigEndian);
                long valueCount = readUInt32(raf, bigEndian);
                if (tag == TAG_SUB_IFDS) {
                    for (long subIfdOffset : readLongValues(raf, bigEndian, type, valueCount)) {
                        enqueue(toVisit, subIfdOffset);
                    }
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, type, valueCount);
                    jpegOffset = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, type, valueCount);
                    jpegLength = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_COMPRESSION && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, type, valueCount);
                    compression = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_PHOTOMETRIC_INTERPRETATION && valueCount == 1) {
                    long[] v = readLongValues(raf, bigEndian, type, valueCount);
                    photometric = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_BITS_PER_SAMPLE) {
                    bitsPerSample = readLongValues(raf, bigEndian, type, valueCount);
                } else if (tag == TAG_STRIP_OFFSETS) {
                    stripOffsets = readLongValues(raf, bigEndian, type, valueCount);
                } else if (tag == TAG_STRIP_BYTE_COUNTS) {
                    stripByteCounts = readLongValues(raf, bigEndian, type, valueCount);
                }
            }
            raf.seek(ifdOffset + 2 + numEntries * 12L);
            enqueue(toVisit, readUInt32(raf, bigEndian));

            if (jpegOffset < 0 && isDisplayableJpegStrip(compression, photometric, bitsPerSample,
                    stripOffsets, stripByteCounts)) {
                jpegOffset = stripOffsets[0];
                jpegLength = stripByteCounts[0];
            }
            if (jpegOffset > 0 && jpegLength > 4 &&
                    jpegLength <= defaultConfig.getMaxPreviewLengthBytes() &&
                    jpegOffset + jpegLength <= fileLength) {
                raf.seek(jpegOffset);
                //require the JPEG SOI marker
                if (raf.read() == 0xFF && raf.read() == 0xD8) {
                    previews.add(new long[]{jpegOffset, jpegLength});
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

    private static void enqueue(Deque<Long> toVisit, long offset) {
        if (toVisit.size() < MAX_PENDING_IFDS) {
            toVisit.add(offset);
        }
    }

    /**
     * Reads the values of a SHORT or LONG entry whose value field starts at
     * the current position; returns an empty array for other types.
     */
    private long[] readLongValues(RandomAccessFile raf, boolean bigEndian, int type,
                                  long valueCount) throws IOException {
        int typeSize;
        if (type == 3) {
            typeSize = 2;
        } else if (type == 4) {
            typeSize = 4;
        } else {
            return new long[0];
        }
        if (valueCount < 1 || valueCount > MAX_ENTRIES_PER_IFD) {
            return new long[0];
        }
        int count = (int) valueCount;
        if (typeSize * count > 4) {
            long valueOffset = readUInt32(raf, bigEndian);
            if (valueOffset + typeSize * (long) count > raf.length()) {
                return new long[0];
            }
            raf.seek(valueOffset);
        }
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            values[i] = typeSize == 2 ? readUInt16(raf, bigEndian) : readUInt32(raf, bigEndian);
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

    private static class TiffStructureException extends TikaException {
        TiffStructureException(String msg) {
            super(msg);
        }
    }

    /**
     * Configuration class for RawTiffParser.
     */
    public static class RawTiffParserConfig {
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
