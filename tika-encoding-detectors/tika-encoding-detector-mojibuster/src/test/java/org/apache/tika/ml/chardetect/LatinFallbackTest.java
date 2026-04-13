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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests for the byte-walk decode-equivalence helper and the narrow
 * Latin→windows-1252 fallback semantics.  Integration with the detector
 * pipeline is exercised in the broader regression tests.
 */
public class LatinFallbackTest {

    private static final Charset WIN1252 = Charset.forName("windows-1252");
    private static final Charset WIN1257 = Charset.forName("windows-1257");
    private static final Charset WIN1250 = Charset.forName("windows-1250");
    private static final Charset MACROMAN = Charset.forName("x-MacRoman");
    private static final Charset ISO8859_1 = Charset.forName("ISO-8859-1");
    private static final Charset IBM852 = Charset.forName("IBM852");

    @Test
    public void vcardSingleUmlautIsByteIdenticalUnderLatin1252And1257() {
        byte[] probe = "BEGIN:VCARD\r\nN:M\u00FCller\r\nFN:Hans M\u00FCller\r\nEND:VCARD\r\n"
                .getBytes(ISO8859_1);
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(probe, WIN1257, WIN1252),
                "German vCard bytes should decode identically under 1257 and 1252");
    }

    @Test
    public void ibm852DiffersFrom1252OnUmlaut() {
        // 0xFC in windows-1252 is 'ü'; in IBM852 it's 'Ř'.  The fallback
        // must NOT relabel IBM852 to windows-1252 when the probe contains
        // bytes where the two genuinely differ.
        byte[] probe = "stra\u00DFe".getBytes(ISO8859_1);  // 'ß' = 0xDF
        // 0xDF in IBM852 is different from 0xDF in 1252 — check byte 0xFC too
        byte[] probeWithUmlaut = new byte[]{'M', (byte) 0xFC, 'l', 'l', 'e', 'r'};
        assertFalse(DecodeEquivalence.byteIdenticalOnProbe(probeWithUmlaut, IBM852, WIN1252),
                "IBM852 'Ř' must not be byte-identical to 1252 'ü'");
    }

    @Test
    public void pureAsciiIsByteIdenticalAcrossAllLatinFamily() {
        byte[] probe = "Hello, world!  No accents here at all.\r\n"
                .getBytes(StandardCharsets.US_ASCII);
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(probe, WIN1257, WIN1252));
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(probe, WIN1250, WIN1252));
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(probe, MACROMAN, WIN1252));
    }

    @Test
    public void win1257EuroSignDiffersFrom1252() {
        // 0xA4 in windows-1257 is the generic currency sign '¤';
        // in windows-1252 it is also '¤' — they AGREE here.
        // But 0xB8 differs: 1257='ø', 1252='¸'.
        byte[] probe = new byte[]{'t', 'e', 's', 't', (byte) 0xB8};
        assertFalse(DecodeEquivalence.byteIdenticalOnProbe(probe, WIN1257, WIN1252),
                "0xB8 differs between 1257 and 1252 — must not byte-match");
    }

    @Test
    public void sameCharsetIsAlwaysEquivalent() {
        byte[] probe = "anything at all \u00E4\u00F6\u00FC".getBytes(ISO8859_1);
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(probe, WIN1252, WIN1252));
    }

    @Test
    public void emptyProbeIsEquivalentEverywhere() {
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(new byte[0], WIN1257, WIN1252));
        assertTrue(DecodeEquivalence.byteIdenticalOnProbe(new byte[0], IBM852, WIN1252));
    }
}
