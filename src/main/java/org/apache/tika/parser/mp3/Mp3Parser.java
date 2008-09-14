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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.commons.lang.StringUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * The <code>Mp3Parser</code> is used to parse ID3 Version 1 Tag information
 * from an MP3 file, if available.
 * </p>
 */
public class Mp3Parser extends AbstractParser {

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "audio/mpeg");

        ID3v1Tag tag = ID3v1Tag.createID3v1Tag(stream);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        if (null != tag) {
            if (StringUtils.isNotEmpty(tag.getTitle())) {
                xhtml.element("p", tag.getTitle());
                xhtml.characters("\n");
                metadata.set(Metadata.TITLE, tag.getTitle());
            }
            if (StringUtils.isNotEmpty(tag.getArtist())) {
                xhtml.element("p", tag.getArtist());
                xhtml.characters("\n");
            }
            if (StringUtils.isNotEmpty(tag.getAlbum())) {
                xhtml.element("p", tag.getAlbum());
                xhtml.characters("\n");
            }
            if (StringUtils.isNotEmpty(tag.getYear())) {
                xhtml.element("p", tag.getYear());
                xhtml.characters("\n");
            }
            if (StringUtils.isNotEmpty(tag.getComment())) {
                xhtml.element("p", tag.getComment());
                xhtml.characters("\n");
                metadata.set(Metadata.COMMENTS, tag.getComment());
            }
            if (StringUtils.isNotEmpty(tag.getGenreAsString())) {
                xhtml.element("p", tag.getGenreAsString());
                xhtml.endDocument();
            }
        }
    }
}
