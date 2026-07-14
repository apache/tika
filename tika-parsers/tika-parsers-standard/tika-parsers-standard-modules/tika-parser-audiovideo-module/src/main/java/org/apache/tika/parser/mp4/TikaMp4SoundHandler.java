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
package org.apache.tika.parser.mp4;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.media.Mp4SoundHandler;

import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Audio;

/**
 * Extends the sound track handling with what the base handler does not read
 * from the sample description: DRM protection markers (protected sample entry
 * formats such as 'drms' or 'enca') and the average bitrate from the 'esds'
 * elementary stream descriptor. See TIKA-4779.
 */
class TikaMp4SoundHandler extends Mp4SoundHandler {

    private final org.apache.tika.metadata.Metadata tikaMetadata;

    TikaMp4SoundHandler(Metadata metadata, Mp4Context context,
                        org.apache.tika.metadata.Metadata tikaMetadata) {
        super(metadata, context);
        this.tikaMetadata = tikaMetadata;
    }

    @Override
    public Mp4Handler<?> processBox(String type, byte[] payload, long boxSize,
                                    Mp4Context context) throws IOException {
        if ("stsd".equals(type) && payload != null) {
            extractFromSampleDescriptions(payload);
        }
        return super.processBox(type, payload, boxSize, context);
    }

    /**
     * Walks the sample description entries: 4 bytes version and flags, a
     * 4 byte entry count, then one sample entry per count, each starting with
     * its own size and format fourcc.
     */
    private void extractFromSampleDescriptions(byte[] b) {
        if (b.length < 8) {
            return;
        }
        long entryCount = EndianUtils.getUIntBE(b, 4);
        int pos = 8;
        for (long i = 0; i < entryCount && pos + 8 <= b.length; i++) {
            long size = EndianUtils.getUIntBE(b, pos);
            if (size < 16 || size > b.length - pos) {
                break;
            }
            int end = pos + (int) size;
            String format = fourCc(b, pos + 4);
            //protected streams replace the codec fourcc with a protected
            //entry format: 'drms' (FairPlay) or 'enca' (ISO common encryption)
            if ("drms".equals(format) || "enca".equals(format)) {
                tikaMetadata.set(Audio.HAS_DRM, true);
            }
            if (pos + 18 <= end) {
                //sample entry: 8 byte header, 6 reserved, 2 data ref index,
                //then version-dependent fixed sound fields before child boxes
                int version = EndianUtils.getUShortBE(b, pos + 16);
                int bitRate = findEsdsAverageBitRate(b, pos + soundEntrySize(version), end);
                if (bitRate > 0) {
                    tikaMetadata.set(Audio.BITRATE, bitRate);
                }
            }
            pos = end;
        }
    }

    /**
     * Size of the fixed part of a sound sample entry, after which the child
     * boxes start: 36 bytes for version 0, 52 for version 1 (four extra
     * 32-bit QuickTime fields), 72 for version 2.
     */
    private static int soundEntrySize(int version) {
        if (version == 1) {
            return 52;
        }
        if (version == 2) {
            return 72;
        }
        return 36;
    }

    /**
     * Scans the child boxes of a sample entry for an 'esds' box and returns
     * its average bitrate, or 0 if there is none. QuickTime version 1/2
     * entries may nest the 'esds' inside a 'wave' extension box.
     */
    private static int findEsdsAverageBitRate(byte[] b, int pos, int end) {
        while (pos >= 0 && pos + 8 <= end) {
            long size = EndianUtils.getUIntBE(b, pos);
            if (size < 8 || size > end - pos) {
                return 0;
            }
            String type = fourCc(b, pos + 4);
            if ("esds".equals(type)) {
                return readEsdsAverageBitRate(b, pos + 8, pos + (int) size);
            }
            if ("wave".equals(type)) {
                int nested = findEsdsAverageBitRate(b, pos + 8, pos + (int) size);
                if (nested > 0) {
                    return nested;
                }
            }
            pos += (int) size;
        }
        return 0;
    }

    /**
     * Extracts the average bitrate from an 'esds' box body, or returns 0 if
     * the descriptors cannot be walked. The chain is an ES_Descriptor (tag
     * 0x03) with three optional fields signalled by its flags byte, followed
     * by a DecoderConfigDescriptor (tag 0x04) whose fixed fields end with the
     * maximum and average bitrates.
     */
    private static int readEsdsAverageBitRate(byte[] b, int pos, int end) {
        //4 bytes version and flags, then the ES descriptor
        pos += 4;
        if (pos >= end || b[pos] != 0x03) {
            return 0;
        }
        pos = skipDescriptorLength(b, pos + 1);
        if (pos + 3 > end) {
            return 0;
        }
        //ES_ID (2 bytes), then a flags/priority byte announcing the
        //optional stream dependence, URL and OCR fields
        int flags = b[pos + 2] & 0xFF;
        pos += 3;
        if ((flags & 0x80) != 0) {
            pos += 2;
        }
        if ((flags & 0x40) != 0) {
            if (pos >= end) {
                return 0;
            }
            pos += 1 + (b[pos] & 0xFF);
        }
        if ((flags & 0x20) != 0) {
            pos += 2;
        }
        if (pos >= end || b[pos] != 0x04) {
            return 0;
        }
        pos = skipDescriptorLength(b, pos + 1);
        //object type (1), stream type (1), buffer size (3), max bitrate (4)
        pos += 9;
        if (pos + 4 > end) {
            return 0;
        }
        long averageBitRate = EndianUtils.getUIntBE(b, pos);
        return averageBitRate > 0 && averageBitRate <= Integer.MAX_VALUE
                ? (int) averageBitRate : 0;
    }

    /**
     * Skips a descriptor's variable length encoding (bytes with the high bit
     * set continue the length) and returns the position of the payload.
     */
    private static int skipDescriptorLength(byte[] b, int pos) {
        while (pos < b.length && (b[pos] & 0x80) != 0) {
            pos++;
        }
        return pos + 1;
    }

    private static String fourCc(byte[] b, int pos) {
        return new String(b, pos, 4, StandardCharsets.ISO_8859_1);
    }
}
