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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.parser.ParseContext;

/**
 * Correctness and strategy-routing tests for {@link CharSoupLanguageDetector}.
 */
public class CharSoupModelRoutingTest {

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
        assertDetects("spa", "El niño juega en el jardín con sus amigos");
    }

    @Test
    public void testJapaneseShortText() {
        assertDetects("jpn", "日本語のテキストです");
    }

    /** Mixed kanji + hiragana + katakana — typical real Japanese. */
    @Test
    public void testJapaneseMixedScript() {
        assertDetects("jpn", "東京タワーは日本の観光名所であり、多くのツアーが開催されています");
    }

    @Test
    public void testKoreanShortText() {
        assertDetects("kor", "한국어 텍스트입니다");
    }

    @Test
    public void testUcasTextNotEnglish() {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.addText("ᐊᒻᒪᓗ ᑭᒃᑯᑐᐃᓐᓇᐃᑦ");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0, "detectAll returned no results");
        assertNotEquals("eng", results.get(0).getLanguage(),
                "UCAS text must not be classified as English");
    }

    // -----------------------------------------------------------------------
    // Strategy tests
    // -----------------------------------------------------------------------

    @Test
    public void testStrategyStandardNoGlm() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "STANDARD"));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);
        detector.addText("The children are playing");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);
        Set<String> labels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(labels.contains(results.get(0).getLanguage()),
                "STANDARD strategy must return a label from the discriminative model");
    }

    @Test
    public void testStrategyGlm() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "GLM"));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);
        detector.addText("The children are playing in the park on a sunny afternoon");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);
        Set<String> labels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(labels.contains(results.get(0).getLanguage()),
                "GLM strategy must return a label from the discriminative model");
    }

    @Test
    public void testStrategyAutomatic() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "AUTOMATIC"));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);
        detector.addText("The children are playing in the park on a sunny afternoon");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);
    }

    @Test
    public void testParseContextConfigInjection() {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();

        ParseContext ctx = new ParseContext();
        ctx.set(CharSoupDetectorConfig.class,
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "STANDARD")));
        detector.reset(ctx);

        detector.addText("The children are playing in the park");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        Set<String> generalLabels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(generalLabels.contains(results.get(0).getLanguage()),
                "ParseContext-injected STANDARD config must use general model");

        detector.reset();
        detector.addText("The children are playing in the park");
        assertNotNull(detector.detectAll());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

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
