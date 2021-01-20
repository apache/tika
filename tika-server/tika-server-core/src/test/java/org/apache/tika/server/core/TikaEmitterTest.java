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

import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
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
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
public class TikaEmitterTest extends CXFTestBase {

    private static final String EMITTER_PATH = "/emit/fs";
    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static Path TMP_OUTPUT_FILE;
    private static String TIKA_CONFIG_XML;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-emitter-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        TMP_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve("hello_world.xml.json");
        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);
        Files.copy(TikaEmitterTest.class.getResourceAsStream("/test-documents/mock/hello_world.xml"),
                inputDir.resolve("hello_world.xml"));
        TIKA_CONFIG_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
                "<properties>"+
                    "<fetchers>"+
                        "<fetcher class=\"org.apache.tika.fetcher.FileSystemFetcher\">"+
                            "<params>"+
                                "<param name=\"basePath\" type=\"string\">"+inputDir.toAbsolutePath()+"</param>"+
                            "</params>"+
                        "</fetcher>"+
                    "</fetchers>"+
                    "<emitters>"+
                        "<emitter class=\"org.apache.tika.emitter.fs.FileSystemEmitter\">"+
                            "<params>"+
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
        return new FetcherStreamFactory(tikaConfig.getFetcher());
    }

    @Test
    public void testGet() throws Exception {
        String q = "?fetchString="+ URLEncoder.encode("fs:hello_world.xml", StandardCharsets.UTF_8.name());
        String getUrl = endPoint+EMITTER_PATH+q;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json").get();
        assertEquals(200, response.getStatus());
        Path targetFile = TMP_OUTPUT_DIR.resolve("hello_world.xml.json");
        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(targetFile)) {
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

    //TODO: add put and post

}
