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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Verifies the two-phase EBCDIC detection pipeline:
 * <ol>
 *   <li>The general model emits an {@code "EBCDIC"} routing label for true EBCDIC bytes
 *       (IBM420, IBM424, IBM500 variants).</li>
 *   <li>{@code MojibusterEncodingDetector} routes to the EBCDIC sub-model, which
 *       returns a specific IBM variant — never the bare {@code "EBCDIC"} routing label.</li>
 * </ol>
 *
 * IBM855 and IBM866 are DOS/OEM Cyrillic code pages, not true EBCDIC.  They appear
 * as direct labels in the general model (alongside windows-1251, KOI8-R, etc.) and
 * never trigger the EBCDIC routing path.
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
     * The general model must have:
     * - one "EBCDIC" routing label for true EBCDIC variants (IBM420, IBM424, IBM500)
     * - IBM855 and IBM866 as direct labels (DOS Cyrillic — not routed via sub-model)
     *
     * True EBCDIC variants must NOT appear as direct general-model labels.
     */
    @Test
    public void generalModelHasCorrectEbcdicLabels() {
        LinearModel general = detector.getModel();
        List<String> labels = Arrays.asList(general.getLabels());

        assertTrue(labels.contains("EBCDIC"),
                "General model must have an 'EBCDIC' routing label");

        // True EBCDIC variants must not be direct labels — they live in the sub-model
        for (String trueEbcdic : new String[]{"IBM420-ltr", "IBM420-rtl", "IBM424-ltr", "IBM424-rtl", "IBM500"}) {
            assertFalse(labels.contains(trueEbcdic),
                    "True EBCDIC variant must not be a direct general-model label: " + trueEbcdic);
        }

        // DOS Cyrillic variants must be direct labels (not routed via sub-model)
        assertTrue(labels.contains("IBM855"),
                "IBM855 (DOS Cyrillic) must be a direct general-model label");
        assertTrue(labels.contains("IBM866"),
                "IBM866 (DOS Cyrillic) must be a direct general-model label");
    }

    /**
     * IBM500 bytes must route through the EBCDIC sub-model and return a specific IBM
     * variant, not the bare "EBCDIC" routing label.
     */
    @Test
    public void ibm500RoutesToSubModel() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(IBM500_BYTES)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something for IBM500 bytes");
            String topLabel = results.get(0).getLabel();
            assertNotEquals("EBCDIC", topLabel,
                    "Result must be a specific IBM variant, not the routing label");
            assertTrue(topLabel.startsWith("IBM"),
                    "Result should be an IBM variant, got: " + topLabel);
        }
    }
}
