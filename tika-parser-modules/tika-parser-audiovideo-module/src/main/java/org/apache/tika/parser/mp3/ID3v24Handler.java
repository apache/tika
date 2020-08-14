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
import java.util.ArrayList;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.mp3.ID3v2Frame.RawTag;
import org.apache.tika.parser.mp3.ID3v2Frame.RawTagIterator;
import org.xml.sax.SAXException;

/**
 * This is used to parse ID3 Version 2.4 Tag information from an MP3 file,
 * if available.
 *
 * @see <a href="http://www.id3.org/id3v2.4.0-structure">MP3 ID3 Version 2.4 specification</a>
 * @see <a href="http://www.id3.org/id3v2.4.0-frames">MP3 ID3 Version 2.4 frames/tags</a>
 */
public class ID3v24Handler implements ID3Tags {
    private String title;
    private String artist;
    private String album;
    private String year;
    private String composer;
    private String genre;
    private String trackNumber;
    private String albumArtist;
    private String disc;
    private String compilation;
    private List<ID3Comment> comments = new ArrayList<ID3Comment>();

    public ID3v24Handler(ID3v2Frame frame)
            throws IOException, SAXException, TikaException {
        RawTagIterator tags = new RawV24TagIterator(frame);
        while (tags.hasNext()) {
            RawTag tag = tags.next();
            if (tag.name.equals("TIT2")) {
                title = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TPE1")) {
                artist = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TPE2")) {
                albumArtist = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TALB")) {
                album = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TYER")) {
                year = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TDRC")) {
               if(year == null) {
                  year = getTagString(tag.data, 0, tag.data.length);
               }
            } else if (tag.name.equals("TCOM")) {
                composer = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("COMM")) {
                comments.add( getComment(tag.data, 0, tag.data.length) ); 
            } else if (tag.name.equals("TRCK")) {
                trackNumber = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TPOS")) {
                disc = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TCMP")) {
                compilation = getTagString(tag.data, 0, tag.data.length); 
            } else if (tag.name.equals("TCON")) {
               genre = ID3v22Handler.extractGenre( getTagString(tag.data, 0, tag.data.length) );
            }
        }
    }

    private String getTagString(byte[] data, int offset, int length) {
        return ID3v2Frame.getTagString(data, offset, length);
    }
    private ID3Comment getComment(byte[] data, int offset, int length) {
        return ID3v2Frame.getComment(data, offset, length);
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

    public String getComposer() {
        return composer;
    }

    public List<ID3Comment> getComments() {
        return comments;
    }

    public String getGenre() {
        return genre;
    }

    public String getTrackNumber() {
        return trackNumber;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public String getDisc() {
        return disc;
    }

    public String getCompilation() {
        return compilation;
    }

    private class RawV24TagIterator extends RawTagIterator {
        private RawV24TagIterator(ID3v2Frame frame) {
            frame.super(4, 4, 1, 2);
        }
    }
}
