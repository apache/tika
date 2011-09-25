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

import junit.framework.TestCase;

import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

public class SerializerTest extends TestCase {

    public void testToTextContentHandler() throws Exception {
        assertStartDocument("", new ToTextContentHandler());
        assertCharacters("content", new ToTextContentHandler());
        assertCharacterEscaping("<&\">", new ToTextContentHandler());
        assertIgnorableWhitespace(" \t\r\n", new ToTextContentHandler());
        assertEmptyElement("", new ToTextContentHandler());
        assertEmptyElementWithAttributes("", new ToTextContentHandler());
        assertEmptyElementWithAttributeEscaping("", new ToTextContentHandler());
        assertElement("content", new ToTextContentHandler());
        assertElementWithAttributes("content", new ToTextContentHandler());
    }

    public void testToXMLContentHandler() throws Exception {
        assertStartDocument("", new ToXMLContentHandler());
        assertStartDocument(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
                new ToXMLContentHandler("UTF-8"));
        assertCharacters("content", new ToXMLContentHandler());
        assertCharacterEscaping("&lt;&amp;\"&gt;", new ToXMLContentHandler());
        assertIgnorableWhitespace(" \t\r\n", new ToXMLContentHandler());
        assertEmptyElement("<br />", new ToXMLContentHandler());
        assertEmptyElementWithAttributes(
                "<meta name=\"foo\" value=\"bar\" />",
                new ToXMLContentHandler());
        assertEmptyElementWithAttributeEscaping(
                "<p class=\"&lt;&amp;&quot;&gt;\" />",
                new ToXMLContentHandler());
        assertElement("<p>content</p>", new ToXMLContentHandler());
        assertElementWithAttributes(
                "<p class=\"test\">content</p>",
                new ToXMLContentHandler());
    }

    public void testToHTMLContentHandler() throws Exception {
        assertStartDocument("", new ToHTMLContentHandler());
        assertCharacters("content", new ToHTMLContentHandler());
        assertCharacterEscaping("&lt;&amp;\"&gt;", new ToHTMLContentHandler());
        assertIgnorableWhitespace(" \t\r\n", new ToHTMLContentHandler());
        assertEmptyElement("<br>", new ToHTMLContentHandler());
        assertEmptyElementWithAttributes(
                "<meta name=\"foo\" value=\"bar\">",
                new ToHTMLContentHandler());
        assertEmptyElementWithAttributeEscaping(
                "<p class=\"&lt;&amp;&quot;&gt;\"></p>",
                new ToHTMLContentHandler());
        assertElement("<p>content</p>", new ToHTMLContentHandler());
        assertElementWithAttributes(
                "<p class=\"test\">content</p>",
                new ToHTMLContentHandler());
    }

    private void assertStartDocument(String expected, ContentHandler handler)
            throws Exception {
        handler.startDocument();
        assertEquals(expected, handler.toString());
    }

    private void assertCharacters(String expected, ContentHandler handler)
            throws Exception {
        handler.characters("content".toCharArray(), 0, 7);
        assertEquals(expected, handler.toString());
    }

    private void assertCharacterEscaping(
            String expected, ContentHandler handler) throws Exception {
        handler.characters("<&\">".toCharArray(), 0, 4);
        assertEquals(expected, handler.toString());
    }

    private void assertIgnorableWhitespace(
            String expected, ContentHandler handler) throws Exception {
        handler.ignorableWhitespace(" \t\r\n".toCharArray(), 0, 4);
        assertEquals(expected, handler.toString());
    }

    private void assertEmptyElement(String expected, ContentHandler handler)
            throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        handler.startElement("", "br", "br", attributes);
        handler.endElement("", "br", "br");
        assertEquals(expected, handler.toString());
    }

    private void assertEmptyElementWithAttributes(
            String expected, ContentHandler handler) throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "name", "name", "CDATA", "foo");
        attributes.addAttribute("", "value", "value", "CDATA", "bar");
        handler.startElement("", "meta", "meta", attributes);
        handler.endElement("", "meta", "meta");
        assertEquals(expected, handler.toString());
    }

    private void assertEmptyElementWithAttributeEscaping(
            String expected, ContentHandler handler) throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "<&\">");
        handler.startElement("", "p", "p", attributes);
        handler.endElement("", "p", "p");
        assertEquals(expected, handler.toString());
    }

    private void assertElement(
            String expected, ContentHandler handler) throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        handler.startElement("", "p", "p", attributes);
        handler.characters("content".toCharArray(), 0, 7);
        handler.endElement("", "p", "p");
        assertEquals(expected, handler.toString());
    }

    private void assertElementWithAttributes(
            String expected, ContentHandler handler) throws Exception {
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "test");
        handler.startElement("", "p", "p", attributes);
        handler.characters("content".toCharArray(), 0, 7);
        handler.endElement("", "p", "p");
        assertEquals(expected, handler.toString());
    }

}
