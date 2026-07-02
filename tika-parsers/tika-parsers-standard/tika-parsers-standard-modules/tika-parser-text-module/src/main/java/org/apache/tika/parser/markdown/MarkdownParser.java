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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Markdown documents ({@code text/markdown}).
 * <p>
 * Uses <a href="https://github.com/commonmark/commonmark-java">commonmark-java</a>
 * (already a Tika dependency, and the same library behind
 * {@link org.apache.tika.sax.ToMarkdownContentHandler}) to parse the document into an
 * AST, then emits structured XHTML. Headings become {@code h1..h6}, lists become
 * {@code ul}/{@code ol}/{@code li}, fenced code becomes {@code pre}/{@code code},
 * GFM tables become {@code table}, emphasis/strong/strikethrough become
 * {@code em}/{@code strong}/{@code del}, links and images become {@code a} and
 * {@code img}. Because the emitted vocabulary matches what {@code ToMarkdownContentHandler}
 * consumes, a document round-trips markdown to XHTML and back.
 * <p>
 * Fidelity: all <em>content</em> carried by the commonmark AST is preserved -- text and
 * code literals (raw HTML as escaped text), link/image destinations and titles, image alt
 * text (including code spans), heading levels, table cell alignment and header cells,
 * ordered-list start numbers ({@code <ol start=...>}), the full code-fence info string
 * ({@code data-info} when it carries more than the language token), and hard/soft line
 * breaks. Only markdown <em>syntax presentation</em> is normalized (bullet/fence/emphasis
 * delimiter characters, ATX vs setext headings), matching commonmark's reference
 * {@code HtmlRenderer}. Unused link reference definitions produce no output, also matching
 * the reference renderer.
 */
@TikaComponent
public class MarkdownParser extends AbstractEncodingDetectorParser {

    private static final long serialVersionUID = 7690358159471186692L;

    private static final MediaType MARKDOWN = MediaType.text("markdown");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MARKDOWN);

    private static final List<Extension> EXTENSIONS =
            List.of(TablesExtension.create(), StrikethroughExtension.create());

    // commonmark Parser is immutable and thread-safe, so a single instance is reused.
    private static final Parser COMMONMARK = Parser.builder().extensions(EXTENSIONS).build();

    public MarkdownParser() {
        super();
    }

    public MarkdownParser(EncodingDetector encodingDetector) {
        super(encodingDetector);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        Node document;
        try (AutoDetectReader reader = new AutoDetectReader(tis, metadata,
                getEncodingDetector(context))) {
            Charset charset = reader.getCharset();
            metadata.set(Metadata.CONTENT_TYPE, new MediaType(MARKDOWN, charset).toString());
            metadata.set(Metadata.CONTENT_ENCODING, charset.name());
            document = COMMONMARK.parseReader(reader);
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            document.accept(new XhtmlMarkdownVisitor(xhtml));
        } catch (WrappedSaxException e) {
            throw e.getCause();
        }
        xhtml.endDocument();
    }

    /**
     * Walks the commonmark AST and emits XHTML SAX events.
     */
    private static final class XhtmlMarkdownVisitor extends AbstractVisitor {

        private final XHTMLContentHandler xhtml;

        XhtmlMarkdownVisitor(XHTMLContentHandler xhtml) {
            this.xhtml = xhtml;
        }

        @Override
        public void visit(Heading heading) {
            String tag = "h" + Math.max(1, Math.min(6, heading.getLevel()));
            start(tag);
            visitChildren(heading);
            end(tag);
        }

        @Override
        public void visit(Paragraph paragraph) {
            // Tight list items render their paragraph content inline (no <p>), matching markdown.
            if (isTightListItemParagraph(paragraph)) {
                visitChildren(paragraph);
                return;
            }
            start("p");
            visitChildren(paragraph);
            end("p");
        }

        @Override
        public void visit(BlockQuote blockQuote) {
            start("blockquote");
            visitChildren(blockQuote);
            end("blockquote");
        }

        @Override
        public void visit(BulletList bulletList) {
            start("ul");
            visitChildren(bulletList);
            end("ul");
        }

        @Override
        public void visit(OrderedList orderedList) {
            AttributesImpl atts = new AttributesImpl();
            Integer startNumber = orderedList.getMarkerStartNumber();
            if (startNumber != null && startNumber != 1) {
                addAttribute(atts, "start", String.valueOf(startNumber));
            }
            start("ol", atts);
            visitChildren(orderedList);
            end("ol");
        }

        @Override
        public void visit(ListItem listItem) {
            start("li");
            visitChildren(listItem);
            end("li");
        }

        @Override
        public void visit(ThematicBreak thematicBreak) {
            start("hr");
            end("hr");
        }

        @Override
        public void visit(FencedCodeBlock codeBlock) {
            emitCodeBlock(codeBlock.getInfo(), codeBlock.getLiteral());
        }

        @Override
        public void visit(IndentedCodeBlock codeBlock) {
            emitCodeBlock(null, codeBlock.getLiteral());
        }

        @Override
        public void visit(HtmlBlock htmlBlock) {
            // Raw HTML block: keep the text, escaped, so nothing is lost or injected.
            start("p");
            characters(htmlBlock.getLiteral());
            end("p");
        }

        @Override
        public void visit(Text text) {
            characters(text.getLiteral());
        }

        @Override
        public void visit(Emphasis emphasis) {
            start("em");
            visitChildren(emphasis);
            end("em");
        }

        @Override
        public void visit(StrongEmphasis strongEmphasis) {
            start("strong");
            visitChildren(strongEmphasis);
            end("strong");
        }

        @Override
        public void visit(Code code) {
            start("code");
            characters(code.getLiteral());
            end("code");
        }

        @Override
        public void visit(Link link) {
            AttributesImpl atts = new AttributesImpl();
            addAttribute(atts, "href", link.getDestination());
            if (link.getTitle() != null) {
                addAttribute(atts, "title", link.getTitle());
            }
            start("a", atts);
            visitChildren(link);
            end("a");
        }

        @Override
        public void visit(Image image) {
            AttributesImpl atts = new AttributesImpl();
            addAttribute(atts, "src", image.getDestination());
            String alt = collectText(image);
            if (!alt.isEmpty()) {
                addAttribute(atts, "alt", alt);
            }
            if (image.getTitle() != null) {
                addAttribute(atts, "title", image.getTitle());
            }
            start("img", atts);
            end("img");
        }

        @Override
        public void visit(HardLineBreak hardLineBreak) {
            start("br");
            end("br");
        }

        @Override
        public void visit(SoftLineBreak softLineBreak) {
            characters("\n");
        }

        @Override
        public void visit(HtmlInline htmlInline) {
            characters(htmlInline.getLiteral());
        }

        @Override
        public void visit(LinkReferenceDefinition linkReferenceDefinition) {
            // Reference definitions produce no rendered output.
        }

        @Override
        public void visit(CustomBlock customBlock) {
            if (customBlock instanceof TableBlock) {
                start("table");
                visitChildren(customBlock);
                end("table");
            } else {
                visitChildren(customBlock);
            }
        }

        @Override
        public void visit(CustomNode customNode) {
            if (customNode instanceof Strikethrough) {
                start("del");
                visitChildren(customNode);
                end("del");
            } else if (customNode instanceof TableHead) {
                start("thead");
                visitChildren(customNode);
                end("thead");
            } else if (customNode instanceof TableBody) {
                start("tbody");
                visitChildren(customNode);
                end("tbody");
            } else if (customNode instanceof TableRow) {
                start("tr");
                visitChildren(customNode);
                end("tr");
            } else if (customNode instanceof TableCell) {
                TableCell cell = (TableCell) customNode;
                String tag = cell.isHeader() ? "th" : "td";
                AttributesImpl atts = new AttributesImpl();
                String align = alignment(cell.getAlignment());
                if (align != null) {
                    addAttribute(atts, "align", align);
                }
                start(tag, atts);
                visitChildren(customNode);
                end(tag);
            } else {
                visitChildren(customNode);
            }
        }

        private void emitCodeBlock(String info, String literal) {
            AttributesImpl atts = new AttributesImpl();
            String language = language(info);
            if (language != null) {
                addAttribute(atts, "class", "language-" + language);
            }
            // The fence info string can carry more than the language token
            // (e.g. ```java title="Example"); keep the full string so nothing is lost.
            if (info != null && !info.trim().isEmpty() && !info.trim().equals(language)) {
                addAttribute(atts, "data-info", info.trim());
            }
            start("pre");
            start("code", atts);
            characters(literal);
            end("code");
            end("pre");
        }

        private void start(String name) {
            try {
                xhtml.startElement(name);
            } catch (SAXException e) {
                throw new WrappedSaxException(e);
            }
        }

        private void start(String name, AttributesImpl atts) {
            try {
                xhtml.startElement(name, atts);
            } catch (SAXException e) {
                throw new WrappedSaxException(e);
            }
        }

        private void end(String name) {
            try {
                xhtml.endElement(name);
            } catch (SAXException e) {
                throw new WrappedSaxException(e);
            }
        }

        private void characters(String text) {
            try {
                xhtml.characters(text);
            } catch (SAXException e) {
                throw new WrappedSaxException(e);
            }
        }

        private static boolean isTightListItemParagraph(Paragraph paragraph) {
            Node parent = paragraph.getParent();
            if (parent instanceof ListItem) {
                Node list = parent.getParent();
                return list instanceof ListBlock && ((ListBlock) list).isTight();
            }
            return false;
        }

        private static String language(String info) {
            if (info == null) {
                return null;
            }
            String trimmed = info.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            int space = trimmed.indexOf(' ');
            return space < 0 ? trimmed : trimmed.substring(0, space);
        }

        private static String alignment(TableCell.Alignment alignment) {
            if (alignment == null) {
                return null;
            }
            switch (alignment) {
                case LEFT:
                    return "left";
                case CENTER:
                    return "center";
                case RIGHT:
                    return "right";
                default:
                    return null;
            }
        }

        /** Flattens inline content to plain text (for img alt), losing no literals. */
        private static String collectText(Node node) {
            StringBuilder sb = new StringBuilder();
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof Text) {
                    sb.append(((Text) child).getLiteral());
                } else if (child instanceof Code) {
                    sb.append(((Code) child).getLiteral());
                } else if (child instanceof HtmlInline) {
                    sb.append(((HtmlInline) child).getLiteral());
                } else if (child instanceof SoftLineBreak || child instanceof HardLineBreak) {
                    sb.append(' ');
                } else {
                    sb.append(collectText(child));
                }
            }
            return sb.toString();
        }

        private static void addAttribute(AttributesImpl atts, String name, String value) {
            atts.addAttribute("", name, name, "CDATA", value == null ? "" : value);
        }
    }

    /**
     * Carries a {@link SAXException} out through the commonmark visitor, whose methods
     * cannot declare checked exceptions.
     */
    private static final class WrappedSaxException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        WrappedSaxException(SAXException cause) {
            super(cause);
        }

        @Override
        public synchronized SAXException getCause() {
            return (SAXException) super.getCause();
        }
    }
}
