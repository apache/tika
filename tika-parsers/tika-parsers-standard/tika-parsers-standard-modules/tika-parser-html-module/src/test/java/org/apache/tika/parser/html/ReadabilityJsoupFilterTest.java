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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HTML;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.ReadabilityMetadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParseRecord;
import org.apache.tika.sax.ToHTMLContentHandler;

public class ReadabilityJsoupFilterTest extends TikaTest {

    private static final String PARA = "<p>The committee met on Tuesday, and after a long debate, it voted to adopt"
            + " the proposal, which had been revised twice since the spring.</p>";

    private static final String PAGE = "<html><head><title>Council vote passes after long debate | Example News</title>"
            + "<meta name=\"author\" content=\"A. Writer\"></head><body>"
            + "<nav class=\"menu\"><a href=\"/\">Home</a> <a href=\"/world\">World</a> <a href=\"/sport\">Sport</a></nav>"
            + "<div class=\"sidebar\"><a href=\"/x\">Most read nav link</a></div>"
            + "<article><h1>Council vote passes after long debate</h1>" + PARA.repeat(8) + "</article>"
            + "<footer class=\"footer\">Copyright footer text</footer></body></html>";

    private XMLResult parse(String html, ParseContext context) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(html.getBytes(StandardCharsets.UTF_8))) {
            Metadata m = new Metadata();
            m.set(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8");
            return getXML(tis, new JSoupParser(), m, context);
        }
    }

    private static ParseContext withFilter(ReadabilityJsoupFilter filter) {
        ParseContext context = new ParseContext();
        context.set(JsoupFilter.class, filter);
        return context;
    }

    private static class Append implements JsoupFilter {
        @Override
        public Document filter(Document document, Metadata metadata, ParseContext context) {
            document.body().appendElement("p").text("from-a");
            return document;
        }
    }

    private static class Suffix implements JsoupFilter {
        @Override
        public Document filter(Document document, Metadata metadata, ParseContext context) {
            document.select("p").last().appendText("-then-b");
            return document;
        }
    }

    @Test
    public void testCompositeRunsInOrder() throws Exception {
        ParseContext context = new ParseContext();
        context.set(JsoupFilter.class, new CompositeJsoupFilter(List.of(new Append(), new Suffix())));
        assertContains("from-a-then-b", parse("<html><body><p>x</p></body></html>", context).xml);

        context.set(JsoupFilter.class, new CompositeJsoupFilter(List.of(new Suffix(), new Append())));
        String xml = parse("<html><body><p>x</p></body></html>", context).xml;
        assertContains("x-then-b", xml);
        assertNotContained("from-a-then-b", xml);
    }

    @Test
    public void testInlineResourcesComeFromUnfilteredPage() throws Exception {
        // the sidebar Readability drops holds one of each inline resource
        String page = PAGE.replace("<div class=\"sidebar\">", "<div class=\"sidebar\">"
                + "<img src=\"data:text/plain;base64,aGVsbG8gZnJvbSBkYXRh\">"
                + "<iframe srcdoc=\"<p>framed page</p>\"></iframe>"
                + "<script src=\"side.js\">var sidebarWidget = 1;</script>");
        JSoupParser parser = new JSoupParser();
        parser.setExtractScripts(true);

        List<Metadata> plain = rmeta(page, parser, new ParseContext());
        List<Metadata> filtered = rmeta(page, parser, withFilter(new ReadabilityJsoupFilter()));

        assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, filtered.get(0).get(ReadabilityMetadata.STATUS));
        assertNotContained("Most read nav link", filtered.get(0).get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals(List.of("INLINE", "INLINE", "MACRO"), resourceTypes(plain));
        assertEquals(resourceTypes(plain), resourceTypes(filtered));
        assertEquals("side.js", filtered.get(3).get(HTML.SCRIPT_SOURCE));
        assertContains("sidebarWidget", filtered.get(3).get(TikaCoreProperties.TIKA_CONTENT));
    }

    private List<Metadata> rmeta(String html, JSoupParser parser, ParseContext context) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(html.getBytes(StandardCharsets.UTF_8))) {
            Metadata m = new Metadata();
            m.set(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8");
            return getRecursiveMetadata(tis, parser, m, context, true);
        }
    }

    private static List<String> resourceTypes(List<Metadata> list) {
        return list.subList(1, list.size()).stream()
                .map(m -> m.get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE)).toList();
    }

    @Test
    public void testOffByDefault() throws Exception {
        XMLResult r = parse(PAGE, new ParseContext());
        assertContains("Most read nav link", r.xml);
        assertNull(r.metadata.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testExtractsArticle() throws Exception {
        XMLResult r = parse(PAGE, withFilter(new ReadabilityJsoupFilter()));
        assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, r.metadata.get(ReadabilityMetadata.STATUS));
        assertContains("revised twice since the spring", r.xml);
        assertNotContained("Most read nav link", r.xml);
        assertNotContained("Copyright footer text", r.xml);
        // head is untouched, so HTML metadata is as without the filter
        assertEquals("Council vote passes after long debate | Example News", r.metadata.get(TikaCoreProperties.TITLE));
        assertEquals("A. Writer", r.metadata.get(ReadabilityMetadata.BYLINE));
        assertEquals("Council vote passes after long debate", r.metadata.get(ReadabilityMetadata.TITLE));
        assertEquals("true", r.metadata.get(ReadabilityMetadata.READERABLE));
    }

    @Test
    public void testNotReaderableEmitsWholeDocument() throws Exception {
        String nav = "<html><body><nav><a href=\"/\">Home</a></nav></body></html>";
        XMLResult r = parse(nav, withFilter(new ReadabilityJsoupFilter()));
        assertEquals(ReadabilityMetadata.STATUS_NOT_READERABLE, r.metadata.get(ReadabilityMetadata.STATUS));
        assertEquals("false", r.metadata.get(ReadabilityMetadata.READERABLE));
        assertContains("Home", r.xml);

        // without the pre-check Readability returns a best attempt even here
        ReadabilityJsoupFilter ungated = new ReadabilityJsoupFilter();
        ungated.setRequireReaderable(false);
        r = parse(nav, withFilter(ungated));
        assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, r.metadata.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testNoArticleEmitsWholeDocument() throws Exception {
        ReadabilityJsoupFilter ungated = new ReadabilityJsoupFilter();
        ungated.setRequireReaderable(false);
        XMLResult r = parse("<html><head><title>t</title></head><body></body></html>", withFilter(ungated));
        assertEquals(ReadabilityMetadata.STATUS_NO_ARTICLE, r.metadata.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testLimitEmitsWholeDocument() throws Exception {
        ReadabilityJsoupFilter filter = new ReadabilityJsoupFilter();
        filter.setMaxElements(10);
        XMLResult r = parse(PAGE, withFilter(filter));
        assertEquals(ReadabilityMetadata.STATUS_LIMIT, r.metadata.get(ReadabilityMetadata.STATUS));
        assertContains("Most read nav link", r.xml);

        filter = new ReadabilityJsoupFilter();
        filter.setMaxTextScanned(100);
        r = parse(PAGE, withFilter(filter));
        assertEquals(ReadabilityMetadata.STATUS_LIMIT, r.metadata.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testLimitsMustBePositive() {
        ReadabilityJsoupFilter filter = new ReadabilityJsoupFilter();
        assertThrows(IllegalArgumentException.class, () -> filter.setMaxElements(0));
        assertThrows(IllegalArgumentException.class, () -> filter.setMaxTextScanned(0));
    }

    @Test
    public void testEmbeddedHtmlDocumentIsFiltered() throws Exception {
        ParseContext context = withFilter(new ReadabilityJsoupFilter());
        ParseRecord record = ParseRecord.newInstance(context);
        record.enterEmbedded();
        context.set(ParseRecord.class, record);
        XMLResult r = parse(PAGE, context);
        assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, r.metadata.get(ReadabilityMetadata.STATUS));
        assertNotContained("Most read nav link", r.xml);
    }

    @Test
    public void testParseInlineIsNotFiltered() throws Exception {
        ToHTMLContentHandler handler = new ToHTMLContentHandler();
        Metadata m = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(PAGE.getBytes(StandardCharsets.UTF_8))) {
            new JSoupParser().parseInline(tis, handler, m, withFilter(new ReadabilityJsoupFilter()));
        }
        assertContains("Most read nav link", handler.toString());
        assertNull(m.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testParseStringIsNotFiltered() throws Exception {
        ToHTMLContentHandler handler = new ToHTMLContentHandler();
        Metadata m = new Metadata();
        new JSoupParser().parseString(PAGE, handler, m, withFilter(new ReadabilityJsoupFilter()));
        assertContains("Most read nav link", handler.toString());
        assertNull(m.get(ReadabilityMetadata.STATUS));
    }

    @Test
    public void testCommentDoesNotSplitParagraph() throws Exception {
        String html = "<html><body><div>" + ("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do"
                + " eiusmod tempor incididunt ut labore, et dolore magna aliqua. ").repeat(4)
                + "first half <!-- ad slot --> second half</div></body></html>";
        ReadabilityJsoupFilter ungated = new ReadabilityJsoupFilter();
        ungated.setRequireReaderable(false);
        XMLResult r = parse(html, withFilter(ungated));
        assertEquals(ReadabilityMetadata.STATUS_EXTRACTED, r.metadata.get(ReadabilityMetadata.STATUS));
        assertContains("first half  second half</p>", r.xml);
    }
}
