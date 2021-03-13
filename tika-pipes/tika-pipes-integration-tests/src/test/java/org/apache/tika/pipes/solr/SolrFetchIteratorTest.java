package org.apache.tika.pipes.solr;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.pipes.PipeIntegrationTests;
import org.apache.tika.pipes.fetchiterator.FetchIterator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SolrFetchIteratorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SolrFetchIteratorTest.class);

    @Rule
    public GenericContainer solr = new GenericContainer(DockerImageName.parse("solr:8.8.1"))
            .withExposedPorts(8983, 9983)
            .withCommand("-DzkRun");
    private String solrHost;
    private Integer solrPort;
    private Integer zkPort;
    private final int numDocs = 42;

    @Before
    public void setupTest() throws Exception {
        solrHost = solr.getHost();
        solrPort = solr.getMappedPort(8983);
        zkPort = solr.getMappedPort(9983);
        String solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        String collection = "testcol";

        CloseableHttpResponse createColResp = HttpClients.createDefault().execute(new HttpGet(solrEndpoint + "/admin/collections?action=CREATE&name=" + collection + "&numShards=1&replicationFactor=1&wt=xml"));
        Assert.assertEquals(200, createColResp.getStatusLine().getStatusCode());
        LOGGER.info("Create solr collection result: {}", IOUtils.toString(createColResp.getEntity().getContent(), StandardCharsets.UTF_8));

        try (SolrClient solrClient = new LBHttpSolrClient.Builder()
                .withConnectionTimeout(10000)
                .withSocketTimeout(60000)
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

    @Test
    public void testSolrToFsWithSolrUrls() throws Exception {
        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-fetch-iterator-with-solr-urls.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = StringUtils.replace(xmlContents, "{SOLR_URL}", "http://" + solrHost + ":" + solrPort + "/solr");
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8)));
            FetchIterator it = tikaConfig.getFetchIterator();
            it.init(1);
            int numProcessed = it.call();
            Assert.assertEquals(numDocs, numProcessed);

        }
    }

    @Test
    public void testSolrToFsWithZkHost() throws Exception {
        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-fetch-iterator-with-zk-host.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = StringUtils.replace(xmlContents, "{ZK_HOST}", solrHost + ":" + zkPort);
            TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8)));
            FetchIterator it = tikaConfig.getFetchIterator();
            it.init(1);
            int numProcessed = it.call();
            Assert.assertEquals(numDocs, numProcessed);

        }
    }
}
