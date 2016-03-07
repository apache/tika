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
package org.apache.tika.sax;

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Content handler that collects links from an XHTML document.
 */
public class LinkContentHandler extends DefaultHandler {

    /**
     * Stack of link builders, one for each level of nested links currently
     * being processed. A usual case of a nested link would be a hyperlinked
     * image (<code>&a href="..."&gt;&lt;img src="..."&gt;&lt;&gt;</code>),
     * but it's possible (though unlikely) for also other kinds of nesting
     * to occur.
     */
    private final LinkedList<LinkBuilder> builderStack =
        new LinkedList<LinkBuilder>();

    /** Collected links */
    private final List<Link> links = new ArrayList<Link>();
    
    /** Whether to collapse whitespace in anchor text */
    private boolean collapseWhitespaceInAnchor;
    
    /**
     * Default constructor
     */
    public LinkContentHandler() { 
        this(false);
    }
    
    /**
     * Default constructor
     *
     * @boolean collapseWhitespaceInAnchor
     */
    public LinkContentHandler(boolean collapseWhitespaceInAnchor) {
      super();
      
      this.collapseWhitespaceInAnchor = collapseWhitespaceInAnchor;
    }

    /**
     * Returns the list of collected links.
     *
     * @return collected links
     */
    public List<Link> getLinks() {
        return links;
    }

    //-------------------------------------------------------< ContentHandler>

    @Override
    public void startElement(
            String uri, String local, String name, Attributes attributes) {
        if (XHTML.equals(uri)) {
            if ("a".equals(local)) {
                LinkBuilder builder = new LinkBuilder("a");
                builder.setURI(attributes.getValue("", "href"));
                builder.setTitle(attributes.getValue("", "title"));
                builder.setRel(attributes.getValue("", "rel"));
                builderStack.addFirst(builder);
            } else if ("img".equals(local)) {
                LinkBuilder builder = new LinkBuilder("img");
                builder.setURI(attributes.getValue("", "src"));
                builder.setTitle(attributes.getValue("", "title"));
                builder.setRel(attributes.getValue("", "rel"));
                builderStack.addFirst(builder);

                String alt = attributes.getValue("", "alt");
                if (alt != null) {
                    char[] ch = alt.toCharArray();
                    characters(ch, 0, ch.length);
                }
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        for (LinkBuilder builder : builderStack) {
            builder.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        characters(ch, start, length);
    }

    @Override
    public void endElement(String uri, String local, String name) {
        if (XHTML.equals(uri)) {
            if ("a".equals(local) || "img".equals(local)) {
                links.add(builderStack.removeFirst().getLink(collapseWhitespaceInAnchor));
            }
        }
    }

}
