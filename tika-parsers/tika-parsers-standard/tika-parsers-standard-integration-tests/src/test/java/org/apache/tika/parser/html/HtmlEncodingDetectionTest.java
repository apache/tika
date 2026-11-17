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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.Tika;
import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * HTML charset-detection tests that need the full default EncodingDetector
 * chain (including the meta arbiter).  Live here rather than in
 * {@code tika-parser-html-module} because that module's unit-test scope
 * pulls in only the html and mojibuster base detectors.
 */
public class HtmlEncodingDetectionTest extends TikaTest {

    /** TIKA-892. */
    @Test
    public void testHtml5Charset() throws Exception {
        String test = "<html><head><meta charset=\"ISO-8859-15\" />" +
                "<title>the name is \u00e1ndre</title>" + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test.getBytes(ISO_8859_1))) {
            new JSoupParser().parse(tis,
                    new BodyContentHandler(), metadata, new ParseContext());
        }
        assertEquals("ISO-8859-15", metadata.get(HttpHeaders.CONTENT_ENCODING));
    }

    /** TIKA-349. */
    @Test
    public void testHttpEquivCharsetFunkyAttributes() throws Exception {
        String test1 = "<html><head><meta http-equiv=\"content-type\"" +
                " content=\"text/html; charset=ISO-8859-15; charset=iso-8859-15\" />" +
                "<title>the name is \u00e1ndre</title>" + "</head><body></body></html>";
        Metadata metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test1.getBytes(ISO_8859_1))) {
            new JSoupParser().parse(tis,
                    new BodyContentHandler(), metadata, new ParseContext());
        }
        assertEquals("ISO-8859-15", metadata.get(HttpHeaders.CONTENT_ENCODING));

        // Some HTML pages have errors like ';;' versus '; ' as separator.
        String test2 = "<html><head><meta http-equiv=\"content-type\"" +
                " content=\"text/html;;charset=ISO-8859-15\" />" +
                "<title>the name is \u00e1ndre</title>" + "</head><body></body></html>";
        metadata = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(test2.getBytes(ISO_8859_1))) {
            new JSoupParser().parse(tis,
                    new BodyContentHandler(), metadata, new ParseContext());
        }
        assertEquals("ISO-8859-15", metadata.get(HttpHeaders.CONTENT_ENCODING));
    }

    /** TIKA-357. */
    @Test
    public void testMetaHttpEquivWithLotsOfPreambleText() throws Exception {
        Metadata metadata = new Metadata();
        new JSoupParser().parse(getResourceAsStream("/test-documents/big-preamble.html"),
                new BodyContentHandler(), metadata, new ParseContext());

        assertEquals("windows-1251", metadata.get(HttpHeaders.CONTENT_ENCODING));
    }

    /** TIKA-1001. */
    @Test
    public void testNoisyMetaCharsetHeaders() throws Exception {
        Tika tika = new Tika();
        String hit = "\u0623\u0639\u0631\u0628";

        for (int i = 1; i <= 4; i++) {
            String fileName = "/test-documents/testHTMLNoisyMetaEncoding_" + i + ".html";
            String content = tika.parseToString(getResourceAsStream(fileName));
            assertTrue(content.contains(hit), "testing: " + fileName);
        }
    }

    @Test
    public void testSkippingCommentsInEncodingDetection() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append(' ');
        }
        byte[] bytes = ("<html><head>" +
                "<!--<meta http-equiv=\"Content-Type\" " +
                "content=\"text/html; charset=ISO-8859-1\"> -->\n" +
                "   <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />" +
                "</head>" + sb + "<body>" + "有什么需要我帮你的" + "</body></html>")
                .getBytes(StandardCharsets.UTF_8);
        XMLResult r;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            r = getXML(tis, AUTO_DETECT_PARSER, new Metadata());
        }
        assertContains("有什么需要我帮你的", r.xml);
    }

    /**
     * UTF-8 BOM, ASCII bytes, and a meta that says UTF-16BE (shape of a Common Crawl page).  The
     * visible text is sparse ASCII art, so the junk margin alone is too thin to overrule the meta.
     */
    @Test
    public void testUtf16MetaOnAsciiBytesIsUtf8() throws Exception {
        String html = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"\n"
                + "    \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">\n"
                + "<head>\n"
                + "    <title>Example Studio</title>\n"
                + "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-16be\" />\n"
                + "    <link rel=\"stylesheet\" type=\"text/css\" href=\"style.css\" />\n"
                + "    <meta name=\"keywords\" content=\"example, studio, design, media, art, gallery,"
                + " video, photography\" />\n"
                + "</head>\n"
                + "<body>\n"
                + "    ##<div id=\"page\"> # # ## ## ## ## ## ## # # # # # # # #\n"
                + "    #<br />\n"
                + "    <div id=\"stage\"><br />\n"
                + "    Back soon.\n"
                + "    " + "#<br />".repeat(11) + "\n"
                + "    </div>#\n"
                + "    <div id=\"menu\"></div>#\n"
                + "    </div>#\n"
                + " #<br />\n"
                + "</body>\n"
                + "</html>\n\n";
        byte[] body = html.getBytes(StandardCharsets.US_ASCII);
        // even length: an odd trailing byte would give the UTF-16 decode a U+FFFD and hide the bug
        assertEquals(0, body.length % 2);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        Metadata metadata = new Metadata();
        XMLResult r;
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            r = getXML(tis, AUTO_DETECT_PARSER, metadata);
        }
        assertEquals("UTF-8", r.metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertContains("Back soon.", r.xml);
    }

    /** A real UTF-16 page cannot carry an ASCII-readable meta, so its BOM still decides. */
    @Test
    public void testUtf16BomWithUtf16MetaStaysUtf16() throws Exception {
        String html = "\uFEFF<html><head><meta charset=\"utf-16\"><title>t</title></head>"
                + "<body><p>Ce texte est en UTF-16 avec une marque d\u2019ordre des octets.</p>"
                + "</body></html>";
        Metadata metadata = new Metadata();
        XMLResult r;
        try (TikaInputStream tis = TikaInputStream.get(html.getBytes(StandardCharsets.UTF_16LE))) {
            r = getXML(tis, AUTO_DETECT_PARSER, metadata);
        }
        assertEquals("UTF-16LE", r.metadata.get(HttpHeaders.CONTENT_ENCODING));
        assertContains("marque d\u2019ordre des octets", r.xml);
    }

    /** TIKA-1980. */
    @Test
    public void testAllHeadElements() throws Exception {
        // IdentityHtmlMapper is needed to extract <script> tags
        ParseContext context = new ParseContext();
        context.set(org.apache.tika.parser.html.HtmlMapper.class,
                org.apache.tika.parser.html.IdentityHtmlMapper.INSTANCE);
        Metadata metadata = new Metadata();
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/html");

        final Map<String, Integer> tagFrequencies = new HashMap<>();

        String path = "/test-documents/testHTML_head.html";
        try (TikaInputStream tis = getResourceAsStream(path)) {
            ContentHandler tagCounter = new DefaultHandler() {
                @Override
                public void startElement(String uri, String local, String name,
                                         Attributes attributes) throws SAXException {
                    int count = tagFrequencies.getOrDefault(name, 0);
                    tagFrequencies.put(name, count + 1);
                }
            };
            new JSoupParser().parse(tis, tagCounter, metadata, context);
        }

        assertEquals(1, (int) tagFrequencies.get("title"));
        assertEquals(12, (int) tagFrequencies.get("meta"));
        assertEquals(12, (int) tagFrequencies.get("link"));
        assertEquals(6, (int) tagFrequencies.get("script"));
    }
}
