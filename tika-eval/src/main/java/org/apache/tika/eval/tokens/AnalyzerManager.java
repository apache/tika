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
package org.apache.tika.eval.tokens;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.apache.lucene.analysis.Analyzer;

public class AnalyzerManager {

    private final static String GENERAL = "general";
    private static final String ALPHA_IDEOGRAPH = "alpha";
    private static final String COMMON_TOKENS = "common_tokens";

    private final Analyzer generalAnalyzer;
    private final Analyzer commonTokensAnalyzer;

    private AnalyzerManager(Analyzer generalAnalyzer, Analyzer commonTokensAnalyzer) {
        this.generalAnalyzer = generalAnalyzer;
        this.commonTokensAnalyzer = commonTokensAnalyzer;
    }

    public static AnalyzerManager newInstance(int maxTokens) throws IOException {
        InputStream is = AnalyzerManager.class.getClassLoader().getResourceAsStream("lucene-analyzers.json");
        Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(Map.class, new AnalyzerDeserializer(maxTokens));
        Gson gson = builder.create();
        Map<String, Analyzer> map = gson.fromJson(reader, Map.class);
        Analyzer general = map.get(GENERAL);
        Analyzer alphaIdeo = map.get(ALPHA_IDEOGRAPH);
        Analyzer common = map.get(COMMON_TOKENS);
        if (general == null) {
            throw new JsonParseException("Must specify "+GENERAL + " analyzer");
        }
        if (common == null) {
            throw new JsonParseException("Must specify "+ COMMON_TOKENS + " analyzer");
        }

        return new AnalyzerManager(general, common);
    }

    /**
     * This analyzer should be used to extract all tokens.
     * @return
     */
    public Analyzer getGeneralAnalyzer() {
        return generalAnalyzer;
    }

    /**
     * This analyzer should be used to generate common tokens lists from
     * large corpora.  It is not used by tika-eval in profiling or comparing.
     * @return
     */
    public Analyzer getCommonTokensAnalyzer() {
        return commonTokensAnalyzer;
    }
}
