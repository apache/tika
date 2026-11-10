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

/** Reads values at arbitrary bit positions, LSB first. */
public class BitReader {
    /**
     * Direct-mapped cache from raw GUID bytes to the derived UUID: a file references the same
     * few GUIDs everywhere, so this avoids recomputing the MD5. Entries are immutable and a pure
     * function of the key, so racing writers only replace one correct entry with another.
     */
    private static final int GUID_CACHE_MASK = 1023;
    private static final GuidCacheEntry[] GUID_CACHE = new GuidCacheEntry[GUID_CACHE_MASK + 1];

    private record GuidCacheEntry(long high, long low, UUID uuid) {
    }

    private final long startPosition;
    private final long length;
    private byte[] byteArray;
    private long offset;

    public BitReader(byte[] array, int index) {
        this.offset = ((long) index * 8) - 1;
        this.startPosition = this.offset;
        this.length = (long) array.length * 8;
        this.byteArray = array;
    }

    public boolean getCurrent() {
        return (byteArray[(int) offset >> 3] & (1 << ((int) offset & 7))) != 0;
    }

    public long readUInt64(int readingLength) throws IOException {
        byte[] uint64Bytes = this.getBytes(readingLength, 8);
        return LittleEndianBitConverter.toUInt64(uint64Bytes, 0);
    }

    public int readUInt32(int readingLength) throws IOException {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return (int) LittleEndianBitConverter.toUInt32(uint32Bytes, 0);
    }

    public int readUInt16(int readingLength) throws IOException {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.ToUInt16(uint16Bytes, 0);
    }

    public byte[] readBytes(int readingLength) throws IOException {
        return this.getBytes(readingLength * 8, readingLength);
    }

    public short readInt16(int readingLength) throws IOException {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.toInt16(uint16Bytes, 0);
    }

    public int readInt32(int readingLength) throws IOException {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return LittleEndianBitConverter.toInt32(uint32Bytes, 0);
    }

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
        UUID uuid = GuidUtil.fromMicrosoftBytes(raw, 0);
        GUID_CACHE[slot] = new GuidCacheEntry(high, low, uuid);
        return uuid;
    }

    /** Advances one bit; false once past the end. */
    public boolean moveNext() {
        return ++this.offset < this.length;
    }

    public void dispose() {
        this.byteArray = null;
    }

    /** Rewinds to just before the start position. */
    public void reset() {
        this.offset = this.startPosition;
    }

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
