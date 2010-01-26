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

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.mp3.ID3v2Frame.RawTag;
import org.apache.tika.parser.mp3.ID3v2Frame.RawTagIterator;
import org.xml.sax.SAXException;

/**
 * This is used to parse ID3 Version 2.3 Tag information from an MP3 file,
 * if available.
 *
 * @see <a href="http://id3lib.sourceforge.net/id3/id3v2.3.0.html">MP3 ID3 Version 2.3 specification</a>
 */
public class ID3v23Handler implements ID3Tags {
    private String title;
    private String artist;
    private String album;
    private String year;
    private String comment;
    private String genre;
    private String trackNumber;

    public ID3v23Handler(ID3v2Frame frame)
            throws IOException, SAXException, TikaException {
        RawTagIterator tags = new RawV23TagIterator(frame);
        while (tags.hasNext()) {
            RawTag tag = tags.next();
            if (tag.name.equals("TIT2")) {
                title = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TPE1")) {
                artist = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TALB")) {
                album = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TYER")) {
                year = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("COMM")) {
                comment = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TRCK")) {
                trackNumber = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TCON")) {
                String rawGenre = getTagString(tag.data, 0, tag.data.length);
                int open = rawGenre.indexOf("(");
                int close = rawGenre.indexOf(")");
                if (open < close) {
                    try {
                        int genreID = Integer.parseInt(rawGenre.substring(open+1, close));
                        genre = ID3Tags.GENRES[genreID];
                    } catch(NumberFormatException ignore) {
                    }
                }
            }
        }
    }

    private String getTagString(byte[] data, int offset, int length) {
        return ID3v2Frame.getTagString(data, offset, length);
    }

    public boolean getTagsPresent() {
        return true;
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

    private class RawV23TagIterator extends RawTagIterator {
        private RawV23TagIterator(ID3v2Frame frame) {
            frame.super(4, 4, 1, 2);
        }
    }

}
