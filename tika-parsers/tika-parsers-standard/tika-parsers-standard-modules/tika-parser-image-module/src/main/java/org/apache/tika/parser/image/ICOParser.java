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
 * the fallback.
 */
@TikaComponent
public class ICOParser implements Parser {

    private static final long serialVersionUID = 4212837190215395123L;

    static final MediaType ICO_TYPE = MediaType.image("vnd.microsoft.icon");
    static final MediaType CUR_TYPE = MediaType.image("x-win-bitmap");

    private static final Set<MediaType> SUPPORTED_TYPES = Set.of(ICO_TYPE, CUR_TYPE);

    private static final int TYPE_ICON = 1;
    private static final int TYPE_CURSOR = 2;
    private static final int HEADER_SIZE = 6;
    private static final int ENTRY_SIZE = 16;
    private static final int BITMAP_INFO_HEADER_SIZE = 40;
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final int PNG_IHDR_SIZE = 8 + 8 + 13;
    // Icons are small; anything bigger is read only this far
    private static final int MAX_FILE_SIZE = 16 * 1024 * 1024;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        byte[] file = IOUtils.toByteArray(new BoundedInputStream(MAX_FILE_SIZE, tis));
        if (file.length < HEADER_SIZE || EndianUtils.getUShortLE(file, 0) != 0) {
            throw new TikaException("Not an ICO or CUR file");
        }
        int type = EndianUtils.getUShortLE(file, 2);
        if (type != TYPE_ICON && type != TYPE_CURSOR) {
            throw new TikaException("Not an ICO or CUR file: type " + type);
        }
        metadata.set(HttpHeaders.CONTENT_TYPE,
                (type == TYPE_CURSOR ? CUR_TYPE : ICO_TYPE).toString());

        int count = EndianUtils.getUShortLE(file, 4);
        metadata.set(Icon.IMAGE_COUNT, count);
        Image largest = null;
        int unreadable = 0;
        for (int i = 0; i < count; i++) {
            int entry = HEADER_SIZE + i * ENTRY_SIZE;
            if (entry + ENTRY_SIZE > file.length) {
                unreadable += count - i;
                break;
            }
            Image image = readImage(file, entry);
            if (image == null) {
                unreadable++;
                continue;
            }
            metadata.add(Icon.IMAGES, image.describe());
            if (largest == null || image.outranks(largest)) {
                largest = image;
            }
        }
        if (largest != null) {
            metadata.set(TIFF.IMAGE_WIDTH, largest.width);
            metadata.set(TIFF.IMAGE_LENGTH, largest.height);
            metadata.set(TIFF.BITS_PER_SAMPLE, Integer.toString(largest.bitsPerPixel));
            if (type == TYPE_CURSOR) {
                metadata.set(Icon.HOTSPOT_X, largest.hotspotX);
                metadata.set(Icon.HOTSPOT_Y, largest.hotspotY);
            }
        }
        if (unreadable > 0) {
            EmbeddedDocumentUtil.recordException(new TikaException(
                    unreadable + " of " + count + " images lie outside the file or have no" +
                            " readable header"), metadata, context);
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        xhtml.endDocument();
    }

    /**
     * Reads one ICONDIRENTRY and the header of the image it points to.
     *
     * @return the image, or null if its data lies outside the file
     */
    private static Image readImage(byte[] file, int entry) {
        Image image = new Image();
        // 0 in the directory means 256
        image.width = file[entry] == 0 ? 256 : file[entry] & 0xff;
        image.height = file[entry + 1] == 0 ? 256 : file[entry + 1] & 0xff;
        // planes and bit count for icons, hotspot for cursors
        image.hotspotX = EndianUtils.getUShortLE(file, entry + 4);
        image.hotspotY = EndianUtils.getUShortLE(file, entry + 6);
        image.bitsPerPixel = image.hotspotY;
        long size = EndianUtils.getUIntLE(file, entry + 8);
        long offset = EndianUtils.getUIntLE(file, entry + 12);
        if (offset < HEADER_SIZE || offset >= file.length) {
            return null;
        }
        int data = (int) offset;
        long available = Math.min(size, file.length - offset);
        if (available >= PNG_IHDR_SIZE && startsWithPngSignature(file, data)) {
            image.encoding = "png";
            image.width = EndianUtils.getIntBE(file, data + 16);
            image.height = EndianUtils.getIntBE(file, data + 20);
            image.bitsPerPixel = pngBitsPerPixel(file[data + 24] & 0xff, file[data + 25] & 0xff);
        } else if (available >= BITMAP_INFO_HEADER_SIZE &&
                EndianUtils.getUIntLE(file, data) == BITMAP_INFO_HEADER_SIZE) {
            image.encoding = "bmp";
            image.width = Math.abs(EndianUtils.getIntLE(file, data + 4));
            // the height covers the XOR bitmap and the AND mask
            image.height = Math.abs(EndianUtils.getIntLE(file, data + 8)) / 2;
            image.bitsPerPixel = EndianUtils.getUShortLE(file, data + 14);
        } else {
            image.encoding = "unknown";
        }
        return image;
    }

    private static boolean startsWithPngSignature(byte[] file, int offset) {
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (file[offset + i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static int pngBitsPerPixel(int bitDepth, int colorType) {
        switch (colorType) {
            case 2: // truecolour
                return 3 * bitDepth;
            case 4: // greyscale with alpha
                return 2 * bitDepth;
            case 6: // truecolour with alpha
                return 4 * bitDepth;
            default: // greyscale or palette
                return bitDepth;
        }
    }

    private static final class Image {
        int width;
        int height;
        int bitsPerPixel;
        int hotspotX;
        int hotspotY;
        String encoding;

        /**
         * Larger area wins, then the higher colour depth, like Windows' own choice.
         */
        boolean outranks(Image other) {
            long area = (long) width * height;
            long otherArea = (long) other.width * other.height;
            return area > otherArea || area == otherArea && bitsPerPixel > other.bitsPerPixel;
        }

        String describe() {
            return width + "x" + height + "@" + bitsPerPixel + "bpp " + encoding;
        }
    }
}
