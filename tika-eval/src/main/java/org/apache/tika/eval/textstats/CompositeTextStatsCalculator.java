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
package org.apache.tika.eval.textstats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.tika.eval.langid.Language;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.apache.tika.eval.tokens.TokenCounts;


public class CompositeTextStatsCalculator {

    private static final String FIELD = "f";
    private static final int DEFAULT_MAX_TOKENS = 10_000_000;
    private final Analyzer analyzer;
    private final LanguageIDWrapper languageIDWrapper;
    private final List<LanguageAwareTokenCountStats> languageAwareTokenCountStats = new ArrayList<>();
    private final List<TokenCountStatsCalculator> tokenCountStatCalculators = new ArrayList<>();
    private final List<StringStatsCalculator> stringStatCalculators = new ArrayList<>();

    public CompositeTextStatsCalculator(List<TextStatsCalculator> calculators) {
        this(calculators,
                AnalyzerManager.newInstance(DEFAULT_MAX_TOKENS).getGeneralAnalyzer(),
                new LanguageIDWrapper());
    }

    public CompositeTextStatsCalculator(List<TextStatsCalculator> calculators, Analyzer analyzer,
                                        LanguageIDWrapper languageIDWrapper) {
        this.analyzer = analyzer;
        this.languageIDWrapper = languageIDWrapper;
        for (TextStatsCalculator t : calculators) {
            if (t instanceof StringStatsCalculator) {
                stringStatCalculators.add((StringStatsCalculator)t);
            } else if (t instanceof LanguageAwareTokenCountStats) {
                languageAwareTokenCountStats.add((LanguageAwareTokenCountStats) t);
                if (languageIDWrapper == null) {
                    throw new IllegalArgumentException("Must specify a LanguageIdWrapper "+
                            "if you want to calculate languageAware stats: "+t.getClass());
                }
            } else if (t instanceof TokenCountStatsCalculator) {
                tokenCountStatCalculators.add((TokenCountStatsCalculator)t);
                if (analyzer == null) {
                    throw new IllegalArgumentException(
                            "Analyzer must not be null if you are using "+
                                    "a TokenCountStats: "+t.getClass()
                    );
                }
            } else {
                throw new IllegalArgumentException(
                        "I regret I don't yet handle: "+t.getClass()
                );
            }
        }
    }

    public Map<Class, Object> calculate(String txt) {
        Map<Class, Object> results = new HashMap<>();
        for (StringStatsCalculator calc : stringStatCalculators) {
            results.put(calc.getClass(), calc.calculate(txt));
        }

        TokenCounts tokenCounts = null;
        if (tokenCountStatCalculators.size() > 0 || languageAwareTokenCountStats.size() > 0) {
            try {
                tokenCounts = tokenize(txt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (languageAwareTokenCountStats.size() > 0) {
            List<Language> langs = results.containsKey(LanguageIDWrapper.class) ?
                    (List)results.get(LanguageIDWrapper.class) : languageIDWrapper.calculate(txt);
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

    private TokenCounts tokenize(String txt) throws IOException  {
        TokenCounts counts = new TokenCounts();
        TokenStream ts = analyzer.tokenStream(FIELD, txt);
        try {
            CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String token = termAtt.toString();
                counts.increment(token);
            }
        } finally {
            ts.close();
            ts.end();
        }
        return counts;
    }
}
