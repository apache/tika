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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.Charset;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Regression tests for charset detection edge-cases that surfaced during
 * integration testing with the CharSoup language-aware detector.
 *
 * <ul>
 *   <li><b>ASCII-only HTML</b> (Solr integration test regression): simple
 *       {@code <html><body>…</body></html>} content written as UTF-8 was
 *       returned as {@code ISO-8859-1} by the old detector chain.
 *       The correct answer is {@code UTF-8}.</li>
 *   <li><b>Short plain-text English</b> (TXTParserTest regression): a short
 *       English paragraph whose bytes are all in the ASCII range was returned
 *       as {@code ISO-8859-1} and in some cases as {@code UTF-16}.
 *       The ML-based chain must not return UTF-16 for ASCII-range input.</li>
 *   <li><b>Shift-JIS ZIP entry name</b>: 9 raw bytes encoding {@code 文章1.txt}
 *       in Shift-JIS must be detected as {@code Shift_JIS}, not Big5-HKSCS.
 *       The raw ML logits favour Big5-HKSCS; the CharSoup language signal must
 *       override the model ranking.</li>
 * </ul>
 */
public class CharsetDetectionRegressionTest {

    // 文章1.txt in Shift-JIS (9 raw bytes from a real zip entry)
    private static final byte[] SJIS_RAW = hexToBytes("95b68fcd312e747874");

    // Pure-ASCII HTML without a meta charset declaration — mirrors what the
    // Solr integration test wrote before the meta-tag workaround was added.
    // The old detector returned ISO-8859-1 for this without any meta tag.
    // The new detector required adding <meta charset="UTF-8"> to avoid
    // returning an unexpected charset.
    private static final byte[] ASCII_HTML_NO_META =
            "<html><body>initial</body></html>".getBytes(UTF_8);

    // English plain text from TXTParserTest — all bytes in the ASCII range
    private static final byte[] ENGLISH_TEXT =
            ("Hello, World! This is simple UTF-8 text content written"
            + " in English to test autodetection of both the character"
            + " encoding and the language of the input stream.").getBytes(UTF_8);

    // -----------------------------------------------------------------------
    // Solr integration-test regression
    // -----------------------------------------------------------------------

    /**
     * ASCII HTML <em>without</em> a meta charset declaration must not be
     * returned as UTF-16.
     *
     * <p>The old detector returned {@code ISO-8859-1} here without requiring
     * any meta tag.  The new detector regressed: without a meta tag it started
     * returning an unexpected charset, which caused the Solr integration test
     * to fail.  The workaround was to add {@code <meta charset="UTF-8">} to
     * the generated HTML — but we should not need to do that.  UTF-8,
     * US-ASCII, and ISO-8859-1 are all acceptable; UTF-16 is not.</p>
     */
    @Test
    public void asciiHtmlWithoutMetaIsNotDetectedAsUtf16() throws Exception {
        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(ASCII_HTML_NO_META)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "detector returned no result for ASCII HTML");
            Charset top = results.get(0).getCharset();
            assertFalse(top.name().startsWith("UTF-16"),
                    "ASCII HTML without meta tag must not be detected as UTF-16, got: "
                            + top.name());
        }
    }

    // -----------------------------------------------------------------------
    // TXTParser regression
    // -----------------------------------------------------------------------

    /**
     * A plain-English paragraph whose bytes are all in the ASCII range must
     * be returned as {@code windows-1252} — the HTML5/WHATWG default for
     * unlabeled 8-bit Western content and the statistical fallback for
     * pure-ASCII bytes in the ML-based detector chain.
     */
    @Test
    public void englishPlainTextIsDetectedAsWindows1252() throws Exception {
        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(ENGLISH_TEXT)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "detector returned no result for English text");
            Charset top = results.get(0).getCharset();
            assertEquals("windows-1252", top.name(),
                    "Pure-ASCII English text should be detected as windows-1252, got: "
                            + top.name());
        }
    }

    // -----------------------------------------------------------------------
    // Shift-JIS ZIP entry name
    // -----------------------------------------------------------------------

    /**
     * 9 raw bytes encoding {@code 文章1.txt} in Shift-JIS must be identified
     * as {@code Shift_JIS}.
     *
     * <p>The same bytes are structurally valid Big5-HKSCS and ranked higher by
     * the raw ML logits.  CharSoup must override the model ranking using the
     * Japanese language signal.  ZipParser feeds entry names as raw byte arrays
     * to the encoding detector, so a wrong answer here means garbled filenames
     * in Japanese zip archives.</p>
     */
    @Disabled("Requires retrained model from TIKA-4691")
    @Test
    public void sjisZipEntryNameIsDetectedAsShiftJis() throws Exception {
        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(SJIS_RAW)) {
            List<EncodingResult> results =
                    detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(),
                    "detector returned no result for SJIS filename bytes");
            Charset top = results.get(0).getCharset();
            assertEquals("Shift_JIS", top.name(),
                    "SJIS zip entry bytes should be detected as Shift_JIS, got: " + top.name());
        }
    }

    // -----------------------------------------------------------------------

    private static byte[] hexToBytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }
}
