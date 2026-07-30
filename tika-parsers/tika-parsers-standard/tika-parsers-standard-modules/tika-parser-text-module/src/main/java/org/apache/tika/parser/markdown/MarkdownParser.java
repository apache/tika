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

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.exception.RuntimeSAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractEncodingDetectorParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.datauri.DataURIScheme;
import org.apache.tika.parser.datauri.DataURISchemeParseException;
import org.apache.tika.parser.datauri.DataURISchemeUtil;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Parser for Markdown ({@code text/markdown}). Uses commonmark-java (with the GFM
 * tables and strikethrough extensions) to emit structured XHTML. Raw HTML in the
 * source is emitted as escaped text; data: URIs in image/link destinations and in
 * raw HTML are extracted as embedded documents.
 */
@TikaComponent
public class MarkdownParser extends AbstractEncodingDetectorParser {

    private static final long serialVersionUID = 7690358159471186692L;

    private static final MediaType MARKDOWN = MediaType.text("markdown");

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MARKDOWN);

    private static final List<Extension> EXTENSIONS =
            List.of(TablesExtension.create(), StrikethroughExtension.create());

    //immutable and thread-safe.
    //maxOpenBlockParsers caps block nesting: deeper blocks are parsed as flat paragraph text
    //rather than nested structure, so a pathologically deep block document still extracts.
    //Kept below SecureContentHandler's 100-level element-nesting cap so the flattened output
    //stays under that limit and is emitted rather than rejected as a suspected zip bomb.
    private static final Parser COMMONMARK =
            Parser.builder().extensions(EXTENSIONS).maxOpenBlockParsers(64).build();

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
        } catch (StackOverflowError e) {
            //for reasons
            throw new TikaException("Markdown is too deeply nested to parse", e);
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata, context);
        xhtml.startDocument();
        try {
            document.accept(new XhtmlMarkdownVisitor(xhtml, metadata, context));
        } catch (RuntimeSAXException e) {
            throw (SAXException) e.getCause();
        }
        xhtml.endDocument();
    }

    /**
     * Walks the commonmark AST and emits XHTML SAX events.
     */
    private static final class XhtmlMarkdownVisitor extends AbstractVisitor {

        private final XHTMLContentHandler xhtml;
        private final Metadata metadata;
        private final ParseContext context;
        private final DataURISchemeUtil dataURISchemeUtil = new DataURISchemeUtil();

        XhtmlMarkdownVisitor(XHTMLContentHandler xhtml, Metadata metadata, ParseContext context) {
            this.xhtml = xhtml;
            this.metadata = metadata;
            this.context = context;
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
            //tight list items render their paragraph content inline, as in markdown
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
            //raw html: keep the text escaped so nothing is injected
            start("p");
            characters(htmlBlock.getLiteral());
            end("p");
            extractDataURIs(htmlBlock.getLiteral());
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
            maybeEmbedDataURI(link.getDestination());
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
            maybeEmbedDataURI(image.getDestination());
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
            extractDataURIs(htmlInline.getLiteral());
        }

        @Override
        public void visit(LinkReferenceDefinition linkReferenceDefinition) {
            //no rendered output
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
            //keep any fence info beyond the language token
            if (info != null && !info.trim().isEmpty() && !info.trim().equals(language)) {
                addAttribute(atts, "data-info", info.trim());
            }
            start("pre");
            start("code", atts);
            characters(literal);
            end("code");
            end("pre");
        }

        private void maybeEmbedDataURI(String destination) {
            if (destination == null || !destination.startsWith("data:")) {
                return;
            }
            try {
                embed(dataURISchemeUtil.parse(destination));
            } catch (DataURISchemeParseException e) {
                //swallow
            }
        }

        private void extractDataURIs(String literal) {
            if (literal == null) {
                return;
            }
            for (DataURIScheme dataURIScheme : dataURISchemeUtil.extract(literal)) {
                embed(dataURIScheme);
            }
        }

        private void embed(DataURIScheme dataURIScheme) {
            Metadata m = Metadata.newInstance(context);
            m.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    TikaCoreProperties.EmbeddedResourceType.INLINE.toString());
            if (dataURIScheme.getMediaType() != null) {
                m.set(Metadata.CONTENT_TYPE, dataURIScheme.getMediaType().toString());
            }
            EmbeddedDocumentExtractor extractor =
                    EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
            if (extractor.shouldParseEmbedded(m)) {
                try (TikaInputStream tis = TikaInputStream.get(dataURIScheme.getInputStream())) {
                    extractor.parseEmbedded(tis, xhtml, m, context, true);
                } catch (IOException e) {
                    EmbeddedDocumentUtil.recordEmbeddedStreamException(e, metadata);
                } catch (SAXException e) {
                    throw new RuntimeSAXException(e);
                }
            }
        }

        private void start(String name) {
            try {
                xhtml.startElement(name);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        private void start(String name, AttributesImpl atts) {
            try {
                xhtml.startElement(name, atts);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        private void end(String name) {
            try {
                xhtml.endElement(name);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
            }
        }

        private void characters(String text) {
            try {
                xhtml.characters(text);
            } catch (SAXException e) {
                throw new RuntimeSAXException(e);
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
}
