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
package org.apache.tika.eval.core.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.apache.tika.eval.core.langid.LanguageIDWrapper;
import org.apache.tika.eval.core.textstats.BasicTokenCountStatsCalculator;
import org.apache.tika.eval.core.textstats.CommonTokens;
import org.apache.tika.eval.core.textstats.CompositeTextStatsCalculator;
import org.apache.tika.eval.core.textstats.TextStatsCalculator;
import org.apache.tika.eval.core.tokens.CommonTokenResult;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.MetadataFilter;

public class TikaEvalMetadataFilter extends MetadataFilter {

    public static final String TIKA_EVAL_NS = "tika-eval" + TikaCoreProperties.NAMESPACE_PREFIX_DELIMITER;

    public static final Property NUM_TOKENS = Property.externalInteger(TIKA_EVAL_NS + "numTokens");

    public static final Property NUM_UNIQUE_TOKENS =
            Property.externalInteger(TIKA_EVAL_NS + "numUniqueTokens");

    public static final Property NUM_ALPHA_TOKENS =
            Property.externalInteger(TIKA_EVAL_NS + "numAlphaTokens");

    public static final Property NUM_UNIQUE_ALPHA_TOKENS =
            Property.externalInteger(TIKA_EVAL_NS + "numUniqueAlphaTokens");

    public static final Property LANGUAGE = Property.externalText(TIKA_EVAL_NS + "lang");

    public static final Property LANGUAGE_CONFIDENCE =
            Property.externalReal(TIKA_EVAL_NS + "langConfidence");

    public static final Property OUT_OF_VOCABULARY = Property.externalReal(TIKA_EVAL_NS + "oov");


    static CompositeTextStatsCalculator TEXT_STATS_CALCULATOR;

    static {
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new BasicTokenCountStatsCalculator());
        calcs.add(new CommonTokens());
        TEXT_STATS_CALCULATOR = new CompositeTextStatsCalculator(calcs);
    }


    @Override
    public void filter(Metadata metadata) throws TikaException {
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        if (StringUtils.isAllBlank(content)) {
            return;
        }
        calcStats(content, metadata);
    }

    private void calcStats(String content, Metadata metadata) {
        Map<Class, Object> results = TEXT_STATS_CALCULATOR.calculate(content);

        TokenCounts tokenCounts = (TokenCounts) results.get(BasicTokenCountStatsCalculator.class);
        metadata.set(NUM_TOKENS, tokenCounts.getTotalTokens());
        metadata.set(NUM_UNIQUE_TOKENS, tokenCounts.getTotalUniqueTokens());


        //common token results
        CommonTokenResult commonTokenResult = (CommonTokenResult) results.get(CommonTokens.class);
        metadata.set(NUM_ALPHA_TOKENS, commonTokenResult.getAlphabeticTokens());
        metadata.set(NUM_UNIQUE_ALPHA_TOKENS, commonTokenResult.getUniqueAlphabeticTokens());
        if (commonTokenResult.getAlphabeticTokens() > 0) {
            metadata.set(OUT_OF_VOCABULARY, commonTokenResult.getOOV());
        } else {
            metadata.set(OUT_OF_VOCABULARY, -1.0f);
        }

        //languages
        List<LanguageResult> probabilities =
                (List<LanguageResult>) results.get(LanguageIDWrapper.class);
        if (probabilities.size() > 0) {
            metadata.set(LANGUAGE, probabilities.get(0).getLanguage());
            metadata.set(LANGUAGE_CONFIDENCE, probabilities.get(0).getRawScore());
        }
    }

}
