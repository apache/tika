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

import org.apache.tika.parser.ner.NERecogniser;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;
import java.util.Locale;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;

/**
 *  This class offers an implementation of {@link NERecogniser} based on
 *  ne_chunk() module of NLTK. This NER requires additional setup,
 *  due to Http requests to an endpoint server that runs NLTK.
 *  See <a href="http://wiki.apache.org/tika/TikaAndNLTK">
 *
 */
public class NLTKNERecogniser implements NERecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(NLTKNERecogniser.class);
    private static boolean available = false;
    private static final String NLTK_REST_HOST = "http://localhost:8881";
    private String restHostUrlStr;
     /**
     * some common entities identified by NLTK
     */
    public static final Set<String> ENTITY_TYPES = new HashSet<String>(){{
        add("NAMES");
    }};


    public NLTKNERecogniser(){
        try {

            String restHostUrlStr="";
            try {
                restHostUrlStr = readRestUrl();
            } catch (IOException e) {
                LOG.warn("Can't read rest url", e);
            }

            if (restHostUrlStr == null || restHostUrlStr.equals("")) {
                this.restHostUrlStr = NLTK_REST_HOST;
            } else {
                this.restHostUrlStr = restHostUrlStr;
            }

            Response response = WebClient.create(restHostUrlStr).accept(MediaType.TEXT_HTML).get();
            int responseCode = response.getStatus();
            if(responseCode == 200){
                available = true;
            }
            else{
                LOG.info("NLTKRest Server is not running");
            }

        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    private static String readRestUrl() throws IOException {
        Properties nltkProperties = new Properties();
        nltkProperties.load(NLTKNERecogniser.class
                .getResourceAsStream("NLTKServer.properties"));

        return nltkProperties.getProperty("nltk.server.url");
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
            String url = restHostUrlStr + "/nltk";
            Response response = WebClient.create(url).accept(MediaType.TEXT_HTML).post(text);
            int responseCode = response.getStatus();
            if (responseCode == 200) {
                String result = response.readEntity(String.class);
                JSONParser parser = new JSONParser();
                JSONObject j = (JSONObject) parser.parse(result);
                Iterator<?> keys = j.keySet().iterator();
                while( keys.hasNext() ) {
                    String key = (String)keys.next();
                    if ( !key.equals("result") ) {
                        ENTITY_TYPES.add(key);
                        entities.put(key.toUpperCase(Locale.ENGLISH), new HashSet((Collection) j.get(key)));
                    }
                }
            }
        }
        catch (Exception e) {
            LOG.debug(e.getMessage(), e);
        }

        return entities;
    }


}
