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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.client.HttpClientFactory;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.opensearch.JsonResponse;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitter;

@Testcontainers(disabledWithoutDocker = true)
public class OpenSearchTest {
    private static DockerImageName OPENSEARCH_IMAGE = DockerImageName.parse("opensearchproject/opensearch:2.17.1");
    private static OpensearchContainer<?> CONTAINER;

    protected static final String TEST_INDEX = "tika-pipes-index";
    private int numTestDocs = 0;


    @BeforeAll
    public static void setUp() {
        CONTAINER = new OpensearchContainer<>(OPENSEARCH_IMAGE).withSecurityEnabled();
        CONTAINER.start();
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        httpClientFactory.setUserName(CONTAINER.getUsername());
        httpClientFactory.setPassword(CONTAINER.getPassword());
    }

    @AfterAll
    public static void tearDown() {
        CONTAINER.close();
    }

    @AfterEach
    public void clearIndex() throws TikaConfigException, IOException {
        OpensearchTestClient client = getNewClient();
        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        client.deleteIndex(endpoint);
    }

    @Test
    public void testBasicFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {

        OpensearchTestClient client = getNewClient();
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);

        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(client, OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.UPSERT, HandlerConfig.PARSE_MODE.CONCATENATE, endpoint,
                pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ \"track_total_hits\": true, \"query\": { \"match_all\": {} }, " +
                "\"from\": 0, \"size\": 1000 }";
        results = client.postJson(endpoint + "/_search", query);
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
    public void testParentChildFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {
        int numHtmlDocs = 42;
        OpensearchTestClient client = getNewClient();

        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-parent-child-mappings.json");

        runPipes(client, OpenSearchEmitter.AttachmentStrategy.PARENT_CHILD,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE,
                HandlerConfig.PARSE_MODE.RMETA, endpoint, pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1, results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, // 3 mock files and...
                // the .docx file has 11 embedded files, plus itself
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

        Matcher m = Pattern
                .compile("\\Atest_recursive_embedded" +
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
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(11,
                results.getJson().get("hits").get("total").get("value").asInt());
    }


    @Test
    public void testSeparateDocsFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {
        OpensearchTestClient client = getNewClient();

        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(client, OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE,
                HandlerConfig.PARSE_MODE.RMETA, endpoint,
                pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, //3 for the mock docs,
                // and the .docx file has 11 embedded files, plus itself
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
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }

    @Test
    public void testUpsertSeparateDocsFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {
        OpensearchTestClient client = getNewClient();

        //now test that this works with upsert
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(client, OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.UPSERT,
                HandlerConfig.PARSE_MODE.RMETA, endpoint, pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";

        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12, //3 for the mock docs,
                // and the .docx file has 11 embedded files, plus itself
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
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }

    @Test
    public void testUpsert(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {
        OpensearchTestClient client = getNewClient();

        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-mappings.json");
        Path tikaConfigFile =
                getTikaConfigFile(OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                        OpenSearchEmitter.UpdateStrategy.UPSERT, HandlerConfig.PARSE_MODE.RMETA,
                        endpoint, pipesDirectory, testDocDirectory);
        Emitter emitter = EmitterManager
                .load(tikaConfigFile).getEmitter();
        Metadata metadata = new Metadata();
        metadata.set("mime", "mimeA");
        metadata.set("title", "titleA");
        emitter.emit("1", Collections.singletonList(metadata), new ParseContext());
        JsonResponse refresh = client.getJson(endpoint + "/_refresh");
        metadata.set("title", "titleB");
        emitter.emit("1", Collections.singletonList(metadata), new ParseContext());
        refresh = client.getJson(endpoint + "/_refresh");

        Metadata metadata2 = new Metadata();
        metadata2.set("content", "the quick brown fox");
        emitter.emit("1", Collections.singletonList(metadata2), new ParseContext());
        refresh = client.getJson(endpoint + "/_refresh");

        String query = "{ " +
                //"\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        JsonResponse response = client.postJson(endpoint + "/_search", query);
        JsonNode doc1 = response.getJson().get("hits").get("hits").get(0).get(
                "_source");
        assertEquals("mimeA", doc1.get("mime").asText());
        assertEquals("titleB", doc1.get("title").asText());
        assertEquals("the quick brown fox", doc1.get("content").asText());
    }



    private OpensearchTestClient getNewClient() throws TikaConfigException {
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        httpClientFactory.setUserName(CONTAINER.getUsername());
        httpClientFactory.setPassword(CONTAINER.getPassword());

        return new OpensearchTestClient(CONTAINER.getHttpHostAddress(), httpClientFactory.build(), OpenSearchEmitter.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitter.UpdateStrategy.OVERWRITE, OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME);

    }

    protected void sendMappings(OpensearchTestClient client, String endpoint, String index, String mappingsFile) throws Exception {
        //create the collection with mappings
        String mappings = IOUtils.toString(OpenSearchTest.class.getResourceAsStream(
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


    private void runPipes(OpensearchTestClient client, OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                          OpenSearchEmitter.UpdateStrategy updateStrategy,
                          HandlerConfig.PARSE_MODE parseMode, String endpoint, Path pipesDirectory, Path testDocDirectory) throws Exception {

        Path tikaConfigFile = getTikaConfigFile(attachmentStrategy, updateStrategy, parseMode,
                endpoint, pipesDirectory, testDocDirectory);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.toAbsolutePath().toString()});

        //refresh to make sure the content is searchable
        JsonResponse refresh = client.getJson(endpoint + "/_refresh");

    }

    private Path getTikaConfigFile(OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                   OpenSearchEmitter.UpdateStrategy updateStrategy,
                                   HandlerConfig.PARSE_MODE parseMode, String endpoint,
                                   Path pipesDirectory, Path testDocDirectory) throws IOException {
        Path tikaConfigFile = pipesDirectory.resolve("ta-opensearch.xml");
        Path log4jPropFile = pipesDirectory.resolve("tmp-log4j2.xml");
        try (InputStream is = OpenSearchTest.class
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            Files.copy(is, log4jPropFile);
        }

        String tikaConfigTemplateXml;
        try (InputStream is = OpenSearchTest.class
                .getResourceAsStream("/opensearch/tika-config-opensearch.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String tikaConfigXml =
                createTikaConfigXml(tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                        attachmentStrategy, updateStrategy, parseMode, endpoint, testDocDirectory);
        writeStringToPath(tikaConfigFile, tikaConfigXml);

        return tikaConfigFile;
    }

    @NotNull
    private String createTikaConfigXml(Path tikaConfigFile, Path log4jPropFile,
                                       String tikaConfigTemplateXml,
                                       OpenSearchEmitter.AttachmentStrategy attachmentStrategy,
                                       OpenSearchEmitter.UpdateStrategy updateStrategy,
                                       HandlerConfig.PARSE_MODE parseMode, String endpoint, Path testDocDirectory) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.toAbsolutePath().toString())
                                     .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                                     .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.toAbsolutePath().toString())
                                     .replace("{UPDATE_STRATEGY}", updateStrategy.toString())
                        .replaceAll("\\{OPENSEARCH_USERNAME\\}", CONTAINER.getUsername())
                                     .replaceAll("\\{OPENSEARCH_PASSWORD\\}", CONTAINER.getPassword())
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


    private void createTestHtmlFiles(String bodyContent, int numHtmlDocs, Path testDocDirectory) throws Exception {
        Files.createDirectories(testDocDirectory);
        for (int i = 0; i < numHtmlDocs; ++i) {
            String html = "<html><body>" + bodyContent +  "</body></html>";
            Path p = testDocDirectory.resolve( "test-" + i + ".html");
            writeStringToPath(p, html);
        }
        File testDocuments =
                Paths
                        .get(OpenSearchTest.class.getResource("/test-documents").toURI()).toFile();
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
