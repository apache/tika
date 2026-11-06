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
package org.apache.tika.parser.blender;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.TikaMemoryLimitException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.EndianUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Extracts the preview Blender stores in a .blend file as the THUMBNAIL embedded document.
 * <p>
 * The file header is "BLENDER", then either the pointer size ('_' 4, '-' 8 bytes), the byte
 * order ('v' little, 'V' big endian) and three version digits, or, from Blender 5.0, the
 * header length and the file format version as two digits each, separated by '-', the byte
 * order and four version digits. Blocks follow, each a code of four characters, the data's
 * length, an address, the type of its data and the number of items, then the data. Format
 * version 0 orders them code, length (32 bits), address (pointer size), type (32 bits), count
 * (32 bits); version 1 code, type (32 bits), address, length and count (64 bits each).
 * <p>
 * Blender writes the preview as the "TEST" block after the "REND" one: width and height as
 * 32-bit integers, then RGBA bytes, the bottom row first. A file saved without one, as by an
 * older version or from a script, yields no thumbnail.
 */
@TikaComponent
public class BlenderParser implements Parser {

    private static final long serialVersionUID = 1L;

    private static final MediaType BLENDER = MediaType.application("x-blender");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(BLENDER);

    private static final String MAGIC = "BLENDER";

    /** The preview is the second block; a few more are looked at before giving up. */
    static final int MAX_BLOCKS = 16;

    /** Blender's previews are 128 pixels wide; this bounds the memory a bad header costs. */
    static final int MAX_THUMBNAIL_SIDE = 1024;

    /**
     * How much is skipped to reach the preview; the REND block before it is some 70 bytes.
     * Skipping a compressed file means uncompressing it, so a bad length must not lead far.
     */
    static final long MAX_SKIPPED_BYTES = 1024 * 1024;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(HttpHeaders.CONTENT_TYPE, BLENDER.toString());
        byte[] thumbnail;
        try (InputStream in = BlendCompression.open(CloseShieldInputStream.wrap(tis))) {
            if (in == null) {
                throw new TikaException("The file is zstd-compressed, and zstd-jni is not on the "
                        + "classpath");
            }
            thumbnail = readThumbnail(in);
        }
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            if (thumbnail != null) {
                emitThumbnail(thumbnail, xhtml, context);
            }
        } finally {
            xhtml.endDocument();
        }
    }

    /** The preview as a PNG, or {@code null} when the file has none. */
    static byte[] readThumbnail(InputStream in) throws IOException, TikaException {
        String magic = ascii(in, MAGIC.length());
        if (!MAGIC.equals(magic)) {
            throw new TikaException("Not a Blender file");
        }
        char next = (char) readByte(in);
        boolean largeBlocks;
        int pointerSize;
        boolean littleEndian;
        if (next == '_' || next == '-') {
            pointerSize = next == '_' ? 4 : 8;
            littleEndian = byteOrder(readByte(in));
            IOUtils.skipFully(in, 3);
            largeBlocks = false;
        } else {
            String digits = next + ascii(in, 1);
            if (!digits.matches("[0-9]{2}") || Integer.parseInt(digits) < MAGIC.length() + 6) {
                throw new TikaException("Bad Blender file header length: " + digits);
            }
            String rest = ascii(in, Integer.parseInt(digits) - MAGIC.length() - 2);
            if (!rest.startsWith("-01")) {
                throw new TikaException("Unsupported Blender file format version: " + rest);
            }
            littleEndian = byteOrder(rest.charAt(3));
            pointerSize = 8;
            largeBlocks = true;
        }
        long skipped = 0;
        for (int i = 0; i < MAX_BLOCKS; i++) {
            String code = ascii(in, 4);
            long length;
            if (largeBlocks) {
                IOUtils.skipFully(in, 4 + 8);
                length = littleEndian ? EndianUtils.readLongLE(in) : EndianUtils.readLongBE(in);
                IOUtils.skipFully(in, 8);
            } else {
                length = Integer.toUnsignedLong(readInt(in, littleEndian));
                IOUtils.skipFully(in, pointerSize + 4 + 4);
            }
            if (length < 0) {
                throw new TikaException("Bad block length " + length + " for " + code);
            }
            if ("TEST".equals(code)) {
                return png(in, length, littleEndian);
            }
            if ("ENDB".equals(code)) {
                return null;
            }
            skipped += length;
            if (skipped > MAX_SKIPPED_BYTES) {
                return null;
            }
            IOUtils.skipFully(in, length);
        }
        return null;
    }

    private static byte[] png(InputStream in, long length, boolean littleEndian)
            throws IOException, TikaException {
        int width = readInt(in, littleEndian);
        int height = readInt(in, littleEndian);
        if (width <= 0 || height <= 0 || 8L + 4L * width * height > length) {
            throw new TikaException("Bad preview size " + width + "x" + height + " in a "
                    + length + "-byte block");
        }
        if (width > MAX_THUMBNAIL_SIDE || height > MAX_THUMBNAIL_SIDE) {
            throw new TikaMemoryLimitException(4L * width * height,
                    4L * MAX_THUMBNAIL_SIDE * MAX_THUMBNAIL_SIDE);
        }
        byte[] rgba = IOUtils.toByteArray(in, 4 * width * height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int row = (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++) {
                int p = row + 4 * x;
                image.setRGB(x, y, (rgba[p + 3] & 0xff) << 24 | (rgba[p] & 0xff) << 16
                        | (rgba[p + 1] & 0xff) << 8 | (rgba[p + 2] & 0xff));
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);
        return png.toByteArray();
    }

    private static void emitThumbnail(byte[] png, XHTMLContentHandler xhtml,
                                      ParseContext context) throws IOException, SAXException {
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        Metadata thumbnail = new Metadata();
        thumbnail.set(TikaCoreProperties.RESOURCE_NAME_KEY, "thumbnail.png");
        thumbnail.set(HttpHeaders.CONTENT_TYPE, "image/png");
        thumbnail.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                TikaCoreProperties.EmbeddedResourceType.THUMBNAIL.toString());
        if (!extractor.shouldParseEmbedded(thumbnail, context)) {
            return;
        }
        try (TikaInputStream image = TikaInputStream.get(png)) {
            extractor.parseEmbedded(image, new EmbeddedContentHandler(xhtml), thumbnail, context,
                    true);
        }
    }

    private static boolean byteOrder(int b) throws TikaException {
        if (b == 'v') {
            return true;
        }
        if (b == 'V') {
            return false;
        }
        throw new TikaException("Bad byte order mark " + b);
    }

    private static int readInt(InputStream in, boolean littleEndian)
            throws IOException, TikaException {
        return littleEndian ? EndianUtils.readIntLE(in) : EndianUtils.readIntBE(in);
    }

    private static int readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private static String ascii(InputStream in, int length) throws IOException {
        return new String(IOUtils.toByteArray(in, length), StandardCharsets.US_ASCII);
    }
}
