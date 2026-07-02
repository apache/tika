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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToMarkdownContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;

class MarkdownParserTest {

    private static final String MARKDOWN =
            "# Title\n\n"
                    + "Some **bold** and *italic* and ~~struck~~ text with `inline` code.\n\n"
                    + "- one\n- two\n\n"
                    + "1. first\n2. second\n\n"
                    + "> a quote\n\n"
                    + "```java\nSystem.out.println(\"hi\");\n```\n\n"
                    + "[link](https://example.com)\n\n"
                    + "| A | B |\n|---|---|\n| 1 | 2 |\n";

    private static String toXhtml(String markdown) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown");
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(markdown.getBytes(StandardCharsets.UTF_8))) {
            new MarkdownParser().parse(tis, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    @Test
    void emitsStructuredXhtml() throws Exception {
        String xhtml = toXhtml(MARKDOWN);

        assertTrue(xhtml.contains("<h1>") && xhtml.contains("Title"), xhtml);
        assertTrue(xhtml.contains("<strong>bold</strong>"), xhtml);
        assertTrue(xhtml.contains("<em>italic</em>"), xhtml);
        assertTrue(xhtml.contains("<del>struck</del>"), xhtml);
        assertTrue(xhtml.contains("<code>inline</code>"), xhtml);
        assertTrue(xhtml.contains("<ul>") && xhtml.contains("<li>one</li>"), xhtml);
        assertTrue(xhtml.contains("<ol>") && xhtml.contains("<li>first</li>"), xhtml);
        assertTrue(xhtml.contains("<blockquote>"), xhtml);
        assertTrue(xhtml.contains("<pre>") && xhtml.contains("language-java"), xhtml);
        assertTrue(xhtml.contains("href=\"https://example.com\"") && xhtml.contains(">link</a>"), xhtml);
        assertTrue(xhtml.contains("<table>") && xhtml.contains("<th") && xhtml.contains("<td"), xhtml);
    }

    @Test
    void escapesRawHtml() throws Exception {
        String xhtml = toXhtml("before\n\n<script>alert(1)</script>\n\nand inline <b>bold</b> here\n");

        assertFalse(xhtml.contains("<script>"), xhtml);
        assertTrue(xhtml.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), xhtml);
        assertFalse(xhtml.contains("<b>"), xhtml);
        assertTrue(xhtml.contains("&lt;b&gt;bold&lt;/b&gt;"), xhtml);
    }

    @Test
    void emitsTableAlignmentAndSections() throws Exception {
        String xhtml = toXhtml("| L | C | R |\n|:--|:-:|--:|\n| a | b | c |\n");

        assertTrue(xhtml.contains("<thead>") && xhtml.contains("<tbody>"), xhtml);
        assertTrue(xhtml.contains("<th align=\"left\">L</th>"), xhtml);
        assertTrue(xhtml.contains("<th align=\"center\">C</th>"), xhtml);
        assertTrue(xhtml.contains("<th align=\"right\">R</th>"), xhtml);
        assertTrue(xhtml.contains("<td align=\"center\">b</td>"), xhtml);
    }

    @Test
    void emitsImagesAndLineBreaks() throws Exception {
        String xhtml = toXhtml("![tika logo](logo.png \"Tika\")\n\nhard  \nbreak\n");

        assertTrue(xhtml.contains("src=\"logo.png\""), xhtml);
        assertTrue(xhtml.contains("alt=\"tika logo\""), xhtml);
        assertTrue(xhtml.contains("title=\"Tika\""), xhtml);
        assertTrue(xhtml.contains("<br"), xhtml);
    }

    @Test
    void setsContentTypeAndEncodingWithDetectedCharset() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown");
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream tis = TikaInputStream.get(
                "# Café\n\nnaïve résumé\n".getBytes(StandardCharsets.UTF_8))) {
            new MarkdownParser().parse(tis, handler, metadata, new ParseContext());
        }

        assertTrue(metadata.get(Metadata.CONTENT_TYPE).startsWith("text/markdown; charset="),
                metadata.get(Metadata.CONTENT_TYPE));
        assertTrue(metadata.get(Metadata.CONTENT_ENCODING) != null
                && !metadata.get(Metadata.CONTENT_ENCODING).isEmpty(), "encoding should be set");
        assertTrue(handler.toString().contains("Café"), handler.toString());
        assertTrue(handler.toString().contains("naïve résumé"), handler.toString());
    }

    @Test
    void preservesOrderedListStartNumber() throws Exception {
        String xhtml = toXhtml("3. third\n4. fourth\n");

        assertTrue(xhtml.contains("<ol start=\"3\">"), xhtml);
        assertTrue(xhtml.contains("<li>third</li>"), xhtml);
        // a list starting at 1 needs no attribute
        assertFalse(toXhtml("1. a\n2. b\n").contains("start="), "start=1 should be implicit");
    }

    @Test
    void preservesCodeSpansInImageAltText() throws Exception {
        String xhtml = toXhtml("![see `config.json` for\ndetails](img.png)\n");

        assertTrue(xhtml.contains("alt=\"see config.json for details\""), xhtml);
    }

    @Test
    void preservesFullCodeFenceInfoString() throws Exception {
        String xhtml = toXhtml("```java title=\"Example\"\nint x = 1;\n```\n");

        assertTrue(xhtml.contains("class=\"language-java\""), xhtml);
        assertTrue(xhtml.contains("data-info=\"java title=&quot;Example&quot;\""), xhtml);
        // plain language-only fences carry no data-info
        assertFalse(toXhtml("```java\nint x;\n```\n").contains("data-info"), "no extra info");
    }

    @Test
    void roundTripsBackToMarkdown() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(Metadata.CONTENT_TYPE, "text/markdown");
        StringWriter writer = new StringWriter();
        try (TikaInputStream tis = TikaInputStream.get(MARKDOWN.getBytes(StandardCharsets.UTF_8))) {
            new MarkdownParser().parse(tis, new ToMarkdownContentHandler(writer), metadata, new ParseContext());
        }
        String md = writer.toString();

        assertTrue(md.contains("# Title"), md);
        assertTrue(md.contains("**bold**"), md);
        assertTrue(md.contains("*italic*"), md);
        assertTrue(md.contains("~~struck~~"), md);
        assertTrue(md.contains("`inline`"), md);
    }
}
