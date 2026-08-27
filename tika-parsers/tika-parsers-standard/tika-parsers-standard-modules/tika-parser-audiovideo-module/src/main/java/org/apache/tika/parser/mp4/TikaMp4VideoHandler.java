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
     * Fixed size of a VisualSampleEntry (ISO/IEC 14496-12) after its box
     * header, up to where the child boxes start: 8 bytes of SampleEntry (6
     * reserved, 2 data reference index) and 70 bytes of visual fields ending
     * with the 32 byte compressor name, the depth and a pre-defined field.
     */
    private static final int VISUAL_ENTRY_SIZE = 78;

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

    private void extractFromSampleDescriptions(byte[] b) {
        Mp4SampleEntries.walk(b, this::sampleEntry);
    }

    private void sampleEntry(String fourCC, byte[] b, int start, int end) {
        int children = start + VISUAL_ENTRY_SIZE;
        //the FourCC is the video codec ('avc1', 'hev1', ...) or, for protected
        //streams, the protected sample entry format ('encv'/'drmi') with the
        //original one kept in a child 'sinf'/'frma' box
        if (Mp4SampleEntries.isProtected(fourCC)) {
            String original = Mp4SampleEntries.originalFormat(b, children, end);
            if (original != null) {
                fourCC = original;
            }
        }
        if (fourCC != null) {
            tikaMetadata.set(Video.FOURCC, fourCC);
        }
        int bitRate = findBtrtAverageBitRate(b, children, end);
        if (bitRate > 0) {
            tikaMetadata.set(Video.BITRATE, bitRate);
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
            if ("btrt".equals(Mp4SampleEntries.fourCC(b, pos + 4)) && pos + 20 <= end) {
                long averageBitRate = EndianUtils.getUIntBE(b, pos + 16);
                return averageBitRate > 0 && averageBitRate <= Integer.MAX_VALUE
                        ? (int) averageBitRate : 0;
            }
            pos += (int) size;
        }
        return 0;
    }
}
