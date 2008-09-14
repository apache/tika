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

import org.apache.commons.lang.StringUtils;

/**
 * This class parses and represents a ID3v1 Tag.
 * 
 * @see http://www.id3.org/ID3v1
 */
public class ID3v1Tag {

    /**
     * List of predefined genres.
     *
     * @see http://www.id3.org/id3v2-00
     */
    private static final String[] GENRES = new String[] {
        /*  0 */ "Blues",
        /*  1 */ "Classic Rock",
        /*  2 */ "Country",
        /*  3 */ "Dance",
        /*  4 */ "Disco",
        /*  5 */ "Funk",
        /*  6 */ "Grunge",
        /*  7 */ "Hip-Hop",
        /*  8 */ "Jazz",
        /*  9 */ "Metal",
        /* 10 */ "New Age",
        /* 11 */ "Oldies",
        /* 12 */ "Other",
        /* 13 */ "Pop",
        /* 14 */ "R&B",
        /* 15 */ "Rap",
        /* 16 */ "Reggae",
        /* 17 */ "Rock",
        /* 18 */ "Techno",
        /* 19 */ "Industrial",
        /* 20 */ "Alternative",
        /* 21 */ "Ska",
        /* 22 */ "Death Metal",
        /* 23 */ "Pranks",
        /* 24 */ "Soundtrack",
        /* 25 */ "Euro-Techno",
        /* 26 */ "Ambient",
        /* 27 */ "Trip-Hop",
        /* 28 */ "Vocal",
        /* 29 */ "Jazz+Funk",
        /* 30 */ "Fusion",
        /* 31 */ "Trance",
        /* 32 */ "Classical",
        /* 33 */ "Instrumental",
        /* 34 */ "Acid",
        /* 35 */ "House",
        /* 36 */ "Game",
        /* 37 */ "Sound Clip",
        /* 38 */ "Gospel",
        /* 39 */ "Noise",
        /* 40 */ "AlternRock",
        /* 41 */ "Bass",
        /* 42 */ "Soul",
        /* 43 */ "Punk",
        /* 44 */ "Space",
        /* 45 */ "Meditative",
        /* 46 */ "Instrumental Pop",
        /* 47 */ "Instrumental Rock",
        /* 48 */ "Ethnic",
        /* 49 */ "Gothic",
        /* 50 */ "Darkwave",
        /* 51 */ "Techno-Industrial",
        /* 52 */ "Electronic",
        /* 53 */ "Pop-Folk",
        /* 54 */ "Eurodance",
        /* 55 */ "Dream",
        /* 56 */ "Southern Rock",
        /* 57 */ "Comedy",
        /* 58 */ "Cult",
        /* 59 */ "Gangsta",
        /* 60 */ "Top 40",
        /* 61 */ "Christian Rap",
        /* 62 */ "Pop/Funk",
        /* 63 */ "Jungle",
        /* 64 */ "Native American",
        /* 65 */ "Cabaret",
        /* 66 */ "New Wave",
        /* 67 */ "Psychadelic",
        /* 68 */ "Rave",
        /* 69 */ "Showtunes",
        /* 70 */ "Trailer",
        /* 71 */ "Lo-Fi",
        /* 72 */ "Tribal",
        /* 73 */ "Acid Punk",
        /* 74 */ "Acid Jazz",
        /* 75 */ "Polka",
        /* 76 */ "Retro",
        /* 77 */ "Musical",
        /* 78 */ "Rock & Roll",
        /* 79 */ "Hard Rock"
    };

    private String title;
    private String artist;
    private String album;
    private String year;
    private String comment;
    private int genre;

    /**
     * Default private constructor.
     *
     * @param title   the title.
     * @param artist  the artist.
     * @param album   the album.
     * @param year    the year.
     * @param comment the comment.
     * @param genre   the genre code.
     */
    private ID3v1Tag(String title, String artist, String album,
                     String year, String comment, int genre) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.year = year;
        this.comment = comment;
        this.genre = genre;
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

    public int getGenre() {
        return genre;
    }

    public String getGenreAsString() {
        if (0 <= genre && genre < GENRES.length) {
            return GENRES[genre];
        } else {
            return null;
        }
    }

    /**
     * Create an <code>ID3v1Tag</code> from an <code>InputStream</code>.
     *
     * @param stream the <code>InputStream</code> to parse.
     * @return a <code>ID3v1Tag</code> if ID3 v1 information is available, null otherwise.
     * @throws IOException if the stream can not be read
     */
    public static ID3v1Tag createID3v1Tag(InputStream stream)
            throws IOException {
        byte[] buffer = getSuffix(stream, 128);
        if (buffer.length != 128
                || buffer[0] != 'T' || buffer[0] != 'A' || buffer[2] != 'G') {
            return null;
        }

        String tag = new String(buffer, "ISO-8859-1");
        String title = StringUtils.substring(tag, 3, 33).trim();
        String artist = StringUtils.substring(tag, 33, 63).trim();
        String album = StringUtils.substring(tag, 63, 93).trim();
        String year = StringUtils.substring(tag, 93, 97).trim();
        String comment = StringUtils.substring(tag, 97, 127).trim();
        int genre = new Byte((byte) tag.charAt(127)).intValue();

        // Return new ID3v1Tag instance.
        return new ID3v1Tag(title, artist, album, year, comment, genre);
    }

    /**
     * Reads and returns the last <code>length</code> bytes from the
     * given stream.
     * @param stream input stream
     * @param length number of bytes from the end to read and return
     * @return stream the <code>InputStream</code> to read from.
     * @throws IOException if the stream could not be read from.
     */
   private static byte[] getSuffix(InputStream stream, int length) throws IOException {
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
