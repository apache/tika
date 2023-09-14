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
package org.apache.tika.langdetect.mitll;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;


/**
 * Created by trevorlewis on 3/7/16.
 */

/**
 * Language Detection using MIT Lincoln Lab’s Text.jl library
 * https://github.com/trevorlewis/TextREST.jl
 * <p>
 * Please run the TextREST.jl server before using this.
 */
public class TextLangDetector extends LanguageDetector {

    private static final Logger LOG = LoggerFactory.getLogger(TextLangDetector.class);


    private static final String TEXT_REST_HOST = "http://localhost:8000";
    private static final String TEXT_LID_PATH = "/lid";

    private static String restHostUrlStr;

    private Set<String> languages;
    private CharArrayWriter writer;

    public TextLangDetector() {
        super();
        restHostUrlStr = TEXT_REST_HOST;
        languages = getAllLanguages();
        writer = new CharArrayWriter();
    }

    protected static boolean canRun() {
        try {
            Response response = WebClient.create(TEXT_REST_HOST + TEXT_LID_PATH).get();
            String json = response.readEntity(String.class);
            JsonNode jsonArray = new ObjectMapper().readTree(json).get("all_languages");
            return jsonArray.size() != 0;
        } catch (Exception e) {
            LOG.warn("Can't run", e);
            return false;
        }
    }

    @Override
    public LanguageDetector loadModels() throws IOException {
        return null;
    }

    @Override
    public LanguageDetector loadModels(Set<String> set) throws IOException {
        return null;
    }

    @Override
    public boolean hasModel(String language) {
        return languages.contains(language);
    }

    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        return null;
    }

    @Override
    public void reset() {
        writer.reset();
    }

    @Override
    public void addText(char[] cbuf, int off, int len) {
        writer.write(cbuf, off, len);
        writer.write(' ');
    }

    @Override
    public List<LanguageResult> detectAll() {
        List<LanguageResult> result = new ArrayList<>();
        String language = detect(writer.toString());
        if (language != null) {
            result.add(new LanguageResult(language, LanguageConfidence.MEDIUM, 1));
        } else {
            result.add(new LanguageResult(language, LanguageConfidence.NONE, 0));
        }
        return result;
    }

    private Set<String> getAllLanguages() {
        Set<String> languages = new HashSet<>();
        try {
            Response response = WebClient.create(restHostUrlStr + TEXT_LID_PATH).get();
            String json = response.readEntity(String.class);
            JsonNode jsonArray = new ObjectMapper().readTree(json).get("all_languages");
            for (JsonNode jsonElement : jsonArray) {
                languages.add(jsonElement.asText());
            }
        } catch (Exception e) {
            LOG.warn("problem getting and parsing json", e);
        }
        return languages;
    }

    private String detect(String content) {
        String language = null;
        try {
            Response response = WebClient.create(restHostUrlStr + TEXT_LID_PATH).put(content);
            String json = response.readEntity(String.class);
            language = new ObjectMapper().readTree(json).get("language").asText();
        } catch (Exception e) {
            LOG.warn("problem detecting", e);
        }
        return language;
    }
}
