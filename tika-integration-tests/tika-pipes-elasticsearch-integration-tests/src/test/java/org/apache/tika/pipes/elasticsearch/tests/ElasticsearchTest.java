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
package org.apache.tika.pipes.elasticsearch.tests;

import static org.apache.tika.pipes.emitter.elasticsearch.ElasticsearchEmitter.DEFAULT_EMBEDDED_FILE_FIELD_NAME;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
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
import org.apache.tika.pipes.emitter.elasticsearch.ElasticsearchEmitterConfig;
import org.apache.tika.pipes.emitter.elasticsearch.HttpClientConfig;
import org.apache.tika.pipes.emitter.elasticsearch.JsonResponse;
import org.apache.tika.plugins.TikaPluginManager;

/**
 * Integration tests for the Elasticsearch emitter using a Dockerized
 * Elasticsearch instance via testcontainers.
 *
 * <p>Uses a {@link GenericContainer} with the official Elasticsearch Docker
 * image. Security is disabled for test simplicity — no ES Java client
 * dependency needed.
 */
@Testcontainers(disabledWithoutDocker = true)
public class ElasticsearchTest {

    private static final DockerImageName ES_IMAGE =
            DockerImageName.parse(
                    "docker.elastic.co/elasticsearch/elasticsearch:8.17.0");

    private static GenericContainer<?> CONTAINER;

    protected static final String TEST_INDEX = "tika-pipes-index";
    private int numTestDocs = 0;

    @BeforeAll
    public static void setUp() {
        CONTAINER = new GenericContainer<>(ES_IMAGE)
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("cluster.routing.allocation.disk.threshold_enabled",
                        "false")
                .waitingFor(new HttpWaitStrategy()
                        .forPort(9200)
                        .forStatusCode(200));

        CONTAINER.start();
    }

    @AfterAll
    public static void tearDown() {
        CONTAINER.close();
    }

    @AfterEach
    public void clearIndex() throws TikaConfigException, IOException {
        ElasticsearchTestClient client = getNewClient();
        String endpoint = getEndpoint();
        client.deleteIndex(endpoint);
    }

    @Test
    public void testBasicFSToElasticsearch(
            @TempDir Path pipesDirectory,
            @TempDir Path testDocDirectory) throws Exception {

        ElasticsearchTestClient client = getNewClient();
        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);

        String endpoint = getEndpoint();
        sendMappings(client, endpoint, TEST_INDEX,
                "elasticsearch-mappings.json");

        runPipes(client,
                ElasticsearchEmitterConfig.AttachmentStrategy
                        .SEPARATE_DOCUMENTS,
                ElasticsearchEmitterConfig.UpdateStrategy.UPSERT,
                ParseMode.CONCATENATE, endpoint,
                pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"match\": { \"content\": " +
                "{ \"query\": \"happiness\" } } } }";

        JsonResponse results =
                client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());

        // match all
        query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"match_all\": {} }, " +
                "\"from\": 0, \"size\": 1000 }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + numTestDocs,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());
    }

    @Test
    public void testParentChildFSToElasticsearch(
            @TempDir Path pipesDirectory,
            @TempDir Path testDocDirectory) throws Exception {

        int numHtmlDocs = 42;
        ElasticsearchTestClient client = getNewClient();

        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = getEndpoint();
        sendMappings(client, endpoint, TEST_INDEX,
                "elasticsearch-parent-child-mappings.json");

        runPipes(client,
                ElasticsearchEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                ElasticsearchEmitterConfig.UpdateStrategy.OVERWRITE,
                ParseMode.RMETA, endpoint,
                pipesDirectory, testDocDirectory);

        // match all
        String query = "{ " +
                "\"from\":0, \"size\":1000," +
                "\"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        JsonResponse results =
                client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12,
                // 3 mock files and the .docx has 11 embedded + itself
                results.getJson().get("hits").get("total").get("value")
                        .asInt());

        // check an embedded file
        query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"query_string\": { " +
                "\"default_field\": \"content\", " +
                "\"query\": \"embed4 zip\", " +
                "\"minimum_should_match\":2 } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());
        JsonNode source = results.getJson().get("hits").get("hits")
                .get(0).get("_source");

        Matcher m = Pattern
                .compile("\\Atest_recursive_embedded" +
                        ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                        "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z")
                .matcher(
                        results.getJson().get("hits").get("hits").get(0)
                                .get("_id").asText());
        assertTrue(m.find(), "test_recursive_embedded.docx_$guid");
        assertEquals("test_recursive_embedded.docx",
                results.getJson().get("hits").get("hits").get(0)
                        .get("_routing").asText());
        assertEquals("test_recursive_embedded.docx",
                source.get("relation_type").get("parent").asText());
        assertEquals("embedded",
                source.get("relation_type").get("name").asText());

        assertEquals("application/zip",
                source.get("mime").asText());

        // verify parent query returns all children
        query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"parent_id\": { " +
                "\"type\": \"embedded\", " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(11,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());
    }

    @Test
    public void testSeparateDocsFSToElasticsearch(
            @TempDir Path pipesDirectory,
            @TempDir Path testDocDirectory) throws Exception {

        ElasticsearchTestClient client = getNewClient();

        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = getEndpoint();
        sendMappings(client, endpoint, TEST_INDEX,
                "elasticsearch-mappings.json");

        runPipes(client,
                ElasticsearchEmitterConfig.AttachmentStrategy
                        .SEPARATE_DOCUMENTS,
                ElasticsearchEmitterConfig.UpdateStrategy.OVERWRITE,
                ParseMode.RMETA, endpoint,
                pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"match\": { \"content\": " +
                "{ \"query\": \"happiness\" } } } }";

        JsonResponse results =
                client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());

        // match all
        query = "{ \"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 3 + 12,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());

        // check an embedded file
        query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"query_string\": { " +
                "\"default_field\": \"content\", " +
                "\"query\": \"embed4 zip\", " +
                "\"minimum_should_match\":2 } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(1,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());
        JsonNode source = results.getJson().get("hits").get("hits")
                .get(0).get("_source");

        Matcher m = Pattern
                .compile("\\Atest_recursive_embedded" +
                        ".docx-[0-9a-f]{8}-[0-9a-f]{4}-" +
                        "[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\Z")
                .matcher(
                        results.getJson().get("hits").get("hits").get(0)
                                .get("_id").asText());
        assertTrue(m.find(), "test_recursive_embedded.docx-$guid");

        assertNull(
                results.getJson().get("hits").get("hits").get(0)
                        .get("_routing"),
                "test_recursive_embedded.docx");
        assertNull(source.get("relation_type"),
                "test_recursive_embedded.docx");

        assertEquals("application/zip",
                source.get("mime").asText());

        // parent_id query should fail — no join in schema
        query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"parent_id\": { " +
                "\"type\": \"embedded\", " +
                "\"id\": \"test_recursive_embedded.docx\" } } } ";
        results = client.postJson(endpoint + "/_search", query);
        assertEquals(400, results.getStatus());
    }

    @Test
    public void testUpsertSeparateDocsFSToElasticsearch(
            @TempDir Path pipesDirectory,
            @TempDir Path testDocDirectory) throws Exception {

        ElasticsearchTestClient client = getNewClient();

        int numHtmlDocs = 42;
        createTestHtmlFiles("Happiness", numHtmlDocs, testDocDirectory);
        String endpoint = getEndpoint();
        sendMappings(client, endpoint, TEST_INDEX,
                "elasticsearch-mappings.json");

        runPipes(client,
                ElasticsearchEmitterConfig.AttachmentStrategy
                        .SEPARATE_DOCUMENTS,
                ElasticsearchEmitterConfig.UpdateStrategy.UPSERT,
                ParseMode.RMETA, endpoint,
                pipesDirectory, testDocDirectory);

        String query = "{ \"track_total_hits\": true, \"query\": " +
                "{ \"match\": { \"content\": " +
                "{ \"query\": \"happiness\" } } } }";

        JsonResponse results =
                client.postJson(endpoint + "/_search", query);
        assertEquals(200, results.getStatus());
        assertEquals(numHtmlDocs + 1,
                results.getJson().get("hits").get("total").get("value")
                        .asInt());
    }

    @Test
    public void testUpsert(
            @TempDir Path pipesDirectory,
            @TempDir Path testDocDirectory) throws Exception {

        ElasticsearchTestClient client = getNewClient();

        String endpoint = getEndpoint();
        sendMappings(client, endpoint, TEST_INDEX,
                "elasticsearch-mappings.json");
        Path pluginsConfigFile = getPluginsConfig(pipesDirectory,
                ElasticsearchEmitterConfig.AttachmentStrategy
                        .SEPARATE_DOCUMENTS,
                ElasticsearchEmitterConfig.UpdateStrategy.UPSERT,
                ParseMode.RMETA, endpoint, testDocDirectory);

        TikaJsonConfig tikaJsonConfig =
                TikaJsonConfig.load(pluginsConfigFile);
        Emitter emitter = EmitterManager
                .load(TikaPluginManager.load(tikaJsonConfig),
                        tikaJsonConfig)
                .getEmitter();
        Metadata metadata = new Metadata();
        metadata.set("mime", "mimeA");
        metadata.set("title", "titleA");
        emitter.emit("1",
                Collections.singletonList(metadata), new ParseContext());
        client.getJson(endpoint + "/_refresh");
        metadata.set("title", "titleB");
        emitter.emit("1",
                Collections.singletonList(metadata), new ParseContext());
        client.getJson(endpoint + "/_refresh");

        Metadata metadata2 = new Metadata();
        metadata2.set("content", "the quick brown fox");
        emitter.emit("1",
                Collections.singletonList(metadata2), new ParseContext());
        client.getJson(endpoint + "/_refresh");

        String query = "{ \"track_total_hits\": true, \"query\": { " +
                "\"match_all\": {} } }";
        JsonResponse response =
                client.postJson(endpoint + "/_search", query);
        JsonNode doc1 = response.getJson().get("hits").get("hits")
                .get(0).get("_source");
        assertEquals("mimeA", doc1.get("mime").asText());
        assertEquals("titleB", doc1.get("title").asText());
        assertEquals("the quick brown fox",
                doc1.get("content").asText());
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private String getEndpoint() {
        return "http://" + CONTAINER.getHost() + ":" +
                CONTAINER.getMappedPort(9200) + "/" + TEST_INDEX;
    }

    private ElasticsearchTestClient getNewClient()
            throws TikaConfigException {
        HttpClientFactory httpClientFactory = new HttpClientFactory();
        ElasticsearchEmitterConfig config =
                new ElasticsearchEmitterConfig(
                        getEndpoint(), "_id",
                        ElasticsearchEmitterConfig.AttachmentStrategy
                                .SEPARATE_DOCUMENTS,
                        ElasticsearchEmitterConfig.UpdateStrategy
                                .OVERWRITE,
                        10, DEFAULT_EMBEDDED_FILE_FIELD_NAME, null,
                        new HttpClientConfig(null, null, null,
                                -1, -1, null, -1));
        return new ElasticsearchTestClient(config,
                httpClientFactory.build());
    }

    protected void sendMappings(ElasticsearchTestClient client,
                                String endpoint, String index,
                                String mappingsFile) throws Exception {
        String mappings = IOUtils.toString(
                ElasticsearchTest.class.getResourceAsStream(
                        "/elasticsearch/" + mappingsFile),
                StandardCharsets.UTF_8);
        int status = -1;
        int tries = 0;
        JsonResponse response = null;
        while (status != 200 && tries++ < 20) {
            response = client.putJson(endpoint, mappings);
            if (status != 200) {
                Thread.sleep(1000);
            }
            status = response.getStatus();
        }
        if (status != 200) {
            throw new IllegalArgumentException(
                    "couldn't create index/add mappings: " + response);
        }
        assertTrue(response.getJson().get("acknowledged").asBoolean());
        assertEquals(index,
                response.getJson().get("index").asText());
    }

    private void runPipes(
            ElasticsearchTestClient client,
            ElasticsearchEmitterConfig.AttachmentStrategy attachStrat,
            ElasticsearchEmitterConfig.UpdateStrategy updateStrat,
            ParseMode parseMode, String endpoint,
            Path pipesDirectory, Path testDocDirectory) throws Exception {

        Path pluginsConfig = getPluginsConfig(pipesDirectory,
                attachStrat, updateStrat, parseMode,
                endpoint, testDocDirectory);

        TikaCLI.main(new String[]{
                "-a", "-c",
                pluginsConfig.toAbsolutePath().toString()});

        // refresh to make content searchable
        client.getJson(endpoint + "/_refresh");
    }

    @NotNull
    private Path getPluginsConfig(
            Path pipesDirectory,
            ElasticsearchEmitterConfig.AttachmentStrategy attachStrat,
            ElasticsearchEmitterConfig.UpdateStrategy updateStrat,
            ParseMode parseMode, String endpoint,
            Path testDocDirectory) throws IOException {

        Path tikaConfig = pipesDirectory.resolve("plugins-config.json");

        Path log4jPropFile = pipesDirectory.resolve("log4j2.xml");
        try (InputStream is = ElasticsearchTest.class
                .getResourceAsStream(
                        "/pipes-fork-server-custom-log4j2.xml")) {
            Files.copy(is, log4jPropFile);
        }

        boolean includeRouting = (attachStrat ==
                ElasticsearchEmitterConfig.AttachmentStrategy
                        .PARENT_CHILD);

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("ATTACHMENT_STRATEGY",
                attachStrat.toString());
        replacements.put("UPDATE_STRATEGY",
                updateStrat.toString());
        replacements.put("FETCHER_BASE_PATH", testDocDirectory);
        replacements.put("PARSE_MODE", parseMode.name());
        replacements.put("INCLUDE_ROUTING", includeRouting);
        replacements.put("ELASTICSEARCH_URL", endpoint);
        replacements.put("LOG4J_JVM_ARG",
                "-Dlog4j.configurationFile=" +
                        log4jPropFile.toAbsolutePath());

        JsonConfigHelper.writeConfigFromResource(
                "/elasticsearch/plugins-template.json",
                ElasticsearchTest.class, replacements, tikaConfig);

        return tikaConfig;
    }

    private void createTestHtmlFiles(String bodyContent,
                                     int numHtmlDocs,
                                     Path testDocDirectory)
            throws Exception {
        Files.createDirectories(testDocDirectory);
        for (int i = 0; i < numHtmlDocs; ++i) {
            String html = "<html><body>" + bodyContent +
                    "</body></html>";
            Path p = testDocDirectory.resolve("test-" + i + ".html");
            Files.write(p, html.getBytes(StandardCharsets.UTF_8));
        }
        File testDocuments = Paths
                .get(ElasticsearchTest.class
                        .getResource("/test-documents").toURI())
                .toFile();
        for (File f : testDocuments.listFiles()) {
            Path targ = testDocDirectory.resolve(f.getName());
            Files.copy(f.toPath(), targ);
            numTestDocs++;
        }
    }
}
