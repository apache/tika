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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;

public class BitWriter {

    private final BitSet bitSet;

    /**
     * An offset which is used to keep trace for the current write position in bit, staring with 0.
     */
    private int bitOffset;

    /**
     * Initializes a new instance of the BitWriter class with specified buffer size in byte.
     *
     * @param bufferSize Specify the buffer byte size.
     */
    public BitWriter(int bufferSize) {
        this.bitSet = new BitSet(bufferSize);
        this.bitOffset = 0;
    }

    /**
     * Gets a copy byte array which contains the current written byte.
     */
    public byte[] getBytes() throws IOException {
        if (this.bitOffset % 8 != 0) {
            throw new IOException(
                    "BitWriter:Bytes, Cannot get the current bytes because the last byte is not written completely.");
        }

        int retByteLength = this.bitOffset / 8;
        return Arrays.copyOfRange(bitSet.toByteArray(), 0, retByteLength);
    }

    public List<Byte> getByteList() throws IOException {
        byte[] bytes = getBytes();
        List<Byte> byteList = new ArrayList<>(bytes.length);
        for (byte aByte : bytes) {
            byteList.add(aByte);
        }
        return byteList;
    }

    /**
     * Append a specified Unit64 type value into the buffer with the specified bit length.
     *
     * @param value  Specify the value which needs to be appended.
     * @param length Specify the bit length which the value will occupy in the buffer.
     */
    public void appendUInt64(long value, int length) {
        byte[] convertedBytes = LittleEndianBitConverter.getBytes(value);
        this.setBytes(convertedBytes, length);
    }

    /**
     * Append a specified Unit32 type value into the buffer with the specified bit length.
     *
     * @param value  Specify the value which needs to be appended.
     * @param length Specify the bit length which the value will occupy in the buffer.
     */
    public void appendUInit32(int value, int length) {
        byte[] convertedBytes = LittleEndianBitConverter.getBytes(value);
        this.setBytes(convertedBytes, length);
    }

    /**
     * Append a specified Init32 type value into the buffer with the specified bit length.
     *
     * @param value  Specify the value which needs to be appended.
     * @param length Specify the bit length which the value will occupy in the buffer.
     */
    public void appendInit32(int value, int length) {
        byte[] convertedBytes = LittleEndianBitConverter.getBytes(value);
        this.setBytes(convertedBytes, length);
    }

    /**
     * Append a specified GUID value into the buffer.
     *
     * @param value Specify the GUID value.
     */
    public void appendGUID(UUID value) {
        this.setBytes(UuidUtils.asBytes(value), 128);
    }

    /**
     * Write the specified byte array into the buffer from the current position with the specified bit length.
     *
     * @param needWrittenBytes Specify the needed written byte array.
     * @param length           Specify the bit length which the byte array will occupy in the buffer.
     */
    private void setBytes(byte[] needWrittenBytes, int length) {
        for (int i = 0; i < length; i++) {
            if (Bit.isBitSet(needWrittenBytes, i)) {
                bitSet.set(this.bitOffset++);
            } else {
                bitSet.clear(this.bitOffset++);
            }
        }
    }
}
