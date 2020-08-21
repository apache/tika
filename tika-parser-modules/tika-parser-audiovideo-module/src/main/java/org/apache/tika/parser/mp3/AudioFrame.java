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

import org.apache.tika.exception.TikaException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * An Audio Frame in an MP3 file. These come after the ID3v2 tags in the file.
 * Currently, only the header is processed, not the raw audio data.
 */
public class AudioFrame implements MP3Frame {
    /** Constant for the MPEG version 1. */
    public static final int MPEG_V1 = 3;

    /** Constant for the MPEG version 2. */
    public static final int MPEG_V2 = 2;

    /** Constant for the MPEG version 2.5. */
    public static final int MPEG_V2_5 = 0;

    /** Constant for audio layer 1. */
    public static final int LAYER_1 = 3;
    
    /** Constant for audio layer 2. */
    public static final int LAYER_2 = 2;
    
    /** Constant for audio layer 3. */
    public static final int LAYER_3 = 1;
    
    private final String version;
    private final int versionCode;
    private final int layer;
    private final int sampleRate;
    private final int channels;
    private final int bitRate;
    private final int length;
    private final float duration;

    public String getVersion() {
        return version;
    }

    /**
     * Get the sampling rate, in Hz
     */
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Get the number of channels (1=mono, 2=stereo)
     */
    public int getChannels() {
        return channels;
    }

    /**
     * Get the version code.
     * @return the version code (one of the {@code MPEG} constants)
     */
    public int getVersionCode()
    {
        return versionCode;
    }

    /**
     * Get the audio layer code.
     * @return the audio layer (one of the {@code LAYER} constants)
     */
    public int getLayer()
    {
        return layer;
    }

    /**
     * Get the bit rate in bit per second.
     * @return the bit rate
     */
    public int getBitRate()
    {
        return bitRate;
    }

    /**
     * Returns the frame length in bytes.
     * @return the frame length
     */
    public int getLength()
    {
        return length;
    }

    /**
     * Returns the duration in milliseconds.
     * @return the duration
     */
    public float getDuration()
    {
        return duration;
    }

    /**
     * Does this appear to be a 4 byte audio frame header?
     */
    public static boolean isAudioHeader(int h1, int h2, int h3, int h4) {
        if (h1 == -1 || h2 == -1 || h3 == -1 || h4 == -1) {
            return false;
        }
        // Check for the magic 11 bits set at the start
        // Note - doesn't do a CRC check
        if (h1 == 0xff && (h2 & 0x60) == 0x60) {
            return true;
        }
        return false;
    }

    /**
     * @deprecated Use the constructor which is passed all values directly.
     */
    @Deprecated
    public AudioFrame(InputStream stream, ContentHandler handler)
            throws IOException, SAXException, TikaException {
        this(-2, -2, -2, -2, stream);
    }

    /**
     * @deprecated Use the constructor which is passed all values directly.
     */
    @Deprecated
    public AudioFrame(int h1, int h2, int h3, int h4, InputStream in)
            throws IOException {
        if (h1 == -2 && h2 == -2 && h3 == -2 && h4 == -2) {
            h1 = in.read();
            h2 = in.read();
            h3 = in.read();
            h4 = in.read();
        }

        if (isAudioHeader(h1, h2, h3, h4)) {
            layer = (h2 >> 1) & 0x03;
            versionCode = (h2 >> 3) & 0x03;
            version = generateVersionStr(versionCode, layer);

            int rateCode = (h3 >> 2) & 0x03;
            int rate;
            switch (rateCode) {
            case 0:
                rate = 11025;
                break;
            case 1:
                rate = 12000;
                break;
            default:
                rate = 8000;
            }
            if (versionCode == MPEG_V2) {
                rate *= 2;
            } else if(versionCode == MPEG_V1) {
                rate *= 4;
            }
            sampleRate = rate;

            int chans = h4 & 0x192;
            if (chans < 3) {
                // Stereo, joint stereo, dual channel
                channels = 2;
            } else {
                channels = 1;
            }
            bitRate = 0;
            duration = 0;
            length = 0;
        } else {
            throw new IllegalArgumentException("Magic Audio Frame Header not found");
        }
    }
    
    /**
     * 
     * Creates a new instance of {@code AudioFrame} and initializes all properties.
     * @param mpegVersion the code for the MPEG version
     * @param layer the code for the layer
     * @param bitRate the bit rate (in bps)
     * @param sampleRate the sample rate (in samples per second)
     * @param channels the number of channels
     * @param length the frame length (in bytes)
     * @param duration the duration of this frame (in milliseconds)
     */
    public AudioFrame(int mpegVersion, int layer, int bitRate, int sampleRate,
            int channels, int length, float duration) {
        versionCode = mpegVersion;
        this.layer = layer;
        this.bitRate = bitRate;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.length = length;
        this.duration = duration;
        version = generateVersionStr(mpegVersion, layer);
    }

    /**
     * Generates a string for the version of this audio frame.
     * @param version the code for the MPEG version
     * @param layer the code for the layer
     * @return a string for the version
     */
    private static String generateVersionStr(int version, int layer) {
        StringBuilder buf = new StringBuilder(64);
        buf.append("MPEG 3 Layer ");
        if (layer == LAYER_3) {
            buf.append("III");
        } else if (layer == LAYER_2) {
            buf.append("II");
        } else if (layer == LAYER_1) {
            buf.append("I");
        } else {
            buf.append("(reserved)");
        }

        buf.append(" Version ");
        if (version == MPEG_V2_5) {
            buf.append("2.5");
        } else if(version == MPEG_V2) {
            buf.append("2");
        } else if(version == MPEG_V1) {
            buf.append("1");
        } else {
            buf.append("(reseved)");
        }
        
        return buf.toString();
    }
}
