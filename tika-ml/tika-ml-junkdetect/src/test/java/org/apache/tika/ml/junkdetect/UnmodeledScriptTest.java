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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.tika.quality.TextQualityScore;

/**
 * Real text in a script the bundled model has no usable table for must read as
 * unmodeled (coverage 0, not junk), and scripts it does model must be unaffected.
 */
public class UnmodeledScriptTest {

    private static JunkDetector detector;

    @BeforeAll
    static void loadModel() throws Exception {
        detector = JunkDetector.loadFromClasspath();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "YI|ꆈꌠꁱꂷ ꀉꂾꑭꇬ ꌋꆀꃅꑍ ꆈꌠꉙ ꀊꁨꌠ ꄉꈌꌠ ꑳꌠꆏ ꃅꐥꇬ ꌠꅇ",
            "MONGOLIAN|ᠮᠣᠩᠭᠣᠯ ᠤᠯᠤᠰ ᠪᠣᠯ ᠳᠣᠷᠤᠨᠠᠳᠤ ᠠᠽᠢ ᠶᠢᠨ ᠬᠣᠢᠳᠤ ᠬᠡᠰᠡᠭ ᠲᠦ ᠣᠷᠣᠰᠢᠳᠠᠭ",
            "CANADIAN_ABORIGINAL|ᐃᓄᒃᑎᑐᑦ ᐅᖃᐅᓯᖅ ᐃᓄᐃᑦ ᐅᖃᐅᓯᖓᑦ ᑲᓇᑕᒥ ᓄᓇᕗᒻᒥ ᐊᒻᒪ ᓄᓇᕕᒻᒥ",
            "CHEROKEE|ᏣᎳᎩ ᎦᏬᏂᎯᏍᏗ ᎠᏂᏴᏫᏯ ᎤᏂᎦᏚ ᏚᏂᏃᎮᎴ ᏣᎳᎩ ᎠᏰᎵ ᏓᎵᏍᏓᏴᏗ",
            "ADLAM|𞤀𞤣𞤤𞤢𞤥 𞤫𞤼𞤵 𞤦𞤭𞤲𞤣𞤭 𞤯𞤫𞤲𞤺𞤢𞤤 𞤆𞤵𞤤𞤢𞤪 𞤲𞤣𞤫𞤪 𞤀𞤬𞤪𞤭𞤳𞤢"
    })
    void unmodeledScriptIsNotJunk(String script, String text) {
        assertFalse(detector.getModel().getTrainedScripts().contains(Character.UnicodeScript.valueOf(script)), script + " should be unmodeled");
        TextQualityScore s = detector.score(text);
        assertTrue(s.isUnmodeled(), "coverage should be 0: " + s);
        assertEquals(script, s.getDominantScript());
        assertTrue(s.isUnknown(), "unmodeled-script text must be unknown, not junk: " + s);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "GREEK|Η Ελλάδα είναι χώρα της νοτιοανατολικής Ευρώπης. Άρθρα για την ιστορία της χώρας.",
            "LATIN|The quick brown fox jumps over the lazy dog and keeps running down the road.",
            "CYRILLIC|Москва является столицей России и крупнейшим городом страны.",
            "HIRAGANA|これはひらがなのぶんしょうです。きょうはいいてんきですね。",
            "KATAKANA|コンピュータのソフトウェアをインストールしてください。"
    })
    void modeledScriptFullyCovered(String script, String text) {
        assertTrue(detector.getModel().getTrainedScripts().contains(Character.UnicodeScript.valueOf(script)));
        TextQualityScore s = detector.score(text);
        assertEquals(1f, s.getCoverage(), 1e-6f, s.toString());
        assertEquals(script, s.getDominantScript());
        assertTrue(s.getZScore() > 0, s.toString());
    }

    @Test
    void mixedTextIsPartiallyCovered() {
        TextQualityScore s = detector.score(
                "Mongolia ᠮᠣᠩᠭᠣᠯ ᠤᠯᠤᠰ ᠪᠣᠯ ᠳᠣᠷᠤᠨᠠᠳᠤ is a country in East Asia.");
        assertEquals("LATIN", s.getDominantScript());
        assertTrue(s.getCoverage() > 0f && s.getCoverage() < 1f, s.toString());
    }

    /** Clean text mostly in an untrained script scores like junk, so below MIN_COVERAGE it is unknown. */
    @Test
    void mostlyUnmodeledTextIsUnknown() {
        TextQualityScore s = detector.score("Mongolia ᠮᠣᠩᠭᠣᠯ ᠤᠯᠤᠰ ᠪᠣᠯ ᠳᠣᠷᠤᠨᠠᠳᠤ ᠠᠽᠢ ᠶᠢᠨ ᠬᠣᠢᠳᠤ is a country.");
        assertTrue(s.getCoverage() > 0 && s.getCoverage() < JunkDetector.MIN_COVERAGE, s.toString());
        assertTrue(s.isUnknown() && !s.isUnmodeled(), s.toString());
        TextQualityScore mostlyLatin = detector.score("The Mongolian name of the country, ᠮᠣᠩᠭᠣᠯ, is written in the "
                + "traditional script, which runs in vertical columns from left to right across the page.");
        assertTrue(mostlyLatin.getCoverage() >= JunkDetector.MIN_COVERAGE && !mostlyLatin.isUnknown(), mostlyLatin.toString());
    }

    @Test
    void unmodeledScriptSaladIsJunk() {
        int[] pool = "ꆈꌠꁱꂷᠮᠣᠩᠭᐃᓄᒃᑎᏣᎳᎩᎦ".codePoints().toArray();
        Random r = new Random(20261103L);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.appendCodePoint(pool[r.nextInt(pool.length)]);
            if (r.nextInt(5) == 0) {
                sb.append(' ');
            }
        }
        TextQualityScore s = detector.score(sb.toString());
        assertTrue(s.isUnmodeled() && s.isUnknown(), "no trained letters: nothing to judge: " + s);
    }

    @Test
    void letterFreeTextDoesNotOutscoreProse() {
        float prose = detector.score("Москва является столицей России и крупнейшим городом.")
                .getZScore();
        for (String letterFree : new String[]{"      \n\n\t   ", "╔══╗ ║░░║ ╚══╝ ▒▓█ ┌─┐", "2026-11-03 3.14159"}) {
            TextQualityScore s = detector.score(letterFree);
            assertTrue(Float.isNaN(s.getCoverage()), s.toString());
            assertTrue(s.isUnknown() && prose > 0, "letter-free text must be unknown: " + s);
        }
    }
}
