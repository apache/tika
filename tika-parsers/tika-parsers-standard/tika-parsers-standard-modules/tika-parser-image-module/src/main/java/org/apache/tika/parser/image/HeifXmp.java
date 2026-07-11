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

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.drew.lang.SequentialByteArrayReader;
import com.drew.metadata.heif.boxes.Box;
import com.drew.metadata.heif.boxes.ItemInfoBox;
import com.drew.metadata.heif.boxes.ItemInfoBox.ItemInfoEntry;
import com.drew.metadata.heif.boxes.ItemLocationBox;
import com.drew.metadata.heif.boxes.ItemLocationBox.Extent;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xmp.XmpExtractor;

/**
 * HEIF/HEIC XMP fallback for packets that are <em>not</em> {@code <?xpacket?>}-wrapped, which
 * the byte scanner ({@link ImageXmp#scanAndExtract}) cannot find. XMP is stored as a
 * {@code mime}/{@code application/rdf+xml} item located through the ISO-BMFF
 * {@code meta}/{@code iinf}/{@code iloc} boxes. The box parsing reuses metadata-extractor's
 * public box classes (it already reads the Exif item the same way); only the top-level box
 * walk is ours. metadata-extractor exposes no content-type getter, so a {@code mime} item is
 * confirmed by sniffing its bytes for XMP markers before parsing.
 */
final class HeifXmp {

    private HeifXmp() {
    }

    // 64 MB: guards against a corrupt extent length triggering a huge allocation.
    private static final long MAX_ITEM = 64L * 1024 * 1024;

    /** Locate the rdf+xml item and parse it; returns false if none was found. */
    static boolean extract(File file, Metadata metadata, ParseContext context) {
        byte[] xmp = locateQuietly(file);
        if (xmp == null) {
            return false;
        }
        try {
            new XmpExtractor().extract(xmp, metadata, context);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            EmbeddedDocumentUtil.recordException(e, metadata);   // bad XMP must not fail the parse
        }
        return true;
    }

    private static byte[] locateQuietly(File file) {
        try {
            return locate(file);
        } catch (SecurityException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            return null;   // malformed / unreadable box structure -> no XMP, let EXIF proceed
        }
    }

    private static byte[] locate(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long len = raf.length();
            long[] meta = box(raf, 0, len, "meta");
            if (meta == null) {
                return null;
            }
            // meta is a FullBox: skip its 4-byte version/flags to reach the child boxes.
            long childStart = meta[1] + 4;
            long[] iinfAt = box(raf, childStart, meta[2], "iinf");
            long[] ilocAt = box(raf, childStart, meta[2], "iloc");
            if (iinfAt == null || ilocAt == null) {
                return null;
            }
            ItemInfoBox iinf = parseIinf(readBytes(raf, iinfAt[0], (int) (iinfAt[2] - iinfAt[0])));
            ItemLocationBox iloc = parseIloc(readBytes(raf, ilocAt[0], (int) (ilocAt[2] - ilocAt[0])));
            return readXmpItem(raf, iinf, iloc, len);
        }
    }

    /** First box of {@code type} within [start,end); returns {boxStart, payloadStart, boxEnd}. */
    private static long[] box(RandomAccessFile raf, long start, long end, String type)
            throws IOException {
        long p = start;
        while (p + 8 <= end) {
            raf.seek(p);
            long size = raf.readInt() & 0xffffffffL;
            byte[] t = new byte[4];
            raf.readFully(t);
            long headerLen = 8;
            if (size == 1) {
                size = raf.readLong();
                headerLen = 16;
            } else if (size == 0) {
                size = end - p;
            }
            if (size < headerLen || p + size > end) {
                return null;   // corrupt/truncated
            }
            if (type.equals(new String(t, US_ASCII))) {
                return new long[]{p, p + headerLen, p + size};
            }
            p += size;
        }
        return null;
    }

    private static ItemInfoBox parseIinf(byte[] bytes) throws IOException {
        SequentialByteArrayReader r = new SequentialByteArrayReader(bytes);
        return new ItemInfoBox(r, new Box(r));
    }

    private static ItemLocationBox parseIloc(byte[] bytes) throws IOException {
        SequentialByteArrayReader r = new SequentialByteArrayReader(bytes);
        return new ItemLocationBox(r, new Box(r));
    }

    private static byte[] readXmpItem(RandomAccessFile raf, ItemInfoBox iinf, ItemLocationBox iloc,
                                      long fileLen) throws IOException {
        Map<Long, List<Extent>> byItem = new LinkedHashMap<>();
        for (Extent e : iloc.getExtents()) {
            byItem.computeIfAbsent(e.getItemId(), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<Long, List<Extent>> item : byItem.entrySet()) {
            ItemInfoEntry info = iinf.getEntry(item.getKey());
            if (info == null || !"mime".equals(info.getItemType())) {
                continue;
            }
            byte[] data = readExtents(raf, item.getValue(), fileLen);
            if (data != null && looksLikeXmp(data)) {
                return data;
            }
        }
        return null;
    }

    private static byte[] readExtents(RandomAccessFile raf, List<Extent> extents, long fileLen)
            throws IOException {
        // Extent.getOffset() is treated as an absolute file offset: iloc construction_method 0,
        // which every real XMP item uses. Methods 1/2 (idat-/item-relative) are not resolved here
        // -- the same limitation as metadata-extractor's own Exif-item reader -- but that is a safe
        // miss, not a fault: a wrong offset either fails the bounds check below or yields bytes that
        // looksLikeXmp() rejects, so the item is skipped, never read as garbage or thrown. Resolve
        // the base offset here only if a real method-1/2 XMP file ever turns up.
        long total = 0;
        for (Extent e : extents) {
            if (e.getOffset() < 0 || e.getLength() < 0 || e.getOffset() + e.getLength() > fileLen) {
                return null;
            }
            total += e.getLength();
            if (total > MAX_ITEM) {
                return null;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) total);
        for (Extent e : extents) {
            out.write(readBytes(raf, e.getOffset(), (int) e.getLength()));
        }
        return out.toByteArray();
    }

    private static byte[] readBytes(RandomAccessFile raf, long pos, int len) throws IOException {
        byte[] b = new byte[len];
        raf.seek(pos);
        raf.readFully(b);
        return b;
    }

    private static boolean looksLikeXmp(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 8192), US_ASCII);
        return head.contains("xmpmeta") || head.contains("rdf:RDF") || head.contains("xpacket");
    }
}
