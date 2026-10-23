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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.core.EmitStrategy;
import org.apache.tika.pipes.core.EmitStrategyConfig;
import org.apache.tika.pipes.core.PipesConfig;
import org.apache.tika.pipes.core.PipesParser;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * A /pipes tuple's parse-context may tighten the server's limits but not loosen them: with the
 * server's writeLimit at 5, a tuple asking for an unlimited handler still hits the limit.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PipesRequestLimitsTest extends CXFTestBase {

    private static final String HELLO_WORLD = "hello_world.xml";

    private Path tmpDir;
    private Path tmpOutputFile;
    private Path tikaConfigPath;
    private PipesParser pipesParser;

    @Override
    @BeforeAll
    public void setUp() throws Exception {
        tmpDir = Files.createTempDirectory("tika-pipes-request-limits-");
        Path inputDir = tmpDir.resolve("input");
        Path outputDir = tmpDir.resolve("output");
        tmpOutputFile = outputDir.resolve(HELLO_WORLD + ".json");
        Files.createDirectories(inputDir);
        Files.createDirectories(outputDir);
        Files.copy(PipesRequestLimitsTest.class.getResourceAsStream("/test-documents/mock/" + HELLO_WORLD),
                inputDir.resolve(HELLO_WORLD));
        tikaConfigPath = Files.createTempFile(tmpDir, "tika-pipes-config-", ".json");
        CXFTestBase.createPluginsConfig(tikaConfigPath, inputDir, outputDir, null, 10000L);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(tikaConfigPath.toFile());
        ((ObjectNode) root.get("parse-context")).putObject("output-limits").put("writeLimit", 5);
        mapper.writeValue(tikaConfigPath.toFile(), root);
        super.setUp();
    }

    @Override
    @AfterAll
    public void tearDown() throws Exception {
        if (pipesParser != null) {
            pipesParser.close();
            pipesParser = null;
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
            TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(tikaConfigPath);
            PipesConfig pipesConfig = PipesConfig.load(tikaJsonConfig);
            pipesConfig.setEmitStrategy(new EmitStrategyConfig(EmitStrategy.EMIT_ALL));
            pipesParser = PipesParser.load(tikaJsonConfig, pipesConfig);
            providers.add(new SingletonResourceProvider(new PipesResource(pipesParser, pipesConfig)));
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

    @Test
    public void testRequestMayNotRaiseWriteLimit() throws Exception {
        String tuple = "{\"id\":\"" + HELLO_WORLD + "\"," +
                "\"fetcher\":\"" + FETCHER_ID + "\"," +
                "\"fetchKey\":\"" + HELLO_WORLD + "\"," +
                "\"emitter\":\"" + EMITTER_JSON_ID + "\"," +
                "\"emitKey\":\"\"," +
                "\"parse-context\":{\"basic-content-handler-factory\":" +
                "{\"type\":\"TEXT\",\"writeLimit\":-1,\"throwOnWriteLimitReached\":false}}}";
        Response response = WebClient
                .create(endPoint + "/pipes")
                .accept("application/json")
                .post(tuple);
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList;
        try (Reader reader = Files.newBufferedReader(tmpOutputFile)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        Metadata metadata = metadataList.get(0);
        assertEquals("true", metadata.get(TikaCoreProperties.WRITE_LIMIT_REACHED), metadata.toString());
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertTrue(content == null || !content.contains("hello world"), content);
    }
}
