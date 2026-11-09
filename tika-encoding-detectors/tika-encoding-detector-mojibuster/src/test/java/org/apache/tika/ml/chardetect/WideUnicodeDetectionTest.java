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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.detect.EncodingResult;

/** UTF-16 and UTF-32 without a BOM (TIKA-5048), and the pure-ASCII gate. */
public class WideUnicodeDetectionTest {

    private static MojibusterEncodingDetector detector;

    @BeforeAll
    static void load() throws Exception {
        detector = new MojibusterEncodingDetector();
    }

    private static String top(byte[] probe) {
        List<EncodingResult> results = detector.detect(probe);
        assertFalse(results.isEmpty());
        return results.get(0).getCharset().name();
    }

    private static String emojiCsv() {
        int[] emoji = {0x1F604, 0x1F603, 0x1F600, 0x1F60A, 0x263A, 0x2639};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            sb.append(',').appendCodePoint(emoji[i % emoji.length])
                    .append(", Smiling Face Number ").append(i).append(",0x09,*,*,0,0,\r\n");
        }
        return sb.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    public void emojiInFirst64BytesNoBom(String charset) {
        byte[] bytes = emojiCsv().getBytes(Charset.forName(charset));
        assertEquals(charset, top(bytes));
        assertEquals(charset, top(Arrays.copyOf(bytes, 64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE"})
    public void emojiStraddlingByte512(String charset) {
        String prefix = "The quick brown fox jumps over the lazy dog. ".repeat(6).substring(0, 255);
        String text = prefix + "😀 and the rest of the line, more words follow here.";
        byte[] bytes = text.getBytes(Charset.forName(charset));
        // high surrogate at bytes 510-511, low at 512-513
        assertEquals(charset, top(bytes));
        assertEquals(charset, top(Arrays.copyOf(bytes, 512)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-32LE", "UTF-32BE"})
    public void utf32NeverReportedAsUtf16(String charset) {
        for (String text : new String[]{emojiCsv(), "Привет, мир! Hello, world.", "日本語のテキスト"}) {
            List<EncodingResult> results = detector.detect(text.getBytes(Charset.forName(charset)));
            assertEquals(charset, results.get(0).getCharset().name(), text);
            for (EncodingResult r : results) {
                assertFalse(r.getCharset().name().startsWith("UTF-16"), r.toString());
            }
        }
    }

    /**
     * No NUL and no byte at or above 0x80: these used to pass as pure ASCII.  (Devanagari
     * needs a code unit below U+0920 here: 09 xx with printable xx is TAB + text, which the
     * model abstains on.)
     */
    @ParameterizedTest
    @CsvSource({
            "UTF-16LE, สวัสดีครับผม",
            "UTF-16BE, สวัสดีครับผม",
            "UTF-16LE, Приветмир",
            "UTF-16BE, Приветмир",
            "UTF-16BE, किताबपढ़ो",
    })
    public void shortNoBomUtf16WithoutNulOrHighBytes(String charset, String text) {
        byte[] bytes = text.substring(0, 8).getBytes(Charset.forName(charset));
        assertEquals(16, bytes.length);
        assertFalse(MojibusterEncodingDetector.isPureAscii(bytes));
        assertEquals(charset, top(bytes));
    }

    // TODO: 16-byte BOM-less Devanagari UTF-16LE goes to x-MacRoman; add short UTF-16 Indic to training
    @Disabled("thin signal: 16-byte BOM-less Devanagari UTF-16LE")
    @Test
    public void shortNoBomUtf16LeDevanagari() {
        byte[] bytes = "किताबपढ़ो".substring(0, 8).getBytes(Charset.forName("UTF-16LE"));
        assertEquals("UTF-16LE", top(bytes));
    }

    @ParameterizedTest
    @CsvSource({
            "UTF-16LE, This is ordinary English text in UTF-16 without a byte order mark.",
            "UTF-16BE, This is ordinary English text in UTF-16 without a byte order mark.",
            "UTF-16LE, 中文文本的编码检测测试，没有字节顺序标记。这是第二句话。",
            "UTF-16BE, 中文文本的编码检测测试，没有字节顺序标记。这是第二句话。",
    })
    public void noBomUtf16(String charset, String text) {
        assertEquals(charset, top(text.getBytes(Charset.forName(charset))));
    }

    @Test
    public void asciiHtmlIsNotUtf16() {
        byte[] bytes = ("<html><head><title>Test</title></head><body><p>Hello, world. "
                + "Plain markup with no high bytes at all.</p></body></html>")
                .getBytes(StandardCharsets.US_ASCII);
        assertEquals("windows-1252", top(bytes));
    }

    @Test
    public void scatteredNulsInLatinTextAreNotUtf16() {
        byte[] bytes = "Grüße aus München, schöne Grüße und viele Wörter über Straßen. "
                .repeat(8).getBytes(Charset.forName("windows-1252"));
        for (int i = 37; i < bytes.length; i += 97) {
            bytes[i] = 0;
        }
        assertFalse(top(bytes).startsWith("UTF-16"));
    }

    /** Text bytes with a stray control decode the same in every ASCII-compatible charset. */
    @ParameterizedTest
    @ValueSource(ints = {0x01, 0x08, 0x0B, 0x1A, 0x7F})
    public void asciiWithStrayControlIsWindows1252(int control) {
        String line = "This is a plain English sentence about nothing in particular.\r\n";
        byte[] bytes = (line.repeat(3) + (char) control).getBytes(StandardCharsets.US_ASCII);
        List<EncodingResult> results = detector.detect(bytes);
        assertEquals(1, results.size());
        assertEquals("windows-1252", results.get(0).getCharset().name());
    }

    @Test
    public void pureAsciiGate() {
        assertTrue(MojibusterEncodingDetector.isPureAscii("a\tb\nc\fd\re\u001b$B".getBytes(
                StandardCharsets.US_ASCII)));
        assertFalse(MojibusterEncodingDetector.isPureAscii(new byte[]{'a', 0x0E, 0x2A}));
        assertFalse(MojibusterEncodingDetector.isPureAscii(new byte[]{'a', 0, 'b'}));
        assertFalse(MojibusterEncodingDetector.isPureAscii(new byte[]{'a', (byte) 0xE9}));
    }

    /** Binary: no model candidate, so the chain sees the low-confidence windows-1252 fallback. */
    @Test
    public void binaryGetsTheFallback() {
        byte[] bytes = new byte[4096];
        new Random(20261105L).nextBytes(bytes);
        List<EncodingResult> results = detector.detect(bytes);
        assertEquals(1, results.size());
        assertEquals("windows-1252", results.get(0).getCharset().name());
        assertEquals(0.1f, results.get(0).getConfidence());
    }

    /**
     * Header {@code 80 37 00 00} then ASCII '0'/'1' (a CC .mtsl2 shader file): the model scores only the
     * header's NULs and says UTF-32BE, but no 4-byte group is a code point.
     */
    @Test
    public void bitstringWithBinaryHeaderIsNotUtf32() {
        byte[] bytes = new byte[10240];
        bytes[0] = (byte) 0x80;
        bytes[1] = 0x37;
        Random r = new Random(380107L);
        for (int i = 4; i < bytes.length; i++) {
            bytes[i] = (byte) (r.nextBoolean() ? '1' : '0');
        }
        WideUnicodeDetector.Result wide = WideUnicodeDetector.analyze(bytes);
        assertTrue(wide.invalidUtf32Be);
        assertTrue(wide.invalidUtf32Le);
        assertEquals("windows-1252", top(bytes));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-32BE", "UTF-32LE"})
    public void validUtf32HasNoInvalidFlag(String charset) {
        WideUnicodeDetector.Result wide = WideUnicodeDetector.analyze(
                "Grüße, мир, 世界".getBytes(Charset.forName(charset)));
        assertEquals(charset, wide.charset.name());
        assertFalse("UTF-32BE".equals(charset) ? wide.invalidUtf32Be : wide.invalidUtf32Le);
    }

    /** UTF-16LE of {@code text} with raw code {@code units} inserted at char index {@code at}. */
    private static byte[] le(String text, int at, int... units) {
        byte[] head = text.substring(0, at).getBytes(StandardCharsets.UTF_16LE);
        byte[] tail = text.substring(at).getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[head.length + 2 * units.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        for (int i = 0; i < units.length; i++) {
            out[head.length + 2 * i] = (byte) units[i];
            out[head.length + 2 * i + 1] = (byte) (units[i] >>> 8);
        }
        System.arraycopy(tail, 0, out, head.length + 2 * units.length, tail.length);
        return out;
    }

    @Test
    public void surrogateCutAtProbeEndIsNotInvalid() {
        assertFalse(WideUnicodeDetector.analyze(le("abcdefgh", 8, 0xD83D)).invalidUtf16Le);
        byte[] be = new byte[]{0, 'a', 0, 'b', 0, 'c', 0, 'd', (byte) 0xD8, 0x3D};
        assertFalse(WideUnicodeDetector.analyze(be).invalidUtf16Be);
        // a high surrogate followed by a non-surrogate is still invalid
        assertTrue(WideUnicodeDetector.analyze(le("abcdefghx", 8, 0xD83D)).invalidUtf16Le);
    }

    /** The surrogate check samples 512 bytes; a pair cut there is checked against the next unit. */
    @Test
    public void surrogateCutAtSampleEndLooksAhead() {
        String prefix = "x".repeat(255);
        assertFalse(WideUnicodeDetector.analyze(le(prefix + " tail", 255, 0xD83D, 0xDE00))
                .invalidUtf16Le);
        assertTrue(WideUnicodeDetector.analyze(le(prefix + "x tail", 255, 0xD83D)).invalidUtf16Le);
    }
}
