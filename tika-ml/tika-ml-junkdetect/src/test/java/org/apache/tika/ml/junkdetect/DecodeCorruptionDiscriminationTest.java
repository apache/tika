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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Languageness must be MONOTONIC under decode corruption: a clean German phrase
 * must score strictly higher than the same phrase broken two distinct ways, each
 * exercising a different feature.
 *
 * <ol>
 *   <li>U+FFFD -- a decode failure (accented chars replaced by the replacement
 *       char).  Caught by z6 (replacement ratio).  Subtle: FFFD is a token
 *       boundary, so it DROPS the hard accented bigram and the surviving common-
 *       letter fragments LIFT z1 -- coherence is fooled into preferring the broken
 *       decode, which used to score HIGHER than clean on short pages in the 150k
 *       CommonCrawl eval (deu/gsw, deu/frr).  z6 must overrule that.</li>
 *   <li>Wrong accented letter -- real Latin letters that do not belong in German
 *       (Nordic a-ring / y-diaeresis / thorn), no FFFD.  Caught by z1 (letter
 *       coherence); the margin is smaller (pan-Latin pools many languages) but the
 *       clean decode must still win.</li>
 * </ol>
 *
 * <p>Non-ASCII is written with Unicode escapes so the source stays ASCII-only.
 */
public class DecodeCorruptionDiscriminationTest {

    private static JunkDetector jd;

    @BeforeAll
    static void load() throws Exception {
        jd = JunkDetector.loadFromClasspath();
    }

    private static float languageness(String s) {
        return jd.scoreWithFeatureComponents(s).logit;
    }

    /** Clean German prose: "Die naechste Stunde beginnt am Montag um neun Uhr im
     *  grossen Saal fuer alle Anfaenger" (with real umlauts/eszett). */
    private static final String CLEAN =
            "Die n\u00E4chste Stunde beginnt am Montag um neun Uhr im "
            + "gro\u00DFen Saal f\u00FCr alle Anf\u00E4nger";

    @Test
    void cleanOutscoresFffdBrokenWord() {
        // Every accented char -> U+FFFD: a decode failure that leaves the
        // surrounding letters real and in order, so z1 is unharmed (even helped).
        // z6 (replacement ratio) must overrule z1 and rank clean higher.
        String fffd =
                "Die n\uFFFDchste Stunde beginnt am Montag um neun Uhr im "
                + "gro\uFFFDen Saal f\uFFFDr alle Anf\uFFFDnger";
        float clean = languageness(CLEAN);
        float broken = languageness(fffd);
        assertTrue(clean > broken,
                "clean German must outscore the U+FFFD-broken decode (a decode "
                + "failure must never raise languageness); clean=" + clean
                + " fffd=" + broken);
    }

    @Test
    void cleanOutscoresWrongAccentedLetter() {
        // Accented Latin letters that do not belong in German: ae->a-ring (U+00E5),
        // ss->thorn (U+00FE), ue->y-diaeresis (U+00FF).  No FFFD; z1 (coherence)
        // must rank the clean decode higher even though the substitutes are valid
        // Latin in some language.
        String wrong =
                "Die n\u00E5chste Stunde beginnt am Montag um neun Uhr im "
                + "gro\u00FEen Saal f\u00FFr alle Anf\u00E5nger";
        float clean = languageness(CLEAN);
        float broken = languageness(wrong);
        assertTrue(clean > broken,
                "clean German must outscore the wrong-accented-letter decode; "
                + "clean=" + clean + " wrong=" + broken);
    }

    @Test
    void allCapsAndTitleOutscoreAlternatingCase() {
        // Case-CONSISTENT real text (ALL-CAPS headings, Title Case) scores clean;
        // case-INCONSISTENT alternating case ("aLtErNaTiNg") is the junk pattern and
        // must floor.  The all-caps fix borrows the lowercase score for consistent
        // uppercase; the case-consistency gate keeps alternating case from rescue.
        String lower = "international organization standards committee meeting";
        String allCaps = lower.toUpperCase(Locale.ROOT);
        String title = toTitleCase(lower);
        String alt = toAlternatingCase(lower);
        float lLower = languageness(lower);
        float lAll = languageness(allCaps);
        float lTitle = languageness(title);
        float lAlt = languageness(alt);
        assertTrue(lAll > lAlt,
                "ALL-CAPS must outscore aLtErNaTiNg junk; allCaps=" + lAll + " alt=" + lAlt);
        assertTrue(lTitle > lAlt,
                "Title-case must outscore aLtErNaTiNg junk; title=" + lTitle + " alt=" + lAlt);
        assertTrue(lAll > lLower - 1.0f,
                "ALL-CAPS must score ~= lowercase (case-fold rescue); allCaps=" + lAll
                + " lower=" + lLower);
    }

    @Test
    void allCapsCyrillicOutscoresGibberishDecode() {
        // The real 150k regression (corpus file 1A68D...): all-caps Russian
        // "MUZEJ BUDUSchEGO" was scored BELOW a KOI8-R gibberish decode, so the
        // detector chose the gibberish.  The case-fold must rank the all-caps real
        // Russian like its lowercase form, above the gibberish.  ASCII source: the
        // Russian is written with Unicode escapes.
        String allCaps = "\u041C\u0423\u0417\u0415\u0419 \u0411\u0423\u0414\u0423\u0429\u0415\u0413\u041E";
        String lower = "\u043C\u0443\u0437\u0435\u0439 \u0431\u0443\u0434\u0443\u0449\u0435\u0433\u043E";
        String gibberish = "\u043B\u0441\u0433\u0435\u0438 \u0430\u0441\u0434\u0441\u044B\u0435\u0446\u043D";
        float lAll = languageness(allCaps);
        float lLower = languageness(lower);
        float lJunk = languageness(gibberish);
        assertTrue(lAll > lJunk,
                "all-caps real Russian must outscore KOI8-R gibberish; allCaps=" + lAll
                + " gibberish=" + lJunk);
        assertTrue(Math.abs(lAll - lLower) < 0.6f,
                "all-caps must score ~= lowercase; allCaps=" + lAll + " lower=" + lLower);
    }

    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                start = true;
                sb.append(c);
            } else if (start) {
                sb.append(Character.toUpperCase(c));
                start = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toAlternatingCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean up = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c));
                up = !up;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
