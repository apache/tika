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
 *   <li>General model emits {@code "EBCDIC"} routing label for EBCDIC-family bytes.</li>
 *   <li>{@code MojibusterEncodingDetector} routes to the EBCDIC sub-model, which
 *       returns a specific IBM variant (IBM500, IBM420, IBM855, etc.) — never the
 *       bare {@code "EBCDIC"} routing label.</li>
 * </ol>
 */
public class EbcdicRoutingTest {

    private static MojibusterEncodingDetector detector;

    // Representative English prose encoded in IBM500 (International EBCDIC).
    // Generated via: text.getBytes(Charset.forName("IBM500"))
    private static final byte[] IBM500_BYTES = makeEbcdic("IBM500",
            "The quick brown fox jumps over the lazy dog. " +
            "This sentence contains every letter of the English alphabet. " +
            "EBCDIC encoding is used on IBM mainframe systems. " +
            "Fields are often fixed-width and space-padded in EBCDIC files.");

    // Russian text encoded in IBM855 (Cyrillic EBCDIC).
    private static final byte[] IBM855_BYTES = makeEbcdic("IBM855",
            "\u041f\u0440\u0438\u0432\u0435\u0442 \u043c\u0438\u0440! " +  // Привет мир!
            "\u042d\u0442\u043e \u0442\u0435\u043a\u0441\u0442 \u043d\u0430 " + // Это текст на
            "\u0440\u0443\u0441\u0441\u043a\u043e\u043c \u044f\u0437\u044b\u043a\u0435. " + // русском языке.
            "\u041a\u043e\u0434\u0438\u0440\u043e\u0432\u043a\u0430 IBM855 " + // Кодировка IBM855
            "\u0438\u0441\u043f\u043e\u043b\u044c\u0437\u0443\u0435\u0442\u0441\u044f " + // используется
            "\u043d\u0430 \u043c\u0435\u0439\u043d\u0444\u0440\u0435\u0439\u043c\u0430\u0445."); // на мейнфреймах.

    private static byte[] makeEbcdic(String charsetName, String text) {
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
     * The general model must have exactly one EBCDIC routing label.
     * Individual IBM variants must NOT appear as top-level labels — they live
     * only in the EBCDIC sub-model.
     */
    @Test
    public void generalModelHasSingleEbcdicRoutingLabel() {
        LinearModel general = detector.getModel();
        String[] labels = general.getLabels();

        assertTrue(Arrays.asList(labels).contains("EBCDIC"),
                "General model must have an 'EBCDIC' routing label");

        // No individual IBM variant should appear as a direct label in the general model —
        // they live only in the EBCDIC sub-model
        for (String label : labels) {
            assertFalse(label.startsWith("IBM"),
                    "General model must not contain individual IBM variant: " + label);
        }
    }

    /**
     * IBM500 bytes must route through the sub-model and return a specific IBM variant,
     * not the bare "EBCDIC" routing label.
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

    /**
     * IBM855 (Cyrillic EBCDIC) bytes must similarly route through the sub-model.
     */
    @Test
    public void ibm855RoutesToSubModel() throws Exception {
        try (TikaInputStream tis = TikaInputStream.get(IBM855_BYTES)) {
            List<EncodingResult> results = detector.detect(tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(), "Should detect something for IBM855 bytes");
            String topLabel = results.get(0).getLabel();
            assertNotEquals("EBCDIC", topLabel,
                    "Result must be a specific IBM variant, not the routing label");
            assertTrue(topLabel.startsWith("IBM"),
                    "Result should be an IBM variant, got: " + topLabel);
        }
    }
}
