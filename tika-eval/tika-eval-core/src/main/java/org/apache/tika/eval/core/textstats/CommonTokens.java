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
import java.util.Set;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;

import org.apache.tika.eval.core.tokens.AlphaIdeographFilterFactory;
import org.apache.tika.eval.core.tokens.CommonTokenCountManager;
import org.apache.tika.eval.core.tokens.CommonTokenResult;
import org.apache.tika.eval.core.tokens.LangModel;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.language.detect.LanguageResult;

public class CommonTokens implements LanguageAwareTokenCountStats<CommonTokenResult> {

    private final CommonTokenCountManager commonTokenCountManager;

    public CommonTokens() {
        this(new CommonTokenCountManager());
    }

    public CommonTokens(CommonTokenCountManager mgr) {
        this.commonTokenCountManager = mgr;
    }

    @Override
    public CommonTokenResult calculate(List<LanguageResult> languages, TokenCounts tokenCounts) {
        Pair<String, LangModel> pair =
                commonTokenCountManager.getLangTokens(languages.get(0).getLanguage());
        String actualLangCode = pair.getKey();
        Set<String> commonTokens = pair.getValue().getTokens();
        int numUniqueCommonTokens = 0;
        int numCommonTokens = 0;
        int numUniqueAlphabeticTokens = 0;
        int numAlphabeticTokens = 0;
        for (Map.Entry<String, MutableInt> e : tokenCounts.getTokens().entrySet()) {
            String token = e.getKey();
            int count = e.getValue().intValue();
            if (AlphaIdeographFilterFactory.isAlphabetic(token.toCharArray(), token.length())) {
                numAlphabeticTokens += count;
                numUniqueAlphabeticTokens++;
            }
            if (commonTokens.contains(token)) {
                numCommonTokens += count;
                numUniqueCommonTokens++;
            }

        }
        return new CommonTokenResult(actualLangCode, numUniqueCommonTokens, numCommonTokens,
                numUniqueAlphabeticTokens, numAlphabeticTokens);
    }
}
