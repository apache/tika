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
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
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
                "Title : Test Indexation Html", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));
        
        assertEquals("51.2312", metadata.get(Geographic.LATITUDE));
        assertEquals("-5.1987", metadata.get(Geographic.LONGITUDE));

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
        assertEquals("XHTML test document", metadata.get(TikaCoreProperties.TITLE));

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
                        if (name.equals("a") && atts.getValue("", "href") != null) {
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
                new ByteArrayInputStream(test.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-892
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-892">TIKA-892</a>
     */
    public void testHtml5Charset() throws Exception {
        String test =
                "<html><head><meta charset=\"ISO-8859-15\" />"
                + "<title>the name is \u00e1ndre</title>"
                + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("ISO-8859-1")),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));
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
        assertEquals("\u017d", metadata.get(TikaCoreProperties.TITLE));
    }

    /**
     * Test case for TIKA-341
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-341">TIKA-341</a>
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
                new ByteArrayInputStream(test.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for HTML content like
     * "&gt;div&lt;foo&gt;br&lt;bar&gt;/div&gt;" that should result
     * in three whitespace-separated tokens "foo", "bar" and "baz" instead
     * of a single token "foobarbaz".
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-343">TIKA-343</a>
     */
    public void testLineBreak() throws Exception {
        String test = "<html><body><div>foo<br>bar</div>baz</body></html>";
        String text = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes("US-ASCII")));
        String[] parts = text.trim().split("\\s+");
        assertEquals(3, parts.length);
        assertEquals("foo", parts[0]);
        assertEquals("bar", parts[1]);
        assertEquals("baz", parts[2]);
    }

    /**
     * Test case for TIKA-339: Don't use language returned by CharsetDetector
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-339">TIKA-339</a>
     */
    public void testIgnoreCharsetDetectorLanguage() throws Exception {
        String test = "<html><title>Simple Content</title><body></body></html>";
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_LANGUAGE, "en");
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());

        assertEquals("en", metadata.get(Metadata.CONTENT_LANGUAGE));
    }

    /**
     * Test case for TIKA-349
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-349">TIKA-349</a>
     */
    public void testHttpEquivCharsetFunkyAttributes() throws Exception {
        String test1 =
            "<html><head><meta http-equiv=\"content-type\""
            + " content=\"text/html; charset=ISO-8859-15; charset=iso-8859-15\" />"
            + "<title>the name is \u00e1ndre</title>"
            + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test1.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));

        // Some HTML pages have errors like ';;' versus '; ' as separator
        String test2 =
            "<html><head><meta http-equiv=\"content-type\""
            + " content=\"text/html;;charset=ISO-8859-15\" />"
            + "<title>the name is \u00e1ndre</title>"
            + "</head><body></body></html>";
        metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test2.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-350
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-350">TIKA-350</a>
     */
    public void testUsingFunkyCharsetInContentTypeHeader() throws Exception {
        final String test =
            "<html><head><title>the name is \u00e1ndre</title></head>"
            + "<body></body></html>";

        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "charset=ISO-8859-1;text/html");
        new HtmlParser().parse (
                new ByteArrayInputStream(test.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }


    /**
     * Test case for TIKA-357
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-357">TIKA-357</a>
     */
    public void testMetaHttpEquivWithLotsOfPreambleText() throws Exception {
        String path = "/test-documents/big-preamble.html";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                new BodyContentHandler(),  metadata, new ParseContext());

        assertEquals("windows-1251", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-420
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-420">TIKA-420</a>
     */
    public void testBoilerplateRemoval() throws Exception {
        String path = "/test-documents/boilerplate.html";
        
        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                new BoilerpipeContentHandler(handler),  metadata, new ParseContext());
        
        String content = handler.toString();
        assertTrue(content.startsWith("This is the real meat"));
        assertTrue(content.endsWith("This is the end of the text.\n"));
        assertFalse(content.contains("boilerplate"));
        assertFalse(content.contains("footer"));
    }
    
    
    /**
     * Test case for TIKA-478. Don't emit <head> sub-elements inside of <body>.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-478">TIKA-478</a>
     */
    public void testElementOrdering() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<meta http-equiv=\"content-type\" content=\"text/html\">" +
        "<link rel=\"next\" href=\"next.html\" />" +
        "</head><body><p>Simple Content</p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // Title element in <head> section
        assertTrue(Pattern.matches("(?s)<html.*<head>.*<title>Title</title>.*</head>.*$", result));

        // No meta elements in body
        assertFalse(Pattern.matches("(?s).*<body>.*<meta. *</body>.*$", result));
        
        // meta elements should show up in <head> section
        assertTrue(Pattern.matches("(?s)<html.*<head>.*<meta .*</head>.*$", result));
        
        // No link elements in body
        assertFalse(Pattern.matches("(?s).*<body>.*<link .*</body>.*$", result));
        
        // link element should be in <head> section
        assertTrue(Pattern.matches("(?s)<html.*<head>.*<link .*</head>.*$", result));
        
        // There should be ending elements.
        assertTrue(Pattern.matches("(?s).*</body>.*</html>$", result));

    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testImgUrlExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><body><img src=\"image.jpg\" /></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <img> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*src=\"http://domain.com/image.jpg\".*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testFrameSrcExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><frameset><frame src=\"frame.html\" /></frameset></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <frame> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"http://domain.com/frame.html\"/>.*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testIFrameSrcExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><body><iframe src =\"framed.html\" width=\"100%\" height=\"300\">" +
        "<p>Your browser doesn't support iframes!</p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <iframe> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<iframe .* src=\"http://domain.com/framed.html\".*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testAreaExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><body><p><map name=\"map\" id=\"map\">" +
        "<area shape=\"rect\" href=\"map.html\" alt=\"\" />" +
        "</map></p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <map> tag should exist, with <area> tag with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<map .*<area .* href=\"http://domain.com/map.html\".*</map>.*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testObjectExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><body><p><object data=\"object.data\" type=\"text/html\">" +
        "<param name=\"name\" value=\"value\" />" +
        "</object></p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <object> tag should exist with fully resolved URLs
        assertTrue(
              "<object> tag not correctly found in:\n" + result,
              Pattern.matches("(?s).*<object data=\"http://domain.com/object.data\".*<param .* name=\"name\" value=\"value\"/>.*</object>.*$", result)
        );
    }

    /**
     * Test case for change related to TIKA-463. Verify proper handling of <meta> tags.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    public void testMetaTagHandling() throws Exception {
        final String test = "<html><body><h1>header</h1><p>some text</p></body></html>";

        Metadata metadata = new Metadata();
        metadata.add("Content-Type", "text/html; charset=utf-8");
        metadata.add("Language", null);
        
        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), metadata, new ParseContext());

        String result = sw.toString();

        // <meta> tag for Content-Type should exist, but nothing for Language
        assertTrue(Pattern.matches("(?s).*<meta name=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>.*$", result));
        assertFalse(Pattern.matches("(?s).*<meta name=\"Language\".*$", result));
    }

    /**
     * Test case for TIKA-457. Better handling for broken HTML that has <frameset> inside of <body>.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-457">TIKA-457</a>
     */
    public void testBrokenFrameset() throws Exception {
        final String test1 = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "</head><body><frameset><frame src=\"frame.html\" /></frameset></body></html>";

        StringWriter sw1 = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test1.getBytes("UTF-8")),
                makeHtmlTransformer(sw1), new Metadata(), new ParseContext());

        String result = sw1.toString();
        
        // <frame> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"http://domain.com/frame.html\"/>.*$", result));
        
        // <body> tag should not exist.
        assertFalse(Pattern.matches("(?s).*<body>.*$", result));

        // Test the example from the Nutch project.
        final String test2 = "<html><head><title> my title </title></head><body>" +
        "<frameset rows=\"20,*\"><frame src=\"top.html\"></frame>" +
        "<frameset cols=\"20,*\"><frame src=\"left.html\"></frame>" +
        "<frame src=\"invalid.html\"/></frame>" +
        "<frame src=\"right.html\"></frame>" +
        "</frameset></frameset></body></html>";

        StringWriter sw2 = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test2.getBytes("UTF-8")),
                makeHtmlTransformer(sw2), new Metadata(), new ParseContext());

        result = sw2.toString();
        
        // <frame> tags should exist, with relative URL (no base element specified)
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"top.html\"/>.*$", result));
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"left.html\"/>.*$", result));
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"invalid.html\"/>.*$", result));
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"right.html\"/>.*$", result));

        // <body> tag should not exist.
        assertFalse(Pattern.matches("(?s).*<body>.*$", result));
    }

    /**
     * Test case for TIKA-480: fix NPE when using BodyContentHandler or HtmlTransformer
     * as delegate for BoilerpipeContentHandler
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-480">TIKA-480</a>
     */
    public void testBoilerplateDelegation() throws Exception {
        String path = "/test-documents/boilerplate.html";
        
        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                makeHtmlTransformer(sw),  metadata, new ParseContext());
        
        String content = sw.toString();
        
        // Should have <html>, <head>, <title>, <body> elements
        assertTrue(Pattern.matches("(?s).*<html xmlns=\"http://www.w3.org/1999/xhtml\">.*</html>.*$", content));
        assertTrue(Pattern.matches("(?s).*<head>.*</head>.*$", content));
        assertTrue(Pattern.matches("(?s).*<title>Title</title>.*$", content));
        assertTrue(Pattern.matches("(?s).*<body>.*</body>.*$", content));
    }
    
    /**
     * Test case for TIKA-481. Verify href in <link> is resolved.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-481">TIKA-481</a>
     */
    public void testLinkHrefResolution() throws Exception {
        final String test = "<html><head><title>Title</title>" +
        "<base href=\"http://domain.com\" />" +
        "<link rel=\"next\" href=\"next.html\" />" +
        "</head><body></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes("UTF-8")),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();
        
        // <link> tag should exist in <head>, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<head>.*<link rel=\"next\" href=\"http://domain.com/next.html\"/>.*</head>.*$", result));
    }
    

    /**
     * Create ContentHandler that transforms SAX events into textual HTML output,
     * and writes it out to <writer> - typically this is a StringWriter.
     * 
     * @param writer Where to write resulting HTML text.
     * @return ContentHandler suitable for passing to parse() methods.
     * @throws Exception
     */
    private ContentHandler makeHtmlTransformer(Writer writer) throws Exception {
        SAXTransformerFactory factory = (SAXTransformerFactory)SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "utf-8");
        handler.setResult(new StreamResult(writer));
        return handler;
    }
    
    /**
     * Test case for TIKA-564. Support returning markup from BoilerpipeContentHandler.
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-564">TIKA-564</a>
     */
    public void testBoilerplateWithMarkup() throws Exception {
        String path = "/test-documents/boilerplate.html";
        
        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        ContentHandler ch = makeHtmlTransformer(sw);
        BoilerpipeContentHandler bpch = new BoilerpipeContentHandler(ch);
        bpch.setIncludeMarkup(true);
        
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                bpch,  metadata, new ParseContext());
        
        String content = sw.toString();
        assertTrue("Has empty table elements", content.contains("<body><table><tr><td><table><tr><td>"));
        assertTrue("Has empty a element", content.contains("<a shape=\"rect\" href=\"Main.php\"/>"));
        assertTrue("Has real content", content.contains("<p>This is the real meat"));
        assertTrue("Ends with appropriate HTML", content.endsWith("</p></body></html>"));
        assertFalse(content.contains("boilerplate"));
        assertFalse(content.contains("footer"));
    }

    /**
     * Test case for TIKA-434 - Pushback buffer overflow in TagSoup
     */
    public void testPushback() throws IOException, TikaException {
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream("/test-documents/tika434.html"), new Metadata());


        assertNotNull(content);
    }

    /**
     * Test case for TIKA-869
     * IdentityHtmlMapper needs to lower-case tag names.
     * 
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-869">TIKA-869</a>
     */
    public void testIdentityMapper() throws Exception {
        final String html = "<html><head><title>Title</title></head>" +
                "<body></body></html>";
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);

        StringWriter sw = new StringWriter();

        new HtmlParser().parse (
                new ByteArrayInputStream(html.getBytes("UTF-8")),
                makeHtmlTransformer(sw),  metadata, parseContext);
        
        String result = sw.toString();
        // Make sure we don't get <body><BODY/></body>
        assertTrue(Pattern.matches("(?s).*<body/>.*$", result));
    }
    
    /**
     * Test case for TIKA-889
     * XHTMLContentHandler wont emit newline when html element matches ENDLINE set.
     * 
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-889">TIKA-889</a>
     */
    public void testNewlineAndIndent() throws Exception {
        final String html = "<html><head><title>Title</title></head>" +
                "<body><ul><li>one</li></ul></body></html>";

        BodyContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                new ByteArrayInputStream(html.getBytes("UTF-8")),
                handler,  new Metadata(), new ParseContext());
        
        // Make sure we get <tab>, "one", newline, newline
        String result = handler.toString();
        
        assertTrue(Pattern.matches("\tone\n\n", result));
    }

    /**
     * Test case for TIKA-983:  HTML parser should add Open Graph meta tag data to Metadata returned by parser
     * 
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-983">TIKA-983</a>
     */
    public void testOpenGraphMetadata() throws Exception {
        String test1 =
            "<html><head><meta property=\"og:description\""
            + " content=\"some description\" />"
            + "<title>hello</title>"
            + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse (
                new ByteArrayInputStream(test1.getBytes("ISO-8859-1")),
                new BodyContentHandler(),  metadata, new ParseContext());
        assertEquals("some description", metadata.get("og:description"));

    }

    // TIKA-1011
    public void testUserDefinedCharset() throws Exception {
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream("/test-documents/testUserDefinedCharset.mhtml"), new Metadata());
        assertNotNull(content);
    }
}
