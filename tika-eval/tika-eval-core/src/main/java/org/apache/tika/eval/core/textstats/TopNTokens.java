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
package org.apache.tika.eval.core.textstats;

import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;

import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.eval.core.tokens.TokenIntPair;

public class TopNTokens implements TokenCountStatsCalculator<TokenIntPair[]> {

    private final int topN;

    public TopNTokens(int topN) {
        this.topN = topN;
    }

    @Override
    public TokenIntPair[] calculate(TokenCounts tokenCounts) {
        TokenCountPriorityQueue queue = new TokenCountPriorityQueue(topN);

        for (Map.Entry<String, MutableInt> e : tokenCounts.getTokens().entrySet()) {
            String token = e.getKey();
            int termFreq = e.getValue().intValue();

            if (queue.top() == null || queue.size() < topN || termFreq >= queue.top().getValue()) {
                queue.insertWithOverflow(new TokenIntPair(token, termFreq));
            }

        }
        return queue.getArray();
    }
}
