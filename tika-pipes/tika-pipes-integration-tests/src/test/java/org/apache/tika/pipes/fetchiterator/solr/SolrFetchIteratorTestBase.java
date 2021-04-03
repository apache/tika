package org.apache.tika.pipes.fetchiterator.solr;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.PipeIntegrationTests;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.junit.Assert;
import org.testcontainers.containers.GenericContainer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public abstract class SolrFetchIteratorTestBase {

    private final String collection = "testcol";
    private final int numDocs = 42;

    protected GenericContainer<?> solr;

    protected void setupSolr(GenericContainer<?> solr) throws Exception {
        this.solr = solr;
        String solrHost = solr.getHost();
        int solrPort = solr.getMappedPort(8983);
        String solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        solr.execInContainer("/opt/solr/bin/solr", "create_collection", "-c", collection);

        try (SolrClient solrClient = new LBHttpSolrClient.Builder()
                .withBaseSolrUrls(solrEndpoint).build()) {

            for (int i = 0; i < numDocs; ++i) {
                SolrInputDocument solrDoc = new SolrInputDocument();
                solrDoc.setField("id", "test" + i + ".html");
                solrDoc.setField("path", "/path/to/my/test" + i + ".html");
                solrClient.add(collection, solrDoc);
            }
            solrClient.commit(collection);
        }
    }

    protected void runSolrToFsWithSolrUrlsTest() throws Exception {
        String solrHost = solr.getHost();
        int solrPort = solr.getMappedPort(8983);
        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-fetch-iterator-with-solr-urls.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = xmlContents.replace("{SOLR_URL}", "http://" + solrHost + ":" + solrPort + "/solr");
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8)));
            FetchIterator it = tikaConfig.getFetchIterator();
            it.initialize(Collections.emptyMap());
            int numProcessed = it.call();
            Assert.assertEquals(numDocs, numProcessed);
        }
    }

    protected void runSolrToFsWithZkHostTest() throws Exception {
        String solrHost = solr.getHost();
        int zkPort = solr.getMappedPort(9983);
        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-fetch-iterator-with-zk-host.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = xmlContents.replace("{ZK_HOST}", solrHost + ":" + zkPort);
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8)));
            FetchIterator it = tikaConfig.getFetchIterator();
            it.initialize(Collections.emptyMap());
            int numProcessed = it.call();
            Assert.assertEquals(numDocs, numProcessed);
        }
    }
}
