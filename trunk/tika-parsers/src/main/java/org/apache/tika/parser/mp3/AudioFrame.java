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
    private String version;
    private int sampleRate;
    private int channels;

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


    public AudioFrame(InputStream stream, ContentHandler handler)
            throws IOException, SAXException, TikaException {
        this(-2, -2, -2, -2, stream);
    }

    public AudioFrame(int h1, int h2, int h3, int h4, InputStream in)
            throws IOException {
        if (h1 == -2 && h2 == -2 && h3 == -2 && h4 == -2) {
            h1 = in.read();
            h2 = in.read();
            h3 = in.read();
            h4 = in.read();
        }

        if (isAudioHeader(h1, h2, h3, h4)) {
            version = "MPEG 3 Layer ";
            int layer = (h2 >> 1) & 0x03;
            if (layer == 1) {
                version += "III";
            } else if (layer == 2) {
                version += "II";
            } else if (layer == 3) {
                version += "I";
            } else {
                version += "(reserved)";
            }

            version += " Version ";
            int ver = (h2 >> 3) & 0x03;
            if (ver == 0) {
                version += "2.5";
            } else if(ver == 2) {
                version += "2";
            } else if(ver == 3) {
                version += "1";
            } else {
                version += "(reseved)";
            }

            int rate = (h3 >> 2) & 0x03;
            switch (rate) {
            case 0:
                sampleRate = 11025;
                break;
            case 1:
                sampleRate = 12000;
                break;
            default:
                sampleRate = 8000;
            }
            if (ver == 2) {
                sampleRate *= 2;
            } else if(ver == 3) {
                sampleRate *= 4;
            }

            int chans = h4 & 0x03;
            if (chans < 3) {
                // Stereo, joint stereo, dual channel
                channels = 2;
            } else {
                channels = 1;
            }
        } else {
            throw new IllegalArgumentException("Magic Audio Frame Header not found");
        }
    }

}
