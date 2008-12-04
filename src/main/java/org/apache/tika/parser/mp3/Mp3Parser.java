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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * The <code>Mp3Parser</code> is used to parse ID3 Version 1 Tag information
 * from an MP3 file, if available.
 *
 * @see http://www.id3.org/ID3v1
 */
public class Mp3Parser implements Parser {

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
        /* 79 */ "Hard Rock",
        /* 80 */ "Folk",
        /* 81 */ "Folk-Rock",
        /* 82 */ "National Folk",
        /* 83 */ "Swing",
        /* 84 */ "Fast Fusion",
        /* 85 */ "Bebob",
        /* 86 */ "Latin",
        /* 87 */ "Revival",
        /* 88 */ "Celtic",
        /* 89 */ "Bluegrass",
        /* 90 */ "Avantgarde",
        /* 91 */ "Gothic Rock",
        /* 92 */ "Progressive Rock",
        /* 93 */ "Psychedelic Rock",
        /* 94 */ "Symphonic Rock",
        /* 95 */ "Slow Rock",
        /* 96 */ "Big Band",
        /* 97 */ "Chorus",
        /* 98 */ "Easy Listening",
        /* 99 */ "Acoustic",
        /* 100 */ "Humour",
        /* 101 */ "Speech",
        /* 102 */ "Chanson",
        /* 103 */ "Opera",
        /* 104 */ "Chamber Music",
        /* 105 */ "Sonata",
        /* 106 */ "Symphony",
        /* 107 */ "Booty Bass",
        /* 108 */ "Primus",
        /* 109 */ "Porn Groove",
        /* 110 */ "Satire",
        /* 111 */ "Slow Jam",
        /* 112 */ "Club",
        /* 113 */ "Tango",
        /* 114 */ "Samba",
        /* 115 */ "Folklore",
        /* 116 */ "Ballad",
        /* 117 */ "Power Ballad",
        /* 118 */ "Rhythmic Soul",
        /* 119 */ "Freestyle",
        /* 120 */ "Duet",
        /* 121 */ "Punk Rock",
        /* 122 */ "Drum Solo",
        /* 123 */ "A capella",
        /* 124 */ "Euro-House",
        /* 125 */ "Dance Hall",
        /* sentinel */ ""
    };

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "audio/mpeg");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        byte[] tag = getSuffix(stream, 128);
        if (tag.length == 128
                && tag[0] == 'T' && tag[1] == 'A' && tag[2] == 'G') {
            String title = getString(tag, 3, 33);
            String artist = getString(tag, 33, 63);
            String album = getString(tag, 63, 93);
            String year = getString(tag, 93, 97);
            String comment = getString(tag, 97, 127);
            int genre = (int) tag[127] & 0xff; // unsigned byte

            metadata.set(Metadata.TITLE, title);
            metadata.set(Metadata.AUTHOR, artist);

            xhtml.element("h1", title);
            xhtml.characters("\n");

            xhtml.element("p", artist);
            xhtml.characters("\n");

            // ID3v1.1 Track addition
            // If the last two bytes of the comment field are zero and
            // non-zero, then the last byte is the track number
            if (tag[125] == 0 && tag[126] != 0) {
                int track = (int) tag[126] & 0xff;
                xhtml.element("p", album + ", track " + track);
            } else {
                xhtml.element("p", album);
            }
            xhtml.characters("\n");

            xhtml.element("p", year);
            xhtml.characters("\n");

            xhtml.element("p", comment);
            xhtml.characters("\n");

            xhtml.element("p", GENRES[Math.min(genre, GENRES.length - 1)]);
            xhtml.characters("\n");
        }

        xhtml.endDocument();
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
