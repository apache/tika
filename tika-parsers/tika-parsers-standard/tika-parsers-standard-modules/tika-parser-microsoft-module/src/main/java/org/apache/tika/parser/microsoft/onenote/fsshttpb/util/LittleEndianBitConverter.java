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


/**
 * Implement a converter which converts to/from little-endian byte arrays
 */
public class LittleEndianBitConverter {
    /**
     * Prevents a default instance of the LittleEndianBitConverter class from being created
     */
    private LittleEndianBitConverter() {
    }

    /**
     * Returns a 16-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 16-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static short ToUInt16(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 2);
        return (short) convertFromBytes(array, index, 2);
    }

    /**
     * Returns a 32-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 32-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static int toUInt32(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (int) convertFromBytes(array, index, 4);
    }

    /**
     * Returns a 32-bit signed integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 32-bit signed integer formed by two bytes beginning at startIndex.
     */
    public static int toInt32(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (int) convertFromBytes(array, index, 4);
    }

    /**
     * Returns a 16-bit signed integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 16-bit signed integer formed by two bytes beginning at startIndex.
     */
    public static short toInt16(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (short) convertFromBytes(array, index, 2);
    }

    /**
     * Returns a 64-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 64-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static long toUInt64(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 8);
        return convertFromBytes(array, index, 8);
    }

    /**
     * Returns a value built from the specified number of bytes from the given buffer,
     * starting at index.
     *
     * @param buffer         Specify the data in byte array format
     * @param startIndex     Specify the first index to use
     * @param bytesToConvert Specify the number of bytes to use
     * @return Return the value built from the given bytes
     */

    private static long convertFromBytes(byte[] buffer, int startIndex, int bytesToConvert) {
        long ret = 0;
        int bitCount = 0;
        for (int i = 0; i < bytesToConvert; i++) {
            ret |= Byte.toUnsignedLong(buffer[startIndex + i]) << bitCount;

            bitCount += 8;
        }

        return ret;
    }

    /**
     * This method is used to check the given argument for validity.
     *
     * @param value         Specify the byte array.
     * @param startIndex    Specify the start index.
     * @param bytesRequired Specify the number of bytes.
     */
    private static void checkByteArgument(byte[] value, int startIndex, int bytesRequired) throws IOException {
        if (value == null) {
            throw new IOException("value must be non-null");
        }

        if (startIndex < 0) {
            throw new IOException("The index cannot be less than 0.");
        }

        if (startIndex > value.length - bytesRequired) {
            throw new IOException(
                    "startIndex " + startIndex + " is less than value.length (" + value.length +
                            ") minus bytesRequired (" + bytesRequired + ")");
        }
    }
}
