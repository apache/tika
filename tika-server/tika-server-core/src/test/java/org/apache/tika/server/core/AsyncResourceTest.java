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
package org.apache.tika.server.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTupleList;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.AsyncResource;
import org.apache.tika.server.core.resource.PipesParsingHelper;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * /async request-validation contract: caller errors are 400s with the reason in
 * the body, and none of them may poison the shared workers.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AsyncResourceTest extends CXFTestBase {

    private static final String ASYNC_PATH = "/async";
    private static final int QUEUE_SIZE = 5;

    private Path tmpDir;
    private Path tmpOutputDir;
    private Path tikaConfigPath;
    private AsyncResource asyncResource;

    @Override
    @BeforeAll
    public void setUp() throws Exception {
        tmpDir = Files.createTempDirectory("tika-async-test-");
        Path inputDir = tmpDir.resolve("input");
        tmpOutputDir = tmpDir.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(tmpOutputDir);
        Files.copy(AsyncResourceTest.class.getResourceAsStream("/test-documents/mock/hello_world.xml"),
                inputDir.resolve("hello_world.xml"));

        tikaConfigPath = Files.createTempFile(tmpDir, "tika-async-config-", ".json");
        CXFTestBase.createPluginsConfig(tikaConfigPath, inputDir, tmpOutputDir, null, 10000L);
        setQueueSize(tikaConfigPath, QUEUE_SIZE);

        super.setUp();
    }

    private static void setQueueSize(Path configPath, int queueSize) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(configPath.toFile());
        ((ObjectNode) root.get("pipes")).put("queueSize", queueSize);
        mapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), root);
    }

    @Override
    @AfterAll
    public void tearDown() throws Exception {
        if (asyncResource != null) {
            asyncResource.shutdownNow();
            asyncResource = null;
        }
        super.tearDown();
        if (tmpDir != null) {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> providers = new ArrayList<>();
        try {
            asyncResource = new AsyncResource(tikaConfigPath);
            providers.add(new SingletonResourceProvider(asyncResource));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        sf.setResourceProviders(providers);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper());
        providers.add(new BadRequestExceptionMapper());
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return Files.newInputStream(tikaConfigPath);
    }

    private Response post(String body) {
        return WebClient
                .create(endPoint + ASYNC_PATH)
                .accept("application/json")
                .post(body);
    }

    private String tuplesBody(List<FetchEmitTuple> tuples) throws IOException {
        StringWriter writer = new StringWriter();
        JsonFetchEmitTupleList.toJson(tuples, writer);
        return writer.toString();
    }

    private FetchEmitTuple tuple(String id, String fetcherId, String emitterId) {
        return new FetchEmitTuple(id, new FetchKey(fetcherId, "hello_world.xml"),
                new EmitKey(emitterId, ""), new Metadata());
    }

    @Test
    public void testBareArrayIs400() throws Exception {
        String envelope = tuplesBody(List.of(tuple("t1", FETCHER_ID, EMITTER_JSON_ID)));
        String bareArray = new ObjectMapper()
                .readTree(envelope)
                .get(JsonFetchEmitTupleList.TUPLES)
                .toString();
        Response response = post(bareArray);
        assertEquals(400, response.getStatus());
        assertContains("tuples", getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testMalformedBodyIs400() throws Exception {
        Response response = post("this is not json");
        assertEquals(400, response.getStatus());
        assertContains("/async request body",
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testUnknownTupleFieldIs400() throws Exception {
        String body = "{\"tuples\":[{\"id\":\"x\",\"fetcher\":\"" + FETCHER_ID +
                "\",\"fetchKey\":\"hello_world.xml\",\"emitter\":\"" + EMITTER_JSON_ID +
                "\",\"bogusField\":1}]}";
        Response response = post(body);
        assertEquals(400, response.getStatus());
        assertContains("bogusField", getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testUnknownFetcherIs400() throws Exception {
        Response response = post(tuplesBody(List.of(tuple("t1", "no-such-fetcher", EMITTER_JSON_ID))));
        assertEquals(400, response.getStatus());
        assertContains("no-such-fetcher",
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testUnknownEmitterIs400() throws Exception {
        Response response = post(tuplesBody(List.of(tuple("t1", FETCHER_ID, "no-such-emitter"))));
        assertEquals(400, response.getStatus());
        assertContains("no-such-emitter",
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    @Test
    public void testReservedFetcherIdIs400() throws Exception {
        Response response = post(tuplesBody(
                List.of(tuple("t1", PipesParsingHelper.DEFAULT_FETCHER_ID, EMITTER_JSON_ID))));
        assertEquals(400, response.getStatus());
        assertContains("reserved", getStringFromInputStream((InputStream) response.getEntity()));
    }

    /** A batch bigger than the queue can EVER hold: retrying is futile, so 400, not 429. */
    @Test
    public void testBatchLargerThanQueueIs400() throws Exception {
        List<FetchEmitTuple> tuples = new ArrayList<>();
        for (int i = 0; i <= QUEUE_SIZE; i++) {
            tuples.add(tuple("t" + i, FETCHER_ID, EMITTER_JSON_ID));
        }
        Response response = post(tuplesBody(tuples));
        assertEquals(400, response.getStatus());
        assertContains("queue capacity",
                getStringFromInputStream((InputStream) response.getEntity()));
    }

    /** Caller errors above must not poison the workers: a valid batch still parses after them. */
    @Test
    public void testValidBatchStillAcceptedAndParsed() throws Exception {
        Response response = post(tuplesBody(List.of(tuple("t-ok", FETCHER_ID, EMITTER_JSON_ID))));
        assertEquals(200, response.getStatus());
        JsonNode body = new ObjectMapper().readTree((InputStream) response.getEntity());
        assertEquals("ok", body.get("status").asText());
        assertEquals(1, body.get("added").asInt());

        Path expected = tmpOutputDir.resolve("hello_world.xml.json");
        long deadline = System.currentTimeMillis() + 60_000;
        while (!Files.isRegularFile(expected) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertTrue(Files.isRegularFile(expected), "emitted json never appeared");
        List<Metadata> metadataList;
        try (java.io.Reader reader = Files.newBufferedReader(expected)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals("hello world", metadataList.get(0)
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim());
    }
}
