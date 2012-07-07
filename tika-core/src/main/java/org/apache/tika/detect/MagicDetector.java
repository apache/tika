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
package org.apache.tika.detect;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Content type detection based on magic bytes, i.e. type-specific patterns
 * near the beginning of the document input stream.
 *
 * @since Apache Tika 0.3
 */
public class MagicDetector implements Detector {

    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    public static MagicDetector parse(
            MediaType mediaType,
            String type, String offset, String value, String mask) {
        int start = 0;
        int end = 0;
        if (offset != null) {
            int colon = offset.indexOf(':');
            if (colon == -1) {
                start = Integer.parseInt(offset);
                end = start;
            } else {
                start = Integer.parseInt(offset.substring(0, colon));
                end = Integer.parseInt(offset.substring(colon + 1));
            }
        }

        byte[] patternBytes = decodeValue(value, type);
        byte[] maskBytes = null;
        if (mask != null) {
            maskBytes = decodeValue(mask, type);
        }

        return new MagicDetector(
                mediaType, patternBytes, maskBytes,
                type.equals("regex"), start, end);
    }

    private static byte[] decodeValue(String value, String type) {
        // Preliminary check
        if ((value == null) || (type == null)) {
            return null;
        }

        byte[] decoded = null;
        String tmpVal = null;
        int radix = 8;

        // hex
        if (value.startsWith("0x")) {
            tmpVal = value.substring(2);
            radix = 16;
        } else {
            tmpVal = value;
            radix = 8;
        }

        if (type.equals("string")
                || type.equals("regex")
                || type.equals("unicodeLE")
                || type.equals("unicodeBE")) {
            decoded = decodeString(value, type);
        } else if (type.equals("byte")) {
            decoded = tmpVal.getBytes();
        } else if (type.equals("host16") || type.equals("little16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i & 0x00FF), (byte) (i >> 8) };
        } else if (type.equals("big16")) {
            int i = Integer.parseInt(tmpVal, radix);
            decoded = new byte[] { (byte) (i >> 8), (byte) (i & 0x00FF) };
        } else if (type.equals("host32") || type.equals("little32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] {
                    (byte) ((i & 0x000000FF)),
                    (byte) ((i & 0x0000FF00) >> 8),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0xFF000000) >> 24) };
        } else if (type.equals("big32")) {
            long i = Long.parseLong(tmpVal, radix);
            decoded = new byte[] {
                    (byte) ((i & 0xFF000000) >> 24),
                    (byte) ((i & 0x00FF0000) >> 16),
                    (byte) ((i & 0x0000FF00) >> 8),
                    (byte) ((i & 0x000000FF)) };
        }
        return decoded;
    }

    private static byte[] decodeString(String value, String type) {
        if (value.startsWith("0x")) {
            byte[] vals = new byte[(value.length() - 2) / 2];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = (byte)
                Integer.parseInt(value.substring(2 + i * 2, 4 + i * 2), 16);
            }
            return vals;
        }

        CharArrayWriter decoded = new CharArrayWriter();

        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\') {
                if (value.charAt(i + 1) == '\\') {
                    decoded.write('\\');
                    i++;
                } else if (value.charAt(i + 1) == 'x') {
                    decoded.write(Integer.parseInt(
                            value.substring(i + 2, i + 4), 16));
                    i += 3;
                } else if (value.charAt(i + 1) == 'r') {
                    decoded.write((int)'\r');
                    i++;
                } else if (value.charAt(i + 1) == 'n') {
                   decoded.write((int)'\n');
                   i++;
                } else {
                    int j = i + 1;
                    while ((j < i + 4) && (j < value.length())
                            && (Character.isDigit(value.charAt(j)))) {
                        j++;
                    }
                    decoded.write(Short.decode(
                            "0" + value.substring(i + 1, j)).byteValue());
                    i = j - 1;
                }
            } else {
                decoded.write(value.charAt(i));
            }
        }

        // Now turn the chars into bytes
        char[] chars = decoded.toCharArray();
        byte[] bytes;
        if ("unicodeLE".equals(type)) {
            bytes = new byte[chars.length * 2];
            for (int i = 0; i < chars.length; i++) {
                bytes[i * 2] = (byte) (chars[i] & 0xff);
                bytes[i * 2 + 1] = (byte) (chars[i] >> 8);
            }
        } else if ("unicodeBE".equals(type)) {
            bytes = new byte[chars.length * 2];
            for(int i = 0; i < chars.length; i++) {
                bytes[i * 2] = (byte) (chars[i] >> 8);
                bytes[i * 2 + 1] = (byte) (chars[i] & 0xff);
            }
        } else {
            // Copy with truncation
            bytes = new byte[chars.length];
            for(int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) chars[i];
            }
        }
        return bytes;
    }

    /**
     * The matching media type. Returned by the
     * {@link #detect(InputStream, Metadata)} method if a match is found.
     */
    private final MediaType type;

    /**
     * Length of the comparison window.
     */
    private final int length;

    /**
     * The magic match pattern. If this byte pattern is equal to the
     * possibly bit-masked bytes from the input stream, then the type
     * detection succeeds and the configured {@link #type} is returned.
     */
    private final byte[] pattern;
    
    /**
     * Length of the pattern, which in the case of regular expressions will
     * not be the same as the comparison window length.
     */
    private final int patternLength;
    
    /**
     * True if pattern is a regular expression, false otherwise.
     */
    private final boolean isRegex;

    /**
     * Bit mask that is applied to the source bytes before pattern matching.
     */
    private final byte[] mask;

    /**
     * First offset (inclusive) of the comparison window within the
     * document input stream. Greater than or equal to zero.
     */
    private final int offsetRangeBegin;

    /**
     * Last offset (inclusive) of the comparison window within the document
     * input stream. Greater than or equal to the
     * {@link #offsetRangeBegin first offset}.
     * <p>
     * Note that this is <em>not</em> the offset of the last byte read from
     * the document stream. Instead, the last window of bytes to be compared
     * starts at this offset.
     */
    private final int offsetRangeEnd;

    /**
     * Creates a detector for input documents that have the exact given byte
     * pattern at the beginning of the document stream.
     *
     * @param type matching media type
     * @param pattern magic match pattern
     */
    public MagicDetector(MediaType type, byte[] pattern) {
        this(type, pattern, 0);
    }

    /**
     * Creates a detector for input documents that have the exact given byte
     * pattern at the given offset of the document stream.
     *
     * @param type matching media type
     * @param pattern magic match pattern
     * @param offset offset of the pattern match
     */
    public MagicDetector(MediaType type, byte[] pattern, int offset) {
        this(type, pattern, null, offset, offset);
    }
    
    /**
     * Creates a detector for input documents that meet the specified magic
     * match.  {@code pattern} must NOT be a regular expression.
     * Constructor maintained for legacy reasons.
     */
    public MagicDetector(
        MediaType type, byte[] pattern, byte[] mask,
        int offsetRangeBegin, int offsetRangeEnd) {
        this(type, pattern, mask, false, offsetRangeBegin, offsetRangeEnd);
    }

    /**
     * Creates a detector for input documents that meet the specified
     * magic match.
     */
    public MagicDetector(
            MediaType type, byte[] pattern, byte[] mask,
            boolean isRegex,
            int offsetRangeBegin, int offsetRangeEnd) {
        if (type == null) {
            throw new IllegalArgumentException("Matching media type is null");
        } else if (pattern == null) {
            throw new IllegalArgumentException("Magic match pattern is null");
        } else if (offsetRangeBegin < 0
                || offsetRangeEnd < offsetRangeBegin) {
            throw new IllegalArgumentException(
                    "Invalid offset range: ["
                    + offsetRangeBegin + "," + offsetRangeEnd + "]");
        }

        this.type = type;

        this.isRegex = isRegex;

        this.patternLength = Math.max(pattern.length, mask != null ? mask.length : 0);

        if (this.isRegex) {
            // 8K buffer should cope with most regex patterns
            this.length = 8 * 1024;
        } else {
            this.length = patternLength;
        }

        this.mask = new byte[this.patternLength];
        this.pattern = new byte[this.patternLength];

        for (int i = 0; i < this.patternLength; i++) {
            if (mask != null && i < mask.length) {
                this.mask[i] = mask[i];
            } else {
                this.mask[i] = -1;
            }

            if (i < pattern.length) {
                this.pattern[i] = (byte) (pattern[i] & this.mask[i]);
            } else {
                this.pattern[i] = 0;
            }
        }

        this.offsetRangeBegin = offsetRangeBegin;
        this.offsetRangeEnd = offsetRangeEnd;
    }

    /**
     * 
     * @param input document input stream, or <code>null</code>
     * @param metadata ignored
     */
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return MediaType.OCTET_STREAM;
        }

        input.mark(offsetRangeEnd + length);
        try {
            int offset = 0;

            // Skip bytes at the beginning, using skip() or read()
            while (offset < offsetRangeBegin) {
                long n = input.skip(offsetRangeBegin - offset);
                if (n > 0) {
                    offset += n;
                } else if (input.read() != -1) {
                    offset += 1;
                } else {
                    return MediaType.OCTET_STREAM;
                }
            }

            // Fill in the comparison window
            byte[] buffer =
                new byte[length + (offsetRangeEnd - offsetRangeBegin)];
            int n = input.read(buffer);
            if (n > 0) {
                offset += n;
            }
            while (n != -1 && offset < offsetRangeEnd + length) {
                int bufferOffset = offset - offsetRangeBegin;
                n = input.read(
                        buffer, bufferOffset, buffer.length - bufferOffset);
                // increment offset - in case not all read (see testDetectStreamReadProblems)
                if (n > 0) {
                    offset += n;
                }
            }

            if (this.isRegex) {
                Pattern p = Pattern.compile(new String(this.pattern));

                ByteBuffer bb = ByteBuffer.wrap(buffer);
                CharBuffer result = ISO_8859_1.decode(bb);
                Matcher m = p.matcher(result);

                boolean match = false;
                // Loop until we've covered the entire offset range
                for (int i = 0; i <= offsetRangeEnd - offsetRangeBegin; i++) {
                    m.region(i,  length+i);
                    match = m.lookingAt(); // match regex from start of region
                    if (match) {
                        return type;
                    }
                }
            } else {
                if (offset < offsetRangeBegin + length) {
                    return MediaType.OCTET_STREAM;
                }
                // Loop until we've covered the entire offset range
                for (int i = 0; i <= offsetRangeEnd - offsetRangeBegin; i++) {
                    boolean match = true;
                    for (int j = 0; match && j < length; j++) {
                        match = (buffer[i + j] & mask[j]) == pattern[j];
                    }
                    if (match) {
                        return type;
                    }
                }
            }

            return MediaType.OCTET_STREAM;
        } finally {
            input.reset();
        }
    }

    public int getLength() {
        return this.patternLength;
    }

    /**
     * Returns a string representation of the Detection Rule.
     * Should sort nicely by type and details, as we sometimes
     *  compare these.
     */
    public String toString() {
        // Needs to be unique, as these get compared.
        return "Magic Detection for " + type +
                " looking for " + pattern.length + 
                " bytes = " + this.pattern + 
                " mask = " + this.mask;
    }
}
