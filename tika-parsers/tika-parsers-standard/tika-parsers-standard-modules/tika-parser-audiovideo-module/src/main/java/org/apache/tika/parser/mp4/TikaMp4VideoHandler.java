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
import com.drew.metadata.mp4.media.Mp4VideoHandler;

import org.apache.tika.io.EndianUtils;
import org.apache.tika.metadata.Video;

/**
 * Extends the video track handling with what the base handler does not read
 * from the sample description: the average bitrate from the 'btrt' BitRateBox.
 * See TIKA-4802.
 */
class TikaMp4VideoHandler extends Mp4VideoHandler {

    /**
     * Fixed size of a VisualSampleEntry (ISO/IEC 14496-12) before its child
     * boxes: the 8 byte box header, 8 bytes of SampleEntry (6 reserved, 2 data
     * reference index) and 70 bytes of visual fields ending with the 32 byte
     * compressor name, the depth and a pre-defined field.
     */
    private static final int VISUAL_ENTRY_SIZE = 86;

    private final org.apache.tika.metadata.Metadata tikaMetadata;

    TikaMp4VideoHandler(Metadata metadata, Mp4Context context,
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
            //the format fourcc is the video codec ('avc1', 'hev1', ...) or, for
            //protected streams, the protected sample entry format ('encv'/'drmi')
            tikaMetadata.set(Video.FORMAT, fourCc(b, pos + 4));
            int bitRate = findBtrtAverageBitRate(b, pos + VISUAL_ENTRY_SIZE, end);
            if (bitRate > 0) {
                tikaMetadata.set(Video.BITRATE, bitRate);
            }
            pos = end;
        }
    }

    /**
     * Scans the child boxes of a sample entry for a 'btrt' BitRateBox and
     * returns its average bitrate, or 0 if there is none. The box body is the
     * decoding buffer size, the maximum bitrate and the average bitrate.
     */
    private static int findBtrtAverageBitRate(byte[] b, int pos, int end) {
        while (pos >= 0 && pos + 8 <= end) {
            long size = EndianUtils.getUIntBE(b, pos);
            if (size < 8 || size > end - pos) {
                return 0;
            }
            if ("btrt".equals(fourCc(b, pos + 4)) && pos + 20 <= end) {
                long averageBitRate = EndianUtils.getUIntBE(b, pos + 16);
                return averageBitRate > 0 && averageBitRate <= Integer.MAX_VALUE
                        ? (int) averageBitRate : 0;
            }
            pos += (int) size;
        }
        return 0;
    }

    private static String fourCc(byte[] b, int pos) {
        return new String(b, pos, 4, StandardCharsets.ISO_8859_1);
    }
}
