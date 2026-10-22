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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.SequenceInputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.ConfigDeserializer;
import org.apache.tika.config.JsonConfig;
import org.apache.tika.config.OperatorOnly;
import org.apache.tika.config.ParseContextConfig;
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
 * Pentax PEF/PTX, Samsung SRW, Epson ERF, Kodak KDC, Leaf MOS, Adobe DNG,
 * Canon CR2, Olympus ORF and Panasonic RW2.
 * <p>
 * These formats are TIFF containers: metadata extraction is inherited from
 * {@link TiffParser}. In addition, this parser extracts the camera-generated
 * JPEG preview images embedded in the raw file and hands them to the
 * {@link EmbeddedDocumentExtractor}. Previews are referenced from the IFD
 * chain or from SubIFDs, either via the JPEGInterchangeFormat/
 * JPEGInterchangeFormatLength tags or as a single JPEG-compressed strip
 * (DNG, CR2). Four formats keep theirs elsewhere. A Panasonic RW2 uses the
 * private JpgFromRaw tag. An Olympus ORF uses its MakerNote, whose own IFD
 * holds the camera thumbnail and the CameraSettings sub-IFD that points at
 * the preview. An Epson ERF uses its MakerNote too, under a tag of its own,
 * at an offset counted from the start of the file and behind a byte the
 * camera writes over the JPEG's first one, which is put back on the way out.
 * A Leaf MOS uses a vendor block of named sections, one of which holds the
 * JPEG. ORF and RW2 carry a magic number of their own; ERF, KDC and MOS are
 * plain TIFFs, and a KDC names its preview the way a TIFF does.
 * <p>
 * Strips holding raw sensor data are also JPEG-encoded in some
 * formats (lossless JPEG in CR2 and DNG), so strip candidates are only
 * accepted for displayable images: PhotometricInterpretation RGB or YCbCr,
 * or 8 bits per sample when PhotometricInterpretation is absent (CR2).
 * Both classic TIFF and BigTIFF containers (allowed for DNG since spec
 * version 1.7) are supported.
 * <p>
 * Of the previews that are extracted (see the length limits in
 * {@link RawTiffParserConfig}), the largest by JPEG byte length is marked
 * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}, any smaller
 * ones are {@link TikaCoreProperties.EmbeddedResourceType#INLINE} images
 * (TIKA-4851).
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
                    MediaType.image("x-raw-samsung"),
                    MediaType.image("x-raw-epson"),
                    MediaType.image("x-raw-kodak"),
                    MediaType.image("x-raw-leaf"),
                    MediaType.image("x-raw-adobe"),
                    MediaType.image("x-raw-olympus"),
                    MediaType.image("x-raw-panasonic"),
                    MediaType.image("x-canon-cr2"))));

    private static final String JPEG_MIME = "image/jpeg";
    private static final byte[] JPEG_SOI_FIRST_BYTE = {(byte) 0xFF};

    private static final int TAG_BITS_PER_SAMPLE = 0x0102;
    private static final int TAG_COMPRESSION = 0x0103;
    private static final int TAG_PHOTOMETRIC_INTERPRETATION = 0x0106;
    private static final int TAG_STRIP_OFFSETS = 0x0111;
    private static final int TAG_STRIP_BYTE_COUNTS = 0x0117;
    private static final int TAG_SUB_IFDS = 0x014A;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;
    private static final int TAG_EXIF_IFD = 0x8769;
    private static final int TAG_MAKER_NOTE = 0x927C;
    //Panasonic keeps the whole preview JPEG in a private tag of IFD0
    private static final int TAG_PANASONIC_JPG_FROM_RAW = 0x002E;
    //inside an Olympus MakerNote: the camera thumbnail, and the sub-IFD
    //that points at the preview
    private static final int TAG_OLYMPUS_THUMBNAIL = 0x0100;
    private static final int TAG_OLYMPUS_CAMERA_SETTINGS = 0x2020;
    private static final int TAG_OLYMPUS_PREVIEW_START = 0x0101;
    private static final int TAG_OLYMPUS_PREVIEW_LENGTH = 0x0102;
    //ExifTool knows it as PreviewImage, in ERFs and in JPEGs from some Epsons
    private static final int TAG_EPSON_PREVIEW = 0x0280;
    //Leaf keeps its own data in one tag, a chain of named blocks
    private static final int TAG_LEAF_DATA = 0x8606;
    private static final byte[] LEAF_BLOCK_MAGIC =
            "PKTS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LEAF_PREVIEW_NAME =
            "JPEG_preview_data".getBytes(StandardCharsets.US_ASCII);
    //magic and version, then a padded name, then the payload's length
    private static final int LEAF_BLOCK_NAME_OFFSET = 8;
    private static final int LEAF_BLOCK_NAME_LENGTH = 40;
    private static final int LEAF_BLOCK_HEADER_LENGTH =
            LEAF_BLOCK_NAME_OFFSET + LEAF_BLOCK_NAME_LENGTH + 4;
    //a file holds a dozen of them; a crafted one must not be walked forever
    private static final int MAX_LEAF_BLOCKS = 64;

    private static final int MAGIC_TIFF = 42;
    private static final int MAGIC_BIG_TIFF = 43;
    //tika-mimetypes.xml matches the same signatures: "IIRO" and "MMOR" read
    //as 0x4F52, "IIRS" as 0x5352, and an RW2 starts with "IIU\0"
    private static final int MAGIC_OLYMPUS_RO = 0x4F52;
    private static final int MAGIC_OLYMPUS_RS = 0x5352;
    private static final int MAGIC_PANASONIC = 0x0055;

    private static final byte[] OLYMPUS_MAKER_NOTE_NAME =
            "OLYMPUS\u0000".getBytes(StandardCharsets.US_ASCII);
    //OM Digital Solutions bodies (OM-1, OM-3, OM-5, TG-7) write a longer name
    //and the same layout behind it, what ExifTool calls MakerNoteOlympus3
    private static final byte[] OM_SYSTEM_MAKER_NOTE_NAME =
            "OM SYSTEM\u0000\u0000\u0000".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EPSON_MAKER_NOTE_NAME =
            "EPSON\u0000".getBytes(StandardCharsets.US_ASCII);
    //the name and two bytes of version, then the IFD, whose offsets count
    //from the start of the file and not from the MakerNote
    private static final int EPSON_MAKER_NOTE_HEADER_LENGTH = 8;
    //the name, the MakerNote's own byte order marker and its version, then
    //its IFD starts
    private static final int OLYMPUS_MAKER_NOTE_HEADER_LENGTH = 12;
    private static final int OM_SYSTEM_MAKER_NOTE_HEADER_LENGTH = 16;

    private static final int COMPRESSION_OLD_JPEG = 6;
    private static final int COMPRESSION_JPEG = 7;
    private static final int PHOTOMETRIC_RGB = 2;
    private static final int PHOTOMETRIC_YCBCR = 6;

    private static final int MAX_IFDS = 32;
    private static final int CLASSIC_ENTRY_SIZE = 12;
    private static final int CLASSIC_INLINE_SIZE = 4;
    private static final int BIG_TIFF_INLINE_SIZE = 8;
    private static final int TYPE_BYTE = 1;
    private static final int TYPE_ASCII = 2;
    private static final int TYPE_UNDEFINED = 7;
    private static final int BIG_TIFF_ENTRY_SIZE = 20;
    //a crafted file must not send the reader chasing dozens of MakerNotes,
    //so the looking is what is counted, not the finding
    private static final int MAX_MAKER_NOTE_LOOKUPS = 4;
    private static final int MAX_ENTRIES_PER_IFD = 1024;
    // at most MAX_IFDS are ever processed; cap the pending queue so a crafted
    // file packed with SubIFD pointers cannot grow it without bound
    private static final int MAX_PENDING_IFDS = 1024;
    //previews are camera-generated JPEGs, tens of MB is already generous
    private static final long DEFAULT_MAX_PREVIEW_LENGTH_BYTES = 100 * 1024 * 1024;
    //a real raw file holds a handful of small previews; bounding the aggregate
    //stops a crafted file from pointing many IFDs at overlapping large regions
    //to amplify extraction work well beyond the file's own size
    private static final long DEFAULT_MAX_TOTAL_PREVIEW_BYTES = 256L * 1024 * 1024;

    private final RawTiffParserConfig defaultConfig;

    public RawTiffParser() {
        this(new RawTiffParserConfig());
    }

    public RawTiffParser(RawTiffParserConfig config) {
        super(config);
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
        int magic = readMagic(tis);
        if (!isKnownMagic(magic)) {
            //only a file name sent it here (*.raw is shared with other
            //formats): nothing to read, as when no parser claimed the type
            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            xhtml.endDocument();
            return;
        }
        extractMetadata(tis, handler, metadata, context);
        RawTiffParserConfig config = getConfig(context);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        if (config.isExtractPreviews()) {
            extractPreviews(tis, xhtml, metadata, context, config);
        }
        xhtml.endDocument();
    }

    private RawTiffParserConfig getConfig(ParseContext context) throws TikaException, IOException {
        return ParseContextConfig.getConfig(context, "raw-tiff-parser",
                RawTiffParserConfig.class, defaultConfig);
    }

    @Override
    protected ImageMetadataConfig getImageMetadataConfig(ParseContext context)
            throws TikaException, IOException {
        ImageMetadataConfig shared = context.get(ImageMetadataConfig.class);
        return shared != null ? shared : getConfig(context);
    }

    private void extractPreviews(TikaInputStream tis, XHTMLContentHandler xhtml, Metadata metadata,
                                 ParseContext context, RawTiffParserConfig config)
            throws IOException, SAXException {
        List<Preview> previews;
        try (RandomAccessFile raf = new RandomAccessFile(tis.getFile(), "r")) {
            previews = locateJpegPreviews(raf, config);
        } catch (TiffStructureException | IOException e) {
            //a file we cannot walk for previews should not fail the parse;
            //the TIFF metadata has already been extracted at this point
            EmbeddedDocumentUtil.recordException(e, metadata, context);
            return;
        }
        if (previews.isEmpty()) {
            return;
        }
        //of the extracted previews, the largest by JPEG length is the file's
        //thumbnail; the smaller ones (the camera's own thumbnail, intermediate
        //previews) are renderings of the same image and are inline images.
        //The JPEG length is a reliable proxy for the dimensions here.
        previews.sort(Comparator.comparingLong(Preview::length).reversed());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        int count = 0;
        //reuse one channel across previews, positioning to each offset in turn
        try (FileChannel channel = FileChannel.open(tis.getPath())) {
            for (Preview preview : previews) {
                Metadata previewMetadata = Metadata.newInstance(context);
                previewMetadata.set(HttpHeaders.CONTENT_TYPE, JPEG_MIME);
                //the IFD gives the length, so nothing has to read the preview
                //to find out how long it is (TIKA-4873)
                previewMetadata.set(HttpHeaders.CONTENT_LENGTH,
                        Long.toString(preview.length()));
                if (count == 0) {
                    previewMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
                    EmbeddedDocumentUtil.setGeneratedResourceName(previewMetadata,
                            EmbeddedDocumentUtil.EmbeddedResourcePrefix.THUMBNAIL, 0, JPEG_MIME);
                } else {
                    previewMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                            TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
                    EmbeddedDocumentUtil.setGeneratedResourceName(previewMetadata,
                            EmbeddedDocumentUtil.EmbeddedResourcePrefix.IMAGE, count - 1,
                            JPEG_MIME);
                }
                count++;
                if (!extractor.shouldParseEmbedded(previewMetadata, context)) {
                    continue;
                }
                //stream the preview region instead of loading it onto the heap;
                //close-shield the shared channel so parseEmbedded cannot close it
                channel.position(preview.streamOffset());
                BoundedInputStream bounded = BoundedInputStream.builder()
                        .setInputStream(CloseShieldInputStream.wrap(Channels.newInputStream(channel)))
                        .setMaxCount(preview.streamLength())
                        .get();
                InputStream bytes = preview.soiPatched()
                        ? new SequenceInputStream(new ByteArrayInputStream(JPEG_SOI_FIRST_BYTE),
                        bounded)
                        : bounded;
                try (TikaInputStream previewStream = TikaInputStream.get(bytes)) {
                    extractor.parseEmbedded(previewStream, xhtml, previewMetadata, context, true);
                }
            }
        }
    }

    /**
     * Walks the TIFF IFD chain and any SubIFDs (traversal bounded by
     * {@link #MAX_IFDS}) and returns the embedded JPEG previews.
     */
    private List<Preview> locateJpegPreviews(RandomAccessFile raf, RawTiffParserConfig config)
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
        boolean bigTiff = false;
        //ORF and RW2 are classic TIFF layouts behind their own magic number,
        //and keep their previews in places only they use
        boolean olympus = magic == MAGIC_OLYMPUS_RO || magic == MAGIC_OLYMPUS_RS;
        boolean panasonic = magic == MAGIC_PANASONIC;
        if (magic == MAGIC_BIG_TIFF) {
            //BigTIFF: 8-byte offset size, then a constant 0
            bigTiff = true;
            if (readUInt16(raf, bigEndian) != 8 || readUInt16(raf, bigEndian) != 0) {
                throw new TiffStructureException("unsupported BigTIFF header");
            }
        } else if (magic != MAGIC_TIFF && !olympus && !panasonic) {
            throw new TiffStructureException("bad TIFF magic number");
        }
        int countSize = bigTiff ? 8 : 2;
        int entrySize = bigTiff ? BIG_TIFF_ENTRY_SIZE : CLASSIC_ENTRY_SIZE;
        int offsetSize = bigTiff ? 8 : 4;

        PreviewCollector collector = new PreviewCollector(config.getMaxTotalPreviewBytes());
        Set<Long> visited = new HashSet<>();
        Deque<Long> toVisit = new ArrayDeque<>();
        List<Long> makerNotes = new ArrayList<>();
        int makerNoteLookups = 0;
        long leafData = -1;
        long leafDataLength = -1;
        toVisit.add(readOffset(raf, bigEndian, bigTiff));

        while (!toVisit.isEmpty() && visited.size() < MAX_IFDS) {
            long ifdOffset = toVisit.poll();
            if (ifdOffset <= 0 || !visited.add(ifdOffset)) {
                continue;
            }
            // overflow-safe: ifdOffset is a 64-bit value from the file, so avoid
            // ifdOffset + countSize which could wrap past Long.MAX_VALUE
            if (ifdOffset > fileLength - countSize) {
                continue;
            }
            raf.seek(ifdOffset);
            long numEntries = bigTiff ? readUInt64(raf, bigEndian) : readUInt16(raf, bigEndian);
            if (numEntries < 0 || numEntries > MAX_ENTRIES_PER_IFD ||
                    ifdOffset + countSize + numEntries * entrySize + offsetSize > fileLength) {
                continue;
            }
            //read the whole entry table (plus the trailing follower pointer) in one go and
            //parse it from memory, so a crafted IFD cannot force a read syscall per byte
            byte[] table = new byte[(int) (numEntries * entrySize + offsetSize)];
            raf.seek(ifdOffset + countSize);
            raf.readFully(table);
            int inlineSize = bigTiff ? BIG_TIFF_INLINE_SIZE : CLASSIC_INLINE_SIZE;
            long jpegOffset = -1;
            long jpegLength = -1;
            long panasonicOffset = -1;
            long panasonicLength = -1;
            long compression = -1;
            long photometric = -1;
            long[] bitsPerSample = new long[0];
            long[] stripOffsets = new long[0];
            long[] stripByteCounts = new long[0];
            for (int i = 0; i < numEntries; i++) {
                int e = i * entrySize;
                int tag = entryTag(table, e, bigEndian);
                int type = entryType(table, e, bigEndian);
                long valueCount = bigTiff
                        ? getUInt64(table, e + 4, bigEndian) : getUInt32(table, e + 4, bigEndian);
                int valueField = e + 4 + inlineSize;
                if (tag == TAG_SUB_IFDS) {
                    for (long subIfdOffset :
                            readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount)) {
                        enqueue(toVisit, subIfdOffset);
                    }
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT && valueCount == 1) {
                    long[] v = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                    jpegOffset = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && valueCount == 1) {
                    long[] v = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                    jpegLength = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_COMPRESSION && valueCount == 1) {
                    long[] v = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                    compression = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_PHOTOMETRIC_INTERPRETATION && valueCount == 1) {
                    long[] v = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                    photometric = v.length == 1 ? v[0] : -1;
                } else if (tag == TAG_BITS_PER_SAMPLE) {
                    bitsPerSample = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                } else if (tag == TAG_STRIP_OFFSETS) {
                    stripOffsets = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                } else if (tag == TAG_STRIP_BYTE_COUNTS) {
                    stripByteCounts = readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount);
                } else if (panasonic && tag == TAG_PANASONIC_JPG_FROM_RAW
                        && isBlob(type, valueCount, inlineSize)) {
                    panasonicOffset = getUInt32(table, valueField, bigEndian);
                    panasonicLength = valueCount;
                } else if (tag == TAG_LEAF_DATA && leafData < 0 && valueCount > inlineSize
                        && (type == TYPE_ASCII || isBlob(type, valueCount, inlineSize))) {
                    //Leaf types the block as ASCII, which it is not
                    leafData = getUInt32(table, valueField, bigEndian);
                    leafDataLength = valueCount;
                } else if (tag == TAG_EXIF_IFD && valueCount == 1 && !bigTiff
                        && makerNoteLookups < MAX_MAKER_NOTE_LOOKUPS) {
                    for (long exifIfd :
                            readLongValues(raf, table, valueField, bigEndian, bigTiff, type, valueCount)) {
                        makerNoteLookups++;
                        long makerNote = readMakerNoteOffset(raf, fileLength, exifIfd, bigEndian);
                        if (makerNote > 0) {
                            makerNotes.add(makerNote);
                        }
                    }
                }
            }
            int followerField = (int) (numEntries * entrySize);
            enqueue(toVisit, bigTiff
                    ? getUInt64(table, followerField, bigEndian) : getUInt32(table, followerField, bigEndian));

            if (panasonicOffset > 0) {
                //an RW2 keeps its preview here and writes placeholders into the
                //strip tags, so its own tag answers before either of them
                jpegOffset = panasonicOffset;
                jpegLength = panasonicLength;
            }
            if (jpegOffset < 0 && isDisplayableJpegStrip(compression, photometric, bitsPerSample,
                    stripOffsets, stripByteCounts)) {
                jpegOffset = stripOffsets[0];
                jpegLength = stripByteCounts[0];
            }
            Preview candidate = new Preview(jpegOffset, jpegLength);
            if (isPreviewAt(raf, fileLength, candidate, config)) {
                collector.add(candidate);
            }
            if (collector.isFull()) {
                break;
            }
        }

        for (Preview candidate : readLeafPreviews(raf, fileLength, leafData, leafDataLength,
                bigEndian)) {
            if (isPreviewAt(raf, fileLength, candidate, config)) {
                collector.add(candidate);
            }
            if (collector.isFull()) {
                break;
            }
        }

        for (long makerNote : makerNotes) {
            if (collector.isFull()) {
                break;
            }
            for (Preview candidate : readMakerNotePreviews(raf, fileLength, makerNote, bigEndian)) {
                if (isPreviewAt(raf, fileLength, candidate, config)) {
                    collector.add(candidate);
                }
                if (collector.isFull()) {
                    break;
                }
            }
        }
        return collector.previews();
    }

    /**
     * Whether a preview really lies at the offset: inside the file, within the
     * per-preview limit, and starting with the JPEG SOI marker.
     */
    private boolean isPreviewAt(RandomAccessFile raf, long fileLength, Preview preview,
                                RawTiffParserConfig config) throws IOException {
        if (preview.offset() <= 0 || preview.length() <= 4
                || preview.length() > config.getMaxPreviewLengthBytes()
                || preview.offset() > fileLength - preview.length()) {
            return false;
        }
        raf.seek(preview.offset());
        int first = raf.read();
        int second = raf.read();

        return (preview.soiPatched() || first == 0xFF) && second == 0xD8;
    }

    /**
     * The previews found so far, and the budget they share.
     */
    private static final class PreviewCollector {
        private final List<Preview> previews = new ArrayList<>();
        //distinct IFDs can reference the same JPEG region; extract each region once
        private final Set<Long> offsets = new HashSet<>();
        private final long maxTotalBytes;
        private long totalBytes;
        private boolean full;

        PreviewCollector(long maxTotalBytes) {
            this.maxTotalBytes = maxTotalBytes;
        }

        /**
         * The aggregate limit bounds the extraction work: overlapping offsets
         * could otherwise sum to many times the file's own size, and reaching
         * it ends the walk.
         */
        void add(Preview preview) {
            if (!offsets.add(preview.offset())) {
                return;
            }
            if (preview.length() > maxTotalBytes - totalBytes) {
                full = true;
                return;
            }
            totalBytes += preview.length();
            previews.add(preview);
        }

        boolean isFull() {
            return full;
        }

        List<Preview> previews() {
            return previews;
        }
    }

    /**
     * Where the MakerNote of an Exif IFD begins, or -1 when it has none.
     */
    private long readMakerNoteOffset(RandomAccessFile raf, long fileLength, long exifIfd,
                                     boolean bigEndian) throws IOException {
        byte[] table = readIfdEntries(raf, fileLength, exifIfd, bigEndian);
        if (table == null) {
            return -1;
        }
        for (int e = 0; e + CLASSIC_ENTRY_SIZE <= table.length; e += CLASSIC_ENTRY_SIZE) {
            if (entryTag(table, e, bigEndian) == TAG_MAKER_NOTE
                    && isBlob(entryType(table, e, bigEndian), classicEntryCount(table, e, bigEndian),
                    CLASSIC_INLINE_SIZE)) {
                return classicEntryValue(table, e, bigEndian);
            }
        }
        return -1;
    }

    /**
     * The JPEG a Leaf MOS keeps in its data block: a chain of blocks, each a
     * magic and a version, a padded name and the length of what follows. The
     * one named JPEG_preview_data holds the preview, stored as it is.
     */
    private List<Preview> readLeafPreviews(RandomAccessFile raf, long fileLength, long block,
                                           long blockLength, boolean bigEndian)
            throws IOException {
        List<Preview> candidates = new ArrayList<>();
        if (block <= 0 || blockLength <= 0 || block > fileLength - blockLength) {
            return candidates;
        }
        long end = block + blockLength;
        long at = block;
        byte[] header = new byte[LEAF_BLOCK_HEADER_LENGTH];
        for (int n = 0; n < MAX_LEAF_BLOCKS && at <= end - LEAF_BLOCK_HEADER_LENGTH; n++) {
            raf.seek(at);
            raf.readFully(header);
            if (!startsWith(header, LEAF_BLOCK_MAGIC)) {
                break;
            }
            long length = getUInt32(header, LEAF_BLOCK_HEADER_LENGTH - 4, bigEndian);
            long payload = at + LEAF_BLOCK_HEADER_LENGTH;
            if (length > end - payload) {
                break;
            }
            if (Arrays.equals(header, LEAF_BLOCK_NAME_OFFSET,
                    LEAF_BLOCK_NAME_OFFSET + LEAF_PREVIEW_NAME.length,
                    LEAF_PREVIEW_NAME, 0, LEAF_PREVIEW_NAME.length)) {
                candidates.add(new Preview(payload, length));
            }
            at = payload + length;
        }

        return candidates;
    }

    /**
     * The previews a MakerNote points at, by the name it begins with.
     */
    private List<Preview> readMakerNotePreviews(RandomAccessFile raf, long fileLength,
                                                long makerNote, boolean bigEndian)
            throws IOException {
        if (makerNote <= 0 || makerNote > fileLength - EPSON_MAKER_NOTE_HEADER_LENGTH) {
            return new ArrayList<>();
        }
        //as much of a header as the file holds: the three names need 8, 12 and 16
        byte[] header = new byte[OM_SYSTEM_MAKER_NOTE_HEADER_LENGTH];
        raf.seek(makerNote);
        raf.readFully(header, 0, (int) Math.min(header.length, fileLength - makerNote));
        if (startsWith(header, OLYMPUS_MAKER_NOTE_NAME)) {
            return readOlympusMakerNotePreviews(raf, fileLength, makerNote, header,
                    OLYMPUS_MAKER_NOTE_NAME, OLYMPUS_MAKER_NOTE_HEADER_LENGTH);
        }
        if (startsWith(header, OM_SYSTEM_MAKER_NOTE_NAME)) {
            return readOlympusMakerNotePreviews(raf, fileLength, makerNote, header,
                    OM_SYSTEM_MAKER_NOTE_NAME, OM_SYSTEM_MAKER_NOTE_HEADER_LENGTH);
        }
        if (startsWith(header, EPSON_MAKER_NOTE_NAME)) {
            return readEpsonMakerNotePreviews(raf, fileLength, makerNote, bigEndian);
        }

        return new ArrayList<>();
    }

    private static boolean startsWith(byte[] header, byte[] name) {
        return Arrays.equals(header, 0, name.length, name, 0, name.length);
    }

    /**
     * The preview an Epson ERF keeps in its MakerNote (0x0280). Its offset
     * counts from the start of the file, and the camera writes a byte of its
     * own where the JPEG's first one belongs, so the preview is marked for
     * the SOI to be put back when its bytes are handed on.
     */
    private List<Preview> readEpsonMakerNotePreviews(RandomAccessFile raf, long fileLength,
                                                     long makerNote, boolean bigEndian)
            throws IOException {
        List<Preview> candidates = new ArrayList<>();
        byte[] table = readIfdEntries(raf, fileLength,
                makerNote + EPSON_MAKER_NOTE_HEADER_LENGTH, bigEndian);
        if (table == null) {
            return candidates;
        }
        for (int e = 0; e + CLASSIC_ENTRY_SIZE <= table.length; e += CLASSIC_ENTRY_SIZE) {
            long count = classicEntryCount(table, e, bigEndian);
            if (entryTag(table, e, bigEndian) == TAG_EPSON_PREVIEW
                    && isBlob(entryType(table, e, bigEndian), count, CLASSIC_INLINE_SIZE)) {
                candidates.add(new Preview(classicEntryValue(table, e, bigEndian), count, true));
            }
        }

        return candidates;
    }

    /**
     * The JPEGs an Olympus MakerNote points at: the camera's thumbnail
     * (0x0100) and the preview that the CameraSettings sub-IFD (0x2020)
     * names with PreviewImageStart and PreviewImageLength. Every offset
     * inside the MakerNote is relative to its first byte. The "OLYMPUS\0"
     * and "OM SYSTEM\0\0\0" headers are read, which differ in the name's
     * length only; the older "OLYMP\0" one counts from elsewhere and is left
     * to the next reader.
     */
    private List<Preview> readOlympusMakerNotePreviews(RandomAccessFile raf, long fileLength,
                                                       long makerNote, byte[] header,
                                                       byte[] name, int headerLength)
            throws IOException {
        List<Preview> candidates = new ArrayList<>();
        //the MakerNote carries its own byte order marker, which a big endian
        //file does not have to share
        boolean bigEndian;
        int order = name.length;
        if (header[order] == 'I' && header[order + 1] == 'I') {
            bigEndian = false;
        } else if (header[order] == 'M' && header[order + 1] == 'M') {
            bigEndian = true;
        } else {
            return candidates;
        }
        byte[] table = readIfdEntries(raf, fileLength, makerNote + headerLength, bigEndian);
        if (table == null) {
            return candidates;
        }
        long settings = -1;
        for (int e = 0; e + CLASSIC_ENTRY_SIZE <= table.length; e += CLASSIC_ENTRY_SIZE) {
            int tag = entryTag(table, e, bigEndian);
            long count = classicEntryCount(table, e, bigEndian);
            long value = classicEntryValue(table, e, bigEndian);
            if (tag == TAG_OLYMPUS_THUMBNAIL
                    && isBlob(entryType(table, e, bigEndian), count, CLASSIC_INLINE_SIZE)) {
                candidates.add(new Preview(makerNote + value, count));
            } else if (tag == TAG_OLYMPUS_CAMERA_SETTINGS && count == 1) {
                settings = makerNote + value;
            }
        }
        byte[] settingsTable = readIfdEntries(raf, fileLength, settings, bigEndian);
        if (settingsTable == null) {
            return candidates;
        }
        long start = -1;
        long length = -1;
        for (int e = 0; e + CLASSIC_ENTRY_SIZE <= settingsTable.length; e += CLASSIC_ENTRY_SIZE) {
            int tag = entryTag(settingsTable, e, bigEndian);
            if (classicEntryCount(settingsTable, e, bigEndian) != 1) {
                continue;
            }
            if (tag == TAG_OLYMPUS_PREVIEW_START) {
                start = classicEntryValue(settingsTable, e, bigEndian);
            } else if (tag == TAG_OLYMPUS_PREVIEW_LENGTH) {
                length = classicEntryValue(settingsTable, e, bigEndian);
            }
        }
        if (start >= 0 && length > 0) {
            candidates.add(new Preview(makerNote + start, length));
        }
        return candidates;
    }

    /**
     * The fields of an IFD entry. The tag and the type sit where they do in a
     * BigTIFF too; the other two are the classic layout's, as their names say.
     */
    private static int entryTag(byte[] entries, int e, boolean bigEndian) {
        return getUInt16(entries, e, bigEndian);
    }

    private static int entryType(byte[] entries, int e, boolean bigEndian) {
        return getUInt16(entries, e + 2, bigEndian);
    }

    private static long classicEntryCount(byte[] entries, int e, boolean bigEndian) {
        return getUInt32(entries, e + 4, bigEndian);
    }

    private static long classicEntryValue(byte[] entries, int e, boolean bigEndian) {
        return getUInt32(entries, e + 8, bigEndian);
    }

    /**
     * Whether the entry holds its bytes elsewhere, which is what a tag naming
     * an image is: BYTE or UNDEFINED, and too long for the value field.
     */
    private static boolean isBlob(int type, long valueCount, int inlineSize) {
        return (type == TYPE_BYTE || type == TYPE_UNDEFINED) && valueCount > inlineSize;
    }

    /**
     * The entries of a classic IFD at the offset, without the pointer that
     * follows them; null when the file holds no such table there.
     */
    private byte[] readIfdEntries(RandomAccessFile raf, long fileLength, long offset,
                                  boolean bigEndian) throws IOException {
        if (offset <= 0 || offset > fileLength - 2) {
            return null;
        }
        raf.seek(offset);
        int numEntries = readUInt16(raf, bigEndian);
        if (numEntries < 1 || numEntries > MAX_ENTRIES_PER_IFD
                || offset + 2 + (long) numEntries * CLASSIC_ENTRY_SIZE > fileLength) {
            return null;
        }
        byte[] table = new byte[numEntries * CLASSIC_ENTRY_SIZE];
        raf.readFully(table);
        return table;
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

    /**
     * The magic number after the byte order marker, or -1 when the stream
     * does not start with one.
     */
    private static int readMagic(TikaInputStream tis) throws IOException {
        tis.mark(4);
        try {
            byte[] header = new byte[4];
            if (IOUtils.read(tis, header) < 4) {
                return -1;
            }
            if (header[0] == 'I' && header[1] == 'I') {
                return getUInt16(header, 2, false);
            }
            if (header[0] == 'M' && header[1] == 'M') {
                return getUInt16(header, 2, true);
            }
            return -1;
        } finally {
            tis.reset();
        }
    }

    private static boolean isKnownMagic(int magic) {
        return magic == MAGIC_TIFF || magic == MAGIC_BIG_TIFF || magic == MAGIC_OLYMPUS_RO
                || magic == MAGIC_OLYMPUS_RS || magic == MAGIC_PANASONIC;
    }

    private static void enqueue(Deque<Long> toVisit, long offset) {
        if (toVisit.size() < MAX_PENDING_IFDS) {
            toVisit.add(offset);
        }
    }

    /**
     * Reads the values of an integer-typed entry (SHORT, LONG, IFD and, for
     * BigTIFF, LONG8/IFD8) from the in-memory entry table. Values that do not
     * fit the inline value field are read once from their offset into a buffer;
     * returns an empty array for other types.
     */
    private long[] readLongValues(RandomAccessFile raf, byte[] table, int valueField,
                                  boolean bigEndian, boolean bigTiff, int type, long valueCount)
            throws IOException {
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
        long totalBytes = (long) typeSize * count;
        byte[] src;
        int off;
        if (totalBytes <= (bigTiff ? 8 : 4)) {
            src = table;
            off = valueField;
        } else {
            long valueOffset = bigTiff
                    ? getUInt64(table, valueField, bigEndian) : getUInt32(table, valueField, bigEndian);
            if (valueOffset < 0 || valueOffset > raf.length() - totalBytes) {
                return new long[0];
            }
            src = new byte[(int) totalBytes];
            raf.seek(valueOffset);
            raf.readFully(src);
            off = 0;
        }
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            int p = off + i * typeSize;
            if (typeSize == 2) {
                values[i] = getUInt16(src, p, bigEndian);
            } else if (typeSize == 4) {
                values[i] = getUInt32(src, p, bigEndian);
            } else {
                values[i] = getUInt64(src, p, bigEndian);
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

    /**
     * A JPEG inside the file. A patched one begins with a byte the camera
     * wrote over the SOI, which is restored when it is handed on.
     */
    private record Preview(long offset, long length, boolean soiPatched) {
        Preview(long offset, long length) {
            this(offset, length, false);
        }

        long streamOffset() {
            return soiPatched ? offset + 1 : offset;
        }

        long streamLength() {
            return soiPatched ? length - 1 : length;
        }

    }

    private static class TiffStructureException extends TikaException {
        TiffStructureException(String msg) {
            super(msg);
        }
    }

    /**
     * Configuration class for RawTiffParser.
     */
    public static class RawTiffParserConfig extends ImageMetadataConfig {

        private static final long serialVersionUID = 1990316744955315312L;

        private boolean extractPreviews = true;
        private long maxPreviewLengthBytes = DEFAULT_MAX_PREVIEW_LENGTH_BYTES;
        private long maxTotalPreviewBytes = DEFAULT_MAX_TOTAL_PREVIEW_BYTES;

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

        public long getMaxTotalPreviewBytes() {
            return maxTotalPreviewBytes;
        }

        @OperatorOnly
        public void setMaxTotalPreviewBytes(long maxTotalPreviewBytes) {
            this.maxTotalPreviewBytes = maxTotalPreviewBytes;
        }
    }
}
