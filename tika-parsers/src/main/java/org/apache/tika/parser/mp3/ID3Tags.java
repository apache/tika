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


/**
 * Interface that defines the common interface for ID3 tag parsers,
 *  such as ID3v1 and ID3v2.3.
 * Implementations should return NULL if the file lacks a given
 *  tag, or if the tag isn't defined for the version.
 *  
 * Note that so far, only the ID3v1 core tags are listed here. In
 *  future, we may wish to add more to cover the extra tags that
 *  our ID3v2 handlers can produce.
 */
public interface ID3Tags {
    /**
     * List of predefined genres.
     *
     * @see http://www.id3.org/id3v2-00
     */
    String[] GENRES = new String[] {
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

    /**
     * Does the file contain this kind of tags?
     */
    boolean getTagsPresent();

    String getTitle();

    String getArtist();

    String getAlbum();

    String getComment();

    String getGenre();

    String getYear();

    String getTrackNumber();

}
