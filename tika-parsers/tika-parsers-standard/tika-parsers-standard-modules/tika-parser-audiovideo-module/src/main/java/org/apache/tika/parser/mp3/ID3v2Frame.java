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
package org.apache.tika.parser.mp3;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Iterator;

import org.apache.tika.parser.mp3.ID3Tags.ID3Comment;
import org.apache.tika.parser.mp3.ID3Tags.ID3Picture;

/**
 * A frame of ID3v2 data, which is then passed to a handler to
 * be turned into useful data.
 */
public class ID3v2Frame implements MP3Frame {

    protected static final TextEncoding[] encodings =
            new TextEncoding[]{new TextEncoding("ISO-8859-1", false),
                    new TextEncoding("UTF-16", true), // With BOM
                    new TextEncoding("UTF-16BE", true), // Without BOM
                    new TextEncoding("UTF-8", false)};
    private static int MAX_RECORD_SIZE = 50_000_000;
    private int majorVersion;
    private int minorVersion;
    private int flags;
    private int length;
    /**
     * Excludes the header size part
     */
    private byte[] extendedHeader;
    private byte[] data;

    private ID3v2Frame(int majorVersion, int minorVersion, InputStream inp) throws IOException {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;

        // Get the flags and the length
        flags = inp.read();
        length = get7BitsInt(readFully(inp, 4), 0);

        // Do we have an extended header?
        if ((flags & 0x02) == 0x02) {
            int size = getInt(readFully(inp, 4));
            extendedHeader = readFully(inp, size);
        }

        // Get the frame's data, or at least as much
        //  of it as we could do
        data = readFully(inp, length, false);
    }

    public static void setMaxRecordSize(int maxRecordSize) {
        MAX_RECORD_SIZE = maxRecordSize;
    }

    /**
     * Returns the next ID3v2 Frame in
     * the file, or null if the next batch of data
     * doesn't correspond to either an ID3v2 header.
     * If no ID3v2 frame could be detected and the passed in input stream is a
     * {@code PushbackInputStream}, the bytes read so far are pushed back so
     * that they can be read again.
     * ID3v2 Frames should come before all Audio ones.
     */
    public static MP3Frame createFrameIfPresent(InputStream inp) throws IOException {
        int h1 = inp.read();
        int h2 = inp.read();
        int h3 = inp.read();

        // Is it an ID3v2 Frame?
        if (h1 == (int) 'I' && h2 == (int) 'D' && h3 == (int) '3') {
            int majorVersion = inp.read();
            int minorVersion = inp.read();
            if (majorVersion == -1 || minorVersion == -1) {
                pushBack(inp, h1, h2, h3, majorVersion, minorVersion);
                return null;
            }
            return new ID3v2Frame(majorVersion, minorVersion, inp);
        }

        // Not a frame header
        pushBack(inp, h1, h2, h3);
        return null;
    }

    /**
     * Pushes bytes back into the stream if possible. This method is called if
     * no ID3v2 header could be found at the current stream position.
     *
     * @param inp   the input stream
     * @param bytes the bytes to be pushed back
     * @throws IOException if an error occurs
     */
    private static void pushBack(InputStream inp, int... bytes) throws IOException {
        if (inp instanceof PushbackInputStream) {
            byte[] buf = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                buf[i] = (byte) bytes[i];
            }
            ((PushbackInputStream) inp).unread(buf);
        }
    }

    protected static int getInt(byte[] data) {
        return getInt(data, 0);
    }

    protected static int getInt(byte[] data, int offset) {
        int b0 = data[offset + 0] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return (b0 << 24) + (b1 << 16) + (b2 << 8) + (b3);
    }

    protected static int getInt3(byte[] data, int offset) {
        int b0 = data[offset + 0] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        return (b0 << 16) + (b1 << 8) + (b2);
    }

    protected static int getInt2(byte[] data, int offset) {
        int b0 = data[offset + 0] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return (b0 << 8) + (b1);
    }

    /**
     * AKA a Synchsafe integer.
     * 4 bytes hold a 28 bit number. The highest
     * bit in each byte is always 0 and always ignored.
     */
    protected static int get7BitsInt(byte[] data, int offset) {
        int b0 = data[offset + 0] & 0x7F;
        int b1 = data[offset + 1] & 0x7F;
        int b2 = data[offset + 2] & 0x7F;
        int b3 = data[offset + 3] & 0x7F;
        return (b0 << 21) + (b1 << 14) + (b2 << 7) + (b3);
    }

    protected static byte[] readFully(InputStream inp, int length) throws IOException {
        return readFully(inp, length, true);
    }

    protected static byte[] readFully(InputStream inp, int length, boolean shortDataIsFatal)
            throws IOException {
        if (MAX_RECORD_SIZE > 0 && length > MAX_RECORD_SIZE) {
            throw new IOException(
                    "Record size (" + length + " bytes) is larger than the allowed record size: " +
                            MAX_RECORD_SIZE);
        }
        byte[] b = new byte[length];

        int pos = 0;
        int read;
        while (pos < length) {
            read = inp.read(b, pos, length - pos);
            if (read == -1) {
                if (shortDataIsFatal) {
                    throw new IOException("Tried to read " + length + " bytes, but only " + pos +
                            " bytes present");
                } else {
                    // truncated stream: return only the bytes actually read, not the
                    // zero-padded full-length array, so callers (e.g. cover-art
                    // extraction) don't emit padding as data. TIKA-4812
                    return Arrays.copyOf(b, pos);
                }
            }
            pos += read;
        }

        return b;
    }

    /**
     * Returns the (possibly null padded) String at the given offset and
     * length. String encoding is held in the first byte;
     */
    protected static String getTagString(byte[] data, int offset, int length) {
        int actualLength = length;
        if (actualLength == 0) {
            return "";
        }
        if (actualLength == 1 && data[offset] == 0) {
            return "";
        }

        // Does it have an encoding flag?
        // Detect by the first byte being sub 0x20
        TextEncoding encoding = encodings[0];
        byte maybeEncodingFlag = data[offset];
        if (maybeEncodingFlag >= 0 && maybeEncodingFlag < encodings.length) {
            offset++;
            actualLength--;
            encoding = encodings[maybeEncodingFlag];
        }

        // Trim off null termination / padding (as present)
        while (encoding.doubleByte && actualLength >= 2 && data[offset + actualLength - 1] == 0 &&
                data[offset + actualLength - 2] == 0) {
            actualLength -= 2;
        }
        while (!encoding.doubleByte && actualLength >= 1 && data[offset + actualLength - 1] == 0) {
            actualLength--;
        }
        if (actualLength == 0) {
            return "";
        }

        // TIKA-1024: If it's UTF-16 (with BOM) and all we
        // have is a naked BOM then short-circuit here
        // (return empty string), because new String(..)
        // gives different results on different JVMs
        if (encoding.encoding.equals("UTF-16") && actualLength == 2 &&
                hasBOM(data, offset, actualLength)) {
            return "";
        }

        try {
            // Build the base string
            return decodeText(data, offset, actualLength, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Core encoding " + encoding.encoding + " is not available",
                    e);
        }
    }

    /**
     * Decodes text in the frame's declared encoding. $01 is UTF-16 with a BOM; if a tagger omits
     * it, Java assumes big-endian and turns little-endian text into mojibake ('T' 0x54 0x00 ->
     * U+5400), so recover the byte order from the bytes.
     */
    private static String decodeText(byte[] data, int offset, int length, TextEncoding encoding)
            throws UnsupportedEncodingException {
        String charset = encoding.encoding;
        if ("UTF-16".equals(charset) && !hasBOM(data, offset, length)) {
            charset = guessUTF16ByteOrder(data, offset, length);
        }
        return new String(data, offset, length, charset);
    }

    private static boolean hasBOM(byte[] data, int offset, int length) {
        if (length < 2) {
            return false;
        }
        int first = data[offset] & 0xff;
        int second = data[offset + 1] & 0xff;
        return (first == 0xff && second == 0xfe) || (first == 0xfe && second == 0xff);
    }

    /**
     * Recovers the byte order of BOM-less UTF-16, only reached when a $01 frame omits its
     * mandatory BOM (a malformed tagger). Chars below U+0100, which dominate ID3 tags, carry one
     * NUL per code unit whose column reveals the order. Chars above (eg CJK) carry no NUL and no
     * signal; pure-CJK text then falls back to big-endian - Java's own BOM-less default, so this
     * is never worse than the prior unconditional decode.
     */
    private static String guessUTF16ByteOrder(byte[] data, int offset, int length) {
        int bigEndian = 0;
        int littleEndian = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            if (data[offset + i] == 0) {
                bigEndian++;
            }
            if (data[offset + i + 1] == 0) {
                littleEndian++;
            }
        }
        return littleEndian > bigEndian ? "UTF-16LE" : "UTF-16BE";
    }

    /**
     * Parses the comment parts from the given data, or null if the frame is too short or
     * malformed to hold a comment.
     */
    protected static ID3Comment getComment(byte[] data, int offset, int length) {
        // encoding flag + 3-byte language
        if (length < 4) {
            return null;
        }

        // Comments must have an encoding
        int encodingFlag = data[offset];
        if (encodingFlag >= 0 && encodingFlag < encodings.length) {
            // Good, valid flag
        } else {
            // Invalid string
            return null;
        }

        TextEncoding encoding = encodings[encodingFlag];

        // First is a 3 byte language
        String lang = getString(data, offset + 1, 3);

        // After that we have [Desc]\0(\0)[Text]
        int end = offset + length;
        int descStart = offset + 4;
        int textStart = -1;
        String description = null;
        String text = null;

        // Find where the description ends
        try {
            for (int i = descStart; i < end; i++) {
                // a double byte terminator needs both bytes present
                if (encoding.doubleByte && i + 1 < end && data[i] == 0 && data[i + 1] == 0) {
                    // Handle LE vs BE on low byte text
                    if (i + 2 < end && data[i + 2] == 0) {
                        i++;
                    }
                    textStart = i + 2;
                    description = decodeText(data, descStart, i - descStart, encoding);
                    break;
                }
                if (!encoding.doubleByte && data[i] == 0) {
                    textStart = i + 1;
                    description = decodeText(data, descStart, i - descStart, encoding);
                    break;
                }
            }

            // Did we find the end?
            if (textStart > -1) {
                text = decodeText(data, textStart, end - textStart, encoding);
            } else {
                // Assume everything is the text
                text = decodeText(data, descStart, end - descStart, encoding);
            }

            // Return
            return new ID3Comment(lang, description, text);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Core encoding " + encoding.encoding + " is not available",
                    e);
        }
    }

    /**
     * Parses the picture parts from an ID3v2.3/v2.4 APIC frame, or null
     * if the frame is too short or malformed to hold a picture.
     */
    protected static ID3Picture getPicture(byte[] data, int offset, int length) {
        // encoding flag + empty mime terminator + picture type
        if (length < 3) {
            return null;
        }

        // Pictures must have an encoding
        int encodingFlag = data[offset];
        if (encodingFlag < 0 || encodingFlag >= encodings.length) {
            // Invalid picture
            return null;
        }
        TextEncoding encoding = encodings[encodingFlag];

        int end = offset + length;

        // First is the mime type, always ISO-8859-1 and null terminated
        int mimeStart = offset + 1;
        int mimeEnd = -1;
        for (int i = mimeStart; i < end; i++) {
            if (data[i] == 0) {
                mimeEnd = i;
                break;
            }
        }
        if (mimeEnd == -1 || mimeEnd + 1 >= end) {
            return null;
        }
        String mimeType = getString(data, mimeStart, mimeEnd - mimeStart);
        if (mimeType.isEmpty()) {
            // Leave the type for auto-detection
            mimeType = null;
        }

        // Then one byte of picture type
        int pictureType = data[mimeEnd + 1] & 0xFF;

        // Then the description and the picture data
        return getPictureWithDescription(data, mimeEnd + 2, end, encoding, mimeType, pictureType);
    }

    /**
     * Parses the picture parts from an ID3v2.2 PIC frame, which declares
     * a three letter image format instead of a mime type. Linked pictures
     * (format "-->") are skipped, they hold a URL rather than image data.
     */
    protected static ID3Picture getV22Picture(byte[] data, int offset, int length) {
        // encoding flag + 3 byte image format + picture type
        if (length < 5) {
            return null;
        }

        // Pictures must have an encoding
        int encodingFlag = data[offset];
        if (encodingFlag < 0 || encodingFlag >= encodings.length) {
            // Invalid picture
            return null;
        }
        TextEncoding encoding = encodings[encodingFlag];

        String format = getString(data, offset + 1, 3);
        String mimeType;
        if ("PNG".equals(format)) {
            mimeType = "image/png";
        } else if ("JPG".equals(format)) {
            mimeType = "image/jpeg";
        } else if ("-->".equals(format)) {
            // A link to a picture, not an embedded one
            return null;
        } else {
            // Leave the type for auto-detection
            mimeType = null;
        }

        // Then one byte of picture type
        int pictureType = data[offset + 4] & 0xFF;

        // Then the description and the picture data
        return getPictureWithDescription(data, offset + 5, offset + length, encoding, mimeType,
                pictureType);
    }

    /**
     * Reads a picture frame's description, terminated per the text encoding,
     * and the picture data following it. Returns null when the terminator or
     * the picture data is missing.
     */
    private static ID3Picture getPictureWithDescription(byte[] data, int descStart, int end,
                                                        TextEncoding encoding, String mimeType,
                                                        int pictureType) {
        int dataStart = -1;
        String description = null;
        try {
            if (encoding.doubleByte) {
                // a double byte terminator needs both bytes present, and sits
                // on a two byte boundary relative to the description start
                for (int i = descStart; i + 1 < end; i += 2) {
                    if (data[i] == 0 && data[i + 1] == 0) {
                        description = decodeText(data, descStart, i - descStart, encoding);
                        dataStart = i + 2;
                        break;
                    }
                }
            } else {
                for (int i = descStart; i < end; i++) {
                    if (data[i] == 0) {
                        description = decodeText(data, descStart, i - descStart, encoding);
                        dataStart = i + 1;
                        break;
                    }
                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Core encoding " + encoding.encoding + " is not available",
                    e);
        }

        // Without a terminated description there is no picture data
        if (dataStart == -1 || dataStart >= end) {
            return null;
        }

        byte[] picture = new byte[end - dataStart];
        System.arraycopy(data, dataStart, picture, 0, picture.length);
        return new ID3Picture(mimeType, description, pictureType, picture);
    }

    /**
     * Returns the String at the given
     * offset and length. Strings are ISO-8859-1
     */
    protected static String getString(byte[] data, int offset, int length) {
        return new String(data, offset, length, ISO_8859_1);
    }

    public static int getMaxRecordSize() {
        return MAX_RECORD_SIZE;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int getFlags() {
        return flags;
    }

    public int getLength() {
        return length;
    }

    public byte[] getExtendedHeader() {
        return extendedHeader;
    }

    public byte[] getData() {
        return data;
    }

    protected static class TextEncoding {
        public final boolean doubleByte;
        public final String encoding;

        private TextEncoding(String encoding, boolean doubleByte) {
            this.doubleByte = doubleByte;
            this.encoding = encoding;
        }
    }

    protected static class RawTag {
        protected String name;
        protected int flag;
        protected byte[] data;
        private int headerSize;

        private RawTag(int nameLength, int sizeLength, int sizeMultiplier, int flagLength,
                       boolean synchsafeSize, byte[] frameData, int offset) {
            headerSize = nameLength + sizeLength + flagLength;

            // Name, normally 3 or 4 bytes
            name = getString(frameData, offset, nameLength);

            // Size
            int rawSize;
            if (sizeLength == 3) {
                rawSize = getInt3(frameData, offset + nameLength);
            } else if (synchsafeSize) {
                rawSize = getV24FrameSize(frameData, offset, headerSize, nameLength);
            } else {
                rawSize = getInt(frameData, offset + nameLength);
            }
            int size = rawSize * sizeMultiplier;

            // Flag
            if (flagLength > 0) {
                if (flagLength == 1) {
                    flag = (int) frameData[offset + nameLength + sizeLength];
                } else {
                    flag = getInt2(frameData, offset + nameLength + sizeLength);
                }
            }

            // Now data
            int copyFrom = offset + nameLength + sizeLength + flagLength;
            size = Math.max(0, Math.min(size, frameData.length -
                    copyFrom)); // TIKA-1218, prevent negative size for malformed files.
            data = new byte[size];
            System.arraycopy(frameData, copyFrom, data, 0, size);
        }

        protected int getSize() {
            return headerSize + data.length;
        }

        /**
         * Returns the size of an ID3v2.4 frame. The spec encodes frame
         * sizes as synchsafe integers, but widespread taggers (e.g. older
         * iTunes) wrote plain integers instead. Reading a synchsafe size
         * as a plain integer (or the other way around) makes the frame
         * walk skip into the middle of the following frames, losing them,
         * so when the two readings disagree, pick the one that lands the
         * walk on a plausible next frame.
         */
        private static int getV24FrameSize(byte[] frameData, int offset, int headerSize,
                                           int nameLength) {
            int plain = getInt(frameData, offset + nameLength);
            // A size byte with the high bit set cannot be synchsafe
            if (((frameData[offset + nameLength] | frameData[offset + nameLength + 1] |
                    frameData[offset + nameLength + 2] | frameData[offset + nameLength + 3]) &
                    0x80) != 0) {
                return plain;
            }
            int synchsafe = get7BitsInt(frameData, offset + nameLength);
            if (synchsafe == plain) {
                return synchsafe;
            }
            if (isPlausibleFrameStart(frameData, offset + headerSize + synchsafe)) {
                return synchsafe;
            }
            if (isPlausibleFrameStart(frameData, offset + headerSize + plain)) {
                return plain;
            }
            // Neither reading looks right, go with the spec
            return synchsafe;
        }

        /**
         * Checks whether the given offset is a plausible place for the
         * next frame to start: the end of the tag, padding, or a frame id
         * made of capital letters and digits.
         */
        private static boolean isPlausibleFrameStart(byte[] frameData, int nextOffset) {
            if (nextOffset < 0 || nextOffset > frameData.length) {
                return false;
            }
            if (nextOffset == frameData.length) {
                return true;
            }
            if (frameData[nextOffset] == 0) {
                // Padding
                return true;
            }
            if (nextOffset + 4 > frameData.length) {
                return false;
            }
            for (int i = nextOffset; i < nextOffset + 4; i++) {
                byte b = frameData[i];
                if (!((b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9'))) {
                    return false;
                }
            }
            return true;
        }

    }

    /**
     * Iterates over id3v2 raw tags.
     * Create an instance of this that configures the
     * various length and multipliers.
     */
    protected class RawTagIterator implements Iterator<RawTag> {
        private int nameLength;
        private int sizeLength;
        private int sizeMultiplier;
        private int flagLength;
        private boolean synchsafeSize;

        private int offset = 0;

        protected RawTagIterator(int nameLength, int sizeLength, int sizeMultiplier,
                                 int flagLength) {
            this(nameLength, sizeLength, sizeMultiplier, flagLength, false);
        }

        protected RawTagIterator(int nameLength, int sizeLength, int sizeMultiplier,
                                 int flagLength, boolean synchsafeSize) {
            this.nameLength = nameLength;
            this.sizeLength = sizeLength;
            this.sizeMultiplier = sizeMultiplier;
            this.flagLength = flagLength;
            this.synchsafeSize = synchsafeSize;
        }

        public boolean hasNext() {
            // Stop at padding, and at a truncated tail too short for a full frame
            // header: the RawTag constructor reads the header bytes unconditionally,
            // so without the data.length no longer being zero-padded (TIKA-4812) a
            // partial header would throw ArrayIndexOutOfBoundsException.
            return offset + nameLength + sizeLength + flagLength <= data.length
                    && data[offset] != 0;
        }

        public RawTag next() {
            RawTag tag = new RawTag(nameLength, sizeLength, sizeMultiplier, flagLength,
                    synchsafeSize, data, offset);
            offset += tag.getSize();
            return tag;
        }

        public void remove() {
        }

    }

}
