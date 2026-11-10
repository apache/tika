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


public class LittleEndianBitConverter {
    private LittleEndianBitConverter() {
    }

    public static short ToUInt16(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 2);
        return (short) convertFromBytes(array, index, 2);
    }

    public static int toUInt32(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (int) convertFromBytes(array, index, 4);
    }

    public static int toInt32(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (int) convertFromBytes(array, index, 4);
    }

    public static short toInt16(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 4);
        return (short) convertFromBytes(array, index, 2);
    }

    public static long toUInt64(byte[] array, int index) throws IOException {
        checkByteArgument(array, index, 8);
        return convertFromBytes(array, index, 8);
    }

    private static long convertFromBytes(byte[] buffer, int startIndex, int bytesToConvert) {
        long ret = 0;
        int bitCount = 0;
        for (int i = 0; i < bytesToConvert; i++) {
            ret |= Byte.toUnsignedLong(buffer[startIndex + i]) << bitCount;

            bitCount += 8;
        }

        return ret;
    }

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
