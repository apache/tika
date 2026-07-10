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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drew.lang.SequentialByteArrayReader;
import com.drew.lang.SequentialReader;

/**
 * Reassembles a large XMP packet that a JPEG splits across multiple APP1 segments
 * ("Extended XMP", XMP Spec Part 3). The chunk stitching is borrowed almost verbatim
 * from metadata-extractor's {@code XmpReader} (Apache License 2.0, Drew Noakes); the
 * only change is that we find the GUID by scanning the standard packet instead of via
 * xmpcore, since Tika parses the XMP itself.
 */
final class ExtendedXmp {

    private static final String XMP_JPEG_PREAMBLE = "http://ns.adobe.com/xap/1.0/\u0000";
    private static final String XMP_EXTENSION_JPEG_PREAMBLE =
            "http://ns.adobe.com/xmp/extension/\u0000";
    private static final int EXTENDED_XMP_GUID_LENGTH = 32;
    private static final int EXTENDED_XMP_INT_LENGTH = 4;
    // Tika: cap the declared length so a hostile chunk can't drive a huge allocation (not upstream).
    private static final int MAX_EXTENDED = 64 * 1024 * 1024;
    private static final Pattern HAS_EXTENDED =
            Pattern.compile("HasExtendedXMP[\"'>=\\s]{0,4}([A-Fa-f0-9]{32})");

    private ExtendedXmp() {
    }

    /** XMP packets (standard + reassembled extended, if any) from a JPEG's APP1 segment payloads. */
    static List<byte[]> assemble(Iterable<byte[]> segments) {
        // adapted from metadata-extractor XmpReader.readJpegSegments
        final int preambleLength = XMP_JPEG_PREAMBLE.length();
        final int extensionPreambleLength = XMP_EXTENSION_JPEG_PREAMBLE.length();
        String guid = null;
        byte[] standard = null;
        byte[] extendedBuffer = null;

        for (byte[] segmentBytes : segments) {
            if (segmentBytes.length >= preambleLength && XMP_JPEG_PREAMBLE
                    .equalsIgnoreCase(new String(segmentBytes, 0, preambleLength, US_ASCII))) {
                standard = new byte[segmentBytes.length - preambleLength];
                System.arraycopy(segmentBytes, preambleLength, standard, 0, standard.length);
                guid = findGuid(standard);
            } else if (guid != null && segmentBytes.length >= extensionPreambleLength
                    && XMP_EXTENSION_JPEG_PREAMBLE.equalsIgnoreCase(
                            new String(segmentBytes, 0, extensionPreambleLength, US_ASCII))) {
                extendedBuffer = processExtendedXMPChunk(segmentBytes, guid, extendedBuffer);
            }
        }

        List<byte[]> packets = new ArrayList<>();
        if (standard != null) {
            packets.add(standard);
        }
        if (extendedBuffer != null) {
            packets.add(extendedBuffer);
        }
        return packets;
    }

    private static String findGuid(byte[] standard) {
        Matcher m = HAS_EXTENDED.matcher(new String(standard, US_ASCII));
        return m.find() ? m.group(1) : null;
    }

    // Borrowed almost verbatim from metadata-extractor XmpReader.processExtendedXMPChunk (Apache 2.0).
    private static byte[] processExtendedXMPChunk(byte[] segmentBytes, String extendedXMPGUID,
                                                  byte[] extendedXMPBuffer) {
        final int extensionPreambleLength = XMP_EXTENSION_JPEG_PREAMBLE.length();
        final int segmentLength = segmentBytes.length;
        final int totalOffset = extensionPreambleLength + EXTENDED_XMP_GUID_LENGTH
                + EXTENDED_XMP_INT_LENGTH + EXTENDED_XMP_INT_LENGTH;
        if (segmentLength >= totalOffset) {
            try {
                final SequentialReader reader = new SequentialByteArrayReader(segmentBytes);
                reader.skip(extensionPreambleLength);
                final String segmentGUID = reader.getString(EXTENDED_XMP_GUID_LENGTH);
                if (extendedXMPGUID.equals(segmentGUID)) {
                    final int fullLength = (int) reader.getUInt32();
                    final int chunkOffset = (int) reader.getUInt32();
                    if (extendedXMPBuffer == null) {
                        if (fullLength <= 0 || fullLength > MAX_EXTENDED) {   // Tika OOM guard
                            return null;
                        }
                        extendedXMPBuffer = new byte[fullLength];
                    }
                    if (extendedXMPBuffer.length == fullLength) {
                        int copyLength = segmentLength - totalOffset;
                        if (chunkOffset >= 0 && chunkOffset <= extendedXMPBuffer.length - copyLength) {
                            System.arraycopy(segmentBytes, totalOffset, extendedXMPBuffer, chunkOffset,
                                    copyLength);
                        }
                    }
                }
            } catch (IOException ex) {
                // best effort: keep whatever chunks were already assembled
            }
        }
        return extendedXMPBuffer;
    }
}
