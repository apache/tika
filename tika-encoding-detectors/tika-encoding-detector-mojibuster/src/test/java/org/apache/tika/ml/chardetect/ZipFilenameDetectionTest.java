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
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.langdetect.charsoup.CharSoupEncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Integration tests for charset detection of short byte sequences typical of
 * ZIP entry names — a particularly hard case because the probes are tiny (6-23
 * bytes) and structurally valid in several encodings simultaneously.
 *
 * Detection strategy: Mojibuster ranks candidates by raw logit; CharSoup
 * arbitrates using language signal (positive max-logit wins).
 */
public class ZipFilenameDetectionTest {

    // 文章1.txt in Shift-JIS (9 raw bytes from a real zip entry)
    private static final byte[] SJIS_RAW  = hexToBytes("95b68fcd312e747874");
    // 文章2.txt in Shift-JIS (same but '2' instead of '1')
    private static final byte[] SJIS_RAW2 = hexToBytes("95b68fcd322e747874");
    // 审计压缩包文件检索测试/ in GBK (23 bytes from gbk.zip)
    private static final byte[] GBK_RAW   = hexToBytes("c9f3bcc6d1b9cbf5b0fccec4bcfebceccbf7b2e2cad42f");

    private static byte[] hexToBytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    /**
     * CharSoup should confirm Shift-JIS even when Mojibuster ranks Big5-HKSCS first,
     * because the language model gives a higher logit to the Japanese text decoded
     * from the same bytes.
     */
    @Disabled("Requires generative language model for reliable Shift-JIS vs Big5-HKSCS arbitration")
    @Test
    public void charSoupOverridesModelRankingForShiftJis() throws Exception {
        Charset big5 = Charset.forName("Big5-HKSCS");
        Charset shiftJis = Charset.forName("Shift_JIS");

        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(
                new EncodingResult(big5,     0.9f, "Big5-HKSCS", EncodingResult.ResultType.STATISTICAL),
                new EncodingResult(shiftJis, 0.3f, "Shift_JIS",  EncodingResult.ResultType.STATISTICAL)
        ), "MojibusterEncodingDetector");

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, ctx);

        CharSoupEncodingDetector charSoup = new CharSoupEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(SJIS_RAW)) {
            List<EncodingResult> result = charSoup.detect(tis, new Metadata(), parseContext);
            assertTrue(!result.isEmpty(), "CharSoup should return a result");
            assertEquals(shiftJis, result.get(0).getCharset(),
                    "CharSoup should pick Shift-JIS (文章) over Big5-HKSCS via language signal");
        }
    }

    /**
     * Full pipeline (BOM → Metadata → Mojibuster → StandardHtml → CharSoup) run
     * sequentially on two entries differing only in byte 5 (0x31 vs 0x32), simulating
     * what ZipParser does when iterating entries with the same ParseContext.
     */
    @Disabled("Requires generative language model for reliable Shift-JIS detection on short probes")
    @Test
    public void fullPipelineDetectsBothSjisEntries() throws Exception {
        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        Metadata parentMeta = new Metadata();
        ParseContext outerContext = new ParseContext();

        for (byte[] raw : new byte[][]{SJIS_RAW, SJIS_RAW2}) {
            String label = (raw == SJIS_RAW) ? "文章1.txt" : "文章2.txt";
            try (TikaInputStream tis = TikaInputStream.get(raw)) {
                List<EncodingResult> results = detector.detect(tis, parentMeta, outerContext);
                String charset = results.isEmpty() ? "(empty)" : results.get(0).getCharset().name();
                assertTrue(!results.isEmpty() && "Shift_JIS".equals(results.get(0).getCharset().name()),
                        label + " should be detected as Shift_JIS, got: " + charset);
            }
        }
    }

    /**
     * Full pipeline should detect GBK-encoded entry names as GB18030.
     * Disabled: CharSoup's discriminative language model picks KOI8-U over GB18030
     * on short probes because the GBK bytes happen to score as Cyrillic.
     * Re-enable once generative language models are in place (better calibrated
     * confidence will let CharSoup correctly abstain on cross-script ambiguity).
     */
    @Disabled("Requires generative language model for reliable cross-script arbitration")
    @Test
    public void fullPipelineDetectsGbkEntry() throws Exception {
        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        Metadata meta = new Metadata();
        try (TikaInputStream tis = TikaInputStream.get(GBK_RAW)) {
            List<EncodingResult> results = detector.detect(tis, meta, new ParseContext());
            String charset = results.isEmpty() ? "(empty)" : results.get(0).getCharset().name();
            assertTrue(!results.isEmpty() && results.get(0).getCharset().name().startsWith("GB"),
                    "GBK entry should be detected as GB18030/GBK, got: " + charset);
        }
    }
}
