/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;
import org.apache.tika.utils.ProcessUtils;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
public class TikaPipesTest extends CXFTestBase {

    private static final String PIPES_PATH = "/pipes";
    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static Path TMP_OUTPUT_FILE;
    private static Path TIKA_PIPES_LOG4j2_PATH;
    private static Path TMP_NPE_OUTPUT_FILE;
    private static Path TIKA_CONFIG_PATH;
    private static String TIKA_CONFIG_XML;
    private static FetcherManager FETCHER_MANAGER;
    private static String HELLO_WORLD = "hello_world.xml";
    private static String HELLO_WORLD_JSON = "hello_world.xml.json";
    private static String NPE_JSON = "null_pointer.xml.json";

    private static String[] VALUE_ARRAY = new String[]{"my-value-1", "my-value-2", "my-value-3"};

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-pipes-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        TMP_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve(HELLO_WORLD_JSON);
        TMP_NPE_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve("null_pointer.xml.json");

        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);

        for (String mockFile : new String[]{"hello_world.xml", "null_pointer.xml"}) {
            Files.copy(
                    TikaPipesTest.class.getResourceAsStream("/test-documents/mock/" + mockFile),
                    inputDir.resolve(mockFile));
        }
        TIKA_CONFIG_PATH = Files.createTempFile(TMP_DIR, "tika-pipes-", ".xml");
        TIKA_PIPES_LOG4j2_PATH = Files.createTempFile(TMP_DIR, "log4j2-", ".xml");
        Files.copy(TikaPipesTest.class.getResourceAsStream("/log4j2.xml"), TIKA_PIPES_LOG4j2_PATH,
                StandardCopyOption.REPLACE_EXISTING);
        TIKA_CONFIG_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<properties>" + "<fetchers>" +
                        "<fetcher class=\"org.apache.tika.pipes.fetcher.fs.FileSystemFetcher\">" +
                        "<name>fsf</name>" +
                        "<basePath>" + inputDir.toAbsolutePath() +
                        "</basePath>" + "</fetcher>" + "</fetchers>" + "<emitters>" +
                        "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" +
                        "<name>fse</name>" +
                        "<basePath>" +
                        TMP_OUTPUT_DIR.toAbsolutePath() + "</basePath>" +
                        "</emitter>" +
                        "</emitters>" + "<pipes><tikaConfig>" +
                ProcessUtils.escapeCommandLine(TIKA_CONFIG_PATH.toAbsolutePath().toString()) +
                        "</tikaConfig><numClients>10</numClients>" +
                        "<forkedJvmArgs>" +
                        "<arg>-Xmx256m</arg>" +
                        "<arg>-Dlog4j.configurationFile=file:" +
                        ProcessUtils.escapeCommandLine(TIKA_PIPES_LOG4j2_PATH.toAbsolutePath().toString()) + "</arg>" +
                        "</forkedJvmArgs>" +
                        "</pipes>" + "</properties>";
        Files.write(TIKA_CONFIG_PATH, TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
    }

    @BeforeEach
    public void setUpEachTest() throws Exception {
        if (Files.exists(TMP_OUTPUT_FILE)) {
            Files.delete(TMP_OUTPUT_FILE);
        }
        if (Files.exists(TMP_NPE_OUTPUT_FILE)) {
            Files.delete(TMP_NPE_OUTPUT_FILE);
        }
        assertFalse(Files.isRegularFile(TMP_OUTPUT_FILE));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<>();
        try {
            rCoreProviders.add(new SingletonResourceProvider(new PipesResource(TIKA_CONFIG_PATH)));
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
    protected InputStream getTikaConfigInputStream() {
        return new ByteArrayInputStream(TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected InputStreamFactory getInputStreamFactory(InputStream tikaConfigInputStream) {
        return new FetcherStreamFactory(FETCHER_MANAGER);
    }


    @Test
    public void testPost() throws Exception {

        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (String s : VALUE_ARRAY) {
            userMetadata.add("my-key-multi", s);
        }

        FetchEmitTuple t =
                new FetchEmitTuple("myId",
                        new FetchKey("fsf", "hello_world.xml"), new EmitKey("fse", ""),
                        userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response =
                WebClient.create(getUrl).accept("application/json").post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("hello world", metadata.get(TikaCoreProperties.TIKA_CONTENT).trim());
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

        FetchEmitTuple t =
                new FetchEmitTuple("myId",
                        new FetchKey("fsf", "hello_world.xml"),
                        new EmitKey("fse", ""),
                        userMetadata,
                        new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.XML,
                                HandlerConfig.PARSE_MODE.RMETA, -1, -1, true),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response =
                WebClient.create(getUrl).accept("application/json").post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertContains("<p>hello world</p>", metadata.get(TikaCoreProperties.TIKA_CONTENT).trim());
    }

    @Test
    public void testPostNPE() throws Exception {
        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (String s : VALUE_ARRAY) {
            userMetadata.add("my-key-multi", s);
        }

        FetchEmitTuple t =
                new FetchEmitTuple("myId",
                        new FetchKey("fsf", "null_pointer.xml"),
                        new EmitKey("fse", ""),
                        userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response =
                WebClient.create(getUrl).accept("application/json").post(writer.toString());
        assertEquals(200, response.getStatus());

        JsonNode jsonResponse;
        try (Reader reader = new InputStreamReader((InputStream) response.getEntity(),
                StandardCharsets.UTF_8)) {
            jsonResponse = new ObjectMapper().readTree(reader);
        }
        String parseException = jsonResponse.get("parse_exception").asText();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);
        assertTrue(jsonResponse.get("emitted").asBoolean());
        List<Metadata> metadataList;
        try (Reader reader = Files
                .newBufferedReader(TMP_OUTPUT_DIR.resolve("null_pointer.xml.json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("my-value", metadata.get("my-key"));
        assertArrayEquals(VALUE_ARRAY, metadata.getValues("my-key-multi"));
        assertContains("NullPointerException",
                metadata.get(TikaCoreProperties.CONTAINER_EXCEPTION));
    }

    @Test
    public void testPostNPENoEmit() throws Exception {
        FetchEmitTuple t =
                new FetchEmitTuple("myId",
                        new FetchKey("fsf", "null_pointer.xml"),
                        new EmitKey("fse", ""),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response =
                WebClient.create(getUrl).accept("application/json").post(writer.toString());
        assertEquals(200, response.getStatus());

        JsonNode jsonResponse;
        try (Reader reader = new InputStreamReader((InputStream) response.getEntity(),
                StandardCharsets.UTF_8)) {
            jsonResponse = new ObjectMapper().readTree(reader);
        }
        String parseException = jsonResponse.get("parse_exception").asText();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);
        assertFalse(jsonResponse.get("emitted").asBoolean());
        assertFalse(Files.isRegularFile(TMP_NPE_OUTPUT_FILE));
    }
}
