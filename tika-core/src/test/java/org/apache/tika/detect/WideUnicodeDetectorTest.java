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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class WideUnicodeDetectorTest {

    private static final Charset UTF32LE = Charset.forName("UTF-32LE");
    private static final Charset UTF32BE = Charset.forName("UTF-32BE");

    private final WideUnicodeDetector detector = new WideUnicodeDetector();

    private static byte[] encode(String text, Charset cs) {
        return text.getBytes(cs);
    }

    private static byte[] prepend(byte[] prefix, byte[] body) {
        byte[] out = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(body, 0, out, prefix.length, body.length);
        return out;
    }

    /** Build a diverse CJK string spanning 4 high-byte groups. */
    private static String diverseCjk(int countPerBlock) {
        StringBuilder sb = new StringBuilder();
        for (int start : new int[]{0x4E00, 0x5000, 0x6000, 0x7000}) {
            for (int i = 0; i < countPerBlock; i++) sb.appendCodePoint(start + i);
        }
        return sb.toString();
    }

    private Charset detectViaStream(byte[] bytes) throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(bytes))) {
            return detector.detect(tis, new Metadata(), new ParseContext());
        }
    }

    // ── skipBom ──────────────────────────────────────────────────────────────

    @Test
    void skipBomStripsUtf8Bom() {
        byte[] in = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41, 0x42, 0x43};
        assertArrayEquals(new byte[]{0x41, 0x42, 0x43}, WideUnicodeDetector.skipBom(in));
    }

    @Test
    void skipBomStripsUtf16LeBom() {
        byte[] in = {(byte) 0xFF, (byte) 0xFE, 0x41, 0x00};
        assertArrayEquals(new byte[]{0x41, 0x00}, WideUnicodeDetector.skipBom(in));
    }

    @Test
    void skipBomStripsUtf16BeBom() {
        byte[] in = {(byte) 0xFE, (byte) 0xFF, 0x00, 0x41};
        assertArrayEquals(new byte[]{0x00, 0x41}, WideUnicodeDetector.skipBom(in));
    }

    @Test
    void skipBomStripsUtf32LeBom() {
        // FF FE 00 00 must match as UTF-32LE before FF FE matches as UTF-16LE
        byte[] in = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x00, 0x41, 0x00, 0x00, 0x00};
        assertArrayEquals(new byte[]{0x41, 0x00, 0x00, 0x00}, WideUnicodeDetector.skipBom(in));
    }

    @Test
    void skipBomStripsUtf32BeBom() {
        byte[] in = {0x00, 0x00, (byte) 0xFE, (byte) 0xFF, 0x00, 0x00, 0x00, 0x41};
        assertArrayEquals(new byte[]{0x00, 0x00, 0x00, 0x41}, WideUnicodeDetector.skipBom(in));
    }

    @Test
    void skipBomLeavesNoBomContentUnchanged() {
        byte[] in = {0x41, 0x00, 0x42, 0x00};
        assertArrayEquals(in, WideUnicodeDetector.skipBom(in));
    }

    // ── detectEncoding BOM handling ───────────────────────────────────────────

    @Test
    void detectEncodingStripsUtf8BomBeforeAnalysis() {
        // A UTF-8 BOM (EF BB BF) before UTF-16LE content would shift every
        // subsequent byte position by 3, misaligning the fixed-width pairs.
        // detectEncoding() must strip it internally so callers don't have to.
        String latin = "Hello world from a test. ".repeat(6);
        byte[] raw = encode(latin, StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[3 + raw.length];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(raw, 0, withBom, 3, raw.length);
        assertEquals(StandardCharsets.UTF_16LE, WideUnicodeDetector.detectEncoding(withBom));
    }

    @Test
    void detectEncodingStripsUtf16LeBomBeforeAnalysis() {
        String latin = "Hello world from a test. ".repeat(6);
        byte[] raw = encode(latin, StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[2 + raw.length];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(raw, 0, withBom, 2, raw.length);
        assertEquals(StandardCharsets.UTF_16LE, WideUnicodeDetector.detectEncoding(withBom));
    }

    // ── UTF-16 LE ────────────────────────────────────────────────────────────

    @Test
    void utf16LeLatinText() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6);
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(text, StandardCharsets.UTF_16LE)));
    }

    @Test
    void utf16LeCjkDiverse() {
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(diverseCjk(32), StandardCharsets.UTF_16LE)));
    }

    @Test
    void utf16LeCjkSmallSample() {
        // 10 unique chars — variety ratio ~1.1×, below threshold.
        // Block-prefix range check (odd bytes all in [0x4E,0x8B] ⊂ [0,0xD8)) saves it.
        String cjk = "\u4E2D\u6587\u6D4B\u8BD5\u5185\u5BB9\u53EF\u4EE5\u68C0\u6D4B".repeat(6);
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(cjk, StandardCharsets.UTF_16LE)));
    }

    @Test
    void utf16LeHangul() {
        // Hangul Syllables U+AC00–U+D7A3: high bytes 0xAC–0xD7, above old 0xA0 threshold.
        String hangul = "\uAC00\uAC01\uB098\uB2E4\uB77C\uB9C8\uBC14\uC0AC\uC544\uC790".repeat(6);
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(hangul, StandardCharsets.UTF_16LE)));
    }

    @Test
    void utf16LeMixed() {
        String mixed = ("Hello \u4E16\u754C! ").repeat(15);
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(mixed, StandardCharsets.UTF_16LE)));
    }

    // ── UTF-16 BE ────────────────────────────────────────────────────────────

    @Test
    void utf16BeLatinText() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6);
        assertEquals(StandardCharsets.UTF_16BE,
                WideUnicodeDetector.detectEncoding(encode(text, StandardCharsets.UTF_16BE)));
    }

    @Test
    void utf16BeCjkDiverse() {
        assertEquals(StandardCharsets.UTF_16BE,
                WideUnicodeDetector.detectEncoding(encode(diverseCjk(32), StandardCharsets.UTF_16BE)));
    }

    @Test
    void utf16BeCjkSmallSample() {
        String cjk = "\u4E2D\u6587\u6D4B\u8BD5\u5185\u5BB9\u53EF\u4EE5\u68C0\u6D4B".repeat(6);
        assertEquals(StandardCharsets.UTF_16BE,
                WideUnicodeDetector.detectEncoding(encode(cjk, StandardCharsets.UTF_16BE)));
    }

    @Test
    void utf16BeHangul() {
        String hangul = "\uAC00\uAC01\uB098\uB2E4\uB77C\uB9C8\uBC14\uC0AC\uC544\uC790".repeat(6);
        assertEquals(StandardCharsets.UTF_16BE,
                WideUnicodeDetector.detectEncoding(encode(hangul, StandardCharsets.UTF_16BE)));
    }

    @Test
    void utf16LeCjkWithAsciiSpaces() {
        // Realistic CJK document: ~16% ASCII spaces between Chinese characters.
        // null_odd ≈ 16% — below the Phase 1 threshold (25%) — so Phase 1 does
        // not fire.  The characters are chosen so that some low bytes are ≥ 0xD8
        // (以=E5, 们=EC, 说=F4), making !evenInRange true and routing detection
        // to Phase 3.  The 0x00 high bytes from the spaces appear in the odd
        // (block-prefix) column; nonNullAllAbove allows them because they are
        // null — only non-null bytes below 0x20 (binary control tokens) are
        // rejected.  The old allAbove guard would have rejected this text.
        String text = ("\u4ee5\u4eec\u8bf4\u4e2d\u6587 ").repeat(12);
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(encode(text, StandardCharsets.UTF_16LE)));
    }

    @Test
    void utf16BeCjkWithAsciiSpaces() {
        String text = ("\u4ee5\u4eec\u8bf4\u4e2d\u6587 ").repeat(12);
        assertEquals(StandardCharsets.UTF_16BE,
                WideUnicodeDetector.detectEncoding(encode(text, StandardCharsets.UTF_16BE)));
    }

    // ── UTF-32 LE ────────────────────────────────────────────────────────────

    @Test
    void utf32LeLatinText() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(4);
        assertEquals(UTF32LE, WideUnicodeDetector.detectEncoding(encode(text, UTF32LE)));
    }

    @Test
    void utf32LeCjkSmallSample() {
        // Null-position check fires regardless of sample diversity: bytes 2,3 always 0x00 for BMP.
        String cjk = "\u4E2D\u6587\u6D4B\u8BD5\u5185\u5BB9\u53EF\u4EE5\u68C0\u6D4B".repeat(4);
        assertEquals(UTF32LE, WideUnicodeDetector.detectEncoding(encode(cjk, UTF32LE)));
    }

    @Test
    void utf32LeMixed() {
        assertEquals(UTF32LE,
                WideUnicodeDetector.detectEncoding(encode(("Hello \u4E16\u754C! ").repeat(8), UTF32LE)));
    }

    // ── UTF-32 BE ────────────────────────────────────────────────────────────

    @Test
    void utf32BeLatinText() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(4);
        assertEquals(UTF32BE, WideUnicodeDetector.detectEncoding(encode(text, UTF32BE)));
    }

    @Test
    void utf32BeCjkSmallSample() {
        // Bytes 0,1 always 0x00 for BMP — null-position check works with any sample size.
        String cjk = "\u4E2D\u6587\u6D4B\u8BD5\u5185\u5BB9\u53EF\u4EE5\u68C0\u6D4B".repeat(4);
        assertEquals(UTF32BE, WideUnicodeDetector.detectEncoding(encode(cjk, UTF32BE)));
    }

    @Test
    void utf32BeMixed() {
        assertEquals(UTF32BE,
                WideUnicodeDetector.detectEncoding(encode(("Hello \u4E16\u754C! ").repeat(8), UTF32BE)));
    }

    // ── Non-wide encodings return null ────────────────────────────────────────

    @Test
    void utf8LatinReturnsNull() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6);
        assertNull(WideUnicodeDetector.detectEncoding(encode(text, StandardCharsets.UTF_8)));
    }

    @Test
    void utf8CjkReturnsNull() {
        assertNull(WideUnicodeDetector.detectEncoding(encode(diverseCjk(32), StandardCharsets.UTF_8)));
    }

    @Test
    void iso88591ReturnsNull() {
        byte[] bytes = new byte[512];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i & 0xFF);
        assertNull(WideUnicodeDetector.detectEncoding(bytes));
    }

    @Test
    void tooShortReturnsNull() {
        assertNull(WideUnicodeDetector.detectEncoding(new byte[]{0x41, 0x00}));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(WideUnicodeDetector.detectEncoding(null));
    }

    // ── Misleading BOM ───────────────────────────────────────────────────────

    @Test
    void misleadingUtf16LeBomWithUtf8Content() {
        // UTF-16LE BOM prepended to UTF-8 body — structural check on body returns null.
        byte[] combined = prepend(
                new byte[]{(byte) 0xFF, (byte) 0xFE},
                encode("The quick brown fox jumps over the lazy dog. ".repeat(6), StandardCharsets.UTF_8));
        assertNull(WideUnicodeDetector.detectEncoding(WideUnicodeDetector.skipBom(combined)));
    }

    // ── Structural validity — UTF-32 ─────────────────────────────────────────

    @Test
    void utf32BeInvalidCodepointRuledOut() {
        // Build bytes that look like UTF-32BE structurally (positions 0,1 zero)
        // but contain codepoints above U+10FFFF (b1 = 0x11 > max plane 0x10).
        // The data falls back to UTF-16BE because b0=0x00 at every group creates
        // a 50% null rate in the even-byte column; UTF-32BE is still ruled out.
        byte[] bytes = new byte[128];
        for (int g = 0; g < 32; g++) {
            bytes[g * 4]     = 0x00;
            bytes[g * 4 + 1] = 0x11; // > 0x10 → invalid codepoint
            bytes[g * 4 + 2] = (byte) (0x20 + g);
            bytes[g * 4 + 3] = (byte) (0x41 + g % 26);
        }
        assertNotEquals(Charset.forName("UTF-32BE"),
                WideUnicodeDetector.detectEncoding(bytes),
                "UTF-32BE with out-of-range codepoint should be ruled out");
    }

    @Test
    void utf32BeSurrogateRuledOut() {
        // UTF-32BE bytes with a surrogate codepoint (U+D800 = 00 00 D8 00).
        byte[] bytes = new byte[128];
        for (int g = 0; g < 32; g++) {
            bytes[g * 4]     = 0x00;
            bytes[g * 4 + 1] = 0x00;
            bytes[g * 4 + 2] = (byte) 0xD8; // surrogate range
            bytes[g * 4 + 3] = (byte) (0x41 + g % 26);
        }
        assertNull(WideUnicodeDetector.detectEncoding(bytes),
                "UTF-32BE with surrogate codepoint should be ruled out");
    }

    @Test
    void utf32BeContentByteAllZeroRuledOut() {
        // Positions 0,1 are zero (passes the 90% null check) and position 3
        // is always 0x00 — only one distinct value, so countUnique < 2 rules
        // out UTF-32BE before the content-nonzero threshold is even reached.
        // The data falls back to UTF-16LE via the variety-ratio check.
        byte[] bytes = new byte[128];
        for (int g = 0; g < 32; g++) {
            bytes[g * 4]     = 0x00;
            bytes[g * 4 + 1] = 0x00;
            bytes[g * 4 + 2] = (byte) (g % 5); // some variety at pos 2
            bytes[g * 4 + 3] = 0x00;            // content byte always zero
        }
        assertNotEquals(Charset.forName("UTF-32BE"),
                WideUnicodeDetector.detectEncoding(bytes),
                "UTF-32BE with all-zero content bytes should be ruled out");
    }

    @Test
    void utf32BeContentNonzeroThresholdRuledOut() {
        // Positions 0,1 are zero (passes 90% null check) and position 3 has
        // 2 distinct values (passes countUnique check), but only 6/32 = 18.75%
        // non-zero — well below UTF32_CONTENT_NONZERO_MIN (0.80).
        // The new content-nonzero check is what rules this out as UTF-32BE.
        byte[] bytes = new byte[128];
        for (int g = 0; g < 32; g++) {
            bytes[g * 4]     = 0x00;
            bytes[g * 4 + 1] = 0x00;
            bytes[g * 4 + 2] = 0x00;
            bytes[g * 4 + 3] = (g < 6) ? (byte) 0x41 : 0x00; // only 6 non-zero → 18.75%
        }
        assertNotEquals(Charset.forName("UTF-32BE"),
                WideUnicodeDetector.detectEncoding(bytes),
                "UTF-32BE with content byte mostly zero should be ruled out by nonzero threshold");
    }

    // ── Structural validity — UTF-16 ─────────────────────────────────────────

    @Test
    void utf16BeLoneLowSurrogateRuledOut() {
        // UTF-16BE bytes where even positions contain lone low surrogates (0xDC–0xDF).
        // These must appear only after a high surrogate — lone ones are invalid.
        byte[] base = encode("The quick brown fox jumps over the lazy dog. ".repeat(3),
                StandardCharsets.UTF_16BE);
        // Corrupt: inject a lone low surrogate at even position 0
        base[0] = (byte) 0xDC;
        base[1] = 0x00;
        assertNull(WideUnicodeDetector.detectEncoding(base),
                "UTF-16BE with lone low surrogate should be ruled out");
    }

    @Test
    void utf16LeLoneLowSurrogateRuledOut() {
        byte[] base = encode("The quick brown fox jumps over the lazy dog. ".repeat(3),
                StandardCharsets.UTF_16LE);
        // Corrupt: inject a lone low surrogate at odd position 1
        base[0] = 0x00;
        base[1] = (byte) 0xDC;
        assertNull(WideUnicodeDetector.detectEncoding(base),
                "UTF-16LE with lone low surrogate should be ruled out");
    }

    @Test
    void utf16ValidSurrogatePairAccepted() {
        // U+1F600 (emoji 😀) encodes as surrogate pair in UTF-16LE:
        // high surrogate U+D83D → 3D D8, low surrogate U+DE00 → 00 DE
        // Embed it in otherwise Latin text so null-column check fires.
        byte[] latin = encode("Hello world ".repeat(10), StandardCharsets.UTF_16LE);
        // Overwrite first 4 bytes with a valid surrogate pair
        latin[0] = 0x3D;
        latin[1] = (byte) 0xD8; // high surrogate
        latin[2] = 0x00;
        latin[3] = (byte) 0xDE; // low surrogate
        assertEquals(StandardCharsets.UTF_16LE,
                WideUnicodeDetector.detectEncoding(latin),
                "Valid surrogate pair in UTF-16LE should still be detected");
    }

    // ── Stream-based detection (via EncodingDetector interface) ──────────────

    @Test
    void streamDetectsUtf16Le() throws Exception {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6);
        assertEquals(StandardCharsets.UTF_16LE, detectViaStream(encode(text, StandardCharsets.UTF_16LE)));
    }

    @Test
    void streamDetectsUtf32Be() throws Exception {
        String text = "The quick brown fox jumps. ".repeat(6);
        assertEquals(UTF32BE, detectViaStream(encode(text, UTF32BE)));
    }

    @Test
    void streamDetectsUtf16LeWithUtf8Bom() throws Exception {
        // UTF-8 BOM prepended to UTF-16LE content — BOM stripped, then UTF-16LE detected.
        byte[] combined = prepend(
                new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF},
                encode("The quick brown fox jumps over the lazy dog. ".repeat(6), StandardCharsets.UTF_16LE));
        assertEquals(StandardCharsets.UTF_16LE, detectViaStream(combined));
    }

    @Test
    void streamReturnsNullForUtf8() throws Exception {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(6);
        assertNull(detectViaStream(encode(text, StandardCharsets.UTF_8)));
    }

    @Test
    void streamReturnsNullForTooShort() throws Exception {
        assertNull(detectViaStream(new byte[]{0x41, 0x00, 0x42, 0x00}));
    }
}
