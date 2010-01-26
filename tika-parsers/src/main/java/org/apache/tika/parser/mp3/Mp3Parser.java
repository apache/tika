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
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
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
public class Mp3Parser implements Parser {

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "audio/mpeg");

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        
        // Create handlers for the various kinds of ID3 tags
        ID3Tags[] tags = getAllTagHandlers(stream, handler);

        if (tags.length > 0) {
           CompositeTagHandler tag = new CompositeTagHandler(tags);
           
           metadata.set(Metadata.TITLE, tag.getTitle());
           metadata.set(Metadata.AUTHOR, tag.getArtist());

           xhtml.element("h1", tag.getTitle());
           xhtml.element("p", tag.getArtist());
            
            // ID3v1.1 Track addition
            if (tag.getTrackNumber() != null) {
                xhtml.element("p", tag.getAlbum() + ", track " + tag.getTrackNumber());
            } else {
                xhtml.element("p", tag.getAlbum());
            }
            xhtml.element("p", tag.getYear());
            xhtml.element("p", tag.getComment());
            xhtml.element("p", tag.getGenre());
        }

        xhtml.endDocument();
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    /**
     * Scans the MP3 frames for ID3 tags, and creates ID3Tag Handlers
     *  for each supported set of tags. 
     */
    protected ID3Tags[] getAllTagHandlers(InputStream stream, ContentHandler handler)
           throws IOException, SAXException, TikaException {
       ID3v24Handler v24 = null;
       ID3v23Handler v23 = null;
       ID3v22Handler v22 = null;
       ID3v1Handler v1 = null;

       // ID3v2 tags live at the start of the file
       // You can apparently have several different ID3 tag blocks
       // So, keep going until we don't find any more
       ID3v2Frame f;
       while ((f = ID3v2Frame.createFrameIfPresent(stream)) != null) {
          if (f.getMajorVersion() == 4) {
             v24 = new ID3v24Handler(f);
          } else if(f.getMajorVersion() == 3) {
             v23 = new ID3v23Handler(f);
          } else if(f.getMajorVersion() == 2) {
             v22 = new ID3v22Handler(f);
          }
       }

       // ID3v1 tags live at the end of the file
       // Just let the handler run until it's finished
       v1 = new ID3v1Handler(stream, handler);

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
       return tags.toArray(new ID3Tags[tags.size()]);
    }

}
