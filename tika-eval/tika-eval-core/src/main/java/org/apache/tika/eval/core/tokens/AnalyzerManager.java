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

import org.apache.tika.langdetect.charsoup.WordTokenizer;

/**
 * Manages tokenization for tika-eval. Replaces the former Lucene analyzer-based
 * implementation with the shared WordTokenizer from tika-langdetect-charsoup.
 * <p>
 * The tokenizer uses the same preprocessing pipeline as the language detector:
 * NFC normalization, URL/email stripping, case folding via Character.toLowerCase.
 * Alphabetic text produces whole words; ideographic text produces character bigrams.
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
     * Emits both alphabetic words and numeric tokens.
     *
     * @param text input text
     * @return token counts
     */
    public TokenCounts tokenize(String text) {
        TokenCounts counts = new TokenCounts();
        int[] tokenCount = {0};
        WordTokenizer.tokenizeAlphanumeric(text, token -> {
            if (tokenCount[0] < maxTokens) {
                counts.increment(token);
                tokenCount[0]++;
            }
        });
        return counts;
    }

    /**
     * Tokenize and stream tokens to a consumer, respecting maxTokens limit.
     * Emits both alphabetic words and numeric tokens.
     *
     * @param text     input text
     * @param consumer receives each token string
     */
    public void tokenize(String text, Consumer<String> consumer) {
        int[] tokenCount = {0};
        WordTokenizer.tokenizeAlphanumeric(text, token -> {
            if (tokenCount[0] < maxTokens) {
                consumer.accept(token);
                tokenCount[0]++;
            }
        });
    }

    /**
     * Get the max token limit.
     */
    public int getMaxTokens() {
        return maxTokens;
    }
}
