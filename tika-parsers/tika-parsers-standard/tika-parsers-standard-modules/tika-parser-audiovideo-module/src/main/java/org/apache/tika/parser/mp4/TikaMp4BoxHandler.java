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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Context;
import com.drew.metadata.mp4.Mp4Directory;
import org.xml.sax.SAXException;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.mp4.boxes.TikaUserDataBox;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaMp4BoxHandler extends Mp4BoxHandler {

    //QTFF "well-known" metadata item value types
    private static final int QT_TEXT_TYPE = 1;
    private static final int QT_INT_BE_TYPE = 21;
    private static final int QT_UINT_BE_TYPE = 22;
    private static final int QT_FLOAT32_TYPE = 23;
    private static final int QT_FLOAT64_TYPE = 24;

    //QuickTime stores location as an ISO 6709 string (e.g. +32.4720-084.9952+073.827/)
    private static final String QT_LOCATION_ISO6709 = "com.apple.quicktime.location.ISO6709";
    private static final Pattern ISO6709_PATTERN =
            Pattern.compile("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)?");


    org.apache.tika.metadata.Metadata tikaMetadata;
    final XHTMLContentHandler xhtml;

    //key names for the current 'meta' box, filled from its 'keys' box and consumed
    //by the following 'ilst' box (e.g. com.apple.quicktime.content.identifier)
    private final List<String> quickTimeMetadataKeys = new ArrayList<>();

    //duration of the current track's leading empty edit(s) ('elst' entries
    //with media time -1), in movie timescale units; -1 if the track has none
    private long emptyEditDuration = -1;

    public TikaMp4BoxHandler(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata,
                             XHTMLContentHandler xhtml) {
        super(metadata);
        this.tikaMetadata = tikaMetadata;
        this.xhtml = xhtml;
    }

    @Override
    public boolean shouldAcceptBox(@NotNull String box) {
        if (box.equals("udta") || box.equals("keys") || box.equals("ilst")
                || box.equals("elst")) {
            return true;
        }
        return super.shouldAcceptBox(box);
    }

    @Override
    public boolean shouldAcceptContainer(@NotNull String box) {
        //edts is needed to reach a track's edit list, which the base handler
        //skips; the edit list defines the presentation start of the track
        if (box.equals("edts")) {
            return true;
        }
        return super.shouldAcceptContainer(box);
    }

    @Override
    public Mp4Handler<?> processBox(@NotNull String box, @Nullable byte[] payload,
                                    long size, Mp4Context context)
            throws IOException {
        if (payload == null && box.equals("trak")) {
            //a new track starts (containers route through here with a null
            //payload); forget the previous track's edit list
            emptyEditDuration = -1;
        } else if (box.equals("udta")) {
            return processUserData(box, payload, context);
        } else if (box.equals("keys")) {
            processQuickTimeKeys(payload);
            return this;
        } else if (box.equals("ilst")) {
            processQuickTimeItemList(payload);
            return this;
        } else if (box.equals("elst")) {
            processEditList(payload);
            return this;
        } else if (box.equals("hdlr") && payload != null && payload.length >= 12
                && payload[8] == 'm' && payload[9] == 'e'
                && payload[10] == 't' && payload[11] == 'a') {
            //timed metadata track (e.g. the Live Photo still-image-time track):
            //hand over to our handler, which extracts the mebx key declarations
            //(the base Mp4MetaHandler accepts the track's boxes but extracts
            //nothing from them)
            Long movieTimescale = directory.getLongObject(Mp4Directory.TAG_TIME_SCALE);
            return new TikaMp4MetaHandler(metadata, context, tikaMetadata,
                    emptyEditDuration, movieTimescale == null ? 0 : movieTimescale);
        }

        return super.processBox(box, payload, size, context);
    }


    private Mp4Handler<?> processUserData(String box, byte[] payload, Mp4Context context) throws IOException {
        if (payload == null) {
            return this;
        }
        try {
            new TikaUserDataBox(box, payload, tikaMetadata, xhtml).addMetadata(directory);
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return this;
    }

    /**
     * Parses the QuickTime metadata 'keys' box, which maps 1-based indices to key
     * names such as {@code com.apple.quicktime.content.identifier}. The base MP4
     * handler descends into the enclosing 'meta' container but skips 'keys'/'ilst',
     * so this metadata (content identifier, ISO 6709 location, make/model, ...) was
     * previously dropped for QuickTime .mov (and any .mp4 carrying it).
     */
    private void processQuickTimeKeys(@Nullable byte[] payload) {
        quickTimeMetadataKeys.clear();
        if (payload == null || payload.length < 8) {
            return;
        }
        //1 byte version + 3 bytes flags, then uint32 entry count
        int pos = 4;
        long entryCount = readUInt32(payload, pos);
        pos += 4;
        for (long i = 0; i < entryCount && pos + 8 <= payload.length; i++) {
            long keySize = readUInt32(payload, pos);
            if (keySize < 8 || pos + keySize > payload.length) {
                return;
            }
            //4 bytes key namespace, then the UTF-8 key name
            quickTimeMetadataKeys.add(
                    new String(payload, pos + 8, (int) keySize - 8, StandardCharsets.UTF_8));
            pos += (int) keySize;
        }
    }

    /**
     * Parses the QuickTime metadata 'ilst' box, whose entries are keyed by the
     * 1-based index into the preceding 'keys' box. Each entry holds a 'data' box
     * with the value. UTF-8 text and the numeric "well-known" value types are emitted
     * under their key name; other types (e.g. images, binary plists) are skipped.
     */
    private void processQuickTimeItemList(@Nullable byte[] payload) {
        if (payload == null) {
            return;
        }
        int pos = 0;
        while (pos + 8 <= payload.length) {
            long entrySize = readUInt32(payload, pos);
            if (entrySize < 8 || pos + entrySize > payload.length) {
                return;
            }
            int index = (int) readUInt32(payload, pos + 4);
            int entryEnd = (int) (pos + entrySize);
            int data = pos + 8;
            //inner 'data' box: size(4) type(4) valueType(4) locale(4) value
            if (data + 16 <= entryEnd) {
                long dataSize = readUInt32(payload, data);
                boolean isData = payload[data + 4] == 'd' && payload[data + 5] == 'a'
                        && payload[data + 6] == 't' && payload[data + 7] == 'a';
                if (isData && dataSize >= 16 && data + dataSize <= entryEnd) {
                    int valueType = (int) readUInt32(payload, data + 8);
                    int valueLength = (int) dataSize - 16;
                    if (index >= 1 && index <= quickTimeMetadataKeys.size()) {
                        String key = quickTimeMetadataKeys.get(index - 1);
                        String value = decodeValue(payload, data + 16, valueLength, valueType);
                        if (value != null) {
                            tikaMetadata.add(key, value);
                            if (key.equals(QT_LOCATION_ISO6709)) {
                                addLocation(value);
                            }
                        }
                    }
                }
            }
            pos += (int) entrySize;
        }
    }


    /**
     * Parses an 'elst' edit list and remembers the total duration of the leading
     * empty edits (media time -1, expressed in movie timescale units), which is
     * what delays the track's presentation start. Empty edits after the first
     * normal entry do not delay the track and are ignored. Apple writes the Live
     * Photo still moment as such an empty edit shifting the single one-tick
     * sample of the still-image-time track.
     */
    private void processEditList(@Nullable byte[] payload) {
        if (payload == null || payload.length < 8) {
            return;
        }
        int version = payload[0];
        long entryCount = readUInt32(payload, 4);
        int pos = 8;
        int entrySize = version == 1 ? 20 : 12;
        long leadingEmptyEdits = 0;
        for (long i = 0; i < entryCount && pos + entrySize <= payload.length; i++) {
            long segmentDuration;
            long mediaTime;
            if (version == 1) {
                segmentDuration = readInt64(payload, pos);
                mediaTime = readInt64(payload, pos + 8);
            } else {
                segmentDuration = readUInt32(payload, pos);
                mediaTime = (int) readUInt32(payload, pos + 4);
            }
            if (mediaTime != -1) {
                break;
            }
            leadingEmptyEdits += segmentDuration;
            pos += entrySize;
        }
        if (leadingEmptyEdits > 0) {
            emptyEditDuration = leadingEmptyEdits;
        }
    }





    /**
     * Maps an ISO 6709 location string (latitude, longitude, optional altitude) to the
     * standard {@code geo:lat}/{@code geo:long}/{@code geo:alt} properties, in addition to
     * the raw value, so QuickTime location matches the {@code geo:*} output of the udta path.
     */
    private void addLocation(String iso6709) {
        Matcher matcher = ISO6709_PATTERN.matcher(iso6709);
        if (matcher.find()) {
            tikaMetadata.set(TikaCoreProperties.LATITUDE, Double.parseDouble(matcher.group(1)));
            tikaMetadata.set(TikaCoreProperties.LONGITUDE, Double.parseDouble(matcher.group(2)));
            if (matcher.group(3) != null) {
                tikaMetadata.set(TikaCoreProperties.ALTITUDE, Double.parseDouble(matcher.group(3)));
            }
        }
    }

    /**
     * Decodes a metadata item value of one of the QTFF "well-known" types to a string,
     * or returns null for types that are not handled (e.g. images or binary plists).
     * Integers may be 1 to 8 bytes wide (e.g. the live-photo.auto flag is a single byte).
     */
    @Nullable
    private static String decodeValue(byte[] b, int off, int len, int valueType) {
        switch (valueType) {
            case QT_TEXT_TYPE:
                return new String(b, off, len, StandardCharsets.UTF_8);
            case QT_INT_BE_TYPE:
            case QT_UINT_BE_TYPE:
                if (len < 1 || len > 8) {
                    return null;
                }
                byte[] intBytes = Arrays.copyOfRange(b, off, off + len);
                return valueType == QT_INT_BE_TYPE
                        ? new BigInteger(intBytes).toString()
                        : new BigInteger(1, intBytes).toString();
            case QT_FLOAT32_TYPE:
                return len == 4 ? String.valueOf(ByteBuffer.wrap(b, off, len).getFloat()) : null;
            case QT_FLOAT64_TYPE:
                return len == 8 ? String.valueOf(ByteBuffer.wrap(b, off, len).getDouble()) : null;
            default:
                return null;
        }
    }

    private static long readUInt32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16)
                | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }

    private static long readInt64(byte[] b, int off) {
        return (readUInt32(b, off) << 32) | readUInt32(b, off + 4);
    }
}
