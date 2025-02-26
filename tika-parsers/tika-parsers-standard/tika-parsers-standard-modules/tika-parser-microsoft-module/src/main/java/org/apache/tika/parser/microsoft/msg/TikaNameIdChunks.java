/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package org.apache.tika.parser.microsoft.msg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.codec.digest.PureJavaCrc32;
import org.apache.poi.hpsf.ClassID;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.ChunkGroup;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.Types;
import org.apache.poi.util.LittleEndian;
import org.apache.poi.util.LittleEndianByteArrayInputStream;
import org.apache.poi.util.StringUtil;

/**
 * Collection of convenience chunks for the NameID part of an outlook file
 * <p>
 * This is a temporary copy+paste+modify from Apache POI
 */
public final class TikaNameIdChunks implements ChunkGroup {
    public static final String NAME = "__nameid_version1.0";

    public enum PropertySetType {
        PS_MAPI("00020328-0000-0000-C000-000000000046"), PS_PUBLIC_STRINGS("00020329-0000-0000-C000-000000000046"),
        PS_INTERNET_HEADERS("00020386-0000-0000-C000-000000000046");

        private final ClassID classID;

        PropertySetType(String uuid) {
            classID = new ClassID(uuid);
        }

        public ClassID getClassID() {
            return classID;
        }
    }

    public enum PredefinedPropertySet {
        PSETID_COMMON("00062008-0000-0000-C000-000000000046"), PSETID_ADDRESS("00062004-0000-0000-C000-000000000046"), PSETID_APPOINTMENT("00062002-0000-0000-C000-000000000046"),
        PSETID_MEETING("6ED8DA90-450B-101B-98DA-00AA003F1305"), PSETID_LOG("0006200A-0000-0000-C000-000000000046"), PSETID_MESSAGING("41F28F13-83F4-4114-A584-EEDB5A6B0BFF"),
        PSETID_NOTE("0006200E-0000-0000-C000-000000000046"), PSETID_POST_RSS("00062041-0000-0000-C000-000000000046"), PSETID_TASK("00062003-0000-0000-C000-000000000046"),
        PSETID_UNIFIED_MESSAGING("4442858E-A9E3-4E80-B900-317A210CC15B"), PSETID_AIR_SYNC("71035549-0739-4DCB-9163-00F0580DBBDF"),
        PSETID_SHARING("00062040-0000-0000-C000-000000000046"), PSETID_XML_EXTRACTED_ENTITIES("23239608-685D-4732-9C55-4C95CB4E8E33"),
        PSETID_ATTACHMENT("96357F7F-59E1-47D0-99A7-46515C183B54"), //add this to POI
        PSETID_CALENDAR_ASSISTANT("11000E07-B51B-40D6-AF21-CAA85EDAB1D0");

        private final ClassID classID;

        PredefinedPropertySet(String uuid) {
            classID = new ClassID(uuid);
        }

        public ClassID getClassID() {
            return classID;
        }
    }

    private ByteChunk guidStream;
    private ByteChunk entryStream;
    private ByteChunk stringStream;

    /**
     * Holds all the chunks that were found, keyed by id. Not clear if we need a list
     * or if we can rely on a unique id
     */
    private Map<Integer, List<Chunk>> chunksById = new HashMap<>();
    private Map<Integer, List<MAPITag>> mapiTagMap = new HashMap<>();

    public Chunk[] getAll() {
        List<Chunk> chunks = new ArrayList<>();
        for (List<Chunk> c : chunksById.values()) {
            chunks.addAll(c);
        }
        return chunks.toArray(new Chunk[0]);
    }

    @Override
    public Chunk[] getChunks() {
        return getAll();
    }

    /**
     * Called by the parser whenever a chunk is found.
     */
    @Override
    public void record(Chunk chunk) {
        if (chunk.getType() == Types.BINARY) {
            switch (chunk.getChunkId()) {
                case 2:
                    guidStream = (ByteChunk) chunk;
                    break;
                case 3:
                    entryStream = (ByteChunk) chunk;
                    break;
                case 4:
                    stringStream = (ByteChunk) chunk;
                    break;
            }
        }
        List<Chunk> chunkList = chunksById.computeIfAbsent(chunk.getChunkId(), k -> new ArrayList<>());
        chunkList.add(chunk);
    }

    /**
     * Used to flag that all the chunks of the NameID have now been located.
     */
    @Override
    public void chunksComplete() {
        loadTags();
    }

    //does not return null
    public List<MAPITag> getTags(int storageId) {
        List<MAPITag> tags = mapiTagMap.get(storageId);
        if (tags == null) {
            return Collections.emptyList();
        }
        return tags;
    }

    private void loadTags() {
        final byte[] entryStreamBytes = (entryStream == null) ? null : entryStream.getValue();
        if (guidStream == null || entryStream == null || stringStream == null || entryStreamBytes == null) {
            return;
        }
        LittleEndianByteArrayInputStream leis = new LittleEndianByteArrayInputStream(entryStreamBytes);
        for (int i = 0; i < entryStreamBytes.length / 8; i++) {
            final long nameOffset = leis.readUInt();
            int guidIndex = leis.readUShort();
            final int propertyKind = guidIndex & 0x01;
            guidIndex = guidIndex >>> 1;
            final int propertyIndex = leis.readUShort();

            // fetch and match property GUID
            ClassID guid = getPropertyGUID(guidIndex);


            // fetch property name / stream ID
            final String[] propertyName = {null};
            final long[] propertyNameCRC32 = {-1L};
            long streamID = getStreamID(propertyKind, (int) nameOffset, guid, guidIndex, n -> propertyName[0] = n, c -> propertyNameCRC32[0] = c);

            long tag = -1;
            // find property index in matching stream entry
            if (propertyKind == 1 && propertyNameCRC32[0] < 0) {
                // skip stream entry matching and return tag from property index from entry stream
                // this code should not be reached
                tag = 0x8000L + propertyIndex;
            } else {
                tag = getPropertyTag(streamID, nameOffset, propertyNameCRC32[0]);
            }
            if (tag > 0 && tag < Integer.MAX_VALUE) {
                List<MAPITag> tagList = mapiTagMap.computeIfAbsent((int) tag, k -> new ArrayList<>());
                tagList.add(new MAPITag((int) nameOffset, propertyName[0], guid));
            }
        }

    }

    /**
     * Get property tag id by property set GUID and string name or numerical name from named properties mapping
     *
     * @param guid Property set GUID in registry format without brackets.
     *             May be one of the PS_* or PSETID_* constants
     * @param name Property name in case of string named property
     * @param id   Property id in case of numerical named property
     * @return Property tag which can be matched with {@link MAPIProperty#id}
     * or 0 if the property could not be found.
     */
    public long getPropertyTag(ClassID guid, String name, long id) {
        final byte[] entryStreamBytes = (entryStream == null) ? null : entryStream.getValue();
        if (guidStream == null || entryStream == null || stringStream == null || guid == null || entryStreamBytes == null) {
            return 0;
        }

        LittleEndianByteArrayInputStream leis = new LittleEndianByteArrayInputStream(entryStreamBytes);
        for (int i = 0; i < entryStreamBytes.length / 8; i++) {
            final long nameOffset = leis.readUInt();
            int guidIndex = leis.readUShort();
            final int propertyKind = guidIndex & 0x01;
            guidIndex = guidIndex >>> 1;
            final int propertyIndex = leis.readUShort();

            // fetch and match property GUID
            if (!guid.equals(getPropertyGUID(guidIndex))) {
                continue;
            }

            // fetch property name / stream ID
            final String[] propertyName = {null};
            final long[] propertyNameCRC32 = {-1L};
            long streamID = getStreamID(propertyKind, (int) nameOffset, guid, guidIndex, n -> propertyName[0] = n, c -> propertyNameCRC32[0] = c);

            if (!matchesProperty(propertyKind, nameOffset, name, propertyName[0], id)) {
                continue;
            }

            // find property index in matching stream entry
            if (propertyKind == 1 && propertyNameCRC32[0] < 0) {
                // skip stream entry matching and return tag from property index from entry stream
                // this code should not be reached
                return 0x8000L + propertyIndex;
            }

            return getPropertyTag(streamID, nameOffset, propertyNameCRC32[0]);
        }
        return 0;
    }

    private long getPropertyTag(long streamID, long nameOffset, long propertyNameCRC32) {
        List<Chunk> chunks = chunksById.get((int) streamID);
        if (chunks == null) {
            return 0;
        }
        for (Chunk chunk : chunks) {
            if (chunk == null || chunk.getType() != Types.BINARY || chunk.getChunkId() != streamID) {
                continue;
            }
            byte[] matchChunkBytes = ((ByteChunk) chunk).getValue();
            if (matchChunkBytes == null) {
                continue;
            }
            LittleEndianByteArrayInputStream leis = new LittleEndianByteArrayInputStream(matchChunkBytes);
            for (int m = 0; m < matchChunkBytes.length / 8; m++) {
                long nameCRC = leis.readUInt();
                int matchGuidIndex = leis.readUShort();
                int matchPropertyIndex = leis.readUShort();
                int matchPropertyKind = matchGuidIndex & 0x01;

                if (nameCRC == (matchPropertyKind == 0 ? nameOffset : propertyNameCRC32)) {
                    return 0x8000L + matchPropertyIndex;
                }
            }
        }
        return 0;
    }

    private ClassID getPropertyGUID(int guidIndex) {
        if (guidIndex == 1) {
            // predefined GUID
            return PropertySetType.PS_MAPI.classID;
        } else if (guidIndex == 2) {
            // predefined GUID
            return PropertySetType.PS_PUBLIC_STRINGS.classID;
        } else if (guidIndex >= 3) {
            // GUID from guid stream
            byte[] guidStreamBytes = guidStream.getValue();
            int guidIndexOffset = (guidIndex - 3) * 0x10;
            if (guidStreamBytes.length >= guidIndexOffset + 0x10) {
                return new ClassID(guidStreamBytes, guidIndexOffset);
            }
        }
        return null;
    }

    // property set GUID matches
    private static boolean matchesProperty(int propertyKind, long nameOffset, String name, String propertyName, long id) {
        return
                // match property by id
                (propertyKind == 0 && id >= 0 && id == nameOffset) ||
                        // match property by name
                        (propertyKind == 1 && name != null && name.equals(propertyName));
    }


    private long getStreamID(int propertyKind, int nameOffset, ClassID guid, int guidIndex, Consumer<String> propertyNameSetter, Consumer<Long> propertyNameCRC32Setter) {
        if (propertyKind == 0) {
            // numerical named property
            return 0x1000L + (nameOffset ^ (guidIndex << 1)) % 0x1F;
        }

        // string named property
        byte[] stringBytes = stringStream.getValue();
        long propertyNameCRC32 = -1;
        if (stringBytes.length > nameOffset) {
            long nameLength = LittleEndian.getUInt(stringBytes, nameOffset);
            if (stringBytes.length >= nameOffset + 4 + nameLength) {
                int nameStart = nameOffset + 4;
                String propertyName = new String(stringBytes, nameStart, (int) nameLength, StringUtil.UTF16LE);
                if (PropertySetType.PS_INTERNET_HEADERS.classID.equals(guid)) {
                    byte[] n = propertyName
                            .toLowerCase(Locale.ROOT)
                            .getBytes(StringUtil.UTF16LE);
                    propertyNameCRC32 = calculateCRC32(n, 0, n.length);
                } else {
                    propertyNameCRC32 = calculateCRC32(stringBytes, nameStart, (int) nameLength);
                }
                propertyNameSetter.accept(propertyName);
                propertyNameCRC32Setter.accept(propertyNameCRC32);
            }
        }
        return 0x1000 + (propertyNameCRC32 ^ ((guidIndex << 1) | 1)) % 0x1F;
    }

    /**
     * Calculates the CRC32 of the given bytes (conforms to RFC 1510, SSH-1).
     * The CRC32 calculation is similar to the standard one as demonstrated in RFC 1952,
     * but with the inversion (before and after the calculation) omitted.
     * <ul>
     * <li>poly:    0x04C11DB7</li>
     * <li>init:    0x00000000</li>
     * <li>xor:     0x00000000</li>
     * <li>revin:   true</li>
     * <li>revout:  true</li>
     * <li>check:   0x2DFD2D88 (CRC32 of "123456789")</li>
     * </ul>
     *
     * @param buf the byte array to calculate CRC32 on
     * @param off the offset within buf at which the CRC32 calculation will start
     * @param len the number of bytes on which to calculate the CRC32
     * @return the CRC32 value (unsigned 32-bit integer stored in a long).
     * @see <a href="http://www.zorc.breitbandkatze.de/crc.html">CRC parameter check</a>
     */
    private static long calculateCRC32(byte[] buf, int off, int len) {
        PureJavaCrc32 crc = new PureJavaCrc32();
        // set initial crc value to 0
        crc.update(new byte[]{-1, -1, -1, -1}, 0, 4);
        crc.update(buf, off, len);
        return ~crc.getValue() & 0xFFFFFFFFL;
    }

}
