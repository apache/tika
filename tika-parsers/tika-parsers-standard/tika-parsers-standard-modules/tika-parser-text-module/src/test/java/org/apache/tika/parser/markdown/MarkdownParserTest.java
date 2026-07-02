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
package org.apache.tika.parser.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToMarkdownContentHandler;

public class MarkdownParserTest extends TikaTest {

    /** Parses markdown from a string via AutoDetectParser (glob detection on a dummy name). */
    private XMLResult parseString(String markdown) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.md");
        try (TikaInputStream tis =
                TikaInputStream.get(markdown.getBytes(StandardCharsets.UTF_8))) {
            return getXML(tis, AUTO_DETECT_PARSER, metadata);
        }
    }

    @Test
    public void testBasic() throws Exception {
        XMLResult r = getXML("testMARKDOWN.md", AUTO_DETECT_PARSER);
        String xhtml = r.xml;

        assertContains("<h1>Title</h1>", xhtml);
        assertContains("<strong>bold</strong>", xhtml);
        assertContains("<em>italic</em>", xhtml);
        assertContains("<del>struck</del>", xhtml);
        assertContains("<code>inline</code>", xhtml);
        assertContains("<li>one</li>", xhtml);
        assertContains("<ol start=\"3\">", xhtml);
        assertContains("<blockquote>", xhtml);
        assertContains("href=\"https://example.com\"", xhtml);

        //full fence info survives alongside the language class
        assertContains("class=\"language-java\"", xhtml);
        assertContains("data-info=\"java title=&quot;Example&quot;\"", xhtml);

        //gfm table with alignment sections
        assertContains("<thead>", xhtml);
        assertContains("<th align=\"left\">L</th>", xhtml);
        assertContains("<th align=\"center\">C</th>", xhtml);
        assertContains("<td align=\"right\">c</td>", xhtml);

        //alt text keeps code spans
        assertContains("alt=\"see config.json for details\"", xhtml);
        assertContains("title=\"Tika\"", xhtml);

        //detected charset lands in the metadata
        assertTrue(r.metadata.get(Metadata.CONTENT_TYPE).startsWith("text/markdown; charset="),
                r.metadata.get(Metadata.CONTENT_TYPE));
        assertContains("Café naïve résumé", xhtml);
    }

    @Test
    public void testRawHtmlIsEscaped() throws Exception {
        String xhtml =
                parseString("before\n\n<script>alert(1)</script>\n\nand inline <b>bold</b>\n").xml;

        assertFalse(xhtml.contains("<script>"), xhtml);
        assertContains("&lt;script&gt;alert(1)&lt;/script&gt;", xhtml);
        assertFalse(xhtml.contains("<b>"), xhtml);
        assertContains("&lt;b&gt;bold&lt;/b&gt;", xhtml);
    }

    @Test
    public void testOrderedListStartsAtOneNeedsNoAttribute() throws Exception {
        assertFalse(parseString("1. a\n2. b\n").xml.contains("start="));
    }

    @Test
    public void testLineBreaks() throws Exception {
        assertContains("<br", parseString("hard  \nbreak\n").xml);
    }

    @Test
    public void testDataURIsBecomeEmbeddedDocuments() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "testMARKDOWN_dataURI.md");
        List<Metadata> metadataList = getRecursiveMetadata("testMARKDOWN_dataURI.md", metadata);

        //container + image destination + data uri scraped from the raw html block
        assertEquals(3, metadataList.size());
        assertEquals("image/gif", metadataList.get(1).get(Metadata.CONTENT_TYPE));
        assertEquals("image/gif", metadataList.get(2).get(Metadata.CONTENT_TYPE));
        assertEquals(TikaCoreProperties.EmbeddedResourceType.INLINE.toString(),
                metadataList.get(1).get(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE));
    }

    @Test
    public void testDeeplyNestedBlocksAreFlattenedNotFailed() throws Exception {
        //maxOpenBlockParsers caps block nesting below SecureContentHandler's 100-level limit,
        //so a pathologically deep block document extracts (deeper nesting flattened to text)
        //rather than being rejected as a suspected zip bomb or overflowing the stack.
        assertContains("deep", parseString("> ".repeat(5000) + "deep\n").xml);
    }

    @Test
    public void testRoundTripsBackToMarkdown() throws Exception {
        String markdown = "# Title\n\nSome **bold** and *italic* and ~~struck~~ `inline`.\n";
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test.md");
        StringWriter writer = new StringWriter();
        try (TikaInputStream tis =
                TikaInputStream.get(markdown.getBytes(StandardCharsets.UTF_8))) {
            AUTO_DETECT_PARSER.parse(tis, new ToMarkdownContentHandler(writer), metadata,
                    new ParseContext());
        }
        String md = writer.toString();

        assertContains("# Title", md);
        assertContains("**bold**", md);
        assertContains("*italic*", md);
        assertContains("~~struck~~", md);
        assertContains("`inline`", md);
    }
}
