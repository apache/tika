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

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.math3.util.FastMath;

import org.apache.tika.eval.core.tokens.TokenCounts;

public class TokenEntropy implements TokenCountStatsCalculator<Double> {

    @Override
    public Double calculate(TokenCounts tokenCounts) {
        double ent = 0.0d;
        double p = 0.0d;
        double base = 2.0;
        double totalTokens = (double) tokenCounts.getTotalTokens();
        for (MutableInt i : tokenCounts.getTokens().values()) {
            int termFreq = i.intValue();

            p = (double) termFreq / totalTokens;
            ent += p * FastMath.log(base, p);
        }
        return -1.0 * ent;
    }
}
