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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

public class EvidenceProbeTest {

    private static final int CT = AdaptiveProbe.DEFAULT_CONTENT_TARGET;
    private static final int RC = AdaptiveProbe.DEFAULT_RAW_CAP;
    private static final Charset W1252 = Charset.forName("windows-1252");
    private static final String RUSSIAN = "Съешь же ещё этих мягких французских булок, да выпей чаю. ";

    private static final MojibusterEncodingDetector DET = newDetector();

    private static MojibusterEncodingDetector newDetector() {
        try {
            return new MojibusterEncodingDetector();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Header-like ASCII lines, {@code n} bytes. */
    private static byte[] ascii(int n, long seed) {
        String[] words = {"Received:", "from", "mail", "server", "by", "relay", "with", "ESMTP",
                "id", "for", "Tue,", "Nov", "2026", "Subject:", "report", "status", "the", "and"};
        Random r = new Random(seed);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n) {
            sb.append(words[r.nextInt(words.length)]).append(r.nextInt(8) == 0 ? "\r\n" : " ");
        }
        return sb.substring(0, n).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            o.writeBytes(p);
        }
        return o.toByteArray();
    }

    private static byte[] prefix(byte[] b) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(b)) {
            return AdaptiveProbe.read(tis, CT, RC);
        }
    }

    private static byte[] evidence(byte[] b) throws IOException {
        try (TikaInputStream tis = TikaInputStream.get(b)) {
            return EvidenceProbe.read(tis, CT, RC);
        }
    }

    private static String detect(byte[] b) throws IOException {
        ParseContext pc = new ParseContext();
        pc.set(EncodingDetectorContext.class, new EncodingDetectorContext());
        try (TikaInputStream tis = TikaInputStream.get(b)) {
            return DET.detect(tis, new Metadata(), pc).get(0).getCharset().name();
        }
    }

    private static int highBytes(byte[] b) {
        int n = 0;
        for (byte x : b) {
            n += (x & 0xFF) >= 0x80 ? 1 : 0;
        }
        return n;
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    @Test
    public void lateUtf8IsFoundPastAnAsciiPrefix() throws Exception {
        byte[] b = cat(ascii(40_000, 1), RUSSIAN.repeat(20).getBytes(StandardCharsets.UTF_8),
                ascii(5_000, 2));
        byte[] pre = prefix(b);
        assertEquals(0, highBytes(pre));
        assertEquals("windows-1252", DET.detect(pre).get(0).getCharset().name());
        byte[] p = evidence(b);
        assertArrayEquals(pre, Arrays.copyOf(p, pre.length));
        assertTrue(p.length < pre.length + 2_000, "probe length " + p.length);
        assertEquals("UTF-8", detect(b));
    }

    @Test
    public void evidenceFollowsThePrefixAfterANewline() throws Exception {
        byte[] b = cat("café ".getBytes(W1252), ascii(40_000, 3),
                RUSSIAN.repeat(20).getBytes(StandardCharsets.UTF_8));
        byte[] pre = prefix(b);
        byte[] p = evidence(b);
        assertTrue(p.length > pre.length);
        assertArrayEquals(pre, Arrays.copyOf(p, pre.length));
        assertEquals('\n', p[pre.length]);
    }

    @Test
    public void decisivePrefixIsUnchanged() throws Exception {
        byte[] b = cat(RUSSIAN.repeat(5).getBytes(StandardCharsets.UTF_8), ascii(40_000, 4),
                "naïve".getBytes(W1252));
        assertArrayEquals(prefix(b), evidence(b));
    }

    /** Below {@value EvidenceProbe#ACCEPT_HIGH} scattered high bytes the evidence is too thin to use. */
    @ParameterizedTest
    @ValueSource(ints = {1, 7, 8})
    public void sparseLateEvidenceFallsBackToThePrefix(int n) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(ascii(40_000, 5));
        for (int i = 0; i < n; i++) {
            o.writeBytes(("Norrgård " + new String(ascii(400, i), StandardCharsets.US_ASCII))
                    .getBytes(W1252));
        }
        byte[] b = o.toByteArray();
        byte[] p = evidence(b);
        if (n < EvidenceProbe.ACCEPT_HIGH) {
            assertArrayEquals(prefix(b), p);
        } else {
            assertEquals(n, highBytes(p));
        }
    }

    /** DBCS trail bytes can be ASCII-range (0x5C here): every window must start on a character. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    public void windowsStartOnDbcsCharacterBoundaries(int split) throws Exception {
        Charset sjis = Charset.forName("windows-31j");
        String chars = "表ソ能予あい日本";
        Random r = new Random(split);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 600) {
            for (int i = 0, k = 1 + r.nextInt(6); i < k; i++) {
                sb.append(chars.charAt(r.nextInt(chars.length())));
                if (r.nextInt(3) == 0) {
                    sb.append('a');
                }
            }
            sb.append(r.nextInt(4) == 0 ? " ".repeat(40) : "  ");
        }
        String text = sb.toString();
        // the prefix ends `split` bytes into the Japanese text
        byte[] b = cat(ascii(CT - split, 6), text.getBytes(sjis));
        byte[] pre = prefix(b);
        byte[] p = evidence(b);
        assertTrue(p.length > pre.length);
        String tail = new String(p, pre.length, p.length - pre.length, sjis);
        for (String w : tail.split("\n")) {
            if (!w.isEmpty()) {
                assertTrue(text.contains(w) || new String(b, sjis).contains(w), "misaligned: " + w);
            }
        }
    }

    private static String japanese() {
        return "日本語のテスト文章です。"
                + "これは七ビットの文書です。";
    }

    @Test
    public void iso2022JpPastThePrefixIsWindowedToItsClosingEscape() throws Exception {
        byte[] jp = ("Dear user, " + japanese().repeat(10) + " thanks.\n").getBytes("ISO-2022-JP");
        byte[] b = cat(ascii(20_000, 7), jp, ascii(5_000, 8));
        assertEquals("windows-1252", detect(cat(ascii(20_000, 7), ascii(5_000, 8))));
        byte[] p = evidence(b);
        int esc = indexOf(jp, new byte[] {0x1B, '$', 'B'});
        int close = lastIndexOf(jp, new byte[] {0x1B, '(', 'B'});
        assertTrue(contains(p, Arrays.copyOfRange(jp, esc, close + 3)));
        assertEquals("ISO-2022-JP", detect(b));
    }

    /** Signature-only evidence is used only when there is no high byte at all. */
    @Test
    public void iso2022JpWithAStrayLateHighByteKeepsThePrefix() throws Exception {
        byte[] jp = japanese().repeat(10).getBytes("ISO-2022-JP");
        byte[] late = cat(ascii(20_000, 9), jp, ascii(5_000, 10), "café".getBytes(W1252));
        assertArrayEquals(prefix(late), evidence(late));
        // escapes inside the prefix: the prefix is evidence enough, whatever follows
        byte[] early = cat(jp, ascii(20_000, 11), "café".getBytes(W1252));
        assertArrayEquals(prefix(early), evidence(early));
        assertEquals("ISO-2022-JP", detect(early));
    }

    @Test
    public void hzRunIsWindowedWhole() throws Exception {
        // HZ: ~{ GB2312 bytes with the high bit cleared ~}
        byte[] gb = "中文测试文本".repeat(40).getBytes("GB2312");
        ByteArrayOutputStream hz = new ByteArrayOutputStream();
        hz.writeBytes("~{".getBytes(StandardCharsets.US_ASCII));
        for (byte x : gb) {
            hz.write(x & 0x7F);
        }
        hz.writeBytes("~}".getBytes(StandardCharsets.US_ASCII));
        byte[] run = hz.toByteArray();
        byte[] b = cat(ascii(20_000, 12), run, ascii(5_000, 13));
        assertTrue(contains(evidence(b), run));
        byte[] stray = cat(b, "café".getBytes(W1252));
        assertArrayEquals(prefix(stray), evidence(stray));
    }

    @Test
    public void utf7RunIsWindowed() throws Exception {
        byte[] run = "+BB8EQAQ4BDIENQRC-".getBytes(StandardCharsets.US_ASCII);
        byte[] b = cat(ascii(20_000, 14), run, ascii(5_000, 15));
        assertTrue(contains(evidence(b), run));
        byte[] stray = cat(b, "café".getBytes(W1252));
        assertArrayEquals(prefix(stray), evidence(stray));
    }

    /** The scan skips ASCII a word at a time: evidence must be found at every byte alignment. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9})
    public void evidenceIsFoundAtEveryAlignment(int shift) throws Exception {
        byte[] hz = "~{<:WS~}".getBytes(StandardCharsets.US_ASCII);
        byte[] fake = "a+b c~d ~~ +0100 ".getBytes(StandardCharsets.US_ASCII);
        byte[] latin = "éèêëàâçô".getBytes(W1252);
        byte[] b = cat(ascii(30_000 + shift, 19), fake, ascii(3_000, 20), hz, ascii(3_000, 21));
        assertTrue(contains(evidence(b), hz), "hz at shift " + shift);
        byte[] h = cat(ascii(30_000 + shift, 22), fake, ascii(3_000, 23), latin, ascii(3_000, 24));
        assertTrue(contains(evidence(h), latin), "high bytes at shift " + shift);
    }

    /** False signatures (base64, binary) cannot grow the probe past the signature budget. */
    @Test
    public void signatureWindowsAreBounded() throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.writeBytes(ascii(20_000, 18));
        for (int i = 0; i < 3_000; i++) {
            o.writeBytes(("~{" + "ab".repeat(15) + "~}").getBytes(StandardCharsets.US_ASCII));
            o.writeBytes(ascii(100, i));
        }
        byte[] b = o.toByteArray();
        int extra = evidence(b).length - prefix(b).length;
        assertTrue(extra > EvidenceProbe.SIG_BUDGET && extra < EvidenceProbe.SIG_BUDGET + 1_000,
                "evidence bytes " + extra);
    }

    private static final String HEBREW = "שלום עולם "
            + "זהו מסמך בעברית "
            + "על המחשב והספרים "
            + "של המדינה היום. ";
    private static final String ARABIC = "مرحبا بالعالم "
            + "هذا نص عربي عن الحاسوب "
            + "والكتب في المدينة اليوم. ";
    private static final String LATIN = "\nPage 12 of 345 - Report ABC 2024 - Ref 9876\n";

    /**
     * EBCDIC Hebrew letters are all below 0x80, so an IBM424 prefix can hold no high byte;
     * digits and Latin letters later on are.
     */
    @Test
    public void ibm424WithLateLatinKeepsThePrefix() throws Exception {
        Charset ibm424 = Charset.forName("IBM424");
        byte[] heb = HEBREW.repeat(300).getBytes(ibm424);
        byte[] b = cat(heb, LATIN.repeat(3).getBytes(ibm424));
        byte[] pre = prefix(b);
        assertEquals(0, highBytes(pre));
        byte[] p = evidence(b);
        assertTrue(p.length > pre.length);
        assertArrayEquals(pre, Arrays.copyOf(p, pre.length));
        assertEquals("IBM424", detect(b));
    }

    @Test
    public void ibm420PrefixIsKeptWhole() throws Exception {
        Charset ibm420 = Charset.forName("IBM420");
        byte[] b = cat(ARABIC.repeat(300).getBytes(ibm420), LATIN.repeat(3).getBytes(ibm420));
        byte[] pre = prefix(b);
        byte[] p = evidence(b);
        assertArrayEquals(pre, Arrays.copyOf(p, pre.length));
        assertEquals("IBM420", detect(b));
    }

    /** A passthrough stream is scanned no further than {@value EvidenceProbe#STREAM_SCAN_CAP} bytes. */
    @Test
    public void streamsUseTheSmallerCap(@TempDir Path tmp) throws Exception {
        byte[] lateUtf8 = RUSSIAN.repeat(20).getBytes(StandardCharsets.UTF_8);
        byte[] b = cat(ascii(1_200_000, 16), lateUtf8);
        try (TikaInputStream tis = TikaInputStream.get(new ByteArrayInputStream(b))) {
            assertArrayEquals(prefix(b), EvidenceProbe.read(tis, CT, RC));
            assertEquals(0, tis.getPosition());
        }
        Path f = tmp.resolve("late.txt");
        Files.write(f, b);
        try (TikaInputStream tis = TikaInputStream.get(f)) {
            assertTrue(contains(EvidenceProbe.read(tis, CT, RC), Arrays.copyOf(lateUtf8, 64)));
            assertEquals(0, tis.getPosition());
            byte[] all = tis.readAllBytes();
            assertArrayEquals(b, all);
        }
    }

    /** The detector caches the evidence probe under its own kind. */
    @Test
    public void probeIsCachedUnderItsKind() throws Exception {
        byte[] b = cat(ascii(40_000, 17), RUSSIAN.repeat(20).getBytes(StandardCharsets.UTF_8));
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ParseContext pc = new ParseContext();
        pc.set(EncodingDetectorContext.class, ctx);
        try (TikaInputStream tis = TikaInputStream.get(b)) {
            EncodingResult top = DET.detect(tis, new Metadata(), pc).get(0);
            assertEquals("UTF-8", top.getCharset().name());
        }
        assertArrayEquals(evidence(b), ctx.getProbeCache().get(EvidenceProbe.KIND, CT, RC));
        assertEquals(null, ctx.getProbeCache().get(CT, RC));
    }

    private static int indexOf(byte[] b, byte[] n) {
        for (int i = 0; i + n.length <= b.length; i++) {
            if (Arrays.equals(b, i, i + n.length, n, 0, n.length)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(byte[] b, byte[] n) {
        for (int i = b.length - n.length; i >= 0; i--) {
            if (Arrays.equals(b, i, i + n.length, n, 0, n.length)) {
                return i;
            }
        }
        return -1;
    }
}
