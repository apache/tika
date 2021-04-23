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
package org.apache.tika.eval.core.tokens;


import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.TokenFilterFactory;

class AnalyzerDeserializer {


    private static final String ANALYZERS = "analyzers";
    private static final String CHAR_FILTERS = "charfilters";
    private static final String TOKEN_FILTERS = "tokenfilters";
    private static final String TOKENIZER = "tokenizer";
    private static final String FACTORY = "factory";
    private static final String PARAMS = "params";
    private static final String COMMENT = "_comment";

    public static Map<String, Analyzer> buildAnalyzers(Reader reader, int maxTokens)
            throws IOException {
        JsonNode root = new ObjectMapper().readTree(reader);
        Map<String, Analyzer> analyzers = new HashMap<>();

        if (!root.isObject() || root.get(ANALYZERS) == null) {
            throw new IllegalArgumentException(
                    "root object must be object with an 'analyzers' element");
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = root.get(ANALYZERS).fields();
             it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String analyzerName = e.getKey();
            Analyzer analyzer = buildAnalyzer(analyzerName, e.getValue(), maxTokens);
            analyzers.put(analyzerName, analyzer);
        }
        return analyzers;
    }

    public static Analyzer buildAnalyzer(String analyzerName, JsonNode node, int maxTokens)
            throws IOException {
        if (!node.isObject()) {
            throw new IllegalArgumentException(
                    "Expecting map of charfilter, tokenizer, tokenfilters");
        }

        CustomAnalyzer.Builder builder =
                CustomAnalyzer.builder(new ClasspathResourceLoader(AnalyzerDeserializer.class));
        for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String k = e.getKey();
            if (k.equals(CHAR_FILTERS)) {
                buildCharFilters(e.getValue(), analyzerName, builder);
            } else if (k.equals(TOKEN_FILTERS)) {
                buildTokenFilterFactories(e.getValue(), analyzerName, maxTokens, builder);
            } else if (k.equals(TOKENIZER)) {
                buildTokenizerFactory(e.getValue(), analyzerName, builder);
            } else if (!k.equals(COMMENT)) {
                throw new IllegalArgumentException(
                        "Should have one of three values here:" + CHAR_FILTERS + ", " + TOKENIZER +
                                ", " + TOKEN_FILTERS + ". I don't recognize: " + k);
            }
        }
        return builder.build();
    }

    private static void buildTokenizerFactory(JsonNode map, String analyzerName,
                                              CustomAnalyzer.Builder builder) throws IOException {
        if (!map.isObject()) {
            throw new IllegalArgumentException("Expecting a map with \"factory\" string and " +
                    "\"params\" map in tokenizer factory;" + " not: " + map.toString() + " in " +
                    analyzerName);
        }
        JsonNode factoryEl = map.get(FACTORY);
        if (factoryEl == null || !factoryEl.isTextual()) {
            throw new IllegalArgumentException(
                    "Expecting value for factory in char filter factory builder in:" +
                            analyzerName);
        }
        String factoryName = factoryEl.asText();
        factoryName = factoryName.startsWith("oala.") ?
                factoryName.replaceFirst("oala.", "org.apache.lucene.analysis.") : factoryName;

        JsonNode paramsEl = map.get(PARAMS);
        Map<String, String> params = mapify(paramsEl);
        builder.withTokenizer(factoryName, params);
    }

    private static void buildCharFilters(JsonNode el, String analyzerName,
                                         CustomAnalyzer.Builder builder) throws IOException {
        if (el == null || el.isNull()) {
            return;
        }
        if (!el.isArray()) {
            throw new IllegalArgumentException(
                    "Expecting array for charfilters, but got:" + el.toString() + " for " +
                            analyzerName);
        }

        for (Iterator<JsonNode> it = el.elements(); it.hasNext(); ) {
            JsonNode filterMap = it.next();
            if (!filterMap.isObject()) {
                throw new IllegalArgumentException(
                        "Expecting a map with \"factory\" string and \"params\" map in char filter factory;" +
                                " not: " + filterMap.toString() + " in " + analyzerName);
            }
            JsonNode factoryEl = filterMap.get(FACTORY);
            if (factoryEl == null || !factoryEl.isTextual()) {
                throw new IllegalArgumentException(
                        "Expecting value for factory in char filter factory builder in:" +
                                analyzerName);
            }
            String factoryName = factoryEl.asText();
            factoryName = factoryName.replaceAll("oala.", "org.apache.lucene.analysis.");

            JsonNode paramsEl = filterMap.get(PARAMS);
            Map<String, String> params = mapify(paramsEl);
            builder.addCharFilter(factoryName, params);
        }
    }

    private static void buildTokenFilterFactories(JsonNode el, String analyzerName, int maxTokens,
                                                  CustomAnalyzer.Builder builder)
            throws IOException {
        if (el == null || el.isNull()) {
            return;
        }
        if (!el.isArray()) {
            throw new IllegalArgumentException(
                    "Expecting array for tokenfilters, but got:" + el.toString() + " in " +
                            analyzerName);
        }

        List<TokenFilterFactory> ret = new LinkedList<>();
        for (Iterator<JsonNode> it = el.elements(); it.hasNext(); ) {
            JsonNode filterMap = it.next();
            if (!filterMap.isObject()) {
                throw new IllegalArgumentException(
                        "Expecting a map with \"factory\" string and \"params\" map in token filter factory;" +
                                " not: " + filterMap.toString() + " in " + analyzerName);
            }
            JsonNode factoryEl = filterMap.get(FACTORY);
            if (factoryEl == null || !factoryEl.isTextual()) {
                throw new IllegalArgumentException(
                        "Expecting value for factory in token filter factory builder in " +
                                analyzerName);
            }
            String factoryName = factoryEl.asText();
            factoryName = factoryName.startsWith("oala.") ?
                    factoryName.replaceFirst("oala.", "org.apache.lucene.analysis.") : factoryName;
            JsonNode paramsEl = filterMap.get(PARAMS);
            Map<String, String> params = mapify(paramsEl);
            builder.addTokenFilter(factoryName, params);
        }

        if (maxTokens > -1) {
            Map<String, String> m = new HashMap<>();
            m.put("maxTokenCount", Integer.toString(maxTokens));
            builder.addTokenFilter("limittokencount", m);
        }
    }

    private static Map<String, String> mapify(JsonNode paramsEl) {
        if (paramsEl == null || paramsEl.isNull()) {
            return Collections.EMPTY_MAP;
        }
        if (!paramsEl.isObject()) {
            throw new IllegalArgumentException("Expecting map, not: " + paramsEl.toString());
        }
        Map<String, String> params = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = paramsEl.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            JsonNode value = e.getValue();
            if (value.isObject() || value.isArray() || value.isNull()) {
                throw new IllegalArgumentException(
                        "Expecting parameter to have primitive value: " + value.toString());
            }
            String v = e.getValue().asText();
            params.put(e.getKey(), v);
        }
        return params;
    }
}
