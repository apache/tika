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

import org.xml.sax.Attributes;

/**
 * Records a table's SAX events as an HTML literal, for tables a GFM pipe table
 * cannot express (merged cells).
 * <p>
 * The literal becomes a CommonMark HTML block, which ends at the first blank line,
 * so it never contains one: whitespace collapses to a space, and newlines in
 * {@code <pre>} are written as {@code &#10;}.
 */
final class HtmlTableRecorder {

    /** Elements written by name; the rest contribute their text. */
    private static final Set<String> KEPT = Set.of(
            "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
            "p", "br", "ul", "ol", "li", "blockquote", "pre", "code",
            "h1", "h2", "h3", "h4", "h5", "h6", "b", "strong", "i", "em",
            "s", "strike", "del", "sup", "sub", "a", "img");

    private static final Set<String> VOID = Set.of("br", "img");

    /** Table structure: whitespace next to these tags is formatting, not content. */
    private static final Set<String> STRUCTURE = Set.of(
            "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption");

    /** Ends after which a newline keeps the literal readable. */
    private static final Set<String> LINE_ENDS = Set.of(
            "table", "thead", "tbody", "tfoot", "tr", "caption");

    /** Dropped elements whose end still separates words. */
    private static final Set<String> SEPARATING = Set.of("div", "dt", "dd", "dl", "section");

    private final StringBuilder html = new StringBuilder();
    private int preDepth;
    private int cellDepth;
    private boolean pendingSpace;
    private boolean spaceSuppressed = true;

    void start(String name, Attributes atts) {
        if (!KEPT.contains(name)) {
            return;
        }
        boolean structure = STRUCTURE.contains(name);
        if (structure) {
            pendingSpace = false;
        } else {
            flushSpace();
        }
        html.append('<').append(name);
        switch (name) {
            case "th":
            case "td":
                span(atts, "colspan");
                span(atts, "rowspan");
                break;
            case "a":
                attr(atts, "href");
                break;
            case "img":
                attr(atts, "src");
                attr(atts, "alt");
                break;
            default:
                break;
        }
        html.append('>');
        spaceSuppressed = structure;
        if (name.equals("pre")) {
            preDepth++;
        } else if (name.equals("td") || name.equals("th")) {
            cellDepth++;
        }
    }

    void end(String name) {
        if (SEPARATING.contains(name)) {
            pendingSpace = true;
            return;
        }
        if (!KEPT.contains(name) || VOID.contains(name)) {
            return;
        }
        if (name.equals("pre")) {
            preDepth = Math.max(0, preDepth - 1);
        } else if (name.equals("td") || name.equals("th")) {
            cellDepth = Math.max(0, cellDepth - 1);
        }
        boolean structure = STRUCTURE.contains(name);
        if (structure) {
            pendingSpace = false;
        }
        html.append("</").append(name).append('>');
        spaceSuppressed = structure;
        // inside a cell a newline would be cell text
        if (cellDepth == 0 && LINE_ENDS.contains(name)) {
            html.append('\n');
        }
    }

    void characters(char[] ch, int start, int length) {
        for (int i = start; i < start + length; i++) {
            char c = ch[i];
            if (preDepth > 0) {
                if (c == '\n') {
                    html.append("&#10;");
                } else if (c != '\r') {
                    escape(c);
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                pendingSpace = true;
                continue;
            }
            flushSpace();
            escape(c);
            spaceSuppressed = false;
        }
    }

    String html() {
        int end = html.length();
        while (end > 0 && html.charAt(end - 1) == '\n') {
            end--;
        }
        return html.substring(0, end);
    }

    private void flushSpace() {
        if (pendingSpace && !spaceSuppressed) {
            html.append(' ');
        }
        pendingSpace = false;
    }

    private void span(Attributes atts, String name) {
        String v = atts.getValue(name);
        if (v != null && v.trim().matches("[1-9][0-9]{0,3}") && !v.trim().equals("1")) {
            html.append(' ').append(name).append("=\"").append(v.trim()).append('"');
        }
    }

    private void attr(Attributes atts, String name) {
        String v = atts.getValue(name);
        if (v == null || v.isEmpty()) {
            return;
        }
        html.append(' ').append(name).append("=\"");
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '"') {
                html.append("&quot;");
            } else if (c == '\n' || c == '\r') {
                html.append(' ');
            } else {
                escape(c);
            }
        }
        html.append('"');
    }

    private void escape(char c) {
        switch (c) {
            case '&':
                html.append("&amp;");
                break;
            case '<':
                html.append("&lt;");
                break;
            case '>':
                html.append("&gt;");
                break;
            default:
                html.append(c);
        }
    }

    /** True when a cell's attributes ask for a merged cell. */
    static boolean isSpanned(Attributes atts) {
        for (String name : new String[] {"colspan", "rowspan"}) {
            String v = atts.getValue(name);
            if (v != null && v.trim().matches("[1-9][0-9]{0,3}") && !v.trim().equals("1")) {
                return true;
            }
        }
        return false;
    }
}
