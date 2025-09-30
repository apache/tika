/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package org.apache.tika.server.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import org.apache.tika.eval.core.langid.LanguageIDWrapper;
import org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter;
import org.apache.tika.eval.core.textstats.BasicTokenCountStatsCalculator;
import org.apache.tika.eval.core.textstats.CommonTokens;
import org.apache.tika.eval.core.textstats.CompositeTextStatsCalculator;
import org.apache.tika.eval.core.textstats.TextStatsCalculator;
import org.apache.tika.eval.core.tokens.CommonTokenResult;
import org.apache.tika.eval.core.tokens.ContrastStatistics;
import org.apache.tika.eval.core.tokens.TokenContraster;
import org.apache.tika.eval.core.tokens.TokenCounts;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Property;
import org.apache.tika.server.api.EvalResourceApi;
import org.apache.tika.server.component.ServerStatus;
import org.apache.tika.server.model.PutEvalCompare200Response;
import org.apache.tika.server.model.PutEvalCompareRequest;
import org.apache.tika.server.model.PutEvalProfile200Response;
import org.apache.tika.server.model.PutEvalProfileRequest;
import org.apache.tika.utils.StringUtils;

@Controller
public class EvalController implements EvalResourceApi {

    public static final long DEFAULT_TIMEOUT_MILLIS = 60000;

    public static final Property DICE = Property.externalReal(
            TikaEvalMetadataFilter.TIKA_EVAL_NS + "dice");

    public static final Property OVERLAP = Property.externalReal(
            TikaEvalMetadataFilter.TIKA_EVAL_NS + "overlap");

    static CompositeTextStatsCalculator TEXT_STATS_CALCULATOR;

    static {
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new BasicTokenCountStatsCalculator());
        calcs.add(new CommonTokens());
        TEXT_STATS_CALCULATOR = new CompositeTextStatsCalculator(calcs);
    }

    @Autowired
    private ServerStatus serverStatus;

    @Override
    public ResponseEntity<PutEvalCompare200Response> putEvalCompare(PutEvalCompareRequest putEvalCompareRequest) {
        try {
            String id = putEvalCompareRequest.getId();
            String textA = putEvalCompareRequest.getTextA();
            String textB = putEvalCompareRequest.getTextB();
            long timeoutMillis = putEvalCompareRequest.getTimeoutMillis() != null ?
                putEvalCompareRequest.getTimeoutMillis() : DEFAULT_TIMEOUT_MILLIS;

            Map<String, Object> result = compareText(id, textA, textB, timeoutMillis);

            PutEvalCompare200Response response = new PutEvalCompare200Response();
            mapResultToCompareResponse(result, response);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<PutEvalProfile200Response> putEvalProfile(PutEvalProfileRequest putEvalProfileRequest) {
        try {
            String id = putEvalProfileRequest.getId();
            String text = putEvalProfileRequest.getText();
            long timeoutMillis = putEvalProfileRequest.getTimeoutMillis() != null ?
                putEvalProfileRequest.getTimeoutMillis() : DEFAULT_TIMEOUT_MILLIS;

            Map<String, Object> result = profile(id, text, timeoutMillis);

            PutEvalProfile200Response response = new PutEvalProfile200Response();
            mapResultToProfileResponse(result, response);

            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> compareText(String id, String textA, String textB, long timeoutMillis) {
        Map<String, Object> stats = new HashMap<>();
        long taskId = serverStatus.start(ServerStatus.TASK.EVAL, id, timeoutMillis);
        try {
            TokenCounts tokensA = profile("A", textA, stats);
            TokenCounts tokensB = profile("B", textB, stats);
            TokenContraster tokenContraster = new TokenContraster();
            ContrastStatistics contrastStatistics =
                    tokenContraster.calculateContrastStatistics(tokensA, tokensB);
            reportContrastStats(contrastStatistics, stats);
        } finally {
            serverStatus.complete(taskId);
        }
        return stats;
    }

    private Map<String, Object> profile(String id, String text, long timeoutMillis) {
        Map<String, Object> stats = new HashMap<>();
        long taskId = serverStatus.start(ServerStatus.TASK.EVAL, id, timeoutMillis);
        try {
            profile(StringUtils.EMPTY, text, stats);
        } finally {
            serverStatus.complete(taskId);
        }
        return stats;
    }

    private TokenCounts profile(String suffix, String content, Map<String, Object> stats) {
        Map<Class, Object> results = TEXT_STATS_CALCULATOR.calculate(content);

        TokenCounts tokenCounts = (TokenCounts) results.get(BasicTokenCountStatsCalculator.class);
        stats.put("tika-eval:numTokens" + suffix, tokenCounts.getTotalTokens());
        stats.put("tika-eval:numUniqueTokens" + suffix, tokenCounts.getTotalUniqueTokens());

        //common token results
        CommonTokenResult commonTokenResult = (CommonTokenResult) results.get(CommonTokens.class);
        stats.put("tika-eval:numAlphaTokens" + suffix, commonTokenResult.getAlphabeticTokens());
        stats.put("tika-eval:numUniqueAlphaTokens" + suffix, commonTokenResult.getUniqueAlphabeticTokens());
        if (commonTokenResult.getAlphabeticTokens() > 0) {
            stats.put("tika-eval:oov" + suffix, commonTokenResult.getOOV());
        } else {
            stats.put("tika-eval:oov" + suffix, -1.0f);
        }

        //languages
        List<LanguageResult> probabilities =
                (List<LanguageResult>) results.get(LanguageIDWrapper.class);
        if (probabilities.size() > 0) {
            stats.put("tika-eval:lang" + suffix, probabilities.get(0).getLanguage());
            stats.put("tika-eval:langConfidence" + suffix, probabilities.get(0).getRawScore());
        }
        return tokenCounts;
    }

    private void reportContrastStats(ContrastStatistics contrastStatistics,
                                     Map<String, Object> stats) {
        stats.put("tika-eval:dice", contrastStatistics.getDiceCoefficient());
        stats.put("tika-eval:overlap", contrastStatistics.getOverlap());
        //TODO, add topNMore, topNUnique
    }

    private void mapResultToCompareResponse(Map<String, Object> result, PutEvalCompare200Response response) {
        if (result.get("tika-eval:dice") != null) {
            response.setTikaEvalColonDice(((Number) result.get("tika-eval:dice")).floatValue());
        }
        if (result.get("tika-eval:overlap") != null) {
            response.setTikaEvalColonOverlap(((Number) result.get("tika-eval:overlap")).floatValue());
        }
        if (result.get("tika-eval:numTokensA") != null) {
            response.setTikaEvalColonNumTokensA(((Number) result.get("tika-eval:numTokensA")).intValue());
        }
        if (result.get("tika-eval:numTokensB") != null) {
            response.setTikaEvalColonNumTokensB(((Number) result.get("tika-eval:numTokensB")).intValue());
        }
        if (result.get("tika-eval:numUniqueTokensA") != null) {
            response.setTikaEvalColonNumUniqueTokensA(((Number) result.get("tika-eval:numUniqueTokensA")).intValue());
        }
        if (result.get("tika-eval:numUniqueTokensB") != null) {
            response.setTikaEvalColonNumUniqueTokensB(((Number) result.get("tika-eval:numUniqueTokensB")).intValue());
        }
        if (result.get("tika-eval:numAlphaTokensA") != null) {
            response.setTikaEvalColonNumAlphaTokensA(((Number) result.get("tika-eval:numAlphaTokensA")).intValue());
        }
        if (result.get("tika-eval:numAlphaTokensB") != null) {
            response.setTikaEvalColonNumAlphaTokensB(((Number) result.get("tika-eval:numAlphaTokensB")).intValue());
        }
        if (result.get("tika-eval:numUniqueAlphaTokensA") != null) {
            response.setTikaEvalColonNumUniqueAlphaTokensA(((Number) result.get("tika-eval:numUniqueAlphaTokensA")).intValue());
        }
        if (result.get("tika-eval:numUniqueAlphaTokensB") != null) {
            response.setTikaEvalColonNumUniqueAlphaTokensB(((Number) result.get("tika-eval:numUniqueAlphaTokensB")).intValue());
        }
        if (result.get("tika-eval:oovA") != null) {
            response.setTikaEvalColonOovA(((Number) result.get("tika-eval:oovA")).floatValue());
        }
        if (result.get("tika-eval:oovB") != null) {
            response.setTikaEvalColonOovB(((Number) result.get("tika-eval:oovB")).floatValue());
        }
        if (result.get("tika-eval:langA") != null) {
            response.setTikaEvalColonLangA((String) result.get("tika-eval:langA"));
        }
        if (result.get("tika-eval:langB") != null) {
            response.setTikaEvalColonLangB((String) result.get("tika-eval:langB"));
        }
        if (result.get("tika-eval:langConfidenceA") != null) {
            response.setTikaEvalColonLangConfidenceA(((Number) result.get("tika-eval:langConfidenceA")).floatValue());
        }
        if (result.get("tika-eval:langConfidenceB") != null) {
            response.setTikaEvalColonLangConfidenceB(((Number) result.get("tika-eval:langConfidenceB")).floatValue());
        }
    }

    private void mapResultToProfileResponse(Map<String, Object> result, PutEvalProfile200Response response) {
        if (result.get("tika-eval:numTokens") != null) {
            response.setTikaEvalColonNumTokens(((Number) result.get("tika-eval:numTokens")).intValue());
        }
        if (result.get("tika-eval:numUniqueTokens") != null) {
            response.setTikaEvalColonNumUniqueTokens(((Number) result.get("tika-eval:numUniqueTokens")).intValue());
        }
        if (result.get("tika-eval:numAlphaTokens") != null) {
            response.setTikaEvalColonNumAlphaTokens(((Number) result.get("tika-eval:numAlphaTokens")).intValue());
        }
        if (result.get("tika-eval:numUniqueAlphaTokens") != null) {
            response.setTikaEvalColonNumUniqueAlphaTokens(((Number) result.get("tika-eval:numUniqueAlphaTokens")).intValue());
        }
        if (result.get("tika-eval:oov") != null) {
            response.setTikaEvalColonOov(((Number) result.get("tika-eval:oov")).floatValue());
        }
        if (result.get("tika-eval:lang") != null) {
            response.setTikaEvalColonLang((String) result.get("tika-eval:lang"));
        }
        if (result.get("tika-eval:langConfidence") != null) {
            response.setTikaEvalColonLangConfidence(((Number) result.get("tika-eval:langConfidence")).floatValue());
        }
    }
}
