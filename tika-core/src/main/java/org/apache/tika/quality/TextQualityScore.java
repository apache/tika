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
package org.apache.tika.quality;

/**
 * Result of scoring a string for text quality via a {@link TextQualityDetector}.
 *
 * <p>{@code zScore} is the primary output: how many standard deviations below
 * typical clean text this string scores on its dominant script's model.
 * Negative means worse than average clean text; more negative means worse.
 *
 * <p>{@code pClean} is a probability estimate in [0,1] that this is clean text.
 *
 * <p>{@code ciLow} / {@code ciHigh} are the 95% confidence interval bounds on
 * {@code zScore}.  For short strings these bounds are wide; for long strings
 * they narrow.  Prefer {@code ciLow < threshold} over {@code zScore < threshold}
 * when triggering actions, to reduce false positives on short strings.
 */
public final class TextQualityScore {

    /** Sentinel z-score returned when scoring could not be run (e.g. null or empty input). */
    public static final float UNKNOWN = Float.NaN;

    private final float zScore;
    private final float pClean;
    private final float ciLow;
    private final float ciHigh;
    private final String dominantScript;

    public TextQualityScore(float zScore, float pClean,
                            float ciLow, float ciHigh,
                            String dominantScript) {
        this.zScore = zScore;
        this.pClean = pClean;
        this.ciLow = ciLow;
        this.ciHigh = ciHigh;
        this.dominantScript = dominantScript;
    }

    /** Z-score relative to clean text for the detected script. 0 = average clean; negative = worse. */
    public float getZScore() {
        return zScore;
    }

    /** Probability in [0,1] that this string is clean text. */
    public float getPClean() {
        return pClean;
    }

    /** Lower bound of the 95% confidence interval on zScore. */
    public float getCiLow() {
        return ciLow;
    }

    /** Upper bound of the 95% confidence interval on zScore. */
    public float getCiHigh() {
        return ciHigh;
    }

    /** Name of the dominant Unicode script detected, e.g. "LATIN", "CYRILLIC", "ARABIC". */
    public String getDominantScript() {
        return dominantScript;
    }

    /** True if scoring could not be performed (e.g. empty or unsupported-script input). */
    public boolean isUnknown() {
        return Float.isNaN(zScore);
    }

    @Override
    public String toString() {
        if (isUnknown()) {
            return "TextQualityScore[UNKNOWN script=" + dominantScript + "]";
        }
        return String.format("TextQualityScore[z=%.3f p=%.3f ci=(%.3f,%.3f) script=%s]",
                zScore, pClean, ciLow, ciHigh, dominantScript);
    }
}
