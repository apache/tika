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

import org.apache.tika.exception.TikaException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This is used to parse ID3 Version 1 Tag information from an MP3 file, 
 * if available.
 *
 * @see <a href="http://www.id3.org/ID3v1">MP3 ID3 Version 1 specification</a>
 */
public class ID3v1Handler implements ID3Tags {
    private String title;
    private String artist;
    private String album;
    private String year;
    private String comment;
    private String genre;
    private String trackNumber;

    boolean found = false;

    public ID3v1Handler(InputStream stream, ContentHandler handler)
            throws IOException, SAXException, TikaException {
        this(getSuffix(stream, 128));
    }

    /**
     * Creates from the last 128 bytes of a stream.
     * @param tagData Must be the last 128 bytes 
     */
    protected ID3v1Handler(byte[] tagData)
            throws IOException, SAXException, TikaException {
        if (tagData.length == 128
                && tagData[0] == 'T' && tagData[1] == 'A' && tagData[2] == 'G') {
            found = true;

            title = getString(tagData, 3, 33);
            artist = getString(tagData, 33, 63);
            album = getString(tagData, 63, 93);
            year = getString(tagData, 93, 97);
            comment = getString(tagData, 97, 127);

            int genreID = (int) tagData[127] & 0xff; // unsigned byte
            genre = GENRES[Math.min(genreID, GENRES.length - 1)];

            // ID3v1.1 Track addition
            // If the last two bytes of the comment field are zero and
            // non-zero, then the last byte is the track number
            if (tagData[125] == 0 && tagData[126] != 0) {
                int trackNum = (int) tagData[126] & 0xff;
                trackNumber = Integer.toString(trackNum);
            }
        }
    }


    public boolean getTagsPresent() {
        return found;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public String getYear() {
        return year;
    }

    public String getComment() {
        return comment;
    }

    public String getGenre() {
        return genre;
    }

    public String getTrackNumber() {
        return trackNumber;
    }

    /**
     * Returns the identified ISO-8859-1 substring from the given byte buffer.
     * The return value is the zero-terminated substring retrieved from
     * between the given start and end positions in the given byte buffer.
     * Extra whitespace (and control characters) from the beginning and the
     * end of the substring is removed.
     *
     * @param buffer byte buffer
     * @param start start index of the substring
     * @param end end index of the substring
     * @return the identified substring
     * @throws TikaException if the ISO-8859-1 encoding is not available
     */
    private static String getString(byte[] buffer, int start, int end)
            throws TikaException {
        // Find the zero byte that marks the end of the string
        int zero = start;
        while (zero < end && buffer[zero] != 0) {
            zero++;
        }

        // Skip trailing whitespace
        end = zero;
        while (start < end && buffer[end - 1] <= ' ') {
            end--;
        }

        // Skip leading whitespace
        while (start < end && buffer[start] <= ' ') {
            start++;
        }

        // Return the remaining substring
        try {
            return new String(buffer, start, end - start, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new TikaException("ISO-8859-1 encoding is not available", e);
        }
    }

    /**
     * Reads and returns the last <code>length</code> bytes from the
     * given stream.
     * @param stream input stream
     * @param length number of bytes from the end to read and return
     * @return stream the <code>InputStream</code> to read from.
     * @throws IOException if the stream could not be read from.
     */
    private static byte[] getSuffix(InputStream stream, int length)
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
