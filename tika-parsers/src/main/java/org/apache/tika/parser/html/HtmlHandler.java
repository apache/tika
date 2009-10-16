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
package org.apache.tika.parser.html;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

class HtmlHandler extends TextContentHandler {

    /**
     * Set of safe mappings from incoming HTML elements to outgoing
     * XHTML elements. Ensures that the output is valid XHTML 1.0 Strict.
     */
    private static final Map<String, String> SAFE_ELEMENTS =
        new HashMap<String, String>();

    /**
     * Set of HTML elements whose content will be discarded.
     */
    private static final Set<String> DISCARD_ELEMENTS = new HashSet<String>();

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
        SAFE_ELEMENTS.put("MENU", "ul");
        SAFE_ELEMENTS.put("DL", "dl");
        SAFE_ELEMENTS.put("DT", "dt");
        SAFE_ELEMENTS.put("DD", "dd");
        SAFE_ELEMENTS.put("PRE", "pre");
        SAFE_ELEMENTS.put("BLOCKQUOTE", "blockquote");
        SAFE_ELEMENTS.put("TABLE", "table");
        SAFE_ELEMENTS.put("THEAD", "thead");
        SAFE_ELEMENTS.put("TBODY", "tbody");
        SAFE_ELEMENTS.put("TR", "tr");
        SAFE_ELEMENTS.put("TH", "th");
        SAFE_ELEMENTS.put("TD", "td");

        DISCARD_ELEMENTS.add("STYLE");
        DISCARD_ELEMENTS.add("SCRIPT");
    }

    private final XHTMLContentHandler xhtml;

    private final Metadata metadata;

    private int bodyLevel = 0;

    private int discardLevel = 0;

    private int titleLevel = 0;

    private final StringBuilder title = new StringBuilder();

    private HtmlHandler(XHTMLContentHandler xhtml, Metadata metadata) {
        super(xhtml);
        this.xhtml = xhtml;
        this.metadata = metadata;

        // Try to determine the default base URL, if one has not been given
        if (metadata.get(Metadata.CONTENT_LOCATION) == null) {
            String name = metadata.get(Metadata.RESOURCE_NAME_KEY);
            if (name != null) {
                name = name.trim();
                try {
                    new URL(name); // test URL format
                    metadata.set(Metadata.CONTENT_LOCATION, name);
                } catch (MalformedURLException e) {
                    // The resource name is not a valid URL, ignore it
                }
            }
        }
    }

    public HtmlHandler(ContentHandler handler, Metadata metadata) {
        this(new XHTMLContentHandler(handler, metadata), metadata);
    }

    @Override
    public void startElement(
            String uri, String local, String name, Attributes atts)
            throws SAXException {
        if ("TITLE".equals(name) || titleLevel > 0) {
            titleLevel++;
        }
        if ("BODY".equals(name) || bodyLevel > 0) {
            bodyLevel++;
        }
        if (DISCARD_ELEMENTS.contains(name) || discardLevel > 0) {
            discardLevel++;
        }

        if (bodyLevel == 0 && discardLevel == 0) {
            if ("META".equals(name) && atts.getValue("content") != null) {
                if (atts.getValue("http-equiv") != null) {
                    metadata.set(
                            atts.getValue("http-equiv"),
                            atts.getValue("content"));
                }
                if (atts.getValue("name") != null) {
                    metadata.set(
                            atts.getValue("name"),
                            atts.getValue("content"));
                }
            } else if ("BASE".equals(name) && atts.getValue("href") != null) {
                metadata.set(
                        Metadata.CONTENT_LOCATION,
                        resolve(atts.getValue("href").trim()));
            }
        }

        if (bodyLevel > 0 && discardLevel == 0) {
            if (SAFE_ELEMENTS.containsKey(name)) {
                xhtml.startElement(SAFE_ELEMENTS.get(name));
            } else if ("A".equals(name)) {
                String href = atts.getValue("href");
                if (href != null) {
                    xhtml.startElement("a", "href", resolve(href.trim()));
                } else {
                    String anchor = atts.getValue("name");
                    if (anchor != null) {
                        xhtml.startElement("a", "name", anchor.trim());
                    } else {
                        xhtml.startElement("a");
                    }
                }
            }
        }

        title.setLength(0);
    }

    @Override
    public void endElement(
            String uri, String local, String name) throws SAXException {
        if (bodyLevel > 0 && discardLevel == 0) {
            if (SAFE_ELEMENTS.containsKey(name)) {
                xhtml.endElement(SAFE_ELEMENTS.get(name));
            } else if ("A".equals(name)) {
                xhtml.endElement("a");
            }
        }

        if (titleLevel > 0) {
            titleLevel--;
            if (titleLevel == 0) {
                metadata.set(Metadata.TITLE, title.toString().trim());
            }
        }
        if (bodyLevel > 0) {
            bodyLevel--;
        }
        if (discardLevel > 0) {
            discardLevel--;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (titleLevel > 0 && bodyLevel == 0) {
            title.append(ch, start, length);
        }
        if (bodyLevel > 0 && discardLevel == 0) {
            super.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        if (bodyLevel > 0 && discardLevel == 0) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

    private String resolve(String url) {
        // Return the URL as-is if no base URL is available
        if (metadata.get(Metadata.CONTENT_LOCATION) == null) {
            return url;
        }

        // Check for common non-hierarchical and pseudo URI prefixes
        String lower = url.toLowerCase();
        if (lower.startsWith("urn:")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")
                || lower.startsWith("data:")
                || lower.startsWith("javascript:")
                || lower.startsWith("about:")) {
            return url;
        }

        try {
            URL base = new URL(metadata.get(Metadata.CONTENT_LOCATION).trim());

            // We need to handle one special case, where the relativeUrl is
            // just a query string (like "?pid=1"), and the baseUrl doesn't
            // end with a '/'. In that case, the URL class removes the last
            // portion of the path, which we don't want.
            String path = base.getPath();
            if (url.startsWith("?") && path.length() > 0 && !path.endsWith("/")) {
                return new URL(
                        base.getProtocol(), base.getHost(), base.getPort(),
                        base.getPath() + url).toExternalForm();
            } else {
                return new URL(base, url).toExternalForm();
            }
        } catch (MalformedURLException e) {
            // Unknown or broken format; just return the URL as received.
            return url;
        }
    }

}