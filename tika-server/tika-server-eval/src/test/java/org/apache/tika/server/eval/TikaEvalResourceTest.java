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

package org.apache.tika.server.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.cxf.binding.BindingFactoryManager;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSBindingFactory;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.transport.common.gzip.GZIPInInterceptor;
import org.apache.cxf.transport.common.gzip.GZIPOutInterceptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.eval.core.metadata.TikaEvalMetadataFilter;
import org.apache.tika.server.core.ProduceTypeResourceComparator;
import org.apache.tika.server.core.ServerStatus;
import org.apache.tika.server.core.TikaServerConfig;
import org.apache.tika.server.core.writer.JSONObjWriter;

public class TikaEvalResourceTest {

    protected static final String END_POINT =
            "http://localhost:" + TikaServerConfig.DEFAULT_PORT;

    protected static final String COMPARE_END_POINT = END_POINT + "/eval/compare";
    protected static final String PROFILE_END_POINT = END_POINT + "/eval/profile";
    protected static Server SERVER;

    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    public static void setUp() throws Exception {
        ServerStatus serverStatus = new ServerStatus("", 0, true);
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        //set compression interceptors
        sf.setOutInterceptors(Collections.singletonList(new GZIPOutInterceptor()));
        sf.setInInterceptors(Collections.singletonList(new GZIPInInterceptor()));

        setUpResources(sf, serverStatus);
        setUpProviders(sf);
        sf.setAddress(END_POINT + "/");
        sf.setResourceComparator(new ProduceTypeResourceComparator());

        BindingFactoryManager manager = sf.getBus().getExtension(BindingFactoryManager.class);

        JAXRSBindingFactory factory = new JAXRSBindingFactory();
        factory.setBus(sf.getBus());

        manager.registerBindingFactory(JAXRSBindingFactory.JAXRS_BINDING_ID, factory);
        SERVER = sf.create();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        SERVER.stop();
        SERVER.destroy();
    }

    protected static void setUpResources(JAXRSServerFactoryBean sf, ServerStatus serverStatus) {
        sf.setResourceClasses(TikaEvalResource.class);
        TikaEvalResource tikaEvalResource = new TikaEvalResource();
        tikaEvalResource.setServerStatus(serverStatus);
        sf.setResourceProvider(TikaEvalResource.class,
                new SingletonResourceProvider(tikaEvalResource));
    }

    protected static void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Test
    public void testBasicProfile() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put(TikaEvalResource.ID, "1");
        request.put(TikaEvalResource.TEXT, "the quick brown fox jumped qwertyuiop");
        Response response = profile(request);
        Map<String, Object> results = deserialize(response);
        assertEquals(6, (int)results.get(TikaEvalMetadataFilter.NUM_TOKENS.getName()));
        assertEquals(0.166, (double)results.get(TikaEvalMetadataFilter.OUT_OF_VOCABULARY.getName()),
                0.01);
        assertEquals("eng", (String)results.get(TikaEvalMetadataFilter.LANGUAGE.getName()));
    }

    @Test
    public void testBasicCompare() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put(TikaEvalResource.ID, "1");
        request.put(TikaEvalResource.TEXT_A, "the quick brown fox jumped qwertyuiop");
        request.put(TikaEvalResource.TEXT_B, "the the the fast brown dog jumped qwertyuiop");
        Response response = compare(request);
        Map<String, Object> results = deserialize(response);
        assertEquals(6,
                (int)results.get(TikaEvalMetadataFilter.NUM_TOKENS.getName() + "A"));
        assertEquals(0.166,
                (double)results.get(TikaEvalMetadataFilter.OUT_OF_VOCABULARY.getName() + "A"),
                0.01);
        assertEquals("eng", results.get(TikaEvalMetadataFilter.LANGUAGE.getName() + "A"));

        assertEquals(0.666, (double)results.get(TikaEvalResource.DICE.getName()), 0.01);
        assertEquals(0.571, (double)results.get(TikaEvalResource.OVERLAP.getName()), 0.01);
    }

    private Map<String, Object> deserialize(Response response) throws IOException {
        TypeReference<HashMap<String, Object>> typeRef
                = new TypeReference<HashMap<String, Object>>() {};
        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader((InputStream)response.getEntity(),
                             StandardCharsets.UTF_8))) {
            return objectMapper.readValue(reader, typeRef);
        }
    }

    private Response profile(Map<String, String> request) throws JsonProcessingException {

        String jsonRequest = objectMapper//.writerWithDefaultPrettyPrinter()
                .writeValueAsString(request);
        return  WebClient.create(PROFILE_END_POINT)
                .type("application/json")
                .accept("application/json")
                .put(jsonRequest.getBytes(StandardCharsets.UTF_8));
    }

    private Response compare(Map<String, String> request) throws JsonProcessingException {

        String jsonRequest = objectMapper//.writerWithDefaultPrettyPrinter()
                .writeValueAsString(request);
        return  WebClient.create(COMPARE_END_POINT)
                .type("application/json")
                .accept("application/json")
                .put(jsonRequest.getBytes(StandardCharsets.UTF_8));
    }
}
