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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.AsyncResource;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * /pipes and /async honor allowPerRequestConfig the way the multipart config part does:
 * a tuple's parseContext is per-request configuration, so with the flag off (the default,
 * which CXFTestBase mirrors) a tuple that carries one is refused with 403 before anything
 * in it is resolved, and a tuple without one still parses.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PipesPerRequestConfigDisabledTest extends CXFTestBase {
    private static final String PIPES_PATH = "/pipes";
    private static final String ASYNC_PATH = "/async";
    private static final String HELLO_WORLD = "hello_world.xml";
    // the kind of entry the wire allowlist leaves unscanned: it relaxes the server's limits
    private static final String PARSE_CONTEXT = "{\"timeout-limits\":{\"progressTimeoutMillis\":100}}";

    private Path tmpDir;
    private Path tmpOutputFile;
    private Path tikaConfigPath;
    private PipesParser pipesParser;
    private AsyncResource asyncResource;

    // isAllowPerRequestConfig() is intentionally NOT overridden; it defaults to false
    // so a parseContext must be rejected.

    @Override
    @BeforeAll
    public void setUp() throws Exception {
        tmpDir = Files.createTempDirectory("tika-pipes-config-disabled-test-");
        Path inputDir = tmpDir.resolve("input");
        Path tmpOutputDir = tmpDir.resolve("output");
        tmpOutputFile = tmpOutputDir.resolve(HELLO_WORLD + ".json");
        Files.createDirectories(inputDir);
        Files.createDirectories(tmpOutputDir);
        Files.copy(PipesPerRequestConfigDisabledTest.class.getResourceAsStream("/test-documents/mock/" + HELLO_WORLD),
                inputDir.resolve(HELLO_WORLD));
        tikaConfigPath = Files.createTempFile(tmpDir, "tika-pipes-config-", ".json");
        CXFTestBase.createPluginsConfig(tikaConfigPath, inputDir, tmpOutputDir, null, 10000L);
        super.setUp();
    }

    @Override
    @AfterAll
    public void tearDown() throws Exception {
        if (pipesParser != null) {
            pipesParser.close();
            pipesParser = null;
        }
        if (asyncResource != null) {
            asyncResource.shutdownNow();
            asyncResource = null;
        }
        super.tearDown();
        if (tmpDir != null) {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    @BeforeEach
    public void setUpEachTest() throws Exception {
        Files.deleteIfExists(tmpOutputFile);
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> providers = new ArrayList<>();
        try {
            TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
            PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
            pipesConfig.setEmitStrategy(new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
            pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig);
            providers.add(new SingletonResourceProvider(
                    new PipesResource(pipesParser, pipesConfig, isAllowPerRequestConfig())));
            asyncResource = new AsyncResource(tikaConfigPath, isAllowPerRequestConfig());
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
        return new ByteArrayInputStream(Files.readAllBytes(tikaConfigPath));
    }

    private static String tuple(String parseContextJson) {
        return "{\"id\":\"" + HELLO_WORLD + "\"," +
                "\"fetcher\":\"" + FETCHER_ID + "\"," +
                "\"fetchKey\":\"" + HELLO_WORLD + "\"," +
                "\"emitter\":\"" + EMITTER_JSON_ID + "\"," +
                "\"emitKey\":\"\"" +
                (parseContextJson == null ? "" : ",\"parse-context\":" + parseContextJson) + "}";
    }

    private Response post(String path, String body) {
        return WebClient
                .create(endPoint + path)
                .accept("application/json")
                .post(body);
    }

    private static void assertPerRequestConfigDisabled(Response response) throws IOException {
        assertEquals(403, response.getStatus());
        String msg = getStringFromInputStream((InputStream) response.getEntity());
        assertTrue(msg.contains("Per-request configuration is disabled"),
                "expected the config-disabled message, got: " + msg);
    }

    @Test
    public void testPipesRejectsParseContextWhenDisabled() throws Exception {
        assertPerRequestConfigDisabled(post(PIPES_PATH, tuple(PARSE_CONTEXT)));
        assertFalse(Files.isRegularFile(tmpOutputFile), "nothing should be emitted for a rejected request");
    }

    @Test
    public void testAsyncRejectsParseContextWhenDisabled() throws Exception {
        assertPerRequestConfigDisabled(post(ASYNC_PATH, "{\"tuples\":[" + tuple(PARSE_CONTEXT) + "]}"));
        assertFalse(Files.isRegularFile(tmpOutputFile), "nothing should be queued for a rejected request");
    }

    @Test
    public void testPipesWithoutParseContextStillParses() throws Exception {
        Response response = post(PIPES_PATH, tuple(null));
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList;
        try (Reader reader = Files.newBufferedReader(tmpOutputFile)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        assertEquals("hello world", metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT).trim());
    }
}
