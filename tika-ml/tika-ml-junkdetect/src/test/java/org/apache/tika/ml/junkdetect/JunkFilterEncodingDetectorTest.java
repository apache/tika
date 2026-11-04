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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Unit tests for {@link JunkFilterEncodingDetector}: the rules that decide without the model,
 * the hashed arbitration with the bundled model, and pinned behaviour on synthesized bytes.
 */
public class JunkFilterEncodingDetectorTest {

    private static final HashedJunkModel MODEL = load();

    private static HashedJunkModel load() {
        try {
            return JunkDetector.loadFromClasspath().getModel();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static ParseContext contextWith(EncodingResult... results) {
        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(results), "stub");
        ParseContext p = new ParseContext();
        p.set(EncodingDetectorContext.class, ctx);
        return p;
    }

    @Test
    public void picksPreferredCharsetFromTwoCandidates() throws Exception {
        Charset utf8 = StandardCharsets.UTF_8;
        Charset win1252 = Charset.forName("windows-1252");
        // Non-ASCII bytes so UTF-8 and windows-1252 decode to different strings
        // (otherwise arbiter sees identical decodings and abstains).
        byte[] bytes = "café résumé naïve".getBytes(StandardCharsets.UTF_8);

        ParseContext pc = contextWith(
                new EncodingResult(utf8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(win1252, 0.5f, "windows-1252",
                        EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size(), "Expected exactly one result");
            assertEquals(utf8, out.get(0).getCharset());
        }
    }

    @Test
    public void noopWithoutAModel() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector((HashedJunkModel) null);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "no model -> the filter is a no-op");
        }
    }

    @Test
    public void noopWhenOnlyOneCandidate() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.9f, "UTF-8",
                        EncodingResult.ResultType.DECLARATIVE));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Single candidate → no arbitration needed, no-op");
        }
    }

    @Test
    public void noopWhenAllDecodingsIdentical() throws Exception {
        // Pure-ASCII bytes decode identically under UTF-8 and windows-1252.
        byte[] bytes = "plain ascii content".getBytes(StandardCharsets.US_ASCII);

        ParseContext pc = contextWith(
                new EncodingResult(StandardCharsets.UTF_8, 0.5f, "UTF-8",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(Charset.forName("windows-1252"), 0.5f,
                        "windows-1252", EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertTrue(out.isEmpty(),
                    "Byte-identical decodings → arbiter abstains");
            assertEquals("junk-filter-identical-decodings",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    /** {@code <a x="..">plain body</a>} with {@code hi} as the attribute's high
     *  bytes — all non-ASCII lives in markup, so every candidate's tag-stripped
     *  text ties and the equivalent-decode shortcut engages. */
    private static byte[] markupWithHighBytes(byte[] hi) {
        byte[] pre = "<a x=\"".getBytes(StandardCharsets.US_ASCII);
        byte[] post = "\">plain ascii body text here</a>".getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[pre.length + hi.length + post.length];
        System.arraycopy(pre, 0, out, 0, pre.length);
        System.arraycopy(hi, 0, out, pre.length, hi.length);
        System.arraycopy(post, 0, out, pre.length + hi.length, post.length);
        return out;
    }

    @Test
    public void structuralUtf8OverridesDeclaredLatin() throws Exception {
        // Real-bug shape (TIKA test.html): declared x-MacRoman but bytes are UTF-8
        // (é = C3 A9) with the only high bytes in markup, so stripped text ties.
        // A STRUCTURAL proof must beat the contradicted declaration.
        Charset utf8 = StandardCharsets.UTF_8;
        Charset macRoman = Charset.forName("x-MacRoman");
        byte[] bytes = markupWithHighBytes(new byte[] {(byte) 0xC3, (byte) 0xA9});

        ParseContext pc = contextWith(
                new EncodingResult(macRoman, 0.9f, "x-MacRoman",
                        EncodingResult.ResultType.DECLARATIVE),
                new EncodingResult(utf8, 0.95f, "UTF-8",
                        EncodingResult.ResultType.STRUCTURAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(utf8, out.get(0).getCharset());
            assertEquals("junk-filter-prefer-structural",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    @Test
    public void structuralUtf8DefersToDeclaredCjkWithoutWideSequence() throws Exception {
        // Declared Shift_JIS + STRUCTURAL UTF-8, but the only UTF-8 evidence is a
        // 2-byte sequence a legacy CJK pair can fake — keep the declaration.
        Charset utf8 = StandardCharsets.UTF_8;
        Charset sjis = Charset.forName("Shift_JIS");
        byte[] bytes = markupWithHighBytes(new byte[] {(byte) 0xC3, (byte) 0xA9});

        ParseContext pc = contextWith(
                new EncodingResult(sjis, 0.9f, "Shift_JIS",
                        EncodingResult.ResultType.DECLARATIVE),
                new EncodingResult(utf8, 0.95f, "UTF-8",
                        EncodingResult.ResultType.STRUCTURAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(sjis, out.get(0).getCharset());
            assertEquals("junk-filter-prefer-declarative",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    @Test
    public void structuralUtf8OverridesDeclaredCjkWithWideSequence() throws Exception {
        // Declared Shift_JIS + STRUCTURAL UTF-8 with a 3-byte sequence (日 =
        // E6 97 A5): unfakeable by a legacy 2-byte CJK pair, so the proof wins.
        Charset utf8 = StandardCharsets.UTF_8;
        Charset sjis = Charset.forName("Shift_JIS");
        byte[] bytes = markupWithHighBytes(
                new byte[] {(byte) 0xE6, (byte) 0x97, (byte) 0xA5});

        ParseContext pc = contextWith(
                new EncodingResult(sjis, 0.9f, "Shift_JIS",
                        EncodingResult.ResultType.DECLARATIVE),
                new EncodingResult(utf8, 0.95f, "UTF-8",
                        EncodingResult.ResultType.STRUCTURAL));

        JunkFilterEncodingDetector detector =
                new JunkFilterEncodingDetector(MODEL);
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size());
            assertEquals(utf8, out.get(0).getCharset());
            assertEquals("junk-filter-prefer-structural",
                    pc.get(EncodingDetectorContext.class).getArbitrationInfo());
        }
    }

    /**
     * Regression: Korean text was once mis-arbitrated to GB18030 (Chinese) because the old
     * per-script model rated Han mojibake above real Hangul.  The bundled model, loaded by the
     * default constructor, on synthesized bytes.
     */
    @Test
    public void koreanTextNotMisarbitragedToChinese() throws Exception {
        Charset xwin949 = Charset.forName("x-windows-949");
        Charset gb18030 = Charset.forName("GB18030");
        // Real Korean text — enough characters that the HANGUL classifier
        // has signal to work with after HTML strip would leave it alone
        // (the bytes are pure non-HTML).
        String korean = "초록샘 새벽교회 주일말씀 열린침례교회 한국교회";
        byte[] bytes = korean.getBytes(xwin949);

        // Note: GB18030 listed first so calibrated arbitration has to beat
        // the insertion-order tiebreak to pick x-windows-949 — this also
        // exercises the cross-script calibration directly.
        ParseContext pc = contextWith(
                new EncodingResult(gb18030, 1.0f, "GB18030",
                        EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(xwin949, 1.0f, "x-windows-949",
                        EncodingResult.ResultType.STATISTICAL));

        JunkFilterEncodingDetector detector = new JunkFilterEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            List<EncodingResult> out = detector.detect(tis, new Metadata(), pc);
            assertEquals(1, out.size(), "Expected exactly one result");
            assertEquals(xwin949, out.get(0).getCharset(),
                    "Korean text must arbitrate to x-windows-949, not GB18030");
        }
    }

    @Test
    void expandHtmlEntities_numericDecimalResolvesToCodepoint() {
        // U+0D4D = Malayalam Sign Virama
        assertEquals("്",
                JunkFilterEncodingDetector.expandHtmlEntities("&#3405;"));
        // Surrounding ASCII preserved
        assertEquals("a്b",
                JunkFilterEncodingDetector.expandHtmlEntities("a&#3405;b"));
    }

    @Test
    void expandHtmlEntities_numericHexResolvesToCodepoint() {
        // U+4E2D = 中 (Han ideograph "middle")
        assertEquals("中",
                JunkFilterEncodingDetector.expandHtmlEntities("&#x4E2D;"));
        assertEquals("中",
                JunkFilterEncodingDetector.expandHtmlEntities("&#x4e2d;"));
    }

    @Test
    void expandHtmlEntities_namedReferences() {
        assertEquals("&", JunkFilterEncodingDetector.expandHtmlEntities("&amp;"));
        assertEquals("<", JunkFilterEncodingDetector.expandHtmlEntities("&lt;"));
        assertEquals(">", JunkFilterEncodingDetector.expandHtmlEntities("&gt;"));
        assertEquals("\"", JunkFilterEncodingDetector.expandHtmlEntities("&quot;"));
        assertEquals("a & b < c", JunkFilterEncodingDetector.expandHtmlEntities("a &amp; b &lt; c"));
    }

    @Test
    void expandHtmlEntities_malformedPassesThrough() {
        // No semicolon → not matched, left as literal
        assertEquals("&#3405", JunkFilterEncodingDetector.expandHtmlEntities("&#3405"));
        // Unknown named entity → left as literal
        assertEquals("&unknown;",
                JunkFilterEncodingDetector.expandHtmlEntities("&unknown;"));
    }

    @Test
    void expandHtmlEntities_mixedEntityAndRawCodepoints() {
        // Simulates an AIT5-style document: mix of raw Malayalam codepoints
        // and numeric entity references encoding more Malayalam codepoints.
        // ത = ത  ് = ് (virama)
        String input = "ത&#3405;ര";
        String expected = "ത്ര";
        assertEquals(expected, JunkFilterEncodingDetector.expandHtmlEntities(input));
    }

    /** The filter returns {@link HashedArbitration}'s pick for the candidates it reports. */
    @Test
    public void filterAppliesTheHashedArbitration() throws Exception {
        Charset w1252 = Charset.forName("windows-1252");
        Charset l9 = Charset.forName("ISO-8859-15");
        Charset utf8 = StandardCharsets.UTF_8;
        String[] texts = {"Der Preis betr\u00e4gt 5 \u20ac pro St\u00fcck und Woche",
                "Le minist\u00e8re a \u00e9t\u00e9 cr\u00e9\u00e9 \u00e0 Paris o\u00f9 il r\u00e9side"};
        Charset[] sources = {l9, utf8};
        JunkFilterEncodingDetector detector = new JunkFilterEncodingDetector(MODEL);
        for (int i = 0; i < texts.length; i++) {
            byte[] bytes = texts[i].getBytes(sources[i]);
            ParseContext pc = contextWith(
                    new EncodingResult(w1252, 0.9f, "windows-1252", EncodingResult.ResultType.STATISTICAL),
                    new EncodingResult(sources[i], 0.9f, sources[i].name(), EncodingResult.ResultType.DECLARATIVE));
            EncodingDetectorContext ctx = pc.get(EncodingDetectorContext.class);
            HashedArbitration.Decision d;
            try (TikaInputStream tis = TikaInputStream.get(bytes)) {
                d = HashedArbitration.decide(MODEL, detector.candidates(tis, ctx));
            }
            try (TikaInputStream tis = TikaInputStream.get(bytes)) {
                assertEquals(d.pick(), detector.detect(tis, new Metadata(), pc).get(0).getCharset());
            }
            assertEquals(d.pick().equals(d.getChampion()) ? "junk-filter-hashed" : "junk-filter-hashed-deferred",
                    ctx.getArbitrationInfo());
            assertEquals(sources[i], d.pick(), texts[i]);
        }
    }
}
