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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HtmlParserTest extends TestCase {

    private Parser parser = new HtmlParser();

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(name);
    }

    public void testParseAscii() throws Exception {
        final StringWriter href = new StringWriter();

        ContentHandler body = new BodyContentHandler();
        ContentHandler link = new DefaultHandler() {
            @Override
            public void startElement(
                    String u, String l, String n, Attributes a)
                    throws SAXException {
                if ("a".equals(l)) {
                    href.append(a.getValue("href"));
                }
            }
        };
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-documents/testHTML.html");
        try {
            parser.parse(stream, new TeeContentHandler(body, link), metadata);
        } finally {
            stream.close();
        }

        assertEquals(
                "Title : Test Indexation Html", metadata.get(Metadata.TITLE));
        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));
        assertEquals("http://www.apache.org/", href.toString());

        String content = body.toString();
        assertTrue(
                "Did not contain expected text:" + "Test Indexation Html",
                content.contains("Test Indexation Html"));
        assertTrue(
                "Did not contain expected text:" + "Indexation du fichier",
                content.contains("Indexation du fichier"));
    }

    public void XtestParseUTF8() throws IOException, SAXException, TikaException {
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        parser.parse(
                getStream("test-documents/testHTML_utf8.html"),
                handler, metadata);
        String content = handler.toString();

        assertTrue("Did not contain expected text:"
                + "Title : Tilte with UTF-8 chars öäå", content
                .contains("Title : Tilte with UTF-8 chars öäå"));

        assertTrue("Did not contain expected text:"
                + "Content with UTF-8 chars", content
                .contains("Content with UTF-8 chars"));

        assertTrue("Did not contain expected text:" + "åäö", content
                .contains("åäö"));
    }

    public void testXhtmlParsing() throws Exception {
        Parser parser = new AutoDetectParser(); // Should auto-detect!
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();

        InputStream stream = HtmlParserTest.class.getResourceAsStream(
                "/test-documents/testXHTML.html");
        try {
            parser.parse(stream, handler, metadata);
        } finally {
            stream.close();
        }

        assertEquals("application/xhtml+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("XHTML test document", metadata.get(Metadata.TITLE));
        String content = handler.toString();
        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));
        assertTrue(content.contains("ability of Apache Tika"));
        assertTrue(content.contains("extract content"));
        assertTrue(content.contains("an XHTML document"));
    }

    public void testParseEmpty() throws Exception {
        Metadata metadata = new Metadata();
        StringWriter writer = new StringWriter();
        parser.parse(
                new ByteArrayInputStream(new byte[0]),
                new BodyContentHandler(writer), metadata);
        String content = writer.toString();
        assertEquals("", content);
    }

    /**
     * Test case for TIKA-210
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
     */
    public void testCharactersDirectlyUnderBodyElement() throws Exception {
        String test = "<html><body>test</body></html>";
        ContentHandler handler = new BodyContentHandler();
        parser.parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                handler, new Metadata());
        String content = handler.toString();
        assertEquals("test", content);
    }

    /**
     * Test case for TIKA-268
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-268">TIKA-268</a>
     */
    public void testWhitespaceBetweenTableCells() throws Exception {
        String test =
            "<html><body><table><tr><td>a</td><td>b</td></table></body></html>";
        ContentHandler handler = new BodyContentHandler();
        parser.parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                handler, new Metadata());
        String content = handler.toString();
        assertTrue(content.contains("a"));
        assertTrue(content.contains("b"));
        assertFalse(content.contains("ab"));
    }

}
