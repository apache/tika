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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HtmlParserTest extends TestCase {

    public void testParseAscii() throws Exception {
        String path = "/test-documents/testHTML.html";
        final StringWriter href = new StringWriter();
        final StringWriter name = new StringWriter();
        ContentHandler body = new BodyContentHandler();
        Metadata metadata = new Metadata();
        InputStream stream = HtmlParserTest.class.getResourceAsStream(path);
        try {
            ContentHandler link = new DefaultHandler() {
                @Override
                public void startElement(
                        String u, String l, String n, Attributes a)
                        throws SAXException {
                    if ("a".equals(l)) {
                        if (a.getValue("href") != null) {
                            href.append(a.getValue("href"));
                        } else if (a.getValue("name") != null) {
                            name.append(a.getValue("name"));
                        }
                    }
                }
            };
            new HtmlParser().parse(
                    stream, new TeeContentHandler(body, link),
                    metadata, new ParseContext());
        } finally {
            stream.close();
        }

        assertEquals(
                "Title : Test Indexation Html", metadata.get(Metadata.TITLE));
        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));

        assertEquals("http://www.apache.org/", href.toString());
        assertEquals("test-anchor", name.toString());

        String content = body.toString();
        assertTrue(
                "Did not contain expected text:" + "Test Indexation Html",
                content.contains("Test Indexation Html"));
        assertTrue(
                "Did not contain expected text:" + "Indexation du fichier",
                content.contains("Indexation du fichier"));
    }

    public void XtestParseUTF8() throws IOException, SAXException, TikaException {
        String path = "/test-documents/testXHTML_utf8.html";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream(path), metadata);

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
        String path = "/test-documents/testXHTML.html";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream(path), metadata);

        assertEquals("application/xhtml+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("XHTML test document", metadata.get(Metadata.TITLE));

        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));
        assertTrue(content.contains("ability of Apache Tika"));
        assertTrue(content.contains("extract content"));
        assertTrue(content.contains("an XHTML document"));
    }

    public void testParseEmpty() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                new ByteArrayInputStream(new byte[0]),
                handler,  new Metadata(), new ParseContext());
        assertEquals("", handler.toString());
    }

    /**
     * Test case for TIKA-210
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
     */
    public void testCharactersDirectlyUnderBodyElement() throws Exception {
        String test = "<html><body>test</body></html>";
        String content = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes("UTF-8")));
        assertEquals("test", content);
    }

    /**
     * Test case for TIKA-287
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-287">TIKA-287</a>
     */
    public void testBaseHref() throws Exception {
        assertRelativeLink(
                "http://lucene.apache.org/tika/",
                "http://lucene.apache.org/", "tika/");

        assertRelativeLink(
                "http://domain.com/?pid=1",
                "http://domain.com", "?pid=1");
        assertRelativeLink(
                "http://domain.com/?pid=2",
                "http://domain.com?pid=1", "?pid=2");

        assertRelativeLink(
                "http://domain.com/file.html",
                "http://domain.com/path/", "/file.html");
        assertRelativeLink(
                "http://domain.com/path/file.html",
                "http://domain.com/path/", "./file.html");
        assertRelativeLink(
                "http://domain.com/path/file.html",
                "http://domain.com/path/", "file.html");

        assertRelativeLink(
                "http://domain2.com/newpath",
                "http://domain.com/path/to/file", "http://domain2.com/newpath");

        // See http://www.communities.hp.com/securitysoftware/blogs/jeff/archive/2007/12/19/RFC-1808-vs-2396-vs-3986_3A00_-Browsers-vs.-programing-languages.aspx
        // Also http://www.ietf.org/rfc/rfc3986.txt
        // Also http://issues.apache.org/jira/browse/NUTCH-566
        // Also http://issues.apache.org/jira/browse/NUTCH-436
        assertRelativeLink(
                "http://domain.com/path/?pid=1",
                "http://domain.com/path/", "?pid=1");
        assertRelativeLink(
                "http://domain.com/file?pid=1",
                "http://domain.com/file", "?pid=1");
        assertRelativeLink(
                "http://domain.com/path/d;p?pid=1",
                "http://domain.com/path/d;p?q#f", "?pid=1");
    }

    private void assertRelativeLink(String url, String base, String relative)
            throws Exception {
        String test =
            "<html><head><base href=\"" + base + "\"></head>"
            + "<body><a href=\"" + relative + "\">test</a></body></html>";
        final List<String> links = new ArrayList<String>();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new DefaultHandler() {
                    @Override
                    public void startElement(
                            String u, String l, String name, Attributes atts) {
                        if (atts.getValue("", "href") != null) {
                            links.add(atts.getValue("", "href"));
                        }
                    }
                },
                new Metadata(),
                new ParseContext());
        assertEquals(1, links.size());
        assertEquals(url, links.get(0));
    }

    /**
     * Test case for TIKA-268
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-268">TIKA-268</a>
     */
    public void testWhitespaceBetweenTableCells() throws Exception {
        String test =
            "<html><body><table><tr><td>a</td><td>b</td></table></body></html>";
        String content = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes("UTF-8")));
        assertTrue(content.contains("a"));
        assertTrue(content.contains("b"));
        assertFalse(content.contains("ab"));
    }

    /**
     * Test case for TIKA-332
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-332">TIKA-332</a>
     */
    public void testHttpEquivCharset() throws Exception {
        String test =
            "<html><head><meta http-equiv=\"content-type\""
            + " content=\"text/html; charset=ISO-8859-1\" />"
            + "<title>the name is \u00e1ndre</title>"
            + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-334
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-334">TIKA-334</a>
     */
    public void testDetectOfCharset() throws Exception {
        String test =
            "<html><head><title>\u017d</title></head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("\u017d", metadata.get(Metadata.TITLE));
    }

    /**
     * Test case for TIKA-341
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-XXX">TIKA-XXX</a>
     */
    public void testUsingCharsetInContentTypeHeader() throws Exception {
        final String test =
            "<html><head><title>the name is \u00e1ndre</title></head>"
            + "<body></body></html>";

        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

}
