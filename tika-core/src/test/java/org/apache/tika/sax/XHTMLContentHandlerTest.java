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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.tika.config.TikaConfigTest;
import org.apache.tika.metadata.Metadata;

import org.junit.Before;
import org.junit.Test;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Unit tests for the {@link XHTMLContentHandler} class.
 */
public class XHTMLContentHandlerTest {

    private ContentHandler output;

    private XHTMLContentHandler xhtml;

    @Before
    public void setUp() {
        output = new BodyContentHandler();
        xhtml = new XHTMLContentHandler(output, new Metadata());
    }

    /**
     * Test that content in block elements are properly separated in text
     * output.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-188">TIKA-188</a>
     */
    @Test
    public void testExtraWhitespace() throws SAXException {
        xhtml.startDocument();

        xhtml.element("p", "foo");
        xhtml.startElement("p");
        xhtml.characters("b");
        xhtml.element("b", "a"); // inlines should not cause extra whitespace
        xhtml.characters("r");
        xhtml.endElement("p");

        xhtml.startElement("table");
        xhtml.startElement("tr");
        xhtml.element("th", "x");
        xhtml.element("th", "y");
        xhtml.endElement("tr");
        xhtml.startElement("tr");
        xhtml.element("td", "a");
        xhtml.element("td", "b");
        xhtml.endElement("tr");
        xhtml.endElement("table");
        xhtml.endDocument();

        String[] words = output.toString().split("\\s+");
        assertEquals(6, words.length);
        assertEquals("foo", words[0]);
        assertEquals("bar", words[1]);
        assertEquals("x", words[2]);
        assertEquals("y", words[3]);
        assertEquals("a", words[4]);
        assertEquals("b", words[5]);
    }
    
    /**
     * Test that content in option elements are properly separated in text
     * output.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-394">TIKA-394</a>
     */
    @Test
    public void testWhitespaceWithOptions() throws Exception {
        xhtml.startDocument();
        xhtml.startElement("form");
        xhtml.startElement("select");
        xhtml.element("option", "opt1");
        xhtml.element("option", "opt2");
        xhtml.endElement("select");
        xhtml.endElement("form");
        xhtml.endDocument();

        String[] words = output.toString().split("\\s+");
        assertEquals(2, words.length);
        assertEquals("opt1", words[0]);
        assertEquals("opt2", words[1]);
    }
    
    @Test
    public void testWhitespaceWithMenus() throws Exception {
        xhtml.startDocument();
        xhtml.startElement("menu");
        xhtml.element("li", "one");
        xhtml.element("li", "two");
        xhtml.endElement("menu");
        xhtml.endDocument();
        
        String[] words = getRealWords(output.toString());
        assertEquals(2, words.length);
        assertEquals("one", words[0]);
        assertEquals("two", words[1]);
    }

    @Test
    public void testAttributesOnBody() throws Exception {
        ToHTMLContentHandler toHTMLContentHandler = new ToHTMLContentHandler();
        XHTMLContentHandler xhtmlContentHandler = new XHTMLContentHandler(toHTMLContentHandler, new Metadata());
        AttributesImpl attributes = new AttributesImpl();

        attributes.addAttribute(XHTMLContentHandler.XHTML, "itemscope", "itemscope", "", "");
        attributes.addAttribute(XHTMLContentHandler.XHTML, "itemtype", "itemtype", "", "http://schema.org/Event");

        xhtmlContentHandler.startDocument();
        xhtmlContentHandler.startElement(XHTMLContentHandler.XHTML, "body", "body", attributes);
        xhtmlContentHandler.endElement("body");
        xhtmlContentHandler.endDocument();

        assertTrue(toHTMLContentHandler.toString().contains("itemscope"));
    }
    
    @Test
    public void testAttributesOnHtml() throws Exception {
        ToHTMLContentHandler toHTMLContentHandler = new ToHTMLContentHandler();
        XHTMLContentHandler xhtmlContentHandler = new XHTMLContentHandler(toHTMLContentHandler, new Metadata());
        AttributesImpl attributes = new AttributesImpl();

        attributes.addAttribute(XHTMLContentHandler.XHTML, "itemscope", "itemscope", "", "");
        attributes.addAttribute(XHTMLContentHandler.XHTML, "itemtype", "itemtype", "", "http://schema.org/Event");

        xhtmlContentHandler.startDocument();
        xhtmlContentHandler.startElement(XHTMLContentHandler.XHTML, "html", "html", attributes);
        xhtmlContentHandler.endElement("html");
        xhtmlContentHandler.endDocument();

        assertTrue(toHTMLContentHandler.toString().contains("itemscope"));
    }
    
    
    @Test
    public void testInvalidControlCharacter0x7F() throws Exception {
        xhtml.startDocument();
        xhtml.startElement("menu");
        xhtml.element("li", "a\u007Fz");
        xhtml.endElement("menu");
        xhtml.endDocument();
        
        String[] words = getRealWords(output.toString());
        assertEquals(1, words.length);
        assertEquals("a\ufffdz", words[0]);
    }
    
    @Test
    public void testInvalidControlCharacter0x9F() throws Exception {
        xhtml.startDocument();
        xhtml.startElement("menu");
        xhtml.element("li", "a\u009Fz");
        xhtml.endElement("menu");
        xhtml.endDocument();
        
        String[] words = getRealWords(output.toString());
        assertEquals(1, words.length);
        assertEquals("a\ufffdz", words[0]);
    }
    
    @Test
    public void testInvalidControlCharacter0x93() throws Exception {
        xhtml.startDocument();
        xhtml.startElement("menu");
        xhtml.element("li", "a\u0093z");
        xhtml.endElement("menu");
        xhtml.endDocument();
        
        String[] words = getRealWords(output.toString());
        assertEquals(1, words.length);
        assertEquals("a\ufffdz", words[0]);
    }

    /**
     * Return array of non-zerolength words. Splitting on whitespace will get us
     * empty words for emptylines.
     * 
     * @param string some mix of newlines and real words
     * @return array of real words.
     */
    private static String[] getRealWords(String string) {
        String[] possibleWords = string.split("\\s+");
        List<String> words = new ArrayList<String>(possibleWords.length);
        for (String word : possibleWords) {
            if (word.length() > 0) {
                words.add(word);
            }
        }
        
        return words.toArray(new String[words.size()]);
    }

}
