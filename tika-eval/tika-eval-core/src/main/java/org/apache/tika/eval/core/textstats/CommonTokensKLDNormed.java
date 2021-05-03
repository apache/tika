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

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.util.FastMath;

import org.apache.tika.eval.core.tokens.CommonTokenCountManager;
import org.apache.tika.eval.core.tokens.LangModel;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.language.detect.LanguageResult;

public class CommonTokensKLDNormed implements LanguageAwareTokenCountStats<Double> {

    private final CommonTokenCountManager commonTokenCountManager;

    public CommonTokensKLDNormed(CommonTokenCountManager mgr) {
        this.commonTokenCountManager = mgr;
    }

    @Override
    public Double calculate(List<LanguageResult> languages, TokenCounts tokenCounts) {
        Pair<String, LangModel> pair =
                commonTokenCountManager.getLangTokens(languages.get(0).getLanguage());
        LangModel model = pair.getValue();
        double kl = 0.0;
        if (tokenCounts.getTokens().entrySet().size() == 0) {
            return 1.0;
        }
        double worstCase = 0.0;
        for (Map.Entry<String, MutableInt> e : tokenCounts.getTokens().entrySet()) {
            double p = (double) e.getValue().intValue() / (double) tokenCounts.getTotalTokens();
            if (p == 0.0) { //shouldn't happen, but be defensive
                continue;
            }
            double q = model.getProbability(e.getKey());
            kl += p * FastMath.log(q / p);
        }
        for (int i = 0; i < tokenCounts.getTotalTokens(); i++) {
            double worstCaseP = 1 / (double) tokenCounts.getTotalTokens();
            worstCase += worstCaseP * FastMath.log(model.getUnseenProbability() / worstCaseP);

        }
        return (kl / worstCase);
    }
}
