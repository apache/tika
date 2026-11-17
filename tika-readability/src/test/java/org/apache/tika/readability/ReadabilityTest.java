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
package org.apache.tika.readability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/** Ports of upstream's API tests plus Java-side behavior. Conformance lives in UpstreamTestPagesTest. */
public class ReadabilityTest {

    private static final String LOREM = "<p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nunc mollis leo lacus, vitae semper nisl ullamcorper ut.</p>";

    private static Document doc(String html) {
        return Jsoup.parse(html, "http://fakehost/test/page.html");
    }

    @Test
    public void testMaxElemsToParse() {
        ReadabilityLimitException e = assertThrows(ReadabilityLimitException.class,
                () -> new Readability(doc("<html><div>yo</div></html>"), new ReadabilityOptions().maxElemsToParse(1)).parse());
        assertEquals("Aborting parsing document; 4 elements found", e.getMessage());
    }

    @Test
    public void testMaxTextScanned() {
        // nesting makes every level re-read the text below it: ~100x the page's own text here
        String nested = "<body>" + ("<section>" + LOREM).repeat(200);
        ReadabilityLimitException e = assertThrows(ReadabilityLimitException.class,
                () -> new Readability(doc(nested), new ReadabilityOptions().maxTextScanned(1_000_000)).parse());
        assertTrue(e.getMessage().contains("1000000 characters"), e.getMessage());

        Readability r = new Readability(doc("<body>" + LOREM.repeat(200)), new ReadabilityOptions().maxTextScanned(1_000_000));
        assertNotNull(r.parse());
        assertTrue(r.textScanned() < 1_000_000);
    }

    @Test
    public void testCustomVideoRegex() {
        String html = LOREM + "<iframe src=\"https://mycustomdomain.com/some-embeds\"></iframe>";
        String expected = "<div id=\"readability-page-1\" class=\"page\">" + LOREM
                + "<iframe src=\"https://mycustomdomain.com/some-embeds\"></iframe></div>";
        Article a = new Readability(doc(html), new ReadabilityOptions().charThreshold(20)
                .allowedVideoRegex(Pattern.compile(".*mycustomdomain.com.*"))).parse();
        assertEquals(expected, a.contentHtml());

        a = new Readability(doc(html), new ReadabilityOptions().charThreshold(20)).parse();
        assertFalse(a.contentHtml().contains("iframe"));
    }

    @Test
    public void testKeepClasses() {
        String html = "<div class=\"story\">" + LOREM.repeat(8) + "</div>";
        assertTrue(new Readability(doc(html), new ReadabilityOptions().keepClasses(true)).parse().contentHtml().contains("story"));
        assertFalse(new Readability(doc(html)).parse().contentHtml().contains("story"));
    }

    @Test
    public void testNothingReadable() {
        assertNull(new Readability(doc("<html><body></body></html>")).parse());
    }

    @Test
    public void testMalformedJsonLdIsIgnored() {
        String html = "<html><head><title>A title that is long enough to keep</title>"
                + "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"NewsArticle\",\"name\":\"x\" </script>"
                + "<script type=\"application/ld+json\">[null]</script>"
                + "<script type=\"application/ld+json\">{\"@context\":null,\"@type\":\"NewsArticle\"}</script>"
                + "</head><body>" + LOREM.repeat(8) + "</body></html>";
        Article a = new Readability(doc(html)).parse();
        assertNotNull(a);
        assertEquals("A title that is long enough to keep", a.title());
    }

    @Test
    public void testJsonLdMetadata() {
        String html = "<html><head><title>Ignored | Site</title>"
                + "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@type\":\"NewsArticle\","
                + "\"headline\":\"JSON-LD headline\",\"author\":[{\"name\":\"A\"},{\"name\":\"B\"}],"
                + "\"publisher\":{\"name\":\"Pub\"},\"datePublished\":\"2020-01-02\"}</script>"
                + "</head><body>" + LOREM.repeat(8) + "</body></html>";
        Article a = new Readability(doc(html)).parse();
        assertEquals("JSON-LD headline", a.title());
        assertEquals("A, B", a.byline());
        assertEquals("Pub", a.siteName());
        assertEquals("2020-01-02", a.publishedTime());
    }

    @Test
    public void testReaderable() {
        String verySmall = "<html><p id=\"main\">hello there</p></html>";
        String small = "<html><p id=\"main\">" + "hello there ".repeat(11) + "</p></html>";
        String large = "<html><p id=\"main\">" + "hello there ".repeat(12) + "</p></html>";
        String veryLarge = "<html><p id=\"main\">" + "hello there ".repeat(50) + "</p></html>";

        assertFalse(Readerable.isProbablyReaderable(doc(verySmall)));
        assertFalse(Readerable.isProbablyReaderable(doc(small)));
        assertFalse(Readerable.isProbablyReaderable(doc(large)));
        assertTrue(Readerable.isProbablyReaderable(doc(veryLarge)));

        assertFalse(Readerable.isProbablyReaderable(doc(verySmall), 120, 0, Readerable::isNodeVisible));
        assertTrue(Readerable.isProbablyReaderable(doc(small), 120, 0, Readerable::isNodeVisible));
        assertFalse(Readerable.isProbablyReaderable(doc(large), 200, 0, Readerable::isNodeVisible));
        assertTrue(Readerable.isProbablyReaderable(doc(veryLarge), 200, 0, Readerable::isNodeVisible));
    }

    @Test
    public void testResolveUrl() {
        String base = "http://fakehost/test/page.html";
        assertEquals("http://fakehost/test/a.html", JsCompat.resolveUrl(base, "a.html"));
        assertEquals("http://fakehost/a.html", JsCompat.resolveUrl(base, "../../a.html"));
        assertEquals("http://fakehost/test/page.html?q", JsCompat.resolveUrl(base, "?q"));
        assertEquals("http://fakehost/test/page.html#f", JsCompat.resolveUrl(base, "#f"));
        assertEquals("http://example.com/", JsCompat.resolveUrl(base, "//EXAMPLE.com:80"));
        // empty path segments survive, unlike java.net.URI.normalize
        assertEquals("https://web.archive.org/web/1/http://a.b/c", JsCompat.resolveUrl(base, "https://web.archive.org/web/1/http://a.b/c"));
        assertEquals("http://fakehost/test/a%20b%C3%A9", JsCompat.resolveUrl(base, "a b\u00e9"));
        assertEquals("file:///C:/x", JsCompat.resolveUrl(base, "file:///C|/x"));
        assertEquals("mailto:a@b", JsCompat.resolveUrl(base, "mailto:a@b"));
        assertNull(JsCompat.resolveUrl(base, "http://"));
    }
}
