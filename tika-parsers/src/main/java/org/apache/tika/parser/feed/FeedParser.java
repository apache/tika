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
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;

/**
 * Feed parser.
 * <p>
 * Uses Rome for parsing the feeds. A feed description is put in a paragraph
 * with its link and title in an anchor.
 */
public class FeedParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -3785361933034525186L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                    MediaType.application("rss+xml"),
                    MediaType.application("atom+xml"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        // set the encoding?
        try {
            SyndFeed feed = new SyndFeedInput().build(
                    new InputSource(new CloseShieldInputStream(stream)));

            String title = stripTags(feed.getTitleEx());
            String description = stripTags(feed.getDescriptionEx());

            metadata.set(TikaCoreProperties.TITLE, title);
            metadata.set(TikaCoreProperties.DESCRIPTION, description);
            // store the other fields in the metadata

            XHTMLContentHandler xhtml =
                new XHTMLContentHandler(handler, metadata);
            xhtml.startDocument();

            xhtml.element("h1", title);
            xhtml.element("p", description);

            xhtml.startElement("ul");
            for (Object e : feed.getEntries()) {
                SyndEntry entry = (SyndEntry) e;
                String link = entry.getLink();
                if (link != null) {
                    xhtml.startElement("li");
                    xhtml.startElement("a", "href", link);
                    xhtml.characters(stripTags(entry.getTitleEx()));
                    xhtml.endElement("a");
                    SyndContent content = entry.getDescription();
                    if (content != null) {
                        xhtml.newline();
                        xhtml.characters(content.getValue());
                    }
                    xhtml.endElement("li");
                }
            }
            xhtml.endElement("ul");

            xhtml.endDocument();
        } catch (FeedException e) {
            throw new TikaException("RSS parse error", e);
        }

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
