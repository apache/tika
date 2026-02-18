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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.apache.tika.language.detect.LanguageResult;

/**
 * End-to-end regression test for {@link CharSoupLanguageDetector}.
 * <p>
 * Each case runs a representative sentence through the full pipeline
 * (preprocessing → bigram extraction → softmax → group collapsing) and checks:
 * <ol>
 *   <li>The top-ranked language is the expected ISO 639-3 code.</li>
 *   <li>The raw score exceeds {@link #MIN_SCORE}, confirming the model is
 *       genuinely confident rather than picking the least-bad option.</li>
 * </ol>
 * <p>
 * <strong>If this test fails after a code change</strong> you have almost
 * certainly altered {@link CharSoupFeatureExtractor} in a way that diverges
 * from how the model was trained. Either revert the change or retrain.
 * <p>
 * <strong>If this test fails after deliberately retraining</strong> update
 * the expected language codes here to match the new model — do not lower
 * {@link #MIN_SCORE} to paper over a quality regression.
 */
public class LangIdRegressionTest {

    /**
     * Minimum raw softmax score the correct language must achieve.
     * Unambiguous sentences in supported languages should comfortably exceed 0.5.
     * Lowering this threshold to make a failing test pass is a sign that the
     * model or the feature pipeline has regressed.
     */
    private static final float MIN_SCORE = 0.50f;

    @Test
    public void testDetectsCorrectLanguage() {
        // Latin-script languages
        assertDetects("eng",
                "The Parliament of the United Kingdom consists of the Sovereign, "
                        + "the House of Lords, and the House of Commons.");
        assertDetects("deu",
                "Der Deutsche Bundestag ist das Parlament der Bundesrepublik Deutschland "
                        + "und besteht aus direkt gewählten Abgeordneten.");
        assertDetects("fra",
                "La République française est une démocratie laïque dont la devise est "
                        + "Liberté, Égalité, Fraternité.");
        assertDetects("spa",
                "El español es una lengua romance procedente del latín vulgar hablada "
                        + "principalmente en España y en América Latina.");
        assertDetects("por",
                "A língua portuguesa é uma língua indo-europeia românica falada "
                        + "principalmente em Portugal e no Brasil.");
        assertDetects("ita",
                "La lingua italiana è una lingua romanza parlata principalmente "
                        + "in Italia e in alcune regioni limitrofe.");
        assertDetects("nld",
                "Het Nederlands is een West-Germaanse taal die in Nederland, "
                        + "België en Suriname als officiële taal wordt gebruikt.");
        assertDetects("pol",
                "Język polski jest językiem z grupy słowiańskiej i należy do rodziny "
                        + "indoeuropejskiej, urzędowym językiem Polski.");
        assertDetects("swe",
                "Svenska är ett östnordiskt språk som talas av ungefär tio miljoner "
                        + "personer, främst i Sverige och Finland.");
        assertDetects("tur",
                "Türkçe, Türk dil ailesinin Oğuz grubuna ait bir dil olup "
                        + "ağırlıklı olarak Türkiye'de konuşulmaktadır.");
        assertDetects("fin",
                "Suomi on uralilainen kieli, jota puhutaan pääasiassa Suomessa "
                        + "ja joka on maan virallinen kieli.");

        // Cyrillic-script languages
        assertDetects("rus",
                "Российская Федерация — государство в Восточной Европе и Северной "
                        + "Азии, занимающее первое место в мире по территории.");
        assertDetects("ukr",
                "Українська мова є слов'янською мовою і є офіційною мовою України, "
                        + "однією з найпоширеніших мов в Європі.");
        assertDetects("bul",
                "Българският език е индоевропейски език от групата на южнославянските "
                        + "езици и е официален език на Република България.");

        // Arabic-script languages
        assertDetects("ara",
                "اللغة العربية إحدى اللغات السامية، وهي أكثر اللغات انتشاراً "
                        + "وتحدثاً في العالم العربي والشرق الأوسط.");
        // fas / prs / pes are in the same confusable group — any is correct
        assertDetects("prs",
                "زبان فارسی یکی از زبان‌های هندواروپایی است که در ایران، افغانستان "
                        + "و تاجیکستان به عنوان زبان رسمی استفاده می‌شود.");

        // CJK and other East Asian
        assertDetects("zho",
                "中华人民共和国位于亚洲东部，是世界上人口最多的国家，也是世界第二大经济体。");
        assertDetects("jpn",
                "日本語は日本国の公用語として広く使用されており、約一億三千万人の話者がいる言語である。");
        assertDetects("kor",
                "한국어는 대한민국과 조선민주주의인민공화국의 공용어이며 약 칠천오백만 명이 사용한다.");

        // Indic scripts
        assertDetects("hin",
                "हिन्दी भारत की राजभाषा है और भारत में सबसे अधिक बोली जाने वाली भाषाओं में से एक है।");
        assertDetects("ben",
                "বাংলা ভাষা বাংলাদেশের রাষ্ট্রভাষা এবং ভারতের পশ্চিমবঙ্গ রাজ্যের সরকারি ভাষা।");

        // Greek
        assertDetects("ell",
                "Η ελληνική γλώσσα είναι μια από τις πιο παλιές γλώσσες στον κόσμο "
                        + "και η επίσημη γλώσσα της Ελλάδας και της Κύπρου.");
    }

    private static void assertDetects(String expectedLang, String text) {
        CharSoupLanguageDetector detector = new CharSoupLanguageDetector();
        detector.addText(text);
        List<LanguageResult> results = detector.detectAll();

        assertTrue(results.size() > 0,
                "detectAll returned no results for: " + text.substring(0, 30));

        LanguageResult top = results.get(0);
        assertEquals(expectedLang, top.getLanguage(),
                String.format(Locale.US, "Expected '%s' but got '%s' (score=%.3f) for: %s",
                        expectedLang, top.getLanguage(), top.getRawScore(),
                        text.substring(0, Math.min(50, text.length()))));

        assertTrue(top.getRawScore() >= MIN_SCORE,
                String.format(Locale.US, "'%s' score %.3f is below MIN_SCORE %.3f for: %s",
                        expectedLang, top.getRawScore(), MIN_SCORE,
                        text.substring(0, Math.min(50, text.length()))));
    }
}
