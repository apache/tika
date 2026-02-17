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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.tika.eval.core.langid.LanguageIDWrapper;
import org.apache.tika.eval.core.tokens.AnalyzerManager;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.language.detect.LanguageResult;


public class CompositeTextStatsCalculator {

    private static final int DEFAULT_MAX_TOKENS = 10_000_000;
    private final byte[] whitespace = new byte[]{' '};
    private final AnalyzerManager analyzerManager;
    private final LanguageIDWrapper languageIDWrapper;
    private final List<LanguageAwareTokenCountStats> languageAwareTokenCountStats =
            new ArrayList<>();
    private final List<TokenCountStatsCalculator> tokenCountStatCalculators = new ArrayList<>();
    private final List<StringStatsCalculator> stringStatCalculators = new ArrayList<>();
    private final List<BytesRefCalculator> bytesRefCalculators = new ArrayList<>();

    public CompositeTextStatsCalculator(List<TextStatsCalculator> calculators) {
        this(calculators, AnalyzerManager.newInstance(DEFAULT_MAX_TOKENS),
                new LanguageIDWrapper());
    }

    public CompositeTextStatsCalculator(List<TextStatsCalculator> calculators,
                                        AnalyzerManager analyzerManager,
                                        LanguageIDWrapper languageIDWrapper) {
        this.analyzerManager = analyzerManager;
        this.languageIDWrapper = languageIDWrapper;
        for (TextStatsCalculator t : calculators) {
            if (t instanceof StringStatsCalculator) {
                stringStatCalculators.add((StringStatsCalculator) t);
            } else if (t instanceof LanguageAwareTokenCountStats) {
                languageAwareTokenCountStats.add((LanguageAwareTokenCountStats) t);
                if (languageIDWrapper == null) {
                    throw new IllegalArgumentException("Must specify a LanguageIdWrapper " +
                            "if you want to calculate languageAware stats: " + t.getClass());
                }
            } else if (t instanceof TokenCountStatsCalculator) {
                tokenCountStatCalculators.add((TokenCountStatsCalculator) t);
            } else if (t instanceof BytesRefCalculator) {
                bytesRefCalculators.add((BytesRefCalculator) t);
            } else {
                throw new IllegalArgumentException("I regret I don't yet handle: " + t.getClass());
            }
        }
    }

    public Map<Class, Object> calculate(String txt) {
        Map<Class, Object> results = new HashMap<>();
        for (StringStatsCalculator calc : stringStatCalculators) {
            results.put(calc.getClass(), calc.calculate(txt));
        }

        TokenCounts tokenCounts = null;
        if (!tokenCountStatCalculators.isEmpty() || !languageAwareTokenCountStats.isEmpty() ||
                !bytesRefCalculators.isEmpty()) {
            tokenCounts = tokenize(txt, results);
        }

        if (!languageAwareTokenCountStats.isEmpty()) {
            List<LanguageResult> langs = results.containsKey(LanguageIDWrapper.class) ?
                    (List) results.get(LanguageIDWrapper.class) : languageIDWrapper.calculate(txt);
            results.put(LanguageIDWrapper.class, langs);
            for (LanguageAwareTokenCountStats calc : languageAwareTokenCountStats) {
                results.put(calc.getClass(), calc.calculate(langs, tokenCounts));
            }
        }

        for (TokenCountStatsCalculator calc : tokenCountStatCalculators) {
            results.put(calc.getClass(), calc.calculate(tokenCounts));
        }
        return results;
    }

    private TokenCounts tokenize(String txt, Map<Class, Object> results) {
        TokenCounts counts = new TokenCounts();
        if (bytesRefCalculators.isEmpty()) {
            analyzerManager.tokenize(txt, counts::increment);
        } else {
            List<BytesRefCalculator.BytesRefCalcInstance> brcis = new ArrayList<>();
            for (BytesRefCalculator brf : bytesRefCalculators) {
                brcis.add(brf.getInstance());
            }
            int[] tokenIndex = {0};
            analyzerManager.tokenize(txt, token -> {
                counts.increment(token);
                byte[] utf8 = token.getBytes(StandardCharsets.UTF_8);
                for (BytesRefCalculator.BytesRefCalcInstance brci : brcis) {
                    if (tokenIndex[0] > 0) {
                        brci.update(whitespace, 0, 1);
                    }
                    brci.update(utf8, 0, utf8.length);
                }
                tokenIndex[0]++;
            });
            for (BytesRefCalculator.BytesRefCalcInstance brc : brcis) {
                results.put(brc.getOuterClass(), brc.finish());
            }
        }
        return counts;
    }
}
