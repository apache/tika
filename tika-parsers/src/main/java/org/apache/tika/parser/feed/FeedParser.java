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
package org.apache.tika.parser.feed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;

/**
 * Feed parser.
 * <p>
 * Uses Rome for parsing the feeds. A feed description is put in a paragraph
 * with its link and title in an anchor.
 */
public class FeedParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.application("rss+xml"),
                    MediaType.application("atom+xml"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        SyndFeed feed = null;
        // set the encoding?
        try {
            SyndFeedInput feedInput = new SyndFeedInput();
            InputSource input = new InputSource(stream);
            feed = feedInput.build(input);
        } catch (Exception e) {
            throw new TikaException(e.getMessage());
        }

        String feedDesc = stripTags(feed.getDescriptionEx());
        String feedTitle = stripTags(feed.getTitleEx());

        metadata.set(Metadata.TITLE, feedTitle);
        metadata.set(Metadata.DESCRIPTION, feedDesc);
        // store the other fields in the metadata

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        List entries = feed.getEntries();
        for (Iterator i = entries.iterator(); i.hasNext();) {
            SyndEntry entry = (SyndEntry) i.next();
            String link = entry.getLink();
            if (link == null)
                continue;
            SyndContent description = entry.getDescription();

            String title = stripTags(entry.getTitleEx());
            xhtml.startElement("a", "href", link);
            xhtml.characters(title);
            xhtml.endElement("a");
            xhtml.startElement("p");
            if (description != null)
                xhtml.characters(description.getValue());
            xhtml.endElement("p");
        }

        xhtml.endDocument();
    }

    private static String stripTags(SyndContent c) {
        if (c == null)
            return "";

        String value = c.getValue();

        String[] parts = value.split("<[^>]*>");
        StringBuffer buf = new StringBuffer();

        for (String part : parts)
            buf.append(part);

        return buf.toString().trim();
    }
}
