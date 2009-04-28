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

package org.apache.tika.utils;

// JDK imports
import java.util.HashMap;
import java.nio.charset.Charset;

/**
 * A collection of String processing utility methods.
 */
public class StringUtil {

    /**
     * Returns a copy of <code>s</code> padded with trailing spaces so that
     * it's length is <code>length</code>. Strings already
     * <code>length</code> characters long or longer are not altered.
     */
    public static String rightPad(String s, int length) {
        StringBuffer sb = new StringBuffer(s);
        for (int i = length - s.length(); i > 0; i--)
            sb.append(" ");
        return sb.toString();
    }

    /**
     * Returns a copy of <code>s</code> padded with leading spaces so that
     * it's length is <code>length</code>. Strings already
     * <code>length</code> characters long or longer are not altered.
     */
    public static String leftPad(String s, int length) {
        StringBuffer sb = new StringBuffer();
        for (int i = length - s.length(); i > 0; i--)
            sb.append(" ");
        sb.append(s);
        return sb.toString();
    }

    private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

    /**
     * Convenience call for {@link #toHexString(byte[], String, int)}, where
     * <code>sep = null; lineLen = Integer.MAX_VALUE</code>.
     * 
     * @param buf
     */
    public static String toHexString(byte[] buf) {
        return toHexString(buf, null, Integer.MAX_VALUE);
    }

    /**
     * Get a text representation of a byte[] as hexadecimal String, where each
     * pair of hexadecimal digits corresponds to consecutive bytes in the array.
     * 
     * @param buf
     *            input data
     * @param sep
     *            separate every pair of hexadecimal digits with this separator,
     *            or null if no separation is needed.
     * @param lineLen
     *            break the output String into lines containing output for
     *            lineLen bytes.
     */
    public static String toHexString(byte[] buf, String sep, int lineLen) {
        if (buf == null)
            return null;
        if (lineLen <= 0)
            lineLen = Integer.MAX_VALUE;
        StringBuffer res = new StringBuffer(buf.length * 2);
        for (int i = 0; i < buf.length; i++) {
            int b = buf[i];
            res.append(HEX_DIGITS[(b >> 4) & 0xf]);
            res.append(HEX_DIGITS[b & 0xf]);
            if (i > 0 && (i % lineLen) == 0)
                res.append('\n');
            else if (sep != null && i < lineLen - 1)
                res.append(sep);
        }
        return res.toString();
    }

    /**
     * Convert a String containing consecutive (no inside whitespace)
     * hexadecimal digits into a corresponding byte array. If the number of
     * digits is not even, a '0' will be appended in the front of the String
     * prior to conversion. Leading and trailing whitespace is ignored.
     * 
     * @param text
     *            input text
     * @return converted byte array, or null if unable to convert
     */
    public static byte[] fromHexString(String text) {
        text = text.trim();
        if (text.length() % 2 != 0)
            text = "0" + text;
        int resLen = text.length() / 2;
        int loNibble, hiNibble;
        byte[] res = new byte[resLen];
        for (int i = 0; i < resLen; i++) {
            int j = i << 1;
            hiNibble = charToNibble(text.charAt(j));
            loNibble = charToNibble(text.charAt(j + 1));
            if (loNibble == -1 || hiNibble == -1)
                return null;
            res[i] = (byte) (hiNibble << 4 | loNibble);
        }
        return res;
    }

    private static final int charToNibble(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'a' && c <= 'f') {
            return 0xa + (c - 'a');
        } else if (c >= 'A' && c <= 'F') {
            return 0xA + (c - 'A');
        } else {
            return -1;
        }
    }

    /**
     * Parse the character encoding from the specified content type header. If
     * the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned. <br />
     * This method was copy from org.apache.catalina.util.RequestUtil is
     * licensed under the Apache License, Version 2.0 (the "License").
     * 
     * @param contentType
     *            a content type header
     */
    public static String parseCharacterEncoding(String contentType) {
        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0)
            return (null);
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
            encoding = encoding.substring(0, end);
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
                && (encoding.endsWith("\"")))
            encoding = encoding.substring(1, encoding.length() - 1);
        return (encoding.trim());

    }

    /**
     * Checks if a string is empty (ie is null or empty).
     */
    public static boolean isEmpty(String str) {
        return (str == null) || (str.equals(""));
    }

    private static HashMap encodingAliases = new HashMap();

    /**
     * the following map is not an alias mapping table, but maps character
     * encodings which are often used in mislabelled documents to their correct
     * encodings. For instance, there are a lot of documents labelled
     * 'ISO-8859-1' which contain characters not covered by ISO-8859-1 but
     * covered by windows-1252. Because windows-1252 is a superset of ISO-8859-1
     * (sharing code points for the common part), it's better to treat
     * ISO-8859-1 as synonymous with windows-1252 than to reject, as invalid,
     * documents labelled as ISO-8859-1 that have characters outside ISO-8859-1.
     */
    static {
        encodingAliases.put("ISO-8859-1", "windows-1252");
        encodingAliases.put("EUC-KR", "x-windows-949");
        encodingAliases.put("x-EUC-CN", "GB18030");
        encodingAliases.put("GBK", "GB18030");
        // encodingAliases.put("Big5", "Big5HKSCS");
        // encodingAliases.put("TIS620", "Cp874");
        // encodingAliases.put("ISO-8859-11", "Cp874");

    }

    public static String resolveEncodingAlias(String encoding) {
        if (!Charset.isSupported(encoding))
            return null;
        String canonicalName = new String(Charset.forName(encoding).name());
        return encodingAliases.containsKey(canonicalName) ? (String) encodingAliases
                .get(canonicalName)
                : canonicalName;
    }

    public static void main(String[] args) {
        if (args.length != 1)
            System.out.println("Usage: StringUtil <encoding name>");
        else
            System.out.println(args[0] + " is resolved to "
                    + resolveEncodingAlias(args[0]));
    }
}
