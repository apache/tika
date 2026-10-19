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
package org.apache.tika.parser.ner.grobid;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.parser.ner.NERecogniser;

public class GrobidNERecogniser implements NERecogniser {

    /*
     * Useful Entities from Grobid NER
     */
    public static final Set<String> ENTITY_TYPES = new HashSet<String>() {
        {
            add("MEASUREMENT_NUMBERS");
            add("MEASUREMENT_UNITS");
            add("MEASUREMENTS");
            add("NORMALIZED_MEASUREMENTS");
            add("MEASUREMENT_TYPES");
        }
    };
    private static final Logger LOG = LoggerFactory.getLogger(GrobidNERecogniser.class);
    private static final String GROBID_REST_HOST = "http://localhost:8060";
    private static final String ISALIVE_URL = "/service/isalive";
    private boolean available = false;
    private String restHostUrlStr;


    public GrobidNERecogniser() {
        try {

            String restHostUrlStr = "";
            try {
                restHostUrlStr = readRestUrl();
            } catch (IOException e) {
                LOG.warn("couldn't read rest url", e);
            }

            if (restHostUrlStr == null || restHostUrlStr.equals("")) {
                this.restHostUrlStr = GROBID_REST_HOST;
            } else {
                this.restHostUrlStr = restHostUrlStr;
            }

            this.available = isServerAlive(restHostUrlStr);

        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
    }

    private static boolean isServerAlive(String restHostUrlStr) {
        boolean available = false;
        try {
            int responseCode =
                    send(HttpRequest.newBuilder(URI.create(restHostUrlStr + ISALIVE_URL)).GET())
                            .statusCode();
            if (responseCode == 200) {
                available = true;
            } else {
                LOG.info("Grobid Quantities REST Server is not running");
            }
        } catch (Exception e) {
            LOG.info("Grobid Quantities REST Server is not running", e);
        }

        return available;
    }

    /**
     * Reads the GROBID REST URL from the properties file
     * returns the GROBID REST URL
     */
    private static String readRestUrl() throws IOException {
        Properties grobidProperties = new Properties();
        grobidProperties
                .load(GrobidNERecogniser.class.getResourceAsStream("GrobidServer.properties"));
        return grobidProperties.getProperty("grobid.server.url");
    }

    /**
     * Reads the GROBID REST Endpoint from the properties file
     * returns the GROBID REST Endpoint
     */
    private static String readRestEndpoint() throws IOException {
        Properties grobidProperties = new Properties();
        grobidProperties
                .load(GrobidNERecogniser.class.getResourceAsStream("GrobidServer.properties"));
        return grobidProperties.getProperty("grobid.endpoint.text");
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
     *
     * @return set of entity classes/types
     */
    public Set<String> getEntityTypes() {
        return ENTITY_TYPES;
    }

    /**
     * Converts JSON Object to JSON Array
     *
     * @return a JSON array
     */
    public JSONArray convertToJSONArray(JSONObject obj, String key) {
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray = (JSONArray) obj.get(key);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
        return jsonArray;
    }

    /**
     * Parses a JSON String and converts it to a JSON Object
     *
     * @return a JSON Object
     */
    public JSONObject convertToJSONObject(String jsonString) {
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject = (JSONObject) parser.parse(jsonString);
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);
        }
        return jsonObject;
    }

    /**
     * recognises names of entities in the text
     *
     * @param text text which possibly contains names
     * @return map of entity type -&gt; set of names
     */
    public Map<String, Set<String>> recognise(String text) {

        Map<String, Set<String>> entities = new HashMap<>();
        Set<String> measurementNumberSet = new HashSet<>();
        Set<String> unitSet = new HashSet<>();
        Set<String> measurementSet = new HashSet<>();
        Set<String> normalizedMeasurementSet = new HashSet<>();
        Set<String> measurementTypeSet = new HashSet<>();

        try {
            String url = restHostUrlStr + readRestEndpoint();
            HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "text=" + URLEncoder.encode(text, UTF_8), UTF_8)));
            int responseCode = response.statusCode();

            if (responseCode == 200) {
                String result = response.body();
                JSONObject jsonObject = convertToJSONObject(result);
                JSONArray measurements = convertToJSONArray(jsonObject, "measurements");
                for (Object measurement : measurements) {

                    StringBuilder measurementString = new StringBuilder();
                    StringBuilder normalizedMeasurementString = new StringBuilder();

                    JSONObject quantity = (JSONObject) convertToJSONObject(measurement.toString())
                            .get("quantity");
                    if (quantity != null) {
                        if (quantity.containsKey("rawValue")) {
                            String measurementNumber =
                                    (String) convertToJSONObject(quantity.toString())
                                            .get("rawValue");
                            measurementString.append(measurementNumber);
                            measurementString.append(" ");
                            measurementNumberSet.add(measurementNumber);
                        }

                        if (quantity.containsKey("normalizedQuantity")) {
                            String normalizedMeasurementNumber =
                                    convertToJSONObject(quantity.toString())
                                            .get("normalizedQuantity").toString();
                            normalizedMeasurementString.append(normalizedMeasurementNumber);
                            normalizedMeasurementString.append(" ");
                        }

                        if (quantity.containsKey("type")) {
                            String measurementType =
                                    (String) convertToJSONObject(quantity.toString()).get("type");
                            measurementTypeSet.add(measurementType);
                        }

                        JSONObject jsonObj = (JSONObject) convertToJSONObject(quantity.toString());
                        if (jsonObj.containsKey("rawUnit")) {
                            JSONObject rawUnit = (JSONObject) jsonObj.get("rawUnit");
                            String unitName =
                                    (String) convertToJSONObject(rawUnit.toString()).get("name");
                            unitSet.add(unitName);
                            measurementString.append(unitName);
                        }

                        if (jsonObj.containsKey("normalizedUnit")) {
                            JSONObject normalizedUnit = (JSONObject) jsonObj.get("normalizedUnit");
                            String normalizedUnitName =
                                    (String) convertToJSONObject(normalizedUnit.toString())
                                            .get("name");
                            normalizedMeasurementString.append(normalizedUnitName);
                        }

                        if (!measurementString.toString().equals("")) {
                            measurementSet.add(measurementString.toString());
                        }

                        if (!normalizedMeasurementString.toString().equals("")) {
                            normalizedMeasurementSet.add(normalizedMeasurementString.toString());
                        }
                    }

                }

                entities.put("MEASUREMENT_NUMBERS", measurementNumberSet);
                entities.put("MEASUREMENT_UNITS", unitSet);
                entities.put("MEASUREMENTS", measurementSet);
                entities.put("NORMALIZED_MEASUREMENTS", normalizedMeasurementSet);
                entities.put("MEASUREMENT_TYPES", measurementTypeSet);

            }
        } catch (Exception e) {
            LOG.info(e.getMessage(), e);

        }
        ENTITY_TYPES.clear();
        ENTITY_TYPES.addAll(entities.keySet());
        return entities;
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException {
        try {
            return ClientHolder.CLIENT.send(request.timeout(Duration.ofSeconds(60)).build(),
                    HttpResponse.BodyHandlers.ofString(UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static class ClientHolder {
        private static final HttpClient CLIENT =
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(30)).build();
    }
}
