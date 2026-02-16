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

import java.util.List;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.utils.CompareUtils;

/**
 * Scores text quality by measuring how well character bigram frequencies
 * match known language profiles. Uses average log-likelihood per bigram,
 * which is naturally normalized across different text lengths.
 *
 * <p>Implementations are discovered via the ServiceLoader/SPI mechanism.
 * When no implementation is on the classpath, {@link #getDefault()} returns
 * a no-op scorer that always returns a neutral result.</p>
 *
 * <p>Use cases include:
 * <ul>
 *   <li>RTL text direction detection (score both variants, pick higher)</li>
 *   <li>Charset detection (score text decoded with candidate charsets)</li>
 *   <li>Junk/quality filtering (score below threshold = garbled text)</li>
 * </ul>
 */
public abstract class TextQualityScorer {

    private static final ServiceLoader DEFAULT_SERVICE_LOADER =
            new ServiceLoader();

    /**
     * Get the default TextQualityScorer. If no implementation is available
     * on the classpath, returns a no-op scorer.
     */
    public static TextQualityScorer getDefault() {
        List<TextQualityScorer> scorers = getScorers();
        if (scorers.isEmpty()) {
            return NoOpTextQualityScorer.INSTANCE;
        }
        return scorers.get(0);
    }

    public static List<TextQualityScorer> getScorers() {
        return getScorers(DEFAULT_SERVICE_LOADER);
    }

    public static List<TextQualityScorer> getScorers(ServiceLoader loader) {
        List<TextQualityScorer> scorers =
                loader.loadStaticServiceProviders(TextQualityScorer.class);
        scorers.sort(CompareUtils::compareClassName);
        return scorers;
    }

    /**
     * Score the quality of the given text. Returns the average
     * log2-likelihood per character bigram under the best-matching
     * language profile, along with the detected language.
     */
    public abstract TextQualityResult score(CharSequence text);

    /**
     * Score text against a specific language profile. Use this for
     * comparison use cases (RTL direction, charset detection) where
     * two text variants should be scored against the same language.
     *
     * @param text     text to score
     * @param language ISO 639-3 language code
     * @return score against that language, or neutral result if
     *         the language is not available
     */
    public abstract TextQualityResult score(CharSequence text,
                                            String language);

    /**
     * No-op implementation returned when no real scorer is on the classpath.
     */
    private static class NoOpTextQualityScorer extends TextQualityScorer {
        static final NoOpTextQualityScorer INSTANCE =
                new NoOpTextQualityScorer();

        @Override
        public TextQualityResult score(CharSequence text) {
            return new TextQualityResult(0.0, "unk", 0.0, 0);
        }

        @Override
        public TextQualityResult score(CharSequence text, String language) {
            return new TextQualityResult(0.0, "unk", 0.0, 0);
        }
    }
}
