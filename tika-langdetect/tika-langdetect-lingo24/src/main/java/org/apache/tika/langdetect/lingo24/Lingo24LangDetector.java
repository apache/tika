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
package org.apache.tika.langdetect.lingo24;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.language.detect.LanguageConfidence;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;

/**
 * An implementation of a Language Detector using the
 * <a href="https://developer.lingo24.com/premium-machine-translation-api">Premium MT API v1</a>.
 * <br/>
 * You can sign up for an access plan online on the <a href="https://developer.lingo24.com/plans">Lingo24 Developer Portal</a>
 * and set your Application's User Key in the <code>langdetect.lingo24.properties</code> file.
 */
public class Lingo24LangDetector extends LanguageDetector {

    private static final Logger LOG = LoggerFactory.getLogger(Lingo24LangDetector.class);


    private static final String LINGO24_TRANSLATE_URL_BASE = "https://api.lingo24.com/mt/v1/";
    private static final String LINGO24_LANGID_ACTION = "langid";
    private static final String LINGO24_SOURCELANG_ACTION = "sourcelangs";
    private static final String DEFAULT_KEY = "dummy-key";

    private WebClient client;
    private String userKey;
    private Set<String> languages;
    private boolean isAvailable;

    private CharArrayWriter writer;

    /**
     * Default constructor which first checks for the presence of
     * the <code>langdetect.lingo24.properties</code> file to set the API Key.
     * <p>
     * If a key is available, it sets the detector as available and also loads
     * the languages supported by the detector.
     */
    public Lingo24LangDetector() {
        this.client = WebClient.create(LINGO24_TRANSLATE_URL_BASE + LINGO24_LANGID_ACTION);
        this.isAvailable = true;
        Properties config = new Properties();
        try {
            config.load(
                    Lingo24LangDetector.class.getResourceAsStream("langdetect.lingo24.properties"));

            this.userKey = config.getProperty("api.user-key");

            if (this.userKey.equals(DEFAULT_KEY)) {
                this.isAvailable = false;
            }
        } catch (Exception e) {
            LOG.warn("Couldn't load config", e);
            isAvailable = false;
        }
        writer = new CharArrayWriter();
        languages = getAllLanguages();
    }

    @Override
    public LanguageDetector loadModels() throws IOException {
        return this;
    }

    @Override
    public LanguageDetector loadModels(Set<String> set) throws IOException {
        return this;
    }

    @Override
    public boolean hasModel(String language) {
        return languages.contains(language);
    }

    @Override
    public LanguageDetector setPriors(Map<String, Float> languageProbabilities) throws IOException {
        return this;
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

    /**
     * Detects the content's language using the Lingo24 API.
     *
     * @param content the <code>String</code> content to be used for detection
     * @return the language detected or <code>null</code> if detection failed
     */
    private String detect(String content) {
        String language = null;

        if (!isAvailable) {
            return language;
        }

        Form form = new Form();
        form.param("user_key", userKey);
        form.param("q", content);

        Response response = client.accept(MediaType.APPLICATION_JSON).form(form);

        String json = response.readEntity(String.class);
        JsonNode element = null;
        try {
            element = new ObjectMapper().readTree(json);
        } catch (JsonProcessingException e) {
            LOG.warn("problem detecting ", e);
        }
        if (element.has("success") && element.get("success").asText().equals("true")) {
            language = element.get("lang").asText();
        }
        return language;
    }

    /**
     * Load the supported languages from the <a href="https://developer.lingo24.com/premium-machine-translation-api">Premium MT API</a>.
     * Support is continually expanding.
     *
     * @return <code>Set<String></code> of supported languages.
     */
    private Set<String> getAllLanguages() {
        Set<String> languages = new HashSet<>();

        if (!isAvailable) {
            return languages;
        }

        WebClient _client = null;
        try {
            _client = WebClient.create(LINGO24_TRANSLATE_URL_BASE + LINGO24_SOURCELANG_ACTION);
            Response response =
                    _client.accept(MediaType.APPLICATION_JSON).query("user_key", userKey).get();

            String json = response.readEntity(String.class);
            JsonNode jsonArray = new ObjectMapper().readTree(json).get("source_langs");
            for (JsonNode jsonElement : jsonArray) {
                languages.add(jsonElement.get(0).asText());
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("problem detecting", e);
        } finally {
            if (_client != null) {
                _client.close();
            }
        }
        return languages;
    }

    /**
     * @return true if this Detector is probably able to detect right now.
     */
    public boolean isAvailable() {
        return this.isAvailable;
    }
}
