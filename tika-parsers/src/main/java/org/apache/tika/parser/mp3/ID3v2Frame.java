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
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import org.apache.tika.parser.mp3.ID3Tags.ID3Comment;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

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
     * Returns the next ID3v2 Frame in
     *  the file, or null if the next batch of data
     *  doesn't correspond to either an ID3v2 header.
     * If no ID3v2 frame could be detected and the passed in input stream is a
     * {@code PushbackInputStream}, the bytes read so far are pushed back so
     * that they can be read again.
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
     * @param inp the input stream
     * @param bytes the bytes to be pushed back
     * @throws IOException if an error occurs
     */
    private static void pushBack(InputStream inp, int... bytes)
            throws IOException
    {
        if (inp instanceof PushbackInputStream)
        {
            byte[] buf = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++)
            {
                buf[i] = (byte) bytes[i];
            }
            ((PushbackInputStream) inp).unread(buf);
        }
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

        // Get the frame's data, or at least as much
        //  of it as we could do
        data = readFully(inp, length, false);
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
       return readFully(inp, length, true);
    }
    protected static byte[] readFully(InputStream inp, int length, boolean shortDataIsFatal)
            throws IOException {
        byte[] b = new byte[length];

        int pos = 0;
        int read;
        while (pos < length) {
            read = inp.read(b, pos, length-pos);
            if (read == -1) {
                if(shortDataIsFatal) {
                   throw new IOException("Tried to read " + length + " bytes, but only " + pos + " bytes present");
                } else {
                   // Give them what we found
                   // TODO Log the short read
                   return b;
                }
            }
            pos += read;
        }

        return b;
    }
    
    protected static class TextEncoding {
       public final boolean doubleByte;
       public final String encoding;
       private TextEncoding(String encoding, boolean doubleByte) {
          this.doubleByte = doubleByte;
          this.encoding = encoding;
       }
    }
    protected static final TextEncoding[] encodings = new TextEncoding[] {
          new TextEncoding("ISO-8859-1", false),
          new TextEncoding("UTF-16", true), // With BOM
          new TextEncoding("UTF-16BE", true), // Without BOM
          new TextEncoding("UTF-8", false)
    };

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
        while (encoding.doubleByte && actualLength >= 2 && data[offset+actualLength-1] == 0 && data[offset+actualLength-2] == 0) {
           actualLength -= 2;
        } 
        while (!encoding.doubleByte && actualLength >= 1 && data[offset+actualLength-1] == 0) {
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
            ((data[offset] == (byte) 0xff && data[offset+1] == (byte) 0xfe) ||
             (data[offset] == (byte) 0xfe && data[offset+1] == (byte) 0xff))) {
          return "";
        }

        try {
            // Build the base string
            return new String(data, offset, actualLength, encoding.encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(
                    "Core encoding " + encoding.encoding + " is not available", e);
        }
    }
    /**
     * Builds up the ID3 comment, by parsing and extracting
     *  the comment string parts from the given data. 
     */
    protected static ID3Comment getComment(byte[] data, int offset, int length) {
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
       String lang = getString(data, offset+1, 3);
       
       // After that we have [Desc]\0(\0)[Text]
       int descStart = offset+4;
       int textStart = -1;
       String description = null;
       String text = null;
       
       // Find where the description ends
       try {
          for (int i=descStart; i<offset+length; i++) {
             if (encoding.doubleByte && data[i]==0 && data[i+1] == 0) {
                // Handle LE vs BE on low byte text
                if (i+2 < offset+length && data[i+1] == 0 && data[i+2] == 0) {
                   i++;
                }
                textStart = i+2;
                description = new String(data, descStart, i-descStart, encoding.encoding);
                break;
             }
             if (!encoding.doubleByte && data[i]==0) {
                textStart = i+1;
                description = new String(data, descStart, i-descStart, encoding.encoding);
                break;
             }
          }
          
          // Did we find the end?
          if (textStart > -1) {
             text = new String(data, textStart, offset+length-textStart, encoding.encoding);
          } else {
             // Assume everything is the text
             text = new String(data, descStart, offset+length-descStart, encoding.encoding);
          }
          
          // Return
          return new ID3Comment(lang, description, text);
       } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(
                  "Core encoding " + encoding.encoding + " is not available", e);
       }
    }

    /**
     * Returns the String at the given
     *  offset and length. Strings are ISO-8859-1 
     */
    protected static String getString(byte[] data, int offset, int length) {
        return new String(data, offset, length, ISO_8859_1);
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
            int copyFrom = offset+nameLength+sizeLength+flagLength;
            size = Math.max(0, Math.min(size, frameData.length-copyFrom)); // TIKA-1218, prevent negative size for malformed files.
            data = new byte[size];
            System.arraycopy(frameData, copyFrom, data, 0, size);
        }

        protected int getSize() {
            return headerSize + data.length;
        }

    }

}
