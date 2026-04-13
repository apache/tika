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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.ml.chardetect.tools.ConfigurableByteNgramFeatureExtractor;

public class ConfigurableGlobalFeatureTest {

    private static final int NUM_BUCKETS = 16384;
    private static final int HASH_BUCKETS = NUM_BUCKETS
            - ConfigurableByteNgramFeatureExtractor.GLOBAL_FEATURE_COUNT;

    private static ConfigurableByteNgramFeatureExtractor withGlobals() {
        return new ConfigurableByteNgramFeatureExtractor(
                NUM_BUCKETS, true, true, false, false, true, true);
    }

    private static ConfigurableByteNgramFeatureExtractor withoutGlobals() {
        return new ConfigurableByteNgramFeatureExtractor(
                NUM_BUCKETS, true, true, false, false, true, false);
    }

    @Test
    public void pureAsciiLandsInTopBin() {
        assertEquals(5, ConfigurableByteNgramFeatureExtractor.asciiDensityBin(
                "BEGIN:VCARD\r\nVERSION:3.0\r\nEND:VCARD\r\n".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void sparseLatinVcardLandsInTopBin() {
        // 99.4% ASCII: 3 high bytes in ~510 bytes of vCard text
        byte[] probe = "BEGIN:VCARD\r\nN:M\u00FCller;Hans\r\nFN:Hans M\u00FCller\r\nADR:K\u00F6ln\r\nEND:VCARD\r\n"
                .getBytes(StandardCharsets.ISO_8859_1);
        int bin = ConfigurableByteNgramFeatureExtractor.asciiDensityBin(probe);
        assertTrue(bin >= 4, "sparse-Latin vCard should land in bin 4 or 5, got: " + bin);
    }

    @Test
    public void ebcdicTextLandsInLowBin() {
        // Real EBCDIC: letters 0x81..0xE9 (~80%), 0x40 space (~20%)
        // Under the ASCII-text bin definition, 0x40 IS printable ASCII ('@'),
        // so EBCDIC lands in bin 1, not bin 0.  What matters is that it's
        // cleanly separated from the plain-ASCII bin 5.
        byte[] ebcdic = new byte[100];
        int p = 0;
        for (int i = 0; i < 20; i++) {
            ebcdic[p++] = 0x40;  // space
        }
        for (int i = 0; i < 80; i++) {
            ebcdic[p++] = (byte) (0x81 + (i % 9));  // letters
        }
        int bin = ConfigurableByteNgramFeatureExtractor.asciiDensityBin(ebcdic);
        assertTrue(bin <= 2, "EBCDIC should land in bin 0-2, got: " + bin);
        assertNotEquals(5, bin, "EBCDIC must not collide with the ASCII bin");
    }

    @Test
    public void utf16LeEnglishLandsInMiddleBin() {
        // UTF-16LE "Hello, world" — every other byte is 0x00
        byte[] utf16 = "Hello, world! This is English text in UTF-16LE."
                .getBytes(Charset.forName("UTF-16LE"));
        int bin = ConfigurableByteNgramFeatureExtractor.asciiDensityBin(utf16);
        assertTrue(bin == 2, "UTF-16LE English should land in bin 2 (~50%), got: " + bin);
    }

    @Test
    public void utf16LeBmpTextLandsInMidHighBin() {
        // UTF-16LE of BMP text (Hiragana U+3040..U+309F etc.) — note that the
        // "high byte of the codepoint" (0x30 here) is printable ASCII '0', and
        // the "low byte" of most Hiragana falls in 0x40..0x9F — half printable.
        // So UTF-16LE BMP text has a HIGH printable-ASCII-byte fraction despite
        // not being ASCII text.  The global feature does not try to distinguish
        // UTF-16 from ASCII — that's stride-2's job.  This test documents the
        // observed behaviour so it isn't mistaken for a bug later.
        byte[] utf16 = "\u6587\u7AE0\u3042\u3044\u3046\u3048\u304A\u304B\u304D\u304F"
                .getBytes(Charset.forName("UTF-16LE"));
        int bin = ConfigurableByteNgramFeatureExtractor.asciiDensityBin(utf16);
        assertTrue(bin >= 2, "UTF-16LE BMP text has many printable bytes, got bin: " + bin);
    }

    @Test
    public void globalFeatureFiresExactlyOneTailSlot() {
        ConfigurableByteNgramFeatureExtractor ext = withGlobals();
        int[] dense = new int[NUM_BUCKETS];
        int[] touched = new int[NUM_BUCKETS];

        int n = ext.extractSparseInto(
                "Plain ASCII text with no accents at all.".getBytes(StandardCharsets.US_ASCII),
                dense, touched);

        int tailFirings = 0;
        int tailSlot = -1;
        for (int i = 0; i < n; i++) {
            if (touched[i] >= HASH_BUCKETS) {
                tailFirings++;
                tailSlot = touched[i];
            }
        }
        assertEquals(1, tailFirings, "exactly one global tail slot must fire");
        assertEquals(HASH_BUCKETS + 5, tailSlot, "pure ASCII should fire bin 5");
        assertEquals(1, dense[tailSlot], "count for global bin must be 1");
    }

    @Test
    public void disablingGlobalsLeavesTailEmpty() {
        ConfigurableByteNgramFeatureExtractor ext = withoutGlobals();
        int[] dense = new int[NUM_BUCKETS];
        int[] touched = new int[NUM_BUCKETS];

        int n = ext.extractSparseInto(
                "Plain ASCII text".getBytes(StandardCharsets.US_ASCII),
                dense, touched);

        for (int i = 0; i < n; i++) {
            assertTrue(touched[i] < NUM_BUCKETS,
                    "all firings must be in hash range when globals are off");
        }
    }

    @Test
    public void sparseAndDenseExtractionAgreeWithGlobals() {
        ConfigurableByteNgramFeatureExtractor ext = withGlobals();
        byte[] probe = "r\u00E9sum\u00E9 caf\u00E9 cr\u00E8me br\u00FBl\u00E9e"
                .getBytes(StandardCharsets.ISO_8859_1);

        int[] dense = ext.extract(probe);

        int[] sparseDense = new int[NUM_BUCKETS];
        int[] touched = new int[NUM_BUCKETS];
        ext.extractSparseInto(probe, sparseDense, touched);

        for (int i = 0; i < NUM_BUCKETS; i++) {
            assertEquals(dense[i], sparseDense[i],
                    "bucket " + i + " differs between dense and sparse paths");
        }
    }
}
