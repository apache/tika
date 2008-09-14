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

import org.apache.commons.lang.StringUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

/**
 * <p>
 * This class parses and represents a ID3v1 Tag. Implemented based on http://www.id3.org/ID3v1.
 * </p>
 */
public class ID3v1Tag {
    /**
     * Static Map of genre codes.
     */
    private static Map genres = new HashMap();

    static {
        genres.put(0, "Blues");
        genres.put(1, "Classic Rock");
        genres.put(2, "Country");
        genres.put(3, "Dance");
        genres.put(4, "Disco");
        genres.put(5, "Funk");
        genres.put(6, "Grunge");
        genres.put(7, "Hip-Hop");
        genres.put(8, "Jazz");
        genres.put(9, "Metal");
        genres.put(10, "New Age");
        genres.put(11, "Oldies");
        genres.put(12, "Other");
        genres.put(13, "Pop");
        genres.put(14, "R&B");
        genres.put(15, "Rap");
        genres.put(16, "Reggae");
        genres.put(17, "Rock");
        genres.put(18, "Techno");
        genres.put(19, "Industrial");
        genres.put(20, "Alternative");
        genres.put(21, "Ska");
        genres.put(22, "Death Metal");
        genres.put(23, "Pranks");
        genres.put(24, "Soundtrack");
        genres.put(25, "Euro-Techno");
        genres.put(26, "Ambient");
        genres.put(27, "Trip-Hop");
        genres.put(28, "Vocal");
        genres.put(29, "Jazz+Funk");
        genres.put(30, "Fusion");
        genres.put(31, "Trance");
        genres.put(32, "Classical");
        genres.put(33, "Instrumental");
        genres.put(34, "Acid");
        genres.put(35, "House");
        genres.put(36, "Game");
        genres.put(37, "Sound Clip");
        genres.put(38, "Gospel");
        genres.put(39, "Noise");
        genres.put(40, "AlternRock");
        genres.put(41, "Bass");
        genres.put(42, "Soul");
        genres.put(43, "Punk");
        genres.put(44, "Space");
        genres.put(45, "Meditative");
        genres.put(46, "Instrumental Pop");
        genres.put(47, "Instrumental Rock");
        genres.put(48, "Ethnic");
        genres.put(49, "Gothic");
        genres.put(50, "Darkwave");
        genres.put(51, "Techno-Industrial");
        genres.put(52, "Electronic");
        genres.put(53, "Pop-Folk");
        genres.put(54, "Eurodance");
        genres.put(55, "Dream");
        genres.put(56, "Southern Rock");
        genres.put(57, "Comedy");
        genres.put(58, "Cult");
        genres.put(59, "Gangsta");
        genres.put(60, "Top 40");
        genres.put(61, "Christian Rap");
        genres.put(62, "Pop/Funk");
        genres.put(63, "Jungle");
        genres.put(64, "Native American");
        genres.put(65, "Cabaret");
        genres.put(66, "New Wave");
        genres.put(67, "Psychadelic");
        genres.put(68, "Rave");
        genres.put(69, "Showtunes");
        genres.put(70, "Trailer");
        genres.put(71, "Lo-Fi");
        genres.put(72, "Tribal");
        genres.put(73, "Acid Punk");
        genres.put(74, "Acid Jazz");
        genres.put(75, "Polka");
        genres.put(76, "Retro");
        genres.put(77, "Musical");
        genres.put(78, "Rock & Roll");
        genres.put(79, "Hard Rock");
    }

    private String title;
    private String artist;
    private String album;
    private String year;
    private String comment;
    private int genre;

    /**
     * Default Private Contructor.
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
        return (String) genres.get(genre);
    }

    /**
     * Create an <code>ID3v1Tag</code> from an <code>InputStream</code>.
     *
     * @param stream the <code>InputStream</code> to parse.
     * @return a <code>ID3v1Tag</code> if ID3 v1 information is available, null otherwise.
     */
    public static ID3v1Tag createID3v1Tag(InputStream stream) {
        byte[] buffer;
        try {
             buffer = getSuffix(stream, 128);
        } catch (IOException ex) {
            return null;
        }

        // We have read what we think is the tag, first check and if ok extract values
        String tag = new String(buffer, 0, 128);
        if (!StringUtils.equals(StringUtils.substring(tag, 0, 3), "TAG")) {
            return null;
        }
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
