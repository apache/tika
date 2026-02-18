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
package org.apache.tika.eval.core.tokens;

import java.util.function.Consumer;

/**
 * Manages tokenization for tika-eval. Uses {@link TikaEvalTokenizer} in
 * {@link TikaEvalTokenizer.Mode#STANDARD STANDARD} mode, which includes
 * alphabetic, ideographic, and numeric tokens with NFKD normalization,
 * case folding, and CJK bigrams. No minimum length filter or skip list
 * is applied — those are only used in
 * {@link TikaEvalTokenizer.Mode#COMMON_TOKENS COMMON_TOKENS} mode.
 */
public class AnalyzerManager {

    private final int maxTokens;

    private AnalyzerManager(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public static AnalyzerManager newInstance(int maxTokens) {
        return new AnalyzerManager(maxTokens);
    }

    /**
     * Tokenize the given text and return a TokenCounts object.
     *
     * @param text input text
     * @return token counts
     */
    public TokenCounts tokenize(String text) {
        TokenCounts counts = new TokenCounts();
        TikaEvalTokenizer.tokenize(text, TikaEvalTokenizer.Mode.STANDARD, maxTokens,
                counts::increment);
        return counts;
    }

    /**
     * Tokenize and stream tokens to a consumer, respecting maxTokens limit.
     *
     * @param text     input text
     * @param consumer receives each token string
     */
    public void tokenize(String text, Consumer<String> consumer) {
        TikaEvalTokenizer.tokenize(text, TikaEvalTokenizer.Mode.STANDARD, maxTokens, consumer);
    }

    /**
     * Get the max token limit.
     */
    public int getMaxTokens() {
        return maxTokens;
    }
}
