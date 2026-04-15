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
package org.apache.tika.langdetect.charsoup;

/**
 * HTML/XML markup stripping tuned for language scoring.  Not a full HTML
 * parser — purpose-built to feed character-bigram language detectors a
 * markup-free string that still carries the page's content language.
 *
 * <p>Real-world HTML probes are routinely 95-99% markup by byte count.
 * Without this pass, a language detector sees the markup as its primary
 * input — which on any HTML page looks predominantly like ASCII English
 * regardless of the page's actual content language.  Stripping markup
 * (and decoding numeric entities, which can carry content) lets the
 * detector see the actual content.
 *
 * <h3>What it does, in one linear pass</h3>
 * <ul>
 *   <li>Removes {@code <script>...</script>} and {@code <style>...</style>}
 *       block contents — JavaScript identifiers / CSS property names look
 *       strongly like English and would skew language scoring on any
 *       page.</li>
 *   <li>Removes {@code <!-- ... -->} comments.</li>
 *   <li>Removes {@code <...>} tag markup (element names, attribute names,
 *       attribute values).</li>
 *   <li><em>Decodes</em> numeric character references ({@code &#1234;},
 *       {@code &#xABCD;}) to their actual code points — these can carry
 *       the page's primary content (e.g. Korean-charset pages that emit
 *       simplified-Chinese-only ideographs via numeric entities for
 *       cross-charset compatibility).</li>
 *   <li>Replaces named entity references ({@code &amp;}, {@code &nbsp;},
 *       {@code &copy;}) with a space — these are nearly always
 *       punctuation/typography with low language signal, and a full
 *       named-entity table would be heavyweight.</li>
 * </ul>
 *
 * <h3>What it doesn't do</h3>
 * <ul>
 *   <li>Validate HTML structure.  Malformed input, unclosed
 *       {@code <script>} blocks, and CDATA sections are handled
 *       defensively: unclosed brackets and unfound matching tags fall
 *       through to end-of-input.</li>
 *   <li>Resolve named entities.  A 2-element shortlist
 *       ({@code &amp;}, {@code &lt;}, etc.) might be worth adding later
 *       if some downstream needs them; current users score language and
 *       don't.</li>
 *   <li>Preserve element-content semantics ({@code <title>} vs body text,
 *       {@code <pre>} whitespace).  All content is treated equivalently.</li>
 * </ul>
 */
public final class HtmlStripper {

    private HtmlStripper() {
    }

    /**
     * Strip markup from {@code text} and return the content with numeric
     * entities decoded.  See class javadoc for details.
     *
     * @param text input string (HTML/XML or plain text); {@code null} or empty
     *             returns the input unchanged
     * @return content with markup removed and numeric entities decoded
     */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '<') {
                i = handleOpenAngle(text, i, n, out);
            } else if (c == '&') {
                i = handleAmpersand(text, i, n, out);
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Handle a {@code <} — element tag, comment, or script/style block. */
    private static int handleOpenAngle(String s, int i, int n, StringBuilder out) {
        if (startsWithIgnoreCase(s, i, "<!--")) {
            int end = s.indexOf("-->", i + 4);
            return end < 0 ? n : end + 3;
        }
        if (startsRawElementBlock(s, i, "script")) {
            return skipPastClosing(s, i, n, "</script", out);
        }
        if (startsRawElementBlock(s, i, "style")) {
            return skipPastClosing(s, i, n, "</style", out);
        }
        // Generic tag.  Skip to matching `>`; if none, swallow rest of input
        // (defensive — malformed `<` shouldn't dump uninterpreted bytes back).
        int end = s.indexOf('>', i + 1);
        return end < 0 ? n : end + 1;
    }

    /** Handle a {@code &} — numeric entity (decode), named entity (drop), or literal. */
    private static int handleAmpersand(String s, int i, int n, StringBuilder out) {
        // Look for ; within a small window — entity references are short.
        int max = Math.min(n, i + 12);
        int semi = -1;
        for (int j = i + 1; j < max; j++) {
            char c = s.charAt(j);
            if (c == ';') {
                semi = j;
                break;
            }
            if (c == '<' || c == '&' || Character.isWhitespace(c)) {
                break;  // not an entity
            }
        }
        if (semi < 0) {
            out.append('&');
            return i + 1;
        }
        // Numeric entity?
        if (semi >= i + 3 && s.charAt(i + 1) == '#') {
            int cp = parseNumericEntity(s, i + 2, semi);
            if (cp >= 0) {
                appendCodePointSafe(out, cp);
                return semi + 1;
            }
            // Unparseable numeric entity — treat as space (it's not literal text).
            out.append(' ');
            return semi + 1;
        }
        // Named entity? Drop to space (low-signal punctuation).
        if (isNamedEntity(s, i + 1, semi)) {
            out.append(' ');
            return semi + 1;
        }
        // Otherwise treat as literal.
        out.append('&');
        return i + 1;
    }

    /**
     * {@code true} if {@code s} starts with {@code <name} followed by a
     * tag-name boundary character.  We require the boundary to actually be
     * present (not just end-of-string) so the truncated input {@code "<script"}
     * is treated as malformed-tag rather than a real script-block opener —
     * no boundary, no contents to skip, and crucially no AIOOBE on the
     * lookahead.
     */
    private static boolean startsRawElementBlock(String s, int i, String name) {
        int after = i + 1 + name.length();
        if (after >= s.length()) {
            return false;
        }
        if (!startsWithIgnoreCase(s, i + 1, name)) {
            return false;
        }
        char c = s.charAt(after);
        return c == '>' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/';
    }

    /**
     * Skip past the closing tag for a raw-text element (script/style),
     * returning the position immediately after {@code closing>}.  If no
     * matching closer is found, swallows to end-of-input.
     */
    private static int skipPastClosing(String s, int i, int n, String closing, StringBuilder out) {
        out.append(' ');  // preserve a word boundary in the output
        int from = i + 1;
        while (from < n) {
            int p = indexOfIgnoreCase(s, closing, from);
            if (p < 0) {
                return n;
            }
            // Verify it's a tag boundary, then skip to the next `>`.
            int after = p + closing.length();
            if (after >= n) {
                return n;
            }
            char c = s.charAt(after);
            if (c == '>' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/') {
                int gt = s.indexOf('>', after);
                return gt < 0 ? n : gt + 1;
            }
            from = p + 1;
        }
        return n;
    }

    /** Parse a numeric entity body ({@code #1234} or {@code #xABCD}) starting at {@code from}. */
    private static int parseNumericEntity(String s, int from, int semiExclusive) {
        if (from >= semiExclusive) {
            return -1;
        }
        int hex = (s.charAt(from) == 'x' || s.charAt(from) == 'X') ? 1 : 0;
        int start = from + hex;
        if (start >= semiExclusive || semiExclusive - start > 7) {
            return -1;
        }
        int cp = 0;
        for (int j = start; j < semiExclusive; j++) {
            int d = Character.digit(s.charAt(j), hex == 1 ? 16 : 10);
            if (d < 0) {
                return -1;
            }
            cp = cp * (hex == 1 ? 16 : 10) + d;
            if (cp > 0x10FFFF) {
                return -1;
            }
        }
        return cp;
    }

    /** Append a code point, replacing controls and surrogate halves with a space. */
    private static void appendCodePointSafe(StringBuilder out, int cp) {
        if (cp <= 0 || cp > 0x10FFFF
                || Character.isISOControl(cp)
                || (cp >= 0xD800 && cp <= 0xDFFF)) {
            out.append(' ');
            return;
        }
        out.appendCodePoint(cp);
    }

    /** {@code true} if the body of a {@code &…;} reference is a plausible named entity. */
    private static boolean isNamedEntity(String s, int from, int semiExclusive) {
        int len = semiExclusive - from;
        if (len < 2 || len > 8) {
            return false;
        }
        for (int j = from; j < semiExclusive; j++) {
            char c = s.charAt(j);
            if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }

    /**
     * ASCII-only case-insensitive prefix match.  HTML element names are ASCII
     * by spec, so we avoid {@link Character#toLowerCase} entirely — that
     * method is Unicode-aware (which we don't need) and behaves differently
     * in some locales for non-ASCII characters (the Turkish dotted-I being
     * the canonical example).  An ASCII-only fold is faster, locale-
     * independent, and exactly matches the HTML spec.
     */
    private static boolean startsWithIgnoreCase(String s, int i, String prefix) {
        if (i + prefix.length() > s.length()) {
            return false;
        }
        for (int j = 0; j < prefix.length(); j++) {
            if (asciiLower(s.charAt(i + j)) != asciiLower(prefix.charAt(j))) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(char c) {
        return (c >= 'A' && c <= 'Z') ? (char) (c + 32) : c;
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int last = s.length() - needle.length();
        for (int i = from; i <= last; i++) {
            if (startsWithIgnoreCase(s, i, needle)) {
                return i;
            }
        }
        return -1;
    }
}
