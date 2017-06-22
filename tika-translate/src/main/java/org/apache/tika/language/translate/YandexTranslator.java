/**
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

package org.apache.tika.language.translate;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.translate.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An implementation of a REST client for the YANDEX <a href="https://tech.yandex.com/translate/">Translate API</a>.
 * You can sign up for free access online on the <a href="https://tech.yandex.com/key/form.xml?service=trnsl">API Key form</a>
 * and set your Application's User Key in the <code>translator.yandex.properties</code> file.
 */
public class YandexTranslator implements Translator {

    private static final Logger LOG = LoggerFactory.getLogger(YandexTranslator.class);

    /**
     * Yandex Translate API service end-point URL
     */
    private static final String YANDEX_TRANSLATE_URL_BASE = "https://translate.yandex.net/api/v1.5/tr.json/translate";

    /**
     * Default USer-Key, a real User-Key must be provided before the Lingo24 can successfully request translations
     */
    private static final String DEFAULT_KEY = "dummy-key";

    /**
     * Identifies the client of the request, used for authentication 
     */
    private String apiKey;
    
    /**
     * The Yandex Translate API can handle text in <b>plain</b> and/or <b>html</b> format, the default
     * format is <b>plain</b>
     */
    private String format = "plain";

    public YandexTranslator() {
        Properties config = new Properties();
        try {
            config.load(YandexTranslator.class
                    .getResourceAsStream(
                            "translator.yandex.properties"));
            this.apiKey = config.getProperty("translator.api-key");
            this.format = config.getProperty("translator.text.format");
        } catch (Exception e) {
            LOG.warn("Exception loading Yandex config", e);
        }
    }

    @Override
    public String translate(String text, String sourceLanguage,
            String targetLanguage) throws TikaException, IOException {
        if (!this.isAvailable()) {
            return text;
        }
        
        WebClient client = WebClient.create(YANDEX_TRANSLATE_URL_BASE);
        
        String langCode;
        
        if (sourceLanguage == null) {
            //Translate Service will identify source language
            langCode = targetLanguage;
        } else {
            //Source language is well known
            langCode = sourceLanguage + '-' + targetLanguage;
        }

        //TODO Add support for text over 10k characters
        Response response = client.accept(MediaType.APPLICATION_JSON)
                .query("key", this.apiKey).query("lang", langCode)
                .query("text", text).get();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                (InputStream) response.getEntity(), UTF_8));
        String line = null;
        StringBuffer responseText = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            responseText.append(line);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonResp = mapper.readTree(responseText.toString());
            
            if (!jsonResp.findValuesAsText("code").isEmpty()) {
                String code = jsonResp.findValuesAsText("code").get(0);
                if (code.equals("200")) {
                    return jsonResp.findValue("text").get(0).asText();
                } else {
                    throw new TikaException(jsonResp.findValue("message").get(0).asText());
                }
            } else {
                throw new TikaException("Return message not recognized: " + responseText.toString().substring(0, Math.min(responseText.length(), 100)));
            }
        } catch (JsonParseException e) {
            throw new TikaException("Error requesting translation from '" + sourceLanguage + "' to '" + targetLanguage + "', JSON response from Lingo24 is not well formatted: " + responseText.toString());
        }
    }


    /**
     * Get the API Key in use for client authentication
     * @return API Key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Set the API Key for client authentication
     * @param apiKey API Key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Retrieve the current text format setting.
     * The Yandex Translate API can handle text in <b>plain</b> and/or <b>html</b> format, the default
     * format is <b>plain</b>
     * @return
     */
    public String getFormat() {
        return format;
    }

    /**
     * Set the text format to use (plain/html)
     * @param format Text format setting, either plain or html
     */
    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public String translate(String text, String targetLanguage)
            throws TikaException, IOException {
        return this.translate(text, null, targetLanguage);
    }

    @Override
    public boolean isAvailable() {
        return this.apiKey!=null && !this.apiKey.equals(DEFAULT_KEY);
    }

}
