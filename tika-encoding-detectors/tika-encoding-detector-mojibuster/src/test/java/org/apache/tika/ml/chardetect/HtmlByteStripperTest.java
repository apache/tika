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
package org.apache.tika.ml.chardetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class HtmlByteStripperTest {

    /** Helper: strip a string and return the (text, tagCount, entityCount) tuple. */
    private static StripOutcome strip(String input) {
        byte[] src = input.getBytes(StandardCharsets.US_ASCII);
        byte[] dst = new byte[src.length];
        HtmlByteStripper.Result r = HtmlByteStripper.strip(src, 0, src.length, dst, 0);
        return new StripOutcome(new String(dst, 0, r.length, StandardCharsets.US_ASCII),
                r.tagCount, r.entityCount);
    }

    private static final class StripOutcome {
        final String text;
        final int tagCount;
        final int entityCount;
        StripOutcome(String text, int tagCount, int entityCount) {
            this.text = text;
            this.tagCount = tagCount;
            this.entityCount = entityCount;
        }
    }

    /** Helper: tagCount when stripping the given bytes (tags+entities). */
    private static int tagCount(byte[] src) {
        byte[] dst = new byte[src.length];
        return HtmlByteStripper.strip(src, 0, src.length, dst, 0).tagCount;
    }

    @Test
    public void multiByteUnicodeIsNotTagStripped() {
        // The byte-level stripper must not mangle UTF-16/UTF-32: those bytes
        // don't form single-byte ASCII tags, so tagCount stays 0 and callers
        // (Mojibuster / JunkFilter) fall back to the raw bytes via their
        // `tagCount > 0` gate.  Regression guard for the "does the byte
        // stripper botch wide Unicode?" question.
        String html = "<html><head><title>商品</title></head>"
                + "<body><p>这是中文测试 with markup</p></body></html>";
        // ASCII-compatible encodings: tags ARE recognized (and safely stripped).
        assertTrue(tagCount(html.getBytes(StandardCharsets.UTF_8)) > 0,
                "UTF-8 tags should be recognized");
        assertTrue(tagCount(html.getBytes(Charset.forName("GBK"))) > 0,
                "GBK (ASCII-compatible) tags should be recognized");
        // Wide Unicode: no single-byte ASCII tags → tagCount 0 → strip not used.
        assertEquals(0, tagCount(html.getBytes(Charset.forName("UTF-16LE"))),
                "UTF-16LE must not register tags");
        assertEquals(0, tagCount(html.getBytes(Charset.forName("UTF-16BE"))),
                "UTF-16BE must not register tags");
        assertEquals(0, tagCount(html.getBytes(Charset.forName("UTF-32LE"))),
                "UTF-32LE must not register tags");
        assertEquals(0, tagCount(html.getBytes(Charset.forName("UTF-32BE"))),
                "UTF-32BE must not register tags");
    }

    @Test
    public void stripTagsPreservesEntitiesForJunkDetection() {
        // JunkFilter path: tags removed, entities KEPT (expanded later in
        // string space).  Charset path (default strip) removes both.
        String in = "<p>Copyright &#169; 2024 caf&eacute;</p>";
        byte[] src = in.getBytes(StandardCharsets.US_ASCII);
        byte[] dstA = new byte[src.length];
        byte[] dstB = new byte[src.length];
        HtmlByteStripper.Result tagsOnly =
                HtmlByteStripper.stripTags(src, 0, src.length, dstA, 0);
        HtmlByteStripper.Result both =
                HtmlByteStripper.stripTagsAndEntities(src, 0, src.length, dstB, 0);
        assertEquals("Copyright &#169; 2024 caf&eacute;",
                new String(dstA, 0, tagsOnly.length, StandardCharsets.US_ASCII));
        assertEquals("Copyright  2024 caf",
                new String(dstB, 0, both.length, StandardCharsets.US_ASCII));
        // tagsOnly does not count entities (it doesn't enter the entity path)
        assertEquals(0, tagsOnly.entityCount);
        assertEquals(2, both.entityCount);
    }

    @Test
    public void namedEntityIsStripped() {
        StripOutcome r = strip("hello &amp; world");
        assertEquals("hello  world", r.text);
        assertEquals(1, r.entityCount);
        assertEquals(0, r.tagCount);
    }

    @Test
    public void decimalNumericEntityIsStripped() {
        StripOutcome r = strip("foo &#169; bar");
        assertEquals("foo  bar", r.text);
        assertEquals(1, r.entityCount);
    }

    @Test
    public void hexNumericEntityIsStripped() {
        StripOutcome r = strip("foo &#x00A9; bar");
        assertEquals("foo  bar", r.text);
        assertEquals(1, r.entityCount);
    }

    @Test
    public void hexNumericEntityUppercaseXIsStripped() {
        StripOutcome r = strip("a&#XA9;b");
        assertEquals("ab", r.text);
        assertEquals(1, r.entityCount);
    }

    @Test
    public void ampersandFollowedByLetterWithoutSemicolonIsLiteral() {
        // AT&T pattern: & followed by letter(s) but no closing ';'
        StripOutcome r = strip("AT&T Inc");
        assertEquals("AT&T Inc", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void ampersandFollowedByNonLetterIsLiteral() {
        // Q&A pattern: & followed by uppercase letter then space → bailout
        StripOutcome r = strip("Q&A session");
        assertEquals("Q&A session", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void ampersandFollowedBySpaceIsLiteral() {
        StripOutcome r = strip("a & b");
        assertEquals("a & b", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void ampersandAtEndOfInputIsLiteral() {
        StripOutcome r = strip("end&");
        assertEquals("end&", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void unclosedEntityNameAtEndOfInputIsLiteral() {
        StripOutcome r = strip("end&foo");
        assertEquals("end&foo", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void unclosedNumericEntityAtEndOfInputIsLiteral() {
        StripOutcome r = strip("end&#123");
        assertEquals("end&#123", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void entityExceedingLengthCapIsLiteral() {
        // Standard HTML5 entity longer than the 16-byte cap.
        StripOutcome r = strip("x&CounterClockwiseContourIntegral;y");
        // The cap kicks in mid-body; the consumed prefix is emitted as
        // literal text, then the rest of the bytes follow as text.
        // Exact prefix depends on cap; key assertion is the entity was
        // NOT counted as stripped.
        assertEquals(0, r.entityCount);
        // The full input is preserved as text (cap bailout emits what
        // it consumed, and the remaining tail follows naturally).
        assertEquals("x&CounterClockwiseContourIntegral;y", r.text);
    }

    @Test
    public void adjacentEntitiesAreAllStripped() {
        StripOutcome r = strip("&amp;&amp;&amp;");
        assertEquals("", r.text);
        assertEquals(3, r.entityCount);
    }

    @Test
    public void ampersandCascadingIntoTagWorks() {
        // & followed by letters then '<' should emit the bailout prefix
        // and then transition into tag-stripping.
        StripOutcome r = strip("a&foo<b>c");
        assertEquals("a&fooc", r.text);
        assertEquals(0, r.entityCount);
        assertEquals(1, r.tagCount);
    }

    @Test
    public void ampersandCascadingIntoAnotherEntity() {
        // & followed by non-entity content then another &amp; — the
        // first '&' should emit literal, second '&' starts a new entity.
        StripOutcome r = strip("a&!&amp;b");
        assertEquals("a&!b", r.text);
        assertEquals(1, r.entityCount);
    }

    @Test
    public void tagWithEntityInBodyStripsBoth() {
        StripOutcome r = strip("<p>hello&nbsp;world</p>");
        assertEquals("helloworld", r.text);
        assertEquals(2, r.tagCount);
        assertEquals(1, r.entityCount);
    }

    @Test
    public void plainTextNoMarkup() {
        StripOutcome r = strip("just plain text, no markup at all");
        assertEquals("just plain text, no markup at all", r.text);
        assertEquals(0, r.tagCount);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void emptyEntityIsLiteral() {
        // "&;" — & followed immediately by ';' (not letter / not '#')
        StripOutcome r = strip("a&;b");
        assertEquals("a&;b", r.text);
        assertEquals(0, r.entityCount);
    }

    @Test
    public void numericEmptyBodyIsLiteral() {
        // "&#;" — &# followed by ';' (not 'x' / not digit)
        StripOutcome r = strip("a&#;b");
        assertEquals("a&#;b", r.text);
        assertEquals(0, r.entityCount);
    }
}
