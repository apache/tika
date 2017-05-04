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
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.LimitTokenCountFilterFactory;
import org.apache.lucene.analysis.util.CharFilterFactory;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.analysis.util.ResourceLoaderAware;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.analysis.util.TokenizerFactory;

class AnalyzerDeserializer implements JsonDeserializer<Map<String, Analyzer>> {


    private static final String ANALYZERS = "analyzers";
    private static final String CHAR_FILTERS = "charfilters";
    private static final String TOKEN_FILTERS = "tokenfilters";
    private static final String TOKENIZER = "tokenizer";
    private static final String FACTORY = "factory";
    private static final String PARAMS = "params";
    private static final String COMMENT = "_comment";

    private final int maxTokens;

    AnalyzerDeserializer(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public Map<String, Analyzer> deserialize(JsonElement element, Type type,
                                             JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        if (! element.isJsonObject()) {
            throw new IllegalArgumentException("Expecting top level 'analyzers:{}'");
        }

        JsonElement root = element.getAsJsonObject().get(ANALYZERS);
        if (root == null) {
            throw new IllegalArgumentException("Expecting top level 'analyzers:{}");
        }
        try {
            return buildAnalyzers(root, maxTokens);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public static Map<String, Analyzer> buildAnalyzers(JsonElement value, int maxTokens) throws IOException {
        if (! value.isJsonObject()) {
            throw new IllegalArgumentException("Expecting map with analyzer names/analyzer definitions");
        }
        Map<String, Analyzer> analyzers = new HashMap<>();
        JsonObject root = (JsonObject)value;
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            String analyzerName = e.getKey();
            Analyzer analyzer = buildAnalyzer(analyzerName, e.getValue(), maxTokens);
            analyzers.put(analyzerName, analyzer);
        }
        return analyzers;
    }

    public static Analyzer buildAnalyzer(String analyzerName, JsonElement value, int maxTokens) throws IOException {
        if (! value.isJsonObject()) {
            throw new IllegalArgumentException("Expecting map of charfilter, tokenizer, tokenfilters");
        }
        JsonObject aRoot = (JsonObject)value;
        CharFilterFactory[] charFilters = new CharFilterFactory[0];
        TokenizerFactory tokenizerFactory = null;
        TokenFilterFactory[] tokenFilterFactories = new TokenFilterFactory[0];
        for ( Map.Entry<String, JsonElement> e : aRoot.entrySet()) {
            String k = e.getKey();
            if (k.equals(CHAR_FILTERS)) {
                charFilters = buildCharFilters(e.getValue(), analyzerName);
            } else if (k.equals(TOKEN_FILTERS)) {
                tokenFilterFactories = buildTokenFilterFactories(e.getValue(), analyzerName, maxTokens);
            } else if (k.equals(TOKENIZER)) {
                tokenizerFactory = buildTokenizerFactory(e.getValue(), analyzerName);
            } else if (! k.equals(COMMENT)) {
                throw new IllegalArgumentException("Should have one of three values here:"+
                        CHAR_FILTERS + ", "+
                        TOKENIZER+", "+
                        TOKEN_FILTERS +
                        ". I don't recognize: "+k);
            }
        }
        if (tokenizerFactory == null) {
            throw new IllegalArgumentException("Must specify at least a tokenizer factory for an analyzer!");
        }
        return new MyTokenizerChain(charFilters, tokenizerFactory, tokenFilterFactories);
    }

    private static TokenizerFactory buildTokenizerFactory(JsonElement map, String analyzerName) throws IOException {
        if (!(map instanceof JsonObject)) {
            throw new IllegalArgumentException("Expecting a map with \"factory\" string and " +
                    "\"params\" map in tokenizer factory;"+
                    " not: "+map.toString() + " in "+analyzerName);
        }
        JsonElement factoryEl = ((JsonObject)map).get(FACTORY);
        if (factoryEl == null || ! factoryEl.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expecting value for factory in char filter factory builder in:"+
                    analyzerName);
        }
        String factoryName = factoryEl.getAsString();
        factoryName = factoryName.startsWith("oala.") ?
                factoryName.replaceFirst("oala.", "org.apache.lucene.analysis.") : factoryName;

        JsonElement paramsEl = ((JsonObject)map).get(PARAMS);
        Map<String, String> params = mapify(paramsEl);
        String spiName = "";
        for (String s : TokenizerFactory.availableTokenizers()) {
            Class clazz = TokenizerFactory.lookupClass(s);
            if (clazz.getName().equals(factoryName)) {
                spiName = s;
                break;
            }
        }
        if (spiName.equals("")) {
            throw new IllegalArgumentException("A SPI class of type org.apache.lucene.analysis.util.TokenizerFactory with name"+
            "'"+factoryName+"' does not exist.");
        }
        try {
            TokenizerFactory tokenizerFactory = TokenizerFactory.forName(spiName, params);
            if (tokenizerFactory instanceof ResourceLoaderAware) {
                ((ResourceLoaderAware) tokenizerFactory).inform(new ClasspathResourceLoader(AnalyzerDeserializer.class));
            }

            return tokenizerFactory;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("While working on "+analyzerName, e);
        }
    }

    private static CharFilterFactory[] buildCharFilters(JsonElement el, String analyzerName) throws IOException {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (! el.isJsonArray()) {
            throw new IllegalArgumentException("Expecting array for charfilters, but got:"+el.toString() +
                    " for "+analyzerName);
        }
        JsonArray jsonArray = (JsonArray)el;
        List<CharFilterFactory> ret = new LinkedList<CharFilterFactory>();
        for (JsonElement filterMap : jsonArray) {
            if (!(filterMap instanceof JsonObject)) {
                throw new IllegalArgumentException("Expecting a map with \"factory\" string and \"params\" map in char filter factory;"+
                        " not: "+filterMap.toString() + " in "+analyzerName);
            }
            JsonElement factoryEl = ((JsonObject)filterMap).get(FACTORY);
            if (factoryEl == null || ! factoryEl.isJsonPrimitive()) {
                throw new IllegalArgumentException(
                        "Expecting value for factory in char filter factory builder in:"+analyzerName);
            }
            String factoryName = factoryEl.getAsString();
            factoryName = factoryName.replaceAll("oala.", "org.apache.lucene.analysis.");

            JsonElement paramsEl = ((JsonObject)filterMap).get(PARAMS);
            Map<String, String> params = mapify(paramsEl);
            String spiName = "";
            for (String s : CharFilterFactory.availableCharFilters()) {
                Class clazz = CharFilterFactory.lookupClass(s);
                if (clazz.getName().equals(factoryName)) {
                    spiName = s;
                    break;
                }
            }
            if (spiName.equals("")) {
                throw new IllegalArgumentException("A SPI class of type org.apache.lucene.analysis.util.CharFilterFactory with name"+
                        "'"+factoryName+"' does not exist.");
            }

            try {
                CharFilterFactory charFilterFactory = CharFilterFactory.forName(spiName, params);
                if (charFilterFactory instanceof ResourceLoaderAware) {
                    ((ResourceLoaderAware) charFilterFactory).inform(new ClasspathResourceLoader(AnalyzerDeserializer.class));
                }
                ret.add(charFilterFactory);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("While trying to load "+
                        analyzerName + ": "+ e.getMessage(), e);
            }
        }
        if (ret.size() == 0) {
            return new CharFilterFactory[0];
        }
        return ret.toArray(new CharFilterFactory[ret.size()]);
    }

    private static TokenFilterFactory[] buildTokenFilterFactories(JsonElement el,
                                                                  String analyzerName, int maxTokens) throws IOException {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (! el.isJsonArray()) {
            throw new IllegalArgumentException(
                    "Expecting array for tokenfilters, but got:"+el.toString() + " in "+analyzerName);
        }
        JsonArray jsonArray = (JsonArray)el;
        List<TokenFilterFactory> ret = new LinkedList<>();
        for (JsonElement filterMap : jsonArray) {
            if (!(filterMap instanceof JsonObject)) {
                throw new IllegalArgumentException("Expecting a map with \"factory\" string and \"params\" map in token filter factory;"+
                        " not: "+filterMap.toString() + " in "+ analyzerName);
            }
            JsonElement factoryEl = ((JsonObject)filterMap).get(FACTORY);
            if (factoryEl == null || ! factoryEl.isJsonPrimitive()) {
                throw new IllegalArgumentException("Expecting value for factory in token filter factory builder in "+analyzerName);
            }
            String factoryName = factoryEl.getAsString();
            factoryName = factoryName.startsWith("oala.") ?
                    factoryName.replaceFirst("oala.", "org.apache.lucene.analysis.") :
                    factoryName;

            JsonElement paramsEl = ((JsonObject)filterMap).get(PARAMS);
            Map<String, String> params = mapify(paramsEl);
            String spiName = "";
            for (String s : TokenFilterFactory.availableTokenFilters()) {
                Class clazz = TokenFilterFactory.lookupClass(s);
                if (clazz.getName().equals(factoryName)) {
                    spiName = s;
                    break;
                }
            }
            if (spiName.equals("")) {
                throw new IllegalArgumentException("A SPI class of type org.apache.lucene.analysis.util.TokenFilterFactory with name"+
                        "'"+factoryName+"' does not exist.");
            }

            try {
                TokenFilterFactory tokenFilterFactory = TokenFilterFactory.forName(spiName, params);
                if (tokenFilterFactory instanceof ResourceLoaderAware) {
                    ((ResourceLoaderAware) tokenFilterFactory).inform(new ClasspathResourceLoader(AnalyzerDeserializer.class));
                }
                ret.add(tokenFilterFactory);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("While loading "+analyzerName, e);
            }
        }

        if (maxTokens > -1) {
            Map<String, String> m = new HashMap<>();
            m.put("maxTokenCount", Integer.toString(maxTokens));
            ret.add(new LimitTokenCountFilterFactory(m));
        }

        if (ret.size() == 0) {
            return new TokenFilterFactory[0];
        }
        return ret.toArray(new TokenFilterFactory[ret.size()]);
    }

    private static  Map<String, String> mapify(JsonElement paramsEl) {
        if (paramsEl == null || paramsEl.isJsonNull()) {
            return Collections.EMPTY_MAP;
        }
        if (! paramsEl.isJsonObject()) {
            throw new IllegalArgumentException("Expecting map, not: "+paramsEl.toString());
        }
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String,JsonElement> e : ((JsonObject)paramsEl).entrySet()) {
            JsonElement value = e.getValue();
            if (! value.isJsonPrimitive()) {
                throw new IllegalArgumentException("Expecting parameter to have primitive value: "+value.toString());
            }
            String v = e.getValue().getAsString();
            params.put(e.getKey(), v);
        }
        return params;
    }

    /**
     * Plagiarized verbatim from Solr!
     */
    private static class MyTokenizerChain extends Analyzer {

        final private CharFilterFactory[] charFilters;
        final private TokenizerFactory tokenizer;
        final private TokenFilterFactory[] filters;

        public MyTokenizerChain(TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
            this(null, tokenizer, filters);
        }

        public MyTokenizerChain(CharFilterFactory[] charFilters, TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
            this.charFilters = charFilters;
            this.tokenizer = tokenizer;
            this.filters = filters;
        }

        public CharFilterFactory[] getCharFilterFactories() {
            return charFilters;
        }

        public TokenizerFactory getTokenizerFactory() {
            return tokenizer;
        }

        public TokenFilterFactory[] getTokenFilterFactories() {
            return filters;
        }

        @Override
        public Reader initReader(String fieldName, Reader reader) {

            if (charFilters != null && charFilters.length > 0) {
                Reader cs = reader;
                for (CharFilterFactory charFilter : charFilters) {
                    cs = charFilter.create(cs);
                }
                reader = cs;
            }

            return reader;
        }

        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tk = tokenizer.create();
            TokenStream ts = tk;
            for (TokenFilterFactory filter : filters) {
                ts = filter.create(ts);
            }

            return new TokenStreamComponents(tk, ts);
        }
    }

}
