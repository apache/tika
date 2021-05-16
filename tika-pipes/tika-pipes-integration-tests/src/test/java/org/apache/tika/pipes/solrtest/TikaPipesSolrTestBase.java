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
package org.apache.tika.pipes.solrtest;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.cli.TikaCLI;
import org.apache.tika.pipes.PipeIntegrationTests;
import org.apache.tika.pipes.emitter.solr.SolrEmitter;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;

public abstract class TikaPipesSolrTestBase {

    private final String collection = "testcol";
    private final int numDocs = 42;

    protected GenericContainer<?> solr;

    private final File testFileFolder = new File("target", "test-files");
    private String solrHost;
    private int solrPort;
    private int zkPort;
    private String solrEndpoint;

    private void createTestHtmlFiles(String bodyContent) throws Exception {
        testFileFolder.mkdirs();
        for (int i = 0; i < numDocs; ++i) {
            FileUtils.writeStringToFile(new File(testFileFolder, "test-" + i + ".html"), "<html><body>" + bodyContent + "</body></html>", StandardCharsets.UTF_8);
        }
    }

    protected void setupSolr(GenericContainer<?> solr) throws Exception {
        createTestHtmlFiles("initial");
        this.solr = solr;
        solrHost = solr.getHost();
        solrPort = solr.getMappedPort(8983);
        zkPort = solr.getMappedPort(9983);
        solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        solr.execInContainer("/opt/solr/bin/solr", "create_collection", "-c", collection);

        try (SolrClient solrClient = new LBHttpSolrClient.Builder()
                .withBaseSolrUrls(solrEndpoint).build()) {

            for (int i = 0; i < numDocs; ++i) {
                SolrInputDocument solrDoc = new SolrInputDocument();
                String filename = "test-" + i + ".html";
                solrDoc.setField("id", filename);
                solrDoc.setField("path", filename);
                solrClient.add(collection, solrDoc);
            }
            solrClient.commit(collection);
        }
    }

    /**
     * Runs a test using Solr Pipe Iterator, File Fetcher and Solr Emitter.
     * @param useZk If true, use zookeeper to connect to solr. Otherwise use direct solr URLs.
     */
    protected void runTikaAsyncSolrPipeIteratorFileFetcherSolrEmitter(boolean useZk) throws Exception {
        File tikaConfigFile = new File("target", "ta.xml");
        File log4jPropFile = new File("target", "tmp-log4j.properties");
        try (InputStream is = PipeIntegrationTests.class.getResourceAsStream("/tika-async-log4j.properties")) {
            FileUtils.copyInputStreamToFile(is, log4jPropFile);
        }
        String tikaConfigTemplateXml;
        try (InputStream is = PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-urls.xml")) {
            tikaConfigTemplateXml = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        String tikaConfigXml = createTikaConfigXml(useZk,
                tikaConfigFile,
                log4jPropFile,
                tikaConfigTemplateXml,
                SolrEmitter.UpdateStrategy.ADD,
                SolrEmitter.AttachmentStrategy.CONCATENATE_CONTENT);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.getAbsolutePath()});

        try (SolrClient solrClient = new LBHttpSolrClient.Builder()
                .withBaseSolrUrls(solrEndpoint).build()) {
            solrClient.commit(collection);
            Assert.assertEquals(numDocs, solrClient.query(collection, new SolrQuery("mime_s:\"text/html; charset=ISO-8859-1\"")).getResults().getNumFound());
            Assert.assertEquals(numDocs, solrClient.query(collection, new SolrQuery("content_s:*initial*")).getResults().getNumFound());
        }

        // update the documents with "update must exist" and run tika async again with "UPDATE_MUST_EXIST". It should not fail, and docs should be updated.
        createTestHtmlFiles("updated");
        tikaConfigXml = createTikaConfigXml(useZk,
                tikaConfigFile,
                log4jPropFile,
                tikaConfigTemplateXml,
                SolrEmitter.UpdateStrategy.UPDATE_MUST_EXIST,
                SolrEmitter.AttachmentStrategy.CONCATENATE_CONTENT);
        FileUtils.writeStringToFile(tikaConfigFile, tikaConfigXml, StandardCharsets.UTF_8);

        TikaCLI.main(new String[]{"-a", "--config=" + tikaConfigFile.getAbsolutePath()});

        try (SolrClient solrClient = new LBHttpSolrClient.Builder()
                .withBaseSolrUrls(solrEndpoint).build()) {
            solrClient.commit(collection);
            Assert.assertEquals(numDocs, solrClient.query(collection, new SolrQuery("mime_s:\"text/html; charset=ISO-8859-1\"")).getResults().getNumFound());
            Assert.assertEquals(numDocs, solrClient.query(collection, new SolrQuery("content_s:*updated*")).getResults().getNumFound());
        }
    }

    @NotNull
    private String createTikaConfigXml(boolean useZk,
                                       File tikaConfigFile,
                                       File log4jPropFile,
                                       String tikaConfigTemplateXml,
                                       SolrEmitter.UpdateStrategy updateStrategy,
                                       SolrEmitter.AttachmentStrategy attachmentStrategy) {
        String res = tikaConfigTemplateXml.replace("{TIKA_CONFIG}", tikaConfigFile.getAbsolutePath())
                .replace("{UPDATE_STRATEGY}", updateStrategy.toString())
                .replace("{ATTACHMENT_STRATEGY}", attachmentStrategy.toString())
                .replace("{LOG4J_PROPERTIES_FILE}", log4jPropFile.getAbsolutePath())
                .replace("{PATH_TO_DOCS}", testFileFolder.getAbsolutePath());
        if (useZk) {
            res = res.replace("{SOLR_CONNECTION}", "<solrZkHosts>\n" +
                    "        <solrZkHost>" + solrHost + ":" + zkPort + "</solrZkHost>\n" +
                    "      </solrZkHosts>\n");
        } else {
            res = res.replace("{SOLR_CONNECTION}", "<solrUrls>\n" +
                    "        <solrUrl>http://" + solrHost + ":" + solrPort + "/solr</solrUrl>\n" +
                    "      </solrUrls>\n");
        }
        return res;
    }
}
