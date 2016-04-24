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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Test cases for the {@link LinkContentHandler} class.
 */
public class LinkContentHandlerTest {

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-975">TIKA-975</a>
     */
    @Test
    public void testWhitespaceCollapsing() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler(true);
        
        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "a", "", new AttributesImpl());
        char[] anchorText = {'\n', 'N', 'o', ' ', 'w', 'h', 'i', 't', 'e', '\n', '\t', '\t', 's', 'p', 'a', 'c', 'e'};
        linkContentHandler.characters(anchorText, 1, anchorText.length - 1);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "a", "");

        assertEquals("No white space", linkContentHandler.getLinks().get(0).getText());
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-975">TIKA-975</a>
     */
    @Test
    public void testDefaultBehavior() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler();
        
        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "a", "", new AttributesImpl());
        char[] anchorText = {' ', 'a', 'n', 'c', 'h', 'o', 'r', ' '};
        linkContentHandler.characters(anchorText, 0, anchorText.length);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "a", "");

        assertEquals(" anchor ", linkContentHandler.getLinks().get(0).getText());
    }
    
    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-1835">TIKA-1835</a>
     */
    @Test
    public void testLinkTag() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler();
        
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "href", "href", "", "http://tika.apache.org/stylesheet.css");
        atts.addAttribute("", "rel", "rel", "", "stylesheet");
        
        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "link", "", atts);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "link", "");

        assertEquals("http://tika.apache.org/stylesheet.css", linkContentHandler.getLinks().get(0).getUri());
        assertEquals("stylesheet", linkContentHandler.getLinks().get(0).getRel());
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-1835">TIKA-1835</a>
     */
    @Test
    public void testIframeTag() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler();
        
        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "src", "src", "", "http://tika.apache.org/iframe.html");
        
        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "iframe", "", atts);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "iframe", "");

        assertEquals("http://tika.apache.org/iframe.html", linkContentHandler.getLinks().get(0).getUri());
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-1937">TIKA-1937</a>
     */
    @Test
    public void testScriptTag() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler();

        AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "src", "src", "", "http://tika.apache.org/script.js");

        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "script", "", atts);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "script", "");

        assertEquals("http://tika.apache.org/script.js", linkContentHandler.getLinks().get(0).getUri());
    }

    /**
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-1937">TIKA-1937</a>
     */
    @Test
    public void testEmbeddedScriptTag() throws Exception {
        LinkContentHandler linkContentHandler = new LinkContentHandler();

        AttributesImpl atts = new AttributesImpl();
        // no "src" attribute implies it's an embedded script

        linkContentHandler.startElement(XHTMLContentHandler.XHTML, "script", "", atts);
        linkContentHandler.endElement(XHTMLContentHandler.XHTML, "script", "");

        assert(linkContentHandler.getLinks().isEmpty());
    }
}
