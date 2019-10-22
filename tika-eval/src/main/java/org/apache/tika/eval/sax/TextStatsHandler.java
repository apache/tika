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
package org.apache.tika.eval.sax;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.tika.eval.langid.Language;
import org.apache.tika.eval.langid.LanguageIDWrapper;
import org.apache.tika.eval.metadata.TextStats;
import org.apache.tika.eval.textstats.BasicTokenCountStatsCalculator;
import org.apache.tika.eval.textstats.CommonTokens;
import org.apache.tika.eval.textstats.CompositeTextStatsCalculator;
import org.apache.tika.eval.textstats.ContentLengthCalculator;
import org.apache.tika.eval.textstats.TextStatsCalculator;
import org.apache.tika.eval.textstats.TokenEntropy;
import org.apache.tika.eval.textstats.TokenLengths;
import org.apache.tika.eval.textstats.TopNTokens;
import org.apache.tika.eval.tokens.AnalyzerManager;
import org.apache.tika.eval.tokens.CommonTokenCountManager;
import org.apache.tika.eval.tokens.CommonTokenResult;
import org.apache.tika.eval.tokens.TokenCounts;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.EndDocumentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextStatsHandler extends EndDocumentHandler {

    private static CommonTokenCountManager COMMON_TOKEN_COUNT_MANAGER =
            new CommonTokenCountManager(null, "UNK");

    private static int MAX_TOKENS = 1000000;
    private static CompositeTextStatsCalculator TEXT_STATS_CALCULATOR;
    static {
        LanguageIDWrapper langIder = new LanguageIDWrapper();
        AnalyzerManager analyzerManager = AnalyzerManager.newInstance(MAX_TOKENS);
        List<TextStatsCalculator> calculators = new ArrayList<>();
        calculators.add(new CommonTokens(COMMON_TOKEN_COUNT_MANAGER));
        calculators.add(new TokenEntropy());
        calculators.add(new TokenLengths());
        calculators.add(new TopNTokens(10));
        calculators.add(new BasicTokenCountStatsCalculator());
        calculators.add(new ContentLengthCalculator());

        TEXT_STATS_CALCULATOR = new CompositeTextStatsCalculator(calculators,
                analyzerManager.getGeneralAnalyzer(), langIder);

    }

    public TextStatsHandler(ContentHandler contentHandler, Metadata metadata) {
        super(contentHandler, metadata);
    }

    @Override
    protected void _endDocument() throws SAXException {
        Map<Class, Object> textStats = TEXT_STATS_CALCULATOR.calculate(stringBuilder.toString());

        List<Language> probabilities = (List<Language>) textStats.get(LanguageIDWrapper.class);

        if (probabilities.size() > 0) {
            metadata.set(TextStats.LANG_ID_1, probabilities.get(0).getLanguage());
            metadata.set(TextStats.LANG_ID_1_CONFIDENCE, probabilities.get(0).getConfidence());
        }
        if (probabilities.size() > 1) {
            metadata.set(TextStats.LANG_ID_2, probabilities.get(1).getLanguage());
            metadata.set(TextStats.LANG_ID_2_CONFIDENCE, probabilities.get(1).getConfidence());
        }

        CommonTokenResult commonTokenResult = (CommonTokenResult) textStats.get(CommonTokens.class);
        if (commonTokenResult != null) {
            metadata.set(TextStats.COMMON_TOKENS_LANG, commonTokenResult.getLangCode());
            metadata.set(TextStats.NUM_UNIQUE_COMMON_TOKENS, Integer.toString(commonTokenResult.getUniqueCommonTokens()));
            metadata.set(TextStats.NUM_COMMON_TOKENS, Integer.toString(commonTokenResult.getCommonTokens()));
            metadata.set(TextStats.NUM_UNIQUE_ALPHABETIC_TOKENS,
                    Integer.toString(commonTokenResult.getUniqueAlphabeticTokens()));
            metadata.set(TextStats.NUM_ALPHABETIC_TOKENS,
                    Integer.toString(commonTokenResult.getAlphabeticTokens()));
            int alpha = commonTokenResult.getAlphabeticTokens();
            int common = commonTokenResult.getCommonTokens();

            if (alpha > 0) {
                double oov = 1.0-(double)common/(double)alpha;
                metadata.set(TextStats.OOV, oov);
            }
        }

        TokenCounts tokenCounts = (TokenCounts) textStats.get(BasicTokenCountStatsCalculator.class);
        if (tokenCounts != null) {

            metadata.set(TextStats.NUM_UNIQUE_TOKENS,
                    Integer.toString(tokenCounts.getTotalUniqueTokens()));
            metadata.set(TextStats.NUM_TOKENS,
                    Integer.toString(tokenCounts.getTotalTokens()));
        }
        if (textStats.get(TokenEntropy.class) != null) {
            metadata.set(TextStats.TOKEN_ENTROPY_RATE,
                    Double.toString((Double) textStats.get(TokenEntropy.class)));
        }

        SummaryStatistics summStats = (SummaryStatistics) textStats.get(TokenLengths.class);
        if (summStats != null) {
            metadata.set(TextStats.TOKEN_LENGTH_SUM,
                    Integer.toString((int) summStats.getSum()));

            metadata.set(TextStats.TOKEN_LENGTH_MEAN,
                    Double.toString(summStats.getMean()));

            metadata.set(TextStats.TOKEN_LENGTH_STD_DEV,
                    Double.toString(summStats.getStandardDeviation()));
        }

    }
}
