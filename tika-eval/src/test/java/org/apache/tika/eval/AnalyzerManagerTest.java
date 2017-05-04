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

package org.apache.tika.eval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.tika.eval.tokens.AlphaIdeographFilterFactory;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.junit.Test;

public class AnalyzerManagerTest {

    @Test
    public void testGeneral() throws Exception {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(100000);
        Analyzer general = analyzerManager.getGeneralAnalyzer();
        TokenStream ts = general.tokenStream("f", "tHe quick aaaa aaa anD dirty dog");
        ts.reset();

        CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
        Set<String> seen = new HashSet<>();
        while (ts.incrementToken()) {
            seen.add(termAtt.toString());
        }
        ts.end();
        ts.close();

        assertTrue(seen.contains("the"));
        assertTrue(seen.contains("and"));
        assertTrue(seen.contains("dog"));

    }

    @Test
    public void testCommon() throws Exception {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(100000);
        Analyzer common = analyzerManager.getCommonTokensAnalyzer();
        TokenStream ts = common.tokenStream("f", "the 5,000.12 and dirty dog");
        ts.reset();
        CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
        Set<String> seen = new HashSet<>();
        while (ts.incrementToken()) {
            String t = termAtt.toString();
            if (AlphaIdeographFilterFactory.isAlphabetic(t.toCharArray()) && t.contains("5")) {
                fail("Shouldn't have found a numeric");
            }
            seen.add(termAtt.toString());
        }
        ts.end();
        ts.close();

        assertTrue(seen.contains("dirty"));
        assertFalse(seen.contains("the"));

    }

    @Test
    public void testTokenCountFilter() throws Exception {
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(1000000);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1001000; i++) {
            sb.append("the ");
        }
        TokenStream ts = analyzerManager.getGeneralAnalyzer().tokenStream("f", sb.toString());
        ts.reset();
        CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
        int tokens = 0;
        while (ts.incrementToken()) {
            tokens++;
        }

        assertEquals(1000000, tokens);

    }

}
