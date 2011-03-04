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
 * This is used to parse Lyrics3 tag information
 *  from an MP3 file, if available.
 * Handles lyrics tags of up to 10kb in size.
 * Will process any ID3v1 tag data if present.
 * Ignores extended ID3v1 data in the lyrics block
 *
 * @see <a href="http://www.id3.org/Lyrics3v2">Lyrics3 v2.0 specification</a>
 */
public class LyricsHandler {
    boolean foundLyrics = false;
    String lyricsText = null;
    ID3v1Handler id3v1 = null;

    public LyricsHandler(InputStream stream, ContentHandler handler)
            throws IOException, SAXException, TikaException {
        this(getSuffix(stream, 10240+128));
    }

    /**
     * Looks for the Lyrics data, which will be
     *  just before the ID3v1 data (if present),
     *  and process it.
     * Also sets things up for the ID3v1
     *  processing if required.
     * Creates from the last 128 bytes of a stream.
     */
    protected LyricsHandler(byte[] tagData)
            throws IOException, SAXException, TikaException {
        if(tagData.length < 128) {
            return;
        }

        // Is there ID3v1 data?
        byte[] last128 = new byte[128];
        System.arraycopy(tagData, tagData.length-128, last128, 0, 128);
        id3v1 = new ID3v1Handler(last128);

        if(tagData.length < 137) {
            return;
        }

        // Are there lyrics? Look for the closing Lyrics tag
        //  at the end to decide if there is any
        int lookat = tagData.length - 9;
        if(id3v1.found) {
            lookat -= 128;
        }
        if(tagData[lookat+0] == 'L' && tagData[lookat+1] == 'Y' && 
                tagData[lookat+2] == 'R' && tagData[lookat+3] == 'I' &&
                tagData[lookat+4] == 'C' && tagData[lookat+5] == 'S' &&
                tagData[lookat+6] == '2' && tagData[lookat+7] == '0' &&
                tagData[lookat+8] == '0') {
            foundLyrics = true;

            // The length (6 bytes) comes just before LYRICS200, and is the
            //  size including the LYRICSBEGIN but excluding the 
            //  length+LYRICS200 at the end.
            int length = Integer.parseInt(
                    new String(tagData, lookat-6, 6)
            );

            String lyrics = new String(
                    tagData, lookat-length+5, length-11,
                    "ASCII"
            );

            // Tags are a 3 letter code, 5 digit length, then data
            int pos = 0;
            while(pos < lyrics.length()-8) {
                String tagName = lyrics.substring(pos, pos+3);
                int tagLen = Integer.parseInt(
                        lyrics.substring(pos+3, pos+8)
                );
                int startPos = pos + 8;
                int endPos = startPos + tagLen;

                if(tagName.equals("LYR")) {
                    lyricsText = lyrics.substring(startPos, endPos);
                }

                pos = endPos;
            }
        }
    }

    public boolean hasID3v1() {
        if(id3v1 == null || id3v1.found == false) {
            return false;
        }
        return true;
    }
    public boolean hasLyrics() {
        return lyricsText != null && lyricsText.length() > 0;
    }

    /**
     * Reads and returns the last <code>length</code> bytes from the
     * given stream.
     * @param stream input stream
     * @param length number of bytes from the end to read and return
     * @return stream the <code>InputStream</code> to read from.
     * @throws IOException if the stream could not be read from.
     */
    protected static byte[] getSuffix(InputStream stream, int length)
            throws IOException {
        byte[] buffer = new byte[2 * length];
        int bytesInBuffer = 0;

        int n = stream.read(buffer);
        while (n != -1) {
            bytesInBuffer += n;
            if (bytesInBuffer == buffer.length) {
                System.arraycopy(buffer, bytesInBuffer - length, buffer, 0, length);
                bytesInBuffer = length;
            }
            n = stream.read(buffer, bytesInBuffer, buffer.length - bytesInBuffer);
        }

        if (bytesInBuffer < length) {
            length = bytesInBuffer;
        }

        byte[] result = new byte[length];
        System.arraycopy(buffer, bytesInBuffer - length, result, 0, length);
        return result;
    }
}
