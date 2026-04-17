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
     * The general model must have a direct label for the international EBCDIC
     * variant it trains on today.  There must be no bare "EBCDIC" routing label
     * — that was the old two-model architecture which has been replaced by a
     * single model.
     *
     * <p>Script-specific EBCDIC variants (IBM424 Hebrew, IBM420 Arabic, and
     * IBM1047 z/OS Unix Latin) are explicitly excluded from today's SBCS
     * include list (see {@code TrainCharsetModel.TODAY_SBCS_INCLUDE}).  A
     * future EBCDIC specialist will cover them; today they must NOT appear
     * as direct labels.</p>
     */
    @Test
    public void generalModelEbcdicLabelPolicy() {
        LinearModel general = detector.getModel();
        List<String> labels = Arrays.asList(general.getLabels());

        assertFalse(labels.contains("EBCDIC"),
                "Model must not have a bare 'EBCDIC' routing label (single-model architecture)");

        // IBM500 (international EBCDIC) is the only EBCDIC in today's SBCS model.
        assertTrue(labels.contains("IBM500"),
                "IBM500 must be a direct model label");

        // Script-specific and duplicate EBCDIC variants must NOT be direct labels.
        for (String excluded : new String[]{
                "IBM420-ltr", "IBM420-rtl", "IBM424-ltr", "IBM424-rtl", "IBM1047"}) {
            assertFalse(labels.contains(excluded),
                    "Excluded EBCDIC variant must not appear in today's model: " + excluded);
        }

        // DOS Cyrillic variants (not EBCDIC) must be direct labels.
        assertTrue(labels.contains("IBM855"), "IBM855 (DOS Cyrillic) must be a direct model label");
        assertTrue(labels.contains("IBM866"), "IBM866 (DOS Cyrillic) must be a direct model label");
    }

    /**
     * IBM500 bytes must be detected as an IBM variant directly by the model.
     */
    @Test
    public void ibm500IsDetectedDirectly() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(IBM500_BYTES)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something for IBM500 bytes");
            String topLabel = results.get(0).getLabel();
            assertTrue(topLabel.startsWith("IBM"),
                    "Result should be an IBM variant, got: " + topLabel);
        }
    }
}
