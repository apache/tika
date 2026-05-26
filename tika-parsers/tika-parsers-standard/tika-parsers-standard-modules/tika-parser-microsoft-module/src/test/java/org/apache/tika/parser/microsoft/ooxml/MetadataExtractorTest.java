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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import org.apache.tika.metadata.Metadata;

/**
 * Tests for length-cap defenses in {@link MetadataExtractor}'s SAX-based
 * custom-properties path (backport of the 4.x SAXBasedMetadataExtractor fix).
 * <p>
 * A 3 KB OOXML carrier whose {@code <vt:decimal>} contains a 1,000,000-digit
 * numeric literal would otherwise burn ~25 s of CPU per file in JDK 17's
 * {@code BigDecimal(String)} (O(n²)) when reached through POI/XMLBeans.
 * 3.x's MetadataExtractor now reads {@code docProps/custom.xml} via SAX
 * directly, bypassing XMLBeans, and rejects decimal literals longer than
 * {@link MetadataExtractor#MAX_DECIMAL_LENGTH} before constructing BigDecimal.
 */
public class MetadataExtractorTest {

    private static final String CUSTOM_HEADER = "<?xml version=\"1.0\"?>"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument"
            + "/2006/custom-properties\""
            + " xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument"
            + "/2006/docPropsVTypes\">";
    private static final String CUSTOM_FOOTER = "</Properties>";

    @Test
    public void appendCappedTruncatesAtLimit() {
        StringBuilder buf = new StringBuilder();
        char[] giant = new char[MetadataExtractor.MAX_TEXT_BUFFER_LENGTH + 10_000];
        java.util.Arrays.fill(giant, '9');

        MetadataExtractor.appendCapped(buf, giant, 0, giant.length);
        assertEquals(MetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "buffer must be capped at MAX_TEXT_BUFFER_LENGTH");

        // Further appends after the cap are silent no-ops.
        MetadataExtractor.appendCapped(buf, giant, 0, 100);
        assertEquals(MetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "appends past the cap must be silently dropped");
    }

    @Test
    public void appendCappedRespectsRemainingRoom() {
        StringBuilder buf = new StringBuilder();
        char[] padding = new char[MetadataExtractor.MAX_TEXT_BUFFER_LENGTH - 1];
        java.util.Arrays.fill(padding, 'x');
        buf.append(padding);

        MetadataExtractor.appendCapped(buf, new char[]{'a', 'b', 'c'}, 0, 3);
        assertEquals(MetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "remaining room (1 char) must be filled; overflow dropped");
        assertEquals('a', buf.charAt(buf.length() - 1));
    }

    @Test
    public void normalDecimalIsExtracted() throws Exception {
        Metadata m = parseCustomProperties(customProperty("price", "decimal", "1234.56"));
        assertEquals("1234.56", m.get("custom:price"));
    }

    @Test
    public void oversizedDecimalIsSkippedNotParsed() throws Exception {
        // 1,000 digits is well past MAX_DECIMAL_LENGTH (256) but far below the
        // attacker's 1M-digit DoS payload. With the cap in place this should
        // complete in milliseconds and the property should NOT be set.
        String hugeDigits = "9".repeat(1000);
        long start = System.nanoTime();
        Metadata m = parseCustomProperties(customProperty("evil", "decimal", hugeDigits));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNull(m.get("custom:evil"),
                "oversized decimal must be rejected, not parsed");
        assertTrue(elapsedMs < 2_000,
                "parse must complete quickly; took " + elapsedMs + "ms");
    }

    @Test
    public void oversizedDecimalAttackPayloadCompletesQuickly() throws Exception {
        // Reporter's actual attack shape: 1,000,000 digits. Without the cap
        // this takes ~25 s on JDK 17. With the cap the SAX read still has to
        // accumulate the buffer (bounded to 64 KB by appendCapped) and the
        // decimal-length check then rejects it.
        String attackDigits = "9".repeat(1_000_000);
        long start = System.nanoTime();
        Metadata m = parseCustomProperties(customProperty("evil", "decimal", attackDigits));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNull(m.get("custom:evil"));
        assertTrue(elapsedMs < 2_000,
                "1M-digit attack payload must not trigger O(n²) BigDecimal; took "
                        + elapsedMs + "ms");
    }

    @Test
    public void oversizedStringIsTruncatedNotRejected() throws Exception {
        // A large lpwstr isn't a CPU-DoS like decimal, but unbounded text
        // accumulation would still be a memory pressure vector. The buffer
        // cap stops accumulation at 64 KB; the truncated value still flows.
        String giantString = "a".repeat(200_000);
        Metadata m = parseCustomProperties(customProperty("bigstr", "lpwstr", giantString));
        String got = m.get("custom:bigstr");
        assertNotNull(got, "string-typed property survives truncation");
        assertEquals(MetadataExtractor.MAX_TEXT_BUFFER_LENGTH, got.length(),
                "string value must be capped at MAX_TEXT_BUFFER_LENGTH");
    }

    // ===== helpers =====

    private static String customProperty(String name, String type, String value) {
        return CUSTOM_HEADER
                + "<property fmtid=\"{DEADBEEF-0000-0000-0000-000000000000}\" pid=\"2\""
                + " name=\"" + name + "\">"
                + "<vt:" + type + ">" + value + "</vt:" + type + ">"
                + "</property>"
                + CUSTOM_FOOTER;
    }

    private static Metadata parseCustomProperties(String xml) throws Exception {
        MetadataExtractor.CustomPropertiesHandler handler =
                new MetadataExtractor.CustomPropertiesHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();
        parser.parse(new InputSource(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))), handler);
        Metadata metadata = new Metadata();
        handler.applyTo(metadata);
        return metadata;
    }
}
