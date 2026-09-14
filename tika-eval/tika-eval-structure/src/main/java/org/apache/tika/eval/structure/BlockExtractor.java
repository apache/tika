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
package org.apache.tika.eval.structure;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Splits an XHTML extract into blocks in document order. Text inside a block element
 * (p, h1-h6, li, td, th, blockquote, pre, dt, dd) is that block's; a block inside a block
 * (a p in an li) ends the outer block's text run. Text outside any block element (a figure's
 * Alt, a bare div) becomes a block of its own, named after the element it sits in. Pages
 * count {@code <div class="page">}; artifact and untagged divs flag the blocks they hold.
 */
public final class BlockExtractor extends DefaultHandler {

    private static final Set<String> BLOCK_ELEMENTS =
            Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "td", "th", "blockquote",
                    "pre", "dt", "dd");

    private final List<Block> blocks = new ArrayList<>();
    private final Deque<Open> open = new ArrayDeque<>();
    private final Deque<String> elements = new ArrayDeque<>();
    private StringBuilder loose;
    private String looseElement;
    private int page = -1;
    private int depth;
    private int artifactUntil = -1;
    private int untaggedUntil = -1;

    private static final class Open {
        final String element;
        final StringBuilder text = new StringBuilder();

        Open(String element) {
            this.element = element;
        }
    }

    public static List<Block> extract(String xhtml) throws TikaException, SAXException,
            IOException {
        BlockExtractor extractor = new BlockExtractor();
        XMLReaderUtils.getSAXParser().parse(new InputSource(new StringReader(xhtml)), extractor);
        return extractor.blocks;
    }

    public List<Block> blocks() {
        return blocks;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        String name = localName == null || localName.isEmpty() ? qName : localName;
        int colon = name.indexOf(':');
        if (colon >= 0) {
            name = name.substring(colon + 1);
        }
        name = name.toLowerCase(Locale.ROOT);
        String cls = attributes.getValue("class");
        depth++;
        if ("div".equals(name) && cls != null) {
            if ("page".equals(cls)) {
                page++;
            } else if ("artifact".equals(cls) && artifactUntil < 0) {
                artifactUntil = depth;
            } else if ("untagged".equals(cls) && untaggedUntil < 0) {
                untaggedUntil = depth;
            }
        }
        flushLoose();
        elements.push(cls == null ? name : name + ":" + cls);
        if (BLOCK_ELEMENTS.contains(name)) {
            // a block inside a block: what the outer one had so far is its own block
            if (!open.isEmpty()) {
                flush(open.peek());
            }
            open.push(new Open(name));
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String element = elements.pop();
        flushLoose();
        String name = element.indexOf(':') >= 0 ? element.substring(0, element.indexOf(':')) :
                element;
        if (BLOCK_ELEMENTS.contains(name) && !open.isEmpty() && open.peek().element.equals(name)) {
            flush(open.pop());
        }
        if (depth == artifactUntil) {
            artifactUntil = -1;
        }
        if (depth == untaggedUntil) {
            untaggedUntil = -1;
        }
        depth--;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        append(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        append(ch, start, length);
    }

    private void append(char[] ch, int start, int length) {
        if (!open.isEmpty()) {
            open.peek().text.append(ch, start, length);
            return;
        }
        if (loose == null) {
            loose = new StringBuilder();
            looseElement = elements.isEmpty() ? "" : elements.peek();
        }
        loose.append(ch, start, length);
    }

    private void flushLoose() {
        if (loose != null) {
            add(looseElement, loose);
            loose = null;
        }
    }

    private void flush(Open o) {
        add(o.element, o.text);
        o.text.setLength(0);
    }

    private void add(String element, CharSequence text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return;
        }
        blocks.add(new Block(blocks.size(), Math.max(page, 0), kind(element),
                artifactUntil >= 0, untaggedUntil >= 0, tokens));
    }

    /** A paragraph inside a cell is cell content; inside a list item, list content. */
    private Block.Kind kind(String element) {
        Block.Kind kind = kindOf(element);
        for (Open o : open) {
            Block.Kind outer = kindOf(o.element);
            if (outer == Block.Kind.CELL) {
                return outer;
            }
            if (outer == Block.Kind.LI && kind != Block.Kind.CELL) {
                kind = outer;
            }
        }
        return kind;
    }

    static Block.Kind kindOf(String element) {
        String name = element.indexOf(':') >= 0 ? element.substring(0, element.indexOf(':')) :
                element;
        switch (name) {
            case "p":
                return Block.Kind.P;
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                return Block.Kind.H;
            case "li":
                return Block.Kind.LI;
            case "td":
            case "th":
                return Block.Kind.CELL;
            default:
                return Block.Kind.OTHER;
        }
    }

    /**
     * Lower-cased runs of letters and digits; every character of a script written without
     * word spaces (Han, kana, Hangul, Thai, Lao, Khmer, Myanmar) is a token of its own, so
     * an extractor that spaces ideographs and one that does not tokenize alike.
     */
    static List<String> tokenize(CharSequence text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = Character.codePointAt(text, i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                if (unspaced(cp)) {
                    if (sb.length() > 0) {
                        tokens.add(sb.toString());
                        sb.setLength(0);
                    }
                    tokens.add(new String(Character.toChars(Character.toLowerCase(cp))));
                } else {
                    sb.appendCodePoint(Character.toLowerCase(cp));
                }
            } else if (sb.length() > 0) {
                tokens.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString());
        }
        return tokens;
    }

    private static boolean unspaced(int cp) {
        switch (Character.UnicodeScript.of(cp)) {
            case HAN:
            case HIRAGANA:
            case KATAKANA:
            case HANGUL:
            case THAI:
            case LAO:
            case KHMER:
            case MYANMAR:
                return true;
            default:
                return false;
        }
    }
}
