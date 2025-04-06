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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.exception.TikaException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of a REST client for the FirstLanguage API.
 * You can sign up for an API Key online on the <a href="https://www.firstlanguage.in/">FirstLanguage Developer Portal</a>
 * and set your API Key in the <code>translator.firstlanguage.properties</code> file.
 */
public class FirstLanguageTranslator extends AbstractTranslator {

    private static final Logger LOG = LoggerFactory.getLogger(FirstLanguageTranslator.class);

    private static final String FL_TRANSLATE_URL_BASE = "https://api.firstlanguage.in/api/translate";

    private static final String DEFAULT_KEY = "dummy-key";

    private WebClient client;

    private String apiKey;

    private boolean isAvailable;

    public FirstLanguageTranslator() {
        this.client = WebClient.create(FL_TRANSLATE_URL_BASE);
        this.isAvailable = true;
        Properties config = new Properties();
        try {
            config.load(FirstLanguageTranslator.class
                    .getResourceAsStream(
                            "translator.firstlanguage.properties"));
            this.apiKey = config.getProperty("translator.api-key");
            if (this.apiKey.equals(DEFAULT_KEY))
                this.isAvailable = false;
        } catch (Exception e) {
            LOG.warn("Couldn't read config file", e);
            isAvailable = false;
        }
    }

    
    /**
     * This function sets the apiKey variable to the value of the apiKey parameter
     * 
     * @param apiKey The API key you got from the FirstLanguage Portal.
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.isAvailable=true;
    }

    /**
     * This function translates the text extracted from the URL passed 
     * to the target language specified. This function will read the file mentioned
     * in the URL and send the response as plaintext irrespective of the source file
     * contentType
     * 
     * 
     * @param url The url from which the text to be translated is extracted.
     * @param targetLanguage The target language.
     * @param sourceLanguage The source language.
     * @param contentType The content type of the url. It can be plaintext, html, pdf or docx
     * @param pathWithFileNameToSave The path where the translated PDF or DOCX is to be saved. 
     *                               This should include the filename.
     * 
     */
    public String translatePDFOrDOCXFile(String url, String targetLanguage, String contentType, 
                String pathWithFileNameToSave) throws TikaException, IOException {
        if (!this.isAvailable)
            return url;        

        if(url == null || url.isEmpty()){
            throw new TikaException("URL cannot be null or empty"); 
        }

        if(contentType == null || contentType.isEmpty()){
            throw new TikaException("Content Type cannot be null or empty"); 
        }

        if(contentType != "pdf" && contentType != "docx"){
            throw new TikaException("Content Type must be  pdf or docx for this method."); 
        }
        if(pathWithFileNameToSave == null || pathWithFileNameToSave.isEmpty()){
            throw new TikaException("Path with filename to save cannot be null or empty"); 
        }
        
        final List<Object> providers = new ArrayList<>();
        JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();
        providers.add(jacksonJsonProvider);
    
        client = WebClient.create(FL_TRANSLATE_URL_BASE, providers);
    
        ObjectMapper requestMapper = new ObjectMapper();
        ObjectNode jsonNode = requestMapper.createObjectNode();
        ObjectNode inputNode = requestMapper.createObjectNode();
        inputNode.put("lang", targetLanguage);
        inputNode.put("url", url);
        inputNode.put("contentType", contentType);
        inputNode.put("preserveFormat", "true");
        jsonNode.put("input", inputNode);
        //make the request
        Response response = client.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON)
                        .header("apikey", apiKey).post(jsonNode);
        
        try {
            byte[] ba1 = new byte[1024];
            int baLength=0;
            FileOutputStream fos1 = new FileOutputStream(pathWithFileNameToSave);
            InputStream is1 = (InputStream) response.getEntity();
            while ((baLength = is1.read(ba1)) != -1) {
                fos1.write(ba1, 0, baLength);
            }
            fos1.flush();
            fos1.close();
            is1.close();
            
        }catch (IOException e) {            
            throw new TikaException("Error while reading response");
        }
                
        return "File Translated";
    }
    
    /**
     * This function translates the text extracted from the URL passed 
     * to the target language specified. This function will read the file mentioned
     * in the URL and send the response as plaintext irrespective of the source file
     * contentType
     * 
     * 
     * @param url The url from which the text to be translated is extracted.
     * @param targetLanguage The target language.
     * @param sourceLanguage The source language.
     * @param contentType The content type of the url. It can be plaintext, html, pdf or docx
     * 
     * @return The translated text.
     */
    public String translateFromURL(String url, String targetLanguage, String contentType) throws TikaException, IOException {
        if (!this.isAvailable)
            return url;        

        if(url == null || url.isEmpty()){
            throw new TikaException("URL cannot be null or empty"); 
        }

        if(contentType == null || contentType.isEmpty()){
            throw new TikaException("Content Type cannot be null or empty"); 
        }

        if(contentType != "plaintext" && contentType != "html" && contentType != "pdf" && contentType != "docx"){
            throw new TikaException("Content Type must be plaintext, html, pdf or docx"); 
        }

        final List<Object> providers = new ArrayList<>();
        JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();
        providers.add(jacksonJsonProvider);
    
        client = WebClient.create(FL_TRANSLATE_URL_BASE, providers);
    
        ObjectMapper requestMapper = new ObjectMapper();
        ObjectNode jsonNode = requestMapper.createObjectNode();
        ObjectNode inputNode = requestMapper.createObjectNode();
        inputNode.put("lang", targetLanguage);
        inputNode.put("url", url);
        inputNode.put("contentType", contentType);
        jsonNode.put("input", inputNode);
        //make the request
        Response response = client.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON)
                        .header("apikey", apiKey).post(jsonNode);

        StringBuilder responseText = new StringBuilder();
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                (InputStream) response.getEntity(), UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader);
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
            responseText.append(line);
            }
        }
        
        try {
            ObjectMapper responseMapper = new ObjectMapper();
            if(contentType == "html" || contentType == "plaintext"){
                    JsonNode jsonResp = responseMapper.readTree(responseText.toString());
                    
                    if (jsonResp.findValuesAsText("generated_text") != null && jsonResp.findValuesAsText("generated_text").size() > 0) {
                        return jsonResp.findValuesAsText("generated_text").get(0);              
                    } else {
                    throw new TikaException("Exception while Translating...");
                    }
            }else if(contentType == "pdf" || contentType == "docx"){
                return responseText.toString();
            }
          } catch (JsonParseException e) {
            throw new TikaException("Error requesting translation '" + 
                 "' to '" + targetLanguage + "', JSON response "
                + "from FirstLanguage Server is not well formatted: " + responseText.toString());
          }
          return url;
    }


    @Override
    /**
     * This function translates the text passed to the target language specified.
     * 
     * @param text The text to be translated.
     * @param targetLanguage The target language.
     * @param sourceLanguage The source language.
     * 
     * @return The translated text.
     */
    public String translate(String text, String sourceLanguage,
                            String targetLanguage) throws TikaException, IOException {
        if (!this.isAvailable)
            return text;        

        final List<Object> providers = new ArrayList<>();
        JacksonJsonProvider jacksonJsonProvider = new JacksonJsonProvider();
        providers.add(jacksonJsonProvider);
    
        client = WebClient.create(FL_TRANSLATE_URL_BASE, providers);
    
        ObjectMapper requestMapper = new ObjectMapper();
        ObjectNode jsonNode = requestMapper.createObjectNode();
        ObjectNode inputNode = requestMapper.createObjectNode();
        inputNode.put("lang", targetLanguage);
        inputNode.put("text", text);
        jsonNode.put("input", inputNode);
        //make the request
        Response response = client.accept(MediaType.APPLICATION_JSON).type(MediaType.APPLICATION_JSON)
                        .header("apikey", apiKey).post(jsonNode);

        StringBuilder responseText = new StringBuilder();
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                (InputStream) response.getEntity(), UTF_8);
                BufferedReader reader = new BufferedReader(inputStreamReader);
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
            responseText.append(line);
            }
        }
        
        try {
            ObjectMapper responseMapper = new ObjectMapper();
            JsonNode jsonResp = responseMapper.readTree(responseText.toString());
            
            if (jsonResp.findValuesAsText("generated_text") != null && jsonResp.findValuesAsText("generated_text").size() > 0) {
                return jsonResp.findValuesAsText("generated_text").get(0);              
            } else {
              throw new TikaException("Exception while Translating...");
            }
          } catch (JsonParseException e) {
            throw new TikaException("Error requesting translation from '" + 
                sourceLanguage + "' to '" + targetLanguage + "', JSON response "
                + "from FirstLanguage Server is not well formatted: " + responseText.toString());
          }

    }

    @Override
    public String translate(String text, String targetLanguage)
            throws TikaException, IOException {
        if (!this.isAvailable)
            return text;
        
        String sourceLanguage = detectLanguage(text).getLanguage();
        return translate(text, sourceLanguage, targetLanguage);
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

}
