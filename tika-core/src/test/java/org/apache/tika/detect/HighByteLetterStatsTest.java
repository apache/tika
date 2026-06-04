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
package org.apache.tika.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

public class HighByteLetterStatsTest {

    private static final Charset WIN1252 = Charset.forName("windows-1252");
    private static final Charset IBM850 = Charset.forName("IBM850");
    private static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

    /** Bytes 0xC0-0xCF are À-Ï (all letters) in windows-1252 but mostly
     *  box-drawing (└┴┬├─┼ ... ¤) in IBM850 — the box-drawing signature the
     *  within-Latin gate keys on. */
    @Test
    void winBeatsIbm850OnBoxDrawingRange() {
        byte[] probe = new byte[16];
        for (int i = 0; i < 16; i++) {
            probe[i] = (byte) (0xC0 + i);
        }
        int win = HighByteLetterStats.countCasedHighByteLetters(probe, WIN1252);
        int ibm = HighByteLetterStats.countCasedHighByteLetters(probe, IBM850);
        assertEquals(16, win, "all of 0xC0-0xCF are letters in windows-1252");
        assertTrue(ibm <= 4, "IBM850 maps most of 0xC0-0xCF to box-drawing; was " + ibm);
        assertTrue(win > ibm + 6, "decisive letter gap expected; win=" + win + " ibm=" + ibm);
    }

    /** ª (0xAA), º (0xBA) are ordinal indicators, not letters; é (0xE9) is. */
    @Test
    void excludesOrdinalIndicators() {
        byte[] probe = {(byte) 0xAA, (byte) 0xBA, (byte) 0xE9};
        assertEquals(1, HighByteLetterStats.countCasedHighByteLetters(probe, WIN1252),
                "only é should count; ª and º are ordinal indicators");
    }

    /** CJK ideographs are Lo (other-letter), excluded — so a CJK decode can
     *  never win the cased-letter comparison against a Latin sibling. */
    @Test
    void doesNotCountCjkIdeographs() {
        byte[] probe = "日本語の文章".getBytes(SHIFT_JIS);
        assertEquals(0, HighByteLetterStats.countCasedHighByteLetters(probe, SHIFT_JIS),
                "ideographs are Lo and must not count as cased letters");
    }

    @Test
    void countHighBytesIsByteCountAtOrAbove0x80() {
        byte[] probe = {0x41, (byte) 0x80, (byte) 0xFF, 0x20, (byte) 0xC3};
        assertEquals(3, HighByteLetterStats.countHighBytes(probe));
        assertEquals(0, HighByteLetterStats.countHighBytes(new byte[0]));
        assertEquals(0, HighByteLetterStats.countHighBytes(null));
    }
}
