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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonFetchEmitTuple;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.server.core.resource.EmitterResource;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
public class TikaEmitterTest extends CXFTestBase {

    private static final String EMITTER_PATH = "/emit";
    private static final String EMITTER_PATH_AND_FS = "/emit/fse";
    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static Path TMP_OUTPUT_FILE;
    private static String TIKA_CONFIG_XML;
    private static FetcherManager FETCHER_MANAGER;
    private static EmitterManager EMITTER_MANAGER;
    private static String HELLO_WORLD = "hello_world.xml";
    private static String HELLO_WORLD_JSON = "hello_world.xml.json";

    private static String[] VALUE_ARRAY = new String[]{"my-value-1", "my-value-2", "my-value-3"};

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-emitter-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        TMP_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve(HELLO_WORLD_JSON);
        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);

        for (String mockFile : new String[]{"hello_world.xml", "null_pointer.xml"}) {
            Files.copy(
                    TikaEmitterTest.class.getResourceAsStream("/test-documents/mock/" + mockFile),
                    inputDir.resolve(mockFile));
        }

        TIKA_CONFIG_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<properties>" + "<fetchers>" +
                        "<fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">" +
                        "<params>" + "<name>fsf</name>" +
                        "<basePath>" + inputDir.toAbsolutePath() +
                        "</basePath>" + "</params>" + "</fetcher>" + "</fetchers>" + "<emitters>" +
                        "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" +
                        "<params>" + "<name>fse</name>" +
                        "<basePath>" +
                        TMP_OUTPUT_DIR.toAbsolutePath() + "</basePath>" + "</params>" +
                        "</emitter>" +
                        "</emitters>" + "</properties>";
        Path tmp = Files.createTempFile("tika-emitter-", ".xml");
        Files.write(tmp, TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
        FETCHER_MANAGER = FetcherManager.load(tmp);
        EMITTER_MANAGER = EmitterManager.load(tmp);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
    }

    @Before
    public void setUpEachTest() throws Exception {
        if (Files.exists(TMP_OUTPUT_FILE)) {
            Files.delete(TMP_OUTPUT_FILE);
        }
        assertFalse(Files.isRegularFile(TMP_OUTPUT_FILE));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<ResourceProvider>();
        rCoreProviders.add(new SingletonResourceProvider(new EmitterResource(FETCHER_MANAGER, EMITTER_MANAGER)));
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
    protected InputStreamFactory getInputStreamFactory(TikaConfig tikaConfig) {
        return new FetcherStreamFactory(FETCHER_MANAGER);
    }

    @Test
    public void testGet() throws Exception {

        String q = "?fn=fsf&fk=hello_world.xml&type=text";
        String getUrl = endPoint + EMITTER_PATH_AND_FS + q;
        Response response = WebClient.create(getUrl).accept("application/json").get();
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
    }

    @Test
    public void testGetXML() throws Exception {

        String q = "?fn=fsf&fk=hello_world.xml&type=xml";
        String getUrl = endPoint + EMITTER_PATH_AND_FS + q;
        Response response = WebClient.create(getUrl).accept("application/json").get();
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        String xml = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertContains("<p>hello world</p>", xml);
    }

    @Test
    public void testPost() throws Exception {

        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (int i = 0; i < VALUE_ARRAY.length; i++) {
            userMetadata.add("my-key-multi", VALUE_ARRAY[i]);
        }

        FetchEmitTuple t =
                new FetchEmitTuple(new FetchKey("fsf", "hello_world.xml"), new EmitKey("fse", ""),
                        userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + EMITTER_PATH;
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
        for (int i = 0; i < VALUE_ARRAY.length; i++) {
            userMetadata.add("my-key-multi", VALUE_ARRAY[i]);
        }

        FetchEmitTuple t =
                new FetchEmitTuple(new FetchKey("fsf", "hello_world.xml"), new EmitKey("fse", ""),
                        userMetadata,
                        new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1, -1),
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + EMITTER_PATH;
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
    public void testPut() throws Exception {

        String getUrl = endPoint + EMITTER_PATH_AND_FS + "/text";
        String metaPathKey = EmitterResource.EMIT_KEY_FOR_HTTP_HEADER;

        Response response = WebClient.create(getUrl).accept("application/json")
                .header(metaPathKey, "hello_world.xml")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/mock/hello_world.xml"));
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
    }

    @Test
    public void testPutXML() throws Exception {

        String putUrl = endPoint + EMITTER_PATH_AND_FS + "/xml";
        String metaPathKey = EmitterResource.EMIT_KEY_FOR_HTTP_HEADER;

        Response response = WebClient.create(putUrl).accept("application/json")
                .header(metaPathKey, "hello_world.xml")
                .put(ClassLoader.getSystemResourceAsStream("test-documents/mock/hello_world.xml"));
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertContains("<p>hello world</p>", metadata.get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testPostNPE() throws Exception {
        Metadata userMetadata = new Metadata();
        userMetadata.set("my-key", "my-value");
        for (int i = 0; i < VALUE_ARRAY.length; i++) {
            userMetadata.add("my-key-multi", VALUE_ARRAY[i]);
        }

        FetchEmitTuple t =
                new FetchEmitTuple(new FetchKey("fsf", "null_pointer.xml"), new EmitKey("fse", ""),
                        userMetadata);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + EMITTER_PATH;
        Response response =
                WebClient.create(getUrl).accept("application/json").post(writer.toString());
        assertEquals(200, response.getStatus());

        JsonNode jsonResponse;
        try (Reader reader = new InputStreamReader((InputStream) response.getEntity(),
                StandardCharsets.UTF_8)) {
            jsonResponse = new ObjectMapper().readTree(reader);
        }
        ;
        String parseException = jsonResponse.get("parse_exception").asText();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);

        List<Metadata> metadataList = null;
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

    //can't test system_exit here because server is in same process
}
