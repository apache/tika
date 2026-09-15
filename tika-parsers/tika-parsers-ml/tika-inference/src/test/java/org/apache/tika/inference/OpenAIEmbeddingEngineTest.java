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
package org.apache.tika.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.parser.ParseContext;

public class OpenAIEmbeddingEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private TikaTestHttpServer server;
    private OpenAIEmbeddingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();
        engine = new OpenAIEmbeddingEngine();
        engine.setBaseUrl(server.url());
        engine.setModel("clip");
        engine.setApiKey("secret");
        engine.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        engine.close();
        server.shutdown();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testManyImagesOneRequestPlacedByIndex() throws Exception {
        // out of order on purpose: vectors are placed by index, not position
        server.enqueue(new TikaTestHttpServer.MockResponse(200, "{\"data\":["
                + "{\"index\":1,\"embedding\":[1.0,0.5]},{\"index\":0,\"embedding\":[0.0,0.5]}]}"));
        List<float[]> vectors = engine.embedImages(List.of(bytes("a"), bytes("b")),
                List.of("image/png", "image/jpeg"), new ParseContext());
        assertEquals(2, vectors.size());
        assertEquals(0.0f, vectors.get(0)[0]);
        assertEquals(1.0f, vectors.get(1)[0]);

        TikaTestHttpServer.RecordedRequest request = server.takeRequest();
        assertEquals("/v1/embeddings", request.path());
        assertEquals("Bearer secret", request.header("Authorization"));
        JsonNode body = MAPPER.readTree(request.body());
        assertEquals("clip", body.get("model").asText());
        assertEquals(2, body.get("input").size());
        assertEquals("data:image/png;base64,YQ==", body.get("input").get(0).get("image").asText());
        assertEquals("data:image/jpeg;base64,Yg==", body.get("input").get(1).get("image").asText());
    }

    @Test
    public void testTextsGoAsPlainStrings() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                "{\"data\":[{\"index\":1,\"embedding\":[1.0]},{\"index\":0,\"embedding\":[0.0]}]}"));
        List<float[]> vectors = engine.embedTexts(List.of("alpha", "beta"), new ParseContext());
        JsonNode request = new ObjectMapper().readTree(server.takeRequest().body());
        assertEquals("clip", request.get("model").asText());
        assertEquals("alpha", request.get("input").get(0).asText());
        assertEquals("beta", request.get("input").get(1).asText());
        assertEquals(0.0f, vectors.get(0)[0]);
        assertEquals(1.0f, vectors.get(1)[0]);
    }

    @Test
    public void testMalformedResponsesAreRefused() throws Exception {
        String[] bad = {
            "{\"data\":[{\"index\":0,\"embedding\":[1.0]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1.0]},{\"index\":0,\"embedding\":[2.0]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1.0]},{\"index\":5,\"embedding\":[2.0]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1.0]},{\"index\":1,\"embedding\":\"x\"}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[1.0]},{\"index\":1,\"embedding\":[\"NaN\"]}]}",
            "{}",
        };
        for (String body : bad) {
            server.enqueue(new TikaTestHttpServer.MockResponse(200, body));
            assertThrows(TikaException.class, () -> engine.embedImages(
                    List.of(bytes("a"), bytes("b")), List.of("image/png", "image/png"),
                    new ParseContext()), body);
        }
    }

    @Test
    public void testHttpErrorIsAnException() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(500, "boom"));
        assertThrows(TikaException.class, () -> engine.embedImages(List.of(bytes("a")),
                List.of("image/png"), new ParseContext()));
    }

    @Test
    public void testInitializeValidates() {
        OpenAIEmbeddingEngine bad = new OpenAIEmbeddingEngine();
        bad.setMaxBatchSize(0);
        assertThrows(org.apache.tika.exception.TikaConfigException.class, bad::initialize);
    }
}
