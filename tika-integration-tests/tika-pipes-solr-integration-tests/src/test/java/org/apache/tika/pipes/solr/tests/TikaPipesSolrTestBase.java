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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.cli.TikaCLI;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.solr.SolrEmitter;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

public abstract class TikaPipesSolrTestBase {

    private final String collection = "testcol";
    private final int numDocs = 42;
    private final File testFileFolder = new File("target", "test-files");
    protected GenericContainer<?> solr;
    private String solrHost;
    private int solrPort;
    private int zkPort;
    private String solrEndpoint;

    public abstract boolean useZk();

    public abstract String getSolrImageName();


    public boolean handlesParentChild() {
        return true;
    }

    @Rule
    public GenericContainer<?> solrContainer =
            new GenericContainer<>(DockerImageName.parse(getSolrImageName())).withExposedPorts(8983,
                    9983)
                    .withCommand("-DzkRun");

    @Before
    public void setupTest() throws Exception {
        setupSolr(solrContainer);
    }

    @AfterEach
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(testFileFolder);
    }

    @Test
    public void testPipesIteratorWithSolrUrls() throws Exception {
        runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter();
    }

    private void createTestFiles(String bodyContent) throws Exception {
        testFileFolder.mkdirs();
        for (int i = 0; i < numDocs; ++i) {
            FileUtils.writeStringToFile(new File(testFileFolder, "test-" + i + ".html"),
                    "<html><body>" + bodyContent + "</body></html>", StandardCharsets.UTF_8);
        }
        FileUtils.copyInputStreamToFile(this.getClass().getResourceAsStream("/embedded/embedded.docx"),
                new File(testFileFolder, "test-embedded.docx"));
    }

    protected void setupSolr(GenericContainer<?> solr) throws Exception {
        createTestFiles("initial");
        this.solr = solr;
        solrHost = solr.getHost();
        solrPort = solr.getMappedPort(8983);
        zkPort = solr.getMappedPort(9983);
        solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        solr.execInContainer("/opt/solr/bin/solr", "create_collection", "-c", collection);

        try (SolrClient solrClient = new LBHttpSolrClient.Builder().withBaseSolrUrls(solrEndpoint)
                .build()) {

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
            postAddRoot.setEntity(new StringEntity("{\n" +
                    "  \"add-field\":{\n" +
                    "     \"name\":\"path\",\n" +
                    "     \"type\":\"string\",\n" +
                    "     \"indexed\":true,\n" +
                    "     \"stored\":true, \n" +
                    "     \"docValues\":false \n" +
                    "  }\n" +
                    "}"));
            CloseableHttpResponse resp = client.execute(postAddRoot);
            Assert.assertEquals(200, resp.getStatusLine().getStatusCode());
        }
    }
    private void addSchemaFieldsForNestedDocs(String solrUrl) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            HttpPost postAddRoot = new HttpPost(solrUrl + "/schema");
            postAddRoot.setHeader("Content-Type", "application/json");
            postAddRoot.setEntity(new StringEntity("{\n" +
                    "  \"replace-field\":{\n" +
                    "     \"name\":\"_root_\",\n" +
                    "     \"type\":\"string\",\n" +
                    "     \"indexed\":true,\n" +
                    "     \"stored\":true, \n" +
                    "     \"docValues\":false \n" +
                    "  }\n" +
                    "}"));
            CloseableHttpResponse resp = client.execute(postAddRoot);
            Assert.assertEquals(200, resp.getStatusLine().getStatusCode());
        }
    }

    /**
     * Runs a test using Solr Pipe Iterator, File Fetcher and Solr Emitter.
     */
    protected void runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter()
            throws Exception {
        File tikaConfigFile = new File("target", "ta.xml");
        File log4jPropFile = new File("target", "tmp-log4j2.xml");
        try (InputStream is = this.getClass()
                .getResourceAsStream("/pipes-fork-server-custom-log4j2.xml")) {
            FileUtils.copyInputStreamToFile(is, log4jPropFile);
        }
        String tikaConfigTemplateXml;
        try (InputStream is = this.getClass()
                .getResourceAsStream("/tika-config-solr-urls.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String tikaConfigXml =
                createTikaConfigXml(useZk(), tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                        SolrEmitter.UpdateStrategy.ADD,
                        SolrEmitter.AttachmentStrategy.PARENT_CHILD,
                        HandlerConfig.PARSE_MODE.RMETA);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);
        TikaCLI.main(new String[] {"-a", "--config=" + tikaConfigFile.getAbsolutePath()});

        try (SolrClient solrClient = new LBHttpSolrClient.Builder().withBaseSolrUrls(solrEndpoint)
                .build()) {
            solrClient.commit(collection, true, true);
            Assert.assertEquals(numDocs, solrClient
                    .query(collection, new SolrQuery("mime_s:\"text/html; charset=ISO-8859-1\""))
                    .getResults().getNumFound());
            Assert.assertEquals(numDocs,
                    solrClient.query(collection, new SolrQuery("content_s:*initial*")).getResults()
                            .getNumFound());
            if(handlesParentChild()) {
                Assert.assertEquals(3,
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
        createTestFiles("updated");
        tikaConfigXml =
                createTikaConfigXml(useZk(), tikaConfigFile, log4jPropFile, tikaConfigTemplateXml,
                        SolrEmitter.UpdateStrategy.UPDATE_MUST_EXIST,
                        SolrEmitter.AttachmentStrategy.PARENT_CHILD,
                        HandlerConfig.PARSE_MODE.RMETA);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);

        TikaCLI.main(new String[] {"-a", "--config=" + tikaConfigFile.getAbsolutePath()});

        try (SolrClient solrClient = new LBHttpSolrClient.Builder().withBaseSolrUrls(solrEndpoint)
                .build()) {
            solrClient.commit(collection, true, true);
            Assert.assertEquals(numDocs, solrClient
                    .query(collection, new SolrQuery("mime_s:\"text/html; charset=ISO-8859-1\""))
                    .getResults().getNumFound());
            Assert.assertEquals(numDocs,
                    solrClient.query(collection, new SolrQuery("content_s:*updated*")).getResults()
                            .getNumFound());
        }
    }

    @NotNull
    private String createTikaConfigXml(boolean useZk, File tikaConfigFile, File log4jPropFile,
                                       String tikaConfigTemplateXml,
                                       SolrEmitter.UpdateStrategy updateStrategy,
                                       SolrEmitter.AttachmentStrategy attachmentStrategy,
                                       HandlerConfig.PARSE_MODE parseMode) {
        String res =
                tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                        .replace("{UPDATE_STRATEGY}", updateStrategy.toString())
                        .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                        .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                        .replace("{PATH_TO_DOCS}", testFileFolder.getAbsolutePath())
                        .replace("{PARSE_MODE}", parseMode.name());
        if (useZk) {
            res = res.replace("{SOLR_CONNECTION}",
                    "<solrZkHosts>\n" + "        <solrZkHost>" + solrHost + ":" + zkPort +
                            "</solrZkHost>\n" + "      </solrZkHosts>\n");
        } else {
            res = res.replace("{SOLR_CONNECTION}",
                    "<solrUrls>\n" + "        <solrUrl>http://" + solrHost + ":" + solrPort +
                            "/solr</solrUrl>\n" + "      </solrUrls>\n");
        }
        return res;
    }
}
