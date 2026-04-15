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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HtmlStripperTest {

    @Test
    public void stripsBasicTags() {
        assertEquals("Hello world",
                HtmlStripper.strip("<html><body>Hello world</body></html>"));
        assertEquals("no tags here",
                HtmlStripper.strip("no tags here"));
        assertEquals("",
                HtmlStripper.strip("<empty/>"));
    }

    @Test
    public void removesScriptAndStyleContents() {
        // Script bodies (JavaScript) and style bodies (CSS) used to leak into
        // the output and skew language detection toward English.  Verify they
        // are removed entirely.
        String input = "<html><head>"
                + "<script type=\"text/javascript\">function add(a,b) { return a+b; }</script>"
                + "<style>body { font-family: sans-serif; }</style>"
                + "</head><body>real content here</body></html>";
        String stripped = HtmlStripper.strip(input);
        assertFalse(stripped.contains("function"),
                "JavaScript identifier 'function' should not survive: " + stripped);
        assertFalse(stripped.contains("font-family"),
                "CSS property name should not survive: " + stripped);
        assertTrue(stripped.contains("real content here"),
                "Body prose should survive: " + stripped);
    }

    @Test
    public void removesComments() {
        String input = "<p>before<!-- some comment with content -->after</p>";
        String stripped = HtmlStripper.strip(input);
        assertFalse(stripped.contains("comment"),
                "Comment body should not survive: " + stripped);
        assertTrue(stripped.contains("before"));
        assertTrue(stripped.contains("after"));
    }

    @Test
    public void handlesEntities() {
        // Named entities (e.g. &amp;, &nbsp;) → stripped to space (low signal,
        // and a full named-entity table is heavyweight).
        // Numeric entities (e.g. &#1234;, &#x201D;) → DECODED to their actual
        // code point so the content reaches the language detector.  This
        // matters for files where the page's primary content is delivered
        // via numeric entities (e.g. industrial-product pages emitting CJK
        // ideographs as &#NNNN; for cross-charset compatibility).
        String stripped = HtmlStripper.strip(
                "<p>&amp;hello&nbsp;world&#8211;test&#x201D;end</p>");
        assertFalse(stripped.contains("&"),
                "No entity references should survive: " + stripped);
        // 0x2013 = en-dash, 0x201D = right double quote — should appear as
        // actual chars, not as entity references nor as spaces.
        assertTrue(stripped.contains("\u2013"),
                "Numeric entity &#8211; should decode to en-dash: " + stripped);
        assertTrue(stripped.contains("\u201D"),
                "Numeric entity &#x201D; should decode to right double quote: " + stripped);
        assertTrue(stripped.contains("hello"));
        assertTrue(stripped.contains("world"));
    }

    @Test
    public void decodesCjkNumericEntities() {
        // Real-world case: industrial-product pages that emit CJK ideographs
        // via numeric entities (so they render correctly regardless of the
        // page's declared charset).  The decoded content must reach the
        // language detector — without this, language detection sees only
        // ASCII markup and concludes "English" no matter what the page is
        // actually about.
        String input = "<p>&#36807;&#28388;&#31163; cyclone</p>";
        String stripped = HtmlStripper.strip(input);
        assertTrue(stripped.contains("\u8FC7"),
                "0x8FC7 (过) should decode: " + stripped);
        assertTrue(stripped.contains("\u6EE4"),
                "0x6EE4 (滤) should decode: " + stripped);
        assertTrue(stripped.contains("\u79BB"),
                "0x79BB (离) should decode: " + stripped);
    }

    @Test
    public void rejectsInvalidNumericEntities() {
        // Surrogate-half codepoints, control chars, and out-of-range numbers
        // should be replaced with a space rather than emitted (they would
        // either crash the language detector or skew scores).
        String stripped = HtmlStripper.strip("good&#xD800;bad&#0;bad&#9999999;good");
        assertFalse(stripped.contains("\uD800"),
                "Surrogate code point should not be emitted: " + stripped);
        assertTrue(stripped.contains("good"));
    }

    @Test
    public void nullAndEmptyAreReturnedAsIs() {
        assertEquals(null, HtmlStripper.strip(null));
        assertEquals("", HtmlStripper.strip(""));
    }

    @Test
    public void unclosedTagSwallowsToEnd() {
        // Defensive: a `<` with no matching `>` should not dump uninterpreted
        // markup back into the output (would dominate language scoring).
        // The unclosed tag is consumed silently — no trailing space.
        assertEquals("before", HtmlStripper.strip("before<unclosed never ends"));
    }

    @Test
    public void literalAmpersandPreserved() {
        // A `&` not followed by a recognisable entity body must survive.
        assertEquals("a & b", HtmlStripper.strip("a & b"));
        assertEquals("rock&roll", HtmlStripper.strip("rock&roll"));
    }

    // --- defensive-input cases (regression coverage) ---

    @Test
    public void truncatedAtScriptDoesNotThrow() {
        // Previously triggered AIOOBE: input ending exactly with `<script`
        // (or `<style`) — element-name match succeeded but the
        // tag-boundary lookahead read past end-of-string.
        for (String s : new String[]{
                "<script", "<SCRIPT", "<style", "<STYLE",
                "before<script", "x<style", "<scriptX"}) {
            // Just verify no exception and a string is returned.
            String out = HtmlStripper.strip(s);
            assertTrue(out != null, "stripping " + s + " returned null");
        }
    }

    @Test
    public void manyOpenAnglesDoesNotHang() {
        // Worst-case stress: 32K of `<` characters with no matching `>`.
        // The main loop should run in O(N) and produce empty output.
        StringBuilder sb = new StringBuilder(32 * 1024);
        for (int k = 0; k < 32 * 1024; k++) {
            sb.append('<');
        }
        long start = System.nanoTime();
        String out = HtmlStripper.strip(sb.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertEquals("", out);
        assertTrue(ms < 1000, "took " + ms + " ms — possible quadratic blowup");
    }

    @Test
    public void manyAmpersandsDoesNotHang() {
        // 32K of `&` characters with no entity bodies.  Each is treated as
        // literal `&` with O(1) lookahead bounded by 12 chars.
        StringBuilder sb = new StringBuilder(32 * 1024);
        for (int k = 0; k < 32 * 1024; k++) {
            sb.append('&');
        }
        long start = System.nanoTime();
        String out = HtmlStripper.strip(sb.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        // All 32K ampersands survive as literals.
        assertEquals(32 * 1024, out.length());
        assertTrue(ms < 1000, "took " + ms + " ms — possible quadratic blowup");
    }

    @Test
    public void unclosedScriptBlockDoesNotHang() {
        // 32K of false `</script` partial closers (no tag boundary).
        // skipPastClosing should advance past each one in O(1) and
        // terminate cleanly.
        StringBuilder sb = new StringBuilder(32 * 1024);
        sb.append("<script>");
        while (sb.length() < 32 * 1024) {
            sb.append("</scriptX");  // X is not a tag boundary
        }
        long start = System.nanoTime();
        String out = HtmlStripper.strip(sb.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue(out != null);
        assertTrue(ms < 1000, "took " + ms + " ms — possible quadratic blowup");
    }

    @Test
    public void truncatedNumericEntityDoesNotThrow() {
        // Various truncations.  None should throw.
        for (String s : new String[]{
                "&", "&#", "&#x", "&#1", "&#x1",
                "&#;", "&#x;", "&#xZZ;", "&#9999999999;",  // overflow
                "&;", "&abc", "&abc def;"}) {
            String out = HtmlStripper.strip(s);
            assertTrue(out != null, "stripping " + s + " returned null");
        }
    }
}
