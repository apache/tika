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

package org.apache.tika.eval.tokens;


import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.BeforeClass;
import org.junit.Test;

public class TokenCounterTest {
    private final static String FIELD = "f";
    private static AnalyzerManager analyzerManager;

    private final int topN = 10;

    @BeforeClass
    public static void setUp() throws IOException {
        analyzerManager = AnalyzerManager.newInstance(100000);

    }

    @Test
    public void testBasic() throws Exception {
        String s = " bde cde def abc efg f f f f ghijklmnop a a a a a a a a a a a a a a a a a b b b b b b b b b b b b b";
        TokenCounter counter = new TokenCounter(analyzerManager.getGeneralAnalyzer());
        counter.add(FIELD, s);
        TokenStatistics simpleTokenStatistics = counter.getTokenStatistics(FIELD);
        LuceneTokenCounter tokenCounter = new LuceneTokenCounter(analyzerManager.getGeneralAnalyzer());
        tokenCounter.add(FIELD, s);
        assertEquals(simpleTokenStatistics, tokenCounter.getTokenStatistics(FIELD));
    }

    @Test
    public void testRandom() throws Exception {

        long simple = 0;
        long lucene = 0;
        int numberOfTests = 100;
        for (int i = 0; i < numberOfTests; i++) {
            String s = generateString();
            long start = new Date().getTime();
            TokenCounter counter = new TokenCounter(analyzerManager.getGeneralAnalyzer());
            counter.add(FIELD, s);
            simple += new Date().getTime()-start;
            TokenStatistics simpleTokenStatistics = counter.getTokenStatistics(FIELD);

            start = new Date().getTime();
            LuceneTokenCounter tokenCounter = new LuceneTokenCounter(analyzerManager.getGeneralAnalyzer());
            tokenCounter.add(FIELD, s);
            lucene += new Date().getTime()-start;
            assertEquals(s, simpleTokenStatistics, tokenCounter.getTokenStatistics(FIELD));
        }
    }

    @Test
    public void testCommonTokens() throws Exception {
        TokenCounter tokenCounter = new TokenCounter(analyzerManager.getCommonTokensAnalyzer());
        String s = "the http://www.cnn.com and blahdeblah@apache.org are in valuable www.sites.org 普林斯顿大学";
        tokenCounter.add(FIELD, s);
        Map<String, MutableInt> tokens = tokenCounter.getTokens(FIELD);
        assertEquals(new MutableInt(2), tokens.get("___url___"));
        assertEquals(new MutableInt(1), tokens.get("___email___"));
    }

    @Test
    public void testCJKFilter() throws Exception {
        String s = "then quickbrownfoxjumpedoverthelazy dogss dog 普林斯顿大学";
        Analyzer analyzer = analyzerManager.getCommonTokensAnalyzer();
        TokenStream ts = analyzer.tokenStream(FIELD, s);
        CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
        ts.reset();
        Map<String, Integer> tokens = new HashMap<>();
        while (ts.incrementToken()) {
            String t = termAtt.toString();
            Integer count = tokens.get(t);
            count = (count == null) ? count = 0 : count;
            count++;
            tokens.put(t, count);
        }
        ts.end();
        ts.close();
        assertEquals(7, tokens.size());
        assertEquals(new Integer(1), tokens.get("林斯"));
    }

    private String generateString() {

        Random r = new Random();
        int len = r.nextInt(1000);
        int uniqueVocabTerms = 10000;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(Integer.toString(r.nextInt(uniqueVocabTerms)+100000));
            sb.append(" ");
        }
        return sb.toString();
    }
}
