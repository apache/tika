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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.http.TikaTestHttpServer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.TikaPagedText;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceUnit;
import org.apache.tika.parser.inference.InputKind;

public class EmbedTaskTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private TikaTestHttpServer server;
    private OpenAIEmbeddingEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        server = new TikaTestHttpServer();
        engine = new OpenAIEmbeddingEngine();
        engine.setBaseUrl(server.url());
        engine.setModel("clip");
        engine.setMaxBatchSize(2);
        engine.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        engine.close();
        server.shutdown();
    }

    private static String response(int... dims) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < dims.length; i++) {
            // out of order on purpose: the task must place by index
            int index = dims.length - 1 - i;
            sb.append(i > 0 ? "," : "").append("{\"index\":").append(index)
                    .append(",\"embedding\":[").append(index).append(".0,0.5]}");
        }
        return sb.append("]}").toString();
    }

    @TempDir
    Path tmp;

    private InferenceUnit unit(Metadata parent, String name, String page) throws Exception {
        Metadata target = new Metadata();
        target.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE, "INLINE");
        target.set(TikaCoreProperties.EMBEDDED_ID_PATH, "/" + name);
        target.set(TikaCoreProperties.RESOURCE_NAME_KEY, name + ".png");
        if (page != null) {
            target.set(TikaPagedText.PAGE_NUMBER, page);
        }
        Path file = Files.createTempFile(tmp, name, ".png");
        Files.write(file, name.getBytes(StandardCharsets.UTF_8));
        return new InferenceUnit(InputKind.IMAGES, MediaType.image("png"), target, parent, file);
    }

    private static InferenceBinding binding() {
        return new InferenceBinding("pics", "clip", InputKind.IMAGES, List.of("embed"), null,
                null, -1, -1, true);
    }

    @Test
    public void testBatchesByEngineSizeAndPlacesByIndex() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(0, 1)));
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(0)));
        Metadata parent = new Metadata();
        List<InferenceUnit> units = List.of(unit(parent, "a", "1"), unit(parent, "b", null),
                unit(parent, "c", null));

        new EmbedTask().run(binding(), units, engine, new ParseContext());

        assertEquals(2, server.getRequestCount(), "three units, batch size two");
        JsonNode first = MAPPER.readTree(server.takeRequest().body());
        assertEquals("clip", first.get("model").asText());
        assertEquals(2, first.get("input").size());
        assertEquals("data:image/png;base64,YQ==", first.get("input").get(0).get("image").asText());

        List<Chunk> chunks = ChunkSerializer.fromJson(parent.get(TikaCoreProperties.TIKA_CHUNKS));
        assertEquals(3, chunks.size());
        assertEquals(0.0f, chunks.get(0).getVector()[0], "unit a got index 0 despite the order");
        assertEquals(1.0f, chunks.get(1).getVector()[0]);
        assertEquals(1, chunks.get(0).getLocators().getPaginated().get(0).getPage());
        assertEquals("a.png", chunks.get(0).getLocators().getEmbedded().get(0).getName());
        assertEquals("/c", chunks.get(2).getLocators().getEmbedded().get(0).getIdPath());
        for (InferenceUnit unit : units) {
            assertNull(unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS), "moved to the parent");
        }
    }

    @Test
    public void testNoParentKeepsTheChunk() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(0)));
        InferenceUnit unit = unit(null, "solo", null);
        new EmbedTask().run(binding(), List.of(unit), engine, new ParseContext());
        assertEquals(1, ChunkSerializer.fromJson(
                unit.getTarget().get(TikaCoreProperties.TIKA_CHUNKS)).size());
    }

    @Test
    public void testValidateRejectsANonEmbeddingEngine() {
        assertThrows(TikaConfigException.class,
                () -> new EmbedTask().validate(binding(), new Engine() { }));
        InferenceBinding media = new InferenceBinding("clips", "clip", InputKind.MEDIA,
                List.of("embed"), null, null, -1, -1, true);
        assertThrows(TikaConfigException.class, () -> new EmbedTask().validate(media, engine),
                "embed takes IMAGES only");
    }

    @Test
    public void testDuplicateIndexIsRejected() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200,
                "{\"data\":[{\"index\":0,\"embedding\":[1.0]},{\"index\":0,\"embedding\":[2.0]}]}"));
        Metadata parent = new Metadata();
        assertThrows(org.apache.tika.exception.TikaException.class, () -> new EmbedTask().run(
                binding(), List.of(unit(parent, "a", null), unit(parent, "b", null)), engine,
                new ParseContext()));
    }

    @Test
    public void testFailedBatchRetriesUnitsSingly() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(400, "{\"error\":\"bad image\"}"));
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(0)));
        server.enqueue(new TikaTestHttpServer.MockResponse(400, "{\"error\":\"bad image\"}"));
        Metadata parent = new Metadata();
        List<InferenceUnit> units = List.of(unit(parent, "good", null), unit(parent, "bad", null));
        assertThrows(org.apache.tika.exception.TikaException.class,
                () -> new EmbedTask().run(binding(), units, engine, new ParseContext()),
                "the failure is still reported");
        assertEquals(3, server.getRequestCount(), "one batch, then one request per unit");
        assertEquals(1, ChunkSerializer.fromJson(parent.get(TikaCoreProperties.TIKA_CHUNKS)).size(),
                "the good unit keeps its vector");
    }

    @Test
    public void testResponseCountMustMatch() throws Exception {
        server.enqueue(new TikaTestHttpServer.MockResponse(200, response(0)));
        Metadata parent = new Metadata();
        assertThrows(org.apache.tika.exception.TikaException.class, () -> new EmbedTask().run(
                binding(), List.of(unit(parent, "a", null), unit(parent, "b", null)), engine,
                new ParseContext()));
    }
}
