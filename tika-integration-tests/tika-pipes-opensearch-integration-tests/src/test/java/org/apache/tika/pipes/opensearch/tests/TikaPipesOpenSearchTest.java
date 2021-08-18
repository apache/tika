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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.opensearch.JsonResponse;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitter;

public class TikaPipesOpenSearchTest {

    private static final String TEST_INDEX = "tika-pipes-index";
    private static final File TEST_FILE_FOLDER = new File("target", "test-files");
    private int numTestDocs = 0;
    protected GenericContainer<?> openSearch;
    private String openSearchHost;
    private int openSearchPort;
    //this includes the collection, e.g. https://localhost:49213/testcol
    private String openSearchEndpointBase;
    private OpenSearchTestClient client;

    @Rule
    public GenericContainer<?> openSearchContainer =
            new GenericContainer<>(DockerImageName.parse(getOpenSearchImageName()))
                    .withExposedPorts(9200)
                    .withStartupTimeout(Duration.of(180, ChronoUnit.SECONDS))
                    .withEnv("discovery.type", "single-node");

    public String getOpenSearchImageName() {
        return "opensearchproject/opensearch:1.0.0";
    }

    public String getProtocol() {
        return "https://";
    }

    @Before
    public void setupTest() throws Exception {
        setupOpenSearch(openSearchContainer);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(TEST_FILE_FOLDER);
        openSearchContainer.close();
    }

    @Test
    public void testBasicFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);

        String endpoint = openSearchEndpointBase + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                HandlerConfig.PARSE_MODE.CONCATENATE, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ \"track_total_hits\": true, \"query\": { \"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value").asInt());
    }

    @Test
    public void testParentChildFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);
        String endpoint = openSearchEndpointBase + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-parent-child-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD,
                HandlerConfig.PARSE_MODE.RMETA, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
              results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 12, //the .docx file has 11 embedded files, plus itself
                results.getJson().get("hits").get("total").get("value").asInt());

        //now check out one of the embedded files
        query = "{ \"track_total_hits\": true, \"query\": { \"query_string\": { " +
                "\"default_field\": \"content\",  " +
                "\"query\": \"embed4 zip\" , \"minimum_should_match\":2 } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value").asInt());
        JsonNode source = results.getJson().get("hits").get("hits").get(0).get("_source");

        Matcher m = Pattern.compile("\\Atest_recursive_embedded" +
                ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z").matcher(
                results.getJson().get("hits").get("hits").get(0).get("_id").asText()
        );
        assertTrue("test_recursive_embedded.docx_$guid", m.find());
        assertEquals("test_recursive_embedded.docx",
                results.getJson().get("hits").get("hits").get(0).get("_routing").asText());
        assertEquals("test_recursive_embedded.docx",
                source.get("relation_type").get("parent").asText());
        assertEquals("embedded",
                source.get("relation_type").get("name").asText());

        assertEquals("application/zip", source.get("mime").asText());

        //now make sure all the children are returned by a parent search
        query = "{ \"track_total_hits\": true, \"query\": { \"parent_id\": { " +
                "\"type\": \"embedded\",  " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(11,
                results.getJson().get("hits").get("total").get("value").asInt());
    }


    @Test
    public void testSeparateDocsFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);
        String endpoint = openSearchEndpointBase + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                HandlerConfig.PARSE_MODE.RMETA, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 12, //the .docx file has 11 embedded files, plus itself
                results.getJson().get("hits").get("total").get("value").asInt());

        //now check out one of the embedded files
        query = "{ \"track_total_hits\": true, \"query\": { \"query_string\": { " +
                "\"default_field\": \"content\",  " +
                "\"query\": \"embed4 zip\" , \"minimum_should_match\":2 } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value").asInt());
        JsonNode source = results.getJson().get("hits").get("hits").get(0).get("_source");

        Matcher m = Pattern.compile("\\Atest_recursive_embedded" +
                ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z").matcher(
                results.getJson().get("hits").get("hits").get(0).get("_id").asText()
        );
        assertTrue("test_recursive_embedded.docx-$guid", m.find());

        assertNull("test_recursive_embedded.docx",
                results.getJson().get("hits").get("hits").get(0).get("_routing"));
        assertNull("test_recursive_embedded.docx",
                source.get("relation_type"));

        assertEquals("application/zip", source.get("mime").asText());

        //now make sure there are no children; this query should
        //cause an exception because there are no relationships in the schema
        query = "{ \"track_total_hits\": true, \"query\": { \"parent_id\": { " +
                "\"type\": \"embedded\",  " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }


    private void sendMappings(String endpoint, String index, String mappingsFile) throws Exception {
        //create the collection with mappings
        String mappings = IOUtils.toString(TikaPipesOpenSearchTest.class.getResourceAsStream(
                "/opensearch/" + mappingsFile), StandardCharsets.UTF_8);
        int status = -1;
        int tries = 0;
        JsonResponse response = null;
        //need to wait a bit sometimes before OpenSearch is up
        while (status != 200 && tries++ < 20) {
            response = client.putJson(endpoint, mappings);
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
        assertEquals(index, response.getJson().get("index").asText());

    }

    private void runPipes(OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                          HandlerConfig.PARSE_MODE parseMode, String endpoint) throws Exception {

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
                        attachmentStrategy, parseMode, endpoint);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.getAbsolutePath()});

        //refresh to make sure the content is searchable
        JsonResponse refresh = client.getJson(endpoint + "/_refresh");

    }

    @NotNull
    private String createTikaConfigXml(File tikaConfigFile, File log4jPropFile, String tikaConfigTemplateXml,
                                       OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                       HandlerConfig.PARSE_MODE parseMode, String endpoint) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                        .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                        .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                        .replaceAll("\\{PATH_TO_DOCS\\}", 
                                Matcher.quoteReplacement(TEST_FILE_FOLDER.getAbsolutePath()))
                        .replace("{PARSE_MODE}", parseMode.name());

        res = res.replace("{OPENSEARCH_CONNECTION}", endpoint);

        return res;

    }

    private void setupOpenSearch(GenericContainer<?> openSearchContainer) throws Exception {
        this.openSearch = openSearchContainer;
        openSearchHost = openSearch.getHost();
        openSearchPort = openSearch.getMappedPort(9200);
        openSearchEndpointBase = getProtocol() + openSearchHost + ":" + openSearchPort + "/";
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        httpClientFactory.setUserName("admin");
        httpClientFactory.setPassword("admin");
        //attachment strategy is not used here...TODO clean this up
        client = new OpenSearchTestClient(openSearchEndpointBase,
                httpClientFactory.build(),
                OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME);
    }

    private void createTestHtmlFiles(String bodyContent, int numHtmlDocs) throws Exception {
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
