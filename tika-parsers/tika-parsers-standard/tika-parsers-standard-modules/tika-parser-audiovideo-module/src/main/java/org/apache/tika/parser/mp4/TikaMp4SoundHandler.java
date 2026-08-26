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

    private void extractFromSampleDescriptions(byte[] b) {
        Mp4SampleEntries.walk(b, this::sampleEntry);
    }

    private void sampleEntry(String fourCC, byte[] b, int start, int end) {
        int children = -1;
        if (start + Mp4SampleEntries.SAMPLE_ENTRY_FIELDS + 2 <= end) {
            //after the SampleEntry fields come the version-dependent fixed
            //sound fields, starting with the 2 byte version, then child boxes
            int version = EndianUtils.getUShortBE(b, start + Mp4SampleEntries.SAMPLE_ENTRY_FIELDS);
            children = start + soundEntrySize(version);
        }
        //protected streams replace the codec FourCC with a protected sample
        //entry format, 'drms' (FairPlay) or 'enca' (ISO common encryption),
        //and keep the original one in a child 'sinf'/'frma' box
        if (Mp4SampleEntries.isProtected(fourCC)) {
            tikaMetadata.set(Audio.HAS_DRM, true);
            if (children >= 0) {
                String original = Mp4SampleEntries.originalFormat(b, children, end);
                if (original != null) {
                    fourCC = original;
                }
            }
        }
        if (fourCC != null) {
            tikaMetadata.set(Audio.FOURCC, fourCC);
        }
        if (children >= 0) {
            int bitRate = findEsdsAverageBitRate(b, children, end, 0);
            if (bitRate > 0) {
                tikaMetadata.set(Audio.BITRATE, bitRate);
            }
        }
    }

    /**
     * Size of a sound sample entry after its box header, up to where the
     * child boxes start: 28 bytes for version 0, 44 for version 1 (four extra
     * 32-bit QuickTime fields), 64 for version 2.
     */
    private static int soundEntrySize(int version) {
        if (version == 1) {
            return 44;
        }
        if (version == 2) {
            return 64;
        }
        return 28;
    }

    //real files nest 'wave' at most one level; this only bounds crafted input,
    //where a deep chain of nested 'wave' boxes would otherwise recurse until the
    //stack overflows (an uncaught Error, not caught by Mp4Reader or CompositeParser).
    //See TIKA-4812.
    private static final int MAX_BOX_DEPTH = 10;

    /**
     * Scans the child boxes of a sample entry for an 'esds' box and returns
     * its average bitrate, or 0 if there is none. QuickTime version 1/2
     * entries may nest the 'esds' inside a 'wave' extension box.
     */
    private static int findEsdsAverageBitRate(byte[] b, int pos, int end, int depth) {
        if (depth > MAX_BOX_DEPTH) {
            return 0;
        }
        while (pos >= 0 && pos + 8 <= end) {
            long size = EndianUtils.getUIntBE(b, pos);
            if (size < 8 || size > end - pos) {
                return 0;
            }
            String type = Mp4SampleEntries.fourCC(b, pos + 4);
            if ("esds".equals(type)) {
                return readEsdsAverageBitRate(b, pos + 8, pos + (int) size);
            }
            if ("wave".equals(type)) {
                int nested = findEsdsAverageBitRate(b, pos + 8, pos + (int) size, depth + 1);
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
}
