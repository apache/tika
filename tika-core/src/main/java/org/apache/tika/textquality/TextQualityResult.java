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
package org.apache.tika.textquality;

/**
 * Result of scoring text quality against language models.
 * The score is the average log-likelihood per character bigram
 * under the best-matching language profile. Higher (less negative)
 * scores indicate text that better matches known language patterns.
 *
 * <p>Scores are designed for comparison, not absolute thresholds.
 * Compare two text variants (e.g., forward vs reversed, charset A
 * vs charset B) against the same language profile — the higher
 * score indicates the better variant.</p>
 */
public class TextQualityResult {

    private final double score;
    private final String language;
    private final double confidence;
    private final int bigramCount;

    public TextQualityResult(double score, String language,
                             double confidence, int bigramCount) {
        this.score = score;
        this.language = language;
        this.confidence = confidence;
        this.bigramCount = bigramCount;
    }

    /**
     * Average log2-likelihood per bigram under the best-matching
     * language profile. Typical range: -6 to -10 for good text,
     * below -12 for garbled/reversed text. Language-dependent.
     */
    public double getScore() {
        return score;
    }

    /**
     * ISO 639-3 language code of the best-matching profile.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Score difference between best and second-best language match.
     * Higher values indicate more confident language identification.
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * Number of character bigrams extracted from the input text.
     * Low counts (below ~20) produce unreliable scores.
     */
    public int getBigramCount() {
        return bigramCount;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "TextQualityResult[score=%.4f, lang=%s, "
                        + "confidence=%.4f, bigrams=%d]",
                score, language, confidence, bigramCount);
    }
}
