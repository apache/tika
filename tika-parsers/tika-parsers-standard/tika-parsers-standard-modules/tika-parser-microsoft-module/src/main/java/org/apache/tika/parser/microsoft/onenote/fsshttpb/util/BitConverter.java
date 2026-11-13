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

public class BitConverter {

    public static short toInt16(byte[] bytes, int index) throws IOException {
        checkRange(bytes, index, 2);
        return LittleEndianBitConverter.ToUInt16(bytes, index);
    }

    public static int toInt32(byte[] bytes, int index) throws IOException {
        checkRange(bytes, index, 4);
        return LittleEndianBitConverter.toInt32(bytes, index);
    }

    public static long toUInt32(byte[] bytes, int index) throws IOException {
        checkRange(bytes, index, 4);
        return LittleEndianBitConverter.toUInt32(bytes, index);
    }

    public static long toInt64(byte[] bytes, int index) throws IOException {
        checkRange(bytes, index, 8);
        return LittleEndianBitConverter.toUInt64(bytes, index);
    }

    private static void checkRange(byte[] bytes, int index, int size) throws IOException {
        if (index < 0 || index > bytes.length - size) {
            throw new IOException("Cannot read " + size + " bytes at index " + index +
                    " of a " + bytes.length + "-byte array");
        }
    }
}
