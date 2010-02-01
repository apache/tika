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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

/**
 * A frame of ID3v2 data, which is then passed to a handler to 
 * be turned into useful data.
 */
public class ID3v2Frame implements MP3Frame {
    private int majorVersion;
    private int minorVersion;
    private int flags;
    private int length;
    /** Excludes the header size part */
    private byte[] extendedHeader;
    private byte[] data;

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

    /**
     * Returns the next Frame (ID3v2 or Audio) in
     *  the file, or null if the next batch of data
     *  doesn't correspond to either an ID3v2 Frame
     *  or an Audio Frame.
     * ID3v2 Frames should come before all Audio ones.
     */
    public static MP3Frame createFrameIfPresent(InputStream inp)
            throws IOException {
        int h1 = inp.read();
        int h2 = inp.read();
        int h3 = inp.read();
        
        // Is it an ID3v2 Frame? 
        if (h1 == (int)'I' && h2 == (int)'D' && h3 == (int)'3') {
            int majorVersion = inp.read();
            int minorVersion = inp.read();
            if (majorVersion == -1 || minorVersion == -1) {
                return null;
            }
            return new ID3v2Frame(majorVersion, minorVersion, inp);
        }
        
        // Is it an Audio Frame?
        int h4 = inp.read();
        if (AudioFrame.isAudioHeader(h1, h2, h3, h4)) {
            return new AudioFrame(h1, h2, h3, h4, inp);
        }

        // Not a frame header
        return null;
    }

    private ID3v2Frame(int majorVersion, int minorVersion, InputStream inp)
            throws IOException {
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

        // Get the frame's data
        data = readFully(inp, length);
    }

    protected static int getInt(byte[] data) {
        return getInt(data, 0);
    }

    protected static int getInt(byte[] data, int offset) {
        int b0 = data[offset+0] & 0xFF;
        int b1 = data[offset+1] & 0xFF;
        int b2 = data[offset+2] & 0xFF;
        int b3 = data[offset+3] & 0xFF;
        return (b0 << 24) + (b1 << 16) + (b2 << 8) + (b3 << 0);
    }

    protected static int getInt3(byte[] data, int offset) {
        int b0 = data[offset+0] & 0xFF;
        int b1 = data[offset+1] & 0xFF;
        int b2 = data[offset+2] & 0xFF;
        return (b0 << 16) + (b1 << 8) + (b2 << 0);
    }

    protected static int getInt2(byte[] data, int offset) {
        int b0 = data[offset+0] & 0xFF;
        int b1 = data[offset+1] & 0xFF;
        return (b0 << 8) + (b1 << 0);
    }

    /**
     * AKA a Synchsafe integer.
     * 4 bytes hold a 28 bit number. The highest
     *  bit in each byte is always 0 and always ignored.
     */
    protected static int get7BitsInt(byte[] data, int offset) {
        int b0 = data[offset+0] & 0x7F;
        int b1 = data[offset+1] & 0x7F;
        int b2 = data[offset+2] & 0x7F;
        int b3 = data[offset+3] & 0x7F;
        return (b0 << 21) + (b1 << 14) + (b2 << 7) + (b3 << 0);
    }

    protected static byte[] readFully(InputStream inp, int length)
            throws IOException {
        byte[] b = new byte[length];

        int pos = 0;
        int read;
        while (pos < length) {
            read = inp.read(b, pos, length-pos);
            if (read == -1) {
                throw new IOException("Tried to read " + length + " bytes, but only " + pos + " bytes present"); 
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
        while (data[actualLength-1] == 0) {
            actualLength--;
        }

        // Does it have an encoding flag?
        // Detect by the first byte being sub 0x20
        String encoding = "ISO-8859-1";
        byte maybeEncodingFlag = data[offset];
        if (maybeEncodingFlag == 0 || maybeEncodingFlag == 1) {
            offset++;
            actualLength--;
            if (maybeEncodingFlag == 1) {
                // With BOM
                encoding = "UTF-16";
            } else if (maybeEncodingFlag == 2) {
                // Without BOM
                encoding = "UTF-16BE";
            } else if (maybeEncodingFlag == 3) {
                encoding = "UTF8";
            }
        }

        try {
            return new String(data, offset, actualLength, encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Core encoding " + encoding + " is not available", e);
        }
    }

    /**
     * Returns the String at the given
     *  offset and length. Strings are ISO-8859-1 
     */
    protected static String getString(byte[] data, int offset, int length) {
        try {
            return new String(data, offset, length, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Core encoding ISO-8859-1 encoding is not available", e);
        }
    }


    /**
     * Iterates over id3v2 raw tags.
     * Create an instance of this that configures the
     *  various length and multipliers.
     */
    protected class RawTagIterator implements Iterator<RawTag> {
        private int nameLength;
        private int sizeLength;
        private int sizeMultiplier;
        private int flagLength;

        private int offset = 0;

        protected RawTagIterator(
                int nameLength, int sizeLength, int sizeMultiplier,
                int flagLength) {
            this.nameLength = nameLength;
            this.sizeLength = sizeLength;
            this.sizeMultiplier = sizeMultiplier;
            this.flagLength = flagLength;
        }

        public boolean hasNext() {
            // Check for padding at the end
            return offset < data.length && data[offset] != 0;
        }

        public RawTag next() {
            RawTag tag = new RawTag(nameLength, sizeLength, sizeMultiplier,
                    flagLength, data, offset);
            offset += tag.getSize();
            return tag;
        }

        public void remove() {
        }

    }

    protected static class RawTag {
        private int headerSize;
        protected String name;
        protected int flag;
        protected byte[] data;

        private RawTag(
                int nameLength, int sizeLength, int sizeMultiplier,
                int flagLength, byte[] frameData, int offset) {
            headerSize = nameLength + sizeLength + flagLength;

            // Name, normally 3 or 4 bytes
            name = getString(frameData, offset, nameLength);

            // Size
            int rawSize;
            if (sizeLength == 3) {
                rawSize = getInt3(frameData, offset+nameLength);
            } else {
                rawSize = getInt(frameData, offset+nameLength);
            }
            int size = rawSize * sizeMultiplier;

            // Flag
            if (flagLength > 0) {
                if (flagLength == 1) {
                    flag = (int)frameData[offset+nameLength+sizeLength];
                } else {
                    flag = getInt2(frameData, offset+nameLength+sizeLength);
                }
            }

            // Now data
            data = new byte[size];
            System.arraycopy(frameData, 
                    offset+nameLength+sizeLength+flagLength, data, 0, size);
        }

        protected int getSize() {
            return headerSize + data.length;
        }

    }

}
