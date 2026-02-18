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
package org.apache.tika.eval.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.tokens.AnalyzerManager;
import org.apache.tika.eval.core.tokens.TikaEvalTokenizer;
import org.apache.tika.eval.core.tokens.TokenCounts;

public class AnalyzerManagerTest {

    @Test
    public void testGeneral() {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(100000);
        TokenCounts counts = analyzerManager.tokenize(
                "tHe quick aaaa aaa anD dirty dog");

        assertTrue(counts.getTokens().containsKey("the"));
        assertTrue(counts.getTokens().containsKey("and"));
        assertTrue(counts.getTokens().containsKey("dog"));
    }

    @Test
    public void testCommon() {
        List<String> tokens = TikaEvalTokenizer.tokenize(
                "th 5,000.12 5000 and dirty dog",
                TikaEvalTokenizer.Mode.COMMON_TOKENS);

        // tokens shorter than 3 chars and numbers excluded in COMMON_TOKENS mode
        assertFalse(tokens.contains("th"));
        assertFalse(tokens.contains("5000"));

        assertTrue(tokens.contains("dirty"));
        assertTrue(tokens.contains("dog"));
    }

    @Test
    public void testTokenCountLimit() {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(1000000);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1001000; i++) {
            sb.append("the ");
        }
        TokenCounts counts = analyzerManager.tokenize(sb.toString());
        assertEquals(1000000, counts.getTotalTokens());
    }
}
