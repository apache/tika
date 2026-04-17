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
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.LinearModel;
import org.apache.tika.parser.ParseContext;

/**
 * Verifies EBCDIC detection using the single statistical model.
 *
 * <p>EBCDIC variants (IBM420-ltr/rtl, IBM424-ltr/rtl, IBM500, IBM1047) are
 * direct labels in the general model alongside all other charsets.  IBM855 and
 * IBM866 are DOS/OEM Cyrillic code pages — not true EBCDIC — and are also
 * direct model labels.
 */
public class EbcdicRoutingTest {

    private static MojibusterEncodingDetector detector;

    // Representative English prose encoded in IBM500 (International EBCDIC).
    private static final byte[] IBM500_BYTES = encode("IBM500",
            "The quick brown fox jumps over the lazy dog. " +
            "This sentence contains every letter of the English alphabet. " +
            "EBCDIC encoding is used on IBM mainframe systems. " +
            "Fields are often fixed-width and space-padded in EBCDIC files.");

    private static byte[] encode(String charsetName, String text) {
        try {
            return text.getBytes(Charset.forName(charsetName));
        } catch (Exception e) {
            throw new RuntimeException("Cannot encode test data as " + charsetName, e);
        }
    }

    @BeforeAll
    static void setUp() throws Exception {
        detector = new MojibusterEncodingDetector();
    }

    /**
     * The general model must have direct labels for all EBCDIC variants.
     * There must be no bare "EBCDIC" routing label — that was the old two-model
     * architecture which has been replaced by a single model.
     */
    @Test
    public void generalModelHasDirectEbcdicLabels() {
        LinearModel general = detector.getModel();
        List<String> labels = Arrays.asList(general.getLabels());

        assertFalse(labels.contains("EBCDIC"),
                "Model must not have a bare 'EBCDIC' routing label (single-model architecture)");

        // True EBCDIC variants must be direct labels
        for (String ebcdic : new String[]{"IBM420-ltr", "IBM420-rtl", "IBM424-ltr", "IBM424-rtl", "IBM500"}) {
            assertTrue(labels.contains(ebcdic),
                    "EBCDIC variant must be a direct model label: " + ebcdic);
        }

        // DOS Cyrillic variants must also be direct labels
        assertTrue(labels.contains("IBM855"), "IBM855 (DOS Cyrillic) must be a direct model label");
        assertTrue(labels.contains("IBM866"), "IBM866 (DOS Cyrillic) must be a direct model label");
    }

    /**
     * IBM500 bytes must be detected as IBM500, not as a script-specific
     * EBCDIC variant.
     *
     * <p>The model can fall back to IBM424 bias for English EBCDIC content
     * because the bytes are identical in IBM500 and IBM424 for all common
     * ASCII characters — there is no byte-level discriminating signal.
     * {@link MojibusterEncodingDetector.Rule#EBCDIC_FALLBACK_IBM500} must
     * always correct this to IBM500 when the probe decodes cleanly under it.</p>
     */
    @Test
    public void ibm500IsDetectedAsIbm500() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(IBM500_BYTES)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something for IBM500 bytes");
            assertEquals("IBM500", results.get(0).getLabel(),
                    "English IBM500 prose must detect as IBM500, not as a script-specific "
                            + "EBCDIC variant (got: " + results.get(0).getLabel() + ")");
        }
    }

    /**
     * Short English EBCDIC probe.  Even with very few bytes the EBCDIC fallback
     * must override the model's IBM424 bias.
     */
    @Test
    public void shortIbm500IsDetectedAsIbm500() throws Exception {
        byte[] shortProbe = encode("IBM500", "Hello World. Test.");
        try (TikaInputStream tis = TikaInputStream.get(shortProbe)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something");
            assertEquals("IBM500", results.get(0).getLabel(),
                    "Short English IBM500 probe must detect as IBM500 (got: "
                            + results.get(0).getLabel() + ")");
        }
    }

    /**
     * Repetitive English EBCDIC — byte distribution is dominated by a few
     * letter values, maximising ambiguity between IBM500 and IBM424.
     * The fallback must still produce IBM500.
     */
    @Test
    public void repetitiveIbm500IsDetectedAsIbm500() throws Exception {
        byte[] repProbe = encode("IBM500",
                ("The quick brown fox jumps over the lazy dog. ").repeat(5));
        try (TikaInputStream tis = TikaInputStream.get(repProbe)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something");
            assertEquals("IBM500", results.get(0).getLabel(),
                    "Repetitive English IBM500 probe must detect as IBM500 (got: "
                            + results.get(0).getLabel() + ")");
        }
    }

    /**
     * Hebrew EBCDIC (IBM424) content must NOT be swapped to IBM500.
     *
     * <p>IBM500 maps all 256 byte values to valid characters, so a
     * "decodes cleanly" guard would incorrectly fire for Hebrew content.
     * The correct guard is "decoded strings are identical" — Hebrew bytes
     * decode to different characters in IBM500 vs IBM424, so the fallback
     * must not fire.</p>
     */
    @Test
    public void hebrewIbm424IsNotSwappedToIbm500() throws Exception {
        // Encode Hebrew text in IBM424. The Hebrew letters live in the
        // sub-0x80 range in IBM424 (0x41–0x79), so they differ from IBM500
        // at the decoded-character level.
        byte[] hebrewProbe = encode("IBM424",
                "שלום עולם. זהו טקסט עברי. " +
                "הקידוד של IBM424 הוא תקן עבור עברית. " +
                "The quick brown fox. IBM mainframe Hebrew text.");
        try (TikaInputStream tis = TikaInputStream.get(hebrewProbe)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something for Hebrew IBM424 bytes");
            String topLabel = results.get(0).getLabel();
            assertTrue(topLabel.startsWith("IBM424"),
                    "Hebrew IBM424 content must detect as IBM424, not be overridden to IBM500 "
                            + "(got: " + topLabel + ")");
        }
    }
}
