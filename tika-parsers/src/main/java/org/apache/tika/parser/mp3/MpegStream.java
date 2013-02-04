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

/**
 * <p>
 * A specialized stream class which can be used to extract single frames of MPEG
 * audio files.
 * </p>
 * <p>
 * Instances of this class are constructed with an underlying stream which
 * should point to an audio file. Read operations are possible in the usual way.
 * However, there are special methods for searching and extracting headers of
 * MPEG frames. Some meta information of frames can be queried.
 * </p>
 */
class MpegStream extends PushbackInputStream
{
    /** Bit rate table for MPEG V1, layer 1. */
    private static final int[] BIT_RATE_MPEG1_L1 = {
            0, 32000, 64000, 96000, 128000, 160000, 192000, 224000, 256000,
            288000, 320000, 352000, 384000, 416000, 448000
    };

    /** Bit rate table for MPEG V1, layer 2. */
    private static final int[] BIT_RATE_MPEG1_L2 = {
            0, 32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000,
            160000, 192000, 224000, 256000, 320000, 384000
    };

    /** Bit rate table for MPEG V1, layer 3. */
    private static final int[] BIT_RATE_MPEG1_L3 = {
            0, 32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000,
            160000, 192000, 224000, 256000, 320000
    };

    /** Bit rate table for MPEG V2/V2.5, layer 1. */
    private static final int[] BIT_RATE_MPEG2_L1 = {
            0, 32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000,
            144000, 160000, 176000, 192000, 224000, 256000
    };

    /** Bit rate table for MPEG V2/V2.5, layer 2 and 3. */
    private static final int[] BIT_RATE_MPEG2_L2 = {
            0, 8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000,
            96000, 112000, 128000, 144000, 160000
    };

    /** Sample rate table for MPEG V1. */
    private static final int[] SAMPLE_RATE_MPEG1 = {
            44100, 48000, 32000
    };

    /** Sample rate table for MPEG V2. */
    private static final int[] SAMPLE_RATE_MPEG2 = {
            22050, 24000, 16000
    };

    /** Sample rate table for MPEG V2.5. */
    private static final int[] SAMPLE_RATE_MPEG2_5 = {
            11025, 12000, 8000
    };

    /** Sample rate table for all MPEG versions. */
    private static final int[][] SAMPLE_RATE = createSampleRateTable();

    /** Constant for the number of samples for a layer 1 frame. */
    private static final int SAMPLE_COUNT_L1 = 384;

    /** Constant for the number of samples for a layer 2 or 3 frame. */
    private static final int SAMPLE_COUNT_L2 = 1152;

    /** Constant for the size of an MPEG frame header in bytes. */
    private static final int HEADER_SIZE = 4;

    /** The current MPEG header. */
    private AudioFrame currentHeader;

    /** A flag whether the end of the stream is reached. */
    private boolean endOfStream;

    /**
     * Creates a new instance of {@code MpegStream} and initializes it with the
     * underlying stream.
     * 
     * @param in the underlying audio stream
     */
    public MpegStream(InputStream in)
    {
        super(in, 2 * HEADER_SIZE);
    }

    /**
     * Searches for the next MPEG frame header from the current stream position
     * on. This method advances the underlying input stream until it finds a
     * valid frame header or the end of the stream is reached. In the former
     * case a corresponding {@code AudioFrame} object is created. In the latter
     * case there are no more headers, so the end of the stream is probably
     * reached.
     * 
     * @return the next {@code AudioFrame} or <b>null</b>
     * @throws IOException if an IO error occurs
     */
    public AudioFrame nextFrame() throws IOException
    {
        AudioFrame frame = null;
        while (!endOfStream && frame == null)
        {
            findFrameSyncByte();
            if (!endOfStream)
            {
                HeaderBitField headerField = createHeaderField();
                if (!endOfStream)
                {
                    frame = createHeader(headerField);
                    if (frame == null)
                    {
                        pushBack(headerField);
                    }
                }
            }
        }

        currentHeader = frame;
        return frame;
    }

    /**
     * Skips the current MPEG frame. This method can be called after a valid
     * MPEG header has been retrieved using {@code nextFrame()}. In this case
     * the underlying stream is advanced to the end of the associated MPEG
     * frame. Otherwise, this method has no effect. The return value indicates
     * whether a frame could be skipped.
     * 
     * @return <b>true</b> if a frame could be skipped, <b>false</b> otherwise
     * @throws IOException if an IO error occurs
     */
    public boolean skipFrame() throws IOException
    {
        if (currentHeader != null)
        {
            skipStream(in, currentHeader.getLength() - HEADER_SIZE);
            currentHeader = null;
            return true;
        }
        return false;
    }

    /**
     * Advances the underlying stream until the first byte of frame sync is
     * found.
     * 
     * @throws IOException if an error occurs
     */
    private void findFrameSyncByte() throws IOException
    {
        boolean found = false;
        while (!found && !endOfStream)
        {
            if (nextByte() == 0xFF)
            {
                found = true;
            }
        }
    }

    /**
     * Creates a bit field for the MPEG frame header.
     * 
     * @return the bit field
     * @throws IOException if an error occurs
     */
    private HeaderBitField createHeaderField() throws IOException
    {
        HeaderBitField field = new HeaderBitField();
        field.add(nextByte());
        field.add(nextByte());
        field.add(nextByte());
        return field;
    }

    /**
     * Creates an {@code AudioFrame} object based on the given header field. If
     * the header field contains invalid values, result is <b>null</b>.
     * 
     * @param bits the header bit field
     * @return the {@code AudioFrame}
     */
    private AudioFrame createHeader(HeaderBitField bits)
    {
        if (bits.get(21, 23) != 7)
        {
            return null;
        }

        int mpegVer = bits.get(19, 20);
        int layer = bits.get(17, 18);
        int bitRateCode = bits.get(12, 15);
        int sampleRateCode = bits.get(10, 11);
        int padding = bits.get(9);

        if (mpegVer == 1 || layer == 0 || bitRateCode == 0 || bitRateCode == 15
                || sampleRateCode == 3)
        {
            // invalid header values
            return null;
        }

        int bitRate = calculateBitRate(mpegVer, layer, bitRateCode);
        int sampleRate = calculateSampleRate(mpegVer, sampleRateCode);
        int length = calculateFrameLength(layer, bitRate, sampleRate, padding);
        float duration = calculateDuration(layer, sampleRate);
        int channels = calculateChannels(bits.get(6, 7));
        return new AudioFrame(mpegVer, layer, bitRate, sampleRate, channels,
                length, duration);
    }

    /**
     * Reads the next byte.
     * 
     * @return the next byte
     * @throws IOException if an error occurs
     */
    private int nextByte() throws IOException
    {
        int result = 0;
        if (!endOfStream)
        {
            result = read();
            if (result == -1)
            {
                endOfStream = true;
            }
        }
        return endOfStream ? 0 : result;
    }

    /**
     * Pushes the given header field back in the stream so that the bytes are
     * read again. This method is called if an invalid header was detected. Then
     * search has to continue at the next byte after the frame sync byte.
     * 
     * @param field the header bit field with the invalid frame header
     * @throws IOException if an error occurs
     */
    private void pushBack(HeaderBitField field) throws IOException
    {
        unread(field.toArray());
    }

    /**
     * Skips the given number of bytes from the specified input stream.
     * 
     * @param in the input stream
     * @param count the number of bytes to skip
     * @throws IOException if an IO error occurs
     */
    private static void skipStream(InputStream in, long count)
            throws IOException
    {
        long size = count;
        long skipped = 0;
        while (size > 0 && skipped >= 0)
        {
            skipped = in.skip(size);
            if (skipped != -1)
            {
                size -= skipped;
            }
        }
    }
    
    /**
     * Calculates the bit rate based on the given parameters.
     * 
     * @param mpegVer the MPEG version
     * @param layer the layer
     * @param code the code for the bit rate
     * @return the bit rate in bits per second
     */
    private static int calculateBitRate(int mpegVer, int layer, int code)
    {
        int[] arr = null;

        if (mpegVer == AudioFrame.MPEG_V1)
        {
            switch (layer)
            {
            case AudioFrame.LAYER_1:
                arr = BIT_RATE_MPEG1_L1;
                break;
            case AudioFrame.LAYER_2:
                arr = BIT_RATE_MPEG1_L2;
                break;
            case AudioFrame.LAYER_3:
                arr = BIT_RATE_MPEG1_L3;
                break;
            }
        }
        else
        {
            if (layer == AudioFrame.LAYER_1)
            {
                arr = BIT_RATE_MPEG2_L1;
            }
            else
            {
                arr = BIT_RATE_MPEG2_L2;
            }
        }
        return arr[code];
    }

    /**
     * Calculates the sample rate based on the given parameters.
     * 
     * @param mpegVer the MPEG version
     * @param code the code for the sample rate
     * @return the sample rate in samples per second
     */
    private static int calculateSampleRate(int mpegVer, int code)
    {
        return SAMPLE_RATE[mpegVer][code];
    }

    /**
     * Calculates the length of an MPEG frame based on the given parameters.
     * 
     * @param layer the layer
     * @param bitRate the bit rate
     * @param sampleRate the sample rate
     * @param padding the padding flag
     * @return the length of the frame in bytes
     */
    private static int calculateFrameLength(int layer, int bitRate,
            int sampleRate, int padding)
    {
        if (layer == AudioFrame.LAYER_1)
        {
            return (12 * bitRate / sampleRate + padding) * 4;
        }
        else
        {
            return 144 * bitRate / sampleRate + padding;
        }
    }

    /**
     * Calculates the duration of a MPEG frame based on the given parameters.
     * 
     * @param layer the layer
     * @param sampleRate the sample rate
     * @return the duration of this frame in milliseconds
     */
    private static float calculateDuration(int layer, int sampleRate)
    {
        int sampleCount =
                (layer == AudioFrame.LAYER_1) ? SAMPLE_COUNT_L1
                        : SAMPLE_COUNT_L2;
        return (1000.0f / sampleRate) * sampleCount;
    }

    /**
     * Calculates the number of channels based on the given parameters.
     * 
     * @param chan the code for the channels
     * @return the number of channels
     */
    private static int calculateChannels(int chan)
    {
        return chan < 3 ? 2 : 1;
    }

    /**
     * Creates the complete array for the sample rate mapping.
     * 
     * @return the table for the sample rates
     */
    private static int[][] createSampleRateTable()
    {
        int[][] arr = new int[4][];
        arr[AudioFrame.MPEG_V1] = SAMPLE_RATE_MPEG1;
        arr[AudioFrame.MPEG_V2] = SAMPLE_RATE_MPEG2;
        arr[AudioFrame.MPEG_V2_5] = SAMPLE_RATE_MPEG2_5;
        return arr;
    }

    /**
     * A class representing the bit field of an MPEG header. It allows
     * convenient access to specific bit groups.
     */
    private static class HeaderBitField
    {
        /** The internal value. */
        private int value;

        /**
         * Adds a byte to this field.
         * 
         * @param b the byte to be added
         */
        public void add(int b)
        {
            value <<= 8;
            value |= b;
        }

        /**
         * Returns the value of the bit group from the given start and end
         * index. E.g. ''from'' = 0, ''to'' = 3 will return the value of the
         * first 4 bits.
         * 
         * @param the from index
         * @param to the to index
         * @return the value of this group of bits
         */
        public int get(int from, int to)
        {
            int shiftVal = value >> from;
            int mask = (1 << (to - from + 1)) - 1;
            return shiftVal & mask;
        }

        /**
         * Returns the value of the bit with the given index. The bit index is
         * 0-based. Result is either 0 or 1, depending on the value of this bit.
         * 
         * @param bit the bit index
         * @return the value of this bit
         */
        public int get(int bit)
        {
            return get(bit, bit);
        }

        /**
         * Returns the internal value of this field as an array. The array
         * contains 3 bytes.
         * 
         * @return the internal value of this field as int array
         */
        public byte[] toArray()
        {
            byte[] result = new byte[3];
            result[0] = (byte) get(16, 23);
            result[1] = (byte) get(8, 15);
            result[2] = (byte) get(0, 7);
            return result;
        }
    }
}
