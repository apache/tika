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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.commons.math3.util.FastMath;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public class TokenCounter {


    Map<String, Map<String, MutableInt>> map = new HashMap<>(); //Map<field, Map<token, count>>
    Map<String, TokenStatistics> tokenStatistics = new HashMap<>();

    private final TokenStatistics NULL_TOKEN_STAT = new TokenStatistics(
            0, 0, new TokenIntPair[0], 0.0d, new SummaryStatistics());

    private final Analyzer generalAnalyzer;

    private int topN = 10;

    public TokenCounter(Analyzer generalAnalyzer) throws IOException {
        this.generalAnalyzer = generalAnalyzer;
    }

    public void add(String field, String content) throws IOException {
        _add(field, generalAnalyzer, content);
    }

    private void _add(String field, Analyzer analyzer, String content) throws IOException {
        int totalTokens = 0;

        TokenStream ts = analyzer.tokenStream(field, content);
        CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
        ts.reset();
        Map<String, MutableInt> tokenMap = map.get(field);
        if (tokenMap == null) {
            tokenMap = new HashMap<>();
            map.put(field, tokenMap);
        }
        while (ts.incrementToken()) {
            String token = termAtt.toString();
            MutableInt cnt = tokenMap.get(token);
            if (cnt == null) {
                cnt = new MutableInt(1);
                tokenMap.put(token, cnt);
            } else {
                cnt.increment();
            }
            totalTokens++;
        }
        ts.close();
        ts.end();

        int totalUniqueTokens = tokenMap.size();

        double ent = 0.0d;
        double p = 0.0d;
        double base = 2.0;

        TokenCountPriorityQueue queue = new TokenCountPriorityQueue(topN);

        SummaryStatistics summaryStatistics = new SummaryStatistics();
        for (Map.Entry<String, MutableInt> e : tokenMap.entrySet()) {
            String token = e.getKey();
            int termFreq = e.getValue().intValue();

            p = (double) termFreq / (double) totalTokens;
            ent += p * FastMath.log(base, p);
            int len = token.codePointCount(0, token.length());
            for (int i = 0; i < e.getValue().intValue(); i++) {
                summaryStatistics.addValue(len);
            }
            if (queue.top() == null || queue.size() < topN ||
                    termFreq >= queue.top().getValue()) {
                queue.insertWithOverflow(new TokenIntPair(token, termFreq));
            }

        }
        if (totalTokens > 0) {
            ent = (-1.0d / (double)totalTokens) * ent;
        }

/*            Collections.sort(allTokens);
            List<TokenIntPair> topNList = new ArrayList<>(topN);
            for (int i = 0; i < topN && i < allTokens.size(); i++) {
                topNList.add(allTokens.get(i));
            }*/

        tokenStatistics.put(field, new TokenStatistics(totalUniqueTokens, totalTokens,
                queue.getArray(), ent, summaryStatistics));

    }

    public TokenStatistics getTokenStatistics(String field) {
        TokenStatistics tokenStat = tokenStatistics.get(field);
        if (tokenStat == null) {
            return NULL_TOKEN_STAT;
        }
        return tokenStat;
    }

    public void setTopN(int topN) {
        this.topN = topN;
    }

    public void clear(String field) {
        Map<String, MutableInt> tokenMap = map.get(field);
        if (tokenMap != null) {
            tokenMap.clear();
        }

        tokenStatistics.put(field, NULL_TOKEN_STAT);
    }

    public Map<String, MutableInt> getTokens(String field) {
        Map<String, MutableInt> ret = map.get(field);
        if (ret == null) {
            return Collections.emptyMap();
        }
        return ret;
    }
}
