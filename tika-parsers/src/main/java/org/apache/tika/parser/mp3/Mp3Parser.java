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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.ID3Tags.ID3Comment;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * The <code>Mp3Parser</code> is used to parse ID3 Version 1 Tag information
 * from an MP3 file, if available.
 *
 * @see <a href="http://www.id3.org/ID3v1">MP3 ID3 Version 1 specification</a>
 * @see <a href="http://www.id3.org/id3v2.4.0-structure">MP3 ID3 Version 2.4 Structure Specification</a>
 * @see <a href="http://www.id3.org/id3v2.4.0-frames">MP3 ID3 Version 2.4 Frames Specification</a>
 */
public class Mp3Parser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 8537074922934844370L;

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.singleton(MediaType.audio("mpeg"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "audio/mpeg");
        metadata.set(XMPDM.AUDIO_COMPRESSOR, "MP3");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        // Create handlers for the various kinds of ID3 tags
        ID3TagsAndAudio audioAndTags = getAllTagHandlers(stream, handler);

        if (audioAndTags.tags.length > 0) {
           CompositeTagHandler tag = new CompositeTagHandler(audioAndTags.tags);

           metadata.set(TikaCoreProperties.TITLE, tag.getTitle());
           metadata.set(TikaCoreProperties.CREATOR, tag.getArtist());
           metadata.set(XMPDM.ARTIST, tag.getArtist());
           metadata.set(XMPDM.COMPOSER, tag.getComposer());
           metadata.set(XMPDM.ALBUM, tag.getAlbum());
           metadata.set(XMPDM.RELEASE_DATE, tag.getYear());
           metadata.set(XMPDM.GENRE, tag.getGenre());
           
           List<String> comments = new ArrayList<String>();
           for (ID3Comment comment : tag.getComments()) {
              StringBuffer cmt = new StringBuffer();
              if (comment.getLanguage() != null) {
                 cmt.append(comment.getLanguage());
                 cmt.append(" - ");
              }
              if (comment.getDescription() != null) {
                 cmt.append(comment.getDescription());
                 if (comment.getText() != null) {
                    cmt.append("\n");
                 }
              }
              if (comment.getText() != null) {
                 cmt.append(comment.getText());
              }
              
              comments.add(cmt.toString());
              metadata.add(XMPDM.LOG_COMMENT.getName(), cmt.toString());
           }

           xhtml.element("h1", tag.getTitle());
           xhtml.element("p", tag.getArtist());

            // ID3v1.1 Track addition
            if (tag.getTrackNumber() != null) {
                xhtml.element("p", tag.getAlbum() + ", track " + tag.getTrackNumber());
                metadata.set(XMPDM.TRACK_NUMBER, tag.getTrackNumber());
            } else {
                xhtml.element("p", tag.getAlbum());
            }
            xhtml.element("p", tag.getYear());
            xhtml.element("p", tag.getGenre());
            for (String comment : comments) {
               xhtml.element("p", comment);
            }
        }
        if (audioAndTags.audio != null) {
            metadata.set("samplerate", String.valueOf(audioAndTags.audio.getSampleRate()));
            metadata.set("channels", String.valueOf(audioAndTags.audio.getChannels()));
            metadata.set("version", audioAndTags.audio.getVersion());
            
            metadata.set(
                    XMPDM.AUDIO_SAMPLE_RATE,
                    Integer.toString(audioAndTags.audio.getSampleRate()));
            if(audioAndTags.audio.getChannels() == 1) {
               metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Mono");
            } else if(audioAndTags.audio.getChannels() == 2) {
               metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "Stereo");
            } else if(audioAndTags.audio.getChannels() == 5) {
               metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "5.1");
            } else if(audioAndTags.audio.getChannels() == 7) {
               metadata.set(XMPDM.AUDIO_CHANNEL_TYPE, "7.1");
            }
        }
        if (audioAndTags.lyrics != null && audioAndTags.lyrics.hasLyrics()) {
           xhtml.startElement("p", "class", "lyrics");
           xhtml.characters(audioAndTags.lyrics.lyricsText);
           xhtml.endElement("p");
        }

        xhtml.endDocument();
    }

    /**
     * Scans the MP3 frames for ID3 tags, and creates ID3Tag Handlers
     *  for each supported set of tags. 
     */
    protected static ID3TagsAndAudio getAllTagHandlers(InputStream stream, ContentHandler handler)
           throws IOException, SAXException, TikaException {
       ID3v24Handler v24 = null;
       ID3v23Handler v23 = null;
       ID3v22Handler v22 = null;
       ID3v1Handler v1 = null;
       LyricsHandler lyrics = null;
       AudioFrame firstAudio = null;

       // ID3v2 tags live at the start of the file
       // You can apparently have several different ID3 tag blocks
       // So, keep going until we don't find any more
       MP3Frame f;
       while ((f = ID3v2Frame.createFrameIfPresent(stream)) != null && firstAudio == null) {
           if(f instanceof ID3v2Frame) {
               ID3v2Frame id3F = (ID3v2Frame)f;
               if (id3F.getMajorVersion() == 4) {
                   v24 = new ID3v24Handler(id3F);
               } else if(id3F.getMajorVersion() == 3) {
                   v23 = new ID3v23Handler(id3F);
               } else if(id3F.getMajorVersion() == 2) {
                   v22 = new ID3v22Handler(id3F);
               }
           } else if(f instanceof AudioFrame) {
               firstAudio = (AudioFrame)f;
           }
       }

       // ID3v1 tags live at the end of the file
       // Lyrics live just before ID3v1, at the end of the file
       // Search for both (handlers seek to the end for us)
       lyrics = new LyricsHandler(stream, handler);
       v1 = lyrics.id3v1;

       // Go in order of preference
       // Currently, that's newest to oldest
       List<ID3Tags> tags = new ArrayList<ID3Tags>();

       if(v24 != null && v24.getTagsPresent()) {
          tags.add(v24);
       }
       if(v23 != null && v23.getTagsPresent()) {
          tags.add(v23);
       }
       if(v22 != null && v22.getTagsPresent()) {
          tags.add(v22);
       }
       if(v1 != null && v1.getTagsPresent()) {
          tags.add(v1);
       }
       
       ID3TagsAndAudio ret = new ID3TagsAndAudio();
       ret.audio = firstAudio;
       ret.lyrics = lyrics;
       ret.tags = tags.toArray(new ID3Tags[tags.size()]);
       return ret;
    }

    protected static class ID3TagsAndAudio {
        private ID3Tags[] tags;
        private AudioFrame audio;
        private LyricsHandler lyrics;
    }

}
