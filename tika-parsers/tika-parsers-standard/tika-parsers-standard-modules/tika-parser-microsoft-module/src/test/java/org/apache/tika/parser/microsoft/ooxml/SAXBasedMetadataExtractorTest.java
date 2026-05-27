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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Tests for length-cap defenses in {@link SAXBasedMetadataExtractor}.
 * <p>
 * Both caps target attacker-controlled docProps/custom.xml. A 3 KB OOXML
 * carrier whose {@code <vt:decimal>} contains a 1,000,000-digit numeric
 * literal would otherwise burn ~25 s of CPU per file in JDK 17's
 * {@code BigDecimal(String)} (O(n²)).
 */
public class SAXBasedMetadataExtractorTest {

    private static final String CUSTOM_HEADER = "<?xml version=\"1.0\"?>"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument"
            + "/2006/custom-properties\""
            + " xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument"
            + "/2006/docPropsVTypes\">";
    private static final String CUSTOM_FOOTER = "</Properties>";

    @Test
    public void appendCappedTruncatesAtLimit() {
        StringBuilder buf = new StringBuilder();
        char[] giant = new char[SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH + 10_000];
        java.util.Arrays.fill(giant, '9');

        SAXBasedMetadataExtractor.appendCapped(buf, giant, 0, giant.length);
        assertEquals(SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "buffer must be capped at MAX_TEXT_BUFFER_LENGTH");

        // Further appends after the cap are silent no-ops.
        SAXBasedMetadataExtractor.appendCapped(buf, giant, 0, 100);
        assertEquals(SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "appends past the cap must be silently dropped");
    }

    @Test
    public void appendCappedRespectsRemainingRoom() {
        StringBuilder buf = new StringBuilder();
        // Pre-fill to one short of the cap; next 3 chars should partially land.
        char[] padding = new char[SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH - 1];
        java.util.Arrays.fill(padding, 'x');
        buf.append(padding);

        SAXBasedMetadataExtractor.appendCapped(buf, new char[]{'a', 'b', 'c'}, 0, 3);
        assertEquals(SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH, buf.length(),
                "remaining room (1 char) must be filled; overflow dropped");
        assertEquals('a', buf.charAt(buf.length() - 1));
    }

    @Test
    public void normalDecimalIsExtracted() throws Exception {
        Metadata m = parseCustomProperties(customProperty("price", "decimal", "1234.56"));
        assertEquals("1234.56", m.get("custom:price"));
    }

    @Test
    public void decimalAtMaxLengthIsAccepted() throws Exception {
        // Boundary: exactly MAX_DECIMAL_LENGTH digits must still parse. This is
        // the upper edge of the accept-and-parse path.
        String digits = "9".repeat(SAXBasedMetadataExtractor.MAX_DECIMAL_LENGTH);
        Metadata m = parseCustomProperties(customProperty("ok", "decimal", digits));
        assertEquals(digits, m.get("custom:ok"),
                "decimal of exactly MAX_DECIMAL_LENGTH digits must round-trip");
    }

    @Test
    public void decimalOneOverMaxLengthIsRejected() throws Exception {
        // Boundary: one character past the cap must short-circuit before
        // BigDecimal(String). Combined with appendCappedTruncatesAtLimit this
        // mechanically proves the O(n²) parser is never invoked above the cap.
        String digits = "9".repeat(SAXBasedMetadataExtractor.MAX_DECIMAL_LENGTH + 1);
        Metadata m = parseCustomProperties(customProperty("evil", "decimal", digits));
        assertNull(m.get("custom:evil"),
                "decimal one char over MAX_DECIMAL_LENGTH must be rejected, not parsed");
    }

    @Test
    public void oversizedDecimalAttackPayloadIsRejected() throws Exception {
        // Reporter's actual attack shape: 1,000,000 digits. The SAX read
        // truncates accumulation at MAX_TEXT_BUFFER_LENGTH (64 KB) via
        // appendCapped, then the decimal-length check rejects the truncated
        // value before BigDecimal(String) runs. No wall-clock assertion —
        // the boundary tests above are the mechanical proof that
        // BigDecimal is never called on an oversized payload.
        String attackDigits = "9".repeat(1_000_000);
        Metadata m = parseCustomProperties(customProperty("evil", "decimal", attackDigits));
        assertNull(m.get("custom:evil"),
                "1M-digit attack payload must be rejected without parsing");
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
        assertEquals(SAXBasedMetadataExtractor.MAX_TEXT_BUFFER_LENGTH, got.length(),
                "string value must be capped at MAX_TEXT_BUFFER_LENGTH");
    }

    @Test
    public void stringValuesPreserveLeadingAndTrailingWhitespace() throws Exception {
        // Legacy POI's getLpwstr/getLpstr/getBstr returned the raw element text;
        // anything that depends on whitespace inside the value would regress if
        // the SAX path silently trimmed.
        Metadata m = parseCustomProperties(
                customProperty("padded", "lpwstr", "  hello world  "));
        assertEquals("  hello world  ", m.get("custom:padded"),
                "lpwstr must preserve leading/trailing whitespace");

        m = parseCustomProperties(customProperty("padded", "lpstr", " ascii "));
        assertEquals(" ascii ", m.get("custom:padded"));

        m = parseCustomProperties(customProperty("padded", "bstr", "\tindented\n"));
        assertEquals("\tindented\n", m.get("custom:padded"));
    }

    @Test
    public void boolLexicalOneIsNormalizedToTrue() throws Exception {
        Metadata m = parseCustomProperties(customProperty("flag", "bool", "1"));
        assertEquals("true", m.get("custom:flag"),
                "<vt:bool>1</vt:bool> must normalize to \"true\" (matching legacy POI)");
    }

    @Test
    public void boolLexicalZeroIsNormalizedToFalse() throws Exception {
        Metadata m = parseCustomProperties(customProperty("flag", "bool", "0"));
        assertEquals("false", m.get("custom:flag"),
                "<vt:bool>0</vt:bool> must normalize to \"false\" (matching legacy POI)");
    }

    @Test
    public void boolLexicalTrueAndFalsePassThrough() throws Exception {
        Metadata m = parseCustomProperties(customProperty("flag", "bool", "true"));
        assertEquals("true", m.get("custom:flag"));

        m = parseCustomProperties(customProperty("flag", "bool", "false"));
        assertEquals("false", m.get("custom:flag"));
    }

    @Test
    public void vectorContainingScalarIsNotEmittedAsScalar() throws Exception {
        // <vt:vector> with inner <vt:lpstr> children. The container latches as
        // the value type; inner children must NOT overwrite it, and the
        // container itself must not be emitted as a scalar. Legacy POI skipped
        // vector/array entirely.
        String xml = CUSTOM_HEADER
                + "<property fmtid=\"{DEADBEEF-0000-0000-0000-000000000000}\" pid=\"2\""
                + " name=\"tags\">"
                + "<vt:vector size=\"2\" baseType=\"lpstr\">"
                + "<vt:lpstr>alpha</vt:lpstr>"
                + "<vt:lpstr>beta</vt:lpstr>"
                + "</vt:vector>"
                + "</property>"
                + CUSTOM_FOOTER;
        Metadata m = parseCustomProperties(xml);
        assertNull(m.get("custom:tags"),
                "vector container must not emit a scalar custom property");
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
        SAXBasedMetadataExtractor.CustomPropertiesHandler handler =
                new SAXBasedMetadataExtractor.CustomPropertiesHandler();
        try (InputStream is = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))) {
            XMLReaderUtils.parseSAX(is, handler, new ParseContext());
        }
        Metadata metadata = new Metadata();
        handler.applyTo(metadata);
        return metadata;
    }
}
