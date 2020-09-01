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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Geographic;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.LinkContentHandler;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.ccil.cowan.tagsoup.HTMLSchema;
import org.ccil.cowan.tagsoup.Schema;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HtmlParserTest extends TikaTest {

    @Test
    public void testParseAscii() throws Exception {
        String path = "/test-documents/testHTML.html";
        final StringWriter href = new StringWriter();
        final StringWriter name = new StringWriter();
        ContentHandler body = new BodyContentHandler();
        Metadata metadata = new Metadata();
        try (InputStream stream = HtmlParserTest.class.getResourceAsStream(path)) {
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

    @Test
    @Ignore("The file 'testXHTML_utf8.html' is not available for testing")
    public void XtestParseUTF8() throws IOException, SAXException, TikaException {
        String path = "/test-documents/testXHTML_utf8.html";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream(path), metadata);

        assertTrue("Did not contain expected text:"
                + "Title : Tilte with UTF-8 chars √∂√§√•", content
                .contains("Title : Tilte with UTF-8 chars √∂√§√•"));

        assertTrue("Did not contain expected text:"
                + "Content with UTF-8 chars", content
                .contains("Content with UTF-8 chars"));

        assertTrue("Did not contain expected text:" + "√•√§√∂", content
                .contains("√•√§√∂"));
    }

    @Test
    public void testXhtmlParsing() throws Exception {
        String path = "/test-documents/testXHTML.html";
        Metadata metadata = new Metadata();
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream(path), metadata);

        //can't specify charset because default differs between OS's
        assertTrue(metadata.get(Metadata.CONTENT_TYPE).startsWith("application/xhtml+xml; charset="));
        assertEquals("XHTML test document", metadata.get(TikaCoreProperties.TITLE));

        assertEquals("Tika Developers", metadata.get("Author"));
        assertEquals("5", metadata.get("refresh"));
        assertContains("ability of Apache Tika", content);
        assertContains("extract content", content);
        assertContains("an XHTML document", content);
    }

    @Test
    public void testParseEmpty() throws Exception {
        ContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                new ByteArrayInputStream(new byte[0]),
                handler, new Metadata(), new ParseContext());
        assertEquals("", handler.toString());
    }

    /**
     * Test case for TIKA-210
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-210">TIKA-210</a>
     */
    @Test
    public void testCharactersDirectlyUnderBodyElement() throws Exception {
        String test = "<html><body>test</body></html>";
        String content = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes(UTF_8)));
        assertEquals("test", content);
    }

    /**
     * Test case for TIKA-287
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-287">TIKA-287</a>
     */
    @Test
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
                new ByteArrayInputStream(test.getBytes(UTF_8)),
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
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-268">TIKA-268</a>
     */
    @Test
    public void testWhitespaceBetweenTableCells() throws Exception {
        String test =
                "<html><body><table><tr><td>a</td><td>b</td></table></body></html>";
        String content = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes(UTF_8)));
        assertContains("a", content);
        assertContains("b", content);
        assertFalse(content.contains("ab"));
    }

    /**
     * Test case for TIKA-332
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-332">TIKA-332</a>
     */
    @Test
    public void testHttpEquivCharset() throws Exception {
        String test =
                "<html><head><meta http-equiv=\"content-type\""
                        + " content=\"text/html; charset=ISO-8859-1\" />"
                        + "<title>the name is \u00e1ndre</title>"
                        + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-892
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-892">TIKA-892</a>
     */
    @Test
    public void testHtml5Charset() throws Exception {
        String test =
                "<html><head><meta charset=\"ISO-8859-15\" />"
                        + "<title>the name is \u00e1ndre</title>"
                        + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-334
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-334">TIKA-334</a>
     */
    @Test
    public void testDetectOfCharset() throws Exception {
        String test =
                "<html><head><title>\u017d</title></head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("\u017d", metadata.get(TikaCoreProperties.TITLE));
    }

    /**
     * Test case for TIKA-341
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-341">TIKA-341</a>
     */
    @Test
    public void testUsingCharsetInContentTypeHeader() throws Exception {
        final String test =
                "<html><head><title>the name is \u00e1ndre</title></head>"
                        + "<body></body></html>";

        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html; charset=ISO-8859-1");
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
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
    @Test
    public void testLineBreak() throws Exception {
        String test = "<html><body><div>foo<br>bar</div>baz</body></html>";
        String text = new Tika().parseToString(
                new ByteArrayInputStream(test.getBytes(US_ASCII)));
        String[] parts = text.trim().split("\\s+");
        assertEquals(3, parts.length);
        assertEquals("foo", parts[0]);
        assertEquals("bar", parts[1]);
        assertEquals("baz", parts[2]);
    }

    /**
     * Test case for TIKA-339: Don't use language returned by CharsetDetector
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-339">TIKA-339</a>
     */
    @Test
    public void testIgnoreCharsetDetectorLanguage() throws Exception {
        String test = "<html><title>Simple Content</title><body></body></html>";
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_LANGUAGE, "en");
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("en", metadata.get(Metadata.CONTENT_LANGUAGE));
    }

    /**
     * Test case for TIKA-349
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-349">TIKA-349</a>
     */
    @Test
    public void testHttpEquivCharsetFunkyAttributes() throws Exception {
        String test1 =
                "<html><head><meta http-equiv=\"content-type\""
                        + " content=\"text/html; charset=ISO-8859-15; charset=iso-8859-15\" />"
                        + "<title>the name is \u00e1ndre</title>"
                        + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test1.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));

        // Some HTML pages have errors like ';;' versus '; ' as separator
        String test2 =
                "<html><head><meta http-equiv=\"content-type\""
                        + " content=\"text/html;;charset=ISO-8859-15\" />"
                        + "<title>the name is \u00e1ndre</title>"
                        + "</head><body></body></html>";
        metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test2.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-15", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-350
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-350">TIKA-350</a>
     */
    @Test
    public void testUsingFunkyCharsetInContentTypeHeader() throws Exception {
        final String test =
                "<html><head><title>the name is \u00e1ndre</title></head>"
                        + "<body></body></html>";

        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("UTF-8", metadata.get(Metadata.CONTENT_ENCODING));

        metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "charset=ISO-8859-1;text/html");
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("ISO-8859-1", metadata.get(Metadata.CONTENT_ENCODING));
    }


    /**
     * Test case for TIKA-357
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-357">TIKA-357</a>
     */
    @Test
    public void testMetaHttpEquivWithLotsOfPreambleText() throws Exception {
        String path = "/test-documents/big-preamble.html";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("windows-1251", metadata.get(Metadata.CONTENT_ENCODING));
    }

    /**
     * Test case for TIKA-420
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-420">TIKA-420</a>
     */
    @Test
    public void testBoilerplateRemoval() throws Exception {
        String path = "/test-documents/boilerplate.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                new BoilerpipeContentHandler(handler), metadata, new ParseContext());

        String content = handler.toString();
        assertTrue(content.startsWith("This is the real meat"));
        assertTrue(content.endsWith("This is the end of the text.\n"));
        assertFalse(content.contains("boilerplate"));
        assertFalse(content.contains("footer"));
    }

    /**
     * Test case for TIKA-478. Don't emit <head> sub-elements inside of <body>.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-478">TIKA-478</a>
     */
    @Test
    public void testElementOrdering() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<meta http-equiv=\"content-type\" content=\"text/html\">" +
                "<link rel=\"next\" href=\"next.html\" />" +
                "</head><body><p>Simple Content</p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
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
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testImgUrlExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><body><img src=\"image.jpg\" /></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();

        // <img> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*src=\"http://domain.com/image.jpg\".*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testFrameSrcExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><frameset><frame src=\"frame.html\" /></frameset></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();

        // <frame> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<frame .* src=\"http://domain.com/frame.html\"/>.*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testIFrameSrcExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><body><iframe src =\"framed.html\" width=\"100%\" height=\"300\">" +
                "<p>Your browser doesn't support iframes!</p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();

        // <iframe> tag should exist, with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<iframe .* src=\"http://domain.com/framed.html\".*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testAreaExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><body><p><map name=\"map\" id=\"map\">" +
                "<area shape=\"rect\" href=\"map.html\" alt=\"\" />" +
                "</map></p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                makeHtmlTransformer(sw), new Metadata(), new ParseContext());

        String result = sw.toString();

        // <map> tag should exist, with <area> tag with fully resolved URL
        assertTrue(Pattern.matches("(?s).*<map .*<area .* href=\"http://domain.com/map.html\".*</map>.*$", result));
    }

    /**
     * Test case for TIKA-463. Don't skip elements that have URLs.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testObjectExtraction() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><body><p><object data=\"object.data\" type=\"text/html\">" +
                "<param name=\"name\" value=\"value\" />" +
                "</object></p></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
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
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-463">TIKA-463</a>
     */
    @Test
    public void testMetaTagHandling() throws Exception {
        final String test = "<html><body><h1>header</h1><p>some text</p></body></html>";

        Metadata metadata = new Metadata();
        metadata.add("Content-Type", "text/html; charset=utf-8");
        metadata.add("Language", null);

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                makeHtmlTransformer(sw), metadata, new ParseContext());

        String result = sw.toString();

        // <meta> tag for Content-Type should exist, but nothing for Language
        assertTrue(Pattern.matches("(?s).*<meta name=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>.*$", result));
        assertFalse(Pattern.matches("(?s).*<meta name=\"Language\".*$", result));
    }

    /**
     * Test case for TIKA-457. Better handling for broken HTML that has <frameset> inside of <body>.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-457">TIKA-457</a>
     */
    @Test
    public void testBrokenFrameset() throws Exception {
        final String test1 = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "</head><body><frameset><frame src=\"frame.html\" /></frameset></body></html>";

        StringWriter sw1 = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test1.getBytes(UTF_8)),
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
                new ByteArrayInputStream(test2.getBytes(UTF_8)),
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
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-480">TIKA-480</a>
     */
    @Test
    public void testBoilerplateDelegation() throws Exception {
        String path = "/test-documents/boilerplate.html";

        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                makeHtmlTransformer(sw), metadata, new ParseContext());

        String content = sw.toString();

        // Should have <html>, <head>, <title>, <body> elements
        assertTrue(Pattern.matches("(?s).*<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\">.*</html>.*$", content));
        assertTrue(Pattern.matches("(?s).*<head>.*</head>.*$", content));
        assertTrue(Pattern.matches("(?s).*<title>Title</title>.*$", content));
        assertTrue(Pattern.matches("(?s).*<body>.*</body>.*$", content));
    }

    /**
     * Test case for TIKA-481. Verify href in <link> is resolved.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-481">TIKA-481</a>
     */
    @Test
    public void testLinkHrefResolution() throws Exception {
        final String test = "<html><head><title>Title</title>" +
                "<base href=\"http://domain.com\" />" +
                "<link rel=\"next\" href=\"next.html\" />" +
                "</head><body></body></html>";

        StringWriter sw = new StringWriter();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
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
        SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "html");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.getTransformer().setOutputProperty(OutputKeys.ENCODING, "utf-8");
        handler.setResult(new StreamResult(writer));
        return handler;
    }

    /**
     * Test case for TIKA-564. Support returning markup from BoilerpipeContentHandler.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-564">TIKA-564</a>
     */
    @Test
    public void testBoilerplateWithMarkup() throws Exception {
        String path = "/test-documents/boilerplate.html";

        Metadata metadata = new Metadata();
        StringWriter sw = new StringWriter();
        ContentHandler ch = makeHtmlTransformer(sw);
        BoilerpipeContentHandler bpch = new BoilerpipeContentHandler(ch);
        bpch.setIncludeMarkup(true);

        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                bpch, metadata, new ParseContext());

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
    @Test
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
    @Test
    public void testIdentityMapper() throws Exception {
        final String html = "<html><head><title>Title</title></head>" +
                "<body></body></html>";
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);

        StringWriter sw = new StringWriter();

        new HtmlParser().parse(
                new ByteArrayInputStream(html.getBytes(UTF_8)),
                makeHtmlTransformer(sw), metadata, parseContext);

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
    @Test
    public void testNewlineAndIndent() throws Exception {
        final String html = "<html><head><title>Title</title></head>" +
                "<body><ul><li>one</li></ul></body></html>";

        BodyContentHandler handler = new BodyContentHandler();
        new HtmlParser().parse(
                new ByteArrayInputStream(html.getBytes(UTF_8)),
                handler, new Metadata(), new ParseContext());

        // Make sure we get <tab>, "one", newline, newline
        String result = handler.toString();

        assertTrue(Pattern.matches("\tone\n\n", result));
    }

    /**
     * Test case for Tika-2100
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-2100">TIKA-2100</a>
     */
    @Test
    public void testHtmlLanguage() throws Exception {
        final String html = "<html lang=\"fr\"></html>";

        StringWriter sw = new StringWriter();
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(html.getBytes(UTF_8)),
                makeHtmlTransformer(sw), metadata, new ParseContext());

        assertEquals("fr", metadata.get(Metadata.CONTENT_LANGUAGE));
        assertTrue("Missing HTML lang attribute",
                Pattern.matches("(?s)<html[^>]* lang=\"fr\".*", sw.toString()));
    }

    /**
     * Test case for TIKA-961
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-961">TIKA-961</a>
     */
    @Test
    public void testBoilerplateWhitespace() throws Exception {
        String path = "/test-documents/boilerplate-whitespace.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();

        BoilerpipeContentHandler bpHandler = new BoilerpipeContentHandler(handler);
        bpHandler.setIncludeMarkup(true);

        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                bpHandler, metadata, new ParseContext());

        String content = handler.toString();

        // Should not contain item_aitem_b
        assertFalse(content.contains("item_aitem_b"));

        // Should contain the two list items with a newline in between.
        assertContains("item_a\nitem_b", content);

        // Should contain 有什么需要我帮你的 (can i help you) without whitespace
        assertContains("有什么需要我帮你的", content);
    }

    /**
     * Test case for TIKA-2683
     *
     * @see <a href="https://issues.apache.org/jira/projects/TIKA/issues/TIKA-2683">TIKA-2683</a>
     */
    @Test
    public void testBoilerplateMissingWhitespace() throws Exception {
        String path = "/test-documents/testBoilerplateMissingSpace.html";

        Metadata metadata = new Metadata();
        BodyContentHandler handler = new BodyContentHandler();

        BoilerpipeContentHandler bpHandler = new BoilerpipeContentHandler(handler);
        bpHandler.setIncludeMarkup(true);

        new HtmlParser().parse(
                HtmlParserTest.class.getResourceAsStream(path),
                bpHandler, metadata, new ParseContext());

        String content = handler.toString();

        // Should contain space between these two words as mentioned in HTML
        assertContains("family Psychrolutidae", content);

        // Shouldn't add new-line chars around brackets; This is not how the HTML look
        assertContains("(Psychrolutes marcidus)", content);
    }

    /**
     * Test case for TIKA-983:  HTML parser should add Open Graph meta tag data to Metadata returned by parser
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-983">TIKA-983</a>
     */
    @Test
    public void testOpenGraphMetadata() throws Exception {
        String test1 =
                "<html><head><meta property=\"og:description\""
                        + " content=\"some description\" />"
                        + "<meta property=\"og:image\" content=\"http://example.com/image1.jpg\" />"
                        + "<meta property=\"og:image\" content=\"http://example.com/image2.jpg\" />"
                        + "<title>hello</title>"
                        + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        new HtmlParser().parse(
                new ByteArrayInputStream(test1.getBytes(ISO_8859_1)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("some description", metadata.get("og:description"));
        assertTrue(metadata.isMultiValued("og:image"));
    }

    // TIKA-1011
    @Test
    public void testUserDefinedCharset() throws Exception {
        String content = new Tika().parseToString(
                HtmlParserTest.class.getResourceAsStream("/test-documents/testUserDefinedCharset.mhtml"), new Metadata());
        assertNotNull(content);
    }

    //TIKA-1001
    @Test
    public void testNoisyMetaCharsetHeaders() throws Exception {
        Tika tika = new Tika();
        String hit = "\u0623\u0639\u0631\u0628";

        for (int i = 1; i <= 4; i++) {
            String fileName = "/test-documents/testHTMLNoisyMetaEncoding_" + i + ".html";
            String content = tika.parseToString(
                    HtmlParserTest.class.getResourceAsStream(fileName));
            assertTrue("testing: " + fileName, content.contains(hit));
        }
    }

    // TIKA-1193
    @Test
    public void testCustomHtmlSchema() throws Exception {
        // Default schema does not allow tables inside anchors
        String test = "<html><body><a><table><tr><td>text</tr></tr></table></a></body></html>";

        Metadata metadata = new Metadata();
        LinkContentHandler linkContentHandler = new LinkContentHandler();

        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                linkContentHandler, metadata, new ParseContext());

        // Expect no anchor text
        assertEquals("", linkContentHandler.getLinks().get(0).getText());

        // We'll change the schema to allow tables inside anchors!
        Schema schema = new HTMLSchema();
        schema.elementType("a", HTMLSchema.M_ANY, 65535, 0);

        ParseContext parseContext = new ParseContext();
        parseContext.set(Schema.class, schema);
        linkContentHandler = new LinkContentHandler();
        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(ISO_8859_1)),
                linkContentHandler, metadata, parseContext);

        // Expect anchor text
        assertEquals("\ttext\n\n", linkContentHandler.getLinks().get(0).getText());
    }

    /**
     * Test case for TIKA-820:  Locator is unset for HTML parser
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-820">TIKA-820</a>
     */
    @Test
    public void testLocator() throws Exception {
        final int line = 0;
        final int col = 1;
        final int[] textPosition = new int[2];

        new HtmlParser().parse(HtmlParserTest.class.getResourceAsStream("/test-documents/testHTML.html"),
                new ContentHandler() {
                    Locator locator;

                    public void setDocumentLocator(Locator locator) {
                        this.locator = locator;
                    }

                    public void startDocument() throws SAXException {
                    }

                    public void endDocument() throws SAXException {
                    }

                    public void startPrefixMapping(String prefix, String uri)
                            throws SAXException {
                    }

                    public void endPrefixMapping(String prefix)
                            throws SAXException {
                    }

                    public void startElement(String uri, String localName,
                                             String qName, Attributes atts) throws SAXException {
                    }

                    public void endElement(String uri, String localName,
                                           String qName) throws SAXException {
                    }

                    public void characters(char[] ch, int start, int length)
                            throws SAXException {
                        String text = new String(ch, start, length);
                        if (text.equals("Test Indexation Html") && locator != null) {
                            textPosition[line] = locator.getLineNumber();
                            textPosition[col] = locator.getColumnNumber();
                        }
                    }

                    public void ignorableWhitespace(char[] ch, int start,
                                                    int length) throws SAXException {
                    }

                    public void processingInstruction(String target, String data)
                            throws SAXException {
                    }

                    public void skippedEntity(String name) throws SAXException {
                    }
                },
                new Metadata(),
                new ParseContext());

        // The text occurs at line 24 (if lines start at 0) or 25 (if lines start at 1).
        assertEquals(24, textPosition[line]);
        // The column reported seems fuzzy, just test it is close enough.
        assertTrue(Math.abs(textPosition[col] - 47) < 10);
    }


    /**
     * Test case for TIKA-1303: HTML parse should use the first title tag to set value in meta data
     * and ignore any subsequent title tags found in HTML.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-1303">TIKA-1303</a>
     */
    @Test
    public void testFirstTitleValueisSetToMetadata() throws Exception {
        String test = "<html><title>Simple Content</title><body><h1></h1>"
                + "<title>TitleToIgnore</title></body></html>";
        Metadata metadata = new Metadata();

        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());

        //Expecting first title to be set in meta data and second one to be ignored.
        assertEquals("Simple Content", metadata.get(TikaCoreProperties.TITLE));
    }

    @Test
    public void testMisleadingMetaContentTypeTags() throws Exception {
        //TIKA-1519

        String test = "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-ELEVEN\">" +
                "</head><title>title</title><body>body</body></html>";
        Metadata metadata = new Metadata();

        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("text/html; charset=UTF-ELEVEN", metadata.get(TikaCoreProperties.CONTENT_TYPE_HINT));
        assertEquals("text/html; charset=ISO-8859-1", metadata.get(Metadata.CONTENT_TYPE));

        test = "<html><head><meta http-equiv=\"content-type\" content=\"application/pdf\">" +
                "</head><title>title</title><body>body</body></html>";
        metadata = new Metadata();

        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("application/pdf", metadata.get(TikaCoreProperties.CONTENT_TYPE_HINT));
        assertEquals("text/html; charset=ISO-8859-1", metadata.get(Metadata.CONTENT_TYPE));

        //test two content values
        test = "<html><head><meta http-equiv=\"content-type\" content=\"application/pdf\" content=\"application/ms-word\">" +
                "</head><title>title</title><body>body</body></html>";
        metadata = new Metadata();

        new HtmlParser().parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());
        assertEquals("application/ms-word", metadata.get(TikaCoreProperties.CONTENT_TYPE_HINT));
        assertEquals("text/html; charset=ISO-8859-1", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testXHTMLWithMisleading() throws Exception {
        //first test an acceptable XHTML header with http-equiv tags
        String test = "<?xml version=\"1.0\" ?>" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\" />\n" +
                "<title>title</title></head><body>body</body></html>";
        Metadata metadata = new Metadata();
        AUTO_DETECT_PARSER.parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("text/html; charset=iso-8859-1", metadata.get(TikaCoreProperties.CONTENT_TYPE_HINT));
        assertEquals("application/xhtml+xml; charset=ISO-8859-1", metadata.get(Metadata.CONTENT_TYPE));

        test = "<?xml version=\"1.0\" ?>" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-NUMBER_SEVEN\" />\n" +
                "<title>title</title></head><body>body</body></html>";
        metadata = new Metadata();
        AUTO_DETECT_PARSER.parse(
                new ByteArrayInputStream(test.getBytes(UTF_8)),
                new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("text/html; charset=iso-NUMBER_SEVEN", metadata.get(TikaCoreProperties.CONTENT_TYPE_HINT));
        assertEquals("application/xhtml+xml; charset=ISO-8859-1", metadata.get(Metadata.CONTENT_TYPE));

    }

    @Test
    public void testScriptSrc() throws Exception {
        String url = "http://domain.com/logic.js";
        String scriptInBody =
                "<html><body><script src=\"" + url + "\"></script></body></html>";
        String scriptInHead =
                "<html><head><script src=\"" + url + "\"></script></head></html>";

        assertScriptLink(scriptInBody, url);
        assertScriptLink(scriptInHead, url);
    }

    private void assertScriptLink(String html, String url) throws Exception {
        // IdentityHtmlMapper is needed to extract <script> tags
        ParseContext context = new ParseContext();
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html");

        final List<String> links = new ArrayList<String>();
        new HtmlParser().parse(
                new ByteArrayInputStream(html.getBytes(UTF_8)),
                new DefaultHandler() {
                    @Override
                    public void startElement(
                            String u, String l, String name, Attributes atts) {
                        if (name.equals("script") && atts.getValue("", "src") != null) {
                            links.add(atts.getValue("", "src"));
                        }
                    }
                },
                metadata,
                context);

        assertEquals(1, links.size());
        assertEquals(url, links.get(0));
    }

    @Test
    public void testAllHeadElements() throws Exception {
        //TIKA-1980
        // IdentityHtmlMapper is needed to extract <script> tags
        ParseContext context = new ParseContext();
        context.set(HtmlMapper.class, IdentityHtmlMapper.INSTANCE);
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/html");

        final Map<String, Integer> tagFrequencies = new HashMap<>();

        String path = "/test-documents/testHTML_head.html";
        try (InputStream stream = HtmlParserTest.class.getResourceAsStream(path)) {
            ContentHandler tagCounter = new DefaultHandler() {
                @Override
                public void startElement(
                        String uri, String local, String name, Attributes attributes)
                        throws SAXException {

                    int count = tagFrequencies.containsKey(name) ? tagFrequencies.get(name) : 0;
                    tagFrequencies.put(name, count + 1);
                }
            };
            new HtmlParser().parse(stream, tagCounter, metadata, context);
        }

        assertEquals(1, (int)tagFrequencies.get("title"));
        assertEquals(9, (int)tagFrequencies.get("meta"));
        assertEquals(12, (int)tagFrequencies.get("link"));
        assertEquals(6, (int)tagFrequencies.get("script"));
    }

    @Test
    public void testSkippingCommentsInEncodingDetection() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append(" ");
        }
        byte[] bytes = new String("<html><head>" +
                "<!--<meta http-equiv=\"Content-Type\" content=\"text/html; charset=ISO-8859-1\"> -->\n" +
                "   <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />"+
                "</head>"+sb.toString()+
                "<body>"+
                "有什么需要我帮你的" +
                "</body></html>").getBytes(StandardCharsets.UTF_8);
        XMLResult r = getXML(new ByteArrayInputStream(bytes), AUTO_DETECT_PARSER, new Metadata());
        assertContains("有什么需要我帮你的", r.xml);
    }

    @Test
    @Ignore("until we fix TIKA-1896")
    public void testBadScript() throws Exception {
        String xml = getXML("testHTMLBadScript.html").xml;
        assertContains("This is a test", xml);
        assertNotContained("cool", xml);
    }

    @Test
    public void testGoodScript() throws Exception {
        String xml = getXML("testHTMLGoodScript.html").xml;
        assertContains("This is a test", xml);
        assertNotContained("cool", xml);
    }

    @Test
    public void testExtractScript() throws Exception {
        HtmlParser p = new HtmlParser();
        p.setExtractScripts(true);
        //TIKA-2550 -- make absolutely sure that macros are still extracted
        //with the ToTextHandler
        List<Metadata> metadataList = getRecursiveMetadata("testHTMLGoodScript.html",
                p, BasicContentHandlerFactory.HANDLER_TYPE.TEXT);
        assertEquals(2, metadataList.size());
        assertEquals("MACRO", metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertContains("cool",
                metadataList.get(1).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertNotContained("cool", metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
    }

    @Test
    public void testConfigExtractScript() throws Exception {
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/html/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testHTMLGoodScript.html", p);
        assertEquals(2, metadataList.size());
        assertEquals("MACRO", metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
        assertContains("cool",
                metadataList.get(1).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));
        assertNotContained("cool", metadataList.get(0).get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT));

    }



    @Test
    public void testMultiThreadingEncodingDetection() throws Exception {
        List<EncodingDetector> detectors = new ArrayList<>();
        ServiceLoader loader =
                new ServiceLoader(AutoDetectReader.class.getClassLoader());
        detectors.addAll(loader.loadServiceProviders(EncodingDetector.class));
        for (EncodingDetector detector : detectors) {
            testDetector(detector);
        }
    }

    private void testDetector(EncodingDetector detector) throws Exception {
        Path testDocs = Paths.get(this.getClass().getResource("/test-documents").toURI());
        List<Path> tmp = new ArrayList<>();
        Map<Path, String> encodings = new ConcurrentHashMap<>();
        File[] testDocArray = testDocs.toFile().listFiles();
        assertNotNull("no test docs??", testDocArray);
        for (File file : testDocArray) {
            if (file.getName().endsWith(".txt") || file.getName().endsWith(".html")) {
                    String encoding = getEncoding(detector, file.toPath());
                    tmp.add(file.toPath());
                    encodings.put(file.toPath(), encoding);
            }
        }
        ArrayBlockingQueue<Path> paths = new ArrayBlockingQueue<>(tmp.size());
        paths.addAll(tmp);
        int numThreads = paths.size()+1;
        ExecutorService ex = Executors.newFixedThreadPool(numThreads);
        CompletionService<String> completionService =
                new ExecutorCompletionService<>(ex);

        for (int i = 0; i < numThreads; i++) {
            completionService.submit(new EncodingDetectorRunner(paths, encodings, detector));
        }
        int completed = 0;
        while (completed < numThreads) {
            Future<String> future = completionService.take();

            if (future.isDone() &&
                    //will trigger ExecutionException if an IOException
                    //was thrown during call
                    EncodingDetectorRunner.DONE.equals(future.get())) {
                completed++;
            }
        }
    }

    private class EncodingDetectorRunner implements Callable<String> {

        final static String DONE = "done";
        private final ArrayBlockingQueue<Path> paths;
        private final Map<Path, String> encodings;
        private final EncodingDetector detector;
        private EncodingDetectorRunner(ArrayBlockingQueue<Path> paths,
                                       Map<Path, String> encodings, EncodingDetector detector) {
            this.paths = paths;
            this.encodings = encodings;
            this.detector = detector;
        }

        @Override
        public String call() throws IOException {
            for (int i = 0; i < encodings.size(); i++) {
                Path p = paths.poll();
                if (p == null) {
                    return DONE;
                }
                String detectedEncoding = getEncoding(detector, p);
                String trueEncoding = encodings.get(p);
                assertEquals( "detector class="+detector.getClass() + " : file=" + p.toString(),
                        trueEncoding, detectedEncoding);

            }
            return DONE;
        }
    }

    public String getEncoding(EncodingDetector detector, Path p) throws IOException {
        try (InputStream is = TikaInputStream.get(p)) {
            Charset charset = detector.detect(is, new Metadata());
            if (charset == null) {
                return "NULL";
            } else {
                return charset.toString();
            }
        }
    }

    @Test
    public void testCharsetsNotSupportedByIANA() throws Exception {
        assertContains("This is a sample text",
                getXML("testHTML_charset_utf8.html").xml);

        assertContains("This is a sample text",
                getXML("testHTML_charset_utf16le.html").xml);

    }

    @Test
    public void testSkippingDataURIInScriptNode() throws Exception {
        //TIKA-2759 skip data: uri element if inside a script
        //default behavior
        List<Metadata> metadataList = getRecursiveMetadata("testHTML_embedded_data_uri_js.html");
        assertEquals(1, metadataList.size());
        assertNotContained("alert( 'Hello, world!' );",
                metadataList.get(0).get(RecursiveParserWrapperHandler.TIKA_CONTENT));

        //make sure to include it if a user wants scripts to be extracted
        InputStream is = getClass().getResourceAsStream("/org/apache/tika/parser/html/tika-config.xml");
        assertNotNull(is);
        TikaConfig tikaConfig = new TikaConfig(is);
        Parser p = new AutoDetectParser(tikaConfig);
        metadataList = getRecursiveMetadata("testHTML_embedded_data_uri_js.html", p);
        assertEquals(2, metadataList.size());
        assertContains("alert( 'Hello, world!' );",
                metadataList.get(1).get(RecursiveParserWrapperHandler.TIKA_CONTENT));


    }
}
