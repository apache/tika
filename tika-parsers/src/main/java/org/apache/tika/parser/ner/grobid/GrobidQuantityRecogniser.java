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

package org.apache.tika.parser.ner.grobid;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class GrobidQuantityRecogniser {

  private static final String GROBID_REST_HOST = "http://localhost:8080";


  private static final String GROBID_QUANTITY_NER_PATH = "/processQuantityText";

  private String restHostUrlStr;
  
  public static final Set<String> ALL_MEASUREMENTS = new HashSet<String>(){{
      add("measurements");
  }};
  
  public GrobidQuantityRecogniser() {
    String restHostUrlStr = null;
    try {
      restHostUrlStr = readRestUrl();
    } catch (IOException e) {
      e.printStackTrace();
    }

    if (restHostUrlStr == null
        || (restHostUrlStr != null && restHostUrlStr.equals(""))) {
      this.restHostUrlStr = GROBID_REST_HOST;
    } else {
      this.restHostUrlStr = restHostUrlStr;
    }
  }
  
  
  
  
  public Map<String, Set<String>> recognise(String text)  {
      Map<String, Set<String>> entities = new HashMap<String, Set<String>>();
    try{
      text=URLEncoder.encode(text,"UTF-8");
      Response response = WebClient
          .create(restHostUrlStr + GROBID_QUANTITY_NER_PATH+ "?text=" + text)
          .accept(MediaType.APPLICATION_JSON)
          .get();
      int responseCode = response.getStatus();
          if (responseCode == 200) {
            String result = response.readEntity(String.class);
                JSONParser parser = new JSONParser();
                JSONObject j = (JSONObject) parser.parse(result);
                entities.put("measurements", new HashSet((Collection) j.get("measurements")));
          }
          else
          {
            System.out.println("GROBID-QUANTITY REST SERVICE NOT WORKING!");
          }
      } 
    catch (Exception e) {
        e.printStackTrace();
      }
    ALL_MEASUREMENTS.clear();
    ALL_MEASUREMENTS.addAll(entities.keySet());
        return entities;
  }

    private static String readRestUrl() throws IOException {
      Properties grobidProperties = new Properties();
      grobidProperties.load(GrobidQuantityRecogniser.class
          .getResourceAsStream("GrobidExtractor.properties"));
      return grobidProperties.getProperty("grobid.server.url");
  }
}
