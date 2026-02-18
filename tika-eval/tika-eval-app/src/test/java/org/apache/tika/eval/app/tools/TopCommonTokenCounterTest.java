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
package org.apache.tika.eval.app.tools;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.tokens.TikaEvalTokenizer;

public class TopCommonTokenCounterTest {

    @Test
    public void testTokenization() {
        // 2-char alphabetic tokens are dropped in COMMON_TOKENS mode
        List<String> tokens = TikaEvalTokenizer.tokenize(
                "th quick brown fox jumped lazy",
                TikaEvalTokenizer.Mode.COMMON_TOKENS);

        assertTrue(tokens.contains("brown"));
        assertTrue(tokens.contains("lazy"));
        assertFalse(tokens.contains("th"));  // 2-char dropped

        // CJK: only bigrams, no unigrams or trigrams
        List<String> cjk = TikaEvalTokenizer.tokenize(
                "\u666e\u6797\u65af\u987f\u5927\u5b66",
                TikaEvalTokenizer.Mode.COMMON_TOKENS);

        assertTrue(cjk.contains("\u5927\u5b66"));           // bigram ok
        assertFalse(cjk.contains("\u5b66"));                // unigram excluded
        assertFalse(cjk.contains("\u987f\u5927\u5b66"));    // trigram excluded
    }
}
