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

package org.apache.tika.language.translate.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;

/**
 * An implementation of a REST client to the <a
 * href="https://www.googleapis.com/language/translate/v2">Google Translate v2
 * API</a>. Based on the <a
 * href="http://hayageek.com/google-translate-api-tutorial/">great tutorial</a>
 * from <a href="http://hayageek.com">hayageek.com</a>. Set your API key in
 * translator.google.properties.
 */
public class GoogleTranslator extends AbstractTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleTranslator.class);

    private static final String GOOGLE_TRANSLATE_URL_BASE =
            "https://www.googleapis.com/language/translate/v2";

    private static final String DEFAULT_KEY = "dummy-secret";

    private WebClient client;

    private String apiKey;

    private boolean isAvailable;

    public GoogleTranslator() {
        this.client = WebClient.create(GOOGLE_TRANSLATE_URL_BASE);
        this.isAvailable = true;
        Properties config = new Properties();
        try {
            config.load(GoogleTranslator.class.getResourceAsStream("translator.google.properties"));
            this.apiKey = config.getProperty("translator.client-secret");
            if (this.apiKey.equals(DEFAULT_KEY)) {
                this.isAvailable = false;
            }
        } catch (Exception e) {
            LOG.warn("Exception reading config file", e);
            isAvailable = false;
        }
    }

    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage)
            throws TikaException, IOException {
        if (!this.isAvailable) {
            return text;
        }
        Response response = client.accept(MediaType.APPLICATION_JSON).query("key", apiKey)
                .query("source", sourceLanguage).query("target", targetLanguage).query("q", text)
                .get();

        StringBuilder responseText = new StringBuilder();
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                (InputStream) response.getEntity(), UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseText.append(line);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonResp = mapper.readTree(responseText.toString());
        return jsonResp.findValuesAsText("translatedText").get(0);
    }

    @Override
    public String translate(String text, String targetLanguage) throws TikaException, IOException {
        if (!this.isAvailable) {
            return text;
        }

        String sourceLanguage = detectLanguage(text).getLanguage();
        return translate(text, sourceLanguage, targetLanguage);
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

}
