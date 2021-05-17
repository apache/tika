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

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.exception.TikaException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;


/**
 * <p>This translator is designed to work with a TCP-IP available
 * RTG translation server, specifically the
 * <a href="https://isi-nlp.github.io/rtg/#_rtg_serve">
 * REST-based RTG server</a>.</p>
 * To get Docker image:
 *   https://hub.docker.com/repository/docker/tgowda/rtg-model <br/>
 * <pre>
 * {code
 * # without GPU
 *   docker run --rm -i -p 6060:6060 tgowda/rtg-model:500toEng-v1
 * # Or, with GPU device 0
 *   docker run --rm -i -p 6060:6060 --gpus '"device=0"' tgowda/rtg-model:500toEng-v1
 * }
 * </pre>
 *
 * <p>If you were to interact with the server via curl a request
 * would look as follows</p>
 *
 * <pre>
 * {code
 * curl --data "source=Comment allez-vous?" \
 *      --data "source=Bonne journée" \
 *      http://localhost:6060/translate
 * }
 * </pre>
 *
 * RTG requires input to be pre-formatted into sentences, one per line,
 * so this translation implementation takes care of that.
 */
public class RTGTranslator extends AbstractTranslator {

    public static final String RTG_TRANSLATE_URL_BASE = "http://localhost:6060";
    public static final String RTG_PROPS = "translator.rtg.properties";
    private static final Logger LOG = LoggerFactory.getLogger(RTGTranslator.class);
    private WebClient client;
    private boolean isAvailable = false;

    public RTGTranslator() {
        String rtgBaseUrl = RTG_TRANSLATE_URL_BASE;
        Properties config = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(RTG_PROPS)){
            if (stream != null){
                config.load(stream);
            }
            rtgBaseUrl = config.getProperty("rtg.base.url", rtgBaseUrl);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        }
        LOG.info("RTG base URL: " + rtgBaseUrl);
        List<Object> providers = new ArrayList<>();
        providers.add(new JacksonJsonProvider());
        try {
            this.client = WebClient.create(rtgBaseUrl, providers);
            this.isAvailable = client.head().getStatus() == 200;
        } catch (Exception e){
            LOG.warn(e.getMessage(), e);
            isAvailable = false;
        }

    }
    @Override
    public String translate(String text, String sourceLanguage, String targetLanguage)
            throws TikaException, IOException {
        return this.translate(text);
    }

    @Override
    public String translate(String text, String targetLanguage)
            throws TikaException, IOException {
        return this.translate(text);
    }

    public String translate(String text) throws TikaException, IOException {
        if (!this.isAvailable) {
            return text;
        }
        Map<String, List<Object>> input = new HashMap<>();
        input.put("source", Arrays.asList(text.split("(?<=(?<![A-Z])\\. )|\\n")));
        Response response = client.path("translate")
                .type(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .post(input);
        try (InputStreamReader reader = new InputStreamReader(
							      (InputStream) response.getEntity(), Charset.defaultCharset())) {
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject) parser.parse(reader);
            List<String> sentences = (List<String>) obj.get("translation");
            return String.join("\n", sentences);
        } catch (ParseException e){
            throw new IOException(e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }
}
