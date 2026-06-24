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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Document;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX event handler that writes content as Markdown.
 * Supports headings, paragraphs, bold, italic, strikethrough, links, images,
 * lists (ordered and unordered, including nested), tables (GFM pipe tables),
 * code blocks, inline code, blockquotes, horizontal rules, and definition
 * lists.
 * <p>
 * The handler builds a <a href="https://github.com/commonmark/commonmark-java">
 * commonmark-java</a> document model from the SAX event stream and renders it
 * with commonmark's {@code MarkdownRenderer}. Document text is added to the
 * model as raw literals, so escaping of Markdown metacharacters — including
 * characters that would otherwise break out of a link, image, or table cell —
 * is performed in one place by the renderer rather than at each emit site.
 * <p>
 * The handler tolerates malformed input (unbalanced or misnested tags): block
 * elements are always attached at a structurally valid point so a well-formed
 * document model is rendered regardless of the event stream.
 * <p>
 * Content within &lt;script&gt; and &lt;style&gt; tags is ignored.
 *
 * @since Apache Tika 3.2
 */
public class ToMarkdownContentHandler extends DefaultHandler {

    private static final List<Extension> EXTENSIONS = Arrays.asList(
            TablesExtension.create(), StrikethroughExtension.create());

    private final Writer writer;
    private final MarkdownRenderer renderer =
            MarkdownRenderer.builder().extensions(EXTENSIONS).build();

    private final Document document = new Document();
    private final Deque<Node> stack = new ArrayDeque<>();

    // An implicit Paragraph opened to hold inline content that appeared in a
    // block container (e.g. text directly under <body>/<li>/<blockquote>).
    private Paragraph implicitParagraph;

    // Buffer for inline <code> literal (commonmark Code holds a literal, not children).
    private Code inlineCode;
    private StringBuilder inlineCodeText;

    // Buffer for <pre> fenced code block literal.
    private FencedCodeBlock codeBlock;
    private StringBuilder codeBlockText;

    // Table state (outermost table only).
    private int tableDepth;
    private TableBlock tableBlock;
    private TableBody tableBody;
    private int rowIndex;
    private boolean inHeaderRow;

    // <script>/<style> content is dropped.
    private int suppressDepth;

    // True once endDocument has rendered the full document to the writer.
    private boolean finished;

    public ToMarkdownContentHandler(Writer writer) {
        this.writer = writer;
        this.stack.push(document);
    }

    public ToMarkdownContentHandler(OutputStream stream, String encoding)
            throws UnsupportedEncodingException {
        this(new OutputStreamWriter(stream, encoding));
    }

    public ToMarkdownContentHandler() {
        this(new StringWriter());
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts)
            throws SAXException {
        String name = localName(localName, qName);

        if (name.equals("script") || name.equals("style")) {
            suppressDepth++;
            return;
        }
        if (suppressDepth > 0) {
            return;
        }

        switch (name) {
            // --- block-level ---
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                Heading heading = new Heading();
                heading.setLevel(name.charAt(1) - '0');
                openBlock(heading);
                break;
            case "p":
                openBlock(new Paragraph());
                break;
            case "blockquote":
                openBlock(new BlockQuote());
                break;
            case "ul":
                openBlock(newBulletList());
                break;
            case "ol":
                OrderedList ol = new OrderedList();
                ol.setMarkerStartNumber(1);
                ol.setMarkerDelimiter(".");
                ol.setTight(true);
                openBlock(ol);
                break;
            case "li":
                openListItem();
                break;
            case "pre":
                popToBlockHost();
                codeBlock = new FencedCodeBlock();
                codeBlock.setFenceCharacter("`");
                codeBlock.setOpeningFenceLength(3);
                codeBlock.setLiteral("");
                stack.peek().appendChild(codeBlock);
                codeBlockText = new StringBuilder();
                break;
            case "hr":
                ThematicBreak hr = new ThematicBreak();
                hr.setLiteral("---");
                addBlockLeaf(hr);
                break;
            case "div":
                // Block boundary: close any open paragraph so adjacent divs separate.
                popToBlockHost();
                break;
            case "table":
                startTable();
                break;
            case "tr":
                startRow();
                break;
            case "th":
                startCell(true);
                break;
            case "td":
                startCell(false);
                break;
            case "dt":
                Paragraph term = new Paragraph();
                openBlock(term);
                StrongEmphasis bold = new StrongEmphasis();
                term.appendChild(bold);
                stack.push(bold);
                break;
            case "dd":
                Paragraph def = new Paragraph();
                openBlock(def);
                def.appendChild(new Text(": "));
                break;

            // --- inline-level ---
            case "b":
            case "strong":
                openInline(new StrongEmphasis());
                break;
            case "i":
            case "em":
                openInline(new Emphasis());
                break;
            case "s":
            case "strike":
            case "del":
                openInline(new Strikethrough("~~"));
                break;
            case "a":
                String href = atts.getValue("href");
                openInline(new Link(href != null ? href : "", null));
                break;
            case "code":
                if (codeBlockText == null) {
                    ensureInlineContainer();
                    inlineCode = new Code("");
                    stack.peek().appendChild(inlineCode);
                    inlineCodeText = new StringBuilder();
                }
                break;
            case "br":
                ensureInlineContainer();
                stack.peek().appendChild(new HardLineBreak());
                break;
            case "img":
                ensureInlineContainer();
                Image img = new Image(value(atts, "src"), null);
                String alt = atts.getValue("alt");
                if (alt != null && !alt.isEmpty()) {
                    img.appendChild(new Text(alt));
                }
                stack.peek().appendChild(img);
                break;

            default:
                // html, head, body, title, meta, div, span, dl, thead, tbody...
                // structurally transparent; their inline children flow to the
                // nearest real container.
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String name = localName(localName, qName);

        if (name.equals("script") || name.equals("style")) {
            suppressDepth = Math.max(0, suppressDepth - 1);
            return;
        }
        if (suppressDepth > 0) {
            return;
        }

        switch (name) {
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "p":
            case "blockquote":
            case "ul":
            case "ol":
            case "li":
            case "b":
            case "strong":
            case "i":
            case "em":
            case "s":
            case "strike":
            case "del":
            case "a":
                closeImplicitParagraph();
                pop();
                break;
            case "dt":
                pop(); // StrongEmphasis
                pop(); // Paragraph
                break;
            case "dd":
                pop();
                break;
            case "pre":
                if (codeBlock != null) {
                    codeBlock.setLiteral(withTrailingNewline(codeBlockText.toString()));
                    codeBlock = null;
                    codeBlockText = null;
                }
                break;
            case "code":
                if (inlineCode != null) {
                    inlineCode.setLiteral(inlineCodeText.toString());
                    inlineCode = null;
                    inlineCodeText = null;
                }
                break;
            case "div":
                closeImplicitParagraph();
                break;
            case "table":
                endTable();
                break;
            case "tr":
                if (tableDepth == 1) {
                    pop();
                    rowIndex++;
                }
                break;
            case "th":
            case "td":
                if (tableDepth == 1) {
                    pop();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (suppressDepth > 0) {
            return;
        }
        if (codeBlockText != null) {
            codeBlockText.append(ch, start, length);
            return;
        }
        if (inlineCodeText != null) {
            inlineCodeText.append(ch, start, length);
            return;
        }

        String text = new String(ch, start, length);
        if (text.trim().isEmpty()) {
            // Inter-element whitespace: drop between blocks, collapse within inline runs.
            if (!isBlockContainer(stack.peek())) {
                stack.peek().appendChild(new Text(" "));
            }
            return;
        }

        if (isStructuralOnly(stack.peek())) {
            // Stray text inside a list/table wrapper — not a valid inline position; drop.
            return;
        }
        ensureInlineContainer();
        // Newlines inside prose would otherwise survive as literal line breaks.
        stack.peek().appendChild(new Text(collapseLineBreaks(text)));
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            renderer.render(document, writer);
            writer.flush();
            finished = true;
        } catch (IOException e) {
            throw new SAXException("Error writing markdown", e);
        }
    }

    @Override
    public String toString() {
        if (finished) {
            return writer.toString();
        }
        // Parsing did not complete (e.g. the parser threw mid-document). Render the
        // content accumulated so far so partial content is not lost on failure — the
        // streaming writer this replaced exposed partial content the same way.
        return renderer.render(document);
    }

    // --- tree construction helpers ---

    /**
     * Append a block-level node at a structurally valid point and descend into
     * it. commonmark requires a block's parent to be a block, so any inline (or
     * other non-hosting) nodes left open are closed first.
     */
    private void openBlock(Node block) {
        popToBlockHost();
        stack.peek().appendChild(block);
        stack.push(block);
    }

    /** Append a block-level leaf (hr, code block, table) without descending. */
    private void addBlockLeaf(Node block) {
        popToBlockHost();
        stack.peek().appendChild(block);
    }

    /**
     * A list item is only valid inside a list. If the current point isn't a
     * list (a stray {@code <li>} or misnested markup), recover by opening an
     * implicit bullet list so the model stays renderable.
     */
    private void openListItem() {
        if (!(stack.peek() instanceof ListBlock)) {
            popToBlockHost();
            BulletList implicit = newBulletList();
            stack.peek().appendChild(implicit);
            stack.push(implicit);
        }
        ListItem li = new ListItem();
        stack.peek().appendChild(li);
        stack.push(li);
    }

    private void openInline(Node node) {
        ensureInlineContainer();
        stack.peek().appendChild(node);
        stack.push(node);
    }

    private void pop() {
        if (stack.size() > 1) {
            Node popped = stack.pop();
            if (popped == implicitParagraph) {
                implicitParagraph = null;
            }
        }
    }

    /** Pop open nodes until the top can host block children (Document/blockquote/list item). */
    private void popToBlockHost() {
        while (stack.size() > 1 && !canHostBlocks(stack.peek())) {
            Node popped = stack.pop();
            if (popped == implicitParagraph) {
                implicitParagraph = null;
            }
        }
    }

    /**
     * If the current insertion point is a block container that cannot hold
     * inline content directly, open an implicit {@link Paragraph} to hold it.
     */
    private void ensureInlineContainer() {
        Node top = stack.peek();
        if (canHostBlocks(top)) {
            Paragraph p = new Paragraph();
            top.appendChild(p);
            stack.push(p);
            implicitParagraph = p;
        }
    }

    private void closeImplicitParagraph() {
        if (implicitParagraph != null && stack.peek() == implicitParagraph) {
            stack.pop();
            implicitParagraph = null;
        }
    }

    private void startTable() {
        tableDepth++;
        if (tableDepth == 1) {
            popToBlockHost();
            tableBlock = new TableBlock();
            tableBody = null;
            rowIndex = 0;
            stack.peek().appendChild(tableBlock);
        }
    }

    private void endTable() {
        if (tableDepth == 1) {
            tableBlock = null;
            tableBody = null;
        }
        tableDepth = Math.max(0, tableDepth - 1);
    }

    private void startRow() {
        if (tableDepth != 1) {
            return;
        }
        TableRow row = new TableRow();
        if (rowIndex == 0) {
            TableHead head = new TableHead();
            tableBlock.appendChild(head);
            head.appendChild(row);
            inHeaderRow = true;
        } else {
            if (tableBody == null) {
                tableBody = new TableBody();
                tableBlock.appendChild(tableBody);
            }
            tableBody.appendChild(row);
            inHeaderRow = false;
        }
        stack.push(row);
    }

    private void startCell(boolean header) {
        if (tableDepth != 1 || !(stack.peek() instanceof TableRow)) {
            return;
        }
        TableCell cell = new TableCell();
        cell.setHeader(header || inHeaderRow);
        stack.peek().appendChild(cell);
        stack.push(cell);
    }

    private static BulletList newBulletList() {
        BulletList list = new BulletList();
        list.setMarker("-");
        list.setTight(true);
        return list;
    }

    private static boolean canHostBlocks(Node n) {
        return n instanceof Document || n instanceof BlockQuote || n instanceof ListItem;
    }

    private static boolean isBlockContainer(Node n) {
        return n instanceof Document || n instanceof BlockQuote || n instanceof ListItem
                || n instanceof ListBlock || n instanceof TableBlock || n instanceof TableHead
                || n instanceof TableBody || n instanceof TableRow;
    }

    private static boolean isStructuralOnly(Node n) {
        return n instanceof ListBlock || n instanceof TableBlock || n instanceof TableHead
                || n instanceof TableBody || n instanceof TableRow;
    }

    private static String collapseLineBreaks(String s) {
        return s.replace('\r', ' ').replace('\n', ' ');
    }

    private static String withTrailingNewline(String s) {
        if (s.isEmpty() || s.charAt(s.length() - 1) == '\n') {
            return s;
        }
        return s + "\n";
    }

    private static String value(Attributes atts, String name) {
        String v = atts.getValue(name);
        return v != null ? v : "";
    }

    private static String localName(String localName, String qName) {
        if (localName != null && !localName.isEmpty()) {
            return localName.toLowerCase(Locale.ROOT);
        }
        if (qName != null) {
            int colon = qName.indexOf(':');
            String name = colon >= 0 ? qName.substring(colon + 1) : qName;
            return name.toLowerCase(Locale.ROOT);
        }
        return "";
    }
}
