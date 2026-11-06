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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.util;

import java.io.IOException;
import java.util.UUID;

/**
 * A class is used to extract values across byte boundaries with arbitrary bit positions.
 */
public class BitReader {
    /**
     * Direct-mapped cache from raw GUID bytes to the derived UUID. A file references the same
     * few GUIDs from every ExGuid and cell ID, so most reads would otherwise recompute the same
     * MD5. Entries are immutable and the mapping is a pure function of the key, so racing
     * writers can only replace one correct entry with another.
     */
    private static final int GUID_CACHE_MASK = 1023;
    private static final GuidCacheEntry[] GUID_CACHE = new GuidCacheEntry[GUID_CACHE_MASK + 1];

    private record GuidCacheEntry(long high, long low, UUID uuid) {
    }

    /**
     * A start position which will be not changed in the process of reading.
     * This value will be used for recording the start position and will be used by the function reset.
     */
    private final long startPosition;
    /**
     * The length of the byte Array which contains the byte need to be read.
     */
    private final long length;
    /**
     * A byte array which contains the bytes need to be read.
     */
    private byte[] byteArray;
    /**
     * An offset which is used to keep trace for the current read position in bit.
     */
    private long offset;

    /**
     * Initializes a new instance of the BitReader class with specified bytes buffer and start position in byte.
     *
     * @param array Specify the byte array which contains the bytes need to be read.
     * @param index Specify the start position in byte.
     */
    public BitReader(byte[] array, int index) {
        this.offset = ((long) index * 8) - 1;
        this.startPosition = this.offset;
        this.length = (long) array.length * 8;
        this.byteArray = array;
    }

    public boolean getCurrent() {
        return (byteArray[(int) offset >> 3] & (1 << ((int) offset & 7))) != 0;
    }

    /**
     * Read specified bit length content as an UInt64 type and increase the bit offset.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt64 type value.
     */
    public long readUInt64(int readingLength) throws IOException {
        byte[] uint64Bytes = this.getBytes(readingLength, 8);
        return LittleEndianBitConverter.toUInt64(uint64Bytes, 0);
    }

    /**
     * Read specified bit length content as an UInt32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt32 type value.
     */
    public int readUInt32(int readingLength) throws IOException {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return LittleEndianBitConverter.toUInt32(uint32Bytes, 0);
    }

    public int readUInt16(int readingLength) throws IOException {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.ToUInt16(uint16Bytes, 0);
    }

    /**
     * Reading the bytes specified by the byte length.
     *
     * @param readingLength Specify the reading byte length.
     * @return Return the read bytes array.
     */
    public byte[] readBytes(int readingLength) throws IOException {
        return this.getBytes(readingLength * 8, readingLength);
    }

    /**
     * Read specified bit length content as an UInt16 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt16 value.
     */
    public short readInt16(int readingLength) throws IOException {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.toInt16(uint16Bytes, 0);
    }

    /**
     * Read specified bit length content as an Int32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the Int32 type value.
     */
    public int readInt32(int readingLength) throws IOException {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return LittleEndianBitConverter.toInt32(uint32Bytes, 0);
    }

    /**
     * Read as a GUID from the current offset position and increate the bit offset with 128 bit.
     *
     * @return Return the GUID value.
     */
    public UUID readGuid() throws IOException {
        byte[] raw = this.getBytes(128, 16);
        long high = 0;
        long low = 0;
        for (int i = 0; i < 8; i++) {
            high = (high << 8) | (raw[i] & 0xff);
            low = (low << 8) | (raw[i + 8] & 0xff);
        }
        long mixed = (high ^ low) * 0x9E3779B97F4A7C15L;
        int slot = (int) (mixed >>> 54) & GUID_CACHE_MASK;
        GuidCacheEntry entry = GUID_CACHE[slot];
        if (entry != null && entry.high == high && entry.low == low) {
            return entry.uuid;
        }
        UUID uuid = UUID.nameUUIDFromBytes(raw);
        GUID_CACHE[slot] = new GuidCacheEntry(high, low, uuid);
        return uuid;
    }

    /**
     * Advances the enumerator to the next bit of the byte array.
     *
     * @return true if the enumerator was successfully advanced to the next bit; false if the enumerator
     * has passed the end of the byte array.
     */
    public boolean moveNext() {
        return ++this.offset < this.length;
    }

    /**
     * Assign the internal read buffer to null.
     */
    public void dispose() {
        this.byteArray = null;
    }

    /**
     * Sets the enumerator to its initial position, which is before the first bit in the byte array.
     */
    public void reset() {
        this.offset = this.startPosition;
    }

    /**
     * Construct a byte array with specified bit length and the specified the byte array size.
     *
     * @param needReadlength Specify the need read bit length.
     * @param size           Specify the byte array size.
     * @return Returns the constructed byte array.
     */
    private byte[] getBytes(int needReadlength, int size) throws IOException {
        // Bit i of the result is bit i after the current offset, least significant bit first.
        // The bounds check below guarantees every source byte read holds a requested bit.
        long start = this.offset + 1;
        if (needReadlength > this.length - start) {
            this.offset = this.length;
            throw new IOException("Unexpected to meet the byte array end.");
        }
        byte[] result = new byte[size];
        int shift = (int) (start & 7);
        int source = (int) (start >> 3);
        int fullBytes = needReadlength >> 3;
        int remainingBits = needReadlength & 7;
        if (shift == 0) {
            System.arraycopy(byteArray, source, result, 0, fullBytes);
            source += fullBytes;
        } else {
            for (int i = 0; i < fullBytes; i++, source++) {
                result[i] = (byte) (((byteArray[source] & 0xFF) >>> shift) |
                        (byteArray[source + 1] << (8 - shift)));
            }
        }
        if (remainingBits > 0) {
            int bits = (byteArray[source] & 0xFF) >>> shift;
            if (shift + remainingBits > 8) {
                bits |= byteArray[source + 1] << (8 - shift);
            }
            result[fullBytes] = (byte) (bits & ((1 << remainingBits) - 1));
        }
        this.offset += needReadlength;
        return result;
    }
}
