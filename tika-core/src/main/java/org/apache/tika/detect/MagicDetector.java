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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Content type detection based on magic bytes, i.e. type-specific patterns
 * near the beginning of the document input stream.
 * <p>
 * Because this works on bytes, not characters, by default any string
 * matching is done as ISO_8859_1. To use an explicit different
 * encoding, supply a type other than "string" / "stringignorecase"
 *
 * @since Apache Tika 0.3
 */
@TikaComponent(spi = false)
public class MagicDetector implements Detector {

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
     * True if we're doing a case-insensitive string match, false otherwise.
     */
    private final boolean isStringIgnoreCase;
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
     * @param type    matching media type
     * @param pattern magic match pattern
     */
    public MagicDetector(MediaType type, byte[] pattern) {
        this(type, pattern, 0);
    }

    /**
     * Creates a detector for input documents that have the exact given byte
     * pattern at the given offset of the document stream.
     *
     * @param type    matching media type
     * @param pattern magic match pattern
     * @param offset  offset of the pattern match
     */
    public MagicDetector(MediaType type, byte[] pattern, int offset) {
        this(type, pattern, null, offset, offset);
    }

    /**
     * Creates a detector for input documents that meet the specified magic
     * match.  {@code pattern} must NOT be a regular expression.
     * Constructor maintained for legacy reasons.
     */
    public MagicDetector(MediaType type, byte[] pattern, byte[] mask, int offsetRangeBegin,
                         int offsetRangeEnd) {
        this(type, pattern, mask, false, offsetRangeBegin, offsetRangeEnd);
    }

    /**
     * Creates a detector for input documents that meet the specified
     * magic match.
     */
    public MagicDetector(MediaType type, byte[] pattern, byte[] mask, boolean isRegex,
                         int offsetRangeBegin, int offsetRangeEnd) {
        this(type, pattern, mask, isRegex, false, offsetRangeBegin, offsetRangeEnd);
    }

    /**
     * Creates a detector for input documents that meet the specified
     * magic match.
     */
    public MagicDetector(MediaType type, byte[] pattern, byte[] mask, boolean isRegex,
                         boolean isStringIgnoreCase, int offsetRangeBegin, int offsetRangeEnd) {
        if (type == null) {
            throw new IllegalArgumentException("Matching media type is null");
        } else if (pattern == null) {
            throw new IllegalArgumentException("Magic match pattern is null");
        } else if (offsetRangeBegin < 0 || offsetRangeEnd < offsetRangeBegin) {
            throw new IllegalArgumentException(
                    "Invalid offset range: [" + offsetRangeBegin + "," + offsetRangeEnd + "]");
        }

        this.type = type;

        this.isRegex = isRegex;
        this.isStringIgnoreCase = isStringIgnoreCase;

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

    public static MagicDetector parse(MediaType mediaType, String type, String offset, String value,
                                      String mask) {
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

        return new MagicDetector(mediaType, patternBytes, maskBytes, type.equals("regex"),
                type.equals("stringignorecase"), start, end);
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

        switch (type) {
            case "string":
            case "regex":
            case "unicodeLE":
            case "unicodeBE":
                decoded = decodeString(value, type);
                break;
            case "stringignorecase":
                decoded = decodeString(value.toLowerCase(Locale.ROOT), type);
                break;
            case "byte":
                decoded = tmpVal.getBytes(UTF_8);
                break;
            case "host16":
            case "little16": {
                int i = Integer.parseInt(tmpVal, radix);
                decoded = new byte[]{(byte) (i & 0x00FF), (byte) (i >> 8)};
                break;
            }
            case "big16": {
                int i = Integer.parseInt(tmpVal, radix);
                decoded = new byte[]{(byte) (i >> 8), (byte) (i & 0x00FF)};
                break;
            }
            case "host32":
            case "little32": {
                long i = Long.parseLong(tmpVal, radix);
                decoded = new byte[]{(byte) ((i & 0x000000FF)), (byte) ((i & 0x0000FF00) >> 8),
                        (byte) ((i & 0x00FF0000) >> 16), (byte) ((i & 0xFF000000) >> 24)};
                break;
            }
            case "big32": {
                long i = Long.parseLong(tmpVal, radix);
                decoded = new byte[]{(byte) ((i & 0xFF000000) >> 24), (byte) ((i & 0x00FF0000) >> 16),
                        (byte) ((i & 0x0000FF00) >> 8), (byte) ((i & 0x000000FF))};
                break;
            }
        }
        return decoded;
    }

    private static byte[] decodeString(String value, String type) {
        if (value.startsWith("0x")) {
            byte[] vals = new byte[(value.length() - 2) / 2];
            for (int i = 0; i < vals.length; i++) {
                vals[i] = (byte) Integer.parseInt(value.substring(2 + i * 2, 4 + i * 2), 16);
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
                    decoded.write(Integer.parseInt(value.substring(i + 2, i + 4), 16));
                    i += 3;
                } else if (value.charAt(i + 1) == 'r') {
                    decoded.write('\r');
                    i++;
                } else if (value.charAt(i + 1) == 'n') {
                    decoded.write('\n');
                    i++;
                } else {
                    int j = i + 1;
                    while ((j < i + 4) && (j < value.length()) &&
                            (Character.isDigit(value.charAt(j)))) {
                        j++;
                    }
                    decoded.write(Short.decode("0" + value.substring(i + 1, j)).byteValue());
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
            for (int i = 0; i < chars.length; i++) {
                bytes[i * 2] = (byte) (chars[i] >> 8);
                bytes[i * 2 + 1] = (byte) (chars[i] & 0xff);
            }
        } else {
            // Copy with truncation
            bytes = new byte[chars.length];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) chars[i];
            }
        }
        return bytes;
    }

    /**
     * @param tis      document input stream, or <code>null</code>
     * @param metadata ignored
     * @param parseContext the parse context
     */
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        if (tis == null) {
            return MediaType.OCTET_STREAM;
        }

        tis.mark(offsetRangeEnd + length);
        try {
            int offset = 0;

            // Skip bytes at the beginning, using skip() or read()
            while (offset < offsetRangeBegin) {
                long n = tis.skip(offsetRangeBegin - offset);
                if (n > 0) {
                    offset += n;
                } else if (tis.read() != -1) {
                    offset += 1;
                } else {
                    return MediaType.OCTET_STREAM;
                }
            }

            // Fill in the comparison window
            byte[] buffer = new byte[length + (offsetRangeEnd - offsetRangeBegin)];
            int n = tis.read(buffer);
            if (n > 0) {
                offset += n;
            }
            while (n != -1 && offset < offsetRangeEnd + length) {
                int bufferOffset = offset - offsetRangeBegin;
                n = tis.read(buffer, bufferOffset, buffer.length - bufferOffset);
                // increment offset - in case not all read (see testDetectStreamReadProblems)
                if (n > 0) {
                    offset += n;
                }
            }

            // For non-regex, verify we have enough data
            if (!isRegex && offset < offsetRangeBegin + length) {
                return MediaType.OCTET_STREAM;
            }

            // Buffer starts at offsetRangeBegin, so search from 0 to (offsetRangeEnd - offsetRangeBegin)
            if (matchesBuffer(buffer, 0, offsetRangeEnd - offsetRangeBegin)) {
                return type;
            }

            return MediaType.OCTET_STREAM;
        } finally {
            tis.reset();
        }
    }

    public int getLength() {
        return this.patternLength;
    }

    /**
     * Checks if the given byte array matches this magic pattern.
     * This is a more efficient alternative to {@link #detect(TikaInputStream, Metadata, ParseContext)}
     * when you already have the bytes in memory.
     *
     * @param data the byte array to check
     * @return true if the data matches this magic pattern, false otherwise
     */
    public boolean matches(byte[] data) {
        if (data == null) {
            return false;
        }

        // For non-regex, we need at least patternLength bytes starting at offsetRangeBegin.
        // For regex, we can match with less data since the pattern may be shorter than the buffer.
        if (!isRegex) {
            int requiredLength = offsetRangeBegin + length;
            if (data.length < requiredLength) {
                return false;
            }
            // For non-regex, we need enough data after the start position for the pattern
            int maxOffset = Math.min(offsetRangeEnd, data.length - length);
            return matchesBuffer(data, offsetRangeBegin, maxOffset);
        } else {
            // For regex, just need data to reach offsetRangeBegin
            if (data.length <= offsetRangeBegin) {
                return false;
            }
            // For regex, try all positions up to min(offsetRangeEnd, data.length - 1)
            // The regex pattern can match even at the last byte position
            int maxOffset = Math.min(offsetRangeEnd, data.length - 1);
            return matchesBuffer(data, offsetRangeBegin, maxOffset);
        }
    }

    /**
     * Core matching logic that checks if the pattern matches anywhere in the buffer
     * within the specified offset range.
     *
     * @param buffer the byte array to search in
     * @param startOffset the first position in the buffer to start matching (inclusive)
     * @param endOffset the last position in the buffer to start matching (inclusive)
     * @return true if a match is found, false otherwise
     */
    private boolean matchesBuffer(byte[] buffer, int startOffset, int endOffset) {
        if (this.isRegex) {
            int flags = 0;
            if (this.isStringIgnoreCase) {
                flags = Pattern.CASE_INSENSITIVE;
            }

            Pattern p = Pattern.compile(new String(this.pattern, UTF_8), flags);

            int bufferLen = Math.min(buffer.length - startOffset, length + (endOffset - startOffset));
            if (bufferLen <= 0) {
                return false;
            }
            ByteBuffer bb = ByteBuffer.wrap(buffer, startOffset, bufferLen);
            CharBuffer result = ISO_8859_1.decode(bb);
            Matcher m = p.matcher(result);

            // Loop until we've covered the entire offset range
            for (int i = 0; i <= endOffset - startOffset; i++) {
                // For regex, use available data length, not the fixed 8KB buffer size
                int regionEnd = Math.min(length + i, result.length());
                if (i < regionEnd) {
                    m.region(i, regionEnd);
                    if (m.lookingAt()) {
                        return true;
                    }
                }
            }
        } else {
            // Loop until we've covered the entire offset range
            for (int i = startOffset; i <= endOffset; i++) {
                if (i + length > buffer.length) {
                    break;
                }
                boolean match = true;
                int masked;
                for (int j = 0; match && j < length; j++) {
                    masked = (buffer[i + j] & mask[j]);
                    if (this.isStringIgnoreCase) {
                        masked = Character.toLowerCase(masked);
                    }
                    match = (masked == pattern[j]);
                }
                if (match) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns a string representation of the Detection Rule.
     * Should sort nicely by type and details, as we sometimes
     * compare these.
     */
    public String toString() {
        // Needs to be unique, as these get compared.
        return "Magic Detection for " + type + " looking for " + pattern.length + " bytes = " +
                Arrays.toString(this.pattern) + " mask = " + Arrays.toString(this.mask);
    }
}
