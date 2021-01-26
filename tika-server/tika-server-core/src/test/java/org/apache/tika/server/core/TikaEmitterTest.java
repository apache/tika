/**
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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.server.core.resource.EmitterResource;
import org.apache.tika.server.core.writer.JSONObjWriter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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
    private static String HELLO_WORLD = "hello_world.xml";
    private static String HELLO_WORLD_JSON = "hello_world.xml.json";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-emitter-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        TMP_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve(HELLO_WORLD_JSON);
        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);

        for (String mockFile : new String[]{
                "hello_world.xml", "null_pointer.xml"}) {
            Files.copy(TikaEmitterTest.class.getResourceAsStream(
                    "/test-documents/mock/"+mockFile),
                    inputDir.resolve(mockFile));
        }

        TIKA_CONFIG_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<properties>"+
                    "<fetchers>"+
                        "<fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">"+
                            "<params>"+
                                "<param name=\"name\" type=\"string\">fsf</param>"+
                                "<param name=\"basePath\" type=\"string\">"+inputDir.toAbsolutePath()+"</param>"+
                            "</params>"+
                        "</fetcher>"+
                    "</fetchers>"+
                    "<emitters>"+
                        "<emitter class=\"org.apache.tika.emitter.fs.FileSystemEmitter\">"+
                            "<params>"+
                                "<param name=\"name\" type=\"string\">fse</param>"+
                                "<param name=\"basePath\" type=\"string\">"+ TMP_OUTPUT_DIR.toAbsolutePath()+"</param>"+
                            "</params>"+
                        "</emitter>"+
                    "</emitters>"+
                "</properties>";
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
        rCoreProviders.add(new SingletonResourceProvider(new EmitterResource()));
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return new ByteArrayInputStream(TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected InputStreamFactory getInputStreamFactory(TikaConfig tikaConfig) {
        return new FetcherStreamFactory(tikaConfig.getFetcherManager());
    }

    @Test
    public void testGet() throws Exception {

        String q = "?fn=fsf&fk=hello_world.xml";
        String getUrl = endPoint+EMITTER_PATH_AND_FS+q;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json").get();
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("hello world",
                metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim());
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("你好，世界", metadata.get("title"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testPost() throws Exception {

        JsonObject root = new JsonObject();
        root.add("fetcherName", new JsonPrimitive("fsf"));
        root.add("fetchKey", new JsonPrimitive("hello_world.xml"));
        root.add("emitter", new JsonPrimitive("fse"));
        JsonObject userMetadata = new JsonObject();
        String[] valueArray = new String[] {"my-value-1", "my-value-2", "my-value-3"};
        JsonArray arr = new JsonArray();
        for (int i = 0; i < valueArray.length; i++) {
            arr.add(valueArray[i]);
        }

        userMetadata.add("my-key", new JsonPrimitive("my-value"));
        userMetadata.add("my-key-multi", arr);
        root.add("metadata", userMetadata);
        String jsonPost = new Gson().toJson(root);

        String getUrl = endPoint+EMITTER_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(jsonPost);
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("hello world",
                metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim());
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("你好，世界", metadata.get("title"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("my-value", metadata.get("my-key"));
        assertArrayEquals(valueArray, metadata.getValues("my-key-multi"));
    }

    @Test
    public void testPut() throws Exception {

        String getUrl = endPoint+EMITTER_PATH_AND_FS;
        String metaPathKey = EmitterResource.PATH_KEY_FOR_HTTP_HEADER;

        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .header(metaPathKey, "hello_world.xml")
                .put(
                        ClassLoader
                                .getSystemResourceAsStream("test-documents/mock/hello_world.xml")
                );
        assertEquals(200, response.getStatus());
        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("hello world",
                metadata.get(AbstractRecursiveParserWrapperHandler.TIKA_CONTENT).trim());
        assertEquals("Nikolai Lobachevsky", metadata.get("author"));
        assertEquals("你好，世界", metadata.get("title"));
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
    }

    @Test
    public void testPostNPE() throws Exception {

        JsonObject root = new JsonObject();
        root.add("fetcherName", new JsonPrimitive("fsf"));
        root.add("fetchKey", new JsonPrimitive("null_pointer.xml"));
        root.add("emitter", new JsonPrimitive("fse"));
        JsonObject userMetadata = new JsonObject();
        String[] valueArray = new String[] {"my-value-1", "my-value-2", "my-value-3"};
        JsonArray arr = new JsonArray();
        for (int i = 0; i < valueArray.length; i++) {
            arr.add(valueArray[i]);
        }

        userMetadata.add("my-key", new JsonPrimitive("my-value"));
        userMetadata.add("my-key-multi", arr);
        root.add("metadata", userMetadata);
        String jsonPost = new Gson().toJson(root);

        String getUrl = endPoint+EMITTER_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(jsonPost);
        assertEquals(200, response.getStatus());

        JsonObject jsonResponse;
        try (Reader reader = new InputStreamReader(
                (InputStream)response.getEntity(), StandardCharsets.UTF_8)) {
            jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();
        };
        String parseException = jsonResponse.get("parse_exception").getAsString();
        assertNotNull(parseException);
        assertContains("NullPointerException", parseException);

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_DIR.resolve("null_pointer.xml.json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        assertEquals("application/mock+xml", metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("my-value", metadata.get("my-key"));
        assertArrayEquals(valueArray, metadata.getValues("my-key-multi"));
        assertContains("NullPointerException", metadata.get(AbstractRecursiveParserWrapperHandler.CONTAINER_EXCEPTION));
    }

    //can't test system_exit here because server is in same process
}
