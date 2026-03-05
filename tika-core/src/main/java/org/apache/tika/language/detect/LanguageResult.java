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
package org.apache.tika.language.detect;

import java.util.Locale;

public class LanguageResult {

    // A result that indicates no match. Used when no language was detected.
    public static final LanguageResult NULL = new LanguageResult("", LanguageConfidence.NONE, 0.0f);

    private final String language;

    private final LanguageConfidence confidence;

    // rawScore should be a number from 0.0 to 1.0, with higher values implying
    // greater confidence.
    private final float rawScore;

    // Detector-agnostic confidence score (0.0 to 1.0, higher = more confident).
    // Detectors can populate this however makes sense for their internals
    // (e.g., entropy-derived for CharSoup, probability-based for OpenNLP).
    // Defaults to rawScore for backwards compatibility.
    private final float confidenceScore;

    /**
     * @param language ISO 639-1 language code (plus optional country code)
     * @param rawScore confidence of detector in the result.
     */
    public LanguageResult(String language, LanguageConfidence confidence, float rawScore) {
        this(language, confidence, rawScore, rawScore);
    }

    /**
     * @param language        ISO 639-1 language code (plus optional country code)
     * @param rawScore        detector-specific score (e.g., softmax probability)
     * @param confidenceScore detector-agnostic confidence (0.0 to 1.0, higher = more confident).
     *                        For comparing results across different decodings or detectors.
     */
    public LanguageResult(String language, LanguageConfidence confidence,
                          float rawScore, float confidenceScore) {
        this.language = language;
        this.confidence = confidence;
        this.rawScore = rawScore;
        this.confidenceScore = confidenceScore;
    }

    /**
     * The ISO 639-1 language code (plus optional country code)
     *
     * @return a string representation of the language code
     */
    public String getLanguage() {
        return language;
    }

    public float getRawScore() {
        return rawScore;
    }

    /**
     * Detector-agnostic confidence score (0.0 to 1.0). Higher values indicate
     * the detector is more confident in the result. This can be used to compare
     * results across different text decodings (e.g., for encoding detection)
     * without knowing the detector implementation.
     */
    public float getConfidenceScore() {
        return confidenceScore;
    }

    public LanguageConfidence getConfidence() {
        return confidence;
    }

    public boolean isReasonablyCertain() {
        return confidence == LanguageConfidence.HIGH;
    }

    public boolean isUnknown() {
        return confidence == LanguageConfidence.NONE;
    }

    /**
     * Return true if the target language matches the detected language. We consider
     * it a match if, for the precision requested or detected, it matches. This means:
     * <p>
     * target | detected | match?
     * zh | en | false
     * zh | zh | true
     * zh | zh-CN | true
     * zh-CN | zh | true
     * zh-CN | zh-TW | false
     * zh-CN | zh-cn | true (case-insensitive)
     *
     * @param language
     * @return
     */
    public boolean isLanguage(String language) {
        String[] targetLanguage = language.split("\\-");
        String[] resultLanguage = this.language.split("\\-");

        int minLength = Math.min(targetLanguage.length, resultLanguage.length);
        for (int i = 0; i < minLength; i++) {
            if (!targetLanguage[i].equalsIgnoreCase(resultLanguage[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s: %s (%f)", language, confidence, rawScore);
    }
}
