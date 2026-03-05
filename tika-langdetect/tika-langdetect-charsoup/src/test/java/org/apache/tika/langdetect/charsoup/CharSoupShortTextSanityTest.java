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
package org.apache.tika.langdetect.charsoup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.apache.tika.language.detect.LanguageResult;

/**
 * Short-text sanity checks for {@link CharSoupLanguageDetector}.
 * <p>
 * These 30-character-or-so snippets catch regressions where the model
 * misclassifies short Latin text as a non-Latin language, or vice-versa.
 * They also verify the inference-time script gate introduced in TIKA-4662:
 * UCAS text must never surface as English, and CJK/Korean inputs must
 * stay in their script family.
 * <p>
 * If any of these assertions fail after a model retrain, investigate the
 * training data rather than weakening the assertion.
 */
public class CharSoupShortTextSanityTest {

    @Test
    public void testEnglishShortText() {
        assertDetects("eng", "The children are playing in the park");
    }

    @Test
    public void testFrenchShortText() {
        assertDetects("fra", "Le chat est sur le tapis et");
    }

    @Test
    public void testGermanShortText() {
        assertDetects("deu", "Der Hund saß auf der Matte");
    }

    @Test
    public void testSpanishShortText() {
        assertDetects("spa", "El perro estaba sobre la");
    }

    @Test
    public void testJapaneseShortText() {
        assertDetects("jpn", "日本語のテキストです");
    }

    @Test
    public void testKoreanShortText() {
        assertDetects("kor", "한국어 텍스트입니다");
    }

    /**
     * TIKA-4662 regression: UCAS (Inuktitut syllabics) text must NOT be
     * classified as English. The inference-time script gate should zero out
     * all Latin-script classes when the input is predominantly UCAS.
     */
    @Test
    public void testUcasTextNotEnglish() {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.addText("ᐊᒻᒪᓗ ᑭᒃᑯᑐᐃᓐᓇᐃᑦ");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0, "detectAll returned no results");
        assertNotEquals("eng", results.get(0).getLanguage(),
                "UCAS text must not be classified as English");
    }

    private static void assertDetects(String expectedLang, String text) {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.addText(text);
        List<LanguageResult> results = detector.detectAll();

        assertTrue(results.size() > 0,
                "detectAll returned no results for: " + text);

        LanguageResult top = results.get(0);
        assertEquals(expectedLang, top.getLanguage(),
                String.format(Locale.US,
                        "Expected '%s' but got '%s' (score=%.3f) for: %s",
                        expectedLang, top.getLanguage(), top.getRawScore(), text));
    }
}
