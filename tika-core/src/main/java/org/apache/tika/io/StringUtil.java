/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.io;

import java.nio.charset.Charset;

/**
 * General String Related Utilities.
 * <p>
 * This class provides static utility methods for string operations
 * <p>
 * Origin of code: Based on the version in POI
 */
public class StringUtil {
    
    protected static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    protected static final Charset UTF16LE = Charset.forName("UTF-16LE");
    public static final Charset UTF8 = Charset.forName("UTF-8");
    
    private StringUtil() {
        // no instances of this class
    }

    /**
     *  Given a byte array of 16-bit unicode characters in Little Endian
     *  format (most important byte last), return a Java String representation
     *  of it.
     *
     * { 0x16, 0x00 } -0x16
     *
     * @param  string  the byte array to be converted
     * @param  offset  the initial offset into the
     *                 byte array. it is assumed that string[ offset ] and string[ offset +
     *                 1 ] contain the first 16-bit unicode character
     * @param len the length of the final string
     * @return the converted string, never <code>null</code>.
     * @exception  ArrayIndexOutOfBoundsException  if offset is out of bounds for
     *      the byte array (i.e., is negative or is greater than or equal to
     *      string.length)
     * @exception  IllegalArgumentException        if len is too large (i.e.,
     *      there is not enough data in string to create a String of that
     *      length)
     */
    public static String getFromUnicodeLE(
            final byte[] string,
            final int offset,
            final int len)
            throws ArrayIndexOutOfBoundsException, IllegalArgumentException {
        if ((offset < 0) || (offset >= string.length)) {
            throw new ArrayIndexOutOfBoundsException("Illegal offset " + offset + " (String data is of length " + string.length + ")");
        }
        if ((len < 0) || (((string.length - offset) / 2) < len)) {
            throw new IllegalArgumentException("Illegal length " + len);
        }

        return new String(string, offset, len * 2, UTF16LE);
    }
    
    /**
     *  Given a byte array of 16-bit unicode characters in little endian
     *  format (most important byte last), return a Java String representation
     *  of it.
     *
     * { 0x16, 0x00 } -0x16
     *
     * @param  string  the byte array to be converted
     * @return the converted string, never <code>null</code>
     */
    public static String getFromUnicodeLE(byte[] string) {
        if(string.length == 0) { return ""; }
        return getFromUnicodeLE(string, 0, string.length / 2);
    }
    
    /**
     * Read 8 bit data (in ISO-8859-1 codepage) into a (unicode) Java
     * String and return.
     * (In Excel terms, read compressed 8 bit unicode as a string)
     *
     * @param string byte array to read
     * @param offset offset to read byte array
     * @param len    length to read byte array
     * @return String generated String instance by reading byte array
     */
    public static String getFromCompressedUnicode(
            final byte[] string,
            final int offset,
            final int len) {
        int len_to_use = Math.min(len, string.length - offset);
        return new String(string, offset, len_to_use, ISO_8859_1);
    }
    
    /**
     * Takes a unicode (java) string, and returns it as 8 bit data (in ISO-8859-1
     * codepage).
     * (In Excel terms, write compressed 8 bit unicode)
     *
     * @param  input   the String containing the data to be written
     * @param  output  the byte array to which the data is to be written
     * @param  offset  an offset into the byte arrat at which the data is start
     *      when written
     */
    public static void putCompressedUnicode(String input, byte[] output, int offset) {
        byte[] bytes = input.getBytes(ISO_8859_1);
        System.arraycopy(bytes, 0, output, offset, bytes.length);
    }

}
