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
import java.util.Locale;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.BoundedInputStream;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Icon;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Windows icon (ICO) and cursor (CUR) files. Reads the ICONDIR
 * and the header of every image to report the dimensions and colour depth of
 * the largest image, the list of all images and, for cursors, the hotspot.
 * The images themselves are not decoded.
 * <p>
 * The directory's own width, height and colour fields are unreliable (256 px
 * is stored as 0, many tools leave the bit count empty), so the values come
 * from each image's PNG IHDR or BITMAPINFOHEADER and the directory is only
 * the fallback. Colour depth is reported the TIFF way, bits per sample and
 * samples per pixel; the per-image list carries the total bits per pixel.
 * <p>
 * OS/2 bitmap arrays are detected as the same type. They pass through
 * without metadata.
 */
@TikaComponent
public class ICOParser implements Parser {

    private static final long serialVersionUID = 4212837190215395123L;

    static final MediaType ICO_TYPE = MediaType.image("vnd.microsoft.icon");
    static final MediaType ICO_ALIAS = MediaType.image("x-icon");
    static final MediaType CUR_TYPE = MediaType.image("x-win-bitmap");

    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(ICO_TYPE, ICO_ALIAS, CUR_TYPE);

    private static final int TYPE_ICON = 1;
    private static final int TYPE_CURSOR = 2;
    private static final int COUNT_OFFSET = 4;
    private static final int HEADER_SIZE = 6;
    private static final int ENTRY_SIZE = 16;
    private static final int BITMAP_INFO_HEADER_SIZE = 40;
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final int PNG_IHDR_SIZE = 8 + 8 + 13;
    // Icons are small; anything bigger is read only this far
    static final int MAX_FILE_SIZE = 16 * 1024 * 1024;
    // Far beyond any real icon; a header that claims more is not believed
    private static final int MAX_DIMENSION = 65535;

    private enum Encoding {
        PNG, BMP, UNKNOWN
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        byte[] file = IOUtils.toByteArray(new BoundedInputStream(MAX_FILE_SIZE, tis));
        if (isOs2BitmapArray(file)) {
            metadata.set(HttpHeaders.CONTENT_TYPE, ICO_TYPE.toString());
        } else {
            extractMetadata(file, metadata, context);
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    private static boolean isOs2BitmapArray(byte[] file) {
        return file.length >= 2 && file[0] == 'B' && file[1] == 'A';
    }

    private static void extractMetadata(byte[] file, Metadata metadata, ParseContext context)
            throws TikaException {
        if (file.length < COUNT_OFFSET || EndianUtils.getUShortLE(file, 0) != 0) {
            throw new TikaException("Not an ICO or CUR file");
        }
        int type = EndianUtils.getUShortLE(file, 2);
        if (type != TYPE_ICON && type != TYPE_CURSOR) {
            throw new TikaException("Not an ICO or CUR file: type " + type);
        }
        boolean cursor = type == TYPE_CURSOR;
        metadata.set(HttpHeaders.CONTENT_TYPE, (cursor ? CUR_TYPE : ICO_TYPE).toString());
        if (file.length < HEADER_SIZE) {
            warn("The header ends before the image count", metadata, context);
            return;
        }

        int count = EndianUtils.getUShortLE(file, COUNT_OFFSET);
        metadata.set(Icon.IMAGE_COUNT, count);
        if (count == 0) {
            warn("The directory lists no images", metadata, context);
            return;
        }
        Image largest = null;
        int unreadable = 0;
        for (int i = 0; i < count; i++) {
            int entryOffset = HEADER_SIZE + i * ENTRY_SIZE;
            if (entryOffset + ENTRY_SIZE > file.length) {
                unreadable += count - i;
                break;
            }
            Image image = Image.read(file, entryOffset, cursor);
            if (image == null) {
                unreadable++;
                continue;
            }
            if (image.encoding == Encoding.UNKNOWN) {
                // still listed with what the directory says, but worth a warning
                unreadable++;
            }
            metadata.add(Icon.IMAGES, image.describe());
            if (largest == null || image.outranks(largest)) {
                largest = image;
            }
        }
        if (largest != null) {
            metadata.set(TIFF.IMAGE_WIDTH, largest.width);
            metadata.set(TIFF.IMAGE_LENGTH, largest.height);
            if (largest.bitsPerSample > 0) {
                metadata.set(TIFF.BITS_PER_SAMPLE, Integer.toString(largest.bitsPerSample));
                metadata.set(TIFF.SAMPLES_PER_PIXEL, largest.samplesPerPixel);
            }
            if (cursor) {
                metadata.set(Icon.HOTSPOT_X, largest.hotspotX);
                metadata.set(Icon.HOTSPOT_Y, largest.hotspotY);
            }
        }
        if (unreadable > 0) {
            warn(unreadable + " of " + count + " images lie outside the file or have no" +
                    " readable header", metadata, context);
        }
    }

    private static void warn(String message, Metadata metadata, ParseContext context) {
        EmbeddedDocumentUtil.recordException(new TikaException(message), metadata, context);
    }

    private static boolean startsWithPngSignature(byte[] file, int offset) {
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (file[offset + i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int pngSamplesPerPixel(int colorType) {
        switch (colorType) {
            case 2: // truecolour
                return 3;
            case 4: // greyscale with alpha
                return 2;
            case 6: // truecolour with alpha
                return 4;
            default: // greyscale or palette
                return 1;
        }
    }

    private static final class Image {
        int width;
        int height;
        int bitsPerPixel;
        int bitsPerSample;
        int samplesPerPixel;
        int hotspotX;
        int hotspotY;
        Encoding encoding = Encoding.UNKNOWN;

        /**
         * Reads one ICONDIRENTRY and the header of the image it points to.
         * Without a header that has a usable size, the image keeps the
         * directory's values and an unknown encoding.
         *
         * @return the image, or null if its data lies outside the file
         */
        static Image read(byte[] file, int entryOffset, boolean cursor) {
            Image image = new Image();
            image.width = directorySize(file[entryOffset]);
            image.height = directorySize(file[entryOffset + 1]);
            if (cursor) {
                // a cursor's directory holds the hotspot where an icon's holds planes and bit count
                image.hotspotX = EndianUtils.getUShortLE(file, entryOffset + 4);
                image.hotspotY = EndianUtils.getUShortLE(file, entryOffset + 6);
            } else {
                image.setDepth(EndianUtils.getUShortLE(file, entryOffset + 6));
            }
            long size = EndianUtils.getUIntLE(file, entryOffset + 8);
            long offset = EndianUtils.getUIntLE(file, entryOffset + 12);
            if (offset < HEADER_SIZE || offset >= file.length) {
                return null;
            }
            int dataOffset = (int) offset;
            long available = Math.min(size, file.length - offset);
            if (available >= PNG_IHDR_SIZE && startsWithPngSignature(file, dataOffset)) {
                if (image.trySetSize(EndianUtils.getUIntBE(file, dataOffset + 16),
                        EndianUtils.getUIntBE(file, dataOffset + 20))) {
                    image.encoding = Encoding.PNG;
                    image.bitsPerSample = file[dataOffset + 24] & 0xff;
                    image.samplesPerPixel = pngSamplesPerPixel(file[dataOffset + 25] & 0xff);
                    image.bitsPerPixel = image.bitsPerSample * image.samplesPerPixel;
                }
            } else if (available >= BITMAP_INFO_HEADER_SIZE &&
                    EndianUtils.getUIntLE(file, dataOffset) == BITMAP_INFO_HEADER_SIZE) {
                // the height covers the XOR bitmap and the AND mask; a negative one means top-down
                long height = Math.abs((long) EndianUtils.getIntLE(file, dataOffset + 8)) / 2;
                if (image.trySetSize(EndianUtils.getIntLE(file, dataOffset + 4), height)) {
                    image.encoding = Encoding.BMP;
                    image.setDepth(EndianUtils.getUShortLE(file, dataOffset + 14));
                }
            }
            return image;
        }

        private static int directorySize(byte size) {
            return size == 0 ? 256 : size & 0xff;
        }

        /**
         * @return false, leaving the size as it was, unless both lie between 1 and
         *         {@code MAX_DIMENSION}
         */
        boolean trySetSize(long width, long height) {
            if (width <= 0 || width > MAX_DIMENSION || height <= 0 || height > MAX_DIMENSION) {
                return false;
            }
            this.width = (int) width;
            this.height = (int) height;
            return true;
        }

        /**
         * Splits a DIB colour depth into samples: 32 and 24 bit images are
         * 8 bits per channel, 16 bit ones 5, anything below is palette or mono.
         */
        void setDepth(int bitsPerPixel) {
            this.bitsPerPixel = bitsPerPixel;
            if (bitsPerPixel == 32 || bitsPerPixel == 24) {
                bitsPerSample = 8;
                samplesPerPixel = bitsPerPixel / 8;
            } else if (bitsPerPixel == 16) {
                bitsPerSample = 5;
                samplesPerPixel = 3;
            } else {
                bitsPerSample = bitsPerPixel;
                samplesPerPixel = bitsPerPixel > 0 ? 1 : 0;
            }
        }

        /**
         * Larger area wins, then the higher colour depth, like Windows' own choice.
         */
        boolean outranks(Image other) {
            long area = (long) width * height;
            long otherArea = (long) other.width * other.height;
            return area > otherArea || area == otherArea && bitsPerPixel > other.bitsPerPixel;
        }

        String describe() {
            String depth = bitsPerPixel > 0 ? "@" + bitsPerPixel + "bpp" : "";
            return width + "x" + height + depth + " " + encoding.name().toLowerCase(Locale.ROOT);
        }
    }
}
