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
package org.apache.tika.language.translate;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.exception.TikaException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <p>This translator is designed to work with a TCP-IP available
 * Joshua translation server, specifically the
 * <a href="https://github.com/joshua-decoder/joshua_translation_engine">
 * REST-based Joshua server</a>.</p>
 * 
 * <p>If you were to interact with the server via curl a request
 * would look as follows</p>
 * 
 * <pre>
 * {code
 * curl http://localhost:5000/joshua/translate/english \
 *   -i -H "Content-Type: application/json" \
 *   -X POST -d '{"inputLanguage": "Spanish", "inputText": "vuelo"}' -v
 * }
 * </pre>
 * 
 * Joshua requires input to be pre-formatted into sentences, one per line,
 * so this translation implementation takes care of that.
 */
public class JoshuaNetworkTranslator extends AbstractTranslator {

  private static final String PROPERTIES_FILE = "translator.joshua.properties";

  private String JOSHUA_SERVER = "joshua.server.url";

  private String networkServer;
  
  private WebClient client;

  /**
   * Default constructor which first checks for the presence of
   * the <code>translator.joshua.properties</code> file. 
   * We check if the remote server is available on each 
   * translation process. This check is not a remote call, but instead
   * a check for null value within of a local variable represetning the 
   * value for <code>joshua.server.url</code>, which should be populated 
   * within the <code>translator.joshua.properties</code> file.
   */
  public JoshuaNetworkTranslator() {
    Properties props = new Properties();
    InputStream stream;
    stream = JoshuaNetworkTranslator.class.getResourceAsStream(PROPERTIES_FILE);
    try {
      if(stream != null) {
        props.load(stream);
        networkServer = props.getProperty(JOSHUA_SERVER);
      }
    } catch (IOException e) {
      // Error with properties file. Translation will not work.
      e.printStackTrace();
    }
  }

  /**
   * <p>Initially then check if the source language has been provided.
   * If no source language (or a null value) has been provided then
   * we make an attempt to guess the source using Tika's
   * {@link org.apache.tika.langdetect.OptimaizeLangDetector}. If we
   * are still unable to guess the language then we return the source
   * text.</p>
   * 
   * <p>We then process the input text into a new string consisting of 
   * sentences, one per line e.g. insert \n between the presence of '.'</p>
   * 
   * @see org.apache.tika.language.translate.Translator#translate
   * (java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public String translate(String text, String sourceLanguage,
      String targetLanguage) throws TikaException, IOException {
    if (!this.isAvailable())
      return text;

    //make an attempt to guess language if one is not provided.
    if (sourceLanguage == null)
      sourceLanguage = detectLanguage(text).getLanguage();

    //process input text into sentences, one per line 
    // e.g. insert \n between the presence of '.'
    StringBuilder sb = new StringBuilder(text);
    int i = 0;
    while ((i = sb.indexOf(".", i + 1)) != -1) {
      sb.replace(i, i + 1, "\n");
    }

    text = sb.toString();

    //create client
    if (!networkServer.endsWith("/")) {
      client = WebClient.create(networkServer + "/" + targetLanguage + "/");
    } else {
      client = WebClient.create(networkServer + targetLanguage + "/");
    }

    //make the reuest
    Response response = client.accept(MediaType.APPLICATION_JSON)
        .query("inputLanguage", sourceLanguage)
        .query("inputText", text).get();
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
        throw new TikaException("Return message not recognized: " + 
            responseText.toString().substring(0, Math.min(responseText.length(), 100)));
      }
    } catch (JsonParseException e) {
      throw new TikaException("Error requesting translation from '" + 
          sourceLanguage + "' to '" + targetLanguage + "', JSON response "
          + "from Joshua REST Server is not well formatted: " + responseText.toString());
    }
  }

  /**
   * Make an attempt to guess the source language via
   * {@link org.apache.tika.language.translate.AbstractTranslator#detectLanguage(String)} 
   * before making the call to 
   * {@link org.apache.tika.language.translate.JoshuaNetworkTranslator#translate(String, String, String)}
   * @see org.apache.tika.language.translate.Translator#translate(java.lang.String, java.lang.String)
   */
  @Override
  public String translate(String text, String targetLanguage)
      throws TikaException, IOException {
    if (isAvailable())
      return text;
    String sourceLanguage = detectLanguage(text).getLanguage();
    return translate(text, sourceLanguage, targetLanguage);
  }

  /**
   * @see org.apache.tika.language.translate.Translator#isAvailable()
   */
  @Override
  public boolean isAvailable() {
    return this.networkServer!=null;
  }

}
