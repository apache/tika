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
package org.apache.tika.server.eval;

import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.LANGUAGE;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.LANGUAGE_CONFIDENCE;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.NUM_ALPHA_TOKENS;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.NUM_TOKENS;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.NUM_UNIQUE_ALPHA_TOKENS;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.NUM_UNIQUE_TOKENS;
import static org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter.OUT_OF_VOCABULARY;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.ServerStatusResource;
import org.apache.tika.server.core.resource.TikaServerResource;
import org.apache.tika.utils.StringUtils;

@Path("/eval")
public class TikaEvalResource implements TikaServerResource, ServerStatusResource {

    public static final String TEXT = "text";
    public static final String TEXT_A = "textA";
    public static final String TEXT_B = "textB";
    public static final String ID = "id";

    public static final Property DICE = Property.externalReal(
            TikaEvalMetadataFilter.TIKA_EVAL_NS + "dice");

    public static final Property OVERLAP = Property.externalReal(
            TikaEvalMetadataFilter.TIKA_EVAL_NS + "overlap");

    private ServerStatus serverStatus;
    public static final long DEFAULT_TIMEOUT_MILLIS = 60000;

    static CompositeTextStatsCalculator TEXT_STATS_CALCULATOR;

    static {
        List<TextStatsCalculator> calcs = new ArrayList<>();
        calcs.add(new BasicTokenCountStatsCalculator());
        calcs.add(new CommonTokens());
        TEXT_STATS_CALCULATOR = new CompositeTextStatsCalculator(calcs);
    }

    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("compare")
    public Map<String, Object> compare(InputStream is) throws Exception {
        JsonNode node = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            node = new ObjectMapper().readTree(reader);
        }
        String id = node.get(ID).asText();
        String textA = node.get(TEXT_A).asText();
        String textB = node.get(TEXT_B).asText();
        long timeoutMillis = node.has("timeoutMillis") ? node.get("timeoutMillis").asLong() :
                DEFAULT_TIMEOUT_MILLIS;
        return compareText(id, textA, textB, timeoutMillis);
    }

    @PUT
    @Consumes("application/json")
    @Produces("application/json")
    @Path("profile")
    public Map<String, Object> profile(InputStream is) throws Exception {
        JsonNode node = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            node = new ObjectMapper().readTree(reader);
        }
        String id = node.get(ID).asText();
        String text = node.get(TEXT).asText();
        long timeoutMillis = node.has("timeoutMillis") ? node.get("timeoutMillis").asLong() :
                DEFAULT_TIMEOUT_MILLIS;
        return profile(id, text, timeoutMillis);
    }

    private Map<String, Object> profile(String id, String text, long timeoutMillis) {

        Map<String, Object> stats = new HashMap<>();
        long taskId = serverStatus.start(ServerStatus.TASK.PARSE, id, timeoutMillis);
        try {
            profile(StringUtils.EMPTY, text, stats);
        } finally {
            serverStatus.complete(taskId);
        }
        return stats;
    }


    private Map<String, Object> compareText(String id, String textA, String textB, long timeoutMillis) {

        Map<String, Object> stats = new HashMap<>();
        long taskId = serverStatus.start(ServerStatus.TASK.PARSE, id, timeoutMillis);
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

    private void reportContrastStats(ContrastStatistics contrastStatistics,
                                     Map<String, Object> stats) {
        stats.put(DICE.getName(), contrastStatistics.getDiceCoefficient());
        stats.put(OVERLAP.getName(), contrastStatistics.getOverlap());
        //TODO, add topNMore, topNUnique
    }

    private TokenCounts profile(String suffix, String content, Map<String, Object> stats) {
        Map<Class, Object> results = TEXT_STATS_CALCULATOR.calculate(content);

        TokenCounts tokenCounts = (TokenCounts) results.get(BasicTokenCountStatsCalculator.class);
        stats.put(NUM_TOKENS.getName() + suffix, tokenCounts.getTotalTokens());
        stats.put(NUM_UNIQUE_TOKENS.getName() + suffix, tokenCounts.getTotalUniqueTokens());


        //common token results
        CommonTokenResult commonTokenResult = (CommonTokenResult) results.get(CommonTokens.class);
        stats.put(NUM_ALPHA_TOKENS.getName() + suffix, commonTokenResult.getAlphabeticTokens());
        stats.put(NUM_UNIQUE_ALPHA_TOKENS.getName() + suffix, commonTokenResult.getUniqueAlphabeticTokens());
        if (commonTokenResult.getAlphabeticTokens() > 0) {
            stats.put(OUT_OF_VOCABULARY.getName() + suffix, commonTokenResult.getOOV());
        } else {
            stats.put(OUT_OF_VOCABULARY.getName() + suffix, -1.0f);
        }

        //languages
        List<LanguageResult> probabilities =
                (List<LanguageResult>) results.get(LanguageIDWrapper.class);
        if (probabilities.size() > 0) {
            stats.put(LANGUAGE.getName() + suffix, probabilities.get(0).getLanguage());
            stats.put(LANGUAGE_CONFIDENCE.getName() + suffix, probabilities.get(0).getRawScore());
        }
        return tokenCounts;
    }

    @Override
    public void setServerStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }
}
