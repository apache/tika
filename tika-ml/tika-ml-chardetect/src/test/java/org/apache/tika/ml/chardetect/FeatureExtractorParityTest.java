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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.ml.chardetect.tools.ConfigurableByteNgramFeatureExtractor;

/**
 * Verifies that the production {@link ByteNgramFeatureExtractor} and the
 * training-time {@link ConfigurableByteNgramFeatureExtractor} produce
 * identical feature vectors when configured with matching flags.
 *
 * <p>Training flags that match the production extractor:
 * {@code --no-tri} (trigrams off, which is the default-on flag turned off),
 * default {@code --no-anchored}, default {@code --stride2}.</p>
 *
 * <p>Also verifies that {@code extract()} and {@code extractSparseInto()}
 * agree within each extractor, since training uses the sparse path and
 * eval/inference uses the dense path.</p>
 */
public class FeatureExtractorParityTest {

    private static final int NUM_BUCKETS = 16384;

    private final ByteNgramFeatureExtractor production =
            new ByteNgramFeatureExtractor(NUM_BUCKETS);

    private final ConfigurableByteNgramFeatureExtractor configurable =
            new ConfigurableByteNgramFeatureExtractor(NUM_BUCKETS,
                    true,   // unigrams
                    true,   // bigrams
                    false,  // trigrams OFF  (--no-tri)
                    false,  // anchored OFF  (default)
                    true);  // stride2  ON   (default)

    // --- Cross-extractor parity: production.extract == configurable.extract ---

    @Test
    public void parityOnPureAscii() {
        assertParity("Hello, world! This is ASCII text.\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    public void parityOnHighByteContent() {
        // windows-1252 French: "résumé café"
        assertParity(new byte[]{
                (byte) 0x72, (byte) 0xE9, (byte) 0x73, (byte) 0x75,
                (byte) 0x6D, (byte) 0xE9, (byte) 0x20,
                (byte) 0x63, (byte) 0x61, (byte) 0x66, (byte) 0xE9
        });
    }

    @Test
    public void parityOnShiftJis() {
        // Shift-JIS: lead 0x82, trail in 0x40-0x7E range
        assertParity(new byte[]{
                (byte) 0x82, (byte) 0x42, (byte) 0x82, (byte) 0x60,
                (byte) 0x83, (byte) 0x41, (byte) 0x83, (byte) 0x5E
        });
    }

    @Test
    public void parityOnUtf16Le() {
        // "ABCé" in UTF-16LE: 41 00 42 00 43 00 E9 00
        assertParity(new byte[]{
                (byte) 0x41, (byte) 0x00, (byte) 0x42, (byte) 0x00,
                (byte) 0x43, (byte) 0x00, (byte) 0xE9, (byte) 0x00
        });
    }

    @Test
    public void parityOnUtf16Be() {
        // "ABCé" in UTF-16BE: 00 41 00 42 00 43 00 E9
        assertParity(new byte[]{
                (byte) 0x00, (byte) 0x41, (byte) 0x00, (byte) 0x42,
                (byte) 0x00, (byte) 0x43, (byte) 0x00, (byte) 0xE9
        });
    }

    @Test
    public void parityOnUtf32Le() {
        // "AB" in UTF-32LE: 41 00 00 00 42 00 00 00
        assertParity(new byte[]{
                (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x42, (byte) 0x00, (byte) 0x00, (byte) 0x00
        });
    }

    @Test
    public void parityOnUtf32Be() {
        // "AB" in UTF-32BE: 00 00 00 41 00 00 00 42
        assertParity(new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x41,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x42
        });
    }

    @Test
    public void parityOnUtf32LeNonAscii() {
        // U+0E01 (Thai ก) in UTF-32LE: 01 0E 00 00
        // U+0E02 (Thai ข) in UTF-32LE: 02 0E 00 00
        assertParity(new byte[]{
                (byte) 0x01, (byte) 0x0E, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x0E, (byte) 0x00, (byte) 0x00
        });
    }

    @Test
    public void parityOnUtf32BeNonAscii() {
        // U+0E01 in UTF-32BE: 00 00 0E 01
        // U+0E02 in UTF-32BE: 00 00 0E 02
        assertParity(new byte[]{
                (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0x01,
                (byte) 0x00, (byte) 0x00, (byte) 0x0E, (byte) 0x02
        });
    }

    @Test
    public void parityOnDenseHighBytes() {
        // All high bytes: typical of KOI8-R or similar
        byte[] dense = new byte[64];
        for (int i = 0; i < dense.length; i++) {
            dense[i] = (byte) (0xC0 + (i % 64));
        }
        assertParity(dense);
    }

    @Test
    public void parityOnSingleByte() {
        assertParity(new byte[]{(byte) 0xE0});
    }

    @Test
    public void parityOnTwoBytes() {
        assertParity(new byte[]{(byte) 0xE0, (byte) 0xE1});
    }

    @Test
    public void parityOnEmpty() {
        assertParity(new byte[0]);
    }

    @Test
    public void parityOnRealUtf16Le() {
        // Encode actual Unicode text as UTF-16LE to get a realistic probe
        String text = "日本語テスト";  // Japanese
        assertParity(text.getBytes(StandardCharsets.UTF_16LE));
    }

    @Test
    public void parityOnRealUtf16Be() {
        String text = "日本語テスト";
        assertParity(text.getBytes(StandardCharsets.UTF_16BE));
    }

    @Test
    public void parityOnRealUtf32() {
        // UTF-32 via Charset.forName
        Charset utf32le = Charset.forName("UTF-32LE");
        Charset utf32be = Charset.forName("UTF-32BE");
        String text = "Hello世界";
        assertParity(text.getBytes(utf32le));
        assertParity(text.getBytes(utf32be));
    }

    @Test
    public void parityOnLongProbe() {
        // 4096-byte probe mixing ASCII and high bytes
        byte[] probe = new byte[4096];
        for (int i = 0; i < probe.length; i++) {
            probe[i] = (byte) ((i % 3 == 0) ? (0x80 + (i % 128)) : (0x20 + (i % 96)));
        }
        assertParity(probe);
    }

    // --- Internal consistency: extract() == extractSparseInto() within each extractor ---

    @Test
    public void productionDenseMatchesSparse() {
        String text = "日本語テスト résumé";
        byte[] probe = text.getBytes(StandardCharsets.UTF_16LE);
        assertDenseSparseMatch(production, probe);
    }

    @Test
    public void configurableDenseMatchesSparse() {
        String text = "日本語テスト résumé";
        byte[] probe = text.getBytes(StandardCharsets.UTF_16LE);

        int[] dense = configurable.extract(probe);
        int[] sparseDense = new int[NUM_BUCKETS];
        int[] touched = new int[NUM_BUCKETS];
        int n = configurable.extractSparseInto(probe, sparseDense, touched);

        assertArrayEquals(dense, sparseDense,
                "ConfigurableByteNgramFeatureExtractor: extract() vs extractSparseInto() differ");
    }

    // --- Helpers ---

    private void assertParity(byte[] probe) {
        int[] prodFeatures = production.extract(probe);
        int[] confFeatures = configurable.extract(probe);

        assertEquals(prodFeatures.length, confFeatures.length,
                "Feature vector lengths differ");

        // Find first mismatch for a useful error message
        for (int i = 0; i < prodFeatures.length; i++) {
            if (prodFeatures[i] != confFeatures[i]) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(
                        "Bucket %d: production=%d, configurable=%d. Probe (%d bytes): [",
                        i, prodFeatures[i], confFeatures[i], probe.length));
                int show = Math.min(probe.length, 32);
                for (int j = 0; j < show; j++) {
                    if (j > 0) sb.append(' ');
                    sb.append(String.format("%02X", probe[j] & 0xFF));
                }
                if (probe.length > show) sb.append(" ...");
                sb.append(']');
                org.junit.jupiter.api.Assertions.fail(sb.toString());
            }
        }
    }

    private void assertDenseSparseMatch(ByteNgramFeatureExtractor ext, byte[] probe) {
        int[] dense = ext.extract(probe);
        int[] sparseDense = new int[NUM_BUCKETS];
        int[] touched = new int[NUM_BUCKETS];
        int n = ext.extractSparseInto(probe, sparseDense, touched);

        assertArrayEquals(dense, sparseDense,
                "ByteNgramFeatureExtractor: extract() vs extractSparseInto() differ");
    }
}
