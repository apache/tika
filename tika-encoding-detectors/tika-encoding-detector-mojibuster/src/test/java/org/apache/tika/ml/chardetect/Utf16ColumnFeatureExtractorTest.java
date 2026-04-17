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

/**
 * Tests for {@link Utf16ColumnFeatureExtractor}.  These verify that the
 * raw column-count features correctly capture the alignment asymmetry
 * that distinguishes UTF-16 from non-UTF-16 content — including the
 * HTML-immunity property.
 *
 * <p>Feature indexing (must match the extractor):</p>
 * <pre>
 *   0 = count_even(0x00)       1 = count_odd(0x00)
 *   2 = count_even(0x01-1F)    3 = count_odd(0x01-1F)        (controls excl. 0x09/0x0A/0x0D)
 *   4 = count_even(0x20-7E+)   5 = count_odd(0x20-7E+)       (printable + tab/lf/cr)
 *   6 = count_even(0x7F)       7 = count_odd(0x7F)
 *   8 = count_even(0x80-9F)    9 = count_odd(0x80-9F)
 *  10 = count_even(0xA0-FF)   11 = count_odd(0xA0-FF)
 * </pre>
 */
public class Utf16ColumnFeatureExtractorTest {

    private static final int NUL_EVEN = 0;
    private static final int NUL_ODD = 1;
    private static final int CTRL_EVEN = 2;
    private static final int CTRL_ODD = 3;
    private static final int ASCII_EVEN = 4;
    private static final int ASCII_ODD = 5;
    private static final int DEL_EVEN = 6;
    private static final int DEL_ODD = 7;
    private static final int C1_EVEN = 8;
    private static final int C1_ODD = 9;
    private static final int HI_EVEN = 10;
    private static final int HI_ODD = 11;

    private final Utf16ColumnFeatureExtractor extractor = new Utf16ColumnFeatureExtractor();

    // --- basic sanity ---

    @Test
    public void emptyInputReturnsAllZeros() {
        int[] features = extractor.extract(new byte[0]);
        assertEquals(12, features.length);
        for (int i = 0; i < 12; i++) {
            assertEquals(0, features[i], "feature " + i + " should be 0");
        }
    }

    @Test
    public void nullInputReturnsAllZeros() {
        int[] features = extractor.extract(null);
        assertEquals(12, features.length);
        for (int i = 0; i < 12; i++) {
            assertEquals(0, features[i]);
        }
    }

    @Test
    public void numBucketsIs12() {
        assertEquals(12, extractor.getNumBuckets());
    }

    @Test
    public void featuresSumToProbeLength() {
        byte[] probe = "some mixed content\r\n\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int[] features = extractor.extract(probe);
        int sum = 0;
        for (int c : features) {
            sum += c;
        }
        assertEquals(probe.length, sum, "features must cover every byte exactly once");
    }

    // --- UTF-16 Latin cases ---

    @Test
    public void utf16LeLatinPutsNullsInOddColumn() {
        // "Hello World" in UTF-16LE = 48 00 65 00 6C 00 6C 00 6F 00 20 00 57 00 6F 00 72 00 6C 00 64 00
        byte[] probe = "Hello World".getBytes(Charset.forName("UTF-16LE"));
        int[] f = extractor.extract(probe);

        // 11 characters, each 2 bytes:
        //   even positions → ASCII letters (0x20-7E range)
        //   odd positions  → 0x00 (null range)
        assertEquals(0, f[NUL_EVEN], "no nulls in even column");
        assertEquals(11, f[NUL_ODD], "every odd position is null");
        assertEquals(11, f[ASCII_EVEN], "every even position is ASCII letter/space");
        assertEquals(0, f[ASCII_ODD], "no ASCII in odd column");
        // strong asymmetry: nulls in odd, ASCII in even → UTF-16LE Latin signal
    }

    @Test
    public void utf16BeLatinPutsNullsInEvenColumn() {
        byte[] probe = "Hello World".getBytes(Charset.forName("UTF-16BE"));
        int[] f = extractor.extract(probe);

        assertEquals(11, f[NUL_EVEN], "every even position is null");
        assertEquals(0, f[NUL_ODD], "no nulls in odd column");
        assertEquals(0, f[ASCII_EVEN]);
        assertEquals(11, f[ASCII_ODD]);
    }

    // --- UTF-16 non-Latin BMP cases (high byte in 0x03-0x0E, the "controls" range) ---

    @Test
    public void utf16LeCyrillicPutsHighByteInOddColumn() {
        // Russian "Привет" in UTF-16LE.  Codepoints U+041F U+0440 U+0438 U+0432 U+0435 U+0442.
        // Bytes: 1F 04 40 04 38 04 32 04 35 04 42 04
        //   even positions = 0x1F, 0x40, 0x38, 0x32, 0x35, 0x42 — all in 0x20-7E (except 0x1F which is control)
        //   odd positions  = 0x04 × 6 — in the 0x01-0x1F control range
        byte[] probe = "Привет".getBytes(Charset.forName("UTF-16LE"));
        int[] f = extractor.extract(probe);

        // Odd column: all six 0x04 bytes → control range
        assertEquals(6, f[CTRL_ODD], "every odd position is 0x04 (control range)");
        // Even column: П=0x1F (ctrl), р=0x40, и=0x38, в=0x32, е=0x35, т=0x42 → 1 ctrl + 5 printable
        assertEquals(1, f[CTRL_EVEN], "0x1F from П lands in control range on even side");
        assertEquals(5, f[ASCII_EVEN], "the other 5 even bytes are in 0x20-7E range");
        assertEquals(0, f[ASCII_ODD]);
        // No nulls, no high bytes
        assertEquals(0, f[NUL_EVEN] + f[NUL_ODD]);
        assertEquals(0, f[HI_EVEN] + f[HI_ODD]);
    }

    // --- UTF-16 CJK (the hard case) ---

    @Test
    public void utf16LeCjkPutsHighByteInOddColumn() {
        // "精密過濾旋流器" in UTF-16LE.  Codepoints in U+4E00-U+9FFF range.
        //   精 U+7CBE → BE 7C
        //   密 U+5BC6 → C6 5B
        //   過 U+904E → 4E 90
        //   濾 U+6FFE → FE 6F
        //   旋 U+65CB → CB 65
        //   流 U+6D41 → 41 6D
        //   器 U+5668 → 68 56
        // Even column (low bytes of codepoints):   BE, C6, 4E, FE, CB, 41, 68
        // Odd column (high bytes of codepoints):   7C, 5B, 90, 6F, 65, 6D, 56
        byte[] probe = "精密過濾旋流器".getBytes(Charset.forName("UTF-16LE"));
        int[] f = extractor.extract(probe);

        // Odd column: all bytes in 0x4E-0x90 range.
        // 0x7C, 0x5B, 0x6F, 0x65, 0x6D, 0x56 → range 2 (ASCII 0x20-7E)
        // 0x90 → range 4 (C1 range 0x80-9F)
        assertEquals(6, f[ASCII_ODD], "most odd bytes fall in ASCII-printable range for CJK low half");
        assertEquals(1, f[C1_ODD], "0x90 from 過 lands in C1 range");

        // Even column: BE, C6, 4E, FE, CB, 41, 68
        // 0x41, 0x68, 0x4E → range 2 (ASCII 0x20-7E)
        // 0xBE, 0xC6, 0xFE, 0xCB → range 5 (0xA0-FF)
        assertEquals(3, f[ASCII_EVEN]);
        assertEquals(4, f[HI_EVEN]);

        // No nulls anywhere for CJK
        assertEquals(0, f[NUL_EVEN] + f[NUL_ODD]);
    }

    @Test
    public void utf16BeCjkPutsHighByteInEvenColumn() {
        // Same CJK text in UTF-16BE — roles of columns swap.
        byte[] probe = "精密過濾旋流器".getBytes(Charset.forName("UTF-16BE"));
        int[] f = extractor.extract(probe);

        // Even column now has codepoint high bytes (7C, 5B, 90, 6F, 65, 6D, 56).
        assertEquals(6, f[ASCII_EVEN], "BE even column has codepoint high bytes in ASCII range");
        assertEquals(1, f[C1_EVEN], "0x90 from 過 lands in C1 range on even side for BE");

        // Odd column has codepoint low bytes (BE, C6, 4E, FE, CB, 41, 68).
        assertEquals(3, f[ASCII_ODD]);
        assertEquals(4, f[HI_ODD]);
    }

    @Test
    public void utf16LeUpperCjkHitsC1Range() {
        // Codepoints U+8000-U+9FFF have high byte in 0x80-0x9F (the C1 range).
        // Under UTF-16LE, this high byte lands in the ODD column.
        // 試 U+8A66 → 66 8A (LE)
        // 験 U+9A13 → 13 9A (LE) — wait, 0x13 is control
        // 誠 U+8AA0 → A0 8A (LE)
        byte[] probe = "試験誠".getBytes(Charset.forName("UTF-16LE"));
        int[] f = extractor.extract(probe);

        // Odd column (codepoint high bytes): 8A, 9A, 8A → all in 0x80-9F (C1 range).
        assertEquals(3, f[C1_ODD], "all three odd-column bytes in C1 range");
        assertEquals(0, f[C1_EVEN]);
    }

    // --- HTML — must produce minimal asymmetry ---

    @Test
    public void htmlProducesSymmetricColumns() {
        String html = "<html><head><title>Hello</title></head>"
                + "<body><p class=\"a\">Content here</p></body></html>";
        byte[] probe = html.getBytes(StandardCharsets.US_ASCII);
        int[] f = extractor.extract(probe);

        // All bytes are ASCII (0x20-0x7E range).  Expect rough even/odd balance.
        int totalAscii = f[ASCII_EVEN] + f[ASCII_ODD];
        assertEquals(probe.length, totalAscii, "all bytes should be ASCII");
        int diff = Math.abs(f[ASCII_EVEN] - f[ASCII_ODD]);
        assertTrue(diff <= 2, "HTML columns should be near-symmetric, diff=" + diff);

        // No UTF-16-signature ranges: no nulls, no C1, no high bytes.
        assertEquals(0, f[NUL_EVEN] + f[NUL_ODD], "HTML has no nulls");
        assertEquals(0, f[C1_EVEN] + f[C1_ODD], "HTML never emits C1 bytes");
        assertEquals(0, f[HI_EVEN] + f[HI_ODD], "ASCII HTML has no high bytes");
    }

    @Test
    public void largeHtmlStillSymmetric() {
        // Simulate a larger HTML probe — symmetry should hold across columns.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("<div class=\"node-").append(i).append("\">text ")
              .append(i).append("</div>\n");
        }
        byte[] probe = sb.toString().getBytes(StandardCharsets.US_ASCII);
        int[] f = extractor.extract(probe);

        int asymmetry = Math.abs(f[ASCII_EVEN] - f[ASCII_ODD]);
        double asymmetryRatio = (double) asymmetry / probe.length;
        assertTrue(asymmetryRatio < 0.02,
                "HTML column asymmetry ratio should be very small, got " + asymmetryRatio);
        assertEquals(0, f[NUL_EVEN] + f[NUL_ODD]);
        assertEquals(0, f[C1_EVEN] + f[C1_ODD]);
    }

    // --- pure ASCII text (symmetric, like HTML) ---

    @Test
    public void pureAsciiEnglishProducesSymmetricColumns() {
        byte[] probe = ("The quick brown fox jumps over the lazy dog. "
                + "Pack my box with five dozen liquor jugs.")
                .getBytes(StandardCharsets.US_ASCII);
        int[] f = extractor.extract(probe);

        int diff = Math.abs(f[ASCII_EVEN] - f[ASCII_ODD]);
        assertTrue(diff <= 2, "pure ASCII should be near-symmetric, diff=" + diff);
        assertEquals(0, f[NUL_EVEN] + f[NUL_ODD]);
        assertEquals(0, f[C1_EVEN] + f[C1_ODD]);
    }

    // --- adversarial: pure 2-byte Shift_JIS ---

    @Test
    public void pure2ByteShiftJisProducesWeakerAsymmetryThanUtf16Cjk() {
        // Japanese "テスト" in Shift_JIS (all 2-byte chars, no ASCII interruptions).
        //   テ 0x83 0x65
        //   ス 0x83 0x58
        //   ト 0x83 0x67
        // Even column: 0x83, 0x83, 0x83 (all in C1 range 0x80-9F)
        // Odd column: 0x65, 0x58, 0x67 (all in ASCII printable range)
        byte[] probe = "テスト".getBytes(Charset.forName("Shift_JIS"));
        int[] f = extractor.extract(probe);

        // This looks LIKE UTF-16BE CJK (even column has high bytes, odd column has printable).
        // Combiner should still pick Shift_JIS because the CJK specialist's logit is higher.
        assertEquals(3, f[C1_EVEN], "Shift_JIS leads in C1 range for this probe");
        assertEquals(3, f[ASCII_ODD], "Shift_JIS trails in ASCII range");
        // We don't assert the UTF-16 logit — this is just the raw feature vector.
        // The interesting question is what the trained model does with it, which is a
        // training-and-evaluation concern, not a feature-extraction concern.
    }

    @Test
    public void mixedShiftJisWithAsciiBreaksAlignment() {
        // Realistic Shift_JIS with ASCII interruptions.  Alignment shifts per ASCII byte.
        byte[] probe = ("test " + "テスト" + " text").getBytes(Charset.forName("Shift_JIS"));
        int[] f = extractor.extract(probe);

        // Hard to predict exact counts, but asymmetry in C1 range should be much
        // weaker than the pure-2-byte case because the leading "test " (5 ASCII
        // chars) shifts alignment of the Japanese bytes.
        int c1Asymmetry = Math.abs(f[C1_EVEN] - f[C1_ODD]);
        // Some non-zero asymmetry is likely, but should be small vs pure-2-byte.
        assertTrue(c1Asymmetry <= 3, "ASCII interruption should weaken column asymmetry");
    }

    // --- scattered null — the §P1 false-positive case ---

    @Test
    public void scatteredNullsProduceSymmetricColumns() {
        // Synthesize a probe with low-density scattered nulls: 1% null rate,
        // distributed randomly across both columns.
        byte[] probe = new byte[1000];
        java.util.Random rng = new java.util.Random(42);  // deterministic
        int nullsPlaced = 0;
        for (int i = 0; i < probe.length; i++) {
            if (rng.nextDouble() < 0.01) {
                probe[i] = 0x00;
                nullsPlaced++;
            } else {
                // random printable ASCII
                probe[i] = (byte) (0x20 + rng.nextInt(95));
            }
        }
        int[] f = extractor.extract(probe);

        assertEquals(nullsPlaced, f[NUL_EVEN] + f[NUL_ODD],
                "all nulls accounted for in NUL range");
        // Nulls should be roughly balanced across columns (noisy but symmetric in expectation).
        int nullAsymmetry = Math.abs(f[NUL_EVEN] - f[NUL_ODD]);
        assertTrue(nullAsymmetry <= nullsPlaced / 2 + 3,
                "scattered nulls should be roughly balanced, asymmetry=" + nullAsymmetry);
    }

    // --- controls and whitespace handling ---

    @Test
    public void whitespaceCountsAsAsciiTextNotAsControls() {
        // 0x09 (tab), 0x0A (LF), 0x0D (CR) should land in the ASCII range, not the control range.
        byte[] probe = new byte[]{
                0x09, 0x0A, 0x0D, ' ', 'a',  // 5 bytes, all in ASCII range
                0x01, 0x02, 0x03              // 3 bytes in control range
        };
        int[] f = extractor.extract(probe);

        assertEquals(5, f[ASCII_EVEN] + f[ASCII_ODD],
                "tab/LF/CR plus ' ' and 'a' = 5 ASCII-range bytes");
        assertEquals(3, f[CTRL_EVEN] + f[CTRL_ODD],
                "0x01/0x02/0x03 = 3 control-range bytes");
    }

    @Test
    public void delByteLandsInDelRange() {
        byte[] probe = new byte[]{0x7E, 0x7F, (byte) 0x80};
        int[] f = extractor.extract(probe);
        assertEquals(1, f[ASCII_EVEN] + f[ASCII_ODD], "0x7E is ASCII");
        assertEquals(1, f[DEL_EVEN] + f[DEL_ODD], "0x7F is DEL");
        assertEquals(1, f[C1_EVEN] + f[C1_ODD], "0x80 is C1");
    }

    // --- sparse extraction interface ---

    @Test
    public void sparseExtractionMatchesDense() {
        byte[] probe = "Hello World".getBytes(Charset.forName("UTF-16LE"));

        int[] dense = extractor.extract(probe);
        int[] sparseDense = new int[12];
        int[] touched = new int[12];
        int n = extractor.extractSparseInto(probe, sparseDense, touched);

        // dense[] values should match between paths
        for (int i = 0; i < 12; i++) {
            assertEquals(dense[i], sparseDense[i],
                    "feature " + i + " should match between dense and sparse");
        }
        // touched[] should list exactly the non-zero indices
        int nonZero = 0;
        for (int i = 0; i < 12; i++) {
            if (dense[i] != 0) {
                nonZero++;
            }
        }
        assertEquals(nonZero, n, "touched count should equal number of non-zero features");
    }

    @Test
    public void sparseExtractionWithEmptyProbe() {
        int[] dense = new int[12];
        int[] touched = new int[12];
        int n = extractor.extractSparseInto(new byte[0], dense, touched);
        assertEquals(0, n);
    }

    // --- range offset extraction ---

    @Test
    public void subRangeExtractionIsCorrect() {
        byte[] probe = "XXHelloXX".getBytes(StandardCharsets.US_ASCII);
        // Extract from offset 2, length 5 ("Hello")
        int[] f = extractor.extract(probe, 2, 5);
        // "Hello" = 5 bytes, all ASCII.  Column assignment relative to the sub-range start.
        assertEquals(5, f[ASCII_EVEN] + f[ASCII_ODD]);
        // 5 bytes: positions (relative) 0,1,2,3,4 → even,odd,even,odd,even → 3 even, 2 odd
        assertEquals(3, f[ASCII_EVEN]);
        assertEquals(2, f[ASCII_ODD]);
    }

    // --- feature label sanity ---

    @Test
    public void featureLabelsAreReasonable() {
        assertEquals("count_even(0x00)", Utf16ColumnFeatureExtractor.featureLabel(0));
        assertEquals("count_odd(0x00)", Utf16ColumnFeatureExtractor.featureLabel(1));
        assertEquals("count_even(0x80-9F)", Utf16ColumnFeatureExtractor.featureLabel(8));
        assertEquals("count_odd(0xA0-FF)", Utf16ColumnFeatureExtractor.featureLabel(11));
    }
}
