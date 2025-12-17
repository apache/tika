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

import static org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME;
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
import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.Emitter;
import org.apache.tika.pipes.core.emitter.EmitterManager;
import org.apache.tika.pipes.emitter.opensearch.HttpClientConfig;
import org.apache.tika.pipes.emitter.opensearch.JsonResponse;
import org.apache.tika.pipes.emitter.opensearch.OpenSearchEmitterConfig;
import org.apache.tika.plugins.TikaPluginManager;

@Testcontainers(disabledWithoutDocker = true)
public class OpenSearchTest {
    private static DockerImageName OPENSEARCH_IMAGE = DockerImageName.parse("opensearchproject/opensearch:2.19.3");
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
    public void testPluginsConfig(@TempDir Path pipesDirectory) throws Exception {
        Path pluginsConfg = getPluginsConfig(
                pipesDirectory, OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE,
                ParseMode.RMETA, "https://opensearch", Paths.get("testDocs"));
        //      PipesReporter reporter = ReporterManager.load(pluginsConfg);
//        System.out.println(reporter);
//        PipesIterator pipesIterator = PipesIteratorManager.load(pluginsConfg);
    }

    @Test
    public void testBasicFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {

        OpensearchTestClient client = getNewClient();
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);

        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-mappings.json");

        runPipes(client, OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitterConfig.UpdateStrategy.UPSERT, ParseMode.CONCATENATE, endpoint,
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

        assertEquals(numHtmlDocs, (int) statusCounts.get("PARSE_SUCCESS"), "should have had " + numHtmlDocs + " parse successes: " + statusCounts);
        //the npe is caught and counted as a "parse success with exception"
        assertEquals(1, (int) statusCounts.get("PARSE_SUCCESS_WITH_EXCEPTION"), "should have had 1 parse exception: " + statusCounts);
        //the embedded docx is emitted directly
        assertEquals(1, (int) statusCounts.get("EMIT_SUCCESS"), "should have had 1 emit success: " + statusCounts);
        assertEquals(2, numberOfCrashes(statusCounts), "should have had 2 OOM or 1 OOM and 1 timeout: " + statusCounts);

    }

    private int numberOfCrashes(Map<String, Integer> statusCounts) {
        Integer oom = statusCounts.get("OOM");
        Integer timeout = statusCounts.get("TIMEOUT");
        int sum = 0;
        if (oom != null) {
            sum += oom;
        }
        if (timeout != null) {
            sum += timeout;
        }
        return sum;
    }


    @Test
    public void testParentChildFSToOpenSearch(@TempDir Path pipesDirectory, @TempDir Path testDocDirectory) throws Exception {
        int numHtmlDocs = 42;
        OpensearchTestClient client = getNewClient();

        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = CONTAINER.getHttpHostAddress() + "/" + TEST_INDEX;
        sendMappings(client, endpoint, TEST_INDEX, "opensearch-parent-child-mappings.json");

        runPipes(client, OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE,
                ParseMode.RMETA, endpoint, pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"from\":0, \"size\": 10000, \"query\": { \"match\": { \"content\": { " +
                "\"query\": \"happiness\" } } } }";


        JsonResponse results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        //assertEquals(numHtmlDocs + 1, results.getJson().get("hits").get("total").get("value").asInt());

        //now try match all
        query = "{ " +
                "\"from\":0, \"size\":1000," +
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

        runPipes(client, OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE,
                ParseMode.RMETA, endpoint,
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

        runPipes(client, OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitterConfig.UpdateStrategy.UPSERT,
                ParseMode.RMETA, endpoint, pipesDirectory, testDocDirectory);

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
        Path pluginsConfigFile = getPluginsConfig(pipesDirectory, OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS,
                        OpenSearchEmitterConfig.UpdateStrategy.UPSERT, ParseMode.RMETA,
                        endpoint, testDocDirectory);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(pluginsConfigFile);
        Emitter emitter = EmitterManager
                .load(TikaPluginManager.load(tikaJsonConfig), tikaJsonConfig).getEmitter();
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
        OpenSearchEmitterConfig config = new OpenSearchEmitterConfig(CONTAINER.getHttpHostAddress(), "_id", OpenSearchEmitterConfig.AttachmentStrategy.SEPARATE_DOCUMENTS,
                OpenSearchEmitterConfig.UpdateStrategy.OVERWRITE, 10, DEFAULT_EMBEDDED_FILE_FIELD_NAME,
                new HttpClientConfig(null, null, null, -1, -1, null, -1));
        return new OpensearchTestClient(config, httpClientFactory.build());

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


    private void runPipes(OpensearchTestClient client, OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
                          OpenSearchEmitterConfig.UpdateStrategy updateStrategy,
                          ParseMode parseMode, String endpoint, Path pipesDirectory, Path testDocDirectory) throws Exception {

        Path pluginsConfig = getPluginsConfig(pipesDirectory, attachmentStrategy, updateStrategy, parseMode,
                endpoint, testDocDirectory);

        TikaCLI.main(new String[]{"-a", "-c", pluginsConfig.toAbsolutePath().toString() });

        //refresh to make sure the content is searchable
        JsonResponse refresh = client.getJson(endpoint + "/_refresh");

    }


    @NotNull
    private Path getPluginsConfig(Path pipesDirectory, OpenSearchEmitterConfig.AttachmentStrategy attachmentStrategy,
                                       OpenSearchEmitterConfig.UpdateStrategy updateStrategy,
                                       ParseMode parseMode, String endpoint, Path testDocDirectory) throws IOException {
        Path tikaConfig = pipesDirectory.resolve("plugins-config.json");

        Path log4jPropFile = pipesDirectory.resolve("log4j2.xml");
        try (InputStream is = OpenSearchTest.class
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            Files.copy(is, log4jPropFile);
        }

        boolean includeRouting = (attachmentStrategy == OpenSearchEmitterConfig.AttachmentStrategy.PARENT_CHILD);

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("ATTACHMENT_STRATEGY", attachmentStrategy.toString());
        replacements.put("UPDATE_STRATEGY", updateStrategy.toString());
        replacements.put("USER_NAME", CONTAINER.getUsername());
        replacements.put("PASSWORD", CONTAINER.getPassword());
        replacements.put("FETCHER_BASE_PATH", testDocDirectory);
        replacements.put("PARSE_MODE", parseMode.name());
        replacements.put("INCLUDE_ROUTING", includeRouting);
        replacements.put("OPEN_SEARCH_URL", endpoint);
        replacements.put("LOG4J_JVM_ARG", "-Dlog4j.configurationFile=" + log4jPropFile.toAbsolutePath());

        JsonConfigHelper.writeConfigFromResource("/opensearch/plugins-template.json",
                OpenSearchTest.class, replacements, tikaConfig);

        return tikaConfig;
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
