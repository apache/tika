package org.apache.tika.pipes.emitter.solr;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.pipes.PipeIntegrationTests;
import org.junit.Assert;
import org.testcontainers.containers.GenericContainer;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SolrEmitterTestBase {
    private final String collection = "testcol";
    protected GenericContainer<?> solr;

    protected void setupSolr(GenericContainer<?> solr) throws Exception {
        this.solr = solr;
        solr.execInContainer("/opt/solr/bin/solr", "create_collection", "-c", collection);
    }

    protected void runEmitTestWithZkHost() throws Exception {
        String solrHost = solr.getHost();
        int zkPort = solr.getMappedPort(9983);
        int solrPort = solr.getMappedPort(8983);
        String solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-emitter-with-zk-host.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = xmlContents.replace("{ZK_HOST}", solrHost + ":" + zkPort);
            runTest(solrEndpoint, xmlContents);
        }
    }

    protected void runEmitTestWithSolrUrls() throws Exception {
        String solrHost = solr.getHost();
        int solrPort = solr.getMappedPort(8983);
        String solrEndpoint = "http://" + solrHost + ":" + solrPort + "/solr";

        try (InputStream is =
                     PipeIntegrationTests.class.getResourceAsStream("/tika-config-solr-emitter-with-solr-urls.xml")) {
            String xmlContents = IOUtils.toString(is, StandardCharsets.UTF_8);
            xmlContents = xmlContents.replace("{SOLR_URL}", solrEndpoint);
            runTest(solrEndpoint, xmlContents);
        }
    }

    private void runTest(String solrEndpoint, String xmlContents) throws TikaException, IOException, SAXException, SolrServerException {
        TikaConfig tikaConfig = new TikaConfig(new ByteArrayInputStream(xmlContents.getBytes(StandardCharsets.UTF_8)));
        SolrEmitter solrEmitter = (SolrEmitter)tikaConfig.getEmitterManager().getEmitter("solr-emitter-1");
        List<Metadata> metadataList = new ArrayList<>();
        Metadata metadata = new Metadata();
        metadata.set("CACHE-CONTROL", "NO-CACHE");
        metadata.set("Content-Encoding", "UTF-8");
        metadata.set("Content-Type", "text/html; charset=UTF-8");
        metadata.set("Content-Type-Hint", "text/html; charset=UTF-8");
        metadata.set(Property.externalTextBag("EXPIRES"), new String[]{"-1", "-1"});
        metadata.set(Property.externalTextBag("PRAGMA"), new String[]{"NO-CACHE", "NO-CACHE"});
        metadata.set(Property.externalTextBag("X-Parsed-By"), new String[]{"org.apache.tika.parser.DefaultParser",
                "org.apache.tika.parser.html.HtmlParser"});
        metadata.set("X-TIKA:content", "\n\n\n\n\n\n\n\n\n\n\nbody text from the doc here \n\n\n\n\n\n    ");
        metadata.set("X-TIKA:content_handler", "ToTextContentHandler");
        metadata.set("X-TIKA:embedded_depth", "0");
        metadata.set("X-TIKA:parse_time_millis", "301");
        metadata.set("dc:title", "SOME TITLE");
        metadata.set("title", "SOME TITLE");
        metadataList.add(metadata);
        String emitKey = "http://127.0.0.1/path/to/page1.html";
        solrEmitter.emit(emitKey, metadataList);

        try (SolrClient solrClient = new CloudSolrClient.Builder(Collections.singletonList(solrEndpoint))
                .withConnectionTimeout(10000)
                .withSocketTimeout(60000)
                .build()) {
            solrClient.commit(collection);
            QueryResponse response = solrClient.query(collection, new SolrQuery("*:*"));
            Assert.assertEquals(1, response.getResults().size());
            SolrDocument solrDocument = response.getResults().get(0);

            Assert.assertEquals(emitKey, solrDocument.getFirstValue("id"));
        }
    }
}
