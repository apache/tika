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

import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.lucene.util.PriorityQueue;

/**
 * Computes some corpus contrast statistics.
 *
 * Not thread safe.
 */
public class TokenContraster {

    private TokenStatistics tokenStatisticsA;
    private TokenStatistics tokenStatisticsB;

    private TokenCountPriorityQueue uniqA;
    private TokenCountPriorityQueue uniqB;

    private TokenCountDiffQueue moreA;
    private TokenCountDiffQueue moreB;


    private int topN = 10;

    private int diceCoefficientNum = 0;
    private int overlapNum = 0;

    private double diceCoefficient = 0.0d;
    private double overlap = 0.0;


    public ContrastStatistics calculateContrastStatistics(Map<String, MutableInt> mapA,
                                                          TokenStatistics tokenStatisticsA,
                                                          Map<String, MutableInt> mapB,
                                                          TokenStatistics tokenStatisticsB) {
        reset();
        this.tokenStatisticsA = tokenStatisticsA;
        this.tokenStatisticsB = tokenStatisticsB;

        for (Map.Entry<String, MutableInt> e : mapA.entrySet()) {
            MutableInt bVal = mapB.get(e.getKey());
            int b = (bVal == null) ? 0 : bVal.intValue();
            add(e.getKey(), e.getValue().intValue(), b);
        }

        for (Map.Entry<String, MutableInt> e : mapB.entrySet()) {
            if (mapA.containsKey(e.getKey())) {
                continue;
            }
            add(e.getKey(), 0, e.getValue().intValue());
        }
        finishComputing();
        ContrastStatistics contrastStatistics = new ContrastStatistics();
        contrastStatistics.setDiceCoefficient(diceCoefficient);
        contrastStatistics.setOverlap(overlap);
        contrastStatistics.setTopNUniqueA(uniqA.getArray());
        contrastStatistics.setTopNUniqueB(uniqB.getArray());
        contrastStatistics.setTopNMoreA(moreA.getArray());
        contrastStatistics.setTopNMoreB(moreB.getArray());
        return contrastStatistics;
    }

    private void reset() {
        this.uniqA = new TokenCountPriorityQueue(topN);
        this.uniqB = new TokenCountPriorityQueue(topN);
        this.moreA = new TokenCountDiffQueue(topN);
        this.moreB = new TokenCountDiffQueue(topN);
        diceCoefficientNum = 0;
        overlapNum = 0;
        diceCoefficient = 0.0d;
        overlap = 0.0;

    }
    private void add(String token, int tokenCountA, int tokenCountB) {
        if (tokenCountA > 0 && tokenCountB > 0) {
            diceCoefficientNum += 2;
            overlapNum += 2 * Math.min(tokenCountA, tokenCountB);
        }


        if (tokenCountA == 0L && tokenCountB > 0L) {
            addToken(token, tokenCountB, uniqB);
        }
        if (tokenCountB == 0L && tokenCountA > 0L) {
            addToken(token, tokenCountA, uniqA);
        }

        if (tokenCountA > tokenCountB) {
            addTokenDiff(token, tokenCountA, tokenCountA-tokenCountB, moreA);
        } else if (tokenCountB > tokenCountA) {
            addTokenDiff(token, tokenCountB, tokenCountB-tokenCountA, moreB);

        }

    }

    private void finishComputing() {

        long sumUniqTokens = tokenStatisticsA.getTotalUniqueTokens()
                +tokenStatisticsB.getTotalUniqueTokens();

        diceCoefficient = (double) diceCoefficientNum / (double) sumUniqTokens;
        overlap = (float) overlapNum / (double) (tokenStatisticsA.getTotalTokens() +
                tokenStatisticsB.getTotalTokens());

    }

    private void addTokenDiff(String token, int tokenCount, int diff, TokenCountDiffQueue queue) {
        if (queue.top() == null || queue.size() < topN ||
                diff >= queue.top().diff) {
            queue.insertWithOverflow(new TokenCountDiff(token, diff, tokenCount));
        }

    }

    private void addToken(String token, int tokenCount, TokenCountPriorityQueue queue) {
        if (queue.top() == null || queue.size() < topN ||
                tokenCount >= queue.top().getValue()) {
            queue.insertWithOverflow(new TokenIntPair(token, tokenCount));
        }

    }

    class TokenCountDiffQueue extends PriorityQueue<TokenCountDiff> {

        TokenCountDiffQueue(int maxSize) {
            super(maxSize);
        }

        @Override
        protected boolean lessThan(TokenCountDiff arg0, TokenCountDiff arg1) {
            if (arg0.diff < arg1.diff) {
                return true;
            } else if (arg0.diff > arg1.diff) {
                return false;
            }
            return arg1.token.compareTo(arg0.token) < 0;
        }

        public TokenIntPair[] getArray() {
            TokenIntPair[] topN = new TokenIntPair[size()];
            //now we reverse the queue
            TokenCountDiff token = pop();
            int i = topN.length-1;
            while (token != null && i > -1) {
                topN[i--] = new TokenIntPair(token.token, token.diff);
                token = pop();
            }
            return topN;
        }
    }

    private class TokenCountDiff {
        private final String token;
        private final int diff;
        private final int count;

        private TokenCountDiff(String token, int diff, int count) {
            this.token = token;
            this.diff = diff;
            this.count = count;
        }
    }
}
