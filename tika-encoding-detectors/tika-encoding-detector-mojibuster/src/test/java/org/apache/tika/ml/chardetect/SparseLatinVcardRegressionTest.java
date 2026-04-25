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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Regression test for the sparse-Latin vCard / config-file detection
 * class.
 *
 * <p>Historically a predominantly-ASCII probe with a small number of
 * Latin-supplement high bytes (e.g. a vCard containing a German
 * business name) could detect as {@code IBM424} (Hebrew EBCDIC) at
 * 0.99 confidence — producing complete mojibake.  The combination of
 * structural IBM424 gating, Latin-sibling → windows-1252 fallback in
 * {@code MojibusterEncodingDetector}, and CharSoup's
 * language-signal arbitration prevents that.  This test exercises
 * the full detector chain via {@link DefaultEncodingDetector} and
 * asserts the non-catastrophic property: not IBM424.</p>
 */
public class SparseLatinVcardRegressionTest {

    /**
     * Sparse-Latin vCard probes must NOT detect as {@code IBM424}
     * (Hebrew EBCDIC).  Whether they detect specifically as
     * {@code windows-1252} vs a byte-identical Latin sibling
     * (windows-1257, IBM852, etc.) is a documented sibling-arbitration
     * limitation; only the catastrophic case is asserted here.
     */
    @Test
    public void sparseLatinVcardDoesNotDetectAsIbm424() throws Exception {
        byte[] probe = buildSparseLatinVcard();

        DefaultEncodingDetector detector = new DefaultEncodingDetector();
        try (TikaInputStream tis = TikaInputStream.get(probe)) {
            List<EncodingResult> results = detector.detect(
                    tis, new Metadata(), new ParseContext());
            assertFalse(results.isEmpty(),
                    "Detector must return at least one candidate");
            assertNotEquals("IBM424", results.get(0).getCharset().name(),
                    "Sparse-Latin vCard must NOT detect as IBM424 (Hebrew EBCDIC) — "
                            + "that's the catastrophic mojibake regression this test "
                            + "was created to guard against.  (Whether it detects as "
                            + "windows-1252 vs a byte-identical Latin sibling is a "
                            + "separate, documented sibling-arbitration limitation.)");
        }
    }

    /**
     * Synthetic vCard-shaped probe that reproduces the regression class.
     *
     * <p>Preserved byte statistics from the original failing file:
     * <ul>
     *   <li>Length in the 400-600 byte range (long-probe path).</li>
     *   <li>Exactly 3 non-ASCII bytes, all {@code 0xE4} — 'ä' under
     *       ISO-8859-1 / windows-1252 / windows-1257. The extreme-sparse
     *       regime where the flat statistical model was overconfidently
     *       wrong.</li>
     *   <li>Zero C1 bytes ({@code 0x80–0x9F}) so ISO→Windows upgrade
     *       does not fire.</li>
     *   <li>LF line endings only (no CRLF) so CRLF→Windows upgrade
     *       does not fire.</li>
     *   <li>Zero {@code 0x40} bytes so the EBCDIC gate cleanly returns
     *       {@code false}.</li>
     * </ul>
     *
     * <p>Content is a fictitious German bakery at a fictitious address.
     * No real business or person is represented.</p>
     */
    private static byte[] buildSparseLatinVcard() {
        String vcard =
                  "BEGIN:VCARD\n"
                + "\t\t\t\t\tVERSION:3.0\n"
                + "\t\t\t\t\tN:Example B\u00E4ckerei GmbH\n"
                + "\t\t\t\t\tFN:Example B\u00E4ckerei GmbH\n"
                + "\t\t\t\t\tORG:Example B\u00E4ckerei GmbH;\n"
                + "\t\t\t\t\tPHOTO;VALUE=URL;TYPE=jpg:"
                        + "https://example.com/images/logo.jpg\n"
                + "\t\t\t\t\titem1.EMAIL;TYPE=PREF,INTERNET:\n"
                + "\t\t\t\t\titem1.X-ABLabel:email\n"
                + "\t\t\t\t\tTEL;TYPE=WORK,VOICE:\n"
                + "\t\t\t\t\tTEL;TYPE=WORK,FAX:\n"
                + "\t\t\t\t\titem2.ADR;TYPE=WORK:"
                        + ";;Teststr. 1;Musterstadt;;12345;Germany;\n"
                + "\t\t\t\t\titem2.X-ABADR:de\n"
                + "\t\t\t\t\tLABEL;TYPE=WORK:Teststr. 1 Musterstadt, 12345\n"
                + "\t\t\t\t\tURL;TYPE=PREF:\n"
                + "\t\t\t\t\tREV:2026-04-12 12:00:00\n"
                + "\t\t\t\t\tNOTE:Synthetic test fixture for charset "
                        + "detector regression coverage\n"
                + "\t\t\t\t\tEND:VCARD\n";
        return vcard.getBytes(StandardCharsets.ISO_8859_1);
    }
}
