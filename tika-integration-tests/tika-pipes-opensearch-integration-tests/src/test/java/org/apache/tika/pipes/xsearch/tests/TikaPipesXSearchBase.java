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
package org.apache.tika.pipes.xsearch.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.opensearch.JsonResponse;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitter;

public abstract class TikaPipesXSearchBase {

    //@TempDir -- we can't use this yet because of junit4+junit5
    private Path pipesDirectory;

    private Path testDocDirectory;

    protected static final String TEST_INDEX = "tika-pipes-index";
    private int numTestDocs = 0;
    private static String OPEN_SEARCH_HOST;
    private static int OPEN_SEARCH_PORT;
    //this includes only the base, not the collection, e.g. https://localhost:49213
    protected static String OPEN_SEARCH_ENDPOINT_BASE;
    private static XSearchTestClient CLIENT;


    @BeforeEach
    public void setUp() throws IOException {
        pipesDirectory = Files.createTempDirectory("tika-opensearch-integration-");
        testDocDirectory = pipesDirectory.resolve("docs");
    }
    @AfterEach
    public void tearDown() throws Exception {
        //we shouldn't have to do this because of @TempDir
        //there are some timing/order issues because of the joint junit 4 and 5
        FileUtils.deleteDirectory(pipesDirectory.toFile());
        JsonResponse response = CLIENT.deleteIndex(OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX);
        assertEquals(200, response.getStatus());
        assertTrue(response.getJson().get("acknowledged").asBoolean());
    }

    @Test
    public void testBasicFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);

        String endpoint = OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.UPSERT,
                HandlerConfig.PARSE_MODE.CONCATENATE, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ \"track_total_hits\": true, \"query\": { \"match_all\": {} }, " +
                "\"from\": 0, \"size\": 1000 }";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now test that the reporter worked
        Map<String, Integer> statusCounts = new HashMap<>();
        for (JsonNode n : results.getJson().get("hits").get("hits")) {
            String status = n.get("_source").get("my_test_parse_status").asText();
            //this will throw an NPE if the field isn't there
            //in short, this guarantees that the value is there
            long parseTimeMs = n.get("_source").get("my_test_parse_time_ms").asLong();
            Integer cnt = statusCounts.get(status);
            if (cnt == null) {
                cnt = 1;
            } else {
                cnt++;
            }
            statusCounts.put(status, cnt);
        }
        assertEquals(numHtmlDocs, (int) statusCounts.get("PARSE_SUCCESS"));
        //the npe is caught and counted as a "parse success with exception"
        assertEquals(1, (int) statusCounts.get("PARSE_SUCCESS_WITH_EXCEPTION"));
        //the embedded docx is emitted directly
        assertEquals(1, (int) statusCounts.get("EMIT_SUCCESS"));
        assertEquals(2, (int) statusCounts.get("OOM"));
    }

    @Test
    public void testParentChildFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);
        String endpoint = OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-parent-child-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE,
                HandlerConfig.PARSE_MODE.RMETA, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1, results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, // 3 mock files and...
                // the .docx file has 11 embedded files, plus itself
                results.getJson().get("hits").get("total").get("value").asInt());

        //now check out one of the embedded files
        query = "{ \"track_total_hits\": true, \"query\": { \"query_string\": { " +
                "\"default_field\": \"content\",  " +
                "\"query\": \"embed4 zip\" , \"minimum_should_match\":2 } } } ";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value").asInt());
        JsonNode source = results.getJson().get("hits").get("hits").get(0).get("_source");

        Matcher m = Pattern.compile("\\Atest_recursive_embedded" +
                ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z").matcher(
                results.getJson().get("hits").get("hits").get(0).get("_id").asText()
        );
        assertTrue(m.find(), "test_recursive_embedded.docx_$guid");
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
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(11,
                results.getJson().get("hits").get("total").get("value").asInt());
    }


    @Test
    public void testSeparateDocsFSToOpenSearch() throws Exception {
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);
        String endpoint = OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE,
                HandlerConfig.PARSE_MODE.RMETA, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, //3 for the mock docs,
                // and the .docx file has 11 embedded files, plus itself
                results.getJson().get("hits").get("total").get("value").asInt());

        //now check out one of the embedded files
        query = "{ \"track_total_hits\": true, \"query\": { \"query_string\": { " +
                "\"default_field\": \"content\",  " +
                "\"query\": \"embed4 zip\" , \"minimum_should_match\":2 } } } ";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value").asInt());
        JsonNode source = results.getJson().get("hits").get("hits").get(0).get("_source");

        Matcher m = Pattern.compile("\\Atest_recursive_embedded" +
                ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z").matcher(
                results.getJson().get("hits").get("hits").get(0).get("_id").asText()
        );
        assertTrue(m.find(), "test_recursive_embedded.docx-$guid");

        assertNull(results.getJson().get("hits").get("hits").get(0).get("_routing"),
                "test_recursive_embedded.docx");
        assertNull(source.get("relation_type"), "test_recursive_embedded.docx");

        assertEquals("application/zip", source.get("mime").asText());

        //now make sure there are no children; this query should
        //cause an exception because there are no relationships in the schema
        query = "{ \"track_total_hits\": true, \"query\": { \"parent_id\": { " +
                "\"type\": \"embedded\",  " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }

    @Test
    public void testUpsertSeparateDocsFSToOpenSearch() throws Exception {
        //now test that this works with upsert
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs);
        String endpoint = OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.UPSERT,
                HandlerConfig.PARSE_MODE.RMETA, endpoint);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, //3 for the mock docs,
                // and the .docx file has 11 embedded files, plus itself
                results.getJson().get("hits").get("total").get("value").asInt());

        //now check out one of the embedded files
        query = "{ \"track_total_hits\": true, \"query\": { \"query_string\": { " +
                "\"default_field\": \"content\",  " +
                "\"query\": \"embed4 zip\" , \"minimum_should_match\":2 } } } ";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value").asInt());
        JsonNode source = results.getJson().get("hits").get("hits").get(0).get("_source");

        Matcher m = Pattern.compile("\\Atest_recursive_embedded" +
                ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z").matcher(
                results.getJson().get("hits").get("hits").get(0).get("_id").asText()
        );
        assertTrue(m.find(), "test_recursive_embedded.docx-$guid");

        assertNull(results.getJson().get("hits").get("hits").get(0).get("_routing"),
                "test_recursive_embedded.docx");
        assertNull(source.get("relation_type"), "test_recursive_embedded.docx");

        assertEquals("application/zip", source.get("mime").asText());

        //now make sure there are no children; this query should
        //cause an exception because there are no relationships in the schema
        query = "{ \"track_total_hits\": true, \"query\": { \"parent_id\": { " +
                "\"type\": \"embedded\",  " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = CLIENT.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }

    @Test
    public void testUpsert() throws Exception {
        String endpoint = OPEN_SEARCH_ENDPOINT_BASE + TEST_INDEX;
        sendMappings(endpoint, TEST_INDEX, "opensearch-mappings.json");
        Path tikaConfigFile =
                getTikaConfigFile(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                        OpenSearchEmitter.UpdateStrategy.UPSERT, HandlerConfig.PARSE_MODE.RMETA,
                        endpoint);
        Emitter emitter = EmitterManager.load(tikaConfigFile).getEmitter();
        Metadata metadata = new Metadata();
        metadata.set("mime", "mimeA");
        metadata.set("title", "titleA");
        emitter.emit("1", Collections.singletonList(metadata), ParseContext.EMPTY);
        JsonResponse refresh = CLIENT.getJson(endpoint + "/_refresh");
        metadata.set("title", "titleB");
        emitter.emit("1", Collections.singletonList(metadata), ParseContext.EMPTY);
        refresh = CLIENT.getJson(endpoint + "/_refresh");

        Metadata metadata2 = new Metadata();
        metadata2.set("content", "the quick brown fox");
        emitter.emit("1", Collections.singletonList(metadata2), ParseContext.EMPTY);
        refresh = CLIENT.getJson(endpoint + "/_refresh");

        String query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        JsonResponse response = CLIENT.postJson(endpoint + "/_search", query);
        JsonNode doc1 = response.getJson().get("hits").get("hits").get(0).get(
                "_source");
        assertEquals("mimeA", doc1.get("mime").asText());
        assertEquals("titleB", doc1.get("title").asText());
        assertEquals("the quick brown fox", doc1.get("content").asText());
    }

    protected void sendMappings(String endpoint, String index, String mappingsFile) throws Exception {
        //create the collection with mappings
        String mappings = IOUtils.toString(TikaPipesXSearchBase.class.getResourceAsStream(
                "/opensearch/" + mappingsFile), StandardCharsets.UTF_8);
        int status = -1;
        int tries = 0;
        JsonResponse response = null;
        //need to wait a bit sometimes before OpenSearch is up
        while (status != 200 && tries++ < 20) {
            response = CLIENT.putJson(endpoint, mappings);
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
                          OpenSearchEmitter.UpdateStrategy updateStrategy,
                          HandlerConfig.PARSE_MODE parseMode, String endpoint) throws Exception {

        Path tikaConfigFile = getTikaConfigFile(attachmentStrategy, updateStrategy, parseMode,
                endpoint);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.toAbsolutePath().toString()});

        //refresh to make sure the content is searchable
        JsonResponse refresh = CLIENT.getJson(endpoint + "/_refresh");

    }

    private Path getTikaConfigFile(OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                   OpenSearchEmitter.UpdateStrategy updateStrategy,
                                   HandlerConfig.PARSE_MODE parseMode, String endpoint) throws
            IOException {
        Path tikaConfigFile = pipesDirectory.resolve("ta-opensearch.xml");
        Path log4jPropFile = pipesDirectory.resolve("tmp-log4j2.xml");
        try (InputStream is = TikaPipesXSearchBase.class
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            Files.copy(is, log4jPropFile);
        }

        String tikaConfigTemplateXml;
        try (InputStream is = TikaPipesXSearchBase.class
                .getResourceAsStream("/opensearch/tika-config-opensearch.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String tikaConfigXml =
                createTikaConfigXml(tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                        attachmentStrategy, updateStrategy, parseMode, endpoint);
        writeStringToPath(tikaConfigFile, tikaConfigXml);

        return tikaConfigFile;
    }

    @NotNull
    private String createTikaConfigXml(Path tikaConfigFile, Path log4jPropFile,
                                       String tikaConfigTemplateXml,
                                       OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                       OpenSearchEmitter.UpdateStrategy updateStrategy,
                                       HandlerConfig.PARSE_MODE parseMode, String endpoint) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.toAbsolutePath().toString())
                        .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                        .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.toAbsolutePath().toString())
                        .replace("{UPDATE_STRATEGY}", updateStrategy.toString())
                        .replaceAll("\\{PATH_TO_DOCS\\}", 
                                Matcher.quoteReplacement(testDocDirectory.toAbsolutePath().toString()))
                        .replace("{PARSE_MODE}", parseMode.name());

        if (attachmentStrategy == OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD) {
            res = res.replace("{INCLUDE_ROUTING}", "true");
        } else {
            res = res.replace("{INCLUDE_ROUTING}", "false");
        }
        res = res.replace("{OPENSEARCH_CONNECTION}", endpoint);

        return res;

    }

    public void setupXSearch(GenericContainer<?> openSearchContainer, String protocol) throws Exception {
        OPEN_SEARCH_HOST = openSearchContainer.getHost();
        OPEN_SEARCH_PORT = openSearchContainer.getMappedPort(9200);
        OPEN_SEARCH_ENDPOINT_BASE = protocol + OPEN_SEARCH_HOST + ":" + OPEN_SEARCH_PORT + "/";
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        httpClientFactory.setUserName("admin");
        httpClientFactory.setPassword("admin");

        //attachment strategy is not used here...TODO clean this up
        CLIENT = new XSearchTestClient(OPEN_SEARCH_ENDPOINT_BASE,
                httpClientFactory.build(),
                OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE,
                OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME);
    }

    private void createTestHtmlFiles(String bodyContent, int numHtmlDocs) throws Exception {
        Files.createDirectories(testDocDirectory);
        for (int i = 0; i < numHtmlDocs; ++i) {
            String html = "<html><body>" + bodyContent +  "</body></html>";
            Path p = testDocDirectory.resolve( "test-" + i + ".html");
            writeStringToPath(p, html);
        }
        File testDocuments =
                Paths.get(TikaPipesXSearchBase.class.getResource("/test-documents").toURI()).toFile();
        for (File f : testDocuments.listFiles()) {
            Path targ = testDocDirectory.resolve(f.getName());
            Files.copy(f.toPath(), targ);
            numTestDocs++;
        }
    }

    private static void writeStringToPath(Path path, String string) throws IOException {
        Files.write(path, string.getBytes(StandardCharsets.UTF_8));
    }


}
