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
package org.apache.tika.parser.vlm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Parses a markdown string using commonmark-java and emits XHTML SAX events.
 * <p>
 * Supports:
 * <ul>
 *   <li>Headings ({@code h1}–{@code h6})</li>
 *   <li>Paragraphs ({@code p})</li>
 *   <li>Bold / italic / strikethrough ({@code b}, {@code i}, {@code s})</li>
 *   <li>Links ({@code a}) and images ({@code img})</li>
 *   <li>Ordered and unordered lists ({@code ol}, {@code ul}, {@code li})</li>
 *   <li>Blockquotes ({@code blockquote})</li>
 *   <li>Code blocks ({@code pre}/{@code code}) and inline code ({@code code})</li>
 *   <li>GFM tables ({@code table}, {@code thead}, {@code tbody}, {@code tr},
 *       {@code th}, {@code td})</li>
 *   <li>Thematic breaks ({@code hr})</li>
 *   <li>Hard / soft line breaks ({@code br})</li>
 *   <li>Raw HTML blocks, which OCR models such as jina-ocr-v1 and DeepSeek-OCR use for
 *       tables: parsed leniently and re-emitted as the same structural subset with
 *       attributes dropped except {@code colspan}/{@code rowspan}; unknown elements
 *       contribute their text. Inline HTML tags are
 *       dropped (their text is kept), except {@code <br>}.</li>
 * </ul>
 *
 * @since Apache Tika 4.0
 */
class MarkdownToXHTMLEmitter {

    private static final String XHTML_NS = "http://www.w3.org/1999/xhtml";

    private static final List<Extension> EXTENSIONS = Arrays.asList(
            TablesExtension.create(),
            StrikethroughExtension.create()
    );

    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final AttributesImpl EMPTY_ATTRS = new AttributesImpl();

    /** HTML elements re-emitted by name from a raw HTML block; the rest contribute text only. */
    private static final Set<String> HTML_PASSTHROUGH = Set.of(
            "table", "thead", "tbody", "tfoot", "tr", "th", "td",
            "p", "br", "ul", "ol", "li", "blockquote", "pre", "code",
            "h1", "h2", "h3", "h4", "h5", "h6", "b", "i", "s", "sup", "sub");

    private static final Map<String, String> HTML_RENAMES = Map.of(
            "strong", "b", "em", "i", "del", "s", "strike", "s");

    private static final Set<String> HTML_DROPPED = Set.of("script", "style", "head");

    /** Table structure elements, where whitespace-only text is formatting, not content. */
    private static final Set<String> HTML_STRUCTURAL = Set.of(
            "table", "thead", "tbody", "tfoot", "tr", "ul", "ol");

    private static final Pattern INLINE_BR = Pattern.compile("(?i)<br\\s*/?>");

    /**
     * Parses the given markdown text and emits SAX events to the handler.
     * <p>
     * The caller is responsible for calling {@code startDocument} /
     * {@code endDocument} on the handler if desired — this method only emits
     * the body-level elements.
     *
     * @param markdown the markdown text to parse
     * @param handler  the SAX content handler to receive events
     * @throws SAXException if the handler throws
     */
    static void emit(String markdown, ContentHandler handler) throws SAXException {
        if (markdown == null || markdown.isEmpty()) {
            return;
        }
        Node document = PARSER.parse(markdown);
        SAXVisitor visitor = new SAXVisitor(handler);
        document.accept(visitor);
        if (visitor.saxException != null) {
            throw visitor.saxException;
        }
    }

    /**
     * commonmark AST visitor that fires SAX events for each node.
     */
    private static class SAXVisitor extends AbstractVisitor {

        private final ContentHandler handler;
        SAXException saxException;

        SAXVisitor(ContentHandler handler) {
            this.handler = handler;
        }

        // --- block nodes ---

        @Override
        public void visit(Document document) {
            visitChildren(document);
        }

        @Override
        public void visit(Heading heading) {
            String tag = "h" + heading.getLevel();
            startElement(tag);
            visitChildren(heading);
            endElement(tag);
        }

        @Override
        public void visit(Paragraph paragraph) {
            // Skip wrapping <p> inside list items — commonmark wraps
            // "loose" list item content in Paragraph nodes, which would
            // produce <li><p>text</p></li>.  We emit the text directly.
            if (paragraph.getParent() instanceof ListItem) {
                visitChildren(paragraph);
                return;
            }
            startElement("p");
            visitChildren(paragraph);
            endElement("p");
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            startElement("blockquote");
            visitChildren(blockQuote);
            endElement("blockquote");
        }

        @Override
        public void visit(BulletList bulletList) {
            startElement("ul");
            visitChildren(bulletList);
            endElement("ul");
        }

        @Override
        public void visit(OrderedList orderedList) {
            startElement("ol");
            visitChildren(orderedList);
            endElement("ol");
        }

        @Override
        public void visit(ListItem listItem) {
            startElement("li");
            visitChildren(listItem);
            endElement("li");
        }

        @Override
        public void visit(FencedCodeBlock fencedCodeBlock) {
            AttributesImpl attrs = EMPTY_ATTRS;
            String info = fencedCodeBlock.getInfo();
            if (info != null && !info.isEmpty()) {
                attrs = new AttributesImpl();
                attrs.addAttribute("", "class", "class", "CDATA",
                        "language-" + info.split("\\s+")[0]);
            }
            startElement("pre");
            startElement("code", attrs);
            characters(fencedCodeBlock.getLiteral());
            endElement("code");
            endElement("pre");
        }

        @Override
        public void visit(IndentedCodeBlock indentedCodeBlock) {
            startElement("pre");
            startElement("code");
            characters(indentedCodeBlock.getLiteral());
            endElement("code");
            endElement("pre");
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            emptyElement("hr");
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            Element body = Jsoup.parseBodyFragment(htmlBlock.getLiteral()).body();
            for (org.jsoup.nodes.Node child : body.childNodes()) {
                emitHtml(child, false);
            }
        }

        private void emitHtml(org.jsoup.nodes.Node node, boolean inStructure) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).getWholeText();
                if (!(inStructure && text.isBlank())) {
                    characters(text);
                }
                return;
            }
            if (!(node instanceof Element)) {
                return;
            }
            String name = ((Element) node).normalName();
            if (HTML_DROPPED.contains(name)) {
                return;
            }
            String tag = HTML_RENAMES.getOrDefault(name, name);
            boolean emit = HTML_PASSTHROUGH.contains(tag);
            if (emit) {
                startElement(tag, spanAttributes((Element) node));
            }
            boolean structural = HTML_STRUCTURAL.contains(tag);
            for (org.jsoup.nodes.Node child : node.childNodes()) {
                emitHtml(child, structural);
            }
            if (emit) {
                endElement(tag);
            }
        }

        /** colspan/rowspan are structure, not presentation: the only attributes kept. */
        private AttributesImpl spanAttributes(Element element) {
            AttributesImpl attrs = EMPTY_ATTRS;
            for (String span : new String[] {"colspan", "rowspan"}) {
                String v = element.attr(span).trim();
                if (v.matches("[1-9][0-9]*") && !v.equals("1")) {
                    if (attrs == EMPTY_ATTRS) {
                        attrs = new AttributesImpl();
                    }
                    attrs.addAttribute("", span, span, "CDATA", v);
                }
            }
            return attrs;
        }

        // --- inline nodes ---

        @Override
        public void visit(Text text) {
            characters(text.getLiteral());
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            startElement("b");
            visitChildren(strongEmphasis);
            endElement("b");
        }

        @Override
        public void visit(Emphasis emphasis) {
            startElement("i");
            visitChildren(emphasis);
            endElement("i");
        }

        @Override
        public void visit(Code code) {
            startElement("code");
            characters(code.getLiteral());
            endElement("code");
        }

        @Override
        public void visit(Link link) {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "href", "href", "CDATA", link.getDestination());
            if (link.getTitle() != null && !link.getTitle().isEmpty()) {
                attrs.addAttribute("", "title", "title", "CDATA", link.getTitle());
            }
            startElement("a", attrs);
            visitChildren(link);
            endElement("a");
        }

        @Override
        public void visit(Image image) {
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute("", "src", "src", "CDATA", image.getDestination());
            if (image.getTitle() != null && !image.getTitle().isEmpty()) {
                attrs.addAttribute("", "title", "title", "CDATA", image.getTitle());
            }
            // Use alt text from child text nodes
            StringBuilder alt = new StringBuilder();
            Node child = image.getFirstChild();
            while (child != null) {
                if (child instanceof Text) {
                    alt.append(((Text) child).getLiteral());
                }
                child = child.getNext();
            }
            attrs.addAttribute("", "alt", "alt", "CDATA", alt.toString());
            emptyElement("img", attrs);
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            emptyElement("br");
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            characters(" ");
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            // Open and close tags arrive as separate nodes with the text between them as
            // siblings, so the tag itself is dropped and the text survives.
            if (INLINE_BR.matcher(htmlInline.getLiteral()).matches()) {
                emptyElement("br");
            }
        }

        // --- GFM extensions ---

        @Override
        public void visit(org.commonmark.node.CustomBlock customBlock) {
            if (customBlock instanceof TableBlock) {
                startElement("table");
                visitChildren(customBlock);
                endElement("table");
            } else {
                visitChildren(customBlock);
            }
        }

        @Override
        public void visit(org.commonmark.node.CustomNode customNode) {
            if (customNode instanceof TableHead) {
                startElement("thead");
                visitChildren(customNode);
                endElement("thead");
            } else if (customNode instanceof TableBody) {
                startElement("tbody");
                visitChildren(customNode);
                endElement("tbody");
            } else if (customNode instanceof TableRow) {
                startElement("tr");
                visitChildren(customNode);
                endElement("tr");
            } else if (customNode instanceof TableCell) {
                TableCell cell = (TableCell) customNode;
                String tag = cell.isHeader() ? "th" : "td";
                AttributesImpl attrs = EMPTY_ATTRS;
                TableCell.Alignment alignment = cell.getAlignment();
                if (alignment != null) {
                    attrs = new AttributesImpl();
                    String align;
                    switch (alignment) {
                        case LEFT:
                            align = "left";
                            break;
                        case CENTER:
                            align = "center";
                            break;
                        case RIGHT:
                            align = "right";
                            break;
                        default:
                            align = null;
                            break;
                    }
                    if (align != null) {
                        attrs.addAttribute("", "align", "align", "CDATA", align);
                    }
                }
                startElement(tag, attrs);
                visitChildren(customNode);
                endElement(tag);
            } else if (customNode instanceof Strikethrough) {
                startElement("s");
                visitChildren(customNode);
                endElement("s");
            } else {
                visitChildren(customNode);
            }
        }

        // --- SAX helpers ---

        private void startElement(String localName) {
            startElement(localName, EMPTY_ATTRS);
        }

        private void startElement(String localName, AttributesImpl attrs) {
            if (saxException != null) {
                return;
            }
            try {
                handler.startElement(XHTML_NS, localName, localName, attrs);
            } catch (SAXException e) {
                saxException = e;
            }
        }

        private void endElement(String localName) {
            if (saxException != null) {
                return;
            }
            try {
                handler.endElement(XHTML_NS, localName, localName);
            } catch (SAXException e) {
                saxException = e;
            }
        }

        private void emptyElement(String localName) {
            emptyElement(localName, EMPTY_ATTRS);
        }

        private void emptyElement(String localName, AttributesImpl attrs) {
            startElement(localName, attrs);
            endElement(localName);
        }

        private void characters(String text) {
            if (saxException != null || text == null || text.isEmpty()) {
                return;
            }
            try {
                char[] chars = text.toCharArray();
                handler.characters(chars, 0, chars.length);
            } catch (SAXException e) {
                saxException = e;
            }
        }
    }
}
