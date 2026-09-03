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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownWriter;
import org.commonmark.text.AsciiMatcher;

/**
 * Replacement for commonmark's {@code Text} node rendering that emits unescaped spans in
 * bulk instead of one character at a time. commonmark's {@code MarkdownWriter} routes every
 * character of a text literal through an escape check and a per-char {@code Appendable}
 * append; on multi-megabyte documents that is most of the render cost. This renderer scans
 * for the next escape-needing character and writes the clean span between them with one
 * {@code raw()} call.
 * <p>
 * The escaping semantics replicate {@code CoreMarkdownNodeRenderer#visit(Text)} for
 * the commonmark version pinned in tika-parent exactly: the line-start disambiguation
 * cases, the heading escape variant, the {@code !}-before-link case, and the
 * {@code \n} numeric reference. {@code FastMarkdownTextRendererTest} renders ASTs
 * through this renderer and the stock one and requires byte-identical output; a
 * commonmark upgrade that changes escaping fails there and must be re-synced here.
 * <p>
 * Text inside a table cell renders through the stock per-char path: the tables extension
 * pushes a raw-escape for {@code |} onto the writer there, and hand-emitted escapes would
 * be escaped a second time by {@code raw()}. That is the only push site in the extensions
 * this handler registers, so outside a cell {@code raw()} is a verbatim bulk append.
 */
class FastMarkdownTextRenderer implements NodeRenderer {

    static final MarkdownNodeRendererFactory FACTORY = new MarkdownNodeRendererFactory() {
        @Override
        public NodeRenderer create(MarkdownNodeRendererContext context) {
            return new FastMarkdownTextRenderer(context);
        }

        @Override
        public Set<Character> getSpecialCharacters() {
            return Set.of();
        }
    };

    // mirrors CoreMarkdownNodeRenderer's orderedListMarkerPattern
    private static final Pattern ORDERED_LIST_MARKER = Pattern.compile("^([0-9]{1,9})([.)])");

    private static final String TEXT_ESCAPE_CHARS = "[]<>`*_&\n\\";

    private final MarkdownNodeRendererContext context;
    // AsciiMatcher semantics: characters >= 128 never escape
    private final boolean[] textEscape = new boolean[128];
    private final boolean[] headingEscape = new boolean[128];
    private final AsciiMatcher textEscapeMatcher;
    private final AsciiMatcher headingEscapeMatcher;

    FastMarkdownTextRenderer(MarkdownNodeRendererContext context) {
        this.context = context;
        textEscapeMatcher = AsciiMatcher.builder()
                .anyOf(TEXT_ESCAPE_CHARS)
                .anyOf(context.getSpecialCharacters())
                .build();
        headingEscapeMatcher = AsciiMatcher.builder(textEscapeMatcher).anyOf("#").build();
        for (int c = 0; c < 128; c++) {
            textEscape[c] = textEscapeMatcher.matches((char) c);
            headingEscape[c] = headingEscapeMatcher.matches((char) c);
        }
    }

    @Override
    public Set<Class<? extends Node>> getNodeTypes() {
        return Set.of(Text.class);
    }

    @Override
    public void render(Node node) {
        Text text = (Text) node;
        MarkdownWriter writer = context.getWriter();
        String literal = text.getLiteral();

        // line-start disambiguation, identical to CoreMarkdownNodeRenderer
        if (writer.isAtLineStart() && !literal.isEmpty()) {
            char c = literal.charAt(0);
            switch (c) {
                case '-': {
                    writer.raw("\\-");
                    literal = literal.substring(1);
                    break;
                }
                case '#': {
                    writer.raw("\\#");
                    literal = literal.substring(1);
                    break;
                }
                case '=': {
                    if (text.getPrevious() != null) {
                        writer.raw("\\=");
                        literal = literal.substring(1);
                    }
                    break;
                }
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9': {
                    Matcher m = ORDERED_LIST_MARKER.matcher(literal);
                    if (m.find()) {
                        writer.raw(m.group(1));
                        writer.raw("\\" + m.group(2));
                        literal = literal.substring(m.end());
                    }
                    break;
                }
                case '\t': {
                    writer.raw("&#9;");
                    literal = literal.substring(1);
                    break;
                }
                case ' ': {
                    writer.raw("&#32;");
                    literal = literal.substring(1);
                    break;
                }
                default:
                    break;
            }
        }

        boolean inHeading = text.getParent() instanceof Heading;
        boolean bangBeforeLink = literal.endsWith("!") && text.getNext() instanceof Link;
        String body = bangBeforeLink ? literal.substring(0, literal.length() - 1) : literal;

        if (inTableCell(text)) {
            // stock path: the writer carries a raw-escape here, so hand-emitted
            // escapes would be double-escaped by raw()
            writer.text(body, inHeading ? headingEscapeMatcher : textEscapeMatcher);
        } else {
            writeSpanned(writer, body, inHeading ? headingEscape : textEscape);
        }
        if (bangBeforeLink) {
            writer.raw("\\!");
        }
    }

    private static boolean inTableCell(Node node) {
        for (Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof TableCell) {
                return true;
            }
        }
        return false;
    }

    private static void writeSpanned(MarkdownWriter writer, String s, boolean[] escape) {
        if (s.isEmpty()) {
            // writer.text() is a no-op on empty input; match it
            return;
        }
        int n = s.length();
        int start = 0;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c < 128 && escape[c]) {
                if (i > start) {
                    writer.raw(s.substring(start, i));
                }
                // same replacements append(char, matcher) makes
                writer.raw(c == '\n' ? "&#10;" : "\\" + c);
                start = i + 1;
            }
        }
        if (start < n) {
            writer.raw(start == 0 ? s : s.substring(start));
        }
    }
}
