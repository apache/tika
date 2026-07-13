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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.jpeg.JpegSegmentData;
import com.drew.imaging.jpeg.JpegSegmentReader;
import com.drew.imaging.jpeg.JpegSegmentType;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xmp.XMPPacketScanner;
import org.apache.tika.parser.xmp.XmpExtractor;

/**
 * Image XMP via the shared {@link XmpExtractor}. XMP is auxiliary: a bad packet is recorded and
 * swallowed so it never fails the image parse (matches the PDF module).
 */
final class ImageXmp {

    private ImageXmp() {
    }

    /**
     * Scan an {@code <?xpacket?>}-wrapped packet out of a stream (TIFF/JXL/PSD/HEIF) and parse
     * it. Returns true if a packet was found, so callers can fall back to another locator.
     */
    static boolean scanAndExtract(InputStream stream, Metadata metadata, ParseContext context) {
        try {
            UnsynchronizedByteArrayOutputStream out =
                    UnsynchronizedByteArrayOutputStream.builder().get();
            if (new XMPPacketScanner().parse(stream, out)) {
                try (InputStream packet = out.toInputStream()) {
                    new XmpExtractor().extract(packet, metadata, context);
                }
                return true;
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        }
        return false;
    }

    /** Parse an already-isolated raw XMP packet (BPG). */
    static void extractRaw(byte[] xmp, Metadata metadata, ParseContext context) {
        try {
            new XmpExtractor().extract(xmp, metadata, context);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        }
    }

    /** JPEG: read APP1 segments, reassemble Extended XMP, parse each resulting packet. */
    static void extractJpeg(File file, Metadata metadata, ParseContext context) {
        try {
            Iterable<byte[]> app1;
            try {
                JpegSegmentData data = JpegSegmentReader.readSegments(file,
                        Collections.singletonList(JpegSegmentType.APP1));
                app1 = data.getSegments(JpegSegmentType.APP1);
            } catch (JpegProcessingException e) {
                return;   // not a decodable jpeg segment structure
            }
            if (app1 == null) {
                return;
            }
            for (byte[] packet : ExtendedXmp.assemble(app1)) {
                new XmpExtractor().extract(packet, metadata, context);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        }
    }

    /** WebP: pull the raw packet out of the RIFF {@code "XMP "} chunk and parse it. */
    static void extractWebp(File file, Metadata metadata, ParseContext context) {
        try {
            byte[] xmp = readRiffChunk(file, "XMP ");
            if (xmp != null) {
                new XmpExtractor().extract(xmp, metadata, context);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);
        }
    }

    // 64 MB: guards against a corrupt chunk size triggering a huge allocation.
    private static final long MAX_CHUNK = 64L * 1024 * 1024;

    /** Return the payload of the first top-level RIFF chunk with the given FourCC, or null. */
    private static byte[] readRiffChunk(File file, String fourCC) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] head = new byte[12];
            if (IOUtils.read(in, head, 0, 12) < 12 || head[0] != 'R' || head[1] != 'I' ||
                    head[2] != 'F' || head[3] != 'F' || head[8] != 'W' || head[9] != 'E' ||
                    head[10] != 'B' || head[11] != 'P') {
                return null;
            }
            byte[] ch = new byte[8];
            while (IOUtils.read(in, ch, 0, 8) == 8) {
                long size = (ch[4] & 0xffL) | (ch[5] & 0xffL) << 8 |
                        (ch[6] & 0xffL) << 16 | (ch[7] & 0xffL) << 24;
                if (fourCC.equals(new String(ch, 0, 4, StandardCharsets.US_ASCII))) {
                    if (size > MAX_CHUNK) {
                        return null;   // target chunk too large to allocate
                    }
                    byte[] data = new byte[(int) size];
                    return IOUtils.read(in, data, 0, data.length) == data.length ? data : null;
                }
                // a large foreign chunk before "XMP " must be skipped, not abort the scan
                IOUtils.skipFully(in, size + (size & 1L));   // RIFF pads chunks to even length
            }
        }
        return null;
    }
}
