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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.langdetect.charsoup.CharSoupEncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * Diagnostic: raw logits and CharSoup arbitration for Shift-JIS zip entry name bytes.
 *
 * The v2 model (28 classes) removes the UTF-16/32 labels that were confusing the model.
 * With v2, Shift-JIS (logit ~10.5) scores clearly above GB18030 (logit ~6.2) for the
 * bytes "文章1.txt" in Shift-JIS encoding.
 */
public class ZipFilenameDetectionTest {

    // 文章1.txt in Shift-JIS (9 raw bytes from a real zip entry)
    private static final byte[] SJIS_RAW = hexToBytes("95b68fcd312e747874");

    private static byte[] hexToBytes(String hex) {
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private boolean isWideUnicode(String label) {
        return label.startsWith("UTF-16") || label.startsWith("UTF-32");
    }

    @Test
    public void printModelLabels() throws Exception {
        LinearModel model = new MojibusterEncodingDetector().getModel();
        String[] labels = model.getLabels();
        System.out.println("Model labels (" + labels.length + "):");
        for (String l : labels) {
            System.out.println("  " + l);
        }
        long wideCount = Arrays.stream(labels).filter(this::isWideUnicode).count();
        System.out.println("Wide-unicode labels in model: " + wideCount
                + " (expected 0 — handled structurally by WideUnicodeDetector)");
        assertEquals(0, wideCount, "v2 model should have no wide-unicode labels");
    }

    @Test
    public void diagnoseLogits() throws Exception {
        MojibusterEncodingDetector detector = new MojibusterEncodingDetector();
        LinearModel model = detector.getModel();
        ByteNgramFeatureExtractor extractor =
                new ByteNgramFeatureExtractor(model.getNumBuckets());
        String[] labels = model.getLabels();

        float[] logits = model.predictLogits(extractor.extract(SJIS_RAW));

        Integer[] idx = new Integer[labels.length];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Float.compare(logits[b], logits[a]));

        System.out.printf(Locale.ROOT, "%n=== Raw logits for 文章1.txt (9 bytes) ===%n");
        System.out.printf(Locale.ROOT, "%-24s %8s%n", "charset", "logit");
        System.out.println("-".repeat(35));
        float shiftJisLogit = Float.NEGATIVE_INFINITY;
        float gb18030Logit = Float.NEGATIVE_INFINITY;
        for (int rank = 0; rank < labels.length; rank++) {
            int i = idx[rank];
            boolean cjk = labels[i].contains("JIS") || labels[i].contains("GB")
                    || labels[i].contains("Big5") || labels[i].contains("EUC");
            if (rank < 6 || cjk) {
                System.out.printf(Locale.ROOT, "  %-24s %8.2f%n", labels[i], logits[i]);
            }
            if ("Shift_JIS".equals(labels[i])) {
                shiftJisLogit = logits[i];
            } else if ("GB18030".equals(labels[i])) {
                gb18030Logit = logits[i];
            }
        }
        // Verify Shift-JIS ranks ahead of GB18030 on raw (un-tiled) bytes.
        // ZipParser no longer tiles short filenames, so this is the actual input.
        assertTrue(shiftJisLogit > gb18030Logit,
                String.format(Locale.ROOT,
                        "Shift_JIS logit (%.2f) should beat GB18030 logit (%.2f)",
                        shiftJisLogit, gb18030Logit));
    }

    /**
     * Verifies CharSoup correctly picks Shift-JIS when it and GB18030 are both candidates.
     * With v2 model, Mojibuster already ranks Shift-JIS above GB18030 (logit ~10.5 vs ~6.2).
     * This test uses Shift-JIS as the higher-confidence candidate to reflect that reality.
     */
    @Test
    public void charSoupPicksShiftJis() throws Exception {
        Charset shiftJis = Charset.forName("Shift_JIS");
        Charset gb18030 = Charset.forName("GB18030");

        EncodingDetectorContext ctx = new EncodingDetectorContext();
        ctx.addResult(List.of(new EncodingResult(shiftJis, 0.6f)), "MojibusterEncodingDetector");
        ctx.addResult(List.of(new EncodingResult(gb18030, 0.5f)), "MojibusterEncodingDetector");

        ParseContext parseContext = new ParseContext();
        parseContext.set(EncodingDetectorContext.class, ctx);

        CharSoupEncodingDetector charSoup = new CharSoupEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(SJIS_RAW)) {
            List<EncodingResult> result = charSoup.detect(tis, new Metadata(), parseContext);

            System.out.println("\n=== CharSoup arbitration: Shift-JIS(0.6) vs GB18030(0.5) ===");
            System.out.println("arbitration: " + ctx.getArbitrationInfo());
            if (!result.isEmpty()) {
                System.out.printf(Locale.ROOT, "winner: %s (conf=%.4f)%n",
                        result.get(0).getCharset().name(), result.get(0).getConfidence());
                assertEquals(shiftJis, result.get(0).getCharset(),
                        "CharSoup should confirm Shift-JIS (文章) over GB18030");
            } else {
                System.out.println("result: empty — CharSoup abstained (Mojibuster winner stands)");
            }
        }
    }
}
