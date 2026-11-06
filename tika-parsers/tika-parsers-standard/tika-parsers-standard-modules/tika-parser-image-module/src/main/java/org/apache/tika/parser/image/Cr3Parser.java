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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import com.drew.imaging.tiff.TiffProcessingException;
import com.drew.imaging.tiff.TiffReader;
import com.drew.imaging.tiff.TiffStandard;
import com.drew.lang.ByteArrayReader;
import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifTiffHandler;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
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
import org.apache.tika.metadata.TIFF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.xmp.XmpExtractor;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Canon CR3 raw images. CR3 is built on the ISO base media file format,
 * like HEIF, and keeps what a still image needs in Canon {@code uuid} boxes:
 * <ul>
 *   <li>{@code 85c0b687-820f-11e0-8111-f4ce462b6a48} in {@code moov} holds the Exif
 *   data as four TIFF blocks, {@code CMT1} (IFD0), {@code CMT2} (Exif IFD),
 *   {@code CMT3} (Canon maker note) and {@code CMT4} (GPS), and a {@code THMB}
 *   record with a 160x120 JPEG, emitted as
 *   {@link TikaCoreProperties.EmbeddedResourceType#INLINE}</li>
 *   <li>{@code eaf42b5e-1c98-4b88-b9fb-b7dc406e4d16} at the top level holds, after
 *   eight bytes, a {@code PRVW} record with the large JPEG preview (1620 pixels wide
 *   on current bodies), emitted as the
 *   {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}</li>
 *   <li>{@code be7acfcb-97a9-42e8-9c71-999491e3afac} at the top level holds an XMP packet,
 *   with the rating given in the camera or in Canon's software</li>
 * </ul>
 * The {@code THMB} and {@code PRVW} records carry width and height and the JPEG
 * length ahead of the JPEG, which starts 24 bytes into the record.
 */
@TikaComponent(name = "cr3-parser")
public class Cr3Parser implements Parser {

    private static final long serialVersionUID = 7130874255196421580L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.image("x-canon-cr3"));

    private static final byte[] METADATA_UUID = uuid("85c0b687820f11e08111f4ce462b6a48");
    private static final byte[] PREVIEW_UUID = uuid("eaf42b5e1c984b88b9fbb7dc406e4d16");
    private static final byte[] XMP_UUID = uuid("be7acfcb97a942e89c71999491e3afac");
    private static final int UUID_LENGTH = 16;
    /** The preview box's children start after eight bytes of its own. */
    private static final int PREVIEW_CHILDREN_OFFSET = 8;
    /** From the start of a PRVW or THMB box to its JPEG: size, type and sixteen header bytes. */
    private static final int JPEG_OFFSET = 24;
    /** An Exif block above this size is not read; real ones are a few KB to some tens of KB. */
    private static final int MAX_TIFF_BLOCK = 4 * 1024 * 1024;
    /** A preview above this size is not read; real ones are some hundred KB. */
    private static final int MAX_PREVIEW = 64 * 1024 * 1024;
    /** An XMP packet above this size is not read; Canon writes one of 64 KB, padding included. */
    private static final int MAX_XMP = 1024 * 1024;

    private final ImageMetadataConfig defaultConfig;

    public Cr3Parser() {
        this(new ImageMetadataConfig());
    }

    public Cr3Parser(ImageMetadataConfig config) {
        this.defaultConfig = config;
    }

    public Cr3Parser(JsonConfig jsonConfig) {
        this(ConfigDeserializer.buildConfig(jsonConfig, ImageMetadataConfig.class));
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /** A JPEG record: where its JPEG starts, how long it is, and the size it declares. */
    private record Jpeg(long offset, int length, int width, int height) {
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        Path path = tis.getPath();
        com.drew.metadata.Metadata exif = new com.drew.metadata.Metadata();
        Jpeg thumbnail = null;
        Jpeg preview = null;
        byte[] xmp = null;
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long length = channel.size();
            long offset = 0;
            while (offset + 8 <= length) {
                Box box = Box.read(channel, offset, length);
                if (box == null) {
                    break;
                }
                if (box.is("moov")) {
                    thumbnail = readMoov(channel, box, exif);
                } else if (box.is("uuid") && box.hasUuid(channel, PREVIEW_UUID)) {
                    preview = readRecord(channel, box.payload + UUID_LENGTH + PREVIEW_CHILDREN_OFFSET,
                            box.end, "PRVW", 10, 16);
                } else if (box.is("uuid") && box.hasUuid(channel, XMP_UUID)) {
                    long xmpLength = box.end - box.payload - UUID_LENGTH;
                    if (xmpLength > 0 && xmpLength <= MAX_XMP) {
                        xmp = readBytes(channel, box.payload + UUID_LENGTH, (int) xmpLength);
                    }
                }
                offset = box.end;
            }

            if (xmp != null) {
                try {
                    new XmpExtractor().extract(xmp, metadata, context);
                } catch (SecurityException e) {
                    throw e;
                } catch (IOException | SAXException | TikaException | RuntimeException e) {
                    EmbeddedDocumentUtil.recordException(e, metadata, context);
                }
            }
            try {
                new ImageMetadataExtractor(metadata,
                        ImageMetadataConfig.resolve(context, "cr3-parser", defaultConfig))
                        .handle(exif);
            } catch (MetadataException e) {
                throw new TikaException("Can't read the CR3 Exif data", e);
            }

            XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
            xhtml.startDocument();
            if (thumbnail != null) {
                emit(channel, thumbnail, TikaCoreProperties.EmbeddedResourceType.INLINE, xhtml,
                        context);
            }
            if (preview != null) {
                emit(channel, preview, TikaCoreProperties.EmbeddedResourceType.THUMBNAIL, xhtml,
                        context);
            }
            xhtml.endDocument();
        }
    }

    /** Reads the Exif blocks of the Canon box in {@code moov} and returns its THMB record. */
    private static Jpeg readMoov(FileChannel channel, Box moov, com.drew.metadata.Metadata exif)
            throws IOException {
        long offset = moov.payload;
        while (offset + 8 <= moov.end) {
            Box box = Box.read(channel, offset, moov.end);
            if (box == null) {
                return null;
            }
            if (box.is("uuid") && box.hasUuid(channel, METADATA_UUID)) {
                Jpeg thumbnail = null;
                long child = box.payload + UUID_LENGTH;
                while (child + 8 <= box.end) {
                    Box record = Box.read(channel, child, box.end);
                    if (record == null) {
                        break;
                    }
                    switch (record.type) {
                        case "CMT1" -> readExif(channel, record, exif, ExifIFD0Directory.class);
                        case "CMT2" -> readExif(channel, record, exif, ExifSubIFDDirectory.class);
                        case "CMT3" -> readExif(channel, record, exif, CanonMakernoteDirectory.class);
                        case "CMT4" -> readExif(channel, record, exif, GpsDirectory.class);
                        case "THMB" -> thumbnail = readRecord(channel, record.offset, box.end,
                                "THMB", 8, 12);
                        default -> {
                        }
                    }
                    child = record.end;
                }
                return thumbnail;
            }
            offset = box.end;
        }
        return null;
    }

    private static void readExif(FileChannel channel, Box record, com.drew.metadata.Metadata exif,
                                 Class<? extends Directory> firstDirectory) throws IOException {
        long size = record.end - record.payload;
        if (size <= 8 || size > MAX_TIFF_BLOCK) {
            return;
        }
        byte[] tiff = readBytes(channel, record.payload, (int) size);
        try {
            new TiffReader().processTiff(new ByteArrayReader(tiff),
                    new StartingDirectoryHandler(exif, firstDirectory), 0);
        } catch (TiffProcessingException | IOException e) {
            //a block that cannot be read leaves the others; metadata-extractor records
            //what it found up to there
        }
    }

    /**
     * Finds the record of the given type from {@code start} on and returns where its
     * JPEG is; {@code sizeOffset} and {@code lengthOffset} count from the record's type.
     */
    private static Jpeg readRecord(FileChannel channel, long start, long end, String type,
                                   int sizeOffset, int lengthOffset) throws IOException {
        long offset = start;
        while (offset + 8 <= end) {
            Box box = Box.read(channel, offset, end);
            if (box == null) {
                return null;
            }
            if (box.is(type)) {
                if (box.end - box.offset < JPEG_OFFSET) {
                    return null;
                }
                ByteBuffer header = ByteBuffer.wrap(readBytes(channel, box.offset + 4, JPEG_OFFSET - 4));
                long jpegLength = header.getInt(lengthOffset) & 0xFFFFFFFFL;
                long jpeg = box.offset + JPEG_OFFSET;
                if (jpegLength < 2 || jpegLength > box.end - jpeg || jpegLength > MAX_PREVIEW) {
                    return null;
                }
                return new Jpeg(jpeg, (int) jpegLength, header.getShort(sizeOffset) & 0xFFFF,
                        header.getShort(sizeOffset + 2) & 0xFFFF);
            }
            offset = box.end;
        }
        return null;
    }

    private static void emit(FileChannel channel, Jpeg jpeg,
                             TikaCoreProperties.EmbeddedResourceType type,
                             XHTMLContentHandler xhtml, ParseContext context)
            throws IOException, SAXException {
        byte[] bytes = readBytes(channel, jpeg.offset(), jpeg.length());
        if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) {
            return;
        }
        Metadata metadata = Metadata.newInstance(context);
        metadata.set(HttpHeaders.CONTENT_TYPE, "image/jpeg");
        metadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, type.name());
        EmbeddedDocumentUtil.setGeneratedResourceName(metadata,
                type == TikaCoreProperties.EmbeddedResourceType.THUMBNAIL
                        ? EmbeddedDocumentUtil.EmbeddedResourcePrefix.THUMBNAIL
                        : EmbeddedDocumentUtil.EmbeddedResourcePrefix.IMAGE, 0, "image/jpeg");
        metadata.set(TIFF.IMAGE_WIDTH, jpeg.width());
        metadata.set(TIFF.IMAGE_LENGTH, jpeg.height());
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        if (!extractor.shouldParseEmbedded(metadata, context)) {
            return;
        }
        try (TikaInputStream image = TikaInputStream.get(bytes)) {
            extractor.parseEmbedded(image, xhtml, metadata, context, true);
        }
    }

    private static byte[] readBytes(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, offset + buffer.position()) < 0) {
                throw new IOException("unexpected end of the CR3 file");
            }
        }
        return buffer.array();
    }

    private static byte[] uuid(String hex) {
        byte[] bytes = new byte[UUID_LENGTH];
        for (int i = 0; i < UUID_LENGTH; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    /** An ISO base media box: where it starts, where its payload starts and where it ends. */
    private static final class Box {
        final long offset;
        final long payload;
        final long end;
        final String type;

        private Box(long offset, long payload, long end, String type) {
            this.offset = offset;
            this.payload = payload;
            this.end = end;
            this.type = type;
        }

        /** The box at {@code offset}, or null when its header does not fit before {@code limit}. */
        static Box read(FileChannel channel, long offset, long limit) throws IOException {
            if (offset + 8 > limit) {
                return null;
            }
            ByteBuffer header = ByteBuffer.wrap(readBytes(channel, offset, 8));
            long size = header.getInt(0) & 0xFFFFFFFFL;
            String type = new String(header.array(), 4, 4, StandardCharsets.ISO_8859_1);
            long payload = offset + 8;
            if (size == 1) {
                if (offset + 16 > limit) {
                    return null;
                }
                size = ByteBuffer.wrap(readBytes(channel, offset + 8, 8)).getLong();
                payload = offset + 16;
            } else if (size == 0) {
                size = limit - offset;
            }
            if (size < payload - offset || size > limit - offset) {
                return null;
            }
            return new Box(offset, payload, offset + size, type);
        }

        boolean is(String boxType) {
            return type.equals(boxType);
        }

        boolean hasUuid(FileChannel channel, byte[] uuid) throws IOException {
            return end - payload >= UUID_LENGTH
                    && Arrays.equals(readBytes(channel, payload, UUID_LENGTH), uuid);
        }
    }

    /** Starts a TIFF block in the given directory instead of IFD0. */
    private static final class StartingDirectoryHandler extends ExifTiffHandler {
        private final Class<? extends Directory> firstDirectory;

        StartingDirectoryHandler(com.drew.metadata.Metadata metadata,
                                 Class<? extends Directory> firstDirectory) {
            super(metadata, null, 0);
            this.firstDirectory = firstDirectory;
        }

        @Override
        public TiffStandard processTiffMarker(int marker) {
            pushDirectory(firstDirectory);
            return TiffStandard.TIFF;
        }
    }
}
