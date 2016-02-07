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
package org.apache.tika.parser.ner.nltk;

import org.apache.http.client.methods.HttpGet;
import org.apache.tika.parser.ner.NERecogniser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;

/**
 *  This class offers an implementation of {@link NERecogniser} based on
 *  ne_chunk() module of NLTK. This NER requires additional setup,
 *  due to Http requests to an endpoint server that runs NLTK.
 *  See <a href="http://wiki.apache.org/tika/TikaAndNER#NLTK">
 *
 */
public class NLTKNERecogniser implements NERecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(NLTKNERecogniser.class);
    private final static String USER_AGENT = "Mozilla/5.0";
    private static boolean available = false;
    
     /**
     * some common entities identified by NLTK
     */
    public static final Set<String> ENTITY_TYPES = new HashSet<String>(){{
        add(PERSON);
        add(TIME);
        add(LOCATION);
        add(ORGANIZATION);
        add(MONEY);
        add(PERCENT);
        add(DATE);
        add(FACILITY);
        add(GPE);
    }};

    public NLTKNERecogniser(){
        try {
            String url = "http://localhost:5000/";
            Response response = WebClient.create(url).accept(MediaType.TEXT_HTML).get();
            int responseCode = response.getStatus();
            if(responseCode == 200){
                available = true;
            }
            else{
                LOG.info("NLTKRest Server is not running");
            }

        } catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
    }


    /**
     * @return {@code true} if server endpoint is available.
     * returns {@code false} if server endpoint is not avaliable for service.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Gets set of entity types recognised by this recogniser
     * @return set of entity classes/types
     */
    public Set<String> getEntityTypes() {
        return ENTITY_TYPES;
    }

    /**
     * recognises names of entities in the text
     * @param text text which possibly contains names
     * @return map of entity type -> set of names
     */
    public Map<String, Set<String>> recognise(String text) {
        Map<String, Set<String>> entities = new HashMap<>();
        try {
            String url = "http://localhost:5000/nltk";
            Response response = WebClient.create(url).accept(MediaType.TEXT_HTML).form(new Form().param("text",text));
            int responseCode = response.getStatus();
            if (responseCode == 200) {
                String result = response.readEntity(String.class);
                JSONParser parser = new JSONParser();
                JSONObject j = (JSONObject) parser.parse(result);
                JSONArray aa = new JSONArray();
                for (Object x : j.keySet()) {
                    aa = (JSONArray) j.get(x.toString());
                    Set s = new HashSet();
                    for (Object y : aa) {
                        s.add(y.toString());
                    }
                    entities.put(x.toString(), s);
                }
            }
        }
        catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }
        ENTITY_TYPES.clear();
        ENTITY_TYPES.addAll(entities.keySet());
        return entities;
    }


}
