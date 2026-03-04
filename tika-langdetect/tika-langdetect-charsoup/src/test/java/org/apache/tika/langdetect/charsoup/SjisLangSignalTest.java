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

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.langdetect.opennlp.OpenNLPDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * Deep-dive: do language detectors give a stronger Japanese signal for the
 * correct Shift-JIS decoding of a zip entry name than for plausible single-byte
 * alternatives?
 *
 * Raw bytes (testZipEntryNameCharsetShiftSJIS.zip, entry "文章1.txt"):
 *   0x95 0xb6 0x8f 0xcd 0x31 0x2e 0x74 0x78 0x74
 *
 * Shift_JIS  →  文章1.txt   (correct)
 * windows-1257 →  •¶¸Ķ1.txt  (plausible single-byte alternative)
 *
 * <h2>Findings (Mar 2026)</h2>
 * <p>
 * At 9 raw bytes, both CharSoup and OpenNLP return pure noise — all scores are
 * NONE confidence (~0.007). The detected language at this length is entirely a
 * hash-collision artifact: only 4 letters survive the isLetter() filter
 * (Ķ, t, x, t from windows-1257; 文, 章, t, x, t from Shift-JIS), producing
 * 6-7 bigrams out of 65,536 buckets. Whichever language has the highest weights
 * in those specific buckets "wins" — no linguistic signal whatsoever.
 * </p>
 * <p>
 * Padding/tiling the bytes (to 100 or 300 bytes) makes things actively worse:
 * CharSoup becomes confidently wrong. Tiled Shift-JIS ("文章文章…") is detected as
 * Chinese (zho, maxLogit = -8 at 100 bytes, -29 at 300 bytes) because:
 * (a) 文章 IS valid Chinese text, and (b) the repetition creates extreme bigram
 * entropy that the model interprets as non-language content, collapsing the logit.
 * Meanwhile windows-1250 ("•¶ŹÍ…") scores maxLogit = +9 to +26 as Catalan
 * from the repeated Latin diacritics. Language detection is fundamentally
 * unsuitable for short charset arbitration.
 * </p>
 *
 * <h2>Historical context: why ICU4J and Universal needed padding</h2>
 * <p>
 * ICU4J (CharsetRecog_sjis) uses double-byte sequence counting and a common-char
 * frequency table. At 9 bytes it sees only 2 double-byte chars (≤ 10 threshold)
 * and returns confidence=10 — too low to win. Even with tiling, 文章 (0x95B6,
 * 0x8FCD) are NOT in ICU4J's commonChars list (which contains hiragana/katakana),
 * so commonCharCount=0 and confidence stays at 10 regardless. ICU4J fails at
 * both raw and padded lengths for this specific pair of kanji.
 * </p>
 * <p>
 * Universal (juniversalchardet) uses byte n-gram frequency profiles. At 9 bytes
 * the sample is too small to build a reliable distribution. At 100 bytes (~11
 * repetitions of the same 9 bytes), the n-gram frequency profile becomes large
 * enough that Universal correctly identifies Shift-JIS. This is why the original
 * ZipParser tiled short byte arrays to 100 bytes before calling the encoding
 * detector — it was load-bearing for Universal, but a hack that confused
 * Mojibuster's byte n-gram model with artificial repetition patterns.
 * </p>
 * <p>
 * The correct solution (implemented) is structural CJK grammar validation in
 * MojibusterEncodingDetector.refineCjkResults: 0x95B6 and 0x8FCD parse as
 * exactly 2 valid Shift-JIS double-byte sequences with zero parse errors, which
 * is conclusive structural evidence independent of sample size or language models.
 * See also: ZipFilenameDetectionTest in tika-encoding-detector-mojibuster.
 * </p>
 */
public class SjisLangSignalTest {

    // Raw bytes for "文章1.txt" encoded as Shift-JIS
    private static final byte[] RAW =
            {(byte) 0x95, (byte) 0xb6, (byte) 0x8f, (byte) 0xcd,
             0x31, 0x2e, 0x74, 0x78, 0x74};

    private static final String[] CHARSETS = {
            "Shift_JIS", "windows-1257", "GB18030", "EUC-JP",
            "windows-1252", "windows-1250", "ISO-8859-2"
    };

    @Disabled("Diagnostic only — run manually to evaluate a new language detector against SJIS zip filenames")
    @Test
    public void diagnoseLanguageSignals() throws Exception {
        System.out.println("=== Language detector signals for SJIS zip entry name ===");
        System.out.printf(Locale.ROOT, "Raw bytes (%d): %s%n%n", RAW.length, hexDump(RAW));

        for (int padTo : new int[]{RAW.length, 100, 300}) {
            byte[] probe = tile(RAW, padTo);
            System.out.printf(Locale.ROOT,
                    "======== probe length = %d bytes (tiled x%.1f) ========%n",
                    probe.length, (double) probe.length / RAW.length);

            Map<Charset, String> candidates = new LinkedHashMap<>();
            for (String name : CHARSETS) {
                try {
                    Charset cs = Charset.forName(name);
                    candidates.put(cs, new String(probe, cs));
                } catch (Exception e) {
                    // unsupported charset, skip
                }
            }

            System.out.printf(Locale.ROOT, "%-20s  %-35s  %-9s%n",
                    "Charset", "Decoded (first 30 chars)", "JunkRatio");
            System.out.println("-".repeat(70));
            for (Map.Entry<Charset, String> e : candidates.entrySet()) {
                String preview = e.getValue().length() > 30
                        ? e.getValue().substring(0, 30) + "…" : e.getValue();
                System.out.printf(Locale.ROOT, "%-20s  %-35s  %.3f%n",
                        e.getKey().name(), preview, computeJunkRatio(e.getValue()));
            }
            System.out.println();

            System.out.println("  CharSoup logits (raw max logit, sigmoid, top-lang):");
            for (Map.Entry<Charset, String> e : candidates.entrySet()) {
                if (computeJunkRatio(e.getValue()) > 0.10f) {
                    System.out.printf(Locale.ROOT, "    %-20s: skipped (junk > 10%%)%n",
                            e.getKey().name());
                    continue;
                }
                float[] info = CharSoupLanguageDetector.maxLogitInfo(e.getValue());
                String topLang = CharSoupLanguageDetector.labelAt((int) info[0]);
                System.out.printf(Locale.ROOT,
                        "    %-20s: maxLogit=%7.3f  sigmoid=%7.4f  topLang=%s%n",
                        e.getKey().name(), info[1], info[2], topLang);
            }
            System.out.println();

            System.out.println("  OpenNLP top-3:");
            for (Map.Entry<Charset, String> e : candidates.entrySet()) {
                OpenNLPDetector d = new OpenNLPDetector();
                d.addText(e.getValue().toCharArray(), 0, e.getValue().length());
                List<LanguageResult> r = d.detectAll();
                System.out.printf(Locale.ROOT, "    %-20s: %s%n",
                        e.getKey().name(), r.subList(0, Math.min(3, r.size())));
            }
            System.out.println();

            CharSoupLanguageDetector csd = new CharSoupLanguageDetector();
            Charset winner = csd.compareLanguageSignal(candidates);
            System.out.printf(Locale.ROOT,
                    "  compareLanguageSignal winner: %s%n%n",
                    winner != null ? winner + " -> \"" + candidates.get(winner) + "\"" :
                            "(inconclusive — all below threshold)");
        }
    }

    private static byte[] tile(byte[] src, int targetLen) {
        if (src.length >= targetLen) {
            return src;
        }
        byte[] out = new byte[targetLen];
        for (int i = 0; i < targetLen; i++) {
            out[i] = src[i % src.length];
        }
        return out;
    }

    private static float computeJunkRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0f;
        }
        int junk = 0, total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            total++;
            if (cp == 0xFFFD || Character.isISOControl(cp)) {
                junk++;
            }
        }
        return total == 0 ? 0f : (float) junk / total;
    }

    private static String hexDump(byte[] bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "0x%02x", bytes[i] & 0xff));
        }
        return sb.append("]").toString();
    }
}
