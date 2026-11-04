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
package org.apache.tika.parser.sketchup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
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
 * Extracts the preview SketchUp stores in a .skp file as the THUMBNAIL embedded document.
 * <p>
 * The file starts with "SketchUp Model" and the version, both as length-prefixed UTF-16
 * strings. Up to SketchUp 2020 the preview follows in a "CDib" record: the class tag
 * {@code ff ff}, a 16-bit schema, the 16-bit name length and "CDib", then two 32-bit
 * little-endian integers, the second the length of the PNG after them. From SketchUp 2021,
 * a zip follows a few bytes after the version; its "meta/model_thumbnail.png" is the model
 * alone on a transparent background, "meta/preview_thumbnail.png" the view with its
 * background and axes. The first is taken when it is there.
 */
@TikaComponent
public class SketchUpParser implements Parser {

    private static final long serialVersionUID = 1L;

    private static final MediaType SKETCHUP = MediaType.application("vnd.sketchup.skp");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(SKETCHUP);

    private static final byte[] MAGIC = utf16("SketchUp Model");

    private static final byte[] CDIB = {(byte) 0xff, (byte) 0xff, 3, 0, 4, 0, 'C', 'D', 'i', 'b'};

    private static final byte[] ZIP = {'P', 'K', 3, 4};

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private static final String MODEL_THUMBNAIL = "meta/model_thumbnail.png";

    private static final String VIEW_THUMBNAIL = "meta/preview_thumbnail.png";

    /** The preview record sits about 2 KB into the files of SketchUp 3 to 2020. */
    static final int MAX_HEAD_LENGTH = 64 * 1024;

    /** How far behind the version the zip may start; it is 6 bytes in every known file. */
    private static final int MAX_ZIP_OFFSET = 64;

    /** The zip's thumbnails come first; a few more entries are looked at. */
    private static final int MAX_ZIP_ENTRIES = 8;

    static final int MAX_THUMBNAIL_LENGTH = 16 * 1024 * 1024;

    /**
     * How much of an entry before the thumbnail is read through; moving past an entry means
     * uncompressing it, and real files have none before the thumbnail.
     */
    static final int MAX_SKIPPED_ENTRY_LENGTH = 1024 * 1024;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(HttpHeaders.CONTENT_TYPE, SKETCHUP.toString());
        byte[] png = readThumbnail(tis);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            if (png != null) {
                emitThumbnail(png, xhtml, context);
            }
        } finally {
            xhtml.endDocument();
        }
    }

    /** The preview, a PNG, or {@code null} when the file has none. */
    static byte[] readThumbnail(InputStream in) throws IOException, TikaException {
        byte[] head = in.readNBytes(MAX_HEAD_LENGTH);
        int afterVersion = afterHeader(head);
        int zip = indexOf(head, ZIP, afterVersion, afterVersion + MAX_ZIP_OFFSET);
        if (zip >= 0) {
            return fromZip(rest(head, zip, in));
        }
        int cdib = indexOf(head, CDIB, afterVersion, head.length);
        if (cdib < 0) {
            return null;
        }
        InputStream record = rest(head, cdib + CDIB.length, in);
        EndianUtils.readIntLE(record);
        long length = Integer.toUnsignedLong(EndianUtils.readIntLE(record));
        if (length > MAX_THUMBNAIL_LENGTH) {
            throw new TikaMemoryLimitException(length, MAX_THUMBNAIL_LENGTH);
        }
        return png(IOUtils.toByteArray(record, (int) length));
    }

    /** The offset after "SketchUp Model" and the version string. */
    private static int afterHeader(byte[] head) throws TikaException {
        int at = 0;
        for (int i = 0; i < 2; i++) {
            if (head.length < at + 4 || (head[at] & 0xff) != 0xff || (head[at + 1] & 0xff) != 0xfe
                    || (head[at + 2] & 0xff) != 0xff) {
                throw new TikaException("Not a SketchUp file");
            }
            int end = at + 4 + 2 * (head[at + 3] & 0xff);
            if (i == 0 && (head.length < end
                    || !Arrays.equals(head, at + 4, end, MAGIC, 0, MAGIC.length))) {
                throw new TikaException("Not a SketchUp file");
            }
            at = end;
        }
        return Math.min(at, head.length);
    }

    private static byte[] fromZip(InputStream in) throws IOException, TikaException {
        byte[] view = null;
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        for (int i = 0; i < MAX_ZIP_ENTRIES && (entry = zip.getNextEntry()) != null; i++) {
            if (MODEL_THUMBNAIL.equals(entry.getName())) {
                return png(bounded(zip));
            }
            if (VIEW_THUMBNAIL.equals(entry.getName())) {
                view = bounded(zip);
            } else if (zip.readNBytes(MAX_SKIPPED_ENTRY_LENGTH + 1).length
                    > MAX_SKIPPED_ENTRY_LENGTH) {
                break;
            }
        }
        return view == null ? null : png(view);
    }

    private static byte[] bounded(InputStream in) throws IOException, TikaException {
        byte[] bytes = in.readNBytes(MAX_THUMBNAIL_LENGTH + 1);
        if (bytes.length > MAX_THUMBNAIL_LENGTH) {
            throw new TikaMemoryLimitException(bytes.length, MAX_THUMBNAIL_LENGTH);
        }
        return bytes;
    }

    private static byte[] png(byte[] bytes) throws TikaException {
        if (bytes.length < PNG.length || !Arrays.equals(bytes, 0, PNG.length, PNG, 0, PNG.length)) {
            throw new TikaException("The preview is not a PNG");
        }
        return bytes;
    }

    private static InputStream rest(byte[] head, int from, InputStream in) {
        return new SequenceInputStream(
                new ByteArrayInputStream(head, from, Math.max(0, head.length - from)), in);
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

    private static int indexOf(byte[] haystack, byte[] needle, int from, int to) {
        int end = Math.min(to, haystack.length) - needle.length;
        for (int i = Math.max(0, from); i <= end; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] utf16(String s) {
        return s.getBytes(StandardCharsets.UTF_16LE);
    }
}
