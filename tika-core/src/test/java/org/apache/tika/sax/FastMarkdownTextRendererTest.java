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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Test;

/**
 * Differential guard for {@link FastMarkdownTextRenderer}: every AST must render
 * byte-identically through the fast renderer and through commonmark's stock
 * renderer. If a commonmark upgrade changes {@code CoreMarkdownNodeRenderer}'s
 * escaping, these tests fail and the fast renderer must be re-synced.
 */
public class FastMarkdownTextRendererTest {

    private static final List<Extension> EXTENSIONS =
            Arrays.asList(TablesExtension.create(), StrikethroughExtension.create());

    private static final MarkdownRenderer FAST = MarkdownRenderer.builder()
            .extensions(EXTENSIONS)
            .nodeRendererFactory(FastMarkdownTextRenderer.FACTORY).build();

    private static final MarkdownRenderer STOCK =
            MarkdownRenderer.builder().extensions(EXTENSIONS).build();

    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private static void assertSameRendering(Node document, String label) {
        assertEquals(STOCK.render(document), FAST.render(document), label);
    }

    private static void assertSameRendering(String markdown) {
        assertSameRendering(PARSER.parse(markdown), "diverged on: " + markdown);
    }

    private static void assertSameRendering(Node document) {
        assertSameRendering(document, "diverged on hand-built AST");
    }

    private static Node paragraphs(String... literals) {
        Document doc = new Document();
        Paragraph p = new Paragraph();
        for (String literal : literals) {
            p.appendChild(new Text(literal));
        }
        doc.appendChild(p);
        return doc;
    }

    @Test
    public void testLineStartEscapes() {
        // Literals whose first char would parse as block structure at line start
        for (String literal : new String[]{
                "- not a list", "-not a list", "-", "+ plus", "* star",
                "# not a heading", "#hash", "#",
                "= not setext", "=",
                "1. not ordered", "12. also not", "123456789. limit", "1) paren",
                "1234567890. ten digits is not a marker", "12x. no",
                "> not a quote",
                " leading space", "\tleading tab", "  two spaces"}) {
            assertSameRendering(paragraphs(literal), "diverged on literal: " + literal);
        }
    }

    @Test
    public void testSecondTextNodeNotAtLineStart() {
        // The line-start branches must not fire mid-line
        assertSameRendering(paragraphs("before ", "- mid", "# mid", "= mid", "12. mid"));
    }

    @Test
    public void testEscapableCharacters() {
        for (String literal : new String[]{
                "a[b]c", "a<b>c", "a`b`c", "a*b*c", "a_b_c", "a&b;c", "a\\b",
                "pipe | in text", "tilde ~~x~~", "", "plain text, no specials.",
                "text\nwith\nnewlines", "\n", "trailing newline\n", "*", "\\"}) {
            assertSameRendering(paragraphs(literal), "diverged on literal: " + literal);
        }
    }

    @Test
    public void testBangBeforeLink() {
        Document doc = new Document();
        Paragraph p = new Paragraph();
        p.appendChild(new Text("see!"));
        Link link = new Link("http://example.com", null);
        link.appendChild(new Text("here"));
        p.appendChild(link);
        doc.appendChild(p);
        assertSameRendering(doc, "bang before link");

        // bang NOT followed by a link needs no escape
        assertSameRendering(paragraphs("no link here!"));
    }

    @Test
    public void testHeadingEscapeSet() {
        for (String literal : new String[]{"plain", "with # hash", "with\nnewline", "a`b"}) {
            Document doc = new Document();
            Heading h = new Heading();
            h.setLevel(2);
            h.appendChild(new Text(literal));
            doc.appendChild(h);
            assertSameRendering(doc, "heading literal: " + literal);
        }
    }

    @Test
    public void testParsedDocuments() {
        // Round-trips through the parser: escaped specials come back as raw Text
        for (String markdown : new String[]{
                "\\- not a list\n",
                "\\# not a heading\n",
                "12\\. not ordered\n",
                "para one\n\npara two with \\*stars\\* and \\[brackets\\]\n",
                "**bold** and _em_ and ~~strike~~\n",
                "a paragraph\nwith a soft break\n",
                "| a | b\\|c |\n|---|---|\n| d | *e* |\n",
                "# heading `code` and *em*\n",
                "> quoted \\> text\n",
                "[link](http://example.com \"ti\\\"tle\") and ![img](http://example.com/i.png)\n"}) {
            assertSameRendering(markdown);
        }
    }

    @Test
    public void testRandomizedDifferential() {
        char[] alphabet = ("abc XYZ \t\n-#=!*_[]<>&`\\|~.)0129" +
                "é中😀").toCharArray();
        for (long seed = 0; seed < 50; seed++) {
            Random random = new Random(seed);
            StringBuilder sb = new StringBuilder();
            int len = 1 + random.nextInt(80);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String literal = sb.toString();
            // the alphabet splits astral chars into chars; skip broken pairs
            if (!isWellFormed(literal)) {
                continue;
            }
            assertSameRendering(paragraphs(literal), "seed " + seed + " literal: " + literal);
        }
    }

    private static boolean isWellFormed(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return false;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testTableCellKeepsStockPath() {
        // '|' inside a cell must come out escaped once, not twice
        Node doc = PARSER.parse("| a\\|b | *c* |\n|---|---|\n| \\|start | end\\| |\n");
        String stock = STOCK.render(doc);
        String fast = FAST.render(doc);
        assertEquals(stock, fast, "table cell rendering");
        assertTrue(fast.contains("a\\|b"), "single-escaped pipe expected: " + fast);
    }
}
