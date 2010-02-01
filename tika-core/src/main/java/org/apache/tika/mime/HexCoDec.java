/**
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
package org.apache.tika.mime;

/**
 * 
 * A set of Hex encoding and decoding utility methods.
 * 
 */
public class HexCoDec {

    private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Decode a hex string
     * 
     * @param hexValue
     *            the string of hex characters
     * @return the decode hex string as bytes.
     */
    public static byte[] decode(String hexValue) {
        return decode(hexValue.toCharArray());
    }

    /**
     * Decode an array of hex chars
     * 
     * @param hexChars
     *            an array of hex characters.
     * @return the decode hex chars as bytes.
     */
    public static byte[] decode(char[] hexChars) {
        return decode(hexChars, 0, hexChars.length);
    }

    /**
     * Decode an array of hex chars.
     * 
     * @param hexChars
     *            an array of hex characters.
     * @param startIndex
     *            the index of the first character to decode
     * @param length
     *            the number of characters to decode.
     * @return the decode hex chars as bytes.
     */
    public static byte[] decode(char[] hexChars, int startIndex, int length) {
        if ((length & 1) != 0)
            throw new IllegalArgumentException("Length must be even");

        byte[] result = new byte[length / 2];
        for (int j = 0; j < result.length; j++) {
            result[j] = (byte) (hexCharToNibble(hexChars[startIndex++]) * 16 + hexCharToNibble(hexChars[startIndex++]));
        }
        return result;
    }

    /**
     * Hex encode an array of bytes
     * 
     * @param bites
     *            the array of bytes to encode.
     * @return the array of hex characters.
     */
    public static char[] encode(byte[] bites) {
        return encode(bites, 0, bites.length);
    }

    /**
     * Hex encode an array of bytes
     * 
     * @param bites
     *            the array of bytes to encode.
     * @param startIndex
     *            the index of the first character to encode.
     * @param length
     *            the number of characters to encode.
     * @return the array of hex characters.
     */
    public static char[] encode(byte[] bites, int startIndex, int length) {
        char[] result = new char[length * 2];
        for (int i = 0, j = 0; i < length; i++) {
            int bite = bites[startIndex++] & 0xff;
            result[j++] = HEX_CHARS[bite >> 4];
            result[j++] = HEX_CHARS[bite & 0xf];
        }
        return result;
    }

    /**
     * Internal method to turn a hex char into a nibble.
     */
    private static int hexCharToNibble(char ch) {
        if ((ch >= '0') && (ch <= '9')) {
            return ch - '0';
        } else if ((ch >= 'a') && (ch <= 'f')) {
            return ch - 'a' + 10;
        } else if ((ch >= 'A') && (ch <= 'F')) {
            return ch - 'A' + 10;
        } else {
            throw new IllegalArgumentException("Not a hex char - '" + ch + "'");
        }
    }

}
