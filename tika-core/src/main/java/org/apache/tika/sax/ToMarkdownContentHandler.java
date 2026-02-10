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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX event handler that writes content as Markdown.
 * Supports headings, paragraphs, bold, italic, links, images, lists (ordered
 * and unordered, including nested), tables (GFM pipe tables), code blocks,
 * inline code, blockquotes, horizontal rules, and definition lists.
 * <p>
 * Content within &lt;script&gt; and &lt;style&gt; tags is ignored.
 * </p>
 *
 * @since Apache Tika 3.2
 */
public class ToMarkdownContentHandler extends DefaultHandler {

    private static final String STYLE = "STYLE";
    private static final String SCRIPT = "SCRIPT";

    private final Writer writer;

    private final Deque<String> elementStack = new ArrayDeque<>();
    private final Deque<ListState> listStack = new ArrayDeque<>();

    // Link buffering
    private StringBuilder linkText;
    private String linkHref;

    // Table buffering (only the outermost table is rendered; nested tables are ignored)
    private int tableDepth = 0;
    private List<List<String>> tableRows;
    private List<String> currentRow;
    private StringBuilder currentCell;

    // Blockquote
    private int blockquoteDepth = 0;

    // Code
    private boolean inPreBlock = false;
    private boolean inInlineCode = false;

    // Script/style suppression
    private int scriptDepth = 0;
    private int styleDepth = 0;

    // Spacing
    private boolean needsBlockSeparator = false;
    private boolean atLineStart = true;

    // Track if we've written any content at all
    private boolean hasContent = false;

    // Track if meaningful (non-whitespace) content was written since last block separator
    private boolean hasContentSinceLastSeparator = false;

    public ToMarkdownContentHandler(Writer writer) {
        this.writer = writer;
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

        // Track script/style depth
        if (name.equals("script")) {
            scriptDepth++;
            elementStack.push(name);
            return;
        }
        if (name.equals("style")) {
            styleDepth++;
            elementStack.push(name);
            return;
        }

        if (scriptDepth > 0 || styleDepth > 0) {
            elementStack.push(name);
            return;
        }

        elementStack.push(name);

        switch (name) {
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                emitBlockSeparator();
                int level = name.charAt(1) - '0';
                write(repeatChar('#', level) + " ");
                break;
            case "p":
                emitBlockSeparator();
                break;
            case "b":
            case "strong":
                write("**");
                break;
            case "i":
            case "em":
                write("*");
                break;
            case "a":
                linkHref = atts.getValue("href");
                linkText = new StringBuilder();
                break;
            case "img":
                String alt = atts.getValue("alt");
                String src = atts.getValue("src");
                write("![" + (alt != null ? alt : "") + "](" + (src != null ? src : "") + ")");
                break;
            case "ul":
            case "ol":
                if (!listStack.isEmpty()) {
                    // nested list — no extra block separator
                } else {
                    emitBlockSeparator();
                }
                listStack.push(new ListState(name.equals("ol"), listStack.size()));
                break;
            case "li":
                if (!listStack.isEmpty()) {
                    ListState state = listStack.peek();
                    String indent = repeatChar(' ', state.depth * 4);
                    if (state.ordered) {
                        state.counter++;
                        write(indent + state.counter + ". ");
                    } else {
                        write(indent + "- ");
                    }
                }
                break;
            case "blockquote":
                emitBlockSeparator();
                blockquoteDepth++;
                break;
            case "pre":
                emitBlockSeparator();
                inPreBlock = true;
                write("```\n");
                break;
            case "code":
                if (!inPreBlock) {
                    inInlineCode = true;
                    write("`");
                }
                break;
            case "br":
                write("\n");
                atLineStart = true;
                break;
            case "hr":
                emitBlockSeparator();
                write("---");
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "table":
                tableDepth++;
                if (tableDepth == 1) {
                    emitBlockSeparator();
                    tableRows = new ArrayList<>();
                }
                break;
            case "tr":
                if (tableDepth == 1 && tableRows != null) {
                    currentRow = new ArrayList<>();
                }
                break;
            case "th":
                if (tableDepth == 1 && currentRow != null) {
                    currentCell = new StringBuilder();
                }
                break;
            case "td":
                if (tableDepth == 1 && currentRow != null) {
                    currentCell = new StringBuilder();
                }
                break;
            case "dt":
                emitBlockSeparator();
                write("**");
                break;
            case "dd":
                write("\n: ");
                break;
            case "div":
                emitBlockSeparator();
                break;
            default:
                // Ignore structural elements like html, head, body, title, meta
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        String name = localName(localName, qName);

        if (!elementStack.isEmpty()) {
            elementStack.pop();
        }

        // Track script/style depth
        if (name.equals("script")) {
            scriptDepth--;
            return;
        }
        if (name.equals("style")) {
            styleDepth--;
            return;
        }

        if (scriptDepth > 0 || styleDepth > 0) {
            return;
        }

        switch (name) {
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "p":
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "b":
            case "strong":
                write("**");
                break;
            case "i":
            case "em":
                write("*");
                break;
            case "a":
                if (linkText != null) {
                    String text = linkText.toString();
                    String href = linkHref != null ? linkHref : "";
                    write("[" + text + "](" + href + ")");
                    linkText = null;
                    linkHref = null;
                }
                break;
            case "ul":
            case "ol":
                if (!listStack.isEmpty()) {
                    listStack.pop();
                }
                if (listStack.isEmpty()) {
                    needsBlockSeparator = true;
                    hasContent = true;
                }
                break;
            case "li":
                write("\n");
                atLineStart = true;
                break;
            case "blockquote":
                blockquoteDepth--;
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "pre":
                if (!endsWithNewline()) {
                    write("\n");
                }
                write("```");
                inPreBlock = false;
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "code":
                if (!inPreBlock) {
                    inInlineCode = false;
                    write("`");
                }
                break;
            case "table":
                if (tableDepth == 1) {
                    emitTable();
                    tableRows = null;
                    currentRow = null;
                    currentCell = null;
                    needsBlockSeparator = true;
                    hasContent = true;
                }
                tableDepth = Math.max(0, tableDepth - 1);
                break;
            case "tr":
                if (tableDepth == 1 && tableRows != null && currentRow != null) {
                    tableRows.add(currentRow);
                    currentRow = null;
                }
                break;
            case "th":
            case "td":
                if (tableDepth == 1 && currentRow != null && currentCell != null) {
                    currentRow.add(currentCell.toString().trim());
                    currentCell = null;
                }
                break;
            case "dt":
                write("**");
                break;
            case "dd":
                needsBlockSeparator = true;
                hasContent = true;
                break;
            case "div":
                needsBlockSeparator = true;
                hasContent = true;
                break;
            default:
                break;
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (scriptDepth > 0 || styleDepth > 0) {
            return;
        }

        // Buffer into link text
        if (linkText != null) {
            linkText.append(ch, start, length);
            return;
        }

        // Buffer into table cell
        if (currentCell != null) {
            currentCell.append(ch, start, length);
            return;
        }

        String text = new String(ch, start, length);

        // In pre blocks, write raw (no escaping)
        if (inPreBlock) {
            write(text);
            return;
        }

        // In inline code, write raw (no escaping)
        if (inInlineCode) {
            write(text);
            return;
        }

        // Skip whitespace-only text at line start; preserve inline spaces
        if (text.trim().isEmpty()) {
            if (!atLineStart) {
                write(" ");
            }
            return;
        }

        // Escape markdown special characters in normal text
        text = escapeMarkdown(text);

        // Add blockquote prefix if needed at line start
        if (blockquoteDepth > 0 && atLineStart && !text.isEmpty()) {
            write(repeatChar('>', blockquoteDepth) + " ");
            atLineStart = false;
        }

        if (!text.isEmpty()) {
            write(text);
            hasContent = true;
            hasContentSinceLastSeparator = true;
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        characters(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            writer.flush();
        } catch (IOException e) {
            throw new SAXException("Error flushing character output", e);
        }
    }

    @Override
    public String toString() {
        return writer.toString();
    }

    private void write(String s) throws SAXException {
        try {
            writer.write(s);
            if (!s.isEmpty()) {
                atLineStart = s.charAt(s.length() - 1) == '\n';
                if (!s.trim().isEmpty()) {
                    hasContentSinceLastSeparator = true;
                }
            }
        } catch (IOException e) {
            throw new SAXException("Error writing: " + s, e);
        }
    }

    private void emitBlockSeparator() throws SAXException {
        if (needsBlockSeparator && hasContent && hasContentSinceLastSeparator) {
            write("\n\n");
            needsBlockSeparator = false;
            atLineStart = true;
            hasContentSinceLastSeparator = false;
        } else {
            needsBlockSeparator = false;
        }
    }

    private void emitTable() throws SAXException {
        if (tableRows == null || tableRows.isEmpty()) {
            return;
        }

        // Determine column count
        int cols = 0;
        for (List<String> row : tableRows) {
            cols = Math.max(cols, row.size());
        }

        // Emit rows
        for (int r = 0; r < tableRows.size(); r++) {
            List<String> row = tableRows.get(r);
            StringBuilder sb = new StringBuilder("|");
            for (int c = 0; c < cols; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                sb.append(" ").append(cell).append(" |");
            }
            write(sb.toString());
            write("\n");

            // Insert separator after first row
            if (r == 0) {
                StringBuilder sep = new StringBuilder("|");
                for (int c = 0; c < cols; c++) {
                    sep.append(" --- |");
                }
                write(sep.toString());
                write("\n");
            }
        }
    }

    private boolean endsWithNewline() {
        String s = writer.toString();
        return !s.isEmpty() && s.charAt(s.length() - 1) == '\n';
    }

    private static String escapeMarkdown(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\':
                case '`':
                case '*':
                case '_':
                case '[':
                case ']':
                case '#':
                case '|':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private static String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static String localName(String localName, String qName) {
        if (localName != null && !localName.isEmpty()) {
            return localName.toLowerCase(Locale.ROOT);
        }
        if (qName != null) {
            // Strip namespace prefix
            int colon = qName.indexOf(':');
            String name = colon >= 0 ? qName.substring(colon + 1) : qName;
            return name.toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static class ListState {
        final boolean ordered;
        final int depth;
        int counter;

        ListState(boolean ordered, int depth) {
            this.ordered = ordered;
            this.depth = depth;
            this.counter = 0;
        }
    }
}
