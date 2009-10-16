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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.tika.sax.TextContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

class BodyHandler extends TextContentHandler {

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

    private int discardLevel = 0;

    public BodyHandler(XHTMLContentHandler xhtml) {
        super(xhtml);
        this.xhtml = xhtml;
    }

    @Override
    public void startElement(
            String uri, String local, String name, Attributes atts)
            throws SAXException {
        if (discardLevel != 0) {
            discardLevel++;
        } else if (DISCARD_ELEMENTS.contains(name)) {
            discardLevel = 1;
        } else if (SAFE_ELEMENTS.containsKey(name)) {
            xhtml.startElement(SAFE_ELEMENTS.get(name));
        } else if ("A".equals(name)) {
            String href = atts.getValue("href");
            if (href != null) {
                xhtml.startElement("a", "href", href);
            } else {
                String anchor = atts.getValue("name");
                if (anchor != null) {
                    xhtml.startElement("a", "name", anchor);
                } else {
                    xhtml.startElement("a");
                }
            }
        }
    }

    @Override
    public void endElement(
            String uri, String local, String name) throws SAXException {
        if (discardLevel != 0) {
            discardLevel--;
        } else if (SAFE_ELEMENTS.containsKey(name)) {
            xhtml.endElement(SAFE_ELEMENTS.get(name));
        } else if ("A".equals(name)) {
            xhtml.endElement("a");
        }
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        if (discardLevel == 0) {
            super.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length)
            throws SAXException {
        if (discardLevel == 0) {
            super.ignorableWhitespace(ch, start, length);
        }
    }

}