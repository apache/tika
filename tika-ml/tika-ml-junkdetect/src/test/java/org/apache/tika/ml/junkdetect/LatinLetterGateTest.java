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
package org.apache.tika.ml.junkdetect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the within-Latin letter-plausibility gate
 * ({@link JunkFilterEncodingDetector#applyLatinLetterGate}) in isolation.
 */
public class LatinLetterGateTest {

    private static final Charset WIN1252 = Charset.forName("windows-1252");
    private static final Charset IBM850 = Charset.forName("IBM850");
    private static final Charset ISO_8859_2 = Charset.forName("ISO-8859-2");
    private static final Charset WIN1251 = Charset.forName("windows-1251");
    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    private static Set<Charset> candidates(Charset... cs) {
        Set<Charset> s = new LinkedHashSet<>();
        for (Charset c : cs) {
            s.add(c);
        }
        return s;
    }

    /** 0xC0-0xCF: À-Ï (letters) in windows-1252, box-drawing in IBM850 →
     *  windows-1252 wins the letter count decisively → demote. */
    private static byte[] boxDrawingProbe(int repeats) {
        byte[] probe = new byte[16 * repeats];
        for (int r = 0; r < repeats; r++) {
            for (int i = 0; i < 16; i++) {
                probe[r * 16 + i] = (byte) (0xC0 + i);
            }
        }
        return probe;
    }

    @Test
    void demotesBoxDrawingIbm850ToWindows1252() {
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                boxDrawingProbe(3), IBM850, candidates(IBM850, WIN1252));
        assertEquals(WIN1252, out, "box-drawing IBM850 pick should demote to windows-1252");
    }

    /** Bytes that are Central-European letters in ISO-8859-2 (Ą Ł Ś Š Ž ...) but
     *  symbols (¡ £ ¦ © ...) in windows-1252.  ISO-8859-2 wins the letter count,
     *  so the directional gate must NOT flip genuine CE text. */
    @Test
    void keepsGenuineCentralEuropean() {
        int[] ceLetters = {0xA1, 0xA3, 0xA5, 0xA6, 0xA9, 0xAB, 0xAC, 0xAE, 0xAF,
                0xB1, 0xB3, 0xB6, 0xB9, 0xBB, 0xBC, 0xBE, 0xBF};
        byte[] probe = new byte[ceLetters.length];
        for (int i = 0; i < ceLetters.length; i++) {
            probe[i] = (byte) ceLetters[i];
        }
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                probe, ISO_8859_2, candidates(ISO_8859_2, WIN1252));
        assertEquals(ISO_8859_2, out, "genuine CE text wins letters under its true charset");
    }

    @Test
    void silentBelowHighByteFloor() {
        byte[] sparse = {(byte) 0xC0, (byte) 0xC1, (byte) 0xC2};
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                sparse, IBM850, candidates(IBM850, WIN1252));
        assertEquals(IBM850, out, "below the high-byte floor the gate must not act");
    }

    @Test
    void silentOnNonLatinChampion() {
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                boxDrawingProbe(3), WIN1251, candidates(WIN1251, WIN1252));
        assertEquals(WIN1251, out, "Cyrillic champion is outside the Latin allowlist");
    }

    @Test
    void silentOnCjkChampion() {
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                boxDrawingProbe(3), SHIFT_JIS, candidates(SHIFT_JIS, WIN1252));
        assertEquals(SHIFT_JIS, out, "CJK champion is the family gate's territory, not this one");
    }

    @Test
    void silentWhenWindows1252NotACandidate() {
        Charset out = JunkFilterEncodingDetector.applyLatinLetterGate(
                boxDrawingProbe(3), IBM850, candidates(IBM850, ISO_8859_2));
        assertEquals(IBM850, out, "nothing canonical to demote to without a windows-1252 candidate");
    }
}
