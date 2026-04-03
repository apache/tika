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
 * JSON keys (all optional; unrecognised keys are ignored):
 * <pre>
 * {
 *   "strategy" : "AUTOMATIC"   // STANDARD | AUTOMATIC | GLM
 * }
 * </pre>
 *
 * @see CharSoupLanguageDetector.Strategy
 */
public final class CharSoupDetectorConfig {

    public static final CharSoupDetectorConfig DEFAULT = new CharSoupDetectorConfig(
            CharSoupLanguageDetector.Strategy.AUTOMATIC);

    private final CharSoupLanguageDetector.Strategy strategy;

    private CharSoupDetectorConfig(CharSoupLanguageDetector.Strategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy must not be null");
        }
        this.strategy = strategy;
    }

    public static CharSoupDetectorConfig fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return DEFAULT;
        }
        CharSoupLanguageDetector.Strategy strategy = DEFAULT.strategy;

        Object s = map.get("strategy");
        if (s != null) {
            strategy = CharSoupLanguageDetector.Strategy.valueOf(
                    s.toString().toUpperCase(Locale.ROOT));
        }
        return new CharSoupDetectorConfig(strategy);
    }

    public CharSoupLanguageDetector.Strategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return "CharSoupDetectorConfig{strategy=" + strategy + "}";
    }
}
