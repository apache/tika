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
package org.apache.tika.pipes.opensearch.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;

import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.pipes.emitter.opensearch.JsonResponse;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitter;

public class TikaPipesOpenSearchTest {

    private static final String COLLECTION = "testcol";
    private static final File TEST_FILE_FOLDER = new File("target", "test-files");
    private final int numHtmlDocs = 42;
    private int numTestDocs = 0;
    protected GenericContainer<?> openSearch;
    private String openSearchHost;
    private int openSearchPort;
    //this includes the collection, e.g. https://localhost:49213/testcol
    private String openSearchEndpoint;
    private OpenSearchTestClient client;

    @Rule
    public GenericContainer<?> openSearchContainer =
            new GenericContainer<>(DockerImageName.parse(getOpenSearchImageName()))
                    .withExposedPorts(9200)
                    .withEnv("discovery.type", "single-node");

    private String getOpenSearchImageName() {
        return "opensearchproject/opensearch:1.0.0";
    }

    @Before
    public void setupTest() throws Exception {
        setupOpenSearch(openSearchContainer);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        FileUtils.deleteDirectory(TEST_FILE_FOLDER);
    }

    @Test
    public void testFSToOpenSearch() throws Exception {
        //create the collection with mappings
        String mappings = IOUtils.toString(TikaPipesOpenSearchTest.class.getResourceAsStream(
                "/opensearch/opensearch-mappings.json"), StandardCharsets.UTF_8);
        int status = -1;
        int tries = 0;
        JsonResponse response = null;
        //need to wait a bit sometimes before OpenSearch is up
        while (status != 200 && tries++ < 20) {
            response = client.putJson(openSearchEndpoint, mappings);
            if (status != 200) {
                Thread.sleep(1000);
            }
            status = response.getStatus();
        }
        if (status != 200) {
            throw new IllegalArgumentException("couldn't create index/add mappings: " +
                    response);
        }
        assertTrue(response.getJson().get("acknowledged").asBoolean());
        assertEquals("testcol", response.getJson().get("index").asText());

        runPipes(OpenSearchEmitter.AttachmentStrategy.SKIP);
        //refresh to make sure the content is searchable
        JsonResponse refresh = client.getJson(openSearchEndpoint + "/_refresh");

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(openSearchEndpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        //assertEquals(numHtmlDocs + numTestDocs,
          //      results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ \"track_total_hits\": true, \"query\": { \"match_all\": {} } }";
        results = client.postJson(openSearchEndpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value").asInt());


    }

    private void runPipes(OpenSearchEmitter.AttachmentStrategy attachmentStrategy) throws Exception {

        File tikaConfigFile = new File("target", "ta-opensearch.xml");
        File log4jPropFile = new File("target", "tmp-log4j2.xml");
        try (InputStream is = TikaPipesOpenSearchTest.class
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            FileUtils.copyInputStreamToFile(is, log4jPropFile);
        }
        String tikaConfigTemplateXml;
        try (InputStream is = TikaPipesOpenSearchTest.class
                .getResourceAsStream("/opensearch/tika-config-opensearch.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String tikaConfigXml =
                createTikaConfigXml(tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                        attachmentStrategy);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.getAbsolutePath()});


    }

    @NotNull
    private String createTikaConfigXml(File tikaConfigFile, File log4jPropFile, String tikaConfigTemplateXml,
                                       OpenSearchEmitter.AttachmentStrategy attachmentStrategy) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                        .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                        .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                        .replaceAll("\\{PATH_TO_DOCS\\}", 
                                Matcher.quoteReplacement(TEST_FILE_FOLDER.getAbsolutePath()));

        res = res.replace("{OPENSEARCH_CONNECTION}", openSearchEndpoint);

        return res;

    }

    private void setupOpenSearch(GenericContainer<?> openSearchContainer) throws Exception {
        createTestHtmlFiles("Happiness");
        this.openSearch = openSearchContainer;
        openSearchHost = openSearch.getHost();
        openSearchPort = openSearch.getMappedPort(9200);
        openSearchEndpoint = "https://" + openSearchHost + ":" + openSearchPort + "/" + COLLECTION;
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        httpClientFactory.setUserName("admin");
        httpClientFactory.setPassword("admin");
        //attachment strategy is not used here...TODO clean this up
        client = new OpenSearchTestClient(openSearchEndpoint,
                httpClientFactory.build(), OpenSearchEmitter.AttachmentStrategy.SKIP);
    }

    private void createTestHtmlFiles(String bodyContent) throws Exception {
        TEST_FILE_FOLDER.mkdirs();
        for (int i = 0; i < numHtmlDocs; ++i) {
            FileUtils.writeStringToFile(new File(TEST_FILE_FOLDER, "test-" + i + ".html"),
                    "<html><body>" + bodyContent +  "</body></html>", StandardCharsets.UTF_8);
        }
        File testDocuments =
                Paths.get(TikaPipesOpenSearchTest.class.getResource("/test-documents").toURI()).toFile();
        for (File f : testDocuments.listFiles()) {
            Path targ = TEST_FILE_FOLDER.toPath().resolve(f.getName());
            Files.copy(f.toPath(), targ);
            numTestDocs++;
        }
    }


}
