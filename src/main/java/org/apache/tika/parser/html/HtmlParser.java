/**
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
package org.apache.tika.parser.html;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.sax.xpath.Matcher;
import org.apache.tika.sax.xpath.MatchingContentHandler;
import org.apache.tika.sax.xpath.XPathParser;
import org.apache.tika.utils.Utils;
import org.cyberneko.html.parsers.SAXParser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * HTML parser. Uses CyberNeko to turn the input document to HTML SAX events,
 * and post-processes the events to produce XHTML and metadata expected by
 * Tika clients.
 */
public class HtmlParser extends AbstractParser {

    /**
     * Set of safe mappings from incoming HTML elements to outgoing
     * XHTML elements. Ensures that the output is valid XHTML 1.0 Strict.
     */
    private static final Map<String, String> SAFE_ELEMENTS =
        new HashMap<String, String>();

    static {
        // Based on http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd
        SAFE_ELEMENTS.put("P", "p");
        SAFE_ELEMENTS.put("H1", "h1");
        SAFE_ELEMENTS.put("H2", "h2");
        SAFE_ELEMENTS.put("H3", "h3");
        SAFE_ELEMENTS.put("H4", "h4");
        SAFE_ELEMENTS.put("H5", "h5");
        SAFE_ELEMENTS.put("H6", "h6");
        SAFE_ELEMENTS.put("UL", "ul");
        SAFE_ELEMENTS.put("OL", "ol");
        SAFE_ELEMENTS.put("LI", "li");
        SAFE_ELEMENTS.put("DL", "dl");
        SAFE_ELEMENTS.put("DT", "dt");
        SAFE_ELEMENTS.put("DD", "dd");
        SAFE_ELEMENTS.put("PRE", "pre");
        SAFE_ELEMENTS.put("BLOCKQUOTE", "blockquote");
        SAFE_ELEMENTS.put("TABLE", "p"); // TODO colspan/rowspan issues
    }

    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        // Protect the stream from being closed by CyberNeko
        stream = new CloseShieldInputStream(stream);

        // Prepare the HTML content handler that generates proper
        // XHTML events to records relevant document metadata
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        XPathParser xpath = new XPathParser(null, "");
        Matcher body = xpath.parse("/HTML/BODY//node()");
        Matcher title = xpath.parse("/HTML/HEAD/TITLE//node()");
        handler = new TeeContentHandler(
                new MatchingContentHandler(getBodyHandler(xhtml), body),
                new MatchingContentHandler(getTitleHandler(metadata), title));

        // Parse the HTML document
        xhtml.startDocument();
        SAXParser parser = new SAXParser();
        parser.setContentHandler(handler);
        parser.parse(new InputSource(Utils.getUTF8Reader(stream, metadata)));
        xhtml.endDocument();
    }

    private ContentHandler getTitleHandler(final Metadata metadata) {
        final StringWriter writer = new StringWriter();
        return new WriteOutContentHandler(writer) {
            @Override
            public void endElement(String u, String l, String n) {
                metadata.set(Metadata.TITLE, writer.toString());
            }
        };
    }

    private ContentHandler getBodyHandler(final XHTMLContentHandler xhtml) {
        return new TextContentHandler(xhtml) {
            @Override
            public void startElement(
                    String uri, String local, String name, Attributes atts)
                    throws SAXException {
                String safe = SAFE_ELEMENTS.get(name);
                if (safe != null) {
                    xhtml.startElement(safe);
                } else if ("A".equals(name)) {
                    String href = atts.getValue("href");
                    if (href == null) {
                        href = "";
                    }
                    xhtml.startElement("a", "href", href);
                }
            }

            @Override
            public void endElement(
                    String uri, String local, String name) throws SAXException {
                String safe = SAFE_ELEMENTS.get(name);
                if (safe != null) {
                    xhtml.endElement(safe);
                } else if ("A".equals(name)) {
                    xhtml.endElement("a");
                }
            }
        };
    }

}
