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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BitConverter {
    public static byte[] getBytes(boolean x) {
        return new byte[]{(byte) (x ? 1 : 0)};
    }

    public static byte[] getBytes(char c) {
        return new byte[]{(byte) (c & 0xff), (byte) (c >> 8 & 0xff)};
    }

    public static byte[] getBytes(double x) {
        return getBytes(Double.doubleToRawLongBits(x));
    }

    public static byte[] getBytes(short x) {
        return new byte[]{(byte) (x >>> 8), (byte) x};
    }

    public static byte[] getBytes(int x) {
        return new byte[]{(byte) (x >>> 24), (byte) (x >>> 16), (byte) (x >>> 8), (byte) x};
    }

    public static byte[] getBytes(long x) {
        return new byte[]{(byte) (x >>> 56), (byte) (x >>> 48), (byte) (x >>> 40),
                (byte) (x >>> 32), (byte) (x >>> 24), (byte) (x >>> 16), (byte) (x >>> 8),
                (byte) x};
    }

    public static byte[] getBytes(float x) {
        return getBytes(Float.floatToRawIntBits(x));
    }

    public static byte[] getBytes(String x) {
        return x.getBytes(StandardCharsets.UTF_8);
    }

    public static long doubleToInt64Bits(double x) {
        return Double.doubleToRawLongBits(x);
    }

    public static double int64BitsToDouble(long x) {
        return (double) x;
    }

    public static short toInt16(byte[] bytes, int index) {
        if (bytes.length < 2) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 2 bytes long.");
        }
        byte[] uint16Bytes = Arrays.copyOfRange(bytes, index, index + 2);
        return LittleEndianBitConverter.ToUInt16(uint16Bytes, 0);
    }

    public static int toInt32(byte[] bytes, int index) {
        if (bytes.length < 4) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 4 bytes long.");
        }
        byte[] int32Bytes = Arrays.copyOfRange(bytes, index, index + 4);
        return LittleEndianBitConverter.toInt32(int32Bytes, 0);
    }

    public static long toUInt32(byte[] bytes, int index) {
        byte[] uint32Bytes = Arrays.copyOfRange(bytes, index, index + 4);
        return LittleEndianBitConverter.toUInt32(uint32Bytes, 0);
    }

    public static long toInt64(byte[] bytes, int index) {
        if (bytes.length != 8) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 8 bytes long.");
        }
        byte[] uint64Bytes = Arrays.copyOfRange(bytes, index, index + 8);
        return LittleEndianBitConverter.toUInt64(uint64Bytes, 0);
    }

    public static float toSingle(byte[] bytes, int index) {
        if (bytes.length != 4) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 4 bytes long.");
        }
        return Float.intBitsToFloat(toInt32(bytes, index));
    }

    public static String toString(byte[] bytes) {
        if (bytes == null) {
            throw new RuntimeException("The byte array must have at least 1 byte.");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public boolean toBoolean(byte[] bytes, int index) {
        if (bytes.length != 1) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 1 byte long.");
        }
        return bytes[index] != 0;
    }

    public char toChar(byte[] bytes, int index) {
        if (bytes.length != 2) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 2 bytes long.");
        }
        return (char) ((0xff & bytes[index]) << 8 | (0xff & bytes[index + 1]) << 0);
    }

    public double toDouble(byte[] bytes, int index) {
        if (bytes.length < 8) {
            throw new RuntimeException(
                    "The length of the byte array must be at least 8 bytes long.");
        }
        return Double.longBitsToDouble(toInt64(bytes, index));
    }
}
