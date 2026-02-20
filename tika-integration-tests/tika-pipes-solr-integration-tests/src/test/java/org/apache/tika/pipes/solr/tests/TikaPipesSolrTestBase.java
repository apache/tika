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
package org.apache.tika.pipes.solr.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import org.apache.tika.cli.TikaCLI;
import org.apache.tika.config.JsonConfigHelper;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.emitter.solr.SolrEmitterConfig;
import org.apache.tika.utils.SystemUtils;


public abstract class TikaPipesSolrTestBase {

    private final String collection = "testcol";
    private final int numDocs = 42;
    private Path testFileFolder;

    @Container
    protected GenericContainer<?> solr;
    private String solrHost;
    private int solrPort;
    private int zkPort;
    private String solrEndpoint;

    public TikaPipesSolrTestBase() {
        try {
            init();
        } catch (InterruptedException e) {
            //swallow
        }
    }

    public abstract boolean useZk();

    public abstract String getSolrImageName();

    public boolean handlesParentChild() {
        return true;
    }

    private void init() throws InterruptedException {
        if (SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_VERSION_WSL) {
            // Networking on these operating systems needs fixed ports and localhost to be passed for the SolrCloud
            // with Zookeeper tests to succeed. This means stopping and starting needs
            solr = new FixedHostPortGenericContainer<>(
                    DockerImageName.parse(getSolrImageName()).toString()).withFixedExposedPort(8983,
                    8983).withFixedExposedPort(9983, 9983).withCommand("-DzkRun -Dhost=localhost");
        } else {
            solr = new GenericContainer<>(
                    DockerImageName.parse(getSolrImageName())).withExposedPorts(8983, 9983)
                    .withCommand("-DzkRun");
        }
        solr.start();

        // Ideally wanted to use TestContainers WaitStrategy but they were inconsistent
        Thread.sleep(2000);
    }

    @AfterEach
    public void tearDownAfter() throws Exception {
        if (solr != null) {
            solr.stop();
            long totalWait = 0;
            long maxWait = 250000;
            while (solr.getContainerInfo() != null) {
                if (totalWait >= maxWait) {
                    break;
                }
                Thread.sleep(1000);
                totalWait += 1000;
            }
        }
    }

    @Test
    public void testPipesIteratorWithSolrUrls(@TempDir Path pipesDirectory) throws Exception {
        runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter(pipesDirectory);
    }

    private void createTestFiles(String bodyContent) throws Exception {
        Files.createDirectories(testFileFolder);
        for (int i = 0; i < numDocs; ++i) {
            Files.writeString(testFileFolder.resolve("test-" + i + ".html"),
                    "<html><body>" + bodyContent + "</body></html>", StandardCharsets.UTF_8);
        }
        try (InputStream is = this.getClass().getResourceAsStream("/embedded/embedded.docx")) {
            Files.copy(is, testFileFolder.resolve("test-embedded.docx"));
        }
    }

    protected void setupSolr(Path pipesDirectory) throws Exception {
        testFileFolder = pipesDirectory.resolve("test-files");
        createTestFiles("initial");
        solrHost = solr.getHost();
        solrPort = solr.getMappedPort(8983);
        zkPort = solr.getMappedPort(9983);
        solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        solr.execInContainer("/opt/solr/bin/solr", "create_collection", "-c", collection);

        try (SolrClient solrClient = new Http2SolrClient.Builder(solrEndpoint).build()) {

            addBasicSchemaFields(solrEndpoint + "/" + collection);
            addSchemaFieldsForNestedDocs(solrEndpoint + "/" + collection);
            for (int i = 0; i < numDocs; ++i) {
                SolrInputDocument solrDoc = new SolrInputDocument();
                String filename = "test-" + i + ".html";
                solrDoc.setField("id", filename);
                solrDoc.setField("path", filename);
                solrClient.add(collection, solrDoc);
            }
            SolrInputDocument embeddedDoc = new SolrInputDocument();
            String filename = "test-embedded.docx";
            embeddedDoc.setField("id", filename);
            embeddedDoc.setField("path", filename);
            solrClient.add(collection, embeddedDoc);
            solrClient.commit(collection, true, true);
        }
    }

    private void addBasicSchemaFields(String solrUrl) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            HttpPost postAddRoot = new HttpPost(solrUrl + "/schema");
            postAddRoot.setHeader("Content-Type", "application/json");
            postAddRoot.setEntity(new StringEntity(
                    """
                    {
                      "add-field":{
                         "name":"path",
                         "type":"string",
                         "indexed":true,
                         "stored":true, 
                         "docValues":false 
                      }
                    }"""));
            CloseableHttpResponse resp = client.execute(postAddRoot);
            assertEquals(200, resp.getStatusLine().getStatusCode());
        }
    }

    private void addSchemaFieldsForNestedDocs(String solrUrl) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            HttpPost postAddRoot = new HttpPost(solrUrl + "/schema");
            postAddRoot.setHeader("Content-Type", "application/json");
            postAddRoot.setEntity(new StringEntity(
                    """
                    {
                      "replace-field":{
                         "name":"_root_",
                         "type":"string",
                         "indexed":true,
                         "stored":true, 
                         "docValues":false 
                      }
                    }"""));
            CloseableHttpResponse resp = client.execute(postAddRoot);
            assertEquals(200, resp.getStatusLine().getStatusCode());
        }
    }

    /**
     * Runs a test using Solr Pipe Iterator, File Fetcher and Solr Emitter.
     */
    protected void runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter(Path pipesDirectory) throws Exception {
        setupSolr(pipesDirectory);

        Path tikaConfigFile = getTikaConfig(pipesDirectory,
                SolrEmitterConfig.UpdateStrategy.ADD, SolrEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                ParseMode.RMETA);

        TikaCLI.main(new String[]{"-a", "-c", tikaConfigFile.toAbsolutePath().toString()});

        try (SolrClient solrClient = new Http2SolrClient.Builder(solrEndpoint).build()) {
            solrClient.commit(collection, true, true);
            assertEquals(numDocs, solrClient.query(collection,
                            new SolrQuery("mime_s:\"text/html; charset=UTF-8\"")).getResults()
                    .getNumFound());
            assertEquals(numDocs,
                    solrClient.query(collection, new SolrQuery("content_s:*initial*")).getResults()
                            .getNumFound());
            if (handlesParentChild()) {
                assertEquals(3,
                        solrClient.query(collection, new SolrQuery("_root_:\"test-embedded.docx\""))
                                .getResults().getNumFound());
            }
            //clean up test-embedded.docx so that the iterator won't try to update its children
            //in the next test

            solrClient.deleteByQuery(collection, "_root_:\"test-embedded.docx\"");

            solrClient.commit(collection, true, true);
        }


        // update the documents with "update must exist" and run tika async again with "UPDATE_MUST_EXIST".
        // It should not fail, and docs should be updated.
        // Delete test files and recreate with new content
        FileUtils.deleteDirectory(testFileFolder.toFile());
        createTestFiles("updated");
        tikaConfigFile = getTikaConfig(pipesDirectory,
                SolrEmitterConfig.UpdateStrategy.UPDATE_MUST_EXIST,
                SolrEmitterConfig.AttachmentStrategy.PARENT_CHILD,
                ParseMode.RMETA);

        TikaCLI.main(new String[]{"-a", "-c", tikaConfigFile.toAbsolutePath().toString()});

        try (SolrClient solrClient = new Http2SolrClient.Builder(solrEndpoint).build()) {
            solrClient.commit(collection, true, true);
            assertEquals(numDocs, solrClient.query(collection,
                            new SolrQuery("mime_s:\"text/html; charset=UTF-8\"")).getResults()
                    .getNumFound());
            assertEquals(numDocs,
                    solrClient.query(collection, new SolrQuery("content_s:*updated*")).getResults()
                            .getNumFound());
        }
    }

    @NotNull
    private Path getTikaConfig(Path pipesDirectory,
                               SolrEmitterConfig.UpdateStrategy updateStrategy,
                               SolrEmitterConfig.AttachmentStrategy attachmentStrategy,
                               ParseMode parseMode) throws IOException {
        Path tikaConfig = pipesDirectory.resolve("plugins-config.json");

        Path log4jPropFile = pipesDirectory.resolve("log4j2.xml");
        try (InputStream is = this.getClass().getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            Files.copy(is, log4jPropFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> solrUrls;
        List<String> solrZkHosts;
        if (useZk()) {
            solrUrls = List.of();
            solrZkHosts = List.of(solrHost + ":" + zkPort);
        } else {
            solrUrls = List.of("http://" + solrHost + ":" + solrPort + "/solr");
            solrZkHosts = List.of();
        }

        Map<String, Object> replacements = new HashMap<>();
        replacements.put("UPDATE_STRATEGY", updateStrategy.toString());
        replacements.put("ATTACHMENT_STRATEGY", attachmentStrategy.toString());
        replacements.put("FETCHER_BASE_PATH", testFileFolder);
        replacements.put("PARSE_MODE", parseMode.name());
        replacements.put("SOLR_URLS", solrUrls);
        replacements.put("SOLR_ZK_HOSTS", solrZkHosts);
        replacements.put("LOG4J_JVM_ARG", "-Dlog4j.configurationFile=" + log4jPropFile.toAbsolutePath());

        JsonConfigHelper.writeConfigFromResource("/solr/plugins-template.json",
                TikaPipesSolrTestBase.class, replacements, tikaConfig);

        return tikaConfig;
    }

}
