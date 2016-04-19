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
import java.util.Arrays;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

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
    private ID3Comment comment;
    private String genre;
    private String trackNumber;

    boolean found = false;

    public ID3v1Handler(InputStream stream, ContentHandler handler)
            throws IOException, SAXException, TikaException {
        this(LyricsHandler.getSuffix(stream, 128));
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
            
            String commentStr = getString(tagData, 97, 127);
            comment = new ID3Comment(commentStr);

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

    public List<ID3Comment> getComments() {
       return Arrays.asList(comment);
    }

    public String getGenre() {
        return genre;
    }

    public String getTrackNumber() {
        return trackNumber;
    }
    
    /**
     * ID3v1 doesn't have composers,
     *  so returns null;
     */
    public String getComposer() {
        return null;
    }

    /**
     * ID3v1 doesn't have album-wide artists,
     *  so returns null;
     */
    public String getAlbumArtist() {
        return null;
    }

    /**
     * ID3v1 doesn't have disc numbers,
     *  so returns null;
     */
    public String getDisc() {
        return null;
    }

    /**
     * ID3v1 doesn't have compilations,
     *  so returns null;
     */
    public String getCompilation() {
        return null;
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
        return new String(buffer, start, end - start, ISO_8859_1);
    }
}
