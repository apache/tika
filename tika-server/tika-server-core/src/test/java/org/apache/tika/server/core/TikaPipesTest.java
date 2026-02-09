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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TikaPipesTest extends CXFTestBase {

    private static final String PIPES_PATH = "/pipes";
    private Path tmpDir;
    private Path tmpOutputDir;
    private Path tmpOutputFile;
    private Path tikaPipesLog4j2Path;
    private Path tmpNpeOutputFile;
    private Path tikaConfigPath;
    private static final String HELLO_WORLD = "hello_world.xml";
    private static final String HELLO_WORLD_JSON = "hello_world.xml.json";
    private static final String NPE_JSON = "null_pointer.xml.json";

    private static final String[] VALUE_ARRAY = new String[]{"my-value-1", "my-value-2", "my-value-3"};

    private PipesResource pipesResource;

    @Override
    @BeforeAll
    public void setUp() throws Exception {
        // Initialize test directories and config before parent setup
        tmpDir = Files.createTempDirectory("tika-pipes-test-");
        Path inputDir = tmpDir.resolve("input");
        tmpOutputDir = tmpDir.resolve("output");
        tmpOutputFile = tmpOutputDir.resolve(HELLO_WORLD_JSON);
        tmpNpeOutputFile = tmpOutputDir.resolve("null_pointer.xml.json");

        Files.createDirectories(inputDir);
        Files.createDirectories(tmpOutputDir);

        for (String mockFile : new String[]{"hello_world.xml", "null_pointer.xml"}) {
            Files.copy(TikaPipesTest.class.getResourceAsStream("/test-documents/mock/" + mockFile), inputDir.resolve(mockFile));
        }
        tikaPipesLog4j2Path = Files.createTempFile(tmpDir, "log4j2-", ".xml");
        Files.copy(TikaPipesTest.class.getResourceAsStream("/log4j2.xml"), tikaPipesLog4j2Path, StandardCopyOption.REPLACE_EXISTING);

        tikaConfigPath = Files.createTempFile(tmpDir, "tika-pipes-config-", ".json");
        CXFTestBase.createPluginsConfig(tikaConfigPath, inputDir, tmpOutputDir, null, 10000L);

        // Now call parent setup which will use our config
        super.setUp();
    }

    @Override
    @AfterAll
    public void tearDown() throws Exception {
        if (pipesResource != null) {
            pipesResource.close();
            pipesResource = null;
        }
        super.tearDown();
        if (tmpDir != null) {
            FileUtils.deleteDirectory(tmpDir.toFile());
        }
    }

    @BeforeEach
    public void setUpEachTest() throws Exception {
        if (Files.exists(tmpOutputFile)) {
            Files.delete(tmpOutputFile);
        }
        if (Files.exists(tmpNpeOutputFile)) {
            Files.delete(tmpNpeOutputFile);
        }
        assertFalse(Files.isRegularFile(tmpOutputFile));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<>();
        try {
            pipesResource = new PipesResource(tikaConfigPath);
            rCoreProviders.add(new SingletonResourceProvider(pipesResource));
        } catch (IOException | TikaConfigException e) {
            throw new RuntimeException(e);
        }
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(true));
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(tikaConfigPath));
    }



    @Test
    public void testPost() throws Exception {

        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (String s : VALUE_ARRAY) {
            userMetadata.add("my-key-multi", s);
        }

        FetchEmitTuple t = new FetchEmitTuple("myId",
                new FetchKey(FETCHER_ID, "hello_world.xml"), new EmitKey(EMITTER_JSON_ID, ""), userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(tmpOutputFile)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("hello world", metadata
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim());
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("你好，世界", metadata.get("title"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("my-value", metadata.get("my-key"));
        assertArrayEquals(VALUE_ARRAY, metadata.getValues("my-key-multi"));
    }

    @Test
    public void testPostXML() throws Exception {

        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (String s : VALUE_ARRAY) {
            userMetadata.add("my-key-multi", s);
        }
        ParseContext parseContext = new ParseContext();
        parseContext.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        FetchEmitTuple t =
                new FetchEmitTuple("myId", new FetchKey(FETCHER_ID, "hello_world.xml"),
                        new EmitKey(EMITTER_JSON_ID, ""), userMetadata, parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(tmpOutputFile)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertContains("<p>hello world</p>", metadata
                .get(TikaCoreProperties.TIKA_CONTENT)
                .trim());
    }

    @Test
    public void testPostNPE() throws Exception {
        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (String s : VALUE_ARRAY) {
            userMetadata.add("my-key-multi", s);
        }

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey(FETCHER_ID,
                "null_pointer.xml"), new EmitKey(EMITTER_JSON_ID, ""), userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        JsonNode jsonResponse;
        try (Reader reader = new InputStreamReader((InputStream) response.getEntity(), StandardCharsets.UTF_8)) {
            jsonResponse = new ObjectMapper().readTree(reader);
        }
        String parseException = jsonResponse
                .get("parse_exception")
                .asText();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);
        assertTrue(jsonResponse
                .get("emitted")
                .asBoolean());
        List<Metadata> metadataList;
        try (Reader reader = Files.newBufferedReader(tmpOutputDir.resolve("null_pointer.xml.json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("my-value", metadata.get("my-key"));
        assertArrayEquals(VALUE_ARRAY, metadata.getValues("my-key-multi"));
        assertContains("NullPointerException", metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    @Test
    public void testPostNPENoEmit() throws Exception {
        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey(FETCHER_ID,
                "null_pointer.xml"), new EmitKey(EMITTER_JSON_ID, ""), new Metadata(), new ParseContext(),
                FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        JsonNode jsonResponse;
        try (Reader reader = new InputStreamReader((InputStream) response.getEntity(), StandardCharsets.UTF_8)) {
            jsonResponse = new ObjectMapper().readTree(reader);
        }
        String parseException = jsonResponse
                .get("parse_exception")
                .asText();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);
        assertFalse(jsonResponse
                .get("emitted")
                .asBoolean());
        assertFalse(Files.isRegularFile(tmpNpeOutputFile));
    }
}
