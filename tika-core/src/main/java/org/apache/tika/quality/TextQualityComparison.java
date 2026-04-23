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
 * Result of comparing two candidate strings for text quality via
 * {@link TextQualityDetector#compare}.
 *
 * <p>A typical use is charset-decoding arbitration: given raw bytes decoded
 * two different ways (e.g. cp1251 vs cp1252), pass each decoded string with a
 * label and let the detector pick the cleaner one.
 *
 * <p>The {@code delta} field is the absolute difference between the two z-scores.
 * A delta near zero means the model is uncertain; larger values indicate
 * confident discrimination.  As a rough guide: delta &gt; 1.0 is useful signal,
 * delta &gt; 3.0 is confident.
 */
public final class TextQualityComparison {

    private final String winner;
    private final float delta;
    private final TextQualityScore scoreA;
    private final TextQualityScore scoreB;
    private final String labelA;
    private final String labelB;

    public TextQualityComparison(String winner, float delta,
                                 TextQualityScore scoreA, TextQualityScore scoreB,
                                 String labelA, String labelB) {
        this.winner = winner;
        this.delta = delta;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.labelA = labelA;
        this.labelB = labelB;
    }

    /**
     * Returns {@code "A"} if candidate A is cleaner, {@code "B"} otherwise.
     * Check {@link #delta()} to gauge confidence.
     */
    public String winner() {
        return winner;
    }

    /**
     * Absolute difference in z-scores between the two candidates.
     * Small delta = uncertain; large delta = confident.
     */
    public float delta() {
        return delta;
    }

    /** Quality score for candidate A. */
    public TextQualityScore scoreA() {
        return scoreA;
    }

    /** Quality score for candidate B. */
    public TextQualityScore scoreB() {
        return scoreB;
    }

    /** Label supplied for candidate A (e.g. a charset name or encoding description). */
    public String labelA() {
        return labelA;
    }

    /** Label supplied for candidate B. */
    public String labelB() {
        return labelB;
    }

    @Override
    public String toString() {
        return String.format("TextQualityComparison[winner=%s(%s) delta=%.3f A=%s B=%s]",
                winner, winner.equals("A") ? labelA : labelB,
                delta, scoreA, scoreB);
    }
}
