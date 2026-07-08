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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Context;
import org.xml.sax.SAXException;

import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.mp4.boxes.TikaUserDataBox;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaMp4BoxHandler extends Mp4BoxHandler {

    //metadata item value type for UTF-8 text (QTFF "well-known" type 1)
    private static final int QT_TEXT_TYPE = 1;

    //QuickTime stores location as an ISO 6709 string (e.g. +32.4720-084.9952+073.827/)
    private static final String QT_LOCATION_ISO6709 = "com.apple.quicktime.location.ISO6709";
    private static final Pattern ISO6709_PATTERN =
            Pattern.compile("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)?");

    org.apache.tika.metadata.Metadata tikaMetadata;
    final XHTMLContentHandler xhtml;

    //key names for the current 'meta' box, filled from its 'keys' box and consumed
    //by the following 'ilst' box (e.g. com.apple.quicktime.content.identifier)
    private final List<String> quickTimeMetadataKeys = new ArrayList<>();

    public TikaMp4BoxHandler(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata,
                             XHTMLContentHandler xhtml) {
        super(metadata);
        this.tikaMetadata = tikaMetadata;
        this.xhtml = xhtml;
    }

    @Override
    public boolean shouldAcceptBox(@NotNull String box) {
        if (box.equals("udta") || box.equals("keys") || box.equals("ilst")) {
            return true;
        }
        return super.shouldAcceptBox(box);
    }

    @Override
    public boolean shouldAcceptContainer(@NotNull String box) {
        return super.shouldAcceptContainer(box);
    }

    @Override
    public Mp4Handler<?> processBox(@NotNull String box, @Nullable byte[] payload,
                                    long size, Mp4Context context)
            throws IOException {
        if (box.equals("udta")) {
            return processUserData(box, payload, context);
        } else if (box.equals("keys")) {
            processQuickTimeKeys(payload);
            return this;
        } else if (box.equals("ilst")) {
            processQuickTimeItemList(payload);
            return this;
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
     * with the value. Only UTF-8 text values are emitted, under their key name.
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
                    if (valueType == QT_TEXT_TYPE
                            && index >= 1 && index <= quickTimeMetadataKeys.size()) {
                        String key = quickTimeMetadataKeys.get(index - 1);
                        String value =
                                new String(payload, data + 16, valueLength, StandardCharsets.UTF_8);
                        tikaMetadata.add(key, value);
                        if (key.equals(QT_LOCATION_ISO6709)) {
                            addLocation(value);
                        }
                    }
                }
            }
            pos += (int) entrySize;
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

    private static long readUInt32(byte[] b, int off) {
        return ((b[off] & 0xFFL) << 24) | ((b[off + 1] & 0xFFL) << 16)
                | ((b[off + 2] & 0xFFL) << 8) | (b[off + 3] & 0xFFL);
    }
}
