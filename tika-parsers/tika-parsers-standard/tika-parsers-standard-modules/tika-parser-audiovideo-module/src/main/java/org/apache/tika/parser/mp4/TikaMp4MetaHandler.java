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
import java.util.ArrayList;
import java.util.List;

import com.drew.lang.SequentialReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.media.Mp4MetaHandler;

import org.apache.tika.metadata.QuickTime;

/**
 * Handles a QuickTime timed metadata track (handler type 'meta'). The base
 * {@link Mp4MetaHandler} accepts the track's 'stsd' and 'stts' boxes but
 * extracts nothing from them; this subclass reads the key names declared by
 * 'mebx' sample descriptions (ISO 14496-12 boxed metadata) and, for a
 * single-sample track declaring {@code com.apple.quicktime.still-image-time},
 * emits the presentation time of that sample as
 * {@link QuickTime#STILL_IMAGE_TIME}.
 * <p>
 * Apple Live Photo videos mark the moment the paired still image was
 * captured this way: a leading empty edit delays the track's single one-tick
 * sample to the still moment. Without a leading empty edit the sample
 * presents at 0, so 0 is emitted (the still is the first frame); with more
 * than one sample the track's start is not the time of any single sample and
 * nothing is emitted. The sample value itself is a constant -1 marker, so
 * the moov boxes alone are sufficient and mdat is never read. See TIKA-4777.
 */
class TikaMp4MetaHandler extends Mp4MetaHandler {

    static final String STILL_IMAGE_TIME_KEY = "com.apple.quicktime.still-image-time";

    private final org.apache.tika.metadata.Metadata tikaMetadata;
    //duration of the track's leading empty edit in movie timescale units,
    //or -1 if the track is not delayed
    private final long emptyEditDuration;
    private final long movieTimescale;
    private final List<String> keyNames = new ArrayList<>();
    private long sampleCount = -1;

    TikaMp4MetaHandler(Metadata metadata, Mp4Context context,
                       org.apache.tika.metadata.Metadata tikaMetadata,
                       long emptyEditDuration, long movieTimescale) {
        super(metadata, context);
        this.tikaMetadata = tikaMetadata;
        this.emptyEditDuration = emptyEditDuration;
        this.movieTimescale = movieTimescale;
    }

    @Override
    protected void processSampleDescription(SequentialReader reader) throws IOException {
        reader.skip(4); //1 byte version + 3 bytes flags
        long entryCount = reader.getUInt32();
        for (long i = 0; i < entryCount; i++) {
            long entrySize = reader.getUInt32();
            String format = reader.getString(4, StandardCharsets.ISO_8859_1);
            //also reject sizes beyond the remaining payload: a crafted size
            //like 0xFFFFFFFF would turn negative in the int cast below
            if (entrySize < 16 || entrySize - 8 > reader.available()) {
                return;
            }
            byte[] entry = reader.getBytes((int) entrySize - 8);
            if ("mebx".equals(format)) {
                //6 bytes reserved + 2 bytes data reference index, then the boxes
                parseMebxKeyNames(entry, 8, entry.length, keyNames);
            }
        }
        maybeEmit();
    }

    @Override
    protected void processTimeToSample(SequentialReader reader, Mp4Context context)
            throws IOException {
        reader.skip(4); //1 byte version + 3 bytes flags
        long entryCount = reader.getUInt32();
        long samples = 0;
        for (long i = 0; i < entryCount; i++) {
            samples += reader.getUInt32(); //sample count
            reader.skip(4);                //sample delta
        }
        sampleCount = samples;
        maybeEmit();
    }

    /**
     * Emits once the sample description and the time-to-sample table have both
     * been seen (their order within 'stbl' is not fixed), and only when the
     * conditions from the class comment hold.
     */
    private void maybeEmit() {
        if (sampleCount != 1 || !keyNames.contains(STILL_IMAGE_TIME_KEY)) {
            return;
        }
        if (emptyEditDuration <= 0) {
            //no leading empty edit: the sample presents at 0, the still is
            //the first frame
            tikaMetadata.set(QuickTime.STILL_IMAGE_TIME, 0L);
        } else if (movieTimescale > 0
                && emptyEditDuration <= Long.MAX_VALUE / 1_000_000L) {
            tikaMetadata.set(QuickTime.STILL_IMAGE_TIME,
                    emptyEditDuration * 1_000_000L / movieTimescale);
        }
        keyNames.clear();
    }

    /**
     * Extracts the key names declared by a 'mebx' sample entry: a 'keys' box
     * containing one child box per key (typed by the local key id), each of
     * which holds a 'keyd' key declaration of namespace plus key name.
     */
    private static void parseMebxKeyNames(byte[] b, int start, int end, List<String> keyNames) {
        int pos = start;
        while (pos + 8 <= end) {
            long size = readUInt32(b, pos);
            if (size < 8 || pos + size > end) {
                break;
            }
            if ("keys".equals(boxType(b, pos + 4))) {
                int keyPos = pos + 8;
                int keysEnd = (int) (pos + size);
                while (keyPos + 8 <= keysEnd) {
                    long keySize = readUInt32(b, keyPos);
                    if (keySize < 8 || keyPos + keySize > keysEnd) {
                        break;
                    }
                    int declPos = keyPos + 8;
                    int keyEnd = (int) (keyPos + keySize);
                    while (declPos + 8 <= keyEnd) {
                        long declSize = readUInt32(b, declPos);
                        if (declSize < 8 || declPos + declSize > keyEnd) {
                            break;
                        }
                        //'keyd' payload: 4 bytes namespace (e.g. mdta), then the name
                        if ("keyd".equals(boxType(b, declPos + 4)) && declSize > 12) {
                            keyNames.add(new String(b, declPos + 12,
                                    (int) declSize - 12, StandardCharsets.UTF_8));
                        }
                        declPos += (int) declSize;
                    }
                    keyPos += (int) keySize;
                }
            }
            pos += (int) size;
        }
    }

    private static String boxType(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.ISO_8859_1);
    }

    private static long readUInt32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16)
                | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }
}
