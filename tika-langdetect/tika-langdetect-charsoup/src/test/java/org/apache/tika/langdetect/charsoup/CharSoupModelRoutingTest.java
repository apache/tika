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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.parser.ParseContext;

/**
 * Correctness and routing tests for {@link CharSoupLanguageDetector}.
 * <p>
 * Covers two concerns:
 * <ol>
 *   <li>Basic language detection — short snippets that must be classified
 *       correctly, including script-gate regressions (TIKA-4662).</li>
 *   <li>Model routing logic — verifying that {@link CharSoupLanguageDetector.Strategy},
 *       {@link CharSoupDetectorConfig}, and the two automatic gates (length and
 *       feature-density) route to the correct model.</li>
 * </ol>
 * If any detection assertion fails after a model retrain, investigate the
 * training data rather than weakening the assertion.
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

    // -----------------------------------------------------------------------
    // Model-routing / switching-logic tests
    // -----------------------------------------------------------------------

    /**
     * Strategy.STANDARD must always use the general model, even for very short text.
     * The general model has more classes than the short-text model, so we verify the
     * result language comes from the general model's label set.
     */
    @Test
    public void testStrategyStandardAlwaysUsesGeneralModel() {
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "STANDARD"));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);

        // "Hi" is only 2 chars — well below length threshold — but STANDARD forces general model
        detector.addText("The children are playing");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        // Result label must be from the general model's label set
        Set<String> generalLabels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(generalLabels.contains(results.get(0).getLanguage()),
                "STANDARD strategy must return a label from the general model");
    }

    /**
     * Strategy.SHORT_TEXT must use the short-text model when it is loaded.
     * Skipped if the short-text model binary is absent from the classpath.
     */
    @Test
    public void testStrategyShortTextUsesShortModel() {
        Assumptions.assumeTrue(CharSoupLanguageDetector.SHORT_TEXT_MODEL != null,
                "Short-text model not loaded — skipping routing test");

        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(
                Map.of("strategy", "SHORT_TEXT"));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);

        // Long input — but SHORT_TEXT strategy forces the short-text model regardless
        detector.addText("The quick brown fox jumps over the lazy dog and then some more words");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        // Result label must be from the short-text model's label set
        Set<String> shortLabels = Arrays.stream(CharSoupLanguageDetector.SHORT_TEXT_MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(shortLabels.contains(results.get(0).getLanguage()),
                "SHORT_TEXT strategy must return a label from the short-text model");
    }

    /**
     * Strategy.AUTOMATIC with text shorter than the length threshold should
     * route to the short-text model when it is loaded.
     */
    @Test
    public void testAutomaticRoutesShortInputToShortModel() {
        Assumptions.assumeTrue(CharSoupLanguageDetector.SHORT_TEXT_MODEL != null,
                "Short-text model not loaded — skipping routing test");

        // Use a threshold larger than our input so the length gate fires
        int threshold = 500;
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(Map.of(
                "strategy", "AUTOMATIC",
                "lengthThreshold", threshold,
                "featureThreshold", 0));   // disable feature gate
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);

        // Text is well below the threshold
        detector.addText("The children are playing");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        Set<String> shortLabels = Arrays.stream(CharSoupLanguageDetector.SHORT_TEXT_MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(shortLabels.contains(results.get(0).getLanguage()),
                "AUTOMATIC with short input must route to short-text model");
    }

    /**
     * Strategy.AUTOMATIC with text longer than the length threshold and above the
     * feature threshold should route to the general model.
     */
    @Test
    public void testAutomaticRoutesLongInputToGeneralModel() {
        // Use thresholds of 0 so neither gate fires
        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(Map.of(
                "strategy", "AUTOMATIC",
                "lengthThreshold", 0,
                "featureThreshold", 0));
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);

        detector.addText("The children are playing in the park on a sunny afternoon");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        Set<String> generalLabels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(generalLabels.contains(results.get(0).getLanguage()),
                "AUTOMATIC with both gates disabled must route to general model");
    }

    /**
     * Degenerate input: 1 KB of whitespace followed by a single short word.
     * The feature-density gate must fire and route to the short-text model,
     * even though the raw character length far exceeds the length threshold.
     */
    @Test
    public void testFeatureDensityGateCatchesDegenerateInput() {
        Assumptions.assumeTrue(CharSoupLanguageDetector.SHORT_TEXT_MODEL != null,
                "Short-text model not loaded — skipping routing test");

        CharSoupDetectorConfig cfg = CharSoupDetectorConfig.fromMap(Map.of(
                "strategy", "AUTOMATIC",
                "lengthThreshold", 10,     // tiny — length gate won't fire on 1 KB
                "featureThreshold", 1000)); // large — ensures feature gate fires
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector(cfg);

        // 1 KB of spaces + a real sentence: length >> threshold, but emissions << featureThreshold
        String degenerate = " ".repeat(1000) + "The children are playing in the park";
        detector.addText(degenerate);
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        Set<String> shortLabels = Arrays.stream(CharSoupLanguageDetector.SHORT_TEXT_MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(shortLabels.contains(results.get(0).getLanguage()),
                "Degenerate input (whitespace + single word) must route to short-text model "
                        + "via feature-density gate");
    }

    /**
     * ParseContext injection: a per-document config supplied via ParseContext
     * must override the detector's constructed config for that document only.
     */
    @Test
    public void testParseContextConfigInjection() {
        // Detector constructed with AUTOMATIC
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();

        // Inject STANDARD override via ParseContext
        ParseContext ctx = new ParseContext();
        ctx.set(CharSoupDetectorConfig.class,
                CharSoupDetectorConfig.fromMap(Map.of("strategy", "STANDARD")));
        detector.reset(ctx);

        detector.addText("The children are playing in the park");
        List<LanguageResult> results = detector.detectAll();
        assertTrue(results.size() > 0);

        // Must have come from the general model
        Set<String> generalLabels = Arrays.stream(CharSoupLanguageDetector.MODEL.getLabels())
                .collect(Collectors.toSet());
        assertTrue(generalLabels.contains(results.get(0).getLanguage()),
                "ParseContext-injected STANDARD config must use general model");

        // After a plain reset(), the detector reverts to AUTOMATIC (constructed config)
        detector.reset();
        // internal activeConfig should be back to DEFAULT — verified indirectly by
        // confirming subsequent detection still works
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
