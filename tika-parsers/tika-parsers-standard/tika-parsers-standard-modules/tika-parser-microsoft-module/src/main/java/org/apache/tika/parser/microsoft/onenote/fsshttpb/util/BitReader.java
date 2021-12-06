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

import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

/**
 * A class is used to extract values across byte boundaries with arbitrary bit positions.
 */
public class BitReader {
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
    private BitSet bitSet;
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
        this.bitSet = BitSet.valueOf(array);
    }

    private static String toBinaryString(BitSet bs, int nbits) {
        final StringBuilder buffer = new StringBuilder(bs.size());
        for (int i = nbits - 1; i >= 0; --i) {
            if (i < bs.size()) {
                buffer.append(bs.get(i) ? "1" : "0");
            } else {
                buffer.append("0");
            }
        }
        return buffer.toString();
    }

    public boolean getCurrent() {
        return bitSet.get((int) offset);
    }

    /**
     * Read specified bit length content as an UInt64 type and increase the bit offset.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt64 type value.
     */
    public long readUInt64(int readingLength) {
        byte[] uint64Bytes = this.getBytes(readingLength, 8);
        return LittleEndianBitConverter.toUInt64(uint64Bytes, 0);
    }

    /**
     * Read specified bit length content as an UInt32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt32 type value.
     */
    public int readUInt32(int readingLength) {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return LittleEndianBitConverter.toUInt32(uint32Bytes, 0);
    }

    public int readUInt16(int readingLength) {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.ToUInt16(uint16Bytes, 0);
    }

    /**
     * Reading the bytes specified by the byte length.
     *
     * @param readingLength Specify the reading byte length.
     * @return Return the read bytes array.
     */
    public byte[] readBytes(int readingLength) {
        return this.getBytes(readingLength * 8, readingLength);
    }

    /**
     * Read specified bit length content as an UInt16 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt16 value.
     */
    public short readInt16(int readingLength) {
        byte[] uint16Bytes = this.getBytes(readingLength, 2);
        return LittleEndianBitConverter.toInt16(uint16Bytes, 0);
    }

    /**
     * Read specified bit length content as an Int32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the Int32 type value.
     */
    public int readInt32(int readingLength) {
        byte[] uint32Bytes = this.getBytes(readingLength, 4);
        return LittleEndianBitConverter.toInt32(uint32Bytes, 0);
    }

    /**
     * Read as a GUID from the current offset position and increate the bit offset with 128 bit.
     *
     * @return Return the GUID value.
     */
    public UUID readGuid() {
        return UUID.nameUUIDFromBytes(this.getBytes(128, 16));
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
        this.bitSet = null;
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
    private byte[] getBytes(int needReadlength, int size) {
        BitSet retSet = new BitSet(size);
        int i = 0;
        while (i < needReadlength) {
            if (!this.moveNext()) {
                throw new RuntimeException("Unexpected to meet the byte array end.");
            }
            if (getCurrent()) {
                retSet.set(i);
            } else {
                retSet.clear(i);
            }
            i++;
        }
        byte[] result = new byte[size];
        Arrays.fill(result, (byte) 0);
        byte[] retSetBa = retSet.toByteArray();
        for (i = 0; i < retSetBa.length; ++i) {
            result[i] = retSetBa[i];
        }
        return result;
    }
}
