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

import java.util.Locale;
import java.util.Map;

/**
 * Immutable configuration for {@link CharSoupLanguageDetector}.
 * <p>
 * Instances are constructed via {@link #DEFAULT} (for typical use) or
 * {@link #fromMap(Map)} (for JSON-driven configuration via ParseContext).
 * There are no setters — create a new instance if different parameters
 * are needed.
 * <p>
 * JSON keys (all optional; unrecognised keys are ignored):
 * <pre>
 * {
 *   "strategy"         : "AUTOMATIC",   // AUTOMATIC | SHORT_TEXT | STANDARD
 *   "lengthThreshold"  : 200,           // chars below which short-text model is preferred
 *   "featureThreshold" : 200            // n-gram emissions below which short-text model is preferred
 * }
 * </pre>
 *
 * @see CharSoupLanguageDetector.Strategy
 */
public final class CharSoupDetectorConfig {

    /**
     * Default configuration: automatic model selection, default thresholds.
     * TODO: tune lengthThreshold and featureThreshold from ablation crossover data.
     */
    public static final CharSoupDetectorConfig DEFAULT = new CharSoupDetectorConfig(
            CharSoupLanguageDetector.Strategy.AUTOMATIC,
            CharSoupLanguageDetector.SHORT_TEXT_LENGTH_THRESHOLD,
            CharSoupLanguageDetector.SHORT_TEXT_FEATURE_THRESHOLD);

    private final CharSoupLanguageDetector.Strategy strategy;
    private final int lengthThreshold;
    private final int featureThreshold;

    private CharSoupDetectorConfig(CharSoupLanguageDetector.Strategy strategy,
                                   int lengthThreshold,
                                   int featureThreshold) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        if (lengthThreshold < 0 || featureThreshold < 0) {
            throw new IllegalArgumentException("thresholds must be non-negative");
        }
        this.strategy = strategy;
        this.lengthThreshold = lengthThreshold;
        this.featureThreshold = featureThreshold;
    }

    /**
     * Deserialize from a plain string-to-object map (as produced by a JSON parser).
     * Unrecognised keys are silently ignored; missing keys use DEFAULT values.
     *
     * @param map JSON-decoded config map; may be null or empty
     * @return configured instance
     * @throws IllegalArgumentException if a value is present but invalid
     */
    public static CharSoupDetectorConfig fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return DEFAULT;
        }
        CharSoupLanguageDetector.Strategy strategy = DEFAULT.strategy;
        int lengthThreshold = DEFAULT.lengthThreshold;
        int featureThreshold = DEFAULT.featureThreshold;

        Object s = map.get("strategy");
        if (s != null) {
            strategy = CharSoupLanguageDetector.Strategy.valueOf(
                    s.toString().toUpperCase(Locale.ROOT));
        }
        Object lt = map.get("lengthThreshold");
        if (lt != null) {
            lengthThreshold = ((Number) lt).intValue();
        }
        Object ft = map.get("featureThreshold");
        if (ft != null) {
            featureThreshold = ((Number) ft).intValue();
        }
        return new CharSoupDetectorConfig(strategy, lengthThreshold, featureThreshold);
    }

    public CharSoupLanguageDetector.Strategy getStrategy() {
        return strategy;
    }

    public int getLengthThreshold() {
        return lengthThreshold;
    }

    public int getFeatureThreshold() {
        return featureThreshold;
    }

    @Override
    public String toString() {
        return "CharSoupDetectorConfig{strategy=" + strategy
                + ", lengthThreshold=" + lengthThreshold
                + ", featureThreshold=" + featureThreshold + "}";
    }
}
